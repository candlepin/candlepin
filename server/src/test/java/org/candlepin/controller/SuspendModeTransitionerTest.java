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

import org.candlepin.audit.QpidStatus;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;


@RunWith(MockitoJUnitRunner.class)
public class SuspendModeTransitionerTest {
    @Mock private ModeManager modeManager;
    @Mock private CandlepinCache candlepinCache;
    @Mock private StatusCache cache;

    private SuspendModeTransitioner transitioner;
    private CandlepinModeChange startupModeChange;
    private CandlepinModeChange downModeChange;
    private CandlepinModeChange normalModeChange;

    @Before
    public void setUp() {
        when(candlepinCache.getStatusCache()).thenReturn(cache);
        transitioner = new SuspendModeTransitioner(candlepinCache);
        transitioner.setModeManager(modeManager);
        startupModeChange = new CandlepinModeChange(new Date(), Mode.NORMAL, Reason.STARTUP);
        downModeChange = new CandlepinModeChange(new Date(), Mode.SUSPEND, Reason.QPID_DOWN);
        normalModeChange = new CandlepinModeChange(new Date(), Mode.NORMAL, Reason.QPID_UP);
    }

    @Test
    public void normalConnected() {
        when(modeManager.getLastCandlepinModeChange()).thenReturn(startupModeChange);

        transitioner.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);

        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verifyNoMoreInteractions(modeManager);
    }

    @Test
    public void stillDisconnected() {
        when(modeManager.getLastCandlepinModeChange()).thenReturn(downModeChange);

        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.DOWN);

        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verifyNoMoreInteractions(modeManager);
    }


    @Test
    public void transitionFromDownToConnected() throws Exception {
        when(modeManager.getLastCandlepinModeChange()).thenReturn(downModeChange);

        transitioner.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);

        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.NORMAL, Reason.QPID_UP);
        verifyNoMoreInteractions(modeManager);
    }

    @Test
    public void transitionFromConnectedToDown() throws Exception {
        when(modeManager.getLastCandlepinModeChange()).thenReturn(normalModeChange);

        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.DOWN);

        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.SUSPEND, Reason.QPID_DOWN);
        verifyNoMoreInteractions(modeManager);
    }

    @Test
    public void transitionFromConnectedToFlowStopped()
        throws Exception {
        when(modeManager.getLastCandlepinModeChange()).thenReturn(normalModeChange);

        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.FLOW_STOPPED);

        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
        verifyNoMoreInteractions(modeManager);
    }

    @Test
    public void transitionFromConnectedToMissingExchange() throws Exception {
        when(modeManager.getLastCandlepinModeChange()).thenReturn(normalModeChange);

        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.MISSING_EXCHANGE);

        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.SUSPEND, Reason.QPID_MISSING_EXCHANGE);
        verifyNoMoreInteractions(modeManager);
    }

    @Test
    public void transitionFromConnectedToMissingBinding() {
        when(modeManager.getLastCandlepinModeChange()).thenReturn(normalModeChange);

        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.MISSING_BINDING);

        verify(modeManager, times(1)).getLastCandlepinModeChange();
        verify(modeManager, times(1)).enterMode(Mode.SUSPEND, Reason.QPID_MISSING_BINDING);
        verifyNoMoreInteractions(modeManager);
    }

}
