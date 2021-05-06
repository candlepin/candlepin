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
 * The CPMSessionFactory interface defines methods for creating messaging sessions to a backing
 * message broker.
 */
public interface CPMSessionFactory {

    /**
     * Fetches a string representing the provider of this session factory.
     *
     * @return
     *  The provider of this session factory
     */
    String getProvider();

    /**
     * Initializes this session factory. If the factory has already been initialized, this method
     * throws an IllegalStateException.
     *
     * @throws IllegalStateException
     *  if this factory is already initialized
     */
    void initialize() throws CPMException;

    /**
     * Checks if this factory has been intialized.
     *
     * @return
     *  true if the factory has been initialized; false otherwise
     */
    boolean isInitialized();

    /**
     * Creates a new session configuration instance with the factory-default settings.
     *
     * @return
     *  a new session configuration instance with the factory-default settings
     */
    CPMSessionConfig createSessionConfig();

    /**
     * Creates a new session using the factory default configuration.
     *
     * @return
     *  a new session
     */
    CPMSession createSession() throws CPMException;

    /**
     * Creates a new session using the provided configuration. If the configuration is null,
     * the factory default configuration will be used instead.
     *
     * @param config
     *  the session configuration to use
     *
     * @return
     *  a new session
     */
    CPMSession createSession(CPMSessionConfig config) throws CPMException;

}
