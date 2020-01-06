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
import org.candlepin.bind.BindChainFactory;
import org.candlepin.bind.PoolOperationCallback;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
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
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resteasy.JsonProvider;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.CdnInfo;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.CertificateSerialInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.Traceable;
import org.candlepin.util.TraceableParam;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
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
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;



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
    private ConsumerTypeCurator consumerTypeCurator;
    private EntitlementCertificateCurator entitlementCertificateCurator;
    private EntitlementCertificateGenerator ecGenerator;
    private ComplianceRules complianceRules;
    private SystemPurposeComplianceRules systemPurposeComplianceRules;
    private ProductCurator productCurator;
    private ProductManager productManager;
    private AutobindRules autobindRules;
    private ActivationKeyRules activationKeyRules;
    private ContentManager contentManager;
    private OwnerContentCurator ownerContentCurator;
    private OwnerCurator ownerCurator;
    private OwnerProductCurator ownerProductCurator;
    private CdnCurator cdnCurator;
    private OwnerManager ownerManager;
    private BindChainFactory bindChainFactory;

    @Inject protected JsonProvider jsonProvider;

    /**
     * @param poolCurator
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
        ConsumerTypeCurator consumerTypeCurator,
        EntitlementCertificateCurator entitlementCertCurator,
        EntitlementCertificateGenerator ecGenerator,
        ComplianceRules complianceRules,
        SystemPurposeComplianceRules systemPurposeComplianceRules,
        AutobindRules autobindRules,
        ActivationKeyRules activationKeyRules,
        ProductCurator productCurator,
        ProductManager productManager,
        ContentManager contentManager,
        OwnerContentCurator ownerContentCurator,
        OwnerCurator ownerCurator,
        OwnerProductCurator ownerProductCurator,
        OwnerManager ownerManager,
        CdnCurator cdnCurator,
        I18n i18n,
        BindChainFactory bindChainFactory) {

        this.poolCurator = poolCurator;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.config = config;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.enforcer = enforcer;
        this.poolRules = poolRules;
        this.entitlementCertificateCurator = entitlementCertCurator;
        this.ecGenerator = ecGenerator;
        this.complianceRules = complianceRules;
        this.systemPurposeComplianceRules = systemPurposeComplianceRules;
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
        this.cdnCurator = cdnCurator;
        this.i18n = i18n;
        this.bindChainFactory = bindChainFactory;
    }

    /*
     * We need to update/regen entitlements in the same transaction we update pools
     * so we don't miss anything
     */
    @Transactional
    @SuppressWarnings("checkstyle:methodlength")
    @Traceable
    void refreshPoolsWithRegeneration(SubscriptionServiceAdapter subAdapter,
        @TraceableParam("owner") Owner owner, boolean lazy) {

        Date now = new Date();
        owner = this.resolveOwner(owner);
        log.info("Refreshing pools for owner: {}", owner);

        ImportedEntityCompiler compiler = new ImportedEntityCompiler();

        log.debug("Fetching subscriptions from adapter...");
        compiler.addSubscriptions(subAdapter.getSubscriptions(owner.getKey()));

        Map<String, ? extends SubscriptionInfo> subscriptionMap = compiler.getSubscriptions();
        Map<String, ? extends ProductInfo> productMap = compiler.getProducts();
        Map<String, ? extends ContentInfo> contentMap = compiler.getContent();

        // If trace output is enabled, dump some JSON representing the subscriptions we received so
        // we can simulate this in a testing environment.
        if (log.isTraceEnabled() || "TRACE".equalsIgnoreCase(owner.getLogLevel())) {
            try {
                ObjectMapper mapper = this.jsonProvider
                    .locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

                log.trace("Received {} subscriptions from upstream:", subscriptionMap.size());
                log.trace(mapper.writeValueAsString(subscriptionMap.values()));
                log.trace("Finished outputting upstream subscriptions");
            }
            catch (Exception e) {
                log.trace("Exception occurred while outputting upstream subscriptions", e);
            }
        }

        // Persist content changes
        log.debug("Importing {} content...", contentMap.size());

        Map<String, Content> importedContent = this.contentManager
            .importContent(owner, contentMap, productMap.keySet())
            .getImportedEntities();

        log.debug("Importing {} product(s)...", productMap.size());
        ImportResult<Product> importResult = this.productManager
            .importProducts(owner, productMap, importedContent);

        Map<String, Product> importedProducts = importResult.getImportedEntities();
        Map<String, Product> updatedProducts = importResult.getUpdatedEntities();

        log.debug("Refreshing {} pool(s)...", subscriptionMap.size());
        for (Iterator<? extends SubscriptionInfo> si = subscriptionMap.values().iterator(); si.hasNext();) {
            SubscriptionInfo sub = si.next();

            if (now.after(sub.getEndDate())) {
                log.info("Skipping expired subscription: {}", sub);

                si.remove();
                continue;
            }

            log.debug("Processing subscription: {}", sub);
            Pool pool = this.convertToMasterPoolImpl(sub, owner, importedProducts);
            pool.setLocked(true);
            this.refreshPoolsForMasterPool(pool, false, lazy, updatedProducts);
        }

        // delete pools whose subscription disappeared:
        log.debug("Deleting pools for absent subscriptions...");
        List<Pool> poolsToDelete = new ArrayList<>();

        for (Pool pool : poolCurator.getPoolsFromBadSubs(owner, subscriptionMap.keySet())) {
            if (this.isManaged(pool)) {
                poolsToDelete.add(pool);
            }
        }

        deletePools(poolsToDelete);

        // TODO: break this call into smaller pieces. There may be lots of floating pools
        log.debug("Updating floating pools...");
        List<Pool> floatingPools = poolCurator.getOwnersFloatingPools(owner);
        updateFloatingPools(floatingPools, lazy, updatedProducts);

        log.info("Refresh pools for owner: {} completed in: {}ms", owner.getKey(),
            System.currentTimeMillis() - now.getTime());
    }

    private Owner resolveOwner(Owner owner) {
        if (owner == null || (owner.getKey() == null && owner.getId() == null)) {
            throw new IllegalArgumentException(
                i18n.tr("No owner specified, or owner lacks identifying information"));
        }

        if (owner.getKey() != null) {
            String ownerKey = owner.getKey();
            owner = ownerCurator.getByKey(owner.getKey());

            if (owner == null) {
                throw new IllegalStateException(
                    i18n.tr("Unable to find an owner with the key \"{0}\"", ownerKey));
            }
        }
        else {
            String id = owner.getId();
            owner = ownerCurator.get(owner.getId());

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
        List<Pool> subscriptionPools;

        if (pool.getSubscriptionId() != null) {
            subscriptionPools = this.poolCurator.getPoolsBySubscriptionId(pool.getSubscriptionId()).list();
        }
        else {
            // If we don't have a subscription ID, this *is* the master pool, but we need to use
            // the original, hopefully unmodified, pool
            subscriptionPools = pool.getId() != null ?
                Collections.<Pool>singletonList(this.poolCurator.get(pool.getId())) :
                Collections.<Pool>singletonList(pool);
        }

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

            if (pool.isLocked()) {
                subPool.setLocked(true);
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

    private void removeAndDeletePoolsOnOtherOwners(List<Pool> existingPools, Pool pool) {
        List<Pool> toRemove = new LinkedList<>();

        for (Pool existing : existingPools) {
            if (!existing.getOwner().equals(pool.getOwner())) {
                toRemove.add(existing);
                log.warn("Removing {} because it exists in the wrong org", existing);
                if (existing.getType() == PoolType.NORMAL || existing.getType() == PoolType.BONUS) {
                    deletePool(existing);
                }
            }
        }

        existingPools.removeAll(toRemove);
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

        if (log.isDebugEnabled()) {
            for (Pool pool : pools) {
                log.debug("Cleaning up expired pool: {} (expired: {})",
                    pool.getId(), pool.getEndDate());
            }
        }

        // Delete the block of pools & flush the results to tell Hibernate to evict the objects
        // (we hope). Even if it doesn't, and even if the transaction completion is going to
        // flush the objects anyway, it should not hurt and is an explicit call.
        this.deletePools(pools);
        this.poolCurator.flush();

        return pools.size();
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
        if (existingPools == null || existingPools.isEmpty()) {
            return new HashSet<>(0);
        }

        log.debug("Updating {} pools for existing master pool: {}", existingPools.size(), pool);

        Map<String, EventBuilder> poolEvents = new HashMap<>();
        for (Pool existing : existingPools) {
            EventBuilder eventBuilder = eventFactory
                .getEventBuilder(Target.POOL, Type.MODIFIED)
                .setEventData(existing);

            poolEvents.put(existing.getId(), eventBuilder);
        }

        // Hand off to rules to determine which pools need updating:
        List<PoolUpdate> updatedPools = poolRules.updatePools(pool, existingPools, originalQuantity,
            changedProducts);

        /*
         * 1567922: Due to poolRules.updatePools call above, some fields of a product on a pool might change.
         * Hibernate does not persist these changes yet, and when those pool changes result in
         * revocation of entitlements later in this process, and the same pool is attempted to be locked
         * due to it, we get an error.
         * Temporarily we are resorting to flush the changes here, there will be an investigation later, at
         * which time, this line of the comment could be replaced with a refactor.
         */
        poolCurator.flush();

        String virtLimit = pool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT);
        boolean createsSubPools = !StringUtils.isBlank(virtLimit) && !"0".equals(virtLimit);

        // Update subpools if necessary
        if (updateStackDerived && !updatedPools.isEmpty() && createsSubPools && pool.isStacked()) {
            // Get all pools for the master pool owner derived from the pool's
            // stack id, because we cannot look it up by subscriptionId
            List<Pool> subPools = getOwnerSubPoolsForStackId(pool.getOwner(), pool.getStackId());

            for (Pool subPool : subPools) {
                PoolUpdate update = updatePoolFromStack(subPool, changedProducts);

                if (update.changed()) {
                    updatedPools.add(update);

                    EventBuilder eventBuilder = eventFactory
                        .getEventBuilder(Target.POOL, Type.MODIFIED)
                        .setEventData(subPool);

                    poolEvents.put(subPool.getId(), eventBuilder);
                }
            }
        }

        return processPoolUpdates(poolEvents, updatedPools);
    }

    protected Set<String> processPoolUpdates(Map<String, EventBuilder> poolEvents,
        List<PoolUpdate> updatedPools) {

        boolean flush = false;
        Set<String> existingUndeletedPoolIds = new HashSet<>();
        Set<Pool> poolsToDelete = new HashSet<>();
        Set<Pool> poolsToRegenEnts = new HashSet<>();
        List<Pool> poolsQtyUpdated = new LinkedList<>();
        Set<String> entitlementsToRegen = new HashSet<>();

        // Get our list of pool IDs so we can check which of them still exist in the DB...
        for (PoolUpdate update : updatedPools) {
            if (update != null && update.getPool() != null && update.getPool().getId() != null) {
                existingUndeletedPoolIds.add(update.getPool().getId());
            }
        }

        existingUndeletedPoolIds = this.poolCurator.getExistingPoolIdsByIds(existingUndeletedPoolIds);

        // Process pool updates...
        for (PoolUpdate updatedPool : updatedPools) {
            Pool existingPool = updatedPool.getPool();
            log.debug("Pool changed: {}", updatedPool);

            if (existingPool == null || !existingUndeletedPoolIds.contains(existingPool.getId())) {
                log.debug("Pool has already been deleted from the database.");
                continue;
            }

            // Delete pools the rules signal needed to be cleaned up:
            if (existingPool.isMarkedForDelete()) {
                log.warn("Deleting pool as requested by rules: {}", existingPool.getId());
                poolsToDelete.add(existingPool);
                continue;
            }

            // save changes for the pool. We'll flush these changes later.
            this.poolCurator.merge(existingPool);
            flush = true;

            // quantity has changed. delete any excess entitlements from pool
            // the quantity has not yet been expressed on the pool itself
            if (updatedPool.getQuantityChanged()) {
                poolsQtyUpdated.add(existingPool);
            }

            // dates changed. regenerate all entitlement certificates
            if (updatedPool.getDatesChanged() || updatedPool.getProductsChanged() ||
                updatedPool.getDerivedProductsChanged()) {

                poolsToRegenEnts.add(existingPool);
            }

            // Build event for this update...
            EventBuilder builder = poolEvents.get(existingPool.getId());
            if (builder != null) {
                Event event = builder.setEventData(existingPool).buildEvent();
                sink.queueEvent(event);
            }
            else {
                log.warn("Pool updated without an event builder: {}", existingPool);
            }
        }

        // Flush our merged changes
        if (flush) {
            this.poolCurator.flush();
        }

        // Check if we need to execute the revocation plan
        if (poolsQtyUpdated.size() > 0) {
            this.revokeEntitlementsFromOverflowingPools(poolsQtyUpdated);
        }

        // Fetch entitlement IDs for updated pools...
        if (poolsToRegenEnts.size() > 0) {
            entitlementsToRegen.addAll(this.poolCurator.retrieveOrderedEntitlementIdsOf(poolsToRegenEnts));
        }

        // Delete pools marked for deletion
        if (poolsToDelete.size() > 0) {
            this.deletePools(poolsToDelete);
        }

        // Return entitlement IDs in need regeneration
        return entitlementsToRegen;
    }

    Set<Pool> revokeEntitlementsFromOverflowingPools(List<Pool> pools) {
        Collection<Pool> overflowing = pools.stream()
            .filter(Pool::isOverflowing)
            .collect(Collectors.toList());

        if (overflowing.isEmpty()) {
            return null;
        }

        List<Entitlement> entitlementsToRevoke = new ArrayList<>();

        // Impl note: this may remove pools which are not backed by the DB.
        overflowing = poolCurator.lock(overflowing);

        List<Entitlement> overFlowingEnts = this.poolCurator.retrieveOrderedEntitlementsOf(overflowing);
        Map<String, List<Entitlement>> entMap = new HashMap<>();
        for (Entitlement entitlement : overFlowingEnts) {
            entMap.computeIfAbsent(entitlement.getPool().getId(), k -> new ArrayList<>()).add(entitlement);
        }

        for (Pool pool : overflowing) {
            // we then start revoking the existing entitlements
            List<Entitlement> entitlements = entMap.get(pool.getId());
            long newConsumed = pool.getConsumed();

            // deletes ents in order of date since we retrieved and put them in the map in order.
            for (Entitlement ent : entitlements) {
                if (newConsumed > pool.getQuantity()) {
                    entitlementsToRevoke.add(ent);
                    newConsumed -= ent.getQuantity();
                }
                else {
                    break;
                }
            }
        }

        // revoke the entitlements amassed above
        return revokeEntitlements(new ArrayList<>(entitlementsToRevoke));
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
        Map<String, EventBuilder> poolEvents = new HashMap<>();
        for (Pool existing : floatingPools) {
            EventBuilder eventBuilder = eventFactory.getEventBuilder(Target.POOL, Type.MODIFIED)
                .setEventData(existing);
            poolEvents.put(existing.getId(), eventBuilder);
        }

        // Hand off to rules to determine which pools need updating
        List<PoolUpdate> updatedPools = poolRules.updatePools(floatingPools, changedProducts);
        regenerateCertificatesByEntIds(processPoolUpdates(poolEvents, updatedPools), lazy);
    }

    /**
     * @param sub
     * @return the newly created Pools
     */
    @Override
    public List<Pool> createAndEnrichPools(SubscriptionInfo sub) {
        return createAndEnrichPools(sub, Collections.<Pool>emptyList());
    }

    public List<Pool> createAndEnrichPools(SubscriptionInfo sub, List<Pool> existingPools) {
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
        if (pool != null) {
            // We're assuming that net-new pools will not yet have an ID
            if (pool.getId() == null) {
                pool = this.poolCurator.create(pool);
                log.debug("  created pool: {}", pool);

                if (pool != null) {
                    sink.emitPoolCreated(pool);
                }
            }
            else {
                pool = this.poolCurator.merge(pool);
                log.debug("  updated pool: {}", pool);
            }
        }

        return pool;
    }

    @Override
    public List<Pool> createPools(List<Pool> pools) {
        if (CollectionUtils.isNotEmpty(pools)) {
            Set<String> updatedPoolIds = new HashSet<>();

            for (Pool pool : pools) {
                // We're assuming that net-new pools will not yet have an ID here.
                if (pool.getId() != null) {
                    updatedPoolIds.add(pool.getId());
                }
            }

            poolCurator.saveOrUpdateAll(pools, false, false);

            for (Pool pool : pools) {
                if (pool != null && !updatedPoolIds.contains(pool.getId())) {
                    log.debug("  created pool: {}", pool);
                    sink.emitPoolCreated(pool);
                }
                else {
                    log.debug("  updated pool: {}", pool);
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
            this.deletePoolsForSubscriptions(Collections.<String>singletonList(pool.getSubscriptionId()));
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
    @SuppressWarnings("checkstyle:methodlength")
    private Pool convertToMasterPoolImpl(SubscriptionInfo sub, Owner owner, Map<String, Product> productMap) {
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
        if (sub.getOwner() == null || !owner.getKey().equals(sub.getOwner().getKey())) {
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
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        // Copy over upstream details
        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());

        // Resolve CDN
        if (sub.getCdn() != null) {
            // Impl note: we're attempting to resolve the CDN nicely, but since we used to just
            // copy this as-is, we need to fall back to accepting whatever we had before if that
            // fails.

            CdnInfo cinfo = sub.getCdn();
            Cdn cdn = this.cdnCurator.getByLabel(cinfo.getLabel());

            if (cdn == null) {
                // Create a new CDN instance using the data we received and hope for the best...
                cdn = new Cdn();

                cdn.setLabel(cinfo.getLabel());
                cdn.setName(cinfo.getName());
                cdn.setUrl(cinfo.getUrl());

                // More cert stuff...
                if (cinfo.getCertificate() != null) {
                    CertificateInfo certInfo = cinfo.getCertificate();
                    CdnCertificate cert = new CdnCertificate();

                    cert.setKey(certInfo.getKey());
                    cert.setCert(certInfo.getCertificate());

                    if (certInfo.getSerial() != null) {
                        CertificateSerialInfo serialInfo = certInfo.getSerial();
                        CertificateSerial serial = new CertificateSerial();

                        // Impl note:
                        // We don't set the ID or serial here, as we generate the ID automagically,
                        // and the serial is currently implemented as an alias for the ID.

                        serial.setRevoked(serialInfo.isRevoked());
                        serial.setCollected(serialInfo.isCollected());
                        serial.setExpiration(serialInfo.getExpiration());

                        cert.setSerial(serial);
                    }

                    cdn.setCertificate(cert);
                }
            }

            pool.setCdn(cdn);
        }

        // Resolve subscription certificate
        if (sub.getCertificate() != null) {
            // FIXME: This is probably incorrect. We're blindly copying the cert info to new
            // certificate objects, as this was effectively what we were doing before, but it seems
            // a tad dangerous.

            CertificateInfo certInfo = sub.getCertificate();
            SubscriptionsCertificate cert = new SubscriptionsCertificate();

            cert.setKey(certInfo.getKey());
            cert.setCert(certInfo.getCertificate());

            if (certInfo.getSerial() != null) {
                CertificateSerialInfo serialInfo = certInfo.getSerial();
                CertificateSerial serial = new CertificateSerial();

                // Impl note:
                // We don't set the ID or serial here, as we generate the ID automagically, and the
                // serial is currently implemented as an alias for the ID.

                serial.setRevoked(serialInfo.isRevoked());
                serial.setCollected(serialInfo.isCollected());
                serial.setExpiration(serialInfo.getExpiration());

                cert.setSerial(serial);
            }

            pool.setCertificate(cert);
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
            Set<Product> products = new HashSet<>();

            for (ProductInfo pdata : sub.getProvidedProducts()) {
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
            // TODO: workaround to pass import spec tests. we will revisit and update this in import and
            // refresh code changes
            if (pool.getProduct() != null) {
                pool.getProduct().setProvidedProducts(products);
            }
        }

        if (sub.getDerivedProvidedProducts() != null) {
            Set<Product> products = new HashSet<>();

            for (ProductInfo pdata : sub.getDerivedProvidedProducts()) {
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
            // TODO: workaround to pass import spec tests. we will revisit and update this in import and
            // refresh code changes
            if (pool.getDerivedProduct() != null) {
                pool.getDerivedProduct().setProvidedProducts(products);
            }
        }

        return pool;
    }

    /*
     * if you are using this method, you might want to override the quantity
     * with PoolRules.calculateQuantity
     */
    @Override
    public Pool convertToMasterPool(SubscriptionInfo sub) {
        // TODO: Replace this method with a call to the (currently unwritten) EntityResolver.

        if (sub == null) {
            throw new IllegalArgumentException("subscription is null");
        }

        // Resolve the subscription's owner...
        if (sub.getOwner() == null || sub.getOwner().getKey() == null) {
            throw new IllegalStateException("Subscription references an invalid owner: " + sub.getOwner());
        }

        Owner owner = this.ownerCurator.getByKey(sub.getOwner().getKey());
        if (owner == null) {
            throw new IllegalStateException("Subscription references an owner which cannot be resolved: " +
                sub.getOwner());
        }

        // Gather the product IDs referenced by this subscription...
        Set<ProductInfo> productData = new HashSet<>();
        Set<String> productIds = new HashSet<>();
        Map<String, Product> productMap = new HashMap<>();

        productData.add(sub.getProduct());
        productData.add(sub.getDerivedProduct());

        if (sub.getProvidedProducts() != null) {
            productData.addAll(sub.getProvidedProducts());
        }

        if (sub.getDerivedProvidedProducts() != null) {
            productData.addAll(sub.getDerivedProvidedProducts());
        }

        for (ProductInfo pdata : productData) {
            if (pdata != null) {
                if (pdata.getId() == null || pdata.getId().isEmpty()) {
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
    public Pool get(String poolId) {
        return this.poolCurator.get(poolId);
    }

    @Override
    public List<String> listEntitledConsumerUuids(String poolId) {
        return this.poolCurator.listEntitledConsumerUuids(poolId);
    }

    @Override
    public List<Pool> secureGet(Collection<String> poolIds) {
        if (CollectionUtils.isNotEmpty(poolIds)) {
            return this.poolCurator.listAllByIds(poolIds).list();
        }

        return new ArrayList<>();
    }

    @Override
    public List<Pool> getBySubscriptionId(Owner owner, String id) {
        return this.poolCurator.getBySubscriptionId(owner, id);
    }

    @Override
    public List<Pool> getBySubscriptionIds(String ownerId, Collection<String> subscriptionIds) {
        if (CollectionUtils.isNotEmpty(subscriptionIds)) {
            return this.poolCurator.getBySubscriptionIds(ownerId, subscriptionIds);
        }

        return new ArrayList<>();
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
        String ownerId = consumer.getOwnerId();

        List<PoolQuantity> bestPools = new ArrayList<>();
        // fromPools will be empty if the dev pool was already created.
        if (consumer != null && consumer.isDev() && !fromPools.isEmpty()) {
            String poolId = fromPools.iterator().next();
            PoolQuantity pq = new PoolQuantity(poolCurator.get(poolId), 1);
            bestPools.add(pq);
        }
        else {
            bestPools = getBestPools(consumer, productIds, entitleDate, ownerId, null, fromPools);
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

        this.consumerCurator.lock(host);

        List<Entitlement> entitlements = new LinkedList<>();
        if (!host.getOwnerId().equals(guest.getOwnerId())) {
            log.debug("Host {} and guest {} have different owners", host.getUuid(), guest.getUuid());
            return entitlements;
        }
        // Use the current date if one wasn't provided:
        if (entitleDate == null) {
            entitleDate = new Date();
        }

        List<PoolQuantity> bestPools = getBestPoolsForHost(guest, host, entitleDate, host.getOwnerId(),
            null, possiblePools);

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
     * @param ownerId
     * @param serviceLevelOverride
     * @return PoolQuantity list to attempt to attach
     * @throws EntitlementRefusedException if unable to bind
     */
    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public List<PoolQuantity> getBestPoolsForHost(Consumer guest, Consumer host, Date entitleDate,
        String ownerId, String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException {

        Map<String, ValidationResult> failedResults = new HashMap<>();
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
            host, null, ownerId, null, null, activePoolDate, false,
            poolFilter, null, false, false, null).getPageData();
        log.debug("Found {} total pools in org.", allOwnerPools.size());
        logPools(allOwnerPools);

        List<Pool> allOwnerPoolsForGuest = this.listAvailableEntitlementPools(
            guest, null, ownerId, null, null, activePoolDate,
            false, poolFilter,
            null, false, false, null).getPageData();
        log.debug("Found {} total pools already available for guest", allOwnerPoolsForGuest.size());
        logPools(allOwnerPoolsForGuest);

        for (Entitlement ent : host.getEntitlements()) {
            //filter out pools that are attached, there is no need to
            //complete partial stacks, as they are already granting
            //virtual pools
            log.debug("Removing pool host is already entitled to: {}", ent.getPool());
            allOwnerPools.remove(ent.getPool());
        }
        List<Pool> filteredPools = new LinkedList<>();

        ComplianceStatus guestCompliance = complianceRules.getStatus(guest, entitleDate, false);
        Set<String> tmpSet = new HashSet<>();
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
            boolean matchesAddOns = false;
            boolean matchesRole = false;
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

                if (!providesProduct) {
                    Set<String> consumerAddOns = host.getAddOns() != null ?
                        host.getAddOns() : new HashSet<>();
                    String[] prodAddOns = pool.getProductAttributeValue(Product.Attributes.ADDONS) != null ?
                        pool.getProductAttributeValue(Product.Attributes.ADDONS).split(",") : new String[]{};
                    for (String addon : prodAddOns) {
                        if (consumerAddOns.contains(addon)) {
                            matchesAddOns = true;
                            break;
                        }
                    }
                    String consumerRole = host.getRole() != null ?
                        host.getRole() : "";
                    String[] prodRoles = pool.getProductAttributeValue(Product.Attributes.ROLES) != null ?
                        pool.getProductAttributeValue(Product.Attributes.ROLES).split(",") : new String[]{};
                    for (String prodRole : prodRoles) {
                        if (consumerRole.equalsIgnoreCase(prodRole)) {
                            matchesRole = true;
                            break;
                        }
                    }
                }
            }

            if (providesProduct || matchesAddOns || matchesRole) {
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
            poolCurator.retrieveServiceLevelsForOwner(ownerId, true), true);

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
        Set<String> productsToRemove = new HashSet<>();

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
        String[] productIds, Date entitleDate, String ownerId,
        String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException {

        Map<String, ValidationResult> failedResults = new HashMap<>();

        Date activePoolDate = entitleDate;
        if (entitleDate == null) {
            activePoolDate = new Date();
        }

        PoolFilterBuilder poolFilter = new PoolFilterBuilder();
        poolFilter.addIdFilters(fromPools);
        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(
            consumer, null, ownerId, null, null, activePoolDate, false,
            poolFilter, null, false, false, null).getPageData();
        List<Pool> filteredPools = new LinkedList<>();

        // We have to check compliance status here so we can replace an empty
        // array of product IDs with the array the consumer actually needs. (i.e. during
        // a healing request)
        ComplianceStatus compliance = complianceRules.getStatus(consumer, entitleDate, false);
        if (productIds == null || productIds.length == 0) {
            log.debug("No products specified for bind, checking compliance to see what is needed.");
            Set<String> tmpSet = new HashSet<>();
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
            boolean matchesAddOns = false;
            boolean matchesRole = false;

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

                if (!providesProduct) {
                    Set<String> consumerAddOns = consumer.getAddOns() != null ?
                        consumer.getAddOns() : new HashSet<>();
                    List<String> prodAddOns =
                        pool.getProductAttributeValue(Product.Attributes.ADDONS) != null ?
                        Arrays.asList(pool.getProductAttributeValue(Product.Attributes.ADDONS)
                        .trim().split("\\s*,\\s*")) : Collections.emptyList();

                    for (String consumerAddOn: consumerAddOns) {
                        if (prodAddOns.stream().anyMatch(str -> str.equalsIgnoreCase(consumerAddOn.trim()))) {
                            matchesAddOns = true;
                            break;
                        }
                    }
                    String consumerRole = consumer.getRole() != null ?  consumer.getRole() : "";
                    String[] prodRoles = pool.getProductAttributeValue(Product.Attributes.ROLES) != null ?
                        pool.getProductAttributeValue(Product.Attributes.ROLES).split(",") :
                        new String[]{};
                    for (String prodRole : prodRoles) {
                        if (consumerRole.equalsIgnoreCase(prodRole)) {
                            matchesRole = true;
                        }
                    }
                }
            }

            if (providesProduct || matchesAddOns || matchesRole) {
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
            poolCurator.retrieveServiceLevelsForOwner(ownerId, true), false);
        // Sort the resulting pools to avoid deadlocks
        Collections.sort(enforced);
        return enforced;
    }

    private Map<String, Integer> convertToMap(List<PoolQuantity> poolQuantities) {
        Map<String, Integer> result = new HashMap<>();
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

        return new ArrayList<>();
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

        Pool pool = this.poolCurator.lock(entitlement.getPool());
        this.poolCurator.refresh(pool);

        log.debug("Locked pool: {} consumed: {}", pool, pool.getConsumed());

        ValidationResult result = enforcer.update(consumer, entitlement, change);
        if (!result.isSuccessful()) {
            log.warn("Entitlement not updated: {} for pool: {}", result.getErrors().toString(), pool.getId());

            Map<String, ValidationResult> errorMap = new HashMap<>();
            errorMap.put(pool.getId(), result);

            throw new EntitlementRefusedException(errorMap);
        }


        // Grab an exclusive lock on the consumer to prevent deadlock.
        this.consumerCurator.lock(consumer);

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        // Persist the entitlement after it has been updated.
        log.info("Processing entitlement and persisting.");
        entitlement.setQuantity(entitlement.getQuantity() + change);
        entitlementCurator.merge(entitlement);

        pool.setConsumed(pool.getConsumed() + change);
        if (ctype != null && ctype.isManifest()) {
            pool.setExported(pool.getExported() + change);
        }
        poolCurator.merge(pool);
        consumer.setEntitlementCount(consumer.getEntitlementCount() + change);

        Map<String, Entitlement> entMap = new HashMap<>();
        entMap.put(pool.getId(), entitlement);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, change));

        Owner owner = ownerCurator.get(consumer.getOwnerId());

        // the only thing we do here is decrement bonus pool quantity
        PoolOperationCallback poolOperationCallback = enforcer.postEntitlement(this,
            consumer, owner, entMap, new ArrayList<>(), true, poolQuantityMap);
        poolOperationCallback.apply(this);

        // we might have changed the bonus pool quantities, revoke ents if needed.
        checkBonusPoolQuantities(consumer.getOwnerId(), entMap);

        this.entitlementCurator.markEntitlementsDirty(Arrays.asList(entitlement.getId()));

        /*
         * If the consumer is not a distributor, check consumer's new compliance
         * status and save. the getStatus call does that internally.
         * all consumer's entitlement count are updated though, so we need to update irrespective
         * of the consumer type.
         */
        complianceRules.getStatus(consumer, null, false, false);
        // Note: a quantity change should *not* need a system purpose compliance recalculation. if that is
        // not true any more, we should update that here.
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
    protected List<Entitlement> createEntitlements(Consumer consumer, Map<String, Integer> poolQuantityMap,
        CallerType caller) throws EntitlementRefusedException {

        Collection<Entitlement> ents = bindChainFactory
            .create(consumer, poolQuantityMap, caller)
            .run();

        poolCurator.flush();

        return new ArrayList<>(ents);
    }

    /**
     * This method will pull the bonus pools from a physical and make sure that
     *  the bonus pools are not over-consumed.
     *
     * @param ownerId
     * @param entitlements
     */
    public void checkBonusPoolQuantities(String ownerId,
        Map<String, Entitlement> entitlements) {

        Set<String> excludePoolIds = new HashSet<>();
        Map<String, Entitlement> subEntitlementMap = new HashMap<>();
        for (Entry<String, Entitlement> entry : entitlements.entrySet()) {
            Pool pool = entry.getValue().getPool();
            subEntitlementMap.put(pool.getSubscriptionId(), entitlements.get(entry.getKey()));
            excludePoolIds.add(pool.getId());
        }

        List<Pool> overConsumedPools = poolCurator.getOversubscribedBySubscriptionIds(ownerId,
            subEntitlementMap);

        List<Pool> derivedPools = new ArrayList<>();
        for (Pool pool : overConsumedPools) {
            if (pool.getQuantity() != -1 && !excludePoolIds.contains(pool.getId())) {
                derivedPools.add(pool);
            }
        }

        revokeEntitlementsFromOverflowingPools(derivedPools);
    }

    @Override
    public void regenerateCertificatesOf(Consumer consumer, boolean lazy) {
        this.ecGenerator.regenerateCertificatesOf(consumer, lazy);
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
     * @param e Id of the environment where the content was promoted/demoted.
     * @param affectedContent List of content set IDs promoted/demoted.
     */
    @Override
    @Transactional
    public void regenerateCertificatesOf(String e, Set<String> affectedContent, boolean lazy) {
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
    @Transactional
    public Set<Pool> revokeEntitlements(List<Entitlement> entsToRevoke) {
        return revokeEntitlements(entsToRevoke, null, true);
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
     * @return the pools that are deleted as a consequence of revoking entitlements
     */
    @Transactional
    @Traceable
    public Set<Pool> revokeEntitlements(List<Entitlement> entsToRevoke, Set<String> alreadyDeletedPools,
        boolean regenCertsAndStatuses) {

        if (CollectionUtils.isEmpty(entsToRevoke)) {
            return null;
        }

        log.debug("Starting batch revoke of {} entitlements", entsToRevoke.size());
        if (log.isTraceEnabled()) {
            log.trace("Entitlements IDs: {}", getEntIds(entsToRevoke));
        }

        Set<Pool> poolsToDelete = this.poolCurator.listBySourceEntitlements(entsToRevoke);

        log.debug("Found {} additional pools to delete from source entitlements", poolsToDelete.size());
        if (log.isTraceEnabled()) {
            log.trace("Additional pool IDs: {}", getPoolIds(poolsToDelete));
        }

        Set<Pool> poolsToLock = new HashSet<>();
        poolsToLock.addAll(poolsToDelete);

        for (Entitlement ent: entsToRevoke) {
            poolsToLock.add(ent.getPool());

            // If we are deleting a developer entitlement, be sure to delete the
            // associated pool as well.
            if (ent.getPool() != null && ent.getPool().isDevelopmentPool()) {
                poolsToDelete.add(ent.getPool());
            }
        }

        this.poolCurator.lock(poolsToLock);
        this.poolCurator.refresh(poolsToLock);

        log.info("Batch revoking {} entitlements", entsToRevoke.size());
        entsToRevoke = new ArrayList<>(entsToRevoke);

        for (Pool pool : poolsToDelete) {
            for (Entitlement ent : pool.getEntitlements()) {
                ent.setDeletedFromPool(true);
                entsToRevoke.add(ent);
            }
        }

        log.debug("Adjusting consumed quantities on pools");
        List<Pool> poolsToSave = new ArrayList<>();
        Set<String> entIdsToRevoke = new HashSet<>();
        for (Entitlement ent : entsToRevoke) {
            // TODO: Should we throw an exception if we find a malformed/incomplete entitlement
            // or just continue silently ignoring them?
            if (ent == null || ent.getId() == null) {
                continue;
            }

            // Collect the entitlement IDs to revoke seeing as we are iterating over them anyway.
            entIdsToRevoke.add(ent.getId());

            //We need to trigger lazy load of provided products
            //to have access to those products later in this method.
            Pool pool = ent.getPool();
            int entQuantity = ent.getQuantity() != null ? ent.getQuantity() : 0;

            pool.setConsumed(pool.getConsumed() - entQuantity);
            Consumer consumer = ent.getConsumer();
            ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

            if (ctype != null  && ctype.isManifest()) {
                pool.setExported(pool.getExported() - entQuantity);
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
            log.debug("Marking dependent entitlements as dirty...");
            int update = this.entitlementCurator.markDependentEntitlementsDirty(entIdsToRevoke);
            log.debug("{} dependent entitlements marked dirty.", update);
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
            return poolsToDelete;
        }

        log.info("Recomputing status for {} consumers.", consumerSortedEntitlements.size());
        int i = 1;
        for (Consumer consumer : consumerSortedEntitlements.keySet()) {
            if (i++ % 1000 == 0) {
                consumerCurator.flush();
            }

            complianceRules.getStatus(consumer);
            systemPurposeComplianceRules.getStatus(consumer, consumer.getEntitlements(), null, true);
        }

        consumerCurator.flush();

        log.info("All statuses recomputed.");

        sendDeletedEvents(entsToRevoke);
        return poolsToDelete;
    }

    private void sendDeletedEvents(List<Entitlement> entsToRevoke) {
        // for each deleted entitlement, create an event
        for (Entitlement entitlement : entsToRevoke) {
            if (entitlement.deletedFromPool()) {
                continue;
            }

            Consumer consumer = entitlement.getConsumer();
            Event event = eventFactory.entitlementDeleted(entitlement);

            if (!entitlement.isValid() && entitlement.getPool().isUnmappedGuestPool() &&
                consumerCurator.getHost(consumer.getFact("virt.uuid"), consumer.getOwnerId()) == null) {
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
        List<String> ids = new ArrayList<>();

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
        List<String> ids = new ArrayList<>();

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
        Map<Consumer, List<Entitlement>> stackingEntitlements = new HashMap<>();

        for (Consumer consumer : consumerSortedEntitlements.keySet()) {
            List<Entitlement> ents = consumerSortedEntitlements.get(consumer);
            if (CollectionUtils.isNotEmpty(ents)) {
                for (Entitlement ent : ents) {
                    Pool pool = ent.getPool();

                    if (!"true".equals(pool.getAttributeValue(Pool.Attributes.DERIVED_POOL)) &&
                        pool.getProduct().hasAttribute(Product.Attributes.STACKING_ID)) {
                        List<Entitlement> entList = stackingEntitlements.get(consumer);
                        if (entList == null) {
                            entList = new ArrayList<>();
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

            Set<String> stackIds = new HashSet<>();
            for (Entitlement ent : entry.getValue()) {
                stackIds.add(ent.getPool().getStackId());
            }

            List<Pool> subPools = poolCurator.getSubPoolForStackIds(entry.getKey(), stackIds);
            if (CollectionUtils.isNotEmpty(subPools)) {
                poolRules.updatePoolsFromStack(entry.getKey(), subPools, null, alreadyDeletedPools, true);
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
    public void deletePools(Collection<Pool> pools) {
        this.deletePools(pools, null);
    }

    @Override
    @Transactional
    @Traceable
    @SuppressWarnings("checkstyle:methodlength")
    public void deletePools(Collection<Pool> pools, Collection<String> alreadyDeletedPoolIds) {
        if (pools == null || pools.isEmpty()) {
            return;
        }

        log.info("Attempting to delete {} pools...", pools.size());

        // TODO: Remove this and fix the bugs it works around. We absolutely should not be
        // passing state through the various codepaths like this. It makes things far messier
        // than they need to be and is resulting in running slow calculations multiple times.
        if (alreadyDeletedPoolIds == null) {
            alreadyDeletedPoolIds = new HashSet<>();
        }

        Set<String> poolIds = new HashSet<>();
        Set<String> entitlementIds = new HashSet<>();
        Owner owner = null;

        // Convert pools to pool IDs.
        log.info("Fetching related pools and entitlements...");
        for (Pool pool : pools) {
            if (owner == null) {
                owner = pool.getOwner();
            }

            poolIds.add(pool.getId());
        }

        // Fetch pools which are derived from the pools we're going to delete...
        poolIds.addAll(this.poolCurator.getDerivedPoolIdsForPools(poolIds));

        // Fetch related pools and entitlements (recursively)
        Collection<String> pids = poolIds;
        int cachedSize;
        do {
            // Fetch entitlement IDs for our set of pools
            Collection<String> eids = this.poolCurator.getEntitlementIdsForPools(pids);

            // Fetch pools which are derived from these entitlements...
            pids = this.poolCurator.getPoolIdsForSourceEntitlements(eids);

            // Fetch stack derived pools which will be unentitled when we revoke entitlements
            // Impl note: This may occassionally miss stack derived pools in cases where our
            // entitlement count exceeds the IN block limitations. In those cases, we'll end
            // up doing a recursive call into this method, which sucks, but will still work.
            pids.addAll(this.poolCurator.getUnentitledStackDerivedPoolIds(eids));

            // Fetch pools which are derived from the pools we're going to delete...
            pids.addAll(this.poolCurator.getDerivedPoolIdsForPools(pids));

            // Add the new entitlement and pool IDs to our list of things to delete
            cachedSize = poolIds.size();
            entitlementIds.addAll(eids);
            poolIds.addAll(pids);
        }
        while (poolIds.size() != cachedSize);

        // If we've been provided a collection of already-deleted pool IDs, remove those from
        // the list so we don't try to delete them again.
        // TODO: Remove this and stop recursively calling into this method.
        if (alreadyDeletedPoolIds != null) {
            poolIds.removeAll(alreadyDeletedPoolIds);
        }

        // Lock pools we're going to delete (also, fetch them for event generation/slow deletes)
        pools = this.poolCurator.lockAndLoad(poolIds);

        if (!pools.isEmpty()) {
            log.info("Locked {} pools for deletion...", pools.size());

            // Impl note:
            // There is a fair bit of duplicated work between the actions below this block and
            // methods like revokeEntitlements. However, the decision was made to decouple these
            // methods explicitly to avoid situations such as fetching collections of pools, getting
            // entitlements from them (a slow process in itself) and then passing it off to another
            // standalone method which repeats the process of fetching pools and related entitlements.
            //
            // More work can be done in revokeEntitlements to optimize that method and maybe make it
            // slightly more generic so that this work can be offloaded to it again. Though, at the time
            // of writing, that's no small undertaking. Even changing this method has far-reaching
            // consequences when trying to remove direct uses of entities as far as interoperability is
            // concerned. Going forward we need to be more aware of the amount of duplication we're
            // adding to our code when writing standlone/generic utility methods and linking them
            // together, and perhaps take steps to avoid getting into situations like these two methods.

            // Fetch the list of pools which are related to the entitlements but are *not* being
            // deleted. We'll need to update the quantities on these.
            Collection<String> affectedPoolIds = this.poolCurator.getPoolIdsForEntitlements(entitlementIds);
            affectedPoolIds.removeAll(poolIds);

            // Fetch entitlements (uggh).
            // TODO: Stop doing this. Update the bits below to not use the entities directly and
            // do the updates via queries.
            // Impl note: we have to fetch these in blocks to guard against the case where we're
            // attempting to fetch more entitlements than the parameter limit allows (~32k).
            Set<Entitlement> entitlements = new HashSet<>();

            if (!entitlementIds.isEmpty()) {
                log.debug("IN BLOCK SIZE: {}", this.entitlementCurator.getInBlockSize());
                Iterable<List<String>> blocks =
                    Iterables.partition(entitlementIds, this.entitlementCurator.getInBlockSize());

                for (List<String> block : blocks) {
                    entitlements.addAll(this.entitlementCurator.listAllByIds(block).list());
                }
            }

            // Mark remaining dependent entitlements dirty for this consumer
            this.entitlementCurator.markDependentEntitlementsDirty(entitlementIds);

            // Unlink the pools and entitlements we're about to delete so we don't error out while
            // trying to delete entitlements.
            this.poolCurator.clearPoolSourceEntitlementRefs(poolIds);

            // Revoke/delete entitlements
            if (!entitlements.isEmpty()) {
                log.info("Revoking {} entitlements...", entitlements.size());
                this.entitlementCurator.unlinkEntitlements(entitlements);
                this.entitlementCertificateCurator.deleteByEntitlementIds(entitlementIds);
                this.entitlementCurator.batchDeleteByIds(entitlementIds);
                this.entitlementCurator.flush();
                this.entitlementCurator.batchDetach(entitlements);
                log.info("Entitlements successfully revoked");
            }
            else {
                log.info("Skipping entitlement revocation; no entitlements to revoke");
            }

            // Delete pools
            log.info("Deleting {} pools...", pools.size());
            this.poolCurator.batchDelete(pools, alreadyDeletedPoolIds);
            this.poolCurator.flush();
            log.info("Pools successfully deleted");

            if (!entitlements.isEmpty()) {
                // Update entitlement counts on affected, non-deleted pools
                log.info("Updating entitlement counts on remaining, affected pools...");
                Map<Consumer, List<Entitlement>> consumerStackedEnts = new HashMap<>();
                List<Pool> poolsToSave = new LinkedList<>();
                Set<String> stackIds = new HashSet<>();

                for (Entitlement entitlement : entitlements) {
                    // Since we're sifting through these already, let's also sort them into consumer lists
                    // for some of the other methods we'll be calling later
                    Consumer consumer = entitlement.getConsumer();
                    Pool pool = entitlement.getPool();

                    List<Entitlement> stackedEntitlements = consumerStackedEnts.get(consumer);
                    if (stackedEntitlements == null) {
                        stackedEntitlements = new LinkedList<>();
                        consumerStackedEnts.put(consumer, stackedEntitlements);
                    }

                    if (!"true".equals(pool.getAttributeValue(Pool.Attributes.DERIVED_POOL)) &&
                        pool.hasProductAttribute(Product.Attributes.STACKING_ID)) {

                        stackedEntitlements.add(entitlement);
                        stackIds.add(entitlement.getPool().getStackId());
                    }

                    // Update quantities if the entitlement quantity is non-zero
                    int quantity = entitlement.getQuantity() != null ? entitlement.getQuantity() : 0;
                    if (quantity != 0) {
                        // Update the pool quantities if we didn't delete it
                        if (affectedPoolIds.contains(pool.getId())) {
                            pool.setConsumed(pool.getConsumed() - quantity);
                            poolsToSave.add(pool);
                        }

                        // Update entitlement counts for affected consumers...
                        consumer.setEntitlementCount(consumer.getEntitlementCount() - quantity);

                        // Set the number exported if we're working with a manifest distributor
                        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
                        if (ctype != null && ctype.isManifest()) {
                            pool.setExported(pool.getExported() - quantity);
                        }
                    }
                }

                this.poolCurator.updateAll(poolsToSave, false, false);
                this.consumerCurator.updateAll(consumerStackedEnts.keySet(), false, false);
                this.consumerCurator.flush();
                log.info("Entitlement counts successfully updated for {} pools and {} consumers",
                    poolsToSave.size(), consumerStackedEnts.size());

                // Update stacked entitlements for affected consumers(???)
                if (!stackIds.isEmpty()) {
                    // Get consumer + pool tuples for stack ids
                    Map<String, Set<String>> consumerStackDerivedPoolIds = this.poolCurator
                        .getConsumerStackDerivedPoolIdMap(stackIds);

                    if (!consumerStackDerivedPoolIds.isEmpty()) {
                        log.info("Updating stacked entitlements for {} consumers...",
                            consumerStackDerivedPoolIds.size());

                        for (Consumer consumer : consumerStackedEnts.keySet()) {
                            Set<String> subPoolIds = consumerStackDerivedPoolIds.get(consumer.getId());

                            if (subPoolIds != null && !subPoolIds.isEmpty()) {
                                // Resolve pool IDs...
                                Collection<Pool> subPools = this.poolCurator.listAllByIds(subPoolIds).list();

                                // Invoke the rules engine to update the affected pools
                                if (subPools != null && !subPools.isEmpty()) {
                                    log.debug("Updating {} stacking pools for consumer: {}",
                                        subPools.size(), consumer);

                                    this.poolRules.updatePoolsFromStack(
                                        consumer, subPools, null, alreadyDeletedPoolIds, true);
                                }
                            }
                        }
                    }
                }

                this.consumerCurator.flush();

                // Hydrate remaining consumer pools so we can skip some extra work during serialization
                Set<Pool> poolsToHydrate = new HashSet<>();

                for (Consumer consumer : consumerStackedEnts.keySet()) {
                    for (Entitlement entitlement : consumer.getEntitlements()) {
                        poolsToHydrate.add(entitlement.getPool());
                    }
                }

                this.productCurator.hydratePoolProvidedProducts(poolsToHydrate);

                // Fire post-unbind events for revoked entitlements
                log.info("Firing post-unbind events for {} entitlements...", entitlements.size());
                for (Entitlement entitlement : entitlements) {
                    this.enforcer.postUnbind(entitlement.getConsumer(), this, entitlement);
                }

                log.info("Recomputing status for {} consumers", consumerStackedEnts.keySet().size());

                // Recalculate status for affected consumers
                for (List<Consumer> subList : Iterables.partition(consumerStackedEnts.keySet(), 1000)) {
                    for (Consumer consumer : subList) {
                        this.complianceRules.getStatus(consumer);
                        this.systemPurposeComplianceRules.getStatus(consumer, consumer.getEntitlements(),
                            null, true);

                        // Detach the consumer object (and its children that receive cascaded detaches),
                        // otherwise during the status calculations, the facts proxy objects objects will be
                        // resolved and the memory use will grow linearly with the number of consumers
                        // instead of remaining constant as we calculate the status of each consumer.
                        //
                        // See BZ 1584259 for details
                        this.consumerCurator.detach(consumer);
                    }
                    this.consumerCurator.flush();
                }

                log.info("All statuses recomputed");
            }

            // Impl note:
            // We don't need to fire entitlement revocation events, since they're all being revoked as
            // a consequence of the pools being deleted.

            // Fire pool deletion events
            // This part hurts so much. Because we output the whole entity, we have to fetch the bloody
            // things before we delete them.
            log.info("Firing pool deletion events for {} pools...", pools.size());
            for (Pool pool : pools) {
                this.sink.queueEvent(this.eventFactory.poolDeleted(pool));
            }
        }
        else {
            log.info("Skipping pool deletion; no pools to delete");
        }
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
        this.poolCurator.lock(pool);
        pool.setQuantity(set);
        return poolCurator.merge(pool);
    }

    /**
     * Set the count of pools. The caller sets the absolute quantity.
     *   Current use is setting unlimited bonus pool to -1 or 0.
     */
    @Override
    public void setPoolQuantity(Map<Pool, Long> poolQuantities) {
        if (poolQuantities != null) {
            poolCurator.lock(poolQuantities.keySet());
            for (Entry<Pool, Long> entry : poolQuantities.entrySet()) {
                entry.getKey().setQuantity(entry.getValue());
            }
            poolCurator.mergeAll(poolQuantities.keySet(), false);
        }
    }

    @Override
    public void regenerateDirtyEntitlements(Consumer consumer) {
        if (consumer != null) {
            this.ecGenerator.regenerateCertificatesOf(this.entitlementCurator.listDirty(consumer), false);
        }
    }

    @Override
    public void regenerateDirtyEntitlements(Iterable<Entitlement> entitlements) {
        if (entitlements != null) {
            Map<String, Entitlement> dirty = new HashMap<>();

            for (Entitlement entitlement : entitlements) {
                if (entitlement.isDirty() && !dirty.containsKey(entitlement.getId())) {
                    log.info("Found dirty entitlement to regenerate: {}", entitlement);

                    // Store the entitlement for later processing
                    dirty.put(entitlement.getId(), entitlement);
                }
            }

            // Regenerate the dirty entitlements we found...
            this.ecGenerator.regenerateCertificatesOf(dirty.values(), false);
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

    @Override
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer,
        ActivationKey key, String ownerId, String productId, String subscriptionId, Date activeOn,
        boolean includeWarnings, PoolFilterBuilder filters,
        PageRequest pageRequest, boolean addFuture, boolean onlyFuture, Date after) {

        // Only postfilter if we have to
        boolean postFilter = consumer != null || key != null;

        if (consumer != null && !consumer.isDev()) {
            filters.addAttributeFilter(Pool.Attributes.DEVELOPMENT_POOL, "!true");
        }

        Page<List<Pool>> page = this.poolCurator.listAvailableEntitlementPools(consumer,
            ownerId, productId, subscriptionId, activeOn, filters, pageRequest, postFilter,
            addFuture, onlyFuture, after);

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

        return new Subscription(pool, productCurator);
    }

    @Override
    public CandlepinQuery<Pool> getPoolsBySubscriptionId(String subscriptionId) {
        return this.poolCurator.getPoolsBySubscriptionId(subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<Pool> getMasterPools() {
        return this.poolCurator.getMasterPools();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<Pool> getMasterPoolsForOwner(Owner owner) {
        return this.poolCurator.getMasterPoolsForOwner(owner);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<Pool> getMasterPoolsForOwnerExcludingSubs(Owner owner,
        Collection<String> excludedSubs) {

        return this.poolCurator.getMasterPoolsForOwnerExcludingSubs(owner, excludedSubs);
    }

    @Override
    public Set<String> retrieveServiceLevelsForOwner(String ownerId, boolean exempt) {
        return poolCurator.retrieveServiceLevelsForOwner(ownerId, exempt);
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
    public void updatePoolsFromStackWithoutDeletingStack(Consumer consumer, List<Pool> pools,
        Collection<Entitlement> entitlements) {
        poolRules.updatePoolsFromStack(consumer, pools, entitlements, false);
    }

    public List<Pool> getOwnerSubPoolsForStackId(Owner owner, String stackId) {
        return poolCurator.getOwnerSubPoolsForStackId(owner, stackId);
    }

    private List<Pool> filterPoolsForActKey(ActivationKey key,
        List<Pool> pools, boolean includeWarnings) {
        List<Pool> filteredPools = new LinkedList<>();
        for (Pool p : pools) {
            ValidationResult result = activationKeyRules.runPoolValidationForActivationKey(key, p, null);
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

    @Transactional
    public void recalculatePoolQuantitiesForOwner(Owner owner) {
        poolCurator.calculateConsumedForOwnersPools(owner);
        poolCurator.calculateExportedForOwnersPools(owner);
    }

    /**
     * @{inheritDocs}
     */
    @Override
    public boolean isManaged(Pool pool) {
        // BZ 1452694: Don't delete pools for custom subscriptions
        // We need to verify that we aren't deleting pools that are created via the API.
        // Unfortunately, we don't have a 100% reliable way of detecting such pools at this point,
        // so we'll do the next best thing: In standalone, pools with an upstream pool ID are those
        // we've received from an import (and, thus, are eligible for deletion). In hosted,
        // however, we *are* the upstream source, so everything is eligible for removal.
        // This is pretty hacky, so the way we go about doing this check should eventually be
        // replaced with something more generic and reliable, and not dependent on the config.

        // TODO:
        // Remove the standalone config check and replace it with a check for whether or not the
        // pool is non-custom  -- however we decide to implement that in the future.

        return pool != null &&
            pool.getSourceSubscription() != null &&
            !pool.getType().isDerivedType() &&
            (pool.getUpstreamPoolId() != null || !this.config.getBoolean(ConfigProperties.STANDALONE, true));
    }
}
