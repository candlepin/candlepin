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
import org.fedoraproject.candlepin.pinsetter.core.PinsetterException;
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
    public void configure() throws InstantiationException, InterruptedException {
        ThreadGroup tg = new ThreadGroup("testing");

        Config config = new CandlepinConfigurationForTesting(
            new HashMap<String, String>() {

                {
                    put("org.quartz.threadPool.class",
                        "org.quartz.simpl.SimpleThreadPool");
                    put("org.quartz.threadPool.threadCount", "25");
                    put("org.quartz.threadPool.threadPriority", "5");
                }
            });

        pk = new PinsetterKernel(config);
        assertNotNull(pk);

        try {
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        pk.startup();
                    }
                    catch (PinsetterException e) {
                        fail(e.getMessage());
                    }
                }
            };
            Thread t = new Thread(r);
            t.start();
        }
        catch (Throwable t) {
            fail(t.getMessage());
        }

        Thread.sleep(5000);

        pk.startShutdown();
    }

    public static class TestConfig extends Config {

        public TestConfig(Map<String, String> inConfig) {
            configuration = new TreeMap<String, String>(inConfig);
        }
    }
}
