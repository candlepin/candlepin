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


import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.config.Configuration;
import org.candlepin.controller.QpidStatusListener;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.jms.BrokerDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Connection to a Qpid Broker. It can be in two states: CONNECTED or DOWN.
 * The class has ability to reconnect to the broker.
 *
 * This class also orchestrates all the initial configuration of the
 * Qpid connection
 * @author fnguyen
 *
 */
public class QpidConnection implements QpidStatusListener {

    private static Logger log = LoggerFactory.getLogger(QpidConnection.class);

    /**
     * This connection factory is created only once upon startup,
     * it is configured using many options that we also allow user
     * to override in candlepin.conf
     */
    private AMQConnectionFactory connectionFactory;
    /**
     * Candlepin holds only one connection to the Qpid broker. It may be
     * closed in case of network or qpid server issues. In that case,
     * the client must use connect() method to try establish new connection
     */
    private Connection connection;
    /**
     * A Topic session is associated with connection, when the connection is
     * recreated, so must be the TopicSession
     */
    private TopicSession session;
    /**
     * For each combination of target (Consumer, Pool, etc) and type of event
     * (CREATED, DELETED, ...) we have a special TopicPublisher. The reason we have
     * one publisher for every combo is that we want the events to end up in
     * event exchange under specific routing key. The qpid jms client allows us to
     * do this using TopicPublisher
     */
    private Map<Target, Map<Type, TopicPublisher>> producerMap;
    private InitialContext ctx = null;
    private QpidConfigBuilder config;
    private Configuration candlepinConfig;

    protected boolean isFlowStopped = false;

    /**
     * This class is a singleton, just in case that multiple threads
     * try to reconnect concurrently, we want to shield ourselves
     */
    private static Object connectionLock = new Object();

    @Inject
    public QpidConnection(QpidConfigBuilder config, Configuration candlepinConfiguration) {
        try {
            this.config = config;
            this.candlepinConfig = candlepinConfiguration;
            ctx = createInitialContext();
            connectionFactory = createConnectionFactory();
        }
        catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends a text message to a Qpid Broker. The message will be sent with a binding key that is
     * appropriate to the provided Target and Type enumerations. One example one such binding key
     * is CONSUMER.CREATED. This further allows Qpid clients to filter out events that
     * they are interested in. The reason that for each binding key we have separate
     * TopicPublisher is an implementation detail of the Qpid java client.
     * @param target enumeration
     * @param type enumeration
     * @param msg Usually contains serialized JSON with the message
     * @throws Exception
     */
    public void sendTextMessage(Target target, Type type, String msg) {
        // Don't bother to try and send the message if we know the connection
        // became unavailable or if the queue is FLOW_STOPPED. Throw and exception
        // and let HornetQ attempt to resend it later.
        if (connection == null) {
            throw new RuntimeException("Message not sent: No connection to Qpid.");
        }

        if (this.isFlowStopped) {
            throw new RuntimeException("Message not sent: Qpid queue is FLOW_STOPPED.");
        }

        try {
            log.debug("Sending message to Qpid - {}:{}", target, type);
            Map<Type, TopicPublisher> m = this.producerMap.get(target);
            if (m != null) {
                TopicPublisher tp = m.get(type);
                tp.send(session.createTextMessage(msg));
            }
        }
        catch (Exception ex) {
            throw new RuntimeException("Error sending event to Qpid message bus", ex);
        }
    }

    /**
     * This idempotent method will establish connection to Qpid Broker. Per JMS standard
     * it must recreate all JMS objects such as Connection, TopicSession, TopicPublisher.
     * @throws Exception errors during connecting to the Broker
     */
    public void connect() throws Exception {
        synchronized (connectionLock) {
            connection = newConnection();
            log.debug("creating topic session");
            session = createTopicSession();
            log.info("AMQP session created successfully...");
            Map<Target, Map<Type, TopicPublisher>> pm = new HashMap<>();
            buildAllTopicPublishers(pm);
            producerMap = pm;
        }

    }

    /**
     * Creates a new connection to Qpid and starts the connection
     * @return normal JMS Connection object
     * @throws JMSException possibly when Broker is down
     */
    public Connection newConnection() throws JMSException {
        log.debug("creating connection");
        Connection conn = null;
        conn = connectionFactory.createConnection();
        conn.start();
        return conn;
    }

    /**
     * Closes off all the resources held
     */
    public void close() {
        closeConnection();
        Util.closeSafely(this.ctx, "AMQPContext");
        Util.closeSafely(this.connectionFactory, "AMQPConnectionFactory");
    }

    protected void closeConnection() {
        for (Entry<Target, Map<Type, TopicPublisher>> entry : this.producerMap.entrySet()) {
            for (Entry<Type, TopicPublisher> tpMap : entry.getValue().entrySet()) {
                Util.closeSafely(tpMap.getValue(),
                    String.format("TopicPublisherOf[%s, %s]", entry.getKey(), tpMap.getKey()));
            }
        }
        Util.closeSafely(this.session, "AMQPSession");
        Util.closeSafely(this.connection, "AMQPConnection");
        this.connection = null;
    }

    protected InitialContext createInitialContext() throws NamingException {
        return new InitialContext(config.buildConfigurationProperties());
    }

    protected AMQConnectionFactory createConnectionFactory()
        throws NamingException {
        log.debug("looking up QpidConnectionfactory");

        AMQConnectionFactory connectionFactory = (AMQConnectionFactory) ctx.lookup("qpidConnectionfactory");

        Map<String, String> configProperties = config.buildBrokerDetails(ctx);

        for (BrokerDetails broker : connectionFactory.getConnectionURL().getAllBrokerDetails()) {
            for (Entry<String, String> prop : configProperties.entrySet()) {
                // It is important that broker urls are configured with retries and connection
                // delays to help avoid issues when the qpidd connection is lost. Candlepin
                // will set defaults, or configured value automatically if they are not
                // specified in the broker urls.
                if (prop.getKey().equals("retries") ||
                    prop.getKey().equals("connectdelay")) {
                    if (broker.getProperty(prop.getKey()) != null) {
                        continue;
                    }
                }
                broker.setProperty(prop.getKey(), prop.getValue());
            }
            log.debug("Broker configured: " + broker);
        }
        log.info("AMQP connection factory created.");
        return connectionFactory;
    }

    /**
     * Creates new topic session on this connection. It is important to understand that when
     * Connection to Qpid fails, we need to reestablish all the JMS objects, as per JMS
     * specification.
     * @return TopicSession for the connection
     * @throws JMSException possibly when connection to Qpid is down
     */
    public TopicSession createTopicSession() throws JMSException {
        return ((TopicConnection) connection).createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * Qpid JMS client requires JNDI. It runs it internally, but we have to use it for both
     * configuration and lookup (inconvenient, but necessity)
     * @param name Name of the topic that was previously created using QpidConfigBuilder
     * @return The topic configured
     * @throws NamingException when nothing is found in the JNDI
     */
    public Topic lookupTopic(String name) throws NamingException {
        return (Topic) ctx.lookup(name);
    }

    /**
     * Called each time the QpidStatusMonitor checks for a Qpid status update,
     * and based on this change, updates the connection.
     *
     * @param oldStatus the status of Qpid on the previous update.
     * @param newStatus the current status of Qpid.
     */
    @Override
    public void onStatusUpdate(QpidStatus oldStatus, QpidStatus newStatus) {
        // When the status changes to CONNECTED, rebuild the connection to Qpid.
        // Since the connection went down, the JMS objects are stale, it is necessary
        // to recreate them.
        //
        // NOTE: We do not shut down the connection when FLOW_STOPPED is detected as there is no
        //       need to. Message sends are just blocked in that case as the connection is fine.
        if (QpidStatus.CONNECTED.equals(newStatus) && QpidStatus.DOWN.equals(oldStatus)) {
            log.info("Attempting to connect to QPID");
            try {
                connect();
            }
            catch (Exception e) {
                throw new RuntimeException("Unable to connect to Qpid.", e);
            }
        }
        else if (QpidStatus.DOWN.equals(newStatus) && !QpidStatus.DOWN.equals(oldStatus)) {
            // If the connection changes to DOWN, close the existing connection to
            // ensure that we don't leave a stale one open.
            log.debug("Connection to Qpid was lost. Closing current connection.");
            closeConnection();
        }

        // Qpid queue is in flow_stopped and will not accept any new messages
        // until it catches up. Set the state so that we can use it to prevent
        // sending messages until the state goes back to connected.
        this.isFlowStopped = QpidStatus.FLOW_STOPPED.equals(newStatus);
        log.debug("Qpid is flow stopped: {}", this.isFlowStopped);
    }

    /**
     * We create all the topic publishers in advance and reuse them for sending.
     * @param pm
     * @throws JMSException
     * @throws NamingException
     */
    private void buildAllTopicPublishers(Map<Target, Map<Type, TopicPublisher>> pm)
        throws JMSException, NamingException {

        for (Target target : Target.values()) {
            Map<Type, TopicPublisher> typeToTpMap = new HashMap<>();
            for (Type type : Type.values()) {
                storeTopicProducer(type, target, typeToTpMap);
            }
            pm.put(target, typeToTpMap);
        }
    }

    private void storeTopicProducer(Type type, Target target, Map<Type, TopicPublisher> map)
        throws JMSException, NamingException {

        String name = config.getTopicName(type, target);
        Topic topic = lookupTopic(name);
        log.debug("Creating publisher for topic: {}", name);
        TopicPublisher tp = this.session.createPublisher(topic);
        map.put(type, tp);
    }
}
