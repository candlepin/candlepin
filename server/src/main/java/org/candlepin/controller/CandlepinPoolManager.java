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
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.tasks.ConsumerComplianceJob;
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
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.sync.SubscriptionReconciler;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.quartz.JobDetail;
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
    private ContentCurator contentCurator;
    private ContentManager contentManager;
    private OwnerCurator ownerCurator;
    private PinsetterKernel pinsetterKernel;

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
        ContentCurator contentCurator,
        ContentManager contentManager,
        OwnerCurator ownerCurator,
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
        this.contentCurator = contentCurator;
        this.contentManager = contentManager;
        this.ownerCurator = ownerCurator;
        this.pinsetterKernel = pinsetterKernel;
        this.i18n = i18n;
    }

    /*
     * We need to update/regen entitlements in the same transaction we update pools
     * so we don't miss anything
     */
    @Transactional
    void refreshPoolsWithRegeneration(SubscriptionServiceAdapter subAdapter, Owner owner, boolean lazy) {
        long start = System.currentTimeMillis();
        owner = this.refreshOwner(owner);
        log.info("Refreshing pools for owner: {}", owner);
        List<Subscription> subs = subAdapter.getSubscriptions(owner);

        log.debug("Found {} existing subscriptions.", subs.size());

        SubscriptionReconciler reconciler = new SubscriptionReconciler();
        reconciler.reconcile(owner, subs, poolCurator);

        Set<String> subIds = Util.newSet();

        refreshContent(owner, subs);
        Set<Product> changedProducts = refreshProducts(owner, subs);

        List<String> deletedSubs = new LinkedList<String>();
        for (Subscription sub : subs) {
            String subId = sub.getId();
            subIds.add(subId);

            log.debug("Processing subscription: {}", sub);

            // Remove expired subscriptions
            if (isExpired(sub)) {
                deletedSubs.add(subId);
                log.info("Skipping expired subscription: {}", sub);
                continue;
            }

            refreshPoolsForMasterPool(convertToMasterPool(sub), false, lazy, changedProducts);
        }

        Pool ueberPool = this.findUeberPool(owner);
        String ueberPoolId = ueberPool != null ? ueberPool.getId() : null;

        // We deleted some, need to take that into account so we
        // remove everything that isn't actually active
        subIds.removeAll(deletedSubs);

        // delete pools whose subscription disappeared:
        List<Pool> poolsToDelete = new ArrayList<Pool>();
        for (Pool pool : poolCurator.getPoolsFromBadSubs(owner, subIds)) {
            if (pool.getSourceSubscription() != null && !pool.getType().isDerivedType() &&
                (ueberPoolId == null || !ueberPoolId.equals(pool.getId()))) {
                poolsToDelete.add(pool);
            }
        }

        deletePools(poolsToDelete);

        // TODO: break this call into smaller pieces. There may be lots of floating pools
        List<Pool> floatingPools = poolCurator.getOwnersFloatingPools(owner);
        updateFloatingPools(floatingPools, lazy, changedProducts);
        log.info("Refresh pools for owner: {} completed in: {}ms", owner.getKey(),
            System.currentTimeMillis() - start);
    }

    private Owner refreshOwner(Owner owner) {
        if (owner == null || (owner.getKey() == null && owner.getId() == null)) {
            throw new IllegalArgumentException(
                i18n.tr("No owner specified, or owner lacks identifying information")
            );
        }

        if (owner.getKey() != null) {
            String ownerKey = owner.getKey();
            owner = ownerCurator.lookupByKey(owner.getKey());

            if (owner == null) {
                throw new IllegalStateException(
                    i18n.tr("Unable to find an owner with the key \"{0}\"", ownerKey)
                );
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

    /**
     * Refreshes the specified content under the given owner/org.
     *
     * @param owner
     *  The owner for which to refresh content
     *
     * @param subs
     *  The subscriptions from which to pull content
     *
     * @return
     *  the set of existing content which was updated/changed as a result of this operation
     */
    protected Set<Content> refreshContent(Owner owner, Collection<Subscription> subs) {
        // All inbound content, mapped by content ID. We're assuming it's all under the same org
        Map<String, Content> content = new HashMap<String, Content>();

        log.info("Refreshing content for {} subscriptions.", subs.size());

        for (Subscription sub : subs) {
            // Grab all the content from each product
            this.addProductContentToMap(content, sub.getProduct());
            this.addProductContentToMap(content, sub.getDerivedProduct());

            for (Product product : sub.getProvidedProducts()) {
                this.addProductContentToMap(content, product);
            }

            for (Product product : sub.getDerivedProvidedProducts()) {
                this.addProductContentToMap(content, product);
            }
        }

        Set<Content> changed = this.getChangedContent(owner, content);

        // We need to flush here to make sure our pending persists and merges get pushed to the DB
        // so our reference updates don't fail.
        this.contentCurator.flush();

        // Go back through each sub and update content references so we don't end up with dangling,
        // transient or duplicate references on any of the subs' products.
        for (Subscription sub : subs) {
            this.updateContentRefs(content, owner, sub.getProduct());
            this.updateContentRefs(content, owner, sub.getDerivedProduct());

            for (Product product : sub.getProvidedProducts()) {
                this.updateContentRefs(content, owner, product);
            }

            for (Product product : sub.getDerivedProvidedProducts()) {
                this.updateContentRefs(content, owner, product);
            }
        }

        return changed;
    }

    private void addProductContentToMap(Map<String, Content> contentMap, Product product) {
        if (product == null) {
            return;
        }

        Map<String, Content> mapped = new HashMap<String, Content>();
        List<ProductContent> duplicates = new LinkedList<ProductContent>();

        for (ProductContent pc : product.getProductContent()) {
            Content content = pc.getContent();

            // Check that this product isn't linking to the same content multiple times...
            if (mapped.containsKey(content.getId())) {
                log.warn(
                    "Multiple references to the same content found on a single product; " +
                    "discarding duplicate: {} => {}, {}",
                    product, content, mapped.get(content.getId())
                );

                duplicates.add(pc);
                continue;
            }

            mapped.put(content.getId(), content);


            // Check that the content hasn't changed if we've already seen it.
            Content existing = contentMap.get(content.getId());

            if (existing != null && !content.equals(existing)) {
                log.warn(
                    "Multiple versions of the same content found on a single subscription; " +
                    "discarding previous version: {} => {}, {}",
                    content.getId(), existing, content
                );
            }

            contentMap.put(content.getId(), content);
        }

        // Remove duplicate references from the product so we don't die trying to persist it...
        product.getProductContent().removeAll(duplicates);
    }

    private void updateContentRefs(Map<String, Content> contentCache, Owner owner, Product product) {
        if (product == null) {
            return;
        }

        for (ProductContent pc : product.getProductContent()) {
            Content content = pc.getContent();
            Content existing = this.contentCurator.lookupById(owner, content.getId());

            if (existing == null) {
                // This should never happen.
                throw new RuntimeException(String.format(
                    "Unable to resolve content reference: %s", content
                ));
            }

            pc.setContent(existing);
        }
    }

    public Set<Content> getChangedContent(Owner owner, Map<String, Content> contentCache) {
        Set<Content> changed = Util.newSet();

        log.debug("Syncing {} incoming content", contentCache.size());
        for (String cid : contentCache.keySet()) {
            // If cid and incoming.getId() aren't equal, we're in trouble.
            Content incoming = contentCache.get(cid);
            Content existing = this.contentCurator.lookupById(owner, cid);

            // Ensure the incoming content is linked to the owner that initiated the refresh
            incoming.setOwners(Arrays.asList(owner));

            if (existing == null) {
                log.info("Creating new content for org {}: {}", owner.getKey(), cid);

                // This is coming from a manifest or upstream; lock it so it can't be modified
                // with the API
                incoming.setLocked(true);

                incoming = this.contentManager.createContent(incoming, owner);
            }
            else if (!incoming.equals(existing)) {
                log.info("Updating existing content for org {}: {}", owner.getKey(), cid);

                incoming = this.contentManager.updateContent(incoming, owner, false);

                changed.add(incoming);
            }
        }

        return changed;
    }

    Set<Product> refreshProducts(Owner owner, List<Subscription> subs) {
        /*
         * Build a master list of all products on the incoming subscriptions. Note that
         * these product objects are detached, and need to be synced with what's in the
         * database.
         */
        Map<String, Product> products = new HashMap<String, Product>();

        log.info("Refreshing products for {} subscriptions.", subs.size());

        for (Subscription sub : subs) {
            this.addProductToMap(products, sub.getProduct());
            this.addProductToMap(products, sub.getDerivedProduct());

            for (Product product : sub.getProvidedProducts()) {
                this.addProductToMap(products, product);
            }

            for (Product product : sub.getDerivedProvidedProducts()) {
                this.addProductToMap(products, product);
            }
        }

        Set<Product> changed = this.getChangedProducts(owner, products);

        // We need to flush here to make sure our pending persists and merges get pushed to the DB
        // so our reference updates don't fail.
        this.productCurator.flush();

        // Go back through each sub and update product references so we don't end up with dangling,
        // transient or duplicate references on any of the subs.
        for (Subscription sub : subs) {
            sub.setProduct(this.updateProductRef(products, owner, sub.getProduct()));

            if (sub.getDerivedProduct() != null) {
                sub.setDerivedProduct(this.updateProductRef(products, owner, sub.getDerivedProduct()));
            }

            Set<Product> pset = new HashSet<Product>();
            for (Product product : sub.getProvidedProducts()) {
                pset.add(this.updateProductRef(products, owner, product));
            }
            sub.setProvidedProducts(pset);

            pset.clear();
            for (Product product : sub.getDerivedProvidedProducts()) {
                pset.add(this.updateProductRef(products, owner, product));
            }
            sub.setDerivedProvidedProducts(pset);
        }

        return changed;
    }

    private void addProductToMap(Map<String, Product> productMap, Product product) {
        if (product != null) {
            // Check that the product hasn't changed if we've already seen it.
            Product existing = productMap.get(product.getId());

            if (existing != null && existing.equals(product)) {
                log.warn(
                    "Multiple versions of the same product found on a single subscription; " +
                    "discarding previous version: {} => {}, {}",
                    product.getId(), existing, product
                );
            }

            productMap.put(product.getId(), product);
        }
    }

    private Product updateProductRef(Map<String, Product> productCache, Owner owner, Product product) {
        Product resolved = null;

        if (product != null) {
            resolved = this.productCurator.lookupById(owner, product.getId());

            if (resolved == null) {
                // This should never happen.
                throw new RuntimeException(String.format(
                    "Unable to resolve product reference for product: %s", product
                ));
            }
        }

        return resolved;
    }

    public Set<Product> getChangedProducts(Owner owner, Map<String, Product> productCache) {

        log.debug("Syncing {} incoming products", productCache.size());

        Set<Product> changedProducts = Util.newSet();

        for (String pid : productCache.keySet()) {
            // If the pid key and product.getId() don't match, we'll have some serious issues here.
            Product incoming = productCache.get(pid);
            Product existing = this.productCurator.lookupById(owner, pid);

            if (existing == null) {
                log.info("Creating new product for org {}: {}", owner.getKey(), pid);

                // This is coming from a manifest or upstream; lock it so it can't be modified
                // with the API
                incoming.setLocked(true);

                incoming = this.productManager.createProduct(incoming, owner);
            }
            else if (!existing.equals(incoming)) {
                log.info("Product changed for org {}: {}", owner.getKey(), pid);

                incoming = this.productManager.updateProduct(incoming, owner, false);

                changedProducts.add(incoming);
            }
        }

        return changedProducts;
    }

    @Transactional
    void refreshPoolsForMasterPool(Pool pool, boolean updateStackDerived, boolean lazy,
        Set<Product> changedProducts) {

        // These don't all necessarily belong to this owner
        List<Pool> subscriptionPools = poolCurator.getPoolsBySubscriptionId(pool.getSubscriptionId());
        log.debug("Found {} pools for subscription {}", subscriptionPools.size(), pool.getSubscriptionId());
        if (log.isDebugEnabled()) {
            for (Pool p : subscriptionPools) {
                log.debug("    owner={} - {}", p.getOwner().getKey(), p);
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

    @Transactional
    public void cleanupExpiredPools() {
        int count = 0;
        boolean loop = false;

        do {
            List<Pool> pools = poolCurator.listExpiredPools(PoolCurator.EXPIRED_POOL_BLOCK_SIZE);
            count += pools.size();

            // TODO: Do we still need this? This bit is incredibly slow.
            for (Iterator<Pool> pi = pools.iterator(); pi.hasNext();) {
                Pool pool = pi.next();
                log.info("Cleaning up expired pool: {} (expired: {})", pool.getId(), pool.getEndDate());
            }

            // Delete the block of pools
            this.deletePools(pools);

            // Cleanup memory to try to avoid flooding the heap
            this.poolCurator.clear();

            loop = pools.size() >= PoolCurator.EXPIRED_POOL_BLOCK_SIZE;
        } while (loop);

        if (count > 0) {
            log.info("Cleaned up {} expired pools", count);
        }
    }

    private boolean isExpired(Subscription subscription) {
        Date now = new Date();
        return now.after(subscription.getEndDate());
    }

    @Transactional
    private void deleteExcessEntitlements(List<Pool> existingPools) {
        boolean lifo = !config
            .getBoolean(ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER);
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
        boolean updateStackDerived, Set<Product> changedProducts) {

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

        String virtLimit = pool.getProduct().getAttributeValue("virt_limit");
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

        Set<String> entsToRegen = processPoolUpdates(poolEvents, updatedPools);
        return entsToRegen;
    }

    private Set<String> processPoolUpdates(
        Map<String, EventBuilder> poolEvents, List<PoolUpdate> updatedPools) {
        Set<String> entitlementsToRegen = Util.newSet();
        for (PoolUpdate updatedPool : updatedPools) {

            Pool existingPool = updatedPool.getPool();
            log.info("Pool changed: {}", updatedPool.toString());

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
                List<String> entitlements = poolCurator
                    .retrieveFreeEntitlementIdsOfPool(existingPool, true);
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
    void updateFloatingPools(List<Pool> floatingPools, boolean lazy, Set<Product> changedProducts) {
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
        return createAndEnrichPools(sub, new LinkedList<Pool>());
    }

    public List<Pool> createAndEnrichPools(Subscription sub, List<Pool> existingPools) {
        List<Pool> pools = poolRules.createAndEnrichPools(sub, existingPools);
        log.debug("Creating {} pools for subscription: ", pools.size());
        for (Pool pool : pools) {
            createPool(pool);
        }

        return pools;
    }

    @Override
    public Pool createAndEnrichPools(Pool pool) {
        return createAndEnrichPools(pool, new LinkedList<Pool>());
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
            poolCurator.saveOrUpdateAll(pools, false);

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
            this.refreshPoolsForMasterPool(pool, false, true, Collections.<Product>emptySet());
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

    /*
     * if you are using this method, you might want to override the quantity
     * with PoolRules.calculateQuantity
     */
    @Override
    public Pool convertToMasterPool(Subscription sub) {
        if (sub == null) {
            throw new IllegalArgumentException("subscription is null");
        }
        Pool pool = new Pool(sub.getOwner(), sub.getProduct(), sub.getProvidedProducts(),
            sub.getQuantity(), sub.getStartDate(), sub.getEndDate(), sub.getContractNumber(),
            sub.getAccountNumber(), sub.getOrderNumber());

        // Add all product references
        pool.setDerivedProduct(sub.getDerivedProduct());
        pool.setDerivedProvidedProducts(sub.getDerivedProvidedProducts());

        // Add in branding
        for (Branding b : sub.getBranding()) {
            pool.getBranding().add(new Branding(b.getProductId(), b.getType(), b.getName()));
        }
        pool.setSubscriptionId(sub.getId());
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        // Copy over upstream details...?
        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());
        pool.setCdn(sub.getCdn());
        pool.setCertificate(sub.getCertificate());
        return pool;
    }

    @Override
    public Pool find(String poolId) {
        return this.poolCurator.find(poolId);
    }

    @Override
    public List<Pool> secureFind(Collection<String> poolIds) {
        if (CollectionUtils.isNotEmpty(poolIds)) {
            return this.poolCurator.listAllByIds(poolIds);
        }
        return new ArrayList<Pool>();
    }

    @Override
    public List<Pool> lookupBySubscriptionId(String id) {
        return this.poolCurator.lookupBySubscriptionId(id);
    }

    @Override
    public List<Pool> lookupBySubscriptionIds(Collection<String> subscriptionIds) {
        if (CollectionUtils.isNotEmpty(subscriptionIds)) {
            return this.poolCurator.lookupBySubscriptionIds(subscriptionIds);
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
    @Transactional
    public List<Entitlement> entitleByProducts(AutobindData data)
        throws EntitlementRefusedException {
        Consumer consumer = data.getConsumer();
        String[] productIds = data.getProductIds();
        Collection<String> fromPools = data.getPossiblePools();
        Date entitleDate = data.getOnDate();
        Owner owner = consumer.getOwner();

        int retries = MAX_ENTITLE_RETRIES;

        while (true) {
            try {
                List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
                // fromPools will be empty if the dev pool was already created.
                if (consumer != null && consumer.isDev() && !fromPools.isEmpty()) {
                    String poolId = fromPools.iterator().next();
                    PoolQuantity pq = new PoolQuantity(poolCurator.find(poolId), 1);
                    bestPools.add(pq);
                }
                else {
                    bestPools = getBestPools(consumer, productIds,
                        entitleDate, owner, null, fromPools);
                }
                if (bestPools == null) {
                    List<String> fullList = new ArrayList<String>();
                    fullList.addAll(Arrays.asList(productIds));
                    for (ConsumerInstalledProduct cip : consumer.getInstalledProducts()) {
                        fullList.add(cip.getId());
                    }
                    log.info("No entitlements available for products: {}", fullList);
                    return null;
                }

                return entitleByPools(consumer, convertToMap(bestPools));
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
    public List<Entitlement> entitleByProductsForHost(Consumer guest, Consumer host,
        Date entitleDate, Collection<String> possiblePools)
        throws EntitlementRefusedException {
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

        List<PoolQuantity> bestPools = getBestPoolsForHost(guest, host,
            entitleDate, owner, null, possiblePools);

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
    public List<PoolQuantity> getBestPoolsForHost(Consumer guest,
        Consumer host, Date entitleDate, Owner owner,
        String serviceLevelOverride, Collection<String> fromPools)
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
            host, null, owner, (String) null, null, activePoolDate, true, false,
            poolFilter, null).getPageData();
        log.debug("Found {} total pools in org.", allOwnerPools.size());
        logPools(allOwnerPools);

        List<Pool> allOwnerPoolsForGuest = this.listAvailableEntitlementPools(
            guest, null, owner, (String) null, null, activePoolDate,
            true, false, poolFilter,
            null).getPageData();
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
            log.debug("Attempting host autobind for guest products: {}",
                Util.collectionToString(tmpSet));
        }

        for (Pool pool : allOwnerPools) {
            boolean providesProduct = false;
            // Would parse the int here, but it can be 'unlimited'
            // and we only need to check that it's non-zero
            if (pool.getProduct().hasAttribute("virt_limit") &&
                !pool.getProduct().getAttributeValue("virt_limit").equals("0")) {
                for (String productId : productIds) {
                    // If this is a derived pool, we need to see if the derived product
                    // provides anything for the guest, otherwise we use the parent.
                    if (pool.providesDerived(productId)) {
                        log.debug("Found virt_limit pool providing product {}: {}", productId, pool);
                        providesProduct = true;
                        break;
                    }
                }
            }
            if (providesProduct) {
                ValidationResult result = enforcer.preEntitlement(host,
                    pool, 1, CallerType.BEST_POOLS);

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
                log.debug("   " + poolQuantity.getPool());
            }
        }

        if (tempLevel) {
            host.setServiceLevel("");
        }
        return enforced;
    }

    /**
     * Do not attempt to create subscriptions for products that
     * already have virt_only pools available to the guest
     */
    private Set<String> getProductsToRemove(List<Pool> allOwnerPoolsForGuest, Set<String> tmpSet) {
        Set<String> productsToRemove = new HashSet<String>();
        for (Pool pool : allOwnerPoolsForGuest) {
            if (pool.getProduct().hasAttribute("virt_only") || pool.hasAttribute("virt_only")) {
                for (String prodId : tmpSet) {
                    if (pool.provides(prodId)) {
                        productsToRemove.add(prodId);
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
            consumer, null, owner, (String) null, null, activePoolDate, true, false,
            poolFilter, null).getPageData();
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
                log.debug("  " + productId);
            }
        }

        for (Pool pool : allOwnerPools) {
            boolean providesProduct = false;
            // If We want to complete partial stacks if possible,
            // even if they do not provide any products
            Product poolProduct = pool.getProduct();
            if (poolProduct.hasAttribute("stacking_id") &&
                compliance.getPartialStacks().containsKey(poolProduct.getAttributeValue("stacking_id"))) {
                providesProduct = true;
            }
            else {
                for (String productId : productIds) {
                    if (pool.provides(productId)) {
                        providesProduct = true;
                        break;
                    }
                }
            }
            if (providesProduct) {
                ValidationResult result = enforcer.preEntitlement(consumer,
                    pool, 1, CallerType.BEST_POOLS);

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
        if (enforced != null) {
            Collections.sort(enforced);
        }
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
            return addOrUpdateEntitlements(consumer, poolQuantities, null, false, CallerType.BIND);
        }
        return new ArrayList<Entitlement>();
    }

    @Override
    public Entitlement ueberCertEntitlement(Consumer consumer, Pool pool)
        throws EntitlementRefusedException {
        Map<String, Integer> poolQuantities = new HashMap<String, Integer>();
        poolQuantities.put(pool.getId(), 1);
        List<Entitlement> result = addOrUpdateEntitlements(consumer, poolQuantities, null, true,
            CallerType.UNKNOWN);
        if (CollectionUtils.isNotEmpty(result)) {
            // always going to be one entitlement for a single pool
            return result.get(0);
        }
        return null;
    }

    @Override
    public Entitlement adjustEntitlementQuantity(Consumer consumer,
        Entitlement entitlement, Integer quantity)
        throws EntitlementRefusedException {
        int change = quantity - entitlement.getQuantity();
        if (change == 0) {
            return entitlement;
        }
        Map<String, Integer> poolQuantities = new HashMap<String, Integer>();
        String poolId = entitlement.getPool().getId();
        poolQuantities.put(poolId, change);
        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(entitlement.getPool().getId(), entitlement);

        List<Entitlement> result = addOrUpdateEntitlements(
            consumer, poolQuantities, entitlements, true, CallerType.UNKNOWN);

        if (CollectionUtils.isNotEmpty(result)) {
            // always going to be one entitlement for a single pool
            return result.get(0);
        }

        return null;
    }

    /**
     * Some History, hopefully irrelavant henceforth:
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
     * The solution was to create a longer transaction and grab an exclusive lock
     * on the cp_consumer row (using a select for update) at the start of the transaction.
     * The other thread will then wait for the exclusive lock to be released instead of
     * deadlocking.
     *
     * Another effort on the solution removed compliance status evaluation from
     * this thread and created a separate asynchronous job to accomplish that,
     * thus removing the need to hold that exclusive lock on cp_consumer
     *
     * See BZ #1274074 and git history for details
     */
    @Transactional
    protected List<Entitlement> addOrUpdateEntitlements(Consumer consumer,
        Map<String, Integer> poolQuantityMap, Map<String, Entitlement> entitlements,
        boolean generateUeberCert, CallerType caller) throws EntitlementRefusedException {

        // Because there are several paths to this one place where entitlements
        // are granted, we cannot be positive the caller obtained a lock on the
        // pool when it was read. As such we're going to reload it with a lock
        // before starting this process.

        log.debug("Locking pools: {}", poolQuantityMap.keySet());

        List<Pool> pools = poolCurator.lockAndLoadBatch(poolQuantityMap.keySet());

        if (log.isDebugEnabled()) {
            for (Pool pool : pools) {
                log.debug("Locked pool: {} consumed: {}", pool, pool.getConsumed());
            }
        }

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

        EntitlementHandler handler = null;
        if (entitlements == null) {
            handler = new NewHandler();
        }
        else if (!poolQuantities.keySet().equals(entitlements.keySet())) {
            throw new IllegalArgumentException(i18n.tr(
                "Argument mismatch in entitlement update, number of pools: {}, number of entitlements: {}",
                entitlements.keySet().size(), poolQuantityMap.keySet().size()
            ));
        }
        else {
            handler = new UpdateHandler();
        }

        // Persist the entitlement after it has been created.  It requires an ID in order to
        // create an entitlement-derived subpool
        log.info("Processing entitlements and persisting.");
        entitlements = handler.handleEntitlement(consumer, poolQuantities, entitlements);

        // The quantity is calculated at fetch time. We update it here
        // To reflect what we just added to the db.
        for (PoolQuantity poolQuantity : poolQuantities.values()) {
            Pool pool = poolQuantity.getPool();
            Integer quantity = poolQuantity.getQuantity();
            pool.setConsumed(pool.getConsumed() + quantity);
            if (consumer.getType().isManifest()) {
                pool.setExported(pool.getExported() + quantity);
            }
        }

        handler.handlePostEntitlement(this, consumer, entitlements);
        handler.handleSelfCertificates(consumer, poolQuantities, entitlements, generateUeberCert);

        this.ecGenerator.regenerateCertificatesByEntitlementIds(
            this.entitlementCurator.batchListModifying(entitlements.values()), generateUeberCert, true
        );

        // we might have changed the bonus pool quantities, lets find out.
        handler.handleBonusPools(poolQuantities, entitlements);

        JobDetail detail = ConsumerComplianceJob.scheduleWithForceUpdate(consumer);
        detail.getJobDataMap().put(PinsetterJobListener.PRINCIPAL_KEY, new SystemPrincipal());

        log.info("Triggering ConsumerComplianceJob: {} for consumer: {}", detail.getKey(),
            consumer.getUuid());

        try {
            pinsetterKernel.scheduleSingleJob(detail);
        }
        catch (PinsetterException e) {
            log.error("ConsumerComplianceJob schedule failed", e.getMessage());
        }

        poolCurator.flush();

        return new ArrayList<Entitlement>(entitlements.values());
    }

    /**
     * This method will pull the bonus pools from a physical and make sure that
     *  the bonus pools are not over-consumed.
     *
     * @param consumer
     * @param pool
     */
    private void checkBonusPoolQuantities(Map<String, PoolQuantity> poolQuantities,
        Map<String, Entitlement> entitlements) {

        Set<String> excludePoolIds = new HashSet<String>();
        Map<String, Entitlement> subEntitlementMap = new HashMap<String, Entitlement>();
        for (Entry<String, PoolQuantity> entry : poolQuantities.entrySet()) {
            Pool pool = entry.getValue().getPool();
            subEntitlementMap.put(pool.getSubscriptionId(), entitlements.get(entry.getKey()));
            excludePoolIds.add(pool.getId());
        }

        List<Pool> overConsumedPools = poolCurator.lookupOversubscribedBySubscriptionIds(subEntitlementMap);

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
        this.ecGenerator.regenerateCertificatesByEntitlementIds(iterable, false, lazy);
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
    public void regenerateCertificatesOf(Entitlement e, boolean ueberCertificate, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(e, ueberCertificate, lazy);
    }

    @Override
    @Transactional
    public void regenerateCertificatesOf(Owner owner, String productId, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(owner, productId, lazy);
    }

    @Override
    public void revokeEntitlements(List<Entitlement> entsToRevoke) {
        revokeEntitlements(entsToRevoke, true);
    }

    /**
     * Revokes the given set of entitlements.
     *
     * @param entsToRevoke entitlements to revoke
     * @param regenCertsAndStatuses if this revocation should also trigger regeneration of certificates
     * and recomputation of statuses. For performance reasons some callers might
     * choose to set this to false.
     */
    @Transactional
    public void revokeEntitlements(List<Entitlement> entsToRevoke, boolean regenCertsAndStatuses) {
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

        poolCurator.lock(poolsToLock);
        log.info("Batch revoking {} entitlements ", entsToRevoke.size());
        entsToRevoke = new ArrayList<Entitlement>(entsToRevoke);

        for (Pool pool : poolsToDelete) {
            entsToRevoke.addAll(pool.getEntitlements());
        }

        log.debug("Adjusting consumed quantities on pools");
        for (Entitlement ent : entsToRevoke) {
            //We need to trigger lazy load of provided products
            //to have access to those products later in this method.
            ent.getPool().getProvidedProducts().size();
            Pool pool = ent.getPool();
            pool.setConsumed(pool.getConsumed() - ent.getQuantity());
            pool.getEntitlements().remove(ent);
            Consumer consumer = ent.getConsumer();
            if (consumer.getType().isManifest()) {
                pool.setExported(pool.getExported() - ent.getQuantity());
            }
        }

        /**
         * Before deleting the entitlements, we need to find out if there are any
         * modifier entitlements that need to have their certificates regenerated
         */
        if (regenCertsAndStatuses) {
            Collection<String> modifiedEntIds = this.entitlementCurator.batchListModifying(entsToRevoke);
            log.debug("Regenerating certificates for modifying entitlements: {}", modifiedEntIds);

            this.ecGenerator.regenerateCertificatesByEntitlementIds(modifiedEntIds, false, true);
            log.debug("Modifier entitlements done.");
        }

        log.info("Starting batch delete of pools");
        poolCurator.batchDelete(poolsToDelete);
        log.info("Starting batch delete of entitlements");
        entitlementCurator.batchDelete(entsToRevoke);
        log.info("Starting delete flush");
        entitlementCurator.flush();
        log.info("All deletes flushed successfully");

        Map<Consumer, List<Entitlement>> consumerSortedEntitlements = entitlementCurator
            .getDistinctConsumers(entsToRevoke);

        filterAndUpdateStackingEntitlements(consumerSortedEntitlements);

        // post unbind actions
        for (Entitlement ent : entsToRevoke) {
            enforcer.postUnbind(ent.getConsumer(), this, ent);
        }

        if (!regenCertsAndStatuses) {
            log.info("Regeneration and status computation was not requested finishing batch revoke");

            sendDeletedEvents(entsToRevoke);
            return;
        }

        log.info("Scheduling Compliance status for {} consumers.", consumerSortedEntitlements.size());
        for (Consumer consumer : consumerSortedEntitlements.keySet()) {
            JobDetail detail = ConsumerComplianceJob.scheduleStatusCheck(consumer, null, false, true);
            detail.getJobDataMap().put(PinsetterJobListener.PRINCIPAL_KEY, new SystemPrincipal());

            log.info("Triggering ConsumerComplianceJob: {} for consumer: {}", detail.getKey(),
                consumer.getUuid());
            try {
                pinsetterKernel.scheduleSingleJob(detail);
            }
            catch (PinsetterException e) {
                log.error("ConsumerComplianceJob schedule failed", e.getMessage());
            }
        }

        log.info("All statuses recomputation scheduled.");

        sendDeletedEvents(entsToRevoke);
    }

    private void sendDeletedEvents(List<Entitlement> entsToRevoke) {
        // for each deleted entitlement, create an event
        for (Entitlement entitlement : entsToRevoke) {
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
     * @param entitlements
     * @return
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
     * @return Entitlements that are stacked
     */
    private void filterAndUpdateStackingEntitlements(
        Map<Consumer, List<Entitlement>> consumerSortedEntitlements) {
        Map<Consumer, List<Entitlement>> stackingEntitlements = new HashMap<Consumer, List<Entitlement>>();

        for (Consumer consumer : consumerSortedEntitlements.keySet()) {
            List<Entitlement> ents = consumerSortedEntitlements.get(consumer);
            if (CollectionUtils.isNotEmpty(ents)) {
                for (Entitlement ent : ents) {
                    Pool pool = ent.getPool();

                    if (!"true".equals(pool.getAttributeValue("pool_derived")) &&
                        pool.getProduct().hasAttribute("stacking_id")) {
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
                poolRules.updatePoolsFromStack(entry.getKey(), subPools, true);
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
        revokeEntitlements(entsToDelete, regenCertsAndStatuses);
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
            revokeEntitlement(e);
        }

        poolCurator.delete(pool);
        sink.queueEvent(event);
    }

    @Override
    @Transactional
    public void deletePools(List<Pool> pools) {
        if (pools.isEmpty()) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Delete pools: {}", getPoolIds(pools));
        }

        List<Entitlement> entitlementsToRevoke = new LinkedList<Entitlement>();

        for (Pool p : pools) {
            if (log.isDebugEnabled()) {
                log.debug("Deletion of pool {} will cause revocation of the following entitlements: {}.",
                    p.getId(), getEntIds(p.getEntitlements()));
            }
            entitlementsToRevoke.addAll(p.getEntitlements());
        }

        if (!pools.isEmpty()) {
            revokeEntitlements(entitlementsToRevoke);
            log.debug("Batch deleting pools after successful revocation");
            poolCurator.batchDelete(pools);
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
    public void regenerateDirtyEntitlements(Iterable<Entitlement> entitlements) {
        for (Entitlement entitlement : entitlements) {
            if (entitlement.isDirty()) {
                log.info("Found dirty entitlement to regenerate: {}", entitlement);
                this.ecGenerator.regenerateCertificatesOf(entitlement, false, false);
            }
        }
    }

    @Override
    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter) {
        return this.getRefresher(subAdapter, true);
    }

    @Override
    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter, boolean lazy) {
        return new Refresher(this, subAdapter, lazy);
    }

    /**
     * EntitlementHandler
     */
    private interface EntitlementHandler {
        Map<String, Entitlement> handleEntitlement(Consumer consumer,
            Map<String, PoolQuantity> poolQuantities, Map<String, Entitlement> entitlements);

        void handlePostEntitlement(PoolManager manager, Consumer consumer,
            Map<String, Entitlement> entitlements);

        void handleSelfCertificates(Consumer consumer, Map<String, PoolQuantity> pools,
            Map<String, Entitlement> entitlements, boolean generateUeberCert);

        void handleBonusPools(Map<String, PoolQuantity> pools, Map<String, Entitlement> entitlements);
    }

    /**
     * NewHandler
     */
    private class NewHandler implements EntitlementHandler {

        @Override
        public Map<String, Entitlement> handleEntitlement(Consumer consumer,
            Map<String, PoolQuantity> poolQuantities, Map<String, Entitlement> entitlements) {

            List<Entitlement> entsToPersist = new ArrayList<Entitlement>();
            Map<String, Entitlement> result = new HashMap<String, Entitlement>();

            for (Entry<String, PoolQuantity> entry : poolQuantities.entrySet()) {
                Entitlement newEntitlement = new Entitlement(
                    entry.getValue().getPool(), consumer, entry.getValue().getQuantity()
                );

                entsToPersist.add(newEntitlement);
                result.put(entry.getKey(), newEntitlement);
            }

            entitlementCurator.saveOrUpdateAll(entsToPersist, false);

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

        @Override
        public void handlePostEntitlement(PoolManager manager, Consumer consumer,
            Map<String, Entitlement> entitlements) {
            Set<String> stackIds = new HashSet<String>();
            for (Entitlement entitlement : entitlements.values()) {
                if (entitlement.getPool().isStacked()) {
                    stackIds.add(entitlement.getPool().getStackId());
                }
            }
            List<Pool> subPoolsForStackIds = null;
            if (!stackIds.isEmpty()) {
                subPoolsForStackIds = poolCurator.getSubPoolForStackIds(consumer, stackIds);
                if (CollectionUtils.isNotEmpty(subPoolsForStackIds)) {
                    poolRules.updatePoolsFromStack(consumer, subPoolsForStackIds, false);
                    poolCurator.mergeAll(subPoolsForStackIds, false);
                }
            }
            else {
                subPoolsForStackIds = new ArrayList<Pool>();
            }

            enforcer.postEntitlement(manager, consumer, entitlements, subPoolsForStackIds);
        }

        @Override
        public void handleSelfCertificates(Consumer consumer, Map<String, PoolQuantity> poolQuantities,
            Map<String, Entitlement> entitlements, boolean generateUeberCert) {
            Map<String, Product> products = new HashMap<String, Product>();
            for (PoolQuantity poolQuantity : poolQuantities.values()) {
                Pool pool = poolQuantity.getPool();
                products.put(pool.getId(), pool.getProduct());
            }

            ecGenerator.generateEntitlementCertificates(consumer, products, entitlements, generateUeberCert);
        }

        @Override
        public void handleBonusPools(Map<String, PoolQuantity> pools, Map<String, Entitlement> entitlements) {
            checkBonusPoolQuantities(pools, entitlements);
        }
    }

    /**
     * UpdateHandler
     */
    private class UpdateHandler implements EntitlementHandler {
        @Override
        public Map<String, Entitlement> handleEntitlement(Consumer consumer,
            Map<String, PoolQuantity> poolQuantities, Map<String, Entitlement> entitlements) {
            for (Entry<String, Entitlement> entry : entitlements.entrySet()) {
                Entitlement entitlement = entry.getValue();
                entitlement.setQuantity(entitlement.getQuantity() +
                    poolQuantities.get(entry.getKey()).getQuantity());
            }
            List<Entitlement> entitlementsList = new ArrayList<Entitlement>(entitlements.values());
            entitlementCurator.mergeAll(entitlementsList, false);
            return entitlements;
        }

        @Override
        public void handlePostEntitlement(PoolManager manager, Consumer consumer,
            Map<String, Entitlement> entitlements) {
        }

        @Override
        public void handleSelfCertificates(Consumer consumer, Map<String, PoolQuantity> poolQuantities,
            Map<String, Entitlement> entitlements, boolean generateUeberCert) {
            for (Entry<String, Entitlement> entry : entitlements.entrySet()) {
                regenerateCertificatesOf(entry.getValue(), generateUeberCert, true);
            }
        }
        @Override
        public void handleBonusPools(Map<String, PoolQuantity> pools, Map<String, Entitlement> entitlements) {
            // This is likely a no-op now that virt-limit is the quantity on sub-pools,
            // rather than the older virt_limit * entitlement quantity:
            checkBonusPoolQuantities(pools, entitlements);
        }
    }

    @Override
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer,
        ActivationKey key, Owner owner, String productId, String subscriptionId, Date activeOn,
        boolean activeOnly, boolean includeWarnings, PoolFilterBuilder filters,
        PageRequest pageRequest) {

        // Only postfilter if we have to
        boolean postFilter = consumer != null || key != null;
        if (consumer != null && !consumer.isDev()) {
            filters.addAttributeFilter(Pool.DEVELOPMENT_POOL_ATTRIBUTE, "!true");
        }

        Page<List<Pool>> page = this.poolCurator.listAvailableEntitlementPools(consumer,
            owner, productId, subscriptionId, activeOn, activeOnly, filters, pageRequest, postFilter);

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
            resultingPools = enforcer.filterPools(
                consumer, resultingPools, includeWarnings);
        }
        if (key != null) {
            resultingPools = this.filterPoolsForActKey(
                key, resultingPools, includeWarnings);
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


        /**
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
         * product.getAttributeValue("instance_multiplier").
         */
        if (poolQuantity != null && multiplier != null && multiplier != 0 && pool.getProduct() != null) {
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
            if (pool.getProduct().hasAttribute("instance_multiplier") && pool.getUpstreamPoolId() == null) {
                Integer instMult = null;
                String stringInstmult = pool.getProduct().getAttributeValue("instance_multiplier");
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
                poolQuantity, multiplier, pool.getProduct());
        }


        Subscription fabricated = new Subscription(
            pool.getOwner(),
            pool.getProduct(),
            pool.getProvidedProducts(),
            poolQuantity,
            pool.getStartDate(),
            pool.getEndDate(),
            pool.getUpdated()
        );

        fabricated.setId(pool.getSubscriptionId());
        fabricated.setUpstreamEntitlementId(pool.getUpstreamEntitlementId());
        fabricated.setCdn(pool.getCdn());
        fabricated.setCertificate(pool.getCertificate());
        //Pool.getBranding should return entity Branding. The Branding
        //entity has no futher relationships so everything should be ok.
        fabricated.setBranding(pool.getBranding());

        // TODO:
        // There's probably a fair amount of other stuff we need to migrate over to the
        // subscription. We should do that here.

        return fabricated;
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
    public Pool findUeberPool(Owner owner) {
        return poolCurator.findUeberPool(owner);
    }

    @Override
    public List<Pool> listPoolsByOwner(Owner owner) {
        return poolCurator.listByOwner(owner);
    }

    public PoolUpdate updatePoolFromStack(Pool pool, Set<Product> changedProducts) {
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
}
