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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.auth.PrincipalData;
import org.candlepin.config.Configuration;
import org.candlepin.controller.ActiveMQStatusMonitor;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;

import java.io.StringWriter;
import java.util.stream.Stream;


/**
 * Test suite for the DefaultEventMessageReceiverTest class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DefaultEventMessageReceiverTest {

    @Mock
    private ClientSessionFactory clientSessionFactory;
    @Mock
    private ClientSession clientSession;
    @Mock
    private ClientConsumer clientConsumer;
    @Mock
    private EventListener eventListener;
    @Mock
    private ClientMessage clientMessage;
    @Mock
    private ActiveMQStatusMonitor monitor;
    @Mock
    private Configuration config;
    @Spy
    private ObjectMapper mapper;
    @Spy
    private ActiveMQBuffer activeMQBuffer = ActiveMQBuffers.fixedBuffer(1000);

    private ActiveMQSessionFactory sessionFactory;
    private DefaultEventMessageReceiver receiver;

    @BeforeEach
    public void init() throws Exception {
        when(clientMessage.getBodyBuffer()).thenReturn(activeMQBuffer);
        when(clientSessionFactory.createSession()).thenReturn(clientSession);
        when(clientSession.createConsumer(anyString())).thenReturn(clientConsumer);

        this.sessionFactory = new TestingActiveMQSessionFactory(clientSessionFactory, null);
        receiver = new DefaultEventMessageReceiver(eventListener, this.sessionFactory, mapper);
        receiver.connect();
    }

    private void primeBuffer(byte type, String value) {
        doReturn(type).when(this.clientMessage).getType();

        if (type == ClientMessage.TEXT_TYPE) {
            this.activeMQBuffer.writeNullableSimpleString(SimpleString.of(value));
        }
        else {
            // Old method, injects extra sizes into the message which are not properly translated
            // for other protocols like STOMP.
            this.activeMQBuffer.writeString(value);
        }
    }

    public static Stream<Arguments> testMsgTypes() {
        return Stream.of(
            Arguments.of(ClientMessage.DEFAULT_TYPE),
            Arguments.of(ClientMessage.TEXT_TYPE));
    }

    @Test
    public void shouldCreateNewConsumer() throws Exception {
        verify(clientSession).createConsumer(anyString());
        verify(clientConsumer).setMessageHandler(eq(receiver));
        verify(clientSession).start();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void whenMapperReadThrowsExceptionThenMessageShouldBeAckedAndSessionRolledBack(byte msgType)
        throws Exception {

        this.primeBuffer(msgType, "test123");

        doThrow(mock(DatabindException.class))
            .when(mapper).readValue(anyString(), eq(Event.class));
        receiver.onMessage(clientMessage);
        verify(clientMessage).acknowledge();
        verifyNoInteractions(eventListener);
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void whenMsgAcknowledgeThrowsExceptionSessionIsRolledBack(byte msgType) throws Exception {
        this.primeBuffer(msgType, this.eventJson());

        doThrow(new ActiveMQException(ActiveMQExceptionType.DISCONNECTED, "Induced exception for testing"))
            .when(clientMessage).acknowledge();
        receiver.onMessage(clientMessage);
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void whenProperClientMsgPassedThenOnMessageShouldSucceed(byte msgType) throws Exception {
        this.primeBuffer(msgType, this.eventJson());

        receiver.onMessage(clientMessage);
        verify(eventListener).onEvent(any(Event.class));
        verify(clientMessage).acknowledge();
        verify(clientSession).commit();
        verify(clientSession, never()).rollback();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void sessionIsRolledBackWhenAnyExceptionIsThrownFromEventListener(byte msgType) throws Exception {
        this.primeBuffer(msgType, this.eventJson());

        doThrow(new RuntimeException("Forced")).when(eventListener).onEvent(any(Event.class));
        receiver.onMessage(clientMessage);
        verify(clientMessage).acknowledge();
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @Test
    public void sessionCloseIgnoredIfSessionIsNull() throws Exception {
        DefaultEventMessageReceiver receiver = new DefaultEventMessageReceiver(eventListener,
            this.sessionFactory, mapper);
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
        Event e = new Event(Type.MODIFIED, Target.CONSUMER, new PrincipalData("5678", "910112"))
            .setConsumerUuid("20");
        mapper.writeValue(sw, e);
        return sw.toString();
    }
}
