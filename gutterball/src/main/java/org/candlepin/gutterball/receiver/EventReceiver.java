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

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;


/**
 * Maintains the connection to the AMQP message bus and configured the message listener.
 *
 * Note that this is currently bound as an eager singleton, and messages are
 * received in a single thread.
 */
public class EventReceiver {
    private static Logger log = LoggerFactory.getLogger(EventReceiver.class);

    private TopicSubscriber consumer;
    private Session sess;
    private Topic dest;
    private Connection conn;
    private String connstr;

    /*
     * TODO: Because we're bound as a singleton and are holding an eventMessageListener, we're
     * also holding a reference to a unitOfWork, which is not a threadsafe object. At present
     * we're receiving events in a single thread, but this is still a bit worrisome and should
     * probably be fixed
     */
    private EventMessageListener eventMessageListener;

    @Inject
    public EventReceiver(Configuration config, EventMessageListener eventMessageListener)
        throws AMQException, JMSException, URISyntaxException {
        this.eventMessageListener = eventMessageListener;
        configureSslProperties(config);
        init(config);
    }

    private void init(Configuration config) throws AMQException, JMSException, URISyntaxException  {
        conn = new AMQConnection(config.getString(ConfigProperties.AMQP_CONNECT_STRING));
        conn.start();
        sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        dest = new AMQAnyDestination("event");
        consumer = sess.createDurableSubscriber(dest, "event");
        consumer.setMessageListener(eventMessageListener);
        log.info("Receiver init complete");

    }

    private void configureSslProperties(Configuration config) {
        // FIXME: Setting the property here is dangerous,
        // but in theory nothing else is setting/using it
        // http://qpid.apache.org/releases/qpid-0.24/programming/book/ch03s06.html

        System.setProperty("javax.net.ssl.keyStore",
            config.getString(ConfigProperties.AMQP_KEYSTORE));
        System.setProperty("javax.net.ssl.keyStorePassword",
            config.getString(ConfigProperties.AMQP_KEYSTORE_PASSWORD));
        System.setProperty("javax.net.ssl.trustStore",
            config.getString(ConfigProperties.AMQP_TRUSTSTORE));
        System.setProperty("javax.net.ssl.trustStorePassword",
            config.getString(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD));

        log.info("Configured SSL properites.");
    }

    private void finish() throws JMSException {
        consumer.close();
        sess.close();
        conn.close();
        log.info("DONE");
    }
}
