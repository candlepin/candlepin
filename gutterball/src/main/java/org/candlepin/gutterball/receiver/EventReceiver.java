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
package org.candlepin.gutterball.receiver;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigProperties;

import com.google.inject.Inject;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.url.URLSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;


/**
 * Maintains the connection to the AMQP message bus and configures the message listener.
 *
 * NOTE: this class is currently bound as an eager singleton, and messages are
 * received in a single thread. Similarly the EventMessageListener is therefore also
 * bound as an eager singleton, and is using a non-threadsafe unit of work.
 */
public class EventReceiver {
    private static Logger log = LoggerFactory.getLogger(EventReceiver.class);

    private TopicSubscriber consumer;
    private Session sess;
    private Topic dest;

    private EventMessageListener eventMessageListener;

    private Connection conn;

    @Inject
    public EventReceiver(Configuration config, EventMessageListener eventMessageListener)
        throws Exception {
        this.eventMessageListener = eventMessageListener;
        init(config);
    }

    protected void init(final Configuration config) throws JMSException, URISyntaxException {
        AMQConnectionFactory connectionFactory = configureConnectionFactory(config);
        conn = connectionFactory.createConnection();
        conn.start();

        sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        dest = new AMQAnyDestination("event");
        consumer = sess.createDurableSubscriber(dest, "event");
        consumer.setMessageListener(eventMessageListener);
        log.info("Receiver init complete");
    }

    private AMQConnectionFactory configureConnectionFactory(Configuration config) throws URLSyntaxException {
        int maxRetries = config.getInt(ConfigProperties.AMQP_CONNECTION_RETRY_ATTEMPTS);
        long waitTimeInSeconds = config.getLong(ConfigProperties.AMQP_CONNECTION_RETRY_INTERVAL);

        AMQConnectionFactory connFactory = new AMQConnectionFactory(
            config.getString(ConfigProperties.AMQP_CONNECT_STRING));
        for (BrokerDetails broker : connFactory.getConnectionURL().getAllBrokerDetails()) {
            broker.setProperty("trust_store", config.getString(ConfigProperties.AMQP_TRUSTSTORE));
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

        log.debug("Configured AMQP connection factory.");
        return connFactory;
    }

    // FIXME [mstead] This is not being called and should probably be called
    //       when the app is being shut down.
    private void finish() throws JMSException {
        consumer.close();
        sess.close();
        conn.close();
        log.info("DONE");
    }
}
