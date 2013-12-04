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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.candlepin.auth.SubResource;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.Paginate;
import org.candlepin.pinsetter.tasks.EntitleByProductsJob;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.ConsumerResourcesUtil;
import org.candlepin.version.CertVersionConflictException;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * API Gateway for Consumer Entitlements
 */
@Path("/consumers/{consumer_uuid}/entitlements")
public class ConsumerEntitlementResource {

    private static Logger log = LoggerFactory.getLogger(ConsumerEntitlementResource.class);

    private EntitlementCurator entitlementCurator;
    private ConsumerCurator consumerCurator;
    private PoolManager poolManager;
    private I18n i18n;
    private Entitler entitler;
    private SubscriptionServiceAdapter subAdapter;
    private CalculatedAttributesUtil calculatedAttributesUtil;
    private QuantityRules quantityRules;
    private ProductServiceAdapter productAdapter;
    private ConsumerResourcesUtil consumerResourcesUtil;

    @Inject
    public ConsumerEntitlementResource(EntitlementCurator entitlementCurator,
            ConsumerCurator consumerCurator, PoolManager poolManager,
            I18n i18n, Entitler entitler,
            SubscriptionServiceAdapter subAdapter,
            CalculatedAttributesUtil calculatedAttributesUtil,
            QuantityRules quantityRules, ProductServiceAdapter productAdapter) {
        this.entitlementCurator = entitlementCurator;
        this.consumerCurator = consumerCurator;
        this.poolManager = poolManager;
        this.i18n = i18n;
        this.entitler = entitler;
        this.subAdapter = subAdapter;
        this.calculatedAttributesUtil = calculatedAttributesUtil;
        this.quantityRules = quantityRules;
        this.productAdapter = productAdapter;
        this.consumerResourcesUtil = new ConsumerResourcesUtil(poolManager, i18n);
    }

    /**
     * @return a list of Entitlement objects
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Paginate
    public List<Entitlement> listEntitlements(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("product") String productId,
        @Context PageRequest pageRequest) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Page<List<Entitlement>> entitlementsPage;
        if (productId != null) {
            Product p = productAdapter.getProductById(productId);
            if (p == null) {
                throw new BadRequestException(i18n.tr(
                    "Product with ID ''{0}'' could not be found.", productId));
            }
            entitlementsPage = entitlementCurator.listByConsumerAndProduct(consumer,
                productId, pageRequest);
        }
        else {
            entitlementsPage = entitlementCurator.listByConsumer(consumer, pageRequest);
        }

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, entitlementsPage);

        List<Entitlement> returnedEntitlements = entitlementsPage.getPageData();
        for (Entitlement ent : returnedEntitlements) {
            addCalculatedAttributes(ent);
        }
        poolManager.regenerateDirtyEntitlements(returnedEntitlements);

        return returnedEntitlements;
    }

    /**
     * Request an entitlement.
     *
     * If a pool ID is specified, we know we're binding to that exact pool. Specifying
     * an entitle date in this case makes no sense and will throw an error.
     *
     * If a list of product IDs are specified, we attempt to auto-bind to subscriptions
     * which will provide those products. An optional date can be specified allowing
     * the consumer to get compliant for some date in the future. If no date is specified
     * we assume the current date.
     *
     * If neither a pool nor an ID is specified, this is a healing request. The path
     * is similar to the bind by products, but in this case we use the installed products
     * on the consumer, and their current compliant status, to determine which product IDs
     * should be requested. The entitle date is used the same as with bind by products.
     *
     * @param consumerUuid Consumer identifier to be entitled
     * @param poolIdString Entitlement pool id.
     * @param email email address.
     * @param emailLocale locale for email address.
     * @param async True if bind should be asynchronous, defaults to false.
     * @param entitleDateStr specific date to entitle by.
     * @return Response with a list of entitlements or if async is true, a
     *         JobDetail.
     * @httpcode 400
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response bind(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("pool") @Verify(value = Pool.class, nullable = true,
            subResource = SubResource.ENTITLEMENTS)
                String poolIdString,
        @QueryParam("product") String[] productIds,
        @QueryParam("quantity") Integer quantity,
        @QueryParam("email") String email,
        @QueryParam("email_locale") String emailLocale,
        @QueryParam("async") @DefaultValue("false") boolean async,
        @QueryParam("entitle_date") String entitleDateStr) {

        // Check that only one query param was set:
        if (poolIdString != null && productIds != null && productIds.length > 0) {
            throw new BadRequestException(
                i18n.tr("Cannot bind by multiple parameters."));
        }

        if (poolIdString == null && quantity != null) {
            throw new BadRequestException(
                i18n.tr("Cannot specify a quantity when auto-binding."));
        }

        // doesn't make sense to bind by pool and a date.
        if (poolIdString != null && entitleDateStr != null) {
            throw new BadRequestException(
                i18n.tr("Cannot bind by multiple parameters."));
        }

        // TODO: really should do this in a before we get to this call
        // so the method takes in a real Date object and not just a String.
        Date entitleDate = null;
        if (entitleDateStr != null) {
            entitleDate = ResourceDateParser.parseDateString(entitleDateStr);
        }

        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        log.debug("Consumer (post verify): " + consumer);
        try {
            // I hate double negatives, but if they have accepted all
            // terms, we want comeToTerms to be true.
            if (subAdapter.hasUnacceptedSubscriptionTerms(consumer.getOwner())) {
                return Response.serverError().build();
            }
        }
        catch (CandlepinException e) {
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage());
            }
            throw e;
        }

        if (poolIdString != null && quantity == null) {
            Pool pool = poolManager.find(poolIdString);
            if (pool != null) {
                quantity = quantityRules.getQuantityToBind(pool, consumer);
            }
            else {
                quantity = 1;
            }
        }
        //
        // HANDLE ASYNC
        //
        if (async) {
            JobDetail detail = null;

            if (poolIdString != null) {
                detail = EntitlerJob.bindByPool(poolIdString, consumerUuid, quantity);
            }
            else {
                detail = EntitleByProductsJob.bindByProducts(productIds,
                        consumerUuid, entitleDate);
            }

            // events will be triggered by the job
            return Response.status(Response.Status.OK)
                .type(MediaType.APPLICATION_JSON).entity(detail).build();
        }


        //
        // otherwise we do what we do today.
        //
        List<Entitlement> entitlements = null;

        if (poolIdString != null) {
            entitlements = entitler.bindByPool(poolIdString, consumer, quantity);
        }
        else {
            try {
                entitlements = entitler.bindByProducts(productIds, consumer, entitleDate);
            }
            catch (ForbiddenException fe) {
                throw fe;
            }
            catch (CertVersionConflictException cvce) {
                throw cvce;
            }
            catch (RuntimeException re) {
                log.warn("Unable to attach a subscription for a product that " +
                    "has no pool: " + re.getMessage());
            }
        }

        // Trigger events:
        entitler.sendEvents(entitlements);

        return Response.status(Response.Status.OK)
            .type(MediaType.APPLICATION_JSON).entity(entitlements).build();
    }

    /**
     * Request a list of pools and quantities that would result in an actual auto-bind.
     *
     * This is a dry run of an autobind. It allows the client to see what would be the
     * result of an autobind without executing it. It can only do this for the prevously
     * established list of installed products for the consumer
     *
     * If a service level is included in the request, then that level will override the
     * one stored on the consumer. If no service level is included then the existing
     * one will be used.
     *
     * @param consumerUuid Consumer identifier to be entitled
     * @param serviceLevel String service level override to be used for run
     * @return Response with a list of PoolQuantities containing the pool and number.
     * @httpcode 400
     * @httpcode 403
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/dry-run")
    public List<PoolQuantity> dryBind(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("service_level") String serviceLevel) {

        // Verify consumer exists:
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);

        List<PoolQuantity> dryRunPools = new ArrayList<PoolQuantity>();

        try {
            consumerResourcesUtil.checkServiceLevel(consumer.getOwner(), serviceLevel);
            dryRunPools = entitler.getDryRun(consumer, serviceLevel);
        }
        catch (ForbiddenException fe) {
            return dryRunPools;
        }
        catch (BadRequestException bre) {
            throw bre;
        }
        catch (RuntimeException re) {
            return dryRunPools;
        }

        return dryRunPools;
    }

    private void addCalculatedAttributes(Entitlement ent) {
        // With no consumer/date, this will not build suggested quantity
        Map<String, String> calculatedAttributes =
            calculatedAttributesUtil.buildCalculatedAttributes(ent.getPool(), null, null);
        ent.getPool().setCalculatedAttributes(calculatedAttributes);
    }
}
