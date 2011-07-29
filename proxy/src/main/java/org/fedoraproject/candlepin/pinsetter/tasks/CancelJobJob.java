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
package org.fedoraproject.candlepin.pinsetter.tasks;

import java.util.List;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.JobCurator;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterException;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.hibernate.HibernateException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


import com.google.inject.Inject;

/**
 * CancelJobJob
 */
public class CancelJobJob implements Job {
    
    private static Logger log = Logger.getLogger(CancelJobJob.class);
    public static final String DEFAULT_SCHEDULE = "0/5 * * * * ?"; //every five seconds    
    private JobCurator jobCurator;
    private PinsetterKernel pinsetterKernel;

    
    @Inject
    public CancelJobJob(JobCurator jobCurator, PinsetterKernel pinsetterKernel) {
        this.jobCurator = jobCurator;
        this.pinsetterKernel = pinsetterKernel;
    }
    
    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        List<JobStatus> cancelledJobs;

        try {
            cancelledJobs = jobCurator.findCanceledJobs();
        }
        catch (HibernateException e) {
            log.error("Cannot execute query: ", e);
            throw new JobExecutionException(e);
        }
        for (JobStatus j : cancelledJobs) {
            try {
                pinsetterKernel.cancelJob(j.getId(), j.getGroup());
            }
            catch (PinsetterException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
