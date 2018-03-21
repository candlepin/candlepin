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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives messages from an Artemis Queue. Each receiver creates and handles its own session.
 */
public class EventReceiver {
    private static Logger log = LoggerFactory.getLogger(EventReceiver.class);
    private ClientSession session;

    public EventReceiver(EventListener listener, ClientSessionFactory sessionFactory,
        ObjectMapper mapper) throws ActiveMQException {
        String queueName = EventSource.QUEUE_ADDRESS + "." + listener.getClass().getCanonicalName();
        log.debug("registering listener for {}", queueName);

        // The client session is created without auto-acking enabled. This means
        // that the client handlers will have to manage the session themselves.
        // The session management will be done by each individual ListenerWrapper.
        //
        // A message ack batch size of 0 is specified to prevent duplicate messages
        // if the server goes down before the batch ack size is reached.
        session = sessionFactory.createSession(false, false, 0);

        try {
            // Create a durable queue that will be persisted to disk:
            session.createQueue(EventSource.QUEUE_ADDRESS, queueName, true);
            log.debug("created new event queue: {}", queueName);
        }
        catch (ActiveMQException e) {
            // if the queue exists already we already created it in a previous run,
            // so that's fine.
            if (e.getType() != ActiveMQExceptionType.QUEUE_EXISTS) {
                throw e;
            }
        }

        ClientConsumer consumer = session.createConsumer(queueName);
        consumer.setMessageHandler(new ListenerWrapper(listener, mapper, session));
        session.start();
    }

    /**
     * Close the current session.
     */
    public void close() {
        // Use a separate try/catch to ensure that both methods
        // are at least tried.
        try {
            this.session.stop();
        }
        catch (ActiveMQException e) {
            log.warn("Error stopping client session", e);
        }

        try {
            this.session.close();
        }
        catch (ActiveMQException e) {
            log.warn("Error closing client session.", e);
        }
    }
}
