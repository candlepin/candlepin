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

import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.model.SubscriptionTokenCurator;
import org.fedoraproject.candlepin.model.Subscription;

import com.google.inject.Inject;
import org.fedoraproject.candlepin.auth.Principal;

/**
 * SubscriptionResource
 */

@Path("/subscriptions")
public class SubscriptionResource {

    
    private static Logger log = Logger.getLogger(SubscriptionResource.class);
    private SubscriptionCurator subCurator;
    private Principal principal;

    @Inject
    public SubscriptionResource(SubscriptionCurator subCurator,
        SubscriptionTokenCurator subTokenCurator, OwnerCurator ownerCurator,
        Principal principal) {
        this.subCurator = subCurator;
        this.principal = principal;
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Subscription> getSubscriptions() {
        List<Subscription> subList = new LinkedList<Subscription>();
        subList = subCurator.findAll();
        return subList;
    }

    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Subscription createSubscription(Subscription subscription) {
        //
        subscription.setOwner(principal.getOwner());
        log.debug("owner: " + subscription.getOwner());
        Subscription newSubscription = subCurator.create(subscription);

        return newSubscription;
    }

    @DELETE
    @Path("/{subscription_id}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public void deleteSubscription(
        @PathParam("subscription_id") Long subscriptionId) {
        Subscription subscription = subCurator.find(subscriptionId);

        if (subscription == null) {
            throw new BadRequestException("Couldn't find subscriptipon",
                "Subscription with id " + subscriptionId + " could not be found");
        }

        subCurator.delete(subscription);

    }

    // SubscriptionTokenResource
    // createToken
    // deleteToken
    // get/set token string

}
