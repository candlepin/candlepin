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
package org.candlepin.policy.js.entitlement;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.util.DateSource;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * Allows manifest consumers to bypass most pre-entitlement checks so subscriptions
 * can be exported to on-site Candlepin servers.
 */
public class ManifestEntitlementRules extends AbstractEntitlementRules implements Enforcer {

    @Inject
    public ManifestEntitlementRules(DateSource dateSource,
        JsRunner jsRules,
        ProductCache productCache,
        I18n i18n, Config config, ConsumerCurator consumerCurator) {

        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.productCache = productCache;
        this.i18n = i18n;
        this.attributesToRules = null;
        this.config = config;
        this.consumerCurator = consumerCurator;

        log = Logger.getLogger(ManifestEntitlementRules.class);
        rulesLogger =
            Logger.getLogger(ManifestEntitlementRules.class.getCanonicalName() + ".rules");
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        return runPreEntitlement(consumer, entitlementPool, quantity);
    }

    /**
     * The standard pre entitlement runs both the global and the attribute rules
     *    Here we have limited it to the global only as the exclusions based on
     *    attribute values do not apply to export scenarios.
     * @param consumer
     * @param pool
     * @param quantity
     * @return
     */
    private ValidationResult runPreEntitlement(Consumer consumer, Pool pool,
        Integer quantity) {

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();

        JsonJsContext args = new JsonJsContext(this.objectMapper);
        args.put("consumer", consumer);
        // Entitlements are put into the context seperately because they do
        // not get serialized along with the Consumer.
        args.put("consumerEntitlements", consumer.getEntitlements());
        args.put("pool", pool);
        args.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        args.put("standalone", config.standalone());

        // Can't serialize these objects.
        args.put("log", rulesLogger, false);

        log.debug("Running pre-entitlement global rule for: " + consumer.getUuid() +
            " product: " + topLevelProductId);

        ValidationResult result = invokeGlobalPreEntitlementRule(args);
        validatePoolQuantity(result, pool, quantity);
        logResult(result);

        return result;
    }
}
