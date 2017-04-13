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
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@RunWith(MockitoJUnitRunner.class)
public class SuspendModeTransitionerTest {
    private SuspendModeTransitioner transitioner;
    @Mock
    private ModeManager modeManager;
    @Mock
    private QpidQmf qmf;
    @Mock
    private QpidConnection qpidConnection;
    @Mock
    private ScheduledExecutorService execService;
    private CandlepinModeChange startupModeChange;
    private CandlepinModeChange downModeChange;
    private CandlepinModeChange normalModeChange;
    @Mock
    private CandlepinCache candlepinCache;
    @Mock
    private StatusCache cache;

    @Before
    public void setUp() {
        Mockito.when(candlepinCache.getStatusCache())
            .thenReturn(cache);
        CandlepinCommonTestConfig testConfig =
            new CandlepinCommonTestConfig();
        transitioner = new SuspendModeTransitioner(testConfig, execService,
            candlepinCache);
        transitioner.setModeManager(modeManager);
        transitioner.setQmf(qmf);
        transitioner.setQpidConnection(qpidConnection);
        startupModeChange = new CandlepinModeChange(new Date(),
            Mode.NORMAL, Reason.STARTUP);

        downModeChange = new CandlepinModeChange(new Date(),
            Mode.SUSPEND, Reason.QPID_DOWN);

        normalModeChange = new CandlepinModeChange(new Date(),
            Mode.NORMAL, Reason.QPID_UP);
    }

    @Test
    public void normalConnected() {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(startupModeChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verifyNoMoreInteractions(execService, modeManager, qmf);
    }

    @Test
    public void stillDisconnected() {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.DOWN);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(downModeChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qpidConnection, Mockito.times(1))
            .setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, modeManager, qmf);
    }


    @Test
    public void transitionFromDownToConnected() throws Exception {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(downModeChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1)).enterMode(Mode.NORMAL, Reason.QPID_UP);
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void transitionFromConnectedToDown()
        throws Exception {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.DOWN);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(normalModeChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qpidConnection, Mockito.times(1))
            .setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1))
            .enterMode(Mode.SUSPEND, Reason.QPID_DOWN);
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void transitionFromConnectedToFlowStopped()
        throws Exception {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.FLOW_STOPPED);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(normalModeChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qpidConnection, Mockito.times(1))
            .setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1))
            .enterMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void backOff()
        throws Exception {
        Mockito.when(qmf.getStatus())
            .thenReturn(QpidStatus.CONNECTED)
            .thenReturn(QpidStatus.DOWN)
            .thenReturn(QpidStatus.DOWN)
            .thenReturn(QpidStatus.CONNECTED);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(downModeChange)
            .thenReturn(normalModeChange)
            .thenReturn(downModeChange)
            .thenReturn(downModeChange);

        transitioner.run();

        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 10L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);
        Mockito.reset(execService);

        transitioner.run();

        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 10L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);

        Mockito.reset(execService);
        transitioner.run();
        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 20L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);

        Mockito.reset(execService);
        transitioner.run();
        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 10L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);
    }


    @Test
    public void backOffMaximum()
        throws Exception {
        Mockito.when(qmf.getStatus())
            .thenReturn(QpidStatus.CONNECTED)
            .thenReturn(QpidStatus.DOWN);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(downModeChange)
            .thenReturn(normalModeChange)
            .thenReturn(downModeChange);

        for (int i = 0; i < 10; i++) {
            transitioner.run();
        }

        Mockito.reset(execService);
        transitioner.run();
        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 100L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);
    }
}
