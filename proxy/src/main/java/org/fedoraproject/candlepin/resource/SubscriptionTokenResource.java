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
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.SubscriptionToken;
import org.fedoraproject.candlepin.model.SubscriptionTokenCurator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * SubscriptionTokenResource
 */
@Path("/subscriptiontokens")
public class SubscriptionTokenResource {
    private static Logger log = Logger
        .getLogger(SubscriptionTokenResource.class);
    private SubscriptionTokenCurator subTokenCurator;
    private I18n i18n;

    @Inject
    public SubscriptionTokenResource(SubscriptionTokenCurator subTokenCurator,
        I18n i18n) {
        this.subTokenCurator = subTokenCurator;
        this.i18n = i18n;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<SubscriptionToken> getSubscriptionTokens() {
        List<SubscriptionToken> subTokenList = new LinkedList<SubscriptionToken>();
        subTokenList = subTokenCurator.listAll();
        log.debug("sub token list" + subTokenList);
        return subTokenList;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public SubscriptionToken createSubscriptionToken(
        SubscriptionToken subscriptionToken) {
        log.debug("subscriptionToken" + subscriptionToken);
        SubscriptionToken newSubscriptionToken = subTokenCurator
            .create(subscriptionToken);

        return newSubscriptionToken;
    }

    @DELETE
    @Path("/{subscription_token_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteSubscriptionToken(
        @PathParam("subscription_token_id") String subscriptionTokenId) {
        SubscriptionToken subscriptionToken = subTokenCurator
            .find(subscriptionTokenId);

        if (subscriptionToken == null) {
            throw new BadRequestException(i18n.tr(
                "SubscriptionToken with id {0} could not be found",
                subscriptionTokenId));
        }

        subTokenCurator.delete(subscriptionToken);
    }
}
