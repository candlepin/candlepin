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

import static org.mockito.Mockito.*;

import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.auth.PrincipalData;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.quality.Strictness;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.io.StringWriter;
import java.util.stream.Stream;



/**
 * Test suite for the QpidEventMessageReceiver class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class QpidEventMessageReceiverTest {

    @Mock private ClientSessionFactory clientSessionFactory;
    @Mock private ClientSession clientSession;
    @Mock private ClientConsumer clientConsumer;
    @Mock private EventListener eventListener;
    @Mock private ClientMessage clientMessage;
    @Spy private ObjectMapper mapper = new ObjectMapper();
    @Spy private ActiveMQBuffer activeMQBuffer = ActiveMQBuffers.fixedBuffer(1000);

    private ActiveMQSessionFactory sessionFactory;
    private QpidEventMessageReceiver receiver;

    @BeforeEach
    public void init() throws Exception {
        when(clientMessage.getBodyBuffer()).thenReturn(activeMQBuffer);
        when(clientSessionFactory.createSession()).thenReturn(clientSession);
        when(clientSession.createConsumer(anyString())).thenReturn(clientConsumer);

        this.sessionFactory = new TestingActiveMQSessionFactory(clientSessionFactory, null);

        receiver = new QpidEventMessageReceiver(eventListener, this.sessionFactory, new ObjectMapper());
        // Calling connect will initialize the ClientSession
        receiver.connect();
    }

    private void primeBuffer(byte type, String value) {
        doReturn(type).when(this.clientMessage).getType();

        if (type == ClientMessage.TEXT_TYPE) {
            this.activeMQBuffer.writeNullableSimpleString(SimpleString.toSimpleString(value));
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
            Arguments.of(ClientMessage.TEXT_TYPE)
        );
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

        doThrow(new JsonMappingException("Induced exception"))
            .when(mapper).readValue(anyString(), eq(Event.class));

        receiver.onMessage(clientMessage);

        verify(clientMessage).acknowledge();
        verifyZeroInteractions(eventListener);
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void whenMsgAcknowledgeThrowsExceptionSessionIsRolledBack(byte msgType) throws Exception {
        this.primeBuffer(msgType, eventJson());

        doThrow(new ActiveMQException(ActiveMQExceptionType.DISCONNECTED, "Induced exception for testing"))
            .when(clientMessage).acknowledge();

        receiver.onMessage(clientMessage);

        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void whenProperClientMsgPassedThenOnMessageShouldSucceed(byte msgType) throws Exception {
        this.primeBuffer(msgType, eventJson());

        receiver.onMessage(clientMessage);

        verify(eventListener).onEvent(any(Event.class));
        verify(clientMessage).acknowledge();
        verify(clientSession).commit();
        verify(clientSession, never()).rollback();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void sessionIsRolledBackWhenAnyExceptionIsThrownFromEventListener(byte msgType) throws Exception {
        this.primeBuffer(msgType, eventJson());

        doThrow(new RuntimeException("Forced")).when(eventListener).onEvent(any(Event.class));

        receiver.onMessage(clientMessage);

        verify(clientMessage).acknowledge();
        verify(clientSession).rollback();
        verify(clientSession, never()).commit();
    }

    @ParameterizedTest
    @MethodSource("testMsgTypes")
    public void noRollbackOccursWhenQpidConnectionExceptionIsThrownFromListener(byte msgType)
        throws Exception {

        this.primeBuffer(msgType, eventJson());

        doThrow(new QpidConnectionException("Forced")).when(eventListener).onEvent(any(Event.class));

        receiver.onMessage(clientMessage);

        verify(clientMessage, never()).acknowledge();
        verify(clientSession, never()).rollback();
        verify(clientSession, never()).commit();
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
