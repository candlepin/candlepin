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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.fedoraproject.candlepin.pinsetter.core.ChainedListener;

import org.junit.Before;
import org.junit.Test;
import org.quartz.CronTrigger;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerListener;

import java.util.HashMap;
import java.util.Map;

/**
 * ChainedListenerTest
 * @version $Rev$
 */
public class ChainedListenerTest {
    private ChainedListener cl;
    private TestTriggerListener ttl;
    private Trigger cron;

    @Before
    public void setup() {
        cl = new ChainedListener();
        assertNotNull(cl);
        assertEquals(ChainedListener.LISTENER_NAME, cl.getName());

        ttl = new TestTriggerListener();
        assertNotNull(ttl);
        cl.addListener(ttl);

        cron = new CronTrigger();
        assertNotNull(cron);
    }

    @Test
    public void testTriggerComplete() {
        ttl.addExpectation("triggerComplete", null);
        cl.triggerComplete(cron, null, 0);
        ttl.verify();
    }

    @Test
    public void testTriggerFired() {
        ttl.addExpectation("triggerFired", null);
        cl.triggerFired(cron, null);
        ttl.verify();
    }

    @Test
    public void testTriggerMisfired() {
        ttl.addExpectation("triggerMisfired", null);
        cl.triggerMisfired(cron);
        ttl.verify();
    }

    @Test
    public void testVeto() {
        ttl.addExpectation("vetoJobExecution", null);
        cl.vetoJobExecution(cron, null);
        ttl.verify();
    }

    public static class TestTriggerListener implements TriggerListener {
        private Map<String, String> actual;
        private Map<String, String> expected;

        public TestTriggerListener() {
            actual = new HashMap<String, String>();
            expected = new HashMap<String, String>();
        }

        public void verify() {
            for (String key : expected.keySet()) {
                String erez = expected.get(key);
                if (!actual.containsKey(key)) {
                    throw new RuntimeException("key not found");
                }
                String rez = actual.get(key);

                if (erez != null) {
                    if (!erez.equals(rez)) {
                        throw new RuntimeException("results do not match");
                    }
                }

                // if erez is null, we don't care about the actual result
            }

        }

        public void addExpectation(String methodName, String result) {
            if (methodName == null) {
                throw new IllegalArgumentException("methodname is null");
            }

            expected.put(methodName, result);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getName() {
            return "TestTriggerListener";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void triggerComplete(Trigger trigger, JobExecutionContext ctx,
            int instructionCode) {
            actual.put("triggerComplete", trigger.getName());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void triggerFired(Trigger trigger, JobExecutionContext ctx) {
            actual.put("triggerFired", trigger.getName());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void triggerMisfired(Trigger trigger) {
            actual.put("triggerMisfired", trigger.getName());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean vetoJobExecution(Trigger trigger,
            JobExecutionContext ctx) {

            actual.put("vetoJobExecution", trigger.getName());
            return false;
        }
    }
}
