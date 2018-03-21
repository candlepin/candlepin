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
package org.candlepin.audit;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * EventReceiverTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EventReceiverTest {

    @Mock ClientSessionFactory clientSessionFactory;
    @Mock ClientSession clientSession;
    @Mock EventListener eventListener;

    @Test(expected = ActiveMQException.class)
    public void shouldThrowActiveMQExceptionWhenSessionCreateFailsDuringEventSourceCreation()
        throws Exception {
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(clientSession);
        when(clientSession.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        doThrow(new ActiveMQException()).when(clientSession).start();

        new EventReceiver(eventListener, clientSessionFactory, new ObjectMapper());
        fail("Should have thrown ActiveMQException");
    }

    @Test(expected = ActiveMQException.class)
    public void shouldThrowExceptionWhenQueueCreationFails() throws Exception {
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(clientSession);
        when(clientSession.createConsumer(any(String.class))).thenReturn(mock(ClientConsumer.class));
        doThrow(new ActiveMQException(ActiveMQExceptionType.DISCONNECTED))
            .when(clientSession).createQueue(anyString(), anyString(), eq(true));

        new EventReceiver(eventListener, clientSessionFactory, new ObjectMapper());
        verify(clientSession, never()).createConsumer(anyString()); //should not be invoked
        verify(clientSession, never()).start();
    }

    @Test
    public void shouldCreateNewConsumerWhenQueueExistsOnRegisterListenerCall()
        throws Exception {
        ClientConsumer mockCC = mock(ClientConsumer.class);
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(clientSession);
        when(clientSession.createConsumer(anyString())).thenReturn(mockCC);
        doThrow(new ActiveMQException(ActiveMQExceptionType.QUEUE_EXISTS))
            .when(clientSession).createQueue(anyString(), anyString());

        new EventReceiver(eventListener, clientSessionFactory, new ObjectMapper());

        verify(clientSession).createConsumer(anyString());
        verify(mockCC).setMessageHandler(any(ListenerWrapper.class));
        verify(clientSession).start();
    }

    @Test
    public void shouldCreateNewConsumerWhenQueueDoesNotExistOnRegisterListenerCall()
        throws Exception {
        ClientConsumer mockCC = mock(ClientConsumer.class);
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(clientSession);
        when(clientSession.createConsumer(anyString())).thenReturn(mockCC);

        new EventReceiver(eventListener, clientSessionFactory, new ObjectMapper());

        //make sure queue is created.
        verify(clientSession).createQueue(anyString(), anyString(), eq(true));
        verify(clientSession).createConsumer(anyString());
        verify(mockCC).setMessageHandler(any(ListenerWrapper.class));
        verify(clientSession).start();
    }

}
