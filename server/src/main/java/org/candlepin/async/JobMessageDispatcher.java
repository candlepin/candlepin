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
package org.candlepin.async;

import org.candlepin.common.config.Configuration;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMProducer;
import org.candlepin.messaging.CPMProducerConfig;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionConfig;
import org.candlepin.messaging.CPMSessionFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;



/**
 * The JobMessageDispatcher is responsible for managing sessions to the backing messaging system,
 * and serializing messages as they're sent.
 */
//@Component
public class JobMessageDispatcher {
    private static Logger log = LoggerFactory.getLogger(JobMessageDispatcher.class);

    private static final String JOB_KEY_MESSAGE_PROPERTY = "job_key";
    private static final String JOB_MESSAGE_ADDRESS = "job";

    private final Configuration config;
    private final CPMSessionFactory cpmSessionFactory;
    private final ObjectMapper objMapper;

    private CPMSession cpmSession;
    private CPMProducer cpmProducer;

    /**
     * Creates a new JobMessageDispatcher instance for sending job messages to the backing
     * message bus.
     *
     * @param cpmSessionFactory
     *  A CPMSessionFactory instance for creating messaging sessions
     *
     * @param objMapper
     *  An ObjectMapper instance for serializing job messages prior to sending them to the
     *  message bus
     */
    @Inject
    //@Autowired
    public JobMessageDispatcher(Configuration config, CPMSessionFactory cpmSessionFactory,
        ObjectMapper objMapper) {

        this.config = Objects.requireNonNull(config);
        this.cpmSessionFactory = Objects.requireNonNull(cpmSessionFactory);
        this.objMapper = Objects.requireNonNull(objMapper);
    }

    /**
     * Shuts down this job message receiver, closing any sessions it may have opened
     */
    public synchronized void shutdown() throws JobException {
        try {
            if (this.cpmSession != null) {
                this.cpmSession.close();
            }
        }
        catch (CPMException e) {
            throw new JobException(e);
        }
    }

    /**
     * Fetches the current CPM session, creating a new one if necessary.
     *
     * @return
     *  a CPMSession instance
     */
    private synchronized CPMSession getSession() throws CPMException {
        if (this.cpmSession == null || this.cpmSession.isClosed()) {
            CPMSessionConfig config = this.cpmSessionFactory.createSessionConfig()
                .setTransactional(true);

            // TODO: Add any other job-system-specific session configuration here

            this.cpmSession = this.cpmSessionFactory.createSession(config);
            this.cpmSession.start();
        }

        return this.cpmSession;
    }

    /**
     * Fetches the current CPM producer, creating a new one if necessary.
     *
     * @return
     *  a CPMProducer instance
     */
    private synchronized CPMProducer getProducer() throws CPMException {
        if (this.cpmProducer == null || this.cpmProducer.isClosed()) {
            log.debug("Creating new CPM producer for job message dispatch...");

            CPMSession session = this.getSession();
            CPMProducerConfig config = session.createProducerConfig();

            // TODO: Add any other job-system-specific producer configuration here

            this.cpmProducer = session.createProducer(config);

            log.debug("Created new CPM producer: {}", this.cpmProducer);
        }

        return this.cpmProducer;
    }

    /**
     * Posts a job message to the backing message bus, which may or may not be sent immediately.
     * If the message cannot be posted, this method should throw an exception.
     *
     * @param jobMessage
     *  The JobMessage to post
     *
     * @throws JobMessageDispatchException
     *  if the message cannot be posted for any reason
     */
    public void postJobMessage(JobMessage jobMessage) throws JobMessageDispatchException {
        try {
            CPMSession session = this.getSession();
            CPMMessage message = session.createMessage()
                .setDurable(true)
                .setProperty(JOB_KEY_MESSAGE_PROPERTY, jobMessage.getJobKey());

            String serializedJobMessage = this.objMapper.writeValueAsString(jobMessage);
            message.setBody(serializedJobMessage);

            log.debug("Sending job message to {}: {}", JOB_MESSAGE_ADDRESS, serializedJobMessage);

            this.getProducer().send(JOB_MESSAGE_ADDRESS, message);
        }
        catch (Exception e) {
            throw new JobMessageDispatchException(e);
        }
    }

    /**
     * Commits any pending messages posted to the backing message bus. If no transaction is
     * active, or no messages have been posted, this method returns silently.
     *
     * @throws JobMessageDispatchException
     *  if the messaging session cannot be committed for any reason
     */
    public void commit() throws JobMessageDispatchException {
        try {
            CPMSession session = this.getSession();
            session.commit();
        }
        catch (Exception e) {
            throw new JobMessageDispatchException(e);
        }
    }

    /**
     * Rolls back any pending messages posted to the backing message bus that have not yet been
     * committed. If no transaction is active or there are no messages to roll back, this method
     * returns silently.
     *
     * @throws JobMessageDispatchException
     *  if the messaging session cannot be rolled back for any reason
     */
    public void rollback() throws JobMessageDispatchException {
        try {
            CPMSession session = this.getSession();
            session.rollback();
        }
        catch (Exception e) {
            throw new JobMessageDispatchException(e);
        }
    }

}
