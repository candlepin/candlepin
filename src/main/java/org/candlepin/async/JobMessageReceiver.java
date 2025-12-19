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
import org.candlepin.messaging.CPMConsumer;
import org.candlepin.messaging.CPMConsumerConfig;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMMessageListener;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionConfig;
import org.candlepin.messaging.CPMSessionFactory;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;

import com.google.inject.persist.UnitOfWork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;


/**
 * The JobMessageReceiver class manages the various receivers for handling messages received from
 * the backing message queues, and passes the messages back to the job manager
 */
public class JobMessageReceiver {
    private static Logger log = LoggerFactory.getLogger(JobMessageReceiver.class);

    private static final String JOB_KEY_MESSAGE_PROPERTY = "job_key";

    private final Configuration config;
    private final CPMSessionFactory cpmSessionFactory;
    private final ObjectMapper mapper;

    private boolean initialized;
    private boolean suspended;

    private MessageListener listener;
    private String receiveAddress;
    private String receiveFilter;
    private Set<CPMSession> sessions;
    private UnitOfWork unitOfWork;


    /**
     * Creates a new job message receiver instance
     *
     * @param config
     *  the system configuration to use
     *
     * @param cpmSessionFactory
     *  the session factory to create messaging sessions
     *
     * @param mapper
     *  the object mapper to use to deserialize job messages
     */
    @Inject
    public JobMessageReceiver(Configuration config, CPMSessionFactory cpmSessionFactory,
        ObjectMapper mapper, UnitOfWork unitOfWork) throws ConfigurationException {

        this.config = Objects.requireNonNull(config);
        this.cpmSessionFactory = Objects.requireNonNull(cpmSessionFactory);
        this.mapper = Objects.requireNonNull(mapper);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);

        this.initialized = false;
        this.suspended = false;
        this.sessions = new HashSet<>();

        this.configure(this.config);
    }

    /**
     * Configures this object using the configuration provided.
     *
     * @param config
     *  the configuration to use to configure this message receiver
     *
     * @throws ConfigurationException
     *  if the necessary configuration cannot be read or is invalid
     */
    private void configure(Configuration config) throws ConfigurationException {
        this.receiveAddress = config.getString(ConfigProperties.ASYNC_JOBS_RECEIVE_ADDRESS);
        if (this.receiveAddress == null || this.receiveAddress.isEmpty()) {
            throw new ConfigurationException("Invalid job receive address: address cannot be null or empty");
        }

        this.receiveFilter = config.getString(ConfigProperties.ASYNC_JOBS_RECEIVE_FILTER);
    }

    /**
     * Creates and configures a new session and consumer
     *
     * @return
     *  The newly created CPM session
     */
    private CPMSession createSession() throws CPMException {
        CPMSessionConfig sconfig = this.cpmSessionFactory.createSessionConfig()
            .setTransactional(true);

        // TODO: Add any other job-system-specific session configuration here

        CPMSession session = this.cpmSessionFactory.createSession(sconfig);

        CPMConsumerConfig cconfig = session.createConsumerConfig()
            .setQueue(this.receiveAddress)
            .setMessageFilter(this.receiveFilter);

        session.createConsumer(cconfig)
            .setMessageListener(this.listener);

        // Once the consumer is configured, we no longer need to propagate it, as it'll be managed
        // indirectly through the session, and passed into the message listener as needed

        return session;
    }

    /**
     * Starts all of the managed sessions. If a given session has died or been closed, this method
     * will recreate it.
     */
    private void startSessions() throws CPMException {
        Set<CPMSession> created = null;

        Iterator<CPMSession> iterator = this.sessions.iterator();
        while (iterator.hasNext()) {
            CPMSession session = iterator.next();

            if (session == null || session.isClosed()) {
                if (created == null) {
                    created = new HashSet<>();
                }

                iterator.remove();
                session = this.createSession();

                created.add(session);
            }

            session.start();
        }

        if (created != null) {
            this.sessions.addAll(created);
        }
    }

    /**
     * Close all known sessions.
     */
    private void closeSessions() throws CPMException {
        for (CPMSession session : this.sessions) {
            session.close();
        }
    }

    /**
     * Initializes this message receiver, specfiying the job manager to use to process job
     * messages.
     *
     * @param manager
     *  the JobManager to use to process received job messages
     *
     * @throws IllegalStateException
     *  if this message receiver has already been initialized
     *
     * @throws IllegalArgumentException
     *  if the provided job manager is null
     */
    public synchronized void initialize(JobManager manager) throws JobException {
        if (this.initialized) {
            throw new IllegalStateException("Message receiver already initialized");
        }

        if (manager == null) {
            throw new IllegalArgumentException("manager is null");
        }

        try {
            this.listener = new MessageListener(manager, this.mapper, this.unitOfWork);
            int listenerThreads = this.config.getInt(ConfigProperties.ASYNC_JOBS_THREADS);

            log.info("Creating {} threads receiving job messages from address: \"{}\", with filter: \"{}\"",
                listenerThreads, this.receiveAddress, this.receiveFilter);

            for (int i = 0; i < listenerThreads; ++i) {
                // Each session+consumer gives us an implicit thread for async job processing, so
                // we don't need to do any additional thread creation/management ourselves.
                CPMSession session = this.createSession();
                this.sessions.add(session);
            }

            this.initialized = true;

            // We're not technically suspended, but we're not started, either. This avoids
            // needing an additional state
            this.suspended = true;
        }
        catch (CPMException e) {
            throw new JobException(e);
        }
    }

    /**
     * Shuts down this job message receiver, closing any sessions it may have opened
     */
    public synchronized void shutdown() throws JobException {
        try {
            for (CPMSession session : this.sessions) {
                session.close();
            }
        }
        catch (CPMException e) {
            throw new JobException(e);
        }
    }

    /**
     * Checks if this message receiver has been initialized.
     *
     * @return
     *  true if the message receiver has been initialized; false otherwise.
     */
    public synchronized boolean isInitialized() {
        return this.initialized;
    }

    /**
     * Starts the operations of this message receiver. If this receiver has already been started,
     * this method returns silently.
     */
    public synchronized void start() throws JobException {
        try {
            if (this.suspended) {
                this.startSessions();

                log.debug("Job message processing started");
                this.suspended = false;
            }
        }
        catch (CPMException e) {
            throw new JobException(e);
        }
    }

    /**
     * Suspends the operations of this message receiver. If this receiver is already suspended, this
     * method returns silently.
     */
    public synchronized void suspend() throws JobException {
        try {
            if (!this.suspended) {
                this.closeSessions();

                log.debug("Job message processing suspended");
                this.suspended = true;
            }
        }
        catch (CPMException e) {
            throw new JobException(e);
        }
    }

    /**
     * Resume the operations of this message receiver. If this receiver is not suspended, this
     * method returns silently.
     */
    public void resume() throws JobException {
        this.start();
    }

    /**
     * Checks if this message receiver is currently suspended.
     *
     * @return
     *  true if this message receiver is currently suspended; false otherwise
     */
    public synchronized boolean isSuspended() {
        return this.suspended;
    }

    /**
     * Internal message listener implementation to handle CPM messages
     */
    private static class MessageListener implements CPMMessageListener {

        private final JobManager manager;
        private final ObjectMapper mapper;
        private final UnitOfWork unitOfWork;

        /**
         * Initializes a new message listener using the specified job manager to process
         * received job messages.
         *
         * @param manager
         *  The JobManager instance to process received job messages; cannot be null
         */
        public MessageListener(JobManager manager, ObjectMapper mapper, UnitOfWork unitOfWork) {
            this.manager = Objects.requireNonNull(manager);
            this.mapper = Objects.requireNonNull(mapper);
            this.unitOfWork = Objects.requireNonNull(unitOfWork);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(CPMSession session, CPMConsumer consumer, CPMMessage message) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Received message: {}", this.serializeMessage(message));
                }

                // Acknowledge the message so that the server knows that it was received.
                // By doing this, the server can update the delivery counts which plays
                // part in calculating redelivery delays.
                message.acknowledge();

                // Read the message and deserialize the data.
                JobMessage jobMessage = this.mapper.readValue(message.getBody(), JobMessage.class);
                log.debug("Deserialized job message: {}", jobMessage);

                this.unitOfWork.begin();

                // Execute the job
                AsyncJobStatus jobStatus = this.manager.executeJob(jobMessage);

                // We didn't fail! Commit the message
                this.commit(session);
            }
            catch (JobExecutionException e) {
                // The job failed during execution; retry logic within JobManager will handle this
                // case for us. We need not handle it any further.
                this.commit(session);
            }
            catch (JobStateManagementException e) {
                // The JobManager failed to update the job's state. Depending on the intended state,
                // we may or may not want to commit the message.

                JobState intendedState = e.getIntendedState();

                if (intendedState != null && !intendedState.isTerminal()) {
                    log.error("Job processing failed; rolling back job message to retry later: {}",
                        this.serializeMessage(message), e);

                    // The intended state is non-terminal (likely fail-with-retry), so we'll rollback
                    // to allow the message to be redelivered so the job can be re-attempted.
                    this.rollback(session);
                }
                else {
                    log.error("Job processing failed terminally; committing job message as acknowledged: {}",
                        message, e);

                    // The state is unknown or terminal. We don't want to redeliver the message as
                    // the job was intended to be put in a "completed" state.

                    // TODO: We need to move the message to a dead-letter queue or some such so we can
                    // clean up the old job once the DB is playing nice again. While the job will be
                    // processed correctly, we're going to have a desync'd status in the database.
                    this.commit(session);
                }
            }
            catch (JobMessageDispatchException e) {
                // The JobManager failed to send a message to Artemis. This is pretty bad since there's
                // a very high possibility we're going to fail to either commit or rollback here. The
                // good news, is that since this is happening during execution, we know the state is
                // FAIL-WITH-RETRY, as that's the only reason a message would be sent (and fail).
                // We'll attempt to rollback to let the message be redelivered, since that's what we
                // want anyway.

                log.error("Failed to dispatch job message during job execution; rolling back job message " +
                    "to retry later: {}", this.serializeMessage(message), e);

                this.rollback(session);
            }
            catch (JobException e) {
                // The job failed in some other, unexpected way. This is generally very bad, but we can
                // recover somewhat gracefully here. If the failure is terminal, we can commit the
                // message and not bother retry. Otherwise, we need to retry the message and let it
                // resync itself.

                // Terminal errors here will likely result in some state desync that will need to be
                // sorted out by some kind of cleanup/reaper job. To facilitate that, we need to move
                // the message to a dead-letter queue for semi-manual cleanup once things are back in
                // working order.

                if (e.isTerminal()) {
                    log.error("Job processing failed terminally; committing job message as acknowledged: {}",
                        this.serializeMessage(message), e);

                    // TODO: Move the message to another queue for cleanup/resync later
                    this.commit(session);
                }
                else {
                    log.error("Job processing failed; rolling back job message to retry later: {}",
                        this.serializeMessage(message));

                    this.rollback(session);
                }
            }
            catch (Exception e) {
                // Once a job actually executes, the failure should be recorded in the job's status
                // and should never reach this code. This catch block will trap the exception, log
                // the error and put the message back on the queue.
                // The only time we should hit this branch is if something blows up while attempting
                // to process the job message.
                String messageId = (message != null ? message.getMessageId() : "");
                String reason = (e.getCause() == null ? e.getMessage() : e.getCause().getMessage());

                // Log a warning instead of a full stack trace to reduce log size.
                log.warn("Job message processing failed! {}: {}", messageId, reason);

                // If debugging is enabled log a more in depth message.
                log.debug("Unable to process message; rolling back client session.\n{}",
                    this.serializeMessage(message), e);

                this.rollback(session);
            }
            finally {
                this.unitOfWork.end();
            }
        }

        private String serializeMessage(CPMMessage message) {
            return String.format("Message [id: %s, address: %s, body: %s]",
                message.getMessageId(), message.getAddress(), message.getBody());
        }

        private void commit(CPMSession session) {
            try {
                session.commit();
            }
            catch (CPMException e) {
                log.error("Unable to commit messaging session", e);
            }
        }

        private void rollback(CPMSession session) {
            try {
                session.rollback();
            }
            catch (CPMException e) {
                log.error("Unable to rollback messaging session", e);
            }
        }
    }

}
