/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;

import org.apache.qpid.client.AMQConnectionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class QpidConnectionTest {

    private Connection qpidConnection;
    private AMQConnectionFactory connectionFactory;
    private TopicSession topicSession;
    private InitialContext initialContext;

    private TestingQpidConnection connection;

    @Before
    public void setupTest() throws Exception {
        qpidConnection = mock(Connection.class);
        connectionFactory = mock(AMQConnectionFactory.class);
        topicSession = mock(TopicSession.class);
        initialContext = mock(InitialContext.class);
        connection = spy(new TestingQpidConnection(new MapConfiguration()));

        when(initialContext.lookup(anyString())).thenReturn(mock(Topic.class));
    }

    @Test
    public void throwsExceptionOnMessageSendAndConnectionIsNull() {
        // Without an explicit call to connect() the connection is null.
        try {
            connection.sendTextMessage(Event.Target.CONSUMER, Event.Type.CREATED, "test");
            fail("Expected a QpidConnectionException to be thrown!");
        }
        catch (QpidConnectionException e) {
            assertEquals("Message not sent: No connection to Qpid.", e.getMessage());
        }
    }

    @Test
    public void throwsExceptionOnMessageSendWhenFlowStopped() throws Exception {
        // Make sure that the connection is connected.
        connection.connect();
        // Put the connection into FLOW_STOPPED.
        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.FLOW_STOPPED);
        assertTrue(connection.isFlowStopped());

        try {
            connection.sendTextMessage(Event.Target.CONSUMER, Event.Type.CREATED, "test");
            fail("Expected a QpidConnectionException to be thrown!");
        }
        catch (QpidConnectionException e) {
            assertEquals("Message not sent: Qpid queue is FLOW_STOPPED.", e.getMessage());
        }
    }

    @Test
    public void connectionIsMadeWhenUpdatingFromUnknownState() throws Exception {
        connection.onStatusUpdate(QpidStatus.UNKNOWN, QpidStatus.CONNECTED);

        verify(connection, never()).closeConnection();
        verify(connection, never()).close();
        verify(connection, times(1)).connect();
    }

    @Test
    public void connectionIsReconnectedWhenQpidConnectedStatusIsReportedAndQpidWasPreviouslyDown()
        throws Exception {
        connection.onStatusUpdate(QpidStatus.DOWN, QpidStatus.CONNECTED);

        verify(connection, never()).closeConnection();
        verify(connection, never()).close();
        verify(connection, times(1)).connect();
    }

    @Test
    public void connectionIsReconnectedWhenQpidConnectedStatusIsReportedAndQpidHadNoExchange()
        throws Exception {
        connection.onStatusUpdate(QpidStatus.MISSING_EXCHANGE, QpidStatus.CONNECTED);

        verify(connection, never()).closeConnection();
        verify(connection, never()).close();
        verify(connection, times(1)).connect();
    }

    @Test
    public void connectionIsReconnectedWhenQpidConnectedStatusIsReportedAndQpidHadNoExchangeBinding()
        throws Exception {
        connection.onStatusUpdate(QpidStatus.MISSING_BINDING, QpidStatus.CONNECTED);

        verify(connection, never()).closeConnection();
        verify(connection, never()).close();
        verify(connection, times(1)).connect();
    }

    @Test
    public void connectedToFlowStoppedDoesNotRequireDisconnect() throws Exception {
        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.FLOW_STOPPED);

        verify(connection, never()).closeConnection();
        verify(connection, never()).close();
        verify(connection, never()).connect();
    }

    @Test
    public void flowStoppedToConnectedDoesNotRequireReconnect()
        throws Exception {
        connection.onStatusUpdate(QpidStatus.FLOW_STOPPED, QpidStatus.CONNECTED);

        verify(connection, never()).closeConnection();
        verify(connection, never()).close();
        verify(connection, never()).connect();
    }

    @Test
    public void checkFlowStopValueOnStateChange() throws Exception {
        // Force connection so that the producer map is initialized.
        connection.connect();

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.CONNECTED);
        assertFalse(connection.isFlowStopped());

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.FLOW_STOPPED);
        assertTrue(connection.isFlowStopped());

        connection.onStatusUpdate(QpidStatus.FLOW_STOPPED, QpidStatus.FLOW_STOPPED);
        assertTrue(connection.isFlowStopped());

        connection.onStatusUpdate(QpidStatus.FLOW_STOPPED, QpidStatus.CONNECTED);
        assertFalse(connection.isFlowStopped());

        connection.onStatusUpdate(QpidStatus.FLOW_STOPPED, QpidStatus.DOWN);
        assertFalse(connection.isFlowStopped());

        connection.onStatusUpdate(QpidStatus.DOWN, QpidStatus.FLOW_STOPPED);
        assertTrue(connection.isFlowStopped());

        connection.onStatusUpdate(QpidStatus.FLOW_STOPPED, QpidStatus.MISSING_EXCHANGE);
        assertFalse(connection.isFlowStopped());

        connection.onStatusUpdate(QpidStatus.FLOW_STOPPED, QpidStatus.MISSING_BINDING);
        assertFalse(connection.isFlowStopped());
    }

    @Test
    public void checkMissingExchangeValueOnStateChange() throws Exception {
        // Force connection so that the producer map is initialized.
        connection.connect();

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.CONNECTED);
        assertFalse(connection.isMissingExchange());

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.MISSING_EXCHANGE);
        assertTrue(connection.isMissingExchange());

        connection.onStatusUpdate(QpidStatus.MISSING_EXCHANGE, QpidStatus.MISSING_EXCHANGE);
        assertTrue(connection.isMissingExchange());

        connection.onStatusUpdate(QpidStatus.MISSING_EXCHANGE, QpidStatus.CONNECTED);
        assertFalse(connection.isMissingExchange());

        connection.onStatusUpdate(QpidStatus.MISSING_EXCHANGE, QpidStatus.DOWN);
        assertFalse(connection.isMissingExchange());

        connection.onStatusUpdate(QpidStatus.DOWN, QpidStatus.MISSING_EXCHANGE);
        assertTrue(connection.isMissingExchange());

        connection.onStatusUpdate(QpidStatus.MISSING_EXCHANGE, QpidStatus.FLOW_STOPPED);
        assertFalse(connection.isMissingExchange());

        connection.onStatusUpdate(QpidStatus.MISSING_EXCHANGE, QpidStatus.MISSING_BINDING);
        assertFalse(connection.isMissingExchange());
    }

    @Test
    public void checkMissingBindingValueOnStateChange() throws Exception {
        // Force connection so that the producer map is initialized.
        connection.connect();

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.CONNECTED);
        assertFalse(connection.isMissingBinding());

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.MISSING_BINDING);
        assertTrue(connection.isMissingBinding());

        connection.onStatusUpdate(QpidStatus.MISSING_BINDING, QpidStatus.MISSING_BINDING);
        assertTrue(connection.isMissingBinding());

        connection.onStatusUpdate(QpidStatus.MISSING_BINDING, QpidStatus.CONNECTED);
        assertFalse(connection.isMissingBinding());

        connection.onStatusUpdate(QpidStatus.MISSING_BINDING, QpidStatus.DOWN);
        assertFalse(connection.isMissingBinding());

        connection.onStatusUpdate(QpidStatus.DOWN, QpidStatus.MISSING_BINDING);
        assertTrue(connection.isMissingBinding());

        connection.onStatusUpdate(QpidStatus.MISSING_BINDING, QpidStatus.FLOW_STOPPED);
        assertFalse(connection.isMissingBinding());

        connection.onStatusUpdate(QpidStatus.MISSING_BINDING, QpidStatus.MISSING_EXCHANGE);
        assertFalse(connection.isMissingBinding());
    }

    @Test
    public void connectionIsClosedWhenQpidDisconnectIsReported() throws Exception {
        // Force connection so that the producer map is initialized.
        connection.connect();

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.DOWN);

        verify(connection).closeConnection();
        // Never want to call close() as it shuts down the context.
        verify(connection, never()).close();

        // Account for the forced connect call above.
        verify(connection, atMost(1)).connect();
    }

    @Test
    public void connectionIsClosedWhenExchangeIsLost()
        throws Exception {
        // Force connection so that the producer map is initialized.
        connection.connect();

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.MISSING_EXCHANGE);

        // Initial connection.
        verify(connection, times(1)).connect();
        // Expect the connection to be closed.
        verify(connection, times(1)).closeConnection();
        // Don't expect a hard close where the session factory is also closed.
        verify(connection, never()).close();
    }

    @Test
    public void connectionIsClosedWhenBindingIsMissing()
        throws Exception {
        // Force connection so that the producer map is initialized.
        connection.connect();

        connection.onStatusUpdate(QpidStatus.CONNECTED, QpidStatus.MISSING_BINDING);

        // Initial connection.
        verify(connection, times(1)).connect();
        // Expect the connection to be closed.
        verify(connection, times(1)).closeConnection();
        // Don't expect a hard close where the session factory is also closed.
        verify(connection, never()).close();
    }

    /**
     * A stubbed test class to avoid trying to configure and make an actual
     * connection to Qpid.
     */
    private class TestingQpidConnection extends QpidConnection {

        public TestingQpidConnection(Configuration cpConfig) {
            super(new QpidConfigBuilder(cpConfig), cpConfig);
        }

        @Override
        public Connection newConnection() throws JMSException {
            return qpidConnection;
        }

        @Override
        protected InitialContext createInitialContext() throws NamingException {
            return initialContext;
        }

        @Override
        protected AMQConnectionFactory createConnectionFactory() throws NamingException {
            return connectionFactory;
        }

        @Override
        public TopicSession createTopicSession() throws JMSException {
            return topicSession;
        }

        public boolean isFlowStopped() {
            return this.isFlowStopped;
        }

        public boolean isMissingExchange() {
            return this.exchangeMissing;
        }

        public boolean isMissingBinding() {
            return this.isMissingBinding;
        }
    }
}
