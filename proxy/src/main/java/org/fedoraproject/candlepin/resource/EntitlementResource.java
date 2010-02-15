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

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.ClientCertificateStatus;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.cert.CertGenerator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * REST api gateway for the User object.
 */
@Path("/entitlement")
public class EntitlementResource {
    
    private EntitlementPoolCurator epCurator;
    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ProductServiceAdapter prodAdapter;
    private Entitler entitler;
    private EntitlementCurator entitlementCurator;
    
    private static Logger log = Logger.getLogger(EntitlementResource.class);

    /**
     * ctor
     * @param epCurator interact with Entitlement Pools.
     * @param entitlementCurator interact with entitlements.
     * @param ownerCurator interact with owners.
     * @param consumerCurator interact with consumers.
     * @param prodAdapter interact with products.
     * @param entitler This is what actually does the work.
     */
    @Inject
    public EntitlementResource(EntitlementPoolCurator epCurator, 
            EntitlementCurator entitlementCurator,
            OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
            ProductServiceAdapter prodAdapter, Entitler entitler) {
        
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.prodAdapter = prodAdapter;
        this.entitler = entitler;
    }
    
    

    private void verifyExistence(Object o, String id) {
        if (o == null) {
            throw new RuntimeException(o.getClass().getName() + " with ID: [" + 
                    id + "] not found");
        }
    }

    /**
     * Entitles the given Consumer with the given Product.
     * @param consumerUuid Consumer identifier to be entitled
     * @param productLabel Product identifying label.
     * @return Entitled object
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("consumer/{consumer_uuid}/product/{product_label}")
    public String entitle(@PathParam("consumer_uuid") String consumerUuid, 
            @PathParam("product_label") String productLabel) {
        
        // TODO: actually get current user's owner
        Owner owner = ownerCurator.findAll().get(0);
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
        }
        
        Product p = prodAdapter.getProductByLabel(productLabel);
        if (p == null) {
            throw new BadRequestException("No such product: " + productLabel);
        }
        
        // Attempt to create an entitlement:
        Entitlement e = entitler.entitle(owner, consumer, p);
        // TODO: Probably need to get the validation result out somehow.
        // TODO: return 409?
        if (e == null) {
            throw new BadRequestException("Entitlement refused.");
        }
        
        return CertGenerator.getCertString(); 
    }

    /**
     * Entitles the given consumer, and returns the token.
     * @param consumerUuid Consumer identifier.
     * @param registrationToken registration token.
     * @return token
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("consumer/{consumer_uuid}/token/{registration_token}")
    public String entitleToken(@PathParam("consumer_uuid") String consumerUuid,
            @PathParam("registration_token") String registrationToken) {
        
        //FIXME: this is just a stub, need SubscriptionService to look it up
        // TODO: actually get current user's owner
        Owner owner = ownerCurator.findAll().get(0);
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
        }
        
        return "foo";
    }
    
    /**
     * Check to see if a given Consumer is entitled to given Product
     * @param consumerUuid consumerUuid to check if entitled or not
     * @param productLabel productLabel to check if entitled or not
     * @return boolean if entitled or not
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("consumer/{consumer_uuid}/product/{product_label}")
    public Entitlement hasEntitlement(@PathParam("consumer_uuid") String consumerUuid, 
            @PathParam("product_label") String productLabel) {
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        verifyExistence(consumer, consumerUuid);
        
        Product product = prodAdapter.getProductByLabel(productLabel);
        verifyExistence(product, productLabel);
            
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getProductId().equals(product.getId())) {
                return e;
            }
        }
        
        throw new NotFoundException(
            "Consumer: " + consumerUuid + " has no entitlement for product " +
            productLabel);
    }
    
    /**
     * Match/List the available entitlements for a given Consumer. Right
     * now this returns ALL entitlements because we haven't built any
     * filtering logic.
     * @param consumerUuid Unique id of Consumer
     * @return List<Entitlement> of applicable 
     */
    // TODO: right now returns *all* available entitlement pools
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/consumer/{consumer_uuid}")
    public List<EntitlementPool> listAvailableEntitlements(
        @PathParam("consumer_uuid") Long consumerUuid) {

//        log.debug("consumerCurator: " + consumerCurator.toString());
//        log.debug("epCurator: " + epCurator.toString());
        Consumer consumer = consumerCurator.find(consumerUuid);
//        log.debug("consumer: " + consumer.toString());
        return epCurator.listByConsumer(consumer);
//        return epCurator.findAll();
        
//        Consumer c = consumerCurator.find(consumerId);
//        List<EntitlementPool> entitlementPools = epCurator.findAll();
//        List<EntitlementPool> retval = new ArrayList<EntitlementPool>();
//        EntitlementMatcher matcher = new EntitlementMatcher();
//        for (EntitlementPool ep : entitlementPools) {
//            boolean add = false;
//            System.out.println("max = " + ep.getMaxMembers());
//            System.out.println("cur = " + ep.getCurrentMembers());
//            if (ep.getMaxMembers() > ep.getCurrentMembers()) {
//                add = true;
//            }
//            if (matcher.isCompatible(c, ep.getProduct())) {
//                add = true;
//            }
//            if (add) {
//                retval.add(ep);
//            }
//        }
//        return retval;
    }

    
    // TODO:
    // EntitlementLib.UnentitleProduct(Consumer, Entitlement) 
    
   
    /**
     * Return list of Entitlements
     * @return list of Entitlements
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Entitlement> list() {
        return entitlementCurator.findAll();
    }
   
    /**
     * Return the entitlement for the given id.
     * @param dbid entitlement id.
     * @return the entitlement for the given id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{dbid}")
    public Entitlement getEntitlement(@PathParam("dbid") Long dbid) {
        Entitlement toReturn = entitlementCurator.find(dbid);
        if (toReturn != null) {
            return toReturn;
        }
        throw new NotFoundException(
            "Entitlement with ID '" + dbid + "' could not be found");
    }
   
    /**
     * Deletes all entitlements for the consumer whose id matches the given
     * uuid.
     * @param consumerUuid id of the consumer whose entitlements are to be
     * deleted.
     * @return certificate and status.
     */
    @DELETE
    @Path("consumer/{consumer_uuid}/")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ClientCertificateStatus deleteAllEntitlements(
            @PathParam("consumer_uuid") String consumerUuid) {

        //FIXME: stub
        // Find all entitlements for this consumer id
        // get all the associated clientCerts
        // new list of ClientCertificateStatus
        //   add all the revoked certs to it, with their new
        //      serial numbers, and "REVOKED" status
        // 
        // delete all the Entitlements 
        // return the clientCertificateStatus list
        return new ClientCertificateStatus();
    }
   
    /**
     * Removes the entitlements associated with the given serial number.
     * @param consumerUuid Unique id for the Consumer.
     * @param subscriptionNumberArgs comma seperated list of subscription
     * numbers.
     */
    @DELETE
    @Path("consumer/{consumer_uuid}/{subscription_numbers}")
    public void deleteEntitlementsBySerialNumber(
            @PathParam("consumer_uuid") String consumerUuid,
            @PathParam("subscription_numbers") String subscriptionNumberArgs) {

        //FIXME: just a stub, needs CertifcateService (and/or a
        //CertificateCurator) to lookup by serialNumber
        
        
        // Need to parse off the value of subscriptionNumberArgs, probably
        // use comma seperated see IntergerList in sparklines example in
        // jersey examples find all entitlements for this consumer and
        // subscription numbers delete all of those (and/or return them to
        // entitlement pool)
        
    }
   
    /**
     * Removes the entitlement identified by the given dbid.
     * @param dbid the entitlement to delete.
     */
    @DELETE
    @Path("{dbid}")
    public void deleteEntitlement(@PathParam("dbid") Long dbid) {
        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            entitlementCurator.delete(toDelete);
            return;
        }
        throw new NotFoundException(
            "Entitlement with ID '" + dbid + "' could not be found");
    }

}
