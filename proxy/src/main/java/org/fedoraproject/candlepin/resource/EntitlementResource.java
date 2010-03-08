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
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;


/**
 * REST api gateway for the User object.
 */
@Path("/entitlements")
public class EntitlementResource {
    
    private PoolCurator epCurator;
    private ConsumerCurator consumerCurator;
    private ProductServiceAdapter prodAdapter;
    private SubscriptionServiceAdapter subAdapter; 
    private Entitler entitler;
    private EntitlementCurator entitlementCurator;
    
    private static Logger log = Logger.getLogger(EntitlementResource.class);

    @Inject
    public EntitlementResource(PoolCurator epCurator, 
            EntitlementCurator entitlementCurator,
            ConsumerCurator consumerCurator,
            ProductServiceAdapter prodAdapter, SubscriptionServiceAdapter subAdapter, 
            Entitler entitler) {
        
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.prodAdapter = prodAdapter;
        this.subAdapter = subAdapter;
        this.entitler = entitler;
    }
    
    

    private void verifyExistence(Object o, String id) {
        if (o == null) {
            throw new RuntimeException(o.getClass().getName() + " with ID: [" + 
                    id + "] not found");
        }
    }
    
    // Commenting this out, unused, questionable if this is even possible, more likely
    // client tools would determine what product is installed. -- dgoodwin
//    /**
//     *  Entitles the given Consumer with best fit Product.
//     *
//     *  @param consumerUuid Consumer identifier to be entitled
//     *  @return Entitlend object
//     */
//    @POST
//    @Consumes({ MediaType.APPLICATION_JSON })
//    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
//    @Path("consumer/{consumer_uuid}/")
//    public String entitle(@PathParam("consumer_uuid") String consumerUuid) {
//
//        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
//
//        // TODO: this is doing a NO-OP. Can we determine what products a consumer has
//        // installed or should this be the client tools responsibility?
//
//        // FIXME: this is just a hardcoded cert...
//        return CertGenerator.genCert().toString();
//    }

    /**
     * Check to see if a given Consumer is entitled to given Product
     * @param consumerUuid consumerUuid to check if entitled or not
     * @param productId productLabel to check if entitled or not
     * @return boolean if entitled or not
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("consumer/{consumer_uuid}/product/{product_id}")
    public Entitlement hasEntitlement(@PathParam("consumer_uuid") String consumerUuid, 
            @PathParam("product_id") String productId) {
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        verifyExistence(consumer, consumerUuid);
        
        Product product = prodAdapter.getProductById(productId);
        verifyExistence(product, productId);
            
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getProductId().equals(product.getId())) {
                return e;
            }
        }
        
        throw new NotFoundException(
            "Consumer: " + consumerUuid + " has no entitlement for product " +
            productId);
    }
    
//    /**
//     * Match/List the available entitlements for a given Consumer. Right
//     * now this returns ALL entitlements because we haven't built any
//     * filtering logic.
//     * @param consumerUuid Unique id of Consumer
//     * @return List<Entitlement> of applicable
//     */
//    // TODO: right now returns *all* available entitlement pools
//    @GET
//    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
//    @Path("/consumer/{consumer_uuid}")
//    public List<EntitlementPool> listAvailableEntitlements(
//        @PathParam("consumer_uuid") Long consumerUuid) {
//
////        log.debug("consumerCurator: " + consumerCurator.toString());
////        log.debug("epCurator: " + epCurator.toString());
//        Consumer consumer = consumerCurator.find(consumerUuid);
////        log.debug("consumer: " + consumer.toString());
//        return epCurator.listByConsumer(consumer);
////        return epCurator.findAll();
//
////        Consumer c = consumerCurator.find(consumerId);
////        List<EntitlementPool> entitlementPools = epCurator.findAll();
////        List<EntitlementPool> retval = new ArrayList<EntitlementPool>();
////        EntitlementMatcher matcher = new EntitlementMatcher();
////        for (EntitlementPool ep : entitlementPools) {
////            boolean add = false;
////            System.out.println("max = " + ep.getMaxMembers());
////            System.out.println("cur = " + ep.getCurrentMembers());
////            if (ep.getMaxMembers() > ep.getCurrentMembers()) {
////                add = true;
////            }
////            if (matcher.isCompatible(c, ep.getProduct())) {
////                add = true;
////            }
////            if (add) {
////                retval.add(ep);
////            }
////        }
////        return retval;
//    }

    
    // TODO:
    // EntitlementLib.UnentitleProduct(Consumer, Entitlement) 
    
   
    // TODO: Change to query param
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Entitlement> listAllForConsumer(
        @QueryParam("consumer") String consumerUuid) {

        if (consumerUuid != null) {

            Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
            if (consumer == null) {
                throw new BadRequestException("No such consumer: " + consumerUuid);
            }

            return entitlementCurator.listByConsumer(consumer);
        }

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
     * Remove an entitlement by ID.
     *
     * @param dbid the entitlement to delete.
     */
    @DELETE
    @Path("/{dbid}")
    public void unbind(@PathParam("dbid") Long dbid) {

        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            entitlementCurator.delete(toDelete);
            return;
        }
        throw new NotFoundException(
            "Entitlement with ID '" + dbid + "' could not be found");
    }



}
