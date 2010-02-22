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

import org.fedoraproject.candlepin.config.Config;

import org.apache.log4j.Logger;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Pinsetter Kernel.
 * @version $Rev$
 */
public class PinsetterKernel {

    private static Logger log = Logger.getLogger(PinsetterKernel.class);
    private static final String TASK_GROUP = "Pinsetter Batch Engine Group";
    private static final String TASKS = "pinsetter.tasks";
    private static final String DEFAULT_TASKS = "pinsetter.default_tasks";

    private byte[] shutdownLock = new byte[0];
    private Scheduler scheduler = null;
    private ChainedListener chainedJobListener = null;

    /**
     * Kernel main driver behind Pinsetter
     * @throws InstantiationException thrown if this.scheduler can't be
     * initialized.
     */
    public PinsetterKernel() throws InstantiationException {
        Properties props = Config.get().getNamespaceProperties("org.quartz");
        // create a this.schedulerFactory
        try {
            SchedulerFactory fact = new StdSchedulerFactory(props);

            // this.scheduler
            scheduler = fact.getScheduler();
            scheduler.setJobFactory(new HighlanderJobFactory());

            // Setup TriggerListener chains here.
        }
        catch (SchedulerException e) {
            e.printStackTrace();
            throw new InstantiationException("this.scheduler failed");
        }
    }

    /**
     * Starts Pinsetter
     * This method does not return until the this.scheduler is shutdown
     * @throws PinsetterException error occurred during Quartz or Hibernate startup
     */
    public void startup() throws PinsetterException {
        try {
            scheduler.start();
            synchronized (shutdownLock) {
                try {
                    shutdownLock.wait();
                }
                catch (InterruptedException ignored) {
                    return;
                }
            }
        }
        catch (SchedulerException e) {
            throw new PinsetterException(e.getMessage(), e);
        }
    }

    /**
     * Initiates the shutdown process. Needs to happen in a
     * separate thread to prevent Quartz scheduler errors.
     */
    public void startShutdown() {
        Runnable shutdownTask = new Runnable() {
            public void run() {
                shutdown();
            }
        };
        Thread t = new Thread(shutdownTask);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Configures the system.
     * @param config Configuration object containing config values.
     * @throws ConfigException thrown if there is a problem creating jobs by name.
     */
    public void configure(Config config) throws ConfigException {
        configure(config, null);
    }

    /**
     * Configures the system.
     * @param config Configuration object containing config values.
     * @param overrides Map containing configuration overrides based on cli params
     * @throws ConfigException thrown if there is a problem creating jobs by name.
     */
    public void configure(Config config, Map overrides) throws ConfigException {
        if (log.isDebugEnabled()) {
            log.debug("Scheduling tasks");
        }
        Map<String,String[]> pendingJobs = new HashMap<String,String[]>();
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
            throw new ConfigException("No tasks to schedule");
        }
        int count = 0;
        for (Iterator iter = jobImpls.iterator(); iter.hasNext();) {
            String jobImpl = (String) iter.next();
            if (log.isDebugEnabled()) {
                log.debug("Scheduling " + jobImpl);
            }
            String schedulerEntry = config.getString("pinsetter." + jobImpl + ".schedule");
            if (schedulerEntry != null && schedulerEntry.length() > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("Scheduler entry for " + jobImpl + ": " + schedulerEntry);
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
            throw new ConfigException(e.getLocalizedMessage(), e);
        }
        scheduleJobs(pendingJobs);
    }

    /**
     * Shuts down the application
     */
    protected void shutdown() {
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


    private void scheduleJobs(Map pendingJobs) throws ConfigException {
       // No jobs to schedule
       // This would be quite odd, but it could happen
        if (pendingJobs == null || pendingJobs.size() == 0) {
            log.error("No tasks scheduled");
            throw new ConfigException("No tasks scheduled");
        }
        try {
            for (Iterator iter = pendingJobs.keySet().iterator(); iter.hasNext();) {
                String suffix = (String) iter.next();
                String[] data = (String[]) pendingJobs.get(suffix);
                String jobImpl = data[0];
                String crontab = data[1];
                String jobName = jobImpl + "-" + suffix;
                JobDetail detail = new JobDetail(jobName,
                        TASK_GROUP,
                        this.getClass().getClassLoader().loadClass(jobImpl));
                Trigger trigger = null;
                trigger = new CronTrigger(jobImpl,
                        TASK_GROUP, crontab);
                trigger.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
                trigger.addTriggerListener(this.chainedJobListener.getName());
                this.scheduler.scheduleJob(detail, trigger);
                if (log.isDebugEnabled()) {
                    log.debug("Scheduled " + detail.getFullName());
                }
            }
        }
        catch (Throwable t) {
            log.error(t.getMessage(), t);
            throw new ConfigException(t.getMessage(), t);
        }
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
}
