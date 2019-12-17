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

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;

/**
 * Helper class to build configuration for Qpid. There are two phases to
 * Qpid configuration. First we configure Qpid using JNDI and then we use internal
 * Qpid API (BrokerDetails) to refine the configuration
 * @author fnguyen
 *
 */
public class QpidConfigBuilder {
    private Configuration config;

    @Inject
    public QpidConfigBuilder(Configuration config) {
        this.config = config;
    }

    /**
     * Transforms Candlepin configuration to a {@code Map <String, String> }so that its easy to
     * configure Qpid broker.
     * @param ctx
     * @return Map of configurations for Qpid Broker
     */
    public Map<String, String> buildBrokerDetails(Context ctx) {
        Map<String, String> brokerConfig = new HashMap<>();

        int maxRetries = config.getInt(ConfigProperties.AMQP_CONNECTION_RETRY_ATTEMPTS);
        long waitTimeInSeconds = config.getLong(ConfigProperties.AMQP_CONNECTION_RETRY_INTERVAL);

        brokerConfig.put("trust_store", config.getString(ConfigProperties.AMQP_TRUSTSTORE));
        brokerConfig.put("sasl_mechs", "ANONYMOUS");
        brokerConfig.put("trust_store_password", config.getString(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD));
        brokerConfig.put("key_store", config.getString(ConfigProperties.AMQP_KEYSTORE));
        brokerConfig.put("key_store_password",
            config.getString(ConfigProperties.AMQP_KEYSTORE_PASSWORD));
        brokerConfig.put("retries", Integer.toString(maxRetries));
        long delay = 1000 * waitTimeInSeconds;
        brokerConfig.put("connectdelay", Long.toString(delay));
        return brokerConfig;
    }

    /**
     * Qpid JMS client needs to be configured using both properties (method buildBrokerDetails is used)
     * but also needs configuration in JNDI. This method provides the Properties object that is
     * used to configure the Qpid through JNDI. Besides other things, it also must state in advance
     * which Queues (JMS notion) is mapped to which binding keys (AMQP notion)
     * @return Properties object for JNDI
     */
    public Properties buildConfigurationProperties() {
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

        return properties;
    }

    public String getTopicName(Type type, Target target) {
        return target.toString().toLowerCase() +
            Util.capitalize(type.toString().toLowerCase());
    }

    public String getDestination(Type type, Target target) {
        String key = target.toString().toLowerCase();
        String object = targetToEvent.get(key);
        return (object == null ? key : object) + "." + type.toString().toLowerCase();
    }

    // external events may not have the same name as the internal events
    private Map<String, String> targetToEvent = new HashMap<String, String>() {
        private static final long serialVersionUID = 2L;
        {
            this.put(Event.Target.SUBSCRIPTION.toString().toLowerCase(), "product");
            // add more mappings when necessary
        }
    };
}
