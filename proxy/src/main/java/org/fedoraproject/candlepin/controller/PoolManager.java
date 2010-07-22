/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

/**
 * PoolManager
 */
public class PoolManager {

    private PoolCurator poolCurator;
    private Log log = LogFactory.getLog(PoolManager.class);
    private SubscriptionServiceAdapter subAdapter;
    private EventSink sink;
    private EventFactory eventFactory;
    private Config config;
    private Entitler entitler;

    /**
     * @param poolCurator
     * @param subAdapter
     * @param sink
     * @param eventFactory
     * @param config
     */
    @Inject
    public PoolManager(PoolCurator poolCurator,
        SubscriptionServiceAdapter subAdapter, EventSink sink,
        EventFactory eventFactory, Config config, Entitler entitler) {
        this.poolCurator = poolCurator;
        this.subAdapter = subAdapter;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.config = config;
        this.entitler = entitler;
    }


    /**
     * Check our underlying subscription service and update the pool data. Note
     * that refreshing the pools doesn't actually take any action, should a subscription
     * be reduced, expired, or revoked. Pre-existing entitlements will need to be dealt
     * with separately from this event.
     *
     * @param owner Owner to be refreshed.
     */
    public void refreshPools(Owner owner) {
        if (log.isDebugEnabled()) {
            log.debug("Refreshing pools");
        }
        List<Subscription> subs = subAdapter.getSubscriptions(owner);

        if (log.isDebugEnabled()) {
            log.debug("Found subscriptions: ");
            for (Subscription sub : subs) {
                log.debug("   " + sub);
            }
        }

        List<Pool> pools = this.poolCurator.listAvailableEntitlementPools(null,
            owner, (String) null, false);

        if (log.isDebugEnabled()) {
            log.debug("Found pools: ");
            for (Pool p : pools) {
                log.debug("   " + p);
            }
        }

        // Map all  pools for this owner/product that have a
        // subscription ID associated with them.
        Map<Long, Pool> subToPoolMap = new HashMap<Long, Pool>();
        for (Pool p : pools) {
            if (p.getSubscriptionId() != null) {
                subToPoolMap.put(p.getSubscriptionId(), p);
            }
        }
        for (Subscription sub : subs) {
            if (!poolExistsForSubscription(subToPoolMap, sub.getId())) {
                this.createPoolForSubscription(sub);
                subToPoolMap.remove(sub.getId());
            }
            else {
                Pool existingPool = subToPoolMap.get(sub.getId());
                updatePoolForSubscription(existingPool, sub);
                subToPoolMap.remove(sub.getId());
            }
        }

        // de-activate pools whose subscription disappeared:
        for (Entry<Long, Pool> entry : subToPoolMap.entrySet()) {
            entitler.deletePool(entry.getValue());
        }
    }

    private void deleteExcessEntitlements(Pool existingPool) {
        boolean lifo = !config
        .getBoolean(ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER);

        if (existingPool.isOverflowing()) {
            // if pool quantity has reduced, then start with revocation.
            Iterator<Entitlement> iter = this.poolCurator
                .retrieveFreeEntitlementsOfPool(existingPool, lifo).iterator();

            long consumed = existingPool.getConsumed();
            while ((consumed > existingPool.getQuantity()) && iter.hasNext()) {
                Entitlement e = iter.next();
                this.entitler.revokeEntitlement(e);
                consumed -= e.getQuantity();
            }
        }
    }

    /**
     * Update pool for subscription. - This method only checks for change in
     * quantity and dates of a subscription. Currently any quantity changes
     * in pool are not handled.
     * @param existingPool the existing pool
     * @param sub the sub
     */
    public void updatePoolForSubscription(Pool existingPool,
        Subscription sub) {
        boolean datesChanged = (!sub.getStartDate().equals(
            existingPool.getStartDate())) ||
            (!sub.getEndDate().equals(existingPool.getEndDate()));
        boolean quantityChanged = !sub.getQuantity().equals(existingPool.getQuantity());

        if (!(quantityChanged || datesChanged)) {
            //TODO: Should we check whether pool is overflowing here?
            return; //no changes, just return.
        }

        Event e = eventFactory.poolChangedFrom(existingPool);
        //quantity has changed. delete any excess entitlements from pool
        if (quantityChanged) {
            existingPool.setQuantity(sub.getQuantity());
            this.deleteExcessEntitlements(existingPool);
        }

        //dates changed. regenerate all entitlement certificates
        if (datesChanged) {
            existingPool.setStartDate(sub.getStartDate());
            existingPool.setEndDate(sub.getEndDate());
            this.entitler.regenerateCertificatesOf(poolCurator
                .retrieveFreeEntitlementsOfPool(existingPool, true));
        }

        //save changes for the pool
        this.poolCurator.merge(existingPool);
        eventFactory.poolChangedTo(e, existingPool);
        sink.sendEvent(e);
    }


    private boolean poolExistsForSubscription(Map<Long, Pool> subToPoolMap,
            Long id) {
        return subToPoolMap.containsKey(id);
    }


    /**
     * @param sub
     */
    public void createPoolForSubscription(Subscription sub) {
        if (log.isDebugEnabled()) {
            log.debug("Creating new pool for new sub: " + sub.getId());
        }
        Long quantity = sub.getQuantity() * sub.getProduct().getMultiplier();
        Set<String> productIds = new HashSet<String>();
        for (Product p : sub.getProvidedProducts()) {
            productIds.add(p.getId());
        }
        Pool newPool = new Pool(sub.getOwner(), sub.getProduct().getId(), productIds,
                quantity, sub.getStartDate(), sub.getEndDate());
        newPool.setSubscriptionId(sub.getId());
        this.poolCurator.create(newPool);
        if (log.isDebugEnabled()) {
            log.debug("   new pool: " + newPool);
        }
    }


    public Pool find(Long poolId) {
        return this.poolCurator.find(poolId);
    }

    public Pool lookupBySubscriptionId(Long id) {
        return this.poolCurator.lookupBySubscriptionId(id);
    }

    public PoolCurator getPoolCurator() {
        return this.poolCurator;
    }

}
