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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.ActivationKey;
import org.fedoraproject.candlepin.model.ActivationKeyCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * ActivateResource
 */
@Path("/activate")
public class ActivateResource {

    protected ConsumerResource consumerResource;
    private I18n i18n;
    private ActivationKeyCurator activationKeyCurator;

    @Inject
    public ActivateResource(ConsumerResource consumerResource,
        ActivationKeyCurator activationKeyCurator,
        I18n i18n) {
        this.consumerResource = consumerResource;
        this.i18n = i18n;
        this.activationKeyCurator = activationKeyCurator;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    //@AllowRoles(roles = { Role.NO_AUTH})
    public Consumer activate(Consumer consumer, @Context Principal principal,
        @QueryParam("activation_key") List<String> keyStrings)
        throws BadRequestException {

        // first, look for keys. If it is not found, throw an exception
        List<ActivationKey> keys = new ArrayList<ActivationKey>();
        Owner owner = null;
        if (keyStrings == null || keyStrings.size() == 0) {
            throw new BadRequestException(
                i18n.tr("No activation keys were provided"));
        }
        for (String keyString : keyStrings) {
            ActivationKey key = findKey(keyString);
            if (owner == null) {
                owner = key.getOwner();
            }
            else {
                if (owner.getId() != key.getOwner().getId()) {
                    throw new BadRequestException(
                        i18n.tr("The keys provided are for different owners"));
                }
            }
            keys.add(key);
        }

        throw new BadRequestException("Fix once Ownergeddon is working");
        // set the owner on the principal off of the first key
        //principal.setOwner(owner);

        // Create the consumer via the normal path
        //Consumer newConsumer = consumerResource.create(consumer, principal, userName);

        //return newConsumer;
    }

    protected ActivationKey findKey(String activationKeyId) {
        ActivationKey key = activationKeyCurator
        .find(activationKeyId);

        if (key == null) {
            throw new BadRequestException(i18n.tr(
                "ActivationKey with id {0} could not be found",
                activationKeyId));
        }
        return key;
    }
}
