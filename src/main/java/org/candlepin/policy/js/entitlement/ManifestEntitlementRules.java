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

import java.util.Map;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationWarning;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.ReadOnlyConsumer;
import org.candlepin.policy.js.ReadOnlyPool;
import org.candlepin.policy.js.ReadOnlyProduct;
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
    public PreEntHelper preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        PreEntHelper preHelper = runPreEntitlement(consumer, entitlementPool,
            quantity);

        return preHelper;
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
    private PreEntHelper runPreEntitlement(Consumer consumer, Pool pool, Integer quantity) {
        PreEntHelper preHelper = new PreEntHelper(quantity, consumerCurator);

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        ReadOnlyProduct product = new ReadOnlyProduct(topLevelProductId,
            pool.getProductName(),
            preHelper.getFlattenedAttributes(pool.getProductAttributes()));
        Map<String, String> allAttributes = preHelper.getFlattenedAttributes(pool);

        JsonJsContext args = new JsonJsContext(this.objectMapper);
        args.put("consumer", consumer);
        // Entitlements are put into the context seperately because they do
        // not get serialized along with the Consumer.
        // TODO Perhaps look into a Jackson view to do this.
        args.put("consumerEntitlements", consumer.getEntitlements());
        args.put("product", product);
        args.put("pool", pool);
        args.put("attributes", allAttributes);
        args.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        args.put("standalone", config.standalone());

        // Can't serialize these objects.
        args.put("log", rulesLogger, false);
        args.put("pre", preHelper, false);

        log.debug("Running pre-entitlement global rule for: " + consumer.getUuid() +
            " product: " + topLevelProductId);

        invokeGlobalPreEntitlementRule(args);

        validatePoolQuantity(preHelper.getResult(), pool, quantity);

        if (log.isDebugEnabled()) {
            for (ValidationError error : preHelper.getResult().getErrors()) {
                log.debug("  Rule error: " + error.getResourceKey());
            }
            for (ValidationWarning warning : preHelper.getResult().getWarnings()) {
                log.debug("  Rule warning: " + warning.getResourceKey());
            }
        }

        return preHelper;
    }
}
