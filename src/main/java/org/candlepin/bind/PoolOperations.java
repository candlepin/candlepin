/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.model.Pool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Holds and represents operations to be performed at a later time.
 * Currently, only holds relevant operations for PostBindBonusPoolsOp
 */
public class PoolOperations {

    private final List<Pool> poolsToCreate = new ArrayList<>();
    private final Map<Pool, Long> poolUpdates = new HashMap<>();

    /**
     * Accepts a pool to be created later
     * @param pool the pool to be created
     */
    public void createPool(Pool pool) {
        poolsToCreate.add(pool);
    }

    /**
     * Accepts a pool and a quantity to set on the pool at a later time
     * @param pool the pool to set the quantity on
     * @param quantity the quantity to set on the pool
     */
    public void updateQuantity(Pool pool, long quantity) {
        poolUpdates.put(pool, quantity);
    }

    /**
     * Adds all pending operations of an existing delayed operation to the current operation
     * @param poolOperations the poolOperationCallback to accept
     */
    public void append(PoolOperations poolOperations) {
        poolsToCreate.addAll(poolOperations.poolsToCreate);
        poolUpdates.putAll(poolOperations.poolUpdates);
    }

    public List<Pool> creations() {
        return this.poolsToCreate;
    }

    public Map<Pool, Long> updates() {
        return this.poolUpdates;
    }

}
