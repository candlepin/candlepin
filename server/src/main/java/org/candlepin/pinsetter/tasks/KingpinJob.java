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

import static org.quartz.impl.matchers.NameMatcher.jobNameEquals;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.model.JobStatus;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.persistence.EntityExistsException;
import javax.persistence.PersistenceException;

/**
 * KingpinJob replaces TransactionalPinsetterJob, which encapsulated
 * other jobs.  Using a supertype between actual jobs and the
 * Quartz Job type gives us more freedom to define behavior.
 * Every candlepin job must extend KingpinJob
 */
public abstract class KingpinJob implements Job {

    private static Logger log = LoggerFactory.getLogger(KingpinJob.class);
    @Inject protected UnitOfWork unitOfWork;
    @Inject protected Config config;
    protected static String prefix = "job";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        // Store the job's unique ID in log4j's thread local MDC, which will automatically
        // add it to all log entries executed for this job.
        try {
            MDC.put("requestType", "job");
            MDC.put("requestUuid", context.getJobDetail().getKey().getName());
        }
        catch (NullPointerException npe) {
            //this can occur in testing
        }

        /*
         * Execute our 'real' job inside a custom unit of work scope, instead
         * of the guice provided one, which is HTTP request scoped.
         */
        boolean startedUow = startUnitOfWork();
        try {
            toExecute(context);
        }
        catch (PersistenceException e) {
            // Multiple refreshpools running at once can cause the following:
            // one job attempts to delete a pool that was deleted by another job
            // one job attempts to add a pool that was already added
            // one job attempts to update a pool that was already updated
            // all 3 of these conditions will cause some form of JPA/hibernate
            // exception to bubble up.  the exception seems to vary based on the
            // underlying db, so just catch the top level exception. We then
            // throw an exception that will let pinsetter/quartz know  that
            // there was a race condition detected, and get it to reschedule
            // the job. the other job will have completed successfully, and
            // this one can then run (and possibly use new information, which
            // is why there could be two jobs in the queue for  the same owner).
            //
            // I guess if other jobs fail its ok to restart them, too?
            // note that we have to catch at this level rather than inside the
            // job for any update collisions, which will only be detected
            // on commit.

            int maxRefires = getMaxRetries();
            // If the maximum is sub-zero, do not enforce any limit
            boolean refire = maxRefires < 0 || context.getRefireCount() < maxRefires;
            log.error("Persistence exception caught running pinsetter task. Attempt: " +
                context.getRefireCount() + ", Refire: " + refire, e);
            throw new JobExecutionException(e, refire);
        }
        finally {
            if (startedUow) {
                endUnitOfWork();
            }
        }
    }

    /**
     * Method for actual execution, execute handles unitOfWork for us
     * @param context
     * @throws JobExecutionException if there's a problem executing the job
     */
    public void toExecute(JobExecutionContext context)
        throws JobExecutionException {
    }

    public static JobStatus scheduleJob(JobCurator jobCurator,
            Scheduler scheduler, JobDetail detail,
            Trigger trigger) throws SchedulerException {

        scheduler.getListenerManager().addJobListenerMatcher(
            PinsetterJobListener.LISTENER_NAME,
            jobNameEquals(detail.getKey().getName()));

        JobStatus status = null;
        try {
            status = jobCurator.create(new JobStatus(detail, trigger == null));
        }
        catch (EntityExistsException e) {
            // status exists, let's update it
            // in theory this should be the rare case
            status = jobCurator.find(detail.getKey().getName());
            jobCurator.merge(status);
        }

        if (trigger != null) {
            scheduler.scheduleJob(detail, trigger);
        }
        else {
            scheduler.addJob(detail, false);
        }
        return status;
    }

    public static boolean isSchedulable(JobCurator jobCurator, JobStatus status) {
        return true;
    }

    protected boolean startUnitOfWork() {
        if (unitOfWork != null) {
            try {
                unitOfWork.begin();
                return true;
            }
            catch (IllegalStateException e) {
                log.debug("Already have an open unit of work");
                return false;
            }
        }
        return false;
    }

    protected void endUnitOfWork() {
        if (unitOfWork != null) {
            try {
                unitOfWork.end();
            }
            catch (IllegalStateException e) {
                log.debug("Unit of work is already closed, doing nothing");
                // If there is no active unit of work, there is no reason to close it
            }
        }
    }

    protected void commitAndContinue() {
        endUnitOfWork();
        startUnitOfWork();
    }

    private int getMaxRetries() {
        int maxRetries = ConfigProperties.PINSETTER_MAX_RETRIES_DEFAULT;
        try {
            // config may be null if this Job object has been created by hand
            if (config != null) {
                maxRetries = config.getInt(ConfigProperties.PINSETTER_MAX_RETRIES);
            }
        }
        catch (Exception ce) {
            log.warn("Unable to read '" + ConfigProperties.PINSETTER_MAX_RETRIES +
                "' from candlepin config.  Using default of " + maxRetries);
        }
        return maxRetries;
    }
}
