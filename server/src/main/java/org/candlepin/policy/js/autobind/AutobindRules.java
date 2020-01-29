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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.ComplianceStatusDTO;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.GuestIdDTO;
import org.candlepin.dto.rules.v1.OwnerDTO;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
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
import java.util.stream.Stream;


/**
 * AutobindRules
 *
 * Defers to rules to determine the best match of pools for a given consumer.
 */
public class AutobindRules {

    protected static final String SELECT_POOL_FUNCTION = "select_pools";
    private static Logger log = LoggerFactory.getLogger(AutobindRules.class);

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private ProductCurator productCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private OwnerCurator ownerCurator;
    private ModelTranslator translator;

    @Inject
    public AutobindRules(JsRunner jsRules, ProductCurator productCurator,
        ConsumerTypeCurator consumerTypeCurator, OwnerCurator ownerCurator, RulesObjectMapper mapper,
        ModelTranslator translator) {

        this.jsRules = jsRules;
        this.productCurator = productCurator;
        this.ownerCurator = ownerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.mapper = mapper;
        this.translator = translator;

        jsRules.init("autobind_name_space");
    }

    public List<PoolQuantity> selectBestPools(Consumer consumer, String[] productIds,
        List<Pool> pools, ComplianceStatus compliance, String serviceLevelOverride,
        Set<String> exemptLevels, boolean considerDerived) {

        List<PoolQuantity> bestPools = new ArrayList<>();
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

        List<PoolDTO> poolDTOs = new ArrayList<>();
        for (Pool pool : pools) {
            poolDTOs.add(this.translator.translate(pool, PoolDTO.class));
        }

        Stream<GuestIdDTO> guestIdStream = consumer.getGuestIds() == null ? Stream.empty() :
            consumer.getGuestIds().stream()
            .map(this.translator.getStreamMapper(GuestId.class, GuestIdDTO.class));

        // Provide objects for the script:
        JsonJsContext args = new JsonJsContext(mapper);

        ConsumerDTO consumerDTO = this.translator.translate(consumer, ConsumerDTO.class);

        // #1692411: Current rule code is only to consider consumer installed products to calculate
        // the pool's average priority. Hence added any additional products into consumer’s installed
        // products to correctly calculate pool average priority.
        for (String productId : productIds) {
            if (productId != null && !productId.isEmpty()) {
                consumerDTO.addInstalledProduct(productId);
            }
        }

        args.put("consumer", consumerDTO);
        Owner owner = ownerCurator.findOwnerById(consumer.getOwnerId());
        args.put("owner", this.translator.translate(owner, OwnerDTO.class));
        args.put("serviceLevelOverride", serviceLevelOverride);
        args.put("pools", poolDTOs.toArray());
        args.put("products", productIds);
        args.put("log", log, false);
        args.put("compliance", this.translator.translate(compliance, ComplianceStatusDTO.class));
        args.put("exemptList", exemptLevels);
        args.put("considerDerived", considerDerived);
        args.put("guestIds", guestIdStream);

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
        List<String> consumerProducts = new LinkedList<>();
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
            List<PoolQuantity> toReturn = new ArrayList<>();
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
    private List<Pool> filterPoolsForV1Certificates(Consumer consumer, List<Pool> pools) {
        if (!this.consumerIsCertV3Capable(consumer)) {
            List<Pool> newPools = new LinkedList<>();

            for (Pool p : pools) {
                boolean contentOk = true;

                // Check each provided product, if *any* have too much content, we must
                // skip the pool:

                for (Product product : p.getProduct().getProvidedProducts()) {
                    if (product.getProductContent().size() > X509ExtensionUtil.V1_CONTENT_LIMIT) {
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

    /**
     * Checks if the specified consumer is capable of using v3 certificates
     *
     * @param consumer
     *  The consumer to check
     *
     * @return
     *  true if the consumer is capable of using v3 certificates; false otherwise
     */
    private boolean consumerIsCertV3Capable(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        ConsumerType type = this.consumerTypeCurator.getConsumerType(consumer);

        if (type.isManifest()) {
            for (ConsumerCapability capability : consumer.getCapabilities()) {
                if ("cert_v3".equals(capability.getName())) {
                    return true;
                }
            }

            return false;
        }
        else if (type.isType(ConsumerTypeEnum.HYPERVISOR)) {
            // Hypervisors in this context don't use content, so V3 is allowed
            return true;
        }

        // Consumer isn't a special type, check their certificate_version fact
        String entitlementVersion = consumer.getFact("system.certificate_version");
        return entitlementVersion != null && entitlementVersion.startsWith("3.");
    }

}
