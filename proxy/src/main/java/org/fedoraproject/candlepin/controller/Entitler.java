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

import java.math.BigInteger;
import java.util.Date;

import org.apache.log4j.Logger;
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
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
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
    private ProductServiceAdapter productAdapter;
    private EntitlementCertificateCurator entCertCurator;
    
    @Inject
    protected Entitler(PoolCurator epCurator,
        EntitlementCurator entitlementCurator, ConsumerCurator consumerCurator, 
        EntitlementCertificateCurator entCertCurator,
        Enforcer enforcer, EntitlementCertServiceAdapter entCertAdapter, 
        SubscriptionServiceAdapter subAdapter,
        ProductServiceAdapter productAdapter) {
        
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.enforcer = enforcer;
        this.entCertAdapter = entCertAdapter;
        this.productAdapter = productAdapter;
        this.subAdapter = subAdapter;
        this.entCertCurator = entCertCurator;
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
     * @param product
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
    public Entitlement entitle(Consumer consumer, Product product)
        throws EntitlementRefusedException {
        Owner owner = consumer.getOwner();

        Pool pool = enforcer.selectBestPool(consumer, product.getId(),
            epCurator.listByOwnerAndProduct(owner, product));
        if (pool == null) {
            throw new RuntimeException("No entitlements for product: " +
                product.getName());
        }

        return addEntitlement(consumer, pool);
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
    public Entitlement entitle(Consumer consumer, Pool pool)
        throws EntitlementRefusedException {

        return addEntitlement(consumer, pool);
    }

    private Entitlement addEntitlement(Consumer consumer, Pool pool)
        throws EntitlementRefusedException {
        PreEntHelper preHelper = enforcer.pre(consumer, pool);
        ValidationResult result = preHelper.getResult();

        if (!result.isSuccessful()) {
            log.warn("Entitlement not granted: " +
                result.getErrors().toString());
            throw new EntitlementRefusedException(result);
        }

        Entitlement e = new Entitlement(pool, consumer, new Date());
        consumer.addEntitlement(e);

        if (preHelper.getGrantFreeEntitlement()) {
            log.info("Granting free entitlement.");
            e.setIsFree(Boolean.TRUE);
        }
        else {
            pool.bumpConsumed();
        }

        enforcer.post(e);

        entitlementCurator.create(e);
        consumerCurator.update(consumer);
        Pool mergedPool = epCurator.merge(pool);
        
        Subscription sub = subAdapter.getSubscription(mergedPool.getSubscriptionId());
        if (sub == null) {
            log.warn("Cannot generate entitlement certificate, no subscription for pool: " +
                pool.getId());
            
        }
        else {
            Product prod = productAdapter.getProductById(sub.getProductId());
        
            // TODO: Assuming every entitlement = generate a cert, most likely we'll want
            // to know if this product entails granting a cert someday.
            try {
                // TODO: Fix serial here:
                EntitlementCertificate cert = this.entCertAdapter.generateEntitlementCert(consumer, e, sub, prod, 
                    sub.getEndDate(), BigInteger.valueOf(e.getId()));
                e.getCertificates().add(cert);
                this.entCertCurator.create(cert);
            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        return e;
    }

    // TODO: Does the enforcer have any rules around removing entitlements?
    @Transactional
    public void revokeEntitlement(Entitlement entitlement) {
        if (!entitlement.isFree()) {
            // put this entitlement back in the pool
            entitlement.getPool().dockConsumed();
        }

        Consumer consumer = entitlement.getConsumer();
        consumer.removeEntitlement(entitlement);

        epCurator.merge(entitlement.getPool());
        entitlementCurator.delete(entitlement);
    }
}
