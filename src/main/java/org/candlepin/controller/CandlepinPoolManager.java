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

import org.apache.commons.lang.StringUtils;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
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
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.util.Util;
import org.candlepin.version.CertVersionConflictException;
import org.hibernate.HibernateException;
import org.hibernate.exception.ConstraintViolationException;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
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

    private PoolCurator poolCurator;
    private static Logger log = LoggerFactory.getLogger(CandlepinPoolManager.class);

    private SubscriptionServiceAdapter subAdapter;
    private EventSink sink;
    private EventFactory eventFactory;
    private Config config;
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
        EventFactory eventFactory, Config config, Enforcer enforcer,
        PoolRules poolRules, EntitlementCurator curator1, ConsumerCurator consumerCurator,
        EntitlementCertificateCurator ecC, ComplianceRules complianceRules,
        AutobindRules autobindRules, ActivationKeyRules activationKeyRules) {

        this.poolCurator = poolCurator;
        this.subAdapter = subAdapter;
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
    }

    Set<Entitlement> refreshPoolsWithoutRegeneration(Owner owner) {
        log.info("Refreshing pools for owner: " + owner.getKey());
        List<Subscription> subs = subAdapter.getSubscriptions(owner);
        log.debug("Found " + subs.size() + " existing subscriptions.");

        List<Pool> pools = this.listAvailableEntitlementPools(null, null,
            owner, null, null, false, false, new PoolFilterBuilder(), null).getPageData();

        // Pools with no subscription ID:
        List<Pool> floatingPools = new LinkedList<Pool>();

        // Map all pools for this owner/product that have a
        // subscription ID associated with them.
        Map<String, List<Pool>> subToPoolMap = new HashMap<String, List<Pool>>();
        for (Pool p : pools) {
            if (!StringUtils.isEmpty(p.getSubscriptionId())) {
                if (!subToPoolMap.containsKey(p.getSubscriptionId())) {
                    subToPoolMap.put(p.getSubscriptionId(),
                        new LinkedList<Pool>());
                }
                subToPoolMap.get(p.getSubscriptionId()).add(p);
            }
            else {
                floatingPools.add(p);
            }
        }

        Set<Entitlement> entitlementsToRegen = Util.newSet();
        for (Subscription sub : subs) {
            // Delete any expired subscriptions. Leave it in the map
            // so that the pools will get deleted as well.
            if (isExpired(sub)) {
                log.info("Deleting expired subscription: " + sub);
                subAdapter.deleteSubscription(sub);
                continue;
            }

            try {
                if (!poolExistsForSubscription(subToPoolMap, sub.getId())) {
                    createPoolsForSubscription(sub);
                }
                else {
                    entitlementsToRegen.addAll(
                        updatePoolsForSubscription(subToPoolMap.get(sub.getId()), sub)
                    );
                }
            }
            catch (ConstraintViolationException e) {
                // This shouldn't cause our entire job to fail. Probably a concurrent
                // refresh job
                log.warn("Failed to create or update pool for" + sub + " on " + owner +
                    " Probably the result of concurrent refreshes, and the pool has " +
                    "already been created or modified", e);
            }
            finally {
                subToPoolMap.remove(sub.getId());
            }
        }

        entitlementsToRegen.addAll(updateFloatingPools(floatingPools));

        // delete pools whose subscription disappeared:
        for (Entry<String, List<Pool>> entry : subToPoolMap.entrySet()) {
            for (Pool p : entry.getValue()) {
                // WARNING: need to be very careful here. We do not want to delete
                // derived pools with a source entitlement or stack, these will be cleaned
                // up when the entitlements in the master pool are revoked.
                //
                // However if this is an older bonus pool (hosted) not tied to any
                // entitlements, we need to proceed:
                if (p.getType() == PoolType.NORMAL || p.getType() == PoolType.BONUS) {
                    try {
                        deletePool(p);
                    }
                    catch (HibernateException e) {
                        // Is there an exception for objs that have already been deleted?
                        log.error("Failed to delete " + p + " It has probably already " +
                            "been deleted by another refresh", e);
                    }
                }
            }
        }

        return entitlementsToRegen;
    }

    public void cleanupExpiredPools() {
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

            Subscription sub = subAdapter.getSubscription(p.getSubscriptionId());
            // In case it was already deleted:
            if (sub != null) {
                subAdapter.deleteSubscription(sub);
            }

            deletePool(p);
        }
    }

    private boolean isExpired(Subscription subscription) {
        Date now = new Date();
        return now.after(subscription.getEndDate());
    }

    @Transactional
    private void deleteExcessEntitlements(Pool existingPool) {
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
                revokeEntitlement(e);
                consumed -= e.getQuantity();
            }
        }
    }

    /**
     * Update pool for subscription. - This method only checks for change in
     * quantity and dates of a subscription. Currently any quantity changes in
     * pool are not handled.
     *
     * @param existingPools the existing pools
     * @param sub the sub
     */
    Set<Entitlement> updatePoolsForSubscription(List<Pool> existingPools,
        Subscription sub) {

        /*
         * Rules need to determine which pools have changed, but the Java must
         * send out the events. Create an event for each pool that could change,
         * even if we won't use them all.
         */
        Map<String, Event> poolEvents = new HashMap<String, Event>();
        for (Pool existing : existingPools) {
            Event e = eventFactory.poolChangedFrom(existing);
            poolEvents.put(existing.getId(), e);
        }

        // Hand off to rules to determine which pools need updating:
        List<PoolUpdate> updatedPools = poolRules.updatePools(sub,
            existingPools);

        return processPoolUpdates(poolEvents, updatedPools);
    }

    private Set<Entitlement> processPoolUpdates(
        Map<String, Event> poolEvents, List<PoolUpdate> updatedPools) {
        Set<Entitlement> entitlementsToRegen = Util.newSet();
        for (PoolUpdate updatedPool : updatedPools) {

            Pool existingPool = updatedPool.getPool();
            log.info("Pool changed: " + updatedPool.toString());

            // Delete pools the rules signal needed to be cleaned up:
            if (existingPool.hasAttribute(PoolManager.DELETE_FLAG) &&
                existingPool.getAttributeValue(PoolManager.DELETE_FLAG).equals("true")) {
                log.warn("Deleting pool as requested by rules: " +
                    existingPool.getId());
                deletePool(existingPool);
                continue;
            }

            // quantity has changed. delete any excess entitlements from pool
            if (updatedPool.getQuantityChanged()) {
                this.deleteExcessEntitlements(existingPool);
            }

            // dates changed. regenerate all entitlement certificates
            if (updatedPool.getDatesChanged() ||
                updatedPool.getProductsChanged()) {
                List<Entitlement> entitlements = poolCurator
                    .retrieveFreeEntitlementsOfPool(existingPool, true);
                entitlementsToRegen.addAll(entitlements);
            }
            // save changes for the pool
            this.poolCurator.merge(existingPool);

            eventFactory.poolChangedTo(poolEvents.get(existingPool.getId()),
                existingPool);
            sink.sendEvent(poolEvents.get(existingPool.getId()));
        }

        return entitlementsToRegen;
    }

    /**
     * Update pools which have no subscription attached, if applicable.
     *
     * @param floatingPools
     * @return
     */
    Set<Entitlement> updateFloatingPools(List<Pool> floatingPools) {
        /*
         * Rules need to determine which pools have changed, but the Java must
         * send out the events. Create an event for each pool that could change,
         * even if we won't use them all.
         */
        Map<String, Event> poolEvents = new HashMap<String, Event>();
        for (Pool existing : floatingPools) {
            Event e = eventFactory.poolChangedFrom(existing);
            poolEvents.put(existing.getId(), e);
        }

        // Hand off to rules to determine which pools need updating:
        List<PoolUpdate> updatedPools = poolRules.updatePools(floatingPools);
        return processPoolUpdates(poolEvents, updatedPools);
    }

    private boolean poolExistsForSubscription(
        Map<String, List<Pool>> subToPoolMap, String id) {
        return subToPoolMap.containsKey(id);
    }

    /**
     * @param sub
     * @return the newly created Pool
     */
    @Override
    public List<Pool> createPoolsForSubscription(Subscription sub) {
        if (log.isDebugEnabled()) {
            log.debug("Creating new pool for new sub: " + sub.getId());
        }

        List<Pool> pools = poolRules.createPools(sub);
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
     * @param consumer consumer requesting to be entitled
     * @param productIds products to be entitled.
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
    public List<Entitlement> entitleByProducts(Consumer consumer,
        String[] productIds, Date entitleDate)
        throws EntitlementRefusedException {
        Owner owner = consumer.getOwner();
        List<Entitlement> entitlements = new LinkedList<Entitlement>();

        // Use the current date if one wasn't provided:
        if (entitleDate == null) {
            entitleDate = new Date();
        }

        List<PoolQuantity> bestPools = getBestPools(consumer, productIds,
            entitleDate, owner, null);

        if (bestPools == null) {
            throw new RuntimeException("No entitlements for products: " +
                Arrays.toString(productIds));
        }

        // now make the entitlements
        for (PoolQuantity entry : bestPools) {
            entitlements.add(addOrUpdateEntitlement(consumer, entry.getPool(),
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
    public List<Entitlement> entitleByProductsForHost(Consumer guest,
        Consumer host, Date entitleDate)
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
            entitleDate, owner, null);

        if (bestPools == null) {
            throw new RuntimeException("No entitlements for host: " + host.getUuid());
        }

        // now make the entitlements
        for (PoolQuantity entry : bestPools) {
            entitlements.add(addOrUpdateEntitlement(host, entry.getPool(),
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
        String serviceLevelOverride)
        throws EntitlementRefusedException {

        ValidationResult failedResult = null;

        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(
            host, null, owner, (String) null, entitleDate, true, false,
            new PoolFilterBuilder(), null).getPageData();
        List<Pool> allOwnerPoolsForGuest = this.listAvailableEntitlementPools(
            guest, null, owner, (String) null, entitleDate,
            true, false, new PoolFilterBuilder(),
            null).getPageData();
        for (Entitlement ent : host.getEntitlements()) {
            //filter out pools that are attached, there is no need to
            //complete partial stacks, as they are already granting
            //virtual pools
            allOwnerPools.remove(ent.getPool());
        }
        List<Pool> filteredPools = new LinkedList<Pool>();

        ComplianceStatus guestCompliance = complianceRules.getStatus(guest, entitleDate);
        Set<String> tmpSet = new HashSet<String>();
        //we only want to heal red products, not yellow
        tmpSet.addAll(guestCompliance.getNonCompliantProducts());

        /*Do not attempt to create subscriptions for products that
          already have virt_only pools available to the guest */
        Set<String> productsToRemove = new HashSet<String>();
        for (Pool pool : allOwnerPoolsForGuest) {
            if (pool.hasProductAttribute("virt_only") || pool.hasAttribute("virt_only")) {
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
            if (pool.hasProductAttribute("virt_limit") &&
                    !pool.getProductAttribute("virt_limit").getValue().equals("0")) {
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
        ComplianceStatus hostCompliance = complianceRules.getStatus(host, entitleDate);

        List<PoolQuantity> enforced = autobindRules.selectBestPools(host,
            productIds, filteredPools, hostCompliance, serviceLevelOverride,
            poolCurator.retrieveServiceLevelsForOwner(owner, true), true);
        return enforced;
    }

    @Override
    public List<PoolQuantity> getBestPools(Consumer consumer,
        String[] productIds, Date entitleDate, Owner owner,
        String serviceLevelOverride)
        throws EntitlementRefusedException {


        ValidationResult failedResult = null;

        List<Pool> allOwnerPools = this.listAvailableEntitlementPools(
            consumer, null, owner, (String) null, entitleDate, true, false,
            new PoolFilterBuilder(), null).getPageData();
        List<Pool> filteredPools = new LinkedList<Pool>();

        // We have to check compliance status here so we can replace an empty
        // array of product IDs with the array the consumer actually needs. (i.e. during
        // a healing request)
        ComplianceStatus compliance = complianceRules.getStatus(consumer, entitleDate);
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
            if (pool.hasProductAttribute("stacking_id") &&
                    compliance.getPartialStacks().containsKey(
                        pool.getProductAttribute("stacking_id").getValue())) {
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
    public Entitlement entitleByPool(Consumer consumer, Pool pool,
        Integer quantity) throws EntitlementRefusedException {
        return addOrUpdateEntitlement(consumer, pool, null, quantity,
            false, CallerType.BIND);
    }

    @Override
    @Transactional
    public Entitlement ueberCertEntitlement(Consumer consumer, Pool pool,
        Integer quantity) throws EntitlementRefusedException {
        return addOrUpdateEntitlement(consumer, pool, null, 1, true, CallerType.UNKNOWN);
    }

    @Override
    @Transactional
    public Entitlement adjustEntitlementQuantity(Consumer consumer,
        Entitlement entitlement, Integer quantity)
        throws EntitlementRefusedException {
        int change = quantity - entitlement.getQuantity();
        if (change == 0) {
            return entitlement;
        }
        return addOrUpdateEntitlement(consumer, entitlement.getPool(), entitlement,
            change, true, CallerType.UNKNOWN);
    }

    private Entitlement addOrUpdateEntitlement(Consumer consumer, Pool pool,
        Entitlement entitlement, Integer quantity, boolean generateUeberCert,
        CallerType caller)
        throws EntitlementRefusedException {
        // Because there are several paths to this one place where entitlements
        // are granted, we cannot be positive the caller obtained a lock on the
        // pool
        // when it was read. As such we're going to reload it with a lock
        // before starting this process.
        pool = poolCurator.lockAndLoad(pool);

        if (quantity > 0) {
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

        entitlement = handler.handleEntitlement(consumer, pool, entitlement, quantity);

        // The quantity is calculated at fetch time. We update it here
        // To reflect what we just added to the db.
        pool.setConsumed(pool.getConsumed() + quantity);
        if (consumer.getType().isManifest()) {
            pool.setExported(pool.getExported() + quantity);
        }
        PoolHelper poolHelper = new PoolHelper(this, productCache, entitlement);
        handler.handlePostEntitlement(consumer, poolHelper, entitlement);

        // Check consumer's new compliance status and save:
        ComplianceStatus compliance = complianceRules.getStatus(consumer, new Date());
        consumer.setEntitlementStatus(compliance.getStatus());

        handler.handleEntitlementPersist(entitlement);
        consumerCurator.update(consumer);

        handler.handleSelfCertificate(consumer, pool, entitlement, generateUeberCert);
        for (Entitlement regenEnt : entitlementCurator.listModifying(entitlement)) {
            // Lazily regenerate modified certificates:
            this.regenerateCertificatesOf(regenEnt, generateUeberCert, true);
        }

        // we might have changed the bonus pool quantities, lets find out.
        handler.handleBonusPools(pool, entitlement);
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
    private void checkBonusPoolQuantities(Pool pool, Entitlement e) {
        for (Pool derivedPool :
                lookupOversubscribedBySubscriptionId(pool.getSubscriptionId(), e)) {
            if (!derivedPool.getId().equals(pool.getId()) &&
                derivedPool.getQuantity() != -1) {
                deleteExcessEntitlements(derivedPool);
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
        Pool pool, Entitlement e, boolean generateUeberCert) {
        Subscription sub = null;
        if (pool.getSubscriptionId() != null) {
            sub = subAdapter.getSubscription(pool.getSubscriptionId());
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
    public void regenerateEntitlementCertificates(Consumer consumer, boolean lazy) {
        log.info("Regenerating #" + consumer.getEntitlements().size() +
            " entitlement certificates for consumer: " + consumer);
        // TODO - Assumes only 1 entitlement certificate exists per entitlement
        this.regenerateCertificatesOf(consumer.getEntitlements(), lazy);
    }

    @Transactional
    void regenerateCertificatesOf(Iterable<Entitlement> iterable, boolean lazy) {
        for (Entitlement e : iterable) {
            regenerateCertificatesOf(e, false, lazy);
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
    public void regenerateCertificatesOf(Environment e, Set<String> affectedContent,
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
            for (ProvidedProduct provided : ent.getPool().getProvidedProducts()) {
                Product providedProd = productCache.getProductById(
                    provided.getProductId());
                for (String contentId : affectedContent) {
                    if (providedProd.hasContent(contentId)) {
                        entsToRegen.add(ent);
                    }
                }
            }
        }
        log.info("Found " + entsToRegen.size() + " certificates to regenerate.");

        regenerateCertificatesOf(entsToRegen, lazy);
    }

    /**
     * @param e
     */
    @Override
    @Transactional
    public void regenerateCertificatesOf(Entitlement e, boolean ueberCertificate,
        boolean lazy) {

        if (lazy) {
            if (log.isDebugEnabled()) {
                log.debug("Marking certificates dirty for entitlement: " + e);
            }
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
                e.getPool(), e, ueberCertificate);
            e.setDirty(false);
            entitlementCurator.merge(e);
            this.entCertAdapter.revokeEntitlementCertificates(tempE);
            for (EntitlementCertificate ec : tempE.getCertificates()) {
                if (log.isDebugEnabled()) {
                    log.debug("Deleting entitlementCertificate: #" + ec.getId());
                }
                this.entitlementCertificateCurator.delete(ec);
            }

            // send entitlement changed event.
            this.sink.sendEvent(this.eventFactory.entitlementChanged(e));
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
    public void regenerateCertificatesOf(String productId, boolean lazy) {
        List<Pool> poolsForProduct = this.listAvailableEntitlementPools(null, null, null,
            productId, new Date(), false, false, new PoolFilterBuilder(), null)
            .getPageData();
        for (Pool pool : poolsForProduct) {
            regenerateCertificatesOf(pool.getEntitlements(), lazy);
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
    void removeEntitlement(Entitlement entitlement,
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
                this.revokeEntitlement(e);
            }
            deletablePools.add(p);
        }
        for (Pool dp : deletablePools) {
            deletePool(dp);
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
            pool.hasProductAttribute("stacking_id")) {
            String stackId = pool.getProductAttributeValue("stacking_id");
            Pool stackedSubPool = poolCurator.getSubPoolForStackId(consumer, stackId);
            if (stackedSubPool != null) {
                List<Entitlement> stackedEnts =
                    this.entitlementCurator.findByStackId(consumer, stackId);

                // If there are no stacked entitlements, we need to delete the
                // stacked sub pool, else we update it based on the entitlements
                // currently in the stack.
                if (stackedEnts.isEmpty()) {
                    deletePool(stackedSubPool);
                }
                else {
                    updatePoolFromStackedEntitlements(
                        stackedSubPool, consumer, stackId, stackedEnts);
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
            this.regenerateCertificatesOf(entitlementCurator
                .listModifying(entitlement), true);
        }

        log.info("Revoked entitlement: " + entitlement.getId());

        // If we don't care about updating other entitlements based on this one, we probably
        // don't care about updating compliance either.
        if (regenModified) {
            // Check consumer's new compliance status and save:
            ComplianceStatus compliance = complianceRules.getStatus(consumer, new Date());
            consumer.setEntitlementStatus(compliance.getStatus());
            consumerCurator.update(consumer);
        }

        sink.sendEvent(event);
    }

    @Override
    @Transactional
    public void revokeEntitlement(Entitlement entitlement) {
        entCertAdapter.revokeEntitlementCertificates(entitlement);
        removeEntitlement(entitlement, true);
    }

    @Override
    @Transactional
    public int revokeAllEntitlements(Consumer consumer) {
        int count = 0;
        for (Entitlement e : entitlementCurator.listByConsumer(consumer)) {
            entCertAdapter.revokeEntitlementCertificates(e);
            removeEntitlement(e, false);
            count++;
        }
        // Rerun compliance after removing all entitlements
        ComplianceStatus compliance = complianceRules.getStatus(consumer, new Date());
        consumer.setEntitlementStatus(compliance.getStatus());
        consumerCurator.update(consumer);
        return count;
    }

    @Override
    @Transactional
    public int removeAllEntitlements(Consumer consumer) {
        int count = 0;
        for (Entitlement e : entitlementCurator.listByConsumer(consumer)) {
            removeEntitlement(e, false);
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
    public void deletePool(Pool pool) {
        Event event = eventFactory.poolDeleted(pool);

        // remove the link between the key and the pool before deleting the pool
        // TODO: is there a better way to do this with hibernate annotations?
        List<ActivationKey> keys = poolCurator.getActivationKeysForPool(pool);
        for (ActivationKey k : keys) {
            k.removePool(pool);
        }

        // Must do a full revoke for all entitlements:
        for (Entitlement e : poolCurator.entitlementsIn(pool)) {
            revokeEntitlement(e);
        }

        poolCurator.delete(pool);
        sink.sendEvent(event);
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
    public void regenerateDirtyEntitlements(List<Entitlement> entitlements) {
        List<Entitlement> dirtyEntitlements = new ArrayList<Entitlement>();
        for (Entitlement e : entitlements) {
            if (e.getDirty()) {
                log.info("Found dirty entitlement to regenerate: " + e);
                dirtyEntitlements.add(e);
            }
        }

        regenerateCertificatesOf(dirtyEntitlements, false);
    }

    @Override
    public Refresher getRefresher() {
        return getRefresher(true);
    }

    @Override
    public Refresher getRefresher(boolean lazy) {
        return new Refresher(this, this.subAdapter, lazy);
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
        void handleSelfCertificate(Consumer consumer, Pool pool,
            Entitlement entitlement, boolean generateUeberCert);
        void handleBonusPools(Pool pool, Entitlement entitlement);
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
            String stackId = entPool.getProductAttributeValue("stacking_id");
            if (stackId != null && !stackId.isEmpty()) {
                Pool pool =
                    poolCurator.getSubPoolForStackId(entitlement.getConsumer(), stackId);
                if (pool != null) {
                    poolRules.updatePoolFromStack(pool, consumer, stackId);
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
        public void handleSelfCertificate(Consumer consumer, Pool pool,
            Entitlement entitlement, boolean generateUeberCert) {
            generateEntitlementCertificate(pool, entitlement, generateUeberCert);
        }
        @Override
        public void handleBonusPools(Pool pool, Entitlement entitlement) {
            checkBonusPoolQuantities(pool, entitlement);
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
        public void handleSelfCertificate(Consumer consumer, Pool pool,
            Entitlement entitlement, boolean generateUeberCert) {
            regenerateCertificatesOf(entitlement, generateUeberCert, true);
        }
        @Override
        public void handleBonusPools(Pool pool, Entitlement entitlement) {
            updatePoolsForSubscription(poolCurator.listBySourceEntitlement(entitlement),
                subAdapter.getSubscription(pool.getSubscriptionId()));
            checkBonusPoolQuantities(pool, entitlement);
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
        List<Pool> preFilterResults = page.getPageData();
        List<Pool> newResults = new LinkedList<Pool>();
        for (Pool p : preFilterResults) {
            ValidationResult result = new ValidationResult();
            if (consumer != null) {
                result.add(enforcer.preEntitlement(
                    consumer, p, 1, CallerType.LIST_POOLS));
            }
            if (key != null) {
                result.add(activationKeyRules.runPreActKey(key, p, null));
            }

            if (result.isSuccessful() && (!result.hasWarnings() || includeWarnings)) {
                newResults.add(p);
            }
            else {
                if (log.isDebugEnabled()) {
                    log.debug("Omitting pool due to failed rules: " + p.getId());
                    if (result.hasErrors()) {
                        log.debug("\tErrors: " + result.getErrors());
                    }
                    if (result.hasWarnings()) {
                        log.debug("\tWarnings: " + result.getWarnings());
                    }
                }
            }
        }

        // Set maxRecords once we are done filtering
        page.setMaxRecords(newResults.size());

        if (pageRequest != null && pageRequest.isPaging()) {
            newResults = poolCurator.takeSubList(pageRequest, newResults);
        }

        page.setPageData(newResults);
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

    public PoolUpdate updatePoolFromStack(Pool pool, Consumer consumer, String stackId) {
        return poolRules.updatePoolFromStack(pool, consumer, stackId);
    }

    private PoolUpdate updatePoolFromStackedEntitlements(Pool pool,
        Consumer consumer, String stackId,
        List<Entitlement> stackedEntitlements) {
        return poolRules.updatePoolFromStackedEntitlements(pool, consumer,
            stackId, stackedEntitlements);
    }
}
