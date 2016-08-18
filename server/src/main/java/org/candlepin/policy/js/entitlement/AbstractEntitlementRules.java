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
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.ValidationWarning;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.util.DateSource;

import org.apache.commons.collections.CollectionUtils;
import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Enforces the Javascript Rules definition.
 */
public abstract class AbstractEntitlementRules implements Enforcer {

    protected Logger log = null;
    protected DateSource dateSource;

    protected I18n i18n;
    protected Map<String, Set<Rule>> attributesToRules;
    protected JsRunner jsRules;
    protected Configuration config;
    protected ConsumerCurator consumerCurator;
    protected PoolCurator poolCurator;

    protected RulesObjectMapper objectMapper = RulesObjectMapper.instance();

    protected static final String POST_PREFIX = "post_";

    protected void rulesInit() {
        String mappings;
        try {
            mappings = jsRules.invokeMethod("attribute_mappings");
            this.attributesToRules = parseAttributeMappings(mappings);
        }
        catch (RhinoException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
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
            result.addError("rulefailed.no.entitlements.available");
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
        Map<String, Entitlement> entitlementMap, List<Pool> subPoolsForStackIds) {
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
        postBindVirtLimit(poolManager, consumer, virtLimitEntitlements, flatAttributeMaps,
            subPoolsForStackIds);
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

        if (!config.getBoolean(ConfigProperties.STANDALONE) && !hostLimited && c.getType().isManifest()) {
            // We're making an assumption that VIRT_LIMIT is defined the same way in every possible
            // source for the attributes map.
            String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);

            if (!"unlimited".equals(virtLimit)) {
                // As we have unbound an entitlement from a physical pool that was previously
                // exported, we need to add back the reduced bonus pool quantity.
                int virtQuantity = Integer.parseInt(virtLimit) * entitlement.getQuantity();
                if (virtQuantity > 0) {
                    List<Pool> pools = poolManager.lookupBySubscriptionId(pool.getSubscriptionId());
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
                List<Pool> pools = poolManager.lookupBySubscriptionId(pool.getSubscriptionId());
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

    private void postBindVirtLimit(PoolManager poolManager, Consumer c,
        Map<String, Entitlement> entitlementMap, Map<String, Map<String, String>> attributeMaps,
        List<Pool> subPoolsForStackIds) {

        Set<String> stackIdsThathaveSubPools = new HashSet<String>();
        if (CollectionUtils.isNotEmpty(subPoolsForStackIds)) {
            for (Pool pool : subPoolsForStackIds) {
                stackIdsThathaveSubPools.add(pool.getSourceStackId());
            }
        }

        log.debug("Running virt_limit post-bind.");

        boolean consumerFactExpression = !c.getType().isManifest() &&
            !"true".equalsIgnoreCase(c.getFact("virt.is_guest"));

        boolean isStandalone = config.getBoolean(ConfigProperties.STANDALONE);

        List<Pool> createHostRestrictedPoolFor = new ArrayList<Pool>();
        List<Entitlement> decrementHostedBonusPoolQuantityFor = new ArrayList<Entitlement>();

        for (Entitlement entitlement : entitlementMap.values()) {
            Pool pool = entitlement.getPool();
            Map<String, String> attributes = attributeMaps.get(pool.getId());
            boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));

            if (consumerFactExpression && (isStandalone || hostLimited)) {
                String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);
                String stackId = attributes.get(Product.Attributes.STACKING_ID);

                if (stackId == null || !stackIdsThathaveSubPools.contains(stackId)) {
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

        if (CollectionUtils.isNotEmpty(createHostRestrictedPoolFor)) {
            log.debug("creating host restricted pools for: {}", createHostRestrictedPoolFor);
            PoolHelper.createHostRestrictedPools(poolManager, c, createHostRestrictedPoolFor, entitlementMap,
                attributeMaps);
        }
        if (CollectionUtils.isNotEmpty(decrementHostedBonusPoolQuantityFor)) {
            log.debug("decrementHostedBonusPoolQuantity for: {}", decrementHostedBonusPoolQuantityFor);
            decrementHostedBonusPoolQuantity(poolManager, c, decrementHostedBonusPoolQuantityFor,
                attributeMaps);
        }
    }

    /*
     * When distributors bind to virt_limit pools in hosted, we need to go adjust the
     * quantity on the bonus pool, as those entitlements have now been exported to on-site.
     */
    private void decrementHostedBonusPoolQuantity(PoolManager poolManager, Consumer c,
        List<Entitlement> entitlements, Map<String, Map<String, String>> attributesMaps) {
        boolean consumerFactExpression = c.getType().isManifest() &&
            !config.getBoolean(ConfigProperties.STANDALONE);

        // pre-fetch subscription and respective pools in a batch
        Set<String> subscriptionIds = new HashSet<String>();
        for (Entitlement entitlement : entitlements) {
            subscriptionIds.add(entitlement.getPool().getSubscriptionId());
        }
        List<Pool> subscriptionPools = poolManager.lookupBySubscriptionIds(subscriptionIds);
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

            if (consumerFactExpression && !hostLimited) {
                String virtLimit = attributes.get(Product.Attributes.VIRT_LIMIT);
                if (!"unlimited".equals(virtLimit)) {
                    // if the bonus pool is not unlimited, then the bonus pool
                    // quantity needs to be adjusted based on the virt limit
                    int virtQuantity = Integer.parseInt(virtLimit) * entitlement.getQuantity();
                    if (virtQuantity > 0) {
                        List<Pool> pools = subscriptionPoolMap.get(pool.getSubscriptionId());
                        for (int idex = 0; idex < pools.size(); idex++) {
                            Pool derivedPool = pools.get(idex);
                            if (derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL) != null) {
                                derivedPool = poolManager.updatePoolQuantity(derivedPool, -1 * virtQuantity);
                            }
                        }
                    }
                }
                else {
                    // if the bonus pool is unlimited, then the quantity needs
                    // to go to 0 when the physical pool is exhausted completely
                    // by export. A quantity of 0 will block future binds,
                    // whereas -1 does not.
                    if (pool.getQuantity().equals(pool.getExported())) {
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
        Map<String, Entitlement> entitlements, List<Pool> subPoolsForStackIds) {
        runPostEntitlement(poolManager, consumer, entitlements, subPoolsForStackIds);
    }

    public void postUnbind(Consumer c, PoolManager poolManager, Entitlement ent) {
        runPostUnbind(poolManager, ent);
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {
        jsRules.reinitTo("entitlement_name_space");
        rulesInit();
        return new ValidationResult();
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
