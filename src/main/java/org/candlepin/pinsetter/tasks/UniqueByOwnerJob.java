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

import java.util.List;

import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import com.google.inject.persist.UnitOfWork;

/**
 * UniqueByOwnerJob
 */
public abstract class UniqueByOwnerJob extends CpJob {

    public UniqueByOwnerJob(UnitOfWork unitOfWork) {
        super(unitOfWork);
    }

    // This isn't especially safe, but better than what we've got
    @SuppressWarnings("unchecked")
    public static JobStatus scheduleJob(JobCurator jobCurator,
        Scheduler scheduler, JobDetail detail,
        Trigger trigger) throws SchedulerException {

        List<JobStatus> results = jobCurator.findQueuedByOwnerAndClass(
            detail.getJobDataMap().getString(JobStatus.TARGET_ID),
            (Class<? extends CpJob>) detail.getJobClass());

        if (!results.isEmpty()) {
            log.debug("CAKO found a matching job, using that one rather than scheduling another");
            return results.get(0);
        }
        
        results = jobCurator.findRunningByOwnerAndClass(
            detail.getJobDataMap().getString(JobStatus.TARGET_ID),
            (Class<? extends CpJob>) detail.getJobClass());
        if (!results.isEmpty()) {
            log.debug("CAKO found a matching running job, scheduling without a trigger");
            JobStatus blocking = results.get(0);
            if (blocking.getBlockingJob() == null) {
                JobStatus status = CpJob.scheduleJob(jobCurator, scheduler, detail, null);
                blocking.setBlockingJob(status.getId());
                jobCurator.merge(blocking);
                return status;
            }
            return jobCurator.find(blocking.getBlockingJob());
        }
        return CpJob.scheduleJob(jobCurator, scheduler, detail, trigger);
    }
}
