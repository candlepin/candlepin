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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidStatus;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class QpidStatusMonitorTest {

    @Mock private QpidStatusListener listener;
    @Mock private QpidQmf qmf;
    @Mock private Configuration config;
    @Mock private ScheduledExecutorService executorService;

    private QpidStatusMonitor monitor;
    private int configuredDelay = 20;

    @Before
    public void beforeTest() {
        assertNotNull(listener);
        assertNotNull(qmf);
        when(config.getInt(eq(ConfigProperties.QPID_MODE_TRANSITIONER_DELAY))).thenReturn(configuredDelay);

        monitor = new QpidStatusMonitor(config, executorService);
        monitor.setQmf(qmf);
    }

    @Test
    public void testSchedulingAtConfiguredDelay() {
        monitor.schedule();
        verify(executorService, times(1))
            .scheduleWithFixedDelay(eq(monitor), eq(10L), eq((long) configuredDelay), eq(TimeUnit.SECONDS));
    }

    @Test
    public void listenersAreNotified() {
        when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
        monitor.addStatusChangeListener(listener);
        monitor.run();
        verify(listener, times(1)).onStatusUpdate(any(QpidStatus.class), any(QpidStatus.class));
    }

    @Test
    public void verifyStatusChangeValuesWhenTransitioning() {
        monitor.addStatusChangeListener(listener);
        when(qmf.getStatus())
            .thenReturn(QpidStatus.CONNECTED)
            .thenReturn(QpidStatus.FLOW_STOPPED)
            .thenReturn(QpidStatus.DOWN);

        monitor.run();
        verify(listener, times(1)).onStatusUpdate(eq(QpidStatus.DOWN), eq(QpidStatus.CONNECTED));

        monitor.run();
        verify(listener, times(1)).onStatusUpdate(eq(QpidStatus.CONNECTED), eq(QpidStatus.FLOW_STOPPED));

        monitor.run();
        verify(listener, times(1)).onStatusUpdate(eq(QpidStatus.FLOW_STOPPED), eq(QpidStatus.DOWN));

        verifyNoMoreInteractions(listener);
    }

}
