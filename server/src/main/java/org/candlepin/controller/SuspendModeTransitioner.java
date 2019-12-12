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
import org.candlepin.controller.mode.CandlepinModeManager;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;



// TODO: Remove this class and move the status listeners to the appropriate package related to
// each listener.


/**
 * Logic to transition Candlepin between different modes (SUSPEND, NORMAL) based
 * on what the current status of ActiveMQ/Qpid brokers. This class is notified of state
 * changes by listening for events from the QpidStatusMonitor and the ActiveMQStatusMonitor.
 *
 * Using this class, clients can attempt to transition to appropriate mode. The
 * attempt may be no-op if no transition is required.
 */
public class SuspendModeTransitioner implements QpidStatusListener, ActiveMQStatusListener {
    private static Logger log = LoggerFactory.getLogger(SuspendModeTransitioner.class);

    private static final String REASON_CLASS_QPID = "QPID";
    private static final String REASON_CLASS_ACTIVEMQ = "ACTIVEMQ";

    private CandlepinModeManager modeManager;

    @Inject
    public SuspendModeTransitioner(CandlepinModeManager modeManager) {
        this.modeManager = Objects.requireNonNull(modeManager);
    }

    private void resolveReasons(String reasonClass, Object... reasons) {
        for (Object reason : reasons) {
            this.modeManager.resolveSuspendReason(reasonClass, reason.toString());
        }
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

        switch (newStatus) {
            case CONNECTED:
                this.modeManager.resolveSuspendReasonClass(REASON_CLASS_QPID);
                break;

            case DOWN:
                this.modeManager.suspendOperations(REASON_CLASS_QPID, newStatus.toString());
                this.resolveReasons(REASON_CLASS_QPID, QpidStatus.FLOW_STOPPED, QpidStatus.MISSING_EXCHANGE,
                    QpidStatus.MISSING_BINDING);
                break;

            case FLOW_STOPPED:
            case MISSING_EXCHANGE:
            case MISSING_BINDING:
                this.modeManager.suspendOperations(REASON_CLASS_QPID, newStatus.toString());
                this.resolveReasons(REASON_CLASS_QPID, QpidStatus.DOWN);
                break;

            default:
                String msg = String.format("Unknown or unexpected Qpid status received: %s", newStatus);
                throw new IllegalStateException(msg);
        }
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

        switch (newStatus) {
            case CONNECTED:
                this.modeManager.resolveSuspendReasonClass(REASON_CLASS_ACTIVEMQ);
                break;

            case DOWN:
                this.modeManager.suspendOperations(REASON_CLASS_ACTIVEMQ, newStatus.toString());
                break;

            default:
                String msg = String.format("Unknown or unexpected ActiveMQ status received: %s", newStatus);
                throw new IllegalStateException(msg);
        }
    }
}
