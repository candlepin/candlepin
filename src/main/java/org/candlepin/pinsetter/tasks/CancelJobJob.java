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
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;

import com.google.inject.Inject;

import org.hibernate.HibernateException;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CancelJobJob attempts to cancel the jobs in Quartz for the jobs whose
 * status is JobStatus.CANCEL.
 */
@DisallowConcurrentExecution
public class CancelJobJob extends KingpinJob {

    private static Logger log = LoggerFactory.getLogger(CancelJobJob.class);
    public static final String DEFAULT_SCHEDULE = "0/5 * * * * ?"; //every five seconds
    private JobCurator jobCurator;
    private PinsetterKernel pinsetterKernel;

    @Inject
    public CancelJobJob(JobCurator jobCurator,
            PinsetterKernel pinsetterKernel) {
        this.jobCurator = jobCurator;
        this.pinsetterKernel = pinsetterKernel;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        Set<String> statusIds = new HashSet<String>();
        List<JobStatus> jobsToCancel = null;
        try {
            Set<JobKey> keys = pinsetterKernel.getSingleJobKeys();
            for (JobKey key : keys) {
                statusIds.add(key.getName());
            }
            try {
                jobsToCancel = jobCurator.findCanceledJobs(statusIds);
            }
            catch (HibernateException e) {
                log.error("Cannot execute query: ", e);
                throw new JobExecutionException(e);
            }
            for (JobStatus j : jobsToCancel) {
                try {
                    pinsetterKernel.cancelJob(j.getId(), j.getGroup());
                    log.info("Canceled job: " + j.getId() + ", " + j.getGroup());
                }
                catch (PinsetterException e) {
                    log.error("Exception canceling job " + j.getId(), e);
                }
            }
        }
        catch (SchedulerException e) {
            log.error("Unable to cancel jobs", e);
        }
    }
}
