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
package org.candlepin.hostedtest;

import org.candlepin.model.dto.Subscription;
import org.candlepin.resource.util.ResolverUtil;
import org.candlepin.service.UniqueIdGenerator;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * The SubscriptionResource class is used to provide an
 * in-memory upstream source for subscriptions when candlepin is run in hosted
 * mode, while it is built with candlepin, it is not packaged in candlepin.war,
 * as the only purpose of this class is to support spec tests.
 */
@Path("/hostedtest/subscriptions")
public class HostedTestSubscriptionResource {

    @Inject
    private HostedTestSubscriptionServiceAdapter adapter;
    @Inject
    private UniqueIdGenerator idGenerator;
    @Inject
    private ResolverUtil resolverUtil;

    /**
     * API to check if resource is alive
     *
     * @return always returns true
     */
    @GET
    @Path("/alive")
    @Produces(MediaType.TEXT_PLAIN)
    public Boolean isAlive() {
        return true;
    }

    /**
     * Creates a new subscription from the subscription JSON provided. Any UUID
     * provided in the JSON will be ignored when creating the new subscription.
     *
     * @param subscription
     *        A Subscription object built from the JSON provided in the request
     * @return
     *         The newly created Subscription object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription createSubscription(Subscription subscription) {
        if (subscription.getId() == null || subscription.getId().trim().length() == 0) {
            subscription.setId(this.idGenerator.generateId());
        }
        return adapter.createSubscription(resolverUtil.resolveSubscription(subscription));
    }

    /**
     * Lists all known subscriptions currently maintained by the subscription service.
     *
     * @return
     *  A collection of subscriptions maintained by the subscription service
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Subscription> listSubscriptions() {
        return adapter.getSubscriptions();
    }

    /**
     * Retrieves the subscription for the specified subscription id. If the
     * subscription id cannot be found, this method returns null.
     *
     * @param subscriptionId
     *        The id of the subscription to retrieve
     * @return
     *         The requested Subscription object, or null if the subscription
     *         could not be found
     */
    @GET
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription getSubscription(@PathParam("subscription_id") String subscriptionId) {
        return adapter.getSubscription(subscriptionId);
    }

    /**
     * Updates the specified subscription with the provided subscription data.
     *
     * @param subscriptionNew
     *        A Subscription object built from the JSON provided in the request;
     *        contains the data to use
     *        to update the specified subscription
     * @return
     *         The updated Subscription object
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription updateSubscription(Subscription subscriptionNew) {
        return adapter.updateSubscription(resolverUtil.resolveSubscription(subscriptionNew));
    }

    /**
     * Deletes the specified subscription.
     *
     * @param subscriptionId
     *        The id of the subscription to delete
     * @return
     *         True if the subscription was deleted successfully; false
     *         otherwise
     */
    @DELETE
    @Path("/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteSubscription(@PathParam("subscription_id") String subscriptionId) {
        return adapter.deleteSubscription(subscriptionId);
    }

    /**
     * Deletes all subscriptions.
     */
    @DELETE
    public void deleteAllSubscriptions() {
        adapter.deleteAllSubscriptions();
    }

}
