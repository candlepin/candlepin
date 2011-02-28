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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.guice.PrincipalProvider;
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
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.util.Util;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

import edu.emory.mathcs.backport.java.util.Arrays;
import org.fedoraproject.candlepin.policy.PoolRules;

/**
 * PoolManager
 */
public class CandlepinPoolManager implements PoolManager {

    private PoolCurator poolCurator;
    private Logger log = Logger.getLogger(CandlepinPoolManager.class);

    private SubscriptionServiceAdapter subAdapter;
    private ProductServiceAdapter productAdapter;
    private EventSink sink;
    private EventFactory eventFactory;
    private Config config;
    private Enforcer enforcer;
    private PoolRules poolRules;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private EntitlementCertServiceAdapter entCertAdapter;
    private EntitlementCertificateCurator entitlementCertificateCurator;
    private PrincipalProvider principalProvider;
    private I18n i18n;

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
        ProductServiceAdapter productAdapter, EntitlementCertServiceAdapter entCertAdapter,
        EventSink sink, EventFactory eventFactory, Config config,
        Enforcer enforcer,
        PoolRules poolRules,
        EntitlementCurator curator1, ConsumerCurator consumerCurator,
        EntitlementCertificateCurator ecC,
        PrincipalProvider principalProvider,
        I18n i18n) {

        this.poolCurator = poolCurator;
        this.subAdapter = subAdapter;
        this.productAdapter = productAdapter;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.config = config;
        this.entitlementCurator = curator1;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
        this.poolRules = poolRules;
        this.entCertAdapter = entCertAdapter;
        this.entitlementCertificateCurator = ecC;
        this.principalProvider = principalProvider;
        this.i18n = i18n;
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
            owner, (String) null, false, null);

        if (log.isDebugEnabled()) {
            log.debug("Found pools: ");
            for (Pool p : pools) {
                log.debug("   " + p);
            }
        }

        // Map all  pools for this owner/product that have a
        // subscription ID associated with them.
        Map<String, Pool> subToPoolMap = new HashMap<String, Pool>();
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
        for (Entry<String, Pool> entry : subToPoolMap.entrySet()) {
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
        boolean productsChanged = checkForChangedProducts(existingPool, sub);

        if (!(quantityChanged || datesChanged || productsChanged)) {
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
        if (datesChanged || productsChanged) {
            existingPool.setStartDate(sub.getStartDate());
            existingPool.setEndDate(sub.getEndDate());
            List<Entitlement> entitlements = poolCurator
                .retrieveFreeEntitlementsOfPool(existingPool, true);

            if (productsChanged) {
                log.debug("Merging provided products.");
                log.debug("   size before = " + existingPool.getProvidedProducts().size());

                existingPool.setProductName(sub.getProduct().getName());
                existingPool.getProvidedProducts().clear();

                if (sub.getProvidedProducts() != null) {
                    for (Product p : sub.getProvidedProducts()) {
                        log.debug("   adding " + p.getName());
                        ProvidedProduct providedProduct = new ProvidedProduct(
                            p.getId(), p.getName(), existingPool);
                        existingPool.addProvidedProduct(providedProduct);
                    }
                }
            }

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

    private boolean checkForChangedProducts(Pool existingPool, Subscription sub) {
        Set<String> poolProducts = new HashSet<String>();
        Set<String> subProducts = new HashSet<String>();
        poolProducts.add(existingPool.getProductId());
        
        for (ProvidedProduct pp : existingPool.getProvidedProducts()) {
            poolProducts.add(pp.getProductId());
        }
        
        subProducts.add(sub.getProduct().getId());
        for (Product product : sub.getProvidedProducts()) {
            subProducts.add(product.getId());
        }
        
        // Also check if the product name has been changed:
        return !poolProducts.equals(subProducts) || 
            (existingPool.getProductName() != sub.getProduct().getName());
    }


    private boolean poolExistsForSubscription(Map<String, Pool> subToPoolMap,
            String id) {
        return subToPoolMap.containsKey(id);
    }


    /**
     * @param sub
     * @return the newly created Pool
     */
    public Pool createPoolForSubscription(Subscription sub) {
        if (log.isDebugEnabled()) {
            log.debug("Creating new pool for new sub: " + sub.getId());
        }
        Long quantity = sub.getQuantity() * sub.getProduct().getMultiplier();
        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        Pool newPool = new Pool(sub.getOwner(), sub.getProduct().getId(), 
            sub.getProduct().getName(), providedProducts,
                quantity, sub.getStartDate(), sub.getEndDate(), sub.getContractNumber(),
                sub.getAccountNumber());
        if (sub.getProvidedProducts() != null) {
            for (Product p : sub.getProvidedProducts()) {
                ProvidedProduct providedProduct = new ProvidedProduct(
                    p.getId(), p.getName());
                providedProduct.setPool(newPool);
                providedProducts.add(providedProduct);
            }
        }

        newPool.setSubscriptionId(sub.getId());
        createPool(newPool);
        return newPool;
    }

    public Pool createPool(Pool p) {
        Pool created = poolCurator.create(p);
        if (log.isDebugEnabled()) {
            log.debug("   new pool: " + p);
        }
        if (created != null) {
            sink.emitPoolCreated(created);
        }

        this.poolRules.onCreatePool(p);

        return created;
    }


    public Pool find(String poolId) {
        return this.poolCurator.find(poolId);
    }

    public Pool lookupBySubscriptionId(String id) {
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
     * @param productIds
     *            products to be entitled.
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    //
    // NOTE: after calling this method both entitlement pool and consumer
    // parameters
    // will most certainly be stale. beware!
    //
    @Transactional
    public List<Entitlement> entitleByProducts(Consumer consumer, String[] productIds,
        Integer quantity)
        throws EntitlementRefusedException {
        Owner owner = consumer.getOwner();
        List<Entitlement> entitlements = new LinkedList<Entitlement>();
        
        ValidationResult failedResult = null;
        List<Pool> candidatePools = poolCurator.listByOwner(owner);
        List<Pool> filteredPools = new LinkedList<Pool>();
        for (Pool pool : candidatePools) {
            boolean providesProduct = false;
            for (String productId : productIds) {
                if (pool.provides(productId)) {
                    providesProduct = true;
                    break;
                }
            }
            if (providesProduct) {
                PreEntHelper preHelper = enforcer.preEntitlement(consumer, pool,
                    quantity);
                ValidationResult result = preHelper.getResult();
                
                if (!result.isSuccessful()) {
                    // Just keep the last one around, if we need it
                    failedResult = result;
                    if (log.isDebugEnabled()) {
                        log.debug(
                            "Pool filtered from candidates due to rules failure: " +
                            pool.getId());
                    }
                }
                else {
                    filteredPools.add(pool);
                }
            }
        }

        if (filteredPools.size() == 0) {
            // Only throw refused exception if we actually hit the rules:
            if (failedResult != null) {
                throw new EntitlementRefusedException(failedResult);
            }
            throw new RuntimeException("No entitlements for products: " +
                Arrays.toString(productIds));
        }
        
        List<Pool> bestPools = enforcer.selectBestPools(consumer,
            productIds, filteredPools);
        if (bestPools == null) {
            throw new RuntimeException("No entitlements for products: " +
                Arrays.toString(productIds));
        }
        
        for (Pool pool : bestPools) {
            entitlements.add(addEntitlement(consumer, pool, quantity));
        }

        return entitlements;
    }

    public Entitlement entitleByProduct(Consumer consumer, String productId,
        Integer quantity)
        throws EntitlementRefusedException {
        // There will only be one returned entitlement, anyways
        return entitleByProducts(consumer, new String[] {productId}, quantity).get(0);
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

        // Because there are several paths to this one place where entitlements
        // are granted, we cannot be positive the caller obtained a lock on the pool
        // when it was read. As such we're going to reload it with a lock
        // before starting this process.
        pool = poolCurator.lockAndLoad(pool);

        /* XXX: running pre rules twice on the entitle by product case */
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

        PoolHelper postEntHelper = new PoolHelper(this, productAdapter, pool, e);
        enforcer.postEntitlement(consumer, postEntHelper, e);

        entitlementCurator.create(e);
        consumerCurator.update(consumer);

        generateEntitlementCertificate(consumer, pool, e);
        for (Entitlement regenEnt : entitlementCurator.listModifying(e)) {
            this.regenerateCertificatesOf(regenEnt);
        }

        // The quantity is calculated at fetch time. We update it here
        // To reflect what we just added to the db.
        pool.setConsumed(pool.getConsumed() + quantity);
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
    
    @Transactional
    public void regenerateCertificatesOf(String productId) {
        List<Pool> poolsForProduct = this.poolCurator.listAvailableEntitlementPools(
            null, null, productId, false, new Date());
        for (Pool pool : poolsForProduct) {
            regenerateCertificatesOf(pool.getEntitlements());
        }
    }

    public Iterable<Pool> getListOfEntitlementPoolsForProduct(String productId) {
        return this.poolCurator.listAvailableEntitlementPools(null,
            null, productId, false, null);
    }

    // TODO: Does the enforcer have any rules around removing entitlements?
    @Transactional
    public void revokeEntitlement(Entitlement entitlement) {
        if (this.principalProvider.get() instanceof ConsumerPrincipal) {
            checkForOutstandingSubPoolEntitlements(entitlement);
        }

        Consumer consumer = entitlement.getConsumer();
        consumer.removeEntitlement(entitlement);

        // Look for pools referencing this entitlement as their source entitlement
        // and clean them up as well:
        for (Pool p : poolCurator.listBySourceEntitlement(entitlement)) {
            deletePool(p);
        }

        Event event = eventFactory.entitlementDeleted(entitlement);

        Pool pool = entitlement.getPool();
        poolCurator.merge(pool);
        entCertAdapter.revokeEntitlementCertificates(entitlement);
        entitlementCurator.delete(entitlement);

        // The quantity is calculated at fetch time. We update it here
        // To reflect what we just removed from the db.
        pool.setConsumed(pool.getConsumed() - entitlement.getQuantity());

        // Find all of the entitlements that modified the original entitlement,
        // and regenerate those to remove the content sets.
        this.regenerateCertificatesOf(entitlementCurator.listModifying(entitlement));

        sink.sendEvent(event);
    }
    
    /**
     * Check if the given entitlement has any pools referencing it as their source
     * entitlement, and those pools have outstanding entitlements.
     *
     * This method is used to prevent security violations when unbinding as a consumer,
     * where other consumers are using those sub-pool entitlements.
     *
     * @param e Entitlement to check.
     * @return True if there are outstanding sub-pool entitlements.
     */
    private void checkForOutstandingSubPoolEntitlements(Entitlement entitlement) {
        String entitlementId = entitlement.getId();
        int size = this.poolCurator.getNoOfDependentEntitlements(entitlementId);
        if (size > 0) {
            this.poolCurator.disableConsumerFilter(); //don't need it.
            StringBuilder builder = new StringBuilder("");
            builder.append(i18n.tr("\n-Cannot unsubscribe entitlement ''{0}'' because:",
                entitlementId));

            List<EntitlementCertificate> entCerts = this.poolCurator
                .retrieveEntCertsOfPoolsWithSourceEntitlement(entitlementId);

            //form consumer -> [entitlementCertificate, ...] mapping
            Map<Consumer, List<EntitlementCertificate>> map = Util.newMap();
            for (Iterator<EntitlementCertificate> iterator = entCerts
                .iterator(); iterator.hasNext();) {
                EntitlementCertificate cert = iterator.next();
                Consumer consumer = cert.getEntitlement().getConsumer();
                if (!map.containsKey(consumer)) {
                    map.put(consumer, new ArrayList<EntitlementCertificate>());
                }
                map.get(consumer).add(cert);
            }

            //create the error message:
            for (Iterator<Entry<Consumer, List<EntitlementCertificate>>> iterator = map
                .entrySet().iterator(); iterator.hasNext();) {
                Entry<Consumer, List<EntitlementCertificate>> entry = iterator
                    .next();
                Consumer consumer = entry.getKey();
                List<EntitlementCertificate> certs = entry.getValue();
                builder.append(i18n.tr(
                    "\n  {0} consumer ''{1}'' with id ''{2}'' has " +
                       "the following entitlements:",
                     consumer.getType().getLabel(),
                     consumer.getName(),
                     consumer.getUuid()));

                for (Iterator<EntitlementCertificate> iterator2 = certs
                    .iterator(); iterator2.hasNext();) {
                    EntitlementCertificate certificate = iterator2.next();
                    Entitlement ent = certificate.getEntitlement();
                    builder.append(i18n.tr("\n    Entitlement ''{0}'':", ent.getId()));
                    builder.append(i18n.tr("\n        account number: ''{0}''",
                        ent.getAccountNumber()));
                    builder.append(i18n.tr("\n        serial number: ''{0}''",
                        certificate.getSerial().getId()));
                }
            }
            builder.append(
                i18n.tr(
                    "\n\nThese consumed entitlements were derived " +
                    "from subscription pool: ''{0}''.",
                     entitlement.getPool().getId()));
            builder.append(
                i18n.tr(
                    "\nYou must first unsubscribe these consumers " +
                    "from these entitlements.\n"));
            throw new ForbiddenException(builder.toString());
        }
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
