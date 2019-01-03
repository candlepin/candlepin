/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.audit.MessageAddress;
import org.candlepin.auth.Principal;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;

import org.apache.log4j.MDC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * The JobManager manages the queueing, execution and general bookkeeping on jobs
 */
@Singleton
public class JobManager {
    private static Logger log = LoggerFactory.getLogger(JobManager.class);

    /** Stores our mapping of job keys to job classes */
    private static Map<String, Class<? extends AsyncJob>> jobs = new HashMap<>();


    private Configuration config;
    private AsyncJobStatusCurator jobCurator;
    private ObjectMapper objMapper;

    private PrincipalProvider principalProvider;

    private ClientSessionFactory sessionFactory;
    private ClientSession session;
    private ClientProducer producer;


    /**
     * Registers the given class for the specified key. If the key was already registered to
     * another class, the previously registered class will be returned.
     *
     * @param jobKey
     *  The key under which to register the job class
     *
     * @param jobClass
     *  The job class to register
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty, or jobClass is null
     *
     * @return
     *  the job class previously registered to the given key, or null if the key was not already
     *  registered
     */
    public static Class<? extends AsyncJob> registerJob(String jobKey, Class<? extends AsyncJob> jobClass) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        if (jobClass == null) {
            throw new IllegalArgumentException("jobClass is null");
        }

        return jobs.put(jobKey, jobClass);
    }

    /**
     * Removes the registration for the specified job key, if present. If the given key is not
     * registered to any job class, this function returns null.
     *
     * @param jobKey
     *  The job key to unregister
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty
     *
     * @return
     *  the job class previously registered to the given key, or null if the key was not already
     *  registered
     */
    public static Class<? extends AsyncJob> unregisterJob(String jobKey) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        return jobs.remove(jobKey);
    }

    /**
     * Fetches the job class registered to the specified key. If the key is not registered, this
     * function returns null.
     *
     * @param jobKey
     *  The key for which to fetch the job class
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty
     *
     * @return
     *  the job class registered to the given key, or null if the key is not registered
     */
    public static Class<? extends AsyncJob> getJobClass(String jobKey) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        return jobs.get(jobKey);
    }


    /**
     * The JobMessage container is used to pass messages to other nodes through the Artemis
     * subsystem.
     */
    private static class JobMessage {
        private String jobId;
        private String jobKey;

        @JsonCreator
        public JobMessage(String jobId, String jobKey) {
            this.jobId = jobId;
            this.jobKey = jobKey;
        }

        public String getJobId() {
            return this.jobId;
        }

        public String getJobKey() {
            return this.jobKey;
        }
    }


    /**
     * Creates a new JobManager instance
     */
    @Inject
    public JobManager(Configuration config, AsyncJobStatusCurator jobCurator, ObjectMapper objMapper,
        PrincipalProvider principalProvider) {

        this.config = config;
        this.jobCurator = jobCurator;
        this.objMapper = objMapper;

        this.principalProvider = principalProvider;
    }


    /**
     * Fetches the current session factory, initializing a new instance as necessary. This should
     * almost certainly not be used by any method other than getClientSession.
     *
     * @throws ActiveMQException
     *  if an exception occurs while spinning up the ActiveMQ client session factory
     *
     * @return
     *  the ClientSessionFactory instance for this job manager
     */
    private ClientSessionFactory getClientSessionFactory() throws ActiveMQException {
        if (this.sessionFactory == null || this.sessionFactory.isClosed()) {
            log.debug("Creating new ActiveMQ client session factory...");

            ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(
                new TransportConfiguration(InVMConnectorFactory.class.getName()));

            // TODO: Maybe make this a bit more defensive and skip setting the property if it's
            // not present in the configuration rather than crashing out?
            locator.setMinLargeMessageSize(this.config.getInt(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE));

            try {
                this.sessionFactory = locator.createSessionFactory();
                log.debug("Created new ActiveMQ client session factory: {}", this.sessionFactory);
            }
            catch (Exception e) {
                if (e instanceof ActiveMQException) {
                    throw (ActiveMQException) e;
                }
                else {
                    throw new RuntimeException(e);
                }
            }
        }

        return this.sessionFactory;
    }

    /**
     * Fetches the current client session, creating and intializing a new instance as necessary.
     *
     * @return
     *  the current ClientSession instance for this job manager
     */
    protected ClientSession getClientSession() throws ActiveMQException {
        if (this.session == null || this.session.isClosed()) {
            log.debug("Creating new ActiveMQ session for async job messages...");

            ClientSessionFactory csf = this.getClientSessionFactory();
            this.session = csf.createSession();

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
    protected ClientProducer getClientProducer() throws ActiveMQException {
        if (this.producer == null || this.producer.isClosed()) {
            log.debug("Creating new ActiveMQ producer for async job messages...");

            ClientSession session = this.getClientSession();
            this.producer = session.createProducer();

            log.debug("Created new ActiveMQ producer: {}", this.producer);
        }

        return this.producer;
    }

    /**
     * Posts a job message to the message bus
     *
     * @param JobMessage jobMessage
     */
    private void sendJobMessage(JobMessage jobMessage) throws ActiveMQException, JsonProcessingException {
        // TODO: Clean this up. JobMessage probably shouldn't exist.

        ClientSession session = this.getClientSession();
        ClientMessage message = session.createMessage(true);
        message.putStringProperty("job_key", jobMessage.getJobKey());

        String eventString = this.objMapper.writeValueAsString(message);
        message.getBodyBuffer().writeString(eventString);

        log.debug("Sending message to {}: {}", MessageAddress.JOB_MESSAGE_ADDRESS, eventString);

        ClientProducer producer = this.getClientProducer();
        producer.send(MessageAddress.JOB_MESSAGE_ADDRESS, message);
    }

    /**
     * Builds an AsyncJobStatus instance from the given job details. The job status will not be
     * a managed entity and will need to be manually persisted by the caller.
     *
     * @param builder
     *  The JobBuilder to use to build the job status
     *
     * @throws IllegalArgumentException
     *  if detail is null
     *
     * @return
     *  the newly constructed, unmanaged AsyncJobStatus instance
     */
    private AsyncJobStatus buildJobStatus(JobBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("job builder is null");
        }

        AsyncJobStatus job = new AsyncJobStatus();

        job.setJobKey(builder.getJobKey());
        job.setName(builder.getJobName());
        job.setGroup(builder.getJobGroup());

        // Add environment-specific metadata
        job.setOrigin(Util.getHostname());
        job.setCorrelationId((String) MDC.get(LoggingFilter.CSID));

        Principal principal = this.principalProvider.get();
        job.setPrincipal(principal != null ? principal.getName() : null);

        // TODO: add manually added metadata

        job.setMaxAttempts(builder.getRetryCount() + 1);
        job.setJobData(builder.getJobArguments());

        return job;
    }

    /**
     * Queues a job to be run on any Candlepin node backed by the same database as this node, and
     * is configured to process jobs matching the type of the specified job. If multiple nodes are
     * able to process a given job, there is no mechanism to guarantee consistently repeatable
     * behavior as to which node will actually execute the job.
     * <p></p>
     * If the specified job is one which is unique by some criteria, and a matching job is already
     * in the queue or currently executing, a new job will not be queued and the existing job's
     * job status will be returned instead.
     *
     * @param builder
     *  A JobBuilder instance representing the configuration of the job to queue
     *
     * @return
     *  an AsyncJobStatus instance representing the queued job's status, or the status of the
     *  existing job if it already exists
     */
    public AsyncJobStatus queueJob(JobBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("job builder is null");
        }

        AsyncJobStatus job = this.buildJobStatus(builder);

        // TODO:
        // Add job filtering/deduplication by criteria

        try {
            // Build the job message
            JobMessage message = new JobMessage(job.getId(), job.getJobKey());

            // Send the message
            this.sendJobMessage(message);

            job.setState(JobState.QUEUED);
        }
        catch (Exception e) {
            job.setState(JobState.FAILED);
            job.setJobResult(e);

            // Do we need to persist the job status in this branch? It's basically perma-dead.
        }

        // Persist the job status
        job = this.jobCurator.create(job);

        // Done!
        return job;
    }





    /**
     * Executes the specified job immediately on this Candlepin node, skipping any filtering or
     * deduplication mechanisms.
     * <p></p>
     * <strong>Note</strong>: Generally, this method should not be called directly, and jobs should
     * be queued using the <tt>queueJob</tt> method instead.
     *
     * @param
     *
     * @return
     *  a JobStatus instance representing the job's status
     */
    public AsyncJobStatus executeJob() {
        // TODO: Finish me

        return null;
    }


}
