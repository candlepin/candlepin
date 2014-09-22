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
package org.candlepin.guice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.CandlepinCommonTestingModule;
import org.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.HornetqContextListener;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.pinsetter.core.PinsetterContextListener;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 * CandlepinContextListenerTest
 */
public class CandlepinContextListenerTest {
    private Configuration config;
    private CandlepinContextListener listener;
    private HornetqContextListener hqlistener;
    private PinsetterContextListener pinlistener;
    private AMQPBusPublisher buspublisher;
    private AMQPBusPubProvider busprovider;
    private ServletContextEvent evt;
    private ServletContext ctx;
    private VerifyConfigRead configRead;

    @Before
    public void init() {
        config = mock(Configuration.class);
        hqlistener = mock(HornetqContextListener.class);
        pinlistener = mock(PinsetterContextListener.class);
        buspublisher = mock(AMQPBusPublisher.class);
        busprovider = mock(AMQPBusPubProvider.class);
        configRead = mock(VerifyConfigRead.class);

        // for testing we override the getModules and readConfiguration methods
        // so we can insert our mock versions of listeners to verify
        // they are getting invoked properly.
        listener = new CandlepinContextListener() {
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<Module>();
                // tried simply overriding CandlepinModule
                // but that caused it to read the local config
                // which means the test becomes non-deterministic.
                // so just load the items we need to verify the
                // functionality.
                modules.add(new ConfigModule(config));
                modules.add(new CandlepinNonServletEnvironmentTestingModule());
                modules.add(new TestModule());
                return modules;
            }

            protected Configuration readConfiguration(ServletContext context)
                throws ConfigurationException {

                configRead.verify(context);
                return config;
            }
        };
    }

    @Test
    public void contextInitialized() {
        prepareForInitialization();
        listener.contextInitialized(evt);
        verify(hqlistener).contextInitialized(any(Injector.class));
        verify(pinlistener).contextInitialized();
        verify(ctx).setAttribute(
                eq(CandlepinContextListener.CONFIGURATION_NAME), eq(config));
        verify(configRead).verify(eq(ctx));
    }

    @Test
    public void contextDestroyed() {
        prepareForInitialization();
        listener.contextInitialized(evt);

        // ^^^
        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null. So all the above is simply
        // to setup the test to validate the destruction is doing the
        // proper thing.

        listener.contextDestroyed(evt);

        // make sure we only call it 5 times all from init code
        verify(evt, atMost(5)).getServletContext();
        verifyNoMoreInteractions(evt); // destroy shouldn't use it
        verify(hqlistener).contextDestroyed();
        verify(pinlistener).contextDestroyed();
        verifyZeroInteractions(busprovider);
        verifyZeroInteractions(buspublisher);
    }

    @Test
    public void ensureAMQPClosedProperly() {
        when(config.getBoolean(
                eq(ConfigProperties.AMQP_INTEGRATION_ENABLED))).thenReturn(true);
        prepareForInitialization();
        listener.contextInitialized(evt);

        // ^^^
        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null. So all the above is simply
        // to setup the test to validate the destruction is doing the
        // proper thing.

        // test & verify
        listener.contextDestroyed(evt);
        verify(busprovider).close();
        verify(buspublisher).close();
    }

    @Test(expected = RuntimeException.class)
    public void tharSheBlows() {
        listener = new CandlepinContextListener() {
            protected List<Module> getModules(ServletContext context) {
                return new LinkedList<Module>();
            }

            protected Configuration readConfiguration(ServletContext context)
                throws ConfigurationException {

                throw new ConfigurationException("the ship is sinking");
            }
        };
        prepareForInitialization();
        listener.contextInitialized(evt);
    }

    @Test
    public void exitStageLeft() {
        assertEquals(Stage.PRODUCTION, listener.getStage(ctx));
    }

    @Test
    public void testInjector() {
        Injector injector = listener.getInjector(Stage.PRODUCTION, listener.getModules(null));
        assertNotNull(injector);
        PinsetterContextListener pcl = injector.getInstance(PinsetterContextListener.class);
        assertNotNull(pcl);
        assertEquals(pinlistener, pcl);
    }

    private void prepareForInitialization() {
        evt = mock(ServletContextEvent.class);
        ctx = mock(ServletContext.class);
        Registry registry = mock(Registry.class);
        ResteasyProviderFactory rpfactory = mock(ResteasyProviderFactory.class);
        when(evt.getServletContext()).thenReturn(ctx);
        when(ctx.getAttribute(eq(
            Registry.class.getName()))).thenReturn(registry);
        when(ctx.getAttribute(eq(
            ResteasyProviderFactory.class.getName()))).thenReturn(rpfactory);
    }

    public class TestModule extends AbstractModule {

        @SuppressWarnings("synthetic-access")
        @Override
        protected void configure() {
            bind(PinsetterContextListener.class).toInstance(pinlistener);
            bind(HornetqContextListener.class).toInstance(hqlistener);
            bind(AMQPBusPublisher.class).toInstance(buspublisher);
            bind(AMQPBusPubProvider.class).toInstance(busprovider);
        }
    }

    /**
     * ConfigModule overrides the config from the testing module with the one
     * from this test class. This allows us to override the configuration.
     */
    public class ConfigModule extends CandlepinCommonTestingModule {

        public ConfigModule(Configuration config) {
            super(config);
        }

        @SuppressWarnings("synthetic-access")
        protected void bindConfig() {
            bind(Configuration.class).toInstance(config);
        }
    }

    /**
     * VerifyConfigRead fake interface to use with mockito's mock and verify
     * methods to make sure the correct var was passed in and we called a
     * method we expected.
     */
    interface VerifyConfigRead {
        void verify(ServletContext ctx);
    }
}
