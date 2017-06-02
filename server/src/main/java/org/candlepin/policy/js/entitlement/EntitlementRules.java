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

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductShareCurator;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.util.DateSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;

import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Enforces entitlement rules for normal (non-manifest) consumers.
 */
public class EntitlementRules extends AbstractEntitlementRules implements Enforcer {

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRunner jsRules, I18n i18n, Configuration config, ConsumerCurator consumerCurator,
        PoolCurator poolCurator, ProductCurator productCurator, RulesObjectMapper mapper,
        OwnerCurator ownerCurator, OwnerProductCurator ownerProductCurator,
        ProductShareCurator productShareCurator, ProductManager productManager, EventSink eventSink,
        EventFactory eventFactory) {
        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.i18n = i18n;
        this.attributesToRules = null;
        this.config = config;
        this.consumerCurator = consumerCurator;
        this.poolCurator = poolCurator;
        this.productCurator = productCurator;
        this.objectMapper = mapper;
        this.ownerCurator = ownerCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.shareCurator = productShareCurator;
        this.productManager = productManager;
        this.eventSink = eventSink;
        this.eventFactory = eventFactory;
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
        return preEntitlement(consumer, getHost(consumer), entitlementPool, quantity, caller);
    }

    public ValidationResult preEntitlement(Consumer consumer, Consumer host,
        Pool entitlementPool, Integer quantity, CallerType caller) {
        List<PoolQuantity> poolQuantities = new ArrayList<PoolQuantity>();
        poolQuantities.add(new PoolQuantity(entitlementPool, quantity));
        return preEntitlement(consumer, host, poolQuantities, caller).get(entitlementPool.getId());
    }

    @Override
    public Map<String, ValidationResult> preEntitlement(Consumer consumer,
        Collection<PoolQuantity> entitlementPoolQuantities,
        CallerType caller) {
        return preEntitlement(consumer, getHost(consumer), entitlementPoolQuantities, caller);
    }

    @Override
    public Map<String, ValidationResult> preEntitlement(Consumer consumer, Consumer host,
        Collection<PoolQuantity> entitlementPoolQuantities, CallerType caller) {

        Map<String, ValidationResult> resultMap = new HashMap<String, ValidationResult>();

        /* This document describes the java script portion of the pre entitlement rules check:
         * http://www.candlepinproject.org/docs/candlepin/pre_entitlement_rules_check.html
         * As described in the document, none of the checks are related to share binds, so we
         * skip that step for share consumers.
         */
        if (!consumer.isShare()) {
            JsonJsContext args = new JsonJsContext(objectMapper);
            args.put("consumer", consumer);
            args.put("hostConsumer", host);
            args.put("consumerEntitlements", consumer.getEntitlements());
            args.put("standalone", config.getBoolean(ConfigProperties.STANDALONE));
            args.put("poolQuantities", entitlementPoolQuantities);
            args.put("caller", caller.getLabel());
            args.put("log", log, false);

            String json = jsRules.runJsFunction(String.class, "validate_pools_batch", args);

            TypeReference<Map<String, ValidationResult>> typeref =
                new TypeReference<Map<String, ValidationResult>>() {};
            try {
                resultMap = objectMapper.toObject(json, typeref);
                for (PoolQuantity poolQuantity : entitlementPoolQuantities) {
                    if (!resultMap.containsKey(poolQuantity.getPool().getId())) {
                        resultMap.put(poolQuantity.getPool().getId(), new ValidationResult());
                        log.info("no result returned for pool: {}", poolQuantity.getPool());
                    }

                }
            }
            catch (Exception e) {
                throw new RuleExecutionException(e);
            }
        }

        for (PoolQuantity poolQuantity : entitlementPoolQuantities) {
            if (consumer.isShare()) {
                ValidationResult result = new ValidationResult();
                resultMap.put(poolQuantity.getPool().getId(), result);
                validatePoolSharingEligibility(result, poolQuantity.getPool());
            }
            finishValidation(resultMap.get(poolQuantity.getPool().getId()),
                poolQuantity.getPool(), poolQuantity.getQuantity());
        }

        return resultMap;
    }

    @Override
    public List<Pool> filterPools(Consumer consumer, List<Pool> pools, boolean showAll) {
        JsonJsContext args = new JsonJsContext(objectMapper);
        Map<String, ValidationResult> resultMap = new HashMap<String, ValidationResult>();

        if (!consumer.isShare()) {
            args.put("consumer", consumer);
            args.put("hostConsumer", getHost(consumer));
            args.put("consumerEntitlements", consumer.getEntitlements());
            args.put("standalone", config.getBoolean(ConfigProperties.STANDALONE));
            args.put("pools", pools);
            args.put("caller", CallerType.LIST_POOLS.getLabel());
            args.put("log", log, false);

            String json = jsRules.runJsFunction(String.class, "validate_pools_list", args);
            TypeReference<Map<String, ValidationResult>> typeref =
                new TypeReference<Map<String, ValidationResult>>() {};
            try {
                resultMap = objectMapper.toObject(json, typeref);
            }
            catch (Exception e) {
                throw new RuleExecutionException(e);
            }
        }

        List<Pool> filteredPools = new LinkedList<Pool>();
        for (Pool pool : pools) {
            ValidationResult result;
            if (consumer.isShare()) {
                result = new ValidationResult();
                resultMap.put(pool.getId(), result);
                validatePoolSharingEligibility(result, pool);
            }
            else {
                result = resultMap.get(pool.getId());
            }
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

    private Consumer getHost(Consumer consumer) {
        Consumer host = consumer.hasFact("virt.uuid") ? consumerCurator.getHost(
            consumer.getFact("virt.uuid"), consumer.getOwner()) : null;
        return host;
    }

    private void finishValidation(ValidationResult result, Pool pool, Integer quantity) {
        validatePoolQuantity(result, pool, quantity);
        if (pool.isExpired(dateSource)) {
            result.addError(new ValidationError(i18n.tr("Subscriptions for {0} expired on: {1}",
                pool.getProductId(),
                pool.getEndDate())));
        }
    }

    @Override
    public ValidationResult update(Consumer consumer, Entitlement entitlement, Integer change) {

        ValidationResult result = new ValidationResult();
        if (!consumer.isManifestDistributor() && !consumer.isShare()) {
            Pool pool = entitlement.getPool();
            // multi ent check
            if (!"yes".equalsIgnoreCase(pool.getProductAttributeValue(Pool.Attributes.MULTI_ENTITLEMENT)) &&
                entitlement.getQuantity() + change > 1) {
                result.addError(new ValidationError(
                    EntitlementRulesTranslator.PoolErrorKeys.MULTI_ENTITLEMENT_UNSUPPORTED));
            }
            if (!consumer.isGuest()) {
                String multiplier = pool.getProductAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER);
                if (multiplier != null) {
                    int instanceMultiplier = Integer.parseInt(multiplier);
                    // quantity should be divisible by multiplier
                    if ((entitlement.getQuantity() + change) % instanceMultiplier != 0) {
                        result.addError(new ValidationError(
                            EntitlementRulesTranslator.PoolErrorKeys.QUANTITY_MISMATCH
                        ));
                    }
                }
            }
        }

        finishValidation(result, entitlement.getPool(), change);
        return result;
    }
}
