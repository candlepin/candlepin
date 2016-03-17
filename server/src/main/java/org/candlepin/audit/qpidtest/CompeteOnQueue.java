package org.candlepin.audit.qpidtest;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.BrokerDetails;
import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.auth.PrincipalData;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

/**
 * Example of multiple publishers and multiple receivers. Receivers
 * are competing on a single queue.
 * 
 * Standard AMQP practice is to create a fanout Exchange and 
 * a Queue that binds to that Exchange. Both can be created in 
 * QPID with the following commands:
 * 
 * config_args='-b amqps://localhost:5671 --ssl-certificate ./keys/qpid_ca.crt --ssl-key ./keys/qpid_ca.key'
 * sudo qpid-config $config_args add exchange fanout "activation" --durable 
 * sudo qpid-config $config_args add queue "qactivation" --durable 
 * sudo qpid-config $config_args bind "activation" "qactivation"
 * 
 * @author fnguyen
 *
 */
public class CompeteOnQueue {

    public static void main(String[] args) throws Exception {    
        Configuration config = new AMQPDummyConfig();
        Context ctx = new InitialContext(buildConfigurationProperties(config));
        
        AMQConnectionFactory connectionFactory = createConnectionFactory(config,ctx);
        Connection qcon = connectionFactory.createConnection();
        qcon.start();
        
        //Lets create 4 lightweight sessions. s1 and s2 will be used
        //for two producing threads. s3 and s4 will be used for 
        //two consuming threads
        Session s1 = qcon.createSession(true, Session.AUTO_ACKNOWLEDGE);
        Session s2 = qcon.createSession(true, Session.AUTO_ACKNOWLEDGE);
        Session s3 = qcon.createSession(true, Session.AUTO_ACKNOWLEDGE);
        Session s4 = qcon.createSession(true, Session.AUTO_ACKNOWLEDGE);
        
        ProducingThread pt1 = new ProducingThread((Queue)ctx.lookup("dactivation"), s1, "Producer A");
        ProducingThread pt2 = new ProducingThread((Queue)ctx.lookup("dactivation"),s2, "Producer B");
        ConsumingThread ct1 = new ConsumingThread((Queue)ctx.lookup("qactivation"),s3, "Consumer A");
        ConsumingThread ct2 = new ConsumingThread((Queue)ctx.lookup("qactivation"),s4, "Consumer B");
        
        pt1.start();
        pt2.start();
        ct1.start();
        ct2.start();
    }
    
    public static Event createEvent(){
        Event e = new Event();
        e.setPrincipal(new PrincipalData("tprinc", "tprinc"));
        e.setTarget(Target.ACTIVATIONKEY);
        e.setType(Type.CREATED);
        return e;
    }
    
    private static void produce(Session s) throws Exception  {       
        MessageProducer mp = s.createProducer(new AMQQueue("activation"));
        mp.send(s.createTextMessage("ahoj"));       
        s.commit();
    }

    
    private static AMQConnectionFactory createConnectionFactory(Configuration config, Context ctx) throws NamingException {

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
            }
            return connectionFactory;
        }
    private  static Properties buildConfigurationProperties(Configuration config) {
        Properties properties = new Properties();

        properties.put("java.naming.factory.initial",
            "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        properties.put("connectionfactory.qpidConnectionfactory",
            "amqp://guest:guest@localhost/test?sync_publish='persistent'&brokerlist='" +
            config.getString(ConfigProperties.AMQP_CONNECT_STRING) + "'");
        properties.put("destination.qactivation" , "qactivation");
        properties.put("destination.dactivation" , "activation");
        return properties;
    }
}
