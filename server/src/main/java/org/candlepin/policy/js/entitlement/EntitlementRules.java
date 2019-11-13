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

import org.candlepin.bind.PoolOperationCallback;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.EntitlementDTO;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.util.DateSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Enforces entitlement rules for normal (non-manifest) consumers.
 */
public class EntitlementRules implements Enforcer {
    private static final Logger log = LoggerFactory.getLogger(EntitlementRules.class);

    private DateSource dateSource;

    private I18n i18n;
    private JsRunner jsRules;
    private Configuration config;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductCurator productCurator;
    private RulesObjectMapper objectMapper;
    private ModelTranslator translator;

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRunner jsRules, I18n i18n, Configuration config, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, ProductCurator productCurator, RulesObjectMapper mapper,
        ModelTranslator translator) {

        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.i18n = i18n;
        this.config = config;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productCurator = productCurator;
        this.objectMapper = mapper;
        this.translator = translator;

        jsRules.init("entitlement_name_space");
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool, Integer quantity) {
        return preEntitlement(consumer, entitlementPool, quantity, CallerType.UNKNOWN);
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool, Integer quantity,
        CallerType caller) {
        return preEntitlement(consumer, getHost(consumer), entitlementPool, quantity, caller);
    }

    public ValidationResult preEntitlement(Consumer consumer, Consumer host,
        Pool entitlementPool, Integer quantity, CallerType caller) {

        List<PoolQuantity> poolQuantities = new ArrayList<>();
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
    @SuppressWarnings("checkstyle:indentation")
    public Map<String, ValidationResult> preEntitlement(Consumer consumer, Consumer host,
        Collection<PoolQuantity> entitlementPoolQuantities, CallerType caller) {

        Map<String, ValidationResult> resultMap = new HashMap<>();

        /* This document describes the java script portion of the pre entitlement rules check:
         * http://www.candlepinproject.org/docs/candlepin/pre_entitlement_rules_check.html
         */
        Stream<EntitlementDTO> entStream = consumer.getEntitlements() == null ? Stream.empty() :
            consumer.getEntitlements().stream()
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));


        JsonJsContext args = new JsonJsContext(objectMapper);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("hostConsumer", this.translator.translate(host, ConsumerDTO.class));
        args.put("consumerEntitlements", entStream.collect(Collectors.toSet()));
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

        for (PoolQuantity poolQuantity : entitlementPoolQuantities) {
            finishValidation(resultMap.get(poolQuantity.getPool().getId()),
                poolQuantity.getPool(), poolQuantity.getQuantity());
        }

        return resultMap;
    }

    @Override
    @SuppressWarnings("checkstyle:indentation")
    public List<Pool> filterPools(Consumer consumer, List<Pool> pools, boolean showAll) {
        JsonJsContext args = new JsonJsContext(objectMapper);
        Map<String, ValidationResult> resultMap = new HashMap<>();

        Stream<PoolDTO> poolStream = pools == null ? Stream.empty() :
            pools.stream().map(this.translator.getStreamMapper(Pool.class, PoolDTO.class));

        Stream<EntitlementDTO> entStream = consumer.getEntitlements() == null ? Stream.empty() :
            consumer.getEntitlements().stream()
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("hostConsumer", this.translator.translate(getHost(consumer), ConsumerDTO.class));
        args.put("consumerEntitlements", entStream.collect(Collectors.toSet()));
        args.put("standalone", config.getBoolean(ConfigProperties.STANDALONE));
        args.put("pools", poolStream.collect(Collectors.toSet()));
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

        List<Pool> filteredPools = new LinkedList<>();
        for (Pool pool : pools) {
            ValidationResult result;
            result = resultMap.get(pool.getId());
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
            consumer.getFact("virt.uuid"), consumer.getOwnerId()) : null;
        return host;
    }

    @Override
    public void finishValidation(ValidationResult result, Pool pool, Integer quantity) {
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

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        if (!ctype.isManifest()) {
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

    // Always ensure that we do not over consume.
    // FIXME for auto sub stacking, we need to be able to pull across multiple
    // pools eventually, so this would need to go away in that case
    protected void validatePoolQuantity(ValidationResult result, Pool pool,
        int quantity) {
        if (!pool.entitlementsAvailable(quantity)) {
            result.addError(EntitlementRulesTranslator.PoolErrorKeys.NO_ENTITLEMENTS_AVAILABLE);
        }
    }

    private void postUnbindVirtLimit(PoolManager poolManager, Entitlement entitlement, Pool pool,
        Consumer consumer, Map<String, String> attributes) {

        log.debug("Running virt_limit post unbind.");

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));

        if (!config.getBoolean(ConfigProperties.STANDALONE) && !hostLimited && ctype.isManifest()) {
            // We're making an assumption that VIRT_LIMIT is defined the same way in every possible
            // source for the attributes map.
            String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);

            if (!"unlimited".equals(virtLimit)) {
                // As we have unbound an entitlement from a physical pool that was previously
                // exported, we need to add back the reduced bonus pool quantity.
                int virtQuantity = Integer.parseInt(virtLimit) * entitlement.getQuantity();
                if (virtQuantity > 0) {
                    List<Pool> pools = poolManager.getBySubscriptionId(pool.getOwner(),
                        pool.getSubscriptionId());
                    for (int idex = 0; idex < pools.size(); idex++) {
                        Pool derivedPool = pools.get(idex);
                        if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                            poolManager.setPoolQuantity(derivedPool,
                                derivedPool.adjustQuantity(virtQuantity));
                        }
                    }
                }
            }
            else {
                // As we have unbound an entitlement from a physical pool that
                // was previously
                // exported, we need to set the unlimited bonus pool quantity to
                // -1.
                List<Pool> pools = poolManager.getBySubscriptionId(pool.getOwner(),
                    pool.getSubscriptionId());
                for (int idex = 0; idex < pools.size(); idex++) {
                    Pool derivedPool = pools.get(idex);
                    if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null &&
                        derivedPool.getQuantity() == 0) {

                        poolManager.setPoolQuantity(derivedPool, -1);
                    }
                }
            }
        }
    }

    private PoolOperationCallback postBindVirtLimit(PoolManager poolManager, Consumer consumer,
        Map<String, Entitlement> entitlementMap, Map<String, Map<String, String>> attributeMaps,
        List<Pool> subPoolsForStackIds, boolean isUpdate, Map<String, PoolQuantity> poolQuantityMap) {

        PoolOperationCallback poolOperationCallback = new PoolOperationCallback();
        Set<String> stackIdsThathaveSubPools = new HashSet<>();
        Set<String> alreadyCoveredStackIds = new HashSet<>();
        if (CollectionUtils.isNotEmpty(subPoolsForStackIds)) {
            for (Pool pool : subPoolsForStackIds) {
                stackIdsThathaveSubPools.add(pool.getSourceStackId());
            }
        }

        log.debug("Running virt_limit post-bind.");

        ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);

        boolean consumerFactExpression = !type.isManifest() && !consumer.isGuest();

        boolean isStandalone = config.getBoolean(ConfigProperties.STANDALONE);

        List<Pool> createHostRestrictedPoolFor = new ArrayList<>();
        Map<String, Entitlement> decrementHostedBonusPoolQuantityFor = new HashMap<>();

        for (Entry<String, Entitlement> entry : entitlementMap.entrySet()) {
            Entitlement entitlement = entry.getValue();
            Pool pool = poolQuantityMap.get(entry.getKey()).getPool();
            Map<String, String> attributes = attributeMaps.get(pool.getId());
            boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));

            if (consumerFactExpression && (isStandalone || hostLimited) && !isUpdate) {
                String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);
                String stackId = attributes.get(Product.Attributes.STACKING_ID);

                if (stackId == null ||
                    (!stackIdsThathaveSubPools.contains(stackId) &&
                    !alreadyCoveredStackIds.contains(stackId))) {
                    alreadyCoveredStackIds.add(stackId);
                    log.debug("Creating a new sub-pool for {}", pool);
                    try {
                        int virtQuantity = Integer.parseInt(virtLimit);
                        if (virtQuantity <= 0) {
                            continue;
                        }
                    }
                    catch (NumberFormatException nfe) {
                        if (!"unlimited".equals(virtLimit)) {
                            continue;
                        }
                    }
                    createHostRestrictedPoolFor.add(pool);
                }
                else {
                    log.debug("Skipping sub-pool creation for: {}", pool);
                }
            }
            else {
                decrementHostedBonusPoolQuantityFor.put(entry.getKey(), entitlement);
            }
        }

        if (CollectionUtils.isNotEmpty(createHostRestrictedPoolFor)) {
            log.debug("creating host restricted pools for: {}", createHostRestrictedPoolFor);
            poolOperationCallback.appendCallback(PoolHelper.createHostRestrictedPools(poolManager, consumer,
                createHostRestrictedPoolFor, entitlementMap, attributeMaps, productCurator));
        }

        if (decrementHostedBonusPoolQuantityFor.size() > 0) {
            log.debug("adjustHostedBonusPoolQuantity for: {}", decrementHostedBonusPoolQuantityFor);
            poolOperationCallback.appendCallback(adjustHostedBonusPoolQuantity(poolManager, consumer,
                decrementHostedBonusPoolQuantityFor, attributeMaps, poolQuantityMap, isUpdate));
        }
        return poolOperationCallback;
    }

    /*
     * When distributors/share consumers bind to virt_limit pools in hosted, we need to go adjust the
     * quantity on the bonus pool, as those entitlements have now been exported to on-site or to the share.
     */
    private PoolOperationCallback adjustHostedBonusPoolQuantity(PoolManager poolManager, Consumer consumer,
        Map<String, Entitlement> entitlements, Map<String, Map<String, String>> attributesMaps,
        Map<String, PoolQuantity> poolQuantityMap, boolean isUpdate) {

        PoolOperationCallback poolOperationCallback = new PoolOperationCallback();

        ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);

        boolean consumerFactExpression = type.isManifest() && !config.getBoolean(ConfigProperties
            .STANDALONE);

        if (!consumerFactExpression) {
            return poolOperationCallback;
        }

        // pre-fetch subscription and respective pools in a batch
        Set<String> subscriptionIds = new HashSet<>();
        for (String poolId : entitlements.keySet()) {
            subscriptionIds.add(poolQuantityMap.get(poolId).getPool().getSubscriptionId());
        }

        List<Pool> subscriptionPools = poolManager.getBySubscriptionIds(consumer.getOwnerId(),
            subscriptionIds);
        Map<String, List<Pool>> subscriptionPoolMap = new HashMap<>();

        for (Pool pool : subscriptionPools) {
            if (!subscriptionPoolMap.containsKey(pool.getSubscriptionId())) {
                subscriptionPoolMap.put(pool.getSubscriptionId(), new ArrayList<>());
            }
            subscriptionPoolMap.get(pool.getSubscriptionId()).add(pool);
        }

        for (Entry<String, Entitlement> entry: entitlements.entrySet()) {
            String poolId = entry.getKey();
            Entitlement entitlement = entry.getValue();
            Pool pool = poolQuantityMap.get(poolId).getPool();
            Map<String, String> attributes = attributesMaps.get(pool.getId());

            boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));

            if (!hostLimited) {
                String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);
                if (!"unlimited".equals(virtLimit)) {
                    /* if the bonus pool is not unlimited, then the bonus pool
                     * quantity needs to be adjusted based on the virt limit
                     *
                     * poolQuantity map contains the quantity change requested in the entitlement.
                     * If this is a bind, then change = entitlement quantity, as change is always > 0.
                     * But if this is an entitlement update, change can be positive or negative, hence
                     * we may need to increment or decrement the bonus pool quantity based on the change
                     */
                    int virtQuantity = Integer.parseInt(virtLimit) *
                        poolQuantityMap.get(pool.getId()).getQuantity();
                    if (virtQuantity != 0) {
                        List<Pool> pools = subscriptionPoolMap.get(pool.getSubscriptionId());
                        for (int idex = 0; idex < pools.size(); idex++) {
                            Pool derivedPool = pools.get(idex);
                            if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                                long adjust = derivedPool.adjustQuantity(-1L * virtQuantity);
                                poolOperationCallback.setQuantityToPool(derivedPool, adjust);
                            }
                        }
                    }
                }
                else {
                    // if the bonus pool is unlimited, then the quantity needs
                    // to go to 0 when the physical pool is exhausted completely
                    // by export. A quantity of 0 will block future binds,
                    // whereas -1 does not.
                    Long notConsumedLocally = pool.getExported();

                    // if this is a create, consider the current ent count also
                    if (!isUpdate && (type.isManifest())) {
                        notConsumedLocally += entitlement.getQuantity();
                    }

                    if (pool.getQuantity().equals(notConsumedLocally)) {
                        // getting all pools matching the sub id. Filtering out
                        // the 'parent'.
                        List<Pool> pools = subscriptionPoolMap.get(pool.getSubscriptionId());
                        if (pools != null) {
                            for (int idex = 0; idex < pools.size(); idex++) {
                                Pool derivedPool = pools.get(idex);
                                if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                                    poolOperationCallback.setQuantityToPool(derivedPool, 0);
                                }
                            }
                        }
                    }
                }
            }
        }
        return poolOperationCallback;
    }

    public PoolOperationCallback postEntitlement(PoolManager poolManager, Consumer consumer, Owner owner,
        Map<String, Entitlement> entitlementMap, List<Pool> subPoolsForStackIds, boolean isUpdate,
        Map<String, PoolQuantity> poolQuantityMap) {

        Map<String, Map<String, String>> flatAttributeMaps = new HashMap<>();
        Map<String, Entitlement> virtLimitEntitlements = new HashMap<>();
        PoolOperationCallback poolOperationCallback = new PoolOperationCallback();

        for (Entry<String, Entitlement> entry : entitlementMap.entrySet()) {
            Entitlement entitlement = entry.getValue();
            Pool pool = poolQuantityMap.get(entry.getKey()).getPool();
            Map<String, String> attributes = PoolHelper.getFlattenedAttributes(pool);
            if (attributes.containsKey("virt_limit")) {
                virtLimitEntitlements.put(entry.getKey(), entitlement);
                flatAttributeMaps.put(entry.getKey(), attributes);
            }
        }

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        // Perform pool management based on the attributes of the pool:
        if (!virtLimitEntitlements.isEmpty()) {
            /* manifest consumers only need to compute this method in hosted mode
               because for both these types, of all the operations implemented in this method today,
               we only care about decrementing host bonus pool quantity and that is only implemented
               in hosted mode. These checks are done further below, but doing this up-front to save
                us some computation.
             */
            if (!(ctype.isManifest()) || !config.getBoolean(ConfigProperties.STANDALONE)) {

                poolOperationCallback
                    .appendCallback(postBindVirtLimit(poolManager, consumer, virtLimitEntitlements,
                    flatAttributeMaps, subPoolsForStackIds, isUpdate, poolQuantityMap));
            }
        }

        return poolOperationCallback;
    }

    public void postUnbind(PoolManager poolManager, Entitlement entitlement) {
        Pool pool = entitlement.getPool();

        // Can this attribute appear on pools?
        if (pool.hasAttribute(Product.Attributes.VIRT_LIMIT) ||
            pool.getProduct().hasAttribute(Product.Attributes.VIRT_LIMIT)) {

            Map<String, String> attributes = PoolHelper.getFlattenedAttributes(pool);
            Consumer c = entitlement.getConsumer();
            postUnbindVirtLimit(poolManager, entitlement, pool, c, attributes);
        }
    }
}
