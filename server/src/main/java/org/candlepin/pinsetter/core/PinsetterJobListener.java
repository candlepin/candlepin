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
package org.candlepin.pinsetter.core;

import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.CandlepinException;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.candlepin.pinsetter.tasks.UniqueByEntityJob;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This component receives events around job status and performs actions to
 * allow for the job in question to run outside of a request scope, as well as
 * record the status of the job for later retrieval.
 */
public class PinsetterJobListener implements JobListener {
    private static Logger log = LoggerFactory.getLogger(PinsetterJobListener.class);

    public static final String LISTENER_NAME = "Pinsetter Job Listener";
    public static final String PRINCIPAL_KEY = "principal_key";

    private JobCurator curator;

    // this is a separate unitOfWork and units of work from the actual pinsetter
    // job because we want to tie this closer to the quartz execution, rather than
    // job execution.
    private UnitOfWork unitOfWork;

    @Inject
    public PinsetterJobListener(JobCurator curator, UnitOfWork unitOfWork) {
        this.curator = curator;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public String getName() {
        return LISTENER_NAME;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        Principal principal = (Principal) context.getMergedJobDataMap().get(PRINCIPAL_KEY);
        ResteasyProviderFactory.pushContext(Principal.class, principal);

        try {
            unitOfWork.begin();
            updateJob(context);
        }
        catch (Exception e) {
            log.error("jobToBeExecuted encountered a problem. Usually means " +
                "there was a problem storing the job status. Job will run.", e);
        }
        finally {
            unitOfWork.end();
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // Do nothing sentence
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context,
        JobExecutionException exception) {
        deleteDetail(context);
        try {
            unitOfWork.begin();
            updateJob(context, exception);
        }
        catch (Exception e) {
            if (UniqueByEntityJob.class.isAssignableFrom(context.getJobDetail().getJobClass())) {
                log.error("jobWasExecuted encountered a problem on a blocking job." +
                    " This can block other jobs.  Marking finished, if possible", e);
                try {
                    //This time only update the state so it doesn't block other jobs
                    curator.cancelNoReturn(context.getJobDetail().getKey().getName());
                }
                catch (Exception ex) {
                    log.error("Failed again to cancel status: " +
                        context.getJobDetail().getKey().getName(), ex);
                }
            }
            else {
                log.error("jobWasExecuted encountered a problem. Usually means " +
                    "there was a problem storing the job status. Job finished ok.", e);
            }
        }
        finally {
            unitOfWork.end();
            ResteasyProviderFactory.popContextData(Principal.class);
        }
    }

    private void updateJob(JobExecutionContext ctx) {
        updateJob(ctx, null);
    }

    @Transactional
    private void updateJob(JobExecutionContext ctx, JobExecutionException exc) {
        JobStatus status = curator.find(ctx.getJobDetail().getKey().getName());
        if (status != null) {
            if (exc != null) {
                log.error("Job [" + status.getId() + "] failed." , exc);
                status.setState(JobState.FAILED);
                status.setResult(exc.getMessage());

                if (exc.getCause() instanceof CandlepinException) {
                    status.setResultData(((CandlepinException) exc.getCause()).message());
                }
            }
            else {
                status.update(ctx);
            }
            curator.merge(status);
        }
        else {
            log.debug("No jobinfo found for job: " + ctx);
        }
    }

    private void deleteDetail(JobExecutionContext cx) {
        JobKey key = cx.getJobDetail().getKey();
        if (key.getGroup().equals(PinsetterKernel.SINGLE_JOB_GROUP)) {
            try {
                cx.getScheduler().deleteJob(key);
            }
            catch (SchedulerException e1) {
                // Should fail if the job isn't durable
            }
        }
    }
}
