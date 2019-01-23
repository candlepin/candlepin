/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.audit;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * ArtemisMessageSourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ArtemisMessageSourceTest {

    @Mock private ClientSessionFactory clientSessionFactory;

    private ActiveMQSessionFactory sessionFactory;

    @Before
    public void setup() {
        this.sessionFactory = createConnection();
    }

    @Test
    public void eventSourceDoesNotConnectEventReceiversUntilNotifiedConnectionEstablished() throws Exception {
        ActiveMQSessionFactory connection = createConnection();

        List<MessageReceiver> messageReceivers = new LinkedList<>();
        messageReceivers.add(new DefaultEventMessageReceiver(mock(EventListener.class),
            connection, mock(ObjectMapper.class)));

        MessageSourceReceiverFactory receiverFactory = mock(MessageSourceReceiverFactory.class);
        when(receiverFactory.get(eq(connection))).thenReturn(messageReceivers);

        ArtemisMessageSource source = new ArtemisMessageSource(connection, new ObjectMapper(),
            receiverFactory);

        // Should not attempt to create a client session.
        verifyZeroInteractions(clientSessionFactory);

        ClientSession session = mock(ClientSession.class);
        when(clientSessionFactory.createSession()).thenReturn(session);
        when(session.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));

        // Simulate connection notification.
        source.onStatusUpdate(ActiveMQStatus.UNKNOWN, ActiveMQStatus.CONNECTED);
        verify(session).createConsumer(any(String.class));
        verify(session, times(1)).start();
        verify(session, never()).stop();

    }

    @Test
    public void shouldStopAndCloseSessionsOnShutdown() throws Exception {
        ClientSession session1 = mock(ClientSession.class);
        ClientSession session2 = mock(ClientSession.class);

        when(clientSessionFactory.createSession())
            .thenReturn(session1).thenReturn(session2);
        when(session1.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        when(session2.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));

        DefaultEventMessageReceiver receiver1 = new DefaultEventMessageReceiver(mock(EventListener.class),
            sessionFactory, mock(ObjectMapper.class));
        DefaultEventMessageReceiver receiver2 = new DefaultEventMessageReceiver(mock(EventListener.class),
            sessionFactory, mock(ObjectMapper.class));

        ArtemisMessageSource messageSource =
            createEventSourceStubbedWithFactoryCreation(receiver1, receiver2);
        messageSource.shutDown();

        // Verify that all client sessions were closed.
        verify(session1).close();
        verify(session2).close();
    }

    @Test
    public void shouldStopAndCloseSessionOnShutdownWhenExceptionThrownOnStop() throws Exception {
        ClientSession session = mock(ClientSession.class);

        when(clientSessionFactory.createSession()).thenReturn(session);
        when(session.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        doThrow(new ActiveMQException("Forced")).when(session).stop();

        DefaultEventMessageReceiver receiver = new DefaultEventMessageReceiver(mock(EventListener.class),
            sessionFactory, mock(ObjectMapper.class));

        ArtemisMessageSource messageSource = createEventSourceStubbedWithFactoryCreation(receiver);
        messageSource.shutDown();

        // Verify that close was at least still called.
        verify(session).close();
    }

    @Test
    public void shouldStopAndCloseSessionOnShutdownWhenExceptionThrownOnClose() throws Exception {
        ClientSession session = mock(ClientSession.class);

        when(clientSessionFactory.createSession()).thenReturn(session);
        when(session.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        doThrow(new ActiveMQException("Forced")).when(session).close();

        DefaultEventMessageReceiver receiver = new DefaultEventMessageReceiver(mock(EventListener.class),
            sessionFactory, mock(ObjectMapper.class));

        ArtemisMessageSource messageSource = createEventSourceStubbedWithFactoryCreation(receiver);
        messageSource.shutDown();

        // Verify that close was at least still called.
        verify(session).close();
    }

    @Test
    public void eventSourceDoesNothingWhenQpidStatusDoesntChange() throws Exception {
        ClientSession session = mock(ClientSession.class);
        when(clientSessionFactory.createSession()).thenReturn(session);
        when(session.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));

        ArtemisMessageSource messageSource =
            createEventSourceStubbedWithFactoryCreation(createQpidReceiver());
        messageSource.onStatusUpdate(QpidStatus.DOWN, QpidStatus.DOWN);

        // Start was called only during setup.
        verify(session, times(1)).start();
        verify(session, never()).stop();
    }

    @Test
    public void eventSourceClosesEventReceiverClientConsumerWhenQpidGoesDown() throws Exception {
        ClientSession session = mock(ClientSession.class);
        when(clientSessionFactory.createSession()).thenReturn(session);

        ClientConsumer consumer = mock(ClientConsumer.class);
        when(session.createConsumer(any(String.class))).thenReturn(consumer);

        ArtemisMessageSource messageSource =
            createEventSourceStubbedWithFactoryCreation(createQpidReceiver());
        messageSource.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.DOWN);

        // Start was called only on construction
        verify(session, times(1)).start();
        verify(consumer).close();
        verify(session, never()).stop();
    }

    @Test
    public void eventSourceCreatesNewEventReceiverClientSessionWhenQpidComesUp() throws Exception {
        ClientSession session = mock(ClientSession.class);
        when(clientSessionFactory.createSession()).thenReturn(session);

        // Initially created before qpid goes down.
        ClientConsumer consumer1 = mock(ClientConsumer.class);
        when(consumer1.isClosed()).thenReturn(true);

        // Created when qpid comes back up.
        ClientConsumer consumer2 = mock(ClientConsumer.class);
        when(session.createConsumer(any(String.class))).thenReturn(consumer1, consumer2);

        QpidEventMessageReceiver receiver = createQpidReceiver();

        ArtemisMessageSource messageSource = createEventSourceStubbedWithFactoryCreation(receiver);
        messageSource.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);

        // Start was called only during setup.
        verify(session, times(1)).start();
        verify(session, never()).stop();
        verify(session, times(2)).createConsumer(any(String.class));
    }

    @Test
    public void eventSourceDoesNothingWithEventReceiverClientSessionWhenListenerDoesntRequireQpid()
        throws Exception {
        ClientSession session = mock(ClientSession.class);
        when(clientSessionFactory.createSession()).thenReturn(session);
        when(session.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));

        DefaultEventMessageReceiver receiver = new DefaultEventMessageReceiver(mock(EventListener.class),
            sessionFactory, mock(ObjectMapper.class));

        ArtemisMessageSource messageSource = createEventSourceStubbedWithFactoryCreation(receiver);
        messageSource.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);

        // Start was called only during setup.
        verify(session, times(1)).start();
        verify(session, never()).stop();
    }

    /**
     * Creates a new ArtemisMessageSource with a mocked ClientSessionFactory.
     *
     * @return the new ArtemisMessageSource
     */
    private ArtemisMessageSource createEventSourceStubbedWithFactoryCreation(MessageReceiver ... receivers)
        throws Exception {
        MessageSourceReceiverFactory receiverFactory = mock(MessageSourceReceiverFactory.class);
        when(receiverFactory.get(eq(sessionFactory))).thenReturn(Arrays.asList(receivers));

        ArtemisMessageSource source = new ArtemisMessageSource(sessionFactory, new ObjectMapper(),
            receiverFactory);

        // Listener client sessions are not created until the source has been notified
        // that the connection can be made. Simulate this.
        source.onStatusUpdate(ActiveMQStatus.UNKNOWN, ActiveMQStatus.CONNECTED);
        return source;
    }

    private ActiveMQSessionFactory createConnection() {
        return new TestingActiveMQSessionFactory(clientSessionFactory, null);
    }

    private QpidEventMessageReceiver createQpidReceiver() {
        EventListener listener = mock(EventListener.class);
        when(listener.requiresQpid()).thenReturn(true);
        return new QpidEventMessageReceiver(listener, this.sessionFactory, mock(ObjectMapper.class));
    }

}
