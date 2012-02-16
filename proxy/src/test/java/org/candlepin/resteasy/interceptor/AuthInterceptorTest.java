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
package org.candlepin.resteasy.interceptor;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.config.Config;
import org.candlepin.exceptions.UnauthorizedException;
import org.candlepin.guice.I18nProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * AuthInterceptorTest
 */
public class AuthInterceptorTest {
    private AuthInterceptor interceptor;
    private Config config;
    private UserServiceAdapter usa;
    private OwnerCurator oc;
    private ConsumerCurator cc;
    private DeletedConsumerCurator dcc;
    private Injector injector;

    @Before
    public void init() {
        config = new Config();
        usa = mock(UserServiceAdapter.class);
        oc = mock(OwnerCurator.class);
        cc = mock(ConsumerCurator.class);
        dcc = mock(DeletedConsumerCurator.class);
        injector = Guice.createInjector(new AuthInterceptorTestModule());
        interceptor = new AuthInterceptor(config, usa, oc, cc, dcc, injector);
    }

    @Test(expected = UnauthorizedException.class)
    public void noSecurityHoleNoPrincipal() throws Exception {
        Method method = FakeResource.class.getMethod("someMethod", int.class, String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);

        interceptor.preProcess(req, rmethod);
    }

    @Test
    public void noSecurityHole() throws Exception {
        Method method = FakeResource.class.getMethod("someMethod", int.class, String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        req.header("Authorization", "BASIC QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);
        when(usa.validateUser(eq("Aladdin"), eq("open sesame"))).thenReturn(true);
        when(usa.findByLogin(eq("Aladdin"))).thenReturn(new User("Aladdin", "open sesame"));

        interceptor.preProcess(req, rmethod);

        Principal p = ResteasyProviderFactory.getContextData(Principal.class);
        assertTrue(p instanceof UserPrincipal);
    }

    @Test
    public void securityHoleWithNoAuth() throws Exception {
        Method method = FakeResource.class.getMethod("noAuthMethod", String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);

        interceptor.preProcess(req, rmethod);

        Principal p = ResteasyProviderFactory.getContextData(Principal.class);
        assertTrue(p instanceof NoAuthPrincipal);
    }

    @Test
    public void securityHoleWithAuth() throws Exception {
        Method method = FakeResource.class.getMethod("annotatedMethod", String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        req.header("Authorization", "BASIC QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);
        when(usa.validateUser(eq("Aladdin"), eq("open sesame"))).thenReturn(true);
        when(usa.findByLogin(eq("Aladdin"))).thenReturn(new User("Aladdin", "open sesame"));

        interceptor.preProcess(req, rmethod);

        Principal p = ResteasyProviderFactory.getContextData(Principal.class);
        assertTrue(p instanceof UserPrincipal);
    }

    @Test
    public void noSecurityHoleWithConsumer() throws Exception {
        Method method = FakeResource.class.getMethod("someMethod", int.class, String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);

        Consumer c = mock(Consumer.class);
        when(cc.getConsumer(eq("machine.example.com"))).thenReturn(c);

        // create mock certs to trigger SSLAuth provider
        X509Certificate[] certs = new X509Certificate[1];
        X509Certificate cert = mock(X509Certificate.class);
        certs[0] = cert;
        req.setAttribute("javax.servlet.request.X509Certificate", certs);

        java.security.Principal p = mock(java.security.Principal.class);
        when(p.getName()).thenReturn("CN=machine.example.com, C=US, L=Raleigh");
        when(cert.getSubjectDN()).thenReturn(p);

        interceptor.preProcess(req, rmethod);

        Principal p1 = ResteasyProviderFactory.getContextData(Principal.class);
        assertTrue(p1 instanceof ConsumerPrincipal);
        verify(cc).updateLastCheckin(eq(c));
    }

    /**
     * FakeResource simply to create a Method object to pass down into
     * the interceptor.
     */
    public static class FakeResource {
        public String someMethod(int i, String str) {
            return str + Integer.valueOf(i);
        }

        @SecurityHole
        public String annotatedMethod(String str) {
            return str;
        }

        @SecurityHole(noAuth = true)
        public String noAuthMethod(String str) {
            return str;
        }
    }

    /*
     * simple Guice Module to supply the I18n class
     * TODO: make this a common module for all tests to be able to use.
     */
    private static class AuthInterceptorTestModule extends AbstractModule {
        @Override
        protected void configure() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getLocale()).thenReturn(Locale.US);

            bind(I18n.class).toProvider(I18nProvider.class).asEagerSingleton();
            bind(HttpServletRequest.class).toInstance(req);
        }
    }
}
