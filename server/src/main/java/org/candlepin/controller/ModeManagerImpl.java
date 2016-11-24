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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.SuspendedException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import com.google.inject.Inject;
import com.google.inject.Singleton;

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
    private Configuration config;

    @Inject
    public ModeManagerImpl(Configuration config) {
        this.config = config;
    }

    @Override
    public CandlepinModeChange getLastCandlepinModeChange() {
        return cpMode;
    }

    @Override
    public void enterMode(Mode m, Reason reason) {
        /**
         * When suspend mode is disabled, the Candlepin should never get into Suspend Mode.
         * Candlepin is starting always in NORMAL mode, so disalowing the transition here should
         * guarantee that.
         *
         * In fact, this method enterMode shouldn't be even called when SUSPEND mode is disabled.
         * So this check here is more of a defensive programming approach.
         */
        if (!config.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
            log.debug("Suspend mode is disabled, ignoring mode transition");
            return;
        }

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
