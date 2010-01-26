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

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.resource.cert.CertGenerator;

import com.google.inject.Inject;


/**
 * REST api gateway for the User object.
 */
@Path("/entitlement")
public class EntitlementResource {
    
    private EntitlementPoolCurator epCurator;
    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ProductCurator productCurator;
    private Entitler entitler;
    private EntitlementCurator entitlementCurator;
    
    private DateSource dateSource;
    private static Logger log = Logger.getLogger(EntitlementResource.class);

    @Inject
    public EntitlementResource(EntitlementPoolCurator epCurator, 
            EntitlementCurator entitlementCurator,
            OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
            ProductCurator productCurator, DateSource dateSource, Entitler entitler) {
        
        this.epCurator = epCurator;
        this.entitlementCurator = entitlementCurator;
        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.productCurator = productCurator;
        this.dateSource = dateSource;
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
     * @param c Consumer to be entitled
     * @param p The Product
     * @return Entitled object
     */
    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("consumer/{consumer_uuid}/product/{product_label}")
    public String entitle(@PathParam("consumer_uuid") String consumerUuid, 
            @PathParam("product_label") String productLabel) {
        
        Owner owner = ownerCurator.findAll().get(0); // TODO: actually get current user's owner
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new BadRequestException("No such consumer: " + consumerUuid);
        }
        
        Product p = productCurator.lookupByLabel(productLabel);
        if (p == null) {
            throw new BadRequestException("No such product: " + productLabel);
        }
        
        // Attempt to create an entitlement:
        Entitlement e = entitler.createEntitlement(owner, consumer, p);
        // TODO: Probably need to get the validation result out somehow.
        // TODO: return 409?
        if (e == null) {
            throw new BadRequestException("Entitlement refused.");
        }
        
        return CertGenerator.getCertString(); 
    }

    /**
     * Check to see if a given Consumer is entitled to given Product
     * @param consumerUuid consumerUuid to check if entitled or not
     * @param productId productUuid to check if entitled or not
     * @return boolean if entitled or not
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/consumer/{consumer_uuid}/product/{product_label}")
    public boolean hasEntitlement(@PathParam("consumer_uuid") String consumerUuid, 
            @PathParam("product_label") String productLabel) {
        
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        verifyExistence(consumer, consumerUuid);
        
        Product product = productCurator.lookupByLabel(productLabel);
        verifyExistence(product, productLabel);
            
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getProduct().equals(product)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Match/List the available entitlements for a given Consumer.  Right now
     * this returns ALL entitlements because we haven't built any filtering logic.
     * @param consumerId Unique id of Consumer
     * @return List<Entitlement> of applicable 
     */
    // TODO: right now returns *all* available entitlement pools
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/consumer/{consumer_uuid}")
    public List<EntitlementPool> listAvailableEntitlements(
        @PathParam("consumer_uuid") Long consumerUuid) {

        return epCurator.findAll();
        
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
    
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{dbid}")
    public Entitlement getEntitlement(@PathParam("dbid") Long dbid) {
        Entitlement toReturn = entitlementCurator.find(dbid);
        if (toReturn != null) {
            return toReturn;
        }
        
        throw new NotFoundException("Entitlement with ID '" + dbid + "' could not be found");
    }

}
