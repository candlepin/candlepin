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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.AuthProvider;
import org.candlepin.auth.CandlepinKeycloakRequestAuthenticator;
import org.candlepin.auth.CloudRegistrationAuth;
import org.candlepin.auth.KeycloakConfiguration;
import org.candlepin.auth.KeycloakOIDCFacade;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotAuthorizedException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.User;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.TokenVerifier;
import org.keycloak.adapters.BearerTokenRequestAuthenticator;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.AuthOutcome;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;

/**
 * AuthInterceptorTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AuthenticationFilterTest extends DatabaseTestFixture {
    @Inject private DeletedConsumerCurator deletedConsumerCurator;
    @Inject private Injector injector;

    @Mock private HttpServletRequest mockHttpServletRequest;
    @Mock private ContainerRequestContext mockRequestContext;
    @Mock private CandlepinSecurityContext mockSecurityContext;
    @Mock private ResourceInfo mockInfo;
    @Mock private UserServiceAdapter usa;
    @Mock private KeycloakConfiguration keycloakAdapterConfiguration;
    @Mock private AdapterConfig adapterConfig;
    @Mock private BearerTokenRequestAuthenticator bearerTokenRequestAuthenticator;
    @Mock private KeycloakDeployment keycloakDeployment;

    /* Note: this token can be easily decoded using any JWT-compatible tool (e.g. jwt.io) */
    private static final String TESTTOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9." +
        "eyJqdGkiOiJiNzJiZDlkNi00MDczLTQ2NWUtYTY5YS05NDA2MGZiMjY4Y2QiLCJleHAiOjE1NjQ1MjA3MjIsIm5iZiI6MCwiaW" +
        "F0IjoxNTY0NTE2MjAzLCJpc3MiOiJodHRwczovL3Nzby5kZXYxL3JlZGhhdC1leHRlcm5hbCIsImF1ZCI6ImNhbmRsZXBpbi10" +
        "ZXN0Iiwic3ViIjoiZjplNDRhYTg0ZS0zYjc2LTQwMjgtOTUzNS1hNTQwMDM5MWQwMGY6cWFAcmVkaGF0LmNvbSIsInR5cCI6Ik" +
        "JlYXJlciIsImF6cCI6ImNhbmRsZXBpbi10ZXN0Iiwibm9uY2UiOiI1Y2NkZGFlNS0xYmExLTQxMzYtYTM4OC01NzZkM2MwZDM1" +
        "ZjgiLCJhdXRoX3RpbWUiOjE1NjQ0OTM4NTcsInNlc3Npb25fc3RhdGUiOiIyOTUxMDllNi1iNTQwLTQyYmMtOWQwZC1jODNmMT" +
        "ZhNjYzMmIiLCJhY3IiOiIwIiwiYWxsb3dlZC1vcmlnaW5zIjpbIioiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbXX0sInJl" +
        "c291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbXX19LCJuYW1lIjoidGVzdCIsInByZWZlcnJlZF91c2VybmFtZS" +
        "I6InFhQHJlZGhhdC5jb20iLCJnaXZlbl9uYW1lIjoiSmFuZSIsImZhbWlseV9uYW1lIjoiRG9lIiwiZW1haWwiOiJ0ZXN0QHJl" +
        "ZGhhdC5jb20ifQ." +
        "82Qt5tnh85-klwBNkYpC3QX-hHKIiFv0L8GHSYOjAgM";

    private AuthenticationFilter interceptor;
    private MockHttpRequest mockReq;

    @Override
    protected Module getGuiceOverrideModule() {
        return new AuthInterceptorTestModule();
    }

    @BeforeEach
    public void setUp() throws Exception {
        Class clazz = FakeResource.class;
        when(mockInfo.getResourceClass()).thenReturn(clazz);

        mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/status");

        ResteasyContext.pushContext(ResourceInfo.class, mockInfo);
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        when(mockRequestContext.getSecurityContext()).thenReturn(mockSecurityContext);

        config.setProperty(ConfigProperties.OAUTH_AUTHENTICATION, "false");
        config.setProperty(ConfigProperties.SSL_AUTHENTICATION, "false");
        config.setProperty(ConfigProperties.BASIC_AUTHENTICATION, "true");
        config.setProperty(ConfigProperties.TRUSTED_AUTHENTICATION, "true");

        when(keycloakAdapterConfiguration.getAdapterConfig()).thenReturn(adapterConfig);
        when(adapterConfig.getAuthServerUrl()).thenReturn("https://example.com/auth");
        when(adapterConfig.getResource()).thenReturn("candlepin");
        when(adapterConfig.getRealm()).thenReturn("redhat");
        when(keycloakAdapterConfiguration.getKeycloakDeployment()).thenReturn(keycloakDeployment);
        when(bearerTokenRequestAuthenticator.authenticate(any())).thenReturn(AuthOutcome.AUTHENTICATED);
        when(bearerTokenRequestAuthenticator.getToken()).
            thenReturn(TokenVerifier.create(TESTTOKEN, AccessToken.class).getToken());
    }

    private AuthenticationFilter buildInterceptor() {
        MethodLocator methodLocator = new MethodLocator(injector);
        methodLocator.init();
        AnnotationLocator annotationLocator = new AnnotationLocator(methodLocator);
        interceptor = new AuthenticationFilter(config, consumerCurator, deletedConsumerCurator, injector,
            annotationLocator);
        interceptor.setHttpServletRequest(mockHttpServletRequest);

        return interceptor;
    }

    void setResourceClass(Class resourceClass) {
        when(mockInfo.getResourceClass()).thenReturn(resourceClass);
    }

    private void keycloakSetup() {
        KeycloakOIDCFacade keycloakOIDCFacade = new KeycloakOIDCFacade(mockReq);
        when(usa.findByLogin(eq("qa@redhat.com"))).thenReturn(
            new User("Test", "redhat", true));
        RequestAuthenticator keycloakRequestAuthenticator = new
            CandlepinKeycloakRequestAuthenticator(keycloakOIDCFacade, mockReq, keycloakDeployment) {
            @Override
            protected boolean verifySSL() {
                //false means verification is successful
                return false;
            }

            protected BearerTokenRequestAuthenticator createBearerTokenAuthenticator() {
                this.deployment = keycloakDeployment;
                return bearerTokenRequestAuthenticator;
            }
        };
        when(keycloakAdapterConfiguration.createRequestAuthenticator(mockReq)).
            thenReturn(keycloakRequestAuthenticator);
    }

    private void mockResourceMethod(Method method) {
        when(mockInfo.getResourceMethod()).thenReturn(method);
    }

    private ContainerRequestContext getContext() {
        return getContext(null);
    }

    private ContainerRequestContext getContext(ResourceMethodInvoker invoker) {
        return new PostMatchContainerRequestContext(mockReq, invoker);
    }

    @Test
    public void noSecurityHoleNoPrincipalNoSsl() throws Exception {
        noSecurityHoleNoPrincipalNoSslForResource(FakeResource.class);
    }

    @Test
    void noSecurityHoleNoPrincipalNoSslSpecFirst() throws Exception {
        noSecurityHoleNoPrincipalNoSslForResource(FakeApi.class);
    }

    void noSecurityHoleNoPrincipalNoSslForResource(Class<?> resourceClass) throws Exception {
        setResourceClass(resourceClass);
        when(mockHttpServletRequest.isSecure()).thenReturn(false);
        Method method = resourceClass.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        AuthenticationFilter interceptor = this.buildInterceptor();
        assertThrows(BadRequestException.class, () -> interceptor.filter(getContext()));
    }

    @Test
    public void noSecurityHoleNoPrincipalNoSslButOverridenByConfig() throws Exception {
        noSecurityHoleNoPrincipalNoSslButOverridenByConfigForResource(FakeResource.class);
    }

    @Test
    void noSecurityHoleNoPrincipalNoSslButOverridenByConfigSpecFirst() throws Exception {
        noSecurityHoleNoPrincipalNoSslButOverridenByConfigForResource(FakeApi.class);
    }

    void noSecurityHoleNoPrincipalNoSslButOverridenByConfigForResource(Class<?> resourceClass)
        throws Exception {
        setResourceClass(resourceClass);
        config.setProperty(ConfigProperties.AUTH_OVER_HTTP, "true");
        when(mockHttpServletRequest.isSecure()).thenReturn(false);
        Method method = resourceClass.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        AuthenticationFilter interceptor = this.buildInterceptor();
        assertThrows(NotAuthorizedException.class, () -> interceptor.filter(getContext()));
        // Revert default settings
        config.setProperty(ConfigProperties.AUTH_OVER_HTTP, "false");
    }

    @Test
    public void noSecurityHoleNoPrincipal() throws Exception {
        noSecurityHoleNoPrincipalForResource(FakeResource.class);
    }

    @Test
    void noSecurityHoleNoPrincipalSpecFirst() throws Exception {
        noSecurityHoleNoPrincipalForResource(FakeApi.class);
    }

    void noSecurityHoleNoPrincipalForResource(Class<?> resourceClass) throws Exception {
        setResourceClass(resourceClass);
        when(mockHttpServletRequest.isSecure()).thenReturn(true);
        Method method = resourceClass.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        AuthenticationFilter interceptor = this.buildInterceptor();
        assertThrows(NotAuthorizedException.class, () -> interceptor.filter(getContext()));
    }

    @Test
    public void noSecurityHole() throws Exception {
        noSecurityHoleForResource(FakeResource.class);
    }

    @Test
    void noSecurityHoleSpecFirst() throws Exception {
        noSecurityHoleForResource(FakeApi.class);
    }

    void noSecurityHoleForResource(Class<?> resourceClass) throws Exception {
        setResourceClass(resourceClass);
        mockReq.header("Authorization", "BASIC QWxhZGRpbjpvcGVuIHNlc2FtZQ==");

        when(usa.validateUser(eq("Aladdin"), eq("open sesame"))).thenReturn(true);
        when(usa.findByLogin(eq("Aladdin"))).thenReturn(
            new User("Aladdin", "open sesame", true));

        Method method = resourceClass.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        AuthenticationFilter interceptor = this.buildInterceptor();
        interceptor.filter(getContext());

        Principal p = ResteasyContext.getContextData(Principal.class);
        assertTrue(p instanceof UserPrincipal);
    }

    @Test
    public void securityHoleWithNoAuth() throws Exception {
        securityHoleWithNoAuthForResource(FakeResource.class);
    }

    @Test
    void securityHoleWithNoAuthSpecFirst() throws Exception {
        securityHoleWithNoAuthForResource(FakeApi.class);
    }

    void securityHoleWithNoAuthForResource(Class<?> resourceClass) throws Exception {
        setResourceClass(resourceClass);
        Method method = resourceClass.getMethod("noAuthMethod", String.class);
        mockResourceMethod(method);

        AuthenticationFilter interceptor = this.buildInterceptor();
        interceptor.filter(getContext());

        Principal p = ResteasyContext.getContextData(Principal.class);
        assertTrue(p instanceof NoAuthPrincipal);
    }

    @Test
    public void securityHoleWithAuth() throws Exception {
        securityHoleWithAuthForResource(FakeResource.class);
    }

    @Test
    void securityHoleWithAuthSpecFirst() throws Exception {
        securityHoleWithAuthForResource(FakeApi.class);
    }

    void securityHoleWithAuthForResource(Class<?> resourceClass) throws Exception {
        setResourceClass(resourceClass);
        Method method = resourceClass.getMethod("annotatedMethod", String.class);
        mockResourceMethod(method);

        mockReq.header("Authorization", "BASIC QWxhZGRpbjpvcGVuIHNlc2FtZQ==");

        when(usa.validateUser(eq("Aladdin"), eq("open sesame"))).thenReturn(true);
        when(usa.findByLogin(eq("Aladdin"))).thenReturn(new User("Aladdin", "open sesame"));

        AuthenticationFilter interceptor = this.buildInterceptor();
        interceptor.filter(getContext());

        Principal p = ResteasyContext.getContextData(Principal.class);
        assertTrue(p instanceof UserPrincipal);
    }

    @Test
    public void securityHoleWithAnonAndNoPrincipal() throws Exception {
        securityHoleWithAnonAndNoPrincipalForResource(FakeResource.class);
    }

    @Test
    void securityHoleWithAnonAndNoPrincipalSpecFirst() throws Exception {
        securityHoleWithAnonAndNoPrincipalForResource(FakeApi.class);
    }

    void securityHoleWithAnonAndNoPrincipalForResource(Class<?> resourceClass) throws Exception {
        setResourceClass(resourceClass);
        Method method = resourceClass.getMethod("anonMethod", String.class);
        mockResourceMethod(method);

        AuthenticationFilter interceptor = this.buildInterceptor();
        interceptor.filter(getContext());

        Principal p = ResteasyContext.getContextData(Principal.class);
        assertTrue(p instanceof NoAuthPrincipal);
        // Anon should not even bother attempting to create a real principal
        verify(usa, times(0)).validateUser(anyString(), anyString());
    }

    @Test
    public void securityHoleWithAnonAndPrincipalProvided() throws Exception {
        securityHoleWithAnonAndPrincipalProvidedForResource(FakeResource.class);
    }

    @Test
    void securityHoleWithAnonAndPrincipalProvidedSpecFirst() throws Exception {
        securityHoleWithAnonAndPrincipalProvidedForResource(FakeApi.class);
    }

    void securityHoleWithAnonAndPrincipalProvidedForResource(Class<?> resourceClass) throws Exception {
        setResourceClass(resourceClass);
        Method method = resourceClass.getMethod("anonMethod", String.class);
        mockResourceMethod(method);

        mockReq.header("Authorization", "BASIC QWxhZGRpbjpvcGVuIHNlc2FtZQ==");

        AuthenticationFilter interceptor = this.buildInterceptor();
        interceptor.filter(getContext());

        Principal p = ResteasyContext.getContextData(Principal.class);
        assertTrue(p instanceof NoAuthPrincipal);
        // Anon should not even bother attempting to create a real principal
        verify(usa, times(0)).validateUser(anyString(), anyString());
    }

    @Test
    public void keycloakAuthAuthentication() throws Exception {
        this.config.setProperty(ConfigProperties.KEYCLOAK_AUTHENTICATION, "true");

        // Attempt to disable all other auth methods
        this.config.setProperty(ConfigProperties.OAUTH_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.SSL_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.BASIC_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.TRUSTED_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "false");

        Method method = FakeResource.class.getMethod("someMethod", String.class);
        mockResourceMethod(method);
        mockReq.header("Authorization", "Bearer " + TESTTOKEN);
        keycloakSetup();

        AuthenticationFilter interceptor = this.buildInterceptor();
        interceptor.filter(getContext());

        Principal p = ResteasyContext.getContextData(Principal.class);
        assertEquals("qa@redhat.com", p.getName());
    }

    @Test
    public void testCloudAuthSupport() throws Exception {
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "true");

        // Attempt to disable all other auth methods
        this.config.setProperty(ConfigProperties.OAUTH_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.SSL_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.BASIC_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.TRUSTED_AUTHENTICATION, "false");
        this.config.setProperty(ConfigProperties.KEYCLOAK_AUTHENTICATION, "false");

        Method method = FakeResource.class.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        Injector mockInjector = mock(Injector.class);
        AuthProvider mockProvider = mock(AuthProvider.class);
        Principal mockPrincipal = mock(Principal.class);

        doAnswer(iom -> {
            Class target = (Class) iom.getArguments()[0];
            return target == CloudRegistrationAuth.class ? mockProvider : injector.getInstance(target);
        }).when(mockInjector).getInstance(any(Class.class));

        doReturn(mockProvider).when(mockInjector).getInstance(eq(CloudRegistrationAuth.class));
        doReturn(mockPrincipal).when(mockProvider).getPrincipal(any(HttpRequest.class));

        AuthenticationFilter interceptor = new AuthenticationFilter(this.config, this.consumerCurator,
            this.deletedConsumerCurator, mockInjector, this.annotationLocator);

        mockReq.header("Authorization", "Bearer FAKE_CLOUD_AUTH_TOKEN");
        interceptor.filter(this.getContext());

        Principal principal = ResteasyContext.getContextData(Principal.class);

        verify(mockInjector, times(1)).getInstance(eq(CloudRegistrationAuth.class));
        assertEquals(mockPrincipal, principal);
    }

    /**
     * FakeResource simply to create a Method object to pass down into
     * the interceptor.
     */
    @Path("/fake")
    public static class FakeResource {
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

        @SecurityHole(anon = true)
        public String anonMethod(String str) {
            return str;
        }
    }

    @Path("/fake")
    public interface FakeApi {
        @Path("1")
        String someMethod(String str);

        @Path("2")
        String annotatedMethod(String str);

        @Path("3")
        String noAuthMethod(String str);

        @Path("4")
        String anonMethod(String str);
    }

    public static class FakeApiImpl implements FakeApi {

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

        @SecurityHole(anon = true)
        public String anonMethod(String str) {
            return str;
        }
    }

    private class AuthInterceptorTestModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(PermissionFactory.class).toInstance(mock(PermissionFactory.class));
            bind(ConsumerCurator.class).toInstance(mock(ConsumerCurator.class));
            bind(UserServiceAdapter.class).toInstance(usa);
            bind(FakeApiImpl.class);
            bind(FakeResource.class);
            bind(KeycloakConfiguration.class).toInstance(keycloakAdapterConfiguration);
        }
    }
}
