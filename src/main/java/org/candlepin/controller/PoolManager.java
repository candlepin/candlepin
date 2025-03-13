/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.bind.PoolOpProcessor;
import org.candlepin.bind.PoolOperations;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.RefreshResult.EntityState;
import org.candlepin.controller.refresher.RefreshWorker;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQualifier;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.paging.Page;
import org.candlepin.policy.EntitlementRefusedException;
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
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.Traceable;
import org.candlepin.util.TraceableParam;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
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
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;

public class PoolManager {
    private static final Logger log = LoggerFactory.getLogger(PoolManager.class);

    private static final int MAX_ENTITLE_RETRIES = 3;

    private final I18n i18n;
    private final PoolCurator poolCurator;
    private final EventSink sink;
    private final EventFactory eventFactory;
    private final Enforcer enforcer;
    private final PoolRules poolRules;
    private final EntitlementCurator entitlementCurator;
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final EntitlementCertificateService ecService;
    private final ComplianceRules complianceRules;
    private final AutobindRules autobindRules;
    private final ActivationKeyRules activationKeyRules;
    private final OwnerCurator ownerCurator;
    private final BindChainFactory bindChainFactory;
    private final Provider<RefreshWorker> refreshWorkerProvider;
    private final PoolOpProcessor poolOpProcessor;
    private final PoolConverter poolConverter;
    private final PoolService poolService;
    private final boolean isStandalone;

    @Inject
    public PoolManager(
        PoolCurator poolCurator,
        EventSink sink,
        EventFactory eventFactory,
        Configuration config,
        Enforcer enforcer,
        PoolRules poolRules,
        EntitlementCurator entitlementCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        EntitlementCertificateService ecService,
        ComplianceRules complianceRules,
        AutobindRules autobindRules,
        ActivationKeyRules activationKeyRules,
        OwnerCurator ownerCurator,
        I18n i18n,
        PoolService poolService,
        BindChainFactory bindChainFactory,
        Provider<RefreshWorker> refreshWorkerProvider,
        PoolOpProcessor poolOpProcessor,
        PoolConverter poolConverter) {

        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.sink = Objects.requireNonNull(sink);
        this.eventFactory = Objects.requireNonNull(eventFactory);
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.enforcer = Objects.requireNonNull(enforcer);
        this.poolRules = Objects.requireNonNull(poolRules);
        this.ecService = Objects.requireNonNull(ecService);
        this.complianceRules = Objects.requireNonNull(complianceRules);
        this.autobindRules = Objects.requireNonNull(autobindRules);
        this.activationKeyRules = Objects.requireNonNull(activationKeyRules);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.bindChainFactory = Objects.requireNonNull(bindChainFactory);
        this.refreshWorkerProvider = Objects.requireNonNull(refreshWorkerProvider);
        this.poolOpProcessor = Objects.requireNonNull(poolOpProcessor);
        this.poolConverter = Objects.requireNonNull(poolConverter);
        this.poolService = Objects.requireNonNull(poolService);
        this.isStandalone = config.getBoolean(ConfigProperties.STANDALONE);
    }

    /*
     * We need to update/regen entitlements in the same transaction we update pools so we don't miss
     * anything
     */
    @SuppressWarnings("checkstyle:methodlength")
    @Traceable
    void refreshPoolsWithRegeneration(SubscriptionServiceAdapter subAdapter,
        @TraceableParam("owner") Owner owner, boolean lazy) {

        Date now = new Date();
        Owner resolvedOwner = this.resolveOwner(owner);
        log.info("Refreshing pools for owner: {}", resolvedOwner);

        RefreshWorker refresher = this.refreshWorkerProvider.get();

        log.debug("Fetching subscriptions from adapter...");
        refresher.addSubscriptions(subAdapter.getSubscriptions(resolvedOwner.getKey()));
        Map<String, ? extends SubscriptionInfo> subMap = refresher.getSubscriptions();

        // Execute refresh!
        RefreshResult refreshResult = refresher.execute(resolvedOwner);

        List<EntityState> existingStates = List.of(
            EntityState.CREATED, EntityState.UPDATED, EntityState.UNCHANGED);

        List<EntityState> mutatedStates = List.of(
            EntityState.CREATED, EntityState.UPDATED, EntityState.DELETED);

        Map<String, Product> existingProducts = refreshResult.getEntities(Product.class, existingStates);
        Map<String, Product> updatedProducts = refreshResult.getEntities(Product.class, EntityState.UPDATED);

        // TODO: Move everything below this line to the refresher
        this.poolCurator.transactional(args -> {
            boolean poolsModified = false;

            // Flag pools referencing the updated products as dirty
            List<String> updatedProductUuids = updatedProducts.values()
                .stream()
                .map(Product::getUuid)
                .toList();

            int count = this.poolCurator.markPoolsDirtyReferencingProducts(updatedProductUuids);
            log.debug("Flagged {} pool-products as dirty", count);

            // TODO: We *could* also flag entitlements dirty here, but if the lazy flag is set to
            // false, we'll need to pull all of them back and immediately regen; which could be
            // catastrophic in terms of memory utilization and refresh runtime. Perhaps revisit this
            // when we get around to a refresh refactor. :/

            // Gather local subscriptions for pool refresh
            Map<String, List<Pool>> subscriptionPools = this.poolCurator
                .mapPoolsBySubscriptionIds(subMap.keySet());

            log.debug("Refreshing {} pool(s)...", subMap.size());
            for (Iterator<? extends SubscriptionInfo> si = subMap.values().iterator(); si.hasNext();) {
                SubscriptionInfo sub = si.next();

                if (now.after(sub.getEndDate())) {
                    log.info("Skipping expired subscription: {}", sub);

                    si.remove();
                    continue;
                }

                log.debug("Processing subscription: {}", sub);
                Pool pool = this.poolConverter.convertToPrimaryPool(sub, resolvedOwner, existingProducts);

                // This pool originates from an upstream source and should not be modified locally via API
                pool.setManaged(true);

                List<Pool> subPools = subscriptionPools.getOrDefault(sub.getId(), Collections.emptyList());
                this.refreshPoolsForPrimaryPool(pool, false, lazy, updatedProducts, subPools);
                poolsModified = true;
            }

            // Flush our newly created pools
            this.poolCurator.flush();

            // delete pools whose subscription disappeared:
            log.debug("Deleting pools for absent subscriptions...");
            List<Pool> poolsToDelete = new ArrayList<>();

            for (Pool pool : poolCurator.getPoolsFromBadSubs(resolvedOwner, subMap.keySet())) {
                if (pool != null && pool.isManaged()) {
                    poolsToDelete.add(pool);
                    poolsModified = true;
                }
            }

            this.poolService.deletePools(poolsToDelete);

            // TODO: break this call into smaller pieces. There may be lots of floating pools
            log.debug("Updating floating pools...");
            List<Pool> floatingPools = poolCurator.getOwnersFloatingPools(resolvedOwner);
            updateFloatingPools(floatingPools, lazy, updatedProducts);

            // Check if we've put any pools into a state in which they're referencing a product which no
            // longer belongs to the organization
            List<Pool> invalidPools = poolCurator.getPoolsReferencingInvalidProducts(resolvedOwner.getId());
            if (!invalidPools.isEmpty()) {
                String errmsg = "One or more pools references a product which does not belong to its " +
                    "organization";

                log.error(errmsg);
                invalidPools.forEach(pool -> log.error("{} => {}", pool, pool.getProduct()));

                throw new IllegalStateException(errmsg);
            }

            // If we've updated or deleted a pool and we have product/content changes, our content view has
            // likely changed.
            //
            // Impl note:
            // We're abusing some facts about how this whole process works *at the time of writing* to make
            // a fairly accurate guess as to whether or not the content view changed. That is:
            //
            // - We only receive product and content info from subscriptions provided upstream
            // - We only call refreshPoolsForPrimaryPool or deletePools based on matches with the
            //   received subscriptions
            //
            // By checking both of these, we can estimate that if we have changed products/content or
            // have refreshed or deleted a pool, we likely have a content view update. Note that this
            // does fall apart in a handful of cases (deletion of an expired pool + modification of that
            // pool's product/content), but barring a major refactor of this code to make the evaluation
            // on a per-pool basis, there's not a whole lot more we can do here.
            if (poolsModified || refreshResult.hasEntity(Product.class, mutatedStates)) {
                // TODO: Should we also mark any existing SCA certs as dirty/revoked here?

                resolvedOwner.setLastContentUpdate(now);
                this.ownerCurator.merge(resolvedOwner);
            }

            // Set the last content update for all (other*) orgs with pools referencing any of the
            // products that changed as part of this refresh.
            this.ownerCurator.setLastContentUpdateForOwnersWithProducts(updatedProductUuids);

            log.info("Refresh pools for owner: {} completed in: {}ms", resolvedOwner.getKey(),
                System.currentTimeMillis() - now.getTime());

            return null;
        }).allowExistingTransactions()
            .execute();
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

    void refreshPoolsForPrimaryPool(Pool pool, boolean updateStackDerived, boolean lazy,
        Map<String, Product> changedProducts) {

        // These don't all necessarily belong to this owner
        List<Pool> subscriptionPools;

        if (pool.getSubscriptionId() != null) {
            subscriptionPools = this.poolCurator.getPoolsBySubscriptionId(pool.getSubscriptionId());

            if (log.isDebugEnabled()) {
                log.debug("Found {} pools for subscription {}", subscriptionPools.size(),
                    pool.getSubscriptionId());

                for (Pool p : subscriptionPools) {
                    log.debug("    owner={} - {}", p.getOwner().getKey(), p);
                }
            }
        }
        else {
            // If we don't have a subscription ID, this *is* the primary pool, but we need to use
            // the original, hopefully unmodified, pool
            subscriptionPools = pool.getId() != null ?
                Collections.<Pool>singletonList(this.poolCurator.get(pool.getId())) :
                Collections.<Pool>singletonList(pool);
        }

        this.refreshPoolsForPrimaryPool(pool, updateStackDerived, lazy, changedProducts, subscriptionPools);
    }

    @Transactional
    void refreshPoolsForPrimaryPool(Pool pool, boolean updateStackDerived, boolean lazy,
        Map<String, Product> changedProducts, List<Pool> subscriptionPools) {

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

            if (pool.isManaged()) {
                subPool.setManaged(true);
            }
        }

        // Cleans up pools on other owners who have migrated subs away
        removeAndDeletePoolsOnOtherOwners(subscriptionPools, pool);

        // capture the original quantity to check for updates later
        Long originalQuantity = pool.getQuantity();

        // BZ 1012386: This will regenerate primary/derived for bonus scenarios if only one of the
        // pair still exists.
        this.createAndEnrichPools(pool, subscriptionPools, false);

        // don't update floating here, we'll do that later so we don't update anything twice
        Set<String> updatedPrimaryPools = updatePoolsForPrimaryPool(
            subscriptionPools, pool, originalQuantity, updateStackDerived, changedProducts);

        this.ecService.regenerateCertificatesByEntitlementIds(updatedPrimaryPools, lazy);
    }

    private void removeAndDeletePoolsOnOtherOwners(List<Pool> existingPools, Pool pool) {
        List<Pool> toRemove = new LinkedList<>();

        for (Pool existing : existingPools) {
            if (!existing.getOwner().equals(pool.getOwner())) {
                toRemove.add(existing);
                log.warn("Removing {} because it exists in the wrong org", existing);
                if (existing.getType() == PoolType.NORMAL || existing.getType() == PoolType.BONUS) {
                    this.poolService.deletePool(existing);
                }
            }
        }

        existingPools.removeAll(toRemove);
    }

    /**
     * Deletes all known expired pools. The deletion of expired pools also triggers entitlement
     * revocation and consumer compliance recalculation.
     * <p>
     * </p>
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
        }
        while (loop);

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
        this.poolService.deletePools(pools);
        this.poolCurator.flush();

        return pools.size();
    }

    /**
     * Update pool for primary pool.
     *
     * @param existingPools the existing pools
     * @param pool the primary pool
     * @param originalQuantity the pool's original quantity before multiplier was applied
     * @param updateStackDerived whether or not to attempt to update stack
     *        derived pools
     */
    Set<String> updatePoolsForPrimaryPool(List<Pool> existingPools, Pool pool, Long originalQuantity,
        boolean updateStackDerived, Map<String, Product> changedProducts) {

        /*
         * Rules need to determine which pools have changed, but the Java must
         * send out the events. Create an event for each pool that could change,
         * even if we won't use them all.
         */
        if (existingPools == null || existingPools.isEmpty()) {
            return new HashSet<>(0);
        }

        log.debug("Updating {} pools for existing primary pool: {}", existingPools.size(), pool);

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

        String virtLimit = pool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT);
        boolean createsSubPools = !StringUtils.isBlank(virtLimit) && !"0".equals(virtLimit);

        // Update subpools if necessary
        if (updateStackDerived && !updatedPools.isEmpty() && createsSubPools && pool.isStacked()) {
            // Get all pools for the primary pool owner derived from the pool's
            // stack id, because we cannot look it up by subscriptionId
            List<Pool> subPools = this.poolCurator.getOwnerSubPoolsForStackId(pool.getOwner(),
                pool.getStackId());

            for (Pool subPool : subPools) {
                PoolUpdate update = this.poolRules.updatePoolFromStack(subPool, changedProducts);

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

            // quantity has changed. delete any excess entitlements from pool
            // the quantity has not yet been expressed on the pool itself
            if (updatedPool.getQuantityChanged()) {
                poolsQtyUpdated.add(existingPool);
            }

            // dates changed. regenerate all entitlement certificates
            if (updatedPool.getDatesChanged() || updatedPool.getProductsChanged()) {
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

        // Check if we need to execute the revocation plan
        if (!poolsQtyUpdated.isEmpty()) {
            this.revokeEntitlementsFromOverflowingPools(poolsQtyUpdated);
        }

        // Fetch entitlement IDs for updated pools...
        if (!poolsToRegenEnts.isEmpty()) {
            entitlementsToRegen.addAll(this.poolCurator.retrieveOrderedEntitlementIdsOf(poolsToRegenEnts));
        }

        // Delete pools marked for deletion
        if (!poolsToDelete.isEmpty()) {
            this.poolService.deletePools(poolsToDelete);
        }

        // Return entitlement IDs in need regeneration
        return entitlementsToRegen;
    }

    Set<Pool> revokeEntitlementsFromOverflowingPools(List<Pool> pools) {
        Collection<Pool> overflowing = pools.stream()
            .filter(Pool::isOverflowing)
            .toList();

        if (overflowing.isEmpty()) {
            return null;
        }

        List<Entitlement> entitlementsToRevoke = new ArrayList<>();

        // Impl note: this may remove pools which are not backed by the DB.
        overflowing = poolCurator.lock(overflowing);

        List<Entitlement> overFlowingEnts = this.poolCurator.retrieveOrderedEntitlementsOf(overflowing);
        Map<String, List<Entitlement>> entMap = groupByPoolId(overFlowingEnts);

        for (Pool pool : overflowing) {
            // we then start revoking the existing entitlements
            List<Entitlement> entitlements = entMap.get(pool.getId());
            long newConsumed = pool.getConsumed();

            // deletes ents in order of date since we retrieved and put them in the map in order.
            if (entitlements != null) {
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
        }

        // revoke the entitlements amassed above
        return this.poolService.revokeEntitlements(entitlementsToRevoke);
    }

    private Map<String, List<Entitlement>> groupByPoolId(Collection<Entitlement> entitlements) {
        return entitlements.stream()
            .collect(Collectors.groupingBy(entitlement -> entitlement.getPool().getId()));
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

        Set<String> entIds = processPoolUpdates(poolEvents, updatedPools);
        this.poolCurator.flush();

        this.ecService.regenerateCertificatesByEntitlementIds(entIds, lazy);
    }

    /**
     * @param sub
     * @return the newly created Pool(s)
     */
    public List<Pool> createAndEnrichPools(SubscriptionInfo sub) {
        return createAndEnrichPools(sub, Collections.<Pool>emptyList());
    }

    public List<Pool> createAndEnrichPools(SubscriptionInfo sub, List<Pool> existingPools) {
        return this.createAndEnrichPools(sub, existingPools, true);
    }

    public List<Pool> createAndEnrichPools(SubscriptionInfo sub, List<Pool> existingPools, boolean flush) {
        List<Pool> pools = poolRules.createAndEnrichPools(sub, existingPools);
        log.debug("Creating {} pools for subscription: {}", pools.size(), sub);

        for (Pool pool : pools) {
            this.poolService.createPool(pool, false);
        }

        if (flush) {
            this.poolCurator.flush();
        }

        return pools;
    }

    /**
     * Create any pools that need to be created for the given pool.
     *
     * @param pool
     * @return original pool, enriched
     */
    public Pool createAndEnrichPools(Pool pool) {
        return createAndEnrichPools(pool, Collections.<Pool>emptyList(), true);
    }

    public Pool createAndEnrichPools(Pool pool, List<Pool> existingPools) {
        return this.createAndEnrichPools(pool, existingPools, true);
    }

    @Transactional
    public Pool createAndEnrichPools(Pool pool, List<Pool> existingPools, boolean flush) {
        List<Pool> pools = poolRules.createAndEnrichPools(pool, existingPools);
        log.debug("Creating {} pools: ", pools.size());

        for (Pool p : pools) {
            this.poolService.createPool(p, false);
        }

        if (flush) {
            this.poolCurator.flush();
        }

        return pool;
    }

    /**
     * Applies changes to the given pool to itself and any of its derived pools. This may result in
     * a deletion of the pool if it has been expired as a result of the changes.
     *
     * @param pool
     *  The pool to update
     */
    public void updatePrimaryPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        // Ensure the subscription is not expired
        if (pool.getEndDate() != null && pool.getEndDate().before(new Date())) {
            this.deletePoolsForSubscriptions(Collections.<String>singletonList(pool.getSubscriptionId()));
        }
        else {
            this.refreshPoolsForPrimaryPool(pool, false, true, Collections.<String, Product>emptyMap());
        }
    }

    /**
     * Deletes the pools associated with the specified subscription IDs.
     *
     * @param subscriptionIds
     *  A collection of subscription IDs used to lookup and delete pools
     */
    public void deletePoolsForSubscriptions(Collection<String> subscriptionIds) {
        if (subscriptionIds == null) {
            throw new IllegalArgumentException("subscriptionIds is null");
        }

        if (!subscriptionIds.isEmpty()) {
            for (Pool pool : this.poolCurator.getPoolsBySubscriptionIds(subscriptionIds)) {
                this.poolService.deletePool(pool);
            }
        }
    }

    // TODO:
    // Remove these methods or update them to properly mirror the curator.

    public List<String> listEntitledConsumerUuids(String poolId) {
        return this.poolCurator.listEntitledConsumerUuids(poolId);
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
        SortedSet<String> productIds = data.getProductIds();
        Collection<String> fromPools = data.getPossiblePools();
        Date entitleDate = data.getOnDate();
        String ownerId = consumer.getOwnerId();

        List<PoolQuantity> bestPools = getBestPools(consumer, productIds, entitleDate,
            ownerId, null, fromPools);
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

        PoolQualifier qualifier = new PoolQualifier()
            .addIds(fromPools)
            .setOwnerId(ownerId)
            .setConsumer(host)
            .setActiveOn(activePoolDate);

        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(qualifier)
            .getPageData();
        log.debug("Found {} total pools in org.", allOwnerPools.size());
        logPools(allOwnerPools);

        qualifier.setConsumer(guest);

        List<Pool> allOwnerPoolsForGuest = this.listAvailableEntitlementPools(qualifier)
            .getPageData();
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

        Set<String> productIds = new TreeSet<>();

        //we only want to heal red products, not yellow
        productIds.addAll(guestCompliance.getNonCompliantProducts());
        log.debug("Guest's non-compliant products: {}", productIds);

        /*Do not attempt to create subscriptions for products that
          already have virt_only pools available to the guest */
        Set<String> productsToRemove = getProductsToRemove(allOwnerPoolsForGuest, productIds);
        log.debug("Guest already will have virt-only pools to cover: {}", productsToRemove);

        productIds.removeAll(productsToRemove);
        log.debug("Attempting host autobind for guest products: {}", productIds);

        // Bulk fetch our provided and derived provided product IDs so we're not hitting the DB
        // several times for this lookup.
        Map<String, Set<String>> providedProductIds = this.poolCurator
            .getProvidedProductIdsByPools(allOwnerPools);

        Map<String, Set<String>> derivedProvidedProductIds = this.poolCurator
            .getDerivedProvidedProductIdsByPools(allOwnerPools);

        for (Pool pool : allOwnerPools) {
            boolean providesProduct = false;
            boolean matchesAddOns = false;
            boolean matchesRole = false;

            Product poolProduct = pool.getProduct();
            Product poolDerived = pool.getDerivedProduct();

            // Would parse the int here, but it can be 'unlimited'
            // and we only need to check that it's non-zero
            if (poolProduct.hasAttribute(Product.Attributes.VIRT_LIMIT) &&
                !poolProduct.getAttributeValue(Product.Attributes.VIRT_LIMIT).equals("0")) {

                Map<String, Set<String>> providedProductMap;
                String baseProductId;

                // Determine which set of provided products we should use...
                if (poolDerived != null) {
                    providedProductMap = derivedProvidedProductIds;
                    baseProductId = poolDerived.getId();
                }
                else {
                    providedProductMap = providedProductIds;
                    baseProductId = poolProduct.getId();
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
                    matchesAddOns = this.poolAddonsContainsConsumerAddons(pool, host);
                    matchesRole = this.poolRolesContainsConsumerRole(pool, host);
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
                log.debug("  {}", poolQuantity.getPool());
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
        Map<String, Set<String>> providedProductIdsByPool = this.poolCurator
            .getProvidedProductIdsByPools(allOwnerPoolsForGuest);

        for (Pool pool : allOwnerPoolsForGuest) {
            if (pool == null || pool.getProduct() == null) {
                continue;
            }

            Product poolProduct = pool.getProduct();

            if (pool.hasAttribute(Pool.Attributes.VIRT_ONLY) ||
                poolProduct.hasAttribute(Product.Attributes.VIRT_ONLY)) {

                if (tmpSet.contains(poolProduct.getId())) {
                    productsToRemove.add(poolProduct.getId());
                }

                Set<String> providedProductIds = providedProductIdsByPool.get(pool.getId());
                if (providedProductIds != null) {
                    for (String pid : providedProductIds) {
                        if (tmpSet.contains(pid)) {
                            productsToRemove.add(pid);
                        }
                    }
                }
            }
        }

        return productsToRemove;
    }

    private void logPools(Collection<Pool> pools) {
        if (log.isDebugEnabled()) {
            for (Pool pool : pools) {
                log.debug("  {}", pool);
            }
        }
    }

    /**
     * Checks if any of the addons of the provided pool's marketing product match the addons
     * specified for the consumer.
     *
     * @param pool
     *  the pool to test; cannot be null
     *
     * @param consumer
     *  the consumer to test; cannot be null
     *
     * @return
     *  true if any of the addons defined by the pool's marketing product match any of the
     *  consumer's addons; false otherwise
     */
    private boolean poolAddonsContainsConsumerAddons(Pool pool, Consumer consumer) {
        Set<String> consumerAddons = consumer.getAddOns();
        if (consumerAddons != null && !consumerAddons.isEmpty()) {
            String prodAddons = pool.getProductAttributes().get(Product.Attributes.ADDONS);
            if (prodAddons != null) {

                // Since we need a case-insensitive lookup here, we can't use the set's
                // .contains method. :(
                for (String prodAddon : Util.toList(prodAddons)) {
                    for (String consumerAddon : consumerAddons) {
                        if (consumerAddon != null &&
                            prodAddon.equalsIgnoreCase(consumerAddon.trim())) {

                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks if any of the roles defined on the pool's marketing product match the consumer's role.
     *
     * @param pool
     *  the pool to test; cannot be null
     *
     * @param consumer
     *  the consumer to test; cannot be null
     *
     * @return
     *  true if any of the roles defined by the pool's marketing product match the consumer's role;
     *  false otherwise
     */
    private boolean poolRolesContainsConsumerRole(Pool pool, Consumer consumer) {
        // Check if the product's roles match the consumer's role
        String prodRoles = pool.getProductAttributes().get(Product.Attributes.ROLES);
        if (prodRoles != null) {
            for (String prodRole : Util.toList(prodRoles)) {
                if (prodRole.equalsIgnoreCase(consumer.getRole())) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<PoolQuantity> getBestPools(Consumer consumer, Collection<String> productIds, Date entitleDate,
        String ownerId, String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException {

        Map<String, ValidationResult> failedResults = new HashMap<>();

        Date activePoolDate = entitleDate;
        if (entitleDate == null) {
            activePoolDate = new Date();
        }

        PoolQualifier qualifier = new PoolQualifier()
            .addIds(fromPools)
            .setOwnerId(ownerId)
            .setConsumer(consumer)
            .setActiveOn(activePoolDate);

        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(qualifier)
            .getPageData();
        List<Pool> filteredPools = new LinkedList<>();

        // We have to check compliance status here so we can replace an empty
        // array of product IDs with the array the consumer actually needs. (i.e. during
        // a healing request)
        ComplianceStatus compliance = complianceRules.getStatus(consumer, entitleDate, false);

        if (productIds == null || productIds.isEmpty()) {
            log.debug("No products specified for bind, checking compliance to see what is needed.");

            productIds = new HashSet<>();
            productIds.addAll(compliance.getNonCompliantProducts());
            productIds.addAll(compliance.getPartiallyCompliantProducts().keySet());
        }

        log.debug("Attempting for products on date: {}: {}", entitleDate, productIds);

        // Bulk fetch our provided product IDs so we're not hitting the DB several times
        // for this lookup.
        Map<String, Set<String>> providedProductIds = this.poolCurator
            .getProvidedProductIdsByPools(allOwnerPools);

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
                    matchesAddOns = this.poolAddonsContainsConsumerAddons(pool, consumer);
                    matchesRole = this.poolRolesContainsConsumerRole(pool, consumer);
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
        if (filteredPools.isEmpty() && !failedResults.isEmpty()) {
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
    @Transactional
    public List<Entitlement> entitleByPools(Consumer consumer, Map<String, Integer> poolQuantities)
        throws EntitlementRefusedException {

        if (MapUtils.isNotEmpty(poolQuantities)) {
            return createEntitlements(consumer, poolQuantities, CallerType.BIND);
        }

        return new ArrayList<>();
    }

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
            log.warn("Entitlement not updated: {} for pool: {}", result.getErrors(), pool.getId());

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
        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entMap, new ArrayList<>(), true, poolQuantityMap);
        this.poolOpProcessor.process(poolOperations);

        // we might have changed the bonus pool quantities, revoke ents if needed.
        checkBonusPoolQuantities(consumer.getOwnerId(), entMap);

        this.entitlementCurator.markEntitlementsDirty(Collections.singleton(entitlement.getId()));

        /*
         * If the consumer is not a distributor, check consumer's new compliance
         * status and save. the getStatus call does that internally.
         * all consumer's entitlement count are updated though, so we need to update irrespective
         * of the consumer type.
         */
        complianceRules.getStatus(consumer, null, false, false);
        // Note: a quantity change should *not* need a system purpose compliance recalculation. if that is
        // not true anymore, we should update that here.
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

    public void regenerateDirtyEntitlements(Consumer consumer) {
        if (consumer != null) {
            this.ecService.regenerateCertificatesOf(this.entitlementCurator.listDirty(consumer), false);
        }
    }

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
            this.ecService.regenerateCertificatesOf(dirty.values(), false);
        }
    }

    /**
     * Lists available entitlement pools that match the provided criteria.
     *
     * @param qualifier
     *  a {@link PoolQualifier} with defined criteria for determining which pools should be returned
     *
     * @return a list of pools that meet the defined criteria
     */
    public Page<List<Pool>> listAvailableEntitlementPools(PoolQualifier qualifier) {
        if (qualifier == null) {
            Page<List<Pool>> emptyPage = new Page<>();
            emptyPage.setPageData(Collections.emptyList());
            emptyPage.setMaxRecords(0);

            return emptyPage;
        }

        Page<List<Pool>> page = this.poolCurator.listAvailableEntitlementPools(qualifier);
        if (page.getPageData() == null || page.getPageData().isEmpty()) {
            return page;
        }

        ActivationKey key = qualifier.getActivationKey();
        Consumer consumer = qualifier.getConsumer();
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

        boolean includeWarnings = qualifier.includeWarnings();
        if (consumer != null) {
            resultingPools = enforcer.filterPools(consumer, resultingPools, includeWarnings);
        }

        if (key != null) {
            resultingPools = this.filterPoolsForActKey(key, resultingPools, includeWarnings);
        }

        // Set maxRecords once we are done filtering
        page.setMaxRecords(resultingPools.size());
        page.setPageData(resultingPools);

        return page;
    }

    /**
     * Retrieves a list of pools associated with the specified subscription ID. If there are no
     * pools associated with the given subscription, this method should return an empty list.
     *
     * @param subscriptionId
     *  The subscription ID to use to lookup pools
     *
     * @return
     *  a list of pools associated with the specified subscription.
     */
    public List<Pool> getPoolsBySubscriptionId(String subscriptionId) {
        return this.poolCurator.getPoolsBySubscriptionId(subscriptionId);
    }

    /**
     * Retrieves a list consisting of all known primary pools.
     *
     * @return
     *  a list of known primary pools
     */
    public List<Pool> getPrimaryPools() {
        return this.poolCurator.getPrimaryPools();
    }

    /**
     *  Get the available service levels for consumers for this owner. Exempt
     *  means that a product pool with this level can be used with a consumer of any
     *  service level.
     *
     * @param ownerId The ownerId that has the list of available service levels for
     *              its consumers
     * @param exempt boolean to show if the desired list is the levels that are
     *               explicitly marked with the support_level_exempt attribute.
     * @return Set of levels based on exempt flag.
     */
    public Set<String> retrieveServiceLevelsForOwner(String ownerId, boolean exempt) {
        return poolCurator.retrieveServiceLevelsForOwner(ownerId, exempt);
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

}
