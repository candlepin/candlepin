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

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.candlepin.common.exceptions.SuspendedException;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Date;
import java.util.Locale;


public class ModeManagerTest {
    private ModeManager modeManager;
    private Reason testReason;
    private boolean modeChanged;

    @Before
    public void setUp() {
        I18n mockedi18 = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        modeManager = new ModeManagerImpl(new CandlepinCommonTestConfig(), mockedi18);
        testReason = Reason.STARTUP;
        modeChanged = false;
    }

    @Test
    public void testInitialModeIsNormal() {
        assertEquals(Mode.NORMAL, modeManager.getLastCandlepinModeChange().getMode());
    }

    @Test
    public void testEnterMode() throws InterruptedException {
        Date previousTime = modeManager.getLastCandlepinModeChange().getChangeTime();
        Mode mode = Mode.SUSPEND;
        sleep(1);
        modeManager.enterMode(mode, testReason);
        assertEquals(mode, modeManager.getLastCandlepinModeChange().getMode());
        assertEquals(testReason, modeManager.getLastCandlepinModeChange().getReason());
        assertTrue(previousTime.before(modeManager.getLastCandlepinModeChange().getChangeTime()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnterModeNeedsReason() {
        modeManager.enterMode(Mode.NORMAL, null);
    }

    @Test(expected = SuspendedException.class)
    public void throwRestEasyExceptionIfInSuspendMode() {
        modeManager.enterMode(Mode.SUSPEND, testReason);
        modeManager.throwRestEasyExceptionIfInSuspendMode();
    }

    @Test
    public void registerModeChangeListener() {
        ModeChangeListener listener = new ModeChangeListener() {
            @Override
            public void modeChanged(Mode newMode) {
                modeChanged = true;
            }
        };

        modeManager.registerModeChangeListener(listener);
        modeManager.enterMode(Mode.SUSPEND, testReason);
        assertEquals(true, modeChanged);
    }

}
