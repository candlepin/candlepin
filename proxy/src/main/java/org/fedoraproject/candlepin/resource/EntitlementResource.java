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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.resource.cert.CertGenerator;
import org.fedoraproject.candlepin.util.EntityManagerUtil;

import com.google.inject.Inject;
import com.sun.jersey.api.representation.Form;


/**
 * REST api gateway for the User object.
 */
@Path("/entitlement")
public class EntitlementResource extends BaseResource {
    
    private EntitlementPoolCurator epCurator;
    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ProductCurator productCurator;
    private static Logger log = Logger.getLogger(EntitlementResource.class);

    @Inject
    public EntitlementResource(EntitlementPoolCurator epCurator, 
            OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
            ProductCurator productCurator) {
        super(Entitlement.class);
        this.epCurator = epCurator;
        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.productCurator = productCurator;
    }

    private Object validateObjectInput(String uuid, Class clazz) {
        Object o = ObjectFactory.get().lookupByUUID(clazz, uuid);
        if (o == null) {
            throw new RuntimeException(clazz.getName() + " with UUID: [" + 
                    uuid + "] not found");
        }
        return o;
    }

    private Object newValidateObjectInput(EntityManager em, Long id, Class clazz) {
        Query q = em.createQuery("from " + clazz.getName() + " o where o.id = :id");
        q.setParameter("id", id);
//        if (o == null) {
//            throw new RuntimeException(clazz.getName() + " with UUID: [" + 
//                    uuid + "] not found");
//        }
        Object result = q.getSingleResult();
        return result;
    }

    /**
     * Test method
     * @param c consumer test
     * @return test object
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/foo")
    public Object foo(Consumer c) {
        return "return value";
    }

    /**
     * Entitles the given Consumer with the given Product.
     * @param c Consumer to be entitled
     * @param p The Product
     * @return Entitled object
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/entitle")
    public Object entitle(@FormParam("consumer_uuid") String consumerUuid, 
            @FormParam("product_id") String productLabel) {
        
        // Lookup the entitlement pool for this product.
        Owner owner = getCurrentUsersOwner(ownerCurator);
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {
            throw new RuntimeException("No such consumer: " + consumerUuid);
        }
        
        Product p = productCurator.lookupByLabel(productLabel);
        if (p == null) {
            throw new RuntimeException("No such product: " + productLabel);
        }
        
        EntitlementPool ePool = epCurator.lookupByOwnerAndProduct(owner, p);
        if (ePool == null) {
            throw new RuntimeException("No entitlements for product: " + p.getName());
        }
        
        if (!ePool.hasAvailableEntitlements()) {
            throw new RuntimeException("Not enough entitlements");
        }
        
        
        // Check expiration:
        Date today = new Date();
        if (ePool.getEndDate().before(today)) {
            throw new RuntimeException("Entitlements for " + p.getName() + 
                    " expired on: " + ePool.getEndDate());
        }
        
        // Actually create an entitlement:
        Entitlement e = epCurator.createEntitlement(ePool, consumer); 
        
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
    @Path("/has")
    public boolean hasEntitlement(@PathParam("consumer_uuid") String consumerUuid, 
            @PathParam("product_id") String productId) {
        Consumer c = (Consumer) validateObjectInput(consumerUuid, Consumer.class);
        Product p = (Product) validateObjectInput(productId, Product.class);
        for (Entitlement e : c.getEntitlements()) {
            if (e.getProduct().equals(p)) {
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
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/listavailable")
    public List<EntitlementPool> listAvailableEntitlements(
        @PathParam("consumerId") Long consumerId) {
        EntityManager em = EntityManagerUtil.createEntityManager();

        Consumer c = (Consumer) newValidateObjectInput(em, consumerId, Consumer.class);
        List<EntitlementPool> entitlementPools = new EntitlementPoolResource().list();
        List<EntitlementPool> retval = new ArrayList<EntitlementPool>();
        EntitlementMatcher matcher = new EntitlementMatcher();
        for (EntitlementPool ep : entitlementPools) {
            boolean add = false;
            System.out.println("max = " + ep.getMaxMembers());
            System.out.println("cur = " + ep.getCurrentMembers());
            if (ep.getMaxMembers() > ep.getCurrentMembers()) {
                add = true;
            }
            if (matcher.isCompatible(c, ep.getProduct())) {
                add = true;
            }
            if (add) {
                retval.add(ep);
            }
        }
        return retval;
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
        List<Object> u = ObjectFactory.get().listObjectsByClass(getApiClass());
        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        for (Object o : u) {
            entitlements.add((Entitlement) o);
        }
        return entitlements;
    }

}
