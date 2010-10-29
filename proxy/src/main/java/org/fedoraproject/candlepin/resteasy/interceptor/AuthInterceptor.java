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

    private BasicAuth basicAuth;
    private SSLAuth sslAuth;
    private TrustedConsumerAuth trustedConsumerAuth;
    private boolean sslAuthEnabled;
    private Injector injector;
    private ConsumerCurator consumerCurator;
    private Config config;

    @Inject
    public AuthInterceptor(Config config, UserServiceAdapter userService,
        OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
        Injector injector) {
        super();
        basicAuth = new BasicAuth(userService, ownerCurator, injector);
        sslAuth = new SSLAuth(consumerCurator);
        trustedConsumerAuth = new TrustedConsumerAuth(consumerCurator);

        sslAuthEnabled = config.sslAuthEnabled();
        this.consumerCurator = consumerCurator;
        this.injector = injector;
        this.config = config;
    }

    @Override
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

        // Check basic auth for a user.
        if (principal == null) {
            principal = basicAuth.getPrincipal(request);
        }

        // Only use trusted headers if it is configured
        if (config.useTrustedAuth()) {
            // look for a trusted user
            if (principal == null) {
                // nada
            }

            // look for a trusted consumer
            if (principal == null) {
                principal = trustedConsumerAuth.getPrincipal(request);
            }
        }

        // Finally, look for a consumer certificate
        if (principal == null) {
            if (sslAuthEnabled) {
                principal = sslAuth.getPrincipal(request);
            }
            else {
                log.debug("SSLAuth disabled, setting NoAuth Principal");
                principal = new NoAuthPrincipal();
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

        throw new UnauthorizedException(i18n.tr("Invalid username or password"));
    }

    private void updateLastCheckin(Consumer consumer) {
        consumer.setLastCheckin(new Date());
        consumerCurator.update(consumer);
    }
}
