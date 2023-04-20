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
package org.candlepin.guice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.TestingModules;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.junit.LiquibaseExtension;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;

import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;


@ExtendWith(LiquibaseExtension.class)
public class CandlepinContextListenerTest {
    private Configuration config;
    private CandlepinContextListener listener;
    private ActiveMQContextListener hqlistener;
    private ScheduledExecutorService executorService;
    private ServletContextEvent evt;
    private ServletContext ctx;
    private ResteasyDeployment resteasyDeployment;
    private VerifyConfigRead configRead;

    @BeforeEach
    public void init() {
        this.config = new CandlepinCommonTestConfig();

        // TODO: This shouldn't be necessary for testing to complete. Fix this eventually.
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_THREADS, "0");

        hqlistener = mock(ActiveMQContextListener.class);
        executorService = mock(ScheduledExecutorService.class);
        configRead = mock(VerifyConfigRead.class);

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
            protected Configuration readConfiguration(ServletContext context) {

                configRead.verify(context);
                return config;
            }

            @Override
            protected void checkDbChangelog() { }
        };
    }

    @Test
    public void contextInitialized() {
        this.config.setProperty(ConfigProperties.ACTIVEMQ_ENABLED, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);
        verify(hqlistener).contextInitialized(any(Injector.class));
        verify(ctx).setAttribute(eq(CandlepinContextListener.CONFIGURATION_NAME), eq(config));
        verify(configRead).verify(eq(ctx));

        CandlepinCapabilities expected = new CandlepinCapabilities();
        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertEquals(expected, actual);
    }

    @Test
    public void hidesHiddenCapabilities() {
        Set<String> hiddenSet = Set.of("cores", "ram");
        this.config.setProperty(ConfigProperties.HIDDEN_CAPABILITIES, String.join(",", hiddenSet));

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities expected = new CandlepinCapabilities();
        expected.removeAll(hiddenSet);

        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertEquals(expected, actual);
    }

    @Test
    void keycloakCapabilityPresentWhenKeycloakEnabled() {
        this.config.setProperty(ConfigProperties.KEYCLOAK_AUTHENTICATION, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities expected = new CandlepinCapabilities();
        expected.add(CandlepinCapabilities.KEYCLOAK_AUTH_CAPABILITY);
        expected.add(CandlepinCapabilities.DEVICE_AUTH_CAPABILITY);

        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertEquals(expected, actual);
    }

    @Test
    void keycloakCapabilityAbsentWhenKeycloakDisabled() {
        this.config.setProperty(ConfigProperties.KEYCLOAK_AUTHENTICATION, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertFalse(actual.contains(CandlepinCapabilities.KEYCLOAK_AUTH_CAPABILITY),
            "keycloak_auth present but not expected");

        assertFalse(actual.contains(CandlepinCapabilities.DEVICE_AUTH_CAPABILITY),
            "device_auth present but not expected");
    }

    @Test
    void sslVerifyCapabilityPresentWhenSslVerifyEnabled() {
        this.config.setProperty(ConfigProperties.SSL_VERIFY, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities expected = new CandlepinCapabilities();
        expected.add(CandlepinCapabilities.SSL_VERIFY_CAPABILITY);

        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertEquals(expected, actual);
    }

    @Test
    void sslVerifyCapabilityAbsentWhenSslVerifyDisabled() {
        this.config.setProperty(ConfigProperties.SSL_VERIFY, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertFalse(actual.contains(CandlepinCapabilities.SSL_VERIFY_CAPABILITY),
            "ssl_verify_status present but not expected");
    }


    @Test
    void cloudregCapabilityPresentWhenCloudRegistrationEnabled() {
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities expected = new CandlepinCapabilities();
        expected.add(CandlepinCapabilities.CLOUD_REGISTRATION_CAPABILITY);

        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertEquals(expected, actual);
    }

    @Test
    void cloudregCapabilityAbsentWhenCloudRegistrationDisabled() {
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities actual = CandlepinCapabilities.getCapabilities();

        assertFalse(actual.contains(CandlepinCapabilities.CLOUD_REGISTRATION_CAPABILITY),
            "keycloak_auth present but not expected");
    }

    @Test
    public void activeMQDisabled() {
        this.config.setProperty(ConfigProperties.ACTIVEMQ_ENABLED, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        verifyNoMoreInteractions(hqlistener);
    }

    @Test
    public void contextDestroyed() {
        // backup jdbc drivers before calling contextDestroyed method
        Enumeration<Driver> drivers = DriverManager.getDrivers();

        this.config.setProperty(ConfigProperties.ACTIVEMQ_ENABLED, "true");
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
        verify(hqlistener).contextDestroyed(any(Injector.class));

        // re-register drivers
        registerDrivers(drivers);
    }

    @Test
    public void ensureAMQPClosedProperly() {
        // backup jdbc drivers before calling contextDestroyed method
        Enumeration<Driver> drivers = DriverManager.getDrivers();

        this.config.setProperty(ConfigProperties.SUSPEND_MODE_ENABLED, "true");

        prepareForInitialization();
        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null.
        listener.contextInitialized(evt);

        // test & verify
        listener.contextDestroyed(evt);

        // re-register drivers
        registerDrivers(drivers);
    }

    @Test
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
        assertThrows(RuntimeException.class, () -> listener.contextInitialized(evt));
    }

    @Test
    public void exitStageLeft() {
        assertEquals(Stage.PRODUCTION, listener.getStage(ctx));
    }

    private void prepareForInitialization() {
        evt = mock(ServletContextEvent.class);
        ctx = mock(ServletContext.class);
        resteasyDeployment = mock(ResteasyDeployment.class);
        Registry registry = mock(Registry.class);
        ResteasyProviderFactory rpfactory = mock(ResteasyProviderFactory.class);
        when(evt.getServletContext()).thenReturn(ctx);
        when(ctx.getAttribute(eq(ResteasyProviderFactory.class.getName()))).thenReturn(rpfactory);
        when(ctx.getAttribute(eq(CandlepinContextListener.CONFIGURATION_NAME))).thenReturn(config);
        when(ctx.getAttribute(eq(ResteasyDeployment.class.getName()))).thenReturn(resteasyDeployment);
        when(resteasyDeployment.getRegistry()).thenReturn(registry);
    }

    @Test
    public void hasAllChangesets() throws Exception {
        // needs a listener that allows checkDbChangelog()
        listener = new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
                modules.add(new TestingModules.JpaModule());
                modules.add(new TestingModules.StandardTest(config));
                modules.add(new ContextListenerTestModule());
                return modules;
            }

            @Override
            protected Configuration readConfiguration(ServletContext context) {
                configRead.verify(context);
                return config;
            }
        };

        LiquibaseExtension le = new LiquibaseExtension();
        le.createLiquibaseSchema();
        le.runUpdate();
        Liquibase l = le.getLiquibase();
        CandlepinContextListener spy = Mockito.spy(listener);
        doReturn(l).when(spy).getLiquibase();

        //  no RuntimeException because the db is updated
        prepareForInitialization();
        spy.contextInitialized(evt);
    }

    @Test
    public void hasMissingChangesetsNone() throws Exception {
        // needs a listener that allows checkDbChangelog()
        listener = new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
                modules.add(new TestingModules.JpaModule());
                modules.add(new TestingModules.StandardTest(config));
                modules.add(new ContextListenerTestModule());
                return modules;
            }

            @Override
            protected Configuration readConfiguration(ServletContext context) {
                configRead.verify(context);
                return config;
            }
        };

        LiquibaseExtension le = new LiquibaseExtension();
        le.createLiquibaseSchema();
        le.runUpdate();
        Liquibase l = le.getLiquibase();
        ChangeSet cs = new ChangeSet("21220101",
            "tester",
            true,
            true,
            "db/changelog/21220101-test.xml",
            null,
            null,
            l.getDatabaseChangeLog());
        l.getDatabaseChangeLog().addChangeSet(cs);
        CandlepinContextListener spy = Mockito.spy(listener);
        doReturn(l).when(spy).getLiquibase();

        // no Runtime exception because of config override
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManageLevel.NONE.getName());
        prepareForInitialization();
        spy.contextInitialized(evt);
    }

    @Test
    public void hasMissingChangesetsReport() throws Exception {
        // needs a listener that allows checkDbChangelog()
        listener = new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
                modules.add(new TestingModules.JpaModule());
                modules.add(new TestingModules.StandardTest(config));
                modules.add(new ContextListenerTestModule());
                return modules;
            }

            @Override
            protected Configuration readConfiguration(ServletContext context) {
                configRead.verify(context);
                return config;
            }
        };

        LiquibaseExtension le = new LiquibaseExtension();
        le.createLiquibaseSchema();
        le.runUpdate();
        Liquibase l = le.getLiquibase();
        ChangeSet cs = new ChangeSet("21220101",
            "tester",
            true,
            true,
            "db/changelog/21220101-test.xml",
            null,
            null,
            l.getDatabaseChangeLog());
        l.getDatabaseChangeLog().addChangeSet(cs);
        CandlepinContextListener spy = Mockito.spy(listener);
        doReturn(l).when(spy).getLiquibase();

        // no Runtime exception because of config override
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManageLevel.REPORT.getName());
        prepareForInitialization();
        spy.contextInitialized(evt);
    }

    @Test
    public void hasMissingChangesetsHalt() throws Exception {
        // needs a listener that allows checkDbChangelog()
        listener = new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
                modules.add(new TestingModules.JpaModule());
                modules.add(new TestingModules.StandardTest(config));
                modules.add(new ContextListenerTestModule());
                return modules;
            }

            @Override
            protected Configuration readConfiguration(ServletContext context) {
                configRead.verify(context);
                return config;
            }
        };

        LiquibaseExtension le = new LiquibaseExtension();
        le.createLiquibaseSchema();
        le.runUpdate();
        Liquibase l = le.getLiquibase();
        ChangeSet cs = new ChangeSet("21220101",
            "tester",
            true,
            true,
            "db/changelog/21220101-test.xml",
            null,
            null,
            l.getDatabaseChangeLog());
        l.getDatabaseChangeLog().addChangeSet(cs);
        CandlepinContextListener spy = Mockito.spy(listener);
        doReturn(l).when(spy).getLiquibase();

        // Runtime exception for changeset that is not in db
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManageLevel.HALT.getName());
        prepareForInitialization();
        RuntimeException re = assertThrows(RuntimeException.class, () -> spy.contextInitialized(evt));
        assertEquals("The database is missing Liquibase changeset(s)", re.getMessage());
    }

    @Test
    public void hasMissingChangesetManage() throws Exception {
        // needs a listener that allows checkDbChangelog()
        listener = new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
                modules.add(new TestingModules.JpaModule());
                modules.add(new TestingModules.StandardTest(config));
                modules.add(new ContextListenerTestModule());
                return modules;
            }

            @Override
            protected Configuration readConfiguration(ServletContext context) {
                configRead.verify(context);
                return config;
            }
        };

        LiquibaseExtension le = new LiquibaseExtension();
        le.createLiquibaseSchema();
        le.runUpdate();
        Liquibase l = le.getLiquibase();
        ChangeSet cs = new ChangeSet("21220101",
            "tester",
            true,
            true,
            "db/changelog/21220101-test.xml",
            null,
            null,
            l.getDatabaseChangeLog());
        l.getDatabaseChangeLog().addChangeSet(cs);
        CandlepinContextListener spy = Mockito.spy(listener);
        doReturn(l).when(spy).getLiquibase();

        // no Runtime exception because update is enabled
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManageLevel.MANAGE.getName());
        prepareForInitialization();
        spy.contextInitialized(evt);

        assertEquals(1, l.getDatabaseChangeLog()
            .getChangeSets("db/changelog/21220101-test.xml", "tester", "21220101").size());
    }

    private void registerDrivers(Enumeration<Driver> drivers) {
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            try {
                DriverManager.registerDriver(driver);
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public class ContextListenerTestModule extends AbstractModule {
        @SuppressWarnings("synthetic-access")
        @Override
        protected void configure() {
            bind(ActiveMQContextListener.class).toInstance(hqlistener);
            bind(ScheduledExecutorService.class).toInstance(executorService);
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
