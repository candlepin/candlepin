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

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
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
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Transactional;

import com.google.inject.Inject;

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
    private Configuration config;
    private EventSink sink;
    private EventFactory evtFactory;

    public static final String CREATE = "create";
    protected static String prefix = "hypervisor_update_";

    @Inject
    public HypervisorUpdateAction(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, ConsumerResource consumerResource,
        SubscriptionServiceAdapter subAdapter, ModelTranslator translator,
        Configuration config, EventSink sink, EventFactory evtFactory) {
        this.consumerCurator = consumerCurator;
        this.consumerResource = consumerResource;
        this.subAdapter = subAdapter;
        this.translator = translator;
        this.hypervisorType = consumerTypeCurator.getByLabel(ConsumerTypeEnum.HYPERVISOR.getLabel(), true);
        this.config = config;
        this.sink = sink;
        this.evtFactory = evtFactory;
    }

    public Result update(
        final Owner owner,
        final List<Consumer> hypervisors,
        final Boolean create,
        final String principal,
        final String jobReporterId) {

        final String ownerKey = owner.getKey();

        log.debug("Hypervisor consumers for create/update: {}", hypervisors.size());
        log.debug("Updating hypervisor consumers for org {}", ownerKey);

        Set<String> hosts = new HashSet<>();
        Set<String> guests = new HashSet<>();
        Map<String, Consumer> incomingHosts = new HashMap<>();
        HypervisorUpdateResultDTO result = new HypervisorUpdateResultDTO();
        parseHypervisorList(hypervisors, hosts, guests, incomingHosts);
        VirtConsumerMap hypervisorConsumersMap = new VirtConsumerMap();

        HypervisorUpdateAction act = this;
        Transactional<Consumer> transaction = this.consumerCurator.transactional(args ->
            act.reconcileHost((Owner) args[0], (Consumer) args[1], (HypervisorUpdateResultDTO) args[2],
            (Boolean) args[3], (String) args[4], (String) args[5]))
            .onCommit(status -> sink.sendEvents())
            .onRollback(status -> sink.rollback());

        for (String hypervisorId : hosts) {
            try {
                Consumer knownHost = transaction.execute(owner, incomingHosts.get(hypervisorId), result,
                    create, principal, jobReporterId);

                if (knownHost != null) {
                    hypervisorConsumersMap.add(knownHost.getHypervisorId().getHypervisorId(), knownHost);
                }
            }
            catch (Exception e) {
                // Nothing needs to be done here, probably. The failure should have already
                // been logged in the transactional block
                log.debug("Unexpected exception occurred while processing hypervisor {}:",
                    hypervisorId, e);
            }
        }

        return new Result(result, hypervisorConsumersMap);
    }

    public Consumer reconcileHost(Owner owner, Consumer incomingHost, HypervisorUpdateResultDTO result,
        boolean create, String principal, String jobReporterId) {
        String systemUuid = incomingHost.getFact(Consumer.Facts.SYSTEM_UUID);
        String hypervisorId = incomingHost.getHypervisorId().getHypervisorId();
        Consumer resultHost = consumerCurator.getExistingConsumerByHypervisorIdOrUuid(owner.getId(),
            hypervisorId,
            config.getBoolean(ConfigProperties.USE_SYSTEM_UUID_FOR_MATCHING) ? systemUuid : null);

        if (jobReporterId == null) {
            log.debug("hypervisor checkin reported asynchronously without reporter id " +
                "for hypervisor:{} of owner:{}", hypervisorId, owner.getKey());
        }
        if (resultHost == null) {
            if (!create) {
                result.setFailedUpdate(addFailed(result.getFailedUpdate(),
                    hypervisorId + ": " + "Unable to find hypervisor with id " + hypervisorId +
                    " in org " + owner.getKey()));
            }
            else {
                log.debug("Registering new host consumer for hypervisor ID: {}", hypervisorId);
                resultHost = createConsumerForHypervisorId(hypervisorId,
                    jobReporterId, owner, principal, incomingHost);

                // Since we just created this new consumer, we can migrate the guests immediately
                GuestMigration guestMigration = new GuestMigration(consumerCurator)
                    .buildMigrationManifest(incomingHost, resultHost);

                // Now that we have the new consumer persisted, immediately migrate the guests to it
                if (guestMigration.isMigrationPending()) {
                    guestMigration.migrate(false);
                }
                try {
                    consumerCurator.create(resultHost);
                    result.setCreated(addHypervisorConsumerDTO(result.getCreated(), resultHost));
                    Event event = evtFactory.consumerCreated(resultHost);
                    sink.queueEvent(event);
                }
                catch (Exception e) {
                    result.setFailedUpdate(addFailed(result.getFailedUpdate(),
                        hypervisorId + ": " + "Unable to create hypervisor with id " + hypervisorId +
                        " in org " + owner.getKey()));
                    throw e;
                }
            }
        }
        else {
            consumerCurator.lock(resultHost);
            boolean hypervisorIdUpdated = updateHypervisorId(resultHost, owner, jobReporterId,
                hypervisorId);

            boolean nameUpdated = incomingHost.getName() != null &&
                (resultHost.getName() == null ||
                !resultHost.getName().equals(incomingHost.getName()));
            if (nameUpdated) {
                resultHost.setName(incomingHost.getName());
            }

            if (jobReporterId != null && resultHost.getHypervisorId() != null &&
                hypervisorId.equalsIgnoreCase(resultHost.getHypervisorId().getHypervisorId()) &&
                resultHost.getHypervisorId().getReporterId() != null &&
                !jobReporterId.equalsIgnoreCase(resultHost.getHypervisorId().getReporterId())) {
                log.debug("Reporter changed for Hypervisor {} of Owner {} from {} to {}",
                    hypervisorId, owner.getKey(), resultHost.getHypervisorId().getReporterId(),
                    jobReporterId);
            }

            boolean typeUpdated = false;
            if (!hypervisorType.getId().equals(resultHost.getTypeId())) {
                typeUpdated = true;
                resultHost.setType(hypervisorType);
            }

            final GuestMigration guestMigration = new GuestMigration(consumerCurator)
                .buildMigrationManifest(incomingHost, resultHost);

            final boolean factsUpdated = consumerResource.checkForFactsUpdate(resultHost, incomingHost);

            if (factsUpdated || guestMigration.isMigrationPending() || typeUpdated ||
                hypervisorIdUpdated || nameUpdated) {

                resultHost.setLastCheckin(new Date());
                guestMigration.migrate(false);
                result.setUpdated(addHypervisorConsumerDTO(result.getUpdated(), resultHost));
            }
            else {
                result.setUnchanged(addHypervisorConsumerDTO(result.getUnchanged(), resultHost));
            }

            // update reporter id if it changed
            if (jobReporterId != null && resultHost != null &&
                resultHost.getHypervisorId() != null &&
                (resultHost.getHypervisorId().getReporterId() == null ||
                !jobReporterId.contentEquals(resultHost.getHypervisorId().getReporterId()))) {

                resultHost.getHypervisorId().setReporterId(jobReporterId);
            }

            try {
                consumerCurator.update(resultHost);
            }
            catch (Exception e) {
                result.setFailedUpdate(addFailed(result.getFailedUpdate(),
                    hypervisorId + ": " +
                    "Unable to update hypervisor with id " + hypervisorId +
                    " in org " + owner.getKey()));
                throw e;
            }
        }
        return resultHost;
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

    private void parseHypervisorList(List<Consumer> hypervisorList, Set<String> hosts,
        Set<String> guests, Map<String, Consumer> incomingHosts) {
        int emptyGuestIdCount = 0;
        int emptyHypervisorIdCount = 0;

        for (Iterator<Consumer> hypervisors = hypervisorList.iterator(); hypervisors.hasNext();) {
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
        Owner owner, String principal, Consumer incoming) {
        Consumer consumer = new Consumer();
        consumer.ensureUUID();

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
        if (principal != null) {
            consumer.setUsername(principal);
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

    public Set<HypervisorConsumerDTO> addHypervisorConsumerDTO(Set<HypervisorConsumerDTO> consumerDTOSet,
        Consumer consumer) {

        HypervisorConsumerDTO consumerDTO = this.translator.translate(consumer, HypervisorConsumerDTO.class);

        if (consumerDTOSet == null) {
            consumerDTOSet = new HashSet<>();
        }
        consumerDTOSet.add(consumerDTO);

        return consumerDTOSet;
    }

    public Set<String> addFailed(Set<String> failedSet, String failed) {
        if (failedSet == null) {
            failedSet = new HashSet<>();
        }
        failedSet.add(failed);

        return failedSet;
    }

}
