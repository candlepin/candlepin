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
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.model.JobCurator;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.quartz.JobDetail;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * The JobManager manages the queueing, execution and general bookkeeping on jobs
 */
@Singleton
public class JobManager {
    private static Logger log = LoggerFactory.getLogger(JobManager.class);

    protected Configuration config;
    protected JobCurator jobCurator;
    protected ObjectMapper objMapper;

    private ClientSessionFactory sessionFactory;
    private ClientSession session;
    private ClientProducer producer;


    /**
     * Temporary?
     */
    private static class JobMessage {
        private String jobId;
        private String jobClass;

        @JsonCreator
        public JobMessage(String jobId, String jobClass) {
            this.jobId = jobId;
            this.jobClass = jobClass;
        }

        public String getJobId() {
            return this.jobId;
        }

        public String getJobClass() {
            return this.jobClass;
        }
    }






    /**
     * Creates a new JobManager instance
     */
    @Inject
    public JobManager(Configuration config, JobCurator jobCurator, ObjectMapper objMapper) {
        this.config = config;
        this.jobCurator = jobCurator;
        this.objMapper = objMapper;
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
    private void sendJobMessage(JobMessage jobMessage) {
        // TODO: Clean this up. JobMessage probably shouldn't exist.

        try {
            ClientSession session = this.getClientSession();
            ClientMessage message = session.createMessage(true);
            message.putStringProperty("job_class", jobMessage.getJobClass());

            String eventString = this.objMapper.writeValueAsString(message);
            message.getBodyBuffer().writeString(eventString);

            log.debug("Sending message to {}: {}", MessageAddress.JOB_MESSAGE_ADDRESS, eventString);

            ClientProducer producer = this.getClientProducer();
            producer.send(MessageAddress.JOB_MESSAGE_ADDRESS, message);
        }
        catch (Exception e) {
            log.error("Error while trying to send job message: {}", e);
            throw new RuntimeException("Error trying to send job message.", e);
        }
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
     * @param
     *
     * @return
     *  a JobStatus instance representing the queued job's status, or the status of the existing
     *  job if it already exists
     */
    public JobStatus queueJob(JobDetail detail) {
        if (detail == null) {
            throw new IllegalArgumentException("job detail is null");
        }

        // TODO:
        // This whole input scheme should change. We should not be accepting an already-completed
        // job status as input, as this suggests the work is already done. We should be using either
        // the (fully qualified) job class name, or some kind of job builder which configures a
        // given job exactly as the caller intends.
        //
        // Example:
        //      JobBuilder builder = new JobBuilder()
        //          .forTask(runnable job class or key here)
        //          .setTaskArgument("key", "value")
        //          .setTaskArgument("key2", "value2")
        //          .addUniqueRestriction("owner", owner_id_here)
        //          .addUniqueRestriction("product", product_id_here)
        //          .addTaskMetadata("correlation_id", cid)
        //          .setRetryCount(3)
        //
        //      jobManager.queueJob(builder);

        // TODO:
        // Add job filtering/deduplication by criteria

        // Create the job in the job table
        try {
            JobStatus job = new JobStatus(detail, false);
            job.setJobData(this.objMapper.writeValueAsString(detail.getJobDataMap()));
            job.setJobOrigin(Util.getHostname());

            job = this.jobCurator.create(job);

            // Build the job message
            JobMessage message = new JobMessage(job.getId(), job.getJobClass());

            // Send the message
            this.sendJobMessage(message);

            // Done!
            return job;
        }
        // TODO: Clean up the exception handling here.
        catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
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
    public JobStatus executeJob() {
        // TODO: Finish me

        return null;
    }


}
