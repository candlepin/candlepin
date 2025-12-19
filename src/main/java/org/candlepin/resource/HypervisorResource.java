/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.server.v1.HypervisorsApi;
import org.candlepin.resource.util.GuestMigration;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;


public class HypervisorResource implements HypervisorsApi {
    private static final Logger log = LoggerFactory.getLogger(HypervisorResource.class);

    private final ConsumerCurator consumerCurator;
    private final ConsumerResource consumerResource;
    private final I18n i18n;
    private final OwnerCurator ownerCurator;
    private final Provider<GuestMigration> migrationProvider;
    private final ModelTranslator translator;
    private final ConsumerType hypervisorType;
    private final JobManager jobManager;
    private final ObjectMapper mapper;
    private final PrincipalProvider principalProvider;

    @Inject
    public HypervisorResource(ConsumerResource consumerResource, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, I18n i18n, OwnerCurator ownerCurator,
        Provider<GuestMigration> migrationProvider, ModelTranslator translator, JobManager jobManager,
        PrincipalProvider principalProvider,
        @Named("HypervisorUpdateJobObjectMapper") final ObjectMapper mapper) {
        this.consumerResource = Objects.requireNonNull(consumerResource);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.i18n = Objects.requireNonNull(i18n);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.migrationProvider = Objects.requireNonNull(migrationProvider);
        this.translator = Objects.requireNonNull(translator);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.mapper = Objects.requireNonNull(mapper);
        this.principalProvider = Objects.requireNonNull(principalProvider);

        this.hypervisorType = consumerTypeCurator.getByLabel(ConsumerTypeEnum.HYPERVISOR.getLabel(), true);
    }

    @Override
    @Transactional
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

        // TODO: FIXME: Stop calling into consumer resource to do this work. Move common work to some
        // consumer service/controller or something.
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
        catch (JacksonException e) {
            log.error("Failed to parse Host/Guest mapping provided during hypervisor update.", e);
            throw new BadRequestException(
                i18n.tr("Invalid host to guest mapping was provided for hypervisor update."));
        }
    }

    public Set<String> addFailed(Set<String> failedSet, String failed) {
        if (failedSet == null) {
            failedSet = new HashSet<>();
        }
        failedSet.add(failed);

        return failedSet;
    }
}
