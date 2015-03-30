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
package org.candlepin.guice;

import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.jms.BrokerDetails;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * A provider that creates and configures AMQPBusPublishers.
 */
public class AMQPBusPubProvider implements Provider<AMQPBusPublisher> {

    private Context ctx;
    private TopicConnection connection;
    private TopicSession session;
    private static org.slf4j.Logger log = LoggerFactory.getLogger(AMQPBusPubProvider.class);
    private ObjectMapper mapper;

    // external events may not have the same name as the internal events
    private Map<String, String> targetToEvent = new HashMap<String, String>() {
        private static final long serialVersionUID = 2L;
        {
            this.put(Event.Target.SUBSCRIPTION.toString().toLowerCase(), "product");
            // add more mappings when necessary
        }
    };

    @Inject
    public AMQPBusPubProvider(Configuration config, ObjectMapper omapper) {
        try {
            mapper = omapper;
            log.info("building initialcontext");
            ctx = new InitialContext(buildConfigurationProperties(config));
            init(config);
        }
        catch (Exception ex) {
            log.error("Unable to instantiate AMQPBusProvider: ", ex);
            throw new RuntimeException(ex);
        }
    }

    private void init(Configuration config) throws Exception  {
        AMQConnectionFactory connectionFactory = createConnectionFactory(ctx, config);
        log.info("creating connection");
        connection = (TopicConnection) connectionFactory.createConnection();
        connection.start();

        log.info("creating topic session");
        session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        log.info("AMQP session created successfully...");
    }

    private AMQConnectionFactory createConnectionFactory(Context ctx, Configuration config)
        throws NamingException {
        log.info("looking up qpidConnectionfactory");

        int maxRetries = config.getInt(ConfigProperties.AMQP_CONNECTION_RETRY_ATTEMPTS);
        long waitTimeInSeconds = config.getLong(ConfigProperties.AMQP_CONNECTION_RETRY_INTERVAL);

        AMQConnectionFactory connectionFactory = (AMQConnectionFactory) ctx.lookup("qpidConnectionfactory");
        for (BrokerDetails broker : connectionFactory.getConnectionURL().getAllBrokerDetails()) {
            broker.setProperty("trust_store",
                config.getString(ConfigProperties.AMQP_TRUSTSTORE));
            broker.setProperty("trust_store_password",
                config.getString(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD));
            broker.setProperty("key_store", config.getString(ConfigProperties.AMQP_KEYSTORE));
            broker.setProperty("key_store_password",
                config.getString(ConfigProperties.AMQP_KEYSTORE_PASSWORD));

            // It is important that broker urls are configured with retries and connection
            // delays to help avoid issues when the qpidd connection is lost. Candlepin
            // will set defaults, or configured value automatically if they are not
            // specified in the broker urls.
            if (broker.getProperty("retries") == null) {
                broker.setProperty("retries", Integer.toString(maxRetries));
            }

            if (broker.getProperty("connectdelay") == null) {
                long delay = 1000 * waitTimeInSeconds;
                broker.setProperty("connectdelay", Long.toString(delay));
            }
            log.info("Broker configured: " + broker);
        }
        log.info("AMQP connection factory created.");
        return connectionFactory;
    }

    /**
     * @return A Properties object containing the amqp configuration for jms
     */
    private Properties buildConfigurationProperties(Configuration config) {
        Properties properties = new Properties();

        properties.put("java.naming.factory.initial",
            "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        properties.put("connectionfactory.qpidConnectionfactory",
            "amqp://guest:guest@localhost/test?sync_publish='persistent'&brokerlist='" +
            config.getString(ConfigProperties.AMQP_CONNECT_STRING) + "'");

        for (Target target : Target.values()) {
            for (Type type : Type.values()) {
                // topic name is the internal key used to find the
                // AMQP topic.
                String name = getTopicName(type, target);

                // this represents the destination
                String destination = getDestination(type, target);
                properties.put("destination." + name, "event/" + destination);
            }
        }

        log.debug("Properties: " + properties);

        return properties;
    }

    @Override
    public AMQPBusPublisher get() {
        try {
            // build a map of publishers for each of combination of
            // target.type. So there will be one for owner.created,
            // another for pool.deleted, etc.

            Map<Target, Map<Type, TopicPublisher>> pm = Util.newMap();
            buildAllTopicPublishers(pm);
            return new AMQPBusPublisher(session, pm, mapper);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void close() {
        Util.closeSafely(this.session, "AMQPSession");
        Util.closeSafely(this.connection, "AMQPConnection");
        Util.closeSafely(this.ctx, "AMQPContext");
    }

    protected final void buildAllTopicPublishers(Map<Target, Map<Type, TopicPublisher>> pm)
        throws JMSException, NamingException {

        for (Target target : Target.values()) {
            Map<Type, TopicPublisher> typeToTpMap = Util.newMap();
            for (Type type : Type.values()) {
                storeTopicProducer(type, target, typeToTpMap);
            }
            pm.put(target, typeToTpMap);
        }
    }

    protected final void storeTopicProducer(Type type, Target target,
        Map<Type, TopicPublisher> map) throws JMSException, NamingException {

        String name = getTopicName(type, target);
        Topic topic = (Topic) this.ctx.lookup(name);
        log.debug("Creating publisher for topic: {}", name);
        TopicPublisher tp = this.session.createPublisher(topic);
        map.put(type, tp);
    }

    private String getTopicName(Type type, Target target) {
        return target.toString().toLowerCase() +
            Util.capitalize(type.toString().toLowerCase());
    }

    private String getDestination(Type type, Target target) {
        String key = target.toString().toLowerCase();
        String object = targetToEvent.get(key);
        return (object == null ? key : object) + "." + type.toString().toLowerCase();
    }
}
