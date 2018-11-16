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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import org.candlepin.audit.ActiveMQStatus;
import org.candlepin.audit.QpidStatus;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


@RunWith(MockitoJUnitRunner.class)
public class SuspendModeTransitionerTest {

    @Mock private CandlepinCache candlepinCache;
    @Mock private StatusCache cache;
    @Mock private Configuration config;

    private ModeManager modeManager;
    private SuspendModeTransitioner transitioner;

    @Before
    public void setUp() {
        when(candlepinCache.getStatusCache()).thenReturn(cache);
        when(config.getBoolean(eq(ConfigProperties.SUSPEND_MODE_ENABLED))).thenReturn(true);

        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        modeManager = new ModeManagerImpl(config, i18n);

        transitioner = new SuspendModeTransitioner(candlepinCache);
        transitioner.setModeManager(modeManager);
    }

    @Test
    public void unchangedStatusUpdateDoesNotTransitionAtAll() {
        // Verify initial state.
        assertMode(Mode.NORMAL, Reason.STARTUP);

        // Nothing changed.
        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.CONNECTED);
        assertMode(Mode.NORMAL, Reason.STARTUP);

        // Nothing changed.
        transitioner.onStatusUpdate(ActiveMQStatus.CONNECTED, ActiveMQStatus.CONNECTED);
        assertMode(Mode.NORMAL, Reason.STARTUP);
    }

    @Test
    public void transitionFromNormalModeToQpidDown() {
        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.DOWN);
        assertMode(Mode.SUSPEND, Reason.QPID_DOWN);
    }

    @Test
    public void transitionFromNormalModeToAMQDown() {
        transitioner.onStatusUpdate(ActiveMQStatus.CONNECTED, ActiveMQStatus.DOWN);
        assertMode(Mode.SUSPEND, Reason.ACTIVEMQ_DOWN);
    }

    @Test
    public void transitionFromNormalModeToQpidAndAMQDown() throws Exception {
        transitionFromNormalModeToQpidDown();

        // Bring AMQ down.
        transitioner.onStatusUpdate(ActiveMQStatus.CONNECTED, ActiveMQStatus.DOWN);
        assertMode(Mode.SUSPEND, Reason.ACTIVEMQ_DOWN, Reason.QPID_DOWN);
    }

    @Test
    public void transitionToNormalModeWhenQpidComesUp() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN);

        transitioner.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);
        assertMode(Mode.NORMAL, Reason.QPID_UP);
    }

    @Test
    public void transitionToNormalModeWhenAMQComesUp() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.ACTIVEMQ_DOWN);

        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.NORMAL, Reason.ACTIVEMQ_UP);
    }

    @Test
    public void transitionToNormalModeWhenQpidThenAMQComesUp() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN, Reason.ACTIVEMQ_DOWN);

        transitioner.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);
        assertMode(Mode.SUSPEND, Reason.ACTIVEMQ_DOWN);

        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.NORMAL, Reason.ACTIVEMQ_UP);
    }

    @Test
    public void transitionToNormalModeWhenAMQThenQpidComesUp() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN, Reason.ACTIVEMQ_DOWN);

        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.SUSPEND, Reason.QPID_DOWN);

        transitioner.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);
        assertMode(Mode.NORMAL, Reason.QPID_UP);
    }

    @Test
    public void transitionToSuspendModeWhenQpidBecomesFlowStopped() throws Exception {
        modeManager.enterMode(Mode.NORMAL, Reason.STARTUP);
        transitioner.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.FLOW_STOPPED);
        assertMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
    }

    @Test
    public void remainInSuspendModeWhenQpidComesUpButIsInFlowStopped() {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN);
        transitioner.onStatusUpdate(QpidStatus.DOWN, QpidStatus.FLOW_STOPPED);
        assertMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
    }

    @Test
    public void remainInSuspendModeWhenQpidIsFlowStoppedAndThenGoesDown() {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
        transitioner.onStatusUpdate(QpidStatus.FLOW_STOPPED, QpidStatus.DOWN);
        assertMode(Mode.SUSPEND, Reason.QPID_DOWN);
    }


    @Test
    public void transitionToSuspendModeWhenAMQGoesDown() throws Exception {
        modeManager.enterMode(Mode.NORMAL, Reason.ACTIVEMQ_UP);
        transitioner.onStatusUpdate(ActiveMQStatus.CONNECTED, ActiveMQStatus.DOWN);
        assertMode(Mode.SUSPEND, Reason.ACTIVEMQ_DOWN);
    }

    @Test
    public void transitionToNormalModeWhenAMQComesBackUp() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.ACTIVEMQ_DOWN);
        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.NORMAL, Reason.ACTIVEMQ_UP);
    }

    @Test
    public void remainInSuspendModeWhenAMQComesUpWhileQpidRemainsDown() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN, Reason.ACTIVEMQ_DOWN);
        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.SUSPEND, Reason.QPID_DOWN);
    }

    @Test
    public void remainInSuspendModeWhenQpidComesUpWhileAMQRemainsDown() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN, Reason.ACTIVEMQ_DOWN);
        transitioner.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);
        assertMode(Mode.SUSPEND, Reason.ACTIVEMQ_DOWN);
    }

    @Test
    public void remainInSuspendModeWhenQpidIsFlowStoppedAndAMQComesUp() throws Exception {
        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED, Reason.ACTIVEMQ_DOWN);
        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
    }

    private List<Reason> reasons(Reason ... reasons) {
        return reasons != null ? Arrays.asList(reasons) : new ArrayList<>();
    }

    private void assertMode(Mode expectedMode, Reason ... expectedReasons) {
        CandlepinModeChange lastChange = modeManager.getLastCandlepinModeChange();
        assertEquals(expectedMode, lastChange.getMode());
        assertEquals(expectedReasons.length, lastChange.getReasons().size());
        assertTrue(lastChange.getReasons().containsAll(Arrays.asList(expectedReasons)));
    }
}
