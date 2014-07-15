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

import com.google.inject.Inject;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * PinsetterSweepBarJob
 * en.wikipedia.org/wiki/Pinsetter
 * The Sweep Bar, or "rake", clears pins.
 * This job marks orphaned jobs as "canceled"
 *
 * An orphaned job is defined as a non-finished/failed/canceled
 * job status that does not correspond to a quartz job.
 *
 * This job does not need to run nearly as frequently as
 * the unpause job, because it should be very rare, if
 * not impossible in most cases for this state to be hit.
 *
 * This will most often occur when a clustered candlepin
 * is restarted, quartz jobs are volatile, and will be
 * removed, however cp_jobs (JobStatus) will not reflect
 * changes.
 */
@DisallowConcurrentExecution
public class SweepBarJob extends KingpinJob {

    private static Logger log = LoggerFactory.getLogger(SweepBarJob.class);
    public static final String DEFAULT_SCHEDULE = "0 0/5 * * * ?"; //every five minutes

    private JobCurator jobCurator;
    private PinsetterKernel pinsetterKernel;

    @Inject
    public SweepBarJob(JobCurator jobCurator,
        PinsetterKernel pinsetterKernel) {
        this.jobCurator = jobCurator;
        this.pinsetterKernel = pinsetterKernel;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            Set<JobKey> keys = pinsetterKernel.getSingleJobKeys();
            List<String> statusIds = new LinkedList<String>();
            for (JobKey key : keys) {
                statusIds.add(key.getName());
            }
            int canceled = jobCurator.cancelOrphanedJobs(statusIds);
            if (canceled > 0) {
                log.info("Canceled " + canceled + " orphaned jobs");
            }
        }
        catch (Exception e) {
            log.error("Failed to cancel orphaned jobs", e);
        }
    }
}
