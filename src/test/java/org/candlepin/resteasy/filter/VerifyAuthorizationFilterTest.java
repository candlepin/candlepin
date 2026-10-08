/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.AnonymousCloudConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SSLAuth;
import org.candlepin.auth.Verify;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Persisted;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

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

import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;

import javax.security.auth.x500.X500Principal;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VerifyAuthorizationFilterTest extends DatabaseTestFixture {
    private StoreFactory storeFactory;
    private SSLAuth sslAuth;

    @Mock
    private CandlepinSecurityContext mockSecurityContext;
    @Mock
    private ContainerRequestContext mockRequestContext;

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
        storeFactory = injector.getInstance(StoreFactory.class);
        sslAuth = injector.getInstance(SSLAuth.class);

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

    /**
     * Pushes a ResourceInfo for the named FakeResource method into the Resteasy context.
     * It accepts an arbitrary parameter list, so methods declaring more than
     * one parameter can be exercised through the filter.
     *
     * @param methodName the name of the FakeResource method being invoked
     * @param parameterTypes the parameter types of the FakeResource method being invoked
     * @throws NoSuchMethodException if FakeResource declares no matching method
     */
    private void configureResourceMethod(String methodName, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        ResourceInfo mockInfo = mock(ResourceInfo.class);
        Method method = FakeResource.class.getMethod(methodName, parameterTypes);
        when(mockInfo.getResourceMethod()).thenReturn(method);
        Class clazz = FakeResource.class;
        when(mockInfo.getResourceClass()).thenReturn(clazz);

        ResteasyContext.pushContext(ResourceInfo.class, mockInfo);
    }

    /**
     * Builds a mock Principal which is denied access to the consumer carrying the given UUID, and is
     * allowed access to every other entity. Matching on UUID rather than on instance identity keeps the
     * test independent of which instances entity resolution happens to return.
     *
     * @param deniedConsumerUuid the UUID of the consumer the Principal is denied access to
     * @return a mock Principal that is denied access to the given consumer
     */
    private Principal buildPrincipalDeniedConsumerUuid(String deniedConsumerUuid) {
        Principal principal = mock(Principal.class);
        doAnswer(invocation -> {
            Object target = invocation.getArgument(0);
            return !(target instanceof Consumer) ||
                !deniedConsumerUuid.equals(((Consumer) target).getUuid());
        })
            .when(principal).canAccess(any(), any(), any());

        return principal;
    }

    @Test
    public void testAccessToConsumer() throws Exception {
        testAccessToConsumerForResource(FakeResource.class);
    }

    /**
     * Two parameters carrying identical Verify annotations must both be authorized. Annotation equality
     * is defined by annotation type plus member values, so identical annotations collide when used as
     * map keys and only one of the two parameters survives to be checked.
     */
    @Test
    public void filterShouldDenyWhenDuplicateVerifyAnnotationsHideAnInaccessibleEntity() throws Exception {
        configureResourceMethod("duplicateVerifyParams", String.class, String.class);

        Owner owner = createOwner();
        Consumer inaccessible = createConsumer(owner);
        Consumer accessible = createConsumer(owner);

        // The inaccessible consumer is bound to the first parameter, so it is the one whose value is
        // discarded when the two identical annotations collapse into a single map entry.
        this.mockPathParameters.add("first", inaccessible.getUuid());
        this.mockPathParameters.add("second", accessible.getUuid());

        Method method = FakeResource.class.getMethod("duplicateVerifyParams", String.class, String.class);
        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = interceptor.getArguments(
            mockRequestContext, method);
        assertEquals(List.of(inaccessible.getUuid(), accessible.getUuid()),
            arguments.stream().map(VerifyAuthorizationFilter.VerifiedArgument::value).toList());

        Principal principal = this.buildPrincipalDeniedConsumerUuid(inaccessible.getUuid());
        when(mockSecurityContext.getUserPrincipal()).thenReturn(principal);

        assertThrows(ForbiddenException.class, () -> interceptor.filter(mockRequestContext));
    }

    @Test
    void testAccessToConsumerSpecFirst() throws Exception {
        testAccessToConsumerForResource(FakeApi.class);
    }

    void testAccessToConsumerForResource(Class<?> resourceClass) throws Exception {
        configureResourceMethod("someMethod", String.class);

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
        mockReq.setAttribute("jakarta.servlet.request.X509Certificate", certs);

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
        configureResourceMethod("someMethod", String.class);
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
        mockReq.setAttribute("jakarta.servlet.request.X509Certificate", certs);

        Principal p = sslAuth.getPrincipal(mockReq);
        when(mockSecurityContext.getUserPrincipal()).thenReturn(p);

        assertThrows(ForbiddenException.class, () -> interceptor.filter(mockRequestContext));
    }

    @Test
    public void testAccessForMultiValueVerifyParameterWithExistingConsumer() throws Exception {
        configureResourceMethod("multiValueVerify", String.class);

        Consumer consumer = createConsumer(createOwner());
        String uri = "http://localhost/candlepin/fake/multiValueVerify/" + consumer.getUuid();
        mockReq = MockHttpRequest.create("GET", uri);
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        mockReq.setAttribute(ResteasyProviderFactory.class.getName(), ResteasyProviderFactory.getInstance());

        mockPathParameters.add("uuid", consumer.getUuid());

        // create mock certs to trigger SSLAuth provider
        X509Certificate cert = mock(X509Certificate.class);
        doReturn(new X500Principal("CN=" + consumer.getUuid() + ", C=US, L=Raleigh"))
            .when(cert).getSubjectX500Principal();
        X509Certificate[] certs = new X509Certificate[1];
        certs[0] = cert;
        mockReq.setAttribute("jakarta.servlet.request.X509Certificate", certs);

        doReturn(sslAuth.getPrincipal(mockReq)).when(mockSecurityContext).getUserPrincipal();

        // Expect no errors
        interceptor.filter(mockRequestContext);
    }

    @Test
    public void testAccessForMultiValueVerifyParameterWithExistingAnonymousCloudConsumer() throws Exception {
        configureResourceMethod("multiValueVerify", String.class);

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("account-id")
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId("offer-id")
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(List.of("product-id"));
        consumer = this.anonymousCloudConsumerCurator.create(consumer);

        mockPathParameters.add("uuid", consumer.getUuid());

        doReturn(new AnonymousCloudConsumerPrincipal(consumer)).when(mockSecurityContext).getUserPrincipal();

        // Expect no errors
        interceptor.filter(mockRequestContext);
    }

    @Test
    public void testAccessForMultiValueVerifyParameterWithUnknownUuid() throws Exception {
        configureResourceMethod("multiValueVerify", String.class);

        String uuid = Util.generateUUID();
        mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake/multiValueVerify/" + uuid);
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        mockReq.setAttribute(ResteasyProviderFactory.class.getName(), ResteasyProviderFactory.getInstance());

        mockPathParameters.add("uuid", uuid);

        X509Certificate cert = mock(X509Certificate.class);
        doReturn(new X500Principal("CN=123, C=US, L=Raleigh")).when(cert).getSubjectX500Principal();
        X509Certificate[] certs = new X509Certificate[1];
        certs[0] = cert;
        mockReq.setAttribute("jakarta.servlet.request.X509Certificate", certs);

        doReturn(sslAuth.getPrincipal(mockReq)).when(mockSecurityContext).getUserPrincipal();

        assertThrows(NotFoundException.class, () -> interceptor.filter(mockRequestContext));
    }

    @Test
    public void testAccessForCollectionWithExistingValue() throws Exception {
        configureResourceMethod("multiValueVerify", String.class);

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("account-id")
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId("offer-id")
            .setProductIds(List.of("product-id"))
            .setCloudProviderShortName(TestUtil.randomString());
        consumer = this.anonymousCloudConsumerCurator.create(consumer);
        mockPathParameters.addAll("uuid", List.of(consumer.getUuid(), Util.generateUUID()));

        doReturn(new AnonymousCloudConsumerPrincipal(consumer)).when(mockSecurityContext).getUserPrincipal();

        // Expect no errors
        interceptor.filter(mockRequestContext);
    }

    /**
     * Builds a Verify annotation declared on the given parameter of a FakeResource method, so the
     * tests below can build argument maps keyed by real annotation instances.
     *
     * @param methodName
     *     the name of the FakeResource method declaring the annotation
     *
     * @param parameterIndex
     *     the index of the annotated parameter
     *
     * @return the Verify annotation declared on the given parameter
     */
    private Verify buildVerifyAnnotationOf(String methodName, int parameterIndex) {
        Method method = Arrays.stream(FakeResource.class.getMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("no such method: " + methodName));

        return Arrays.stream(method.getParameterAnnotations()[parameterIndex])
            .filter(Verify.class::isInstance)
            .map(Verify.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("parameter is not annotated with @Verify"));
    }

    /**
     * Builds a filter whose entity resolution is driven by the provided mapping, so the tests below can
     * control exactly which entities each Verify parameter resolves to, and in what order.
     *
     * @param resolved
     *     a mapping of request values to the entities they resolve to
     *
     * @return a filter resolving entities from the given mapping
     */
    private VerifyAuthorizationFilter buildFilterResolving(Map<Object, List<Persisted>> resolved) {
        return new VerifyAuthorizationFilter(i18nProvider, storeFactory, annotationLocator) {
            @Override
            protected List<Persisted> getAccessedEntities(Verify verify, Object requestValue) {
                return resolved.getOrDefault(requestValue, List.of());
            }
        };
    }

    /**
     * Builds a mock Principal whose .canAccess() method will deny any of the entities passed as arguments,
     * but will not deny any entities not passed in.
     * <p></p>
     * Passing in an empty list means the Principal is allowed access to all entities.
     *
     * @param denied the list of entities that the Principal is denied access to
     * @return a mock Principal that is denied access to the entities passed in as arguments
     */
    private Principal buildPrincipalThatIsDenied(Persisted... denied) {
        List<Persisted> deniedEntities = Arrays.asList(denied);

        Principal principal = mock(Principal.class);
        doAnswer(invocation -> deniedEntities.stream()
            .noneMatch(entity -> entity == invocation.getArgument(0)))
            .when(principal).canAccess(any(), any(), any());

        return principal;
    }

    @Test
    public void hasAccessShouldDenyWhenALaterVerifyParameterIsInaccessible() {
        Owner owner = createOwner();
        Consumer consumer = new Consumer().setOwner(owner);
        Pool pool = new Pool().setOwner(owner);

        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = List.of(
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("twoVerifyParams", 0), "consumer-uuid"),
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("twoVerifyParams", 1), "pool-id"));

        VerifyAuthorizationFilter filter = this.buildFilterResolving(Map.of(
            "consumer-uuid", List.of(consumer),
            "pool-id", List.of(pool)));

        assertFalse(filter.hasAccess(arguments, this.buildPrincipalThatIsDenied(pool), Access.READ_ONLY));
    }

    @Test
    public void hasAccessShouldDenyWhenALaterEntityInASingleVerifyParameterIsInaccessible() {
        Owner owner = createOwner();
        Consumer accessible = new Consumer().setOwner(owner);
        Consumer inaccessible = new Consumer().setOwner(owner);

        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = List.of(
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("getCollection", 0), List.of("uuid-1", "uuid-2")));

        VerifyAuthorizationFilter filter = this.buildFilterResolving(Map.of(
            List.of("uuid-1", "uuid-2"), List.of(accessible, inaccessible)));

        assertFalse(filter.hasAccess(arguments, this.buildPrincipalThatIsDenied(inaccessible),
            Access.READ_ONLY));
    }

    @Test
    public void hasAccessShouldDenyWhenTheFirstEntityInASingleVerifyParameterIsInaccessible() {
        Owner owner = createOwner();
        Consumer inaccessible = new Consumer().setOwner(owner);
        Consumer accessible = new Consumer().setOwner(owner);

        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = List.of(
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("getCollection", 0), List.of("uuid-1", "uuid-2")));

        VerifyAuthorizationFilter filter = this.buildFilterResolving(Map.of(
            List.of("uuid-1", "uuid-2"), List.of(inaccessible, accessible)));

        assertFalse(filter.hasAccess(arguments, this.buildPrincipalThatIsDenied(inaccessible),
            Access.READ_ONLY));
    }

    @Test
    public void hasAccessShouldGrantWhenAnEntityHasNoRegisteredEntityStore() {
        Persisted proxied = new ProxiedOwner();

        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = List.of(
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("twoVerifyParams", 0), "owner-key"));

        VerifyAuthorizationFilter filter = this.buildFilterResolving(Map.of("owner-key", List.of(proxied)));

        assertTrue(filter.hasAccess(arguments, this.buildPrincipalThatIsDenied(), Access.READ_ONLY));
    }

    @Test
    public void hasAccessShouldKeepCheckingAfterAnEntityWithNoRegisteredEntityStore() {
        Owner owner = createOwner();
        Persisted proxied = new ProxiedOwner();
        Consumer inaccessible = new Consumer().setOwner(owner);

        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = List.of(
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("getCollection", 0), List.of("uuid-1", "uuid-2")));

        VerifyAuthorizationFilter filter = this.buildFilterResolving(Map.of(
            List.of("uuid-1", "uuid-2"), List.of(proxied, inaccessible)));

        assertFalse(filter.hasAccess(arguments, this.buildPrincipalThatIsDenied(inaccessible),
            Access.READ_ONLY));
    }

    @Test
    public void hasAccessShouldGrantWhenAnEarlierNullableVerifyParameterResolvedNoEntities() {
        Owner owner = createOwner();
        Consumer consumer = new Consumer().setOwner(owner);

        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = List.of(
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("nullableVerifyFirst", 0), "absent-pool-id"),
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("nullableVerifyFirst", 1), "consumer-uuid"));

        VerifyAuthorizationFilter filter = this.buildFilterResolving(Map.of(
            "consumer-uuid", List.of(consumer)));

        assertTrue(filter.hasAccess(arguments, this.buildPrincipalThatIsDenied(), Access.READ_ONLY));
    }

    @Test
    public void hasAccessShouldDenyWhenNoVerifyParameterResolvedAnyEntity() {
        List<VerifyAuthorizationFilter.VerifiedArgument> arguments = List.of(
            new VerifyAuthorizationFilter.VerifiedArgument(
                this.buildVerifyAnnotationOf("nullableVerifyFirst", 0), "absent-pool-id"));

        VerifyAuthorizationFilter filter = this.buildFilterResolving(Map.of());

        assertFalse(filter.hasAccess(arguments, this.buildPrincipalThatIsDenied(), Access.READ_ONLY));
    }

    @Test
    public void testVerifyTypesToString() throws Exception {
        Class<? extends Persisted>[] values = new Class[]{Pool.class, Product.class};
        assertEquals("[Pool, Product]", interceptor.verifyTypesToString(values));
        assertEquals("Pool", interceptor.verifyTypesToString(Arrays.copyOfRange(values, 0, 1)));
    }

    /**
     * FakeResource simply to create a Method object to pass down into the interceptor.
     */
    @Path("fake")
    public static class FakeResource {
        @POST
        @Path("/{uuid}")
        public String someMethod(@PathParam("uuid") @Verify(Consumer.class) String uuid) {
            return uuid;
        }

        @GET
        public String getCollection(@Verify(Consumer.class) Collection<String> uuids) {
            return "return value";
        }

        @GET
        @Path("/multiValueVerify/{uuid}")
        public String multiValueVerify(@PathParam("uuid")
            @Verify({AnonymousCloudConsumer.class, Consumer.class}) String uuid) {
            return uuid;
        }

        @GET
        @Path("/twoVerifyParams")
        public String twoVerifyParams(@Verify(Consumer.class) String uuid,
            @Verify(Pool.class) String poolId) {
            return uuid;
        }

        @GET
        @Path("/nullableVerifyFirst")
        public String nullableVerifyFirst(@Verify(value = Pool.class, nullable = true) String poolId,
            @Verify(Consumer.class) String uuid) {
            return uuid;
        }

        @GET
        @Path("/duplicateVerifyParams/{first}/{second}")
        public String duplicateVerifyParams(@PathParam("first") @Verify(Consumer.class) String first,
            @PathParam("second") @Verify(Consumer.class) String second) {
            return first;
        }
    }

    /**
     * Stands in for a Hibernate proxy: a runtime subclass of a registered entity type which is not
     * itself a key in the StoreFactory type map, so StoreFactory.canValidate returns false for it.
     */
    public static class ProxiedOwner extends Owner {
    }

    /**
     * FakeGeneratedApi helps test that our filter works on spec-first APIs.
     */
    @Path("fake")
    public interface FakeApi {
        @Path("/{uuid}")
        String someMethod(@PathParam("uuid") String uuid);

        String getCollection(@Verify(Consumer.class) Collection<String> uuids);

        @Path("/multiValueVerify/{uuid}")
        String multiValueVerify(
            @PathParam("uuid") @Verify({AnonymousCloudConsumer.class, Consumer.class}) String uuid);
    }

    /**
     * FakeGeneratedResource helps test that our filter works on spec-first APIs.
     */
    public static class FakeApiImpl implements FakeApi {
        @Override
        public String someMethod(@Verify(Consumer.class) String uuid) {
            return null;
        }

        @Override
        public String getCollection(@Verify(Consumer.class) Collection<String> uuids) {
            return "return value";
        }

        @Override
        public String multiValueVerify(
            @Verify({AnonymousCloudConsumer.class, Consumer.class}) String uuid) {
            return uuid;
        }
    }

    /*
     * simple Guice Module to turn our ConsumerCurator into a mock object.
     */
    private static class AuthInterceptorTestModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(PermissionFactory.class).toInstance(mock(PermissionFactory.class));
            bind(StoreFactory.class);
            bind(FakeResource.class);
            bind(FakeApiImpl.class);
        }
    }
}
