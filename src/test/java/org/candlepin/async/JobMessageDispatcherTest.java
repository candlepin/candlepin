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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMProducer;
import org.candlepin.messaging.CPMProducerConfig;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionConfig;
import org.candlepin.messaging.CPMSessionFactory;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tools.jackson.databind.ObjectMapper;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;


/**
 * Test suite for the JobMessageDispatcher class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class JobMessageDispatcherTest {

    /**
     * Functional interface that lets us pass exceptions back through to the test
     */
    @FunctionalInterface
    private interface NoisyRunnable {
        void run() throws Exception;
    }

    /**
     * Utility class to allow async scheduling of tasks in a targetable thread
     */
    private static class TaskExecutor extends Thread {
        private boolean shutdown;
        private NoisyRunnable runnable;

        public TaskExecutor() {
            this.shutdown = false;
            this.runnable = null;

            this.setDaemon(true);
            this.start();

            try {
                synchronized (this) {
                    this.wait(1000);
                }
            }
            catch (InterruptedException e) {
                // Hrmm...
            }
        }

        public synchronized void run() {
            while (!this.shutdown) {
                try {
                    this.notifyAll();
                    this.wait();

                    if (this.runnable != null) {
                        this.runnable.run();
                    }
                }
                catch (InterruptedException e) {
                    // Wake up
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public synchronized TaskExecutor execute(NoisyRunnable runnable) throws InterruptedException {
            this.runnable = runnable;

            this.notifyAll();
            this.wait();

            return this;
        }

        public synchronized TaskExecutor shutdown() throws InterruptedException {
            this.shutdown = true;
            this.runnable = null;

            this.notifyAll();
            this.join();

            return this;
        }
    }

    private DevConfig config;
    private ObjectMapper mapper;

    private CPMSessionFactory sessionFactory;
    private CPMSessionConfig sessionConfig;
    private CPMProducerConfig producerConfig;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();
        this.mapper = ObjectMapperFactory.getObjectMapper();
        this.sessionFactory = mock(CPMSessionFactory.class);
        this.sessionConfig = spy(new CPMSessionConfig());
        this.producerConfig = spy(new CPMProducerConfig());

        doReturn(this.sessionConfig).when(this.sessionFactory).createSessionConfig();
    }

    private JobMessageDispatcher buildJobMessageDispatcher() throws ConfigurationException {
        return new JobMessageDispatcher(this.config, this.sessionFactory, this.mapper);
    }

    private CPMSession mockCPMSession() throws Exception {
        CPMSession session = mock(CPMSession.class);
        CPMMessage message = this.mockCPMMessage();

        doReturn(this.producerConfig).when(session).createProducerConfig();
        doReturn(false).when(session).isClosed();
        doReturn(message).when(session).createMessage();

        return session;
    }

    private CPMMessage mockCPMMessage() {
        CPMMessage message = mock(CPMMessage.class);

        doReturn(message).when(message).setDurable(anyBoolean());
        doReturn(message).when(message).setBody(anyString());
        doReturn(message).when(message).setProperty(anyString(), anyString());

        return message;
    }

    @Test
    public void testDispatchAddressCannotBeNull() {
        this.config.clearProperty(ConfigProperties.ASYNC_JOBS_DISPATCH_ADDRESS);

        assertThrows(ConfigurationException.class, this::buildJobMessageDispatcher);
    }

    @Test
    public void testDispatchAddressCannotBeEmpty() {
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_DISPATCH_ADDRESS, "");

        assertThrows(ConfigurationException.class, this::buildJobMessageDispatcher);
    }

    @Test
    public void testDispatchesMessagesToConfiguredAddress() throws Exception {
        String address = TestUtil.randomString("test_address");
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_DISPATCH_ADDRESS, address);

        JobMessageDispatcher dispatcher = this.buildJobMessageDispatcher();

        CPMSession session = this.mockCPMSession();
        CPMProducer producer = mock(CPMProducer.class);

        doReturn(session).when(this.sessionFactory).createSession(any(CPMSessionConfig.class));
        doReturn(producer).when(session).createProducer(any(CPMProducerConfig.class));

        dispatcher.postJobMessage(new JobMessage("job_id-1", "job_key-1"));

        verify(producer, times(1)).send(eq(address), any(CPMMessage.class));
    }

    @Test
    public void testThreadsReuseSessions() throws Exception {
        JobMessageDispatcher dispatcher = this.buildJobMessageDispatcher();

        CPMSession session = this.mockCPMSession();
        CPMProducer producer = mock(CPMProducer.class);

        doReturn(session).when(this.sessionFactory).createSession(any(CPMSessionConfig.class));
        doReturn(producer).when(session).createProducer(any(CPMProducerConfig.class));

        dispatcher.postJobMessage(new JobMessage("job_id-1", "job_key-1"));
        dispatcher.commit();

        dispatcher.postJobMessage(new JobMessage("job_id-2", "job_key-2"));
        dispatcher.rollback();

        verify(this.sessionFactory, times(1)).createSession(any(CPMSessionConfig.class));
        verify(session, times(1)).createProducer(any(CPMProducerConfig.class));

        verify(producer, times(2)).send(anyString(), any(CPMMessage.class));
        verify(session, times(1)).commit();
        verify(session, times(1)).rollback();
    }

    @Test
    public void testThreadsDontShareSessions() throws Exception {
        JobMessageDispatcher dispatcher = this.buildJobMessageDispatcher();

        CPMSession session1 = this.mockCPMSession();
        CPMSession session2 = this.mockCPMSession();
        CPMProducer producer1 = mock(CPMProducer.class);
        CPMProducer producer2 = mock(CPMProducer.class);

        doReturn(producer1).when(session1).createProducer(any(CPMProducerConfig.class));
        doReturn(producer2).when(session2).createProducer(any(CPMProducerConfig.class));

        doReturn(session1, session2).when(this.sessionFactory).createSession(any(CPMSessionConfig.class));

        TaskExecutor executor1 = new TaskExecutor();
        TaskExecutor executor2 = new TaskExecutor();

        executor1.execute(() -> dispatcher.postJobMessage(new JobMessage("job_id-1", "job_key-1")));
        executor2.execute(() -> dispatcher.postJobMessage(new JobMessage("job_id-2", "job_key-2")));
        executor1.execute(dispatcher::commit);
        executor2.execute(dispatcher::commit);

        executor1.shutdown();
        executor2.shutdown();

        verify(this.sessionFactory, times(2)).createSession(any(CPMSessionConfig.class));

        verify(session1, times(1)).createProducer(any(CPMProducerConfig.class));
        verify(session2, times(1)).createProducer(any(CPMProducerConfig.class));
        verify(producer1, times(1)).send(anyString(), any(CPMMessage.class));
        verify(producer2, times(1)).send(anyString(), any(CPMMessage.class));
        verify(session1, times(1)).commit();
        verify(session2, times(1)).commit();
    }

    @Test
    public void testAbandonedSessionsAreClosed() throws Exception {
        JobMessageDispatcher dispatcher = this.buildJobMessageDispatcher();

        CPMSession session = this.mockCPMSession();
        CPMProducer producer = mock(CPMProducer.class);

        doReturn(session).when(this.sessionFactory).createSession(any(CPMSessionConfig.class));
        doReturn(producer).when(session).createProducer(any(CPMProducerConfig.class));

        ReferenceQueue refQueue = new ReferenceQueue();
        TaskExecutor executor = new TaskExecutor();
        PhantomReference ref = new PhantomReference(executor, refQueue);

        executor.execute(() -> dispatcher.postJobMessage(new JobMessage("job_id-1", "job_key-1")))
            .shutdown();

        executor = null;

        while (true) {
            System.gc();
            Thread.sleep(500);

            if (refQueue.poll() == ref) {
                break;
            }
        }

        // Send another message from this thread to trigger the prune
        dispatcher.postJobMessage(new JobMessage("job_id-2", "job_key-2"));

        verify(session, times(1)).close();
    }

}
