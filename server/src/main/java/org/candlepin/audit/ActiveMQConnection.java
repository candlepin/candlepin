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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract base class representing a connection to the message bus.
 */
public abstract class ActiveMQConnection {

    private static Logger log = LoggerFactory.getLogger(ActiveMQConnection.class);

    protected ServerLocator locator;
    protected String serverUrl;
    protected ClientSessionFactory factory;


    public ActiveMQConnection(Configuration config) {
        serverUrl = config.getProperty(ConfigProperties.ACTIVEMQ_BROKER_URL);
    }

    /**
     * Creates an instance of a ServerLocator that is configured with the connection
     * details of the target broker. Called on the first invocation of getFactory().
     *
     * @return a fully configured ServerLocator from which a ClientSessionFactory can be created.
     * @throws Exception thrown when an error occurs while creating the locator.
     */
    abstract ServerLocator initLocator() throws Exception;

    /**
     * Creates an instance of a ClientSessionFactory from the provided locator.
     * Called on the first invocation of getFactory().
     *
     * @param locator the ServerLocator to be used to create the session factory.
     * @return the ClientSessionFactory representing the connection to the broker.
     * @throws Exception when an error occurs creating the session factory.
     */
    abstract ClientSessionFactory initClientSessionFactory(ServerLocator locator) throws Exception;

    /**
     * Creates a new instance of a client session.
     *
     * @return
     * @throws ActiveMQException
     */
    abstract ClientSession createClientSession() throws ActiveMQException;

    /**
     * Gets the single ClientSessionFactory instance that represents the connection to the broker.
     * The initial connection to the broker will be made on the first invocation of this method.
     *
     * @return the single connection factory instance.
     */
    synchronized ClientSessionFactory getFactory() {
        try {
            if (this.locator == null) {
                this.locator = initLocator();
            }
            if (this.factory == null || this.factory.isClosed()) {
                this.factory = initClientSessionFactory(this.locator);
            }
        }
        catch (Exception e) {
            log.error("Unable to create connection to message bus.", e);
            throw new RuntimeException(e);
        }
        return this.factory;
    }

    public boolean isClosed() {
        try {
            return this.factory == null || this.factory.isClosed();
        }
        catch (Exception e) {
            return true;
        }
    }

    public void close() {
        if (!isClosed()) {
            this.factory.close();
        }
    }
}
