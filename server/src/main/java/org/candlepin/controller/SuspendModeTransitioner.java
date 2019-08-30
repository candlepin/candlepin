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

import org.candlepin.audit.ActiveMQStatus;
import org.candlepin.audit.QpidStatus;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Logic to transition Candlepin between different modes (SUSPEND, NORMAL) based
 * on what the current status of ActiveMQ/Qpid brokers. This class is notified of state
 * changes by listening for events from the QpidStatusMonitor and the ActiveMQStatusMonitor.
 *
 * Using this class, clients can attempt to transition to appropriate mode. The
 * attempt may be no-op if no transition is required.
 *
 */
public class SuspendModeTransitioner implements QpidStatusListener, ActiveMQStatusListener,
    ActiveMQQueueHealthListener {
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
        if (oldStatus.equals(newStatus)) {
            // nothing to do
            return;
        }

        Reason reason;
        if (QpidStatus.CONNECTED.equals(newStatus)) {
            reason = Reason.QPID_UP;
        }
        else if (QpidStatus.DOWN.equals(newStatus)) {
            reason = Reason.QPID_DOWN;
        }
        else if (QpidStatus.FLOW_STOPPED.equals(newStatus)) {
            reason = Reason.QPID_FLOW_STOPPED;
        }
        else if (QpidStatus.MISSING_EXCHANGE.equals(newStatus)) {
            reason = Reason.QPID_MISSING_EXCHANGE;
        }
        else if (QpidStatus.MISSING_BINDING.equals(newStatus)) {
            reason = Reason.QPID_MISSING_BINDING;
        }
        else {
            String msg = String.format("Could not transition candlepin mode: Unknown Qpid status: %s",
                newStatus);
            throw new IllegalArgumentException(msg);
        }
        transitionAppropriately(reason);
    }

    /**
     * Called each time the ActiveMQStatusMonitor checks the status of the broker.
     * Updates the current mode based on the status.
     */
    @Override
    public void onStatusUpdate(ActiveMQStatus oldStatus, ActiveMQStatus newStatus) {
        if (oldStatus.equals(newStatus)) {
            // nothing to do
            return;
        }

        Reason reason;
        if (ActiveMQStatus.CONNECTED.equals(newStatus)) {
            reason = Reason.ACTIVEMQ_UP;
        }
        else if (ActiveMQStatus.DOWN.equals(newStatus)) {
            reason = Reason.ACTIVEMQ_DOWN;
        }
        else {
            String msg = String.format("Could not transition candlepin mode: Unknown ActiveMQ status: %s",
                newStatus);
            throw new IllegalArgumentException(msg);
        }
        transitionAppropriately(reason);
    }

    /**
     * Attempts to transition Candlepin according to current Mode and current status of
     * the Qpid/ActiveMQ broker. Logs and swallows possible exceptions - theoretically
     * there should be none.
     *
     * Most of the time the transition won't be required and this method will be no-op.
     *
     * Because suspend mode can be triggered by multiple sources, transitioning is based
     * on reasons why the mode was changed. For example, in a case where the Qpid and
     * ActiveMQ brokers are down, candlepin should remain in suspend mode until both
     * brokers come back up. In this case, when a single broker comes back online, if
     * the previous reason indicates that the other broker was down, then candlepin
     * remains in suspend mode.
     *
     * There is an edge-case when transitioning from SUSPEND to NORMAL mode.
     * During that transition, there is a small time window between checking the
     * Qpid status and attempt to reconnect. If the Qpid status is reported as
     * Qpid up, the transitioner will try to reconnect to the broker. This reconnect
     * may fail. In that case the transition to NORMAL mode shouldn't go through.
     */
    private synchronized void transitionAppropriately(Reason reason) {
        log.debug("Attempting to transition to appropriate Mode");

        CandlepinModeChange lastModeChange = modeManager.getLastCandlepinModeChange();

        if (lastModeChange.getReasons().contains(reason)) {
            log.debug("No transition required. {} already known.");
            return;
        }

        log.debug("Reason for transition is {}, the current mode is {}", reason, lastModeChange);
        Set<Reason> lastReasons = new HashSet<>(lastModeChange.getReasons());
        if (lastModeChange.getMode() == Mode.SUSPEND) {
            switch (reason) {
                case QPID_UP:
                    resetQpidReasons(lastReasons);
                    break;
                case QPID_FLOW_STOPPED:
                case QPID_DOWN:
                case QPID_MISSING_BINDING:
                case QPID_MISSING_EXCHANGE:
                    resetQpidReasons(lastReasons, reason);
                    break;
                case ACTIVEMQ_UP:
                    resetActiveMQReasons(lastReasons);
                    break;
                case ACTIVEMQ_DOWN:
                    resetActiveMQReasons(lastReasons, reason);
                    break;
                default:
                    throw new RuntimeException("Unknown reason for entering SUSPEND mode: " + reason);
            }

            // No more issues, can exit suspend mode
            if (lastReasons.isEmpty()) {
                log.info("Entering NORMAL mode.");
                modeManager.enterMode(Mode.NORMAL, reason);
                cleanStatusCache();
            }
            else {
                modeManager.enterMode(Mode.SUSPEND, lastReasons.toArray(new Reason[lastReasons.size()]));
                cleanStatusCache();
            }
        }
        else if (lastModeChange.getMode() == Mode.NORMAL) {
            switch (reason) {
                case QPID_FLOW_STOPPED:
                case QPID_MISSING_BINDING:
                case QPID_MISSING_EXCHANGE:
                case QPID_DOWN:
                case ACTIVEMQ_DOWN:
                    log.debug("Will need to transition Candlepin into SUSPEND Mode: {}", reason);
                    modeManager.enterMode(Mode.SUSPEND, reason);
                    cleanStatusCache();
                    break;
                case QPID_UP:
                case ACTIVEMQ_UP:
                    log.debug("All connections are Ok and current mode is NORMAL. No-op!");
                    break;
                default:
                    throw new RuntimeException("Unknown reason for entering NORMAL mode: " + reason);
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

    private void resetQpidReasons(Set<Reason> reasons, Reason ... keep) {
        List<Reason> allAMQ = Arrays.asList(
            Reason.QPID_UP,
            Reason.QPID_DOWN,
            Reason.QPID_FLOW_STOPPED,
            Reason.QPID_MISSING_BINDING,
            Reason.QPID_MISSING_EXCHANGE);
        resetReasonGroup(reasons, allAMQ, keep);
    }

    private void resetActiveMQReasons(Set<Reason> reasons, Reason ... keep) {
        List<Reason> allAMQ = Arrays.asList(Reason.ACTIVEMQ_DOWN, Reason.ACTIVEMQ_UP);
        resetReasonGroup(reasons, allAMQ, keep);
    }

    private void resetReasonGroup(Set<Reason> reasons, List<Reason> reasonGroup, Reason ... keep) {
        List<Reason> toKeep = keep != null ? Arrays.asList(keep) : new ArrayList<>();
        if (!reasonGroup.containsAll(toKeep)) {
            throw new IllegalArgumentException(
                String.format("One of %s is not part of group: %s", reasonGroup, toKeep));
        }
        reasons.removeAll(reasonGroup);
        reasons.addAll(toKeep);
    }

}
