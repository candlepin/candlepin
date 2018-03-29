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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * EventSourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EventSourceTest {

    @Mock private ClientSessionFactory clientSessionFactory;

    @Test
    public void registeringNewListenerCreatesNewSessionAndStartsIt() throws Exception {
        ClientSession clientSession = mock(ClientSession.class);
        EventSource source = createEventSourceStubbedWithFactoryCreation();
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(clientSession);
        when(clientSession.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));

        source.registerListener(mock(EventListener.class));
        verify(clientSessionFactory).createSession(eq(false), eq(false), eq(0));
        verify(clientSession).start();
    }

    @Test
    public void shouldStopAndCloseSessionsOnShutdown() throws Exception {
        ClientSession session1 = mock(ClientSession.class);
        ClientSession session2 = mock(ClientSession.class);

        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0)))
            .thenReturn(session1).thenReturn(session2);
        when(session1.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        when(session2.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));

        eventSource.registerListener(mock(EventListener.class));
        eventSource.registerListener(mock(EventListener.class));
        eventSource.shutDown();

        // Verify that all client sessions were stopped and closed.
        verify(session1).stop();
        verify(session1).close();

        verify(session2).stop();
        verify(session2).close();

        // Verify that the client session factory was closed.
        verify(clientSessionFactory).close();
    }

    @Test
    public void shouldStopAndCloseSessionOnShutdownWhenExceptionThrownOnStop() throws Exception {
        ClientSession session = mock(ClientSession.class);

        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(session);
        when(session.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        doThrow(new ActiveMQException("Forced")).when(session).stop();

        eventSource.registerListener(mock(EventListener.class));
        eventSource.shutDown();

        // Verify that close was at least still called.
        verify(session).close();

        // Verify that the client session factory was closed regardless of the exception.
        verify(clientSessionFactory).close();
    }

    @Test
    public void shouldStopAndCloseSessionOnShutdownWhenExceptionThrownOnClose() throws Exception {
        ClientSession session = mock(ClientSession.class);

        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(session);
        when(session.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        doThrow(new ActiveMQException("Forced")).when(session).close();

        eventSource.registerListener(mock(EventListener.class));
        eventSource.shutDown();

        // Verify that stop was at least still called.
        verify(session).stop();

        // Verify that the client session factory was closed regardless of the exception.
        verify(clientSessionFactory).close();
    }

    /**
     * Creates a new EventSource with a mocked ClientSessionFactory.
     *
     * @return the new EventSource
     */
    private EventSource createEventSourceStubbedWithFactoryCreation() {
        return new EventSource(new ObjectMapper()) {
            protected ClientSessionFactory createSessionFactory() {
                return clientSessionFactory;
            }
        };
    }

}
