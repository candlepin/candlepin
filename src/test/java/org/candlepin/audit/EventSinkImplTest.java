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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hornetq.api.core.HornetQBuffers;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.ServerLocator;
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
    private EventFactory factory;
    private EventSinkImpl eventSinkImpl;
    private Principal principal;
    private ObjectMapper mapper;

    @Before
    public void init() throws Exception {
        this.factory = new EventFactory(mockPrincipalProvider);
        this.principal = TestUtil.createOwnerPrincipal();
        when(mockPrincipalProvider.get()).thenReturn(this.principal);
        when(mockSessionFactory.createSession()).thenReturn(mockClientSession);
        when(mockClientSession.createProducer(anyString())).thenReturn(mockClientProducer);
        when(mockClientSession.createMessage(anyBoolean())).thenReturn(mockClientMessage);
        when(mockClientMessage.getBodyBuffer()).thenReturn(
            HornetQBuffers.fixedBuffer(2000));
        when(mockSessionFactory.getServerLocator()).thenReturn(mockLocator);
        this.mapper = spy(new ObjectMapper());
        this.eventSinkImpl = createEventSink(mockSessionFactory);
    }

    /**
     * @return
     */
    private EventSinkImpl createEventSink(final ClientSessionFactory sessionFactory) {
        return new EventSinkImpl(factory, mapper, new CandlepinCommonTestConfig()) {
            @Override
            protected ClientSessionFactory createClientSessionFactory() {
                return sessionFactory;
            }
        };
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
        doThrow(new HornetQException()).when(csFactory.createSession());
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
        doThrow(new HornetQException()).when(
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
        eventSinkImpl.sendEvent(mock(Event.class));
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

        eventSinkImpl.sendEvent(event);
        verify(mockClientProducer, never()).send(any(ClientMessage.class));
    }

    @Test
    public void consumerCreatedShouldEmitSuccessfully()
        throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        eventSinkImpl.emitConsumerCreated(consumer);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void ownerCreatedShouldEmitSuccessfully()
        throws Exception {
        Owner owner = new Owner("Test Owner ");
        eventSinkImpl.emitOwnerCreated(owner);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void poolCreatedShouldEmitSuccessfully()
        throws Exception {
        Pool pool = TestUtil.createPool(TestUtil.createProduct());
        eventSinkImpl.emitPoolCreated(pool);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void exportCreatedShouldEmitSuccessfully()
        throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        eventSinkImpl.emitExportCreated(consumer);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void importCreatedShouldEmitSuccessfully()
        throws Exception {
        Owner owner = new Owner("Import guy");
        eventSinkImpl.emitImportCreated(owner);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void emptyKeyShouldEmitSuccessfully()
        throws Exception {
        ActivationKey key = TestUtil.createActivationKey(new Owner("deadbeef"), null);
        eventSinkImpl.emitActivationKeyCreated(key);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void keyWithPoolsShouldEmitSuccessfully()
        throws Exception {
        ArrayList<Pool> pools = new ArrayList<Pool>();
        pools.add(TestUtil.createPool(TestUtil.createProduct()));
        pools.add(TestUtil.createPool(TestUtil.createProduct()));
        ActivationKey key = TestUtil.createActivationKey(new Owner("deadbeef"), pools);
        eventSinkImpl.emitActivationKeyCreated(key);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void rulesUpdatedShouldEmitSuccessfully()
        throws Exception {
        Rules oldRules = new Rules(TestUtil.createRulesBlob(1));
        Rules newRules = new Rules(TestUtil.createRulesBlob(2));
        eventSinkImpl.emitRulesModified(oldRules, newRules);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

    @Test
    public void rulesDeletedShouldEmitSuccessfully()
        throws Exception {
        Rules oldRules = new Rules(TestUtil.createRulesBlob(1));
        eventSinkImpl.emitRulesDeleted(oldRules);
        verify(mockClientProducer).send(any(ClientMessage.class));
    }

}
