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
package org.candlepin.resteasy.filter;

import org.candlepin.auth.AuthProvider;
import org.candlepin.auth.BasicAuth;
import org.candlepin.auth.KeycloakAuth;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.OAuth;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SSLAuth;
import org.candlepin.auth.TrustedConsumerAuth;
import org.candlepin.auth.TrustedUserAuth;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotAuthorizedException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.resteasy.AnnotationLocator;

import com.google.inject.Inject;
import com.google.inject.Injector;

import io.swagger.jaxrs.listing.ApiListingResource;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;


/**
 * The AuthenticationFilter is responsible for populating the JAXRS SecurityContext
 */
@Priority(Priorities.AUTHENTICATION)
@Provider
public class AuthenticationFilter implements ContainerRequestFilter {
    private static Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Context
    private HttpServletRequest request;

    private ConsumerCurator consumerCurator;
    private Injector injector;
    private Configuration config;
    private AnnotationLocator annotationLocator;
    private List<AuthProvider> providers = new ArrayList<>();

    @Inject
    public AuthenticationFilter(Configuration config,
        ConsumerCurator consumerCurator,
        DeletedConsumerCurator deletedConsumerCurator,
        Injector injector,
        AnnotationLocator annotationLocator) {
        this.consumerCurator = consumerCurator;
        this.injector = injector;
        this.config = config;
        this.annotationLocator = annotationLocator;

        setupAuthStrategies();
    }

    /**
     * Used to pass mock object during unit testing
     */
    public void setHttpServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    public void setupAuthStrategies() {
        // use keycloak authentication
        if (config.getBoolean(ConfigProperties.KEYCLOAK_AUTHENTICATION)) {
            providers.add(injector.getInstance(KeycloakAuth.class));
        }
        // use oauth
        if (config.getBoolean(ConfigProperties.OAUTH_AUTHENTICATION)) {
            providers.add(injector.getInstance(OAuth.class));
        }
        // http basic auth access
        if (config.getBoolean(ConfigProperties.BASIC_AUTHENTICATION)) {
            providers.add(injector.getInstance(BasicAuth.class));
        }
        // consumer certificates
        if (config.getBoolean(ConfigProperties.SSL_AUTHENTICATION)) {
            providers.add(injector.getInstance(SSLAuth.class));
        }
        // trusted headers
        if (config.getBoolean(ConfigProperties.TRUSTED_AUTHENTICATION)) {
            providers.add(injector.getInstance(TrustedConsumerAuth.class));
            providers.add(injector.getInstance(TrustedUserAuth.class));
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
        throws IOException {
        log.debug("Authentication check for {}", requestContext.getUriInfo().getPath());

        HttpRequest httpRequest = ResteasyContext.getContextData(HttpRequest.class);
        ResourceInfo resourceInfo = ResteasyContext.getContextData(ResourceInfo.class);
        Method method = resourceInfo.getResourceMethod();

        SecurityHole hole = annotationLocator.getAnnotation(method, SecurityHole.class);
        Principal principal = null;

        if (hole != null && hole.anon()) {
            principal = new NoAuthPrincipal();
        }
        else if (resourceInfo.getResourceClass().equals(ApiListingResource.class)) {
            log.debug("Swagger API request made; no principal required.");
            principal = new NoAuthPrincipal();
        }
        else {
            for (AuthProvider provider : providers) {
                principal = provider.getPrincipal(httpRequest);

                if (principal != null) {
                    log.debug("Establishing principal with {}", provider.getClass().getName());
                    break;
                }
            }
        }

        /* At this point, there is no provider that has given a valid principal,
         * so we use the NoAuthPrincipal here if it is allowed. */
        if (principal == null) {
            if (hole != null && hole.noAuth()) {
                log.debug("No auth allowed for resource; setting NoAuth principal");
                principal = new NoAuthPrincipal();
            }
            else if (!config.getBoolean(ConfigProperties.AUTH_OVER_HTTP) && !request.isSecure()) {
                throw new BadRequestException("Please use SSL when accessing protected resources");
            }
            else {
                throw new NotAuthorizedException("Invalid credentials.");
            }
        }

        SecurityContext securityContext = new CandlepinSecurityContext(principal);
        requestContext.setSecurityContext(securityContext);

        // Push the principal into the context for the PrincipalProvider to access directly
        ResteasyContext.pushContext(Principal.class, principal);
    }

}
