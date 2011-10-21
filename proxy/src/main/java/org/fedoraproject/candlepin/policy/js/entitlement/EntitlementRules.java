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
import org.fedoraproject.candlepin.policy.ValidationError;
import org.fedoraproject.candlepin.policy.ValidationWarning;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.ReadOnlyConsumer;
import org.fedoraproject.candlepin.policy.js.ReadOnlyPool;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProduct;
import org.fedoraproject.candlepin.policy.js.ReadOnlyProductCache;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.compliance.ComplianceStatus;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;

import com.google.inject.Inject;

import edu.emory.mathcs.backport.java.util.Arrays;

import org.apache.log4j.Logger;
import org.mozilla.javascript.RhinoException;
import org.xnap.commons.i18n.I18n;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the Javascript Rules definition.
 */
public class EntitlementRules extends AbstractEntitlementRules implements Enforcer {

    @Inject
    public EntitlementRules(DateSource dateSource,
        JsRules jsRules,
        ProductServiceAdapter prodAdapter,
        I18n i18n, Config config, ConsumerCurator consumerCurator) {

        this.jsRules = jsRules;
        this.dateSource = dateSource;
        this.prodAdapter = prodAdapter;
        this.i18n = i18n;
        this.attributesToRules = null;
        this.config = config;
        this.consumerCurator = consumerCurator;

        log = Logger.getLogger(EntitlementRules.class);
        rulesLogger =
            Logger.getLogger(EntitlementRules.class.getCanonicalName() + ".rules");

    }

    @Override
    public PreEntHelper preEntitlement(
        Consumer consumer, Pool entitlementPool, Integer quantity) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

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
        PreEntHelper preHelper = new PreEntHelper(quantity, consumerCurator);

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
    public PoolHelper postEntitlement(
            Consumer consumer, PoolHelper postEntHelper, Entitlement ent) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

        runPostEntitlement(postEntHelper, ent);
        return postEntHelper;
    }

    public Map<Pool, Integer> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance) {

        jsRules.reinitTo("entitlement_name_space");
        rulesInit();

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
        args.put("compliance", compliance);

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

        Map<Pool, Integer> bestPools = new HashMap<Pool, Integer>();
        for (Pool p : pools) {
            for (ReadOnlyPool rp : result.keySet()) {
                if (p.getId().equals(rp.getId())) {
                    log.debug("Best pool: " + p);

                    int quantity = result.get(rp);
                    bestPools.put(p, quantity);
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
}
