/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.bind.PoolOperations;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.EntitlementDTO;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.dto.rules.v1.PoolQuantityDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.util.DateSource;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.core.type.TypeReference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;


/**
 * Enforces entitlement rules for normal (non-manifest) consumers.
 */
public class EntitlementRules implements Enforcer {
    private static final Logger log = LoggerFactory.getLogger(EntitlementRules.class);
    private static final long UNLIMITED_QUANTITY = -1L;

    private final DateSource dateSource;
    private final I18n i18n;
    private final JsRunner jsRules;
    private final Configuration config;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final RulesObjectMapper objectMapper;
    private final ModelTranslator translator;
    private final PoolService poolService;

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRunner jsRules, I18n i18n, Configuration config, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, RulesObjectMapper mapper, ModelTranslator translator,
        PoolService poolService) {

        this.jsRules = Objects.requireNonNull(jsRules);
        this.dateSource = Objects.requireNonNull(dateSource);
        this.i18n = Objects.requireNonNull(i18n);
        this.config = Objects.requireNonNull(config);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.objectMapper = Objects.requireNonNull(mapper);
        this.translator = Objects.requireNonNull(translator);
        this.poolService = Objects.requireNonNull(poolService);

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

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        /* This document describes the java script portion of the pre entitlement rules check:
         * http://www.candlepinproject.org/docs/candlepin/pre_entitlement_rules_check.html
         */
        Stream<EntitlementDTO> entStream = consumer.getEntitlements() == null ? Stream.empty() :
            consumer.getEntitlements().stream()
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        Stream<PoolQuantityDTO> quantityStream = entitlementPoolQuantities == null ? Stream.empty() :
            entitlementPoolQuantities.stream()
                .map(this.translator.getStreamMapper(PoolQuantity.class, PoolQuantityDTO.class));

        JsonJsContext args = new JsonJsContext(objectMapper);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("hostConsumer", this.translator.translate(host, ConsumerDTO.class));
        args.put("consumerEntitlements", entStream.collect(Collectors.toSet()));
        args.put("standalone", config.getBoolean(ConfigProperties.STANDALONE));
        args.put("poolQuantities", quantityStream);
        args.put("caller", caller.getLabel());
        args.put("log", log, false);

        Map<String, ValidationResult> resultMap;
        String json = jsRules.runJsFunction(String.class, "validate_pools_batch", args);
        TypeReference<Map<String, ValidationResult>> typeref = new TypeReference<>() {};
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

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

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

        Map<String, ValidationResult> resultMap;
        String json = jsRules.runJsFunction(String.class, "validate_pools_list", args);
        TypeReference<Map<String, ValidationResult>> typeref = new TypeReference<>() {};

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
                log.debug("Omitting pool due to failed rules: {}", pool.getId());
                if (result.hasErrors()) {
                    log.debug("\tErrors: {}", result.getErrors());
                }
                if (result.hasWarnings()) {
                    log.debug("\tWarnings: {}", result.getWarnings());
                }
            }
        }

        return filteredPools;
    }

    private Consumer getHost(Consumer consumer) {
        if (!consumer.hasFact(Consumer.Facts.VIRT_UUID)) {
            return null;
        }
        return consumerCurator.getHost(consumer.getFact(Consumer.Facts.VIRT_UUID), consumer.getOwnerId());
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
            if (!"yes".equalsIgnoreCase(pool.getProductAttributes().get(Pool.Attributes.MULTI_ENTITLEMENT)) &&
                entitlement.getQuantity() + change > 1) {
                result.addError(new ValidationError(
                    EntitlementRulesTranslator.PoolErrorKeys.MULTI_ENTITLEMENT_UNSUPPORTED));
            }
            if (!consumer.isGuest()) {
                String multiplier = pool.getProductAttributes().get(Product.Attributes.INSTANCE_MULTIPLIER);
                if (multiplier != null) {
                    int instanceMultiplier = Integer.parseInt(multiplier);
                    // quantity should be divisible by multiplier
                    if ((entitlement.getQuantity() + change) % instanceMultiplier != 0) {
                        result.addError(new ValidationError(
                            EntitlementRulesTranslator.PoolErrorKeys.QUANTITY_MISMATCH));
                    }
                }
            }
        }

        finishValidation(result, entitlement.getPool(), change);
        return result;
    }

    public List<Rule> rulesForAttributes(Set<String> attributes,
        Map<String, Set<Rule>> rules) {
        Set<Rule> possibleMatches = new HashSet<>();
        for (String attribute : attributes) {
            if (rules.containsKey(attribute)) {
                possibleMatches.addAll(rules.get(attribute));
            }
        }

        List<Rule> matches = new LinkedList<>();
        for (Rule rule : possibleMatches) {
            if (attributes.containsAll(rule.getAttributes())) {
                matches.add(rule);
            }
        }

        // Always run the global rule, and run it first
        matches.add(new Rule("global", 0, new HashSet<>()));

        Collections.sort(matches, new RuleOrderComparator());
        return matches;
    }

    public Map<String, Set<Rule>> parseAttributeMappings(String mappings) {
        Map<String, Set<Rule>> toReturn = new HashMap<>();
        if (mappings.trim().isEmpty()) {
            return toReturn;
        }

        String[] separatedMappings = mappings.split(",");

        for (String mapping : separatedMappings) {
            Rule rule = parseRule(mapping);
            for (String attribute : rule.getAttributes()) {
                toReturn.computeIfAbsent(attribute, k -> new HashSet<>()).add(rule);
            }
        }

        return toReturn;
    }

    public Rule parseRule(String toParse) {
        String[] tokens = toParse.split(":");

        if (tokens.length < 3) {
            throw new IllegalArgumentException(
                i18n.tr("\"{0}\" Should contain name, priority and at least one attribute", toParse));
        }

        Set<String> attributes = new HashSet<>();
        for (int i = 2; i < tokens.length; i++) {
            attributes.add(tokens[i].trim());
        }

        try {
            return new Rule(tokens[0].trim(), Integer.parseInt(tokens[1]),
                attributes);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(i18n.tr(
                "second parameter should be the priority number.", e));
        }
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

    /**
     * RuleOrderComparator
     */
    public static class RuleOrderComparator implements Comparator<Rule>, Serializable {
        private static final long serialVersionUID = 7602679645721757886L;

        @Override
        public int compare(Rule o1, Rule o2) {
            return Integer.compare(o2.getOrder(), o1.getOrder());
        }
    }

    /**
     * Rule represents a core concept in Candlepin which is a business rule used
     * to determine system compliance as well as entitlement eligibility for a
     * particular consumer.
     */
    public static class Rule {
        private final String ruleName;
        private final int order;
        private final Set<String> attributes;

        public Rule(String ruleName, int order, Set<String> attributes) {
            this.ruleName = ruleName;
            this.order = order;
            this.attributes = attributes;
        }

        public String getRuleName() {
            return ruleName;
        }

        public int getOrder() {
            return order;
        }

        public Set<String> getAttributes() {
            return attributes;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result +
                ((attributes == null) ? 0 : attributes.hashCode());
            result = prime * result + order;
            result = prime * result +
                ((ruleName == null) ? 0 : ruleName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            Rule other = (Rule) obj;
            if (attributes == null) {
                if (other.attributes != null) {
                    return false;
                }
            }
            else if (!attributes.equals(other.attributes)) {
                return false;
            }
            if (order != other.order) {
                return false;
            }
            if (ruleName == null) {
                if (other.ruleName != null) {
                    return false;
                }
            }
            else if (!ruleName.equals(other.ruleName)) {
                return false;
            }
            return true;
        }

        public String toString() {
            return "'" + ruleName + "':" + order + ":" + attributes.toString();
        }
    }

    private PoolOperations postBindVirtLimit(Consumer consumer,
        Map<String, Entitlement> entitlementMap, Map<String, Map<String, String>> attributeMaps,
        List<Pool> subPoolsForStackIds, boolean isUpdate, Map<String, PoolQuantity> poolQuantityMap) {

        PoolOperations poolOperations = new PoolOperations();
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
            poolOperations.append(PoolHelper.createHostRestrictedPools(this.poolService, consumer,
                createHostRestrictedPoolFor, entitlementMap, attributeMaps));
        }

        if (decrementHostedBonusPoolQuantityFor.size() > 0) {
            log.debug("adjustHostedBonusPoolQuantity for: {}", decrementHostedBonusPoolQuantityFor);
            poolOperations.append(adjustHostedBonusPoolQuantity(consumer,
                decrementHostedBonusPoolQuantityFor, attributeMaps, poolQuantityMap, isUpdate));
        }
        return poolOperations;
    }

    /*
     * When distributors/share consumers bind to virt_limit pools in hosted, we need to go adjust the
     * quantity on the bonus pool, as those entitlements have now been exported to on-site or to the share.
     */
    private PoolOperations adjustHostedBonusPoolQuantity(Consumer consumer,
        Map<String, Entitlement> entitlements, Map<String, Map<String, String>> attributesMaps,
        Map<String, PoolQuantity> poolQuantityMap, boolean isUpdate) {

        PoolOperations poolOperations = new PoolOperations();

        ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);

        boolean consumerFactExpression = type.isManifest() && !config.getBoolean(ConfigProperties.STANDALONE);

        if (!consumerFactExpression) {
            return poolOperations;
        }

        // pre-fetch subscription and respective pools in a batch
        Set<String> subscriptionIds = new HashSet<>();
        for (String poolId : entitlements.keySet()) {
            subscriptionIds.add(poolQuantityMap.get(poolId).getPool().getSubscriptionId());
        }

        List<Pool> subscriptionPools = this.poolService.getBySubscriptionIds(consumer.getOwnerId(),
            subscriptionIds);
        Map<String, List<Pool>> subscriptionPoolMap = new HashMap<>();

        for (Pool pool : subscriptionPools) {
            if (!subscriptionPoolMap.containsKey(pool.getSubscriptionId())) {
                subscriptionPoolMap.put(pool.getSubscriptionId(), new ArrayList<>());
            }
            subscriptionPoolMap.get(pool.getSubscriptionId()).add(pool);
        }

        for (Entry<String, Entitlement> entry : entitlements.entrySet()) {
            String poolId = entry.getKey();
            Entitlement entitlement = entry.getValue();
            Pool pool = poolQuantityMap.get(poolId).getPool();
            Map<String, String> attributes = attributesMaps.get(pool.getId());

            boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));

            if (!hostLimited) {
                String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);
                if (!"unlimited".equals(virtLimit)) {
                    /*
                     * Case I
                     * if the bonus pool is not unlimited, then the bonus pool
                     * quantity needs to be adjusted based on the virt limit
                     *
                     * NOTE : poolQuantity map contains the quantity change requested in the entitlement.
                     * If this is a bind, then change = entitlement quantity, as change is always > 0.
                     * But if this is an entitlement update, change can be positive or negative, hence
                     * we may need to increment or decrement the bonus pool quantity based on the change
                     *
                     * Case II
                     * Primary pool quantity is unlimited, with non-zero virt_limit & pool under
                     * consideration is of type Unmapped guest or Bonus pool, set its quantity to be
                     * unlimited.
                     */
                    int virtQuantity = Integer.parseInt(virtLimit) *
                        poolQuantityMap.get(pool.getId()).getQuantity();
                    if (virtQuantity != 0) {
                        List<Pool> pools = subscriptionPoolMap.get(pool.getSubscriptionId());

                        boolean isPrimaryPoolUnlimited = isPrimaryPoolUnlimited(pools);

                        for (Pool derivedPool : pools) {
                            if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                                // If primary pool is of unlimited quantity, set pool quantity as unlimited
                                // if pool type is bonus or unmapped_guest pool.
                                if (isPrimaryPoolUnlimited && (derivedPool.getType() == Pool.PoolType.BONUS ||
                                    derivedPool.getType() == Pool.PoolType.UNMAPPED_GUEST)) {
                                    poolOperations.updateQuantity(derivedPool, UNLIMITED_QUANTITY);
                                }
                                else {
                                    long adjust = derivedPool.adjustQuantity(-1L * virtQuantity);
                                    poolOperations.updateQuantity(derivedPool, adjust);
                                }
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

                    // getting all pools matching the sub id. Filtering out
                    // the 'parent'.
                    List<Pool> pools = subscriptionPoolMap.getOrDefault(pool.getSubscriptionId(),
                        new ArrayList<>());
                    final Long exportCount = notConsumedLocally;
                    pools.stream()
                        .filter(thisPool -> thisPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null)
                        .forEach(thisPool -> poolOperations.updateQuantity(thisPool,
                            pool.getQuantity().equals(exportCount) ? 0 : -1));
                }
            }
        }
        return poolOperations;
    }

    public PoolOperations postEntitlement(Consumer consumer,
        Map<String, Entitlement> entitlementMap, List<Pool> subPoolsForStackIds, boolean isUpdate,
        Map<String, PoolQuantity> poolQuantityMap) {

        Map<String, Map<String, String>> flatAttributeMaps = new HashMap<>();
        Map<String, Entitlement> virtLimitEntitlements = new HashMap<>();
        PoolOperations poolOperations = new PoolOperations();

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
                poolOperations
                    .append(postBindVirtLimit(consumer, virtLimitEntitlements,
                        flatAttributeMaps, subPoolsForStackIds, isUpdate, poolQuantityMap));
            }
        }

        return poolOperations;
    }

    private boolean isPrimaryPoolUnlimited(List<Pool> pools) {
        return pools.stream()
            .map(Pool::getQuantity)
            .anyMatch(quantity -> quantity == UNLIMITED_QUANTITY);
    }

}
