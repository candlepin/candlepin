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

import org.candlepin.audit.QpidStatus;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Logic to transition Candlepin between different modes (SUSPEND, NORMAL) based
 * on what the current status of Qpid Broker. This class is notified of state
 * changes by listening for events from the QpidStatusMonitor.
 *
 * Using this class, clients can attempt to transition to appropriate mode. The
 * attempt may be no-op if no transition is required.
 *
 */
public class SuspendModeTransitioner implements QpidStatusListener {
    private static Logger log = LoggerFactory.getLogger(SuspendModeTransitioner.class);

    private ModeManager modeManager;
    private CandlepinCache candlepinCache;

    @Inject
    public SuspendModeTransitioner(CandlepinCache cache) {
        this.candlepinCache = cache;
    }

    /**
     * Other dependencies are injected using method injection so
     * that Guice can handle circular dependency between SuspendModeTransitioner
     * and the QpidConnection
     */
    @Inject
    public void setModeManager(ModeManager modeManager) {
        this.modeManager = modeManager;
    }

    /**
     * Called each time the QpidStatusMonitor checks for a Qpid status update,
     * and based on this change, updates the candlepin mode.
     *
     * @param oldStatus the status of Qpid on the previous update.
     * @param newStatus the current status of Qpid.
     */
    @Override
    public void onStatusUpdate(QpidStatus oldStatus, QpidStatus newStatus) {
        transitionAppropriately(newStatus);
    }

    /**
     * Attempts to transition Candlepin according to current Mode and current status of
     * the Qpid Broker. Logs and swallows possible exceptions - theoretically
     * there should be none.
     *
     * Most of the time the transition won't be required and this method will be no-op.
     * There is an edge-case when transitioning from SUSPEND to NORMAL mode.
     * During that transition, there is a small time window between checking the
     * Qpid status and attempt to reconnect. If the Qpid status is reported as
     * Qpid up, the transitioner will try to reconnect to the broker. This reconnect
     * may fail. In that case the transition to NORMAL mode shouldn't go through.
     */
    private synchronized void transitionAppropriately(QpidStatus status) {
        log.debug("Attempting to transition to appropriate Mode");
        CandlepinModeChange modeChange = modeManager.getLastCandlepinModeChange();
        log.debug("Qpid status is {}, the current mode is {}", status, modeChange);

        if (modeChange.getMode() == Mode.SUSPEND) {
            Mode newMode;
            Reason newReason;
            switch (status) {
                case CONNECTED:
                    log.info("Connection to qpid is restored! Reconnecting Qpid and" +
                        " entering NORMAL mode");
                    newMode = Mode.NORMAL;
                    newReason = Reason.QPID_UP;
                    break;
                case FLOW_STOPPED:
                    newMode = Mode.SUSPEND;
                    newReason = Reason.QPID_FLOW_STOPPED;
                    break;
                case MISSING_EXCHANGE:
                    newMode = Mode.SUSPEND;
                    newReason = Reason.QPID_MISSING_EXCHANGE;
                    break;
                case MISSING_BINDING:
                    newMode = Mode.SUSPEND;
                    newReason = Reason.QPID_MISSING_BINDING;
                    break;
                case DOWN:
                    log.debug("Staying in {} mode.", status);
                    newMode = Mode.SUSPEND;
                    newReason = Reason.QPID_DOWN;
                    break;
                default:
                    throw new RuntimeException("Unknown status: " + status);
            }

            if (!modeChange.getMode().equals(newMode) || !modeChange.getReason().equals(newReason)) {
                log.debug("Change since going into SUSPEND MODE: {}:{}", newMode, newReason);
                modeManager.enterMode(newMode, newReason);
                cleanStatusCache();
            }
            else {
                log.debug("No mode change since going into SUSPEND MODE.");
            }
        }
        else if (modeChange.getMode() == Mode.NORMAL) {
            switch (status) {
                case FLOW_STOPPED:
                    log.debug("Will need to transition Candlepin into SUSPEND Mode because " +
                        "the Qpid connection is flow stopped");
                    modeManager.enterMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
                    cleanStatusCache();
                    break;
                case MISSING_EXCHANGE:
                    log.debug("Will need to transition Candlepin into SUSPEND Mode because " +
                        "the Qpid event queue's exchange is missing.");
                    modeManager.enterMode(Mode.SUSPEND, Reason.QPID_MISSING_EXCHANGE);
                    cleanStatusCache();
                    break;
                case MISSING_BINDING:
                    log.debug("Will need to transition Candlepin into SUSPEND Mode because " +
                        "the Qpid event queue's exchange has no binding.");
                    modeManager.enterMode(Mode.SUSPEND, Reason.QPID_MISSING_BINDING);
                    cleanStatusCache();
                    break;
                case DOWN:
                    log.debug("Will need to transition Candlepin into SUSPEND Mode because " +
                        "the Qpid connection is down");
                    modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN);
                    cleanStatusCache();
                    break;
                case CONNECTED:
                    log.debug("Connection to Qpid is ok and current mode is NORMAL. No-op!");
                    break;
                default:
                    throw new RuntimeException("Unknown status: " + status);
            }
        }
    }

    /**
     * Cleans Status Cache. We need to do this so that client's don't see
     * cached status response in case of a mode change.
     */
    private void cleanStatusCache() {
        candlepinCache.getStatusCache().clear();
    }

}
