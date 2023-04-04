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

import org.candlepin.audit.ActiveMQStatus;
import org.candlepin.controller.mode.CandlepinModeManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;



// TODO: Remove this class and move the status listeners to the appropriate package related to
// each listener.


/**
 * Logic to transition Candlepin between different modes (SUSPEND, NORMAL) based
 * on what the current status of ActiveMQ broker. This class is notified of state
 * changes by listening for events from the ActiveMQStatusMonitor.
 *
 * Using this class, clients can attempt to transition to appropriate mode. The
 * attempt may be no-op if no transition is required.
 */
public class SuspendModeTransitioner implements ActiveMQStatusListener {
    private static Logger log = LoggerFactory.getLogger(SuspendModeTransitioner.class);

    private static final String REASON_CLASS_ACTIVEMQ = "ACTIVEMQ";

    private CandlepinModeManager modeManager;

    @Inject
    public SuspendModeTransitioner(CandlepinModeManager modeManager) {
        this.modeManager = Objects.requireNonNull(modeManager);
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
