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
package org.candlepin.messaging.impl.artemis;

import org.candlepin.messaging.CPMConsumer;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMMessageListener;
import org.candlepin.messaging.CPMSession;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;

import java.util.Objects;



/**
 * CPMConsumer implementation backed by Artemis
 */
public class ArtemisConsumer implements CPMConsumer {

    /**
     * Internal message handler that allows for juggling types and changing the interface.
     */
    private static class ArtemisMessageForwarder implements MessageHandler {
        private final ArtemisSession session;
        private final ArtemisConsumer consumer;
        private final CPMMessageListener listener;

        /**
         * Creates a new message forwarder that will report the specified session and consumer
         * when forwarding received messages to the given listener.
         */
        public ArtemisMessageForwarder(ArtemisSession session, ArtemisConsumer consumer,
            CPMMessageListener listener) {

            this.session = Objects.requireNonNull(session);
            this.consumer = Objects.requireNonNull(consumer);
            this.listener = Objects.requireNonNull(listener);
        }

        @Override
        public void onMessage(ClientMessage message) {
            this.listener.handleMessage(this.session, this.consumer, translateMessage(this.session, message));
        }
    }


    private final ArtemisSession session;
    private final ClientConsumer consumer;

    private CPMMessageListener listener;


    /**
     * Creates a new Artemis consumer from the given session, backed by the provided consumer and
     * configuration.
     *
     * @param session
     *  the ArtemisSession instance that spawned this consumer
     *
     * @param consumer
     *  the Artemis ClientConsumer backing this consumer
     */
    public ArtemisConsumer(ArtemisSession session, ClientConsumer consumer) {
        this.session = Objects.requireNonNull(session);
        this.consumer = Objects.requireNonNull(consumer);
    }

    private static ArtemisMessage translateMessage(ArtemisSession session, ClientMessage message) {
        return new ArtemisMessage(session, message);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getProvider() {
        return ArtemisUtil.PROVIDER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMSession getSession() {
        return this.session;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws CPMException {
        try {
            this.consumer.close();
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return this.consumer.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessage fetchMessage() throws CPMException {
        try {
            ClientMessage message = this.consumer.receiveImmediate();
            return translateMessage(this.session, message);
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessage waitForMessage() throws CPMException {
        try {
            ClientMessage message = this.consumer.receive();
            return translateMessage(this.session, message);
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessage waitForMessage(long timeout) throws CPMException {
        try {
            ClientMessage message = this.consumer.receive(timeout);
            return translateMessage(this.session, message);
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtemisConsumer setMessageListener(CPMMessageListener listener) throws CPMException {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }

        try {
            ArtemisMessageForwarder forwarder = new ArtemisMessageForwarder(this.session, this, listener);
            this.consumer.setMessageHandler(forwarder);
            this.listener = listener;

            return this;
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessageListener getMessageListener() {
        return this.listener;
    }

}
