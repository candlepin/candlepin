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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UpdateConsumerCheckIn;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.test.DatabaseTestFixture;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
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

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;



/**
 * AuthInterceptorTest
 */
public class ConsumerCheckInFilterTest extends DatabaseTestFixture {
    @Inject private Injector injector;

    @Mock private ContainerRequestContext mockRequestContext;
    @Mock private CandlepinSecurityContext mockSecurityContext;
    @Mock private ResourceInfo mockInfo;

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
        interceptor = new ConsumerCheckInFilter(consumerCurator, annotationLocator);
    }

    private void mockResourceMethod(Method method) {
        when(mockInfo.getResourceMethod()).thenReturn(method);
    }

    private ContainerRequestContext getContext() {
        return new PostMatchContainerRequestContext(mockReq, null);
    }

    @Test
    public void testUpdatesCheckinWithAnnotation() throws Exception {
        testUpdatesCheckinWithAnnotationForResource(FakeResource.class);
    }

    @Test
    void testUpdatesCheckinWithAnnotationSpecFirst() throws Exception {
        testUpdatesCheckinWithAnnotationForResource(FakeApi.class);
    }

    void testUpdatesCheckinWithAnnotationForResource(Class<?> resourceClass) throws Exception {
        Date lastCheckin = this.consumer.getLastCheckin();
        Thread.sleep(1000);

        Method method = resourceClass.getMethod("checkinMethod", String.class);
        mockResourceMethod(method);

        interceptor.filter(getContext());

        ConsumerPrincipal p = (ConsumerPrincipal) ResteasyContext.getContextData(Principal.class);

        this.consumerCurator.refresh(this.consumer);

        Date updatedLastCheckin = p.getConsumer().getLastCheckin();
        assertNotEquals(lastCheckin, updatedLastCheckin);
    }

    @Test
    public void testNoCheckinWithoutAnnotation() throws Exception {
        testNoCheckinWithoutAnnotationForResource(FakeResource.class);
    }

    @Test
    void testNoCheckinWithoutAnnotationSpecFirst() throws Exception {
        testNoCheckinWithoutAnnotationForResource(FakeApi.class);
    }

    void testNoCheckinWithoutAnnotationForResource(Class<?> resourceClass) throws Exception {
        Date lastCheckin = this.consumer.getLastCheckin();
        Thread.sleep(1000);

        Method method = resourceClass.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        interceptor.filter(getContext());

        ConsumerPrincipal p = (ConsumerPrincipal) ResteasyContext.getContextData(Principal.class);

        this.consumerCurator.refresh(this.consumer);

        Date updatedLastCheckin = p.getConsumer().getLastCheckin();
        assertEquals(lastCheckin, updatedLastCheckin);
    }

    /**
     * FakeResource simply to create a Method object to pass down into
     * the interceptor.
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
        }
    }
}
