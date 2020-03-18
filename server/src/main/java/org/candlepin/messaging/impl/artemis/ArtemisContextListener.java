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

import com.google.inject.Injector;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import javax.inject.Singleton;



/**
 * CPMContextListener implementation backed by Artemis
 */
@Singleton
public class ArtemisContextListener implements CPMContextListener {
    private static  Logger log = LoggerFactory.getLogger(ArtemisContextListener.class);

    private Configuration config;
    private EmbeddedActiveMQ activeMQServer;

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(Injector injector) throws CPMException {
        this.config = injector.getInstance(Configuration.class);

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

                // Check if we need to add the JAAS security manager for allowing SSL encryption
                // between the embedded server and message consumers (Katello)
                boolean requireJAAS = this.config.getBoolean(ConfigProperties.ACTIVEMQ_EMBEDDED_REQUIRE_JAAS);

                if (requireJAAS) {
                    ActiveMQSecurityManager securityManager =
                        new ActiveMQJAASSecurityManager("PropertiesLogin", "CertLogin");

                    this.activeMQServer.setSecurityManager(securityManager);
                }
            }

            try {
                this.activeMQServer.start();
                log.info("Embedded Artemis server started successfully");
            }
            catch (Exception e) {
                log.error("Failed to start embedded Artemis message server", e);
                throw new CPMException(e);
            }
        }

        // Initialize our session factory
        ArtemisSessionFactory factory = injector.getInstance(ArtemisSessionFactory.class);
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
