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
package org.candlepin.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Class representing candlepins mode, (NORMAL || SUSPEND),
 * and the associated reason for this mode.
 */
public class CandlepinModeChange implements Serializable {
    private static final long serialVersionUID = -7059065874812188168L;
    private final Mode mode;
    private final Reason reason;
    private final Date changeTime;

    /**
     * Mode states enum.
     */
    public enum Mode {
        SUSPEND,
        NORMAL
    }

    /**
     * Change reason
     */
    public enum Reason {
        /**
         * When Candlepin is starting up, it by default starts
         * in NORMAL mode
         */
        STARTUP,
        /**
         * When Qpid broker isn't available, Candlepin must enter
         * Suspend mode
         */
        QPID_DOWN,
        /**
         * Qpid comes back up
         */
        QPID_UP,
        /**
         * Qpid is overloaded to a point where its not possible to
         * send messages to it
         */
        QPID_FLOW_STOPPED,
        /**
         * Qpid event queue is missing the appropriate exchange.
         */
        QPID_MISSING_EXCHANGE,
        /**
         * Qpid event queue's exchange has no bindings.
         */
        QPID_MISSING_BINDING
    }

    public CandlepinModeChange(Date changeTime, Mode mode, Reason reason) {
        this.mode = mode;
        this.changeTime = changeTime;
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public Mode getMode() {
        return mode;
    }

    public Date getChangeTime() {
        return changeTime;
    }

    @Override
    public String toString() {
        return "CandlepinModeChange [mode=" + mode + ", reason=" + reason + ", changeTime=" +
            changeTime + "]";
    }


}
