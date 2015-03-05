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
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Subscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.Util;
import org.candlepin.version.CertVersionConflictException;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Set;

/**
 * PoolManager
 */
public class CandlepinPoolManager implements PoolManager {

    private PoolCurator poolCurator;
    private static Logger log = LoggerFactory.getLogger(CandlepinPoolManager.class);

    private EventSink sink;
    private EventFactory eventFactory;
    private Configuration config;
    private Enforcer enforcer;
    private PoolRules poolRules;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private EntitlementCertServiceAdapter entCertAdapter;
    private EntitlementCertificateCurator entitlementCertificateCurator;
    private ComplianceRules complianceRules;
    private ProductCache productCache;
    private AutobindRules autobindRules;
    private ActivationKeyRules activationKeyRules;
    private ProductCurator prodCurator;

    /**
     * @param poolCurator
     * @param subAdapter
     * @param sink
     * @param eventFactory
     * @param config
     */
    @Inject
    public CandlepinPoolManager(PoolCurator poolCurator,
        SubscriptionServiceAdapter subAdapter,
        ProductCache productCache,
        EntitlementCertServiceAdapter entCertAdapter, EventSink sink,
        EventFactory eventFactory, Configuration config, Enforcer enforcer,
        PoolRules poolRules, EntitlementCurator curator1, ConsumerCurator consumerCurator,
        EntitlementCertificateCurator ecC, ComplianceRules complianceRules,
        AutobindRules autobindRules, ActivationKeyRules activationKeyRules,
        ProductCurator prodCurator) {

        this.poolCurator = poolCurator;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.config = config;
        this.entitlementCurator = curator1;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
        this.poolRules = poolRules;
        this.entCertAdapter = entCertAdapter;
        this.entitlementCertificateCurator = ecC;
        this.complianceRules = complianceRules;
        this.productCache = productCache;
        this.autobindRules = autobindRules;
        this.activationKeyRules = activationKeyRules;
        this.prodCurator = prodCurator;
    }

    /*
     * We need to update/regen entitlements in the same transaction we update pools
     * so we don't miss anything
     */
    void refreshPoolsWithRegeneration(SubscriptionServiceAdapter subAdapter, Owner owner, boolean lazy) {
        log.info("Refreshing pools for owner: " + owner.getKey());
        List<Subscription> subs = subAdapter.getSubscriptions(owner);
        Set<String> subIds = Util.newSet();
        log.debug("Found " + subIds.size() + " existing subscriptions.");

        Set<Product> changedProducts = refreshProducts(owner, subs);

        List<String> deletedSubs = new LinkedList<String>();
        for (Subscription sub : subs) {
            String subId = sub.getId();
            subIds.add(subId);

            // Remove expired subscriptions
            if (isExpired(sub)) {
                deletedSubs.add(subId);
                log.info("Skipping expired subscription: " + sub);
                continue;
            }

            refreshPoolsForSubscription(subAdapter, sub, lazy, changedProducts);
        }

        // We deleted some, need to take that into account so we
        // remove everything that isn't actually active
        subIds.removeAll(deletedSubs);
        // delete pools whose subscription disappeared:
        for (Pool p : poolCurator.getPoolsFromBadSubs(owner, subIds)) {
            if (p.getType() == PoolType.NORMAL || p.getType() == PoolType.BONUS) {
                deletePool(subAdapter, p);
            }
        }

        // TODO: break this call into smaller pieces.  There may be lots of floating pools
        List<Pool> floatingPools = poolCurator.getOwnersFloatingPools(owner);
        updateFloatingPools(subAdapter, floatingPools, lazy, changedProducts);
    }

    Set<Product> refreshProducts(Owner o, List<Subscription> subs) {

        /*
         * Build a master list of all products on the incoming subscriptions. Note that
         * these product objects are detached, and need to be synced with what's in the
         * database.
         */
        Set<Product> allProducts = Util.newSet();
        for (Subscription sub : subs) {
            allProducts.add(sub.getProduct());
            allProducts.addAll(sub.getProvidedProducts());
        }

        return getChangedProducts(o, allProducts);
    }

    public Set<Product> getChangedProducts(Owner o, Set<Product> allProducts) {
        Set<Product> changedProducts = Util.newSet();

        log.debug("Syncing {} incoming products.", allProducts.size());
        for (Product incoming : allProducts) {
            Product existing = prodCurator.lookupById(o, incoming.getId());
            // TODO: compare and update
            if (existing == null) {
                log.info("Creating new product for org {}: {}", o.getKey(),
                        incoming.getId());
                prodCurator.create(incoming);
            }
            else {
                if (hasProductChanged(existing, incoming)) {
                    log.info("Product changed for org {}: {}", o.getKey(),
                            incoming.getId());
                    prodCurator.createOrUpdate(incoming);
                    changedProducts.add(incoming);
                    // TODO: signal back to caller the set of changed products, we'll
                    // need to know during refreshing of existing pools.
                }
            }
        }
        return changedProducts;
    }

    // TODO: move to comparator?
    protected final boolean hasProductChanged(Product existingProd, Product importedProd) {
        // trying to go in order from least to most work.
        if (!existingProd.getName().equals(importedProd.getName())) {
            return true;
        }

        if (!existingProd.getMultiplier().equals(importedProd.getMultiplier())) {
            return true;
        }

        if (existingProd.getAttributes().size() != importedProd.getAttributes().size()) {
            return true;
        }
        if (Sets.intersection(existingProd.getAttributes(),
            importedProd.getAttributes()).size() != existingProd.getAttributes().size()) {
            return true;
        }

        if (existingProd.getProductContent().size() != importedProd.getProductContent().size()) {
            return true;
        }
        if (Sets.intersection(new HashSet<ProductContent>(existingProd.getProductContent()),
                new HashSet<ProductContent>(importedProd.getProductContent())).size() !=
                existingProd.getProductContent().size()) {
            return true;
        }

        return false;
    }

    @Transactional
    void refreshPoolsForSubscription(SubscriptionServiceAdapter subAdapter,
            Subscription sub, boolean lazy, Set<Product> changedProducts) {

        // These don't all necessarily belong to this owner
        List<Pool> subscriptionPools = poolCurator.getPoolsBySubscriptionId(sub.getId());

        // Cleans up pools on other owners who have migrated subs away
        removeAndDeletePoolsOnOtherOwners(subAdapter, subscriptionPools, sub);

        // BUG 1012386 This will regenerate master/derived for bonus scenarios
        //  if only one of the pair still exists.
        createPoolsForSubscription(sub, subscriptionPools);

        // don't update floating here, we'll do that later
        // so we don't update anything twice
        regenerateCertificatesByEntIds(subAdapter,
                updatePoolsForSubscription(subAdapter, subscriptionPools,
                        sub, false, changedProducts), lazy);
    }

    public void cleanupExpiredPools(SubscriptionServiceAdapter subAdapter) {
        List<Pool> pools = poolCurator.listExpiredPools();
        log.info("Expired pools: " + pools.size());
        for (Pool p : pools) {
            if (p.hasAttribute("derived_pool")) {
                // Derived pools will be cleaned up when their parent entitlement
                // is revoked.
                continue;
            }
            log.info("Cleaning up expired pool: " + p.getId() +
                " (" + p.getEndDate() + ")");

            if (!subAdapter.isReadOnly()) {
                Subscription sub = subAdapter.getSubscription(p.getSubscriptionId());
                // In case it was already deleted:
                if (sub != null) {
                    subAdapter.deleteSubscription(sub);
                }
            }
            deletePool(subAdapter, p);
        }
    }

    private boolean isExpired(Subscription subscription) {
        Date now = new Date();
        return now.after(subscription.getEndDate());
    }

    @Transactional
    private void deleteExcessEntitlements(SubscriptionServiceAdapter subAdapter, Pool existingPool) {
        boolean lifo = !config
            .getBoolean(ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER);

        if (existingPool.isOverflowing()) {
            // if pool quantity has reduced, then start with revocation.
            Iterator<Entitlement> iter = this.poolCurator
                .retrieveFreeEntitlementsOfPool(existingPool, lifo).iterator();

            long consumed = existingPool.getConsumed();
            long existing = existingPool.getQuantity();
            while (consumed > existing && iter.hasNext()) {
                Entitlement e = iter.next();
                revokeEntitlement(subAdapter, e);
                consumed -= e.getQuantity();
            }
        }
    }

    void removeAndDeletePoolsOnOtherOwners(SubscriptionServiceAdapter subAdapter, List<Pool> existingPools, Subscription sub) {
        List<Pool> toRemove = new LinkedList<Pool>();
        for (Pool existing : existingPools) {
            if (!existing.getOwner().equals(sub.getOwner())) {
                toRemove.add(existing);
                log.warn("Removing " + existing + " because it exists in the wrong org");
                if (existing.getType() == PoolType.NORMAL ||
                    existing.getType() == PoolType.BONUS) {
                    deletePool(subAdapter, existing);
                }
            }
        }
        existingPools.removeAll(toRemove);
    }

    /**
     * Update pool for subscription. - This method only checks for change in
     * quantity and dates of a subscription. Currently any quantity changes in
     * pool are not handled.
     *
     * @param existingPools the existing pools
     * @param sub the sub
     * @param updateStackDerived wheter or not to attempt to update stack derived
     * subscriptions
     */
    Set<String> updatePoolsForSubscription(SubscriptionServiceAdapter subAdapter,
            List<Pool> existingPools, Subscription sub, boolean updateStackDerived,
            Set<Product> changedProducts) {

        /*
         * Rules need to determine which pools have changed, but the Java must
         * send out the events. Create an event for each pool that could change,
         * even if we won't use them all.
         */
        if (existingPools == null || existingPools.isEmpty()) {
            return new HashSet<String>(0);
        }
        Map<String, EventBuilder> poolEvents = new HashMap<String, EventBuilder>();
        for (Pool existing : existingPools) {
            EventBuilder eventBuilder = eventFactory
                    .getEventBuilder(Target.POOL, Type.MODIFIED)
                    .setOldEntity(existing);
            poolEvents.put(existing.getId(), eventBuilder);
        }

        // Hand off to rules to determine which pools need updating:
        List<PoolUpdate> updatedPools = poolRules.updatePools(sub, existingPools,
                changedProducts);

        // Update subpools if necessary
        if (updateStackDerived && !updatedPools.isEmpty() &&
                sub.createsSubPools() && sub.isStacked()) {
            // Get all pools for the subscriptions owner derived from the subscriptions
            // stack id, because we cannot look it up by subscriptionId
            List<Pool> subPools = getOwnerSubPoolsForStackId(sub.getOwner(), sub.getStackId());

            for (Pool subPool : subPools) {
                PoolUpdate update = updatePoolFromStack(subPool, changedProducts);

                if (update.changed()) {
                    updatedPools.add(update);
                }
            }
        }

        Set<String> entsToRegen = processPoolUpdates(subAdapter, poolEvents, updatedPools);
        return entsToRegen;
    }

    private Set<String> processPoolUpdates(SubscriptionServiceAdapter subAdapter,
        Map<String, EventBuilder> poolEvents, List<PoolUpdate> updatedPools) {
        Set<String> entitlementsToRegen = Util.newSet();
        for (PoolUpdate updatedPool : updatedPools) {

            Pool existingPool = updatedPool.getPool();
            log.info("Pool changed: " + updatedPool.toString());

            // Delete pools the rules signal needed to be cleaned up:
            if (existingPool.isMarkedForDelete()) {
                log.warn("Deleting pool as requested by rules: " +
                    existingPool.getId());
                deletePool(subAdapter, existingPool);
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
                this.deleteExcessEntitlements(subAdapter, existingPool);
            }

            // dates changed. regenerate all entitlement certificates
            if (updatedPool.getDatesChanged() ||
                updatedPool.getProductsChanged() ||
                updatedPool.getBrandingChanged()) {
                List<String> entitlements = poolCurator
                    .retrieveFreeEntitlementIdsOfPool(existingPool, true);
                entitlementsToRegen.addAll(entitlements);
            }
            Event event = poolEvents.get(existingPool.getId())
                    .setNewEntity(existingPool)
                    .buildEvent();
            sink.queueEvent(event);
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
    void updateFloatingPools(SubscriptionServiceAdapter subAdapter,
            List<Pool> floatingPools, boolean lazy, Set<Product> changedProducts) {
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
        List<PoolUpdate> updatedPools = poolRules.updatePools(floatingPools,
                changedProducts);
        regenerateCertificatesByEntIds(subAdapter, processPoolUpdates(subAdapter, poolEvents, updatedPools), lazy);
    }

    /**
     * @param sub
     * @return the newly created Pools
     */
    @Override
    public List<Pool> createPoolsForSubscription(Subscription sub) {
        return createPoolsForSubscription(sub, new LinkedList<Pool>());
    }

    public List<Pool> createPoolsForSubscription(Subscription sub, List<Pool> existingPools) {

        List<Pool> pools = poolRules.createPools(sub, existingPools);
        for (Pool pool : pools) {
            createPool(pool);
        }

        return pools;
    }

    @Override
    public Pool createPool(Pool p) {
        Pool created = poolCurator.create(p);
        if (log.isDebugEnabled()) {
            log.debug("   new pool: " + p);
        }
        if (created != null) {
            sink.emitPoolCreated(created);
        }

        return created;
    }

    @Override
    public Pool find(String poolId) {
        return this.poolCurator.find(poolId);
    }

    @Override
    public List<Pool> lookupBySubscriptionId(String id) {
        return this.poolCurator.lookupBySubscriptionId(id);
    }

    public List<Pool> lookupOversubscribedBySubscriptionId(String subId, Entitlement ent) {
        return this.poolCurator.lookupOversubscribedBySubscriptionId(subId, ent);
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
    // parameters
    // will most certainly be stale. beware!
    //
    @Override
    @Transactional
    public List<Entitlement> entitleByProducts(SubscriptionServiceAdapter subAdapter, AutobindData data)
        throws EntitlementRefusedException {
        Consumer consumer = data.getConsumer();
        String[] productIds = data.getProductIds();
        Collection<String> fromPools = data.getPossiblePools();
        Date entitleDate = data.getOnDate();
        Owner owner = consumer.getOwner();

        List<Entitlement> entitlements = new LinkedList<Entitlement>();

        List<PoolQuantity> bestPools = getBestPools(consumer, productIds,
            entitleDate, owner, null, fromPools);

        if (bestPools == null) {
            throw new RuntimeException("No entitlements for products: " +
                Arrays.toString(productIds));
        }

        // now make the entitlements
        for (PoolQuantity entry : bestPools) {
            entitlements.add(addOrUpdateEntitlement(subAdapter, consumer, entry.getPool(),
                null, entry.getQuantity(), false, CallerType.BIND));
        }

        return entitlements;
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
    // parameters
    // will most certainly be stale. beware!
    //
    @Override
    @Transactional
    public List<Entitlement> entitleByProductsForHost(SubscriptionServiceAdapter subAdapter, Consumer guest, Consumer host,
            Date entitleDate, Collection<String> possiblePools)
        throws EntitlementRefusedException {
        List<Entitlement> entitlements = new LinkedList<Entitlement>();
        if (!host.getOwner().equals(guest.getOwner())) {
            log.debug("Host " + host.getUuid() + " and guest " +
                guest.getUuid() + " have different owners.");
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
            throw new RuntimeException("No entitlements for host: " + host.getUuid());
        }

        // now make the entitlements
        for (PoolQuantity entry : bestPools) {
            entitlements.add(addOrUpdateEntitlement(subAdapter, host, entry.getPool(),
                null, entry.getQuantity(), false, CallerType.BIND));
        }

        return entitlements;
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

        ValidationResult failedResult = null;

        Date activePoolDate = entitleDate;
        if (entitleDate == null) {
            activePoolDate = new Date();
        }
        PoolFilterBuilder poolFilter = new PoolFilterBuilder();
        poolFilter.addIdFilters(fromPools);
        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(
            host, null, owner, (String) null, activePoolDate, true, false,
            poolFilter, null).getPageData();
        List<Pool> allOwnerPoolsForGuest = this.listAvailableEntitlementPools(
            guest, null, owner, (String) null, activePoolDate,
            true, false, poolFilter,
            null).getPageData();
        for (Entitlement ent : host.getEntitlements()) {
            //filter out pools that are attached, there is no need to
            //complete partial stacks, as they are already granting
            //virtual pools
            allOwnerPools.remove(ent.getPool());
        }
        List<Pool> filteredPools = new LinkedList<Pool>();

        ComplianceStatus guestCompliance = complianceRules.getStatus(guest, entitleDate, false);
        Set<String> tmpSet = new HashSet<String>();
        //we only want to heal red products, not yellow
        tmpSet.addAll(guestCompliance.getNonCompliantProducts());

        /*Do not attempt to create subscriptions for products that
          already have virt_only pools available to the guest */
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
        tmpSet.removeAll(productsToRemove);
        String[] productIds = tmpSet.toArray(new String [] {});

        if (log.isDebugEnabled()) {
            log.debug("Attempting for products on date: " + entitleDate);
            for (String productId : productIds) {
                log.debug("  " + productId);
            }
        }

        for (Pool pool : allOwnerPools) {
            boolean providesProduct = false;
            // Would parse the int here, but it can be 'unlimited'
            // and we only need to check that it's non-zero
            if (pool.getProduct().hasAttribute("virt_limit") &&
                    !pool.getProduct().getAttribute("virt_limit").getValue().equals("0")) {
                for (String productId : productIds) {
                    // If this is a derived pool, we need to see if the derived product
                    // provides anything for the guest, otherwise we use the parent.
                    if (pool.providesDerived(productId)) {
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
                    failedResult = result;
                    if (log.isDebugEnabled()) {
                        log.debug("Pool filtered from candidates due to rules " +
                            "failure: " +
                            pool.getId());
                    }
                }
                else {
                    filteredPools.add(pool);
                }
            }
        }

        // Only throw refused exception if we actually hit the rules:
        if (filteredPools.size() == 0 && failedResult != null) {
            throw new EntitlementRefusedException(failedResult);
        }
        ComplianceStatus hostCompliance = complianceRules.getStatus(host, entitleDate, false);

        List<PoolQuantity> enforced = autobindRules.selectBestPools(host,
            productIds, filteredPools, hostCompliance, serviceLevelOverride,
            poolCurator.retrieveServiceLevelsForOwner(owner, true), true);
        return enforced;
    }

    @Override
    public List<PoolQuantity> getBestPools(Consumer consumer,
        String[] productIds, Date entitleDate, Owner owner,
        String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException {

        boolean hasFromPools = fromPools != null && !fromPools.isEmpty();
        ValidationResult failedResult = null;

        Date activePoolDate = entitleDate;
        if (entitleDate == null) {
            activePoolDate = new Date();
        }

        PoolFilterBuilder poolFilter = new PoolFilterBuilder();
        poolFilter.addIdFilters(fromPools);
        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(
            consumer, null, owner, (String) null, activePoolDate, true, false,
            poolFilter, null).getPageData();
        List<Pool> filteredPools = new LinkedList<Pool>();

        // We have to check compliance status here so we can replace an empty
        // array of product IDs with the array the consumer actually needs. (i.e. during
        // a healing request)
        ComplianceStatus compliance = complianceRules.getStatus(consumer, entitleDate, false);
        if (productIds == null || productIds.length == 0) {
            log.debug("No products specified for bind, checking compliance to see what " +
                "is needed.");
            Set<String> tmpSet = new HashSet<String>();
            tmpSet.addAll(compliance.getNonCompliantProducts());
            tmpSet.addAll(compliance.getPartiallyCompliantProducts().keySet());
            productIds = tmpSet.toArray(new String [] {});
        }

        if (log.isDebugEnabled()) {
            log.debug("Attempting for products on date: " + entitleDate);
            for (String productId : productIds) {
                log.debug("  " + productId);
            }
        }

        for (Pool pool : allOwnerPools) {
            boolean providesProduct = false;
            // If We want to complete partial stacks if possible,
            // even if they do not provide any products
            if (pool.getProduct().hasAttribute("stacking_id") &&
                    compliance.getPartialStacks().containsKey(
                        pool.getProduct().getAttribute("stacking_id").getValue())) {
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
                    // Just keep the last one around, if we need it
                    failedResult = result;
                    if (log.isDebugEnabled()) {
                        log.debug("Pool filtered from candidates due to rules " +
                            "failure: " +
                            pool.getId());
                    }
                }
                else {
                    filteredPools.add(pool);
                }
            }
        }

        // Only throw refused exception if we actually hit the rules:
        if (filteredPools.size() == 0 && failedResult != null) {
            throw new EntitlementRefusedException(failedResult);
        }

        List<PoolQuantity> enforced = autobindRules.selectBestPools(consumer,
            productIds, filteredPools, compliance, serviceLevelOverride,
            poolCurator.retrieveServiceLevelsForOwner(owner, true), false);
        // Sort the resulting pools to avoid deadlocks
        Collections.sort(enforced);
        return enforced;
    }

    /**
     * Request an entitlement by pool.. If the entitlement cannot be granted,
     * null will be returned. TODO: Throw exception if entitlement not granted.
     * Report why.
     *
     * @param consumer consumer requesting to be entitled
     * @param pool entitlement pool to consume from
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    @Override
    @Transactional
    public Entitlement entitleByPool(SubscriptionServiceAdapter subAdapter, Consumer consumer, Pool pool,
        Integer quantity) throws EntitlementRefusedException {
        return addOrUpdateEntitlement(subAdapter, consumer, pool, null, quantity,
            false, CallerType.BIND);
    }

    @Override
    @Transactional
    public Entitlement ueberCertEntitlement(SubscriptionServiceAdapter subAdapter, Consumer consumer, Pool pool,
        Integer quantity) throws EntitlementRefusedException {
        return addOrUpdateEntitlement(subAdapter, consumer, pool, null, 1, true, CallerType.UNKNOWN);
    }

    @Override
    @Transactional
    public Entitlement adjustEntitlementQuantity(SubscriptionServiceAdapter subAdapter, Consumer consumer,
        Entitlement entitlement, Integer quantity)
        throws EntitlementRefusedException {
        int change = quantity - entitlement.getQuantity();
        if (change == 0) {
            return entitlement;
        }
        return addOrUpdateEntitlement(subAdapter, consumer, entitlement.getPool(), entitlement,
            change, true, CallerType.UNKNOWN);
    }

    private Entitlement addOrUpdateEntitlement(SubscriptionServiceAdapter subAdapter, Consumer consumer, Pool pool,
        Entitlement entitlement, Integer quantity, boolean generateUeberCert,
        CallerType caller)
        throws EntitlementRefusedException {
        // Because there are several paths to this one place where entitlements
        // are granted, we cannot be positive the caller obtained a lock on the
        // pool
        // when it was read. As such we're going to reload it with a lock
        // before starting this process.
        log.info("Locking pool: " + pool.getId());
        pool = poolCurator.lockAndLoad(pool);

        if (quantity > 0) {
            log.info("Running pre-entitlement rules.");
            // XXX preEntitlement is run twice for new entitlement creation
            ValidationResult result = enforcer.preEntitlement(
                consumer, pool, quantity, caller);

            if (!result.isSuccessful()) {
                log.warn("Entitlement not granted: " +
                    result.getErrors().toString());
                throw new EntitlementRefusedException(result);
            }
        }
        EntitlementHandler handler = null;
        if (entitlement == null) {
            handler = new NewHandler();
        }
        else {
            handler = new UpdateHandler();
        }

        log.info("Processing entitlement.");
        entitlement = handler.handleEntitlement(consumer, pool, entitlement, quantity);
        // Persist the entitlement after it has been created.  It requires an ID in order to
        // create an entitlement-derived subpool
        log.info("Persisting entitlement.");
        handler.handleEntitlementPersist(entitlement);

        // The quantity is calculated at fetch time. We update it here
        // To reflect what we just added to the db.
        pool.setConsumed(pool.getConsumed() + quantity);
        if (consumer.getType().isManifest()) {
            pool.setExported(pool.getExported() + quantity);
        }
        PoolHelper poolHelper = new PoolHelper(this, productCache, entitlement);
        handler.handlePostEntitlement(consumer, poolHelper, entitlement);

        // Check consumer's new compliance status and save:
        complianceRules.getStatus(consumer, null, false, false);
        consumerCurator.update(consumer);

        handler.handleSelfCertificate(subAdapter, consumer, pool, entitlement, generateUeberCert);
        for (Entitlement regenEnt : entitlementCurator.listModifying(entitlement)) {
            // Lazily regenerate modified certificates:
            this.regenerateCertificatesOf(subAdapter, regenEnt, generateUeberCert, true);
        }

        // we might have changed the bonus pool quantities, lets find out.
        handler.handleBonusPools(subAdapter, pool, entitlement);
        log.info("Granted entitlement: " + entitlement.getId() + " from pool: " +
            pool.getId());
        return entitlement;
    }

    /**
     * This method will pull the bonus pools from a physical and make sure that
     *  the bonus pools are not over-consumed.
     *
     * @param consumer
     * @param pool
     */
    private void checkBonusPoolQuantities(SubscriptionServiceAdapter subAdapter, Pool pool, Entitlement e) {
        for (Pool derivedPool :
                lookupOversubscribedBySubscriptionId(pool.getSubscriptionId(), e)) {
            if (!derivedPool.getId().equals(pool.getId()) &&
                derivedPool.getQuantity() != -1) {
                deleteExcessEntitlements(subAdapter, derivedPool);
            }
        }
    }

    /**
     * @param consumer
     * @param pool
     * @param e
     * @param mergedPool
     * @return
     */
    private EntitlementCertificate generateEntitlementCertificate(
        SubscriptionServiceAdapter subAdapter, Pool pool, Entitlement e, boolean generateUeberCert) {
        Subscription sub = null;
        if (pool.getSubscriptionId() != null) {
            log.info("Getting subscription: " + pool.getSubscriptionId());
            sub = subAdapter.getSubscription(pool.getSubscriptionId());
            log.info("Got subscription");
        }

        Product product = null;
        /*
         * If we have a subscription for this pool, the products we need are already
         * loaded as this saves us some product adapter lookups.
         *
         * If not we'll have to look them up based on the pool's data.
         */
        if (sub != null) {
            // Need to make sure that we check for a defined sub product
            // if it is a derived pool.
            boolean derived = pool.getType() != PoolType.NORMAL;
            product = derived && sub.getDerivedProduct() != null ? sub.getDerivedProduct() :
                sub.getProduct();
        }
        else {
            // Some pools may not have a subscription, i.e. derived from stack pools.
            product = productCache.getProductById(e.getProductId());
        }

        try {
            return generateUeberCert ?
                entCertAdapter.generateUeberCert(e, sub, product) :
                entCertAdapter.generateEntitlementCert(e, sub, product);
        }
        catch (CertVersionConflictException cvce) {
            throw cvce;
        }
        catch (CertificateSizeException cse) {
            throw cse;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void regenerateEntitlementCertificates(SubscriptionServiceAdapter subAdapter, Consumer consumer, boolean lazy) {
        log.info("Regenerating #" + consumer.getEntitlements().size() +
            " entitlement certificates for consumer: " + consumer);
        // TODO - Assumes only 1 entitlement certificate exists per entitlement
        this.regenerateCertificatesOf(subAdapter, consumer.getEntitlements(), lazy);
    }

    @Transactional
    void regenerateCertificatesOf(SubscriptionServiceAdapter subAdapter, Iterable<Entitlement> iterable, boolean lazy) {
        for (Entitlement e : iterable) {
            regenerateCertificatesOf(subAdapter, e, false, lazy);
        }
    }

    @Transactional
    void regenerateCertificatesByEntIds(SubscriptionServiceAdapter subAdapter, Iterable<String> iterable, boolean lazy) {
        for (String entId : iterable) {
            Entitlement e = entitlementCurator.find(entId);
            if (e != null) {
                regenerateCertificatesOf(subAdapter, e, false, lazy);
            }
            else {
                // If it has been deleted, that's fine, one less to regenerate
                log.info("Couldn't load Entitlement '" + entId + "' to regenerate, assuming deleted");
            }
        }
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
    public void regenerateCertificatesOf(SubscriptionServiceAdapter subAdapter, Environment e, Set<String> affectedContent,
        boolean lazy) {
        log.info("Regenerating relevant certificates in environment: " + e.getId());
        List<Entitlement> allEnvEnts = entitlementCurator.listByEnvironment(e);
        Set<Entitlement> entsToRegen = new HashSet<Entitlement>();
        for (Entitlement ent : allEnvEnts) {
            Product prod = productCache.getProductById(ent.getProductId());
            for (String contentId : affectedContent) {
                if (prod.hasContent(contentId)) {
                    entsToRegen.add(ent);
                }
            }

            // Now the provided products:
            for (Product provided : ent.getPool().getProvidedProducts()) {
                for (String contentId : affectedContent) {
                    if (provided.hasContent(contentId)) {
                        entsToRegen.add(ent);
                    }
                }
            }
        }
        log.info("Found " + entsToRegen.size() + " certificates to regenerate.");

        regenerateCertificatesOf(subAdapter, entsToRegen, lazy);
    }

    /**
     * @param e
     */
    @Override
    @Transactional
    public void regenerateCertificatesOf(SubscriptionServiceAdapter subAdapter, Entitlement e, boolean ueberCertificate,
        boolean lazy) {

        if (lazy) {
            log.info("Marking certificates dirty for entitlement: " + e);
            e.setDirty(true);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Revoking entitlementCertificates of : " + e);
        }

        Entitlement tempE = new Entitlement();
        tempE.getCertificates().addAll(e.getCertificates());
        e.getCertificates().clear();
        // below call creates new certificates and saves it to the backend.
        try {
            EntitlementCertificate generated = this.generateEntitlementCertificate(
                subAdapter, e.getPool(), e, ueberCertificate);
            e.setDirty(false);
            entitlementCurator.merge(e);
            for (EntitlementCertificate ec : tempE.getCertificates()) {
                if (log.isDebugEnabled()) {
                    log.debug("Deleting entitlementCertificate: #" + ec.getId());
                }
                this.entitlementCertificateCurator.delete(ec);
            }

            // send entitlement changed event.
            this.sink.queueEvent(this.eventFactory.entitlementChanged(e));
            if (log.isDebugEnabled()) {
                log.debug("Generated entitlementCertificate: #" + generated.getId());
            }
        }
        catch (CertificateSizeException cse) {
            e.getCertificates().addAll(tempE.getCertificates());
            log.warn("The certificate cannot be regenerated at this time: " +
                cse.getMessage());
        }
    }

    @Override
    @Transactional
    public void regenerateCertificatesOf(SubscriptionServiceAdapter subAdapter, String productId, boolean lazy) {
        List<Pool> poolsForProduct = this.listAvailableEntitlementPools(null, null, null,
            productId, new Date(), false, false, new PoolFilterBuilder(), null)
            .getPageData();
        for (Pool pool : poolsForProduct) {
            regenerateCertificatesOf(subAdapter, pool.getEntitlements(), lazy);
        }
    }

    /**
     * Remove the given entitlement and clean up.
     *
     * @param entitlement entitlement to remove
     * @param regenModified should we look for modified entitlements that are affected
     * and regenerated. False if we're mass deleting all the entitlements for a consumer
     * anyhow, true otherwise. Prevents a deadlock issue on mysql (at least).
     */
    @Transactional
    void removeEntitlement(SubscriptionServiceAdapter subAdapter, Entitlement entitlement,
        boolean regenModified) {

        Consumer consumer = entitlement.getConsumer();
        Pool pool = entitlement.getPool();

        // Similarly to when we add an entitlement, lock the pool when we remove one, too.
        // This won't do anything for over/under consumption, but it will prevent
        // concurrency issues if someone else is operating on the pool.
        pool = poolCurator.lockAndLoad(pool);

        consumer.removeEntitlement(entitlement);

        // Look for pools referencing this entitlement as their source
        // entitlement and clean them up as well

        // we need to create a list of pools and entitlements to delete,
        // otherwise we are tampering with the loop iterator from inside
        // the loop (#811581)
        Set<Pool> deletablePools = new HashSet<Pool>();

        for (Pool p : poolCurator.listBySourceEntitlement(entitlement)) {
            for (Entitlement e : p.getEntitlements()) {
                this.revokeEntitlement(subAdapter, e);
            }
            deletablePools.add(p);
        }
        for (Pool dp : deletablePools) {
            deletePool(subAdapter, dp);
        }

        pool.getEntitlements().remove(entitlement);
        poolCurator.merge(pool);
        entitlementCurator.delete(entitlement);
        Event event = eventFactory.entitlementDeleted(entitlement);

        // The quantity is calculated at fetch time. We update it here
        // To reflect what we just removed from the db.
        pool.setConsumed(pool.getConsumed() - entitlement.getQuantity());
        if (consumer.getType().isManifest()) {
            pool.setExported(pool.getExported() - entitlement.getQuantity());
        }

        // Check for a single stacked sub pool as well. We'll need to either
        // update or delete the sub pool now that all other pools have been deleted.
        if (!"true".equals(pool.getAttributeValue("pool_derived")) &&
            pool.getProduct().hasAttribute("stacking_id")) {
            String stackId = pool.getProduct().getAttributeValue("stacking_id");
            Pool stackedSubPool = poolCurator.getSubPoolForStackId(consumer, stackId);
            if (stackedSubPool != null) {
                List<Entitlement> stackedEnts =
                    this.entitlementCurator.findByStackId(consumer, stackId);

                // If there are no stacked entitlements, we need to delete the
                // stacked sub pool, else we update it based on the entitlements
                // currently in the stack.
                if (stackedEnts.isEmpty()) {
                    deletePool(subAdapter, stackedSubPool);
                }
                else {
                    updatePoolFromStackedEntitlements(stackedSubPool, stackedEnts);
                    poolCurator.merge(stackedSubPool);
                }
            }
        }

        // post unbind actions
        PoolHelper poolHelper = new PoolHelper(this, productCache, entitlement);
        enforcer.postUnbind(consumer, poolHelper, entitlement);

        if (regenModified) {
            // Find all of the entitlements that modified the original entitlement,
            // and regenerate those to remove the content sets.
            // Lazy regeneration is ok here.
            this.regenerateCertificatesOf(subAdapter, entitlementCurator
                .listModifying(entitlement), true);
        }

        log.info("Revoked entitlement: " + entitlement.getId());

        // If we don't care about updating other entitlements based on this one, we probably
        // don't care about updating compliance either.
        if (regenModified) {
            // Check consumer's new compliance status and save:
            complianceRules.getStatus(consumer);
        }

        sink.queueEvent(event);
    }

    @Override
    @Transactional
    public void revokeEntitlement(SubscriptionServiceAdapter subAdapter, Entitlement entitlement) {
        removeEntitlement(subAdapter, entitlement, true);
    }

    @Override
    @Transactional
    public int revokeAllEntitlements(SubscriptionServiceAdapter subAdapter, Consumer consumer) {
        int count = 0;
        for (Entitlement e : entitlementCurator.listByConsumer(consumer)) {
            removeEntitlement(subAdapter, e, false);
            count++;
        }
        // Rerun compliance after removing all entitlements
        complianceRules.getStatus(consumer);
        return count;
    }

    @Override
    @Transactional
    public int removeAllEntitlements(SubscriptionServiceAdapter subAdapter, Consumer consumer) {
        int count = 0;
        for (Entitlement e : entitlementCurator.listByConsumer(consumer)) {
            removeEntitlement(subAdapter, e, false);
            count++;
        }
        return count;
    }

    /**
     * Cleanup entitlements and safely delete the given pool.
     *
     * @param pool
     */
    @Override
    @Transactional
    public void deletePool(SubscriptionServiceAdapter subAdapter, Pool pool) {
        Event event = eventFactory.poolDeleted(pool);

        // Must do a full revoke for all entitlements:
        for (Entitlement e : poolCurator.entitlementsIn(pool)) {
            revokeEntitlement(subAdapter, e);
        }

        poolCurator.delete(pool);
        sink.queueEvent(event);
    }

    /**
     * Adjust the count of a pool. The caller does not have knowledge
     *   of the current quantity. It only determines how much to adjust.
     *
     * @param pool The pool.
     * @param adjust the long amount to adjust (+/-)
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
    public void regenerateDirtyEntitlements(SubscriptionServiceAdapter subAdapter, List<Entitlement> entitlements) {
        List<Entitlement> dirtyEntitlements = new ArrayList<Entitlement>();
        for (Entitlement e : entitlements) {
            if (e.getDirty()) {
                log.info("Found dirty entitlement to regenerate: " + e);
                dirtyEntitlements.add(e);
            }
        }

        regenerateCertificatesOf(subAdapter, dirtyEntitlements, false);
    }

    @Override
    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter) {
        return new Refresher(this, subAdapter, true);
    }

    @Override
    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter, boolean lazy) {
        return new Refresher(this, subAdapter, lazy);
    }

    /**
     * EntitlementHandler
     */
    private interface EntitlementHandler {
        Entitlement handleEntitlement(Consumer consumer, Pool pool,
            Entitlement entitlement, int quantity);
        void handlePostEntitlement(Consumer consumer, PoolHelper poolHelper,
            Entitlement entitlement);
        void handleEntitlementPersist(Entitlement entitlement);
        void handleSelfCertificate(SubscriptionServiceAdapter subAdapter, Consumer consumer, Pool pool,
            Entitlement entitlement, boolean generateUeberCert);
        void handleBonusPools(SubscriptionServiceAdapter subAdapter, Pool pool, Entitlement entitlement);
    }

    /**
     * NewHandler
     */
    private class NewHandler implements EntitlementHandler{
        @Override
        public Entitlement handleEntitlement(Consumer consumer, Pool pool,
            Entitlement entitlement, int quantity) {
            Entitlement newEntitlement = new Entitlement(pool, consumer,
                quantity);
            consumer.addEntitlement(newEntitlement);
            pool.getEntitlements().add(newEntitlement);
            return newEntitlement;
        }
        @Override
        public void handlePostEntitlement(Consumer consumer, PoolHelper poolHelper,
            Entitlement entitlement) {

            Pool entPool = entitlement.getPool();
            if (entPool.isStacked()) {
                Pool pool =
                    poolCurator.getSubPoolForStackId(
                        entitlement.getConsumer(), entPool.getStackId());
                if (pool != null) {
                    poolRules.updatePoolFromStack(pool, new HashSet<Product>());
                    poolCurator.merge(pool);
                }
            }

            enforcer.postEntitlement(consumer, poolHelper, entitlement);
        }
        @Override
        public void handleEntitlementPersist(Entitlement entitlement) {
            entitlementCurator.create(entitlement);
        }
        @Override
        public void handleSelfCertificate(SubscriptionServiceAdapter subAdapter, Consumer consumer, Pool pool,
            Entitlement entitlement, boolean generateUeberCert) {
            generateEntitlementCertificate(subAdapter, pool, entitlement, generateUeberCert);
        }
        @Override
        public void handleBonusPools(SubscriptionServiceAdapter subAdapter, Pool pool, Entitlement entitlement) {
            checkBonusPoolQuantities(subAdapter, pool, entitlement);
        }
    }

    /**
     * UpdateHandler
     */
    private class UpdateHandler implements EntitlementHandler{
        @Override
        public Entitlement handleEntitlement(Consumer consumer, Pool pool,
            Entitlement entitlement, int quantity) {
            entitlement.setQuantity(entitlement.getQuantity() + quantity);
            return entitlement;
        }
        @Override
        public void handlePostEntitlement(Consumer consumer, PoolHelper poolHelper,
            Entitlement entitlement) {
        }
        @Override
        public void handleEntitlementPersist(Entitlement entitlement) {
            entitlementCurator.merge(entitlement);
        }
        @Override
        public void handleSelfCertificate(SubscriptionServiceAdapter subAdapter, Consumer consumer, Pool pool,
            Entitlement entitlement, boolean generateUeberCert) {
            regenerateCertificatesOf(subAdapter, entitlement, generateUeberCert, true);
        }
        @Override
        public void handleBonusPools(SubscriptionServiceAdapter subAdapter, Pool pool, Entitlement entitlement) {
            updatePoolsForSubscription(subAdapter, poolCurator.listBySourceEntitlement(entitlement),
                subAdapter.getSubscription(pool.getSubscriptionId()), false,
                new HashSet<Product>());
            checkBonusPoolQuantities(subAdapter, pool, entitlement);
        }
    }

    @Override
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer,
        ActivationKey key, Owner owner, String productId, Date activeOn,
        boolean activeOnly, boolean includeWarnings, PoolFilterBuilder filters,
        PageRequest pageRequest) {
        // Only postfilter if we have to
        boolean postFilter = consumer != null || key != null;
        Page<List<Pool>> page = this.poolCurator.listAvailableEntitlementPools(consumer,
            owner, productId, activeOn, activeOnly, filters, pageRequest, postFilter);

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

    private PoolUpdate updatePoolFromStackedEntitlements(Pool pool,
        List<Entitlement> stackedEntitlements) {
        return poolRules.updatePoolFromStackedEntitlements(pool, stackedEntitlements,
                null);
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
                log.debug("Omitting pool due to failed rules: " + p.getId());
                if (result.hasErrors()) {
                    log.debug("\tErrors: " + result.getErrors());
                }
                if (result.hasWarnings()) {
                    log.debug("\tWarnings: " + result.getWarnings());
                }
            }
        }
        return filteredPools;
    }
}
