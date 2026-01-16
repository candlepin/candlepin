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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;


/**
 * A MessageReceiver implementation that on failure to handle an event, will put the message
 * back in the associated queue and will retry the send on a configured basis. This is the
 * default ActiveMQ message handler implementation.
 */
public class DefaultEventMessageReceiver extends EventMessageReceiver {

    private static Logger log = LoggerFactory.getLogger(DefaultEventMessageReceiver.class);

    public DefaultEventMessageReceiver(EventListener listener, ActiveMQSessionFactory sessionFactory,
        ObjectMapper mapper) {
        super(listener, sessionFactory, mapper);
    }

    @Override
    public void onMessage(ClientMessage msg) {
        String body = "";
        try {
            // Acknowledge the message so that the server knows that it was received.
            // By doing this, the server can update the delivery counts which plays
            // part in calculating redelivery delays.
            msg.acknowledge();
            log.debug("ActiveMQ message {} acknowledged for listener: {}", msg.getMessageID(), listener);

            // Process the message via our EventListener framework.
            if (msg.getType() == ClientMessage.TEXT_TYPE) {
                SimpleString sstr = msg.getBodyBuffer().readNullableSimpleString();
                if (sstr != null) {
                    body = sstr.toString();
                }
            }
            else {
                body = msg.getBodyBuffer().readString();
            }

            log.debug("Got event: {}", body);
            Event event = mapper.readValue(body, Event.class);
            listener.onEvent(event);

            log.debug("Message listener {} processed message: {}: SUCCESS", listener, msg.getMessageID());
            // Finally commit the session so that the message is taken out of the queue.
            session.commit();
        }
        catch (Exception e) {
            // Log a warning instead of a full stack trace to reduce log size.
            String messageId = (msg == null) ? "" : Long.toString(msg.getMessageID());
            String reason = (e.getCause() == null) ? e.getMessage() : e.getCause().getMessage();
            log.error("Unable to process message {}: {}", messageId, reason);

            log.debug("Message listener {} processed message: {}: FAILURE", listener, msg.getMessageID());

            // If debugging is enabled log a more in depth message.
            log.debug("Unable to process message. Rolling back client session.\n{}", body, e);
            try {
                // When any exception occurs while processing the message, we need to roll back
                // the session so that the message remains on the queue.
                session.rollback();
            }
            catch (ActiveMQException amqe) {
                log.error("Unable to roll back client session.", amqe);
            }

            // Session was rolled back, nothing left to do.
        }
    }

    protected String getQueueAddress() {
        return MessageAddress.DEFAULT_EVENT_MESSAGE_ADDRESS;
    }

}
