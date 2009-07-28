/**
 * Copyright (c) 2008 Red Hat, Inc.
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
package org.fedoraproject.candlepin.api;

import com.sun.jersey.api.representation.Form;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.api.cert.CertGenerator;
import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Product;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * REST api gateway for the User object.
 */
@Path("/entitlement")
public class EntitlementApi extends BaseApi {

    /**
     * Logger for this class
     */
    private static final Logger log = Logger.getLogger(EntitlementApi.class);

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class getApiClass() {
        return Entitlement.class;
    }
 

    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Path("/entitle")
    public Object entitle(Form form) {
        String consumerUuid = form.getFirst("consumer_uuid");
        String productUuid = form.getFirst("product_uuid");
        log.debug("UUID: " + consumerUuid);
        Consumer c = (Consumer) ObjectFactory.get().lookupByUUID(Consumer.class, 
                consumerUuid);
        if (c == null) {
            throw new RuntimeException("Consumer with UUID: [" + 
                    consumerUuid + "] not found");
        }
        Product p = (Product) ObjectFactory.get().lookupByUUID(Product.class, productUuid);
        if (p == null) {
            throw new RuntimeException("Product with UUID: [" + 
                    productUuid + "] not found");
        }

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
                    throw new RuntimeException("Entitlement expired on: " + ep.getEndDate());
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

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Entitlement> list() {
        List<Object> u = ObjectFactory.get().listObjectsByClass(getApiClass());
        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        for (Object o : u) {
            entitlements.add((Entitlement) o);
        }
        return entitlements;
    }

}
