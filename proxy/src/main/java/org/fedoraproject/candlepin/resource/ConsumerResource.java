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

import java.io.File;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.dto.CertificateDto;
import org.fedoraproject.candlepin.model.CertificateSerialCollection;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;

import com.google.inject.Inject;

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
public class ConsumerResource {

    // @Context
    // private UriInfo uriInfo;

    private static Logger log = Logger.getLogger(ConsumerResource.class);
    private OwnerCurator ownerCurator;
    private Owner owner;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductServiceAdapter productAdapter;
    private PoolCurator epCurator;
    private Entitler entitler;
    private SubscriptionServiceAdapter subAdapter;
    private EntitlementCurator entitlementCurator;
    private IdentityCertServiceAdapter identityCertService;

    private String username;

    /**
     * @param ownerCurator interact with owner
     * @param consumerCurator interact with consumer
     * @param consumerTypeCurator interact with consumerType
     * @param productAdapter
     * @param entitler
     * @param subAdapter
     * @param epCurator
     * @param entitlementCurator
     * @param identityCertService
     * @param request servlet request
     */

    @Inject
    public ConsumerResource(OwnerCurator ownerCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ProductServiceAdapter productAdapter, Entitler entitler,
        SubscriptionServiceAdapter subAdapter, PoolCurator epCurator,
        EntitlementCurator entitlementCurator,
        IdentityCertServiceAdapter identityCertService,
        @Context HttpServletRequest request) {

        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productAdapter = productAdapter;
        this.subAdapter = subAdapter;
        this.entitler = entitler;
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.identityCertService = identityCertService;
        this.username = (String) request.getAttribute("username");
        if (username != null) {
            this.owner = ownerCurator.lookupByName(username);
            if (owner == null) {
                owner = ownerCurator.create(new Owner(username));
            }
        }
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
    public Consumer getConsumer(@PathParam("consumer_uuid") String uuid) {
        Consumer toReturn = consumerCurator.lookupByUuid(uuid);

        if (toReturn != null) {
            return toReturn;
        }

        throw new NotFoundException("Consumer with UUID '" + uuid +
            "' could not be found");
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
        Owner owner = ownerCurator.findAll().get(0); // TODO: actually get
        // current owner
        Consumer consumer = new Consumer();

        log.debug("Got consumerTypeLabel of: " + in.getType().getLabel());
        ConsumerType type = consumerTypeCurator.lookupByLabel(in.getType()
            .getLabel());
        log.debug("got metadata: ");
        log.debug(in.getFacts());
        for (String key : in.getFacts().keySet()) {
            log.debug("   " + key + " = " + in.getFact(key));
        }

        if (type == null) {
            throw new BadRequestException("No such consumer type: " +
                in.getType().getLabel());
        }

        try {
            consumer = consumerCurator.create(new Consumer(in, owner, type));

            // TODO: Could use some cleanup.
            ConsumerIdentityCertificate idCert = identityCertService
                .generateIdentityCert(consumer, this.username);
            log.debug("Generated identity cert: " + idCert);
            if (idCert == null) {
                throw new RuntimeException(
                    "Error generating identity certificate.");
            }

            return consumer;
        }
        catch (Exception e) {
            log.error("Problem creating consumer:", e);
            throw new BadRequestException(e.getMessage());
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
    public void deleteConsumer(@PathParam("consumer_uuid") String uuid) {
        log.debug("deleteing  consumer_uuid" + uuid);
        try {
            Consumer toDelete = verifyAndLookupConsumer(uuid);
            if (toDelete == null) {
                return;
            }
            identityCertService.deleteIdentityCert(toDelete);

            consumerCurator.delete(toDelete);
        }
        catch (RuntimeException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    // /**
    // * Returns the ConsumerInfo for the given Consumer.
    // *
    // * @return the ConsumerInfo for the given Consumer.
    // */
    // @GET
    // @Path("/info")
    // @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    // // TODO: What consumer?
    // public ConsumerFacts getInfo() {
    // ConsumerFacts ci = new ConsumerFacts();
    // // ci.setType(new ConsumerType("system"));
    // ci.setConsumer(null);
    // // Map<String,String> m = new HashMap<String,String>();
    // // m.put("cpu", "i386");
    // // m.put("hey", "biteme");
    // // ci.setMetadata(m);
    // ci.setFact("cpu", "i386");
    // ci.setFact("hey", "foobar");
    // return ci;
    // }

    /**
     * removes the product whose id matches pid, from the consumer, cid.
     * 
     * @param cid
     *            Consumer ID to affect
     * @param pid
     *            Product ID to remove from Consumer.
     */
    // @DELETE @Path("{cid}/products/{pid}")
    // public void delete(@PathParam("cid") String cid,
    // @PathParam("pid") String pid) {
    // System.out.println("cid " + cid + " pid = " + pid);
    // Consumer c = (Consumer) ObjectFactory.get().lookupByUUID(Consumer.class,
    // cid);
    // if (!c.getConsumedProducts().remove(pid)) {
    // log.error("no product " + pid + " found.");
    // }
    // }

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
    @Path("{cid}/products/{pid}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Product getProduct(@PathParam("cid") String cid,
        @PathParam("pid") String pid) {
        return null;
    }

    /**
     * Return the content of the file identified by the given filename.
     * 
     * @param path filename path.
     * @return the content of the file identified by the given filename.
     * @throws Exception if there's a problem loading the file.
     */
    public String getBytesFromFile(String path) throws Exception {
        String fileContents = FileUtils.readFileToString(
            new File(this.getClass().getResource(path).getPath()));
        log.debug(fileContents);
        return fileContents;
    }

    /**
     * Return the client certificate for the given consumer.
     * 
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificates for the given consumer.
     */
    @GET
    @Path("{consumer_uuid}/certificates")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<CertificateDto> getClientCertificates(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("serials") String serials) {

        log.debug("Getting client certificates for consumer: " + consumerUuid);

        if (serials != null) {
            log.debug("Requested serials: " + serials);
            for (String s : serials.split(",")) {
                log.debug("   " + s);
            }
        }

        List<CertificateDto> allCerts =
            new LinkedList<CertificateDto>();

        // FIXME: make this look the cert from the cert service or whatever
        // Using a static (and unusable) cert for now for demo purposes:
        try {
            String keyBytes = getBytesFromFile("/key.pem");
            String certBytes = getBytesFromFile("/rhel-ap.pem");

            CertificateDto cert = new CertificateDto();
            cert.setSerial(BigInteger.valueOf(1));
            cert.setKey(keyBytes);
            cert.setCert(certBytes);
            allCerts.add(cert);

            CertificateDto cert2 = new CertificateDto();
            cert2.setSerial(BigInteger.valueOf(2));
            cert2.setKey(keyBytes);
            cert2.setCert(certBytes);
            allCerts.add(cert2);

            return allCerts;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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
    public CertificateSerialCollection getClientCertificateSerials(
        @PathParam("consumer_uuid") String consumerUuid) {

        log.debug("Getting client certificate serials for consumer: " +
            consumerUuid);

        CertificateSerialCollection allCerts = new CertificateSerialCollection();

        // FIXME: make this look the cert from the cert service or whatever
        // Using a static (and unusable) cert for now for demo purposes:
        allCerts.addSerial(1);
        allCerts.addSerial(2);

        return allCerts;
    }

    /**
     * Entitles the given Consumer with the given Product.
     * 
     * @param consumerUuid Consumer identifier to be entitled
     * @param productId Product identifying label.
     * @return Entitled object
     */
    private List<Entitlement> bindByProduct(String consumerUuid, String productId,
        Consumer consumer) {

        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        Product p = productAdapter.getProductById(productId);
        if (p == null) {
            throw new BadRequestException("No such product: " + productId);
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
     * @param consumerUuid Consumer identifier.
     * @param registrationToken registration token.
     * @return token
     */
    private List<Entitlement> bindByToken(String registrationToken,
        Consumer consumer) {
        
        List<Subscription> s = subAdapter.getSubscriptionForToken(registrationToken);
        if ((s == null) || (s.isEmpty())) {
            log.debug("token: " + registrationToken);
            throw new BadRequestException("No such token: " + registrationToken);
        }

        List<Product> productList = new LinkedList<Product>();
        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        for (Subscription subscription: s ) {
            Product p = productAdapter.getProductById(subscription.getProductId());
            entitlementList.add(createEntitlement(consumer, p));
            
        }
        return entitlementList;
    }

    private List<Entitlement> bindByPool(Long poolId, Consumer consumer) {
        Pool pool = epCurator.find(poolId);
        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        if (pool == null) {
            throw new BadRequestException("No such entitlement pool: " + poolId);
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
            throw new BadRequestException("Cannot bind by multiple parameters.");
        }

        // Verify consumer exists:
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        if (token != null) {
            return bindByToken(token, consumer);
        }
        if (productId != null) {
            return bindByProduct(consumerUuid, productId, consumer);
        }

        return bindByPool(poolId, consumer);
    }

    private Consumer verifyAndLookupConsumer(String consumerUuid) {
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
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
                throw new BadRequestException("No such product: " + productId);
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
    public void unbindAllOrBySerialNumber(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("serial") String serials) {

        if (serials == null) {
            // FIXME: just a stub, needs CertifcateService (and/or a
            // CertificateCurator) to lookup by serialNumber
            Consumer consumer = verifyAndLookupConsumer(consumerUuid);

            if (consumer == null) {
                throw new NotFoundException("Consumer with ID " + consumerUuid +
                    " could not be found.");
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
    public void unbind(@PathParam("consumer_uuid") String consumerUuid,
        @PathParam("dbid") Long dbid) {

        verifyAndLookupConsumer(consumerUuid);

        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            entitlementCurator.delete(toDelete);
            return;
        }
        throw new NotFoundException("Entitlement with ID '" + dbid +
            "' could not be found");
    }

}
