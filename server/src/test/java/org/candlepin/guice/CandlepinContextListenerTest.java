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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.TestingModules;
import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.HornetqContextListener;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.ConfigurationPrefixes;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.junit.CandlepinLiquibaseResource;
import org.candlepin.pinsetter.core.PinsetterContextListener;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
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

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @ClassRule
    @Rule
    public static CandlepinLiquibaseResource liquibase = new CandlepinLiquibaseResource();

    @Before
    public void init() {
        config = mock(Configuration.class);

        when(config.subset(eq("org.quartz"))).thenReturn(
                new MapConfiguration(ConfigProperties.DEFAULT_PROPERTIES));
        when(config.strippedSubset(eq(ConfigurationPrefixes.LOGGING_CONFIG_PREFIX)))
            .thenReturn(new MapConfiguration());
        hqlistener = mock(HornetqContextListener.class);
        pinlistener = mock(PinsetterContextListener.class);
        buspublisher = mock(AMQPBusPublisher.class);
        busprovider = mock(AMQPBusPubProvider.class);
        configRead = mock(VerifyConfigRead.class);

        // for testing we override the getModules and readConfiguration methods
        // so we can insert our mock versions of listeners to verify
        // they are getting invoked properly.
        listener = new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<Module>();
                // tried simply overriding CandlepinModule
                // but that caused it to read the local config
                // which means the test becomes non-deterministic.
                // so just load the items we need to verify the
                // functionality.
                modules.add(new TestingModules.JpaModule());
                modules.add(new TestingModules.StandardTest(config));
                modules.add(new ContextListenerTestModule());
                return modules;
            }

            @Override
            protected Configuration readConfiguration(ServletContext context)
                throws ConfigurationException {

                configRead.verify(context);
                return config;
            }
        };
    }

    @Test
    public void contextInitialized() {
        when(config.getBoolean(eq(ConfigProperties.HORNETQ_ENABLED))).thenReturn(true);
        prepareForInitialization();
        listener.contextInitialized(evt);
        verify(hqlistener).contextInitialized(any(Injector.class));
        verify(pinlistener).contextInitialized();
        verify(ctx).setAttribute(
                eq(CandlepinContextListener.CONFIGURATION_NAME), eq(config));
        verify(configRead).verify(eq(ctx));
    }

    @Test
    public void hornetQDisabled() {
        when(config.getBoolean(eq(ConfigProperties.HORNETQ_ENABLED))).thenReturn(false);
        prepareForInitialization();
        listener.contextInitialized(evt);
        verifyNoMoreInteractions(hqlistener);
    }

    @Test
    public void contextDestroyed() {
        when(config.getBoolean(eq(ConfigProperties.HORNETQ_ENABLED))).thenReturn(true);
        prepareForInitialization();

        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null.
        listener.contextInitialized(evt);

        // what we really want to test.
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
        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null.
        listener.contextInitialized(evt);

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
        when(ctx.getAttribute(eq(
            CandlepinContextListener.CONFIGURATION_NAME))).thenReturn(config);
    }

    public class ContextListenerTestModule extends AbstractModule {
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
     * VerifyConfigRead fake interface to use with mockito's mock and verify
     * methods to make sure the correct var was passed in and we called a
     * method we expected.
     */
    interface VerifyConfigRead {
        void verify(ServletContext ctx);
    }
}
