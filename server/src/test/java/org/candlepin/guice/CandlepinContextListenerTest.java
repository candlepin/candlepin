///**
// * Copyright (c) 2009 - 2012 Red Hat, Inc.
// *
// * This software is licensed to you under the GNU General Public License,
// * version 2 (GPLv2). There is NO WARRANTY for this software, express or
// * implied, including the implied warranties of MERCHANTABILITY or FITNESS
// * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
// * along with this software; if not, see
// * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
// *
// * Red Hat trademarks are not licensed under GPLv2. No permission is
// * granted to use or replicate Red Hat trademarks that are incorporated
// * in this software or its documentation.
// */
//package org.candlepin.guice;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.Mockito.any;
//import static org.mockito.Mockito.atMost;
//import static org.mockito.Mockito.eq;
//import static org.mockito.Mockito.isNull;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoMoreInteractions;
//import static org.mockito.Mockito.verifyZeroInteractions;
//import static org.mockito.Mockito.when;
//
//import org.candlepin.TestingModules;
//import org.candlepin.audit.AMQPBusPublisher;
//import org.candlepin.audit.ActiveMQContextListener;
//import org.candlepin.audit.QpidQmf;
//import org.candlepin.audit.QpidStatus;
//import org.candlepin.common.config.Configuration;
//import org.candlepin.common.config.ConfigurationException;
//import org.candlepin.common.config.ConfigurationPrefixes;
//import org.candlepin.common.config.MapConfiguration;
//import org.candlepin.config.ConfigProperties;
//import org.candlepin.junit.LiquibaseExtension;
//
//import com.google.inject.AbstractModule;
//import com.google.inject.Injector;
//import com.google.inject.Module;
//import com.google.inject.Stage;
//
//import org.jboss.resteasy.spi.Registry;
//import org.jboss.resteasy.spi.ResteasyDeployment;
//import org.jboss.resteasy.spi.ResteasyProviderFactory;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//
//import java.io.File;
//import java.sql.Driver;
//import java.sql.DriverManager;
//import java.sql.SQLException;
//import java.util.Enumeration;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Set;
//import java.util.concurrent.ScheduledExecutorService;
//
//import javax.servlet.ServletContext;
//import javax.servlet.ServletContextEvent;
//
//
//
///**
// * CandlepinContextListenerTest
// */
//@ExtendWith(LiquibaseExtension.class)
//public class CandlepinContextListenerTest {
//    private Configuration config;
//    private CandlepinContextListener listener;
//    private ActiveMQContextListener hqlistener;
//    private AMQPBusPublisher buspublisher;
//    private ScheduledExecutorService executorService;
//    private ServletContextEvent evt;
//    private ServletContext ctx;
//    private ResteasyDeployment resteasyDeployment;
//    private VerifyConfigRead configRead;
//    private QpidQmf qmf;
//
//    @BeforeEach
//    public void init() {
//        config = mock(Configuration.class);
//
//        when(config.subset(eq("org.quartz"))).thenReturn(
//            new MapConfiguration(ConfigProperties.DEFAULT_PROPERTIES));
//        when(config.strippedSubset(eq(ConfigurationPrefixes.LOGGING_CONFIG_PREFIX)))
//            .thenReturn(new MapConfiguration());
//        when(config.getString(ConfigProperties.CRL_FILE_PATH))
//            .thenReturn("/tmp/tmp.crl");
//        hqlistener = mock(ActiveMQContextListener.class);
//        buspublisher = mock(AMQPBusPublisher.class);
//        executorService = mock(ScheduledExecutorService.class);
//        configRead = mock(VerifyConfigRead.class);
//        qmf = mock(QpidQmf.class);
//
//        // for testing we override the getModules and readConfiguration methods
//        // so we can insert our mock versions of listeners to verify
//        // they are getting invoked properly.
//        listener = new CandlepinContextListener() {
//            @Override
//            protected List<Module> getModules(ServletContext context) {
//                List<Module> modules = new LinkedList<>();
//                // tried simply overriding CandlepinModule
//                // but that caused it to read the local config
//                // which means the test becomes non-deterministic.
//                // so just load the items we need to verify the
//                // functionality.
//                modules.add(new TestingModules.JpaModule());
//                modules.add(new TestingModules.StandardTest(config));
//                modules.add(new ContextListenerTestModule());
//                return modules;
//            }
//
//            @Override
//            protected Configuration readConfiguration(ServletContext context)
//                throws ConfigurationException {
//
//                configRead.verify(context);
//                return config;
//            }
//        };
//    }
//
//    @Test
//    public void contextInitialized() {
//        when(config.getBoolean(eq(ConfigProperties.ACTIVEMQ_ENABLED))).thenReturn(true);
//        prepareForInitialization();
//        listener.contextInitialized(evt);
//        verify(hqlistener).contextInitialized(any(Injector.class));
//        verify(ctx).setAttribute(eq(CandlepinContextListener.CONFIGURATION_NAME), eq(config));
//        verify(configRead).verify(eq(ctx));
//
//        CandlepinCapabilities expected = new CandlepinCapabilities();
//        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();
//
//        assertEquals(expected, actual);
//    }
//
//    @Test
//    public void blackListsCapabilities() {
//        Set<String> testSet = new HashSet<>();
//        testSet.add("cores");
//        testSet.add("ram");
//
//        when(config.getSet(eq(ConfigProperties.HIDDEN_CAPABILITIES), isNull())).thenReturn(testSet);
//        prepareForInitialization();
//        listener.contextInitialized(evt);
//
//        CandlepinCapabilities expected = new CandlepinCapabilities();
//        expected.removeAll(testSet);
//
//        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();
//
//        assertEquals(expected, actual);
//    }
//
//    @Test
//    void keycloakCapabilityPresentWhenKeycloakEnabled() {
//        when(config.getBoolean(eq(ConfigProperties.KEYCLOAK_AUTHENTICATION))).thenReturn(true);
//        prepareForInitialization();
//        listener.contextInitialized(evt);
//
//        CandlepinCapabilities expected = new CandlepinCapabilities();
//        expected.add("keycloak_auth");
//        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();
//
//        assertEquals(expected, actual);
//    }
//
//    @Test
//    void keycloakCapabilityAbsentWhenKeycloakDisabled() {
//        when(config.getBoolean(eq(ConfigProperties.KEYCLOAK_AUTHENTICATION))).thenReturn(false);
//        prepareForInitialization();
//        listener.contextInitialized(evt);
//
//        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();
//
//        assertFalse(actual.contains("keycloak_auth"), "keycloak_auth present but not expected");
//    }
//
//    @Test
//    public void activeMQDisabled() {
//        when(config.getBoolean(eq(ConfigProperties.ACTIVEMQ_ENABLED))).thenReturn(false);
//        prepareForInitialization();
//        listener.contextInitialized(evt);
//        verifyNoMoreInteractions(hqlistener);
//    }
//
//    @Test
//    public void contextDestroyed() {
//        // backup jdbc drivers before calling contextDestroyed method
//        Enumeration<Driver> drivers = DriverManager.getDrivers();
//
//        when(config.getBoolean(eq(ConfigProperties.ACTIVEMQ_ENABLED))).thenReturn(true);
//        prepareForInitialization();
//
//        // we actually have to call contextInitialized before we
//        // can call contextDestroyed, otherwise the listener's
//        // member variables will be null.
//        listener.contextInitialized(evt);
//
//        // what we really want to test.
//        listener.contextDestroyed(evt);
//
//        // make sure we only call it 5 times all from init code
//        verify(evt, atMost(5)).getServletContext();
//        verifyNoMoreInteractions(evt); // destroy shouldn't use it
//        verify(hqlistener).contextDestroyed(any(Injector.class));
//        verifyZeroInteractions(buspublisher);
//
//        // re-register drivers
//        registerDrivers(drivers);
//    }
//
//    @Test
//    public void ensureAMQPClosedProperly() {
//
//        // backup jdbc drivers before calling contextDestroyed method
//        Enumeration<Driver> drivers = DriverManager.getDrivers();
//
//        when(config.getBoolean(
//                eq(ConfigProperties.AMQP_INTEGRATION_ENABLED))).thenReturn(true);
//        when(config.getBoolean(eq(ConfigProperties.SUSPEND_MODE_ENABLED))).thenReturn(true);
//        prepareForInitialization();
//        // we actually have to call contextInitialized before we
//        // can call contextDestroyed, otherwise the listener's
//        // member variables will be null.
//        listener.contextInitialized(evt);
//
//        // test & verify
//        listener.contextDestroyed(evt);
//        verify(buspublisher).close();
//
//        // re-register drivers
//        registerDrivers(drivers);
//    }
//
//    @Test
//    public void tharSheBlows() {
//        listener = new CandlepinContextListener() {
//            protected List<Module> getModules(ServletContext context) {
//                return new LinkedList<>();
//            }
//
//            protected Configuration readConfiguration(ServletContext context)
//                throws ConfigurationException {
//
//                throw new ConfigurationException("the ship is sinking");
//            }
//        };
//        prepareForInitialization();
//        assertThrows(RuntimeException.class, () -> listener.contextInitialized(evt));
//    }
//
//    @Test
//    public void exitStageLeft() {
//        assertEquals(Stage.PRODUCTION, listener.getStage(ctx));
//    }
//
//    @Test
//    void initializesCrlFile() {
//        prepareForInitialization();
//        when(config.getString(ConfigProperties.CRL_FILE_PATH))
//            .thenReturn("/tmp/crlfile.crl");
//        File crlFile = new File(config.getString(ConfigProperties.CRL_FILE_PATH));
//
//        assertFalse(crlFile.exists());
//
//        listener.contextInitialized(evt);
//
//        assertTrue(crlFile.exists());
//    }
//
//    private void prepareForInitialization() {
//        evt = mock(ServletContextEvent.class);
//        ctx = mock(ServletContext.class);
//        resteasyDeployment = mock(ResteasyDeployment.class);
//        Registry registry = mock(Registry.class);
//        ResteasyProviderFactory rpfactory = mock(ResteasyProviderFactory.class);
//        when(evt.getServletContext()).thenReturn(ctx);
//        when(ctx.getAttribute(eq(ResteasyProviderFactory.class.getName()))).thenReturn(rpfactory);
//        when(ctx.getAttribute(eq(CandlepinContextListener.CONFIGURATION_NAME))).thenReturn(config);
//        when(ctx.getAttribute(eq(ResteasyDeployment.class.getName()))).thenReturn(resteasyDeployment);
//        when(resteasyDeployment.getRegistry()).thenReturn(registry);
//        when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
//    }
//
//    private void registerDrivers(Enumeration<Driver> drivers) {
//        while (drivers.hasMoreElements()) {
//            Driver driver = drivers.nextElement();
//            try {
//                DriverManager.registerDriver(driver);
//            }
//            catch (SQLException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    public class ContextListenerTestModule extends AbstractModule {
//        @SuppressWarnings("synthetic-access")
//        @Override
//        protected void configure() {
//            bind(ActiveMQContextListener.class).toInstance(hqlistener);
//            bind(AMQPBusPublisher.class).toInstance(buspublisher);
//            bind(ScheduledExecutorService.class).toInstance(executorService);
//            bind(QpidQmf.class).toInstance(qmf);
//        }
//    }
//
//    /**
//     * VerifyConfigRead fake interface to use with mockito's mock and verify
//     * methods to make sure the correct var was passed in and we called a
//     * method we expected.
//     */
//    interface VerifyConfigRead {
//        void verify(ServletContext ctx);
//    }
//}
