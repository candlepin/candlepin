/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

package org.candlepin.audit;

/**
 * Represents the status of the connection to the ActiveMQ broker.
 */
public enum ActiveMQStatus {

    /**
     * The broker instance is down and
     */
    DOWN,

    /**
     * The broker instance is online and available for new connections.
     */
    CONNECTED,

    /**
     * The broker status is currently unknown. This state should only be set on
     * candlepin startup and should be updated accordingly when the candlepin
     * context is loaded and all listeners have been registered.
     */
    UNKNOWN

}
