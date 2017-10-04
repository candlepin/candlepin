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
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;



/**
 * entitler
 */
public class Entitler {
    private static Logger log = LoggerFactory.getLogger(Entitler.class);

    private Configuration config;
    private ConsumerCurator consumerCurator;
    private ContentManager contentManager;
    private EventFactory evtFactory;
    private EventSink sink;
    private EntitlementRulesTranslator messageTranslator;
    private EntitlementCurator entitlementCurator;
    private I18n i18n;
    private OwnerProductCurator ownerProductCurator;
    private PoolCurator poolCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private ProductManager productManager;
    private ProductServiceAdapter productAdapter;

    private int maxDevLifeDays = 90;
    final String DEFAULT_DEV_SLA = "Self-Service";

    @Inject
    public Entitler(PoolManager pm, ConsumerCurator cc, I18n i18n, EventFactory evtFactory,
        EventSink sink, EntitlementRulesTranslator messageTranslator,
        EntitlementCurator entitlementCurator, Configuration config, OwnerProductCurator ownerProductCurator,
        PoolCurator poolCurator, ProductCurator productCurator, ProductManager productManager,
        ProductServiceAdapter productAdapter, ContentManager contentManager) {

        this.poolManager = pm;
        this.i18n = i18n;
        this.evtFactory = evtFactory;
        this.sink = sink;
        this.consumerCurator = cc;
        this.messageTranslator = messageTranslator;
        this.entitlementCurator = entitlementCurator;
        this.config = config;
        this.ownerProductCurator = ownerProductCurator;
        this.poolCurator = poolCurator;
        this.productCurator = productCurator;
        this.productManager = productManager;
        this.productAdapter = productAdapter;
        this.contentManager = contentManager;
    }

    public List<Entitlement> bindByPoolQuantities(String consumeruuid,
        Map<String, Integer> poolIdAndQuantities) throws EntitlementRefusedException {
        Consumer c = consumerCurator.findByUuid(consumeruuid);
        return bindByPoolQuantities(c, poolIdAndQuantities);
    }

    public List<Entitlement> bindByPoolQuantity(Consumer consumer, String poolId, Integer quantity) {
        Map<String, Integer> poolMap = new HashMap<String, Integer>();
        poolMap.put(poolId, quantity);
        try {
            return bindByPoolQuantities(consumer, poolMap);
        }
        catch (EntitlementRefusedException e) {
            // TODO: Could be multiple errors, but we'll just report the first
            // one for now
            Pool pool = poolCurator.find(poolId);
            throw new ForbiddenException(messageTranslator.poolErrorToMessage(
                pool, e.getResults().get(poolId).getErrors().get(0)
            ));
        }
    }

    public List<Entitlement> bindByPoolQuantities(Consumer consumer,
        Map<String, Integer> poolIdAndQuantities) throws EntitlementRefusedException {
        // Attempt to create entitlements:
        try {
            List<Entitlement> entitlementList = poolManager.entitleByPools(consumer, poolIdAndQuantities);
            log.debug("Created {} entitlements.", entitlementList.size());
            return entitlementList;
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
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
            throw new ForbiddenException(messageTranslator.entitlementErrorToMessage(
                ent, e.getResults().values().iterator().next().getErrors().get(0))
            );
        }
    }

    public List<Entitlement> bindByProducts(String[] productIds,
        String consumeruuid, Date entitleDate, Collection<String> fromPools)
        throws AutobindDisabledForOwnerException {
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
     * @throws AutobindDisabledForOwnerException when an autobind attempt is made and the owner
     *         has it disabled.
     */
    public List<Entitlement> bindByProducts(AutobindData data) throws AutobindDisabledForOwnerException {
        return bindByProducts(data, false);
    }

    /**
     *
     * Force option is used to heal entire org
     *
     * @param data AutobindData encapsulating data required for an autobind request
     * @param force heal host even if it has autoheal disabled
     * @return List of Entitlements
     * @throws AutobindDisabledForOwnerException when an autobind attempt is made and the owner
     *         has it disabled.
     */
    @Transactional
    public List<Entitlement> bindByProducts(AutobindData data, boolean force)
        throws AutobindDisabledForOwnerException {
        Consumer consumer = data.getConsumer();
        Owner owner = consumer.getOwner();

        if (!consumer.isDev() && owner.isAutobindDisabled()) {
            log.info("Skipping auto-attach for consumer '{}'. Auto-attach is disabled for owner {}.",
                consumer, owner.getKey());
            throw new AutobindDisabledForOwnerException(i18n.tr("Auto-attach is disabled for owner '{0}'.",
                owner.getKey()));
        }

        // If the consumer is a guest, and has a host, try to heal the host first
        // Dev consumers should not need to worry about the host or unmapped guest
        // entitlements based on the planned design of the subscriptions
        if (consumer.hasFact("virt.uuid") && !consumer.isDev()) {
            String guestUuid = consumer.getFact("virt.uuid");
            // Remove any expired unmapped guest entitlements
            revokeUnmappedGuestEntitlements(consumer);

            // Scoped to the consumer's organization.  Even in the event of sharing, a guest in one
            // organization should not be able to compel a heal in an another organization
            Consumer host = consumerCurator.getHost(consumer, consumer.getOwner());
            if (host != null && (force || host.isAutoheal())) {
                log.info("Attempting to heal host machine with UUID \"{}\" for guest with UUID \"{}\"",
                    host.getUuid(), consumer.getUuid());
                if (!StringUtils.equals(host.getServiceLevel(), consumer.getServiceLevel())) {
                    log.warn("Host with UUID \"{}\" has a service level \"{}\" that does not match" +
                        " that of the guest with UUID \"{}\" and service level \"{}\"",
                        host.getUuid(), host.getServiceLevel(),
                        consumer.getUuid(), consumer.getServiceLevel());
                }
                try {
                    List<Entitlement> hostEntitlements = poolManager.entitleByProductsForHost(
                        consumer, host, data.getOnDate(), data.getPossiblePools());

                    log.debug("Granted host {} entitlements", hostEntitlements.size());
                    sendEvents(hostEntitlements);
                }
                catch (Exception e) {
                    //log and continue, this should NEVER block
                    log.debug("Healing failed for host UUID {} with message: {}",
                        host.getUuid(), e.getMessage());
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
                    " and with orgs that have active subscriptions."
                ));
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
            data.setProductIds(new String[]{sku});
        }

        // Attempt to create entitlements:
        try {
            // the pools are only used to bind the guest
            List<Entitlement> entitlements = poolManager.entitleByProducts(data);
            log.debug("Created entitlements: {}", entitlements);
            return entitlements;
        }
        catch (EntitlementRefusedException e) {
            // TODO: Could be multiple errors, but we'll just report the first one for now
            String productId = "Unknown Product";

            if (data.getProductIds().length > 0) {
                productId = data.getProductIds()[0];
            }

            throw new ForbiddenException(messageTranslator.productErrorToMessage(
                productId, e.getResults().values().iterator().next().getErrors().get(0)
            ));
        }
    }

    private Date getEndDate(Product prod, Date startTime) {
        int interval = maxDevLifeDays;
        String prodExp = prod.getAttributeValue(Product.Attributes.TTL);

        if (prodExp != null &&  Integer.parseInt(prodExp) < maxDevLifeDays) {
            interval = Integer.parseInt(prodExp);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(startTime);
        cal.add(Calendar.DAY_OF_YEAR, interval);

        return cal.getTime();
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
        Date endDate = getEndDate(skuProduct, startDate);
        Pool pool = new Pool(consumer.getOwner(), skuProduct, devProducts.getProvided(), 1L, startDate,
            endDate, "", "", "");

        log.info("Created development pool with SKU {}", skuProduct.getId());
        pool.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        pool.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, consumer.getUuid());
        return pool;
    }

    private DeveloperProducts getDeveloperPoolProducts(Consumer consumer, String sku) {
        DeveloperProducts devProducts = getDevProductMap(consumer, sku);
        verifyDevProducts(consumer, sku, devProducts);
        return devProducts;
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

        Owner owner = consumer.getOwner();
        Map<String, ProductData> productMap = new HashMap<String, ProductData>();
        Map<String, ContentData> contentMap = new HashMap<String, ContentData>();

        log.debug("Importing products for dev pool resolution...");
        for (ProductData product : this.productAdapter.getProductsByIds(owner, devProductIds)) {
            if (product == null) {
                continue;
            }

            if (sku.equals(product.getId()) &&
                StringUtils.isEmpty(product.getAttributeValue(Product.Attributes.SUPPORT_LEVEL))) {

                // if there is no SLA, apply the default
                product.setAttribute(Product.Attributes.SUPPORT_LEVEL, this.DEFAULT_DEV_SLA);
            }

            // Product is coming from an upstream source; lock it so only upstream can make
            // further changes to it.
            product.setLocked(true);

            ProductData existingProduct = productMap.get(product.getId());
            if (existingProduct != null && !existingProduct.equals(product)) {
                log.warn("Multiple versions of the same product received during dev pool resolution; " +
                    "discarding duplicate: {} => {}, {}",
                    product.getId(), existingProduct, product
                );
            }
            else {
                productMap.put(product.getId(), product);

                Collection<ProductContentData> pcdCollection = product.getProductContent();
                if (pcdCollection != null) {
                    for (ProductContentData pcd : pcdCollection) {
                        // Impl note:
                        // We aren't checking for duplicate mappings to the same content, since our
                        // current implementation of ProductDTO prevents such a thing. However, if it
                        // is reasonably possible that we could end up with ProductDTO instances which
                        // do not prevent duplicate content mappings, we should add checks here to
                        // check for, and throw out, such mappings

                        if (pcd == null) {
                            log.error("product contains a null product-content mapping: {}", product);
                            throw new IllegalStateException(
                                "product contains a null product-content mapping: " + product);
                        }

                        ContentData content = pcd.getContent();

                        // Do some simple mapping validation. Our import method will handle minimal
                        // population validation for us.
                        if (content == null || content.getId() == null) {
                            log.error("product contains a null or incomplete product-content mapping: {}",
                                product);
                            throw new IllegalStateException("product contains a null or incomplete " +
                                "product-content mapping: " + product);
                        }

                        // We need to lock the incoming content here, but doing so will affect
                        // the equality comparison for products. We'll correct them later.

                        ContentData existingContent = contentMap.get(content.getId());
                        if (existingContent != null && !existingContent.equals(content)) {
                            log.warn("Multiple versions of the same content received during dev pool " +
                                "resolution; discarding duplicate: {} => {}, {}",
                                content.getId(), existingContent, content
                            );
                        }
                        else {
                            contentMap.put(content.getId(), content);
                        }
                    }
                }
            }
        }

        log.debug("Importing {} content...", contentMap.size());

        for (ContentData cdata : contentMap.values()) {
            cdata.setLocked(true);
        }

        Map<String, Content> importedContent = this.contentManager
            .importContent(owner, contentMap, productMap.keySet())
            .getImportedEntities();

        log.debug("Importing {} product(s)...", productMap.size());
        Map<String, Product> importedProducts = this.productManager
            .importProducts(owner, productMap, importedContent)
            .getImportedEntities();

        log.debug("Resolved {} dev product(s) for sku: {}", productMap.size(), sku);
        return new DeveloperProducts(sku, importedProducts);
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
                    "development unit: ''{0}''", ip.getProductId()));
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
                        " and with orgs that have active subscriptions."
                    ));
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
            log.debug("Created Pool Quantity list: {}", result);
        }
        catch (EntitlementRefusedException e) {
            // If we catch an exception we will just return an empty list
            // The dry run just reports that an autobind will have no pools
            // We will debug log the message, but returning does not seem to add
            // to the process
            if (log.isDebugEnabled()) {
                log.debug("consumer {} dry-run errors:", consumer.getUuid());
                for (Entry<String, ValidationResult> entry : e.getResults().entrySet()) {
                    log.debug("errors for pool id: {}", entry.getKey());
                    for (ValidationError error : entry.getValue().getErrors()) {
                        log.debug(error.getResourceKey());
                    }
                }
            }
        }
        return result;
    }

    public int revokeUnmappedGuestEntitlements(Consumer consumer) {
        int total = 0;

        CandlepinQuery<Entitlement> unmappedGuestEntitlements;

        if (consumer == null) {
            unmappedGuestEntitlements = entitlementCurator.findByPoolAttribute(
                "unmapped_guests_only", "true");
        }
        else {
            unmappedGuestEntitlements = entitlementCurator.findByPoolAttribute(
                consumer, "unmapped_guests_only", "true");
        }

        // TODO:
        // Make sure this doesn't choke on MySQL, since we're doing queries with the cursor open.
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

        public DeveloperProducts(String expectedSku, Map<String, Product> products) {
            this.sku = products.remove(expectedSku);
            this.provided = products;
        }

        public Product getSku() {
            return sku;
        }

        public Collection<Product> getProvided() {
            return provided.values();
        }

        public boolean foundSku() {
            return sku != null;
        }

        public boolean containsProduct(String productId) {
            return (foundSku() && productId.equals(sku.getId())) || provided.containsKey(productId);
        }

    }
}
