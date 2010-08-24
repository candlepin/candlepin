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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.CandlepinConfigurationTest.CandlepinConfigurationForTesting;
import org.junit.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;

/**
 * PinsetterKernelTest
 * 
 * @version $Rev$
 */
public class PinsetterKernelTest {
    private PinsetterKernel pk = null;

    @SuppressWarnings("serial")
    @Test
    public void configure() throws InstantiationException {
        Config config = new CandlepinConfigurationForTesting(
            new HashMap<String, String>() {

                {
                    put("org.quartz.threadPool.class",
                        "org.quartz.simpl.SimpleThreadPool");
                    put("org.quartz.threadPool.threadCount", "25");
                    put("org.quartz.threadPool.threadPriority", "5");

                    // clustering
                    // put("org.quartz.scheduler.instanceName",
                    // "MyClusteredScheduler");
                    // put("org.quartz.scheduler.instanceId", "AUTO");
                    // put("org.quartz.jobStore.class",
                    // "org.quartz.impl.jdbcjobstore.JobStoreTX");
                    // put("org.quartz.jobStore.driverDelegateClass",
                    // "org.quartz.impl.jdbcjobstore.HSQLDBDelegate");
                    // put("org.quartz.jobStore.tablePrefix", "QRTZ_");
                    // put("org.quartz.jobStore.isClustered", "true");
                    //
                    // put("org.quartz.dataSource.myDS.driver",
                    // "org.hsqldb.jdbcDriver");
                    // put("org.quartz.dataSource.myDS.URL",
                    // "jdbc:hsqldb:mem:unit-testing-jpa");
                    // put("org.quartz.dataSource.myDS.user", "sa");
                    // put("org.quartz.dataSource.myDS.password", "");
                    // put("org.quartz.jobStore.dataSource", "myDS");
                }
            });

        pk = new PinsetterKernel(config, Mockito.mock(JobFactory.class), null, null);
        assertNotNull(pk);

        try {
            pk.startup();
            pk.shutdown();
        }
        catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    @SuppressWarnings("serial")
    @Test
    public void testScheduleJobString() throws InstantiationException,
        PinsetterException, SchedulerException {
        Config config = new CandlepinConfigurationForTesting(
            new HashMap<String, String>() {

                {
                    put("org.quartz.threadPool.class",
                        "org.quartz.simpl.SimpleThreadPool");
                    put("org.quartz.threadPool.threadCount", "25");
                    put("org.quartz.threadPool.threadPriority", "5");

                }
            });

        pk = new PinsetterKernel(config, Mockito.mock(JobFactory.class), null, null);
        assertNotNull(pk);

        pk.startup();

        Scheduler s = pk.getScheduler();
        String[] groups = s.getJobGroupNames();
        for (String grp : groups) {
            String[] jobs = s.getJobNames(grp);
            for (String job : jobs) {
                System.out.println(job);
                // JobDetail d =
                s.getJobDetail(job, grp);
                s.addJobListener(new ListenerJob());
            }
        }

        // TODO: test needs to be fixed big time
        // every second
        pk.scheduleJob(TestJob.class, "testjob", "*/1 * * * * ?");
        Thread.yield();
        pk.shutdown();

    }

    public static class ListenerJob implements JobListener {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getName() {
            return "listener";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void jobExecutionVetoed(JobExecutionContext contextIn) {
            // TODO Auto-generated method stub

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void jobToBeExecuted(JobExecutionContext contextIn) {
            // TODO Auto-generated method stub

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void jobWasExecuted(JobExecutionContext ctx,
            JobExecutionException jobException) {
            System.out.println("JOB RAN! " + ctx.getJobDetail().getName());
            assertTrue(ctx.getJobDetail().getJobClass().equals(TestJob.class));
        }

    }

    public static class TestConfig extends Config {

        public TestConfig(Map<String, String> inConfig) {
            configuration = new TreeMap<String, String>(inConfig);
        }
    }
}
