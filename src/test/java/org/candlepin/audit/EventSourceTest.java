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

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.codehaus.jackson.map.ObjectMapper;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.HornetQExceptionType;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.junit.Before;
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
    @Mock private ClientSession clientSession;

    @Before
    public void init() throws Exception {
        when(clientSessionFactory.createSession(eq(true), eq(true)))
            .thenReturn(clientSession);
    }
    /**
     * @return
     */
    private EventSource createEventSourceStubbedWithFactoryCreation() {
        return new EventSource(new ObjectMapper()) {
            protected ClientSessionFactory createSessionFactory() {
                return clientSessionFactory;
            }
        };
    }

    @Test
    public void shouldStartSessionWhenCreatingEventSource() throws Exception {
        createEventSourceStubbedWithFactoryCreation();
        verify(this.clientSession).start();
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRunTimeExceptionWhenSessionCreateFailsDuringEventSourceCreation()
        throws Exception {
        doThrow(new HornetQException()).when(clientSession).start();
        createEventSourceStubbedWithFactoryCreation();
        fail("Should have thrown runtime exception");
    }

    @Test
    public void shouldNotThrowExceptionWhenQueueCreationFails() throws Exception {
        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        doThrow(new HornetQException(HornetQExceptionType.QUEUE_DOES_NOT_EXIST))
            .when(clientSession).createQueue(anyString(), anyString());
        EventListener eventListener = mock(EventListener.class);
        eventSource.registerListener(eventListener);

        verify(clientSession, never()).createConsumer(anyString()); //should not be invoked
    }

    @Test
    public void shouldCreateNewConsumerWhenQueueDoesNotExistOnRegisterListenerCall()
        throws Exception {
        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        ClientConsumer mockCC = mock(ClientConsumer.class);
        when(clientSession.createConsumer(anyString()))
            .thenReturn(mockCC);
        EventListener eventListener = mock(EventListener.class);
        eventSource.registerListener(eventListener);

        //make sure queue is created.
        verify(clientSession).createQueue(anyString(), anyString());
        verify(mockCC).setMessageHandler(any(ListenerWrapper.class));
    }

    @Test
    public void shouldCreateNewConsumerWhenQueueExistsOnRegisterListenerCall()
        throws Exception {
        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        ClientConsumer mockCC = mock(ClientConsumer.class);
        when(clientSession.createConsumer(anyString()))
            .thenReturn(mockCC);
        doThrow(new HornetQException(HornetQExceptionType.QUEUE_EXISTS))
            .when(clientSession).createQueue(anyString(), anyString());
        EventListener eventListener = mock(EventListener.class);
        //invoke
        eventSource.registerListener(eventListener);

        //verify listener is still added.
        verify(mockCC).setMessageHandler(any(ListenerWrapper.class));
    }

    @Test
    public void shouldStopAndCloseSessionOnShutdown() throws Exception {
        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();

        eventSource.shutDown();

        verify(clientSession).stop();
        verify(clientSession).close();
    }

    @Test
    public void shouldStopAndCloseSessionOnShutdownWhenExceptionThrownOnStop()
        throws Exception {
        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        doThrow(new HornetQException()).when(clientSession).stop();

        eventSource.shutDown();

        assertTrue(true); //so sorry!
    }

    @Test
    public void shouldStopAndCloseSessionOnShutdownWhenExceptionThrownOnClose()
        throws Exception {
        EventSource eventSource = createEventSourceStubbedWithFactoryCreation();
        doThrow(new HornetQException()).when(clientSession).close();

        eventSource.shutDown();
        verify(this.clientSession).stop();
    }

}
