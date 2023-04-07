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
package org.candlepin.messaging.impl.artemis;

import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMProducer;
import org.candlepin.messaging.CPMSession;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;

import java.util.Objects;



/**
 * CPMProducer implementation backed by Artemis
 */
public class ArtemisProducer implements CPMProducer {

    private final ArtemisSession session;
    private final ClientProducer producer;

    /**
     * Creates a new Artemis producer from the given session, backed by the provided producer and
     * configuration.
     *
     * @param session
     *  the ArtemisSession instance that spawned this producer
     *
     * @param producer
     *  the Artemis ClientProducer backing this producer
     */
    public ArtemisProducer(ArtemisSession session, ClientProducer producer) {
        this.session = Objects.requireNonNull(session);
        this.producer = Objects.requireNonNull(producer);
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
            this.producer.close();
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
        return this.producer.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void send(String address, CPMMessage message) throws CPMException {
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("address is null or empty");
        }

        if (message == null) {
            throw new IllegalArgumentException("message is null");
        }

        if (this.isClosed()) {
            throw new IllegalStateException("messages cannot be sent by a producer once it has been closed");
        }

        try {
            ClientMessage cmsg = convertMessage(message);
            this.producer.send(address, cmsg);
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * Converts a CPMMessage into a ClientMessage
     *
     * @param message
     *  the message to convert
     *
     * @return
     *  the converted message
     */
    private static ClientMessage convertMessage(CPMMessage message) {
        if (!(message instanceof ArtemisMessage)) {
            throw new IllegalArgumentException("Cannot send messages created by another messaging provider");
        }

        return ((ArtemisMessage) message).getClientMessage();
    }
}
