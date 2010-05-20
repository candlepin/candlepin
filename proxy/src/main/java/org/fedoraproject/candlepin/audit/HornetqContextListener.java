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
package org.fedoraproject.candlepin.audit;

import java.util.HashSet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.Logger;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.impl.HornetQServerImpl;

/**
 * HornetqContextListener
 */
public class HornetqContextListener implements ServletContextListener {
    private static  Logger log = Logger.getLogger(HornetqContextListener.class);
    
    private HornetQServer hornetqServer;
    private EventHub eventHub;
    
    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        if (hornetqServer != null) {
            eventHub.shutDown();
            try {
                hornetqServer.stop();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent arg0) {
        if (hornetqServer == null) {
            Configuration config = new ConfigurationImpl();

            HashSet<TransportConfiguration> transports =
                new HashSet<TransportConfiguration>();
            transports.add(new TransportConfiguration(InVMAcceptorFactory.class
                .getName()));
            config.setAcceptorConfigurations(transports);

            config.setBindingsDirectory("/tmp/hornetq/bindings");
            config.setCreateBindingsDir(true);
            config.setJournalDirectory("/tmp/hornetq/journal");
            config.setCreateJournalDir(true);
            config.setLargeMessagesDirectory("/tmp/hornetq/largemsgs");

            // in vm, who needs security?
            config.setSecurityEnabled(false);

            hornetqServer = new HornetQServerImpl(config);
        }
        try {
            hornetqServer.start();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        cleanupOldQueues();
        
        eventHub = new EventHub();
        eventHub.registerListener(new OtherExampleListener());
        eventHub.registerListener(new ExampleListener());
    }

    /**
     * Remove any old message queues that have a 0 message count in them.
     * This lets us not worry about changing around the registered listeners.
     */
    private void cleanupOldQueues() {
        log.debug("Cleaning old message queues");
        String [] queues = hornetqServer.getHornetQServerControl().getQueueNames();
        
        ClientSessionFactory factory =  HornetQClient.createClientSessionFactory(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));

        try {
            ClientSession session = factory.createSession(true, true);
            session.start();

            for (int i = 0; i < queues.length; i++) {
                int msgCount =
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
        }
        catch (HornetQException e) {
            log.error("Problem cleaning old message queues - " + e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
