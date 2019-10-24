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

import org.candlepin.config.ConfigProperties;

import com.google.common.collect.Lists;
import com.google.inject.Injector;

import org.candlepin.common.config.Configuration;
import org.candlepin.controller.ActiveMQStatusMonitor;
import org.candlepin.controller.QpidStatusMonitor;
import org.candlepin.controller.SuspendModeTransitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;



/**
 * ActiveMQContextListener - Invoked from our core CandlepinContextListener, thus
 * doesn't actually implement ServletContextListener.
 */
public class ActiveMQContextListener {
    private static  Logger log = LoggerFactory.getLogger(ActiveMQContextListener.class);

    private ArtemisMessageSource messageSource;

    public void contextDestroyed() {
        if (this.messageSource != null) {
            this.messageSource.shutDown();
        }
    }

    public void contextInitialized(Injector injector) {
        Configuration candlepinConfig = injector.getInstance(Configuration.class);

        ActiveMQStatusMonitor activeMQStatusMonitor = injector.getInstance(ActiveMQStatusMonitor.class);
        // If suspend mode is enabled, we need the transitioner to listen for connection drops.
        if (candlepinConfig.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
            activeMQStatusMonitor.registerListener(injector.getInstance(SuspendModeTransitioner.class));
        }

        // Set up the ArtemisMessageSource.
        messageSource = injector.getInstance(ArtemisMessageSource.class);
        // ArtemisMessageSource must listen for ActiveMQ status changes so that connections can be rebuilt.
        activeMQStatusMonitor.registerListener(messageSource);

        setupAmqp(injector, candlepinConfig, messageSource);

        // Initialize the ActiveMQ status monitor so that client sessions can be established
        // if the broker is active.
        activeMQStatusMonitor.initialize();
    }

    private void setupAmqp(Injector injector, Configuration candlepinConfig,
        ArtemisMessageSource messageSource) {
        if (candlepinConfig.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            // Listen for Qpid connection changes so that the appropriate ClientSessions
            // can be shutdown/restarted when Qpid status changes.
            QpidStatusMonitor qpidStatusMonitor = injector.getInstance(QpidStatusMonitor.class);
            qpidStatusMonitor.addStatusChangeListener(messageSource);
        }
    }

    /**
     * @param candlepinConfig
     * @return List of class names that will be configured as ActiveMQ listeners.
     */
    public static List<String> getActiveMQListeners(Configuration candlepinConfig) {
        //AMQP integration here - If it is disabled, don't add it to listeners.
        List<String> listeners = Lists.newArrayList(
            candlepinConfig.getList(ConfigProperties.AUDIT_LISTENERS));

        if (candlepinConfig.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            listeners.add(AMQPBusPublisher.class.getName());
        }

        return listeners;
    }

}
