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

package org.candlepin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A base implementation of Candlepin's ActiveMQ MessageHandler.
 */
public abstract class MessageReceiver implements MessageHandler {

    private static Logger log = LoggerFactory.getLogger(MessageReceiver.class);

    protected ClientSession session;
    protected ObjectMapper mapper;
    protected EventListener listener;
    protected ClientConsumer consumer;
    protected String queueName;

    protected abstract String getQueueAddress();

    public MessageReceiver(EventListener listener, ClientSessionFactory sessionFactory,
        ObjectMapper mapper) throws ActiveMQException {
        this.mapper = mapper;
        this.listener = listener;

        queueName = EventSource.getQueueName(listener);
        log.debug("registering listener for {}", queueName);

        // The client session is created without auto-acking enabled. This means
        // that the client handlers will have to manage the session themselves.
        // The session management will be done by each individual ListenerWrapper.
        //
        // A message ack batch size of 0 is specified to prevent duplicate messages
        // if the server goes down before the batch ack size is reached.
        session = sessionFactory.createSession(false, false, 0);

        consumer = session.createConsumer(queueName);
        consumer.setMessageHandler(this);
        session.start();
    }

    public boolean requiresQpid() {
        return this.listener.requiresQpid();
    }

    public void stopSession() {
        try {
            this.consumer.close();
        }
        catch (ActiveMQException e) {
            log.warn("QpidEventMessageReceiver could not stop client consumer.", e);
        }
    }

    public void startSession() {
        try {
            if (this.consumer.isClosed()) {
                log.debug("### Recreating the ActiveMQ client for {}.", listener);
                this.consumer = session.createConsumer(queueName);
                this.consumer.setMessageHandler(this);
            }
        }
        catch (ActiveMQException e) {
            log.warn("QpidEventMessageReceiver could not start client session.", e);
        }
    }

    /**
     * Close the current session.
     */
    public void close() {
        log.debug("Shutting down message receiver for {}.", listener);
        try {
            this.session.stop();
        }
        catch (ActiveMQException e) {
            log.warn("QpidEventMessageReceiver could not stop client session.", e);
        }

        try {
            this.session.close();
        }
        catch (ActiveMQException e) {
            log.warn("Error closing client session.", e);
        }
    }
}
