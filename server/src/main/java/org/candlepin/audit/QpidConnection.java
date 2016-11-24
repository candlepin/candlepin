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
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ModeManager;
import org.candlepin.controller.SuspendModeTransitioner;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.jms.BrokerDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class QpidConnection {
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
    private static Logger log = LoggerFactory.getLogger(QpidConnection.class);
    private InitialContext ctx = null;
    private STATUS connectionStatus = STATUS.JMS_OBJECTS_STALE;
    private QpidConfigBuilder config;
    private SuspendModeTransitioner modeTransitioner;
    private ModeManager modeManager;
    private Configuration candlepinConfig;

    /**
     * This class is a singleton, just in case that multiple threads
     * try to reconnect concurrently, we want to shield ourselves
     */
    private static Object connectionLock = new Object();

    /**
     * Status of the connection as Candlepin sees it
     * @author fnguyen
     *
     */
    public enum STATUS {
        CONNECTED,
        /**
         * Represents situation when connection to Qpid was disrupted.
         * JMS objects becomes stale and need to be recreated as per
         * JMS specification
         */
        JMS_OBJECTS_STALE
    }

    public void setConnectionStatus(STATUS connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    @Inject
    public QpidConnection(QpidConfigBuilder config, SuspendModeTransitioner modeTransitioner,
        ModeManager modeManager, Configuration candlepinConfiguration) {
        try {
            this.config = config;
            this.modeTransitioner = modeTransitioner;
            this.modeManager = modeManager;
            this.candlepinConfig = candlepinConfiguration;
            ctx = new InitialContext(config.buildConfigurationProperties());
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
        try {
            /**
             * When Candlepin is in NORMAL mode and at the same time the
             * JMS objects are stale, it is necessary to recreate them.
             */
            if (connectionStatus == STATUS.JMS_OBJECTS_STALE &&
                modeManager.getLastCandlepinModeChange().getMode() == Mode.NORMAL) {
                log.debug("Recreating the stale JMS objects");
                connect();
            }

            Map<Type, TopicPublisher> m = this.producerMap.get(target);
            if (m != null) {
                TopicPublisher tp = m.get(type);
                tp.send(session.createTextMessage(msg));
            }
        }
        catch (Exception ex) {
            log.error("Error sending text message");
            connectionStatus = STATUS.JMS_OBJECTS_STALE;
            if (!candlepinConfig
                .getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
                modeTransitioner.transitionAppropriately();
            }

            throw new RuntimeException("Error sending event to message bus", ex);
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
            Map<Target, Map<Type, TopicPublisher>> pm = Util.newMap();
            buildAllTopicPublishers(pm);
            producerMap = pm;
            connectionStatus = STATUS.CONNECTED;
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
        connectionStatus = STATUS.JMS_OBJECTS_STALE;

        for (Entry<Target, Map<Type, TopicPublisher>> entry : this.producerMap.entrySet()) {
            for (Entry<Type, TopicPublisher> tpMap : entry.getValue().entrySet()) {
                Util.closeSafely(tpMap.getValue(),
                    String.format("TopicPublisherOf[%s, %s]", entry.getKey(), tpMap.getKey()));
            }
        }
        Util.closeSafely(this.session, "AMQPSession");
        Util.closeSafely(this.connection, "AMQPConnection");
        Util.closeSafely(this.ctx, "AMQPContext");
        Util.closeSafely(this.connectionFactory, "AMQPConnection");
    }

    private AMQConnectionFactory createConnectionFactory()
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
     * We create all the topic publishers in advance and reuse them for sending.
     * @param pm
     * @throws JMSException
     * @throws NamingException
     */
    private void buildAllTopicPublishers(Map<Target, Map<Type, TopicPublisher>> pm)
        throws JMSException, NamingException {

        for (Target target : Target.values()) {
            Map<Type, TopicPublisher> typeToTpMap = Util.newMap();
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
