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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.MessageConsumer;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.BrokerDetails;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

/**
 * EventSource
 */
public class EventSource {
    private static  Logger log = LoggerFactory.getLogger(EventSource.class);
    static final String QUEUE_PREFIX = "event";
    private QueueConnection connection;
    private QueueSession session;
    private AMQConnectionFactory connectionFactory;
    private ObjectMapper mapper;
    private Configuration config;

    private Map<String, String> targetToEvent = new HashMap<String, String>() {
        private static final long serialVersionUID = 2L;
        {
            this.put(Event.Target.SUBSCRIPTION.toString().toLowerCase(), "product");
            // add more mappings when necessary
        }
    };
    
    @Inject
    public EventSource(ObjectMapper mapper) {
        this.mapper = mapper;

        try {
            connectionFactory =  createSessionFactory();
            // Specify a message ack batch size of 0 to have hornetq immediately ack
            // any message successfully received with the server. Not doing so can lead
            // to duplicate messages if the server goes down before the batch ack size is
            // reached.
            connection = connectionFactory.createQueueConnection();
            connection.start();
            session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return new instance of {@link ClientSessionFactory}
     * @throws Exception
     */
    protected AMQConnectionFactory createSessionFactory() throws Exception {
        return createConnectionFactory(new InitialContext(buildConfigurationProperties(config)), config);
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
    private String getTopicName(Type type, Target target) {
        return target.toString().toLowerCase() +
            Util.capitalize(type.toString().toLowerCase());
    }

    private String getDestination(Type type, Target target) {
        String key = target.toString().toLowerCase();
        String object = targetToEvent.get(key);
        return (object == null ? key : object) + "." + type.toString().toLowerCase();
    }
    private AMQConnectionFactory createConnectionFactory(Context ctx, Configuration config)
            throws NamingException {
            log.debug("looking up qpidConnectionfactory");

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
                log.debug("Broker configured: " + broker);
            }
            log.info("AMQP connection factory created.");
            return connectionFactory;
        }
    protected void shutDown() {
        try {
            connection.stop();
            connection.close();
        }
        catch (Exception e) {
            log.warn("Exception while trying to shutdown hornetq", e);
        }
    }

    void registerListener(EventListener listener) {
        String queueName = QUEUE_PREFIX + "." + listener.getClass().getCanonicalName();
        log.info("registering listener for " + queueName);
        try {
            //Queue must be created! Alternatively we can create it with JMS api that maps to
            //a creation of AMQP queue
            MessageConsumer consumer = session.createConsumer(new AMQQueue(queueName));
            consumer.setMessageListener(new ListenerWrapper(listener, mapper));
        }
        catch (Exception e) {
            log.error("Unable to register listener :" + listener, e);
        }
    }
}
