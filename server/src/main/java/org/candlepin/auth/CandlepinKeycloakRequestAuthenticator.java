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

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.AdapterUtils;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.OAuthRequestAuthenticator;
import org.keycloak.adapters.OidcKeycloakAccount;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.KeycloakAccount;

import java.util.Set;

/**
 * Handles interactions between Candlepin and the Keycloak Adapter code.
 *
 * Namely, this class is responsible for storing the resulting security context from Keycloak as an attribute
 * named "org.keycloak.KeycloakSecurityContext" in the {@link HttpRequest}. This is consistent with
 * other Java-based integration with Keycloak (e.g. org.keycloak.adapters.servlet.FilterRequestAuthenticator).
 *
 * Because we don't directly support any interactive flows, the oauth-named methods are stubbed out.
 */
public class CandlepinKeycloakRequestAuthenticator extends RequestAuthenticator {

    private final HttpRequest httpRequest;

    public CandlepinKeycloakRequestAuthenticator(KeycloakOIDCFacade keycloakOIDCFacade,
        HttpRequest httpRequest, KeycloakDeployment keycloakDeployment) {
        super(keycloakOIDCFacade, keycloakDeployment);
        this.httpRequest = httpRequest;
    }

    @Override
    protected OAuthRequestAuthenticator createOAuthAuthenticator() {
        return null;
    }

    @Override
    protected void completeOAuthAuthentication(KeycloakPrincipal<RefreshableKeycloakSecurityContext>
        principal) {
         //intentionally left empty
    }

    @Override
    protected void completeBearerAuthentication(
        KeycloakPrincipal<RefreshableKeycloakSecurityContext> principal, String method) {

        RefreshableKeycloakSecurityContext securityContext = principal.getKeycloakSecurityContext();

        final Set<String> roles = AdapterUtils.getRolesFromSecurityContext(securityContext);

        httpRequest.setAttribute(KeycloakSecurityContext.class.getName(), securityContext);
        OidcKeycloakAccount account = new OidcKeycloakAccount() {

            @Override
            public java.security.Principal getPrincipal() {
                return principal;
            }

            @Override
            public Set<String> getRoles() {
                return roles;
            }

            @Override
            public KeycloakSecurityContext getKeycloakSecurityContext() {
                return securityContext;
            }

        };
        // need this here to obtain UserPrincipal
        httpRequest.setAttribute(KeycloakAccount.class.getName(), account);
    }

    @Override
    protected String changeHttpSessionId(boolean create) {
        return null; // candlepin does not support sessions for keycloak
    }
}
