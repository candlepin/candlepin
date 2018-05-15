/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

/**
 * An interface for objects that would like to listen for Qpid status updates.
 */
public interface QpidStatusListener {

    /**
     * Called when QpidStatusMonitor determines the latest Qpid status.
     *
     * @param oldStatus the status of Qpid on the previous update.
     * @param newStatus the current status of Qpid.
     */
    void onStatusUpdate(QpidStatus oldStatus, QpidStatus newStatus);
}
