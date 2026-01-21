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
package org.candlepin.async;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMProducer;
import org.candlepin.messaging.CPMProducerConfig;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionConfig;
import org.candlepin.messaging.CPMSessionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;


/**
 * The JobMessageDispatcher is responsible for managing sessions to the backing messaging system,
 * and serializing messages as they're sent.
 */
public class JobMessageDispatcher {
    private static Logger log = LoggerFactory.getLogger(JobMessageDispatcher.class);

    private static final String JOB_KEY_MESSAGE_PROPERTY = "job_key";

    /**
     * The ThreadSessionStore is used to store session information per thread.
     */
    private static final class ThreadSessionStore {
        private final CPMSessionFactory sessionFactory;

        private CPMSession session;
        private CPMProducer producer;

        /**
         * Creates a new ThreadSessionStore which will use the given session factory to create new
         * sessions as necessary.
         *
         * @param sessionFactory
         *  the CPMSessionFactory instance to use to create new sessions
         *
         * @throws IllegalArgumentException
         *  if sessionFactory is null
         */
        public ThreadSessionStore(CPMSessionFactory sessionFactory) {
            if (sessionFactory == null) {
                throw new IllegalArgumentException("sessionFactory is null");
            }

            this.sessionFactory = sessionFactory;
        }

        /**
         * Fetches the current CPM session, creating a new one if necessary.
         *
         * @return
         *  a CPMSession instance
         */
        public CPMSession getSession() throws CPMException {
            if (this.session == null || this.session.isClosed()) {
                log.debug("Creating new CPM session for job message dispatch for thread {}",
                    Thread.currentThread());

                CPMSessionConfig config = this.sessionFactory.createSessionConfig()
                    .setTransactional(true);

                // Add any other job-system-specific session configuration here

                this.session = this.sessionFactory.createSession(config);
                this.session.start();

                log.debug("Created new CPM session: {}", this.session);
            }

            return this.session;
        }

        /**
         * Fetches the current CPM producer, creating a new one if necessary.
         *
         * @return
         *  a CPMProducer instance
         */
        public CPMProducer getProducer() throws CPMException {
            if (this.producer == null || this.producer.isClosed()) {
                log.debug("Creating new CPM producer for job message dispatch for thread {}",
                    Thread.currentThread());

                CPMSession session = this.getSession();
                CPMProducerConfig config = session.createProducerConfig();

                // Add any other job-system-specific producer configuration here

                this.producer = session.createProducer(config);

                log.debug("Created new CPM producer: {}", this.producer);
            }

            return this.producer;
        }

        /**
         * Closes any session resources this session store may have open
         */
        private void close() throws CPMException {
            if (this.producer != null) {
                this.producer.close();
                this.producer = null;
            }

            if (this.session != null) {
                this.session.close();
                this.session = null;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return String.format("ThreadSessionStore [session: %s, producer: %s]",
                this.session, this.producer);
        }
    }

    /**
     * The ThreadReference class is a WeakReference implementation with stable equals and hashCode
     * methods, allowing it to be used as a key in maps and in sets.
     */
    private class ThreadReference extends WeakReference<Thread> {
        private final int hashCode;

        public ThreadReference(Thread thread, ReferenceQueue<? super Thread> queue) {
            super(thread, queue);

            this.hashCode = thread != null ? thread.hashCode() : 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object cmp) {
            if (cmp == this) {
                return true;
            }

            if (cmp instanceof ThreadReference) {
                Thread lhs = this.get();
                Thread rhs = ((ThreadReference) cmp).get();

                return lhs != null ? lhs.equals(rhs) : rhs == null && this.hashCode() == cmp.hashCode();
            }

            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }


    private final Configuration config;
    private final CPMSessionFactory cpmSessionFactory;
    private final ObjectMapper objMapper;

    private final ReferenceQueue<Thread> referenceQueue;
    private final Map<ThreadReference, ThreadSessionStore> sessions;

    private String dispatchAddress;

    /**
     * Creates a new JobMessageDispatcher instance for sending job messages to the backing
     * message bus.
     *
     * @param config
     *  the system configuration to use
     *
     * @param cpmSessionFactory
     *  A CPMSessionFactory instance for creating messaging sessions
     *
     * @param objMapper
     *  An ObjectMapper instance for serializing job messages prior to sending them to the
     *  message bus
     *
     * @throws ConfigurationException
     *  if the necessary configuration cannot be read or is invalid
     */
    @Inject
    public JobMessageDispatcher(Configuration config, CPMSessionFactory cpmSessionFactory,
        ObjectMapper objMapper) throws ConfigurationException {

        this.config = Objects.requireNonNull(config);
        this.cpmSessionFactory = Objects.requireNonNull(cpmSessionFactory);
        this.objMapper = Objects.requireNonNull(objMapper);

        this.referenceQueue = new ReferenceQueue<>();
        this.sessions = new HashMap<>();

        this.configure(this.config);
    }

    /**
     * Configures this object using the configuration provided.
     *
     * @param config
     *  the configuration to use to configure this message dispatcher
     *
     * @throws ConfigurationException
     *  if the necessary configuration cannot be read or is invalid
     */
    private void configure(Configuration config) throws ConfigurationException {
        this.dispatchAddress = config.getString(ConfigProperties.ASYNC_JOBS_DISPATCH_ADDRESS);
        if (this.dispatchAddress == null || this.dispatchAddress.isEmpty()) {
            throw new ConfigurationException("Invalid job dispatch address: address cannot be null or empty");
        }
    }

    /**
     * Shuts down this job message dispatcher, closing any sessions it may have opened
     */
    public synchronized void shutdown() throws JobException {
        try {
            for (ThreadSessionStore store : this.sessions.values()) {
                store.close();
            }

            this.sessions.clear();
        }
        catch (CPMException e) {
            throw new JobException(e);
        }
    }

    /**
     * Closes and removes any sessions which are associated with a thread that has been destroyed.
     */
    private void expungeAbandonedSessions() throws CPMException {
        while (true) {
            Reference<? extends Thread> ref = this.referenceQueue.poll();
            if (ref == null) {
                break;
            }

            ThreadSessionStore store = this.sessions.remove(ref);
            if (store != null) {
                log.warn("Closing abandoned messaging session: {}", store);
                store.close();
            }
        }
    }

    /**
     * Fetches the session store for the current thread, creating one if necessary.
     *
     * @return
     *  the ThreadSessionStore for the current thread
     */
    private synchronized ThreadSessionStore getSessionStore() throws CPMException {
        this.expungeAbandonedSessions();

        ThreadReference ref = new ThreadReference(Thread.currentThread(), this.referenceQueue);
        ThreadSessionStore store = this.sessions.get(ref);
        if (store == null) {
            store = new ThreadSessionStore(this.cpmSessionFactory);
            this.sessions.put(ref, store);
        }

        return store;
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
            ThreadSessionStore store = this.getSessionStore();

            CPMSession cpmSession = store.getSession();
            CPMMessage message = cpmSession.createMessage()
                .setDurable(true)
                .setProperty(JOB_KEY_MESSAGE_PROPERTY, jobMessage.getJobKey());

            String serializedJobMessage = this.objMapper.writeValueAsString(jobMessage);
            message.setBody(serializedJobMessage);

            log.debug("Sending job message to queue \"{}\": {}", this.dispatchAddress, serializedJobMessage);
            store.getProducer().send(this.dispatchAddress, message);
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
            ThreadSessionStore store = this.getSessionStore();

            CPMSession session = store.getSession();
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
            ThreadSessionStore store = this.getSessionStore();

            CPMSession session = store.getSession();
            session.rollback();
        }
        catch (Exception e) {
            throw new JobMessageDispatchException(e);
        }
    }

}
