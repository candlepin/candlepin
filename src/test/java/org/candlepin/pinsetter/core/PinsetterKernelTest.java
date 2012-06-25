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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;
import static org.quartz.impl.matchers.NameMatcher.jobNameEquals;

import org.candlepin.auth.Principal;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.tasks.CancelJobJob;
import org.candlepin.pinsetter.tasks.JobCleaner;
import org.candlepin.pinsetter.tasks.StatisticHistoryTask;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.JobFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * PinsetterKernelTest
 *
 * @version $Rev$
 */
public class PinsetterKernelTest {
    private PinsetterKernel pk = null;
    private JobFactory jfactory;
    private JobCurator jcurator;
    private JobListener jlistener;
    private StdSchedulerFactory sfactory;
    private Config config;
    private Scheduler sched;
    private ListenerManager lm;

    @Before
    public void init() throws SchedulerException {
        sched = mock(Scheduler.class);
        jfactory = mock(JobFactory.class);
        jcurator = mock(JobCurator.class);
        jlistener = mock(JobListener.class);
        sfactory = mock(StdSchedulerFactory.class);
        lm = mock(ListenerManager.class);

        config = new Config(
            new HashMap<String, String>() {
                {
                    put("org.quartz.threadPool.class",
                        "org.quartz.simpl.SimpleThreadPool");
                    put("org.quartz.threadPool.threadCount", "25");
                    put("org.quartz.threadPool.threadPriority", "5");
                    put(ConfigProperties.DEFAULT_TASKS, JobCleaner.class.getName());
                    put(ConfigProperties.TASKS, StatisticHistoryTask.class.getName());
                }
            });
        when(sfactory.getScheduler()).thenReturn(sched);
        when(sched.getListenerManager()).thenReturn(lm);
    }

    @Test(expected = InstantiationException.class)
    public void blowup() throws Exception {
        when(sfactory.getScheduler()).thenThrow(new SchedulerException());
        pk = new PinsetterKernel(config, jfactory, null, jcurator, sfactory);
    }

    @Test
    public void skipListener() throws Exception {
        pk = new PinsetterKernel(config, jfactory, null, jcurator, sfactory);
        verify(sched).setJobFactory(eq(jfactory));
        verify(lm, never()).addJobListener(eq(jlistener));
    }
    @Test
    public void ctor() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        verify(sched).setJobFactory(eq(jfactory));
        verify(lm).addJobListener(eq(jlistener));
    }

    @SuppressWarnings("serial")
    @Test
    public void configure() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.startup();
        verify(sched).start();
        verify(jcurator, atMost(2)).create(any(JobStatus.class));
        verify(sched, atMost(2)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    public void disablePinsetter() throws Exception {
        config = new Config(
            new HashMap<String, String>() {
                {
                    put(ConfigProperties.DEFAULT_TASKS, JobCleaner.class.getName());
                    put(ConfigProperties.TASKS, StatisticHistoryTask.class.getName());
                    put(ConfigProperties.ENABLE_PINSETTER, "false");
                }
            });
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.startup();
        verify(sched).start();
        ArgumentCaptor<JobStatus> arg = ArgumentCaptor.forClass(JobStatus.class);
        verify(jcurator, atMost(1)).create(arg.capture());
        JobStatus stat = (JobStatus) arg.getValue();
        assertTrue(stat.getId().startsWith(Util.getClassName(CancelJobJob.class)));
        verify(sched, atMost(1)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    public void handleExistingJobStatus() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        JobStatus status = mock(JobStatus.class);
        when(jcurator.find(startsWith(
            Util.getClassName(JobCleaner.class)))).thenReturn(status);
        pk.startup();
        verify(sched).start();
        verify(jcurator, atMost(1)).create(any(JobStatus.class));
        verify(sched, atMost(2)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    public void shutdown() throws Exception {
        String crongrp = "cron group";
        String singlegrp = "async group";

        Set<JobKey> cronSet = new HashSet<JobKey>();
        cronSet.add(jobKey("fakejob1", crongrp));
        cronSet.add(jobKey("fakejob2", crongrp));

        Set<JobKey> asyncSet = new HashSet<JobKey>();
        asyncSet.add(jobKey("fakejob1", singlegrp));
        asyncSet.add(jobKey("fakejob2", singlegrp));

        when(sched.getJobKeys(eq(jobGroupEquals(crongrp)))).thenReturn(cronSet);
        when(sched.getJobKeys(eq(jobGroupEquals(singlegrp)))).thenReturn(asyncSet);
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.shutdown();

        verify(sched, atMost(1)).standby();
        verify(sched).deleteJob(eq(jobKey("fakejob1", crongrp)));
        verify(sched).deleteJob(eq(jobKey("fakejob2", crongrp)));
        verify(sched).deleteJob(eq(jobKey("fakejob1", singlegrp)));
        verify(sched).deleteJob(eq(jobKey("fakejob2", singlegrp)));
        verify(sched, atMost(1)).shutdown();
    }

    @Test
    public void noJobsDuringShutdown() throws Exception {
        Set<JobKey> jobs = new HashSet<JobKey>();
        when(sched.getJobKeys(jobGroupEquals(anyString()))).thenReturn(jobs);
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.shutdown();

        verify(sched, atMost(1)).standby();
        verify(sched, never()).deleteJob(any(JobKey.class));
        verify(sched, atMost(1)).shutdown();
    }

    @Test(expected = PinsetterException.class)
    public void handleFailedShutdown() throws Exception {
        doThrow(new SchedulerException()).when(sched).standby();
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.shutdown();
        verify(sched, never()).shutdown();
    }

    @Test
    public void scheduleByString() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.scheduleJob(TestJob.class, "testjob", "*/1 * * * * ?");
        ArgumentCaptor<Trigger> arg = ArgumentCaptor.forClass(Trigger.class);
        verify(jcurator, atMost(1)).create(any(JobStatus.class));
        verify(sched).scheduleJob(any(JobDetail.class), arg.capture());
        CronTrigger trigger = (CronTrigger) arg.getValue();
        assertEquals("*/1 * * * * ?", trigger.getCronExpression());
    }

    @Test
    public void updateSchedule() throws Exception {
        Map<String, String> props = new HashMap<String, String>();
        props.put(ConfigProperties.DEFAULT_TASKS, JobCleaner.class.getName());
        props.put("org.quartz.jobStore.isClustered", "true");
        props.put("pinsetter.org.candlepin.pinsetter.tasks." +
            "JobCleaner.schedule", "*/1 * * * * ?");
        Config config = new Config(props);
        CronTrigger cronTrigger = mock(CronTrigger.class);
        JobDetail jobDetail = mock(JobDetail.class);

        String crongrp = "cron group";
        Set<JobKey> jobs = new HashSet<JobKey>();
        jobs.add(jobKey("org.candlepin.pinsetter.tasks.JobCleaner"));

        when(sched.getJobKeys(eq(jobGroupEquals(crongrp)))).thenReturn(jobs);
        when(sched.getTrigger(any(TriggerKey.class))).thenReturn(cronTrigger);

        when(cronTrigger.getCronExpression()).thenReturn("*/7 * * * * ?");
        when(sched.getJobDetail(any(JobKey.class))).thenReturn(jobDetail);
        doReturn(JobCleaner.class).when(jobDetail).getJobClass();

        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.startup();
        verify(sched).deleteJob(any(JobKey.class));
        verify(jcurator).create(any(JobStatus.class));
    }

    @Test(expected = PinsetterException.class)
    public void handleParseException() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.scheduleJob(TestJob.class, "testjob", "how bout them apples");
    }

    @Test
    public void scheduleByTrigger() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        Trigger trigger = newTrigger()
            .withIdentity("job", "grp")
            .withSchedule(cronSchedule("*/1 * * * * ?"))
            .build();

        pk.scheduleJob(TestJob.class, "testjob", trigger);
        ArgumentCaptor<Trigger> arg = ArgumentCaptor.forClass(Trigger.class);
        verify(jcurator, atMost(1)).create(any(JobStatus.class));
        verify(sched).scheduleJob(any(JobDetail.class), arg.capture());
        assertEquals(trigger, arg.getValue());
    }

    @Test(expected = PinsetterException.class)
    public void scheduleException() throws Exception {
        Trigger trigger = newTrigger()
            .withIdentity("job", "grp")
            .withSchedule(cronSchedule("*/1 * * * * ?"))
            .build();

        doThrow(new SchedulerException()).when(sched).scheduleJob(
            any(JobDetail.class), eq(trigger));
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.scheduleJob(TestJob.class, "testjob", trigger);
        verify(jcurator, atMost(1)).create(any(JobStatus.class));
    }

    @Test
    public void cancelJob() throws Exception {
        String singlegrp = "async group";
        Set<JobKey> jobs = new HashSet<JobKey>();
        jobs.add(jobKey("fakejob1"));
        jobs.add(jobKey("fakejob2"));

        when(sched.getJobKeys(eq(jobGroupEquals(singlegrp)))).thenReturn(jobs);
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.cancelJob("fakejob1", singlegrp);
        verify(sched, atMost(1)).deleteJob(eq(jobKey("fakejob1", singlegrp)));
    }

    @Test
    public void singleJob() throws Exception {
        String singlegrp = "async group";
        JobDataMap map = new JobDataMap();
        map.put(PinsetterJobListener.PRINCIPAL_KEY, mock(Principal.class));
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, "admin");
        JobDetailImpl detail = mock(JobDetailImpl.class);
        JobKey jobKey = jobKey("name", "group");
        when(detail.getKey()).thenReturn(jobKey);
        when(detail.getJobDataMap()).thenReturn(map);
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.scheduleSingleJob(detail);
        verify(detail).setGroup(eq(singlegrp));
        verify(lm).addJobListenerMatcher(PinsetterJobListener.LISTENER_NAME
                , jobNameEquals(detail.getKey().getName()));
        verify(sched).scheduleJob(eq(detail), any(Trigger.class));
    }

    @Test
    public void schedulerStatus() throws Exception {
        when(sched.isInStandbyMode()).thenReturn(false);
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        assertTrue(pk.getSchedulerStatus());
    }

    @Test
    public void pauseScheduler() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.pauseScheduler();
        verify(sched, atMost(1)).standby();
    }

    @Test
    public void unpauseScheduler() throws Exception {
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.unpauseScheduler();
        verify(jcurator).findCanceledJobs();
        verify(sched).start();
    }

    @Test
    public void clusteredShutdown() throws Exception {

        config = new Config(
            new HashMap<String, String>() {
                {
                    put(ConfigProperties.DEFAULT_TASKS, JobCleaner.class.getName());
                    put(ConfigProperties.TASKS, StatisticHistoryTask.class.getName());
                    put("org.quartz.jobStore.isClustered", "true");
                }
            });
        Set<JobKey> jobs = new HashSet<JobKey>();
        jobs.add(jobKey("fakejob1"));
        jobs.add(jobKey("fakejob2"));

        String crongrp = "cron group";
        String singlegrp = "async group";
        when(sched.getJobKeys(eq(jobGroupEquals(crongrp)))).thenReturn(jobs);
        when(sched.getJobKeys(eq(jobGroupEquals(singlegrp)))).thenReturn(jobs);

        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.shutdown();

        verify(sched, atMost(1)).standby();
        verify(sched, never()).deleteJob(eq(jobKey("fakejob1", crongrp)));
        verify(sched, never()).deleteJob(eq(jobKey("fakejob2", crongrp)));
        verify(sched, never()).deleteJob(eq(jobKey("fakejob1", singlegrp)));
        verify(sched, never()).deleteJob(eq(jobKey("fakejob2", singlegrp)));
        verify(sched, atMost(1)).shutdown();
    }

    @Test
    public void clusteredStartupWithJobs() throws Exception {
        config = new Config(
            new HashMap<String, String>() {
                {
                    put(ConfigProperties.DEFAULT_TASKS, JobCleaner.class.getName());
                    put(ConfigProperties.TASKS, StatisticHistoryTask.class.getName());
                    put("org.quartz.jobStore.isClustered", "true");
                }
            });

        Set<JobKey> jobs = new HashSet<JobKey>();
        jobs.add(jobKey(JobCleaner.class.getName()));
        jobs.add(jobKey(StatisticHistoryTask.class.getName()));
        when(sched.getJobKeys(eq(jobGroupEquals("cron group")))).thenReturn(jobs);
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.startup();
        verify(sched).start();
        verify(jcurator, atMost(2)).create(any(JobStatus.class));
        verify(sched, atMost(2)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    public void clusteredStartupWithoutJobs() throws Exception {
        config = new Config(
            new HashMap<String, String>() {
                {
                    put(ConfigProperties.DEFAULT_TASKS, JobCleaner.class.getName());
                    put(ConfigProperties.TASKS, StatisticHistoryTask.class.getName());
                    put("org.quartz.jobStore.isClustered", "true");
                }
            });
        Set<JobKey> jobs = new HashSet<JobKey>();
        when(sched.getJobKeys(eq(jobGroupEquals("cron group")))).thenReturn(jobs);
        pk = new PinsetterKernel(config, jfactory, jlistener, jcurator, sfactory);
        pk.startup();
        verify(sched).start();
        verify(jcurator, atMost(2)).create(any(JobStatus.class));
        verify(sched, atMost(2)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }
}
