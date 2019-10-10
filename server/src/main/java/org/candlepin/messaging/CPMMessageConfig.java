/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.messaging;



/**
 * The CPMMessageConfig class provides standardized configuration options to be provided to the
 * session when creating messages.
 */
public class CPMMessageConfig {

    private boolean durable;

    /**
     * Creates a new CPMMessageConfig instance with the default message configuration.
     */
    public CPMMessageConfig() {
        // Add defaults here as necessary
    }

    /**
     * Sets whether or not the message should be sent "durably," indicating that the message should
     * be available to clients that were offline at the time the message was sent.
     *
     * @param durable
     *  Whether or not to flag the message as durable
     *
     * @return
     *  a reference to this message configuration
     */
    public CPMMessageConfig setDurable(boolean durable) {
        this.durable = durable;
        return this;
    }

    /**
     * Checks if messages created from this configuration will be created durably.
     *
     * @return
     *  true if messages created from this configuration will be durable; false otherwise
     */
    public boolean isDurable() {
        return this.durable;
    }

}
