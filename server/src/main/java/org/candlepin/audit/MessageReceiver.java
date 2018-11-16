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
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A base implementation of Candlepin's ActiveMQ MessageHandler.
 */
public abstract class MessageReceiver implements MessageHandler {

    private static Logger log = LoggerFactory.getLogger(MessageReceiver.class);

    private ActiveMQConnection connection;

    protected ClientSession session;
    protected ObjectMapper mapper;
    protected EventListener listener;
    protected ClientConsumer consumer;
    protected String queueName;

    protected abstract String getQueueAddress();

    public MessageReceiver(EventListener listener, ActiveMQConnection connection,
        ObjectMapper mapper) throws ActiveMQException {
        this.connection = connection;
        this.mapper = mapper;
        this.listener = listener;
        this.queueName = EventSource.getQueueName(listener);
    }

    public boolean requiresQpid() {
        return this.listener.requiresQpid();
    }

    /**
     * Pause message consumption for this receiver.
     */
    public void pause() {
        try {
            log.debug("Pausing message consumption for: {}.", listener);
            this.consumer.close();
        }
        catch (ActiveMQException e) {
            log.warn("Message receiver could not stop client consumer.", e);
        }
    }

    /**
     * Resume message consumption for this receiver.
     */
    public void resume() {
        if (session.isClosed()) {
            log.warn("MessageReceiver was unable to resume message consumption. Artemis DOWN!");
            return;
        }
        try {
            if (this.consumer.isClosed()) {
                log.debug("Resuming message consumption for {}.", listener);
                this.consumer = session.createConsumer(queueName);
                this.consumer.setMessageHandler(this);
            }
        }
        catch (ActiveMQException e) {
            log.warn("MessageReceiver could not start client session.", e);
        }
    }

    /**
     * Close the current session.
     */
    public void close() {
        log.debug("Shutting down message receiver for {}.", listener);
        if (session != null && !session.isClosed()) {

            try {
                this.session.stop();
            }
            catch (ActiveMQException e) {
                log.warn("MessageReceiver could not stop client session.", e);
            }

            try {
                this.session.close();
            }
            catch (ActiveMQException e) {
                log.warn("Error closing client session.", e);
            }
        }

    }

    public void connect() throws ActiveMQException {
        if (session == null || session.isClosed()) {
            session = this.connection.createClientSession();

            consumer = session.createConsumer(queueName);
            consumer.setMessageHandler(this);
            session.start();
        }
    }
}
