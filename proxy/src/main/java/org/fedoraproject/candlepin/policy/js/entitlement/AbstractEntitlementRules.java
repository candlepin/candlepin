/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.policy.js.entitlement;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.ReadOnlyConsumer;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProduct;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.compliance.ComplianceStatus;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;

import org.apache.log4j.Logger;
import org.mozilla.javascript.RhinoException;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the Javascript Rules definition.
 */
public abstract class AbstractEntitlementRules implements Enforcer {

    protected Logger log = null;
    protected Logger rulesLogger = null;
    protected DateSource dateSource;

    protected ProductServiceAdapter prodAdapter;
    protected I18n i18n;
    protected Map<String, Set<Rule>> attributesToRules;
    protected JsRules jsRules;
    protected Config config;
    protected ConsumerCurator consumerCurator;

    protected static final String PROD_ARCHITECTURE_SEPARATOR = ",";
    protected static final String PRE_PREFIX = "pre_";
    protected static final String POST_PREFIX = "post_";
    protected static final String SELECT_POOL_PREFIX = "select_pool_";
    protected static final String GLOBAL_SELECT_POOL_FUNCTION = SELECT_POOL_PREFIX +
        "global";
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

    /**
     * Default behavior if no product specific and no global pool select rules
     * exist.
     *
     * @param pools
     *            Pools to choose from.
     * @return First pool in the list. (default behavior)
     */
    protected Map<Pool, Integer> selectBestPoolDefault(List<Pool> pools) {
        if (pools.size() > 0) {
            Map<Pool, Integer> toReturn = new HashMap<Pool, Integer>();
            for (Pool pool : pools) {
                toReturn.put(pool, 1);
            }
            return toReturn;
        }

        return null;
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
                    toParse)
            );
        }

        Set<String> attributes = new HashSet<String>();
        for (int i = 2; i < tokens.length; i++) {
            attributes.add(tokens[i].trim());
        }

        try {
            return new Rule(tokens[0].trim(), Integer.parseInt(tokens[1]), attributes);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                i18n.tr("second parameter should be the priority number.", e));
        }
    }

    protected void callPreEntitlementRules(List<Rule> matchingRules,
        Map<String, Object> args) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(PRE_PREFIX + rule.getRuleName(), args);
        }
    }

    protected void callPostEntitlementRules(List<Rule> matchingRules) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(POST_PREFIX + rule.getRuleName());
        }
    }

    protected void invokeGlobalPostEntitlementRule(Map<String, Object> args) {
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            jsRules.invokeMethod(GLOBAL_POST_FUNCTION, args);
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

    protected void invokeGlobalPreEntitlementRule(Map<String, Object> args) {
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            jsRules.invokeMethod(GLOBAL_PRE_FUNCTION, args);
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

    protected void invokeGlobalPostUnbindRule(Map<String, Object> args) {
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            jsRules.invokeMethod(GLOBAL_POST_FUNCTION, args);
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


    /**
     * RuleOrderComparator
     */
    public static class RuleOrderComparator implements Comparator<Rule>, Serializable {
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

    protected void runPostEntitlement(PoolHelper postHelper, Entitlement ent) {
        Pool pool = ent.getPool();
        Consumer c = ent.getConsumer();

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        Product product = prodAdapter.getProductById(topLevelProductId);
        Map<String, String> allAttributes = jsRules.getFlattenedAttributes(product, pool);

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(c));
        args.put("product", new ReadOnlyProduct(product));
        args.put("post", postHelper);
        args.put("pool", pool);
        args.put("attributes", allAttributes);
        args.put("log", rulesLogger);
        args.put("standalone", config.standalone());
        args.put("entitlement", ent);

        log.debug("Running post-entitlement rules for: " + c.getUuid() +
            " product: " + topLevelProductId);

        List<Rule> matchingRules
            = rulesForAttributes(allAttributes.keySet(), attributesToRules);

        invokeGlobalPostEntitlementRule(args);
        callPostEntitlementRules(matchingRules);
    }

    protected void runPostUnbind(PoolHelper postHelper, Entitlement ent) {
        Pool pool = ent.getPool();
        Consumer c = ent.getConsumer();

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        Product product = prodAdapter.getProductById(topLevelProductId);
        Map<String, String> allAttributes = jsRules.getFlattenedAttributes(product, pool);

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(c));
        args.put("product", new ReadOnlyProduct(product));
        args.put("post", postHelper);
        args.put("pool", pool);
        args.put("attributes", allAttributes);
        args.put("log", rulesLogger);
        args.put("standalone", config.standalone());
        args.put("entitlement", ent);

        log.debug("Running post-unbind rules for: " + c.getUuid() +
            " product: " + topLevelProductId);

        List<Rule> matchingRules
            = rulesForAttributes(allAttributes.keySet(), attributesToRules);

        invokeGlobalPostUnbindRule(args);
        callPostUnbindRules(matchingRules);
    }

    public PoolHelper postEntitlement(
            Consumer consumer, PoolHelper postEntHelper, Entitlement ent) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        runPostEntitlement(postEntHelper, ent);
        return postEntHelper;
    }

    public PreUnbindHelper preUnbind(Consumer consumer, Pool entitlementPool) {
        jsRules.reinitTo("unbind_name_space");
        rulesInit();
        return new PreUnbindHelper(consumerCurator);
    }

    public PoolHelper postUnbind(Consumer c, PoolHelper postHelper, Entitlement ent) {
        jsRules.reinitTo("unbind_name_space");
        rulesInit();
        runPostUnbind(postHelper, ent);
        return postHelper;
    }

    public PreEntHelper preEntitlement(
            Consumer consumer, Pool entitlementPool, Integer quantity) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        return new PreEntHelper(1, null);
    }

    public Map<Pool, Integer> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance)
        throws RuleExecutionException {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        if (pools.isEmpty()) {
            return null;
        }

        Map<Pool, Integer> best = new HashMap<Pool, Integer>();
        for (Pool pool : pools) {
            best.put(pool, 1);
        }
        return best;
    }
}
