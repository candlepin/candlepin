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
package org.candlepin.controller;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * entitler
 */
public class Entitler {
    private static Logger log = LoggerFactory.getLogger(Entitler.class);

    private PoolManager poolManager;
    private I18n i18n;
    private EventFactory evtFactory;
    private EventSink sink;
    private ConsumerCurator consumerCurator;
    private EntitlementRulesTranslator messageTranslator;
    private EntitlementCurator entitlementCurator;
    private Configuration config;
    private PoolCurator poolCurator;
    private ProductCurator productCurator;
    private ProductServiceAdapter productAdapter;
    private long maxDevLifeDays = 90;
    final String DEFAULT_DEV_SLA = "Self-Service";

    @Inject
    public Entitler(PoolManager pm, ConsumerCurator cc, I18n i18n, EventFactory evtFactory,
        EventSink sink, EntitlementRulesTranslator messageTranslator,
        EntitlementCurator entitlementCurator, Configuration config,
        PoolCurator poolCurator, ProductCurator productCurator,
        ProductServiceAdapter productAdapter) {

        this.poolManager = pm;
        this.i18n = i18n;
        this.evtFactory = evtFactory;
        this.sink = sink;
        this.consumerCurator = cc;
        this.messageTranslator = messageTranslator;
        this.entitlementCurator = entitlementCurator;
        this.config = config;
        this.poolCurator = poolCurator;
        this.productCurator = productCurator;
        this.productAdapter = productAdapter;
    }

    public List<Entitlement> bindByPool(String poolId, String consumeruuid,
        Integer quantity) {
        Consumer c = consumerCurator.findByUuid(consumeruuid);
        return bindByPool(poolId, c, quantity);
    }

    public List<Entitlement> bindByPool(String poolId, Consumer consumer,
        Integer quantity) {
        log.info("Looking up pool to bind: " + poolId);
        Pool pool = poolManager.find(poolId);
        List<Entitlement> entitlementList = new LinkedList<Entitlement>();

        if (pool == null) {
            throw new BadRequestException(i18n.tr(
                "Subscription pool {0} does not exist.", poolId));
        }

        if (log.isDebugEnabled()) {
            log.debug("pool: id[" + pool.getId() + "], consumed[" +
                pool.getConsumed() + "], qty [" + pool.getQuantity() + "]");
        }

        // Attempt to create an entitlement:
        entitlementList.add(createEntitlementByPool(consumer, pool, quantity));
        return entitlementList;
    }

    private Entitlement createEntitlementByPool(Consumer consumer, Pool pool,
        Integer quantity) {
        try {
            // Attempt to create an entitlement:
            Entitlement e = poolManager.entitleByPool(consumer, pool, quantity);
            log.debug("Created entitlement: " + e);
            return e;
        }
        catch (EntitlementRefusedException e) {
            // TODO: Could be multiple errors, but we'll just report the first one for now
            throw new ForbiddenException(messageTranslator.poolErrorToMessage(pool,
                    e.getResult().getErrors().get(0)));
        }
    }

    public void adjustEntitlementQuantity(Consumer consumer, Entitlement ent,
        Integer quantity) {
        // Attempt to adjust an entitlement:
        try {
            poolManager.adjustEntitlementQuantity(consumer, ent, quantity);
        }
        catch (EntitlementRefusedException e) {
            // TODO: Could be multiple errors, but we'll just report the first one for now:
            throw new ForbiddenException(messageTranslator.entitlementErrorToMessage(ent,
                    e.getResult().getErrors().get(0)));
        }
    }

    public List<Entitlement> bindByProducts(String[] productIds,
        String consumeruuid, Date entitleDate, Collection<String> fromPools) {
        Consumer c = consumerCurator.findByUuid(consumeruuid);
        AutobindData data = AutobindData.create(c).on(entitleDate)
                .forProducts(productIds).withPools(fromPools);
        return bindByProducts(data);
    }

    /**
     * Entitles the given Consumer to the given Product. Will seek out pools
     * which provide access to this product, either directly or as a child, and
     * select the best one based on a call to the rules engine.
     *
     * @param data AutobindData encapsulating data required for an autobind request
     * @return List of Entitlements
     */
    public List<Entitlement> bindByProducts(AutobindData data) {
        return bindByProducts(data, false);
    }

    /**
     *
     * Force option is used to heal entire org
     *
     * @param data AutobindData encapsulating data required for an autobind request
     * @param force heal host even if it has autoheal disabled
     * @return List of Entitlements
     */
    public List<Entitlement> bindByProducts(AutobindData data, boolean force) {
        Consumer consumer = data.getConsumer();
        // If the consumer is a guest, and has a host, try to heal the host first
        // Dev consumers should not need to worry about the host or unmapped guest
        // entitlements based on the planned design of the subscriptions
        if (consumer.hasFact("virt.uuid") && !consumer.isDev()) {
            String guestUuid = consumer.getFact("virt.uuid");
            // Remove any expired unmapped guest entitlements
            revokeUnmappedGuestEntitlements(consumer);

            Consumer host = consumerCurator.getHost(guestUuid, consumer.getOwner());
            if (host != null && (force || host.isAutoheal())) {
                log.info("Attempting to heal host machine with UUID " +
                    host.getUuid() + " for guest with UUID " + consumer.getUuid());
                try {
                    List<Entitlement> hostEntitlements =
                        poolManager.entitleByProductsForHost(consumer, host,
                                data.getOnDate(), data.getPossiblePools());
                    log.debug("Granted host {} entitlements", hostEntitlements.size());
                    sendEvents(hostEntitlements);
                }
                catch (Exception e) {
                    //log and continue, this should NEVER block
                    log.debug("Healing failed for host UUID " + host.getUuid() +
                        " with message: " + e.getMessage());
                }

                /* Consumer is stale at this point.  Note that we use find() instead of
                 * findByUuid() or getConsumer() since the latter two methods are secured
                 * to a specific host principal and bindByProducts can get called when
                 * a guest is switching hosts */
                consumer = consumerCurator.find(consumer.getId());
                data.setConsumer(consumer);
            }
        }
        if (consumer.isDev()) {
            if (config.getBoolean(ConfigProperties.STANDALONE) ||
                    !poolCurator.hasActiveEntitlementPools(consumer.getOwner(), null)) {
                throw new ForbiddenException(i18n.tr(
                        "Development units may only be used on hosted servers" +
                        " and with orgs that have active subscriptions."));
            }

            // Look up the dev pool for this consumer, and if not found
            // create one. If a dev pool already exists, remove it and
            // create a new one.
            String sku = consumer.getFact("dev_sku");
            Pool devPool = poolCurator.findDevPool(consumer);
            if (devPool != null) {
                poolManager.deletePool(devPool);
            }
            devPool = poolManager.createPool(assembleDevPool(consumer, sku));
            data.setPossiblePools(Arrays.asList(devPool.getId()));
        }

        // Attempt to create entitlements:
        try {
            // the pools are only used to bind the guest
            List<Entitlement> entitlements = poolManager.entitleByProducts(data);
            log.debug("Created entitlements: " + entitlements);
            return entitlements;
        }
        catch (EntitlementRefusedException e) {
            // TODO: Could be multiple errors, but we'll just report the first one for now
            String productId = "Unknown Product";
            if (data.getProductIds().length > 0) {
                productId = data.getProductIds()[0];
            }
            throw new ForbiddenException(messageTranslator.productErrorToMessage(productId,
                    e.getResult().getErrors().get(0)));
        }
    }

    private long getPoolInterval(Product prod) {
        long interval = maxDevLifeDays;
        String prodThenString = prod.getAttributeValue("expires_after");
        if (prodThenString != null && Long.parseLong(prodThenString) < maxDevLifeDays) {
            interval = Long.parseLong(prodThenString);
        }
        return 1000 * 60 * 60 * 24 * interval;
    }

    /**
     * Create a development pool for the specified consumer that starts when
     * the consumer was registered and expires after the duration specified
     * by the SKU. The pool will be bound to the consumer via the
     * requires_consumer attribute, meaning only the consumer can bind to
     * entitlements from it.
     *
     * @param consumer the consumer the associate the pool with.
     * @param sku the product id of the developer SKU.
     * @return the newly created developer pool (note: not yet persisted)
     */
    protected Pool assembleDevPool(Consumer consumer, String sku) {
        DeveloperProducts devProducts = getDeveloperPoolProducts(consumer, sku);

        Product skuProduct = devProducts.getSku();

        Date startDate = consumer.getCreated();
        Date endDate = new Date(startDate.getTime() + getPoolInterval(skuProduct));
        Pool p = new Pool(consumer.getOwner(), skuProduct, devProducts.getProvided(), 1L, startDate,
                endDate, "", "", "");
        log.info("Created development pool with SKU " + skuProduct.getId());
        p.setAttribute(Pool.DEVELOPMENT_POOL_ATTRIBUTE, "true");
        p.setAttribute(Pool.REQUIRES_CONSUMER_ATTRIBUTE, consumer.getUuid());
        return p;
    }

    private DeveloperProducts getDeveloperPoolProducts(Consumer consumer, String sku) {
        DeveloperProducts devProducts = getDevProductMap(consumer, sku);
        verifyDevProducts(consumer, sku, devProducts);
        updateDevProducts(consumer, devProducts);
        return devProducts;
    }

    /**
     * Update the developer product references as they will be out of sync
     * when retrieved from the server.
     *
     * @param devConsumer
     * @param devProducts
     */
    protected void updateDevProducts(Consumer devConsumer, DeveloperProducts devProducts) {
        Product skuProduct = devProducts.getSku();
        if (StringUtils.isEmpty(skuProduct.getAttributeValue("support_level"))) {
            // if there is no SLA, apply the default
            skuProduct.setAttribute("support_level", this.DEFAULT_DEV_SLA);
        }

        // Update the owner references on the retrieved Product and content.
        for (Product prod : devProducts.getAll()) {
            prod.setOwner(devConsumer.getOwner());
            for (ProductContent pc : prod.getProductContent()) {
                pc.getContent().setOwner(devConsumer.getOwner());
            }
            productCurator.createOrUpdate(prod);
        }
    }

    /**
     * Looks up all Products matching the specified SKU and the consumer's
     * installed products.
     *
     * @param consumer the consumer to pull the installed product id list from.
     * @param sku the product id of the SKU.
     * @return a {@link DeveloperProducts} object that contains the Product objects
     *         from the adapter.
     */
    private DeveloperProducts getDevProductMap(Consumer consumer, String sku) {
        List<String> devProductIds = new ArrayList<String>();
        devProductIds.add(sku);
        for (ConsumerInstalledProduct ip : consumer.getInstalledProducts()) {
            devProductIds.add(ip.getProductId());
        }
        List<Product> prods = productAdapter.getProductsByIds(consumer.getOwner(), devProductIds);
        return new DeveloperProducts(sku, prods);
    }

    /**
     * Verifies that the expected developer SKU product was found and logs any
     * consumer installed products that were not found by the adapter.
     *
     * @param consumer the consumer who's installed products are to be checked.
     * @param expectedSku the product id of the developer sku that must be found
     *                    in order to build the development pool.
     * @param devProducts all products retrieved from the adapter that are validated.
     * @throws ForbiddenException thrown if the sku was not found by the adapter.
     */
    protected void verifyDevProducts(Consumer consumer, String expectedSku, DeveloperProducts devProducts)
        throws ForbiddenException {

        if (!devProducts.foundSku()) {
            throw new ForbiddenException(i18n.tr("SKU product not available to this " +
                    "development unit: ''{0}''", expectedSku));
        }

        for (ConsumerInstalledProduct ip : consumer.getInstalledProducts()) {
            if (!devProducts.containsProduct(ip.getProductId())) {
                log.warn(i18n.tr("Installed product not available to this " +
                        "development unit: {0}", ip.getProductId()));
            }
        }
    }

    /**
     * Entitles the given Consumer to the given Product. Will seek out pools
     * which provide access to this product, either directly or as a child, and
     * select the best one based on a call to the rules engine.
     *
     * @param consumer The consumer being entitled.
     * @return List of Entitlements
     */
    public List<PoolQuantity> getDryRun(Consumer consumer,
        String serviceLevelOverride) {

        List<PoolQuantity> result = new ArrayList<PoolQuantity>();
        try {
            Owner owner = consumer.getOwner();
            if (consumer.isDev()) {
                if (config.getBoolean(ConfigProperties.STANDALONE) ||
                        !poolCurator.hasActiveEntitlementPools(consumer.getOwner(), null)) {
                    throw new ForbiddenException(i18n.tr(
                            "Development units may only be used on hosted servers" +
                            " and with orgs that have active subscriptions."));
                }

                // Look up the dev pool for this consumer, and if not found
                // create one. If a dev pool already exists, remove it and
                // create a new one.
                String sku = consumer.getFact("dev_sku");
                Pool devPool = poolCurator.findDevPool(consumer);
                if (devPool != null) {
                    poolManager.deletePool(devPool);
                }
                devPool = poolManager.createPool(assembleDevPool(consumer, sku));
                result.add(new PoolQuantity(devPool, 1));
            }
            else {
                result = poolManager.getBestPools(
                    consumer, null, null, owner, serviceLevelOverride, null);
            }
            log.debug("Created Pool Quantity list: " + result);
        }
        catch (EntitlementRefusedException e) {
            // If we catch an exception we will just return an empty list
            // The dry run just reports that an autobind will have no pools
            // We will debug log the message, but returning does not seem to add
            // to the process
            if (log.isDebugEnabled()) {
                String message = e.getResult().getErrors().get(0).getResourceKey();
                log.debug("consumer dry-run " + consumer.getUuid() + ": " + message);
            }
        }
        return result;
    }

    public int revokeUnmappedGuestEntitlements(Consumer consumer) {
        int total = 0;

        List<Entitlement> unmappedGuestEntitlements;

        if (consumer == null) {
            unmappedGuestEntitlements = entitlementCurator.findByPoolAttribute(
                "unmapped_guests_only", "true");
        }
        else {
            unmappedGuestEntitlements = entitlementCurator.findByPoolAttribute(
                consumer, "unmapped_guests_only", "true");
        }

        for (Entitlement e : unmappedGuestEntitlements) {
            if (!e.isValid()) {
                poolManager.revokeEntitlement(e);
                total++;
            }
        }
        return total;
    }

    public int revokeUnmappedGuestEntitlements() {
        return revokeUnmappedGuestEntitlements(null);
    }

    public void sendEvents(List<Entitlement> entitlements) {
        if (entitlements != null) {
            for (Entitlement e : entitlements) {
                Event event = evtFactory.entitlementCreated(e);
                sink.queueEvent(event);
            }
        }
    }


    /**
     * A private sub class that encapsulates the products obtained from the
     * product adapter that are used to create the development pool. Its
     * general purpose is to distinguish between the sku and the provided
     * products without having to iterate a map to identify the sku.
     */
    private class DeveloperProducts {

        private Product sku;
        private Map<String, Product> provided;

        public DeveloperProducts(String expectedSku, List<Product> products) {
            provided = new HashMap<String, Product>();
            for (Product prod : products) {
                if (expectedSku.equals(prod.getId())) {
                    sku = prod;
                }
                else {
                    provided.put(prod.getId(), prod);
                }
            }

        }

        public Set<Product> getAll() {
            Set<Product> all = new HashSet<Product>(provided.values());
            all.add(sku);
            return all;
        }

        public Product getSku() {
            return sku;
        }

        public Set<Product> getProvided() {
            return new HashSet<Product>(provided.values());
        }

        public boolean foundSku() {
            return sku != null;
        }

        public boolean containsProduct(String productId) {
            return (foundSku() && productId.equals(sku.getId())) || provided.containsKey(productId);
        }

    }
}
