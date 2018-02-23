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
package org.candlepin.controller;

import static org.mockito.Mockito.*;

import org.candlepin.audit.QpidConnection;
import org.candlepin.audit.QpidConnection.STATUS;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidQmf.QpidStatus;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@RunWith(MockitoJUnitRunner.class)
public class SuspendModeTransitionerTest {
    @Mock private ModeManager modeManager;
    @Mock private QpidQmf qmf;
    @Mock private QpidConnection qpidConnection;
    @Mock private ScheduledExecutorService execService;
    @Mock private CandlepinCache candlepinCache;
    @Mock private StatusCache cache;

    private SuspendModeTransitioner transitioner;
    private CandlepinModeChange startupModeChange;
    private CandlepinModeChange downModeChange;
    private CandlepinModeChange normalModeChange;

    @Before
    public void setUp() {
        when(candlepinCache.getStatusCache()).thenReturn(cache);
        CandlepinCommonTestConfig testConfig = new CandlepinCommonTestConfig();
        transitioner = new SuspendModeTransitioner(testConfig, execService, candlepinCache);
        transitioner.setModeManager(modeManager);
        transitioner.setQmf(qmf);
        transitioner.setQpidConnection(qpidConnection);
        startupModeChange = new CandlepinModeChange(new Date(), Mode.NORMAL, Reason.STARTUP);
        downModeChange = new CandlepinModeChange(new Date(), Mode.SUSPEND, Reason.QPID_DOWN);
        normalModeChange = new CandlepinModeChange(new Date(), Mode.NORMAL, Reason.QPID_UP);
    }

    @Test
    public void normalConnected() {
        when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
        when(modeManager.getLastCandlepinModeChange()).thenReturn(startupModeChange);

        transitioner.transitionAppropriately();

        verify(qmf, times(1)).getStatus();
        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verifyNoMoreInteractions(execService, modeManager, qmf);
    }

    @Test
    public void stillDisconnected() {
        when(qmf.getStatus()).thenReturn(QpidStatus.DOWN);
        when(modeManager.getLastCandlepinModeChange()).thenReturn(downModeChange);

        transitioner.transitionAppropriately();

        verify(qpidConnection, times(1)).setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        verify(qmf, times(1)).getStatus();
        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verifyNoMoreInteractions(execService, qpidConnection, modeManager, qmf);
    }


    @Test
    public void transitionFromDownToConnected() throws Exception {
        when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
        when(modeManager.getLastCandlepinModeChange()).thenReturn(downModeChange);

        transitioner.transitionAppropriately();

        verify(qmf, times(1)).getStatus();
        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.NORMAL, Reason.QPID_UP);
        verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void transitionFromConnectedToDown()
        throws Exception {
        when(qmf.getStatus()).thenReturn(QpidStatus.DOWN);
        when(modeManager.getLastCandlepinModeChange()).thenReturn(normalModeChange);

        transitioner.transitionAppropriately();

        verify(qpidConnection, times(1)).setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        verify(qmf, times(1)).getStatus();
        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.SUSPEND, Reason.QPID_DOWN);
        verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void transitionFromConnectedToFlowStopped()
        throws Exception {
        when(qmf.getStatus()).thenReturn(QpidStatus.FLOW_STOPPED);
        when(modeManager.getLastCandlepinModeChange()).thenReturn(normalModeChange);

        transitioner.transitionAppropriately();

        verify(qpidConnection, times(1)).setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        verify(qmf, times(1)).getStatus();
        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
        verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void constantPolling() throws Exception {
        when(qmf.getStatus())
            .thenReturn(QpidStatus.CONNECTED)
            .thenReturn(QpidStatus.DOWN);
        when(modeManager.getLastCandlepinModeChange())
            .thenReturn(downModeChange)
            .thenReturn(normalModeChange)
            .thenReturn(downModeChange);

        for (int i = 0; i < 10; i++) {
            transitioner.run();
        }

        verify(execService, times(10)).schedule(transitioner, 10, TimeUnit.SECONDS);
        verifyNoMoreInteractions(execService);
    }
}
