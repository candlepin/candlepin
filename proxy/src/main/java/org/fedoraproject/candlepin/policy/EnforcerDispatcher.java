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
package org.fedoraproject.candlepin.policy;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.compliance.ComplianceStatus;
import org.fedoraproject.candlepin.policy.js.entitlement.EntitlementRules;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;

import com.google.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * EnforcerDispatcher
 */
public class EnforcerDispatcher implements Enforcer {

    private ManifestEntitlementRules manifestEnforcer;
    private EntitlementRules jsEnforcer;

    @Inject
    public EnforcerDispatcher(EntitlementRules jsEnforcer,
        ManifestEntitlementRules candlepinEnforcer) {
        this.jsEnforcer = jsEnforcer;
        this.manifestEnforcer = candlepinEnforcer;
    }

    @Override
    public PoolHelper postEntitlement(Consumer consumer, PoolHelper postEntHelper,
        Entitlement ent) {
        if (consumer.getType().isManifest()) {
            return manifestEnforcer.postEntitlement(consumer, postEntHelper, ent);
        }
        return jsEnforcer.postEntitlement(consumer, postEntHelper, ent);
    }

    @Override
    public PreEntHelper preEntitlement(
        Consumer consumer, Pool entitlementPool, Integer quantity) {

        if (consumer.getType().isManifest()) {
            return manifestEnforcer.preEntitlement(consumer, entitlementPool, quantity);
        }

        return jsEnforcer.preEntitlement(consumer, entitlementPool, quantity);
    }

    @Override
    public Map<Pool, Integer> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance)
        throws RuleExecutionException {
        if (consumer.getType().isManifest()) {
            return manifestEnforcer.selectBestPools(consumer, productIds, pools,
                compliance);
        }
        return jsEnforcer.selectBestPools(consumer, productIds, pools, compliance);
    }

}
