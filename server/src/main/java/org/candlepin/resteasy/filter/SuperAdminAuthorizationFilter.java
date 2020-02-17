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

import org.candlepin.auth.Principal;

import com.google.inject.Inject;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.lang.reflect.Method;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;

/**
 * This class is responsible for determining whether or not the principal
 * is a superadmin.  Notice there is no Provider annotation on this class.
 * That is because the AuthorizationFeature takes care of registering
 * this filter to the appropriate methods at servlet initialization time.
 */
@Priority(Priorities.AUTHORIZATION)
public class SuperAdminAuthorizationFilter extends AbstractAuthorizationFilter {
    private static final Logger log = LoggerFactory.getLogger(SuperAdminAuthorizationFilter.class);

    @Inject
    public SuperAdminAuthorizationFilter(javax.inject.Provider<I18n> i18nProvider) {
        this.i18nProvider = i18nProvider;
    }

    @Override
    public void runFilter(ContainerRequestContext requestContext) {
        log.debug("Authorization check for {}", requestContext.getUriInfo().getPath());

        Principal principal = (Principal) requestContext.getSecurityContext().getUserPrincipal();
        ResourceInfo resourceInfo = ResteasyContext.getContextData(ResourceInfo.class);
        Method method = resourceInfo.getResourceMethod();

        if (!principal.hasFullAccess()) {
            denyAccess(principal, method);
        }
    }
}
