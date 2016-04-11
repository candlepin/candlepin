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

import org.candlepin.resteasy.ResourceLocatorMap;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xnap.commons.i18n.I18n;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;

/**
 * SecurityHoleAuthorizationFilter is a no-op JAX-RS 2.0 Filter that is applied
 * to methods that have the SecurityHole annotation applied to them.  The
 * AuthorizationFeature class is what determines whether to register this filter to
 * a method.
 */
@Priority(Priorities.AUTHORIZATION)
public class SecurityHoleAuthorizationFilter extends AbstractAuthorizationFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHoleAuthorizationFilter.class);

    @Inject
    public SecurityHoleAuthorizationFilter(javax.inject.Provider<I18n> i18nProvider,
        StoreFactory storeFactory, ResourceLocatorMap locatorMap) {

        super(i18nProvider, storeFactory, locatorMap);
    }

    @Override
    void runFilter(ContainerRequestContext requestContext) {
        log.debug("NO authorization check for {}", requestContext.getUriInfo().getPath());
        // Do nothing
    }
}
