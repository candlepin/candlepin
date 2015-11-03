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

import org.apache.commons.io.FileUtils;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.JournalType;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.hornetq.core.settings.impl.AddressFullMessagePolicy;
import org.hornetq.core.settings.impl.AddressSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


/**
 * HornetqContextListener - Invoked from our core CandlepinContextListener, thus
 * doesn't actually implement ServletContextListener.
 */
public class HornetqContextListener {
    private static  Logger log = LoggerFactory.getLogger(HornetqContextListener.class);

    private EmbeddedHornetQ hornetqServer;
    private EventSource eventSource;

    public void contextDestroyed() {
        if (hornetqServer != null) {
            eventSource.shutDown();
            try {
                hornetqServer.stop();
                log.info("Hornetq server stopped.");
            }
            catch (Exception e) {
                log.error("Error stopping hornetq server", e);
            }

        }
    }

    public void contextInitialized(Injector injector) {

        org.candlepin.common.config.Configuration candlepinConfig =
            injector.getInstance(org.candlepin.common.config.Configuration.class);

        if (hornetqServer == null) {
            Configuration config = new ConfigurationImpl();

            HashSet<TransportConfiguration> transports =
                new HashSet<TransportConfiguration>();
            transports.add(new TransportConfiguration(InVMAcceptorFactory.class
                .getName()));
            config.setAcceptorConfigurations(transports);

            // alter the default pass to silence log output
            config.setClusterUser(null);
            config.setClusterPassword(null);

            // in vm, who needs security?
            config.setSecurityEnabled(false);

            config.setJournalType(JournalType.NIO);

            config.setCreateBindingsDir(true);
            config.setCreateJournalDir(true);

            String baseDir = candlepinConfig.getString(ConfigProperties.HORNETQ_BASE_DIR);

            config.setBindingsDirectory(new File(baseDir, "bindings").toString());
            config.setJournalDirectory(new File(baseDir, "journal").toString());
            config.setLargeMessagesDirectory(new File(baseDir, "largemsgs").toString());
            config.setPagingDirectory(new File(baseDir, "paging").toString());

            Map<String, AddressSettings> settings = new HashMap<String, AddressSettings>();
            AddressSettings pagingConfig = new AddressSettings();

            String addressPolicyString =
                    candlepinConfig.getString(ConfigProperties.HORNETQ_ADDRESS_FULL_POLICY);
            long maxQueueSizeInMb = candlepinConfig.getInt(ConfigProperties.HORNETQ_MAX_QUEUE_SIZE);
            long maxPageSizeInMb = candlepinConfig.getInt(ConfigProperties.HORNETQ_MAX_PAGE_SIZE);

            AddressFullMessagePolicy addressPolicy = null;
            if (addressPolicyString.equals("PAGE")) {
                addressPolicy = AddressFullMessagePolicy.PAGE;
            }
            else if (addressPolicyString.equals("BLOCK")) {
                addressPolicy = AddressFullMessagePolicy.BLOCK;
            }
            else {
                throw new IllegalArgumentException("Unknown HORNETQ_ADDRESS_FULL_POLICY: " +
                        addressPolicyString + " . Please use one of: PAGE, BLOCK");
            }

            // Paging sizes need to be converted to bytes
            pagingConfig.setMaxSizeBytes(maxQueueSizeInMb * FileUtils.ONE_MB);
            if (addressPolicy == AddressFullMessagePolicy.PAGE) {
                pagingConfig.setPageSizeBytes(maxPageSizeInMb * FileUtils.ONE_MB);
            }
            pagingConfig.setAddressFullMessagePolicy(addressPolicy);
            //Enable for all the queues
            settings.put("#", pagingConfig);
            config.setAddressesSettings(settings);

            int maxScheduledThreads = candlepinConfig.getInt(ConfigProperties.HORNETQ_MAX_SCHEDULED_THREADS);
            int maxThreads = candlepinConfig.getInt(ConfigProperties.HORNETQ_MAX_THREADS);
            if (maxThreads != -1) {
                config.setThreadPoolMaxSize(maxThreads);
            }

            if (maxScheduledThreads != -1) {
                config.setScheduledThreadPoolMaxSize(maxScheduledThreads);
            }

            /**
             * Anything up to size of LARGE_MSG_SIZE may be needed to be written to the Journal,
             * so we must set buffer size accordingly.
             *
             * If buffer size would be < LARGE_MSG_SIZE we may get exceptions such as this:
             * Can't write records bigger than the bufferSize(XXXYYY) on the journal
             */
            int largeMsgSize = candlepinConfig.getInt(ConfigProperties.HORNETQ_LARGE_MSG_SIZE);
            config.setJournalBufferSize_AIO(largeMsgSize);
            config.setJournalBufferSize_NIO(largeMsgSize);

            hornetqServer = new EmbeddedHornetQ();
            hornetqServer.setConfiguration(config);
        }
        try {
            hornetqServer.start();
            log.info("Hornetq server started");
        }
        catch (Exception e) {
            log.error("Failed to start hornetq message server:", e);
            throw new RuntimeException(e);
        }

        cleanupOldQueues();

        List<String> listeners = getHornetqListeners(candlepinConfig);

        eventSource = injector.getInstance(EventSource.class);
        for (int i = 0; i < listeners.size(); i++) {
            try {
                Class<?> clazz = this.getClass().getClassLoader().loadClass(
                    listeners.get(i));
                eventSource.registerListener((EventListener) injector.getInstance(clazz));
            }
            catch (Exception e) {
                log.warn("Unable to register listener " + listeners.get(i), e);
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

    /**
     * @param candlepinConfig
     * @return List of class names that will be configured as HornetQ listeners.
     */
    public static List<String> getHornetqListeners(
            org.candlepin.common.config.Configuration candlepinConfig) {
        //AMQP integration here - If it is disabled, don't add it to listeners.
        List<String> listeners = Lists.newArrayList(
                candlepinConfig.getList(ConfigProperties.AUDIT_LISTENERS));

        if (candlepinConfig
            .getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            listeners.add(AMQPBusPublisher.class.getName());
        }
        return listeners;
    }

    /**
     * Remove any old message queues that have a 0 message count in them.
     * This lets us not worry about changing around the registered listeners.
     */
    private void cleanupOldQueues() {
        log.debug("Cleaning old message queues");
        try {
            String [] queues = hornetqServer.getHornetQServer().getHornetQServerControl().getQueueNames();

            ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(
                new TransportConfiguration(InVMConnectorFactory.class.getName()));

            ClientSessionFactory factory =  locator.createSessionFactory();
            ClientSession session = factory.createSession(true, true);
            session.start();

            for (int i = 0; i < queues.length; i++) {
                long msgCount =
                    session.queueQuery(new SimpleString(queues[i])).getMessageCount();
                if (msgCount == 0) {
                    log.debug(String.format("found queue '%s' with 0 messages. deleting",
                        queues[i]));
                    session.deleteQueue(queues[i]);
                }
                else {
                    log.debug(String.format("found queue '%s' with %d messages. kept",
                        queues[i], msgCount));
                }
            }

            session.stop();
            session.close();
        }
        catch (HornetQException e) {
            log.error("Problem cleaning old message queues:", e);
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            log.error("Problem cleaning old message queues:", e);
            throw new RuntimeException(e);
        }
    }
}
