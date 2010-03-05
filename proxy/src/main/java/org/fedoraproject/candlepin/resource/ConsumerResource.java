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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
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
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.ClientCertificate;
import org.fedoraproject.candlepin.model.ClientCertificateSerial;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerFacts;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificateCurator;
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
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
public class ConsumerResource {
    
    @Context 
    private UriInfo uriInfo;
    
    private static Logger log = Logger.getLogger(ConsumerResource.class);
    private OwnerCurator ownerCurator;
    private Owner owner;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerIdentityCertificateCurator consumerIdCertCurator;
    private ProductServiceAdapter productAdapter;
    private PoolCurator epCurator;
    private Entitler entitler;
    private SubscriptionServiceAdapter subAdapter; 
    private EntitlementCurator entitlementCurator;


    private String username;

    /**
     * @param ownerCurator interact with owner
     * @param consumerCurator interact with consumer
     * @param consumerTypeCurator interact wtih consumerType
     * @param consumerIdCertCurator interact wtih consumerIdCert
     * @param request servlet request
     */
    @Inject
    public ConsumerResource(OwnerCurator ownerCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ConsumerIdentityCertificateCurator consumerIdCertCurator,
        ProductServiceAdapter productAdapter,
        Entitler entitler,
        SubscriptionServiceAdapter subAdapter,
        PoolCurator epCurator,
        EntitlementCurator entitlementCurator,
        @Context HttpServletRequest request) {

        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.consumerIdCertCurator = consumerIdCertCurator;
        this.productAdapter = productAdapter;
        this.subAdapter = subAdapter;
        this.entitler = entitler;
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;

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
     * @return list of available consumers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Consumer> list() {
        return consumerCurator.findAll();
    }
   
    /**
     * Return the consumer identified by the given uuid.
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

        throw new NotFoundException(
            "Consumer with UUID '" + uuid + "' could not be found"); 
    }
    
    /**
     * Create a Consumer
     * @param in Consumer metadata encapsulated in a ConsumerInfo.
     * @return newly created Consumer
     * 
     *  We are calling this "registerConsumer" in the api discussions
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Consumer create(Consumer in) {
        // API:registerConsumer
        Owner owner = ownerCurator.findAll().get(0); // TODO: actually get current owner
        Consumer consumer = new Consumer();
        
        log.debug("Got consumerTypeLabel of: " + in.getType().getLabel());
        ConsumerType type = consumerTypeCurator.lookupByLabel(in.getType().getLabel());
        log.debug("got metadata: ");
        log.debug(in.getFacts().getMetadata());
        for (String key : in.getFacts().getMetadata().keySet()) {
            log.debug("   " + key + " = " + in.getFact(key));
        }
        
        if (type == null) {
            throw new BadRequestException(
                "No such consumer type: " + in.getType().getLabel());
        }

        try {
            consumer = consumerCurator.create(Consumer.createFromConsumer(in, owner, type));
            
            ConsumerIdentityCertificate idCert = consumerIdCertCurator.getCert();
            consumer.setIdCert(idCert);
            return consumer;
            
        }
        catch (RuntimeException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
   
    /**
     * delete the consumer.
     * @param uuid uuid of the consumer to delete.
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    public void deleteConsumer(@PathParam("consumer_uuid") String uuid) {
        log.debug("deleteing  consumer_uuid" + uuid);
        try {
            consumerCurator.delete(consumerCurator.lookupByUuid(uuid));
        }
        catch (RuntimeException e) {
            throw new NotFoundException(e.getMessage());
        }
    }
    
    /**
     * Returns the ConsumerInfo for the given Consumer.
     * @return the ConsumerInfo for the given Consumer.
     */
    @GET @Path("/info")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    // TODO: What consumer?
    public ConsumerFacts getInfo() {
        ConsumerFacts ci = new ConsumerFacts();
//        ci.setType(new ConsumerType("system"));
        ci.setConsumer(null);
//        Map<String,String> m = new HashMap<String,String>();
//        m.put("cpu", "i386");
//        m.put("hey", "biteme");
//        ci.setMetadata(m);
        ci.setFact("cpu", "i386");
        ci.setFact("hey", "foobar");
        return ci;
    }
    
    /**
     * removes the product whose id matches pid, from the consumer, cid.
     * @param cid Consumer ID to affect
     * @param pid Product ID to remove from Consumer.
     */
//    @DELETE @Path("{cid}/products/{pid}")
//    public void delete(@PathParam("cid") String cid,
//                       @PathParam("pid") String pid) {
//        System.out.println("cid " + cid + " pid = " + pid);
//        Consumer c = (Consumer) ObjectFactory.get().lookupByUUID(Consumer.class, cid);
//        if (!c.getConsumedProducts().remove(pid)) {
//            log.error("no product " + pid + " found.");
//        }
//    }

    /**
     * Returns the product whose id matches pid, from the consumer, cid.
     * @param cid Consumer ID to affect
     * @param pid Product ID to remove from Consumer.
     * @return the product whose id matches pid, from the consumer, cid.
     */
    @GET @Path("{cid}/products/{pid}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Product getProduct(@PathParam("cid") String cid,
                       @PathParam("pid") String pid) {
        return null;
    }

    /**
     * Return the content of the file identified by the given filename.
     * @param path filename path.
     * @return the content of the file identified by the given filename.
     * @throws Exception if there's a problem loading the file.
     */
    public byte[] getBytesFromFile(String path) throws Exception {
        InputStream is = this.getClass().getResource(path).openStream();
        byte [] bytes = null;
        try {
            bytes = IOUtils.toByteArray(is);
        }
        finally {
            IOUtils.closeQuietly(is);
        }
        return bytes;
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
    public List<ClientCertificate> getClientCertificates(
        @PathParam("consumer_uuid") String consumerUuid, 
        @QueryParam("serials") String serials) {
        

        log.debug("Getting client certificates for consumer: " + consumerUuid);
        
        if (serials != null) {
            log.debug("Requested serials: " + serials);
            for (String s : serials.split(",")) {
                log.debug("   " + s);
            }
        }

        List<ClientCertificate> allCerts = new LinkedList<ClientCertificate>();
        
        //FIXME: make this look the cert from the cert service or whatever
        // Using a static (and unusable) cert for now for demo purposes:
        try {
            byte[] bytes = getBytesFromFile("/testcert-cert.p12");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream stream = new ObjectOutputStream(baos);
            stream.write(bytes);
            stream.flush();
            stream.close();

            // FIXME : these won't be a pkcs12 bundle
            
            // FIXME: This isn't quite right even for demo purposes, we're taking an
            // entire PKCS12 bundle and cramming it into just the cert portion,
            // no key is set.
            ClientCertificate cert = new ClientCertificate();
            cert.setSerial("SERIAL001");
            cert.setKey(baos.toByteArray());
            cert.setCert(baos.toByteArray());

            allCerts.add(cert);

            ClientCertificate cert2 = new ClientCertificate();
            cert2.setSerial("SERIAL002");
            cert2.setKey(baos.toByteArray());
            cert2.setCert(baos.toByteArray());
            allCerts.add(cert2);
            
            return allCerts;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
   
    /**
     * Return the client certificate metadata for the given consumer.
     * 
     * This is a small subset of data clients can use to determine which certificates
     * they need to update/fetch.
     * 
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificate metadata for the given consumer.
     */
    @GET
    @Path("{consumer_uuid}/certificates/serials")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<ClientCertificateSerial> getClientCertificateSerials(
        @PathParam("consumer_uuid") String consumerUuid) {

        log.debug("Getting client certificate serials for consumer: " + consumerUuid);

        List<ClientCertificateSerial> allCerts =
            new LinkedList<ClientCertificateSerial>();
        
        //FIXME: make this look the cert from the cert service or whatever
        // Using a static (and unusable) cert for now for demo purposes:
        try {
            ClientCertificateSerial cert = new ClientCertificateSerial();
            cert.setSerial("SERIAL001");

            allCerts.add(cert);

            ClientCertificateSerial cert2 = new ClientCertificateSerial();
            cert2.setSerial("SERIAL002");
            allCerts.add(cert2);
            log.debug("Returning metadata: " + allCerts.size());
            for (ClientCertificateSerial md : allCerts) {
                log.debug("   " + md.getSerial());
            }
            
            return allCerts;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
   
    /**
     * Entitles the given Consumer with the given Product.
     * @param consumerUuid Consumer identifier to be entitled
     * @param productId Product identifying label.
     * @return Entitled object
     */
//    @POST
//    @Consumes({ MediaType.APPLICATION_JSON })
//    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
//    @Path("/{consumer_uuid}/entitlements")
    public Entitlement entitleByProduct(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("product") String productId) {
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
        }
        
        Product p = productAdapter.getProductById(productId);
        if (p == null) {
            throw new BadRequestException("No such product: " + productId);
        }
        
        // Attempt to create an entitlement:
        Entitlement e = entitler.entitle(consumer, p);
        // TODO: Probably need to get the validation result out somehow.
        // TODO: return 409?
        if (e == null) {
            throw new BadRequestException("Entitlement refused.");
        }
        log.debug("Entitlement: " + e);
        return e;
    }

    /**
     * Grants entitlements based on a registration token.
     * 
     * @param consumerUuid Consumer identifier.
     * @param registrationToken registration token.
     * @return token
     */
//    @POST
//    @Consumes({ MediaType.APPLICATION_JSON })
//    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
//    @Path("consumer/{consumer_uuid}/entitlements")
    public Entitlement entitleToken(
            @PathParam("consumer_uuid") String consumerUuid, 
            @QueryParam("token") String registrationToken) {
        
        //FIXME: this is just a stub, need SubscriptionService to look it up
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        
        //FIXME: getSubscriptionForToken is a stub, always "works"
        Subscription s = subAdapter.getSubscriptionForToken(registrationToken);
        if (s == null) {
            throw new BadRequestException("No such token: " + registrationToken);
        }

        Product p = productAdapter.getProductById(s.getProductId());

        Entitlement e = entitler.entitle(consumer, p);
        // return it
        
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
        }

        return e;
    }

    /**
     * Request an entitlement from a specific pool.
     *
     * @param consumerUuid Consumer identifier to be entitled
     * @param poolId Entitlement pool id.
     * @return boolean
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/{consumer_uuid}/entitlements")
    public Entitlement entitleByPool(
            @PathParam("consumer_uuid") String consumerUuid,
            @QueryParam("pool") Long poolId, 
            @QueryParam("token") String token, 
            @QueryParam("product") String productId) {
        
        // TODO: Check that only one query param was set:
        
        if (token != null) {
            return entitleToken(consumerUuid, token);
        }
        if (productId != null) {
            return entitleByProduct(consumerUuid, productId);
        }

        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
        }

        Pool pool = epCurator.find(poolId);
        if (pool == null) {
            throw new BadRequestException("No such entitlement pool: " + poolId);
        }

        // Attempt to create an entitlement:
        Entitlement e = entitler.entitle(consumer, pool);
        // TODO: Probably need to get the validation result out somehow.
        // TODO: return 409?
        if (e == null) {
            throw new BadRequestException("Entitlement refused.");
        }

        return e;
    }
    
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/{consumer_uuid}/entitlements")
    public List<Entitlement> listEntitlements(
        @PathParam("consumer_uuid") String consumerUuid) {
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
        }

        return entitlementCurator.listByConsumer(consumer);
    }

}
