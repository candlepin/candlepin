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
package org.candlepin.gutterball.receive;

import java.net.URISyntaxException;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;


/**
 * Classes implementing FileConfiguration take their configuration from a file source and can
 * therefore specify an encoding.
 */
public class EventReceiver {
	TopicSubscriber consumer;
	Session sess;
	Topic dest;
	Connection conn;
	String connstr;
    private static Logger log = LoggerFactory.getLogger(EventReceiver.class);

    @Inject
	public EventReceiver() throws AMQException, JMSException, URISyntaxException {
    	System.setProperty("javax.net.ssl.keyStore", "/etc/candlepin/certs/amqp/keystore");
	        //config.getString(ConfigProperties.AMQP_KEYSTORE));
	    System.setProperty("javax.net.ssl.keyStorePassword", "password");
	        //config.getString(ConfigProperties.AMQP_KEYSTORE_PASSWORD));
	    System.setProperty("javax.net.ssl.trustStore", "/etc/candlepin/certs/amqp/truststore");
	        //config.getString(ConfigProperties.AMQP_TRUSTSTORE));
	    System.setProperty("javax.net.ssl.trustStorePassword", "password");
	        //config.getString(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD));
	    connstr = "amqp://guest:guest@localhost/test?brokerlist='tcp://localhost:5671?ssl='true'&ssl_cert_alias='amqp-client''";
        init();
    }
    
    private void init() throws AMQException, JMSException, URISyntaxException  {
	    conn = new AMQConnection(connstr);
	    conn.start();
	    sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
	    dest = new AMQAnyDestination("event");
	    consumer = sess.createDurableSubscriber(dest, "event");
	    consumer.setMessageListener(new EventMessageListener());
	    log.info("receiver init complete");
	    
    }
    
    private void finish() throws JMSException {
	    consumer.close();
	    sess.close();
	    conn.close();
	    log.info("DONE");
    }
}
