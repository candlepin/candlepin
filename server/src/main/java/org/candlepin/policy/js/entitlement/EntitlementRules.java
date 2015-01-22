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

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.util.DateSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;

import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Enforces entitlement rules for normal (non-manifest) consumers.
 */
public class EntitlementRules extends AbstractEntitlementRules implements Enforcer {

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRunner jsRules,
        ProductCache productCache,
        I18n i18n, Configuration config, ConsumerCurator consumerCurator,
        PoolCurator poolCurator) {

        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.productCache = productCache;
        this.i18n = i18n;
        this.attributesToRules = null;
        this.config = config;
        this.consumerCurator = consumerCurator;
        this.poolCurator = poolCurator;

        log = LoggerFactory.getLogger(EntitlementRules.class);
        jsRules.init("entitlement_name_space");
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {
        return preEntitlement(consumer, entitlementPool, quantity, CallerType.UNKNOWN);
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity, CallerType caller) {
        Consumer host = consumer.hasFact("virt.uuid") ?
                consumerCurator.getHost(consumer.getFact("virt.uuid"),
                        consumer.getOwner()) : null;
        return preEntitlement(consumer, host, entitlementPool, quantity, caller);
    }

    public ValidationResult preEntitlement(Consumer consumer, Consumer host,
        Pool entitlementPool, Integer quantity, CallerType caller) {

        JsonJsContext args = new JsonJsContext(objectMapper);
        args.put("consumer", consumer);
        args.put("hostConsumer", host);
        args.put("consumerEntitlements", consumer.getEntitlements());
        args.put("standalone", config.getBoolean(ConfigProperties.STANDALONE));
        args.put("pool", entitlementPool);
        args.put("quantity", quantity);
        args.put("caller", caller.getLabel());
        args.put("log", log, false);
        args.put("newborn", consumer.isNewborn());

        String json = jsRules.runJsFunction(String.class, "validate_pool", args);
        ValidationResult result;
        try {
            result = objectMapper.toObject(json, ValidationResult.class);
            finishValidation(result, entitlementPool, quantity);
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }

        return result;
    }

    @Override
    public List<Pool> filterPools(Consumer consumer, List<Pool> pools, boolean showAll) {
        JsonJsContext args = new JsonJsContext(objectMapper);
        args.put("consumer", consumer);
        args.put("hostConsumer", consumer.hasFact("virt.uuid") ?
            this.consumerCurator.getHost(consumer.getFact("virt.uuid"),
                consumer.getOwner()) : null);
        args.put("consumerEntitlements", consumer.getEntitlements());
        args.put("standalone", config.getBoolean(ConfigProperties.STANDALONE));
        args.put("pools", pools);
        args.put("caller", CallerType.LIST_POOLS.getLabel());
        args.put("log", log, false);
        args.put("newborn", consumer.isNewborn());

        String json = jsRules.runJsFunction(String.class, "validate_pools_list", args);
        Map<String, ValidationResult> resultMap;
        TypeReference<Map<String, ValidationResult>> typeref =
            new TypeReference<Map<String, ValidationResult>>() {};
        try {
            resultMap = objectMapper.toObject(json, typeref);
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }

        List<Pool> filteredPools = new LinkedList<Pool>();
        for (Pool pool : pools) {
            ValidationResult result = resultMap.get(pool.getId());
            finishValidation(result, pool, 1);

            if (result.isSuccessful() && (!result.hasWarnings() || showAll)) {
                filteredPools.add(pool);
            }
            else if (log.isDebugEnabled()) {
                log.debug("Omitting pool due to failed rules: " + pool.getId());
                if (result.hasErrors()) {
                    log.debug("\tErrors: " + result.getErrors());
                }
                if (result.hasWarnings()) {
                    log.debug("\tWarnings: " + result.getWarnings());
                }
            }
        }
        return filteredPools;
    }

    private void finishValidation(ValidationResult result, Pool pool, Integer quantity) {
        validatePoolQuantity(result, pool, quantity);
        if (pool.isExpired(dateSource)) {
            result.addError(
                new ValidationError(i18n.tr("Subscriptions for {0} expired on: {1}",
                    pool.getProductId(),
                    pool.getEndDate())));
        }
    }
}
