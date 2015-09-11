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
package org.candlepin.gutterball.resteasy.filter;

import static org.mockito.Mockito.*;

import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.gutterball.GutterballTestingModule;
import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.jboss.resteasy.core.InjectorFactoryImpl;
import org.jboss.resteasy.core.ValueInjector;
import org.jboss.resteasy.spi.ApplicationException;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.MethodInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.metadata.ResourceLocator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;

import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;



/**
 * OAuthInterceptorTest
 */
public class OAuthFilterTest {
    private static Logger log = LoggerFactory.getLogger(OAuthFilter.class);

    private Injector injector;
    private javax.inject.Provider<I18n> i18nProvider;
    private OAuthFilter interceptor;

    private StubMethodInjector methodInjector;


    public static class StubInjectorFactoryImpl extends InjectorFactoryImpl {
        private StubMethodInjector stub;

        public StubInjectorFactoryImpl() {
            super();
            this.stub = new StubMethodInjector();
        }

        @Override
        public MethodInjector createMethodInjector(ResourceLocator method, ResteasyProviderFactory factory) {
            return stub;
        }
    }

    public static class StubMethodInjector implements MethodInjector {
        private Object[] arguments;

        @Override
        public Object invoke(HttpRequest request, HttpResponse response, Object target)
            throws Failure, ApplicationException, WebApplicationException {

            return null;
        }

        public Object[] getArguments() {
            return arguments;
        }

        public void setArguments(Object[] arguments) {
            this.arguments = arguments;
        }

        @Override
        public Object[] injectArguments(HttpRequest request, HttpResponse response) throws Failure {
            return arguments;
        }

        @Override
        public ValueInjector[] getParams() {
            return null;
        }

        @Override
        public boolean expectsBody() {
            return false;
        }
    }

    /**
     * FakeResource simply to create a Method object to pass down into
     * the interceptor.
     */
    public static class FakeResource {
        public String someMethod(String str) {
            return str;
        }

        @SecurityHole
        public String annotatedMethod(String str) {
            return str;
        }
    }


    @Before
    public void setUp() {
        Configuration config = new MapConfiguration();
        GutterballTestingModule testModule = new GutterballTestingModule(config);

        this.injector = Guice.createInjector(testModule);
        this.i18nProvider = (javax.inject.Provider<I18n>) this.injector.getInstance(I18nProvider.class);
        this.interceptor = new OAuthFilter(config, this.i18nProvider);

        ResteasyProviderFactory factory = ResteasyProviderFactory.getInstance();
        factory.registerProvider(StubInjectorFactoryImpl.class);
        this.methodInjector = (StubMethodInjector)
            factory.getInjectorFactory().createMethodInjector(null, factory);

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        Enumeration e = mock(Enumeration.class);
        when(e.hasMoreElements()).thenReturn(Boolean.FALSE);
        when(mockRequest.getHeaderNames()).thenReturn(e);
        ResteasyProviderFactory.pushContext(HttpServletRequest.class, mockRequest);
    }
//
//    @Test
//    public void testSecurityHoleMethodRejectedOAuthEnabled() throws Exception {
//        Class declarer = FakeResource.class;
//        Method method = declarer.getMethod("annotatedMethod", String.class);
//
//        Configuration config = new MapConfiguration();
//        config.setProperty(ConfigProperties.OAUTH_AUTHENTICATION, "true");
//
//        OAuthFilter interceptor = new OAuthFilter(config, this.i18nProvider);
//        boolean result = interceptor.accept(declarer, method);
//
//        Assert.assertFalse(result);
//    }
//
//    @Test
//    public void testStandardMethodAccepted() throws Exception {
//        Class declarer = FakeResource.class;
//        Method method = declarer.getMethod("someMethod", String.class);
//
//        Configuration config = new MapConfiguration();
//        config.setProperty(ConfigProperties.OAUTH_AUTHENTICATION, "true");
//
//        OAuthFilter interceptor = new OAuthFilter(config, this.i18nProvider);
//        boolean result = interceptor.accept(declarer, method);
//
//        Assert.assertTrue(result);
//    }

    @Test
    public void testGetAccessor() throws Exception {
        String consumer = "test_consumer";
        String secret = "test_secret";

        OAuthMessage message = mock(OAuthMessage.class);
        when(message.getConsumerKey()).thenReturn(consumer);

        Configuration config = new MapConfiguration();
        config.setProperty("gutterball.auth.oauth.consumer.test_consumer.secret", secret);
        OAuthFilter interceptor = new OAuthFilter(config, this.i18nProvider);

        OAuthAccessor expected = new OAuthAccessor(new OAuthConsumer("", consumer, secret, null));
        OAuthAccessor result = interceptor.getAccessor(message);

        Assert.assertEquals(expected.requestToken, result.requestToken);
        Assert.assertEquals(expected.accessToken, result.accessToken);
        Assert.assertEquals(expected.tokenSecret, result.tokenSecret);
    }

    @Test
    public void testAccessorNotFound() throws Exception {
        OAuthMessage message = mock(OAuthMessage.class);
        when(message.getConsumerKey()).thenReturn("test_consumer");

        OAuthAccessor result = this.interceptor.getAccessor(message);

        Assert.assertNull(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetAccessorInvalidInput() throws Exception {
        this.interceptor.getAccessor(null);
    }
}
