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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.model.SubscriptionTokenCurator;
import org.fedoraproject.candlepin.model.SubscriptionToken;

import com.google.inject.Inject;



/**
 * SubscriptionTokenResource
 */
@Path("/subscriptiontokens")
public class SubscriptionTokenResource {
    private static Logger log = Logger
        .getLogger(SubscriptionTokenResource.class);
    private SubscriptionTokenCurator subTokenCurator;

    @Inject
    public SubscriptionTokenResource(SubscriptionCurator subCurator,
        SubscriptionTokenCurator subTokenCurator, OwnerCurator ownerCurator,
        @Context HttpServletRequest request) {
        this.subTokenCurator = subTokenCurator;
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<SubscriptionToken> getSubscriptionTokens() {
        List<SubscriptionToken> subTokenList = new LinkedList<SubscriptionToken>();
        subTokenList = subTokenCurator.findAll();
        log.debug("sub token list" + subTokenList);
        return subTokenList;
    }

    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public SubscriptionToken createSubscription(
        SubscriptionToken subscriptionToken) {
        log.debug("subscriptionToken" + subscriptionToken);
        SubscriptionToken newSubscriptionToken = subTokenCurator
            .create(subscriptionToken);

        return newSubscriptionToken;
    }

    @DELETE
    @Path("/{subscription_token_id}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public void deleteSubscription(
        @PathParam("subscription_token_id") Long subscriptionTokenId) {
        SubscriptionToken subscriptionToken = subTokenCurator
            .find(subscriptionTokenId);

        if (subscriptionToken == null) {
            throw new BadRequestException("SubscriptionToken caouldn't be found",
                "SubscriptionToken with id " + subscriptionTokenId + " could not be found");
        }

        subTokenCurator.delete(subscriptionToken);

    }
        
}
