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

import java.io.Reader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;
import org.fedoraproject.candlepin.policy.ValidationWarning;
import org.fedoraproject.candlepin.policy.js.ReadOnlyConsumer;
import org.fedoraproject.candlepin.policy.js.ReadOnlyEntitlement;
import org.fedoraproject.candlepin.policy.js.ReadOnlyPool;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProduct;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProductCache;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.RuleParseException;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import edu.emory.mathcs.backport.java.util.Arrays;

/**
 * Enforces the Javascript Rules definition.
 */
public class EntitlementRules implements Enforcer {

    private static Logger log = Logger.getLogger(EntitlementRules.class);
    private static Logger rulesLogger =
        Logger.getLogger(EntitlementRules.class.getCanonicalName() + ".rules");
    private DateSource dateSource;

    private ProductServiceAdapter prodAdapter;

    private ScriptEngine jsEngine;
    private I18n i18n;
    private final Map<String, Set<Rule>> attributesToRules;
    
    private Object entitlementNameSpace;
    private static final String PROD_ARCHITECTURE_SEPARATOR = ",";
    private static final String PRE_PREFIX = "pre_";
    private static final String POST_PREFIX = "post_";
    private static final String SELECT_POOL_PREFIX = "select_pool_";
    private static final String GLOBAL_SELECT_POOL_FUNCTION = SELECT_POOL_PREFIX +
        "global";
    private static final String GLOBAL_PRE_FUNCTION = PRE_PREFIX + "global";
    private static final String GLOBAL_POST_FUNCTION = POST_PREFIX + "global";
    
    // Since we can't peek inside the return values from our java code,
    // do the conversion from a js array to a java array inside rhino's context.
    private static final String CONVERT_ARRAY_FUNCTION =
        "function convertArray(type, arr) {\n" +
        "    if (arr == null) {\n" +
        "        return null;\n" +
        "    }\n" +
        "\n" +
        "    var jArr = java.lang.reflect.Array.newInstance(type, arr.length);\n" +
        "    for (var i = 0; i < arr.length; i++) {\n" +
        "        jArr[i] = arr[i];\n" +
        "    }\n" +
        "    return jArr;\n" +
        "};\n";

    @Inject
    public EntitlementRules(DateSource dateSource,
        @Named("RulesReader") Reader rulesReader,
        ProductServiceAdapter prodAdapter,
        ScriptEngine jsEngine, I18n i18n) {
        this.dateSource = dateSource;

        this.prodAdapter = prodAdapter;
        this.jsEngine = jsEngine;
        this.i18n = i18n;

        if (jsEngine == null) {
            throw new RuntimeException("No Javascript engine");
        }

        try {
            this.jsEngine.eval(rulesReader);
            
            entitlementNameSpace = 
                ((Invocable) this.jsEngine).invokeFunction("entitlement_name_space");
            
            attributesToRules = parseAttributeMappings(
                (String) ((Invocable) this.jsEngine).invokeMethod(
                    entitlementNameSpace, "attribute_mappings"));
            
            this.jsEngine.eval(CONVERT_ARRAY_FUNCTION);
        }
        catch (ScriptException ex) {
            throw new RuleParseException(ex);
        }
        catch (NoSuchMethodException ex) {
            throw new RuleParseException(ex);
        }
    }

    @Override
    public PreEntHelper preEntitlement(
        Consumer consumer, Pool entitlementPool, Integer quantity) {

        PreEntHelper preHelper = runPreEntitlement(consumer, entitlementPool, quantity);

        if (entitlementPool.isExpired(dateSource)) {
            preHelper.getResult().addError(
                new ValidationError(i18n.tr("Entitlements for {0} expired on: {1}",
                    entitlementPool.getProductId(),
                    entitlementPool.getEndDate())));
        }

        return preHelper;
    }
    
    /**
     * Both products and pools can carry attributes, we need to trigger rules for each.
     * In this map, pool attributes will override product attributes, should the same
     * key be set for both.
     *
     * @param product Product
     * @param pool Pool can be null.
     * @return Map of all attribute names and values. Pool attributes have priority.
     */
    private Map<String, String> getFlattenedAttributes(Product product, Pool pool) {
        Map<String, String> allAttributes = new HashMap<String, String>();
        for (Attribute a : product.getAttributes()) {
            allAttributes.put(a.getName(), a.getValue());
        }
        if (pool != null) {
            for (Attribute a : pool.getAttributes()) {
                allAttributes.put(a.getName(), a.getValue());
            }

        }
        return allAttributes;
    }

    private PreEntHelper runPreEntitlement(Consumer consumer, Pool pool, Integer quantity) {
        PreEntHelper preHelper = new PreEntHelper(quantity);

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        Product product = prodAdapter.getProductById(topLevelProductId);
        Map<String, String> allAttributes = getFlattenedAttributes(product, pool);
        jsEngine.put("consumer", new ReadOnlyConsumer(consumer));
        jsEngine.put("product", new ReadOnlyProduct(product));
        jsEngine.put("pool", new ReadOnlyPool(pool, new ReadOnlyProductCache(prodAdapter)));
        jsEngine.put("pre", preHelper);
        jsEngine.put("attributes", allAttributes);
        jsEngine.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        jsEngine.put("log", rulesLogger);

        log.debug("Running pre-entitlement rules for: " + consumer.getUuid() +
            " product: " + topLevelProductId);
        List<Rule> matchingRules 
            = rulesForAttributes(allAttributes.keySet(), attributesToRules);
        
        if (matchingRules.isEmpty()) {
            invokeGlobalPreEntitlementRule();
        }
        else {
            callPreEntitlementRules(matchingRules);
        }

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

    @Override
    public PostEntHelper postEntitlement(
            Consumer consumer, PostEntHelper postEntHelper, Entitlement ent) {
        runPostEntitlement(postEntHelper, ent);
        return postEntHelper;
    }

    private void runPostEntitlement(PostEntHelper postHelper, Entitlement ent) {
        Pool pool = ent.getPool();
        Consumer c = ent.getConsumer();

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        Product product = prodAdapter.getProductById(topLevelProductId);
        Map<String, String> allAttributes = getFlattenedAttributes(product, pool);
        jsEngine.put("consumer", new ReadOnlyConsumer(c));
        jsEngine.put("product", new ReadOnlyProduct(product));
        jsEngine.put("post", postHelper);
        jsEngine.put("pool", new ReadOnlyPool(pool, new ReadOnlyProductCache(prodAdapter)));
        jsEngine.put("entitlement", new ReadOnlyEntitlement(ent));
        jsEngine.put("attributes", allAttributes);
        jsEngine.put("log", rulesLogger);

        log.debug("Running post-entitlement rules for: " + c.getUuid() +
            " product: " + topLevelProductId);

        List<Rule> matchingRules 
            = rulesForAttributes(allAttributes.keySet(), attributesToRules);
        if (matchingRules.isEmpty()) {
            invokeGlobalPostEntitlementRule();
        }
        else {
            callPostEntitlementRules(matchingRules);
        }
    }

    public List<Pool> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools) {
        Invocable inv = (Invocable) jsEngine;

        ReadOnlyProductCache productCache = new ReadOnlyProductCache(prodAdapter);
        
        log.info("Selecting best entitlement pool for product: " + productIds);
        List<ReadOnlyPool> readOnlyPools = ReadOnlyPool.fromCollection(pools, productCache);

        
        List<Product> products = new LinkedList<Product>();
        Set<Rule> matchingRules = new HashSet<Rule>();
        for (String productId : productIds) {
            Product product = prodAdapter.getProductById(productId);
            products.add(product);
            Map<String, String> allAttributes = getFlattenedAttributes(product, null);
            matchingRules.addAll(rulesForAttributes(allAttributes.keySet(),
                attributesToRules));
        }

        Set<ReadOnlyProduct> readOnlyProducts = ReadOnlyProduct.fromProducts(products);
        productCache.addProducts(readOnlyProducts);

        // Provide objects for the script:
        jsEngine.put("pools", readOnlyPools.toArray());
        jsEngine.put("products", readOnlyProducts.toArray());
        jsEngine.put("log", log);

        ReadOnlyPool[] result = null;
        boolean foundMatchingRule = false;
        for (Rule rule : matchingRules) {
            try {
                Object output = inv.invokeMethod(entitlementNameSpace,
                    SELECT_POOL_PREFIX + rule.getRuleName());
                result = (ReadOnlyPool[]) inv.invokeFunction("convertArray",
                    org.fedoraproject.candlepin.policy.js.ReadOnlyPool.class,
                    output);
                foundMatchingRule = true;
                log.info("Excuted javascript rule: " + SELECT_POOL_PREFIX +
                    rule.getRuleName());
                break;
            }
            catch (NoSuchMethodException e) {
                // continue on to the next rule in the list.
            }
            catch (ScriptException e) {
                throw new RuleExecutionException(e);
            }
        }
        
        if (!foundMatchingRule) {
            try {
                Object output = inv.invokeMethod(entitlementNameSpace,
                    GLOBAL_SELECT_POOL_FUNCTION);
                result = (ReadOnlyPool[]) inv.invokeFunction("convertArray",
                    org.fedoraproject.candlepin.policy.js.ReadOnlyPool.class,
                    output);
                log.info("Excuted javascript rule: " +
                    GLOBAL_SELECT_POOL_FUNCTION);
            }
            catch (NoSuchMethodException ex) {
                log.warn("No default rule found: " +
                    GLOBAL_SELECT_POOL_FUNCTION);
                log.warn("Resorting to default pool selection behavior.");
                return selectBestPoolDefault(pools);
            }
            catch (ScriptException ex) {
                throw new RuleExecutionException(ex);
            }
        }
        
        if (pools.size() > 0 && result == null) {
            throw new RuleExecutionException(
              "Rule did not select a pool for products: " + Arrays.toString(productIds));
        }

        List<Pool> bestPools = new LinkedList<Pool>();
        for (Pool p : pools) {
            for (ReadOnlyPool rp : result) {
                rp.getId();
                p.getId().equals("foo");
                if (p.getId().equals(rp.getId())) {
                    log.debug("Best pool: " + p);
                    bestPools.add(p);
                    break;
                }
            }
        }
        
        if (bestPools.size() > 0) {
            return bestPools;
        }
        else {
            return null;
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
    private List<Pool> selectBestPoolDefault(List<Pool> pools) {
        if (pools.size() > 0) {
            return pools;
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
                    "'{0}' Should contain name, priority and at least one attribute",
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
    
    private void callPreEntitlementRules(List<Rule> matchingRules) {
        Invocable inv = (Invocable) jsEngine;
        for (Rule rule : matchingRules) {
            try {
                inv.invokeMethod(entitlementNameSpace, PRE_PREFIX + rule.getRuleName());
                log.debug("Ran rule: " + PRE_PREFIX + rule.getRuleName());
            }
            catch (NoSuchMethodException e) {
                invokeGlobalPreEntitlementRule();
            }
            catch (ScriptException e) {
                throw new RuleExecutionException(e);
            }
        }
    }

    private void callPostEntitlementRules(List<Rule> matchingRules) {
        Invocable inv = (Invocable) jsEngine;
        for (Rule rule : matchingRules) {
            try {
                inv.invokeMethod(entitlementNameSpace, POST_PREFIX + rule.getRuleName());
                log.debug("Ran rule: " + POST_PREFIX + rule.getRuleName());
            }
            catch (NoSuchMethodException e) {
                invokeGlobalPostEntitlementRule();
            }
            catch (ScriptException e) {
                throw new RuleExecutionException(e);
            }
        }
    }

    private void invokeGlobalPreEntitlementRule() {
        Invocable inv = (Invocable) jsEngine;
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            inv.invokeMethod(entitlementNameSpace, GLOBAL_PRE_FUNCTION);
            log.debug("Ran rule: " + GLOBAL_PRE_FUNCTION);
        }
        catch (NoSuchMethodException ex) {
            // This is fine, I hope...
            log.warn("No default rule found: " + GLOBAL_PRE_FUNCTION);
        }
        catch (ScriptException ex) {
            throw new RuleExecutionException(ex);
        }
    }

    private void invokeGlobalPostEntitlementRule() {
        Invocable inv = (Invocable) jsEngine;
        // No method for this product, try to find a global function, if
        // neither exists this is ok and we'll just carry on.
        try {
            inv.invokeFunction(GLOBAL_POST_FUNCTION);
            log.debug("Ran rule: " + GLOBAL_POST_FUNCTION);
        }
        catch (NoSuchMethodException ex) {
            // This is fine, I hope...
            log.warn("No default rule found: " + GLOBAL_POST_FUNCTION);
        }
        catch (ScriptException ex) {
            throw new RuleExecutionException(ex);
        }
    }
    
    /**
     * RuleOrderComparator
     */
    public static class RuleOrderComparator implements Comparator<Rule> {
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

}
