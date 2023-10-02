/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.resteasy.filter.AuthUtil;
import org.candlepin.service.UserServiceAdapter;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.TokenVerifier;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.ServerRequest;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.ws.rs.core.Context;



/**
 * AuthenticationProvider that accepts an {@link AccessToken} or
 * {@link org.keycloak.representations.RefreshToken} as an HTTP Bearer token, and then verifies the token
 * against a configured Keycloak server.
 *
 * This provider is skipped if BASIC auth is present in the request or if the Authorization header is
 * empty/missing.
 */
public class KeycloakAuth extends UserAuth implements AuthProvider {
    private static Logger log = LoggerFactory.getLogger(KeycloakAuth.class);

    private static final String AUTH_TYPE = "Bearer";

    @Context private ServletRequest servletRequest;
    @Context private ServletResponse servletResponse;

    private KeycloakConfiguration keycloakConfig;

    @Inject
    public KeycloakAuth(UserServiceAdapter userServiceAdapter, Provider<I18n> i18nProvider,
        PermissionFactory permissionFactory, KeycloakConfiguration keycloakConfig) {

        super(userServiceAdapter, i18nProvider, permissionFactory);
        this.keycloakConfig = keycloakConfig;
    }

    @Override
    public Principal getPrincipal(HttpRequest httpRequest) {
        String auth = AuthUtil.getHeader(httpRequest, "Authorization");
        if (auth == null || auth.isEmpty()) {
            // No authorization header -- abort
            return null;
        }

        String[] authArray = auth.split(" ");
        if (!AUTH_TYPE.equalsIgnoreCase(authArray[0]) || authArray.length != 2) {
            // Wrong authorization type, or not the format we're expecting -- abort
            return null;
        }

        try {
            String tokenType = TokenVerifier.create(authArray[1], JsonWebToken.class)
                .getToken()
                .getType();

            if (tokenType == null || tokenType.isEmpty()) {
                // Undefined or otherwise absent token type -- abort
                return null;
            }

            switch (tokenType) {
                case TokenUtil.TOKEN_TYPE_BEARER:
                    handleBearerToken(httpRequest);
                    break;

                case TokenUtil.TOKEN_TYPE_REFRESH:
                    handleRefreshToken(httpRequest, authArray[1]);
                    break;

                default:
                    log.debug("Ignoring unsupported token type: {}", tokenType);
                    break;
            }

            KeycloakSecurityContext keycloakSecurityContext = (KeycloakSecurityContext)
                httpRequest.getAttribute(KeycloakSecurityContext.class.getName());

            if (keycloakSecurityContext != null && keycloakSecurityContext.getToken() != null) {
                String userName = keycloakSecurityContext.getToken().getPreferredUsername();
                log.info("Token type used for authentication: Bearer");
                return ((UserPrincipal) createPrincipal(userName))
                    .setAuthenticationMethod(AuthenticationMethod.KEYCLOAK);
            }
        }
        catch (VerificationException e) {
            log.debug("Keycloak token validation failed:", e);

            // Impl note:
            // Since we're using a common/standard auth type (bearer), we can't immediately fail
            // out here, as it's possible the token will be verified by another provider
        }

        return null;
    }

    /**
     * Verify the access token directly.
     *
     * @param httpRequest the request to authenticate
     */
    private void handleBearerToken(HttpRequest httpRequest) {
        this.keycloakConfig.createRequestAuthenticator(httpRequest)
            .authenticate();
    }

    /**
     * Exchange the refresh token for an access token, and verify the resulting access token.
     *
     * @param httpRequest the request to authenticate
     * @param authCredentials the authorization credentials
     * @throws ServerRequest.HttpFailure if the refresh exchange fails
     * @throws VerificationException if the resulting token fails verification
     */
    private void handleRefreshToken(HttpRequest httpRequest, String authCredentials)
        throws VerificationException {

        try {
            KeycloakDeployment keycloakDeployment = this.keycloakConfig.getKeycloakDeployment();

            AccessTokenResponse response = ServerRequest.invokeRefresh(keycloakDeployment, authCredentials);
            String tokenString = response.getToken();
            AccessToken token = TokenVerifier.create(response.getToken(), AccessToken.class).getToken();

            RefreshableKeycloakSecurityContext refreshableKeycloakSecurityContext = new
                RefreshableKeycloakSecurityContext(keycloakDeployment, null, tokenString, token, null, null,
                authCredentials);

            httpRequest.setAttribute(KeycloakSecurityContext.class.getName(),
                refreshableKeycloakSecurityContext);
        }
        catch (Exception e) {
            throw new VerificationException(e);
        }
    }
}
