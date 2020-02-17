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
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.common.filter.ServletLogger;
import org.candlepin.common.filter.TeeHttpServletRequest;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.lang.reflect.Method;

import javax.annotation.Priority;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;



/**
 * AbstractAuthorizationFilter offers a simple wrapper around the ContainerRequestFilter
 * interface that will log the HTTP request details after the filter has finished.
 */
@Priority(Priorities.AUTHORIZATION)
public abstract class AbstractAuthorizationFilter implements ContainerRequestFilter {
    private static Logger log = LoggerFactory.getLogger(AbstractAuthorizationFilter.class);

    protected Provider<I18n> i18nProvider;
    private Marker duplicate = MarkerFactory.getMarker("DUPLICATE");

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            runFilter(requestContext);
        }
        finally {
            /* If a turbo filter returns ACCEPT, a logger will return true for
             * isEnabled for any level.  Since we have a turbo filter that sets
             * log level on a per org basis, this block will execute if our org
             * is set to log at debug or below.
             *
             * We log at this point in the processing because we want the owner
             * to be placed in the MDC by the VerifyAuthorizationFilter.
             */
            if (log.isDebugEnabled()) {
                /* If the logging filter is debug enabled, we want to mark these
                 * log statements as duplicates so we can filter them out if we
                 * want.
                 */
                Marker m =
                    (LoggerFactory.getLogger(LoggingFilter.class).isDebugEnabled()) ?
                    duplicate : null;
                try {
                    TeeHttpServletRequest teeRequest = new TeeHttpServletRequest(
                        ResteasyContext.getContextData(HttpServletRequest.class));
                    log.debug(m, "{}", ServletLogger.logBasicRequestInfo(teeRequest));
                    log.debug(m, "{}", ServletLogger.logRequest(teeRequest));
                }
                catch (IOException e) {
                    log.info("Couldn't log request information", e);
                }
            }
        }
    }

    abstract void runFilter(ContainerRequestContext requestContext);

    protected void denyAccess(Principal principal, Method method) {
        log.warn("Refusing principal: {} access to: {} ", principal, method.getName());

        String error = "Insufficient permissions";
        throw new ForbiddenException(i18nProvider.get().tr(error));
    }
}
