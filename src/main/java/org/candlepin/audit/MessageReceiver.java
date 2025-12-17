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

import org.candlepin.messaging.CPMConsumer;
import org.candlepin.messaging.CPMMessageListener;
import org.candlepin.messaging.CPMSessionManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A base implementation of Candlepin's ActiveMQ MessageHandler.
 */
public abstract class MessageReceiver implements CPMMessageListener {

    private static Logger log = LoggerFactory.getLogger(MessageReceiver.class);

    protected CPMSessionManager sessionManager;

    protected CPMConsumer session;
    protected ObjectMapper mapper;

    protected ClientConsumer consumer;
    protected String queueName;

    // FIXME Do we even need this? Looks like it is just for logging.
    protected abstract String getQueueAddress();

    public MessageReceiver(String queueName, CPMSessionManager sessionManager, ObjectMapper mapper) {
        this.sessionManager = sessionManager;
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

}
