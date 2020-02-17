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
package org.candlepin.test;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.filter.StoreFactory;
import org.candlepin.resteasy.filter.VerifyAuthorizationFilter;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.xnap.commons.i18n.I18n;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is a testing class that converts our Authorization interceptor
 * into a method interceptor so that we can use it during testing without having to
 * set up an entire RestEasy environment.
 *
 * Since the VerifyAuthorizationFilter class requires an Injector and method interceptors
 * are created in Guice modules, we encounter a problem.  It's not
 * really possible to grab your own injector within your module (see
 * http://stackoverflow.com/questions/15989991/accessing-guice-injector-in-its-module ).
 * So we have to use deferred binding via the requestInjection() method and this operates
 * on field injection.  Instead of messing with the internals of VerifyAuthorizationFilter to
 * allow this, we have this Factory class which creates the interceptor at method
 * invocation time after all our dependencies have been injected.
 */
public class VerifyAuthorizationFilterFactory implements MethodInterceptor {
    @Inject private StoreFactory storeFactory;
    @Inject private Provider<I18n> i18nProvider;
    @Inject private Provider<Principal> principalProvider;
    @Inject private AnnotationLocator annotationLocator;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        AuthorizationMethodInterceptor methodInterceptor = new AuthorizationMethodInterceptor(
            i18nProvider, storeFactory);
        return methodInterceptor.invoke(invocation);
    }

    public class AuthorizationMethodInterceptor extends VerifyAuthorizationFilter
        implements MethodInterceptor {

        public AuthorizationMethodInterceptor(Provider<I18n> i18nProvider, StoreFactory storeFactory) {
            super(i18nProvider, storeFactory, annotationLocator);
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Principal principal = principalProvider.get();

            /* In real life, the AuthorizationFeature DynamicFeature decides if the
             * SecurityHole exception applies to a method. */
            if (invocation.getMethod().isAnnotationPresent(SecurityHole.class)) {
                return invocation.proceed();
            }

            Method method = invocation.getMethod();
            Access defaultAccess = getDefaultAccess(method);
            Map<Verify, Object> argMap = getArguments(invocation);

            /* Under normal circumstances the AuthorizationFeature has already determined
             * that VerifyAuthorizationFilter doesn't apply to superadmin only methods.
             */
            if (argMap.isEmpty()) {
                if (principal.hasFullAccess()) {
                    return invocation.proceed();
                }
                else {
                    denyAccess(principal, method);
                }
            }

            if (!hasAccess(argMap, principal, defaultAccess)) {
                denyAccess(principal, method);
            }

            return invocation.proceed();
        }

        private Map<Verify, Object> getArguments(MethodInvocation invocation) {
            Object[] args = invocation.getArguments();

            Annotation[][] allAnnotations = invocation.getMethod().getParameterAnnotations();

            Map<Verify, Object> argMap = new LinkedHashMap<>();

            // Any occurrence of the Verify annotation means the method is not superadmin exclusive.
            for (int i = 0; i < allAnnotations.length; i++) {
                for (Annotation a : allAnnotations[i]) {
                    if (a instanceof Verify) {
                        Verify v = (Verify) a;

                        if (!v.nullable() && args[i] == null) {
                            throw new IllegalStateException(
                                "Null passed to a non-nullable Verify annotation.");
                        }
                        else {
                            argMap.put(v, args[i]);
                        }
                    }
                }
            }

            return argMap;
        }
    }
}
