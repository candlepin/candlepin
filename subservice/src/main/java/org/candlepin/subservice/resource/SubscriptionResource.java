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
package org.candlepin.subservice.resource;

import org.candlepin.model.dto.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

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
 * The SubscriptionResource class handles the subscription-oriented API calls for the subscription
 * service.
 */
@Path("/subscriptions")
public class SubscriptionResource {
    private static Logger log = LoggerFactory.getLogger(SubscriptionResource.class);
    private static Set<Subscription> subscriptions = new HashSet<Subscription>();

    // Things we need:
    // Backing curator
    // Translation service (I18n)
    // Authentication

    /**
     * Creates a new subscription from the subscription JSON provided. Any UUID provided in the JSON
     * will be ignored when creating the new subscription.
     *
     * @param subscription
     *  A Subscription object built from the JSON provided in the request
     *
     * @return
     *  The newly created Subscription object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription createSubscription(Subscription subscription) {
        log.error("{} {} {} {} {} {} {} {} {} {}", subscription.getId(), subscription.getOwner(), subscription.getProduct(),
                subscription.getQuantity(), subscription.getStartDate(), subscription.getContractNumber(),
                subscription.getAccountNumber(), subscription.getBranding(), subscription.getDerivedProvidedProducts(),
                subscription.getDerivedProduct());
        subscriptions.add(subscription);
        return subscription;
    }

    /**
     * Lists all known subscriptions currently maintained by the subscription service.
     *
     * @return
     *  A collection of subscriptions maintained by the subscription service
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Set<Subscription> listSubscriptions() {
        // TODO
        return subscriptions;
    }

    /**
     * Retrieves the subscription for the specified subscription UUID. If the subscription UUID
     * cannot be found, this method returns null.
     *
     * @param subscriptionUuid
     *  The UUID of the subscription to retrieve
     *
     * @return
     *  The requested Subscription object, or null if the subscription could not be found
     */
    @GET
    @Path("/{subscription_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription getSubscription(@PathParam("subscription_uuid") String subscriptionUuid) {
        for(Subscription subscription:subscriptions) {
            if(subscription.getOwner().getKey().equals(subscriptionUuid)) {
                return subscription;
            }
        }
        // TODO
        return null;
    }

    /**
     * Updates the specified subscription with the provided subscription data.
     *
     * @param subscriptionUuid
     *  The UUID of the subscription to update
     *
     * @param subscription
     *  A Subscription object built from the JSON provided in the request; contains the data to use
     *  to update the specified subscription
     *
     * @return
     *  The updated Subscription object
     */
    @PUT
    @Path("{subscription_uuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Subscription updateSubscription(@PathParam("subscription_uuid") String subscriptionUuid,
        Subscription subscriptionNew) {
        for(Subscription subscription:subscriptions) {
            if(subscription.getOwner().getKey().equals(subscriptionUuid)) {
                subscriptions.remove(subscription);
                subscriptions.add(subscriptionNew);
                return subscriptionNew;
            }
        }
        // TODO
        return null;
    }

    /**
     * Deletes the specified subscription.
     *
     * @param subscriptionUuid
     *  The UUID of the subscription to delete
     *
     * @return
     *  True if the subscription was deleted successfully; false otherwise
     */
    @DELETE
    @Path("/{subscription_uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteSubscription(@PathParam("subscription_uuid") String subscriptionUuid) {
        subscriptions.clear();
        return false;
    }

}
