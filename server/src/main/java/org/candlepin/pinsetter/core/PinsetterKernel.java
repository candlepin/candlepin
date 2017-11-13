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
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ModeChangeListener;
import org.candlepin.controller.ModeManager;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.tasks.CancelJobJob;
import org.candlepin.pinsetter.tasks.KingpinJob;
import org.candlepin.util.PropertyUtil;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.lang.StringUtils;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.TriggerListener;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.JobFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Pinsetter Kernel.
 * @version $Rev$
 */
@Singleton
public class PinsetterKernel implements ModeChangeListener {

    public static final String CRON_GROUP = "cron group";
    public static final String SINGLE_JOB_GROUP = "async group";
    public static final String[] DELETED_JOBS = new String[] {
        "StatisticHistoryTask",
        "ExportCleaner"
    };

    private static Logger log = LoggerFactory.getLogger(PinsetterKernel.class);
    private Scheduler scheduler;
    private Configuration config;
    private JobCurator jobCurator;
    private ModeManager modeManager;

    /**
     * Kernel main driver behind Pinsetter
     * @param conf Configuration to use
     * @throws InstantiationException thrown if this.scheduler can't be
     * initialized.
     */
    @Inject
    public PinsetterKernel(Configuration conf, JobFactory jobFactory,
        JobListener listener, JobCurator jobCurator,
        StdSchedulerFactory fact,
        TriggerListener triggerListener,
        ModeManager modeManager) throws InstantiationException {

        this.config = conf;
        this.jobCurator = jobCurator;
        this.modeManager = modeManager;
        /*
         * Did your unit test get an NPE here?
         * this will help:
         * when(config.subset(eq("org.quartz"))).thenReturn(
         * new MapConfiguration(ConfigProperties.DEFAULT_PROPERTIES));
         *
         * TODO: We should probably be clearing up what's happening here. Not a fan of a comment
         * explaining what should be handled by something like an illegal arg or illegal state
         * exception. -C
         */
        Properties props = config.subset("org.quartz").toProperties();

        // create a schedulerFactory
        try {
            fact.initialize(props);
            scheduler = fact.getScheduler();
            scheduler.setJobFactory(jobFactory);

            if (listener != null) {
                scheduler.getListenerManager().addJobListener(listener);
            }
            if (triggerListener != null) {
                scheduler.getListenerManager().addTriggerListener(triggerListener);
            }
        }
        catch (SchedulerException e) {
            throw new InstantiationException("this.scheduler failed: " + e.getMessage());
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
            jobCurator.cancelOrphanedJobs(Collections.EMPTY_LIST);
            if (modeManager.getLastCandlepinModeChange().getMode() != Mode.NORMAL) {
                scheduler.pauseAll();
            }
            modeManager.registerModeChangeListener(this);
            configure();
        }
        catch (SchedulerException e) {
            throw new PinsetterException(e.getMessage(), e);
        }
    }

    private void addToList(Set<String> impls, String confkey) {
        List<String> jobs = config.getList(confkey, null);
        if (jobs != null && !jobs.isEmpty()) {
            for (String job : jobs) {
                if (!StringUtils.isEmpty(job)) {
                    impls.add(job);
                }
            }
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
        Set<String> jobFQNames = new HashSet<String>();

        try {
            if (config.getBoolean(ConfigProperties.ENABLE_PINSETTER, true)) {
                // get the default tasks first
                addToList(jobFQNames, ConfigProperties.DEFAULT_TASKS);

                // get other tasks
                addToList(jobFQNames, ConfigProperties.TASKS);
            }
            else if (!isClustered()) {
                // Since pinsetter is disabled, we only want to allow
                // CancelJob and async jobs on this node.
                jobFQNames.add(CancelJobJob.class.getName());
            }

            // Bail if there is nothing to configure
            if (jobFQNames.size() == 0) {
                log.warn("No tasks to schedule");
                return;
            }
            log.debug("Jobs implemented:" + jobFQNames);
            Set<JobKey> jobKeys = scheduler.getJobKeys(jobGroupEquals(CRON_GROUP));

            /*
             * purge jobs that have been deleted from this version of Candlepin.
             * This is necessary as we might not even have the Class definition
             * at classpath, Hence any attempt at fetching the JobDetail by the
             * Scheduler or JobStatus by the JobCurator will fail.
             */
            for (JobKey jobKey : jobKeys) {
                for (String deletedJob : DELETED_JOBS) {
                    if (jobKey.getName().contains(deletedJob)) {
                        scheduler.deleteJob(jobKey);
                        jobCurator.deleteJobNoStatusReturn(jobKey.getName());
                        break;
                    }
                }
            }

            for (String jobFQName : jobFQNames) {
                if (log.isDebugEnabled()) {
                    log.debug("Scheduling " + jobFQName);
                }

                // Find all existing cron triggers matching this job impl
                List<CronTrigger> existingCronTriggers = new LinkedList<CronTrigger>();
                if (jobKeys != null) {
                    for (JobKey key : jobKeys) {
                        JobDetail jd = scheduler.getJobDetail(key);
                        if (jd != null &&
                            jd.getJobClass().getName().equals(jobFQName)) {
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
                String schedule = getSchedule(jobFQName);
                if (schedule != null) {
                    addUniqueJob(pendingJobs, jobFQName, existingCronTriggers, schedule);
                }
            }
        }
        catch (SchedulerException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }
        scheduleJobs(pendingJobs);
    }

    /** get the default schedule from the job class in case one is not found in the configuration.
     */
    private String getSchedule(String jobFQName) {
        String defvalue = null;
        try {
            defvalue = PropertyUtil.getStaticPropertyAsString(jobFQName, "DEFAULT_SCHEDULE");
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }

        String schedule = this.config.getString("pinsetter." + jobFQName + ".schedule", defvalue);

        if (schedule != null && schedule.length() > 0) {
            log.debug("Scheduler entry for {}: {}", jobFQName, schedule);
            return schedule;
        }
        else {
            log.warn("No schedule found for {}. Skipping...", jobFQName);
        }
        return null;
    }

    /**
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
        if (existingCronTriggers.size() > 0) {
            log.warn("Cleaning up " + existingCronTriggers.size() + " obsolete triggers.");
        }
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

    @SuppressWarnings("checkstyle:indentation")
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

    @SuppressWarnings("checkstyle:indentation")
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

    @SuppressWarnings("unchecked")
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
                .getMethod("scheduleJob", JobCurator.class, Scheduler.class, JobDetail.class, Trigger.class)
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

    /**
     * Cancels the specified job by deleting the job and all triggers from the scheduler.
     * Assumes that the job is already marked as CANCELED in the JobStatus table.
     *
     * @param id the ID of the job to cancel
     * @param group the job group that the job belongs to
     * @throws PinsetterException if there is an error deleting the job from the schedule.
     */
    public void cancelJob(Serializable id, String group) throws PinsetterException {
        try {
            if (scheduler.deleteJob(jobKey((String) id, group))) {
                log.info("Canceled job in scheduler: {}:{} ", group, id);
            }
        }
        catch (SchedulerException e) {
            throw new PinsetterException("problem canceling " + group + ":" + id, e);
        }
    }

    /**
     * Cancels the specified jobs by deleting the jobs and all triggers from the scheduler.
     * Assumes that the jobs are already marked as CANCELED in the JobStatus table.
     *
     * @param toCancel the JobStatus records of the jobs to cancel.
     * @throws PinsetterException if there is an error deleting the jobs from the schedule.
     */
    public void cancelJobs(Collection<JobStatus> toCancel) throws PinsetterException {
        List<JobKey> jobsToDelete = new LinkedList<JobKey>();

        for (JobStatus status : toCancel) {
            JobKey key = jobKey(status.getId(), status.getGroup());
            log.debug("Job {} from group {} will be deleted from the scheduler.",
                key.getName(), key.getGroup());
            jobsToDelete.add(key);
        }

        log.info("Deleting {} cancelled jobs from scheduler.", toCancel.size());
        try {
            scheduler.deleteJobs(jobsToDelete);
        }
        catch (SchedulerException se) {
            throw new PinsetterException("Problem canceling jobs.", se);
        }
        log.info("Finished deleting jobs from scheduler");
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

    public JobStatus scheduleSingleJob(Class<? extends KingpinJob> job, String jobName) throws
        PinsetterException {
        JobDataMap map = new JobDataMap();
        map.put(PinsetterJobListener.PRINCIPAL_KEY, new SystemPrincipal());

        JobDetail detail = newJob(job)
            .withIdentity(jobName, CRON_GROUP)
            .usingJobData(map)
            .build();
        Trigger trigger = newTrigger()
            .withIdentity(detail.getKey().getName() + " trigger", SINGLE_JOB_GROUP)
            .build();

        return scheduleJob(detail, SINGLE_JOB_GROUP, trigger);
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
                this.jobCurator.cancel(jobKey.getName());
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

    public void retriggerCronJob(String taskName, Class<? extends KingpinJob> jobClass)  throws
        PinsetterException {
        Set<TriggerKey> cronTriggerKeys = null;
        try {
            cronTriggerKeys = scheduler.getTriggerKeys(
                GroupMatcher.triggerGroupEquals(PinsetterKernel.CRON_GROUP));
            TriggerKey key = null;
            Iterator<TriggerKey> keysTrigger = cronTriggerKeys.iterator();
            // We should get only key per job. pick the first one and quit the loop
            while (key == null && keysTrigger.hasNext()) {
                TriggerKey current = keysTrigger.next();
                if (current.getName().contains(taskName)) {
                    key = current;
                }
            }
            if (key != null) {
                String newJobName = taskName + "-" + Util.generateUUID();
                String schedule = getSchedule(jobClass.getName());
                if (schedule != null) {
                    Trigger newTrigger = newTrigger()
                        .withIdentity(newJobName, CRON_GROUP)
                        .withSchedule(cronSchedule(schedule).withMisfireHandlingInstructionDoNothing())
                        .build();
                    scheduler.rescheduleJob(key, newTrigger);
                }
            }
        }
        catch (SchedulerException e) {
            throw new PinsetterException("There was a problem rescheduling cron job", e);
        }
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

    private void pauseAll() {
        try {
            log.debug("Pinsetter Kernel is being paused");
            scheduler.pauseAll();
        }
        catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    private void resumeAll() {
        try {
            log.debug("Pinsetter Kernel is being resumed");
            scheduler.resumeAll();
        }
        catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void modeChanged(Mode newMode) {
        if (newMode == Mode.SUSPEND) {
            pauseAll();
        }
        else if (newMode == Mode.NORMAL) {
            resumeAll();
        }
    }

}
