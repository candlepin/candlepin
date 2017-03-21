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
package org.candlepin.policy.js.autobind;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.Inject;

import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * AutobindRules
 *
 * Defers to rules to determine the best match of pools for a given consumer.
 */
public class AutobindRules {

    protected static final String SELECT_POOL_FUNCTION = "select_pools";

    private JsRunner jsRules;
    private static Logger log = LoggerFactory.getLogger(AutobindRules.class);
    private RulesObjectMapper mapper;
    private ProductCurator productCurator;

    @Inject
    public AutobindRules(JsRunner jsRules, ProductCurator productCurator, RulesObjectMapper mapper) {
        this.jsRules = jsRules;
        this.productCurator = productCurator;
        this.mapper = mapper;
        jsRules.init("autobind_name_space");
    }

    public List<PoolQuantity> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance, String serviceLevelOverride,
        Set<String> exemptLevels, boolean considerDerived) {

        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        int poolsBeforeContentFilter = pools.size();
        pools = filterPoolsForV1Certificates(consumer, pools);
        log.debug("pools.size() before V1 certificate filter: {}, after: {}",
            poolsBeforeContentFilter, pools.size());

        if (pools.size() == 0) {
            if (compliance.getReasons().size() == 0) {
                log.info("Consumer is compliant and does not require more entitlements.");
            }
            else {
                logProducts("No pools available to complete compliance for the set of products: {}" +
                    " and consumer installed products: {}", productIds, consumer, false);
            }
            return bestPools;
        }

        if (log.isDebugEnabled()) {
            logProducts("Selecting best entitlement pool for products: {}" +
                "and consumer installed products: {}", productIds, consumer, true);

            if (poolsBeforeContentFilter != pools.size()) {
                log.debug("{} pools filtered due to too much content",
                    (poolsBeforeContentFilter - pools.size()));
            }
        }

        // Provide objects for the script:
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", consumer);
        args.put("owner", consumer.getOwner());
        args.put("serviceLevelOverride", serviceLevelOverride);
        args.put("pools", pools.toArray());
        args.put("products", productIds);
        args.put("log", log, false);
        args.put("compliance", compliance);
        args.put("exemptList", exemptLevels);
        args.put("considerDerived", considerDerived);
        args.put("guestIds", consumer.getGuestIds());

        // Convert the JSON returned into a Map object:
        Map<String, Integer> result = null;
        try {
            String json = jsRules.invokeMethod(SELECT_POOL_FUNCTION, args);
            result = mapper.toObject(json, Map.class);
            if (log.isDebugEnabled()) {
                log.debug("Executed javascript rule: {}", SELECT_POOL_FUNCTION);
            }
        }
        catch (NoSuchMethodException e) {
            log.warn("No method found: {}", SELECT_POOL_FUNCTION);
            log.warn("Resorting to default pool selection behavior.");
            return selectBestPoolDefault(pools);
        }
        catch (RhinoException e) {
            throw new RuleExecutionException(e);
        }

        if (pools.size() > 0 && (result == null || result.isEmpty())) {
            logProducts("Rules did not select a pool for products: {} and consumer installed products: {}",
                productIds, consumer, false);
            return bestPools;
        }

        for (Pool p : pools) {
            for (Entry<String, Integer> entry : result.entrySet()) {
                if (p.getId().equals(entry.getKey())) {
                    log.debug("Best pool: {}", p);

                    int quantity = entry.getValue();
                    bestPools.add(new PoolQuantity(p, quantity));
                }
            }
        }

        return bestPools;
    }

    private void logProducts(String message, String[] productIds, Consumer consumer, boolean debug) {
        List<String> consumerProducts = new LinkedList<String>();
        if (consumer != null && consumer.getInstalledProducts() != null) {
            for (ConsumerInstalledProduct product: consumer.getInstalledProducts()) {
                consumerProducts.add(product.getProductId());
            }
        }

        if (debug) {
            log.debug(message, productIds, consumerProducts);
        }
        else {
            log.info(message, productIds, consumerProducts);
        }
    }

    /**
     * Default behavior if no product specific and no global pool select rules
     * exist.
     *
     * @param pools Pools to choose from.
     * @return First pool in the list. (default behavior)
     */
    protected List<PoolQuantity> selectBestPoolDefault(List<Pool> pools) {
        if (pools.size() > 0) {
            List<PoolQuantity> toReturn = new ArrayList<PoolQuantity>();
            for (Pool pool : pools) {
                toReturn.add(new PoolQuantity(pool, 1));
            }
            return toReturn;
        }

        return null;
    }

    /*
     * If this consumer only supports V1 certificates, we need to filter out pools
     * with too many content sets.
     */
    private List<Pool> filterPoolsForV1Certificates(Consumer consumer,
        List<Pool> pools) {
        if (!consumer.isCertV3Capable()) {
            List<Pool> newPools = new LinkedList<Pool>();

            for (Pool p : pools) {
                boolean contentOk = true;

                // Check each provided product, if *any* have too much content, we must
                // skip the pool:

                for (Product product : productCurator.getPoolProvidedProductsCached(p.getId())) {
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
