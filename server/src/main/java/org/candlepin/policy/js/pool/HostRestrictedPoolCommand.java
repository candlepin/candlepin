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
package org.candlepin.policy.js.pool;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Pool;

import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 *  Encapsulates the actions necessary to create a host restricted pool.
 */
public class HostRestrictedPoolCommand {
    private List<Pool> poolsToCreate;
    private List<Pool> poolsToUpdateFromStack;
    private PoolManager poolManager;

    public HostRestrictedPoolCommand(PoolManager poolManager) {
        poolsToCreate = new ArrayList<Pool>();
        poolsToUpdateFromStack = new ArrayList<Pool>();
        this.poolManager = poolManager;
    }

    public void addPoolToCreate(Pool p) {
        poolsToCreate.add(p);
    }

    public void addPoolToUpdateFromStack(Pool p) {
        poolsToUpdateFromStack.add(p);
    }

    public List<Pool> execute(Consumer consumer) {
        if (CollectionUtils.isNotEmpty(poolsToUpdateFromStack)) {
            poolManager.updatePoolsFromStack(consumer, poolsToUpdateFromStack);
        }
        return poolManager.createPools(poolsToCreate);
    }
}
