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

import org.candlepin.async.impl.ActiveMQSessionFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives messages from an Artemis Queue. Each receiver creates and handles its own session.
 */
public class QpidEventMessageReceiver extends EventMessageReceiver {
    private static Logger log = LoggerFactory.getLogger(QpidEventMessageReceiver.class);

    private static final String AMQ_ORIG_ADDRESS = "_AMQ_ORIG_ADDRESS";
    private static final String AMQ_ORIG_MSG_ID = "_AMQ_ORIG_MESSAGE_ID";

    public QpidEventMessageReceiver(EventListener listener, ActiveMQSessionFactory sessionFactory,
        ObjectMapper mapper) {
        super(listener, sessionFactory, mapper);
    }

    /**
     * Called when a message is received from the Artemis server.
     *
     * @param msg the message that was received.
     */
    @Override
    public void onMessage(ClientMessage msg) {
        boolean messageWasDiverted = msg.containsProperty(AMQ_ORIG_MSG_ID);
        String origMsgId = messageWasDiverted ?
            String.valueOf(msg.getLongProperty(AMQ_ORIG_MSG_ID)) : String.valueOf(msg.getMessageID());
        String msgId = String.valueOf(msg.getMessageID());

        String body = "";
        try {
            // Messages received on this address should have been diverted after it was sent to
            // the 'event.default' address.
            if (messageWasDiverted) {
                log.debug("Message was diverted from: {}:{} -> {}:{}",
                    msg.getStringProperty(AMQ_ORIG_ADDRESS), origMsgId, msg.getAddress(), msg.getMessageID());
            }

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
            log.debug("Message listener {} processed message: {} [{}]: SUCCESS", listener, msgId, origMsgId);

            // Acknowledge the message so that the server knows that it was received.
            msg.acknowledge();

            // Finally commit the session so that the message is taken out of the queue.
            session.commit();
        }
        catch (Exception e) {
            log.debug("Message listener {} processed message: {} [{}]: FAILED", listener, msgId, origMsgId);
            log.error("Unable to process message {}[{}]: {}", msgId, origMsgId, e);

            // If debugging is enabled log a more in depth message.
            log.debug("Unable to process message. Rolling back client session.\n{}", body);

            // Since we are closing the Consumer whenever Qpid is in trouble (when notified)
            // we do not want to roll back as the message will get resent when the Client
            // is recreated. However, when an exception is thrown that is not a QpidConnectionException
            // then we want to make sure that we ack the message so that the delivery counts are
            // updated, and rollback the message so that it is retried until it is discarded.
            if (!(e instanceof QpidConnectionException)) {
                log.warn("Unexpected exception thrown by listener. This message will be discarded: {} [{}]",
                    msgId, origMsgId, e);
                try {
                    msg.acknowledge();
                }
                catch (ActiveMQException amqe) {
                    log.error("Unable to acknowledge message.", amqe);
                }

                try {
                    session.rollback();
                }
                catch (ActiveMQException amqe) {
                    log.warn("Unable to roll back session.", amqe);
                }
            }
        }
    }

    protected String getQueueAddress() {
        return MessageAddress.QPID_EVENT_MESSAGE_ADDRESS;
    }

}
