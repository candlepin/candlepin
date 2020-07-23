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

import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.HypervisorHeartbeatUpdateJob;
import org.candlepin.async.tasks.HypervisorUpdateJob;
import org.candlepin.async.tasks.HypervisorUpdateJob.HypervisorList;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.UpdateConsumerCheckIn;
import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorUpdateResultDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.resource.util.GuestMigration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Provider;

/**
 * HypervisorResource
 */
public class HypervisorResource implements HypervisorsApi {
    private static Logger log = LoggerFactory.getLogger(HypervisorResource.class);

    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerResource consumerResource;
    private I18n i18n;
    private OwnerCurator ownerCurator;
    private Provider<GuestMigration> migrationProvider;
    private ModelTranslator translator;
    private GuestIdResource guestIdResource;
    private ConsumerType hypervisorType;
    private JobManager jobManager;
    private ObjectMapper mapper;
    private PrincipalProvider principalProvider;

    @Inject
    public HypervisorResource(ConsumerResource consumerResource, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, I18n i18n, OwnerCurator ownerCurator,
        Provider<GuestMigration> migrationProvider, ModelTranslator translator,
        GuestIdResource guestIdResource, JobManager jobManager, PrincipalProvider principalProvider,
        @Named("HypervisorUpdateJobObjectMapper") final ObjectMapper mapper) {
        this.consumerResource = consumerResource;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
        this.migrationProvider = migrationProvider;
        this.translator = translator;
        this.guestIdResource = guestIdResource;
        this.jobManager = jobManager;
        this.mapper = mapper;

        this.hypervisorType = consumerTypeCurator.getByLabel(ConsumerTypeEnum.HYPERVISOR.getLabel(), true);
        this.principalProvider = principalProvider;
    }

    /**
     * @deprecated Use the asynchronous method
     * @return HypervisorCheckInResult
     */
    @Override
    @Deprecated
    @Transactional
    @UpdateConsumerCheckIn
    @SuppressWarnings({"checkstyle:indentation", "checkstyle:methodlength"})
    public HypervisorUpdateResultDTO hypervisorUpdate(
        @Verify(value = Owner.class, require = Access.READ_ONLY,
        subResource = SubResource.HYPERVISOR) String ownerKey,
        Map<String, List<String>> hostGuestMap,
        Boolean createMissing) {

        Principal principal = this.principalProvider.get();

        log.debug("Hypervisor check-in by principal: {}", principal);

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
        Set<String> allGuestIds = new HashSet<>();

        Collection<List<String>> idsLists = hostGuestMap.values();
        for (List<String> guestIds : idsLists) {
            // ignore null guest lists
            // See bzs 1332637, 1332635
            if (guestIds == null) {
                continue;
            }
            for (Iterator<String> guestIdsItr = guestIds.iterator(); guestIdsItr.hasNext();) {
                String id = guestIdsItr.next();

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

        HypervisorUpdateResultDTO result = new HypervisorUpdateResultDTO();
        for (Entry<String, List<String>> hostEntry : hostGuestMap.entrySet()) {
            String hypervisorId = hostEntry.getKey();
            // Treat null guest list as an empty list.
            // We can get an empty list here from katello due to an update
            // to ruby on rails.
            // (https://github.com/rails/rails/issues/13766#issuecomment-32730270)
            // See bzs 1332637, 1332635
            if (hostEntry.getValue() == null) {
                hostEntry.setValue(new ArrayList<>());
            }
            try {
                log.debug("Syncing virt host: {} ({} guest IDs)", hypervisorId, hostEntry.getValue().size());

                boolean hostConsumerCreated = false;
                boolean updatedType = false;
                // Attempt to find a consumer for the given hypervisorId
                Consumer consumer = null;
                if (hypervisorConsumersMap.get(hypervisorId) == null) {
                    if (!createMissing.booleanValue()) {
                        log.info("Unable to find hypervisor with id {} in org {}", hypervisorId, ownerKey);
                        result.setFailedUpdate(addFailed(result.getFailedUpdate(),
                            hypervisorId + ": " +
                            i18n.tr("Unable to find hypervisor in org \"{0}\"", ownerKey)));
                        continue;
                    }
                    log.debug("Registering new host consumer for hypervisor ID: {}", hypervisorId);
                    consumer = createConsumerForHypervisorId(hypervisorId, owner, principal);
                    hostConsumerCreated = true;
                }
                else {
                    consumer = hypervisorConsumersMap.get(hypervisorId);
                    if (!hypervisorType.getId().equals(consumer.getTypeId())) {
                        consumer.setType(hypervisorType);
                        updatedType = true;
                    }
                }
                List<GuestId> guestIds = new ArrayList<>();
                guestIdResource.populateEntities(guestIds, hostEntry.getValue());
                boolean guestIdsUpdated = addGuestIds(consumer, guestIds);

                Date now = new Date();
                consumerCurator.updateLastCheckin(consumer, now);
                consumer.setLastCheckin(now);
                // Populate the result with the processed consumer.
                if (hostConsumerCreated) {
                    result.setCreated(addHypervisorConsumerDTO(result.getCreated(), consumer));
                }
                else if (guestIdsUpdated || updatedType) {
                    result.setUpdated(addHypervisorConsumerDTO(result.getUpdated(), consumer));
                }
                else {
                    result.setUnchanged(addHypervisorConsumerDTO(result.getUnchanged(), consumer));
                }
            }
            catch (Exception e) {
                log.error("Hypervisor checkin failed", e);
                result.setFailedUpdate(addFailed(result.getFailedUpdate(),
                    hypervisorId + ": " + e.getMessage()));
            }
        }
        log.info("Summary of hypervisor checkin by principal \"{}\": {}", principal, result);
        return result;
    }

    @Override
    @UpdateConsumerCheckIn
    @SuppressWarnings("checkstyle:indentation")
    public AsyncJobStatusDTO hypervisorUpdateAsync(
        @Verify(value = Owner.class, require = Access.READ_ONLY,
        subResource = SubResource.HYPERVISOR) String ownerKey,
        Boolean createMissing, String reporterId, String hypervisorJson) {

        validateHypervisorJson(hypervisorJson);

        Principal principal = this.principalProvider.get();

        log.info("Hypervisor update by principal: {}", principal);
        Owner owner = this.getOwner(ownerKey);

        JobConfig config = HypervisorUpdateJob.createJobConfig()
            .setOwner(owner)
            .setData(hypervisorJson)
            .setCreateMissing(createMissing.booleanValue())
            .setPrincipal(principal)
            .setReporter(reporterId);

        try {
            AsyncJobStatus status = jobManager.queueJob(config);
            return translator.translate(status, AsyncJobStatusDTO.class);
        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                config.getJobKey());
            log.error(errmsg, e);
            throw new IseException(errmsg, e);
        }
    }

    @Override
    @Transactional
    public AsyncJobStatusDTO hypervisorHeartbeatUpdate(
        @Verify(value = Owner.class, require = Access.READ_ONLY,
        subResource = SubResource.HYPERVISOR)
        final String ownerKey, final String reporterId) {

        if (reporterId == null || reporterId.isEmpty()) {
            throw new BadRequestException("reporter_id is absent or empty");
        }

        final Owner owner = this.getOwner(ownerKey);

        JobConfig config = HypervisorHeartbeatUpdateJob.createJobConfig()
            .setOwner(owner)
            .setReporterId(reporterId);

        try {
            AsyncJobStatus job = this.jobManager.queueJob(config);
            return this.translator.translate(job, AsyncJobStatusDTO.class);

        }
        catch (JobException e) {
            String errmsg = this.i18n.tr("An unexpected exception occurred while scheduling job \"{0}\"",
                config.getJobKey());
            log.error(errmsg, e);
            throw new IseException(errmsg, e);
        }
    }

    /*
     * Get the owner or bust
     */
    private Owner getOwner(String ownerKey) {
        Owner owner = ownerCurator.getByKey(ownerKey);
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

        GuestMigration guestMigration = migrationProvider.get().buildMigrationManifest(withIds, consumer);
        boolean guestIdsUpdated = consumerResource.performConsumerUpdates(
            this.translator.translate(withIds, ConsumerDTO.class), consumer, guestMigration);
        if (guestIdsUpdated) {
            if (guestMigration.isMigrationPending()) {
                guestMigration.migrate();
            }
            else {
                consumerCurator.update(consumer);
            }
        }
        return guestIdsUpdated;
    }

    /*
     * Create a new hypervisor type consumer to represent the incoming hypervisorId
     */
    private Consumer createConsumerForHypervisorId(String incHypervisorId, Owner owner, Principal principal) {
        Consumer consumer = new Consumer();
        consumer.ensureUUID();
        consumer.setName(incHypervisorId);
        consumer.setType(this.hypervisorType);
        consumer.setFact("uname.machine", "x86_64");
        consumer.setGuestIds(new ArrayList<>());
        consumer.setOwner(owner);

        // Create HypervisorId
        HypervisorId hypervisorId = new HypervisorId(consumer, owner, incHypervisorId);
        hypervisorId.setOwner(owner);
        consumer.setHypervisorId(hypervisorId);

        // Create Consumer
        return consumerResource.createConsumerFromDTO(
            this.translator.translate(consumer, ConsumerDTO.class),
            this.hypervisorType,
            principal,
            null,
            owner,
            null,
            false);
    }

    private void validateHypervisorJson(String hypervisorJson) {
        if (hypervisorJson == null || hypervisorJson.isEmpty()) {
            log.debug("Host/Guest mapping provided during hypervisor update was null.");
            throw new BadRequestException(
                i18n.tr("Host to guest mapping was not provided for hypervisor update."));
        }

        try {
            HypervisorList hypervisors = mapper.readValue(hypervisorJson, HypervisorList.class);
            if (hypervisors == null || hypervisors.getHypervisors() == null) {
                log.debug("Invalid Host/Guest mapping provided during hypervisor update.");
                throw new BadRequestException(
                    i18n.tr("Invalid host to guest mapping was provided for hypervisor update."));
            }
        }
        catch (JsonProcessingException e) {
            log.error("Failed to parse Host/Guest mapping provided during hypervisor update.", e);
            throw new BadRequestException(
                i18n.tr("Invalid host to guest mapping was provided for hypervisor update."));
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
