/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.TestingModules;
import org.candlepin.auth.ActivationKeyPrincipal;
import org.candlepin.auth.AnonymousCloudConsumerPrincipal;
import org.candlepin.auth.CloudConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.TooManyRequestsException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotCreatedYetException;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotEntitledYetException;
import org.candlepin.test.TestUtil;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.SecurityContext;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SecurityHoleAuthorizationFilterTest {

    @Mock
    private OwnerCurator mockOwnerCurator;
    @Mock
    private PoolCurator mockPoolCurator;
    @Mock
    private CloudRegistrationAdapter mockCloudAdapter;
    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceInfo mockResourceInfo;
    private I18n i18n;

    private MockHttpRequest mockReq;
    private SecurityHoleAuthorizationFilter interceptor;

    @BeforeEach
    public void setUp() throws URISyntaxException, NoSuchMethodException {
        Injector injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.ServletEnvironmentModule(),
            new TestingModules.StandardTest());
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        MethodLocator methodLocator = new MethodLocator(injector);
        methodLocator.init();

        AnnotationLocator annotationLocator = new AnnotationLocator(methodLocator);
        interceptor = new SecurityHoleAuthorizationFilter(() -> this.i18n, annotationLocator,
            mockOwnerCurator, mockPoolCurator, mockCloudAdapter);
    }

    @AfterEach
    public void tearDown() {
        ResteasyContext.clearContextData();
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalAndOrgNotCreated() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenReturn(
            "owner_key");
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(false);

        TooManyRequestsException thrown = assertThrows(TooManyRequestsException.class, () -> {
            interceptor.runFilter(openRequest());
        });
        assertRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_NOT_CREATED_IN_CANDLEPIN, thrown);
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalAndOrgDoesNotHavePools() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenReturn(
            "owner_key");
        when(mockPoolCurator.hasPoolsForProducts(anyString(), any())).thenReturn(false);

        TooManyRequestsException thrown = assertThrows(TooManyRequestsException.class, () -> {
            interceptor.runFilter(openRequest());
        });
        assertRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_DOES_NOT_HAVE_POOLS, thrown);
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalAndOrgDoesNotExistAtAll() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenThrow(
            OrgForCloudAccountNotCreatedYetException.class);

        TooManyRequestsException thrown = assertThrows(TooManyRequestsException.class, () -> {
            interceptor.runFilter(openRequest());
        });
        assertRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_DOES_NOT_EXIST_AT_ALL, thrown);
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalAndCloudAccountNotEntitledYet() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenThrow(
            OrgForCloudAccountNotEntitledYetException.class);

        TooManyRequestsException thrown = assertThrows(TooManyRequestsException.class,
            () -> interceptor.runFilter(openRequest()));
        assertRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_IS_NOT_ENTITLED, thrown);
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalThatShouldPass() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenReturn(
            "owner_key");
        when(mockPoolCurator.hasPoolsForProducts(anyString(), any())).thenReturn(true);

        interceptor.runFilter(openRequest());
    }

    @ParameterizedTest
    @MethodSource("principals")
    public void shouldForbidAccessWithoutSecurityHole(Principal principal) {
        when(mockSecurityContext.getUserPrincipal()).thenReturn(principal);

        assertThatThrownBy(() -> interceptor.runFilter(secureRequest()))
            .isInstanceOf(ForbiddenException.class);
    }

    public static Stream<Arguments> principals() {
        return Stream.of(
            Arguments.of(new ActivationKeyPrincipal("keys")),
            Arguments.of(new AnonymousCloudConsumerPrincipal(mock(AnonymousCloudConsumer.class))),
            Arguments.of(new CloudConsumerPrincipal(new Owner()))
        );
    }

    private void setupAnonymousCloudConsumerPrincipal() {
        AnonymousCloudConsumer anonymousCloudConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId("account-id")
            .setCloudOfferingId("offering-id")
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(List.of("product-1"));
        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(
            anonymousCloudConsumer);
        when(mockSecurityContext.getUserPrincipal()).thenReturn(principal);
    }

    private void assertRetryAfterHeader(int expectedTime, TooManyRequestsException exception) {
        assertEquals(expectedTime, exception.getRetryAfterTime());
    }

    /**
     * Creates a request to an endpoint with a security hole defined
     *
     * @return mocked request
     */
    private ContainerRequestContext openRequest() {
        try {
            Class clazz = ConsumerResource.class;
            when(mockResourceInfo.getResourceClass()).thenReturn(clazz);

            mockReq = MockHttpRequest.create("POST", "http://localhost/candlepin/consumers");
            Method method = ConsumerResource.class.getMethod("createConsumer", ConsumerDTO.class,
                String.class, String.class, String.class, Boolean.class);
            when(mockResourceInfo.getResourceMethod()).thenReturn(method);

            ResteasyContext.pushContext(ResourceInfo.class, this.mockResourceInfo);
            ResteasyContext.pushContext(SecurityContext.class, this.mockSecurityContext);

            return new PostMatchContainerRequestContext(mockReq, null);
        }
        catch (URISyntaxException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a request to a secured endpoint
     *
     * @return mocked request
     */
    private ContainerRequestContext secureRequest() {
        try {
            Class clazz = ConsumerResource.class;

            when(mockResourceInfo.getResourceClass()).thenReturn(clazz);

            mockReq = MockHttpRequest.create("POST", "http://localhost/candlepin/consumers");
            Method method = ConsumerResource.class
                .getMethod("listConsumerContentOverrides", String.class);
            when(mockResourceInfo.getResourceMethod()).thenReturn(method);

            ResteasyContext.pushContext(ResourceInfo.class, this.mockResourceInfo);
            ResteasyContext.pushContext(SecurityContext.class, this.mockSecurityContext);

            return new PostMatchContainerRequestContext(mockReq, null);
        }
        catch (URISyntaxException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
