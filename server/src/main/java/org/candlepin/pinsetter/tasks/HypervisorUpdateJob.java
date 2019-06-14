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
package org.candlepin.pinsetter.tasks;

import static org.quartz.JobBuilder.newJob;

import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorUpdateResultDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.JobCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.service.impl.HypervisorUpdateAction;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.apache.log4j.MDC;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Asynchronous job for refreshing the entitlement pools for specific
 * {@link Owner}. A job will wait for a running job of the same Owner to
 * finish before beginning execution
 */
public class HypervisorUpdateJob extends KingpinJob {

    private static Logger log = LoggerFactory.getLogger(HypervisorUpdateJob.class);

    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ModelTranslator translator;
    private HypervisorUpdateAction hypervisorUpdateAction;
    private I18n i18n;
    private ObjectMapper mapper;

    public static final String CREATE = "create";
    private static final String REPORTER_ID = "reporter_id";
    private static final String DATA = "data";
    private static final String PRINCIPAL = "principal";
    protected static String prefix = "hypervisor_update_";
    private static final int BULK_SIZE = 10;

    @Inject
    public HypervisorUpdateJob(
        final OwnerCurator ownerCurator,
        final ConsumerCurator consumerCurator,
        final ModelTranslator translator,
        final HypervisorUpdateAction hypervisorUpdateAction,
        final I18n i18n,
        @Named("HypervisorUpdateJobObjectMapper") final ObjectMapper objectMapper) {
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.translator = Objects.requireNonNull(translator);
        this.hypervisorUpdateAction = Objects.requireNonNull(hypervisorUpdateAction);
        this.i18n = Objects.requireNonNull(i18n);
        this.mapper = Objects.requireNonNull(objectMapper);
    }

    public static JobStatus scheduleJob(JobCurator jobCurator, Scheduler scheduler, JobDetail detail,
        Trigger trigger) throws SchedulerException {

        JobStatus result = jobCurator.getByClassAndTarget(
            detail.getJobDataMap().getString(JobStatus.TARGET_ID),
            HypervisorUpdateJob.class);

        if (result == null) {
            return KingpinJob.scheduleJob(jobCurator, scheduler, detail, trigger);
        }

        log.debug("Scheduling job without a trigger: {}", detail.getKey().getName());
        return KingpinJob.scheduleJob(jobCurator, scheduler, detail, null);
    }

    public static boolean isSchedulable(JobCurator jobCurator, JobStatus status) {
        JobStatus nextJob = jobCurator.getNextByClassAndTarget(status.getTargetId(),
            HypervisorUpdateJob.class);
        return nextJob != null && nextJob.getId().equals(status.getId());
    }

    /**
     * {@inheritDoc}
     *
     * Executes {@link ConsumerResource#create(ConsumerDTO, Principal, String, String, String, boolean)}
     * Executes {@link ConsumerResource#updateConsumer(String, ConsumerDTO, Principal)}
     * as a pinsetter job.
     *
     * @param context the job's execution context
     */
    @SuppressWarnings({ "checkstyle:indentation", "checkstyle:methodlength" })
    public void toExecute(final JobExecutionContext context) throws JobExecutionException {
        try {
            final JobDataMap map = context.getMergedJobDataMap();
            final String ownerKey = map.getString(JobStatus.TARGET_ID);
            final Boolean create = map.getBoolean(CREATE);
            final Principal principal = (Principal) map.get(PRINCIPAL);
            final String jobReporterId = map.getString(REPORTER_ID);

            final Owner owner = ownerCurator.getByKey(ownerKey);
            if (owner == null) {
                context.setResult("Nothing to do. Owner does not exist");
                log.warn("Hypervisor update attempted against non-existent org id \"{}\"", ownerKey);
                return;
            }

            if (owner.isAutobindDisabled() || owner.isContentAccessEnabled()) {
                final String caMessage = owner.isContentAccessEnabled() ?
                    " because of the content access mode setting" : "";
                log.debug("Could not update host/guest mapping. Auto-Attach is disabled for owner {}{}",
                    owner.getKey(), caMessage);
                throw new BadRequestException(
                    i18n.tr("Could not update host/guest mapping. Auto-attach is disabled for owner {0}{1}.",
                        owner.getKey(), caMessage));
            }

            final HypervisorList hypervisors = parsedHypervisors(map);
            final HypervisorUpdateAction.Result updateResult = hypervisorUpdateAction.update(
                owner, hypervisors.getHypervisors(), create, principal.getUsername(), jobReporterId);
            final HypervisorUpdateResultDTO result = updateResult.getResult();
            final VirtConsumerMap hypervisorKnownConsumersMap = updateResult.getKnownConsumers();

            final List<Consumer> created = new ArrayList<>();
            final List<Consumer> updated = new ArrayList<>();
            for (Consumer consumer : hypervisorKnownConsumersMap.getConsumers()) {
                final HypervisorConsumerDTO translated = this.translator.translate(
                    consumer, HypervisorConsumerDTO.class);
                if (result.wasCreated(translated)) {
                    created.add(consumer);
                }
                else {
                    updated.add(consumer);
                }
            }

            doInBulk(created, consumers -> consumerCurator.saveAll(consumers, false, false));
            doInBulk(updated, consumers -> consumerCurator.bulkUpdate(consumers, false));

            log.info("Summary for report from {} by principal {}\n {}", jobReporterId, principal, result);
            context.setResult(result);
        }
        catch (Exception e) {
            log.error("HypervisorUpdateJob encountered a problem.", e);
            context.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    private HypervisorList parsedHypervisors(final JobDataMap map) throws IOException {
        final byte[] data = (byte[]) map.get(DATA);
        final String json = decompress(data);
        return mapper.readValue(json, HypervisorList.class);
    }

    private void doInBulk(final List<Consumer> created,
        final java.util.function.Consumer<Set<Consumer>> action) {
        Lists.partition(created, BULK_SIZE)
            .stream()
            .map(HashSet::new)
            .forEach(action);
    }

    /**
     * Creates a {@link JobDetail} that runs this job for the given {@link Owner}.
     *
     * @param owner the owner to refresh
     * @return a {@link JobDetail} that describes the job run
     */
    public static JobDetail forOwner(Owner owner, String data, Boolean create, Principal principal,
        String reporterId) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getKey());
        map.put(JobStatus.OWNER_ID, owner.getKey());
        map.put(JobStatus.OWNER_LOG_LEVEL, owner.getLogLevel());
        map.put(CREATE, create);
        map.put(DATA, compress(data));
        map.put(PRINCIPAL, principal);

        if (reporterId != null) {
            map.put(REPORTER_ID, reporterId);
        }
        map.put(JobStatus.CORRELATION_ID, MDC.get(LoggingFilter.CSID_KEY));

        // Not sure if this is the best way to go:
        // Give each job a UUID to ensure that it is unique
        return newJob(HypervisorUpdateJob.class)
            .withIdentity(prefix + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .storeDurably(true) // required if we have to postpone the job
            .build();
    }

    private static byte[] compress(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            OutputStream out = new DeflaterOutputStream(baos);
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.close();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
        return baos.toByteArray();
    }

    private static String decompress(byte[] bytes) {
        InputStream in = new InflaterInputStream(new ByteArrayInputStream(bytes));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Class for holding the list of consumers in the stored json text
     *
     * @author wpoteat
     */
    public static class HypervisorList {
        private List<Consumer> hypervisors;

        public List<Consumer> getHypervisors() {
            return this.hypervisors;
        }

        public void setConsumers(List<Consumer> hypervisors) {
            this.hypervisors = hypervisors;
        }
    }

}
