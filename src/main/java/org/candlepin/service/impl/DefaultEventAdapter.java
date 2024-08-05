package org.candlepin.service.impl;

import java.time.Duration;
import java.time.Instant;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.exception.EventPublishException;
import org.candlepin.service.model.Event;
import org.jgroups.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultEventAdapter implements EventAdapter {
    private static final Logger log = LoggerFactory.getLogger(DefaultEventAdapter.class);

    private Connection connection;

    // TODO: We should be creating and closing the session when we use it!

    private static final String TOPIC_NAME = "VirtualTopic.services.candlepin.system.cloud.check-in";

    @Inject
    public DefaultEventAdapter(Configuration configuration) throws Exception {
        Instant start = Instant.now();
        this.connection = createConnection(configuration);
        // TODO: There should be no need to start the connection when we are only publishing and not consuming
        // this.connection.start();

        Instant end = Instant.now();
        log.info("Duration of establishing UMB connection and session duration (ms): " + Duration.between(start, end).toMillis());
    }

    @PreDestroy
    public void preDestroy() {
        log.info("Entering DefaultEventAdapter.preDestroy");
        try {
            // connection.stop();
            connection.close();
        }
        catch(JMSException e) {
            log.error("An error occurred while closing UMB connection and session", e);
        }
    }

    @Override
    public void publish(Event event) throws EventPublishException {
        log.info("Attempting to publish event: " + event.getBody());
        Instant start = Instant.now();
        MessageProducer producer = null;
        Session session= null;
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(TOPIC_NAME);
            producer = session.createProducer(topic);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            TextMessage message = session.createTextMessage(event.getBody());
            log.debug("Sending message: "+ message.hashCode() + " to " + TOPIC_NAME);
            producer.send(message);
            log.debug("Message Sent successfully");

            Instant end = Instant.now();
            log.info("UMB message publish duration (ms): " + Duration.between(start, end).toMillis());
        }
        catch (JMSException e) {
            log.error("An error occurred publishing a message", e);
            throw new EventPublishException(e);
        }
        finally {
            if (producer != null) {
                try {
                    producer.close();
                }
                catch (JMSException e) {
                    throw new EventPublishException("Unable to close MessageProducer", e);
                }
            }

            if (session != null) {
                try {
                    session.close();
                }
                catch (JMSException e) {
                    throw new EventPublishException("Unable to close Session", e);
                }
            }
        }
    }

    @Override
    public void shutdown() {
        log.info("Entering DefaultEventAdapter.shutdown");
        try {
            connection.close();
            log.info("Successfully shutdown UMB connection and session.");
        }
        catch(JMSException e) {
            log.error("An error occurred while closing UMB connection and session", e);
        }
    }

    private Connection createConnection(Configuration configuration) throws Exception {
        String brokerURI = configuration.getString(ConfigProperties.UMB_BROKER_URI);
        if (brokerURI == null || brokerURI.isBlank()) {
            throw new ConfigurationException("Null or blank value for configuration: " + ConfigProperties.UMB_BROKER_URI);
        }

        String clientId = configuration.getString(ConfigProperties.UMB_CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            throw new ConfigurationException("Null or blank value for configuration: " + ConfigProperties.UMB_CLIENT_ID);
        }

        String keyStore = configuration.getString(ConfigProperties.UMB_KEY_STORE);
        if (keyStore == null || keyStore.isBlank()) {
            throw new ConfigurationException("Null or blank value for configuration: " + ConfigProperties.UMB_KEY_STORE);
        }

        String keyStorePassword = configuration.getString(ConfigProperties.UMB_KEY_STORE_PASSWORD);
        if (keyStorePassword == null || keyStorePassword.isBlank()) {
            throw new ConfigurationException("Null or blank value for configuration: " + ConfigProperties.UMB_KEY_STORE_PASSWORD);
        }

        // String trustStore = configuration.getString(ConfigProperties.UMB_TRUST_STORE);
        // if (trustStore == null || trustStore.isBlank()) {
        //     throw new ConfigurationException("Null or blank value for configuration: " + ConfigProperties.UMB_TRUST_STORE);
        // }

        // String trustStorePassword = configuration.getString(ConfigProperties.UMB_TRUST_STORE_PASSWORD);
        // if (trustStorePassword == null || trustStorePassword.isBlank()) {
        //     throw new ConfigurationException("Null or blank value for configuration: " + ConfigProperties.UMB_TRUST_STORE_PASSWORD);
        // }

        ActiveMQSslConnectionFactory connectionFactory = new ActiveMQSslConnectionFactory(brokerURI);
        connectionFactory.setKeyStore(keyStore);
        connectionFactory.setKeyStorePassword(keyStorePassword);
        connectionFactory.setTrustStore(keyStore);
        connectionFactory.setTrustStorePassword(keyStorePassword);

        // connectionFactory.setTrustStore(trustStore);
        // connectionFactory.setTrustStorePassword(trustStorePassword);

        clientId = clientId + UUID.randomUUID();
        log.info("Creating UMB connection with client ID: " + clientId);

        Connection connection = connectionFactory.createConnection();
        connection.setClientID(clientId);

        return connection;
    }

}
