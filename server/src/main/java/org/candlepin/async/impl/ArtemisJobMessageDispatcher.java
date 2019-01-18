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
package org.candlepin.async.impl;

import org.candlepin.async.JobMessage;
import org.candlepin.async.JobMessageDispatcher;
import org.candlepin.audit.MessageAddress;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;



/**
 * The ArtemisJobMessageDispatcher is a job message dispatcher implementation that uses Artemis
 * to distribute job messages using a single message queue.
 */
public class ArtemisJobMessageDispatcher implements JobMessageDispatcher {
    private static Logger log = LoggerFactory.getLogger(ArtemisJobMessageDispatcher.class);

    public static final String JOB_KEY_MESSAGE_PROPERTY = "job_key";

    private ActiveMQSessionFactory sessionFactory;
    private ClientSession session;
    private ClientProducer producer;

    private ObjectMapper objMapper;


    /**
     * Creates a new message dispatcher
     *
     * @param sessionFactory
     *  The session factory to use for creating sessions
     *
     * @param objMapper
     *  The ObjectMapper to use for serializing and deserializing messages
     */
    @Inject
    public ArtemisJobMessageDispatcher(ActiveMQSessionFactory sessionFactory, ObjectMapper objMapper) {
        this.sessionFactory = sessionFactory;
        this.objMapper = objMapper;
    }

    /**
     * Fetches the current client session, creating and intializing a new instance as necessary.
     *
     * @return
     *  the current ClientSession instance for this job manager
     */
    protected ClientSession getClientSession() throws Exception {
        if (this.session == null || this.session.isClosed()) {
            log.debug("Creating new ActiveMQ session for sending async job messages...");

            this.session = this.sessionFactory.getEgressSession(false);

            log.debug("Created new ActiveMQ session: {}", this.session);
        }

        return this.session;
    }

    /**
     * Fetches the current client producer, creating and initializing a new instance as necessary.
     *
     * @return
     *  the current ClientProducer instance for this job manager
     */
    protected ClientProducer getClientProducer() throws Exception {
        if (this.producer == null || this.producer.isClosed()) {
            log.debug("Creating new ActiveMQ producer for async job messages...");

            ClientSession session = this.getClientSession();
            this.producer = session.createProducer();

            log.debug("Created new ActiveMQ producer: {}", this.producer);
        }

        return this.producer;
    }

    /**
     * {@inheritDoc}
     */
    public void sendJobMessage(JobMessage jobMessage) throws Exception {
        ClientSession session = this.getClientSession();
        ClientMessage message = session.createMessage(true);
        message.putStringProperty(JOB_KEY_MESSAGE_PROPERTY, jobMessage.getJobKey());

        String eventString = this.objMapper.writeValueAsString(jobMessage);
        message.getBodyBuffer().writeString(eventString);

        log.debug("Sending message to {}: {}", MessageAddress.JOB_MESSAGE_ADDRESS, eventString);

        ClientProducer producer = this.getClientProducer();
        producer.send(MessageAddress.JOB_MESSAGE_ADDRESS, message);
    }

}
