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

import org.candlepin.messaging.CPMConsumerConfig;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessageConfig;
import org.candlepin.messaging.CPMProducerConfig;
import org.candlepin.messaging.CPMSession;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;

import java.util.Objects;



/**
 * CPMSession implementation backed by Artemis
 */
public class ArtemisSession implements CPMSession {

    private final ClientSession session;

    /**
     * Creates a new Artemis session backed by the given ClientSession instance.
     *
     * @param session
     *  the Artemis ClientSession instance to back this session
     */
    public ArtemisSession(ClientSession session) {
        this.session = Objects.requireNonNull(session);
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
    public synchronized void start() throws CPMException {
        try {
            this.session.start();
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop() throws CPMException {
        try {
            this.session.stop();
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close() throws CPMException {
        try {
            this.session.close();
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
        return this.session.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void commit() throws CPMException {
        try {
            this.session.commit();
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void rollback() throws CPMException {
        try {
            this.session.rollback();
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMProducerConfig createProducerConfig() {
        CPMProducerConfig config = new CPMProducerConfig();

        // TODO: Add any producer config from candlepin.conf

        return config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMConsumerConfig createConsumerConfig() {
        CPMConsumerConfig config = new CPMConsumerConfig();

        // TODO: Add any consumer config from candlepin.conf

        return config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMMessageConfig createMessageConfig() {
        CPMMessageConfig config = new CPMMessageConfig();

        // TODO: Add any necessary config from candlepin.conf

        return config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtemisProducer createProducer() throws CPMException {
        return this.createProducer(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ArtemisProducer createProducer(CPMProducerConfig config) throws CPMException {
        if (config == null) {
            config = this.createProducerConfig();
        }

        try {
            ClientProducer producer = this.session.createProducer();
            return new ArtemisProducer(this, producer);
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtemisConsumer createConsumer() throws CPMException {
        return this.createConsumer(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ArtemisConsumer createConsumer(CPMConsumerConfig config) throws CPMException {
        if (config == null) {
            config = this.createConsumerConfig();
        }

        try {
            String filter = config.getMessageFilter();

            ClientConsumer consumer = (filter != null && !filter.isEmpty()) ?
                this.session.createConsumer(config.getQueue(), filter) :
                this.session.createConsumer(config.getQueue());

            return new ArtemisConsumer(this, consumer);
        }
        catch (ActiveMQException e) {
            throw new CPMException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtemisMessage createMessage() throws CPMException {
        return this.createMessage(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized ArtemisMessage createMessage(CPMMessageConfig config) throws CPMException {
        if (config == null) {
            config = this.createMessageConfig();
        }

        ClientMessage message = this.session.createMessage(ClientMessage.TEXT_TYPE, config.isDurable());
        return new ArtemisMessage(this, message);
    }

}
