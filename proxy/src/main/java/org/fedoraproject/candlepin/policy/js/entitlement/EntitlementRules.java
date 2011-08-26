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

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;
import org.fedoraproject.candlepin.policy.ValidationWarning;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.ReadOnlyConsumer;
import org.fedoraproject.candlepin.policy.js.ReadOnlyPool;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProduct;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProductCache;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;

import com.google.inject.Inject;

import edu.emory.mathcs.backport.java.util.Arrays;

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
public class EntitlementRules implements Enforcer {

    private static Logger log = Logger.getLogger(EntitlementRules.class);
    private static Logger rulesLogger =
        Logger.getLogger(EntitlementRules.class.getCanonicalName() + ".rules");
    private DateSource dateSource;

    private ProductServiceAdapter prodAdapter;
    private I18n i18n;
    private Map<String, Set<Rule>> attributesToRules;
    private JsRules jsRules;
    
    private static final String PROD_ARCHITECTURE_SEPARATOR = ",";
    private static final String PRE_PREFIX = "pre_";
    private static final String POST_PREFIX = "post_";
    private static final String SELECT_POOL_PREFIX = "select_pool_";
    private static final String GLOBAL_SELECT_POOL_FUNCTION = SELECT_POOL_PREFIX +
        "global";
    private static final String GLOBAL_PRE_FUNCTION = PRE_PREFIX + "global";
    private static final String GLOBAL_POST_FUNCTION = POST_PREFIX + "global";
    

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRules jsRules,
        ProductServiceAdapter prodAdapter,
        I18n i18n) {

        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.prodAdapter = prodAdapter;
        this.i18n = i18n;
        this.attributesToRules = null;

        jsRules.init("entitlement_name_space");
        rulesInit();
    }

    private void rulesInit() {
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

    private PreEntHelper runPreEntitlement(Consumer consumer, Pool pool, Integer quantity) {
        PreEntHelper preHelper = new PreEntHelper(quantity);

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        Product product = prodAdapter.getProductById(topLevelProductId);
        Map<String, String> allAttributes = jsRules.getFlattenedAttributes(product, pool);
        
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(consumer));
        args.put("product", new ReadOnlyProduct(product));
        args.put("pool", new ReadOnlyPool(pool, new ReadOnlyProductCache(prodAdapter)));
        args.put("pre", preHelper);
        args.put("attributes", allAttributes);
        args.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        args.put("log", rulesLogger);

        log.debug("Running pre-entitlement rules for: " + consumer.getUuid() +
            " product: " + topLevelProductId);
        List<Rule> matchingRules 
            = rulesForAttributes(allAttributes.keySet(), attributesToRules);
        
        callPreEntitlementRules(matchingRules, args);

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
    public PoolHelper postEntitlement(
            Consumer consumer, PoolHelper postEntHelper, Entitlement ent) {
        runPostEntitlement(postEntHelper, ent);
        return postEntHelper;
    }

    private void runPostEntitlement(PoolHelper postHelper, Entitlement ent) {
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

        log.debug("Running post-entitlement rules for: " + c.getUuid() +
            " product: " + topLevelProductId);

        List<Rule> matchingRules 
            = rulesForAttributes(allAttributes.keySet(), attributesToRules);

        invokeGlobalPostEntitlementRule(args);
        callPostEntitlementRules(matchingRules);
    }

    public List<Pool> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools) {
        ReadOnlyProductCache productCache = new ReadOnlyProductCache(prodAdapter);
        
        log.info("Selecting best entitlement pool for product: " +
            Arrays.toString(productIds));
        List<ReadOnlyPool> readOnlyPools = ReadOnlyPool.fromCollection(pools, productCache);
        
        List<Product> products = new LinkedList<Product>();
        Set<Rule> matchingRules = new HashSet<Rule>();
        for (String productId : productIds) {
            Product product = prodAdapter.getProductById(productId);

            if (product != null) {
                products.add(product);
            
                Map<String, String> allAttributes = jsRules.getFlattenedAttributes(product,
                    null);
                matchingRules.addAll(rulesForAttributes(allAttributes.keySet(),
                    attributesToRules));
            }
        }

        Set<ReadOnlyProduct> readOnlyProducts = ReadOnlyProduct.fromProducts(products);
        productCache.addProducts(readOnlyProducts);
        
        // Provide objects for the script:
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(consumer));
        args.put("pools", readOnlyPools.toArray());
        args.put("products", readOnlyProducts.toArray());
        args.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        args.put("log", rulesLogger);

        ReadOnlyPool[] result = null;
        boolean foundMatchingRule = false;
        for (Rule rule : matchingRules) {
            try {
                Object output =
                    jsRules.invokeMethod(SELECT_POOL_PREFIX + rule.getRuleName(), args);
                result = jsRules.convertArray(output);
                foundMatchingRule = true;
                log.info("Excuted javascript rule: " + SELECT_POOL_PREFIX +
                    rule.getRuleName());
                break;
            }
            catch (NoSuchMethodException e) {
                // continue on to the next rule in the list.
            }
            catch (RhinoException e) {
                throw new RuleExecutionException(e);
            }
        }
        
        if (!foundMatchingRule) {
            try {
                Object output = jsRules.invokeMethod(GLOBAL_SELECT_POOL_FUNCTION, args);
                result = jsRules.convertArray(output);
                log.info("Excuted javascript rule: " +
                    GLOBAL_SELECT_POOL_FUNCTION);
            }
            catch (NoSuchMethodException ex) {
                log.warn("No default rule found: " +
                    GLOBAL_SELECT_POOL_FUNCTION);
                log.warn("Resorting to default pool selection behavior.");
                return selectBestPoolDefault(pools);
            }
            catch (RhinoException ex) {
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
                if (p.getId().equals(rp.getId())) {
                    log.debug("Best pool: " + p);
                    bestPools.add(p);
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
    
    private void callPreEntitlementRules(List<Rule> matchingRules,
        Map<String, Object> args) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(PRE_PREFIX + rule.getRuleName(), args);
        }
    }

    private void callPostEntitlementRules(List<Rule> matchingRules) {
        for (Rule rule : matchingRules) {
            jsRules.invokeRule(POST_PREFIX + rule.getRuleName());
        }
    }

    private void invokeGlobalPostEntitlementRule(Map<String, Object> args) {
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

}
