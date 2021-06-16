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

package org.candlepin.auth;


import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;


/**
 * Provides convenient access to keycloak {@link AdapterConfig} and some objects that depend on it.
 *
 * The config is loaded from a file (path is configurable by setting candlepin.keycloak.config), default
 * path is /etc/candlepin/keycloak.json.
 *
 * This file can be exported from a Keycloak Admin console client configuration.
 */
public class KeycloakConfiguration {

    private AdapterConfig adapterConfig;
    private static Logger log = LoggerFactory.getLogger(KeycloakConfiguration.class);
    private KeycloakDeployment keycloakDeployment;

    @Inject
    public KeycloakConfiguration(Configuration configuration) throws Exception {
        if (configuration.getBoolean(ConfigProperties.KEYCLOAK_AUTHENTICATION)) {
            String configFile = configuration.getString(ConfigProperties.KEYCLOAK_FILEPATH);
            try (InputStream cfgStream = new FileInputStream(configFile)) {
                adapterConfig = KeycloakDeploymentBuilder.loadAdapterConfig(cfgStream);
                this.keycloakDeployment = KeycloakDeploymentBuilder.build(adapterConfig);
            }
            catch (IOException e) {
                log.error("Unable to read keycloak configuration file: {}", configFile);
                throw e;
            }
            catch (RuntimeException e) {
                log.warn("Keycloak configuration file invalid", e);
                throw e;
            }
            catch (Exception e) {
                log.error("An unexpected exception occurred while processing keycloak configuration file: {}",
                    configFile);

                throw e;
            }
        }
    }

    /**
     * Get the {@link KeycloakDeployment} that was built from the configuration.
     *
     * @return the {@link KeycloakDeployment} singleton
     */
    public KeycloakDeployment getKeycloakDeployment() {
        return keycloakDeployment;
    }

    /**
     * Get the {@link AdapterConfig} itself.
     *
     * @return the loaded {@link AdapterConfig}
     */
    public AdapterConfig getAdapterConfig() {
        return adapterConfig;
    }

    /**
     * Create and return a new {@link RequestAuthenticator} for the given request.
     *
     * @param httpRequest the request to attempt authentication against
     * @return an instance of {@link CandlepinKeycloakRequestAuthenticator}
     */
    public RequestAuthenticator createRequestAuthenticator(HttpRequest httpRequest) {
        KeycloakOIDCFacade keycloakOIDCFacade = new KeycloakOIDCFacade(httpRequest);
        return new CandlepinKeycloakRequestAuthenticator(keycloakOIDCFacade, httpRequest, keycloakDeployment);
    }
}
