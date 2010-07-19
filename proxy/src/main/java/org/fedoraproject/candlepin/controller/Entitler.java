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

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * Entitler
 */
public class Entitler {

    private PoolCurator epCurator;
    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private Enforcer enforcer;
    private static Logger log = Logger.getLogger(Entitler.class);
    private EntitlementCertServiceAdapter entCertAdapter;
    private SubscriptionServiceAdapter subAdapter;
    private EventFactory eventFactory;
    private EventSink sink;
    private PostEntHelper postEntHelper;
    private EntitlementCertificateCurator entitlementCertificateCurator;

    @Inject
    protected Entitler(PoolCurator poolCurator,
        EntitlementCurator entitlementCurator, ConsumerCurator consumerCurator,
        Enforcer enforcer, EntitlementCertServiceAdapter entCertAdapter, 
        SubscriptionServiceAdapter subAdapter,
        EventFactory eventFactory,
        EventSink sink,
        PostEntHelper postEntHelper, EntitlementCertificateCurator ecC) {
        
        this.epCurator = poolCurator;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
        this.entCertAdapter = entCertAdapter;
        this.subAdapter = subAdapter;
        this.eventFactory = eventFactory;
        this.sink = sink;
        this.postEntHelper = postEntHelper;
        this.entitlementCertificateCurator = ecC;
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
            epCurator.listByOwnerAndProduct(owner, productId));
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
        PreEntHelper preHelper = enforcer.pre(consumer, pool, quantity);
        ValidationResult result = preHelper.getResult();

        if (!result.isSuccessful()) {
            log.warn("Entitlement not granted: " +
                result.getErrors().toString());
            throw new EntitlementRefusedException(result);
        }

        Entitlement e 
            = new Entitlement(pool, consumer, new Date(), pool.getEndDate(), quantity);
        consumer.addEntitlement(e);

        if (preHelper.getGrantFreeEntitlement()) {
            log.info("Granting free entitlement.");
            e.setIsFree(Boolean.TRUE);
        }
        else {
            pool.bumpConsumed(quantity);
        }

        postEntHelper.init(e);
        enforcer.post(consumer, postEntHelper, e);

        entitlementCurator.create(e);
        consumerCurator.update(consumer);
        // TODO: This does not look like it should need a merge, just edit
        // the pool directly.
        Pool mergedPool = epCurator.merge(pool);
        generateEntitlementCertificate(consumer, mergedPool, e);
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
        Subscription sub = subAdapter.getSubscription(pool.getSubscriptionId());
        
        if (sub == null) {
            log.warn("Cannot generate entitlement certificate, no subscription for pool: " +
                pool.getId());
            
        }
        else {
            // TODO: Assuming every entitlement = generate a cert, most likely we'll want
            // to know if this product entails granting a cert someday.
            try {
                return entCertAdapter.
                    generateEntitlementCert(consumer, e, sub, sub.getProduct(),
                    sub.getEndDate());
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return null;
    }

    @Transactional
    public void regenerateEntitlementCertificates(Consumer consumer) {
        log.info("Regenerating #" + consumer.getEntitlements().size() +
            " entitlement's certificates for consumer :" + consumer);
        //TODO - Assumes only 1 entitlement certificate exists per entitlement
        for (Entitlement e : consumer.getEntitlements()) {
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
                this.generateEntitlementCertificate(consumer, e.getPool(), e);
            this.entitlementCurator.refresh(e);

            //send entitlement changed event.
            this.sink.sendEvent(this.eventFactory.entitlementChanged(e));
            if (log.isDebugEnabled()) {
                log.debug("Generated entitlementCertificate: #" + generated.getId());
            }
        }
        log.info("Completed Regenerating #" + consumer.getEntitlements().size() +
            " entitlement's certificates for consumer: " + consumer);
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
        for (Pool p : epCurator.listBySourceEntitlement(entitlement)) {
            deletePool(p);
        }

        Event event = eventFactory.entitlementDeleted(entitlement); 
        
        epCurator.merge(entitlement.getPool());
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
        for (Entitlement e : pool.getEntitlements()) {
            revokeEntitlement(e);
        }
        epCurator.delete(pool);
        sink.sendEvent(event);
    }

}
