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

package org.candlepin.auth;


import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * KeycloakAdapterConfiguration to load contents from keycloak file
 */
public class KeycloakAdapterConfiguration {

    private AdapterConfig adapterConfig;
    private static Logger log = LoggerFactory.getLogger(KeycloakAdapterConfiguration.class);
    private KeycloakDeployment keycloakDeployment;

    @Inject
    public KeycloakAdapterConfiguration(Configuration configuration) throws Exception {
        if (configuration.getBoolean(ConfigProperties.KEYCLOAK_AUTHENTICATION, false)) {
            String configFile = configuration.getString(ConfigProperties.KEYCLOAK_FILEPATH, null);

            try (InputStream cfgStream = new FileInputStream(configFile)) {
                adapterConfig = KeycloakDeploymentBuilder.loadAdapterConfig(cfgStream);
                this.keycloakDeployment = KeycloakDeploymentBuilder.build(adapterConfig);
            }
            catch (IOException e) {
                log.error("Unable to read keycloak configuration file: {}", configFile);
                throw e;
            }
            catch (RuntimeException e) {
                log.warn("Unable to read keycloak.json", e);
                throw e;
            }
            catch (Exception e) {
                log.error("An unexpected exception occurred while processing keycloak configuration file: {}",
                    configFile);

                throw e;
            }
        }
    }

    public KeycloakDeployment getKeycloakDeployment() {
        return keycloakDeployment;
    }

    public AdapterConfig getAdapterConfig() {
        return adapterConfig;
    }

    public RequestAuthenticator getRequestAuthenticator(HttpRequest httpRequest) {
        KeycloakOIDCFacade keycloakOIDCFacade = new KeycloakOIDCFacade(httpRequest);
        return new CandlepinKeycloakRequestAuthenticator(keycloakOIDCFacade, httpRequest, keycloakDeployment);
    }
}
