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
 * The CPMSessionConfig class provides standardized configuration options to be provider to the
 * session factory when creating producers.
 */
public class CPMSessionConfig {

    private boolean transactional;

    /**
     * Creates a new CPMSessionConfig instance with the default session configuration.
     */
    public CPMSessionConfig() {
        // Add defaults here as necessary
    }

    /**
     * Sets whether or not a session created using this config will be transactional.
     *
     * @param transactional
     *  Whether or not to create a transactional session
     *
     * @return
     *  a reference to this session configuration instance
     */
    public CPMSessionConfig setTransactional(boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    /**
     * Checks whether or not sessions created using this configuration should be transactional.
     *
     * @return
     *  true if the sessions created using this configuration should be transactional; false
     *  otherwise
     */
    public boolean isTransactional() {
        return this.transactional;
    }

}
