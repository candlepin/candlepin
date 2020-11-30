/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.messaging.impl.artemis;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.messaging.CPMContextListener;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMSessionFactory;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

/**
 * CPMContextListener implementation backed by Artemis
 */
//@Component
public class ArtemisContextListener implements CPMContextListener {
    private static  Logger log = LoggerFactory.getLogger(ArtemisContextListener.class);
    @Autowired
    private Configuration config;
    private EmbeddedActiveMQ activeMQServer;

    /* TODO candlepin-spring: According to the guice configuration, factory should be a instance of
        ArtemisSessionFactory, but in Spring there is a Bean created for CPMSessionFactory which is
        getting bind to ArtemisSessionFactory. This needs to be confirmed if it okay to have
        factory a instance of CPMSessionFactory here. */
    @Autowired
    private CPMSessionFactory factory;


    /**
     * {@inheritDoc}
     */
    @Override
    //public void initialize(Injector injector) throws CPMException {
    public void initialize() throws CPMException {
        //this.config = injector.getInstance(Configuration.class);

        // Create the server if we're configured to do so.
        // TODO: Change this to use ACTIVEMQ_EMBEDDED_BROKER once configuration upgrades are in
        // place
        boolean embedded = this.config.getBoolean(ConfigProperties.ACTIVEMQ_EMBEDDED);

        if (embedded) {
            log.info("Initializing embedded Artemis server...");

            if (this.activeMQServer == null) {
                this.activeMQServer = new EmbeddedActiveMQ();

                // If the Artemis config file is specified in the config use it. Otherwise
                // the broker.xml file distributed via the WAR file will be used.
                String artemisConfigFilePath = this.config
                    .getProperty(ConfigProperties.ACTIVEMQ_SERVER_CONFIG_PATH);

                if (artemisConfigFilePath != null && !artemisConfigFilePath.isEmpty()) {
                    log.info("Loading Artemis config file: {}", artemisConfigFilePath);
                    this.activeMQServer.setConfigResourcePath(
                        new File(artemisConfigFilePath).toURI().toString());
                }

                String invmLoginEntryName = this.config
                    .getString(ConfigProperties.ACTIVEMQ_JAAS_INVM_LOGIN_NAME);

                String certLoginEntryName = this.config
                    .getString(ConfigProperties.ACTIVEMQ_JAAS_CERTIFICATE_LOGIN_NAME);

                ActiveMQJAASSecurityManager securityManager =
                    new ActiveMQJAASSecurityManager(invmLoginEntryName, certLoginEntryName);

                this.activeMQServer.setSecurityManager(securityManager);
            }

            try {
                this.activeMQServer.start();
                log.info("Embedded Artemis server started successfully");
                //System.out.println(this.activeMQServer.getActiveMQServer().getConfiguration());
            }
            catch (Exception e) {
                log.error("Failed to start embedded Artemis message server", e);
                throw new CPMException(e);
            }
        }

        // Initialize our session factory
        //ArtemisSessionFactory factory = injector.getInstance(ArtemisSessionFactory.class);
        factory.initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() throws CPMException {
        // Tear down the internal artemis server if it exists

        try {
            if (this.activeMQServer != null) {
                this.activeMQServer.stop();
                log.info("Embedded Artemis server stopped");
            }
        }
        catch (Exception e) {
            log.error("Unexpected exception occurred while stopping embedded Artemis server", e);
            throw new CPMException(e);
        }
        ActiveMQClient.clearThreadPools();
    }

}
