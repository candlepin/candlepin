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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.TestingModules;
import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidStatus;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.ConfigurationPrefixes;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.junit.CandlepinLiquibaseResource;
import org.candlepin.model.Status;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 * CandlepinContextListenerTest
 */
public class CandlepinContextListenerTest {
    private Configuration config;
    private CandlepinContextListener listener;
    private ActiveMQContextListener hqlistener;
    private PinsetterContextListener pinlistener;
    private AMQPBusPublisher buspublisher;
    private ScheduledExecutorService executorService;
    private ServletContextEvent evt;
    private ServletContext ctx;
    private VerifyConfigRead configRead;
    private QpidQmf qmf;

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
        hqlistener = mock(ActiveMQContextListener.class);
        pinlistener = mock(PinsetterContextListener.class);
        buspublisher = mock(AMQPBusPublisher.class);
        executorService = mock(ScheduledExecutorService.class);
        configRead = mock(VerifyConfigRead.class);
        qmf = mock(QpidQmf.class);

        // for testing we override the getModules and readConfiguration methods
        // so we can insert our mock versions of listeners to verify
        // they are getting invoked properly.
        listener = new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
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
        when(config.getBoolean(eq(ConfigProperties.ACTIVEMQ_ENABLED))).thenReturn(true);
        prepareForInitialization();
        listener.contextInitialized(evt);
        verify(hqlistener).contextInitialized(any(Injector.class));
        verify(pinlistener).contextInitialized();
        verify(ctx).setAttribute(
            eq(CandlepinContextListener.CONFIGURATION_NAME), eq(config));
        verify(configRead).verify(eq(ctx));

        Set<String> displayedCapabilities = new HashSet<>(Arrays.asList(Status.getAvailableCapabilities()));
        Set<String> expectedCapabilities = new HashSet<>(Arrays.asList(Status.DEFAULT_CAPABILITIES));
        assertEquals(expectedCapabilities, displayedCapabilities);
    }

    @Test
    public void blackListsCapabilities() {
        Set<String> testSet = new HashSet<>();
        testSet.add("cores");
        testSet.add("ram");

        when(config.getSet(eq(ConfigProperties.HIDDEN_CAPABILITIES), any(Set.class))).thenReturn(testSet);
        prepareForInitialization();
        listener.contextInitialized(evt);

        Set<String> expectedCapabilities = new HashSet<>(Arrays.asList(Status.DEFAULT_CAPABILITIES));
        expectedCapabilities.remove("cores");
        expectedCapabilities.remove("ram");

        Set<String> displayedCapabilities = new HashSet<>(Arrays.asList(Status.getAvailableCapabilities()));
        assertEquals(expectedCapabilities, displayedCapabilities);
    }

    @Test
    public void activeMQDisabled() {
        when(config.getBoolean(eq(ConfigProperties.ACTIVEMQ_ENABLED))).thenReturn(false);
        prepareForInitialization();
        listener.contextInitialized(evt);
        verifyNoMoreInteractions(hqlistener);
    }

    @Test
    public void contextDestroyed() {
        when(config.getBoolean(eq(ConfigProperties.ACTIVEMQ_ENABLED))).thenReturn(true);
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
        verifyZeroInteractions(buspublisher);
    }

    @Test
    public void ensureAMQPClosedProperly() {
        when(config.getBoolean(
                eq(ConfigProperties.AMQP_INTEGRATION_ENABLED))).thenReturn(true);
        when(config.getBoolean(eq(ConfigProperties.SUSPEND_MODE_ENABLED))).thenReturn(true);
        prepareForInitialization();
        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null.
        listener.contextInitialized(evt);

        // test & verify
        listener.contextDestroyed(evt);
        verify(buspublisher).close();
    }

    @Test(expected = RuntimeException.class)
    public void tharSheBlows() {
        listener = new CandlepinContextListener() {
            protected List<Module> getModules(ServletContext context) {
                return new LinkedList<>();
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
        when(ctx.getAttribute(eq(Registry.class.getName()))).thenReturn(registry);
        when(ctx.getAttribute(eq(ResteasyProviderFactory.class.getName()))).thenReturn(rpfactory);
        when(ctx.getAttribute(eq(CandlepinContextListener.CONFIGURATION_NAME))).thenReturn(config);
        when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
    }

    public class ContextListenerTestModule extends AbstractModule {
        @SuppressWarnings("synthetic-access")
        @Override
        protected void configure() {
            bind(PinsetterContextListener.class).toInstance(pinlistener);
            bind(ActiveMQContextListener.class).toInstance(hqlistener);
            bind(AMQPBusPublisher.class).toInstance(buspublisher);
            bind(ScheduledExecutorService.class).toInstance(executorService);
            bind(QpidQmf.class).toInstance(qmf);
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
