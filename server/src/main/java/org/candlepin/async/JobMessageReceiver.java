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
import org.candlepin.model.AsyncJobStatus.JobState;

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

        consumer = msgFilter != null ?
            session.createConsumer(queueName, msgFilter) :
            session.createConsumer(queueName);

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

            // We didn't fail! Commit the message
            this.commitSession();
        }
        catch (JobExecutionException e) {
            // The job failed during execution; retry logic within JobManager will handle this
            // case for us. We need not handle it any further.
            this.commitSession();
        }
        catch (JobStateManagementException e) {
            // The JobManager failed to update the job's state. Depending on the intended state,
            // we may or may not want to commit the message.

            JobState intendedState = e.getIntendedState();

            if (intendedState != null && !intendedState.isTerminal()) {
                log.error("Job processing failed; rolling back job message to retry later: {}", body);
                // The intended state is non-terminal (likely fail-with-retry), so we'll rollback
                // to allow the message to be redelivered so the job can be re-attempted.
                this.rollbackSession();
            }
            else {
                log.error("Job processing failed terminally; committing job message as acknowledged: {}",
                    body);

                // The state is unknown or terminal. We don't want to redeliver the message as
                // the job was intended to be put in a "completed" state.

                // TODO: We need to move the message to a dead-letter queue or some such so we can
                // clean up the old job once the DB is playing nice again. While the job will be
                // processed correctly, we're going to have a desync'd status in the database.
                this.commitSession();
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
                "to retry later: {}", body);

            this.rollbackSession();
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
                    body);

                // TODO: Move the message to another queue for cleanup/resync later
                this.commitSession();
            }
            else {
                log.error("Job processing failed; rolling back job message to retry later: {}", body);
                this.rollbackSession();
            }
        }
        catch (Exception e) {
            // Once a job actually executes, the failure should be recorded in the job's status
            // and should never reach this code. This catch block will trap the exception, log
            // the error and put the message back on the queue.
            // The only time we should hit this branch is if something blows up while attempting
            // to process the job message.
            String messageId = (message == null) ? "" : Long.toString(message.getMessageID());
            String reason = (e.getCause() == null) ? e.getMessage() : e.getCause().getMessage();

            // Log a warning instead of a full stack trace to reduce log size.
            log.warn("Job message processing failed! {}: {}", messageId, reason);

            // If debugging is enabled log a more in depth message.
            log.debug("Unable to process message. Rolling back client session.\n{}", body, e);

            this.rollbackSession();
        }
    }

    /**
     * Commits the session if work is pending. If no work is pending, this method immediately
     * returns.
     *
     * @return
     *  true if there was pending work that was committed successfully; false otherwise
     */
    private boolean commitSession() {
        try {
            this.session.commit();
            return true;
        }
        catch (ActiveMQException amqe) {
            log.error("Unable to commit client session", amqe);
        }

        return false;
    }

    /**
     * Rolls back the session if work is pending. If no work is pending, this method immediately
     * returns.
     *
     * @return
     *  true if there was pending work that was rolled back successfully; false otherwise
     */
    private boolean rollbackSession() {
        try {
            this.session.rollback();
            return true;
        }
        catch (ActiveMQException amqe) {
            log.error("Unable to roll back client session.", amqe);
        }

        return false;
    }
}
