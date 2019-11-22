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


import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.candlepin.auth.PrincipalData;
import org.candlepin.common.config.Configuration;
import org.candlepin.controller.ActiveMQStatusMonitor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.StringWriter;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EventMessageReceiverTest {

    @Mock
    private ClientSessionFactory clientSessionFactory;
    @Mock private ClientSession clientSession;
    @Mock private ClientConsumer clientConsumer;
    @Mock private EventListener eventListener;
    @Mock private ClientMessage clientMessage;
    @Mock private ActiveMQStatusMonitor monitor;
    @Mock private Configuration config;
    @Spy
    private ObjectMapper mapper = new ObjectMapper();
    @Spy private ActiveMQBuffer activeMQBuffer = ActiveMQBuffers.fixedBuffer(1000);

    private EventSourceConnection connection;
    private EventMessageReceiver receiver;

    private void setupValidSentMessage() throws Exception {
        byte[] sourceBytes = eventJson().getBytes();
        doReturn(sourceBytes.length).when(clientMessage).getBodySize();
        doAnswer(invocation -> {
            byte[] targetBytes = (byte[]) invocation.getArguments()[0];
            System.arraycopy(sourceBytes, 0, targetBytes, 0, sourceBytes.length);
            return null; // void method, so return null
        }).when(activeMQBuffer).readBytes(any(byte[].class));
    }

    @Before
    public void init() throws Exception {
        when(clientMessage.getBodyBuffer()).thenReturn(activeMQBuffer);
        when(clientSessionFactory.createSession(eq(false), eq(false), eq(0))).thenReturn(clientSession);
        when(clientSession.createConsumer(anyString())).thenReturn(clientConsumer);

        this.connection = new EventSourceConnection(monitor, config) {
            @Override
            ClientSessionFactory getFactory() {
                return clientSessionFactory;
            }
        };

        receiver = new EventMessageReceiver(eventListener, this.connection, mapper);
        receiver.connect();
    }

    @Test
    public void shouldCreateNewConsumer() throws Exception {
        verify(clientSession).createConsumer(anyString());
        verify(clientConsumer).setMessageHandler(eq(receiver));
        verify(clientSession).start();
    }

    @Test
    public void whenMapperReadThrowsExceptionThenMessageShouldBeAckedAndSessionRolledBack() throws Exception {
        doThrow(new JsonMappingException("Induced exception"))
            .when(mapper).readValue(anyString(), eq(Event.class));
        receiver.onMessage(clientMessage);
        verify(clientMessage).acknowledge();
        verifyZeroInteractions(eventListener);
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @Test
    public void whenMsgAcknowledgeThrowsExceptionSessionIsRolledBack()
        throws Exception {
        setupValidSentMessage();

        doThrow(new ActiveMQException(ActiveMQExceptionType.DISCONNECTED, "Induced exception for testing"))
            .when(clientMessage).acknowledge();
        receiver.onMessage(clientMessage);
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @Test
    public void whenProperClientMsgPassedThenOnMessageShouldSucceed()
        throws Exception {
        setupValidSentMessage();

        receiver.onMessage(clientMessage);
        verify(eventListener).onEvent(any(Event.class));
        verify(clientMessage).acknowledge();
        verify(clientSession).commit();
        verify(clientSession, never()).rollback();
    }

    @Test
    public void sessionIsRolledBackWhenAnyExceptionIsThrownFromEventListener() throws Exception {
        setupValidSentMessage();

        doThrow(new RuntimeException("Forced")).when(eventListener).onEvent(any(Event.class));
        receiver.onMessage(clientMessage);
        verify(clientMessage).acknowledge();
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @Test
    public void sessionCloseIgnoredIfSessionIsNull() throws Exception {
        EventMessageReceiver receiver = new EventMessageReceiver(eventListener, this.connection, mapper);
        receiver.close();
        verify(clientSession, never()).close();

        // Should never get called if the session is null
        verify(clientSession, never()).isClosed();
    }

    @Test
    public void sessionCloseIgnoredIfSessionIsAlreadyClosed() throws Exception {
        when(clientSession.isClosed()).thenReturn(true);
        receiver.close();

        verify(clientSession, never()).close();

        // Should get called if the session is not null
        verify(clientSession).isClosed();
    }

    private String eventJson() throws Exception {
        StringWriter sw = new StringWriter();
        Event e = new Event();
        e.setId("10");
        e.setConsumerUuid("20");
        e.setPrincipal(new PrincipalData("5678", "910112"));
        mapper.writeValue(sw, e);
        return sw.toString();
    }
}
