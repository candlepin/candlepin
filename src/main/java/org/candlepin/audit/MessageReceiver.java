/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.async.impl.ActiveMQSessionFactory;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;


/**
 * A base implementation of Candlepin's ActiveMQ MessageHandler.
 */
public abstract class MessageReceiver implements MessageHandler {

    private static Logger log = LoggerFactory.getLogger(MessageReceiver.class);

    protected ActiveMQSessionFactory sessionFactory;

    protected ClientSession session;
    protected ObjectMapper mapper;

    protected ClientConsumer consumer;
    protected String queueName;

    // FIXME Do we even need this? Looks like it is just for logging.
    protected abstract String getQueueAddress();

    public MessageReceiver(String queueName, ActiveMQSessionFactory sessionFactory, ObjectMapper mapper) {
        this.sessionFactory = sessionFactory;
        this.mapper = mapper;
        this.queueName = queueName;
    }

    /**
     * Pause message consumption for this receiver.
     */
    public void pause() {
        try {
            log.debug("Pausing message consumption for: {}.", queueName);
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
                log.debug("Resuming message consumption for {}.", queueName);
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
        log.debug("Shutting down message receiver for {}.", queueName);
        if (session != null && !session.isClosed()) {
            try {
                this.session.close();
            }
            catch (ActiveMQException e) {
                log.warn("Error closing client session.", e);
            }
        }

    }

    protected abstract void initialize() throws Exception;

    public void connect() throws Exception {
        if (session == null || session.isClosed()) {
            initialize();
        }
    }
}
