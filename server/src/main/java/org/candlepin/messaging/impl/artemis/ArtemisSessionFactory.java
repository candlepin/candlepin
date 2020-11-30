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
package org.candlepin.messaging.impl.artemis;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMSessionConfig;
import org.candlepin.messaging.CPMSessionFactory;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;



/**
 * CPMSessionFactory implementation backed by Artemis
 */
public class ArtemisSessionFactory implements CPMSessionFactory {
    private static Logger log = LoggerFactory.getLogger(ArtemisSessionFactory.class);

    /**
     * Manages a session factory and session generation, recreating and reopening the factory as
     * necessary.
     */
    private static class SessionManager implements CloseListener {
        private final ServerLocator locator;

        private ClientSessionFactory sessionFactory;

        /**
         * Creates a new SessionManager instance using the provided locator to create the session
         * factories
         *
         * @param locator
         *  The locator to use for creating session factories
         */
        public SessionManager(ServerLocator locator) {
            if (locator == null) {
                throw new IllegalArgumentException("locator is null");
            }

            this.locator = locator;
        }

        /**
         * Fetches the current session factory, initializing a new instance as necessary. This should
         * almost certainly not be used by any method other than getClientSession.
         *
         * @throws Exception
         *  if an exception occurs while spinning up the ActiveMQ client session factory
         *
         * @return
         *  the ClientSessionFactory instance for this job manager
         */
        public synchronized ClientSessionFactory getClientSessionFactory() throws Exception {
            if (this.sessionFactory == null || this.sessionFactory.isClosed()) {
                log.debug("Creating new Artemis client session factory...");

                this.sessionFactory = this.locator.createSessionFactory();
                this.sessionFactory.getConnection().addCloseListener(this);

                log.debug("Created new Artemis client session factory: {}", this.sessionFactory);
            }

            return this.sessionFactory;
        }

        /**
         * Fetches the current client session, creating and initializing a new instance as necessary.
         *
         * @param config
         *  the session configuration to use to create the session
         *
         * @throws Exception
         *  if an exception occurs while spinning up the ActiveMQ client session
         *
         * @return
         *  the current ClientSession instance for this job manager
         */
        public ClientSession createClientSession(CPMSessionConfig config) throws Exception {
            log.debug("Creating new Artemis session...");

            ClientSessionFactory csf = this.getClientSessionFactory();

            ClientSession session = config.isTransactional() ?
                csf.createTransactedSession() :
                csf.createSession();

            log.debug("Created new Artemis session: {}", session);

            return session;
        }

        @Override
        public void connectionClosed() {
            if (this.sessionFactory != null) {
                this.sessionFactory.close();
            }
        }
    }



    private Configuration config;

    private CPMSessionConfig defaultSessionConfig;

    private boolean initialized;
    private ServerLocator locator;
    private SessionManager sessionManager;


    /**
     * Creates a new ArtemisSessionFactory using the provided Candlepin configuration.
     *
     * @param config
     *  The Candlepin configuration to use to initialize this session factory
     */
    @Autowired
    public ArtemisSessionFactory(Configuration config) {
        this.config = Objects.requireNonNull(config);

        this.initialized = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProvider() {
        return ArtemisUtil.PROVIDER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void initialize() throws CPMException {
        if (this.isInitialized()) {
            throw new IllegalStateException("Session factory is already initialized");
        }

        try {
            // Create server locator
            String brokerUrl = this.config.getProperty(ConfigProperties.ACTIVEMQ_BROKER_URL);
            log.info("Connecting to Artemis server at {}", brokerUrl);

            this.locator = ActiveMQClient.createServerLocator(brokerUrl);
            this.sessionManager = new SessionManager(this.locator);

            this.initialized = true;
            log.info("Artemis session factory initialized");
        }
        catch (Exception e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isInitialized() {
        return this.initialized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMSessionConfig createSessionConfig() {
        CPMSessionConfig config = new CPMSessionConfig();

        // TODO: Add any session config from candlepin.conf

        return config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtemisSession createSession() throws CPMException {
        return this.createSession(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ArtemisSession createSession(CPMSessionConfig config) throws CPMException {
        if (!this.isInitialized()) {
            throw new IllegalStateException("Cannot create new sessions before factory initialization");
        }

        if (config == null) {
            config = this.createSessionConfig();
        }

        try {
            ClientSession acs = this.sessionManager.createClientSession(config);

            return new ArtemisSession(acs);
        }
        catch (Exception e) {
            throw new CPMException(e);
        }
    }

}
