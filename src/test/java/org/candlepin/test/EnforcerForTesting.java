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
package org.candlepin.test;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.Enforcer;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.PreEntHelper;
import org.candlepin.policy.js.entitlement.PreUnbindHelper;
import org.candlepin.policy.js.pool.PoolHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * EnforcerForTesting
 */
public class EnforcerForTesting implements Enforcer {

    @Override
    public PoolHelper postEntitlement(
            Consumer consumer, PoolHelper postEntHelper, Entitlement ent) {
        return postEntHelper;
    }

    @Override
    public PreEntHelper preEntitlement(
            Consumer consumer, Pool enitlementPool, Integer quantity) {
        return new PreEntHelper(1, null);
    }

    @Override
    public List<PreEntHelper> preEntitlement(
        Consumer consumer, List<Pool> entitlementPools, Integer quantity) {

        ArrayList<PreEntHelper> helpers = new ArrayList<PreEntHelper>();
        for (Pool pool : entitlementPools) {
            helpers.add(preEntitlement(consumer, pool, quantity));
        }
        return helpers;
    }

    @Override
    public List<PoolQuantity> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance, String serviceLevelOverride,
        Set<String> exemptList)
        throws RuleExecutionException {
        if (pools.isEmpty()) {
            return null;
        }

        List<PoolQuantity> best = new ArrayList<PoolQuantity>();
        for (Pool pool : pools) {
            best.add(new PoolQuantity(pool, 1));
        }
        return best;
    }

    public PreUnbindHelper preUnbind(Consumer consumer, Pool entitlementPool) {
        return new PreUnbindHelper(null);
    }

    public PoolHelper postUnbind(Consumer consumer, PoolHelper postEntHelper,
            Entitlement ent) {
        return postEntHelper;
    }
}
