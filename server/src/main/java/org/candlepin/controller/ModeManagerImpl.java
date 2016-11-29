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

import com.google.inject.Singleton;
import org.candlepin.common.exceptions.SuspendedException;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This class drives Suspend Mode functionality. Suspend Mode is a
 * mode where Candlepin stops serving requests and only responds to
 * Status resource.
 *
 * @author fnguyen
 */
@Singleton
public class ModeManagerImpl implements ModeManager {
    private static Logger log = LoggerFactory.getLogger(ModeManagerImpl.class);
    private CandlepinModeChange cpMode = new CandlepinModeChange(
        new Date(), Mode.NORMAL, Reason.STARTUP);
    private List<ModeChangeListener> listeners = new ArrayList<ModeChangeListener>();

    @Override
    public CandlepinModeChange getLastCandlepinModeChange() {
        return cpMode;
    }

    @Override
    public void enterMode(Mode m, Reason reason) {
        if (reason == null) {
            String noReasonErrorString = "No reason supplied when trying to change CandlepinModeChange.";
            log.error(noReasonErrorString);
            throw new IllegalArgumentException(noReasonErrorString);
        }
        log.info("Entering new mode {} for reason {}", m, reason);

        Mode previousMode = cpMode.getMode();
        if (previousMode != m) {
            fireModeChangeEvent(m);
        }
        if (m.equals(Mode.SUSPEND)) {
            log.warn("Candlepin is entering suspend mode for the following reason: {}", reason);
        }

        cpMode = new CandlepinModeChange(new Date(), m, reason);
    }

    @Override
    public void throwRestEasyExceptionIfInSuspendMode() {
        if (getLastCandlepinModeChange().getMode() == Mode.SUSPEND) {
            log.debug("Mode manager detected SUSPEND mode and will throw SuspendException");
            throw new SuspendedException("Candlepin is in Suspend Mode");
        }
    }

    @Override
    public void registerModeChangeListener(ModeChangeListener l) {
        log.debug("Registering ModeChangeListener {} ", l.getClass().getSimpleName());
        listeners.add(l);
    }

    private void fireModeChangeEvent(Mode newMode) {
        log.debug("Mode changed event fired {}", newMode);
        for (ModeChangeListener l : listeners) {
            l.modeChanged(newMode);
        }
    }

    private Integer getLastModeActiveTimeSeconds() {
        return (int) ((new Date().getTime() - cpMode.getChangeTime().getTime()) / 1000);
    }
}
