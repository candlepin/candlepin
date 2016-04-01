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

import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UniqueByEntityJob can by extended by jobs that should not be run concurrently
 * per owner. Additional scheduled jobs will be ignored if a similar job exists
 * for the same owner that has not started running yet. A job will wait for the
 * running job to finish before beginning execution.
 */
public abstract class UniqueByEntityJob extends KingpinJob {
    private static Logger log = LoggerFactory.getLogger(UniqueByEntityJob.class);

    @SuppressWarnings("unchecked")
    public static JobStatus scheduleJob(JobCurator jobCurator,
        Scheduler scheduler, JobDetail detail,
        Trigger trigger) throws SchedulerException {
        JobStatus result = jobCurator.getByClassAndTarget(
            detail.getJobDataMap().getString(JobStatus.TARGET_ID),
            (Class<? extends KingpinJob>) detail.getJobClass());
        if (result == null) {
            return KingpinJob.scheduleJob(jobCurator, scheduler, detail, trigger);
        }
        if (result.getState() == JobStatus.JobState.PENDING ||
            result.getState() == JobStatus.JobState.CREATED ||
            result.getState() == JobStatus.JobState.WAITING) {
            log.debug("Returning existing job id: {}", result.getId());
            return result;
        }
        log.debug("Scheduling job without a trigger: {}", detail.getKey().getName());
        JobStatus status = KingpinJob.scheduleJob(jobCurator, scheduler, detail, null);
        return status;
    }

    public static boolean isSchedulable(JobCurator jobCurator, JobStatus status) {
        long running = jobCurator.findNumRunningByClassAndTarget(
            status.getTargetId(), status.getJobClass());
        return running == 0;  // We can start the job if there are 0 like it running
    }
}
