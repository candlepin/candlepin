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
package org.fedoraproject.candlepin.auth.interceptor;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.PathParam;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.guice.I18nProvider;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;

/**
 *
 */
public class ConsumerEnforcerTest {

    private Consumer requestor;
    private TestResource resource;

    @Before
    public void init() {
        this.requestor = new Consumer();
        Principal principal = new ConsumerPrincipal(this.requestor);

        AbstractModule module = new TestModule(principal);
        Injector injector = Guice.createInjector(module);
        this.resource = injector.getInstance(TestResource.class);
    }

    @Test
    public void permissionGranted() {
        String uuid = this.requestor.getUuid();
        Assert.assertEquals("consumer", this.resource.getConsumer(uuid));
    }

    @Test
    public void permissionGrantedForDefault() {
        String uuid = this.requestor.getUuid();
        Assert.assertEquals("defaultConsumer", this.resource.getDefaultConsumer(uuid));
    }

    @Test(expected = ForbiddenException.class)
    public void permissionDenied() {
        this.resource.getConsumer("randomuuid98752");
    }

    @Test(expected = ForbiddenException.class)
    public void noPathParam() {
        this.resource.noPathParam(4, "whatever");
    }

    @Test(expected = ForbiddenException.class)
    public void noMatchingPathParam() {
        this.resource.noMatchingPathParam("whatever");
    }
    
    @Test(expected = NotFoundException.class)
    public void noConsumerFound() {
        // mock out the consumer curator that always returns
        // null for lookupConsumerByUuid
        AbstractModule module = new TestModule(new ConsumerPrincipal(this.requestor), true);
        Injector injector = Guice.createInjector(module);
        this.resource = injector.getInstance(TestResource.class);
        
        this.resource.getConsumer("thisIsNull");
    }

    class TestModule extends AbstractModule {

        private Principal principal;
        private boolean nullUuidLookup;

        public TestModule(Principal principal) {
            this(principal, false);
        }
        
        public TestModule(Principal principal, boolean nullUuidLookup) {
            this.principal = principal;
            this.nullUuidLookup = nullUuidLookup;
        }

        @Override
        protected void configure() {
            bind(TestResource.class);
            bind(Principal.class).toInstance(principal);
            bind(ConsumerCurator.class).toInstance(createConsumerCurator());
            bind(EntityManager.class).toInstance(mock(EntityManager.class));
            bind(I18n.class).toProvider(I18nProvider.class);
            bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
            
            ConsumerEnforcer consumerEnforcer = new ConsumerEnforcer();
            requestInjection(consumerEnforcer);

            bindInterceptor(Matchers.any(),
                Matchers.annotatedWith(EnforceConsumer.class), consumerEnforcer);
        }
        
        private ConsumerCurator createConsumerCurator() {
            ConsumerCurator curator = mock(ConsumerCurator.class);
            
            when(curator.lookupByUuid(anyString())).thenAnswer(new Answer<Consumer>() {

                @Override
                public Consumer answer(InvocationOnMock invocation) throws Throwable {
                    if (nullUuidLookup) {
                        return null;
                    }
                    
                    String consumerUuid = (String) invocation.getArguments()[0];
                    Consumer consumer = new Consumer();
                    consumer.setUuid(consumerUuid);
                    
                    return consumer;
                }
                
            });
            
            return curator;
        }
    }

    public static class TestResource {

        @EnforceConsumer(pathParam = "consumer")
        public String getConsumer(@PathParam("consumer") String consumerUuid) {
            return "consumer";
        }

        @EnforceConsumer
        public String getDefaultConsumer(@PathParam("consumer_uuid") String uuid) {
            return "defaultConsumer";
        }

        @EnforceConsumer
        public boolean noPathParam(int someInt, String someString) {
            return true;
        }

        @EnforceConsumer(pathParam = "bar")
        public int noMatchingPathParam(@PathParam("notBar") String notBar) {
            return 42;
        }
    }
}
