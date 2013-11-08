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

import org.candlepin.auth.Principal;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.resteasy.interceptor.AuthInterceptor;
import org.candlepin.resteasy.interceptor.AuthUtil;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.xnap.commons.i18n.I18n;

/**
 * This is a testing class that converts our AuthInterceptor into a Method interceptor
 * so that we can use it during testing without having to set up an entire RestEasy
 * environment.
 *
 * Since the AuthInterceptor class requires an Injector and method interceptors
 * are created in Guice modules, we encounter a problem.  It's not
 * really possible to grab your own injector within your module (see
 * http://stackoverflow.com/questions/15989991/accessing-guice-injector-in-its-module ).
 * So we have to use deferred binding via the requestInjection() method and this operates
 * on field injection.  Instead of messing with the internals of AuthInterceptor to
 * allow this, we have the AuthMethodInterceptorFactory class which creates the
 * AuthInterceptor at method invocation time after all our dependencies have been
 * injected.
 */
public class AuthMethodInterceptorFactory implements MethodInterceptor {

    @Inject private Injector injector;
    @Inject private I18n i18n;
    @Inject private Provider<Principal> principalProvider;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        AuthMethodInterceptor interceptor = new AuthMethodInterceptor(injector,
            i18n);
        return interceptor.invoke(invocation);
    }

    public class AuthMethodInterceptor extends AuthInterceptor implements
        MethodInterceptor {

        public AuthMethodInterceptor(Injector injector, I18n i18n) {
            super(null, null, null, null, injector, i18n);
        }

        public void setupAuthStrategies() {
            // do nothing here since we don't establish a principal with this class
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            SecurityHole securityHole = AuthUtil.checkForSecurityHoleAnnotation(
                invocation.getMethod());

            Principal principal = principalProvider.get();
            verifyAccess(invocation.getMethod(), principal, getArguments(invocation),
                securityHole);

            return invocation.proceed();
        }

        private Object[] getArguments(MethodInvocation invocation) {
            return invocation.getArguments();
        }
    }
}
