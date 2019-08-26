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

import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.exceptions.ResourceMovedException;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.DoNotUseJAXBProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;



/**
 * SubscriptionResource
 */
@Path("/subscriptions")
@Api(value = "subscriptions", authorizations = { @Authorization("basic") })
@Consumes(MediaType.APPLICATION_JSON)
public class SubscriptionResource {
    private static Logger log = LoggerFactory.getLogger(SubscriptionResource.class);

    private SubscriptionServiceAdapter subService;
    private ConsumerCurator consumerCurator;
    private PoolManager poolManager;

    private I18n i18n;

    @Inject
    public SubscriptionResource(SubscriptionServiceAdapter subService,
        ConsumerCurator consumerCurator, PoolManager poolManager, I18n i18n) {

        this.subService = subService;
        this.consumerCurator = consumerCurator;
        this.poolManager = poolManager;

        this.i18n = i18n;
    }

    @ApiOperation(notes = "Retrieves a list of Subscriptions", value = "getSubscriptions")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Subscription> getSubscriptions() {
        List<Subscription> subscriptions = new LinkedList<>();

        for (Pool pool : this.poolManager.getMasterPools()) {
            subscriptions.add(this.poolManager.fabricateSubscriptionFromPool(pool));
        }

        return subscriptions;
    }

    @ApiOperation(notes = "Retrieves a single Subscription", value = "getSubscription")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @GET
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription getSubscription(@PathParam("subscription_id") String subscriptionId) {

        throw new ResourceMovedException("pools/{pool_id}");
    }

    @ApiOperation(notes = "Retrieves a Subscription Certificate As a PEM", value = "getSubCertAsPem",
        nickname = "getSubscriptionCertificatePem")
    @DoNotUseJAXBProvider
    @GET
    @Path("{subscription_id}/cert")
    // cpc passes up content-type on all calls, make sure we don't 415 it
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({ MediaType.TEXT_PLAIN })
    public String getSubCertAsPem(
        @PathParam("subscription_id") String subscriptionId) {

        throw new ResourceMovedException("pools/{pool_id}/cert");
    }

    @ApiOperation(notes = "Retrieves a Subscription Certificate", value = "getSubCert",
        nickname = "getSubscriptionCertificate")
    @GET
    @Path("{subscription_id}/cert")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({ MediaType.APPLICATION_JSON})
    public CertificateDTO getSubCert(
        @PathParam("subscription_id") String subscriptionId) {

        throw new ResourceMovedException("pools/{pool_id}/cert");
    }

    @ApiOperation(notes = "Activates a Subscription", value = "activateSubscription")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 503, message = ""),
        @ApiResponse(code = 202, message = "") })
    @POST
    @Produces(MediaType.WILDCARD)
    @Consumes(MediaType.WILDCARD)
    public Response activateSubscription(
        @ApiParam(required = true) @QueryParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @ApiParam(required = true) @QueryParam("email") String email,
        @ApiParam(required = true) @QueryParam("email_locale") String emailLocale) {

        if (email == null) {
            throw new BadRequestException(i18n.tr("email is required for notification"));
        }

        if (emailLocale == null) {
            throw new BadRequestException(i18n.tr("email locale is required for notification"));
        }

        Consumer consumer = consumerCurator.findByUuid(consumerUuid);

        if (consumer == null) {
            throw new BadRequestException(i18n.tr("No such unit: {0}", consumerUuid));
        }

        this.subService.activateSubscription(consumer, email, emailLocale);

        // setting response status to 202 because subscription does not
        // exist yet, but is currently being processed
        return Response.status(Status.ACCEPTED).build();
    }

    @ApiOperation(notes = "Removes a Subscription", value = "deleteSubscription")
    @ApiResponses({ @ApiResponse(code = 400, message = "") })
    @DELETE
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteSubscription(@PathParam("subscription_id") String subscriptionId) {

        // Lookup pools from subscription ID
        int count = 0;

        for (Pool pool : this.poolManager.getPoolsBySubscriptionId(subscriptionId)) {
            this.poolManager.deletePool(pool);
            ++count;
        }

        if (count == 0) {
            throw new NotFoundException(
                i18n.tr("A subscription with the ID \"{0}\" could not be found.", subscriptionId));
        }
    }

}
