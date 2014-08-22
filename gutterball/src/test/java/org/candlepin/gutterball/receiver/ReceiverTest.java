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

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URISyntaxException;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;


/**
 * ReceiverTest
 * @version $Rev$
 */
public class ReceiverTest {

    @Ignore
    @Test
    public void test() throws AMQException, JMSException, URISyntaxException {
        System.setProperty("javax.net.ssl.keyStore", "/etc/candlepin/certs/amqp/keystore");
            //config.getString(ConfigProperties.AMQP_KEYSTORE));
        System.setProperty("javax.net.ssl.keyStorePassword", "password");
            //config.getString(ConfigProperties.AMQP_KEYSTORE_PASSWORD));
        System.setProperty("javax.net.ssl.trustStore", "/etc/candlepin/certs/amqp/truststore");
            //config.getString(ConfigProperties.AMQP_TRUSTSTORE));
        System.setProperty("javax.net.ssl.trustStorePassword", "password");
            //config.getString(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD));
        String connstr = "amqp://guest:guest@localhost/test?brokerlist=" +
            "'tcp://localhost:5671?ssl='true'&ssl_cert_alias='amqp-client''";
        Connection conn = new AMQConnection(connstr);
        conn.start();
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination dest = new AMQAnyDestination("event");
        MessageConsumer consumer = sess.createConsumer(dest);
        Message msg;
        while ((msg = consumer.receive(-1)) != null) {
            System.out.println("\n------------- Msg -------------");
            System.out.println(msg);
            System.out.println("-------------------------------\n");
        }
        consumer.close();
        sess.close();
        conn.close();
        System.out.println("DONE");
    }

}
