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

import org.candlepin.auth.interceptor.Verify;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.providers.jaxb.DoNotUseJAXBProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * SubscriptionResource
 */

@Path("/subscriptions")
@Consumes(MediaType.APPLICATION_JSON)
public class SubscriptionResource {
    private static Logger log = LoggerFactory.getLogger(SubscriptionResource.class);

    private SubscriptionServiceAdapter subService;
    private ConsumerCurator consumerCurator;

    private I18n i18n;

    @Inject
    public SubscriptionResource(SubscriptionServiceAdapter subService,
        ConsumerCurator consumerCurator,
        I18n i18n) {
        this.subService = subService;
        this.consumerCurator = consumerCurator;

        this.i18n = i18n;

    }

    /**
     * Retrieves a list of Subscriptions
     *
     * @return a list of Subscription objects
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Subscription> getSubscriptions() {
        return subService.getSubscriptions();
    }

    /**
     * Retrieves a single Subscription
     * <p>
     * <pre>
     * {
     *   "id" : "8a8d0986458ef80101458ef87a27057a",
     *   "owner" : {},
     *   "product" : {},
     *   "derivedProduct" : null,
     *   "providedProducts" : [ ],
     *   "derivedProvidedProducts" : [ ],
     *   "branding" : [ ],
     *   "quantity" : 15,
     *   "startDate" : [date],
     *   "endDate" : [date],
     *   "contractNumber" : "5",
     *   "accountNumber" : "12331131231",
     *   "modified" : null,
     *   "orderNumber" : "order-8675309",
     *   "upstreamPoolId" : null,
     *   "upstreamEntitlementId" : null,
     *   "upstreamConsumerId" : null,
     *   "cdn" : {},
     *   "certificate" : {},
     *   "stacked" : false,
     *   "stackId" : null,
     *   "created" : [date],
     *   "updated" : [date]
     * }
     * </pre>
     *
     * @return a Subscription object
     * @httpcode 400
     * @httpcode 200
     */
    @GET
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription getSubscription(
        @PathParam("subscription_id") String subscriptionId) {

        Subscription subscription = verifyAndFind(subscriptionId);
        return subscription;
    }

     /**
      * Retrieves a Subscription Certificate
     * <p>
      * As a PEM
      *
      * @param subscriptionId
      * @return a String object
      */
    @DoNotUseJAXBProvider
    @GET
    @Path("{subscription_id}/cert")
    // cpc passes up content-type on all calls, make sure we don't 415 it
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({ MediaType.TEXT_PLAIN })
    public String getSubCertAsPem(
        @PathParam("subscription_id") String subscriptionId) {
        SubscriptionsCertificate subCert = getSubCertWorker(subscriptionId);
        log.debug("get as pem");
        return subCert.getCert() + subCert.getKey();
    }

    /**
     * Retrieves a Subscription Certificate
     *
     * @param subscriptionId
     * @return a SubscriptionCertificate object
     */
    @GET
    @Path("{subscription_id}/cert")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({ MediaType.APPLICATION_JSON})
    public SubscriptionsCertificate getSubCert(
        @PathParam("subscription_id") String subscriptionId) {
        SubscriptionsCertificate subCert = getSubCertWorker(subscriptionId);
        return subCert;
    }

    private SubscriptionsCertificate getSubCertWorker(String subscriptionId) {
        Subscription sub = verifyAndFind(subscriptionId);
        SubscriptionsCertificate subCert = sub.getCertificate();
        if (subCert == null) {
            throw new BadRequestException(
                i18n.tr("no certificate for subscription {0}", subscriptionId));
        }
        return subCert;
    }

    /**
     * Activates a Subscription
     *
     * @httpcode 400
     * @httpcode 503
     * @httpcode 200
     */
    @POST
    public void activateSubscription(
        @QueryParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @QueryParam("email") String email,
        @QueryParam("email_locale") String emailLocale,
        @Context HttpServletResponse response) {

        if (email == null) {
            throw new BadRequestException(i18n.tr(
                    "email is required for notification"));
        }

        if (emailLocale == null) {
            throw new BadRequestException(i18n.tr(
                    "email locale is required for notification"));
        }

        Consumer consumer = consumerCurator.findByUuid(consumerUuid);

        if (consumer == null) {
            throw new BadRequestException(i18n.tr("No such unit: {0}",
                consumerUuid));
        }

        this.subService.activateSubscription(consumer, email, emailLocale);

        // setting response status to 202 because subscription does not
        // exist yet, but is currently being processed
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    /**
     * Removes a Subscription
     *
     * @httpcode 400
     * @httpcode 200
     */
    @DELETE
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteSubscription(
        @PathParam("subscription_id") String subscriptionIdString) {

        Subscription subscription = verifyAndFind(subscriptionIdString);
        subService.deleteSubscription(subscription);
    }

    private Subscription verifyAndFind(String subscriptionId) {
        Subscription subscription = subService.getSubscription(subscriptionId);

        if (subscription == null) {
            throw new BadRequestException(
                i18n.tr("Subscription with id {0} could not be found.", subscriptionId));
        }
        return subscription;
    }
}
