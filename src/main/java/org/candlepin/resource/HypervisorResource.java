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

import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.GoneException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.dto.HypervisorCheckInResult;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * HypervisorResource
 */
@Path("/hypervisors")
public class HypervisorResource {
    private static Logger log = Logger.getLogger(HypervisorResource.class);
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;
    private DeletedConsumerCurator deletedConsumerCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;

    @Inject
    public HypervisorResource(ConsumerResource consumerResource,
        ConsumerCurator consumerCurator, DeletedConsumerCurator deletedConsumerCurator,
        OwnerCurator ownerCurator, I18n i18n) {
        this.consumerResource = consumerResource;
        this.consumerCurator = consumerCurator;
        this.deletedConsumerCurator = deletedConsumerCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
    }

    /**
     * Allows agents such as virt-who to update its host list and associate the
     * guests for each host. This is typically used when a host is unable to
     * register to candlepin via subscription manager.
     *
     *
     * @param hostGuestMap a mapping of host_id to list of guestIds
     * @param principal
     * @param ownerKey
     * @return List<Consumer>
     *
     * @httpcode 202
     * @httpcode 200
     *
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    // FIXME Temporarily open up until auth is figured out.
    @SecurityHole(noAuth = true)
    @Transactional
    public HypervisorCheckInResult hypervisorCheckIn(
        Map<String, List<GuestId>> hostGuestMap, @Context Principal principal,
        @QueryParam("owner") String ownerKey) {

        Owner targetOwner = null;
        if (principal instanceof ConsumerPrincipal) {
            ConsumerPrincipal consumerPrincipal = (ConsumerPrincipal) principal;
            targetOwner = consumerPrincipal.getConsumer().getOwner();
            if (ownerKey != null && !ownerKey.isEmpty()
                && !ownerKey.equalsIgnoreCase(targetOwner.getKey())) {
                throw new BadRequestException(i18n.tr(
                    "The specified owner key {0} does not match that of the registered" +
                    " consumer.", ownerKey));
            }
        }
        else if (principal instanceof UserPrincipal) {
            if (ownerKey == null || ownerKey.isEmpty()) {
                throw new BadRequestException("An owner key must be specified");
            }

            targetOwner = ownerCurator.lookupByKey(ownerKey);
            if (targetOwner == null) {
                throw new BadRequestException(i18n.tr(
                    "Organization {0} does not exist.", ownerKey));
            }

            // Check permissions for current principal on the owner.
            if (!principal.canAccess(targetOwner, Access.ALL)) {
                throw new ForbiddenException(i18n.tr(
                    "User ''{0}'' cannot access organization ''{1}''.",
                    principal.getPrincipalName(), targetOwner.getKey()));
            }
        }
        else {
            throw new ForbiddenException(i18n.tr("Insufficient permissions"));
        }

        // We need to make sure that we check if a host was already created
        // in a different org. This is possible if 2 different consumers/users
        // are reporting a host/guest mapping from the same hypervisor, and target
        // a different org.
        boolean hostHasDifferentOwner = false;
        HypervisorCheckInResult result = new HypervisorCheckInResult();
        for (Entry<String, List<GuestId>> hostEntry : hostGuestMap.entrySet()) {
            try {
                log.info("Attempting to register host: " + hostEntry.getKey());
                List<GuestId> guestIds = hostEntry.getValue();
                DeletedConsumer deletedHypervisor =
                    deletedConsumerCurator.findByConsumerUuid(hostEntry.getKey());
                if (deletedHypervisor != null) {
                    throw new GoneException(
                        i18n.tr("Hypervisor {0} has been deleted previously",
                            hostEntry.getKey(), hostEntry.getKey()));
                }

                boolean hostConsumerCreated = false;
                Consumer consumer = consumerCurator.findByUuid(hostEntry.getKey());
                if (consumer == null) {
                    // Create new consumer
                    consumer = new Consumer();
                    consumer.setName(hostEntry.getKey());
                    consumer.setUuid(hostEntry.getKey());
                    consumer.setType(new ConsumerType(ConsumerTypeEnum.HYPERVISOR));
                    consumer.setFact("uname.machine", "x86_64");
                    consumer.setGuestIds(new ArrayList<GuestId>());
                    consumer = consumerResource.create(consumer, principal, null, ownerKey,
                        null);
                    hostConsumerCreated = true;
                }
                else if (!targetOwner.equals(consumer.getOwner())){
                    // If the consumer's owner does not match the one specified,
                    // checkin has already occured, targeting a different owner.
                    hostHasDifferentOwner = true;
                    break;
                }

                Consumer withIds = new Consumer();
                withIds.setGuestIds(guestIds);
                boolean guestIdsUpdated =
                    consumerResource.performConsumerUpdates(withIds, consumer);
                if (guestIdsUpdated) {
                    consumerCurator.update(consumer);
                }

                // Populate the result with the processed consumer.
                if (hostConsumerCreated) {
                    result.created(consumer);
                }
                else if (guestIdsUpdated && !hostConsumerCreated) {
                    result.updated(consumer);
                }
                else {
                    result.unchanged(consumer);
                }
            }
            catch (Exception e) {
                result.failed(hostEntry.getKey(), e.getMessage());
            }
        }

        // Fail the transaction if a single host has already been registered in
        // another organization.
        if (hostHasDifferentOwner) {
            throw new ForbiddenException(i18n.tr("Hosts are already registered in another organization."));
        }

        return result;
    }
}
