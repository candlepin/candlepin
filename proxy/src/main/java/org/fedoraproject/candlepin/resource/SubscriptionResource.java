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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.Subscription;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

/**
 * SubscriptionResource
 */

@Path("/subscriptions")
public class SubscriptionResource {
    private static Logger log = Logger.getLogger(SubscriptionResource.class);

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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Subscription> getSubscriptions() {
        return subService.getSubscriptions();
    }

    @GET
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription getSubscription(
        @PathParam("subscription_id") String subscriptionId) {
        
        Subscription subscription = verifyAndFind(subscriptionId);
        return subscription;
    }

    @POST
    public void activateSubscription(@QueryParam("consumer_uuid") String consumerUuid,
        @QueryParam("email") String email,
        @QueryParam("email_locale") String emailLocale,
        @Context HttpServletResponse response) {

        if (email == null || emailLocale == null) {
            throw new BadRequestException(i18n.tr(
                    "email and email_locale are required for notification"));
        }

        Consumer consumer = consumerCurator.findByUuid(consumerUuid);

        if (consumer == null) {
            throw new BadRequestException(i18n.tr("No such consumer: {0}",
                consumerUuid));
        }

        this.subService.activateSubscription(consumer, email, emailLocale);

        // setting response status to 202 because subscription does not
        // exist yet, but is currently being processed
        response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

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
                i18n.tr("Subscription with id {0} could not be found", subscriptionId));
        }
        return subscription;
    }
}
