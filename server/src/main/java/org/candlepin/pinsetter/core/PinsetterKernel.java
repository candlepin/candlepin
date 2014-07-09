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


import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.TriggerKey.triggerKey;
import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

import org.candlepin.auth.SystemPrincipal;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.tasks.CancelJobJob;
import org.candlepin.util.PropertyUtil;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.JobFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Pinsetter Kernel.
 * @version $Rev$
 */
@Singleton
public class PinsetterKernel {

    public static final String CRON_GROUP = "cron group";
    public static final String SINGLE_JOB_GROUP = "async group";

    private static Logger log = LoggerFactory.getLogger(PinsetterKernel.class);

    private Scheduler scheduler;
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
        JobListener listener, JobCurator jobCurator,
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
                scheduler.getListenerManager().addJobListener(listener);
            }
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

    private void addToList(Set<String> impls, String confkey) {
        String[] jobs = this.config.getStringArray(confkey);
        if (jobs != null && jobs.length > 0) {
            impls.addAll(Arrays.asList(jobs));
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

        List<JobEntry> pendingJobs = new ArrayList<JobEntry>();
        // use a set to remove potential duplicate jobs from config
        Set<String> jobImpls = new HashSet<String>();

        try {
            if (config.getBoolean(ConfigProperties.ENABLE_PINSETTER)) {
                // get the default tasks first
                addToList(jobImpls, ConfigProperties.DEFAULT_TASKS);

                // get other tasks
                addToList(jobImpls, ConfigProperties.TASKS);
            }
            else if (!isClustered()) {
                // Since pinsetter is disabled, we only want to allow
                // CancelJob and async jobs on this node.
                jobImpls.add(CancelJobJob.class.getName());
            }

            // Bail if there is nothing to configure
            if (jobImpls.size() == 0) {
                log.warn("No tasks to schedule");
                return;
            }
            log.debug("Jobs implemented:" + jobImpls);
            Set<JobKey> jobKeys = scheduler.getJobKeys(jobGroupEquals(CRON_GROUP));

            for (String jobImpl : jobImpls) {
                if (log.isDebugEnabled()) {
                    log.debug("Scheduling " + jobImpl);
                }

                // Find all existing cron triggers matching this job impl
                List<CronTrigger> existingCronTriggers = new LinkedList<CronTrigger>();
                if (jobKeys != null) {
                    for (JobKey key : jobKeys) {
                        JobDetail jd = scheduler.getJobDetail(key);
                        if (jd != null &&
                            jd.getJobClass().getName().equals(jobImpl)) {
                            CronTrigger trigger = (CronTrigger) scheduler.getTrigger(
                                triggerKey(key.getName(), CRON_GROUP));
                            if (trigger != null) {
                                existingCronTriggers.add(trigger);
                            }
                            else {
                                log.warn("JobKey " + key + " returned null cron trigger.");
                            }
                        }
                    }
                }
                // get the default schedule from the job class in case one
                // is not found in the configuration.
                String defvalue = PropertyUtil.getStaticPropertyAsString(jobImpl,
                    "DEFAULT_SCHEDULE");

                String schedule = this.config.getString("pinsetter." +
                    jobImpl + ".schedule", defvalue);

                if (schedule != null && schedule.length() > 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Scheduler entry for " + jobImpl + ": " +
                            schedule);
                    }

                    addUniqueJob(pendingJobs, jobImpl,
                        existingCronTriggers, schedule);
                }
                else {
                    log.warn("No schedule found for " + jobImpl + ". Skipping...");
                }
            }
        }
        catch (SchedulerException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }
        catch (Exception e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }
        scheduleJobs(pendingJobs);
    }

    /*
     * Adds a unique job, replacing any old ones with different schedules.
     */
    private void addUniqueJob(List<JobEntry> pendingJobs,
        String jobImpl, List<CronTrigger> existingCronTriggers, String schedule)
        throws SchedulerException {

        // If trigger already exists with same schedule, nothing to do
        if (existingCronTriggers.size() == 1 &&
            existingCronTriggers.get(0).getCronExpression().equals(schedule)) {
            return;
        }

        /*
         * Otherwise, we know there are existing triggers, delete them all and create
         * one with our new schedule. Normally there should only ever be one, but past
         * bugs caused duplicates so we handle this situation by default now.
         *
         * This could be cleaning up some with the same schedule we want, but we can't
         * allow there to be multiple with the same schedule so simpler to just make sure
         * there's only one.
         */
        log.warn("Cleaning up " + existingCronTriggers.size() + " obsolete triggers.");
        for (CronTrigger t : existingCronTriggers) {
            boolean result = scheduler.deleteJob(t.getJobKey());
            log.warn(t.getJobKey() + " deletion success?: " + result);
        }

        // Create our new job:
        pendingJobs.add(new JobEntry(jobImpl, schedule));
    }

    /**
     * Shuts down the application
     *
     * @throws PinsetterException if there was a scheduling error in shutdown
     */
    public void shutdown() throws PinsetterException {
        try {
            log.info("shutting down pinsetter kernel");
            scheduler.standby(); // do not allow any new jobs to be scheduled
            deleteAllJobs(); // delete all jobs if we are not clustered
            log.info("allowing running jobs to finish..");
            scheduler.shutdown(true);
            log.info("pinsetter kernel is shut down");
        }
        catch (SchedulerException e) {
            throw new PinsetterException("Error shutting down Pinsetter.", e);
        }
    }

    private void scheduleJobs(List<JobEntry> pendingJobs) {
        if (pendingJobs.size() == 0) {
            return;
        }
        try {
            for (JobEntry jobentry : pendingJobs) {
                Trigger trigger = newTrigger()
                    .withIdentity(jobentry.getJobName(), CRON_GROUP)
                    .withSchedule(cronSchedule(jobentry.getSchedule())
                        .withMisfireHandlingInstructionDoNothing())
                    .build();

                scheduleJob(
                    this.getClass().getClassLoader().loadClass(
                        jobentry.getClassName()), jobentry.getJobName(), trigger);
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
            Trigger trigger = newTrigger()
                .withIdentity(job.getName(), CRON_GROUP)
                .withSchedule(cronSchedule(crontab)
                    .withMisfireHandlingInstructionDoNothing())
                .build();

            scheduleJob(job, jobName, trigger);
        }
        catch (Exception pe) {
            throw new PinsetterException("problem parsing schedule", pe);
        }
    }

    public void scheduleJob(Class job, String jobName, Trigger trigger)
        throws PinsetterException {
        JobDataMap map = new JobDataMap();
        map.put(PinsetterJobListener.PRINCIPAL_KEY, new SystemPrincipal());

        JobDetail detail = newJob(job)
            .withIdentity(jobName, CRON_GROUP)
            .usingJobData(map)
            .build();
        scheduleJob(detail, CRON_GROUP, trigger);
    }

    private JobStatus scheduleJob(JobDetail detail, String grpName, Trigger trigger)
        throws PinsetterException {

        JobDetailImpl detailImpl = (JobDetailImpl) detail;
        detailImpl.setGroup(grpName);

        try {
            JobStatus status = (JobStatus) (detail.getJobClass()
                .getMethod("scheduleJob", JobCurator.class,
                    Scheduler.class, JobDetail.class, Trigger.class)
                .invoke(null, jobCurator, scheduler, detail, trigger));

            if (log.isDebugEnabled()) {
                log.debug("Scheduled " + detailImpl.getFullName());
            }

            return status;
        }
        catch (Exception e) {
            log.error("There was a problem scheduling " +
                detail.getKey().getName(), e);
            throw new PinsetterException("There was a problem scheduling " +
                detail.getKey().getName(), e);
        }
    }

    public void cancelJob(Serializable id, String group)
        throws PinsetterException {
        try {
            // this deletes from the scheduler, it's already marked as
            // canceled in the JobStatus table
            if (scheduler.deleteJob(jobKey((String) id, group))) {
                log.info("canceled job " + group + ":" + id + " in scheduler");
            }
        }
        catch (SchedulerException e) {
            throw new PinsetterException("problem canceling " + group + ":" + id, e);
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
        Trigger trigger = newTrigger()
            .withIdentity(jobDetail.getKey().getName() + " trigger", SINGLE_JOB_GROUP)
            .build();

        return scheduleJob(jobDetail, SINGLE_JOB_GROUP, trigger);
    }

    public void addTrigger(JobStatus status) throws SchedulerException {
        Trigger trigger = newTrigger()
            .withIdentity(status.getId() + " trigger", SINGLE_JOB_GROUP)
            .forJob(status.getJobKey())
            .build();
        scheduler.scheduleJob(trigger);
    }

    public boolean getSchedulerStatus() throws PinsetterException {
        try {
            // return true when scheduler is running (double negative)
            return !scheduler.isInStandbyMode();
        }
        catch (SchedulerException e) {
            throw new PinsetterException("There was a problem gathering" +
                        "scheduler status ", e);
        }
    }

    public void pauseScheduler() throws PinsetterException {
        // go into standby mode
        try {
            scheduler.standby();
        }
        catch (SchedulerException e) {
            throw new PinsetterException("There was a problem pausing the scheduler", e);
        }
    }

    public void unpauseScheduler() throws PinsetterException {
        log.debug("looking for canceled jobs since scheduler was paused");
        CancelJobJob cjj = new CancelJobJob(jobCurator, this);
        try {
            //Not sure why we don't want to use a UnitOfWork here
            cjj.toExecute(null);
        }
        catch (JobExecutionException e1) {
            throw new PinsetterException("Could not clear canceled jobs before starting");
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
            Set<JobKey> jobs = this.scheduler.getJobKeys(jobGroupEquals(groupName));

            for (JobKey jobKey : jobs) {
                this.scheduler.deleteJob(jobKey);
            }
        }
        catch (SchedulerException e) {
            // TODO:  Something better than nothing
        }
    }

    private void deleteAllJobs() {
        if (!isClustered()) {
            deleteJobs(CRON_GROUP);
            deleteJobs(SINGLE_JOB_GROUP);
        }
    }

    public Set<JobKey> getSingleJobKeys() throws SchedulerException {
        return scheduler.getJobKeys(GroupMatcher.jobGroupEquals(SINGLE_JOB_GROUP));
    }

    private boolean isClustered() {
        boolean clustered = false;
        if (config.containsKey("org.quartz.jobStore.isClustered")) {
            clustered = config.getBoolean("org.quartz.jobStore.isClustered");
        }
        return clustered;
    }

    private static class JobEntry {
        private String classname;
        private String schedule;
        private String jobname;

        public JobEntry(String cname, String sched) {
            classname = cname;
            schedule = sched;
            jobname = genName(classname);
        }

        private String genName(String cname) {
            return Util.getClassName(cname) + "-" + Util.generateUUID();
        }

        public String getClassName() {
            return classname;
        }

        public String getSchedule() {
            return schedule;
        }

        public String getJobName() {
            return jobname;
        }
    }

}
