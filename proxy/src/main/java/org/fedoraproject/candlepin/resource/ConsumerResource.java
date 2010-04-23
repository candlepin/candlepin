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
package org.fedoraproject.candlepin.resource;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCurator;

import org.fedoraproject.candlepin.model.IdentityCertificate;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.auth.interceptor.EnforceConsumer;

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
public class ConsumerResource {
    private static Logger log = Logger.getLogger(ConsumerResource.class);
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductServiceAdapter productAdapter;
    private PoolCurator epCurator;
    private Entitler entitler;
    private SubscriptionServiceAdapter subAdapter;
    private EntitlementCurator entitlementCurator;
    private IdentityCertServiceAdapter identityCertService;
    private EntitlementCertServiceAdapter entCertService;
    private Principal principal;
    private I18n i18n;

    @Inject
    public ConsumerResource(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ProductServiceAdapter productAdapter, Entitler entitler,
        SubscriptionServiceAdapter subAdapter, PoolCurator epCurator,
        EntitlementCurator entitlementCurator,
        IdentityCertServiceAdapter identityCertService,
        EntitlementCertServiceAdapter entCertServiceAdapter,
        Principal principal,
        I18n i18n) {

        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productAdapter = productAdapter;
        this.subAdapter = subAdapter;
        this.entitler = entitler;
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.identityCertService = identityCertService;
        this.entCertService = entCertServiceAdapter;
        this.principal = principal;
        this.i18n = i18n;
    }

    /**
     * List available Consumers
     * 
     * @return list of available consumers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Wrapped(element = "consumers")    
    public List<Consumer> list() {
        return consumerCurator.findAll();
    }

    /**
     * Return the consumer identified by the given uuid.
     * 
     * @param uuid uuid of the consumer sought.
     * @return the consumer identified by the given uuid.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    @EnforceConsumer
    public Consumer getConsumer(@PathParam("consumer_uuid") String uuid) {
        return verifyAndLookupConsumer(uuid);
    }

    /**
     * Create a Consumer
     * 
     * @param in Consumer metadata encapsulated in a ConsumerInfo.
     * @return newly created Consumer
     * @throws BadRequestException generic exception type for web services We
     *         are calling this "registerConsumer" in the api discussions
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Consumer create(Consumer in) throws BadRequestException {
        // API:registerConsumer

        ConsumerType type = consumerTypeCurator.lookupByLabel(
            in.getType().getLabel());

        if (type == null) {
            throw new BadRequestException(
                i18n.tr("No such consumer type: {0}", in.getType().getLabel()) 
            );
        }

        // copy the incoming consumer to avoid modifying the reference.
        Consumer copy = new Consumer(in);
        copy.setOwner(principal.getOwner());
        copy.setType(type); // the type comes in without

        if (log.isDebugEnabled()) {
            if (copy.getType() != null) {
                log.debug("Got consumerTypeLabel of: " + copy.getType().getLabel());
            }
            log.debug("got metadata: ");
            log.debug(copy.getFacts());

            for (String key : copy.getFacts().keySet()) {
                log.debug("   " + key + " = " + copy.getFact(key));
            }
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("my consumer: " + copy);
            }

            Consumer consumer = consumerCurator.create(copy);
            IdentityCertificate idCert = null;

            // This is pretty bad - I'm still not convinced that
            // the id cert actually needs the username at all
            if (principal instanceof UserPrincipal) {
                UserPrincipal user = (UserPrincipal) principal;

                idCert = identityCertService.generateIdentityCert(consumer,
                        user.getUsername());
            }

            if (log.isDebugEnabled()) {
                log.debug("Generated identity cert: " + idCert);
            }

            if (idCert == null) {
                throw new RuntimeException(
                    "Error generating identity certificate.");
            }

            return consumer;
        }
        catch (Exception e) {
            log.error("Problem creating consumer:", e);
            throw new BadRequestException(i18n.tr("Problem creating consumer {0}", in));
        }
    }

    /**
     * delete the consumer.
     * 
     * @param uuid uuid of the consumer to delete.
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    @Transactional
    @EnforceConsumer
    public void deleteConsumer(@PathParam("consumer_uuid") String uuid) {
        log.debug("deleteing  consumer_uuid" + uuid);
        Consumer toDelete = verifyAndLookupConsumer(uuid);
        this.unbindAllOrBySerialNumber(uuid, null);
        consumerCurator.delete(toDelete);
        identityCertService.deleteIdentityCert(toDelete);
    }

    /**
     * Returns the product whose id matches pid, from the consumer, cid.
     * 
     * @param cid
     *            Consumer ID to affect
     * @param pid
     *            Product ID to remove from Consumer.
     * @return the product whose id matches pid, from the consumer, cid.
     */
    @GET
    @Path("{consumer_uuid}/products/{product_id}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @EnforceConsumer
    public Product getProduct(@PathParam("consumer_uuid") String cid,
        @PathParam("product_id") String pid) {
        return null;
    }

    /**
     * Return the entitlement certificate for the given consumer.
     * 
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificates for the given consumer.
     */
    @GET
    @Path("{consumer_uuid}/certificates")
    @Produces({ MediaType.APPLICATION_JSON })
    @EnforceConsumer
    public List<EntitlementCertificate> getEntitlementCertificates(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("serials") String serials) {

        log.debug("Getting client certificates for consumer: " + consumerUuid);
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        Set<BigInteger> serialSet = new HashSet<BigInteger>();
        if (serials != null) {
            log.debug("Requested serials: " + serials);
            for (String s : serials.split(",")) {
                log.debug("   " + s);
                serialSet.add(BigInteger.valueOf(Long.parseLong(s)));
            }
        }

        List<EntitlementCertificate> allCerts =
            new LinkedList<EntitlementCertificate>();
        for (EntitlementCertificate cert : entCertService.listForConsumer(consumer)) {

            if (serialSet.size() == 0 || serialSet.contains(cert.getSerial())) {
                allCerts.add(cert);
            }
        }
        return allCerts;
    }

    /**
     * Return the client certificate metadata for the given consumer. This is a
     * small subset of data clients can use to determine which certificates they
     * need to update/fetch.
     * 
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificate metadata for the given consumer.
     */
    @GET
    @Path("{consumer_uuid}/certificates/serials")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Wrapped(element = "serials")
    @EnforceConsumer
    public List<CertificateSerial> getEntitlementCertificateSerials(
        @PathParam("consumer_uuid") String consumerUuid) {

        log.debug("Getting client certificate serials for consumer: " +
            consumerUuid);
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        List<CertificateSerial> allCerts = new LinkedList<CertificateSerial>();
        for (EntitlementCertificate cert :
            entCertService.listForConsumer(consumer)) {
            allCerts.add(new CertificateSerial(cert.getSerial()));
        }

        return allCerts;
    }

    /**
     * Entitles the given Consumer with the given Product.
     * 
     * @param productId Product identifying label.
     * @return Entitled object
     */
    private List<Entitlement> bindByProduct(String productId, Consumer consumer) {

        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        Product p = productAdapter.getProductById(productId);
        if (p == null) {
            throw new BadRequestException(
                i18n.tr("No such product: {0}", productId));
        }

        entitlementList.add(createEntitlement(consumer, p));
        return entitlementList;
        
    }

    // TODO: Bleh, very duplicated methods here:
    private Entitlement createEntitlement(Consumer consumer, Product p) {
        // Attempt to create an entitlement:
        try {
            Entitlement e = entitler.entitle(consumer, p);
            log.debug("Created entitlement: " + e);
            return e;
        }
        catch (EntitlementRefusedException e) {
            // Could be multiple errors, but we'll just report the first one for now:
            // TODO: Convert resource key to user friendly string?
            throw new ForbiddenException(e.getResult().getErrors().get(0).getResourceKey());
        }
    }

    private Entitlement createEntitlement(Consumer consumer, Pool pool) {
        // Attempt to create an entitlement:
        try {
            Entitlement e = entitler.entitle(consumer, pool);
            log.debug("Created entitlement: " + e);
            return e;
        }
        catch (EntitlementRefusedException e) {
            // Could be multiple errors, but we'll just report the first one for now:
            // TODO: Convert resource key to user friendly string?
            throw new ForbiddenException(e.getResult().getErrors().get(0).getResourceKey());
        }
    }

    /**
     * Grants entitlements based on a registration token.
     * 
     * @param registrationToken registration token.
     * @param consumer Consumer to bind
     * @return token
     */
    private List<Entitlement> bindByToken(String registrationToken, Consumer consumer) {
        
        List<Subscription> s = subAdapter.getSubscriptionForToken(registrationToken);
        if ((s == null) || (s.isEmpty())) {
            log.debug("token: " + registrationToken);
            throw new BadRequestException(
                i18n.tr("No such token: {0}", registrationToken));
        }

        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        for (Subscription subscription : s) {
            Product p = productAdapter.getProductById(subscription.getProductId());
            entitlementList.add(createEntitlement(consumer, p));
            
        }
        return entitlementList;
    }

    private List<Entitlement> bindByPool(Long poolId, Consumer consumer) {
        Pool pool = epCurator.find(poolId);
        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        if (pool == null) {
            throw new BadRequestException(
                i18n.tr("No such entitlement pool: {0}", poolId));
        }

        // Attempt to create an entitlement:
        entitlementList.add(createEntitlement(consumer, pool));
        return entitlementList;
    }

    /**
     * Request an entitlement.
     * 
     * @param consumerUuid Consumer identifier to be entitled
     * @param poolId Entitlement pool id.
     * @return Entitlement.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/{consumer_uuid}/entitlements")
    public List<Entitlement> bind(@PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("pool") Long poolId, @QueryParam("token") String token,
        @QueryParam("product") String productId) {

        // Check that only one query param was set:
        if ((poolId != null && token != null) ||
            (poolId != null && productId != null) ||
            (token != null && productId != null)) {
            throw new BadRequestException(
                i18n.tr("Cannot bind by multiple parameters."));
        }

        // Verify consumer exists:
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        if (token != null) {
            return bindByToken(token, consumer);
        }
        if (productId != null) {
            return bindByProduct(productId, consumer);
        }

        return bindByPool(poolId, consumer);
    }

    private Consumer verifyAndLookupConsumer(String consumerUuid) {
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {

            throw new NotFoundException(
                i18n.tr("No such consumer: {0}", consumerUuid));
        }
        return consumer;
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/{consumer_uuid}/entitlements")
    public List<Entitlement> listEntitlements(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("product") String productId) {

        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        if (productId != null) {
            Product p = productAdapter.getProductById(productId);
            if (p == null) {
                throw new BadRequestException(
                    i18n.tr("No such product: {0}", productId));
            }
            return entitlementCurator.listByConsumerAndProduct(consumer, productId);
        }

        return entitlementCurator.listByConsumer(consumer);
        
    }

    /**
     * Unbind entitlements by serial number, or unbind all.
     * 
     * @param consumerUuid Unique id for the Consumer.
     * @param serials comma seperated list of subscription numbers.
     */
    @DELETE
    @Path("/{consumer_uuid}/entitlements")
    @EnforceConsumer
    public void unbindAllOrBySerialNumber(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("serial") String serials) {

        if (serials == null) {
            // FIXME: just a stub, needs CertifcateService (and/or a
            // CertificateCurator) to lookup by serialNumber
            Consumer consumer = verifyAndLookupConsumer(consumerUuid);

            if (consumer == null) {
                throw new NotFoundException(
                    i18n.tr("Consumer with ID " + consumerUuid + " could not be found."));
            }

            for (Entitlement entitlement : entitlementCurator
                .listByConsumer(consumer)) {
                entitler.revokeEntitlement(entitlement);
            }

            // Need to parse off the value of subscriptionNumberArgs, probably
            // use comma separated see IntergerList in sparklines example in
            // jersey examples find all entitlements for this consumer and
            // subscription numbers delete all of those (and/or return them to
            // entitlement pool)
        }
        else {
            throw new RuntimeException(
                "Unbind by serial number not yet supported.");
        }

    }

    /**
     * Remove an entitlement by ID.
     * 
     * @param dbid the entitlement to delete.
     */
    @DELETE
    @Path("/{consumer_uuid}/entitlements/{dbid}")
    @EnforceConsumer
    public void unbind(@PathParam("consumer_uuid") String consumerUuid,
        @PathParam("dbid") Long dbid) {

        verifyAndLookupConsumer(consumerUuid);

        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            entitler.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID '" + dbid + "' could not be found"));
    }

}
