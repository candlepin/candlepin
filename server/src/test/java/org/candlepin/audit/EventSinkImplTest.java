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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import static org.mockito.Mockito.*;

import org.candlepin.auth.Principal;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.controller.ModeManager;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;

/**
 * EventSinkImplTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EventSinkImplTest {

    @Mock private ClientSessionFactory mockSessionFactory;
    @Mock private ClientSession mockClientSession;
    @Mock private ClientProducer mockClientProducer;
    @Mock private ClientMessage mockClientMessage;
    @Mock private PrincipalProvider mockPrincipalProvider;
    @Mock private ServerLocator mockLocator;
    @Mock private ModeManager mockModeManager;

    private EventSinkConnection eventSinkConnection;
    private EventFactory factory;
    private EventFilter eventFilter;
    private EventSinkImpl eventSinkImpl;
    private Principal principal;
    private ObjectMapper mapper;
    private Owner o;

    @Before
    public void init() throws Exception {
        this.factory = new EventFactory(mockPrincipalProvider, mapper);
        this.principal = TestUtil.createOwnerPrincipal();
        eventFilter = new EventFilter(new CandlepinCommonTestConfig());
        when(mockPrincipalProvider.get()).thenReturn(this.principal);
        when(mockSessionFactory.createTransactedSession()).thenReturn(mockClientSession);
        when(mockClientSession.createProducer(anyString())).thenReturn(mockClientProducer);
        when(mockClientSession.createMessage(anyBoolean())).thenReturn(mockClientMessage);
        when(mockClientMessage.getBodyBuffer()).thenReturn(
            ActiveMQBuffers.fixedBuffer(2000));
        when(mockSessionFactory.getServerLocator()).thenReturn(mockLocator);

        this.eventSinkConnection = new EventSinkConnection(mock(Configuration.class)) {

            @Override
            ClientSessionFactory getFactory() {
                return mockSessionFactory;
            }
        };

        this.mapper = spy(new ObjectMapper());
        this.eventSinkImpl = createEventSink(mockSessionFactory);
        o = new Owner("test owner");
    }

    /**
     * @return
     * @throws Exception
     */
    private EventSinkImpl createEventSink(final ClientSessionFactory sessionFactory) throws Exception {
        EventSinkImpl sink = new EventSinkImpl(eventFilter, factory, mapper,
            new CandlepinCommonTestConfig(), eventSinkConnection, mockModeManager);
        return sink;
    }

    /**Set up the {@link ClientSessionFactory} to throw an exception when
     * {@link ClientSessionFactory#createSession()} is called.
     * Make sure, we throw up our hands saying "I am not dealing with this".
     * @throws Exception
     */
    @Test(expected = RuntimeException.class)
    public void eventSinkShouldThrowExceptionWhenSessionCreationFailsInConstructor()
        throws Exception {
        final ClientSessionFactory csFactory = mock(ClientSessionFactory.class);
        doThrow(new ActiveMQException()).when(csFactory.createSession());
        createEventSink(csFactory);
        fail("Runtime exception should have been thrown.");
    }


    /**Set up the {@link ClientSession} to throw an exception when
     * {@link ClientSession#createProducer(String)} is called.
     * Make sure, we throw up our hands saying "I am not dealing with this".
     * @throws Exception
     */
    @Test(expected = RuntimeException.class)
    public void eventSinkShouldThrowExceptionWhenProducerCreationFailsInConstructor()
        throws Exception {
        doThrow(new ActiveMQException()).when(
            mockClientSession.createProducer(anyString()));
        createEventSink(mockSessionFactory);
        fail("Runtime exception should have been thrown.");
    }

    @Test
    public void sendEventShouldSendMessageOnProperEventInput() throws Exception {
        final String content = "Simple String";
        doReturn(content).when(mapper).writeValueAsString(anyObject());
        ArgumentCaptor<ClientMessage> argumentCaptor = ArgumentCaptor
            .forClass(ClientMessage.class);
        eventSinkImpl.queueEvent(mock(Event.class));
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(argumentCaptor.capture());
        assertEquals(content, argumentCaptor.getValue().getBodyBuffer()
            .readString());
    }

    @Test
    public void sendEventShouldNotFailWhenObjectMapperThrowsException()
        throws Exception {
        doThrow(new JsonGenerationException("Nothing serious!"))
            .when(mapper).writeValueAsString(any());
        Event event = mock(Event.class);

        eventSinkImpl.queueEvent(event);
        verify(mockClientProducer, never()).send(any(ClientMessage.class));
    }

    @Test
    public void consumerCreatedShouldEmitSuccessfully()
        throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        eventSinkImpl.emitConsumerCreated(consumer);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void ownerCreatedShouldEmitSuccessfully()
        throws Exception {
        eventSinkImpl.emitOwnerCreated(o);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void poolCreatedShouldEmitSuccessfully()
        throws Exception {
        Pool pool = TestUtil.createPool(o, TestUtil.createProduct());
        eventSinkImpl.emitPoolCreated(pool);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void exportCreatedShouldEmitSuccessfully()
        throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        eventSinkImpl.emitExportCreated(consumer);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void importCreatedShouldEmitSuccessfully()
        throws Exception {
        Owner owner = new Owner("Import guy");
        eventSinkImpl.emitImportCreated(owner);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void emptyKeyShouldEmitSuccessfully()
        throws Exception {
        ActivationKey key = TestUtil.createActivationKey(new Owner("deadbeef"), null);
        eventSinkImpl.emitActivationKeyCreated(key);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void keyWithPoolsShouldEmitSuccessfully()
        throws Exception {
        ArrayList<Pool> pools = new ArrayList<>();
        pools.add(TestUtil.createPool(o, TestUtil.createProduct()));
        pools.add(TestUtil.createPool(o, TestUtil.createProduct()));
        ActivationKey key = TestUtil.createActivationKey(new Owner("deadbeef"), pools);
        eventSinkImpl.emitActivationKeyCreated(key);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void rulesUpdatedShouldEmitSuccessfully()
        throws Exception {
        Rules oldRules = new Rules(TestUtil.createRulesBlob(1));
        Rules newRules = new Rules(TestUtil.createRulesBlob(2));
        eventSinkImpl.emitRulesModified(oldRules, newRules);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void rulesDeletedShouldEmitSuccessfully()
        throws Exception {
        Rules oldRules = new Rules(TestUtil.createRulesBlob(1));
        eventSinkImpl.emitRulesDeleted(oldRules);
        eventSinkImpl.sendEvents();
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

}
