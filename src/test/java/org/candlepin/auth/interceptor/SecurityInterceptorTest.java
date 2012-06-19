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
package org.candlepin.auth.interceptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.guice.I18nProvider;
import org.candlepin.model.User;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

/**
 * SecurityInterceptorTest
 */
public class SecurityInterceptorTest implements Provider<Principal> {

    private Injector injector;
    private Principal principal = mock(Principal.class);

    @Before
    public void init() {
        injector = Guice.createInjector(new SecurityInterceptorModule(this));
    }

    @Test
    public void invokeNoAuth() {
        TestClass t = injector.getInstance(TestClass.class);
        assertEquals("noauth", t.noAuthSecurityHole());
    }

    @Test
    public void invokeWideOpen() {
        TestClass t = injector.getInstance(TestClass.class);
        assertEquals("hole", t.securityHole());
    }

    @Test
    public void invokeRegularFullAccess() {
        when(principal.hasFullAccess()).thenReturn(true);
        TestClass t = injector.getInstance(TestClass.class);
        assertEquals("regular", t.regularMethod());
    }

    @Test(expected = ForbiddenException.class)
    public void invokeRegularNotEnoughPrivileges() {
        TestClass t = injector.getInstance(TestClass.class);
        t.regularMethod();
    }

    @Test
    public void invokeWithVerify() {
        when(principal.canAccess(
            any(User.class), any(Access.class))).thenReturn(true);
        TestClass t = injector.getInstance(TestClass.class);
        assertEquals("luser", t.verifyMethod("luser"));
    }

    @Test(expected = IseException.class)
    public void invalidVerify() {
        TestClass t = injector.getInstance(TestClass.class);
        t.invalidVerify("foo");
    }

    @Override
    public Principal get() {
        // implements Provider<Principal> so we can change
        // the behavior of the Principal used by the
        // interceptor
        return principal;
    }

    /**
     * TestClass used to allow the SecurityInterceptor to run
     * through its paces.
     */
    private static class TestClass {
        public TestClass() {
            // do nothing
        }

        @SecurityHole(noAuth = true)
        public String noAuthSecurityHole() {
            return "noauth";
        }

        @SecurityHole
        public String securityHole() {
            return "hole";
        }

        public String regularMethod() {
            return "regular";
        }

        public String verifyMethod(@Verify(User.class) String username) {
            return username;
        }

        public void invalidVerify(@Verify(Injector.class) String str) {
            // do nothing
        }
    }

    private static class SecurityInterceptorModule extends AbstractModule {
        private Provider<Principal> daProvider;

        public SecurityInterceptorModule(Provider<Principal> daProvider) {
            this.daProvider = daProvider;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void configure() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getLocale()).thenReturn(Locale.US);

            bind(TestClass.class);
            bind(Principal.class).toProvider(daProvider);
            bind(I18n.class).toProvider(I18nProvider.class);
            bind(HttpServletRequest.class).toInstance(req);

            Matcher resourcePkgMatcher = Matchers.inPackage(
                Package.getPackage("org.candlepin.auth.interceptor"));
            SecurityInterceptor securityEnforcer = new SecurityInterceptor();
            requestInjection(securityEnforcer);
            bindInterceptor(resourcePkgMatcher, Matchers.any(), securityEnforcer);
        }
    }
}
