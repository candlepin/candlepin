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
package org.fedoraproject.candlepin.pinsetter.core;

import org.fedoraproject.candlepin.auth.SystemPrincipal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.JobCurator;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.fedoraproject.candlepin.pinsetter.tasks.CancelJobJob;
import org.fedoraproject.candlepin.util.PropertyUtil;

import com.google.common.base.Nullable;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.log4j.Logger;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.JobFactory;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

/**
 * Pinsetter Kernel.
 * @version $Rev$
 */
@Singleton
public class PinsetterKernel {

    private static final String CRON_GROUP = "cron group";
    private static final String SINGLE_JOB_GROUP = "async group";

    private static Logger log = Logger.getLogger(PinsetterKernel.class);

    private Scheduler scheduler;
    private ChainedListener chainedJobListener;
    private Config config;
    private JobCurator jobCurator;

    /**
     * Kernel main driver behind Pinsetter
     * @param conf Configuration to use
     * @throws InstantiationException thrown if this.scheduler can't be
     * initialized.
     */
    @Inject
    public PinsetterKernel(Config conf, JobFactory jobFactory, 
        @Nullable JobListener listener, JobCurator jobCurator,
        StdSchedulerFactory fact) throws InstantiationException {

        this.config = conf;
        this.jobCurator = jobCurator;

        Properties props = config.getNamespaceProperties("org.quartz");

        // create a schedulerFactory
        try {
            fact.initialize(props);
            scheduler = fact.getScheduler();
            scheduler.setJobFactory(jobFactory);

            if (listener != null) {
                scheduler.addJobListener(listener);
            }

            // Setup TriggerListener chains here.
            chainedJobListener = new ChainedListener();
        }
        catch (SchedulerException e) {
            throw new InstantiationException("this.scheduler failed: " +
                e.getMessage());
        }
    }

    /**
     * Starts Pinsetter
     * This method does not return until the this.scheduler is shutdown
     * @throws PinsetterException error occurred during Quartz or Hibernate
     * startup
     */
    public void startup() throws PinsetterException {
        try {
            scheduler.start();
            configure();
        }
        catch (SchedulerException e) {
            throw new PinsetterException(e.getMessage(), e);
        }
    }

    /**
     * Configures the system.
     * @param conf Configuration object containing config values.
     */
    private void configure() {
        if (log.isDebugEnabled()) {
            log.debug("Scheduling tasks");
        }
        Map<String, String[]> pendingJobs = new HashMap<String, String[]>();
        // use a set to remove potential duplicate jobs from config
        Set<String> jobImpls = new HashSet<String>();

        // get the default tasks first
        String[] jobs = this.config.getStringArray(ConfigProperties.DEFAULT_TASKS);
        if (jobs != null && jobs.length > 0) {
            jobImpls.addAll(Arrays.asList(jobs));
        }

        // get other tasks
        String[] addlJobs = this.config.getStringArray(ConfigProperties.TASKS);
        if (addlJobs != null && addlJobs.length > 0) {
            jobImpls.addAll(Arrays.asList(addlJobs));
        }

        // Bail if there is nothing to configure
        if (jobImpls.size() == 0) {
            log.warn("No tasks to schedule");
            return;
        }

        int count = 0;
        for (String jobImpl : jobImpls) {
            if (log.isDebugEnabled()) {
                log.debug("Scheduling " + jobImpl);
            }

            // get the default schedule from the job class in case one
            // is not found in the configuration.
            String defvalue = "";
            try {
                defvalue = PropertyUtil.getStaticPropertyAsString(jobImpl,
                    "DEFAULT_SCHEDULE");
            }
            catch (Exception e) {
                throw new RuntimeException(e.getLocalizedMessage(), e);
            }

            String schedulerEntry = this.config.getString("pinsetter." + jobImpl +
                ".schedule", defvalue);

            if (schedulerEntry != null && schedulerEntry.length() > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("Scheduler entry for " + jobImpl + ": " +
                        schedulerEntry);
                }
                String[] data = new String[2];
                data[0] = jobImpl;
                data[1] = schedulerEntry;
                pendingJobs.put(String.valueOf(count), data);
            }
            else {
                log.warn("No schedule found for " + jobImpl + ". Skipping...");
            }
            count++;
        }
        try {
            scheduler.addTriggerListener(chainedJobListener);
        }
        catch (SchedulerException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }

        scheduleJobs(pendingJobs);
    }

    /**
     * Shuts down the application
     *
     * @throws PinsetterException if ther was a scheduling error in shutdown
     */
    public void shutdown() throws PinsetterException {
        try {
            scheduler.standby();
            deleteAllJobs();
            scheduler.shutdown();
        }
        catch (SchedulerException e) {
            throw new PinsetterException("Error shutting down Pinsetter.", e);
        }
    }

    private void scheduleJobs(Map<String, String[]> pendingJobs) {
       // No jobs to schedule
       // This would be quite odd, but it could happen
        if (pendingJobs == null || pendingJobs.size() == 0) {
            log.error("No tasks scheduled");
            throw new RuntimeException("No tasks scheduled");
        }
        try {
            for (Entry<String, String[]> entry : pendingJobs.entrySet()) {
                String[] data = pendingJobs.get(entry.getKey());
                String jobImpl = data[0];
                String crontab = data[1];
                String jobName = jobImpl + "-" + entry.getKey();

                Trigger trigger = new CronTrigger(jobImpl, CRON_GROUP, crontab);
                trigger.setMisfireInstruction(
                    CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);

                scheduleJob(
                    this.getClass().getClassLoader().loadClass(jobImpl),
                    jobName, trigger);
            }
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
            throw new RuntimeException(t.getMessage(), t);
        }
    }

    public void scheduleJob(Class job, String jobName, String crontab)
        throws PinsetterException {

        try {
            Trigger trigger = new CronTrigger(job.getName(), CRON_GROUP, crontab);
            trigger.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);

            scheduleJob(job, jobName, trigger);
        }
        catch (ParseException pe) {
            throw new PinsetterException("problem parsing schedule", pe);
        }
    }

    public void scheduleJob(Class job, String jobName, Trigger trigger)
        throws PinsetterException {
        JobDetail detail = new JobDetail(jobName, CRON_GROUP, job);
        JobDataMap map = detail.getJobDataMap();
        map.put(PinsetterJobListener.PRINCIPAL_KEY, new SystemPrincipal());
        scheduleJob(detail, CRON_GROUP, trigger);
    }

    private JobStatus scheduleJob(JobDetail detail, String grpName, Trigger trigger)
        throws PinsetterException {

        detail.setGroup(grpName);
        detail.addJobListener(PinsetterJobListener.LISTENER_NAME);

        try {
            JobStatus status = jobCurator.find(detail.getName());
            if (status == null) {
                status = jobCurator.create(new JobStatus(detail));
            }

            scheduler.scheduleJob(detail, trigger);
            if (log.isDebugEnabled()) {
                log.debug("Scheduled " + detail.getFullName());
            }

            return status;
        }
        catch (SchedulerException e) {
            throw new PinsetterException("There was a problem scheduling " +
                detail.getName(), e);
        }
    }

    public void cancelJob(Serializable id, String group)
        throws PinsetterException {
        try {
            // this deletes from the scheduler, it's already marked as
            // canceled in the JobStatus table
            if (scheduler.deleteJob((String) id, group)) {
                log.info("cancelled job " + group + ":" + id + " in scheduler");
            }
        }
        catch (SchedulerException e) {
            throw new PinsetterException("problem cancelling " + group + ":" + id, e);
        }
    }

    /**
     * Schedule a long-running job for a single execution.
     *
     * @param jobDetail the long-running job to perform - assumed to be
     *     prepopulated with a valid job task and name
     * @return the initial status of the submitted job
     * @throws PinsetterException if there is an error scheduling the job
     */
    public JobStatus scheduleSingleJob(JobDetail jobDetail) throws PinsetterException {
        return scheduleJob(jobDetail, SINGLE_JOB_GROUP, new SimpleTrigger(
            jobDetail.getName() + " trigger", SINGLE_JOB_GROUP));
    }

    public boolean getSchedulerStatus() throws PinsetterException {
        try {
            //return true when scheduler is running (double negative)
            return !scheduler.isInStandbyMode();
        }
        catch (SchedulerException e) {
            throw new PinsetterException("There was a problem gathering" +
                        "scheduler status ", e);
        }
    }

    public void pauseScheduler() throws PinsetterException {
        //go into standby mode
        try {
            scheduler.standby();
        }
        catch (SchedulerException e) {
            throw new PinsetterException("There was a problem pausing the scheduler", e);
        }
    }

    public void unpauseScheduler() throws PinsetterException {
        log.debug("looking for cancelled jobs since scheduler was paused");
        CancelJobJob cjj = new CancelJobJob(jobCurator, this);
        try {
            cjj.execute(null);
        }
        catch (JobExecutionException e1) {
            throw new PinsetterException("Could not clear cancelled jobs before starting");
        }
        log.debug("restarting scheduler");
        try {
            scheduler.start();
        }
        catch (SchedulerException e) {
            throw new PinsetterException("There was a problem unpausing the scheduler", e);
        }
    }

    private void deleteJobs(String groupName) {
        try {
            String[] jobs = this.scheduler.getJobNames(groupName);

            for (String job : jobs) {
                this.scheduler.deleteJob(job, groupName);
            }
        }
        catch (SchedulerException e) {
            // TODO:  Something better than nothing
        }
    }

    private void deleteAllJobs() {
        deleteJobs(CRON_GROUP);
        deleteJobs(SINGLE_JOB_GROUP);
    }

}
