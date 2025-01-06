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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UpdateConsumerCheckIn;
import org.candlepin.controller.ConsumerManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ObjectMapperFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;



/**
 * AuthInterceptorTest
 */
public class ConsumerCheckInFilterTest extends DatabaseTestFixture {

    @Mock
    private ResourceInfo mockInfo;
    @Mock
    private EventAdapter mockEventAdapter;
    @Mock
    private ContentAccessCertificateCurator caCertificateCurator;
    @Mock
    private EnvironmentCurator envCurator;

    private ConsumerCheckInFilter interceptor;
    private MockHttpRequest mockReq;

    private Consumer consumer;
    private ConsumerPrincipal principal;

    protected Module getGuiceOverrideModule() {
        return new ConsumerCheckInFilterModule();
    }

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Class clazz = FakeResource.class;
        when(mockInfo.getResourceClass()).thenReturn(clazz);

        mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/status");

        Owner owner = createOwner();
        this.consumer = createConsumer(owner);
        this.principal = new ConsumerPrincipal(consumer, owner);

        ResteasyContext.pushContext(ResourceInfo.class, mockInfo);
        ResteasyContext.pushContext(Principal.class, this.principal);

        MethodLocator methodLocator = new MethodLocator(injector);
        methodLocator.init();
        AnnotationLocator annotationLocator = new AnnotationLocator(methodLocator);

        ConsumerManager consumerManager = new ConsumerManager(consumerCurator, caCertificateCurator,
            envCurator, mockEventAdapter, ObjectMapperFactory.getObjectMapper());
        interceptor = new ConsumerCheckInFilter(annotationLocator, consumerManager);
    }

    private void mockResourceMethod(Method method) {
        when(mockInfo.getResourceMethod()).thenReturn(method);
    }

    private ContainerRequestContext getContext() {
        return new PostMatchContainerRequestContext(mockReq, null);
    }

    private void testUpdatesCheckinWithAnnotationForResource(Class<?> resourceClass) throws Exception {
        Date lastCheckin = this.consumer.getLastCheckin();
        Thread.sleep(1000);

        Method method = resourceClass.getMethod("checkinMethod", String.class);
        mockResourceMethod(method);

        interceptor.filter(getContext());

        ConsumerPrincipal p = (ConsumerPrincipal) ResteasyContext.getContextData(Principal.class);

        Date updatedLastCheckin = p.getConsumer().getLastCheckin();
        assertNotEquals(lastCheckin, updatedLastCheckin);

        // Verify that we did not get a cloud checkin event for this consumer
        verifyNoInteractions(this.mockEventAdapter);
    }

    @Test
    public void testUpdatesCheckinWithAnnotation() throws Exception {
        testUpdatesCheckinWithAnnotationForResource(FakeResource.class);
    }

    @Test
    public void testUpdatesCheckinWithAnnotationSpecFirst() throws Exception {
        testUpdatesCheckinWithAnnotationForResource(FakeApi.class);
    }

    private void testNoCheckinWithoutAnnotationForResource(Class<?> resourceClass) throws Exception {
        Date lastCheckin = this.consumer.getLastCheckin();
        Thread.sleep(1000);

        Method method = resourceClass.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        interceptor.filter(getContext());

        ConsumerPrincipal p = (ConsumerPrincipal) ResteasyContext.getContextData(Principal.class);

        Date updatedLastCheckin = p.getConsumer().getLastCheckin();
        assertEquals(lastCheckin, updatedLastCheckin);

        // Verify that we did not get a cloud checkin event for this consumer
        verifyNoInteractions(this.mockEventAdapter);
    }

    @Test
    public void testNoCheckinWithoutAnnotation() throws Exception {
        testNoCheckinWithoutAnnotationForResource(FakeResource.class);
    }

    @Test
    public void testNoCheckinWithoutAnnotationSpecFirst() throws Exception {
        testNoCheckinWithoutAnnotationForResource(FakeApi.class);
    }

    private void checkinWithCloudData(Class<?> resourceClass, ConsumerCloudData cloudData)
        throws Exception {

        Method method = resourceClass.getMethod("checkinMethod", String.class);
        mockResourceMethod(method);

        Date lastCheckin = new Date();

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner)
            .setConsumerCloudData(cloudData)
            .setLastCheckin(lastCheckin);

        Thread.sleep(250);

        Principal principal = new ConsumerPrincipal(consumer, owner);
        ResteasyContext.pushContext(Principal.class, principal);

        interceptor.filter(this.getContext());

        assertNotEquals(lastCheckin, consumer.getLastCheckin());

        // We should receive an checkin event on the adapter
        verify(this.mockEventAdapter, times(1)).publish(any(CloudCheckInEvent.class));
    }

    @Test
    public void testCheckInWithCloudDataOnDirectAnnotation() throws Exception {
        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudProviderShortName("fake_cloud")
            .setCloudAccountId("account_id")
            .addCloudOfferingIds("offering_1");

        this.checkinWithCloudData(FakeResource.class, cloudData);
    }

    @Test
    public void testCheckInWithCloudDataOnInheritedAnnotation() throws Exception {
        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudProviderShortName("fake_cloud")
            .setCloudAccountId("account_id")
            .addCloudOfferingIds("offering_1");

        this.checkinWithCloudData(FakeApi.class, cloudData);
    }

    @Test
    public void testCheckinWithMinimalCloudDataOnDirectAnnotation() throws Exception {
        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudProviderShortName("fake_cloud")
            .setCloudAccountId(null)
            .setCloudOfferingIds(List.of());

        this.checkinWithCloudData(FakeResource.class, cloudData);
    }

    @Test
    public void testCheckinWithMinimalCloudDataOnInheritedAnnotation() throws Exception {
        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudProviderShortName("fake_cloud")
            .setCloudAccountId(null)
            .setCloudOfferingIds(List.of());

        this.checkinWithCloudData(FakeApi.class, cloudData);
    }

    /**
     * FakeResource simply to create a Method object to pass down into the interceptor.
     */
    public static class FakeResource {
        public String someMethod(String str) {
            return str;
        }

        @UpdateConsumerCheckIn
        public String checkinMethod(String str) {
            return str;
        }
    }

    /**
     * FakeApi helps test that our filter works on spec-first APIs.
     */
    @Path("/fake")
    public interface FakeApi {
        @Path("1")
        String someMethod(String str);

        @Path("2")
        String checkinMethod(String str);
    }

    /**
     * FakeApiImpl helps test that our filter works on spec-first APIs.
     */
    public static class FakeApiImpl implements FakeApi {
        public String someMethod(String str) {
            return str;
        }

        @UpdateConsumerCheckIn
        public String checkinMethod(String str) {
            return str;
        }
    }

    private static class ConsumerCheckInFilterModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(FakeApiImpl.class);
            bind(FakeResource.class);
            bind(ConsumerCurator.class);
        }
    }
}
