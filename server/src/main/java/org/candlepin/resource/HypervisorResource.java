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
import org.candlepin.auth.Verify;
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
import org.candlepin.pinsetter.tasks.HypervisorUpdateJob;
import org.candlepin.resource.dto.HypervisorCheckInResult;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * HypervisorResource
 */
@Path("/hypervisors")
@Api("hypervisors")
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
     * @deprecated Use the asynchronous method
     * @return HypervisorCheckInResult
     */
    @ApiOperation(notes = "Updates the list of Hypervisor Guests Allows agents such as " +
        "virt-who to update its host list and associate the guests for each host. This is " +
        "typically used when a host is unable to register to candlepin via subscription" +
        " manager.  In situations where consumers already exist it is probably best not " +
        "to allow creation of new hypervisor consumers.  Most consumers do not have a" +
        " hypervisorId attribute, so that should be added manually when necessary by the " +
        "management environment. @deprecated Use the asynchronous method",
        value = "hypervisorUpdate")
    @ApiResponses({ @ApiResponse(code = 202, message = "") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    @Transactional
    @SuppressWarnings("checkstyle:indentation")
    public HypervisorCheckInResult hypervisorUpdate(
        Map<String, List<GuestId>> hostGuestMap, @Context Principal principal,
        @QueryParam("owner") @Verify(value = Owner.class,
            require = Access.READ_ONLY,
            subResource = SubResource.HYPERVISOR) String ownerKey,
        @ApiParam("specify whether or not to create missing hypervisors." +
            "Default is true.  If false is specified, hypervisorIds that are not found" +
            "will result in failed entries in the resulting HypervisorCheckInResult")
        @QueryParam("create_missing") @DefaultValue("true") boolean createMissing) {
        log.debug("Hypervisor check-in by principal: " + principal);

        if (hostGuestMap == null) {
            log.debug("Host/Guest mapping provided during hypervisor checkin was null.");
            throw new BadRequestException(
                i18n.tr("Host to guest mapping was not provided for hypervisor check-in."));
        }

        Owner owner = this.getOwner(ownerKey);

        if (hostGuestMap.remove("") != null) {
            log.warn("Ignoring empty hypervisor id");
        }

        // Maps virt hypervisor ID to registered consumer for that hypervisor, should one exist:
        VirtConsumerMap hypervisorConsumersMap =
            consumerCurator.getHostConsumersMap(owner, hostGuestMap.keySet());

        int emptyGuestIdCount = 0;
        Set<String> allGuestIds = new HashSet<String>();

        Collection<List<GuestId>> idsLists = hostGuestMap.values();
        for (List<GuestId> guestIds : idsLists) {
            // ignore null guest lists
            // See bzs 1332637, 1332635
            if (guestIds == null) {
                continue;
            }
            for (Iterator<GuestId> guestIdsItr = guestIds.iterator(); guestIdsItr.hasNext();) {
                String id = guestIdsItr.next().getGuestId();

                if (StringUtils.isEmpty(id)) {
                    emptyGuestIdCount++;
                    guestIdsItr.remove();
                }
                else {
                    allGuestIds.add(id);
                }
            }
        }

        if (emptyGuestIdCount > 0) {
            log.warn("Ignoring {} empty/null guest id(s).", emptyGuestIdCount);
        }

        // Maps virt guest ID to registered consumer for guest, if one exists:
        VirtConsumerMap guestConsumersMap = consumerCurator.getGuestConsumersMap(
            owner, allGuestIds);

        HypervisorCheckInResult result = new HypervisorCheckInResult();
        for (Entry<String, List<GuestId>> hostEntry : hostGuestMap.entrySet()) {
            String hypervisorId = hostEntry.getKey();
            // Treat null guest list as an empty list.
            // We can get an empty list here from katello due to an update
            // to ruby on rails.
            // (https://github.com/rails/rails/issues/13766#issuecomment-32730270)
            // See bzs 1332637, 1332635
            if (hostEntry.getValue() == null) {
                hostEntry.setValue(new ArrayList<GuestId>());
            }
            try {
                log.debug("Syncing virt host: {} ({} guest IDs)", hypervisorId, hostEntry.getValue().size());

                boolean hostConsumerCreated = false;
                // Attempt to find a consumer for the given hypervisorId
                Consumer consumer = null;
                if (hypervisorConsumersMap.get(hypervisorId) == null) {
                    if (!createMissing) {
                        log.info("Unable to find hypervisor with id {} in org {}", hypervisorId, ownerKey);
                        result.failed(hypervisorId, i18n.tr(
                            "Unable to find hypervisor in org ''{0}''", ownerKey));
                        continue;
                    }
                    log.debug("Registering new host consumer for hypervisor ID: {}", hypervisorId);
                    consumer = createConsumerForHypervisorId(hypervisorId, owner, principal);
                    hostConsumerCreated = true;
                }
                else {
                    consumer = hypervisorConsumersMap.get(hypervisorId);
                }
                List<GuestId> startGuests =  consumer.getGuestIds();
                boolean guestIdsUpdated = addGuestIds(consumer, hostEntry.getValue(), guestConsumersMap);

                if (guestIdsUpdated) {
                    consumerResource.checkForGuestsMigration(consumer, startGuests,
                        consumer.getGuestIds(), guestConsumersMap);
                }

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
        log.info("Summary of hypervisor checkin by principal \"{}\": {}", principal, result);
        return result;
    }

    @ApiOperation(notes = "Creates or Updates the list of Hypervisor hosts Allows agents such" +
        " as virt-who to update hosts' information . This is typically used when a host is" +
        " unable to register to candlepin via subscription manager. In situations where " +
        "consumers already exist it is probably best not to allow creation of new hypervisor" +
        " consumers.  Most consumers do not have a hypervisorId attribute, so that should be" +
        " added manually when necessary by the management environment. Default is true.  " +
        "If false is specified, hypervisorIds that are not found will result in a failed " +
        "state of the job.", value = "hypervisorUpdateAsync")
    @ApiResponses({ @ApiResponse(code = 202, message = "") })
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    @Path("/{owner}")
    @SuppressWarnings("checkstyle:indentation")
    public JobDetail hypervisorUpdateAsync(
        String hypervisorJson, @Context Principal principal,
        @PathParam("owner") @Verify(value = Owner.class,
            require = Access.READ_ONLY,
            subResource = SubResource.HYPERVISOR) String ownerKey,
        @ApiParam("specify whether or not to create missing hypervisors." +
            "Default is true.  If false is specified, hypervisorIds that are not found" +
            "will result in failed entries in the resulting HypervisorCheckInResult")

        @QueryParam("create_missing") @DefaultValue("true") boolean createMissing,
        @QueryParam("reporter_id") String reporterId) {

        if (hypervisorJson == null || hypervisorJson.isEmpty()) {
            log.debug("Host/Guest mapping provided during hypervisor update was null.");
            throw new BadRequestException(
                i18n.tr("Host to guest mapping was not provided for hypervisor update."));
        }

        log.info("Hypervisor update by principal: " + principal);
        Owner owner = this.getOwner(ownerKey);

        return HypervisorUpdateJob.forOwner(owner, hypervisorJson, createMissing, principal, reporterId);
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
        VirtConsumerMap guestConsumerMap) {
        Consumer withIds = new Consumer();
        withIds.setGuestIds(guestIds);
        boolean guestIdsUpdated =
            consumerResource.performConsumerUpdates(withIds, consumer, guestConsumerMap);
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
        HypervisorId hypervisorId = new HypervisorId(consumer, incHypervisorId);
        consumer.setHypervisorId(hypervisorId);
        // Create Consumer
        return consumerResource.create(consumer, principal, null, owner.getKey(), null, false);
    }

}
