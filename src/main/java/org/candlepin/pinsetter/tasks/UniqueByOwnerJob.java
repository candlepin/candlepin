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

import com.google.inject.persist.UnitOfWork;

/**
 * UniqueByOwnerJob
 */
public abstract class UniqueByOwnerJob extends CpJob {

    public UniqueByOwnerJob(UnitOfWork unitOfWork) {
        super(unitOfWork);
    }

    @SuppressWarnings("unchecked")
    public static JobStatus scheduleJob(JobCurator jobCurator,
        Scheduler scheduler, JobDetail detail,
        Trigger trigger) throws SchedulerException {
        JobStatus result = jobCurator.getLatestByClassAndOwner(
            detail.getJobDataMap().getString(JobStatus.TARGET_ID),
            (Class<? extends CpJob>) detail.getJobClass());
        if (result == null){
            log.debug("CAKO scheduling a new job");
            return CpJob.scheduleJob(jobCurator, scheduler, detail, trigger);
        }
        //result = jobCurator.lockAndLoad(result);
        if (result.getState() == JobStatus.JobState.PENDING ||
            result.getState() == JobStatus.JobState.CREATED) {
            log.debug("CAKO returning existing job");
            //jobCurator.merge(result);
            return result;
        }
        if (result.getBlockingJob() != null) {
            log.debug("CAKO this is awkward ========================================");
            //we're in a state where there isn't anything queued, however the running job thinks it's blocking something
            //with proper locking this shouldn't be necessary

            //return jobCurator.find(result.getBlockingJob());
            return null;
        }
        log.debug("CAKO scheduling a job without a trigger");
        result.setBlockingJob(detail.getKey().getName());
        JobStatus status = CpJob.scheduleJob(jobCurator, scheduler, detail, null);
        status = jobCurator.lockAndLoad(status);
        jobCurator.merge(status);
        jobCurator.merge(result);
        return status;
    }
}
