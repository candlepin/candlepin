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

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.util.CertificateSizeException;
import org.candlepin.version.CertVersionConflictException;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;



/**
 * The EntitlementCertificateGenerator class provides utility methods for regenerating the
 * certificates for entitlements associated with various model objects.
 * <p></p>
 * It should be noted that due to the amount of graph traversal that can occur when performing these
 * operations, the number of calls to methods provided by this class should be minimized. The majority
 * are incredibly expensive and, in the case of immediate regeneration, can hold database locks for the
 * duration. Usage of these methods should be carefully evaluated, and bulk operations should be
 * preferred to singular ones.
 */
public class EntitlementCertificateGenerator {
    private static Logger log = LoggerFactory.getLogger(EntitlementCertificateGenerator.class);

    private EntitlementCertificateCurator entitlementCertificateCurator;
    private EntitlementCertServiceAdapter entCertServiceAdapter;
    private EntitlementCurator entitlementCurator;
    private PoolCurator poolCurator;

    private EventSink eventSink;
    private EventFactory eventFactory;

    @Inject
    public EntitlementCertificateGenerator(EntitlementCertificateCurator entitlementCertificateCurator,
        EntitlementCertServiceAdapter entCertServiceAdapter, EntitlementCurator entitlementCurator,
        PoolCurator poolCurator, EventSink eventSink, EventFactory eventFactory) {

        this.entitlementCertificateCurator = entitlementCertificateCurator;
        this.entCertServiceAdapter = entCertServiceAdapter;
        this.entitlementCurator = entitlementCurator;
        this.poolCurator = poolCurator;

        this.eventSink = eventSink;
        this.eventFactory = eventFactory;
    }

    /**
     * Generates new entitlement certificates for the given consumer using the provided products and
     * entitlements.
     *
     * @param consumer
     *  The consumer for which to generate new entitlement certificates
     *
     * @param products
     *  A mapping of products, indexed by pool ID, to use when generating certificates
     *
     * @param entitlements
     *  A mapping of entitlements, indexed by pool ID, to use when generating certificates
     *
     * @param generateUeberCert
     *  Whether or not to generate ueber certs
     *
     * @return
     *  A map of generated entitlement certificates, indexed by pool ID
     */
    @Transactional
    public Map<String, EntitlementCertificate> generateEntitlementCertificates(Consumer consumer,
        Map<String, Product> products, Map<String, Entitlement> entitlements, boolean generateUeberCert) {

        try {
            return generateUeberCert ?
                this.entCertServiceAdapter.generateUeberCerts(consumer, entitlements, products) :
                this.entCertServiceAdapter.generateEntitlementCerts(consumer, entitlements, products);
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

    /**
     * Generates a new entitlement certificate for the given entitlement and pool.
     *
     * @param pool
     *  The pool for which to generate an entitlement certificate
     *
     * @param entitlement
     *  The entitlement to use when generating the certificate
     *
     * @param generateUeberCert
     *  Whether or not to generate an ueber cert
     *
     * @return
     *  The newly generate entitlement certificate
     */
    @Transactional
    public EntitlementCertificate generateEntitlementCertificate(Pool pool, Entitlement entitlement,
        boolean generateUeberCert) {

        Map<String, Product> products = new HashMap<String, Product>();
        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();

        products.put(pool.getId(), pool.getProduct());
        entitlements.put(pool.getId(), entitlement);

        return this.generateEntitlementCertificates(entitlement.getConsumer(), products, entitlements,
            generateUeberCert).get(pool.getId());
    }

    /**
     * Regenerates the certificates for the specified entitlement.
     *
     * @param entitlement
     *  The entitlement for which to regenerate certificates
     *
     * @param ueberCertificate
     *  Whether or not to generate an ueber certificate
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Entitlement entitlement, boolean ueberCertificate, boolean lazy) {
        if (lazy) {
            log.info("Marking certificates dirty for entitlement: {}", entitlement);
            entitlement.setDirty(true);
            return;
        }

        log.debug("Revoking entitlementCertificates of: {}", entitlement);

        Entitlement tempE = new Entitlement();
        tempE.setCertificates(entitlement.getCertificates());
        entitlement.setCertificates(null);

        // below call creates new certificates and saves it to the backend.
        try {
            EntitlementCertificate generated = this.generateEntitlementCertificate(
                entitlement.getPool(), entitlement, ueberCertificate
            );

            entitlement.setDirty(false);
            this.entitlementCurator.merge(entitlement);
            for (EntitlementCertificate ec : tempE.getCertificates()) {
                log.debug("Deleting entitlementCertificate: #{}", ec.getId());
                this.entitlementCertificateCurator.delete(ec);
            }

            // send entitlement changed event.
            this.eventSink.queueEvent(this.eventFactory.entitlementChanged(entitlement));
            log.debug("Generated entitlementCertificate: #{}", generated.getId());
        }
        catch (CertificateSizeException cse) {
            entitlement.setCertificates(tempE.getCertificates());
            log.warn("The certificate cannot be regenerated at this time: {}", cse.getMessage());
        }
    }

    /**
     * Regenerates the certificates for the specified entitlements. This method is a utility method
     * which individually regenerates certificates for each entitlement in the provided collection.
     *
     * @param entitlements
     *  An iterable collection of entitlements for which to regenerate certificates
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Iterable<Entitlement> entitlements, boolean lazy) {
        for (Entitlement entitlement : entitlements) {
            this.regenerateCertificatesOf(entitlement, false, lazy);
        }
    }

    /**
     * Regenerates the certificates for the specified entitlements. This method is a utility method
     * which individually regenerates certificates for each entitlement in the provided collection.
     *
     * @param entitlementIds
     *  An iterable collection of entitlement IDs for which to regenerate certificates
     *
     * @param ueberCertificate
     *  Whether or not to generate an ueber certificate
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesByEntitlementIds(Iterable<String> entitlementIds,
        boolean ueberCertificate, boolean lazy) {

        // If we're regenerating these lazily, we can avoid loading all of them by just updating the
        // DB directly.

        if (lazy) {
            this.entitlementCurator.markEntitlementsDirty(entitlementIds);
        }
        else {
            for (String entitlementId : entitlementIds) {
                Entitlement entitlement = entitlementCurator.find(entitlementId);

                if (entitlement == null) {
                    // If it has been deleted, that's fine; one less to regenerate
                    log.info("Unable to load entitlement for regeneration: {}", entitlementId);
                    continue;
                }

                this.regenerateCertificatesOf(entitlement, ueberCertificate, false);
            }
        }
    }

    /**
     * Regenerates all known certificates for the given consumer.
     *
     * @param consumer
     *  The consumer for which to regenerate entitlement certificates
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Consumer consumer, boolean lazy) {
        log.info(
            "Regenerating #{}, entitlement certificates for consumer: {}",
            consumer.getEntitlements().size(), consumer
        );

        // TODO - Assumes only 1 entitlement certificate exists per entitlement
        this.regenerateCertificatesOf(consumer.getEntitlements(), lazy);
    }

    /**
     * Regenerates the certificates for the specified contents in a given environment.
     *
     * @param environment
     *  The environment in which the entitlements should be regenerated
     *
     * @param contentIds
     *  A collection of content Ids for which to regenerate entitlement certificates
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark them dirty and allow them to
     *  be regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Environment environment, Collection<String> contentIds,
        boolean lazy) {

        log.info("Regenerating relevant certificates in environment: {}", environment);

        List<Entitlement> entitlements = this.entitlementCurator.listByEnvironment(environment);
        Set<Entitlement> entsToRegen = new HashSet<Entitlement>();

        entLoop: for (Entitlement entitlement : entitlements) {
            // Impl note:
            // Since the entitlements came from the DB, we should be safe to traverse the graph as
            // necessary without any sanity checks (so long as our model's restrictions aren't
            // broken).

            for (String contentId : contentIds) {
                if (entitlement.getPool().getProduct().hasContent(contentId)) {
                    entsToRegen.add(entitlement);
                    continue entLoop;
                }

                for (Product provided : entitlement.getPool().getProvidedProducts()) {
                    if (provided.hasContent(contentId)) {
                        entsToRegen.add(entitlement);
                        continue entLoop;
                    }
                }
            }
        }

        log.info("Found {} certificates to regenerate.", entsToRegen.size());
        this.regenerateCertificatesOf(entsToRegen, lazy);
    }

    /**
     * Regenerates the entitlement certificates of all entitlements for pools using the specified
     * product.
     *
     * @param owner
     *  The owner for which to regenerate entitlement certificates
     *
     * @param productId
     *  The Red Hat ID of the product for which to regenerate certificates
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Owner owner, String productId, boolean lazy) {
        List<Pool> pools = this.poolCurator.listAvailableEntitlementPools(
            null, owner, productId, new Date(), false
        );

        for (Pool pool : pools) {
            this.regenerateCertificatesOf(pool.getEntitlements(), lazy);
        }
    }

    /**
     * Regenerates the entitlement certificates of all entitlements for pools using the specified
     * product.
     *
     * @param owner
     *  The owner for which to regenerate entitlement certificates
     *
     * @param product
     *  The product for which to regenerate certificates
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Owner owner, Product product, boolean lazy) {
        this.regenerateCertificatesOf(owner, product.getId(), lazy);
    }

    /**
     * Regenerates the entitlement certificates for all pools using any of the the specified
     * product(s), effective for the given owners.
     *
     * @param owners
     *  A collection of owners for which the certificates should be generated. Pools using the given
     *  products but not owned by an owner within this collection will not have their certificates
     *  regenerated.
     *
     * @param products
     *  A collection of products for which to regenerate affected certificates
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Collection<Owner> owners, Collection<Product> products,
        boolean lazy) {

        List<Pool> pools = new LinkedList<Pool>();

        Set<String> productIds = new HashSet<String>();
        Date now = new Date();

        for (Product product : products) {
            productIds.add(product.getId());
        }

        // TODO: This is a very expensive operation. Update pool curator with something to let us
        // do this without hitting the DB several times over.
        for (Owner owner : owners) {
            pools.addAll(
                this.poolCurator.listAvailableEntitlementPools(null, owner, productIds, now, false)
            );
        }

        for (Pool pool : pools) {
            this.regenerateCertificatesOf(pool.getEntitlements(), lazy);
        }
    }

    /**
     * Regenerates the entitlement certificates for all pools using any of the the specified
     * product(s), effective for the all owners using them.
     *
     * @param products
     *  A collection of products for which to regenerate affected certificates
     *
     * @param lazy
     *  Whether or not to generate the certificate immediately, or mark it dirty and allow it to be
     *  regenerated on-demand
     */
    @Transactional
    public void regenerateCertificatesOf(Collection<Product> products, boolean lazy) {
        Set<Owner> owners = new HashSet<Owner>();

        // TODO: This could fall over if a product is mapped to many owners.
        for (Product product : products) {
            // FIXME: THIS IS BROKEN AT THE MOMENT. UPDATE IT ONCE WE FIGURE OUT WHAT WE'RE DOING WITH
            // CONTENT MANAGER
            //owners.addAll();
            throw new RuntimeException("We're broken right now; check back in 15 minutes.");
        }

        this.regenerateCertificatesOf(owners, products, lazy);
    }

}
