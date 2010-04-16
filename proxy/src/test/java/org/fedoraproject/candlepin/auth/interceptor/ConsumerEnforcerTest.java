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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import javax.ws.rs.PathParam;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.resource.ForbiddenException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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

        Injector injector = Guice.createInjector(new TestModule(principal));
        this.resource = injector.getInstance(TestResource.class);
    }

    @Test
    public void permissionGranted() {
        String uuid = this.requestor.getUuid();
        Assert.assertEquals("defaultConsumer", this.resource.getDefaultConsumer(uuid));
    }

    @Test
    public void permissionGrantedForDefault() {
        String uuid = this.requestor.getUuid();
        Assert.assertEquals("consumer", this.resource.getConsumer(uuid));
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

    class TestModule extends AbstractModule {

        private Principal principal;

        public TestModule(Principal principal) {
            this.principal = principal;
        }

        @Override
        protected void configure() {
            bind(TestResource.class);
            bind(Principal.class).toInstance(principal);
            ConsumerEnforcer consumerEnforcer = new ConsumerEnforcer();
            requestInjection(consumerEnforcer);

            bindInterceptor(Matchers.any(),
                Matchers.annotatedWith(EnforceConsumer.class), consumerEnforcer);
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
