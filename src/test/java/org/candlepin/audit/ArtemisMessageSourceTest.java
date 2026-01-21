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
package org.candlepin.audit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.util.ObjectMapperFactory;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * ArtemisMessageSourceTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ArtemisMessageSourceTest {

    @Mock
    private ClientSessionFactory clientSessionFactory;

    private ActiveMQSessionFactory sessionFactory;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        this.sessionFactory = createConnection();
        this.objectMapper = ObjectMapperFactory.getObjectMapper();
    }

    @Test
    public void eventSourceDoesNotConnectEventReceiversUntilNotifiedConnectionEstablished() throws Exception {
        ActiveMQSessionFactory connection = createConnection();

        List<MessageReceiver> messageReceivers = new LinkedList<>();
        messageReceivers.add(new DefaultEventMessageReceiver(mock(EventListener.class),
            connection, mock(ObjectMapper.class)));

        MessageSourceReceiverFactory receiverFactory = mock(MessageSourceReceiverFactory.class);
        when(receiverFactory.get(connection)).thenReturn(messageReceivers);

        ArtemisMessageSource source = new ArtemisMessageSource(connection,
            objectMapper, receiverFactory);

        // Should not attempt to create a client session.
        verifyNoInteractions(clientSessionFactory);

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

        ArtemisMessageSource messageSource = createEventSourceStubbedWithFactoryCreation(receiver1,
            receiver2);
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

    /**
     * Creates a new ArtemisMessageSource with a mocked ClientSessionFactory.
     *
     * @return the new ArtemisMessageSource
     */
    private ArtemisMessageSource createEventSourceStubbedWithFactoryCreation(MessageReceiver... receivers)
        throws Exception {
        MessageSourceReceiverFactory receiverFactory = mock(MessageSourceReceiverFactory.class);
        when(receiverFactory.get(sessionFactory)).thenReturn(Arrays.asList(receivers));

        ArtemisMessageSource source = new ArtemisMessageSource(sessionFactory,
            objectMapper, receiverFactory);

        // Listener client sessions are not created until the source has been notified
        // that the connection can be made. Simulate this.
        source.onStatusUpdate(ActiveMQStatus.UNKNOWN, ActiveMQStatus.CONNECTED);
        return source;
    }

    private ActiveMQSessionFactory createConnection() {
        return new TestingActiveMQSessionFactory(clientSessionFactory, null);
    }

}
