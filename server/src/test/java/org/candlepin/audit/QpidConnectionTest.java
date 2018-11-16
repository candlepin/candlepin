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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.qpid.client.AMQConnectionFactory;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
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
    }
}
