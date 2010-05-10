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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * SubscriptionResource
 */

@Path("/subscriptions")
public class SubscriptionResource {
    private static Logger log = Logger.getLogger(SubscriptionResource.class);
    private SubscriptionCurator subCurator;

    private I18n i18n;

    @Inject
    public SubscriptionResource(SubscriptionCurator subCurator,
        I18n i18n) {
        this.subCurator = subCurator;
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Subscription> getSubscriptions() {
        List<Subscription> subList = new LinkedList<Subscription>();
        subList = subCurator.listAll();
        return subList;
    }

    @DELETE
    @Path("/{subscription_id}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public void deleteSubscription(
        @PathParam("subscription_id") Long subscriptionId) {
        Subscription subscription = subCurator.find(subscriptionId);

        if (subscription == null) {
            throw new BadRequestException(
                i18n.tr("Subscription with id {0} could not be found", subscriptionId));
        }

        subCurator.delete(subscription);

    }

    // SubscriptionTokenResource
    // createToken
    // deleteToken
    // get/set token string

}
