/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.entitlement.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * PoolManager
 */
public class PoolManager {

    private PoolCurator poolCurator;
    private Log log = LogFactory.getLog(PoolManager.class);
    private SubscriptionServiceAdapter subAdapter;
    private ProductServiceAdapter productAdapter;
    private EventSink sink;
    private EventFactory eventFactory;
    private Config config;
    private Enforcer enforcer;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private EntitlementCertServiceAdapter entCertAdapter;
    private EntitlementCertificateCurator entitlementCertificateCurator;
    
    /**
     * @param poolCurator
     * @param subAdapter
     * @param sink
     * @param eventFactory
     * @param config
     */
    @Inject
    public PoolManager(PoolCurator poolCurator,
        SubscriptionServiceAdapter subAdapter, 
        ProductServiceAdapter productAdapter, EntitlementCertServiceAdapter entCertAdapter,
        EventSink sink, EventFactory eventFactory, Config config,
        Enforcer enforcer,
        EntitlementCurator curator1, ConsumerCurator consumerCurator,
        EntitlementCertificateCurator ecC) {

        this.poolCurator = poolCurator;
        this.subAdapter = subAdapter;
        this.productAdapter = productAdapter;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.config = config;
        this.entitlementCurator = curator1;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
        this.entCertAdapter = entCertAdapter;
        this.entitlementCertificateCurator = ecC;
    }


    /**
     * Check our underlying subscription service and update the pool data. Note
     * that refreshing the pools doesn't actually take any action, should a subscription
     * be reduced, expired, or revoked. Pre-existing entitlements will need to be dealt
     * with separately from this event.
     *
     * @param owner Owner to be refreshed.
     */
    public void refreshPools(Owner owner) {
        if (log.isDebugEnabled()) {
            log.debug("Refreshing pools");
        }
        
        List<Subscription> subs = subAdapter.getSubscriptions(owner);

        if (log.isDebugEnabled()) {
            log.debug("Found subscriptions: ");
            for (Subscription sub : subs) {
                log.debug("   " + sub);
            }
        }

        List<Pool> pools = this.poolCurator.listAvailableEntitlementPools(null,
            owner, (String) null, false);

        if (log.isDebugEnabled()) {
            log.debug("Found pools: ");
            for (Pool p : pools) {
                log.debug("   " + p);
            }
        }

        // Map all  pools for this owner/product that have a
        // subscription ID associated with them.
        Map<Long, Pool> subToPoolMap = new HashMap<Long, Pool>();
        for (Pool p : pools) {
            if (p.getSubscriptionId() != null) {
                subToPoolMap.put(p.getSubscriptionId(), p);
            }
        }
        for (Subscription sub : subs) {
            if (!poolExistsForSubscription(subToPoolMap, sub.getId())) {
                this.createPoolForSubscription(sub);
                subToPoolMap.remove(sub.getId());
            }
            else {
                Pool existingPool = subToPoolMap.get(sub.getId());
                updatePoolForSubscription(existingPool, sub);
                subToPoolMap.remove(sub.getId());
            }
        }

        // delete pools whose subscription disappeared:
        for (Entry<Long, Pool> entry : subToPoolMap.entrySet()) {
            deletePool(entry.getValue());
        }
    }

    private void deleteExcessEntitlements(Pool existingPool) {
        boolean lifo = !config
        .getBoolean(ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER);

        if (existingPool.isOverflowing()) {
            // if pool quantity has reduced, then start with revocation.
            Iterator<Entitlement> iter = this.poolCurator
                .retrieveFreeEntitlementsOfPool(existingPool, lifo).iterator();

            long consumed = existingPool.getConsumed();
            while ((consumed > existingPool.getQuantity()) && iter.hasNext()) {
                Entitlement e = iter.next();
                revokeEntitlement(e);
                consumed -= e.getQuantity();
            }
        }
    }

    /**
     * Update pool for subscription. - This method only checks for change in
     * quantity and dates of a subscription. Currently any quantity changes
     * in pool are not handled.
     * @param existingPool the existing pool
     * @param sub the sub
     */
    public void updatePoolForSubscription(Pool existingPool,
        Subscription sub) {
        boolean datesChanged = (!sub.getStartDate().equals(
            existingPool.getStartDate())) ||
            (!sub.getEndDate().equals(existingPool.getEndDate()));
        boolean quantityChanged = !sub.getQuantity().equals(existingPool.getQuantity());

        if (!(quantityChanged || datesChanged)) {
            //TODO: Should we check whether pool is overflowing here?
            return; //no changes, just return.
        }

        Event e = eventFactory.poolChangedFrom(existingPool);
        //quantity has changed. delete any excess entitlements from pool
        if (quantityChanged) {
            existingPool.setQuantity(sub.getQuantity());
            this.deleteExcessEntitlements(existingPool);
        }

        //dates changed. regenerate all entitlement certificates
        if (datesChanged) {
            existingPool.setStartDate(sub.getStartDate());
            existingPool.setEndDate(sub.getEndDate());
            List<Entitlement> entitlements = poolCurator
            .retrieveFreeEntitlementsOfPool(existingPool, true);
            //when subscription dates change, entitlement dates should change as well
            for (Entitlement entitlement : entitlements) {
                entitlement.setStartDate(sub.getStartDate());
                entitlement.setEndDate(sub.getEndDate());
              //TODO: perhaps optimize it to use hibernate query?
                this.entitlementCurator.merge(entitlement);
            }
            regenerateCertificatesOf(entitlements);
        }
        //save changes for the pool
        this.poolCurator.merge(existingPool);
        eventFactory.poolChangedTo(e, existingPool);
        sink.sendEvent(e);
    }


    private boolean poolExistsForSubscription(Map<Long, Pool> subToPoolMap,
            Long id) {
        return subToPoolMap.containsKey(id);
    }


    /**
     * @param sub
     */
    public void createPoolForSubscription(Subscription sub) {
        if (log.isDebugEnabled()) {
            log.debug("Creating new pool for new sub: " + sub.getId());
        }
        Long quantity = sub.getQuantity() * sub.getProduct().getMultiplier();
        Set<String> productIds = new HashSet<String>();
        for (Product p : sub.getProvidedProducts()) {
            productIds.add(p.getId());
        }
        Pool newPool = new Pool(sub.getOwner(), sub.getProduct().getId(), productIds,
                quantity, sub.getStartDate(), sub.getEndDate());
        newPool.setSubscriptionId(sub.getId());
        createPool(newPool);
    }

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


    public Pool find(Long poolId) {
        return this.poolCurator.find(poolId);
    }

    public Pool lookupBySubscriptionId(Long id) {
        return this.poolCurator.lookupBySubscriptionId(id);
    }

    public PoolCurator getPoolCurator() {
        return this.poolCurator;
    }

    /**
     * Request an entitlement by product.
     *
     * If the entitlement cannot be granted, null will be returned.
     *
     * TODO: Throw exception if entitlement not granted. Report why.
     *
     * @param consumer
     *            consumer requesting to be entitled
     * @param productId
     *            product to be entitled.
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    //
    // NOTE: after calling this method both entitlement pool and consumer
    // parameters
    // will most certainly be stale. beware!
    //
    @Transactional
    public Entitlement entitleByProduct(Consumer consumer, String productId,
        Integer quantity)
        throws EntitlementRefusedException {
        Owner owner = consumer.getOwner();

        Pool pool = enforcer.selectBestPool(consumer, productId,
            poolCurator.listByOwnerAndProduct(owner, productId));
        if (pool == null) {
            throw new RuntimeException("No entitlements for product: " +
                productId);
        }

        return addEntitlement(consumer, pool, quantity);
    }

    /**
     * Request an entitlement by pool..
     *
     * If the entitlement cannot be granted, null will be returned.
     *
     * TODO: Throw exception if entitlement not granted. Report why.
     *
     * @param consumer
     *            consumer requesting to be entitled
     * @param pool
     *            entitlement pool to consume from
     * @return Entitlement
     *
     * @throws EntitlementRefusedException if entitlement is refused
     */
    @Transactional
    public Entitlement entitleByPool(Consumer consumer, Pool pool, Integer quantity)
        throws EntitlementRefusedException {

        return addEntitlement(consumer, pool, quantity);
    }

    private Entitlement addEntitlement(Consumer consumer, Pool pool, Integer quantity)
        throws EntitlementRefusedException {
        PreEntHelper preHelper = enforcer.preEntitlement(consumer, pool, quantity);
        ValidationResult result = preHelper.getResult();

        if (!result.isSuccessful()) {
            log.warn("Entitlement not granted: " +
                result.getErrors().toString());
            throw new EntitlementRefusedException(result);
        }

        Entitlement e
            = new Entitlement(pool, consumer, new Date(), pool.getEndDate(), quantity);
        consumer.addEntitlement(e);
        pool.getEntitlements().add(e);

        if (preHelper.getGrantFreeEntitlement()) {
            log.info("Granting free entitlement.");
            e.setIsFree(Boolean.TRUE);
        }
        else {
            pool.bumpConsumed(quantity);
        }

        PostEntHelper postEntHelper = new PostEntHelper(this, e);
        enforcer.postEntitlement(consumer, postEntHelper, e);

        entitlementCurator.create(e);
        consumerCurator.update(consumer);

        generateEntitlementCertificate(consumer, pool, e);
        return e;
    }

    /**
     * @param consumer
     * @param pool
     * @param e
     * @param mergedPool
     * @return
     */
    private EntitlementCertificate generateEntitlementCertificate(
        Consumer consumer, Pool pool, Entitlement e) {
        Subscription sub = null;
        if (pool.getSubscriptionId() != null) {
            sub = subAdapter.getSubscription(pool.getSubscriptionId());
        }

        Product product = null;
        if (sub != null) {
            // Just look this up off of the subscription if one exists
            product = sub.getProduct();
        }
        else {
            // This is possible in a sub-pool, for example - the pool was not
            // created directly from a subscription
            product = productAdapter.getProductById(e.getProductId());

            // in the case of a sub-pool, we want to find the originating
            // subscription for cert generation
            sub = findSubscription(e);
        }

        // TODO: Assuming every entitlement = generate a cert, most likely we'll want
        // to know if this product entails granting a cert someday.
        try {
            return entCertAdapter.generateEntitlementCert(e, sub, product);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Subscription findSubscription(Entitlement entitlement) {
        Pool pool = entitlement.getPool();

        if (pool.getSubscriptionId() != null) {
            return subAdapter.getSubscription(pool.getSubscriptionId());
        }

        Entitlement source = pool.getSourceEntitlement();

        if (source != null) {
            return findSubscription(source);
        }

        // Cannot traverse any further - just give up
        return null;
    }

    public void regenerateEntitlementCertificates(Consumer consumer) {
        log.info("Regenerating #" + consumer.getEntitlements().size() +
            " entitlement's certificates for consumer :" + consumer);
        //TODO - Assumes only 1 entitlement certificate exists per entitlement
        this.regenerateCertificatesOf(consumer.getEntitlements());
        log.info("Completed Regenerating #" + consumer.getEntitlements().size() +
            " entitlement's certificates for consumer: " + consumer);
    }

    @Transactional
    public void regenerateCertificatesOf(Iterable<Entitlement> iterable) {
        for (Entitlement e : iterable) {
            regenerateCertificatesOf(e);
        }
    }
    /**
     * @param e
     */
    @Transactional
    public void regenerateCertificatesOf(Entitlement e) {
        if (log.isDebugEnabled()) {
            log.debug("Revoking entitlementCertificates of : " + e);
        }
        this.entCertAdapter.revokeEntitlementCertificates(e);
        for (EntitlementCertificate ec : e.getCertificates()) {
            if (log.isDebugEnabled()) {
                log.debug("Deleting entitlementCertificate: #" + ec.getId());
            }
            this.entitlementCertificateCurator.delete(ec);
        }
        e.getCertificates().clear();
        //below call creates new certificates and saves it to the backend.
        EntitlementCertificate generated =
            this.generateEntitlementCertificate(e.getConsumer(), e.getPool(), e);
        this.entitlementCurator.refresh(e);

        //send entitlement changed event.
        this.sink.sendEvent(this.eventFactory.entitlementChanged(e));
        if (log.isDebugEnabled()) {
            log.debug("Generated entitlementCertificate: #" + generated.getId());
        }
    }

    // TODO: Does the enforcer have any rules around removing entitlements?
    @Transactional
    public void revokeEntitlement(Entitlement entitlement) {
        if (!entitlement.isFree()) {
            // put this entitlement back in the pool
            Pool.dockConsumed(entitlement);
        }

        Consumer consumer = entitlement.getConsumer();
        consumer.removeEntitlement(entitlement);

        // Look for pools referencing this entitlement as their source entitlement
        // and clean them up as well:
        for (Pool p : poolCurator.listBySourceEntitlement(entitlement)) {
            deletePool(p);
        }

        Event event = eventFactory.entitlementDeleted(entitlement);

        poolCurator.merge(entitlement.getPool());
        entCertAdapter.revokeEntitlementCertificates(entitlement);
        entitlementCurator.delete(entitlement);
        sink.sendEvent(event);
    }

    @Transactional
    public void revokeAllEntitlements(Consumer consumer) {
        for (Entitlement e : entitlementCurator.listByConsumer(consumer)) {
            revokeEntitlement(e);
        }
    }

    /**
     * Cleanup entitlements and safely delete the given pool.
     *
     * @param pool
     */
    @Transactional
    public void deletePool(Pool pool) {
        Event event = eventFactory.poolDeleted(pool);

        // Must do a full revoke for all entitlements:
        for (Entitlement e : poolCurator.entitlementsIn(pool)) {
            revokeEntitlement(e);
        }

        poolCurator.delete(pool);
        sink.sendEvent(event);
    }

}
