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
import org.candlepin.model.ProvidedProduct;
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
import org.candlepin.util.X509ExtensionUtil;
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

        int poolsBeforeContentFilter = pools.size();
        pools = filterPoolsForV1Certificates(consumer, pools);

        // TODO: Not the best behavior:
        if (pools.size() == 0) {
            throw new RuntimeException("No entitlements for products: " +
                Arrays.toString(productIds));
        }

        if (log.isDebugEnabled()) {
            log.debug("Selecting best entitlement pool for products: " +
                Arrays.toString(productIds));
            if (poolsBeforeContentFilter != pools.size()) {
                log.debug((poolsBeforeContentFilter - pools.size()) + " pools filtered " +
                    "due to too much content");
            }
        }
        List<ReadOnlyPool> readOnlyPools = ReadOnlyPool.fromCollection(pools);

        /*
         * NOTE: These are engineering product IDs being passed in which are installed on
         * the given system. There is almost no value to looking these up from the product
         * service as there's not much useful, and indeed all the select pool rules ever
         * use is the product ID, which we had before we did the lookup. Unfortunately we
         * need to maintain backward compatability with past rules files, so we will
         * continue providing ReadOnlyProduct objects to the rules, but we'll just
         * pre-populate the ID field and not do an actual lookup.
         */
        List<ReadOnlyProduct> readOnlyProducts = new LinkedList<ReadOnlyProduct>();
        for (String productId : productIds) {
            // NOTE: using ID as name here, rules just need ID:
            ReadOnlyProduct roProduct = new ReadOnlyProduct(productId, productId,
                new HashMap<String, String>());
            readOnlyProducts.add(roProduct);
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
        // Only need to run the select best pools global rule:
        try {
            Object output =
                jsRules.invokeMethod(GLOBAL_SELECT_POOL_FUNCTION, args);
            result = jsRules.convertMap(output);
            if (log.isDebugEnabled()) {
                log.debug("Excuted javascript rule: " + GLOBAL_SELECT_POOL_FUNCTION);
            }
        }
        catch (NoSuchMethodException e) {
            log.warn("No default rule found: " + GLOBAL_SELECT_POOL_FUNCTION);
            log.warn("Resorting to default pool selection behavior.");
            return selectBestPoolDefault(pools);
        }
        catch (RhinoException e) {
            throw new RuleExecutionException(e);
        }

        if (pools.size() > 0 && result == null) {
            throw new RuleExecutionException(
                "Rule did not select a pool for products: " + Arrays.toString(productIds));
        }

        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        for (Pool p : pools) {
            for (Entry<ReadOnlyPool, Integer> entry : result.entrySet()) {
                if (p.getId().equals(entry.getKey().getId())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Best pool: " + p);
                    }

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

    /*
     * If this consumer only supports V1 certificates, we need to filter out pools
     * with too many content sets.
     */
    private List<Pool> filterPoolsForV1Certificates(Consumer consumer,
        List<Pool> pools) {
        if (!consumer.hasFact("system.certificate_version") ||
            (consumer.hasFact("system.certificate_version") &&
            consumer.getFact("system.certificate_version").startsWith("1."))) {
            List<Pool> newPools = new LinkedList<Pool>();

            for (Pool p : pools) {
                boolean contentOk = true;

                // Check each provided product, if *any* have too much content, we must
                // skip the pool:
                for (ProvidedProduct providedProd : p.getProvidedProducts()) {
                    Product product = productCache.getProductById(
                        providedProd.getProductId());
                    if (product.getProductContent().size() >
                        X509ExtensionUtil.V1_CONTENT_LIMIT) {
                        contentOk = false;
                        break;
                    }
                }
                if (contentOk) {
                    newPools.add(p);
                }
            }
            return newPools;
        }

        // Otherwise return the list of pools as is:
        return pools;
    }
}
