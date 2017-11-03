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

import com.google.inject.Inject;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
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
    public CancelJobJob(JobCurator jobCurator, PinsetterKernel pinsetterKernel) {
        this.jobCurator = jobCurator;
        this.pinsetterKernel = pinsetterKernel;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            Set<JobKey> keys = pinsetterKernel.getSingleJobKeys();
            Set<String> statusIds = new HashSet<String>();

            for (JobKey key : keys) {
                statusIds.add(key.getName());
            }

            try {
                pinsetterKernel.cancelJobs(this.jobCurator.findCanceledJobs(statusIds));
            }
            catch (PinsetterException e) {
                log.error("Exception canceling jobs.", e);
            }
        }
        catch (SchedulerException e) {
            log.error("Unable to cancel jobs", e);
        }
    }

    @Override
    protected boolean logExecutionTime() {
        return false;
    }
}
