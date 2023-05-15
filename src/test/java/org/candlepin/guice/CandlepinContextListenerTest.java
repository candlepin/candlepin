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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.TestingModules;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.junit.LiquibaseExtension;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.command.core.StatusCommandStep;
import liquibase.database.Database;

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
    private DevConfig config;
    private CandlepinContextListener listener;
    private ActiveMQContextListener hqlistener;
    private ScheduledExecutorService executorService;
    private ServletContextEvent evt;
    private ServletContext ctx;
    private ResteasyDeployment resteasyDeployment;
    private boolean configRead;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();

        // TODO: This shouldn't be necessary for testing to complete. Fix this eventually.
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_THREADS, "0");

        hqlistener = mock(ActiveMQContextListener.class);
        executorService = mock(ScheduledExecutorService.class);
        configRead = false;

        listener = createContextListener();
    }

    @Test
    public void contextInitialized() {
        this.config.setProperty(ConfigProperties.ACTIVEMQ_ENABLED, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);
        verify(hqlistener).contextInitialized(any(Injector.class));
        verify(ctx).setAttribute(CandlepinContextListener.CONFIGURATION_NAME, config);
        assertTrue(configRead);

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
        when(ctx.getAttribute(ResteasyProviderFactory.class.getName())).thenReturn(rpfactory);
        when(ctx.getAttribute(CandlepinContextListener.CONFIGURATION_NAME)).thenReturn(config);
        when(ctx.getAttribute(ResteasyDeployment.class.getName())).thenReturn(resteasyDeployment);
        when(resteasyDeployment.getRegistry()).thenReturn(registry);
    }

    @Test
    public void hasAllChangesets() throws Exception {
        StatusCommandStep statusCommandStep = setupDbWithUnrunSet();
        CandlepinContextListener spy = Mockito.spy(listener);
        Database database = Mockito.mock(Database.class);
        doReturn(List.of()).when(spy).getUnrunChangeSets(database);
        doReturn(database).when(spy).getDatabase();

        //  no RuntimeException because the db is up-to-date
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManagementLevel.HALT.getName());
        prepareForInitialization();
        spy.contextInitialized(evt);

        Mockito.verify(spy, Mockito.times(1)).getUnrunChangeSets(any());
        Mockito.verify(spy, Mockito.never()).executeUpdate(any());
    }

    @Test
    public void hasMissingChangesetConfigNone() throws Exception {
        StatusCommandStep statusCommandStep = setupDbWithUnrunSet();
        CandlepinContextListener spy = Mockito.spy(listener);
        Database database = Mockito.mock(Database.class);
        doReturn(statusCommandStep.listUnrunChangeSets(null, null, null, null))
            .when(spy).getUnrunChangeSets(database);
        doReturn(database).when(spy).getDatabase();

        // does not lookup unchanged sets nor call update
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManagementLevel.NONE.getName());
        prepareForInitialization();
        spy.contextInitialized(evt);

        Mockito.verify(spy, Mockito.never()).getUnrunChangeSets(database);
        Mockito.verify(spy, Mockito.never()).executeUpdate(any());
    }

    @Test
    public void hasMissingChangesetConfigReport() throws Exception {
        StatusCommandStep statusCommandStep = setupDbWithUnrunSet();
        CandlepinContextListener spy = Mockito.spy(listener);
        Database database = Mockito.mock(Database.class);
        doReturn(statusCommandStep.listUnrunChangeSets(null, null, null, null))
            .when(spy).getUnrunChangeSets(database);
        doReturn(database).when(spy).getDatabase();

        // looks up changesets but does not call update
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManagementLevel.REPORT.getName());
        prepareForInitialization();
        spy.contextInitialized(evt);

        Mockito.verify(statusCommandStep, Mockito.times(1)).listUnrunChangeSets(any(), any(), any(), any());
        Mockito.verify(spy, Mockito.never()).executeUpdate(any());
    }

    @Test
    public void hasMissingChangesetConfigHalt() throws Exception {
        StatusCommandStep statusCommandStep = setupDbWithUnrunSet();
        CandlepinContextListener spy = Mockito.spy(listener);
        Database database = Mockito.mock(Database.class);
        doReturn(statusCommandStep.listUnrunChangeSets(null, null, null, null))
            .when(spy).getUnrunChangeSets(database);
        doReturn(database).when(spy).getDatabase();

        // looks up unchanged sets and throws runtime exception
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManagementLevel.HALT.getName());
        prepareForInitialization();
        RuntimeException re = assertThrows(RuntimeException.class, () -> spy.contextInitialized(evt));
        Mockito.verify(statusCommandStep, Mockito.times(1)).listUnrunChangeSets(any(), any(), any(), any());
        assertEquals("The database is missing Liquibase changeset(s)", re.getMessage());
    }

    @Test
    public void hasMissingChangesetConfigManage() throws Exception {
        StatusCommandStep statusCommandStep = setupDbWithUnrunSet();
        CandlepinContextListener spy = Mockito.spy(listener);
        Database database = Mockito.mock(Database.class);
        doReturn(statusCommandStep.listUnrunChangeSets(null, null, null, null))
            .when(spy).getUnrunChangeSets(database);
        doReturn(database).when(spy).getDatabase();
        Mockito.doNothing().when(spy).executeUpdate(database);

        // looks up unchanged sets and calls update
        this.config.setProperty(ConfigProperties.DB_MANAGE_ON_START,
            CandlepinContextListener.DBManagementLevel.MANAGE.getName());
        prepareForInitialization();
        spy.contextInitialized(evt);

        Mockito.verify(statusCommandStep, Mockito.times(1)).listUnrunChangeSets(any(), any(), any(), any());
        Mockito.verify(spy, Mockito.times(1)).executeUpdate(database);
    }

    private StatusCommandStep setupDbWithUnrunSet() throws Exception {
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
            protected Configuration readConfiguration() {
                configRead = false;
                return config;
            }
        };

        StatusCommandStep statusCommandStep = Mockito.mock(StatusCommandStep.class);
        DatabaseChangeLog databaseChangeLog = Mockito.mock(DatabaseChangeLog.class);

        ChangeSet cs = new ChangeSet("21220101",
            "tester",
            true,
            true,
            "db/changelog/21220101-test.xml",
            null,
            null,
            databaseChangeLog);
        doReturn(List.of(cs)).when(statusCommandStep).listUnrunChangeSets(any(), any(), any(), any());
        return statusCommandStep;
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

    private CandlepinContextListener createContextListener() {
        return new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
                modules.add(new TestingModules.JpaModule());
                modules.add(new TestingModules.StandardTest(config));
                modules.add(new ContextListenerTestModule());
                return modules;
            }

            @Override
            protected Configuration readConfiguration() {
                configRead = true;
                return config;
            }
        };
    }

    private class ContextListenerTestModule extends AbstractModule {
        @SuppressWarnings("synthetic-access")
        @Override
        protected void configure() {
            bind(ActiveMQContextListener.class).toInstance(hqlistener);
            bind(ScheduledExecutorService.class).toInstance(executorService);
        }
    }

}
