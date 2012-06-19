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
package org.candlepin.audit;

import com.google.common.collect.Lists;
import com.google.inject.Injector;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
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
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.JournalType;
import org.hornetq.core.server.impl.HornetQServerImpl;

import java.io.File;
import java.util.HashSet;
import java.util.List;

/**
 * HornetqContextListener - Invoked from our core CandlepinContextListener, thus
 * doesn't actually implement ServletContextListener.
 */
public class HornetqContextListener {

    private static  Logger log = Logger.getLogger(HornetqContextListener.class);

    private HornetQServer hornetqServer;
    private EventSource eventSource;

    public void contextDestroyed() {
        if (hornetqServer != null) {
            eventSource.shutDown();
            try {
                hornetqServer.stop();
            }
            catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public void contextInitialized(Injector injector) {

        Config candlepinConfig = new Config();


        if (hornetqServer == null) {

            Configuration config = new ConfigurationImpl();

            HashSet<TransportConfiguration> transports =
                new HashSet<TransportConfiguration>();
            transports.add(new TransportConfiguration(InVMAcceptorFactory.class
                .getName()));
            config.setAcceptorConfigurations(transports);

            // alter the default pass to silence log output
            config.setClustered(false);
            config.setClusterPassword(null);

            // in vm, who needs security?
            config.setSecurityEnabled(false);

            int largeMsgSize =
                candlepinConfig.getInt(ConfigProperties.HORNETQ_LARGE_MSG_SIZE);
            //config.setJournalBufferSize_AIO(largeMsgSize);
            config.setJournalBufferSize_NIO(largeMsgSize);
            // XXX: should use AIO when we get the native bindings working
            config.setJournalType(JournalType.NIO);

            config.setCreateBindingsDir(true);
            config.setCreateJournalDir(true);

            String baseDir = candlepinConfig.getString(ConfigProperties.HORNETQ_BASE_DIR);

            config.setBindingsDirectory(new File(baseDir, "bindings").toString());
            config.setJournalDirectory(new File(baseDir, "journal").toString());
            config.setLargeMessagesDirectory(new File(baseDir, "largemsgs").toString());

            hornetqServer = new HornetQServerImpl(config);
        }
        try {
            hornetqServer.start();
        }
        catch (Exception e) {
            log.error("Failed to start hornetq message server - " + e);
            throw new RuntimeException(e);
        }

        cleanupOldQueues();

        List<String> listeners = Lists.newArrayList(candlepinConfig
            .getStringArray(ConfigProperties.AUDIT_LISTENERS));

        eventSource = injector.getInstance(EventSource.class);
        for (int i = 0; i < listeners.size(); i++) {
            try {
                Class<?> clazz = this.getClass().getClassLoader().loadClass(
                    listeners.get(i));
                eventSource.registerListener((EventListener) injector.getInstance(clazz));
            }
            catch (Exception e) {
                log.warn("Unable to load audit listener " + listeners.get(i), e);
            }
        }
    }

    /**
     * Remove any old message queues that have a 0 message count in them.
     * This lets us not worry about changing around the registered listeners.
     */
    private void cleanupOldQueues() {
        log.debug("Cleaning old message queues");
        String [] queues = hornetqServer.getHornetQServerControl().getQueueNames();

        ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));

        try {
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
            log.error("Problem cleaning old message queues - " + e);
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            log.error("Problem cleaning old message queues - " + e);
            throw new RuntimeException(e);
        }
    }
}
