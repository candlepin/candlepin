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
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Branding;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;



/**
 * PoolManager
 */
public class CandlepinPoolManager implements PoolManager {
    private I18n i18n;

    private PoolCurator poolCurator;
    private static Logger log = LoggerFactory.getLogger(CandlepinPoolManager.class);

    private static final int MAX_ENTITLE_RETRIES = 3;

    private EventSink sink;
    private EventFactory eventFactory;
    private Configuration config;
    private Enforcer enforcer;
    private PoolRules poolRules;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private EntitlementCertificateCurator entitlementCertificateCurator;
    private EntitlementCertificateGenerator ecGenerator;
    private ComplianceRules complianceRules;
    private ProductCurator productCurator;
    private ProductManager productManager;
    private AutobindRules autobindRules;
    private ActivationKeyRules activationKeyRules;
    private ContentManager contentManager;
    private OwnerContentCurator ownerContentCurator;
    private OwnerCurator ownerCurator;
    private OwnerProductCurator ownerProductCurator;
    private PinsetterKernel pinsetterKernel;
    private OwnerManager ownerManager;

    /**
     * @param poolCurator
     * @param subAdapter
     * @param sink
     * @param eventFactory
     * @param config
     */
    @Inject
    public CandlepinPoolManager(
        PoolCurator poolCurator,
        EventSink sink,
        EventFactory eventFactory,
        Configuration config,
        Enforcer enforcer,
        PoolRules poolRules,
        EntitlementCurator entitlementCurator,
        ConsumerCurator consumerCurator,
        EntitlementCertificateCurator entitlementCertCurator,
        EntitlementCertificateGenerator ecGenerator,
        ComplianceRules complianceRules,
        AutobindRules autobindRules,
        ActivationKeyRules activationKeyRules,
        ProductCurator productCurator,
        ProductManager productManager,
        ContentManager contentManager,
        OwnerContentCurator ownerContentCurator,
        OwnerCurator ownerCurator,
        OwnerProductCurator ownerProductCurator,
        OwnerManager ownerManager,
        PinsetterKernel pinsetterKernel,
        I18n i18n) {

        this.poolCurator = poolCurator;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.config = config;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
        this.poolRules = poolRules;
        this.entitlementCertificateCurator = entitlementCertCurator;
        this.ecGenerator = ecGenerator;
        this.complianceRules = complianceRules;
        this.productCurator = productCurator;
        this.autobindRules = autobindRules;
        this.activationKeyRules = activationKeyRules;
        this.productCurator = productCurator;
        this.productManager = productManager;
        this.contentManager = contentManager;
        this.ownerContentCurator = ownerContentCurator;
        this.ownerCurator = ownerCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.ownerManager = ownerManager;
        this.pinsetterKernel = pinsetterKernel;
        this.i18n = i18n;
    }

    /*
     * We need to update/regen entitlements in the same transaction we update pools
     * so we don't miss anything
     */
    @Transactional
    @SuppressWarnings("checkstyle:methodlength")
    void refreshPoolsWithRegeneration(SubscriptionServiceAdapter subAdapter, Owner owner, boolean lazy) {
        long start = System.currentTimeMillis();
        owner = this.resolveOwner(owner);
        log.info("Refreshing pools for owner: {}", owner);

        Map<String, Subscription> subscriptionMap = new HashMap<String, Subscription>();
        Map<String, ProductData> productMap = new HashMap<String, ProductData>();
        Map<String, ContentData> contentMap = new HashMap<String, ContentData>();

        // Resolve all our subscriptions, products and content to ensure we don't have bad or
        // duplicate inbound data
        for (Subscription subscription : subAdapter.getSubscriptions(owner)) {
            if (subscription == null) {
                continue;
            }

            if (subscription.getId() == null) {
                log.error("subscription does not contain a mappable ID: {}", subscription);
                throw new IllegalStateException("subscription does not contain a mappable ID: " +
                    subscription);
            }

            Subscription existingSub = subscriptionMap.get(subscription.getId());
            if (existingSub != null && !existingSub.equals(subscription)) {
                log.warn("Multiple versions of the same subscription received during refresh; " +
                    "discarding duplicate: {} => {}, {}", subscription.getId(), existingSub, subscription);

                continue;
            }

            subscriptionMap.put(subscription.getId(), subscription);

            List<ProductData> products = new LinkedList<ProductData>();
            products.add(subscription.getProduct());
            products.add(subscription.getDerivedProduct());
            products.addAll(subscription.getProvidedProducts());
            products.addAll(subscription.getDerivedProvidedProducts());

            for (ProductData product : products) {
                if (product == null) {
                    // Impl note: This is a (mostly) safe condition, since it's valid for a
                    // subscription to have a null derived product. We'll just ignore it and plow
                    // forward.
                    continue;
                }

                if (product.getId() == null) {
                    log.error("product does not contain a mappable Red Hat ID: {}", product);
                    throw new IllegalStateException("product does not contain a mappable Red Hat ID: " +
                        product);
                }

                // Product is coming from an upstream source; lock it so only upstream can make
                // further changes to it.
                product.setLocked(true);

                ProductData existingProduct = productMap.get(product.getId());
                if (existingProduct != null && !existingProduct.equals(product)) {
                    log.warn("Multiple versions of the same product received during refresh; " +
                        "discarding duplicate: {} => {}, {}", product.getId(), existingProduct, product);
                }
                else {
                    productMap.put(product.getId(), product);

                    Collection<ProductContentData> pcdCollection = product.getProductContent();
                    if (pcdCollection != null) {
                        for (ProductContentData pcd : pcdCollection) {
                            // Impl note:
                            // We aren't checking for duplicate mappings to the same content, since our
                            // current implementation of ProductData prevents such a thing. However, if it
                            // is reasonably possible that we could end up with ProductData instances which
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
                                log.warn("Multiple versions of the same content received during refresh; " +
                                    "discarding duplicate: {} => {}, {}",
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
        }

        // Persist content changes
        log.debug("Importing {} content...", contentMap.size());

        // Lock our content
        // TODO: Find a more efficient way of doing this, preferably within this method
        for (ContentData cdata : contentMap.values()) {
            cdata.setLocked(true);
        }

        Map<String, Content> importedContent = this.contentManager
            .importContent(owner, contentMap, productMap.keySet())
            .getImportedEntities();

        log.debug("Importing {} product(s)...", productMap.size());
        ImportResult<Product> importResult = this.productManager
            .importProducts(owner, productMap, importedContent);

        Map<String, Product> importedProducts = importResult.getImportedEntities();
        Map<String, Product> updatedProducts = importResult.getUpdatedEntities();

        log.debug("Refreshing {} pool(s)...", subscriptionMap.size());
        Iterator<Map.Entry<String, Subscription>> subsIterator = subscriptionMap.entrySet().iterator();
        while (subsIterator.hasNext()) {
            Map.Entry<String, Subscription> entry = subsIterator.next();
            Subscription sub = entry.getValue();

            if (this.isExpired(sub)) {
                log.info("Skipping expired subscription: {}", sub);

                subsIterator.remove();
                continue;
            }

            log.debug("Processing subscription: {}", sub);

            Pool pool = this.convertToMasterPoolImpl(sub, owner, importedProducts);
            this.refreshPoolsForMasterPool(pool, false, lazy, updatedProducts);
        }

        // delete pools whose subscription disappeared:
        log.debug("Deleting pools for absent subscriptions...");
        List<Pool> poolsToDelete = new ArrayList<Pool>();
        for (Pool pool : poolCurator.getPoolsFromBadSubs(owner, subscriptionMap.keySet())) {
            if (pool.getSourceSubscription() != null && !pool.getType().isDerivedType()) {
                poolsToDelete.add(pool);
            }
        }

        deletePools(poolsToDelete);

        // TODO: break this call into smaller pieces. There may be lots of floating pools
        log.debug("Updating floating pools...");
        List<Pool> floatingPools = poolCurator.getOwnersFloatingPools(owner);
        updateFloatingPools(floatingPools, lazy, updatedProducts);

        log.info("Refresh pools for owner: {} completed in: {}ms", owner.getKey(),
            System.currentTimeMillis() - start);
    }

    private Owner resolveOwner(Owner owner) {
        if (owner == null || (owner.getKey() == null && owner.getId() == null)) {
            throw new IllegalArgumentException(
                i18n.tr("No owner specified, or owner lacks identifying information"));
        }

        if (owner.getKey() != null) {
            String ownerKey = owner.getKey();
            owner = ownerCurator.lookupByKey(owner.getKey());

            if (owner == null) {
                throw new IllegalStateException(
                    i18n.tr("Unable to find an owner with the key \"{0}\"", ownerKey));
            }
        }
        else {
            String id = owner.getId();
            owner = ownerCurator.find(owner.getId());

            if (owner == null) {
                throw new IllegalStateException(i18n.tr("Unable to find an owner with the ID \"{0}\"", id));
            }
        }

        return owner;
    }

    @Transactional
    void refreshPoolsForMasterPool(Pool pool, boolean updateStackDerived, boolean lazy,
        Map<String, Product> changedProducts) {

        // These don't all necessarily belong to this owner
        List<Pool> subscriptionPools = poolCurator.getPoolsBySubscriptionId(pool.getSubscriptionId());
        log.debug("Found {} pools for subscription {}", subscriptionPools.size(), pool.getSubscriptionId());
        if (log.isDebugEnabled()) {
            for (Pool p : subscriptionPools) {
                log.debug("    owner={} - {}", p.getOwner().getKey(), p);
            }
        }

        // Update product references on the pools, I guess
        // TODO: Should this be performed by poolRules? Seems like that should be a thing.
        for (Pool subPool : subscriptionPools) {
            Product product = subPool.getProduct();
            if (product != null) {
                Product update = changedProducts.get(product.getId());

                if (update != null) {
                    subPool.setProduct(update);
                }
            }

            product = subPool.getDerivedProduct();
            if (product != null) {
                Product update = changedProducts.get(product.getId());

                if (update != null) {
                    subPool.setDerivedProduct(update);
                }
            }
        }

        // Cleans up pools on other owners who have migrated subs away
        removeAndDeletePoolsOnOtherOwners(subscriptionPools, pool);

        // capture the original quantity to check for updates later
        Long originalQuantity = pool.getQuantity();

        // BZ 1012386: This will regenerate master/derived for bonus scenarios if only one of the
        // pair still exists.
        createAndEnrichPools(pool, subscriptionPools);

        // don't update floating here, we'll do that later so we don't update anything twice
        Set<String> updatedMasterPools = updatePoolsForMasterPool(
            subscriptionPools, pool, originalQuantity, updateStackDerived, changedProducts);

        regenerateCertificatesByEntIds(updatedMasterPools, lazy);
    }

    /**
     * Deletes all known expired pools. The deletion of expired pools also triggers entitlement
     * revocation and consumer compliance recalculation.
     * <p></p>
     * This method will delete pools in blocks, using a new transaction for each block unless a
     * transaction was already started before this method is called.
     */
    public void cleanupExpiredPools() {
        int count = 0;
        boolean loop;

        log.debug("Beginning cleanup expired pools job");

        do {
            // This call is run within a new transaction if we're not already in a transaction
            int blockSize = this.cleanupExpiredPoolsImpl();
            count += blockSize;

            loop = blockSize >= PoolCurator.EXPIRED_POOL_BLOCK_SIZE;
        } while (loop);

        if (count > 0) {
            log.info("Cleaned up {} expired pools", count);
        }
    }

    /**
     * Performs the cleanup of a block of expired pools.
     *
     * @return
     *  the number of expired pools deleted as a result of this method
     */
    @Transactional
    protected int cleanupExpiredPoolsImpl() {
        List<Pool> pools = poolCurator.listExpiredPools(PoolCurator.EXPIRED_POOL_BLOCK_SIZE);

        for (Pool pool : pools) {
            log.info("Cleaning up expired pool: {} (expired: {})", pool.getId(), pool.getEndDate());
        }

        // Delete the block of pools & flush the results to tell Hibernate to evict the objects
        // (we hope). Even if it doesn't, and even if the transaction completion is going to
        // flush the objects anyway, it should not hurt and is an explicit call.
        this.deletePools(pools);
        this.poolCurator.flush();

        return pools.size();
    }

    private boolean isExpired(Subscription subscription) {
        Date now = new Date();
        return now.after(subscription.getEndDate());
    }

    @Transactional
    private void deleteExcessEntitlements(List<Pool> existingPools) {
        if (CollectionUtils.isEmpty(existingPools)) {
            return;
        }

        List<Pool> overFlowingPools = new ArrayList<Pool>();
        for (Pool pool : existingPools) {
            if (pool.isOverflowing()) {
                overFlowingPools.add(pool);
            }
        }

        if (overFlowingPools.isEmpty()) {
            return;
        }

        boolean lifo = !config.getBoolean(ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER);
        List<Entitlement> freeEntitlements = this.poolCurator.retrieveFreeEntitlementsOfPools(
            overFlowingPools, lifo);
        List<Entitlement> entitlementsToDelete = new ArrayList<Entitlement>();
        Map<String, List<Entitlement>> poolSortedEntitlements = new HashMap<String, List<Entitlement>>();

        for (Entitlement entitlement : freeEntitlements) {
            Pool pool = entitlement.getPool();
            List<Entitlement> ents = poolSortedEntitlements.get(pool.getId());
            if (ents == null) {
                ents = new ArrayList<Entitlement>();
                poolSortedEntitlements.put(pool.getId(), ents);
            }

            ents.add(entitlement);
        }

        for (Pool pool : overFlowingPools) {
            List<Entitlement> freeEntitlementsForPool = poolSortedEntitlements.get(pool.getId());
            if (CollectionUtils.isEmpty(freeEntitlementsForPool)) {
                continue;
            }
            long consumed = pool.getConsumed();
            long existing = pool.getQuantity();
            Iterator<Entitlement> iter = freeEntitlementsForPool.iterator();
            while (consumed > existing && iter.hasNext()) {
                Entitlement e = iter.next();
                entitlementsToDelete.add(e);
                consumed -= e.getQuantity();
            }
        }

        revokeEntitlements(entitlementsToDelete);
    }

    void removeAndDeletePoolsOnOtherOwners(List<Pool> existingPools, Pool pool) {
        List<Pool> toRemove = new LinkedList<Pool>();
        for (Pool existing : existingPools) {
            if (!existing.getOwner().equals(pool.getOwner())) {
                toRemove.add(existing);
                log.warn("Removing {} because it exists in the wrong org", existing);
                if (existing.getType() == PoolType.NORMAL ||
                    existing.getType() == PoolType.BONUS) {
                    deletePool(existing);
                }
            }
        }
        existingPools.removeAll(toRemove);
    }

    /**
     * Update pool for master pool.
     *
     * @param existingPools the existing pools
     * @param pool the master pool
     * @param originalQuantity the pool's original quantity before multiplier was applied
     * @param updateStackDerived whether or not to attempt to update stack
     *        derived pools
     */
    Set<String> updatePoolsForMasterPool(List<Pool> existingPools, Pool pool, Long originalQuantity,
        boolean updateStackDerived, Map<String, Product> changedProducts) {

        /*
         * Rules need to determine which pools have changed, but the Java must
         * send out the events. Create an event for each pool that could change,
         * even if we won't use them all.
         */
        if (CollectionUtils.isEmpty(existingPools)) {
            return new HashSet<String>(0);
        }

        log.debug("Updating {} pools for existing master pool: {}", existingPools.size(), pool);

        Map<String, EventBuilder> poolEvents = new HashMap<String, EventBuilder>();
        for (Pool existing : existingPools) {
            EventBuilder eventBuilder = eventFactory
                .getEventBuilder(Target.POOL, Type.MODIFIED)
                .setOldEntity(existing);
            poolEvents.put(existing.getId(), eventBuilder);
        }

        // Hand off to rules to determine which pools need updating:
        List<PoolUpdate> updatedPools = poolRules.updatePools(pool, existingPools, originalQuantity,
            changedProducts);

        String virtLimit = pool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT);
        boolean createsSubPools = !StringUtils.isBlank(virtLimit) && !"0".equals(virtLimit);

        // Update subpools if necessary
        if (updateStackDerived && !updatedPools.isEmpty() &&
            createsSubPools && pool.isStacked()) {
            // Get all pools for the master pool owner derived from the pool's
            // stack id, because we cannot look it up by subscriptionId
            List<Pool> subPools = getOwnerSubPoolsForStackId(pool.getOwner(), pool.getStackId());

            for (Pool subPool : subPools) {
                PoolUpdate update = updatePoolFromStack(subPool, changedProducts);

                if (update.changed()) {
                    updatedPools.add(update);

                    EventBuilder eventBuilder = eventFactory
                        .getEventBuilder(Target.POOL, Type.MODIFIED)
                        .setOldEntity(subPool);

                    poolEvents.put(subPool.getId(), eventBuilder);
                }
            }
        }

        return processPoolUpdates(poolEvents, updatedPools);
    }

    protected Set<String> processPoolUpdates(
        Map<String, EventBuilder> poolEvents, List<PoolUpdate> updatedPools) {
        Set<String> entitlementsToRegen = Util.newSet();
        for (PoolUpdate updatedPool : updatedPools) {

            Pool existingPool = updatedPool.getPool();
            log.info("Pool changed: {}", updatedPool.toString());

            if (!poolCurator.exists(existingPool)) {
                log.info("Pool has already been deleted from the database.");
                continue;
            }

            // Delete pools the rules signal needed to be cleaned up:
            if (existingPool.isMarkedForDelete()) {
                log.warn("Deleting pool as requested by rules: {}", existingPool.getId());
                deletePool(existingPool);
                continue;
            }

            // save changes for the pool
            this.poolCurator.merge(existingPool);

            // Explicitly call flush to avoid issues with how we sync up the attributes.
            // This prevents "instance does not yet exist as a row in the database" errors
            // when we later try to lock the pool if we need to revoke entitlements:
            this.poolCurator.flush();

            // quantity has changed. delete any excess entitlements from pool
            if (updatedPool.getQuantityChanged()) {
                List<Pool> existingPools = Arrays.asList(existingPool);
                this.deleteExcessEntitlements(existingPools);
            }

            // dates changed. regenerate all entitlement certificates
            if (updatedPool.getDatesChanged() ||
                updatedPool.getProductsChanged() ||
                updatedPool.getBrandingChanged()) {
                List<String> entitlements = poolCurator.retrieveFreeEntitlementIdsOfPool(existingPool, true);
                entitlementsToRegen.addAll(entitlements);
            }

            EventBuilder builder = poolEvents.get(existingPool.getId());
            if (builder != null) {
                Event event = builder.setNewEntity(existingPool).buildEvent();
                sink.queueEvent(event);
            }
            else {
                log.warn("Pool updated without an event builder: {}", existingPool);
            }
        }

        return entitlementsToRegen;
    }

    /**
     * Update pools which have no subscription attached, if applicable.
     *
     * @param floatingPools
     * @return
     */
    @Transactional
    void updateFloatingPools(List<Pool> floatingPools, boolean lazy, Map<String, Product> changedProducts) {
        /*
         * Rules need to determine which pools have changed, but the Java must
         * send out the events. Create an event for each pool that could change,
         * even if we won't use them all.
         */
        Map<String, EventBuilder> poolEvents = new HashMap<String, EventBuilder>();
        for (Pool existing : floatingPools) {
            EventBuilder eventBuilder = eventFactory.getEventBuilder(Target.POOL, Type.MODIFIED)
                .setOldEntity(existing);
            poolEvents.put(existing.getId(), eventBuilder);
        }

        // Hand off to rules to determine which pools need updating:
        List<PoolUpdate> updatedPools = poolRules.updatePools(floatingPools, changedProducts);
        regenerateCertificatesByEntIds(processPoolUpdates(poolEvents, updatedPools), lazy);
    }

    /**
     * @param sub
     * @return the newly created Pools
     */
    @Override
    public List<Pool> createAndEnrichPools(Subscription sub) {
        return createAndEnrichPools(sub, Collections.<Pool>emptyList());
    }

    public List<Pool> createAndEnrichPools(Subscription sub, List<Pool> existingPools) {
        List<Pool> pools = poolRules.createAndEnrichPools(sub, existingPools);
        log.debug("Creating {} pools for subscription: {}", pools.size(), sub);
        for (Pool pool : pools) {
            createPool(pool);
        }

        return pools;
    }

    @Override
    public Pool createAndEnrichPools(Pool pool) {
        return createAndEnrichPools(pool, Collections.<Pool>emptyList());
    }

    @Override
    public Pool createAndEnrichPools(Pool pool, List<Pool> existingPools) {
        List<Pool> pools = poolRules.createAndEnrichPools(pool, existingPools);
        log.debug("Creating {} pools: ", pools.size());
        for (Pool p : pools) {
            createPool(p);
        }
        return pool;
    }

    @Override
    public Pool createPool(Pool pool) {
        Pool created = poolCurator.create(pool);
        log.debug("   new pool: {}", pool);

        if (created != null) {
            sink.emitPoolCreated(created);
        }

        return created;
    }

    @Override
    public List<Pool> createPools(List<Pool> pools) {
        if (CollectionUtils.isNotEmpty(pools)) {
            poolCurator.saveOrUpdateAll(pools, false, false);

            for (Pool pool : pools) {
                log.debug("   new pool: {}", pool);

                if (pool != null) {
                    sink.emitPoolCreated(pool);
                }
            }
        }
        return pools;
    }

    @Override
    public void updateMasterPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        // Ensure the subscription is not expired
        if (pool.getEndDate() != null && pool.getEndDate().before(new Date())) {
            List<String> subscriptions = new ArrayList<String>();
            subscriptions.add(pool.getSubscriptionId());
            this.deletePoolsForSubscriptions(subscriptions);
        }
        else {
            this.refreshPoolsForMasterPool(pool, false, true, Collections.<String, Product>emptyMap());
        }
    }

    @Override
    public void deletePoolsForSubscriptions(Collection<String> subscriptionIds) {
        if (subscriptionIds == null) {
            throw new IllegalArgumentException("subscriptionIds is null");
        }

        if (subscriptionIds.size() > 0) {
            for (Pool pool : this.poolCurator.getPoolsBySubscriptionIds(subscriptionIds)) {
                this.deletePool(pool);
            }
        }
    }

    /**
     * Builds a pool instance from the given subscription, using the specified owner and products
     * for resolution.
     * <p></p>
     * The provided owner and products will be used to match and resolve the owner and product
     * DTOs present on the subscription. If the subscription uses DTOs which cannot be resolved,
     * this method will throw an exception.
     *
     * @param sub
     *  The subscription to convert to a pool
     *
     * @param owner
     *  The owner the pool will be assigned to
     */
    private Pool convertToMasterPoolImpl(Subscription sub, Owner owner, Map<String, Product> productMap) {

        if (sub == null) {
            throw new IllegalArgumentException("subscription is null");
        }

        if (owner == null || (owner.getKey() == null && owner.getId() == null)) {
            throw new IllegalArgumentException("owner is null or incomplete");
        }

        if (productMap == null) {
            throw new IllegalArgumentException("productMap is null");
        }

        Pool pool = new Pool();

        // Validate and resolve owner...
        if (sub.getOwner() == null || (sub.getOwner().getId() != null ?
            !owner.getId().equals(sub.getOwner().getId()) :
            !owner.getKey().equals(sub.getOwner().getKey()))) {

            throw new IllegalStateException("Subscription references an invalid owner: " + sub.getOwner());
        }

        pool.setOwner(owner);
        pool.setQuantity(sub.getQuantity());
        pool.setStartDate(sub.getStartDate());
        pool.setEndDate(sub.getEndDate());
        pool.setContractNumber(sub.getContractNumber());
        pool.setAccountNumber(sub.getAccountNumber());
        pool.setOrderNumber(sub.getOrderNumber());

        // Copy over subscription details
        pool.setSubscriptionId(sub.getId());
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        // Copy over upstream details
        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());
        pool.setCdn(sub.getCdn());
        pool.setCertificate(sub.getCertificate());

        // Add in branding
        if (sub.getBranding() != null) {
            Set<Branding> branding = new HashSet<Branding>();

            for (Branding brand : sub.getBranding()) {
                // Impl note:
                // We create a new instance here since we don't have a separate branding DTO (yet),
                // and we need to be certain that we don't try to move or change a branding object
                // associated with another pool.
                branding.add(new Branding(brand.getProductId(), brand.getType(), brand.getName()));
            }

            pool.setBranding(branding);
        }

        if (sub.getProduct() == null || sub.getProduct().getId() == null) {
            throw new IllegalStateException("Subscription has no product, or its product is incomplete: " +
                sub.getProduct());
        }

        Product product = productMap.get(sub.getProduct().getId());
        if (product == null) {
            throw new IllegalStateException("Subscription references a product which cannot be resolved: " +
                sub.getProduct());
        }

        pool.setProduct(product);

        if (sub.getDerivedProduct() != null) {
            product = productMap.get(sub.getDerivedProduct().getId());

            if (product == null) {
                throw new IllegalStateException("Subscription's derived product references a product which " +
                    "cannot be resolved: " + sub.getDerivedProduct());
            }

            pool.setDerivedProduct(product);
        }

        if (sub.getProvidedProducts() != null) {
            Set<Product> products = new HashSet<Product>();

            for (ProductData pdata : sub.getProvidedProducts()) {
                if (pdata != null) {
                    product = productMap.get(pdata.getId());

                    if (product == null) {
                        throw new IllegalStateException("Subscription's provided products references a " +
                            "product which cannot be resolved: " + pdata);
                    }

                    products.add(product);
                }
            }

            pool.setProvidedProducts(products);
        }

        if (sub.getDerivedProvidedProducts() != null) {
            Set<Product> products = new HashSet<Product>();

            for (ProductData pdata : sub.getDerivedProvidedProducts()) {
                if (pdata != null) {
                    product = productMap.get(pdata.getId());

                    if (product == null) {
                        throw new IllegalStateException("Subscription's derived provided products " +
                            "references a product which cannot be resolved: " + pdata);
                    }

                    products.add(product);
                }
            }

            pool.setDerivedProvidedProducts(products);
        }

        return pool;
    }

    /*
     * if you are using this method, you might want to override the quantity
     * with PoolRules.calculateQuantity
     */
    @Override
    public Pool convertToMasterPool(Subscription sub) {
        if (sub == null) {
            throw new IllegalArgumentException("subscription is null");
        }

        // Resolve the subscription's owner...
        if (sub.getOwner() == null || (sub.getOwner().getId() == null && sub.getOwner().getKey() == null)) {
            throw new IllegalStateException("Subscription references an invalid owner: " + sub.getOwner());
        }

        Owner owner = sub.getOwner().getId() != null ?
            this.ownerCurator.find(sub.getOwner().getId()) :
            this.ownerCurator.lookupByKey(sub.getOwner().getKey());

        if (owner == null) {
            throw new IllegalStateException("Subscription references an owner which cannot be resolved: " +
                sub.getOwner());
        }

        // Gather the product IDs referenced by this subscription...
        Set<ProductData> productDTOs = new HashSet<ProductData>();
        Set<String> productIds = new HashSet<String>();
        Map<String, Product> productMap = new HashMap<String, Product>();

        productDTOs.add(sub.getProduct());
        productDTOs.add(sub.getDerivedProduct());

        if (sub.getProvidedProducts() != null) {
            productDTOs.addAll(sub.getProvidedProducts());
        }

        if (sub.getDerivedProvidedProducts() != null) {
            productDTOs.addAll(sub.getDerivedProvidedProducts());
        }

        for (ProductData pdata : productDTOs) {
            if (pdata != null) {
                if (pdata.getId() == null) {
                    throw new IllegalStateException("Subscription references an incomplete product: " +
                        pdata);
                }

                productIds.add(pdata.getId());
            }
        }

        // Build the product map from the product IDs we pulled off the subscription...
        for (Product product : this.ownerProductCurator.getProductsByIds(owner, productIds)) {
            productMap.put(product.getId(), product);
        }

        return this.convertToMasterPoolImpl(sub, owner, productMap);
    }

    // TODO:
    // Remove these methods or update them to properly mirror the curator.

    @Override
    public Pool find(String poolId) {
        return this.poolCurator.find(poolId);
    }

    @Override
    public List<Pool> secureFind(Collection<String> poolIds) {
        if (CollectionUtils.isNotEmpty(poolIds)) {
            return this.poolCurator.listAllByIds(poolIds).list();
        }

        return new ArrayList<Pool>();
    }

    @Override
    public List<Pool> lookupBySubscriptionId(Owner owner, String id) {
        return this.poolCurator.lookupBySubscriptionId(owner, id);
    }

    @Override
    public List<Pool> lookupBySubscriptionIds(Owner owner, Collection<String> subscriptionIds) {
        if (CollectionUtils.isNotEmpty(subscriptionIds)) {
            return this.poolCurator.lookupBySubscriptionIds(owner, subscriptionIds);
        }

        return new ArrayList<Pool>();
    }

    /**
     * Request an entitlement by product. If the entitlement cannot be granted,
     * null will be returned. TODO: Throw exception if entitlement not granted.
     * Report why.
     *
     * @param data Autobind data containing consumer, date, etc..
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    //
    // NOTE: after calling this method both entitlement pool and consumer
    // parameters will most certainly be stale. beware!
    @Override
    public List<Entitlement> entitleByProducts(AutobindData data) throws EntitlementRefusedException {
        int retries = MAX_ENTITLE_RETRIES;

        while (true) {
            try {
                return this.entitleByProductsImpl(data);
            }
            catch (EntitlementRefusedException e) {
                // if there are any pools that had only one error, and that was
                // an availability error, try again
                boolean retry = false;
                if (retries > 0) {
                    for (String poolId : e.getResults().keySet()) {

                        List<ValidationError> errors = e.getResults().get(poolId).getErrors();
                        if (errors.size() == 1 && errors.get(0).getResourceKey()
                            .equals("rulefailed.no.entitlements.available")) {
                            retry = true;
                            break;
                        }
                    }
                }
                if (retry) {
                    log.info("Entitlements exhausted between select best pools and bind operations;" +
                        " retrying");
                    retries--;
                    continue;
                }

                throw e;
            }
        }
    }

    /**
     * Performs the work of the entitleByProducts method in its own transaction to help unlock
     * pools which can no longer be bound.
     * <p></p>
     * This method should not be called directly, and is only declared protected to allow the
     * @Transactional annotation to function.
     *
     * @param data
     *  The autobind data to use for entitling a consumer
     *
     * @return
     *  a list of entitlements created as for this autobind operation
     */
    @Transactional
    protected List<Entitlement> entitleByProductsImpl(AutobindData data) throws EntitlementRefusedException {
        Consumer consumer = data.getConsumer();
        String[] productIds = data.getProductIds();
        Collection<String> fromPools = data.getPossiblePools();
        Date entitleDate = data.getOnDate();
        Owner owner = consumer.getOwner();

        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        // fromPools will be empty if the dev pool was already created.
        if (consumer != null && consumer.isDev() && !fromPools.isEmpty()) {
            String poolId = fromPools.iterator().next();
            PoolQuantity pq = new PoolQuantity(poolCurator.find(poolId), 1);
            bestPools.add(pq);
        }
        else {
            bestPools = getBestPools(consumer, productIds, entitleDate, owner, null, fromPools);
        }

        if (bestPools == null) {
            return null;
        }

        return entitleByPools(consumer, convertToMap(bestPools));
    }

    /**
     * Request an entitlement by product for a host system in
     * a host-guest relationship.  Allows getBestPoolsForHost
     * to choose products to bind.
     *
     * @param guest consumer requesting to have host entitled
     * @param host host consumer to entitle
     * @param entitleDate specific date to entitle by.
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    //
    // NOTE: after calling this method both entitlement pool and consumer
    // parameters will most certainly be stale. beware!
    @Override
    @Transactional
    public List<Entitlement> entitleByProductsForHost(Consumer guest, Consumer host, Date entitleDate,
        Collection<String> possiblePools) throws EntitlementRefusedException {

        host = consumerCurator.lockAndLoad(host);
        List<Entitlement> entitlements = new LinkedList<Entitlement>();
        if (!host.getOwner().equals(guest.getOwner())) {
            log.debug("Host {} and guest {} have different owners", host.getUuid(), guest.getUuid());
            return entitlements;
        }
        Owner owner = host.getOwner();

        // Use the current date if one wasn't provided:
        if (entitleDate == null) {
            entitleDate = new Date();
        }

        List<PoolQuantity> bestPools = getBestPoolsForHost(guest, host, entitleDate, owner, null,
            possiblePools);

        if (bestPools == null) {
            log.info("No entitlements for host: {}", host.getUuid());
            return null;
        }

        // now make the entitlements
        return entitleByPools(host, convertToMap(bestPools));
    }

    /**
     * Here we pick uncovered products from the guest where no virt-only
     * subscriptions exist, and have the host bind non-zero virt_limit
     * subscriptions in order to generate pools for the guest to bind later.
     *
     * @param guest whose products we want to provide
     * @param host to bind entitlements to
     * @param entitleDate
     * @param owner
     * @param serviceLevelOverride
     * @return PoolQuantity list to attempt to attach
     * @throws EntitlementRefusedException if unable to bind
     */
    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public List<PoolQuantity> getBestPoolsForHost(Consumer guest, Consumer host, Date entitleDate,
        Owner owner, String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException {

        Map<String, ValidationResult> failedResults = new HashMap<String, ValidationResult>();
        log.debug("Looking up best pools for host: {}", host);

        boolean tempLevel = false;
        if (StringUtils.isEmpty(host.getServiceLevel())) {
            host.setServiceLevel(guest.getServiceLevel());
            tempLevel = true;
        }

        Date activePoolDate = entitleDate;
        if (entitleDate == null) {
            activePoolDate = new Date();
        }
        PoolFilterBuilder poolFilter = new PoolFilterBuilder();
        poolFilter.addIdFilters(fromPools);
        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(
            host, null, owner, null, null, activePoolDate, false,
            poolFilter, null, false, false).getPageData();
        log.debug("Found {} total pools in org.", allOwnerPools.size());
        logPools(allOwnerPools);

        List<Pool> allOwnerPoolsForGuest = this.listAvailableEntitlementPools(
            guest, null, owner, null, null, activePoolDate,
            false, poolFilter,
            null, false, false).getPageData();
        log.debug("Found {} total pools already available for guest", allOwnerPoolsForGuest.size());
        logPools(allOwnerPoolsForGuest);

        for (Entitlement ent : host.getEntitlements()) {
            //filter out pools that are attached, there is no need to
            //complete partial stacks, as they are already granting
            //virtual pools
            log.debug("Removing pool host is already entitled to: {}", ent.getPool());
            allOwnerPools.remove(ent.getPool());
        }
        List<Pool> filteredPools = new LinkedList<Pool>();

        ComplianceStatus guestCompliance = complianceRules.getStatus(guest, entitleDate, false);
        Set<String> tmpSet = new HashSet<String>();
        //we only want to heal red products, not yellow
        tmpSet.addAll(guestCompliance.getNonCompliantProducts());
        log.debug("Guest's non-compliant products: {}", Util.collectionToString(tmpSet));

        /*Do not attempt to create subscriptions for products that
          already have virt_only pools available to the guest */
        Set<String> productsToRemove = getProductsToRemove(allOwnerPoolsForGuest, tmpSet);
        log.debug("Guest already will have virt-only pools to cover: {}",
            Util.collectionToString(productsToRemove));
        tmpSet.removeAll(productsToRemove);
        String[] productIds = tmpSet.toArray(new String [] {});

        if (log.isDebugEnabled()) {
            log.debug("Attempting host autobind for guest products: {}", Util.collectionToString(tmpSet));
        }

        // Bulk fetch our provided and derived provided product IDs so we're not hitting the DB
        // several times for this lookup.
        Map<String, Set<String>> providedProductIds = this.poolCurator
            .getProvidedProductIds(allOwnerPools);

        Map<String, Set<String>> derivedProvidedProductIds = this.poolCurator
            .getDerivedProvidedProductIds(allOwnerPools);

        for (Pool pool : allOwnerPools) {
            boolean providesProduct = false;
            // Would parse the int here, but it can be 'unlimited'
            // and we only need to check that it's non-zero
            if (pool.getProduct().hasAttribute(Product.Attributes.VIRT_LIMIT) &&
                !pool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT).equals("0")) {

                Map<String, Set<String>> providedProductMap;
                String baseProductId;

                // Determine which set of provided products we should use...
                if (pool.getDerivedProduct() != null) {
                    providedProductMap = derivedProvidedProductIds;
                    baseProductId = pool.getDerivedProduct().getId();
                }
                else {
                    providedProductMap = providedProductIds;
                    baseProductId = pool.getProduct().getId();
                }

                // Add the base product to the list of derived provided products...
                Set<String> poolProvidedProductIds = providedProductMap.get(pool.getId());
                if (baseProductId != null) {
                    if (poolProvidedProductIds != null) {
                        poolProvidedProductIds.add(baseProductId);
                    }
                    else {
                        poolProvidedProductIds = Collections.<String>singleton(baseProductId);
                    }
                }

                // Check if the pool provides any of the specified products
                if (poolProvidedProductIds != null) {
                    for (String productId : productIds) {
                        // If this is a derived pool, we need to see if the derived product
                        // provides anything for the guest, otherwise we use the parent.
                        if (poolProvidedProductIds.contains(productId)) {
                            log.debug("Found virt_limit pool providing product {}: {}", productId, pool);
                            providesProduct = true;
                            break;
                        }
                    }
                }
            }

            if (providesProduct) {
                ValidationResult result = enforcer.preEntitlement(host, pool, 1, CallerType.BEST_POOLS);

                if (result.hasErrors() || result.hasWarnings()) {
                    // Just keep the last one around, if we need it
                    failedResults.put(pool.getId(), result);
                    if (log.isDebugEnabled()) {
                        log.debug("Pool filtered from candidates due to failed rule(s): {}", pool);
                        log.debug("  warnings: {}", Util.collectionToString(result.getWarnings()));
                        log.debug("  errors: {}", Util.collectionToString(result.getErrors()));
                    }
                }
                else {
                    filteredPools.add(pool);
                }
            }
        }

        // Only throw refused exception if we actually hit the rules:
        if (filteredPools.size() == 0 && !failedResults.isEmpty()) {
            throw new EntitlementRefusedException(failedResults);
        }
        ComplianceStatus hostCompliance = complianceRules.getStatus(host, entitleDate, false);

        log.debug("Host pools being sent to rules: {}", filteredPools.size());
        logPools(filteredPools);
        List<PoolQuantity> enforced = autobindRules.selectBestPools(host,
            productIds, filteredPools, hostCompliance, serviceLevelOverride,
            poolCurator.retrieveServiceLevelsForOwner(owner, true), true);

        if (log.isDebugEnabled()) {
            log.debug("Host selectBestPools returned {} pools: ", enforced.size());
            for (PoolQuantity poolQuantity : enforced) {
                log.debug("  " + poolQuantity.getPool());
            }
        }

        if (tempLevel) {
            host.setServiceLevel("");

            // complianceRules.getStatus may have persisted the host with the temp service level,
            // so we need to be certain we undo that.
            consumerCurator.update(host);
        }

        return enforced;
    }

    /**
     * Do not attempt to create subscriptions for products that
     * already have virt_only pools available to the guest
     */
    private Set<String> getProductsToRemove(List<Pool> allOwnerPoolsForGuest, Set<String> tmpSet) {
        Set<String> productsToRemove = new HashSet<String>();

        // Bulk fetch our provided product IDs so we're not hitting the DB several times
        // for this lookup.
        Map<String, Set<String>> providedProductIds = this.poolCurator
            .getProvidedProductIds(allOwnerPoolsForGuest);

        for (Pool pool : allOwnerPoolsForGuest) {
            if (pool.getProduct() != null && (pool.getProduct().hasAttribute(Product.Attributes.VIRT_ONLY) ||
                pool.hasAttribute(Pool.Attributes.VIRT_ONLY))) {

                Set<String> poolProvidedProductIds = providedProductIds.get(pool.getId());
                String poolProductId = pool.getProduct().getId();

                if (poolProductId != null) {
                    // Add the base product to our list of "provided" products
                    if (poolProvidedProductIds != null) {
                        poolProvidedProductIds.add(poolProductId);
                    }
                    else {
                        poolProvidedProductIds = Collections.singleton(poolProductId);
                    }
                }

                if (poolProvidedProductIds != null) {
                    for (String prodId : tmpSet) {
                        if (poolProvidedProductIds.contains(prodId)) {
                            productsToRemove.add(prodId);
                        }
                    }
                }
            }
        }

        return productsToRemove;
    }

    private void logPools(Collection<Pool> pools) {
        if (log.isDebugEnabled()) {
            for (Pool p : pools) {
                log.debug("   " + p);
            }
        }
    }

    @Override
    public List<PoolQuantity> getBestPools(Consumer consumer,
        String[] productIds, Date entitleDate, Owner owner,
        String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException {

        Map<String, ValidationResult> failedResults = new HashMap<String, ValidationResult>();

        Date activePoolDate = entitleDate;
        if (entitleDate == null) {
            activePoolDate = new Date();
        }

        PoolFilterBuilder poolFilter = new PoolFilterBuilder();
        poolFilter.addIdFilters(fromPools);
        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(
            consumer, null, owner, null, null, activePoolDate, false,
            poolFilter, null, false, false).getPageData();
        List<Pool> filteredPools = new LinkedList<Pool>();

        // We have to check compliance status here so we can replace an empty
        // array of product IDs with the array the consumer actually needs. (i.e. during
        // a healing request)
        ComplianceStatus compliance = complianceRules.getStatus(consumer, entitleDate, false);
        if (productIds == null || productIds.length == 0) {
            log.debug("No products specified for bind, checking compliance to see what is needed.");
            Set<String> tmpSet = new HashSet<String>();
            tmpSet.addAll(compliance.getNonCompliantProducts());
            tmpSet.addAll(compliance.getPartiallyCompliantProducts().keySet());
            productIds = tmpSet.toArray(new String [] {});
        }

        if (log.isDebugEnabled()) {
            log.debug("Attempting for products on date: {}", entitleDate);
            for (String productId : productIds) {
                log.debug("  {}", productId);
            }
        }

        // Bulk fetch our provided product IDs so we're not hitting the DB several times
        // for this lookup.
        Map<String, Set<String>> providedProductIds = this.poolCurator.getProvidedProductIds(allOwnerPools);

        for (Pool pool : allOwnerPools) {
            boolean providesProduct = false;
            // We want to complete partial stacks if possible, even if they do not provide any products
            Product poolProduct = pool.getProduct();
            String stackingId = poolProduct.getAttributeValue(Product.Attributes.STACKING_ID);

            if (stackingId != null && compliance.getPartialStacks().containsKey(stackingId)) {
                providesProduct = true;
            }
            else {
                Set<String> poolProvidedProductIds = providedProductIds.get(pool.getId());
                String poolProductId = pool.getProduct() != null ? pool.getProduct().getId() : null;

                if (poolProductId != null) {
                    // Add the base product to our list of "provided" products
                    if (poolProvidedProductIds != null) {
                        poolProvidedProductIds.add(poolProductId);
                    }
                    else {
                        poolProvidedProductIds = Collections.singleton(poolProductId);
                    }
                }

                if (poolProvidedProductIds != null) {
                    for (String productId : productIds) {
                        if (poolProvidedProductIds.contains(productId)) {
                            providesProduct = true;
                            break;
                        }
                    }
                }
            }

            if (providesProduct) {
                ValidationResult result = enforcer.preEntitlement(consumer, pool, 1, CallerType.BEST_POOLS);

                if (result.hasErrors() || result.hasWarnings()) {
                    failedResults.put(pool.getId(), result);
                    log.debug("Pool filtered from candidates due to rules failure: {}", pool.getId());
                }
                else {
                    filteredPools.add(pool);
                }
            }
        }

        // Only throw refused exception if we actually hit the rules:
        if (filteredPools.size() == 0 && !failedResults.isEmpty()) {
            throw new EntitlementRefusedException(failedResults);
        }

        List<PoolQuantity> enforced = autobindRules.selectBestPools(consumer,
            productIds, filteredPools, compliance, serviceLevelOverride,
            poolCurator.retrieveServiceLevelsForOwner(owner, true), false);
        // Sort the resulting pools to avoid deadlocks
        Collections.sort(enforced);
        return enforced;
    }

    private Map<String, Integer> convertToMap(List<PoolQuantity> poolQuantities) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        for (PoolQuantity poolQuantity : poolQuantities) {
            result.put(poolQuantity.getPool().getId(), poolQuantity.getQuantity());
        }

        return result;
    }

    /**
     * Request an entitlement by poolid and quantity
     *
     * @param consumer consumer requesting to be entitled
     * @param poolQuantities a map of entitlement pool ids and the respective
     *        quantities to consume from
     * @return Entitlements A list of entitlements created if the request is
     *         successful
     * @throws EntitlementRefusedException if entitlement is refused
     */
    @Override
    @Transactional
    public List<Entitlement> entitleByPools(Consumer consumer, Map<String, Integer> poolQuantities)
        throws EntitlementRefusedException {
        if (MapUtils.isNotEmpty(poolQuantities)) {
            return createEntitlements(consumer, poolQuantities, CallerType.BIND);
        }
        return new ArrayList<Entitlement>();
    }

    @Override
    @Transactional
    /*
     * NOTE: please refer to the comment on create entitlements with respect to locking.
     */
    public Entitlement adjustEntitlementQuantity(Consumer consumer, Entitlement entitlement, Integer quantity)
        throws EntitlementRefusedException {
        int change = quantity - entitlement.getQuantity();
        if (change == 0) {
            return entitlement;
        }

        // Because there are several paths to this one place where entitlements
        // are updated, we cannot be positive the caller obtained a lock on the
        // pool when it was read. As such we're going to reload it with a lock
        // before starting this process.
        log.debug("Updating entitlement, Locking pool: {}", entitlement.getPool().getId());

        Pool pool = poolCurator.lockAndLoad(entitlement.getPool());

        if (log.isDebugEnabled()) {
            log.debug("Locked pool: {} consumed: {}", pool, pool.getConsumed());
        }

        if (pool == null) {
            throw new IllegalArgumentException(i18n.tr("Subscription pool {0} do not exist.",
                    pool.getId()));
        }

        ValidationResult result = enforcer.update(consumer, entitlement, change);
        if (!result.isSuccessful()) {
            log.warn("Entitlement not updated: {} for pool: {}",
                result.getErrors().toString(), pool.getId());

            Map<String, ValidationResult> errorMap = new HashMap<String, ValidationResult>();
            errorMap.put(pool.getId(), result);
            throw new EntitlementRefusedException(errorMap);
        }

        /*
         * Grab an exclusive lock on the consumer to prevent deadlock.
         */
        consumer = consumerCurator.lockAndLoad(consumer);

        // Persist the entitlement after it has been updated.
        log.info("Processing entitlement and persisting.");
        entitlement.setQuantity(entitlement.getQuantity() + change);
        entitlementCurator.merge(entitlement);

        pool.setConsumed(pool.getConsumed() + change);
        if (consumer.isManifestDistributor()) {
            pool.setExported(pool.getExported() + change);
        }
        poolCurator.merge(pool);
        consumer.setEntitlementCount(consumer.getEntitlementCount() + change);

        Map<String, Entitlement> entMap = new HashMap<String, Entitlement>();
        entMap.put(pool.getId(), entitlement);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, change));
        // the only thing we do here is decrement bonus pool quantity
        enforcer.postEntitlement(this,
            consumer,
            entMap,
            new ArrayList<Pool>(),
            true,
            poolQuantityMap);
        // we might have changed the bonus pool quantities, revoke ents if needed.
        checkBonusPoolQuantities(consumer.getOwner(), entMap);

        // if shared ents, update shared pool quantity
        if (consumer.isShare()) {
            pool.setShared(pool.getShared() + change);
            List<Pool> sharedPools = poolCurator.listBySourceEntitlement(entitlement).list();
            for (Pool p: sharedPools) {
                setPoolQuantity(p, entitlement.getQuantity().longValue());
            }
        }
        else {
            regenerateCertificatesOf(consumer, true);
            this.ecGenerator.regenerateCertificatesByEntitlementIds(
                this.entitlementCurator.batchListModifying(Collections.singleton(entitlement)), true
            );
        }

        /*
         * If the consumer is not a distributor or share, check consumer's new compliance
         * status and save. the getStatus call does that internally.
         * all consumer's entitlement count are updated though, so we need to update irrespective
         * of the consumer type.
         */
        complianceRules.getStatus(consumer, null, false, false);
        consumerCurator.update(consumer);

        poolCurator.flush();

        return entitlement;
    }

    /**
     * This transaction used to update consumer's status hash and got dead
     * locked because:
     * T1 and T2 are entitlement jobs
     *
     * 1. T1 grabs a shared lock on cp_consumer.id due to the FK in cp_entitlement
     *    when inserting into cp_entitlement
     * 2. T2 grabs a shared lock on cp_consumer.id due to the FK in cp_entitlement
     *    when inserting into cp_entitlement
     * 3. T1 attempts to grab an exclusive lock on cp_consumer.id for an
     *    update to cp_consumer's compliance hash.  T1 blocks waiting for the T2's
     *    shared lock to be released.
     * 4. T2 attempts to grab an exclusive lock on cp_consumer.id for an
     *    update to cp_consumer's compliance hash.
     * 5. Deadlock.  T2 is waiting for T1's shared lock to be released but
     *    T1 is waiting for T2's shared lock to be released.
     *
     * The solution is to create a longer transaction and grab an exclusive lock
     * on the cp_consumer row (using a select for update) at the start of the transaction.
     * The other thread will then wait for the exclusive lock to be released instead of
     * deadlocking.
     *
     * See BZ #1274074 and git history for details
     */
    @Transactional
    protected List<Entitlement> createEntitlements(Consumer consumer,
        Map<String, Integer> poolQuantityMap, CallerType caller)
        throws EntitlementRefusedException {

        // Because there are several paths to this one place where entitlements
        // are granted, we cannot be positive the caller obtained a lock on the
        // pool when it was read. As such we're going to reload it with a lock
        // before starting this process.
        log.debug("Locking pools: {}", poolQuantityMap.keySet());
        Collection<Pool> pools = poolCurator.lockAndLoadByIds(poolQuantityMap.keySet());

        if (log.isDebugEnabled()) {
            for (Pool pool : pools) {
                log.debug("Locked pool: {} consumed: {}", pool, pool.getConsumed());
            }
        }

        log.debug("Done locking pools");

        Map<String, PoolQuantity> poolQuantities = new HashMap<String, PoolQuantity>();
        boolean quantityFound = false;
        for (Pool pool : pools) {
            Integer quantity = poolQuantityMap.get(pool.getId());
            if (quantity > 0) {
                quantityFound = true;
            }

            poolQuantities.put(pool.getId(), new PoolQuantity(pool, quantity));
            poolQuantityMap.remove(pool.getId());
        }

        if (!poolQuantityMap.isEmpty()) {
            throw new IllegalArgumentException(i18n.tr("Subscription pool(s) {0} do not exist.",
                poolQuantityMap.keySet()));
        }

        if (quantityFound) {
            log.info("Running pre-entitlement rules.");
            // XXX preEntitlement is run twice for new entitlement creation
            Map<String, ValidationResult> results = enforcer.preEntitlement(consumer,
                poolQuantities.values(), caller);

            for (Entry<String, ValidationResult> entry : results.entrySet()) {
                ValidationResult result = entry.getValue();
                if (!result.isSuccessful()) {
                    log.warn("Entitlement not granted: {} for pool: {}",
                        result.getErrors().toString(), entry.getKey());

                    throw new EntitlementRefusedException(results);
                }
            }
        }

        /*
         * Grab an exclusive lock on the consumer to prevent deadlock.
         */
        consumer = consumerCurator.lockAndLoad(consumer);

        // Persist the entitlement after it has been created.  It requires an ID in order to
        // create an entitlement-derived subpool
        log.info("Processing entitlements and persisting.");
        Map<String, Entitlement> entitlements = handleEntitlement(consumer, poolQuantities);

        List<Pool> poolsToSave = new ArrayList<Pool>();
        for (PoolQuantity poolQuantity : poolQuantities.values()) {
            Pool pool = poolQuantity.getPool();
            Integer quantity = entitlements.get(pool.getId()).getQuantity();
            pool.setConsumed(pool.getConsumed() + quantity);
            if (consumer.isManifestDistributor()) {
                pool.setExported(pool.getExported() + quantity);
            }
            else if (consumer.isShare()) {
                pool.setShared(pool.getShared() + quantity);
            }
            consumer.setEntitlementCount(consumer.getEntitlementCount() + quantity);
            poolsToSave.add(pool);
        }
        poolCurator.updateAll(poolsToSave, false, false);

        handlePostEntitlement(this, consumer, entitlements, poolQuantities);
        // we might have changed the bonus pool quantities, lets revoke ents if needed.
        checkBonusPoolQuantities(consumer.getOwner(), entitlements);

        // shares don't need entitlement certificate since they don't talk to the CDN
        if (!consumer.isShare()) {
            handleSelfCertificates(consumer, poolQuantities, entitlements);
            this.ecGenerator.regenerateCertificatesByEntitlementIds(
                this.entitlementCurator.batchListModifying(entitlements.values()), true
            );
        }

        /*
         * If the consumer is not a distributor or share, check consumer's new compliance
         * status and save. The getStatus call does that internally.
         * All consumer's entitlement count are updated though, so we need to update irrespective
         * of the consumer type.
         */
        complianceRules.getStatus(consumer, null, false, false);
        consumerCurator.update(consumer);

        poolCurator.flush();

        return new ArrayList<Entitlement>(entitlements.values());
    }

    /**
     * This method will pull the bonus pools from a physical and make sure that
     *  the bonus pools are not over-consumed.
     *
     * @param owner
     * @param poolQuantities
     * @param entitlements
     */
    private void checkBonusPoolQuantities(Owner owner,
        Map<String, Entitlement> entitlements) {

        Set<String> excludePoolIds = new HashSet<String>();
        Map<String, Entitlement> subEntitlementMap = new HashMap<String, Entitlement>();
        for (Entry<String, Entitlement> entry : entitlements.entrySet()) {
            Pool pool = entry.getValue().getPool();
            subEntitlementMap.put(pool.getSubscriptionId(), entitlements.get(entry.getKey()));
            excludePoolIds.add(pool.getId());
        }

        List<Pool> overConsumedPools = poolCurator.lookupOversubscribedBySubscriptionIds(owner,
            subEntitlementMap);

        List<Pool> derivedPools = new ArrayList<Pool>();
        for (Pool pool : overConsumedPools) {
            if (pool.getQuantity() != -1 && !excludePoolIds.contains(pool.getId())) {
                derivedPools.add(pool);
            }
        }
        deleteExcessEntitlements(derivedPools);
    }

    @Override
    public void regenerateCertificatesOf(Consumer consumer, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(consumer, lazy);
    }

    @Transactional
    void regenerateCertificatesOf(Iterable<Entitlement> iterable, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(iterable, lazy);
    }

    @Transactional
    void regenerateCertificatesByEntIds(Iterable<String> iterable, boolean lazy) {
        this.ecGenerator.regenerateCertificatesByEntitlementIds(iterable, lazy);
    }

    /**
     * Used to regenerate certificates affected by a mass content promotion/demotion.
     *
     * WARNING: can be quite expensive, currently we must look up all entitlements in the
     * environment, all provided products for each entitlement, and check if any product
     * provides any of the modified content set IDs.
     *
     * @param e Environment where the content was promoted/demoted.
     * @param affectedContent List of content set IDs promoted/demoted.
     */
    @Override
    @Transactional
    public void regenerateCertificatesOf(Environment e, Set<String> affectedContent, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(e, affectedContent, lazy);
    }

    /**
     * @param e
     */
    @Override
    @Transactional
    public void regenerateCertificatesOf(Entitlement e, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(e, lazy);
    }

    @Override
    @Transactional
    public void regenerateCertificatesOf(Owner owner, String productId, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(owner, productId, lazy);
    }

    @Override
    public void revokeEntitlements(List<Entitlement> entsToRevoke) {
        revokeEntitlements(entsToRevoke, null, true);
    }

    public void revokeEntitlements(List<Entitlement> entsToRevoke, Set<String> alreadyDeletedPools) {
        revokeEntitlements(entsToRevoke, alreadyDeletedPools, true);
    }

    /**
     * Revokes the given set of entitlements.
     *
     * @param entsToRevoke entitlements to revoke
     * @param alreadyDeletedPools pools to skip deletion as they have already been deleted
     * @param regenCertsAndStatuses if this revocation should also trigger regeneration of certificates
     * and recomputation of statuses. For performance reasons some callers might
     * choose to set this to false.
     */
    @Transactional
    public void revokeEntitlements(List<Entitlement> entsToRevoke, Set<String> alreadyDeletedPools,
        boolean regenCertsAndStatuses) {
        if (log.isDebugEnabled()) {
            log.debug("Starting batch revoke of entitlements: {}", getEntIds(entsToRevoke));
        }
        if (CollectionUtils.isEmpty(entsToRevoke)) {
            return;
        }
        List<Pool> poolsToDelete = poolCurator.listBySourceEntitlements(entsToRevoke);
        if (log.isDebugEnabled()) {
            log.debug("Found additional pools to delete by source entitlements: {}",
                getPoolIds(poolsToDelete));
        }

        List<Pool> poolsToLock = new ArrayList<Pool>();
        poolsToLock.addAll(poolsToDelete);

        for (Entitlement ent: entsToRevoke) {
            poolsToLock.add(ent.getPool());

            // If we are deleting a developer entitlement, be sure to delete the
            // associated pool as well.
            if (ent.getPool() != null && ent.getPool().isDevelopmentPool()) {
                poolsToDelete.add(ent.getPool());
            }
        }

        poolCurator.lockAndLoad(poolsToLock);
        log.info("Batch revoking {} entitlements ", entsToRevoke.size());
        entsToRevoke = new ArrayList<Entitlement>(entsToRevoke);

        for (Pool pool : poolsToDelete) {
            for (Entitlement ent : pool.getEntitlements()) {
                ent.setDeletedFromPool(true);
                entsToRevoke.add(ent);
            }
        }

        log.debug("Adjusting consumed quantities on pools");
        List<Pool> poolsToSave = new ArrayList<Pool>();
        for (Entitlement ent : entsToRevoke) {
            //We need to trigger lazy load of provided products
            //to have access to those products later in this method.
            Pool pool = ent.getPool();
            int entQuantity = ent.getQuantity() != null ? ent.getQuantity() : 0;

            pool.setConsumed(pool.getConsumed() - entQuantity);
            Consumer consumer = ent.getConsumer();
            if (consumer.isManifestDistributor()) {
                pool.setExported(pool.getExported() - entQuantity);
            }
            else if (consumer.isShare()) {
                pool.setShared(pool.getShared() - entQuantity);
            }
            consumer.setEntitlementCount(consumer.getEntitlementCount() - entQuantity);
            consumerCurator.update(consumer);
            poolsToSave.add(pool);
        }
        poolCurator.updateAll(poolsToSave, false, false);

        /*
         * Before deleting the entitlements, we need to find out if there are any
         * modifier entitlements that need to have their certificates regenerated
         */
        if (regenCertsAndStatuses) {
            Collection<String> modifiedEntIds = this.entitlementCurator.batchListModifying(entsToRevoke);
            log.debug("Regenerating certificates for modifying entitlements: {}", modifiedEntIds);

            this.ecGenerator.regenerateCertificatesByEntitlementIds(modifiedEntIds,  true);
            log.debug("Modifier entitlements done.");
        }

        log.info("Starting batch delete of pools");
        poolCurator.batchDelete(poolsToDelete, alreadyDeletedPools);
        log.info("Starting batch delete of entitlements");
        entitlementCurator.batchDelete(entsToRevoke);
        log.info("Starting delete flush");
        entitlementCurator.flush();
        log.info("All deletes flushed successfully");

        Map<Consumer, List<Entitlement>> consumerSortedEntitlements = entitlementCurator
            .getDistinctConsumers(entsToRevoke);

        filterAndUpdateStackingEntitlements(consumerSortedEntitlements, alreadyDeletedPools);

        // post unbind actions
        for (Entitlement ent : entsToRevoke) {
            enforcer.postUnbind(ent.getConsumer(), this, ent);
        }

        if (!regenCertsAndStatuses) {
            log.info("Regeneration and status computation was not requested finishing batch revoke");

            sendDeletedEvents(entsToRevoke);
            return;
        }

        log.info("Recomputing status for {} consumers.", consumerSortedEntitlements.size());
        int i = 1;
        for (Consumer consumer : consumerSortedEntitlements.keySet()) {
            if (i++ % 1000 == 0) {
                consumerCurator.flush();
            }
            complianceRules.getStatus(consumer);
        }
        consumerCurator.flush();

        log.info("All statuses recomputed.");

        sendDeletedEvents(entsToRevoke);
    }

    private void sendDeletedEvents(List<Entitlement> entsToRevoke) {
        // for each deleted entitlement, create an event
        for (Entitlement entitlement : entsToRevoke) {
            if (entitlement.deletedFromPool()) { continue; }
            Consumer consumer = entitlement.getConsumer();
            Event event = eventFactory.entitlementDeleted(entitlement);
            if (!entitlement.isValid() && entitlement.getPool().isUnmappedGuestPool() &&
                consumerCurator.getHost(consumer.getFact("virt.uuid"), consumer.getOwner()) == null) {
                event = eventFactory.entitlementExpired(entitlement);
                event.setMessageText(event.getMessageText() + ": " +
                    i18n.tr("Unmapped guest entitlement expired without establishing a host/guest mapping."));
            }
            sink.queueEvent(event);
        }
    }

    /**
     * Helper method for log debug messages
     * @param entitlements
     * @return
     */
    private List<String> getEntIds(Collection<Entitlement> entitlements) {
        List<String> ids = new ArrayList<String>();
        for (Entitlement e : entitlements) {
            ids.add(e.getId());
        }
        return ids;
    }

    /**
     * Helper method for log debug messages
     * @param pools pools to get IDs for
     * @return List pool ID list
     */
    private List<String> getPoolIds(Collection<Pool> pools) {
        List<String> ids = new ArrayList<String>();
        for (Pool e : pools) {
            ids.add(e.getId());
        }
        return ids;
    }

    /**
     * Filter the given entitlements so that this method returns only
     * the entitlements that are part of some stack. Then update them
     * accordingly
     *
     * @param consumerSortedEntitlements Entitlements to be filtered
     * @param alreadyDeletedPools pools to skip deletion as they have already been deleted
     * @return Entitlements that are stacked
     */
    private void filterAndUpdateStackingEntitlements(
        Map<Consumer, List<Entitlement>> consumerSortedEntitlements, Set<String> alreadyDeletedPools) {
        Map<Consumer, List<Entitlement>> stackingEntitlements = new HashMap<Consumer, List<Entitlement>>();

        for (Consumer consumer : consumerSortedEntitlements.keySet()) {
            List<Entitlement> ents = consumerSortedEntitlements.get(consumer);
            if (CollectionUtils.isNotEmpty(ents)) {
                for (Entitlement ent : ents) {
                    Pool pool = ent.getPool();

                    if (!"true".equals(pool.getAttributeValue(Pool.Attributes.DERIVED_POOL)) &&
                        pool.getProduct().hasAttribute(Product.Attributes.STACKING_ID)) {
                        List<Entitlement> entList = stackingEntitlements.get(consumer);
                        if (entList == null) {
                            entList = new ArrayList<Entitlement>();
                            stackingEntitlements.put(consumer, entList);
                        }
                        entList.add(ent);
                    }
                }
            }
        }

        for (Entry<Consumer, List<Entitlement>> entry : stackingEntitlements.entrySet()) {
            if (log.isDebugEnabled()) {
                log.debug("Found {} stacking entitlements to delete for consumer: {}",
                    entry.getValue().size(), entry.getKey());
            }

            Set<String> stackIds = new HashSet<String>();
            for (Entitlement ent : entry.getValue()) {
                stackIds.add(ent.getPool().getStackId());
            }
            List<Pool> subPools = poolCurator.getSubPoolForStackIds(entry.getKey(), stackIds);
            if (CollectionUtils.isNotEmpty(subPools)) {
                poolRules.updatePoolsFromStack(entry.getKey(), subPools, alreadyDeletedPools, true);
            }
        }
    }

    @Override
    @Transactional
    public void revokeEntitlement(Entitlement entitlement) {
        revokeEntitlements(Collections.singletonList(entitlement));
    }

    @Override
    @Transactional
    public int revokeAllEntitlements(Consumer consumer) {
        return revokeAllEntitlements(consumer, true);
    }

    @Override
    @Transactional
    public int revokeAllEntitlements(Consumer consumer, boolean regenCertsAndStatuses) {
        List<Entitlement> entsToDelete = entitlementCurator.listByConsumer(consumer);
        revokeEntitlements(entsToDelete, null, regenCertsAndStatuses);
        return entsToDelete.size();
    }

    /**
     * Cleanup entitlements and safely delete the given pool.
     *
     * @param pool
     */
    @Override
    @Transactional
    public void deletePool(Pool pool) {
        Event event = eventFactory.poolDeleted(pool);

        // Must do a full revoke for all entitlements:
        for (Entitlement e : poolCurator.entitlementsIn(pool)) {
            e.setDeletedFromPool(true);
            revokeEntitlement(e);
        }

        poolCurator.delete(pool);
        sink.queueEvent(event);
    }

    @Override
    public void deletePools(List<Pool> pools) {
        deletePools(pools, null);
    }

    @Override
    @Transactional
    public void deletePools(List<Pool> pools, Set<String> alreadyDeletedPools) {
        if (pools == null || pools.isEmpty()) {
            return;
        }

        if (alreadyDeletedPools == null) {
            alreadyDeletedPools = new HashSet<String>();
        }

        if (log.isDebugEnabled()) {
            log.debug("Delete pools: {}", getPoolIds(pools));
        }

        List<Entitlement> entitlementsToRevoke = new LinkedList<Entitlement>();

        for (Pool p : pools) {
            if (log.isDebugEnabled()) {
                log.debug("Deletion of pool {} will revoke the following entitlements: {}",
                    p.getId(), getEntIds(p.getEntitlements()));
            }
            entitlementsToRevoke.addAll(p.getEntitlements());
        }

        if (!pools.isEmpty()) {
            revokeEntitlements(entitlementsToRevoke, alreadyDeletedPools);
            log.debug("Batch deleting pools after successful revocation");
            poolCurator.batchDelete(pools, alreadyDeletedPools);
        }

        for (Pool pool : pools) {
            Event event = eventFactory.poolDeleted(pool);
            sink.queueEvent(event);
        }
    }

    /**
     * Adjust the count of a pool. The caller does not have knowledge
     * of the current quantity. It only determines how much to adjust.
     *
     * @param pool The pool.
     * @param adjust the long amount to adjust ( +/-)
     * @return pool
     */
    @Override
    public Pool updatePoolQuantity(Pool pool, long adjust) {
        pool = poolCurator.lockAndLoad(pool);
        long newCount = pool.getQuantity() + adjust;
        if (newCount < 0) {
            newCount = 0;
        }
        pool.setQuantity(newCount);
        return poolCurator.merge(pool);
    }

    /**
     * Set the count of a pool. The caller sets the absolute quantity.
     *   Current use is setting unlimited bonus pool to -1 or 0.
     *
     * @param pool The pool.
     * @param set the long amount to set
     * @return pool
     */
    @Override
    public Pool setPoolQuantity(Pool pool, long set) {
        pool = poolCurator.lockAndLoad(pool);
        pool.setQuantity(set);
        return poolCurator.merge(pool);
    }

    @Override
    public void regenerateDirtyEntitlements(Consumer consumer) {
        this.regenerateDirtyEntitlements(this.entitlementCurator.listDirty(consumer));
    }

    @Override
    public void regenerateDirtyEntitlements(Iterable<Entitlement> entitlements) {
        if (entitlements != null) {
            for (Entitlement entitlement : entitlements) {
                if (entitlement.isDirty()) {
                    log.info("Found dirty entitlement to regenerate: {}", entitlement);
                    this.ecGenerator.regenerateCertificatesOf(entitlement, false);
                }
            }
        }
    }

    @Override
    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter, OwnerServiceAdapter ownerAdapter) {
        return this.getRefresher(subAdapter, ownerAdapter, true);
    }

    @Override
    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter, OwnerServiceAdapter ownerAdapter,
        boolean lazy) {

        return new Refresher(this, subAdapter, ownerAdapter, ownerManager, lazy);
    }

    public Map<String, Entitlement> handleEntitlement(Consumer consumer,
        Map<String, PoolQuantity> poolQuantities) {

        List<Entitlement> entsToPersist = new ArrayList<Entitlement>();
        Map<String, Entitlement> result = new HashMap<String, Entitlement>();

        for (Entry<String, PoolQuantity> entry : poolQuantities.entrySet()) {
            Entitlement newEntitlement = new Entitlement(
                entry.getValue().getPool(), consumer, entry.getValue().getQuantity());

            entsToPersist.add(newEntitlement);
            result.put(entry.getKey(), newEntitlement);
        }

        entitlementCurator.saveOrUpdateAll(entsToPersist, false, false);

        /*
         * Why iterate twice? to persist the entitlement before we associate
         * it with the pool or consumer. This is important because we use Id
         * to compare Entitlements in the equals method.
        */
        for (Entry<String, PoolQuantity> entry : poolQuantities.entrySet()) {
            Entitlement e = result.get(entry.getKey());
            consumer.addEntitlement(e);
            entry.getValue().getPool().getEntitlements().add(e);
        }

        return result;
    }

    public void handlePostEntitlement(PoolManager manager, Consumer consumer,
        Map<String, Entitlement> entitlements, Map<String, PoolQuantity> poolQuantityMap) {
        Set<String> stackIds = new HashSet<String>();
        for (Entitlement entitlement : entitlements.values()) {
            if (entitlement.getPool().isStacked()) {
                stackIds.add(entitlement.getPool().getStackId());
            }
        }
        List<Pool> subPoolsForStackIds = null;
        // Manifest and Share consumers should not contribute to the sharing org's stack,
        // as these consumer types should not have created a stack derived pool in the first place.
        // Therefore, we do not need to check if any stack derived pools need updating
        if (!stackIds.isEmpty() && !consumer.isShare() && !consumer.isManifestDistributor()) {
            subPoolsForStackIds = poolCurator.getSubPoolForStackIds(consumer, stackIds);
            if (CollectionUtils.isNotEmpty(subPoolsForStackIds)) {
                poolRules.updatePoolsFromStack(consumer, subPoolsForStackIds, false);
                poolCurator.mergeAll(subPoolsForStackIds, false);
            }
        }
        else {
            subPoolsForStackIds = new ArrayList<Pool>();
        }

        enforcer.postEntitlement(manager,
            consumer,
            entitlements,
            subPoolsForStackIds,
            false,
            poolQuantityMap);
    }

    public void handleSelfCertificates(Consumer consumer, Map<String, PoolQuantity> poolQuantities,
        Map<String, Entitlement> entitlements) {
        Map<String, Product> products = new HashMap<String, Product>();
        for (PoolQuantity poolQuantity : poolQuantities.values()) {
            Pool pool = poolQuantity.getPool();
            products.put(pool.getId(), pool.getProduct());
        }

        ecGenerator.generateEntitlementCertificates(consumer, products, entitlements);
    }

    @Override
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer,
        ActivationKey key, Owner owner, String productId, String subscriptionId, Date activeOn,
        boolean includeWarnings, PoolFilterBuilder filters,
        PageRequest pageRequest, boolean addFuture, boolean onlyFuture) {

        // Only postfilter if we have to
        boolean postFilter = consumer != null || key != null;

        if (consumer != null && !consumer.isDev()) {
            filters.addAttributeFilter(Pool.Attributes.DEVELOPMENT_POOL, "!true");
        }

        Page<List<Pool>> page = this.poolCurator.listAvailableEntitlementPools(consumer,
            owner, productId, subscriptionId, activeOn, filters, pageRequest, postFilter,
            addFuture, onlyFuture);

        if (consumer == null && key == null) {
            return page;
        }

        // If the consumer was specified, we need to filter out any
        // pools that the consumer will not be able to attach.
        // If querying for pools available to a specific consumer, we need
        // to do a rules pass to verify the entitlement will be granted.
        // Note that something could change between the time we list a pool as
        // available, and the consumer requests the actual entitlement, and the
        // request still could fail.
        List<Pool> resultingPools = page.getPageData();

        if (consumer != null) {
            resultingPools = enforcer.filterPools(consumer, resultingPools, includeWarnings);
        }

        if (key != null) {
            resultingPools = this.filterPoolsForActKey(key, resultingPools, includeWarnings);
        }

        // Set maxRecords once we are done filtering
        page.setMaxRecords(resultingPools.size());

        if (pageRequest != null && pageRequest.isPaging()) {
            resultingPools = poolCurator.takeSubList(pageRequest, resultingPools);
        }

        page.setPageData(resultingPools);
        return page;
    }

    /**
     * Creates a Subscription object using information derived from the specified pool. Used to
     * support deprecated API calls that still require a subscription.
     *
     * @param pool
     *  The pool from which to build a subscription
     *
     * @return
     *  a new subscription object derived from the specified pool.
     */
    @Override
    public Subscription fabricateSubscriptionFromPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        Long poolQuantity = pool.getQuantity();
        Long multiplier = 1L;

        if (pool.getProduct() != null) {
            multiplier = pool.getProduct().getMultiplier();
        }
        else {
            log.error("Master product for the pool {} is null", pool.getId());
        }


        /*
         * The following code reconstructs Subscription quantity from the Pool quantity.
         * To understand it, it is important to understand how pool (the parameter)
         * is created in candlepin from a source subscription.
         * The pool has quantity was computed from
         * source subscription quantity and was multiplied by product.multiplier.
         * To reconstruct subscription, we must therefore divide the quantity of the pool
         * by the product.multiplier.
         * It's not easy to find COMPLETE code related to the conversion of
         * subscription to the pool. There is a method convertToMasterPool in this class,
         * that should do part of that (multiplication is not there).
         * But looking at its javadoc, it directly instructs callers of the
         * convertToMasterPool method to override quantity with method
         * PoolRules.calculateQuantity (when browsing the code that calls convertToMasterPool,
         * the calculateQuantity is usually called after convertToMasterPool).
         * The method PoolRules.calculateQuantity does the actual
         * multiplication of pool.quantity by pool.product.multiplier.
         * It seems that we also need to account account for
         * instance_multiplier (again logic is in calculateQuantity). If the attribute
         * is present, we must further divide the poolQuantity by
         * product.getAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER).
         */
        Product sku = pool.getProduct();

        if (poolQuantity != null && multiplier != null && multiplier != 0 && sku != null) {
            if (poolQuantity % multiplier != 0) {
                log.error("Pool {} from which we fabricate subscription has quantity {} that " +
                    "is not divisable by its product's multiplier {}! Division wont be made.",
                    pool.getId(), poolQuantity, multiplier);
            }
            else {
                poolQuantity /= multiplier;
            }

            //This is reverse of what part of PooRules.calculateQuantity does. See that method
            //to understand why we check that upstreamPoolId must be null.
            if (sku.hasAttribute(Product.Attributes.INSTANCE_MULTIPLIER) &&
                pool.getUpstreamPoolId() == null) {

                Integer instMult = null;
                String stringInstmult = sku.getAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER);
                try {
                    instMult = Integer.parseInt(stringInstmult);
                }
                catch (NumberFormatException nfe) {
                    log.error("Instance multilier couldn't be parsed: {} ", stringInstmult);
                }
                if (instMult != null && instMult != 0 && poolQuantity % instMult == 0) {
                    poolQuantity /=  instMult;
                }
                else {
                    log.error("Cannot divide pool quantity by instance multiplier. Won't touch the " +
                        "current value {} instance multiplier: {}, pool quantity: {}",
                        poolQuantity, instMult, poolQuantity);
                }
            }
        }
        else {
            log.warn("Either quantity or multiplier or product is null: {}, {}, productInstance={}",
                poolQuantity, multiplier, sku);
        }

        Subscription subscription = new Subscription(pool, productCurator);
        subscription.setQuantity(poolQuantity);

        return subscription;
    }

    @Override
    public List<Pool> getPoolsBySubscriptionId(String subscriptionId) {
        return this.poolCurator.getPoolsBySubscriptionId(subscriptionId);
    }

    @Override
    public Pool getMasterPoolBySubscriptionId(String subscriptionId) {
        return this.poolCurator.getMasterPoolBySubscriptionId(subscriptionId);
    }

    @Override
    public List<Pool> listMasterPools() {
        return this.poolCurator.listMasterPools();
    }

    @Override
    public Set<String> retrieveServiceLevelsForOwner(Owner owner, boolean exempt) {
        return poolCurator.retrieveServiceLevelsForOwner(owner, exempt);
    }

    @Override
    public List<Entitlement> findEntitlements(Pool pool) {
        return poolCurator.entitlementsIn(pool);
    }

    @Override
    public CandlepinQuery<Pool> listPoolsByOwner(Owner owner) {
        return poolCurator.listByOwner(owner);
    }

    public PoolUpdate updatePoolFromStack(Pool pool, Map<String, Product> changedProducts) {
        return poolRules.updatePoolFromStack(pool, changedProducts);
    }

    @Override
    public void updatePoolsFromStack(Consumer consumer, List<Pool> pools) {
        poolRules.updatePoolsFromStack(consumer, pools, false);
    }

    public List<Pool> getOwnerSubPoolsForStackId(Owner owner, String stackId) {
        return poolCurator.getOwnerSubPoolsForStackId(owner, stackId);
    }

    private List<Pool> filterPoolsForActKey(ActivationKey key,
        List<Pool> pools, boolean includeWarnings) {
        List<Pool> filteredPools = new LinkedList<Pool>();
        for (Pool p : pools) {
            ValidationResult result = activationKeyRules.runPreActKey(key, p, null);
            if (result.isSuccessful() && (!result.hasWarnings() || includeWarnings)) {
                filteredPools.add(p);
            }
            else if (log.isDebugEnabled()) {
                log.debug("Omitting pool due to failed rules: {}", p.getId());
                if (result.hasErrors()) {
                    log.debug("\tErrors: {}", result.getErrors());
                }
                if (result.hasWarnings()) {
                    log.debug("\tWarnings: {}", result.getWarnings());
                }
            }
        }
        return filteredPools;
    }

    public void recalculatePoolQuantitiesForOwner(Owner owner) {
        poolCurator.calculateConsumedForOwnersPools(owner);
        poolCurator.calculateExportedForOwnersPools(owner);
        poolCurator.calculateSharedForOwnerPools(owner);
    }
}
