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

import org.candlepin.audit.Event;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductShare;
import org.candlepin.model.ProductShareCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.ValidationWarning;
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
    private ProductCurator productCurator;
    private RulesObjectMapper objectMapper = null;
    private OwnerCurator ownerCurator;
    private OwnerProductCurator ownerProductCurator;
    private ProductShareCurator shareCurator;
    private ProductManager productManager;
    private EventSink eventSink;
    private EventFactory eventFactory;

    private static final String POST_PREFIX = "post_";

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRunner jsRules, I18n i18n, Configuration config, ConsumerCurator consumerCurator,
        ProductCurator productCurator, RulesObjectMapper mapper,
        OwnerCurator ownerCurator, OwnerProductCurator ownerProductCurator,
        ProductShareCurator productShareCurator, ProductManager productManager, EventSink eventSink,
        EventFactory eventFactory) {
        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.i18n = i18n;
        this.config = config;
        this.consumerCurator = consumerCurator;
        this.productCurator = productCurator;
        this.objectMapper = mapper;
        this.ownerCurator = ownerCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.shareCurator = productShareCurator;
        this.productManager = productManager;
        this.eventSink = eventSink;
        this.eventFactory = eventFactory;
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
        return preEntitlement(
            consumer,
            getHost(consumer, new ArrayList<Pool>(Arrays.asList(entitlementPool))),
            entitlementPool,
            quantity,
            caller);
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
        List<Pool> pools = new ArrayList<Pool>();
        for (PoolQuantity pq : entitlementPoolQuantities) {
            pools.add(pq.getPool());
        }
        return preEntitlement(
            consumer,
            getHost(consumer, pools),
            entitlementPoolQuantities,
            caller);
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
            args.put("hostConsumer", getHost(consumer, pools));
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

    /**
     * Similar to consumerCurator's getHost but here we are ensuring that the owners we search are actually
     * sharing with this consumer.
     *
     * @param consumer
     * @param pools
     * @return
     */
    private Consumer getHost(Consumer consumer, List<Pool> pools) {
        if (!consumer.hasFact("virt.uuid")) {
            return null;
        }
        List<Owner> potentialOwners = new ArrayList<Owner>(Arrays.asList(consumer.getOwner()));
        for (Pool p : pools) {
            if (p.getType() == Pool.PoolType.SHARE_DERIVED) {
                potentialOwners.add(p.getSourceEntitlement().getOwner());
            }
        }

        Consumer host = consumerCurator.getHost(consumer, potentialOwners.toArray(new Owner[] {}));
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

    public List<Rule> rulesForAttributes(Set<String> attributes,
        Map<String, Set<Rule>> rules) {
        Set<Rule> possibleMatches = new HashSet<Rule>();
        for (String attribute : attributes) {
            if (rules.containsKey(attribute)) {
                possibleMatches.addAll(rules.get(attribute));
            }
        }

        List<Rule> matches = new LinkedList<Rule>();
        for (Rule rule : possibleMatches) {
            if (attributes.containsAll(rule.getAttributes())) {
                matches.add(rule);
            }
        }

        // Always run the global rule, and run it first
        matches.add(new Rule("global", 0, new HashSet<String>()));

        Collections.sort(matches, new RuleOrderComparator());
        return matches;
    }

    public Map<String, Set<Rule>> parseAttributeMappings(String mappings) {
        Map<String, Set<Rule>> toReturn = new HashMap<String, Set<Rule>>();
        if (mappings.trim().isEmpty()) {
            return toReturn;
        }

        String[] separatedMappings = mappings.split(",");

        for (String mapping : separatedMappings) {
            Rule rule = parseRule(mapping);
            for (String attribute : rule.getAttributes()) {
                if (!toReturn.containsKey(attribute)) {
                    toReturn.put(attribute, new HashSet<Rule>(Collections.singletonList(rule)));
                }

                toReturn.get(attribute).add(rule);
            }
        }

        return toReturn;
    }

    public Rule parseRule(String toParse) {
        String[] tokens = toParse.split(":");

        if (tokens.length < 3) {
            throw new IllegalArgumentException(
                i18n.tr("''{0}'' Should contain name, priority and at least one attribute", toParse));
        }

        Set<String> attributes = new HashSet<String>();
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

    protected void callPostEntitlementRules(List<Rule> matchingRules) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(POST_PREFIX + rule.getRuleName());
        }
    }

    protected void callPostUnbindRules(List<Rule> matchingRules) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(POST_PREFIX + rule.getRuleName());
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

    protected void validatePoolSharingEligibility(ValidationResult result, Pool pool) {
        if (pool.getType() == Pool.PoolType.UNMAPPED_GUEST) {
            result.addError(EntitlementRulesTranslator.PoolErrorKeys.SHARING_UNMAPPED_GUEST_POOL);
        }
        else if (pool.getType() == Pool.PoolType.DEVELOPMENT) {
            result.addError(EntitlementRulesTranslator.PoolErrorKeys.SHARING_DEVELOPMENT_POOL);
        }
        else if (pool.getType() == Pool.PoolType.SHARE_DERIVED) {
            result.addError(EntitlementRulesTranslator.PoolErrorKeys.SHARING_A_SHARE);
        }
    }

    /**
     * RuleOrderComparator
     */
    public static class RuleOrderComparator implements Comparator<Rule>, Serializable {
        private static final long serialVersionUID = 7602679645721757886L;

        @Override
        public int compare(Rule o1, Rule o2) {
            return Integer.valueOf(o2.getOrder()).compareTo(
                Integer.valueOf(o1.getOrder()));
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

    protected void runPostEntitlement(PoolManager poolManager, Consumer consumer,
        Map<String, Entitlement> entitlementMap, List<Pool> subPoolsForStackIds, boolean isUpdate,
        Map<String, PoolQuantity> poolQuantityMap) {
        Map<String, Map<String, String>> flatAttributeMaps = new HashMap<String, Map<String, String>>();
        Map<String, Entitlement> virtLimitEntitlements = new HashMap<String, Entitlement>();

        for (Entry<String, Entitlement> entry : entitlementMap.entrySet()) {
            Entitlement entitlement = entry.getValue();
            Map<String, String> attributes = PoolHelper.getFlattenedAttributes(entitlement.getPool());
            if (attributes.containsKey("virt_limit")) {
                virtLimitEntitlements.put(entry.getKey(), entitlement);
                flatAttributeMaps.put(entry.getKey(), attributes);
            }
        }
        // Perform pool management based on the attributes of the pool:
        if (!virtLimitEntitlements.isEmpty()) {
            /* Share and manifest consumers only need to compute postBindVirtLimit in hosted mode
               because for both these types, of all the operations implemented in postBindVirtLimit today,
               we only care about decrementing host bonus pool quantity and that is only implemented
               in hosted mode. These checks are done further below, but doing this up-front to save
                us some computation.
             */
            if (!(consumer.isShare() || consumer.isManifestDistributor()) ||
                !config.getBoolean(ConfigProperties.STANDALONE)) {
                postBindVirtLimit(poolManager, consumer, virtLimitEntitlements, flatAttributeMaps,
                    subPoolsForStackIds, isUpdate, poolQuantityMap);
            }
        }

        if (consumer.isShare() && !isUpdate) {
            postBindShareCreate(poolManager, consumer, entitlementMap);
        }
    }

    protected void runPostUnbind(PoolManager poolManager, Entitlement entitlement) {
        Pool pool = entitlement.getPool();

        // Can this attribute appear on pools?
        if (pool.hasAttribute(Product.Attributes.VIRT_LIMIT) ||
            pool.getProduct().hasAttribute(Product.Attributes.VIRT_LIMIT)) {

            Map<String, String> attributes = PoolHelper.getFlattenedAttributes(pool);
            Consumer c = entitlement.getConsumer();
            postUnbindVirtLimit(poolManager, entitlement, pool, c, attributes);
        }
    }

    private void postUnbindVirtLimit(PoolManager poolManager, Entitlement entitlement, Pool pool, Consumer c,
        Map<String, String> attributes) {

        log.debug("Running virt_limit post unbind.");

        boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));

        if (!config.getBoolean(ConfigProperties.STANDALONE) && !hostLimited && c.isManifestDistributor()) {
            // We're making an assumption that VIRT_LIMIT is defined the same way in every possible
            // source for the attributes map.
            String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);

            if (!"unlimited".equals(virtLimit)) {
                // As we have unbound an entitlement from a physical pool that was previously
                // exported, we need to add back the reduced bonus pool quantity.
                int virtQuantity = Integer.parseInt(virtLimit) * entitlement.getQuantity();
                if (virtQuantity > 0) {
                    List<Pool> pools = poolManager.lookupBySubscriptionId(pool.getOwner(),
                        pool.getSubscriptionId());
                    for (int idex = 0; idex < pools.size(); idex++) {
                        Pool derivedPool = pools.get(idex);
                        if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                            poolManager.updatePoolQuantity(derivedPool, virtQuantity);
                        }
                    }
                }
            }
            else {
                // As we have unbound an entitlement from a physical pool that
                // was previously
                // exported, we need to set the unlimited bonus pool quantity to
                // -1.
                List<Pool> pools = poolManager.lookupBySubscriptionId(pool.getOwner(),
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

    private void postBindShareCreate(PoolManager poolManager, Consumer c,
        Map<String, Entitlement> entitlementMap) {
        log.debug("Running post-bind share create");

        Owner sharingOwner = c.getOwner();
        Owner recipient = ownerCurator.lookupByKey(c.getRecipientOwnerKey());
        List<Pool> sharedPoolsToCreate = new ArrayList<Pool>();

        for (Entitlement entitlement: entitlementMap.values()) {
            Pool sourcePool = entitlement.getPool();
            // resolve and copy all products
            // Handle any product creation/manipulation incurred by the share action
            Set<Product> allProducts = new HashSet<Product>();
            allProducts.add(sourcePool.getProduct());
            if (sourcePool.getProvidedProducts() != null) {
                allProducts.addAll(sourcePool.getProvidedProducts());
            }
            if (sourcePool.getDerivedProduct() != null) {
                allProducts.add(sourcePool.getDerivedProduct());
            }
            if (sourcePool.getDerivedProvidedProducts() != null) {
                allProducts.addAll(sourcePool.getDerivedProvidedProducts());
            }
            Map<String, Product> resolvedProducts =
                resolveProductShares(sharingOwner, recipient, allProducts);
            Product product = resolvedProducts.get(sourcePool.getProduct().getId());

            Set<Product> providedProducts = copySetFromResolved(sourcePool.getProvidedProducts(),
                resolvedProducts);
            Long q = Long.valueOf(entitlement.getQuantity());
            // endDateOverride doesnt really apply here , this is just for posterity.
            Date endDate = (entitlement.getEndDateOverride() == null) ?
                sourcePool.getEndDate() : entitlement.getEndDateOverride();
            Pool sharedPool = new Pool(
                recipient,
                product,
                providedProducts,
                q,
                sourcePool.getStartDate(),
                endDate,
                sourcePool.getContractNumber(),
                sourcePool.getAccountNumber(),
                sourcePool.getOrderNumber()
            );
            if (sourcePool.getDerivedProduct() != null) {
                Product derivedProduct = resolvedProducts.get(sourcePool.getDerivedProduct().getId());
                sharedPool.setDerivedProduct(derivedProduct);
            }
            Set<Product> derivedProvidedProducts = copySetFromResolved(
                sourcePool.getDerivedProvidedProducts(), resolvedProducts);
            sharedPool.setDerivedProvidedProducts(derivedProvidedProducts);

            if (entitlement != null && entitlement.getPool() != null) {
                sharedPool.setSourceEntitlement(entitlement);
            }

            /* Since we set the source entitlement id as the subscription sub key for
               entitlement derived pools, it makes sense to do the same for share pools,
               as share pools are also entitlement derived, sort of.
             */
            sharedPool.setSourceSubscription(
                new SourceSubscription(sourcePool.getSubscriptionId(), entitlement.getId()));

            // Copy the pool's attributes
            for (Entry<String, String> entry : sourcePool.getAttributes().entrySet()) {
                sharedPool.setAttribute(entry.getKey(), entry.getValue());
            }
            sharedPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
            sharedPool.setAttribute(Pool.Attributes.SHARE, "true");

            for (Branding b : sourcePool.getBranding()) {
                sharedPool.getBranding().add(new Branding(b.getProductId(), b.getType(),
                    b.getName()));
            }
            sharedPoolsToCreate.add(sharedPool);
        }

        /* TODO Create temporary guest pool in OrgB */

        if (CollectionUtils.isNotEmpty(sharedPoolsToCreate)) {
            poolManager.createPools(sharedPoolsToCreate);
        }
    }

    private Set<Product> copySetFromResolved(Set<Product> products, Map<String, Product> resolvedProducts) {
        Set<Product> result = new HashSet<Product>();
        if (products != null) {
            for (Product product : products) {
                result.add(resolvedProducts.get(product.getId()));
            }
        }
        return result;
    }

    private Map<String, Product> resolveProductShares(Owner sharingOwner, Owner recipient,
        Set<Product> products) {
        Map<String, Product> sharedProductsIdMap = new HashMap<String, Product>();
        Map<String, Product> sharedProductsUuidMap = new HashMap<String, Product>();
        Map<String, Product> resolvedProducts = new HashMap<String, Product>();
        List<Event> events = new LinkedList<Event>();
        List<ProductShare> sharesToDelete = new LinkedList<ProductShare>();
        List<ProductShare> sharesToCreate = new LinkedList<ProductShare>();
        Map<String, ProductShare> existingSharesMap = new HashMap<String, ProductShare>();

        for (Product product: products) {
            sharedProductsIdMap.put(product.getId(), product);
            sharedProductsUuidMap.put(product.getUuid(), product);
        }
        List<Product> recipientProducts = ownerProductCurator.getProductsByIds(
            recipient, sharedProductsIdMap.keySet()).list();

        for (Product product: recipientProducts) {
            Map<String, Product> conflictedRecipientProducts = new HashMap<String, Product>();
            if (sharedProductsUuidMap.containsKey(product.getUuid())) {
                // Recipient has a product with the same ID already.  If they are the same instance
                // use then nothing needs doing.  Everything is already in place.
                resolvedProducts.put(product.getId(), product);
                log.debug("Owner {} has the same product {} as the sharer {}",
                    recipient.getKey(), product.getId(), sharingOwner.getKey());
            }
            else {
                // The recipient and owner have two products with the same ID but they are different
                // instances since their uuids do not match.
                conflictedRecipientProducts.put(product.getId(), product);
            }

            if (conflictedRecipientProducts.size() > 0) {
                List<ProductShare> existingShares = shareCurator.findProductSharesByRecipient(
                    recipient, conflictedRecipientProducts.keySet());

                for (ProductShare pShare: existingShares) {
                    existingSharesMap.put(pShare.getProduct().getId(), pShare);
                }
            }

            for (String id: conflictedRecipientProducts.keySet()) {
                if (!existingSharesMap.containsKey(id)) {
                    // If the recipient's product isn't from a share, let the recipient just continue to
                    // use its existing product definition.
                    log.debug("Owner {} already has product {} defined that is not a share",
                        recipient.getKey(), id);
                    resolvedProducts.put(id, conflictedRecipientProducts.get(id));
                }
                else {
                    // If the recipient's product is a share then two owners are sharing into the same
                    // recipient and we must resolve the conflict.
                    Product sharingOwnerProduct = sharedProductsIdMap.get(id);
                    Product existingProduct = conflictedRecipientProducts.get(id);
                    ProductShare existingShare = existingSharesMap.get(id);
                    log.debug("Owner {} already has a share for Product {} from owner {}. Solving conflict.",
                        recipient.getKey(), id, existingShare.getOwner());

                    EventBuilder builder = eventFactory.getEventBuilder(
                        Event.Target.PRODUCT, Event.Type.MODIFIED);
                    builder.setOldEntity(existingProduct);
                    sharesToDelete.add(existingShare);
                    sharesToCreate.add(new ProductShare(sharingOwner, sharingOwnerProduct, recipient));
                    // Now we need to reconcile all of recipient's pools that were using the old product.
                    Product resolvedProduct = productManager.updateProduct(
                        sharingOwnerProduct.toDTO(), recipient, true);
                    builder.setNewEntity(resolvedProduct);
                    resolvedProducts.put(resolvedProduct.getId(), resolvedProduct);
                    events.add(builder.buildEvent());
                }
            }

        }

        Set<String> idsNonExisting = new HashSet<String>(sharedProductsIdMap.keySet());
        idsNonExisting.removeAll(resolvedProducts.keySet());

        for (String id: idsNonExisting) {
            // The recipient doesn't have a definition for the product at all.  Link the recipient
            // and product and create a record of a share.
            log.debug("Linking product {} from owner {} to owner {} as product share",
                id, sharingOwner.getKey(), recipient.getKey());
            Product sharedProduct = sharedProductsIdMap.get(id);
            ownerProductCurator.mapProductToOwner(sharedProduct, recipient);
            sharesToCreate.add(new ProductShare(sharingOwner, sharedProduct, recipient));
            resolvedProducts.put(id, sharedProduct);
        }

        shareCurator.bulkDelete(sharesToDelete);
        shareCurator.saveOrUpdateAll(sharesToCreate, false, false);
        for (Event event: events) {
            eventSink.queueEvent(event);
        }
        return resolvedProducts;
    }

    private void postBindVirtLimit(PoolManager poolManager, Consumer c,
        Map<String, Entitlement> entitlementMap, Map<String, Map<String, String>> attributeMaps,
        List<Pool> subPoolsForStackIds, boolean isUpdate, Map<String, PoolQuantity> poolQuantityMap) {

        Set<String> stackIdsThathaveSubPools = new HashSet<String>();
        Set<String> alreadyCoveredStackIds = new HashSet<String>();
        if (CollectionUtils.isNotEmpty(subPoolsForStackIds)) {
            for (Pool pool : subPoolsForStackIds) {
                stackIdsThathaveSubPools.add(pool.getSourceStackId());
            }
        }

        log.debug("Running virt_limit post-bind.");

        boolean consumerFactExpression = !c.isManifestDistributor() && !c.isShare() && !c.isGuest();

        boolean isStandalone = config.getBoolean(ConfigProperties.STANDALONE);

        List<Pool> createHostRestrictedPoolFor = new ArrayList<Pool>();
        List<Entitlement> decrementHostedBonusPoolQuantityFor = new ArrayList<Entitlement>();

        for (Entitlement entitlement: entitlementMap.values()) {
            Pool pool = entitlement.getPool();
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
                decrementHostedBonusPoolQuantityFor.add(entitlement);
            }
        }

        // Share consumers do not have host restricted pools
        if (CollectionUtils.isNotEmpty(createHostRestrictedPoolFor) && !c.isShare()) {
            log.debug("creating host restricted pools for: {}", createHostRestrictedPoolFor);
            PoolHelper.createHostRestrictedPools(poolManager, c, createHostRestrictedPoolFor, entitlementMap,
                attributeMaps, productCurator);
        }

        if (CollectionUtils.isNotEmpty(decrementHostedBonusPoolQuantityFor)) {
            log.debug("adjustHostedBonusPoolQuantity for: {}", decrementHostedBonusPoolQuantityFor);
            adjustHostedBonusPoolQuantity(poolManager, c, decrementHostedBonusPoolQuantityFor,
                attributeMaps, poolQuantityMap);
        }
    }

    /*
     * When distributors/share consumers bind to virt_limit pools in hosted, we need to go adjust the
     * quantity on the bonus pool, as those entitlements have now been exported to on-site or to the share.
     */
    private void adjustHostedBonusPoolQuantity(PoolManager poolManager, Consumer c,
        List<Entitlement> entitlements, Map<String, Map<String, String>> attributesMaps,
        Map<String, PoolQuantity> poolQuantityMap) {
        boolean consumerFactExpression = (c.isManifestDistributor() || c.isShare()) &&
            !config.getBoolean(ConfigProperties.STANDALONE);

        if (!consumerFactExpression) {
            return;
        }

        // pre-fetch subscription and respective pools in a batch
        Set<String> subscriptionIds = new HashSet<String>();
        for (Entitlement entitlement : entitlements) {
            subscriptionIds.add(entitlement.getPool().getSubscriptionId());
        }
        List<Pool> subscriptionPools = poolManager.lookupBySubscriptionIds(c.getOwner(), subscriptionIds);
        Map<String, List<Pool>> subscriptionPoolMap = new HashMap<String, List<Pool>>();
        for (Pool pool : subscriptionPools) {
            if (!subscriptionPoolMap.containsKey(pool.getSubscriptionId())) {
                subscriptionPoolMap.put(pool.getSubscriptionId(), new ArrayList<Pool>());
            }
            subscriptionPoolMap.get(pool.getSubscriptionId()).add(pool);
        }

        for (Entitlement entitlement : entitlements) {
            Pool pool = entitlement.getPool();
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
                                poolManager.updatePoolQuantity(derivedPool, -1 * virtQuantity);
                            }
                        }
                    }
                }
                else {
                    // if the bonus pool is unlimited, then the quantity needs
                    // to go to 0 when the physical pool is exhausted completely
                    // by export or share. A quantity of 0 will block future binds,
                    // whereas -1 does not.
                    Long notConsumedLocally = pool.getExported() + pool.getShared();
                    if (pool.getQuantity().equals(notConsumedLocally)) {
                        // getting all pools matching the sub id. Filtering out
                        // the 'parent'.
                        List<Pool> pools = subscriptionPoolMap.get(pool.getSubscriptionId());
                        if (pools != null) {
                            for (int idex = 0; idex < pools.size(); idex++) {
                                Pool derivedPool = pools.get(idex);
                                if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                                    derivedPool = poolManager.setPoolQuantity(derivedPool, 0);
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    public void postEntitlement(PoolManager poolManager, Consumer consumer,
        Map<String, Entitlement> entitlements, List<Pool> subPoolsForStackIds, boolean isUpdate,
        Map<String, PoolQuantity> poolQuantityMap) {
        runPostEntitlement(poolManager,
            consumer,
            entitlements,
            subPoolsForStackIds,
            isUpdate,
            poolQuantityMap);
    }

    public void postUnbind(Consumer c, PoolManager poolManager, Entitlement ent) {
        runPostUnbind(poolManager, ent);
    }

    protected void logResult(ValidationResult result) {
        if (log.isDebugEnabled()) {
            for (ValidationError error : result.getErrors()) {
                log.debug("  Rule error: {}", error.getResourceKey());
            }

            for (ValidationWarning warning : result.getWarnings()) {
                log.debug("  Rule warning: {}", warning.getResourceKey());
            }
        }
    }
}
