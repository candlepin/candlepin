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

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.pinsetter.tasks.RegenEntitlementCertsJob;
import org.fedoraproject.candlepin.util.Util;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;


/**
 * REST api gateway for the User object.
 */
@Path("/entitlements")
public class EntitlementResource {
    private final ConsumerCurator consumerCurator;
    private PoolManager poolManager;
    private final EntitlementCurator entitlementCurator;
    private I18n i18n;
    
    //private static Logger log = Logger.getLogger(EntitlementResource.class);

    @Inject
    public EntitlementResource(EntitlementCurator entitlementCurator,
            ConsumerCurator consumerCurator,
            I18n i18n) {
        
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
    }
    
    

    @SuppressWarnings("null")
    private void verifyExistence(Object o, String id) {
        if (o == null) {
            throw new RuntimeException(o.getClass().getName() + " with ID: [" + 
                    id + "] not found");
        }
    }

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
        
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getProductId().equals(productId)) {
                return e;
            }
        }
        
        throw new NotFoundException(
            i18n.tr("Consumer: {0} has no entitlement for product {1}",
                consumerUuid, productId));
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
    
   
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Entitlement> listAllForConsumer(
        @QueryParam("consumer") String consumerUuid) {

        if (consumerUuid != null) {

            Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
            if (consumer == null) {
                throw new BadRequestException(
                    i18n.tr("No such consumer: {0}", consumerUuid));
            }

            return entitlementCurator.listByConsumer(consumer);
        }

        return entitlementCurator.listAll();
    }

    /**
     * Return the entitlement for the given id.
     * @param dbid entitlement id.
     * @return the entitlement for the given id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{dbid}")
    @AllowRoles(roles = {Role.CONSUMER, Role.OWNER_ADMIN})
    public Entitlement getEntitlement(@PathParam("dbid") Long dbid) {
        Entitlement toReturn = entitlementCurator.find(dbid);
        if (toReturn != null) {
            return toReturn;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID '{0}' could not be found", dbid));
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
            poolManager.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID '{0}' could not be found", dbid));
    }
    
    
    @PUT
    @Path("product/{product_id}")
    @AllowRoles(roles = {Role.OWNER_ADMIN})
    public JobDetail regenerateEntitlementCertificatesForProduct(
            @PathParam("product_id") String productId) {
        JobDetail detail = new JobDetail("regen_entitlement_cert_of_prod" +
            Util.generateUUID(), RegenEntitlementCertsJob.class);
        JobDataMap map = new JobDataMap();
        map.put(RegenEntitlementCertsJob.PROD_ID, productId);
        detail.setJobDataMap(map);
        return detail;
    }
    
}
