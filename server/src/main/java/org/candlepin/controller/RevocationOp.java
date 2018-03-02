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
package org.candlepin.controller;

import com.google.inject.persist.Transactional;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.policy.EntitlementRefusedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * Created by wpoteat on 5/11/17.
 */
public class RevocationOp {
    private List<Pool> pools;
    private Set<Entitlement> entitlementsToRevoke;
    private Map<Entitlement, Long> shareEntitlementsToAdjust;
    private Map<Pool, List<Pool>> sharedPools;
    private Map<Pool, Long> poolNewConsumed;
    private PoolCurator poolCurator;
    private ConsumerTypeCurator consumerTypeCurator;

    public RevocationOp(PoolCurator poolCurator, ConsumerTypeCurator consumerTypeCurator, List<Pool> pools) {
        entitlementsToRevoke = new HashSet<>();
        shareEntitlementsToAdjust = new HashMap<>();
        sharedPools = new HashMap<>();
        poolNewConsumed = new HashMap<>();

        this.poolCurator = poolCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.pools = pools;
    }

    public void addEntitlmentToRevoke(Entitlement entitlement) {
        entitlementsToRevoke.add(entitlement);
    }

    public Set<Entitlement> getEntitlementsToRevoke() {
        return entitlementsToRevoke;
    }

    public void addEntitlementToAdjust(Entitlement entitlement, long quantity) {
        shareEntitlementsToAdjust.put(entitlement, quantity);
    }

    public void addEntitlementToRevoke(Entitlement entitlement) {
        entitlementsToRevoke.add(entitlement);
    }

    public Map<Entitlement, Long> getShareEntitlementsToAdjust() {
        return shareEntitlementsToAdjust;
    }

    public void setPoolCurator(PoolCurator poolCurator) {
        this.setPoolCurator(poolCurator);
    }

    @Transactional
    public void execute(PoolManager poolManager) {
        Collection<Pool> overflowing = new ArrayList<>();
        for (Pool pool : pools) {
            if (pool.isOverflowing()) {
                overflowing.add(pool);
            }
        }
        if (overflowing.isEmpty()) {
            return;
        }
        overflowing = poolCurator.lockAndLoad(overflowing);
        for (Pool pool : overflowing) {
            poolNewConsumed.put(pool, pool.getConsumed());
            List<Pool> shared = poolCurator.listSharedPoolsOf(pool);
            if (!shared.isEmpty()) {
                sharedPools.put(pool, shared);
                // first determine shared pool counts where allotted units are not in use
                reduceSharedPools(pool);
            }
            // we then start revoking the existing entitlements
            determineExcessEntitlements(pool);
        }
        // revoke the entitlements amassed above
        poolManager.revokeEntitlements(new ArrayList<>(entitlementsToRevoke));
        // here is where we actually change the source entitlement quantities for the shared pools.
        // We have to wait until we get here so that share pool entitlements we want revoked are gone
        for (Entitlement entitlement : shareEntitlementsToAdjust.keySet()) {
            try {
                poolManager.adjustEntitlementQuantity(entitlement.getConsumer(),
                    entitlement, shareEntitlementsToAdjust.get(entitlement).intValue());
            }
            catch (EntitlementRefusedException e) {
                // TODO: Could be multiple errors, but we'll just report the first one for now:
                throw new ForbiddenException(e.getResults().values().iterator().next().getErrors()
                        .get(0).toString());
            }
        }

    }

    /**
     * The first part of the adjustment is to reduce the shared pool source entitlements for counts
     *  that are not in use as entitlements from the shared pool.
     *
     * @param pool
     * @return boolean
     */
    private void reduceSharedPools(Pool pool) {
        long over = pool.getConsumed() - pool.getQuantity();
        long newConsumed = poolNewConsumed.get(pool);
        for (Pool sPool : sharedPools.get(pool)) {
            if (over > 0) {
                long excessCount = sPool.getQuantity() - sPool.getConsumed();
                long newQuantity;
                if (excessCount > 0) {
                    Entitlement shareEnt = sPool.getSourceEntitlement();
                    if (over >= excessCount) {
                        newQuantity = sPool.getQuantity() - excessCount;
                        over = over - excessCount;
                        newConsumed -= excessCount;
                    }
                    else {
                        newQuantity = shareEnt.getQuantity() - over;
                        newConsumed -= over;
                        over = 0L;
                    }
                    addEntitlementToAdjust(shareEnt, newQuantity);
                }
            }
        }
        poolNewConsumed.put(pool, newConsumed);
    }

    /**
     * In the second part, the list of entitlements is compiled from the main pool plus the shared pools
     * It is sorted in the order of LIFO. Entitlements are put in the revoke list until the count is
     * acceptable for the main pool. Any entitlements that came from a shared pool are also reflected in
     * the adjustment for the source entitlement for that shared pool.
     *
     * @param pool
     */
    private void determineExcessEntitlements(Pool pool) {
        List<Pool> pools = new ArrayList<>();
        pools.add(pool);
        if (sharedPools.get(pool) != null) {
            pools.addAll(sharedPools.get(pool));
        }
        List<Entitlement> entitlements = this.poolCurator.retrieveOrderedEntitlementsOf(pools);
        long newConsumed = poolNewConsumed.get(pool);

        long existing = pool.getQuantity();
        for (Entitlement ent : entitlements) {
            if (newConsumed > existing) {
                ConsumerType ctype = this.consumerTypeCurator.getConsumerType(ent.getConsumer());

                if (!ctype.isType(ConsumerTypeEnum.SHARE)) {
                    if (ent.getPool().isCreatedByShare()) {
                        Entitlement source = ent.getPool().getSourceEntitlement();
                        // the source entitlement may have already been adjusted in the shared pool reduction
                        if (shareEntitlementsToAdjust.get(source) == null) {
                            addEntitlementToAdjust(source, source.getQuantity());
                        }
                        addEntitlementToAdjust(source, shareEntitlementsToAdjust.get(source) -
                            ent.getQuantity());
                        if (shareEntitlementsToAdjust.get(source) == 0) {
                            shareEntitlementsToAdjust.remove(source);
                            addEntitlmentToRevoke(source);
                        }
                    }
                    addEntitlementToRevoke(ent);
                    newConsumed -= ent.getQuantity();
                }
            }
        }
        poolNewConsumed.put(pool, newConsumed);
    }
}
