/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.guice;

import org.fedoraproject.candlepin.audit.AMQPBusPublisher;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.Event.Target;
import org.fedoraproject.candlepin.audit.Event.Type;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.util.Util;

import com.google.common.base.Function;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.jms.ConnectionFactory;
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
 *
 * @author ajay
 */
public class AMQPBusPubProvider implements Provider<AMQPBusPublisher> {

    private Context ctx;
    private TopicConnection connection;
    private Function<Event, String> adapter;
    private TopicSession session;
    private static org.slf4j.Logger log = LoggerFactory.getLogger(AMQPBusPubProvider.class);

    // external events may not have the same name as the internal events
    private Map<String, String> targetToEvent = new HashMap<String, String>() {
        private static final long serialVersionUID = 2L;
        {
            this.put(Event.Target.SUBSCRIPTION.toString().toLowerCase(), "product");
            // add more mappings when necessary
        }
    };

    @SuppressWarnings("unchecked")
    @Inject
    public AMQPBusPubProvider(Config config,
        @Named("abc") Function adapter) {
        try {
            configureSslProperties(config);

            this.ctx = new InitialContext(buildConfigurationProperties(config));
            ConnectionFactory connectionFactory = (ConnectionFactory) ctx
                .lookup("qpidConnectionfactory");
            this.connection = (TopicConnection) connectionFactory
                .createConnection();
            this.adapter = adapter;
            this.session = this.connection.createTopicSession(false,
                Session.AUTO_ACKNOWLEDGE);
        }
        catch (Exception ex) {
            log.error("Unable to instantiate AMQPBusProvider: ", ex);
            throw new RuntimeException(ex);
        }
    }


    private void configureSslProperties(Config config) {
        // FIXME: Setting the property here is dangerous,
        // but in theory nothing else is setting/using it
        System.setProperty("javax.net.ssl.keyStore",
            config.getString(ConfigProperties.AMQP_KEYSTORE));
        System.setProperty("javax.net.ssl.keyStorePassword",
            config.getString(ConfigProperties.AMQP_KEYSTORE_PASSWORD));
        System.setProperty("javax.net.ssl.trustStore",
            config.getString(ConfigProperties.AMQP_TRUSTSTORE));
        System.setProperty("javax.net.ssl.trustStorePassword",
            config.getString(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD));
    }


    /**
     * @return A Properties object containing the amqp configuration for jms
     */
    private Properties buildConfigurationProperties(Config config) {
        Properties properties = new Properties();

        properties.put("java.naming.factory.initial",
            "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
        properties.put("connectionfactory.qpidConnectionfactory",
            "amqp://guest:guest@localhost/test?brokerlist='" +
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
        return properties;
    }


    @Override
    public AMQPBusPublisher get() {
        try {
            Map<Target, Map<Type, TopicPublisher>> pm = Util.newMap();
            for (Target target : Target.values()) {
                Map<Type, TopicPublisher> typeToTpMap = Util.newMap();
                storeTopicProducer(typeToTpMap, target);
                pm.put(target, typeToTpMap);
            }
            return new AMQPBusPublisher(session, this.adapter, pm);
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

    protected final void storeTopicProducer(Type type, Target target,
        Map<Type, TopicPublisher> map) throws JMSException, NamingException {
        String name = getTopicName(type, target);
        Topic topic = (Topic) this.ctx.lookup(name);
        log.info("Creating publisher for topic: {}", name);
        TopicPublisher tp = this.session.createPublisher(topic);
        map.put(type, tp);
    }


    private String getTopicName(Type type, Target target) {
        String name = target.toString().toLowerCase() +
            Util.capitalize(type.toString().toLowerCase());
        return name;
    }

    private String getDestination(Type type, Target target) {
        String key = target.toString().toLowerCase();
        String object = targetToEvent.get(key);
        return (object == null ? key : object) + "." + type.toString().toLowerCase();
    }

    protected final void storeTopicProducer(Map<Type, TopicPublisher> map, Target target)
        throws JMSException, NamingException {
        for (Type type : Type.values()) {
            storeTopicProducer(type, target, map);
        }
    }

}
