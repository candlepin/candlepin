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

import static org.fedoraproject.candlepin.config.ConfigProperties.AMQP_CONFIG_LOCATION;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

import org.fedoraproject.candlepin.audit.AMQPBusPublisher;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.Event.Target;
import org.fedoraproject.candlepin.audit.Event.Type;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.util.Util;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

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
    
    @SuppressWarnings("unchecked")
    @Inject
    public AMQPBusPubProvider(Config config,
        @Named("abc") Function adapter) {
        try {
            Properties properties = new Properties();
            File file = config.getAsFile(AMQP_CONFIG_LOCATION);
            Preconditions.checkState(file.canRead(), 
                "Config for AMQP not found/can't read @ %s.", file);
            properties.load(new BufferedReader(new FileReader(file)));
            this.ctx = new InitialContext(properties);
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
    

    @Override
    public AMQPBusPublisher get() {
        try {
            Map<Target, Map<Type, TopicPublisher>> pm = Util.newMap();
            Target [] targets = { Target.CONSUMER, Target.USER, Target.ROLE };
            for (Target target : targets) {
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
        String name = target.toString().toLowerCase() +
            Util.capitalize(type.toString().toLowerCase());
        Topic topic = (Topic) this.ctx.lookup(name);
        log.info("Creating publisher for topic: {}", name);
        TopicPublisher tp = this.session.createPublisher(topic);
        map.put(type, tp);
    }

    protected final void storeTopicProducer(Map<Type, TopicPublisher> map, Target target)
        throws JMSException, NamingException {
        for (Type type : Type.values()) {
            storeTopicProducer(type, target, map);
        }
    }
    
}
