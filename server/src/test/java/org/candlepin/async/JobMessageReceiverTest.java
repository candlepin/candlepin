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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;

import org.junit.Before;
import org.junit.Test;



public class JobMessageReceiverTest {

    private String filter;
    private JobManager jobManager;
    private ObjectMapper objMapper;

    private ActiveMQSessionFactory sessionFactory;
    private ClientSession session;
    private ClientConsumer consumer;

    @Before
    public void setUp() throws Exception {
        this.filter = "test filter";
        this.jobManager = mock(JobManager.class);
        this.objMapper = new ObjectMapper();

        // Prep the session factory...
        this.sessionFactory = mock(ActiveMQSessionFactory.class);
        this.session = mock(ClientSession.class);
        this.consumer = mock(ClientConsumer.class);

        doReturn(this.session).when(this.sessionFactory).getIngressSession(anyBoolean());
        doReturn(this.consumer).when(this.session).createConsumer(anyString(), anyString());
    }

    public JobMessageReceiver buildJobMessageReceiver() {
        try {
            JobMessageReceiver receiver = new JobMessageReceiver(this.filter, this.jobManager,
                this.sessionFactory, this.objMapper);

            receiver.initialize();

            return receiver;
        }
        catch (Exception e) {
            throw new RuntimeException("Unexpected exception occurred while building JobMessageReceiver", e);
        }
    }

    public ClientMessage buildClientMessage(String jobId, String jobKey) {
        try {
            String body = String.format("{ \"jobId\": \"%s\", \"jobKey\": \"%s\" }", jobId, jobKey);

            ClientMessage message = mock(ClientMessage.class);
            ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(body.length());
            buffer.writeString(body);

            doReturn(buffer).when(message).getBodyBuffer();
            // Mock more of this thing here as necessary. Yeesh...

            return message;
        }
        catch (Exception e) {
            throw new RuntimeException("Unexpected exception occurred while building ClientMessage", e);
        }
    }

    @Test
    public void testMessageAckAndSessionCommitOnSuccess() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();
    }

    @Test
    public void testMessageCommitOnJobExecutionException() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        doThrow(new JobExecutionException()).when(this.jobManager).executeJob(any());

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();
    }

    @Test
    public void testMessageCommitOnTerminalJobStateManagementException() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        JobStateManagementException exception = new JobStateManagementException(new AsyncJobStatus(),
            JobState.RUNNING, JobState.FAILED, true);

        doThrow(exception).when(this.jobManager).executeJob(any());

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();
    }

    @Test
    public void testMessageRollbackOnNonTerminalJobStateManagementException() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        JobStateManagementException exception = new JobStateManagementException(new AsyncJobStatus(),
            JobState.RUNNING, JobState.FAILED_WITH_RETRY, false);

        doThrow(exception).when(this.jobManager).executeJob(any());

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();
    }

    @Test
    public void testMessageRollbackOnMessageDispatchException() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        doThrow(new JobMessageDispatchException()).when(this.jobManager).executeJob(any());

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();
    }

    @Test
    public void testMessageCommitOnTerminalJobException() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        doThrow(new JobException(true)).when(this.jobManager).executeJob(any());

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();
    }

    @Test
    public void testMessageRollbackOnNonTerminalJobException() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        doThrow(new JobException(false)).when(this.jobManager).executeJob(any());

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();
    }

    @Test
    public void testMessageRollbackOnMessageProcessingException() throws Exception {
        ClientMessage message = this.buildClientMessage("test_id", "test_key");

        this.objMapper = mock(ObjectMapper.class);
        doThrow(new RuntimeException("kaboom")).when(this.objMapper).readValue(anyString(), any(Class.class));

        JobMessageReceiver receiver = this.buildJobMessageReceiver();

        receiver.onMessage(message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();
    }

}
