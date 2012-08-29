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

import com.google.inject.Inject;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.policy.Enforcer;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationWarning;
import org.candlepin.policy.js.JsRules;
import org.candlepin.policy.js.ReadOnlyConsumer;
import org.candlepin.policy.js.ReadOnlyPool;
import org.candlepin.policy.js.ReadOnlyProduct;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.util.DateSource;
import org.mozilla.javascript.RhinoException;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
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
public class EntitlementRules extends AbstractEntitlementRules implements Enforcer {

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRules jsRules,
        ProductCache productCache,
        I18n i18n, Config config, ConsumerCurator consumerCurator) {

        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.productCache = productCache;
        this.i18n = i18n;
        this.attributesToRules = null;
        this.config = config;
        this.consumerCurator = consumerCurator;

        log = Logger.getLogger(EntitlementRules.class);
        rulesLogger =
            Logger.getLogger(EntitlementRules.class.getCanonicalName() + ".rules");

    }

    @Override
    public PreEntHelper preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        PreEntHelper preHelper = runPreEntitlement(consumer, entitlementPool,
            quantity);

        if (entitlementPool.isExpired(dateSource)) {
            preHelper.getResult().addError(
                new ValidationError(i18n.tr("Entitlements for {0} expired on: {1}",
                    entitlementPool.getProductId(),
                    entitlementPool.getEndDate())));
        }

        return preHelper;
    }

    private PreEntHelper runPreEntitlement(Consumer consumer, Pool pool, Integer quantity) {
        PreEntHelper preHelper = new PreEntHelper(quantity, consumerCurator);

        // Provide objects for the script:
        String topLevelProductId = pool.getProductId();
        ReadOnlyProduct product = new ReadOnlyProduct(topLevelProductId,
            pool.getProductName(),
            jsRules.getFlattenedAttributes(pool.getProductAttributes()));
        Map<String, String> allAttributes = jsRules.getFlattenedAttributes(pool);

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(consumer));
        args.put("product", product);
        args.put("pool", new ReadOnlyPool(pool));
        args.put("pre", preHelper);
        args.put("attributes", allAttributes);
        args.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        args.put("standalone", config.standalone());
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
    public List<PoolQuantity> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance, String serviceLevelOverride,
        Set<String> exemptLevels) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        log.info("Selecting best entitlement pool for product: " +
            Arrays.toString(productIds));
        List<ReadOnlyPool> readOnlyPools = ReadOnlyPool.fromCollection(pools);

        List<ReadOnlyProduct> readOnlyProducts = new LinkedList<ReadOnlyProduct>();
        Set<Rule> matchingRules = new HashSet<Rule>();
        for (String productId : productIds) {
            Product product = productCache.getProductById(productId);

            if (product != null) {
                ReadOnlyProduct roProduct = new ReadOnlyProduct(product);
                readOnlyProducts.add(roProduct);
                matchingRules.addAll(rulesForAttributes(roProduct.getAttributes().keySet(),
                    attributesToRules));
            }
        }

        // Provide objects for the script:
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", new ReadOnlyConsumer(consumer, serviceLevelOverride));
        args.put("pools", readOnlyPools.toArray());
        args.put("products", readOnlyProducts.toArray());
        args.put("prodAttrSeparator", PROD_ARCHITECTURE_SEPARATOR);
        args.put("log", rulesLogger);
        args.put("compliance", compliance);
        args.put("exemptList", exemptLevels);

        Map<ReadOnlyPool, Integer> result = null;
        boolean foundMatchingRule = false;
        for (Rule rule : matchingRules) {
            try {
                Object output =
                    jsRules.invokeMethod(SELECT_POOL_PREFIX + rule.getRuleName(), args);
                result = jsRules.convertMap(output);
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
                result = jsRules.convertMap(output);
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

        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        for (Pool p : pools) {
            for (Entry<ReadOnlyPool, Integer> entry : result.entrySet()) {
                if (p.getId().equals(entry.getKey().getId())) {
                    log.debug("Best pool: " + p);

                    int quantity = entry.getValue();
                    bestPools.add(new PoolQuantity(p, quantity));
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
}
