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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.candlepin.auth.AnonymousCloudConsumerPrincipal;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.exceptions.TooManyRequestsException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotCreatedYetException;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotEntitledYetException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.SecurityContext;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SecurityHoleAuthorizationFilterTest extends DatabaseTestFixture {

    @Mock
    private OwnerCurator mockOwnerCurator;
    @Mock
    private PoolCurator mockPoolCurator;
    @Mock
    private CloudRegistrationAdapter mockCloudAdapter;
    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private AnonymousCloudConsumerPrincipal mockAnonymousCloudConsumerPrincipal;
    @Mock
    private ResourceInfo mockResourceInfo;

    private MockHttpRequest mockReq;
    private SecurityHoleAuthorizationFilter interceptor;

    @BeforeEach
    public void setUp() throws URISyntaxException, NoSuchMethodException {
        MockitoAnnotations.initMocks(this);

        Class clazz = ConsumerResource.class;
        when(mockResourceInfo.getResourceClass()).thenReturn(clazz);

        mockReq = MockHttpRequest.create("POST", "http://localhost/candlepin/consumers");
        Method method = ConsumerResource.class.getMethod("createConsumer", ConsumerDTO.class, String.class,
            String.class, String.class, Boolean.class);
        mockResourceMethod(method);

        ResteasyContext.pushContext(ResourceInfo.class, this.mockResourceInfo);
        ResteasyContext.pushContext(SecurityContext.class, this.mockSecurityContext);

        MethodLocator methodLocator = new MethodLocator(injector);
        methodLocator.init();
        AnnotationLocator annotationLocator = new AnnotationLocator(methodLocator);
        interceptor = new SecurityHoleAuthorizationFilter(i18nProvider, annotationLocator, mockOwnerCurator,
            mockPoolCurator, mockCloudAdapter);
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
            interceptor.runFilter(getContext());
        });
        assertEqualsRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_NOT_CREATED_IN_CANDLEPIN, thrown);

    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalAndOrgDoesNotHavePools() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenReturn(
            "owner_key");
        when(mockPoolCurator.hasPoolsForProducts(anyString(), any())).thenReturn(false);

        TooManyRequestsException thrown = assertThrows(TooManyRequestsException.class, () -> {
            interceptor.runFilter(getContext());
        });
        assertEqualsRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_DOES_NOT_HAVE_POOLS, thrown);
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalAndOrgDoesNotExistAtAll() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenThrow(
            OrgForCloudAccountNotCreatedYetException.class);

        TooManyRequestsException thrown = assertThrows(TooManyRequestsException.class, () -> {
            interceptor.runFilter(getContext());
        });
        assertEqualsRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_DOES_NOT_EXIST_AT_ALL, thrown);
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalAndCloudAccountNotEntitledYet() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenThrow(
            OrgForCloudAccountNotEntitledYetException.class);

        TooManyRequestsException thrown = assertThrows(TooManyRequestsException.class, () -> {
            interceptor.runFilter(getContext());
        });
        assertEqualsRetryAfterHeader(SecurityHoleAuthorizationFilter.ORG_IS_NOT_ENTITLED, thrown);
    }

    @Test
    public void testRunFilterWithAnonymousCloudConsumerPrincipalThatShouldPass() {
        setupAnonymousCloudConsumerPrincipal();
        when(mockOwnerCurator.existsByKey(anyString())).thenReturn(true);
        when(mockCloudAdapter.checkCloudAccountOrgIsReady(anyString(), any(), anyString())).thenReturn(
            "owner_key");
        when(mockPoolCurator.hasPoolsForProducts(anyString(), any())).thenReturn(true);

        interceptor.runFilter(getContext());
    }

    private void setupAnonymousCloudConsumerPrincipal() {
        AnonymousCloudConsumer anonymousCloudConsumer = new AnonymousCloudConsumer()
            .setCloudAccountId("account-id")
            .setCloudOfferingId("offering-id")
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(List.of("product-1"));
        when(mockSecurityContext.getUserPrincipal()).thenReturn(mockAnonymousCloudConsumerPrincipal);
        when(mockAnonymousCloudConsumerPrincipal.getAnonymousCloudConsumer()).thenReturn(
            anonymousCloudConsumer);
    }

    private void assertEqualsRetryAfterHeader(int expectedTime, TooManyRequestsException exception) {
        assertEquals(expectedTime, exception.getRetryAfterTime());
    }

    private ContainerRequestContext getContext() {
        return new PostMatchContainerRequestContext(mockReq, null);
    }

    private Method mockResourceMethod(Method method) {
        when(mockResourceInfo.getResourceMethod()).thenReturn(method);
        return method;
    }
}
