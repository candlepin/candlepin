/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.log4j.MDC;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;

/**
 * Asynchronous job for refreshing the lastCheckin for specific reporter_id.
 */
public class HypervisorHeartbeatUpdateJob extends KingpinJob {

    private static final Logger log = LoggerFactory.getLogger(HypervisorHeartbeatUpdateJob.class);
    private static final String REPORTER_ID = "reporter_id";

    private final ConsumerCurator consumerCurator;

    @Inject
    public HypervisorHeartbeatUpdateJob(final ConsumerCurator consumerCurator) {
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
    }

    @Override
    public void toExecute(final JobExecutionContext context) throws JobExecutionException {
        final JobDataMap dataMap = context.getMergedJobDataMap();
        final String reporterId = dataMap.getString(REPORTER_ID);
        final String ownerKey = dataMap.getString(JobStatus.TARGET_ID);
        log.debug("Job starter for reporter: {}", reporterId);
        this.consumerCurator.heartbeatUpdate(
            reporterId,
            new Date(),
            ownerKey);
    }

    /**
     * Creates a {@link JobDetail} that runs this job for the given reporter_id.
     *
     * @param reporterId reporter for which to update lastCheckin dates
     * @return a {@link JobDetail} that describes the job run
     */
    public static JobDetail from(final String reporterId, final Owner owner) {
        final JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_ID, owner.getKey());
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(REPORTER_ID, reporterId);
        map.put(JobStatus.CORRELATION_ID, MDC.get(LoggingFilter.CSID));
        map.put(JobStatus.OWNER_LOG_LEVEL, owner.getLogLevel());

        // Not sure if this is the best way to go:
        // Give each job a UUID to ensure that it is unique

        return newJob(HypervisorHeartbeatUpdateJob.class)
            .withIdentity("hypervisor_update_" + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .storeDurably(true) // required if we have to postpone the job
            .build();
    }

}
