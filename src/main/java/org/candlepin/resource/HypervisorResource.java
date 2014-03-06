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
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.dto.HypervisorCheckInResult;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
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
    private static Logger log = LoggerFactory.getLogger(HypervisorResource.class);
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;
    private I18n i18n;
    private OwnerCurator ownerCurator;
    private Config config;

    @Inject
    public HypervisorResource(ConsumerResource consumerResource,
        ConsumerCurator consumerCurator, I18n i18n, OwnerCurator ownerCurator,
        Config config) {
        this.consumerResource = consumerResource;
        this.consumerCurator = consumerCurator;
        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
        this.config = config;
    }

    /**
     * Allows agents such as virt-who to update its host list and associate the
     * guests for each host. This is typically used when a host is unable to
     * register to candlepin via subscription manager.
     *
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
     * @return HypervisorCheckInResult
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

        Owner owner = this.getOwner(ownerKey);

        HypervisorCheckInResult result = new HypervisorCheckInResult();
        for (Entry<String, List<GuestId>> hostEntry : hostGuestMap.entrySet()) {
            try {
                log.info("Checking virt host: " + hostEntry.getKey());

                boolean hostConsumerCreated = false;
                // Attempt to find a consumer for the given hypervisorId
                Consumer consumer =
                    consumerCurator.getHypervisor(hostEntry.getKey(), owner);
                if (consumer == null) {
                    if (!createMissing) {
                        log.info("Unable to find hypervisor with id " +
                            hostEntry.getKey() + " in org " + ownerKey);
                        result.failed(hostEntry.getKey(), i18n.tr(
                            "Unable to find hypervisor in org ''{0}''", ownerKey));
                        continue;
                    }
                    if (this.blockDuplicateHypervisors() &&
                        consumerCurator.isHypervisorIdUsed(hostEntry.getKey())) {
                        // If the hypervisorID is being used, we know it is not in this org
                        log.info("Hypervisor id " + hostEntry.getKey() +
                            " is in use by another org");
                        result.failed(hostEntry.getKey(), i18n.tr(
                            "Hypervisor ''{0}'' is in use by another org", ownerKey));
                        continue;
                    }
                    log.info("Registering new host consumer");
                    // Create new consumer
                    consumer = createConsumerForHypervisorId(
                        hostEntry.getKey(), owner, principal);
                    hostConsumerCreated = true;
                }

                boolean guestIdsUpdated = addGuestIds(consumer, hostEntry.getValue());

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
                result.failed(hostEntry.getKey(), e.getMessage());
            }
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
    private boolean addGuestIds(Consumer consumer, List<GuestId> guestIds) {
        Consumer withIds = new Consumer();
        withIds.setGuestIds(guestIds);
        boolean guestIdsUpdated =
            consumerResource.performConsumerUpdates(withIds, consumer);
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

    private boolean blockDuplicateHypervisors() {
        return config.getBoolean(ConfigProperties.BLOCK_DUPLICATE_HYPERVISOR_IDS);
    }
}
