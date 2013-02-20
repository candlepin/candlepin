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

import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.AttributeHelper;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.util.DateSource;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * Enforces entitlement rules for normal (non-manifest) consumers.
 */
public class EntitlementRules extends AbstractEntitlementRules implements Enforcer {

    @Inject
    public EntitlementRules(DateSource dateSource,
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

        log = Logger.getLogger(EntitlementRules.class);
        rulesLogger =
            Logger.getLogger(EntitlementRules.class.getCanonicalName() + ".rules");
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        ValidationResult result = runPreEntitlement(consumer, entitlementPool, quantity);
        validatePoolQuantity(result, entitlementPool, quantity);

        if (entitlementPool.isExpired(dateSource)) {
            result.addError(
                new ValidationError(i18n.tr("Subscriptions for {0} expired on: {1}",
                    entitlementPool.getProductId(),
                    entitlementPool.getEndDate())));
        }

        return result;
    }

    private ValidationResult runPreEntitlement(Consumer consumer, Pool pool,
        Integer quantity) {
        // Provide objects for the script:
        JsonJsContext context = new JsonJsContext(this.objectMapper);
        context.put("consumer", consumer);
        // Entitlements are put into the context seperately because they do
        // not get serialized along with the Consumer.
        context.put("consumerEntitlements", consumer.getEntitlements());
        context.put("pool", pool);
        context.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        context.put("standalone", config.standalone());
        context.put("quantity", quantity);

        // If the consumer is a guest, the rules may require the host
        // consumer.
        if (consumer.hasFact("virt.uuid")) {
            String guestUuid = consumer.getFact("virt.uuid");
            context.put("hostConsumer", consumerCurator.getHost(guestUuid));
        }

        // Add all non-serializable objects to the context.
        context.put("log", rulesLogger, false);

        log.debug("Running pre-entitlement rules for: " + consumer.getUuid() +
            " product: " + pool.getProductId());

        // Determine all rules to run based on the pool's attributes.
        AttributeHelper attributeHelper = new AttributeHelper();
        Set<String> attributeNames = attributeHelper.getFlattenedAttributes(pool).keySet();
        List<Rule> matchingRules = rulesForAttributes(attributeNames, attributesToRules);

        ValidationResult result = callPreEntitlementRules(matchingRules, context);
        logResult(result);

        return result;
    }

}
