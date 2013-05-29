/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.quartz.JobBuilder.newJob;

import org.candlepin.auth.interceptor.Verify;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.Page;
import org.candlepin.paging.Paginate;
import org.candlepin.pinsetter.tasks.RegenProductEntitlementCertsJob;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * REST api gateway for the User object.
 */
@Path("/entitlements")
public class EntitlementResource {
    private final ConsumerCurator consumerCurator;
    private PoolManager poolManager;
    private final EntitlementCurator entitlementCurator;
    private SubscriptionServiceAdapter subService;
    private I18n i18n;
    private ProductServiceAdapter prodAdapter;
    private Entitler entitler;

    @Inject
    public EntitlementResource(ProductServiceAdapter prodAdapter,
            EntitlementCurator entitlementCurator,
            ConsumerCurator consumerCurator,
            SubscriptionServiceAdapter subService,
            PoolManager poolManager,
            I18n i18n, Entitler entitler) {

        this.entitlementCurator = entitlementCurator;
        this.subService = subService;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
        this.prodAdapter = prodAdapter;
        this.poolManager = poolManager;
        this.entitler = entitler;
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
    @Paginate
    public List<Entitlement> listAllForConsumer(
        @QueryParam("consumer") String consumerUuid,
        @Context PageRequest pageRequest) {

        Page<List<Entitlement>> p;
        if (consumerUuid != null) {

            Consumer consumer = consumerCurator.findByUuid(consumerUuid);
            if (consumer == null) {
                throw new BadRequestException(
                    i18n.tr("No such consumer: {0}", consumerUuid));
            }

            p = entitlementCurator.listByConsumer(consumer, pageRequest);
        }
        else {
            p = entitlementCurator.listAll(pageRequest);
        }

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, p);
        return p.getPageData();
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
        List<Entitlement> tempList = Arrays.asList(toReturn);
        poolManager.regenerateDirtyEntitlements(tempList);
        if (toReturn != null) {
            return toReturn;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID ''{0}'' could not be found.", dbid));
    }

    /**
     * Update entitlement only works for the quantity.
     *
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{entitlement_id}")
    public void updateEntitlement(
        @PathParam("entitlement_id") @Verify(Entitlement.class) String id,
        Entitlement update) {

        // Check that quantity param was set and is not 0:
        if (update.getQuantity() <= 0) {
            throw new BadRequestException(
                i18n.tr("Quantity value must be greater than 0"));
        }

        // Verify entitlement exists:
        Entitlement entitlement = entitlementCurator.find(id);
        if (entitlement != null) {
            // make sure that this will be a change
            if (!entitlement.getQuantity().equals(update.getQuantity())) {
                Consumer consumer = entitlement.getConsumer();
                entitler.adjustEntitlementQuantity(consumer, entitlement,
                    update.getQuantity());
            }
        }
        else {
            throw new NotFoundException(
                i18n.tr("Entitlement with ID ''{0}'' could not be found.", id));
        }
    }


    /**
     * Return the subscription cert for the given id.
     * @param dbid entitlement id.
     * @return the subscription cert for the given id.
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({ MediaType.TEXT_PLAIN })
    @Path("{dbid}/upstream_cert")
    public String getEntitlementUpstreamCert(
        @PathParam("dbid") String dbid) {
        Entitlement ent = entitlementCurator.find(dbid);
        // optimization: don't do entitlement regen here, as we don't read
        // the entitlement certificate in this call.

        if (ent == null) {
            throw new NotFoundException(i18n.tr(
                "Subscription with ID ''{0}'' could not be found.", dbid));
        }

        String subscriptionId = ent.getPool().getSubscriptionId();
        SubscriptionResource subResource = new SubscriptionResource(subService,
            consumerCurator, i18n);
        return subResource.getSubCertAsPem(subscriptionId);
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
            i18n.tr("Entitlement with ID ''{0}'' could not be found.", dbid));
    }

    /**
     * @return a JobDetail
     * @httpcode 202
     */
    @PUT
    @Path("product/{product_id}")
    public JobDetail regenerateEntitlementCertificatesForProduct(
            @PathParam("product_id") String productId,
            @QueryParam("lazy_regen") @DefaultValue("true") boolean lazyRegen) {
        prodAdapter.purgeCache();
        JobDataMap map = new JobDataMap();
        map.put(RegenProductEntitlementCertsJob.PROD_ID, productId);
        map.put(RegenProductEntitlementCertsJob.LAZY_REGEN, lazyRegen);

        JobDetail detail = newJob(RegenProductEntitlementCertsJob.class)
            .withIdentity("regen_entitlement_cert_of_prod" + Util.generateUUID())
            .usingJobData(map)
            .build();

        return detail;
    }

}
