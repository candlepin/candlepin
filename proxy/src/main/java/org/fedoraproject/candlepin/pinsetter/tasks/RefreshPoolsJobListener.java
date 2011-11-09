/**
 * Copyright (c) 2009 Red Hat, Inc.
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
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;

import com.google.inject.Inject;

import org.apache.log4j.Logger;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.List;

/**
 * RefreshPoolsJobListener which looks for other pending RefreshPoolsJobs
 * and starts them. This allows only one RefreshPools job for a given
 * owner to run.
 */
public class RefreshPoolsJobListener implements JobListener {

    private static Logger log = Logger.getLogger(RefreshPoolsJobListener.class);
    public static final String LISTENER_NAME = "refresh jobs listener";
    private JobCurator jobCurator;

    @Inject
    public RefreshPoolsJobListener(JobCurator curator) {
        jobCurator = curator;
    }

    @Override
    public String getName() {
        return LISTENER_NAME;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext ctx) {
        // do nothing
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext ctx) {
        // do nothing
    }

    @Override
    public void jobWasExecuted(JobExecutionContext ctx,
        JobExecutionException jobException) {

        JobDetail ctxDetail = ctx.getJobDetail();
        Scheduler ctxScheduler = ctx.getScheduler();

        // Upon completion look to see if there are any pending jobs, if so,
        if (ctxDetail.getJobClass().equals(RefreshPoolsJob.class)) {
            JobDetail detail = null;
            try {
                String ownerKey =
                    (String) ctxDetail.getJobDataMap().get(JobStatus.TARGET_ID);

                List<JobStatus> statuses =
                    jobCurator.findPendingByOwnerKeyAndName(ownerKey, "refresh_pools");

                if (statuses != null && !statuses.isEmpty()) {
                    // find first pending job, then unpause it.
                    JobStatus status = statuses.get(0);
                    detail = ctxScheduler.getJobDetail(
                        status.getId(), PinsetterKernel.SINGLE_JOB_GROUP);
                    ctxScheduler.resumeJob(
                        detail.getName(), PinsetterKernel.SINGLE_JOB_GROUP);
                }
            }
            catch (SchedulerException e) {
                String jobname = "";

                if (detail != null) {
                    jobname = detail.getName();
                }

                log.error("There was a problem resuming [" + jobname + "].", e);
            }
        }
    }
}
