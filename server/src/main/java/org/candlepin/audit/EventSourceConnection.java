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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.candlepin.common.config.Configuration;
import org.candlepin.controller.ActiveMQStatusMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An ActiveMQ connection specifically configured for candlepin's EventSource. This is a basic
 * connection that will be notified as soon as the broker goes down and will be monitored by
 * the ActiveMQStatusMonitor.
 */
@Singleton
public class EventSourceConnection extends ActiveMQConnection {

    private static Logger log = LoggerFactory.getLogger(EventSourceConnection.class);
    private ActiveMQStatusMonitor monitor;

    @Inject
    public EventSourceConnection(ActiveMQStatusMonitor monitor, Configuration config) {
        super(config);
        this.monitor = monitor;
    }

    @Override
    ServerLocator initLocator() throws Exception {
        return ActiveMQClient.createServerLocator(serverUrl);
    }

    @Override
    ClientSessionFactory initClientSessionFactory(ServerLocator locator) throws Exception {
        ClientSessionFactory factory = locator.createSessionFactory();
        factory.getConnection().addCloseListener(this.monitor);
        return factory;
    }

    @Override
    ClientSession createClientSession() throws ActiveMQException {
        // The client session is created without auto-acking enabled. This means
        // that the client handlers will have to manage the session themselves.
        // The session management will be done by each individual ListenerWrapper.
        //
        // A message ack batch size of 0 is specified to prevent duplicate messages
        // if the server goes down before the batch ack size is reached.
        return getFactory().createSession(false, false, 0);
    }

}
