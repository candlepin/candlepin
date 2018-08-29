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

import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

import org.candlepin.controller.QpidStatusMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * ActiveMQContextListener - Invoked from our core CandlepinContextListener, thus
 * doesn't actually implement ServletContextListener.
 */
public class ActiveMQContextListener {
    private static  Logger log = LoggerFactory.getLogger(ActiveMQContextListener.class);

    private EmbeddedActiveMQ activeMQServer;
    private EventSource eventSource;

    public void contextDestroyed() {
        if (activeMQServer != null) {
            eventSource.shutDown();
            try {
                activeMQServer.stop();
                log.info("ActiveMQ server stopped.");
            }
            catch (Exception e) {
                log.error("Error stopping ActiveMQ server", e);
            }

        }
    }

    public void contextInitialized(Injector injector) {
        org.candlepin.common.config.Configuration candlepinConfig =
            injector.getInstance(org.candlepin.common.config.Configuration.class);

        boolean embedded = candlepinConfig.getBoolean(ConfigProperties.ACTIVEMQ_EMBEDDED);
        if (embedded) {
            log.info("Candlepin will connect to an embedded Artemis server.");
            if (activeMQServer == null) {
                activeMQServer = new EmbeddedActiveMQ();

                // If the Artemis config file is specified in the config use it. Otherwise
                // the broker.xml file distributed via the WAR file will be used.
                String artemisConfigFilePath = candlepinConfig.getProperty(
                    ConfigProperties.ACTIVEMQ_SERVER_CONFIG_PATH);
                if (artemisConfigFilePath != null && !artemisConfigFilePath.isEmpty()) {
                    log.info("Loading Artemis config file: {}", artemisConfigFilePath);
                    activeMQServer.setConfigResourcePath(new File(artemisConfigFilePath).toURI().toString());
                }
            }

            try {
                activeMQServer.start();
                log.info("ActiveMQ server started");
            }
            catch (Exception e) {
                log.error("Failed to start ActiveMQ message server:", e);
                throw new RuntimeException(e);
            }
        }
        else {
            log.info("Candlepin will connect to a remote Artemis server.");
        }

        // Create the event source and register all listeners now that the server is started
        // and the old queues are cleaned up.
        eventSource = injector.getInstance(EventSource.class);
        setupAmqp(injector, candlepinConfig, eventSource);

        List<EventListener> eventListeners = new ArrayList<>();
        getActiveMQListeners(candlepinConfig).forEach(listenerClass -> {
            try {
                Class<?> clazz = this.getClass().getClassLoader().loadClass(listenerClass);
                eventListeners.add((EventListener) injector.getInstance(clazz));
            }
            catch (Exception e) {
                log.warn("Unable to register listener {}", listenerClass, e);
            }
        });

        for (EventListener listener : eventListeners) {
            try {
                eventSource.registerListener(listener);
            }
            catch (Exception e) {
                log.warn("Unable to register listener {}", listener, e);
            }
        }

        // Initialize the Event sink AFTER the internal server has been
        // created and started.
        EventSink sink = injector.getInstance(EventSink.class);
        try {
            sink.initialize();
        }
        catch (Exception e) {
            log.error("Failed to initialize EventSink:", e);
            throw new RuntimeException(e);
        }
    }

    private void setupAmqp(Injector injector, org.candlepin.common.config.Configuration candlepinConfig,
        EventSource eventSource) {
        try {
            if (candlepinConfig.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
                // Listen for Qpid connection changes so that the appropriate ClientSessions
                // can be shutdown/restarted when Qpid status changes.
                QpidStatusMonitor qpidStatusMonitor = injector.getInstance(QpidStatusMonitor.class);
                qpidStatusMonitor.addStatusChangeListener(eventSource);

                // TODO Look into whether this connection is required. Qpid connection is NOT a singleton
                //      so I'm not sure that this connection is required as it isn't doing anything.
                //Both these classes should be singletons
                QpidConnection conFactory = injector.getInstance(QpidConnection.class);
                conFactory.connect();
            }
        }
        catch (Exception e) {
            log.error("Error starting AMQP client", e);
        }
    }

    /**
     * @param candlepinConfig
     * @return List of class names that will be configured as ActiveMQ listeners.
     */
    public static List<String> getActiveMQListeners(
        org.candlepin.common.config.Configuration candlepinConfig) {
        //AMQP integration here - If it is disabled, don't add it to listeners.
        List<String> listeners = Lists.newArrayList(
            candlepinConfig.getList(ConfigProperties.AUDIT_LISTENERS));

        if (candlepinConfig.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            listeners.add(AMQPBusPublisher.class.getName());
        }
        return listeners;
    }

}
