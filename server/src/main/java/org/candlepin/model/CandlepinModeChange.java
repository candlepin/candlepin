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
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Class representing candlepins mode, (NORMAL || SUSPEND),
 * and the associated reason for this mode.
 */
public class CandlepinModeChange implements Serializable {
    private static final long serialVersionUID = -7059065874812188168L;
    private final Mode mode;
    private final Set<Reason> reasons;
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
         * The Qpid broker has gone down and is not available.
         */
        QPID_DOWN,
        /**
         * The Qpid broker has come online and is available.
         */
        QPID_UP,
        /**
         * Qpid is overloaded to a point where its not possible to
         * send messages to it
         */
        QPID_FLOW_STOPPED,

        /**
         * The ActiveMQ broker has come online and is available.
         */
        ACTIVEMQ_UP,

        /**
         * The ActiveMQ broker has gone down and is not available.
         */
        ACTIVEMQ_DOWN
    }

    public CandlepinModeChange(Date changeTime, Mode mode, Reason ... reasons) {
        this.mode = mode;
        this.changeTime = changeTime;
        this.reasons = reasons != null ? new HashSet<>(Arrays.asList(reasons)) : new HashSet<>();
    }

    public Set<Reason> getReasons() {
        return reasons;
    }

    public Mode getMode() {
        return mode;
    }

    public Date getChangeTime() {
        return changeTime;
    }

    @Override
    public String toString() {
        return "CandlepinModeChange [mode=" + mode + ", reasons=" + reasons + ", changeTime=" +
            changeTime + "]";
    }


}
