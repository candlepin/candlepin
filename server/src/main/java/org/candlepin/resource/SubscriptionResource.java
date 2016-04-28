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
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.SubscriptionsCertificate;
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
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * SubscriptionResource
 */

@Path("/subscriptions")
@Api("subscriptions")
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


    /**
     * Retrieves the master pool generated from the specified subscription. If an appropriate master
     * pool cannot be found, this method throws a NotFoundException.
     *
     * @param subscriptionId
     *  The subscription ID for which to retrieve the master pool
     *
     * @throws NotFoundException
     *  if an appropriate master pool cannot be found.
     *
     * @return
     *  the master pool for the given subscription
     */
    protected Pool getMasterPoolForSubscription(String subscriptionId) {
        Pool pool = this.poolManager.getMasterPoolBySubscriptionId(subscriptionId);

        if (pool == null) {
            throw new NotFoundException(
                i18n.tr("A subscription with the ID \"{0}\" could not be found.", subscriptionId)
            );
        }

        return pool;
    }

    /**
     * Retrieves the subscription certificate for the given subscription ID. If the subscription
     * cannot be found or does not have a certificate, this method throws a NotFoundException.
     *
     * @param subscriptionId
     *  The subscription ID for which to retrieve a subscription certificate
     *
     * @throws NotFoundException
     *  if the subscription cannot be found or the subscription does not have a certificate
     *
     * @return
     *  the certificate associated with the specified subscription
     */
    protected SubscriptionsCertificate getSubscriptionCertificate(String subscriptionId) {
        Pool pool = this.getMasterPoolForSubscription(subscriptionId);

        if (pool.getCertificate() == null) {
            throw new NotFoundException(
                i18n.tr("A certificate was not found for subscription \"{0}\"", subscriptionId)
            );
        }

        return pool.getCertificate();
    }

    @ApiOperation(notes = "Retrieves a list of Subscriptions", value = "getSubscriptions")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Subscription> getSubscriptions() {
        List<Subscription> subscriptions = new LinkedList<Subscription>();

        for (Pool pool : this.poolManager.listMasterPools()) {
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
        Pool pool = this.getMasterPoolForSubscription(subscriptionId);

        return this.poolManager.fabricateSubscriptionFromPool(pool);
    }

    @ApiOperation(notes = "Retrieves a Subscription Certificate As a PEM", value = "getSubCertAsPem")
    @DoNotUseJAXBProvider
    @GET
    @Path("{subscription_id}/cert")
    // cpc passes up content-type on all calls, make sure we don't 415 it
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({ MediaType.TEXT_PLAIN })
    public String getSubCertAsPem(
        @PathParam("subscription_id") String subscriptionId) {

        SubscriptionsCertificate cert = this.getSubscriptionCertificate(subscriptionId);
        return cert.getCert() + cert.getKey();
    }

    @ApiOperation(notes = "Retrieves a Subscription Certificate", value = "getSubCert")
    @GET
    @Path("{subscription_id}/cert")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({ MediaType.APPLICATION_JSON})
    public SubscriptionsCertificate getSubCert(
        @PathParam("subscription_id") String subscriptionId) {

        return this.getSubscriptionCertificate(subscriptionId);
    }

    @ApiOperation(notes = "Activates a Subscription", value = "activateSubscription")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 503, message = ""),
        @ApiResponse(code = 202, message = "") })
    @POST
    @Produces(MediaType.WILDCARD)
    @Consumes(MediaType.WILDCARD)
    public Response activateSubscription(
        @QueryParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("email") String email,
        @QueryParam("email_locale") String emailLocale) {

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
        List<Pool> pools = this.poolManager.getPoolsBySubscriptionId(subscriptionId);

        if (pools.isEmpty()) {
            throw new NotFoundException(
                i18n.tr("A subscription with the ID \"{0}\" could not be found.", subscriptionId)
            );
        }

        for (Pool pool : pools) {
            this.poolManager.deletePool(pool);
        }
    }

}
