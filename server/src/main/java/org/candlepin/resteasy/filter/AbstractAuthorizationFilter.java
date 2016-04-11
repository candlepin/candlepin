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
import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.common.filter.ServletLogger;
import org.candlepin.common.filter.TeeHttpServletRequest;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.model.Owner;
import org.candlepin.model.Owned;
import org.candlepin.resteasy.ResourceLocatorMap;

import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.InjectorFactory;
import org.jboss.resteasy.spi.MethodInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.metadata.ResourceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.MDC;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;

/**
 * AbstractAuthorizationFilter offers a simple wrapper around the ContainerRequestFilter
 * interface that will log the HTTP request details after the filter has finished.
 */
@Priority(Priorities.AUTHORIZATION)
public abstract class AbstractAuthorizationFilter implements ContainerRequestFilter {

    private static Logger log = LoggerFactory.getLogger(AbstractAuthorizationFilter.class);

    protected StoreFactory storeFactory;
    protected ResourceLocatorMap locatorMap;
    protected Provider<I18n> i18nProvider;

    private Marker duplicate = MarkerFactory.getMarker("DUPLICATE");


    protected AbstractAuthorizationFilter(javax.inject.Provider<I18n> i18nProvider, StoreFactory storeFactory,
        ResourceLocatorMap locatorMap) {

        this.i18nProvider = i18nProvider;
        this.storeFactory = storeFactory;
        this.locatorMap = locatorMap;
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
        throws IOException {
        try {
            // Ensure owner is put into the context for all requests
            HttpRequest request = ResteasyProviderFactory.getContextData(HttpRequest.class);
            ResourceInfo resourceInfo = ResteasyProviderFactory.getContextData(ResourceInfo.class);
            Method method = resourceInfo.getResourceMethod();
            Map<Verify, Object> argMap = getArguments(request, method);

            try {
                Owner owner = this.findOwnerFromParams(argMap);
                log.debug("FOUND OWNER FOR REQUEST: {}", owner);


                if (owner != null) {
                    ResteasyProviderFactory.pushContext(Owner.class, owner);

                    MDC.put("org", owner.getKey());
                    if (owner.getLogLevel() != null) {
                        MDC.put("orgLogLevel", owner.getLogLevel());
                    }
                }
            }
            catch (IseException e) {
                log.error("Ambiguous owners in signature for {}", method, e);
                throw e;
            }

            this.runFilter(requestContext);
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
                        ResteasyProviderFactory.getContextData(HttpServletRequest.class));
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

    protected Map<Verify, Object> getArguments(HttpRequest request, Method method) {
        ResteasyProviderFactory resourceFactory = ResteasyProviderFactory.getInstance();
        InjectorFactory injectorFactory = resourceFactory.getInjectorFactory();

        ResourceLocator locator = locatorMap.get(method);

        if (null == locator) {
            throw new IseException("Method " + method.getName() + " not registered as RESTful.");
        }

        MethodInjector methodInjector = injectorFactory.createMethodInjector(locator, resourceFactory);
        HttpResponse response = ResteasyProviderFactory.popContextData(HttpResponse.class);
        Object[] args = methodInjector.injectArguments(request, response);

        // LinkedHashMap preserves insertion order
        Map<Verify, Object> argMap = new LinkedHashMap<Verify, Object>();

        Annotation[][] allAnnotations = method.getParameterAnnotations();

        // Any occurrence of the Verify annotation means the method is not superadmin exclusive.
        for (int i = 0; i < allAnnotations.length; i++) {
            for (Annotation a : allAnnotations[i]) {
                if (a instanceof Verify) {
                    Verify v = (Verify) a;

                    if (!v.nullable() && args[i] == null) {
                        throw new IllegalStateException("Null passed to a non-nullable Verify annotation.");
                    }
                    else {
                        argMap.put(v, args[i]);
                    }
                }
            }
        }

        return argMap;
    }

    protected Owner findOwnerFromParams(Map<Verify, Object> argMap) {
        Owner contextOwner = null;

        for (Map.Entry<Verify, Object> entry : argMap.entrySet()) {
            Verify v = entry.getKey();
            Class<?> verifyType = v.value();

            if (v.nullable() && entry.getValue() == null) {
                continue;
            }

            if (Owner.class.isAssignableFrom(verifyType)) {
                Owner possibleOwner = (Owner) storeFactory.getFor(Owner.class).lookup(
                    (String) entry.getValue(), null
                );

                if (possibleOwner != null) {
                    if (contextOwner != null && !possibleOwner.equals(contextOwner)) {
                        throw new IseException(
                            "Multiple Owner parameters with @Verify found on requested method."
                        );
                    }

                    contextOwner = possibleOwner;
                }
            }
            else if (Owned.class.isAssignableFrom(verifyType)) {
                Object value = entry.getValue();

                if (value instanceof String) {
                    // This is sketchy -- we may get null if we don't pass in an owner here
                    Owned entity = (Owned) storeFactory.getFor(verifyType).lookup((String) value, null);

                    Owner possibleOwner = entity != null ? entity.getOwner() : null;
                    if (possibleOwner != null) {
                        if (contextOwner != null && !possibleOwner.equals(contextOwner)) {
                            throw new IseException(
                                "Multiple owned objects provided from different owners."
                            );
                        }

                        contextOwner = possibleOwner;
                    }
                }
                else if (value instanceof Collection) {
                    // This is also sketchy. What should happen if each element is owned by a
                    // different owner?
                    Collection<String> values = (Collection<String>) value;

                    if (values.size() > 0) {
                        List<?> elist = (List<?>) storeFactory.getFor(verifyType)
                            .lookup(values, null);

                        for (Object entity : elist) {
                            Owner possibleOwner = entity != null ? ((Owned) entity).getOwner() : null;

                            if (possibleOwner != null) {
                                if (contextOwner != null && !possibleOwner.equals(contextOwner)) {
                                    throw new IseException(
                                        "Multiple owned objects provided from different owners."
                                    );
                                }

                                contextOwner = possibleOwner;
                            }
                        }
                    }
                }
            }
        }

        return contextOwner;
    }
}
