/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.candlepin.audit.ActiveMQStatus;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ActiveMQStatusMonitorTest {

    @Mock private Configuration config;

    @Before
    public void setup() {
        when(config.getLong(ConfigProperties.ACTIVEMQ_CONNECTION_MONITOR_INTERVAL)).thenReturn(500L);
    }

    @Test
    public void monitorNotifiesListenersOfConnectionChanges() throws Exception {
        TestingListener listener = new TestingListener();
        TestingMonitor monitor = new TestingMonitor(config);
        monitor.registerListener(listener);

        // Connection at this point is Ok. Close the connection to start the
        // connection monitoring thread.
        monitor.setConnectionOk(false);
        monitor.connectionClosed();

        // Sleep for a second to allow the monitoring thread to report a failure
        // before changing the connection state.
        Thread.sleep(1000);

        // Set the connection state and then sleep again to allow the thread to report
        // the connection.
        monitor.setConnectionOk(true);
        Thread.sleep(1000);

        // Assert that both states were reported.
        assertTrue(listener.getReported().contains(ActiveMQStatus.DOWN));
        assertTrue(listener.getReported().contains(ActiveMQStatus.CONNECTED));

        // Check that the listener had received the correct states. Only the last reported
        // state should have been CONNECTED as the monitor is shut down once a connection
        // is reported.
        Iterator<ActiveMQStatus> iter = listener.getReported().iterator();
        while (iter.hasNext()) {
            ActiveMQStatus next = iter.next();
            // Only the last should be connected.
            if (iter.hasNext()) {
                assertEquals(ActiveMQStatus.DOWN, next);
            }
            else {
                assertEquals(ActiveMQStatus.CONNECTED, next);
            }
        }
    }

    private class TestingMonitor extends ActiveMQStatusMonitor {

        public TestingMonitor(Configuration config) throws Exception {
            super(config);
            this.connectionOk = true;
        }

        @Override
        public void initializeLocator() throws Exception {
            // Bypass static locator creation
        }

        @Override
        public boolean testConnection() {
            return connectionOk;
        }

        public void setConnectionOk(boolean ok) {
            this.connectionOk = ok;
        }

    }

    private class TestingListener implements ActiveMQStatusListener {
        private List<ActiveMQStatus> reported = new LinkedList<>();

        @Override
        public void onStatusUpdate(ActiveMQStatus oldStatus, ActiveMQStatus newStatus) {
            reported.add(newStatus);
        }

        public List<ActiveMQStatus> getReported() {
            return this.reported;
        }
    }
}
