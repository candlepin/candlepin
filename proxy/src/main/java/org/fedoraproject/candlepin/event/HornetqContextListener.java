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
package org.fedoraproject.candlepin.event;

import java.util.HashSet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.impl.HornetQServerImpl;

/**
 * HornetqContextListener
 */
public class HornetqContextListener implements ServletContextListener {
    private HornetQServer hornetqServer;
    
    /* (non-Javadoc)
     * @see javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
     */
    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        if (hornetqServer != null) {
            try {
                hornetqServer.stop();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /* (non-Javadoc)
     * @see javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
     */
    @Override
    public void contextInitialized(ServletContextEvent arg0) {
            if (hornetqServer == null) {
                Configuration config = new ConfigurationImpl();
                
                HashSet<TransportConfiguration> transports = new HashSet<TransportConfiguration>();
                transports.add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
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
                System.out.println("HornetQ started. bzzzz");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            
            new OtherExampleListener();
            new ExampleListener();
    }

}
