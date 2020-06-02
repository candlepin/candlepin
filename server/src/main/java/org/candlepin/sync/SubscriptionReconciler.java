/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.sync;

import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
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

    private PoolCurator poolCurator;

    @Inject
    public SubscriptionReconciler(PoolCurator poolCurator) {
        this.poolCurator = poolCurator;
    }

    /**
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
     *
     * @param owner
     *  The owner for which the subscriptions are being imported
     *
     * @param subsToImport
     *  A collection of subscriptions which are being imported
     *
     * @return
     *  The collection of reconciled subscriptions
     */
    public Collection<SubscriptionDTO> reconcile(Owner owner, Collection<SubscriptionDTO> subsToImport) {

        Map<String, Map<String, Pool>> existingPoolsByUpstreamPool = this.mapPoolsByUpstreamPool(owner);

        // if we can match to the entitlement id do it.
        // we need a new list to hold the ones that are left
        Set<SubscriptionDTO> subscriptionsStillToImport = new HashSet<>();
        for (SubscriptionDTO subscription : subsToImport) {
            Pool local = null;
            Map<String, Pool> map = existingPoolsByUpstreamPool.get(subscription.getUpstreamPoolId());

            if (map == null || map.isEmpty()) {
                log.info("New subscription for incoming entitlement ID: {}",
                    subscription.getUpstreamEntitlementId());

                continue;
            }

            local = map.get(subscription.getUpstreamEntitlementId());
            if (local != null) {
                mergeSubscription(subscription, local, map);
                log.info("Merging subscription for incoming entitlement id [{}] into subscription with " +
                    "existing entitlement id [{}]. Entitlement id match.",
                    subscription.getUpstreamEntitlementId(), local.getUpstreamEntitlementId()
                );
            }
            else {
                subscriptionsStillToImport.add(subscription);
                log.warn("Subscription for incoming entitlement id [{}] does not have an entitlement id " +
                    "match in the current subscriptions for the upstream pool id [{}]",
                    subscription.getUpstreamEntitlementId(), subscription.getUpstreamPoolId()
                );
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // we need a new list to hold the ones that are left
        List<SubscriptionDTO> subscriptionsNeedQuantityMatch = new ArrayList<>();
        for (SubscriptionDTO subscription : subscriptionsStillToImport) {
            Pool local = null;
            Map<String, Pool> map = existingPoolsByUpstreamPool.get(subscription.getUpstreamPoolId());
            if (map == null) {
                map = new HashMap<>();
            }

            for (Pool localSub : map.values()) {
                // TODO quantity
                long quantity = localSub.getQuantity() != null ? localSub.getQuantity() : 1;

                long multiplier = localSub.getProduct().getMultiplier() != null ?
                    localSub.getProduct().getMultiplier() :
                    1;

                Long effectiveQuantity = Long.valueOf(quantity / multiplier);
                if (effectiveQuantity.equals(subscription.getQuantity())) {
                    local = localSub;
                    break;
                }
            }

            if (local != null) {
                mergeSubscription(subscription, local, map);
                log.info("Merging subscription for incoming entitlement id [{}] into subscription with " +
                    "existing entitlement id [{}]. Exact quantity match.",
                    subscription.getUpstreamEntitlementId(), local.getUpstreamEntitlementId()
                );
            }
            else {
                subscriptionsNeedQuantityMatch.add(subscription);
                log.warn("Subscription for incoming entitlement id [{}] does not have an exact quantity " +
                    "match in the current subscriptions for the upstream pool id [{}]",
                    subscription.getUpstreamEntitlementId(), subscription.getUpstreamPoolId()
                );
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // quantities will just match by position from highest to lowest
        subscriptionsNeedQuantityMatch.sort((lhs, rhs) -> rhs.getQuantity().compareTo(lhs.getQuantity()));

        for (SubscriptionDTO subscription : subscriptionsNeedQuantityMatch) {
            Map<String, Pool> map = existingPoolsByUpstreamPool.get(subscription.getUpstreamPoolId());

            if (map == null || map.isEmpty()) {
                log.info("Creating new subscription for incoming entitlement with id [{}]",
                    subscription.getUpstreamEntitlementId());

                continue;
            }

            Pool local = map.values().stream()
                .max((lhs, rhs) -> lhs.getQuantity().compareTo(rhs.getQuantity()))
                .get();

            this.mergeSubscription(subscription, local, map);
            log.info("Merging subscription for incoming entitlement id [{}] into subscription with " +
                "existing entitlement id [{}] Ordered quantity match.",
                subscription.getUpstreamEntitlementId(), local.getUpstreamEntitlementId());
        }

        return subsToImport;
    }

    /*
     * Maps upstream pool ID to a map of upstream entitlement ID to Subscription.
     */
    private Map<String, Map<String, Pool>> mapPoolsByUpstreamPool(Owner owner) {
        Map<String, Map<String, Pool>> existingSubsByUpstreamPool = new HashMap<>();

        int idx = 0;

        for (Pool p : this.poolCurator.listByOwnerAndTypes(owner.getId(), PoolType.NORMAL)) {
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
            if (p.getUpstreamEntitlementId() == null || p.getUpstreamEntitlementId().trim().equals("")) {
                p.setUpstreamEntitlementId("" + idx++);
            }

            Map<String, Pool> subs = existingSubsByUpstreamPool.get(p.getUpstreamPoolId());
            if (subs == null) {
                subs = new HashMap<>();
                existingSubsByUpstreamPool.put(p.getUpstreamPoolId(), subs);
            }

            subs.put(p.getUpstreamEntitlementId(), p);
        }

        return existingSubsByUpstreamPool;
    }

    private void mergeSubscription(SubscriptionDTO subscription, Pool local, Map<String, Pool> map) {
        subscription.setId(local.getSubscriptionId());
        map.remove(local.getUpstreamEntitlementId());
    }
}
