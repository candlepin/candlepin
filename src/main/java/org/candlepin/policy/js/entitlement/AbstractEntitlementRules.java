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

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsContext;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.util.DateSource;
import org.mozilla.javascript.RhinoException;
import org.xnap.commons.i18n.I18n;

/**
 * Enforces the Javascript Rules definition.
 */
public abstract class AbstractEntitlementRules implements Enforcer {

    protected Logger log = null;
    protected Logger rulesLogger = null;
    protected DateSource dateSource;

    protected ProductCache productCache;
    protected I18n i18n;
    protected Map<String, Set<Rule>> attributesToRules;
    protected JsRunner jsRules;
    protected Config config;
    protected ConsumerCurator consumerCurator;
    protected PoolCurator poolCurator;

    protected RulesObjectMapper objectMapper = RulesObjectMapper.instance();

    protected static final String PROD_ARCHITECTURE_SEPARATOR = ",";
    protected static final String PRE_PREFIX = "pre_";
    protected static final String POST_PREFIX = "post_";
    protected static final String GLOBAL_PRE_FUNCTION = PRE_PREFIX + "global";
    protected static final String GLOBAL_POST_FUNCTION = POST_PREFIX + "global";

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
                    toReturn.put(attribute,
                        new HashSet<Rule>(Collections.singletonList(rule)));
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
                i18n.tr(
                    "''{0}'' Should contain name, priority and at least one attribute",
                    toParse));
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

    protected void callPreEntitlementRules(List<Rule> matchingRules, JsContext context) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(PRE_PREFIX + rule.getRuleName(), context);
        }
    }

    protected void callPostEntitlementRules(List<Rule> matchingRules) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(POST_PREFIX + rule.getRuleName());
        }
    }

    protected void invokeGlobalPostEntitlementRule(JsContext context) {
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            jsRules.invokeMethod(GLOBAL_POST_FUNCTION, context);
            log.debug("Ran rule: " + GLOBAL_POST_FUNCTION);
        }
        catch (NoSuchMethodException ex) {
            // This is fine, I hope...
            log.warn("No default rule found: " + GLOBAL_POST_FUNCTION);
        }
        catch (RhinoException ex) {
            throw new RuleExecutionException(ex);
        }
    }

    protected void invokeGlobalPreEntitlementRule(JsContext context) {
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            jsRules.invokeMethod(GLOBAL_PRE_FUNCTION, context);
            log.debug("Ran rule: " + GLOBAL_PRE_FUNCTION);
        }
        catch (NoSuchMethodException ex) {
            // This is fine, I hope...
            log.warn("No default rule found: " + GLOBAL_PRE_FUNCTION);
        }
        catch (RhinoException ex) {
            throw new RuleExecutionException(ex);
        }
    }

    protected void callPostUnbindRules(List<Rule> matchingRules) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(POST_PREFIX + rule.getRuleName());
        }
    }

    protected void invokeGlobalPostUnbindRule(JsContext context) {
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            jsRules.invokeMethod(GLOBAL_POST_FUNCTION, context);
            log.debug("Ran rule: " + GLOBAL_POST_FUNCTION);
        }
        catch (NoSuchMethodException ex) {
            // This is fine, I hope...
            log.warn("No default rule found: " + GLOBAL_POST_FUNCTION);
        }
        catch (RhinoException ex) {
            throw new RuleExecutionException(ex);
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
    public static class RuleOrderComparator implements Comparator<Rule>,
        Serializable {
        @Override
        public int compare(Rule o1, Rule o2) {
            return Integer.valueOf(o2.getOrder()).compareTo(
                Integer.valueOf(o1.getOrder()));
        }
    }

    /**
     * Rule
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

    protected void runPostEntitlement(PoolHelper postHelper, Entitlement entitlement) {
        Pool pool = entitlement.getPool();
        Consumer c = entitlement.getConsumer();

        Map<String, String> attributes = postHelper.getFlattenedAttributes(pool);

        // Perform pool management based on the attributes of the pool:
        // TODO: should really be cleaned up, this used to be post rules but
        // because
        // it actually manages pools we pulled back to engine. Needs re-org.
        if (attributes.containsKey("user_license")) {
            postBindUserLicense(postHelper, pool, c, attributes);
        }

        if (attributes.containsKey("virt_limit")) {
            postBindVirtLimit(postHelper, entitlement, pool, c, attributes);
        }
    }

    protected void runPostUnbind(PoolHelper postHelper, Entitlement entitlement) {
        Pool pool = entitlement.getPool();
        Consumer c = entitlement.getConsumer();

        Map<String, String> attributes = postHelper.getFlattenedAttributes(pool);

        if (attributes.containsKey("virt_limit")) {
            postUnbindVirtLimit(postHelper, entitlement, pool, c, attributes);
        }
    }

    private void postUnbindVirtLimit(PoolHelper postHelper,
        Entitlement entitlement, Pool pool, Consumer c,
        Map<String, String> attributes) {
        log.debug("Running virt_limit post unbind.");
        if (!config.standalone() && c.getType().isManifest()) {
            String virtLimit = attributes.get("virt_limit");
            if (!"unlimited".equals(virtLimit)) {
                // As we have unbound an entitlement from a physical pool that
                // was previously
                // exported, we need to add back the reduced bonus pool
                // quantity.
                int virtQuantity = Integer.parseInt(virtLimit) *
                    entitlement.getQuantity();
                if (virtQuantity > 0) {
                    List<Pool> pools = postHelper.lookupBySubscriptionId(pool
                        .getSubscriptionId());
                    for (int idex = 0; idex < pools.size(); idex++) {
                        Pool derivedPool = pools.get(idex);
                        if (derivedPool.getAttributeValue("pool_derived") != null) {
                            postHelper.updatePoolQuantity(derivedPool,
                                virtQuantity);
                        }
                    }
                }
            }
            else {
                // As we have unbound an entitlement from a physical pool that
                // was previously
                // exported, we need to set the unlimited bonus pool quantity to
                // -1.
                List<Pool> pools = postHelper.lookupBySubscriptionId(pool
                    .getSubscriptionId());
                for (int idex = 0; idex < pools.size(); idex++) {
                    Pool derivedPool = pools.get(idex);
                    if (derivedPool.getAttributeValue("pool_derived") != null) {
                        if (derivedPool.getQuantity() == 0) {
                            postHelper.setPoolQuantity(derivedPool, -1);
                        }
                    }
                }
            }
        }
    }

    private void postBindUserLicense(PoolHelper postHelper, Pool pool,
        Consumer c, Map<String, String> attributes) {
        log.debug("Running user_license post-unbind.");
        if (!c.getType().isManifest()) {
            // Default to using the same product from the pool.
            String productId = pool.getProductId();

            // Check if the sub-pool should be for a different product:
            if (attributes.containsKey("user_license_product")) {
                productId = attributes.get("user_license_product");
            }

            // Create a sub-pool for this user
            postHelper.createUserRestrictedPool(productId, pool,
                attributes.get("user_license"));
        }
    }

    private void postBindVirtLimit(PoolHelper postHelper,
        Entitlement entitlement, Pool pool, Consumer c,
        Map<String, String> attributes) {
        log.debug("Running virt_limit post-unbind.");
        if (config.standalone()) {
            String productId = pool.getProductId();
            String virtLimit = attributes.get("virt_limit");
            if ("unlimited".equals(virtLimit)) {
                postHelper.createHostRestrictedPool(productId, pool,
                    "unlimited");
            }
            else {
                int virtQuantity = Integer.parseInt(virtLimit) *
                    entitlement.getQuantity();
                if (virtQuantity > 0) {
                    postHelper.createHostRestrictedPool(productId, pool,
                        String.valueOf(virtQuantity));
                }
            }
        }
        else {
            // if we are exporting we need to deal with the bonus pools
            if (c.getType().isManifest()) {
                String virtLimit = attributes.get("virt_limit");
                if (!"unlimited".equals(virtLimit)) {
                    // if the bonus pool is not unlimited, then the bonus pool
                    // quantity
                    // needs to be adjusted based on the virt limit
                    int virtQuantity = Integer.parseInt(virtLimit) *
                        entitlement.getQuantity();
                    if (virtQuantity > 0) {
                        List<Pool> pools = postHelper
                            .lookupBySubscriptionId(pool.getSubscriptionId());
                        for (int idex = 0; idex < pools.size(); idex++) {
                            Pool derivedPool = pools.get(idex);
                            if (derivedPool.getAttributeValue("pool_derived") != null) {
                                derivedPool = postHelper.updatePoolQuantity(
                                    derivedPool, -1 * virtQuantity);
                            }
                        }
                    }
                }
                else {
                    // if the bonus pool is unlimited, then the quantity needs
                    // to go to 0
                    // when the physical pool is exhausted completely by export.
                    // A quantity of 0 will block future binds, whereas -1 does
                    // not.
                    if (pool.getQuantity().equals(pool.getExported())) {
                        // getting all pools matching the sub id. Filtering out
                        // the 'parent'.
                        List<Pool> pools = postHelper
                            .lookupBySubscriptionId(pool.getSubscriptionId());
                        for (int idex = 0; idex < pools.size(); idex++) {
                            Pool derivedPool = pools.get(idex);
                            if (derivedPool.getAttributeValue("pool_derived") != null) {
                                derivedPool = postHelper.setPoolQuantity(
                                    derivedPool, 0);
                            }
                        }
                    }
                }
            }
        }
    }

    public PoolHelper postEntitlement(Consumer consumer,
        PoolHelper postEntHelper, Entitlement ent) {

        runPostEntitlement(postEntHelper, ent);
        return postEntHelper;
    }

    public PoolHelper postUnbind(Consumer c, PoolHelper postHelper,
        Entitlement ent) {
        runPostUnbind(postHelper, ent);
        return postHelper;
    }

    @Override
    public PreEntHelper preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        return new PreEntHelper(1, null);
    }

}
