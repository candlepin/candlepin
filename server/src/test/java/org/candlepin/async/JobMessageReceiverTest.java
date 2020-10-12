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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.persist.UnitOfWork;

import org.junit.Before;
import org.junit.Test;



/**
 * Test suite for the JobMessageReceiver class
 */
public class JobMessageReceiverTest {

    private JobManager jobManager;
    private Configuration config;
    private CPMSessionFactory cpmSessionFactory;
    private ObjectMapper mapper;
    private UnitOfWork unitOfWork;

    // Collected state issued on a per-test basis
    private CPMSession session;
    private CPMConsumer consumer;
    private ThreadLocal<CPMMessageListener> listenerContainer;

    @Before
    public void setUp() throws Exception {
        this.config = new CandlepinCommonTestConfig();
        this.jobManager = mock(JobManager.class);
        this.mapper = new ObjectMapper();
        this.unitOfWork = mock(UnitOfWork.class);

        // Set the number of threads/consumers to 1 so we don't have to worry about
        // clobbering any collected state during consumer creation
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_THREADS, "1");

        // Reset collected state between tests
        this.listenerContainer = new ThreadLocal<>();
        this.consumer = this.createMockCPMConsumer(this.listenerContainer);
        this.session = this.createMockCPMSession(this.consumer);
        doReturn(this.session).when(this.consumer).getSession();

        this.cpmSessionFactory = this.createMockCPMSessionFactory(this.session);
    }

    private CPMSessionFactory createMockCPMSessionFactory(CPMSession session) throws CPMException {
        CPMSessionFactory factory = mock(CPMSessionFactory.class);
        CPMSessionConfig config = new CPMSessionConfig();

        doReturn(true).when(factory).isInitialized();
        doReturn(config).when(factory).createSessionConfig();

        doAnswer(iom -> session)
            .when(factory)
            .createSession();

        doAnswer(iom -> session)
            .when(factory)
            .createSession(any(CPMSessionConfig.class));

        return factory;
    }

    private CPMSession createMockCPMSession(CPMConsumer consumer) throws CPMException {
        CPMSession session = mock(CPMSession.class);
        CPMConsumerConfig cconfig = new CPMConsumerConfig();

        doReturn(cconfig).when(session).createConsumerConfig();
        doReturn(consumer).when(session).createConsumer();
        doReturn(consumer).when(session).createConsumer(any(CPMConsumerConfig.class));

        return session;
    }

    @SuppressWarnings("indentation")
    private CPMConsumer createMockCPMConsumer(ThreadLocal<CPMMessageListener> container) throws CPMException {
        CPMConsumer consumer = mock(CPMConsumer.class);

        doAnswer(iom -> {
                CPMMessageListener prev = container.get();
                container.set((CPMMessageListener) iom.getArguments()[0]);
                return prev;
            })
            .when(consumer)
            .setMessageListener(any(CPMMessageListener.class));

        doAnswer(iom -> container.get())
            .when(consumer)
            .getMessageListener();

        return consumer;
    }

    private CPMMessage createCPMMessage(String jobId, String jobKey) {
        CPMMessage message = mock(CPMMessage.class);
        String body = String.format("{ \"jobId\": \"%s\", \"jobKey\": \"%s\" }", jobId, jobKey);

        doReturn(body).when(message).getBody();

        return message;
    }

    private JobMessageReceiver buildJobMessageReceiver() {
        try {
            JobMessageReceiver receiver = new JobMessageReceiver(this.config, this.cpmSessionFactory,
                this.mapper);

            receiver.initialize(this.jobManager);

            return receiver;
        }
        catch (Exception e) {
            throw new RuntimeException("Unexpected exception occurred while building JobMessageReceiver", e);
        }
    }


    @Test
    public void testMessageAckAndSessionCommitOnSuccess() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();

        verify(this.unitOfWork, times(1)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

    @Test
    public void testMessageCommitOnJobExecutionException() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);


        doThrow(new JobExecutionException()).when(this.jobManager).executeJob(any(JobMessage.class));

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();

        verify(this.unitOfWork, times(1)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

    @Test
    public void testMessageCommitOnTerminalJobStateManagementException() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);


        JobStateManagementException exception = new JobStateManagementException(new AsyncJobStatus(),
            JobState.RUNNING, JobState.FAILED, true);

        doThrow(exception).when(this.jobManager).executeJob(any(JobMessage.class));

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();

        verify(this.unitOfWork, times(1)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

    @Test
    public void testMessageRollbackOnNonTerminalJobStateManagementException() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);


        JobStateManagementException exception = new JobStateManagementException(new AsyncJobStatus(),
            JobState.RUNNING, JobState.FAILED_WITH_RETRY, false);

        doThrow(exception).when(this.jobManager).executeJob(any(JobMessage.class));

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();

        verify(this.unitOfWork, times(1)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

    @Test
    public void testMessageRollbackOnMessageDispatchException() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);


        doThrow(new JobMessageDispatchException()).when(this.jobManager).executeJob(any(JobMessage.class));

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();

        verify(this.unitOfWork, times(1)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

    @Test
    public void testMessageCommitOnTerminalJobException() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);


        doThrow(new JobException(true)).when(this.jobManager).executeJob(any(JobMessage.class));

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, times(1)).commit();
        verify(this.session, never()).rollback();

        verify(this.unitOfWork, times(1)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

    @Test
    public void testMessageRollbackOnNonTerminalJobException() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");
        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);

        doThrow(new JobException(false)).when(this.jobManager).executeJob(any(JobMessage.class));

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();

        verify(this.unitOfWork, times(1)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

    @Test
    public void testMessageRollbackOnMessageProcessingException() throws Exception {
        CPMMessage message = this.createCPMMessage("test_id", "test_key");

        this.mapper = mock(ObjectMapper.class);
        doThrow(new RuntimeException("kaboom")).when(this.mapper).readValue(anyString(), any(Class.class));

        JobMessageReceiver receiver = this.buildJobMessageReceiver();
        CPMMessageListener listener = this.listenerContainer.get();
        assertNotNull(listener);

        listener.handleMessage(this.session, this.consumer, message);

        verify(message, times(1)).acknowledge();
        verify(this.session, never()).commit();
        verify(this.session, times(1)).rollback();

        verify(this.unitOfWork, times(0)).begin();
        verify(this.unitOfWork, times(1)).end();
    }

}
