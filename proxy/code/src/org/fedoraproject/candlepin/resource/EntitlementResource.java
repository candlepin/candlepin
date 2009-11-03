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

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.cert.CertGenerator;

import com.sun.jersey.api.representation.Form;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
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
public class EntitlementResource extends BaseResource {

    /** default ctor */
    public EntitlementResource() {
        super(Entitlement.class);
    }

    /** Logger for this class */
    private static Logger log = Logger.getLogger(EntitlementResource.class);

    private Object validateObjectInput(Form form, String fieldName, Class clazz) {
        String uuid = form.getFirst(fieldName);
        log.debug("UUID: " + uuid);
        Object o = ObjectFactory.get().lookupByUUID(clazz, uuid);
        if (o == null) {
            throw new RuntimeException(clazz.getName() + " with UUID: [" + 
                    uuid + "] not found");
        }
        return o;
    }
    
    private Object validateObjectInput(String uuid, Class clazz) {
        log.debug("UUID: " + uuid);
        Object o = ObjectFactory.get().lookupByUUID(clazz, uuid);
        if (o == null) {
            throw new RuntimeException(clazz.getName() + " with UUID: [" + 
                    uuid + "] not found");
        }
        return o;
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
        System.out.println("Consumer uuid: " + c.getUuid());
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
    public Object entitle(Consumer c, Product p) {

        // Possibly refactor this down into some 'business layer'
        // Check for a matching EntitlementPool
        List pools = ObjectFactory.get().listObjectsByClass(EntitlementPool.class);
        for (int i = 0; i < pools.size(); i++) {
            EntitlementPool ep = (EntitlementPool) pools.get(i);
            if (ep.getProduct().equals(p)) {
                log.debug("We found a matching EP");
                // Check membership availability
                if (ep.getCurrentMembers() >= ep.getMaxMembers()) {
                    throw new RuntimeException("Not enough entitlements");
                }
                // Check expiration
                Date today = new Date();
                if (ep.getEndDate().before(today)) {
                    throw new RuntimeException("Entitlement expired on: " +
                        ep.getEndDate());
                }
                
                Entitlement e = new Entitlement(BaseModel.generateUUID());
                e.setPool(ep);
                e.setStartDate(new Date());
                ep.bumpCurrentMembers();
                c.addConsumedProduct(p);
                c.addEntitlement(e);
                e.setOwner(ep.getOwner());
                
                
                ObjectFactory.get().store(e);
                ObjectFactory.get().store(ep);
                
                return CertGenerator.getCertString(); 
            }
        }
        return null;
    }

    /**
     * Check to see if a given Consumer is entitled to given Product
     * @param consumerUuid consumerUuid to check if entitled or not
     * @param productUuid productUuid to check if entitled or not
     * @return boolean if entitled or not
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/has")
    public boolean hasEntitlement(@PathParam("consumer_uuid") String consumerUuid, 
            @PathParam("product_uuid") String productUuid) {
        Consumer c = (Consumer) validateObjectInput(consumerUuid, Consumer.class);
        Product p = (Product) validateObjectInput(productUuid, Product.class);
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
     * @param uuid consumerUuid
     * @return List<Entitlement> of applicable 
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/listavailable")
    public List<EntitlementPool> listAvailableEntitlements(
        @PathParam("uuid") String uuid) {

        Consumer c = (Consumer) validateObjectInput(uuid, Consumer.class);
        List<EntitlementPool> entitlementPools = new EntitlementPoolResource().list();
        List<EntitlementPool> retval = new ArrayList<EntitlementPool>();
        EntitlementMatcher matcher = new EntitlementMatcher();
        for (EntitlementPool ep : entitlementPools) {
            boolean add = false;
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
