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
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;

import com.google.inject.Inject;

import org.hibernate.HibernateException;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * UnpauseJob prompts each paused job to check if it
 * is safe to continue executing every 5 seconds.  The polling
 * approach isn't as fast or efficient as allowing blocking jobs
 * to trigger the next in line, but this avoids concurrency
 * and locking problems
 */
@DisallowConcurrentExecution
public class UnpauseJob extends KingpinJob {
    private static Logger log = LoggerFactory.getLogger(UnpauseJob.class);
    public static final String DEFAULT_SCHEDULE = "0/5 * * * * ?"; //every five seconds
    private JobCurator jobCurator;
    private PinsetterKernel pinsetterKernel;

    @Inject
    public UnpauseJob(JobCurator jobCurator,
            PinsetterKernel pinsetterKernel) {
        this.jobCurator = jobCurator;
        this.pinsetterKernel = pinsetterKernel;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        List<JobStatus> waitingJobs;

        try {
            waitingJobs = jobCurator.findWaitingJobs();
        }
        catch (HibernateException e) {
            log.error("Cannot execute query: ", e);
            throw new JobExecutionException(e);
        }
        for (JobStatus j : waitingJobs) {
            try {
                boolean schedule = (Boolean) j.getJobClass()
                    .getMethod("isSchedulable", JobCurator.class, JobStatus.class)
                    .invoke(null, jobCurator, j);
                if (schedule) {
                    log.debug("Triggering waiting job: " + j.getId());
                    pinsetterKernel.addTrigger(j);
                    j.setState(JobState.CREATED);
                    jobCurator.merge(j);
                }
            }
            catch (Exception e) {
                log.error("Failed to schedule waiting job: " + j.getId(), e);
            }
        }
    }
}
