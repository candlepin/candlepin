/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.audit.ActiveMQStatus;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.controller.mode.ModeChangeReason;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;


public class SuspendModeTransitionerTest {
    private DevConfig config;

    private CandlepinModeManager modeManager;
    private SuspendModeTransitioner transitioner;

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.SUSPEND_MODE_ENABLED, "true");

        this.modeManager = new CandlepinModeManager();
        this.transitioner = new SuspendModeTransitioner(modeManager);
    }

    @Test
    public void unchangedStatusUpdateDoesNotTransitionAtAll() {
        // Verify initial state.
        assertMode(Mode.NORMAL);

        // Nothing changed.
        transitioner.onStatusUpdate(ActiveMQStatus.CONNECTED, ActiveMQStatus.CONNECTED);
        assertMode(Mode.NORMAL);
    }

    @Test
    public void transitionFromNormalModeToAMQDown() {
        transitioner.onStatusUpdate(ActiveMQStatus.CONNECTED, ActiveMQStatus.DOWN);
        assertMode(Mode.SUSPEND, ActiveMQStatus.DOWN);
    }

    @Test
    public void transitionToNormalModeWhenAMQComesUp() {
        this.suspendOperations(ActiveMQStatus.DOWN);

        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.NORMAL);
    }

    @Test
    public void transitionToSuspendModeWhenAMQGoesDown() {
        transitioner.onStatusUpdate(ActiveMQStatus.CONNECTED, ActiveMQStatus.DOWN);
        assertMode(Mode.SUSPEND, ActiveMQStatus.DOWN);
    }

    @Test
    public void transitionToNormalModeWhenAMQComesBackUp() {
        this.suspendOperations(ActiveMQStatus.DOWN);

        transitioner.onStatusUpdate(ActiveMQStatus.DOWN, ActiveMQStatus.CONNECTED);
        assertMode(Mode.NORMAL);
    }

    private void suspendOperations(Object... reasons) {
        for (Object reason : reasons) {
            if (reason instanceof ActiveMQStatus) {
                this.modeManager.suspendOperations("ACTIVEMQ", reason.toString());
            }
            else {
                throw new IllegalArgumentException("Unexpected MCR conversion");
            }
        }
    }

    private Set<ModeChangeReason> translateReasons(Object[] input) {
        Set<ModeChangeReason> output = new HashSet<>();

        for (Object obj : input) {
            if (obj instanceof ActiveMQStatus) {
                output.add(new ModeChangeReason("ACTIVEMQ", obj.toString(), new Date(), null));
            }
            else {
                throw new IllegalArgumentException("Unexpected MCR conversion");
            }
        }

        return output;
    }

    private void assertMode(Mode expectedMode, Object... expectedReasons) {
        assertEquals(expectedMode, this.modeManager.getCurrentMode());

        if (expectedReasons != null) {
            Set<ModeChangeReason> reasons = this.modeManager.getModeChangeReasons();
            Set<ModeChangeReason> expected = this.translateReasons(expectedReasons);

            assertEquals(expected, reasons);
        }
    }

}
