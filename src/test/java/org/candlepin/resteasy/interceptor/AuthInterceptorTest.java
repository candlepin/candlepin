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
package org.candlepin.resteasy.interceptor;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.UnauthorizedException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.jboss.resteasy.core.InjectorFactoryImpl;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ValueInjector;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.ApplicationException;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.MethodInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;

/**
 * AuthInterceptorTest
 */
public class AuthInterceptorTest extends DatabaseTestFixture {
    private static Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private AuthInterceptor interceptor;
    private Config config;
    private UserServiceAdapter usa;
    private DeletedConsumerCurator dcc;
    private PermissionFactory permFactory;

    private StubMethodInjector methodInjector;

    public static class StubInjectorFactoryImpl extends InjectorFactoryImpl {
        private StubMethodInjector stub;

        public StubInjectorFactoryImpl(ResteasyProviderFactory factory) {
            super(factory);
            stub = new StubMethodInjector();
        }

        @Override
        public MethodInjector createMethodInjector(Class root, Method method) {
            return stub;
        }
    }

    public static class StubMethodInjector implements MethodInjector {
        private Object[] arguments;

        @Override
        public Object invoke(HttpRequest request, HttpResponse response,
            Object target) throws Failure, ApplicationException,
            WebApplicationException {
            return null;
        }

        public Object[] getArguments() {
            return arguments;
        }

        public void setArguments(Object[] arguments) {
            this.arguments = arguments;
        }

        @Override
        public Object[] injectArguments(HttpRequest request,
            HttpResponse response) throws Failure {
            return arguments;
        }

        @Override
        public ValueInjector[] getParams() {
            return null;
        }
    }

    protected Module getGuiceOverrideModule() {
        return new AuthInterceptorTestModule();
    }

    @Before
    public void setUp() {
        config = new CandlepinCommonTestConfig();
        usa = mock(UserServiceAdapter.class);
        dcc = mock(DeletedConsumerCurator.class);
        permFactory = mock(PermissionFactory.class);
        interceptor = new AuthInterceptor(config, usa,
            consumerCurator, dcc, injector, i18n);

        ResteasyProviderFactory.getInstance().registerProvider(
            StubInjectorFactoryImpl.class);
        methodInjector = (StubMethodInjector)
            ResteasyProviderFactory.getInstance().getInjectorFactory()
            .createMethodInjector(null, null);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        Enumeration e = mock(Enumeration.class);
        when(e.hasMoreElements()).thenReturn(Boolean.FALSE);
        when(mockRequest.getHeaderNames()).thenReturn(e);
        ResteasyProviderFactory.pushContext(HttpServletRequest.class,
            mockRequest);
    }

    @Test(expected = UnauthorizedException.class)
    public void noSecurityHoleNoPrincipal() throws Exception {
        Method method = FakeResource.class.getMethod("someMethod", String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);
        Class clazz = FakeResource.class;
        when(rmethod.getResourceClass()).thenReturn(clazz);

        interceptor.preProcess(req, rmethod);
    }

    @Test
    public void noSecurityHole() throws Exception {
        Method method = FakeResource.class.getMethod("someMethod", String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        req.header("Authorization", "BASIC QWxhZGRpbjpvcGVuIHNlc2FtZQ==");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);
        Class clazz = FakeResource.class;
        when(rmethod.getResourceClass()).thenReturn(clazz);

        when(usa.validateUser(eq("Aladdin"), eq("open sesame"))).thenReturn(true);
        when(usa.findByLogin(eq("Aladdin"))).thenReturn(
            new User("Aladdin", "open sesame", true));

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
        Class clazz = FakeResource.class;
        when(rmethod.getResourceClass()).thenReturn(clazz);

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
        Class clazz = FakeResource.class;
        when(rmethod.getResourceClass()).thenReturn(clazz);

        when(usa.validateUser(eq("Aladdin"), eq("open sesame"))).thenReturn(true);
        when(usa.findByLogin(eq("Aladdin"))).thenReturn(new User("Aladdin", "open sesame"));

        interceptor.preProcess(req, rmethod);

        Principal p = ResteasyProviderFactory.getContextData(Principal.class);
        assertTrue(p instanceof UserPrincipal);
    }

    @Test
    public void noSecurityHoleWithConsumer() throws Exception {
        Method method = FakeResource.class.getMethod(
            "someConsumerOnlyMethod", String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);
        Class clazz = FakeResource.class;
        when(rmethod.getResourceClass()).thenReturn(clazz);

        Consumer c = createConsumer(createOwner());
        methodInjector.setArguments(new Object[] {c.getUuid()});
        when(consumerCurator.getConsumer(eq(c.getUuid()))).thenReturn(c);
        when(consumerCurator.findByUuid(eq(c.getUuid()))).thenReturn(c);

        // create mock certs to trigger SSLAuth provider
        X509Certificate[] certs = new X509Certificate[1];
        X509Certificate cert = mock(X509Certificate.class);
        certs[0] = cert;
        req.setAttribute("javax.servlet.request.X509Certificate", certs);

        java.security.Principal p = mock(java.security.Principal.class);
        when(p.getName()).thenReturn("CN=" + c.getUuid() + ", C=US, L=Raleigh");
        when(cert.getSubjectDN()).thenReturn(p);

        interceptor.preProcess(req, rmethod);

        Principal p1 = ResteasyProviderFactory.getContextData(Principal.class);
        assertTrue(p1 instanceof ConsumerPrincipal);
        verify(consumerCurator).updateLastCheckin(c);
    }

    @Test(expected = ForbiddenException.class)
    public void noAccessToOtherConsumer() throws Exception {
        Method method = FakeResource.class.getMethod(
            "someConsumerOnlyMethod", String.class);
        MockHttpRequest req = MockHttpRequest.create("GET",
            "http://localhost/candlepin/status");
        ResourceMethod rmethod = mock(ResourceMethod.class);
        when(rmethod.getMethod()).thenReturn(method);
        Class clazz = FakeResource.class;
        when(rmethod.getResourceClass()).thenReturn(clazz);

        Consumer c = createConsumer(createOwner());
        Consumer c2 = createConsumer(createOwner());
        methodInjector.setArguments(new Object[] {c2.getUuid()});
        when(consumerCurator.getConsumer(eq(c.getUuid()))).thenReturn(c);
        when(consumerCurator.findByUuid(eq(c2.getUuid()))).thenReturn(c2);

        // create mock certs to trigger SSLAuth provider
        X509Certificate[] certs = new X509Certificate[1];
        X509Certificate cert = mock(X509Certificate.class);
        certs[0] = cert;
        req.setAttribute("javax.servlet.request.X509Certificate", certs);

        java.security.Principal p = mock(java.security.Principal.class);
        when(p.getName()).thenReturn("CN=" + c.getUuid() + ", C=US, L=Raleigh");
        when(cert.getSubjectDN()).thenReturn(p);

        interceptor.preProcess(req, rmethod);
    }
    /**
     * FakeResource simply to create a Method object to pass down into
     * the interceptor.
     */
    public static class FakeResource {
        public String someConsumerOnlyMethod(@Verify(Consumer.class) String uuid) {
            return uuid;
        }

        public String someMethod(String str) {
            return str;
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
     * simple Guice Module to turn our ConsumerCurator into a mock object.
     */
    private static class AuthInterceptorTestModule extends AbstractModule {
        @Override
        protected void configure() {
            PermissionFactory factory = mock(PermissionFactory.class);
            bind(PermissionFactory.class).toInstance(factory);
            ConsumerCurator mockCc = mock(ConsumerCurator.class);
            bind(ConsumerCurator.class).toInstance(mockCc);
        }
    }
}
