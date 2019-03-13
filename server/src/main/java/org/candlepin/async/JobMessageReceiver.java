/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.audit.MessageReceiver;
import org.candlepin.model.AsyncJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for receiving async job messages from the Artemis job queue.
 */
public class JobMessageReceiver extends MessageReceiver {
    private static Logger log = LoggerFactory.getLogger(JobMessageReceiver.class);
    private static final String JOB_QUEUE_NAME = "jobs";

    private JobManager manager;
    private ClientSession session;
    private ClientConsumer consumer;
    private String msgFilter;

    public JobMessageReceiver(String msgFilter, JobManager manager, ActiveMQSessionFactory sessionFactory,
        ObjectMapper mapper) {
        super(JOB_QUEUE_NAME, sessionFactory, mapper);
        this.manager = manager;
        this.msgFilter = msgFilter;
    }

    @Override
    protected void initialize() throws Exception {
        session = this.sessionFactory.getIngressSession(false);
        consumer = session.createConsumer(queueName, msgFilter);
        consumer.setMessageHandler(this);
        session.start();
    }

    @Override
    protected String getQueueAddress() {
        return "jobs";
    }

    @Override
    public void onMessage(ClientMessage message) {
        String body = "";
        try {
            // Acknowledge the message so that the server knows that it was received.
            // By doing this, the server can update the delivery counts which plays
            // part in calculating redelivery delays.
            message.acknowledge();

            // Read the message and deserialize the data.
            body = message.getBodyBuffer().readString();
            log.debug("Got event: {}", body);
            JobMessage jobMessage = mapper.readValue(body, JobMessage.class);
            log.debug("ActiveMQ message {} received for async job: {}.", message.getMessageID(), jobMessage);

            // Execute the job
            AsyncJobStatus jobStatus = manager.executeJob(jobMessage);
            log.debug("Job complete: {}:{}:{}", jobMessage.getJobId(), jobMessage.getJobKey(),
                jobStatus.getState());

            // Finally commit the session so that the message is taken out of the queue.
            session.commit();
        }
        catch (JobInitializationException jie) {
            // Expected when:
            //   * Job status could not be found
            //   * Job was determined cancelled before execution.
            try {
                session.commit();
            }
            catch (ActiveMQException amqe) {
                log.error("Unable to commit job message after receiving JobInitializationException.", amqe);
                // Nothing we can do. The job will be lost.
            }
        }
        catch (Exception e) {
            // Once a job actually executes, the failure should be recorded in the job's status
            // and should never reach this code. This catch block will trap the exception, log
            // the error and put the message back on the queue.
            String messageId = (message == null) ? "" : Long.toString(message.getMessageID());
            String reason = (e.getCause() == null) ? e.getMessage() : e.getCause().getMessage();

            // Log a warning instead of a full stack trace to reduce log size.
            log.warn("Job execution failed! {}: {}", messageId, reason);

            // If debugging is enabled log a more in depth message.
            log.debug("Unable to process message. Rolling back client session.\n{}", body, e);
            try {
                // Roll back the session so that the message remains on the queue.
                session.rollback();
            }
            catch (ActiveMQException amqe) {
                log.error("Unable to roll back client session.", amqe);
            }

            // Session was rolled back, nothing left to do.
        }
    }

}
