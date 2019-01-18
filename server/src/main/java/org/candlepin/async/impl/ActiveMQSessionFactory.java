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
package org.candlepin.async.impl;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;



/**
 * The ActiveMQSessionFactory provides initialization and management of both egress and ingress
 * session factories, as well as a method for fetching sessions configured for each.
 */
@Singleton
public class ActiveMQSessionFactory {
    private static Logger log = LoggerFactory.getLogger(ActiveMQSessionFactory.class);

    /**
     * Manages a session factory and session generation, recreating and reopening the factory as
     * necessary.
     */
    private static class SessionManager {
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
         * @throws ActiveMQException
         *  if an exception occurs while spinning up the ActiveMQ client session factory
         *
         * @return
         *  the ClientSessionFactory instance for this job manager
         */
        public ClientSessionFactory getClientSessionFactory() throws Exception {
            if (this.sessionFactory == null || this.sessionFactory.isClosed()) {
                log.debug("Creating new ActiveMQ client session factory...");

                this.sessionFactory = locator.createSessionFactory();
                log.debug("Created new ActiveMQ client session factory: {}", this.sessionFactory);
            }

            return this.sessionFactory;
        }

        /**
         * Fetches the current client session, creating and intializing a new instance as necessary.
         *
         * @return
         *  the current ClientSession instance for this job manager
         */
        public ClientSession getClientSession() throws Exception {
            log.debug("Creating new ActiveMQ session...");

            ClientSessionFactory csf = this.getClientSessionFactory();
            ClientSession session = csf.createSession();

            log.debug("Created new ActiveMQ session: {}", session);

            return session;
        }
    }


    private Configuration config;

    private SessionManager ingressSessionManager;
    private SessionManager egressSessionManager;


    /**
     * Creates a new ActiveMQSessionFactory using the specified Candlepin configuration.
     *
     * @param config
     *  The Candlepin configuration to use for configuring this session factory
     */
    @Inject
    public ActiveMQSessionFactory(Configuration config) {
        this.config = config;
    }


    /**
     * Fetches the ingress session manager, creating a new instance as necessary.
     *
     * @return
     *  a SessionManager for sessions configured for receiving messages
     */
    protected SessionManager getIngressSessionManager() throws Exception {
        if (this.ingressSessionManager == null) {
            // TODO:
            // This needs to be updated such that it's properly configured for whatever
            // workaround we need on the receiving side of things. If it looks like the
            // egress configuration, something is probably broken.

            ServerLocator locator = ActiveMQClient.createServerLocator(
                this.config.getProperty(ConfigProperties.ACTIVEMQ_BROKER_URL));

            // TODO: Maybe make this a bit more defensive and skip setting the property if it's
            // not present in the configuration rather than crashing out?
            locator.setMinLargeMessageSize(this.config.getInt(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE));

            this.ingressSessionManager = new SessionManager(locator);
        }

        return this.ingressSessionManager;
    }

    /**
     * Fetches the egress session manager, creating a new instance as necessary.
     *
     * @return
     *  a SessionManager for sessions configured for sending messages
     */
    protected SessionManager getEgressSessionManager() throws Exception {
        if (this.egressSessionManager == null) {
            ServerLocator locator = ActiveMQClient.createServerLocator(
                this.config.getProperty(ConfigProperties.ACTIVEMQ_BROKER_URL));

            // TODO: Maybe make this a bit more defensive and skip setting the property if it's
            // not present in the configuration rather than crashing out?
            locator.setMinLargeMessageSize(this.config.getInt(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE));

            this.egressSessionManager = new SessionManager(locator);
        }

        return this.egressSessionManager;
    }

    /**
     * Fetches a new ingress session, configured for receiving messages.
     *
     * @return
     *  a ClientSession instance configured for receiving messages
     */
    public ClientSession getIngressSession() throws Exception {
        SessionManager manager = this.getIngressSessionManager();
        return manager.getClientSession();
    }

    /**
     * Fetches a new egress session, configured for sending messages.
     *
     * @return
     *  a ClientSession instance configured for sending messages
     */
    public ClientSession getEgressSession() throws Exception {
        SessionManager manager = this.getEgressSessionManager();
        return manager.getClientSession();
    }

}
