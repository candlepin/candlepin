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

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.ReadOnlyConsumer;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProduct;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.compliance.ComplianceStatus;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;

import com.google.inject.Inject;

import org.apache.log4j.Logger;
import org.xnap.commons.i18n.I18n;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CandlepinConsumerTypeEnforcer - Exists primarily to allow consumers of type "candlepin"
 * to skip all rules checks. When transferring to a downstream candlepin we do not want to
 * run any rules checks. (otherwise we would need to add an exemption to every rule)
 */
public class ManifestEntitlementRules extends AbstractEntitlementRules implements Enforcer {

    @Inject
    public ManifestEntitlementRules(DateSource dateSource,
        JsRules jsRules,
        ProductServiceAdapter prodAdapter,
        I18n i18n, Config config, ConsumerCurator consumerCurator) {

        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.prodAdapter = prodAdapter;
        this.i18n = i18n;
        this.attributesToRules = null;
        this.config = config;
        this.consumerCurator = consumerCurator;
        
        log = Logger.getLogger(ManifestEntitlementRules.class);
        rulesLogger =
            Logger.getLogger(ManifestEntitlementRules.class.getCanonicalName() + ".rules");
        
        jsRules.init("entitlement_name_space");
        rulesInit();
    }

    @Override
    public PoolHelper postEntitlement(
            Consumer consumer, PoolHelper postEntHelper, Entitlement ent) {
        runPostEntitlement(postEntHelper, ent);
        return postEntHelper;
    }

    private void runPostEntitlement(PoolHelper postHelper, Entitlement ent) {
        Pool pool = ent.getPool();
        Consumer c = ent.getConsumer();

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        Product product = prodAdapter.getProductById(topLevelProductId);
        Map<String, String> allAttributes = jsRules.getFlattenedAttributes(product, pool);

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(c));
        args.put("product", new ReadOnlyProduct(product));
        args.put("post", postHelper);
        args.put("pool", pool);
        args.put("attributes", allAttributes);
        args.put("log", rulesLogger);
        args.put("standalone", config.standalone());
        args.put("entitlement", ent);

        log.debug("Running post-entitlement rules for: " + c.getUuid() +
            " product: " + topLevelProductId);

        List<Rule> matchingRules
            = rulesForAttributes(allAttributes.keySet(), attributesToRules);

        invokeGlobalPostEntitlementRule(args);
        callPostEntitlementRules(matchingRules);
    }
    
    @Override
    public PreEntHelper preEntitlement(
            Consumer consumer, Pool entitlementPool, Integer quantity) {
        return new PreEntHelper(1, null);
    }

    @Override
    public Map<Pool, Integer> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance)
        throws RuleExecutionException {

        if (pools.isEmpty()) {
            return null;
        }

        Map<Pool, Integer> best = new HashMap<Pool, Integer>();
        for (Pool pool : pools) {
            best.put(pool, 1);
        }
        return best;
    }
    
}
