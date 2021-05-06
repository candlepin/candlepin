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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.candlepin.auth.Principal;
import org.candlepin.auth.SSLAuth;
import org.candlepin.auth.Verify;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.test.DatabaseTestFixture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.lang.reflect.Method;
import java.security.cert.X509Certificate;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.security.auth.x500.X500Principal;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VerifyAuthorizationFilterTest extends DatabaseTestFixture {
    @Inject private Provider<I18n> i18nProvider;
    @Inject private StoreFactory storeFactory;
    @Inject private SSLAuth sslAuth;

    @Mock private CandlepinSecurityContext mockSecurityContext;
    @Mock private ContainerRequestContext mockRequestContext;

    private VerifyAuthorizationFilter interceptor;
    private MockHttpRequest mockReq;

    private UriInfo mockUriInfo;
    private MultivaluedHashMap<String, String> mockPathParameters;
    private MultivaluedHashMap<String, String> mockQueryParameters;
    private MultivaluedHashMap<String, String> mockHeaders;


    protected Module getGuiceOverrideModule() {
        return new AuthInterceptorTestModule();
    }

    @BeforeEach
    public void setUp() throws NoSuchMethodException, SecurityException {
        // Turn logger to INFO level to disable HttpServletRequest logging.
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = lc.getLogger(AbstractAuthorizationFilter.class);
        logger.setLevel(Level.INFO);

        this.mockUriInfo = mock(UriInfo.class);

        this.mockPathParameters = new MultivaluedHashMap<>();
        this.mockQueryParameters = new MultivaluedHashMap<>();
        this.mockHeaders = new MultivaluedHashMap<>();

        when(mockRequestContext.getSecurityContext()).thenReturn(mockSecurityContext);

        doReturn(this.mockUriInfo).when(this.mockRequestContext).getUriInfo();
        doReturn(this.mockPathParameters).when(this.mockUriInfo).getPathParameters(anyBoolean());
        doReturn(this.mockQueryParameters).when(this.mockUriInfo).getQueryParameters(anyBoolean());
        doReturn(this.mockHeaders).when(this.mockRequestContext).getHeaders();

        interceptor = new VerifyAuthorizationFilter(i18nProvider, storeFactory, annotationLocator);
    }

    private void configureResourceClass(Class<?> resourceClass) throws NoSuchMethodException {
        ResourceInfo mockInfo = mock(ResourceInfo.class);
        Method method = FakeResource.class.getMethod("someMethod", String.class);
        when(mockInfo.getResourceMethod()).thenReturn(method);
        Class clazz = FakeResource.class;
        when(mockInfo.getResourceClass()).thenReturn(clazz);

        ResteasyContext.pushContext(ResourceInfo.class, mockInfo);
    }

    @Test
    public void testAccessToConsumer() throws Exception {
        testAccessToConsumerForResource(FakeResource.class);
    }

    @Test
    void testAccessToConsumerSpecFirst() throws Exception {
        testAccessToConsumerForResource(FakeApi.class);
    }

    void testAccessToConsumerForResource(Class<?> resourceClass) throws Exception {
        configureResourceClass(resourceClass);

        mockReq = MockHttpRequest.create("POST", "http://localhost/candlepin/fake/123");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        mockReq.setAttribute(ResteasyProviderFactory.class.getName(), ResteasyProviderFactory.getInstance());

        Consumer c = createConsumer(createOwner());

        this.mockPathParameters.add("uuid", c.getUuid());

        X500Principal dn = new X500Principal("CN=" + c.getUuid() + ", C=US, L=Raleigh");

        // create mock certs to trigger SSLAuth provider
        X509Certificate[] certs = new X509Certificate[1];
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectX500Principal()).thenReturn(dn);

        certs[0] = cert;
        mockReq.setAttribute("javax.servlet.request.X509Certificate", certs);

        Principal p = sslAuth.getPrincipal(mockReq);
        when(mockSecurityContext.getUserPrincipal()).thenReturn(p);

        interceptor.filter(mockRequestContext);
    }

    @Test
    public void noAccessToOtherConsumer() throws Exception {
        noAccessToOtherConsumerForResource(FakeResource.class);
    }

    @Test
    void noAccessToOtherConsumerSpecFirst() throws Exception {
        noAccessToOtherConsumerForResource(FakeApi.class);
    }

    void noAccessToOtherConsumerForResource(Class<?> resourceClass) throws Exception {
        configureResourceClass(resourceClass);
        mockReq = MockHttpRequest.create("POST", "http://localhost/candlepin/fake/123");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        mockReq.setAttribute(ResteasyProviderFactory.class.getName(), ResteasyProviderFactory.getInstance());

        Consumer c = createConsumer(createOwner());
        Consumer c2 = createConsumer(createOwner());
        this.mockPathParameters.add("uuid", c2.getUuid());

        X500Principal dn = new X500Principal("CN=" + c.getUuid() + ", C=US, L=Raleigh");

        // create mock certs to trigger SSLAuth provider
        X509Certificate[] certs = new X509Certificate[1];
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectX500Principal()).thenReturn(dn);

        certs[0] = cert;
        mockReq.setAttribute("javax.servlet.request.X509Certificate", certs);

        Principal p = sslAuth.getPrincipal(mockReq);
        when(mockSecurityContext.getUserPrincipal()).thenReturn(p);

        assertThrows(ForbiddenException.class, () -> interceptor.filter(mockRequestContext));
    }

    /**
     * FakeResource simply to create a Method object to pass down into
     * the interceptor.
     */
    @Path("fake")
    public static class FakeResource {
        @POST
        @Path("/{uuid}")
        public String someMethod(@PathParam("uuid") @Verify(Consumer.class) String uuid) {
            return uuid;
        }
    }

    /**
     * FakeGeneratedApi helps test that our filter works on spec-first APIs.
     */
    @Path("fake")
    public interface FakeApi {
        @Path("/{uuid}")
        String someMethod(@PathParam("uuid") String uuid);
    }

    /**
     * FakeGeneratedResource helps test that our filter works on spec-first APIs.
     */
    public static class FakeApiImpl implements FakeApi {
        @Override
        public String someMethod(@Verify(Consumer.class) String uuid) {
            return null;
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
            bind(StoreFactory.class);
            bind(FakeResource.class);
            bind(FakeApiImpl.class);
        }
    }
}
