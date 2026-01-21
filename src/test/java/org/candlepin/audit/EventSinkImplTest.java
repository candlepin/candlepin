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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyByte;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.auth.Principal;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;


/**
 * Test suite for the EventSinkImpl class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EventSinkImplTest {
    @Mock
    private ClientSessionFactory mockSessionFactory;
    @Mock
    private ClientSession mockClientSession;
    @Mock
    private ClientProducer mockClientProducer;
    @Mock
    private ClientMessage mockClientMessage;
    @Mock
    private PrincipalProvider mockPrincipalProvider;
    @Mock
    private ServerLocator mockLocator;
    @Mock
    private CandlepinModeManager mockModeManager;

    private ConsumerTypeCurator mockConsumerTypeCurator;
    private EnvironmentCurator mockEnvironmentCurator;
    private OwnerCurator mockOwnerCurator;
    private ModelTranslator modelTranslator;

    private ActiveMQSessionFactory amqSessionFactory;
    private EventFactory factory;
    private EventFilter eventFilter;
    private EventSinkImpl eventSinkImpl;
    private Principal principal;
    private ObjectMapper mapper;
    private Owner o;

    @BeforeEach
    public void init() throws Exception {
        this.mapper = ObjectMapperFactory.getObjectMapper();
        this.principal = TestUtil.createOwnerPrincipal();
        when(mockPrincipalProvider.get()).thenReturn(this.principal);
        when(mockSessionFactory.createSession()).thenReturn(mockClientSession);
        when(mockClientSession.createProducer(anyString())).thenReturn(mockClientProducer);
        when(mockClientSession.createMessage(anyByte(), anyBoolean())).thenReturn(mockClientMessage);
        when(mockClientMessage.getBodyBuffer()).thenReturn(ActiveMQBuffers.fixedBuffer(2000));
        when(mockSessionFactory.getServerLocator()).thenReturn(mockLocator);
        doReturn(Mode.NORMAL).when(this.mockModeManager).getCurrentMode();

        this.amqSessionFactory = new TestingActiveMQSessionFactory(null, mockSessionFactory);
        this.mapper = spy(this.mapper);

        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockEnvironmentCurator = mock(EnvironmentCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.modelTranslator = new StandardTranslator(this.mockConsumerTypeCurator,
            this.mockEnvironmentCurator, this.mockOwnerCurator);

        this.factory = new EventFactory(mockPrincipalProvider, this.modelTranslator);
        this.eventFilter = new EventFilter(TestConfig.defaults());

        this.eventSinkImpl = createEventSink(mockSessionFactory);

        this.o = new Owner()
            .setId("test_owner")
            .setKey("test_owner")
            .setDisplayName("Test Owner");
    }

    /**
     * @throws Exception
     */
    private EventSinkImpl createEventSink(final ClientSessionFactory sessionFactory) throws Exception {
        EventSinkImpl sink = new EventSinkImpl(eventFilter, factory, mapper,
            TestConfig.defaults(), this.amqSessionFactory, mockModeManager);
        return sink;
    }

    @Test
    public void sendEventShouldSendMessageOnProperEventInput() throws Exception {
        String content = "Simple String";
        doReturn(content).when(mapper).writeValueAsString(any(Object.class));

        ArgumentCaptor<ClientMessage> argumentCaptor = ArgumentCaptor.forClass(ClientMessage.class);
        eventSinkImpl.queueEvent(mock(Event.class));
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(argumentCaptor.capture());

        ClientMessage message = argumentCaptor.getValue();
        assertNotNull(message);

        ActiveMQBuffer buffer = message.getBodyBuffer();
        assertNotNull(buffer);

        SimpleString sstr = buffer.readNullableSimpleString();
        assertNotNull(sstr);
        assertEquals(content, sstr.toString());
    }

    @Test
    public void sendEventShouldNotFailWhenObjectMapperThrowsException() throws Exception {
        doThrow(mock(StreamWriteException.class))
            .when(mapper).writeValueAsString(any());
        Event event = mock(Event.class);

        eventSinkImpl.queueEvent(event);
        verify(mockClientProducer, never()).send(any(ClientMessage.class));
    }

    @Test
    public void consumerCreatedShouldEmitSuccessfully() throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        eventSinkImpl.emitConsumerCreated(consumer);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void ownerCreatedShouldEmitSuccessfully() throws Exception {
        eventSinkImpl.emitOwnerCreated(o);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void poolCreatedShouldEmitSuccessfully() throws Exception {
        Pool pool = TestUtil.createPool(o, TestUtil.createProduct());

        eventSinkImpl.emitPoolCreated(pool);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void exportCreatedShouldEmitSuccessfully() throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        eventSinkImpl.emitExportCreated(consumer);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void importCreatedShouldEmitSuccessfully() throws Exception {
        Owner owner = new Owner()
            .setKey("Import guy")
            .setDisplayName("Import guy");

        eventSinkImpl.emitImportCreated(owner);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void emptyKeyShouldEmitSuccessfully() throws Exception {
        ActivationKey key = TestUtil.createActivationKey(this.o, null);
        eventSinkImpl.emitActivationKeyCreated(key);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void keyWithPoolsShouldEmitSuccessfully() throws Exception {
        Pool pool1 = TestUtil.createPool(this.o, TestUtil.createProduct());
        Pool pool2 = TestUtil.createPool(this.o, TestUtil.createProduct());

        ActivationKey key = TestUtil.createActivationKey(this.o, List.of(pool1, pool2));
        eventSinkImpl.emitActivationKeyCreated(key);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void rulesUpdatedShouldEmitSuccessfully() throws Exception {
        Rules oldRules = new Rules(TestUtil.createRulesBlob(1));
        Rules newRules = new Rules(TestUtil.createRulesBlob(2));
        eventSinkImpl.emitRulesModified(oldRules, newRules);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void rulesDeletedShouldEmitSuccessfully() throws Exception {
        Rules oldRules = new Rules(TestUtil.createRulesBlob(1));
        eventSinkImpl.emitRulesDeleted(oldRules);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

}
