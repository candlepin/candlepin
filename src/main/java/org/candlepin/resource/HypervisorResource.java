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

import org.candlepin.auth.Principal;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.GoneException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.GuestId;
import org.candlepin.resource.dto.HypervisorCheckInResult;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.log4j.Logger;

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
    private PoolManager poolManager;
    private ConsumerResource consumerResource;
    private DeletedConsumerCurator deletedConsumerCurator;

    @Inject
    public HypervisorResource(ConsumerResource consumerResource, PoolManager poolManager,
        ConsumerCurator consumerCurator, DeletedConsumerCurator deletedConsumerCurator) {
        this.consumerResource = consumerResource;
        this.poolManager = poolManager;
        this.consumerCurator = consumerCurator;
        this.deletedConsumerCurator = deletedConsumerCurator;
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
        HypervisorCheckInResult result = new HypervisorCheckInResult();
        for (Entry<String, List<GuestId>> hostEntry : hostGuestMap.entrySet()) {
            try {
                log.info("Attempting to register host: " + hostEntry.getKey());
                List<GuestId> guestIds = hostEntry.getValue();
                DeletedConsumer deletedHypervisor =
                    deletedConsumerCurator.findByConsumerUuid(hostEntry.getKey());
                if (deletedHypervisor != null) {
                    throw new GoneException("Hypervisor " + hostEntry.getKey() +
                                                " has been deleted previously");
                }
                Consumer consumer = consumerCurator.findByUuid(hostEntry.getKey());
                if (consumer == null) {
                    // Create new consumer
                    consumer = new Consumer();
                    consumer.setName(hostEntry.getKey());
                    consumer.setUuid(hostEntry.getKey());
                    consumer.setType(new ConsumerType(ConsumerTypeEnum.HYPERVISOR));
                    consumer.setFact("uname.machine", "x86_64");
                    consumer = consumerResource.create(consumer, principal, null, ownerKey,
                        null);
                    result.created(consumer);
                }
                /* commented out per 768872
                // Revoke all entitlements from the host if no guests were reported.
                else if (guestIds == null || guestIds.isEmpty()) {
                    poolManager.revokeAllEntitlements(consumer);
                }
                */

                Consumer withIds = new Consumer();
                withIds.setGuestIds(guestIds);
                if (consumerResource.performConsumerUpdates(withIds, consumer)) {
                    consumerCurator.update(consumer);
                }
                result.updated(consumer);
            }
            catch (Exception e) {
                result.failed(hostEntry.getKey(), e.getMessage());
            }
        }
        return result;
    }
}
