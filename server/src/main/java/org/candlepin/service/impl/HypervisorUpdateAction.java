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
package org.candlepin.service.impl;

import org.candlepin.auth.Principal;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.HypervisorConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorUpdateResultDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.pinsetter.tasks.HypervisorUpdateJob;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Returns {@link Result} of hypervisor update containing the result of the
 * update and a map of known hypervisor consumers.
 */
public class HypervisorUpdateAction {

    private static Logger log = LoggerFactory.getLogger(HypervisorUpdateAction.class);

    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;
    private ConsumerType hypervisorType;
    private SubscriptionServiceAdapter subAdapter;
    private ModelTranslator translator;

    public static final String CREATE = "create";
    protected static String prefix = "hypervisor_update_";

    @Inject
    public HypervisorUpdateAction(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, ConsumerResource consumerResource,
        SubscriptionServiceAdapter subAdapter, ModelTranslator translator) {
        this.consumerCurator = consumerCurator;
        this.consumerResource = consumerResource;
        this.subAdapter = subAdapter;
        this.translator = translator;
        this.hypervisorType = consumerTypeCurator.getByLabel(
            ConsumerTypeEnum.HYPERVISOR.getLabel(), true);
    }

    @Transactional
    public Result update(
        final Owner owner,
        final HypervisorUpdateJob.HypervisorList hypervisors,
        final Boolean create,
        final Principal principal,
        final String jobReporterId) {

        final String ownerKey = owner.getKey();

        log.debug("Hypervisor consumers for create/update: {}", hypervisors.getHypervisors().size());
        log.debug("Updating hypervisor consumers for org {}", ownerKey);

        Set<String> hosts = new HashSet<>();
        Set<String> guests = new HashSet<>();
        Map<String, Consumer> incomingHosts = new HashMap<>();
        parseHypervisorList(hypervisors, hosts, guests, incomingHosts);
        // TODO Need to ensure that we retrieve existing guestIds from the DB before continuing.

        // Maps virt hypervisor ID to registered consumer for that hypervisor, should one exist:
        VirtConsumerMap hypervisorKnownConsumersMap = consumerCurator
            .getHostConsumersMap(owner, hypervisors);
        HypervisorUpdateResultDTO result = new HypervisorUpdateResultDTO();
        Map<String, Consumer> systemUuidKnownConsumersMap = new HashMap<>();
        for (Consumer consumer : hypervisorKnownConsumersMap.getConsumers()) {
            if (consumer.hasFact(Consumer.Facts.SYSTEM_UUID)) {
                systemUuidKnownConsumersMap.put(
                    consumer.getFact(Consumer.Facts.SYSTEM_UUID).toLowerCase(), consumer);
            }
        }

        Map<String, GuestId> guestIds = consumerCurator.getGuestIdMap(guests, owner);
        for (String hypervisorId : hosts) {
            Consumer incoming = incomingHosts.get(hypervisorId);
            Consumer knownHost = hypervisorKnownConsumersMap.get(hypervisorId);
            // HypervisorId might be different in candlepin
            if (knownHost == null && incoming.hasFact(Consumer.Facts.SYSTEM_UUID) &&
                systemUuidKnownConsumersMap.get(
                incoming.getFact(Consumer.Facts.SYSTEM_UUID).toLowerCase()) != null) {
                knownHost = systemUuidKnownConsumersMap.get(incoming.getFact(
                    Consumer.Facts.SYSTEM_UUID).toLowerCase());
                if (knownHost != null) {
                    log.debug("Found a known host by system uuid");
                }
            }

            Consumer reportedOnConsumer = null;

            if (knownHost == null) {
                if (!create) {
                    result.addFailed(hypervisorId,
                        "Unable to find hypervisor with id " + hypervisorId + " in org " + ownerKey);
                }
                else {
                    log.debug("Registering new host consumer for hypervisor ID: {}", hypervisorId);
                    Consumer newHost = createConsumerForHypervisorId(hypervisorId, jobReporterId, owner,
                        principal, incoming);

                    // Since we just created this new consumer, we can migrate the guests immediately
                    GuestMigration guestMigration = new GuestMigration(consumerCurator)
                        .buildMigrationManifest(incoming, newHost);

                    // Now that we have the new consumer persisted, immediately migrate the guests to it
                    if (guestMigration.isMigrationPending()) {
                        guestMigration.migrate(false);
                    }

                    hypervisorKnownConsumersMap.add(hypervisorId, newHost);
                    result.addCreated(this.translator.translate(newHost, HypervisorConsumerDTO.class));
                    reportedOnConsumer = newHost;
                }
            }
            else {
                boolean hypervisorIdUpdated = updateHypervisorId(knownHost, owner, jobReporterId,
                    hypervisorId);

                boolean nameUpdated = incoming.getName() != null &&
                    (knownHost.getName() == null || !knownHost.getName().equals(incoming.getName()));
                if (nameUpdated) {
                    knownHost.setName(incoming.getName());
                }

                reportedOnConsumer = knownHost;
                if (jobReporterId != null && knownHost.getHypervisorId() != null &&
                    hypervisorId.equalsIgnoreCase(knownHost.getHypervisorId().getHypervisorId()) &&
                    knownHost.getHypervisorId().getReporterId() != null &&
                    !jobReporterId.equalsIgnoreCase(knownHost.getHypervisorId().getReporterId())) {
                    log.debug("Reporter changed for Hypervisor {} of Owner {} from {} to {}",
                        hypervisorId, ownerKey, knownHost.getHypervisorId().getReporterId(),
                        jobReporterId);
                }
                boolean typeUpdated = false;
                if (!hypervisorType.getId().equals(knownHost.getTypeId())) {
                    typeUpdated = true;
                    knownHost.setType(hypervisorType);
                }

                final GuestMigration guestMigration = new GuestMigration(consumerCurator)
                    .buildMigrationManifest(incoming, knownHost);

                final boolean factsUpdated = consumerResource.checkForFactsUpdate(knownHost, incoming);

                if (factsUpdated || guestMigration.isMigrationPending() || typeUpdated ||
                    hypervisorIdUpdated || nameUpdated) {
                    knownHost.setLastCheckin(new Date());
                    guestMigration.migrate(false);
                    result.addUpdated(this.translator.translate(knownHost, HypervisorConsumerDTO.class));
                }
                else {
                    result.addUnchanged(
                        this.translator.translate(knownHost, HypervisorConsumerDTO.class));
                }
            }
            // update reporter id if it changed
            if (jobReporterId != null && reportedOnConsumer != null &&
                reportedOnConsumer.getHypervisorId() != null &&
                (reportedOnConsumer.getHypervisorId().getReporterId() == null ||
                !jobReporterId.contentEquals(reportedOnConsumer.getHypervisorId().getReporterId()))) {
                reportedOnConsumer.getHypervisorId().setReporterId(jobReporterId);
            }
            else if (jobReporterId == null) {
                log.debug("hypervisor checkin reported asynchronously without reporter id " +
                    "for hypervisor:{} of owner:{}", hypervisorId, ownerKey);
            }
        }
        return new Result(result, hypervisorKnownConsumersMap);
    }

    private boolean updateHypervisorId(Consumer consumer, Owner owner, String reporterId,
        String hypervisorId) {

        boolean hypervisorIdUpdated = true;

        if (consumer.getHypervisorId() == null) {
            log.debug("Existing hypervisor id is null, changing hypervisor id to [" + hypervisorId + "]");
            consumer.setHypervisorId(new HypervisorId(consumer, owner, hypervisorId,
                reporterId));
        }
        else if (!hypervisorId.equalsIgnoreCase(consumer.getHypervisorId().getHypervisorId())) {
            log.debug("New hypervisor id is different, Changing hypervisor id to [" + hypervisorId + "]");
            consumer.getHypervisorId().setHypervisorId(hypervisorId);
        }
        else {
            hypervisorIdUpdated = false;
        }
        return hypervisorIdUpdated;
    }

    private void parseHypervisorList(HypervisorUpdateJob.HypervisorList hypervisorList, Set<String> hosts,
        Set<String> guests, Map<String, Consumer> incomingHosts) {
        int emptyGuestIdCount = 0;
        int emptyHypervisorIdCount = 0;

        List<Consumer> l = hypervisorList.getHypervisors();
        for (Iterator<Consumer> hypervisors = l.iterator(); hypervisors.hasNext();) {
            Consumer hypervisor = hypervisors.next();

            HypervisorId idWrapper = hypervisor.getHypervisorId();

            if (idWrapper == null) {
                continue;
            }

            String id = idWrapper.getHypervisorId();

            if (id == null) {
                continue;
            }

            if ("".equals(id)) {
                hypervisors.remove();
                emptyHypervisorIdCount++;
                continue;
            }

            incomingHosts.put(id, hypervisor);
            hosts.add(id);

            List<GuestId> guestsIdList = hypervisor.getGuestIds();

            if (guestsIdList == null || guestsIdList.isEmpty()) {
                continue;
            }

            for (Iterator<GuestId> guestIds = guestsIdList.iterator(); guestIds.hasNext();) {
                GuestId guestId = guestIds.next();
                if (StringUtils.isEmpty(guestId.getGuestId())) {
                    guestIds.remove();
                    emptyGuestIdCount++;
                }
                else {
                    guests.add(guestId.getGuestId());
                }
            }
        }

        if (emptyHypervisorIdCount > 0) {
            log.debug("Ignoring {} hypervisors with empty hypervisor IDs", emptyHypervisorIdCount);
        }

        if (emptyGuestIdCount > 0) {
            log.debug("Ignoring {} empty/null guestId(s)", emptyGuestIdCount);
        }
    }

    /*
     * Create a new hypervisor type consumer to represent the incoming hypervisorId
     */
    private Consumer createConsumerForHypervisorId(String incHypervisorId, String reporterId,
        Owner owner, Principal principal, Consumer incoming) {
        Consumer consumer = new Consumer();
        if (incoming.getName() != null) {
            consumer.setName(incoming.getName());
        }
        else {
            consumer.setName(sanitizeHypervisorId(incHypervisorId));
        }
        consumer.setType(hypervisorType);
        consumer.setFact("uname.machine", "x86_64");
        consumer.setGuestIds(new ArrayList<>());
        consumer.setLastCheckin(new Date());
        consumer.setOwner(owner);
        consumer.setAutoheal(true);
        consumer.setCanActivate(subAdapter.canActivateSubscription(consumer));
        if (owner.getDefaultServiceLevel() != null) {
            consumer.setServiceLevel(owner.getDefaultServiceLevel());
        }
        else {
            consumer.setServiceLevel("");
        }
        if (principal.getUsername() != null) {
            consumer.setUsername(principal.getUsername());
        }
        consumer.setEntitlementCount(0L);
        // TODO: Refactor this to not call resource methods directly
        consumerResource.sanitizeConsumerFacts(consumer);


        // Create HypervisorId
        HypervisorId hypervisorId = new HypervisorId(consumer, owner, incHypervisorId);
        hypervisorId.setReporterId(reporterId);
        consumer.setHypervisorId(hypervisorId);

        // TODO: Refactor this to not call resource methods directly
        consumerResource.checkForFactsUpdate(consumer, incoming);

        return consumer;
    }

    /*
     * Make sure the HypervisorId is a valid consumer name.
     */
    private String sanitizeHypervisorId(String incHypervisorId) {
        // Same validation as consumerResource.checkConsumerName
        if (incHypervisorId.indexOf('#') == 0) {
            log.debug("Hypervisor id cannot begin with # character");
            incHypervisorId = incHypervisorId.substring(1);
        }

        int max = Consumer.MAX_LENGTH_OF_CONSUMER_NAME;
        if (incHypervisorId.length() > max) {
            log.debug("Hypervisor id too long, truncating");
            incHypervisorId = incHypervisorId.substring(0, max);
        }
        return incHypervisorId;
    }

    /**
     * Result of hypervisor update operation
     */
    public static class Result {

        private final HypervisorUpdateResultDTO result;
        private final VirtConsumerMap hypervisorKnownConsumersMap;

        Result(
            final HypervisorUpdateResultDTO result,
            final VirtConsumerMap hypervisorKnownConsumersMap) {
            this.result = result;
            this.hypervisorKnownConsumersMap = hypervisorKnownConsumersMap;
        }

        public HypervisorUpdateResultDTO getResult() {
            return result;
        }

        public VirtConsumerMap getKnownConsumers() {
            return hypervisorKnownConsumersMap;
        }
    }

}
