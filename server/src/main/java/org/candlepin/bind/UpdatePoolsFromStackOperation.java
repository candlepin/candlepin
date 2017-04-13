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
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Basic implementation of BindOperation meant for extension.
 */
public class UpdatePoolsFromStackOperation implements BindOperation {
    private final EntitlementCurator entCurator;
    private final PoolCurator poolCurator;
    private final PoolRules poolRules;
    private final PoolManager poolManager;

    private List<PoolUpdate> poolUpdates;
    private List<Pool> poolsToDelete;

    @Inject
    public UpdatePoolsFromStackOperation(EntitlementCurator entitlementCurator, PoolCurator poolCurator,
        PoolRules poolRules, PoolManager poolManager) {
        this.entCurator = entitlementCurator;
        this.poolCurator = poolCurator;
        this.poolRules = poolRules;
        this.poolManager = poolManager;
        this.poolUpdates = new ArrayList<PoolUpdate>();
        this.poolsToDelete = new ArrayList<Pool>();
    }

    @Override
    public void preProcess(BindContext context, BindChain chain) {
        // Do stuff here
        chain.preProcess(context);
    }

    @Override
    public void acquireLock(BindContext context, BindChain chain) {
        // Does nothing for now
    }

    @Override
    public void execute(BindContext context, BindChain chain) {
        if  (poolUpdates.size() > 0 || poolsToDelete.size() > 0) {
            // for (PoolUpdate update: poolUpdates) {
                // update.apply();
            // }
            if (poolsToDelete.size() > 0) {
                poolManager.deletePools(poolsToDelete);
            }
        }
        chain.execute(context);
    }
}
