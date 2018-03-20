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

import static org.mockito.Mockito.*;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UpdateConsumerCheckIn;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.jboss.resteasy.core.interception.PostMatchContainerRequestContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;

/**
 * AuthInterceptorTest
 */
@RunWith(JukitoRunner.class)
public class ConsumerCheckInFilterTest extends DatabaseTestFixture {
    @Inject private Injector injector;

    @Mock private ContainerRequestContext mockRequestContext;
    @Mock private CandlepinSecurityContext mockSecurityContext;
    @Mock private ResourceInfo mockInfo;

    private ConsumerCheckInFilter interceptor;
    private MockHttpRequest mockReq;

    protected Module getGuiceOverrideModule() {
        return new ConsumerCheckInFilterModule();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Class clazz = FakeResource.class;
        when(mockInfo.getResourceClass()).thenReturn(clazz);

        mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/status");

        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner);
        ConsumerPrincipal principal = new ConsumerPrincipal(consumer, owner);

        ResteasyProviderFactory.pushContext(ResourceInfo.class, mockInfo);
        ResteasyProviderFactory.pushContext(Principal.class, principal);

        interceptor = new ConsumerCheckInFilter(consumerCurator);
    }

    private void mockResourceMethod(Method method) {
        when(mockInfo.getResourceMethod()).thenReturn(method);
    }

    private ContainerRequestContext getContext() {
        return new PostMatchContainerRequestContext(mockReq, null);
    }

    @Test
    public void testUpdatesCheckinWithAnnotation() throws Exception {
        Method method = FakeResource.class.getMethod("checkinMethod", String.class);
        mockResourceMethod(method);

        interceptor.filter(getContext());

        ConsumerPrincipal p = (ConsumerPrincipal) ResteasyProviderFactory.getContextData(Principal.class);
        doNothing().when(consumerCurator).updateLastCheckin(p.getConsumer());
        verify(consumerCurator).updateLastCheckin(p.getConsumer());
    }

    @Test
    public void testNoCheckinWithoutAnnotation() throws Exception {
        Method method = FakeResource.class.getMethod("someMethod", String.class);
        mockResourceMethod(method);

        interceptor.filter(getContext());

        ConsumerPrincipal p = (ConsumerPrincipal) ResteasyProviderFactory.getContextData(Principal.class);
        verify(consumerCurator, never()).updateLastCheckin(p.getConsumer());
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

    private static class ConsumerCheckInFilterModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(ConsumerCurator.class).toInstance(mock(ConsumerCurator.class));
        }
    }
}
