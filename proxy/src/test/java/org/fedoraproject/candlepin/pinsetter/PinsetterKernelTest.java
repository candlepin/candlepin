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
package org.fedoraproject.candlepin.pinsetter;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.configuration.CandlepinConfigurationTest.CandlepinConfigurationForTesting;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * PinsetterKernelTest
 * @version $Rev$
 */
public class PinsetterKernelTest {
    private PinsetterKernel pk = null;

    @Test
    public void defaultCtor() {
        try {
            new PinsetterKernel();
            fail();
        }
        catch (InstantiationException ie) {
            assertTrue(true);
        }
    }

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
//                    put("org.quartz.scheduler.instanceName", "MyClusteredScheduler");
//                    put("org.quartz.scheduler.instanceId", "AUTO");
//                    put("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
//                    put("org.quartz.jobStore.driverDelegateClass",
//                        "org.quartz.impl.jdbcjobstore.HSQLDBDelegate");
//                    put("org.quartz.jobStore.tablePrefix", "QRTZ_");
//                    put("org.quartz.jobStore.isClustered", "true");
//
//                    put("org.quartz.dataSource.myDS.driver", "org.hsqldb.jdbcDriver");
//                    put("org.quartz.dataSource.myDS.URL", "jdbc:hsqldb:mem:unit-testing-jpa");
//                    put("org.quartz.dataSource.myDS.user", "sa");
//                    put("org.quartz.dataSource.myDS.password", "");
//                    put("org.quartz.jobStore.dataSource", "myDS");
                }
            });

        pk = new PinsetterKernel(config);
        assertNotNull(pk);

        try {
            pk.startup();
            pk.shutdown();
        }
        catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    public static class TestConfig extends Config {

        public TestConfig(Map<String, String> inConfig) {
            configuration = new TreeMap<String, String>(inConfig);
        }
    }
}
