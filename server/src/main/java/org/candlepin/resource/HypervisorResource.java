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
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.resource.dto.HypervisorCheckInResult;
import org.candlepin.resource.dto.HypervisorFactUpdateResult;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * HypervisorResource
 */
@Path("/hypervisors")
public class HypervisorResource {
    private static Logger log = LoggerFactory.getLogger(HypervisorResource.class);
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;
    private I18n i18n;
    private OwnerCurator ownerCurator;

    @Inject
    public HypervisorResource(ConsumerResource consumerResource,
        ConsumerCurator consumerCurator, I18n i18n, OwnerCurator ownerCurator) {
        this.consumerResource = consumerResource;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
    }

    /**
     * Updates the list of Hypervisor Guests
     * <p>
     * Allows agents such as virt-who to update its host list and associate the
     * guests for each host. This is typically used when a host is unable to
     * register to candlepin via subscription manager.
     * <p>
     * In situations where consumers already exist it is probably best not to
     * allow creation of new hypervisor consumers.  Most consumers do not
     * have a hypervisorId attribute, so that should be added manually
     * when necessary by the management environment.
     *
     * @param hostGuestMap a mapping of host_id to list of guestIds
     * @param principal
     * @param ownerKey key of owner to update
     * @param createMissing specify whether or not to create missing hypervisors.
     * Default is true.  If false is specified, hypervisorIds that are not found
     * will result in failed entries in the resulting HypervisorCheckInResult
     * @return a HypervisorCheckInResult object
     *
     * @httpcode 202
     * @httpcode 200
     *
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public HypervisorCheckInResult hypervisorCheckIn(
        Map<String, List<GuestId>> hostGuestMap, @Context Principal principal,
        @QueryParam("owner") @Verify(value = Owner.class,
            require = Access.READ_ONLY,
            subResource = SubResource.HYPERVISOR) String ownerKey,
        @QueryParam("create_missing") @DefaultValue("true") boolean createMissing) {
        log.info("Hypervisor check-in by principal: " + principal);

        if (hostGuestMap == null) {
            log.debug("Host/Guest mapping provided during hypervisor checkin was null.");
            throw new BadRequestException(
                i18n.tr("Host to guest mapping was not provided for hypervisor checkin."));
        }

        Owner owner = this.getOwner(ownerKey);

        // Maps virt hypervisor ID to registered consumer for that hypervisor, should one exist:
        VirtConsumerMap hypervisorConsumersMap =
                consumerCurator.getHostConsumersMap(owner, hostGuestMap.keySet());

        List<String> allGuestIds = new LinkedList<String>();
        for (Entry<String, List<GuestId>> hostEntry : hostGuestMap.entrySet()) {
            for (GuestId gid : hostEntry.getValue()) {
                allGuestIds.add(gid.getGuestId());
            }
        }
        // Maps virt guest ID to registered consumer for guest, if one exists:
        VirtConsumerMap guestConsumersMap = consumerCurator.getGuestConsumersMap(
                owner, allGuestIds);

        // Maps virt guest ID to registered consumer for hypervisor, if one exists:
        VirtConsumerMap guestHypervisorConsumers = consumerCurator.
                getGuestsHostMap(owner, allGuestIds);

        HypervisorCheckInResult result = new HypervisorCheckInResult();
        for (Entry<String, List<GuestId>> hostEntry : hostGuestMap.entrySet()) {
            String hypervisorId = hostEntry.getKey();
            try {
                log.info("Syncing virt host: " + hypervisorId +
                        " (" + hostEntry.getValue().size() + " guest IDs)");

                boolean hostConsumerCreated = false;
                // Attempt to find a consumer for the given hypervisorId
                Consumer consumer = null;
                if (hypervisorConsumersMap.get(hypervisorId) == null) {
                    if (!createMissing) {
                        log.info("Unable to find hypervisor with id " +
                            hypervisorId + " in org " + ownerKey);
                        result.failed(hypervisorId, i18n.tr(
                            "Unable to find hypervisor in org ''{0}''", ownerKey));
                        continue;
                    }
                    log.info("Registering new host consumer for hypervisor ID: {}",
                            hypervisorId);
                    // Create new consumer
                    consumer = createConsumerForHypervisorId(
                        hypervisorId, owner, principal);
                    hostConsumerCreated = true;
                }
                else {
                    consumer = hypervisorConsumersMap.get(hypervisorId);
                }

                boolean guestIdsUpdated = addGuestIds(consumer, hostEntry.getValue(),
                        guestConsumersMap, guestHypervisorConsumers);

                // Populate the result with the processed consumer.
                if (hostConsumerCreated) {
                    result.created(consumer);
                }
                else if (guestIdsUpdated) {
                    result.updated(consumer);
                }
                else {
                    result.unchanged(consumer);
                }
            }
            catch (Exception e) {
                log.error("Hypervisor checkin failed", e);
                result.failed(hypervisorId, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Updates the list of Hypervisor Host facts
     * <p>
     * Allows agents such as virt-who to update a hosts fact list. This is typically
     * used when a host is unable to register to candlepin via subscription manager.
     * <p>
     * In situations where consumers already exist it is probably best not to
     * allow creation of new hypervisor consumers.  Most consumers do not
     * have a hypervisorId attribute, so that should be added manually
     * when necessary by the management environment.
     *
     * @param hypervisorId the system UUID if the hypervisor
     * @param principal
     * @param ownerKey key of owner to update
     * @param createMissing specify whether or not to create missing hypervisors.
     * Default is true.  If false is specified, hypervisorIds that are not found
     * will result in failed entries in the resulting HypervisorFactUpdateResult
     * @return a HypervisorFactUpdateResult object
     *
     * @httpcode 202
     * @httpcode 200
     *
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Path("/{hypervisor_system_uuid}")
    public HypervisorFactUpdateResult updateHypervisorFacts(
        @PathParam("hypervisor_system_uuid") String hypervisorId,
        Map<String, String> factMap, @Context Principal principal,
        @QueryParam("owner") @Verify(value = Owner.class,
            require = Access.READ_ONLY,
            subResource = SubResource.HYPERVISOR) String ownerKey,
        @QueryParam("create_missing") @DefaultValue("true") boolean createMissing) {

        log.info("Hypervisor fact update by principal: " + principal);

        if (factMap == null) {
            log.debug("fact mapping provided during hypervisor checkin was null.");
            throw new BadRequestException(
                i18n.tr("fact mapping was not provided for hypervisor fact update."));
        }

        Owner owner = this.getOwner(ownerKey);

        HypervisorFactUpdateResult result = new HypervisorFactUpdateResult();
        boolean hostConsumerCreated = false;
        Consumer host = consumerCurator.getHypervisor(hypervisorId, owner);
        if (host == null) {
            if (!createMissing) {
                log.info("Unable to find hypervisor with id " + hypervisorId + " in org " + ownerKey);
                result.failed(hypervisorId, i18n.tr("Unable to find hypervisor in org ''{0}''", ownerKey));
                return result;
            }
            log.info("Registering new host consumer for hypervisor ID: {}", hypervisorId);
            host = this.createConsumerForHypervisorId(hypervisorId, owner, principal);
            hostConsumerCreated = true;
        }

        boolean factsChanged = false;
        for (String key : factMap.keySet()) {
            String value = factMap.get(key);
            if (!value.equals(host.getFact(key))) {
                host.setFact(key, value);
                factsChanged = true;
            }
        }
        // Populate the result with the processed consumer.
        if (hostConsumerCreated) {
            result.created(host);
            consumerCurator.create(host);
        }
        else if (factsChanged) {
            result.updated(host);
            consumerCurator.update(host);
        }
        else {
            result.unchanged(host);
        }

        return result;
    }

    /*
     * Get the owner or bust
     */
    private Owner getOwner(String ownerKey) {
        Owner owner = ownerCurator.lookupByKey(ownerKey);
        if (owner == null) {
            throw new NotFoundException(i18n.tr(
                "owner with key: {0} was not found.", ownerKey));
        }
        return owner;
    }

    /*
     * Add a list of guestIds to the given consumer,
     * return whether or not there was any change
     */
    private boolean addGuestIds(Consumer consumer, List<GuestId> guestIds,
            VirtConsumerMap guestConsumerMap,
            VirtConsumerMap guestHypervisorConsumers) {
        Consumer withIds = new Consumer();
        withIds.setGuestIds(guestIds);
        boolean guestIdsUpdated =
            consumerResource.performConsumerUpdates(withIds, consumer,
                    guestConsumerMap, guestHypervisorConsumers);
        if (guestIdsUpdated) {
            consumerCurator.update(consumer);
        }
        return guestIdsUpdated;
    }

    /*
     * Create a new hypervisor type consumer to represent the incoming hypervisorId
     */
    private Consumer createConsumerForHypervisorId(String incHypervisorId,
            Owner owner, Principal principal) {
        Consumer consumer = new Consumer();
        consumer.setName(incHypervisorId);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.HYPERVISOR));
        consumer.setFact("uname.machine", "x86_64");
        consumer.setGuestIds(new ArrayList<GuestId>());
        consumer.setOwner(owner);
        // Create HypervisorId
        HypervisorId hypervisorId =
            new HypervisorId(consumer, incHypervisorId);
        consumer.setHypervisorId(hypervisorId);
        // Create Consumer
        return consumerResource.create(consumer,
            principal, null, owner.getKey(), null);
    }
}
