package org.candlepin.sync;

import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles incoming subscriptions from an import against pre-existing pools in the
 * database.
 *
 * Comparisons are made and when we detect an incoming subscription that looks
 * similar to an existing pool, but from a new upstream entitlement, we avoid destroying
 * the pool and mass cert revocation and treat them as if they are the same
 * subscription.
 *
 */
public class SubscriptionReconciler {

    private static Logger log = LoggerFactory.getLogger(SubscriptionReconciler.class);

    /**
     * @param subsToImport
     *
     *  Reconciles incoming entitlements to existing pools to attempt to limit
     *  the number of pools we must destroy and revoke.
     *
     *  Each set is mapped against the upstream pool ID. Subscriptions originating from
     *  different pools will never match each other for replacement.
     *
     *  First match attempts a direct match of entitlement ID from incoming
     *  entitlements for comparison to existing pools. If so we re-use the existing pool.
     *  (note that despite the same entitlement ID, quantity and other data may have
     *  changed)
     *
     *  Next we attempt to match on exact quantity. This would allow the user to re-create
     *  a distributor upstream and re-assign it the same entitlement quantities from the
     *  same pools, without triggering a mass regen on import.
     *
     *  The last attempt will order the remaining incoming entitlements by quantity, and
     *  match these against a quantity ordered list of the remaining pools for that
     *  upstream pool.
     *
     *  Remaining incoming subscriptions that did not match any pools per the above are
     *  treated as new subscriptions.
     */
    public void reconcile(Owner owner, List<Subscription> subsToImport,
            PoolCurator poolCurator) {

        Map<String, Map<String, Pool>> existingPoolsByUpstreamPool =
            mapPoolsByUpstreamPool(owner, poolCurator);

        // if we can match to the entitlement id do it.
        // we need a new list to hold the ones that are left
        Set<Subscription> subscriptionsStillToImport = new HashSet<Subscription>();
        for (Subscription subscription : subsToImport) {
            Pool local = null;
            Map<String, Pool> map = existingPoolsByUpstreamPool.get(
                subscription.getUpstreamPoolId());
            if (map == null || map.isEmpty()) {
//                createSubscription(subscription);
                log.info("New subscription for incoming entitlement ID: " +
                    subscription.getUpstreamEntitlementId());
                continue;
            }
            local = map.get(subscription.getUpstreamEntitlementId());
            if (local != null) {
                mergeSubscription(subscription, local, map);
                log.info("Merging subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] into subscription with existing entitlement id [" +
                    local.getUpstreamEntitlementId() +
                    "]. Entitlement id match.");
            }
            else {
                subscriptionsStillToImport.add(subscription);
                log.warn("Subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] does not have an entitlement id match " +
                    "in the current subscriptions for the upstream pool id [" +
                    subscription.getUpstreamPoolId() +
                    "]");
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // we need a new list to hold the ones that are left
        List<Subscription> subscriptionsNeedQuantityMatch = new ArrayList<Subscription>();
        for (Subscription subscription : subscriptionsStillToImport) {
            Pool local = null;
            Map<String, Pool> map = existingPoolsByUpstreamPool.get(
                subscription.getUpstreamPoolId());
            if (map == null) {
                map = new HashMap<String, Pool>();
            }
            for (Pool localSub : map.values()) {
                // TODO quantity
                Long quantity = localSub.getQuantity() /
                        localSub.getProduct().getMultiplier();
                if (quantity.equals(subscription.getQuantity())) {
                    local = localSub;
                    break;
                }
            }
            if (local != null) {
                mergeSubscription(subscription, local, map);
                log.info("Merging subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] into subscription with existing entitlement id [" +
                    local.getUpstreamEntitlementId() +
                    "]. Exact quantity match.");
            }
            else {
                subscriptionsNeedQuantityMatch.add(subscription);
                log.warn("Subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] does not have an exact quantity match " +
                    "in the current subscriptions for the upstream pool id [" +
                    subscription.getUpstreamPoolId() +
                    "]");
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // quantities will just match by position from highest to lowest
        // we need a new list to hold the ones that are left
        Subscription[] inNeed = subscriptionsNeedQuantityMatch.toArray(
            new Subscription[0]);
        Arrays.sort(inNeed, new SubQuantityComparator());
        for (Subscription subscription : inNeed) {
            Pool local = null;
            Map<String, Pool> map = existingPoolsByUpstreamPool.get(
                subscription.getUpstreamPoolId());
            if (map == null || map.isEmpty()) {
                log.info("Creating new subscription for incoming entitlement with id [" +
                    subscription.getUpstreamEntitlementId() +
                    "]");
                continue;
            }
            Pool[] locals = map.values().toArray(new Pool[0]);
            Arrays.sort(locals, new QuantityComparator());
            local = locals[0];
            mergeSubscription(subscription, local, map);
            log.info("Merging subscription for incoming entitlement id [" +
                subscription.getUpstreamEntitlementId() +
                "] into subscription with existing entitlement id [" +
                local.getUpstreamEntitlementId() +
                "]. Ordered quantity match.");
        }
    }

    /*
     * Maps upstream pool ID to a map of upstream entitlement ID to Subscription.
     */
    private Map<String, Map<String, Pool>> mapPoolsByUpstreamPool(Owner owner,
            PoolCurator poolCurator) {
        Map<String, Map<String, Pool>> existingSubsByUpstreamPool =
            new HashMap<String, Map<String, Pool>>();
        int idx = 0;
        for (Pool p : poolCurator.listByOwnerAndType(owner, PoolType.NORMAL)) {
            // if the upstream pool id is null,
            // this must be a locally controlled sub.
            if (p.getUpstreamPoolId() == null) {
                continue;
            }

            /*
             * If the existing sub does not have the entitlement id yet,
             * just assign a placeholder to differentiate.
             *
             * Suspect this is an old migration path, likely all pools have their
             * entitlement ID stamped by now. - dgoodwin 2015-04-14
             */
            if (p.getUpstreamEntitlementId() == null ||
                p.getUpstreamEntitlementId().trim().equals("")) {
                p.setUpstreamEntitlementId("" + idx++);
            }

            Map<String, Pool> subs = existingSubsByUpstreamPool.get(
                p.getUpstreamPoolId());
            if (subs == null) {
                subs = new HashMap<String, Pool>();
            }
            subs.put(p.getUpstreamEntitlementId(), p);
            existingSubsByUpstreamPool.put(p.getUpstreamPoolId(),
                subs);
        }
        return existingSubsByUpstreamPool;
    }

    private void mergeSubscription(Subscription subscription, Pool local,
        Map<String, Pool> map) {
        subscription.setId(local.getSubscriptionId());
        map.remove(local.getUpstreamEntitlementId());
        // send updated event
//        sink.emitSubscriptionModified(local, subscription);
    }

    /**
     *
     * QuantityComparator
     *
     * descending quantity sort on Subscriptions
     */

    public static class QuantityComparator implements
        Comparator<Pool>, Serializable {

        // TODO: compare quantity / multiplier to match subscription
        @Override
        public int compare(Pool s1, Pool s2) {
            return s2.getQuantity().compareTo(s1.getQuantity());
        }
    }


    public static class SubQuantityComparator implements
        Comparator<Subscription>, Serializable {

        @Override
        public int compare(Subscription s1, Subscription s2) {
            return s2.getQuantity().compareTo(s1.getQuantity());
        }
    }
}
