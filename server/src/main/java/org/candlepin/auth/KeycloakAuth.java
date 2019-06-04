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
import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.TokenVerifier;
import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.ws.rs.core.Context;
import java.io.IOException;

import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.common.exceptions.CandlepinException;
import org.candlepin.common.exceptions.ServiceUnavailableException;
import org.candlepin.common.resteasy.auth.AuthUtil;
import org.keycloak.KeycloakSecurityContext;
import org.candlepin.service.UserServiceAdapter;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.ServerRequest;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.KeycloakDeployment;

import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import javax.inject.Provider;

/**
 * KeycloakAuth
 */
public class KeycloakAuth extends UserAuth implements AuthProvider {

    @Context private ServletRequest servletRequest;
    @Context private ServletResponse servletResponse;

    private static Logger log = LoggerFactory.getLogger(KeycloakAuth.class);
    protected AdapterDeploymentContext deploymentContext;
    private AccessTokenResponse response = null;
    private KeycloakDeployment kd;
    private KeycloakAdapterConfiguration keycloakAdapterConfiguration;
    private RefreshableKeycloakSecurityContext securityContext = null;
    private static AdapterConfig adapterConfig = null;
    private Configuration configuration;

    @Inject
    public KeycloakAuth(UserServiceAdapter userServiceAdapter, Provider<I18n> i18nProvider,
        PermissionFactory permissionFactory, Configuration configuration,
        KeycloakAdapterConfiguration keycloakAdapterConfiguration) {
        super(userServiceAdapter, i18nProvider, permissionFactory);
        this.configuration = configuration;
        adapterConfig = keycloakAdapterConfiguration.getAdapterConfig();
        kd = keycloakAdapterConfiguration.getKeycloakDeployment();
        this.keycloakAdapterConfiguration = keycloakAdapterConfiguration;
        deploymentContext = new AdapterDeploymentContext(kd);

    }

    @Override
    public Principal getPrincipal(HttpRequest httpRequest) {
        try {
            String auth = AuthUtil.getHeader(httpRequest, "Authorization");

            if (!auth.isEmpty()) {

                String[] authArray = auth.split(" ");
                if (authArray[0].toUpperCase().equals("BASIC")) {
                    return null;
                }
                else {
                    String tokenType = TokenVerifier.create(authArray[1],
                        JsonWebToken.class).getToken().getType();
                    switch (tokenType) {
                        case TokenUtil.TOKEN_TYPE_BEARER:
                            handleBearerToken(httpRequest);
                            break;
                        case TokenUtil.TOKEN_TYPE_REFRESH:
                            handleRefreshToken(httpRequest, auth);
                            break;
                        default:
                            break;
                    }
                }
                KeycloakSecurityContext keycloakSecurityContext = (KeycloakSecurityContext)
                    httpRequest.getAttribute(KeycloakSecurityContext.class.getName());
                if (keycloakSecurityContext != null) {
                    String userName = keycloakSecurityContext.getToken().getPreferredUsername();
                    Principal principal = createPrincipal(userName);
                    return principal;
                }
            }
            else {
                // if auth header is empty
                return null;
            }
        }
        catch (CandlepinException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ServiceUnavailableException(i18nProvider.get().tr("Keycloak Authentication failed"));
        }
        return null;
    }


    public void handleBearerToken(HttpRequest httpRequest) {
        RequestAuthenticator requestAuthenticator = keycloakAdapterConfiguration.
            getRequestAuthenticator(httpRequest);
        requestAuthenticator.authenticate();
    }

    private void handleRefreshToken(HttpRequest httpRequest, String auth) throws IOException,
        ServerRequest.HttpFailure, VerificationException {
        String[] arrAut = auth.split(" ");
        response = ServerRequest.invokeRefresh(kd, arrAut[1]);
        String tokenString = response.getToken();
        AccessToken token = TokenVerifier.create(response.getToken(), AccessToken.class).getToken();
        RefreshableKeycloakSecurityContext refreshableKeycloakSecurityContext = new
            RefreshableKeycloakSecurityContext(kd, null, tokenString, token, null, null, arrAut[1]);
        httpRequest.setAttribute(KeycloakSecurityContext.class.getName(), refreshableKeycloakSecurityContext);
    }
}
