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
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.JobCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.dto.HypervisorUpdateResult;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.MDC;
import org.hibernate.Hibernate;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private ConsumerResource consumerResource;
    private I18n i18n;

    public static final String CREATE = "create";
    public static final String REPORTER_ID = "reporter_id";
    public static final String DATA = "data";
    public static final String PRINCIPAL = "principal";
    protected static String prefix = "hypervisor_update_";

    @Inject
    public HypervisorUpdateJob(OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
        ConsumerResource consumerResource, I18n i18n) {
        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.consumerResource = consumerResource;
        this.i18n = i18n;
    }

    public static JobStatus scheduleJob(JobCurator jobCurator,
        Scheduler scheduler, JobDetail detail,
        Trigger trigger) throws SchedulerException {
        JobStatus result = jobCurator.getByClassAndTarget(
            detail.getJobDataMap().getString(JobStatus.TARGET_ID),
            HypervisorUpdateJob.class);
        if (result == null) {
            return KingpinJob.scheduleJob(jobCurator, scheduler, detail, trigger);
        }
        log.debug("Scheduling job without a trigger: " + detail.getKey().getName());
        JobStatus status = KingpinJob.scheduleJob(jobCurator, scheduler, detail, null);
        return status;
    }

    public static boolean isSchedulable(JobCurator jobCurator, JobStatus status) {
        JobStatus nextJob = jobCurator.getNextByClassAndTarget(status.getTargetId(),
            HypervisorUpdateJob.class);
        return nextJob != null && nextJob.getId().equals(status.getId());
    }

    private void parseHypervisorList(HypervisorList hypervisorList, Set<String> hosts,
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

    /**
     * {@inheritDoc}
     *
     * Executes {@link ConsumerResource#create(org.candlepin.model.Consumer, org.candlepin.auth.Principal,
     *  java.utl.String, java.utl.String, java.utl.String)}
     * Executes (@link ConusmerResource#performConsumerUpdates(java.utl.String, org.candlepin.model.Consumer)}
     * as a pinsetter job.
     *
     * @param context the job's execution context
     */
    @Transactional
    @SuppressWarnings("checkstyle:indentation")
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap map = context.getMergedJobDataMap();
            String ownerKey = map.getString(JobStatus.TARGET_ID);
            Boolean create = map.getBoolean(CREATE);
            Principal principal = (Principal) map.get(PRINCIPAL);
            String jobReporterId = map.getString(REPORTER_ID);

            HypervisorUpdateResult result = new HypervisorUpdateResult();

            Owner owner = ownerCurator.lookupByKey(ownerKey);
            if (owner == null) {
                context.setResult("Nothing to do. Owner does not exist");
                log.warn("Hypervisor update attempted against non-existent org id ''{0}''", ownerKey);
                return;
            }

            if (owner.autobindDisabled()) {
                log.debug("Could not update host/guest mapping. Auto-Attach is disabled for owner {}",
                    owner.getKey());
                throw new BadRequestException(
                    i18n.tr("Could not update host/guest mapping. Auto-attach is disabled for owner {0}.",
                        owner.getKey()));
            }

            byte[] data = (byte[]) map.get(DATA);
            String json = decompress(data);
            HypervisorList hypervisors = (HypervisorList) Util.fromJson(json, HypervisorList.class);
            log.debug("Hypervisor consumers for create/update: {}", hypervisors.getHypervisors().size());
            log.debug("Updating hypervisor consumers for org {0}", ownerKey);

            Set<String> hosts = new HashSet<String>();
            Set<String> guests = new HashSet<String>();
            Map<String, Consumer> incomingHosts = new HashMap<String, Consumer>();
            parseHypervisorList(hypervisors, hosts, guests, incomingHosts);

            // Maps virt hypervisor ID to registered consumer for that hypervisor, should one exist:
            VirtConsumerMap hypervisorConsumersMap = consumerCurator.getHostConsumersMap(owner, hosts);

            // Maps virt guest ID to registered consumer for guest, if one exists:
            VirtConsumerMap guestConsumersMap = consumerCurator.getGuestConsumersMap(owner, guests);

            for (String hypervisorId : hosts) {
                Consumer knownHost = hypervisorConsumersMap.get(hypervisorId);
                Consumer incoming = incomingHosts.get(hypervisorId);
                Consumer reportedOnConsumer = null;
                if (knownHost == null) {
                    if (!create) {
                        result.failed(hypervisorId, "Unable to find hypervisor with id " +
                            hypervisorId + " in org " + ownerKey);
                    }
                    else {
                        log.debug("Registering new host consumer for hypervisor ID: {}", hypervisorId);
                        Consumer newHost = createConsumerForHypervisorId(hypervisorId, owner, principal);
                        consumerResource.performConsumerUpdates(incoming, newHost, guestConsumersMap, false);
                        consumerResource.create(newHost, principal, null, owner.getKey(), null, false);
                        hypervisorConsumersMap.add(hypervisorId, newHost);
                        result.created(updateCheckinTime(newHost));
                        reportedOnConsumer = newHost;
                    }
                }
                else {
                    reportedOnConsumer = knownHost;
                    if (jobReporterId != null && knownHost.getHypervisorId() != null &&
                        hypervisorId.equalsIgnoreCase(knownHost.getHypervisorId().getHypervisorId()) &&
                        knownHost.getHypervisorId().getReporterId() != null &&
                        !jobReporterId.equalsIgnoreCase(knownHost.getHypervisorId().getReporterId())) {
                        log.debug("Reporter changed for Hypervisor {} of Owner {} from {} to {}",
                            hypervisorId, ownerKey, knownHost.getHypervisorId().getReporterId(),
                            jobReporterId);
                    }
                    /* Impl. Note (2017-10-27):
                    Now that events no longer serialize whole Objects for the purpose of storing
                    the oldEntity field, forcing initialization of lazy-loaded collections as a side-effect,
                    we need to force their initialization before we get to save the result,
                    to avoid LazyInitializationExceptions when we get to fetch it from the db later.
                     */
                    Hibernate.initialize(knownHost.getCapabilities());
                    Hibernate.initialize(knownHost.getInstalledProducts());
                    Hibernate.initialize(knownHost.getEntitlements());
                    if (consumerResource.performConsumerUpdates(incoming, knownHost, guestConsumersMap,
                        false)) {
                        consumerCurator.update(knownHost);
                        result.updated(updateCheckinTime(knownHost));
                    }
                    else {
                        result.unchanged(updateCheckinTime(knownHost));
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
            log.info("Summary for report from {} by principal {}\n {}", jobReporterId, principal, result);
            context.setResult(result);
        }
        catch (Exception e) {
            log.error("HypervisorUpdateJob encountered a problem.", e);
            context.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
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
        map.put(CREATE, create);
        map.put(DATA, compress(data));
        map.put(PRINCIPAL, principal);
        if (reporterId != null) {
            map.put(REPORTER_ID, reporterId);
        }
        map.put(JobStatus.CORRELATION_ID, MDC.get(LoggingFilter.CSID));

        // Not sure if this is the best way to go:
        // Give each job a UUID to ensure that it is unique
        JobDetail detail = newJob(HypervisorUpdateJob.class)
            .withIdentity(prefix + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .storeDurably(true) // required if we have to postpone the job
            .build();

        return detail;
    }

    public static byte[] compress(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            OutputStream out = new DeflaterOutputStream(baos);
            out.write(text.getBytes("UTF-8"));
            out.close();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
        return baos.toByteArray();
    }

    public static String decompress(byte[] bytes) {
        InputStream in = new InflaterInputStream(new ByteArrayInputStream(bytes));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return new String(baos.toByteArray(), "UTF-8");
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
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
        return consumer;
    }

    /**
     * Class for holding the list of consumers in the stored json text
     *
     * @author wpoteat
     *
     */
    public static class HypervisorList{
        private List<Consumer> hypervisors;

        public HypervisorList() {
        }

        public List<Consumer> getHypervisors() {
            return this.hypervisors;
        }
        public void setConsumers(List<Consumer> hypervisors) {
            this.hypervisors = hypervisors;
        }
    }

    public Consumer updateCheckinTime(Consumer consumer) {
        Date now = new Date();
        consumerCurator.updateLastCheckin(consumer, now);
        consumer.setLastCheckin(now);
        return consumer;
    }
}
