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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.pinsetter.tasks.SubscriptionSyncTask;
import org.fedoraproject.candlepin.util.PropertyUtil;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Pinsetter Kernel.
 * @version $Rev$
 */
public class PinsetterKernel implements SchedulerService {

    private static Logger log = Logger.getLogger(PinsetterKernel.class);
    private static final String TASK_GROUP = "Pinsetter Batch Engine Group";
    private static final String TASKS = "pinsetter.tasks";
    private static final String DEFAULT_TASKS = "pinsetter.default_tasks";

    private byte[] shutdownLock = new byte[0];
    private Scheduler scheduler = null;
    private ChainedListener chainedJobListener = null;
    private Config config = null;
    /**
     * Kernel main driver behind Pinsetter
     * @throws InstantiationException thrown if this.scheduler can't be
     * initialized.
     */
    protected PinsetterKernel(Injector injector) throws InstantiationException {
        this(new Config(), injector);
    }

    /**
     * Kernel main driver behind Pinsetter
     * @param conf Configuration to use
     * @throws InstantiationException thrown if this.scheduler can't be
     * initialized.
     */
    @Inject
    public PinsetterKernel(Config conf, Injector injector) throws InstantiationException {
        config = conf;

        Properties props = config.getNamespaceProperties("org.quartz",
            defaultConfig());

        // create a schedulerFactory
        try {
            SchedulerFactory fact = new StdSchedulerFactory(props);

            // this.scheduler
            scheduler = fact.getScheduler();
            scheduler.setJobFactory(new HighlanderJobFactory(injector));

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
            configure(config, defaultConfig());
        }
        catch (SchedulerException e) {
            throw new PinsetterException(e.getMessage(), e);
        }
    }

    /**
     * Configures the system.
     * @param conf Configuration object containing config values.
     * @param overrides Map containing configuration overrides based on cli
     * params
     */
    private void configure(Config conf, Map<String, String> overrides) {
        if (log.isDebugEnabled()) {
            log.debug("Scheduling tasks");
        }
        Map<String, String[]> pendingJobs = new HashMap<String, String[]>();
        List<String> jobImpls = new ArrayList<String>();
        if (log.isDebugEnabled()) {
            log.debug("No manual overrides detected...Using configuration");
        }

        // get the default tasks first
        String[] jobs = config.getStringArray(DEFAULT_TASKS);
        if (jobs != null && jobs.length > 0) {
            jobImpls.addAll(Arrays.asList(jobs));
        }

        // get other tasks
        String[] addlJobs = config.getStringArray(TASKS);
        if (addlJobs != null && addlJobs.length > 0) {
            jobImpls.addAll(Arrays.asList(addlJobs));
        }

        // Bail if there is nothing to configure
        if (jobImpls == null || jobImpls.size() == 0) {
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

            String schedulerEntry = config.getString("pinsetter." + jobImpl +
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
     */
    public void shutdown() {
        try {
            scheduler.standby();
            deleteAllJobs();
            scheduler.shutdown();
        }
        catch (SchedulerException e) {
            // TODO Figure out what to do with this guy
            e.printStackTrace();
        }
        finally {
            // Wake up thread waiting in startup() so it can exit
            synchronized (this.shutdownLock) {
                this.shutdownLock.notify();
            }
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
            for (String suffix : pendingJobs.keySet()) {
                String[] data = pendingJobs.get(suffix);
                String jobImpl = data[0];
                String crontab = data[1];
                String jobName = jobImpl + "-" + suffix;
 
                Trigger trigger = null;
                trigger = new CronTrigger(jobImpl,
                        TASK_GROUP, crontab);
                trigger
                    .setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
                //trigger.addTriggerListener(this.chainedJobListener.getName());
                
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

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void scheduleJob(Class job, String jobName, String crontab)
        throws PinsetterException {
        
        try {
            Trigger trigger = new CronTrigger(job.getName(), TASK_GROUP, crontab);
            trigger.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
            //trigger.addTriggerListener(chainedJobListener.getName());
            scheduleJob(job, jobName, trigger);
        }
        catch (ParseException pe) {
            throw new PinsetterException("problem parsing schedule", pe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void scheduleJob(Class job, String jobName, Trigger trigger)
        throws PinsetterException {
        try {
            JobDetail detail = new JobDetail(jobName, TASK_GROUP, job);
            //trigger.addTriggerListener(chainedJobListener.getName());
            this.scheduler.scheduleJob(detail, trigger);
            if (log.isDebugEnabled()) {
                log.debug("Scheduled " + detail.getFullName());
            }
        }
        catch (SchedulerException se) {
            throw new PinsetterException("problem scheduling " + jobName, se);
        }       
    }
    
    // TODO: GET RID OF ME!!
    Scheduler getScheduler() {
        return scheduler;
    }
    
    private void deleteAllJobs() {
        boolean done = false;
        while (!done) {
            try {
                String[] groups = this.scheduler.getJobGroupNames();
                if (groups == null || groups.length == 0) {
                    done = true;
                }
                else {
                    String group = groups[0];
                    String[] jobs = this.scheduler.getJobNames(group);
                    for (int x = jobs.length - 1; x > -1; x--) {
                        this.scheduler.deleteJob(jobs[x], group);
                    }
                }
            }
            catch (SchedulerException e) {
                done = true;
            }
        }
    }

    /**
     * Returns the default configuration if no config file is present.
     * @return the default configuration if no config file is present.
     */
    @SuppressWarnings("serial")
    private Map<String, String> defaultConfig() {
        return new HashMap<String, String>() {
            {
                put("org.quartz.threadPool.class",
                    "org.quartz.simpl.SimpleThreadPool");
                put("org.quartz.threadPool.threadCount", "15");
                put("org.quartz.threadPool.threadPriority", "5");
                put(DEFAULT_TASKS, SubscriptionSyncTask.class.getName());
            }
        };
    }
}
