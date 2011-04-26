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
package org.fedoraproject.candlepin.resteasy.interceptor;

import java.util.ArrayList;
import java.util.Date;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.NoAuthPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.exceptions.UnauthorizedException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * NoAuthInterceptor
 */
@Provider
@ServerInterceptor
public class AuthInterceptor implements PreProcessInterceptor {
    private static Logger log = Logger.getLogger(AuthInterceptor.class);

    private Injector injector;
    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private Config config;
    private UserServiceAdapter userService;
    private AuthProvider[] providers = new AuthProvider[0];
    private int providerCount = 0;

    @Inject
    public AuthInterceptor(Config config, UserServiceAdapter userService,
        OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
        Injector injector) {
        super();
        this.consumerCurator = consumerCurator;
        this.injector = injector;
        this.config = config;
        this.userService = userService;
        this.ownerCurator = ownerCurator;
        this.setupAuthStrategies();
    }

    /**
     * Set up the various providers which can be used to authenticate the user
     */
    public void setupAuthStrategies() {
        ArrayList<AuthProvider> providers = new ArrayList<AuthProvider>();

        // use oauth
        if (config.oAuthEnabled()) {
            providerCount++;
            log.debug("OAuth Authentication is enabled.");
            TrustedConsumerAuth consumerAuth = new TrustedConsumerAuth(consumerCurator);
            TrustedUserAuth userAuth = new TrustedUserAuth(userService, ownerCurator,
                injector);
            TrustedExternalSystemAuth systemAuth = new TrustedExternalSystemAuth();
            providers
                .add(new OAuth(consumerAuth, userAuth, systemAuth, injector, config));
        }

        // basic http access
        if (config.basicAuthEnabled()) {
            providerCount++;
            log.debug("Basic Authentication is enabled.");
            providers.add(new BasicAuth(userService, ownerCurator, injector));
        }
        // consumer certificates
        if (config.sslAuthEnabled()) {
            providerCount++;
            log.debug("Certificate Based Authentication is enabled.");
            providers.add(new SSLAuth(consumerCurator));
        }
        // trusted headers
        if (config.trustedAuthEnabled()) {
            providerCount = providerCount + 2;
            log.debug("Trusted Authentication is enabled.");
            providers.add(new TrustedConsumerAuth(consumerCurator));
            providers.add(new TrustedUserAuth(userService, ownerCurator,
                injector));
        }
        this.providers = providers.toArray(this.providers);
    }

    /**
     * Interrogates the request and sets the principal for the request.
     * 
     * @throws WebApplicationException when no auths result in a valid principal
     * @throws Failure when there is an unkown failure in the code
     * @return the Server Response
     */
    public ServerResponse preProcess(HttpRequest request, ResourceMethod method)
        throws Failure, WebApplicationException {

        I18n i18n = injector.getInstance(I18n.class);
        Principal principal = null;
        boolean noAuthAllowed = false;

        if (log.isDebugEnabled()) {
            log.debug("Authentication check for " + request.getUri().getPath());
        }

        // Check to see if authentication is required.
        AllowRoles roles = method.getMethod().getAnnotation(AllowRoles.class);
        if (roles != null) {
            for (Role role : roles.roles()) {
                if (role == Role.NO_AUTH) {
                    noAuthAllowed = true;
                }
            }
        }

        // No authentication is required, give a no auth principal
        if (noAuthAllowed) {
            log.debug("No auth allowed for resource; setting NoAuth principal");
            principal = new NoAuthPrincipal();
        }

        // Check all the configured providers
        for (int x = 0; (x < providerCount) && (principal == null); x++) {
            principal = providers[x].getPrincipal(request);
            if (principal != null) {
                break;
            }
        }

        if (principal != null) {
            // Expose the principal for Resteasy to inject via @Context
            ResteasyProviderFactory.pushContext(Principal.class, principal);

            if (principal.isConsumer()) {
                // HACK: We need to do this after the principal has been pushed,
                // lest our security settings start getting upset when we try to
                // update a consumer without any roles:
                ConsumerPrincipal p = (ConsumerPrincipal) principal;
                Consumer c = p.consumer();
                updateLastCheckin(c);
            }

            return null;
        }

        throw new UnauthorizedException(i18n.tr("Invalid Credentials"));
    }

    private void updateLastCheckin(Consumer consumer) {
        consumer.setLastCheckin(new Date());
        consumerCurator.update(consumer);
    }
}
