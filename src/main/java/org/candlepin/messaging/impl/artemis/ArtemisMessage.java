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

import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessage;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;

import java.util.Objects;



/**
 * CPMMessage implementation backed by Artemis
 */
public class ArtemisMessage implements CPMMessage {

    private final ArtemisSession session;
    private final ClientMessage message;


    /**
     * Creates a new Artemis message for the given session, backed by the specified message.
     *
     * @param session
     *  the Artemis session that spawned this message
     *
     * @param message
     *  the ClientMessage instance backing this message
     */
    public ArtemisMessage(ArtemisSession session, ClientMessage message) {
        this.session = Objects.requireNonNull(session);
        this.message = Objects.requireNonNull(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProvider() {
        return ArtemisUtil.PROVIDER;
    }

    /**
     * Fetches the ClientMessage instance backing this Artemis message.
     * <p></p>
     * This method should only be used internally, as directly interacting with the backing objects
     * couples code to a specific implementation and defeats the purpose of using an abstraction.
     *
     * @return
     *  the ClientMessage instance backing this Artemis message
     */
    public ClientMessage getClientMessage() {
        return this.message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessageId() {
        return String.valueOf(this.message.getMessageID());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acknowledge() throws CPMException {
        try {
            this.message.acknowledge();
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessage setDurable(boolean durable) {
        this.message.setDurable(durable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDurable() {
        return this.message.isDurable();
    }

    // CPMMessage setExpiration(long duration);

    // boolean hasExpired();


    // Message details
    /**
     * {@inheritDoc}
     */
    @Override
    public String getAddress() {
        return this.message.getAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessage setBody(String body) {
        ActiveMQBuffer buffer = this.message.getBodyBuffer();

        buffer.clear();
        buffer.writeNullableSimpleString(SimpleString.toSimpleString(body));

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBody() {
        ActiveMQBuffer buffer = this.message.getBodyBuffer();
        String output = null;

        if (buffer.readableBytes() > 0) {
            int idx = buffer.readerIndex();

            switch (this.message.getType()) {
                case ClientMessage.TEXT_TYPE:
                    SimpleString sstr = buffer.readNullableSimpleString();
                    if (sstr != null) {
                        output = sstr.toString();
                    }
                    break;

                default:
                    // This should probably try to read the whole body as a byte array and convert
                    // that to a string. Change as necessary.
                    output = buffer.readString();
            }

            // Reset the reader index so we can repeat the read
            buffer.readerIndex(idx);
        }

        return output;
    }

    // Message properties
    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessage setProperty(String key, String value) {
        this.message.putStringProperty(key, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProperty(String key) {
        return this.message.getStringProperty(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("ArtemisMessage [id: %s, address: %s, body: %s]",
            this.getMessageId(), this.getAddress(), this.getBody());
    }

}
