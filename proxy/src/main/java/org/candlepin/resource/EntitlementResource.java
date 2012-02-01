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
package org.candlepin.resource;

import org.candlepin.auth.interceptor.Verify;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.pinsetter.tasks.RegenProductEntitlementCertsJob;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
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
    private final ConsumerCurator consumerCurator;
    private PoolManager poolManager;
    private final EntitlementCurator entitlementCurator;
    private I18n i18n;
    private ProductServiceAdapter prodAdapter;

    @Inject
    public EntitlementResource(ProductServiceAdapter prodAdapter,
            EntitlementCurator entitlementCurator,
            ConsumerCurator consumerCurator,
            PoolManager poolManager,
            I18n i18n) {

        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
        this.prodAdapter = prodAdapter;
        this.poolManager = poolManager;
    }

    private void verifyExistence(Object o, String id) {
        if (o == null) {
            throw new RuntimeException("object with ID: [" + id + "] not found");
        }
    }

    /**
     * Check to see if a given Consumer is entitled to given Product
     * @param consumerUuid consumerUuid to check if entitled or not
     * @param productId productLabel to check if entitled or not
     * @return boolean if entitled or not
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("consumer/{consumer_uuid}/product/{product_id}")
    public Entitlement hasEntitlement(@PathParam("consumer_uuid") String consumerUuid,
            @PathParam("product_id") String productId) {

        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
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

    /**
     * @return a list of Entitlement objects
     * @httpcode 400
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Entitlement> listAllForConsumer(
        @QueryParam("consumer") String consumerUuid) {

        if (consumerUuid != null) {

            Consumer consumer = consumerCurator.findByUuid(consumerUuid);
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
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{dbid}")
    public Entitlement getEntitlement(
        @PathParam("dbid") @Verify(Entitlement.class) String dbid) {
        Entitlement toReturn = entitlementCurator.find(dbid);
        if (toReturn != null) {
            return toReturn;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID ''{0}'' could not be found", dbid));
    }

    /**
     * Remove an entitlement by ID.
     *
     * @param dbid the entitlement to delete.
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Path("/{dbid}")
    public void unbind(@PathParam("dbid") String dbid) {
        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            poolManager.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID ''{0}'' could not be found", dbid));
    }

    /**
     * @return a JobDetail
     * @httpcode 202
     */
    @PUT
    @Path("product/{product_id}")
    public JobDetail regenerateEntitlementCertificatesForProduct(
            @PathParam("product_id") String productId) {
        prodAdapter.purgeCache();
        JobDetail detail = new JobDetail("regen_entitlement_cert_of_prod" +
            Util.generateUUID(), RegenProductEntitlementCertsJob.class);
        JobDataMap map = new JobDataMap();
        map.put(RegenProductEntitlementCertsJob.PROD_ID, productId);
        detail.setJobDataMap(map);
        return detail;
    }

}
