/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.bind;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.SourceSubscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Holds and represents operations to be performed at a later time.
 * Currently, only holds relevant operations for PostBindBonusPoolsOp
 */
public class PoolOperationCallback {

    private List<Pool> poolsToCreate = new ArrayList<>();
    private Map<Pool, Long> poolUpdates = new HashMap<>();
    private Map<Pool, String> poolSubscriptionIds = new HashMap<>();
    private Map<Pool, Entitlement> poolEntitlements = new HashMap<>();

    public List<Pool> getPoolCreates() {
        return poolsToCreate;
    }

    public Map<Pool, Long> getPoolUpdates() {
        return poolUpdates;
    }

    public Map<Pool, String> getPoolSubscriptionIds() {
        return poolSubscriptionIds;
    }

    public Map<Pool, Entitlement> getPoolEntitlements() {
        return poolEntitlements;
    }

    /**
     * Accepts a pool to be created later
     * @param pool the pool to be created
     */
    public void addPoolToCreate(Pool pool) {
        poolsToCreate.add(pool);
    }

    /**
     * Accepts a pool and a quantity to set on the pool at a later time
     * @param pool the pool to set the quantity on
     * @param quantity the quantity to set on the pool
     */
    public void setQuantityToPool(Pool pool, long quantity) {
        poolUpdates.put(pool, quantity);
    }

    /**
     * Adds all pending operations of an existing delayed operation to the current operation
     * @param poolOperationCallback the poolOperationCallback to accept
     */
    public void appendCallback(PoolOperationCallback poolOperationCallback) {
        Map<Pool, String> incomingSubscriptionIds = poolOperationCallback.getPoolSubscriptionIds();
        poolSubscriptionIds.putAll(incomingSubscriptionIds);
        Map<Pool, Entitlement> incomingEntitlementMap = poolOperationCallback.getPoolEntitlements();
        poolEntitlements.putAll(incomingEntitlementMap);

        List<Pool> poolCreates = poolOperationCallback.getPoolCreates();
        poolsToCreate.addAll(poolOperationCallback.getPoolCreates());

        for (Map.Entry<Pool, Long> poolUpdate: poolOperationCallback.getPoolUpdates().entrySet()) {
            poolUpdates.put(poolUpdate.getKey(), poolUpdate.getValue());
        }
    }

    /**
     * Accepts a pool and a source subscription to be created later.
     * @param pool the pool to set the source subscription on
     * @param subscriptionId the subscription id of the source subscription
     * @param entitlement the entitlement for the subscription sub key
     */
    public void createSourceSubscription(Pool pool, String subscriptionId, Entitlement entitlement) {
        poolSubscriptionIds.put(pool, subscriptionId);
        poolEntitlements.put(pool, entitlement);
    }

    /**
     * Perform the operations marked.
     * @param poolManager
     */
    public void apply(PoolManager poolManager) {

        for (Entry<Pool, String> entry: poolSubscriptionIds.entrySet()) {
            Entitlement entitlement = poolEntitlements.get(entry.getKey());
            String subscriptionId = entry.getValue();
            entry.getKey().setSourceSubscription(new SourceSubscription(subscriptionId, entitlement.getId()));
        }
        poolManager.createPools(poolsToCreate);
        poolManager.setPoolQuantity(poolUpdates);
    }
}
