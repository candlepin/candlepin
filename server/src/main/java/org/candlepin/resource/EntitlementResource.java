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

import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.common.paging.Paginate;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Cdn;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.Pool;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.pinsetter.tasks.RegenProductEntitlementCertsJob;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.util.EntitlementFinderUtil;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.ArrayList;
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
import javax.ws.rs.core.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST api gateway for the User object.
 */
@Path("/entitlements")
@Api("entitlements")
public class EntitlementResource {
    private static Logger log = LoggerFactory.getLogger(EntitlementResource.class);
    private final ConsumerCurator consumerCurator;
    private PoolManager poolManager;
    private final EntitlementCurator entitlementCurator;
    private I18n i18n;
    private Entitler entitler;
    private Enforcer enforcer;
    private EntitlementRulesTranslator messageTranslator;

    @Inject
    public EntitlementResource(EntitlementCurator entitlementCurator,
        ConsumerCurator consumerCurator,
        PoolManager poolManager,
        I18n i18n,
        Entitler entitler, Enforcer enforcer, EntitlementRulesTranslator messageTranslator) {

        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
        this.poolManager = poolManager;
        this.entitler = entitler;
        this.enforcer = enforcer;
        this.messageTranslator = messageTranslator;
    }

    private void verifyExistence(Object o, String id) {
        if (o == null) {
            throw new RuntimeException(
                i18n.tr("Object with ID ''{0}'' could not found.", id));
        }
    }

    @ApiOperation(notes = "Checks Consumer for Product Entitlement", value = "hasEntitlement")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("consumer/{consumer_uuid}/product/{product_id}")
    public Entitlement hasEntitlement(@PathParam("consumer_uuid") String consumerUuid,
        @PathParam("product_id") String productId) {

        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        verifyExistence(consumer, consumerUuid);

        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getPool().getProductId().equals(productId)) {
                return e;
            }
        }

        throw new NotFoundException(i18n.tr(
            "Unit ''{0}'' has no subscription for product ''{1}''.",
                consumerUuid, productId));
    }

    @ApiOperation(notes = "Retrieves list of Entitlements", value = "listAllForConsumer")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Paginate
    public List<Entitlement> listAllForConsumer(
        @QueryParam("consumer") String consumerUuid,
        @QueryParam("matches") String matches,
        @QueryParam("attribute") @CandlepinParam(type = KeyValueParameter.class)
        List<KeyValueParameter> attrFilters,
        @Context PageRequest pageRequest) {

        EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter(matches, attrFilters);
        Page<List<Entitlement>> p;
        if (consumerUuid != null) {

            Consumer consumer = consumerCurator.findByUuid(consumerUuid);
            if (consumer == null) {
                throw new BadRequestException(
                    i18n.tr("Unit with ID ''{0}'' could not be found.", consumerUuid));
            }
            p = entitlementCurator.listByConsumer(consumer, null, filters, pageRequest);
        }
        else {
            p = entitlementCurator.listAll(filters, pageRequest);
        }

        // Store the page for the LinkHeaderResponseFilter
        ResteasyProviderFactory.pushContext(Page.class, p);
        return p.getPageData();
    }

    @ApiOperation(notes = "Retrieves a single Entitlement", value = "getEntitlement")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{entitlement_id}")
    public Entitlement getEntitlement(
        @PathParam("entitlement_id") @Verify(Entitlement.class) String dbid) {
        Entitlement toReturn = entitlementCurator.find(dbid);
        List<Entitlement> tempList = Arrays.asList(toReturn);
        poolManager.regenerateDirtyEntitlements(tempList);
        if (toReturn != null) {
            return toReturn;
        }
        throw new NotFoundException(
            i18n.tr("Entitlement with ID ''{0}'' could not be found.", dbid));
    }

    @ApiOperation(notes = "Updates an Entitlement. This only works for the quantity.",
        value = "updateEntitlement")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
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
                i18n.tr("Quantity value must be greater than 0."));
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


    @ApiOperation(notes = "Retrieves a Subscription Certificate.  We can't return CdnInfo " +
        "at this time, but when the time comes this is the implementation we want to start" +
        " from. It will require changes to thumbslug.  will also" +
        " @Produces(MediaType.APPLICATION_JSON)", value = "getUpstreamCert")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("{dbid}/upstream_cert")
    public String getUpstreamCert(
        @PathParam("dbid") String entitlementId) {

        Entitlement ent = entitlementCurator.find(entitlementId);
        if (ent == null) {
            throw new NotFoundException(i18n.tr(
                "Entitlement with ID ''{0}'' could not be found.", entitlementId));
        }

        Pool entPool = ent.getPool();
        if (!StringUtils.isBlank(entPool.getSourceStackId())) {
            /*
             * A derived pool originating from a stacked parent pool will have no subscription
             * ID as the pool is technically from many subscriptions. (i.e. all the
             * entitlements in the stack) In this case we must look up an active entitlement
             * in the hosts stack, and use this as our upstream certificate.
             */
            log.debug("Entitlement is from a stack derived pool, searching for oldest " +
                "active entitlements in source stack.");

            ent = entitlementCurator.findUpstreamEntitlementForStack(
                entPool.getSourceConsumer(),
                entPool.getSourceStackId());
        }

        if (ent == null || ent.getPool() == null || ent.getPool().getCertificate() == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find upstream certificate for entitlement: {0}", entitlementId)
            );
        }

        SubscriptionsCertificate cert = ent.getPool().getCertificate();
        return cert.getCert() + cert.getKey();
    }

    @ApiOperation(notes = "Deletes an Entitlement", value = "unbind")
    @ApiResponses({ @ApiResponse(code = 403, message = ""), @ApiResponse(code = 404, message = "") })
    @DELETE
    @Produces(MediaType.WILDCARD)
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

    @ApiOperation(notes = "Regenerates the Entitlement Certificates for a Product",
        value = "regenerateEntitlementCertificatesForProduct")
    @ApiResponses({ @ApiResponse(code = 202, message = "") })
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("product/{product_id}")
    public JobDetail regenerateEntitlementCertificatesForProduct(
        @PathParam("product_id") String productId,
        @QueryParam("lazy_regen") @DefaultValue("true") boolean lazyRegen) {

        JobDataMap map = new JobDataMap();
        map.put(RegenProductEntitlementCertsJob.PROD_ID, productId);
        map.put(RegenProductEntitlementCertsJob.LAZY_REGEN, lazyRegen);

        JobDetail detail = newJob(RegenProductEntitlementCertsJob.class)
            .withIdentity("regen_entitlement_cert_of_prod" + Util.generateUUID())
            .usingJobData(map)
            .build();

        return detail;
    }

    @ApiOperation(notes = "Migrate entitlements from one distributor consumer to another." +
        " Can specify full or partial quantity. No specified quantity " +
        "will lead to full migration of the entitlement.", value = "migrateEntitlement")
    @ApiResponses({ @ApiResponse(code = 404, message = "") })
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("{entitlement_id}/migrate")
    public Response migrateEntitlement(
        @PathParam("entitlement_id") @Verify(Entitlement.class) String id,
        @QueryParam("to_consumer") @Verify(Consumer.class) String uuid,
        @QueryParam("quantity") Integer quantity) {
        // confirm entitlement
        Entitlement entitlement = entitlementCurator.find(id);
        List<Entitlement> entitlements = new ArrayList<Entitlement>();

        if (entitlement != null) {
            if (quantity == null) {
                quantity = entitlement.getQuantity();
            }
            if (quantity > 0 && quantity <= entitlement.getQuantity()) {
                Consumer sourceConsumer = entitlement.getConsumer();
                Consumer destinationConsumer = consumerCurator.verifyAndLookupConsumer(uuid);
                if (!sourceConsumer.getType().isManifest()) {
                    throw new BadRequestException(i18n.tr(
                        "Entitlement migration is not permissible for units of type ''{0}''",
                        sourceConsumer.getType().getLabel()));
                }
                if (!destinationConsumer.getType().isManifest()) {
                    throw new BadRequestException(i18n.tr(
                        "Entitlement migration is not permissible for units of type ''{0}''",
                        destinationConsumer.getType().getLabel()));
                }
                if (!sourceConsumer.getOwner().getKey().equals(destinationConsumer.getOwner().getKey())) {
                    throw new BadRequestException(i18n.tr(
                        "Source and destination units must belong to the same organization"));
                }
                // test to ensure destination can use the pool
                ValidationResult result = enforcer.preEntitlement(destinationConsumer,
                    entitlement.getPool(), 0, CallerType.BIND);
                if (!result.isSuccessful()) {
                    throw new BadRequestException(i18n.tr(
                        "The entitlement cannot be utilized by the destination unit: ") +
                        messageTranslator.poolErrorToMessage(entitlement.getPool(),
                            result.getErrors().get(0)));
                }
                if (quantity.intValue() == entitlement.getQuantity()) {
                    unbind(id);
                }
                else {
                    entitler.adjustEntitlementQuantity(sourceConsumer, entitlement,
                        entitlement.getQuantity() - quantity);
                }
                Pool pool = entitlement.getPool();
                entitlements.addAll(entitler.bindByPoolQuantity(destinationConsumer, pool.getId(),
                    quantity));

                // Trigger events:
                entitler.sendEvents(entitlements);

            }
            else {
                throw new BadRequestException(i18n.tr(
                    "The quantity specified must be greater than zero " +
                    "and less than or equal to the total for this entitlement"));
            }
        }
        else {
            throw new NotFoundException(
                i18n.tr("Entitlement with ID ''{0}'' could not be found.", id));
        }
        return Response.status(Response.Status.OK)
                .type(MediaType.APPLICATION_JSON).entity(entitlements).build();

    }

    /**
     *
     * CdnInfo represents a container for subscription entitlement and cdn
     */
    public static class CdnInfo implements Serializable {
        private static final long serialVersionUID = 523637879312970984L;

        private Cdn cdn;
        private String subCert;

        public CdnInfo() {
        }

        public CdnInfo(Cdn cdn, String subEntitlement) {
            this.cdn = cdn;
            this.subCert = subEntitlement;
        }

        public Cdn getCdn() {
            return this.cdn;
        }

        public void setCdn(Cdn cdn) {
            this.cdn = cdn;
        }

        public String getSubCert() {
            return this.subCert;
        }

        public void setSubCert(String subCert) {
            this.subCert = subCert;
        }
    }

}
