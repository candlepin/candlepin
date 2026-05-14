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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
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
import org.candlepin.junit.DatabaseTestExtension;
import org.candlepin.service.EventAdapter;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;

import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

@ResourceLock("CandlepinCapabilities")
public class CandlepinContextListenerTest {

    @RegisterExtension
    static DatabaseTestExtension ext = new DatabaseTestExtension();

    private DevConfig config;
    private CandlepinContextListener listener;
    private ActiveMQContextListener hqlistener;
    private ScheduledExecutorService executorService;
    private ServletContextEvent evt;
    private ServletContext ctx;
    private ResteasyDeployment resteasyDeployment;
    private boolean configRead;
    private EventAdapter mockEventAdapter;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();

        // TODO: This shouldn't be necessary for testing to complete. Fix this eventually.
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_THREADS, "0");

        hqlistener = mock(ActiveMQContextListener.class);
        executorService = mock(ScheduledExecutorService.class);
        mockEventAdapter = mock(EventAdapter.class);
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
        assertThat(configRead).isTrue();

        assertThat(CandlepinCapabilities.getCapabilities())
            .containsExactlyInAnyOrderElementsOf(new CandlepinCapabilities());
    }

    @Test
    public void hidesHiddenCapabilities() {
        Set<String> hiddenSet = Set.of("cores", "ram");
        this.config.setProperty(ConfigProperties.HIDDEN_CAPABILITIES, String.join(",", hiddenSet));

        prepareForInitialization();
        listener.contextInitialized(evt);

        CandlepinCapabilities expected = new CandlepinCapabilities();
        expected.removeAll(hiddenSet);

        assertThat(CandlepinCapabilities.getCapabilities())
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void keycloakCapabilityPresentWhenKeycloakEnabled() {
        this.config.setProperty(ConfigProperties.KEYCLOAK_AUTHENTICATION, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .contains(
                CandlepinCapabilities.KEYCLOAK_AUTH_CAPABILITY, CandlepinCapabilities.DEVICE_AUTH_CAPABILITY);
    }

    @Test
    void keycloakCapabilityAbsentWhenKeycloakDisabled() {
        this.config.setProperty(ConfigProperties.KEYCLOAK_AUTHENTICATION, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .doesNotContain(
                CandlepinCapabilities.KEYCLOAK_AUTH_CAPABILITY, CandlepinCapabilities.DEVICE_AUTH_CAPABILITY);
    }

    @Test
    void sslVerifyCapabilityPresentWhenSslVerifyEnabled() {
        this.config.setProperty(ConfigProperties.SSL_VERIFY, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .contains(CandlepinCapabilities.SSL_VERIFY_CAPABILITY);
    }

    @Test
    void sslVerifyCapabilityAbsentWhenSslVerifyDisabled() {
        this.config.setProperty(ConfigProperties.SSL_VERIFY, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .doesNotContain(CandlepinCapabilities.SSL_VERIFY_CAPABILITY);
    }

    @Test
    void cloudregCapabilityPresentWhenCloudRegistrationEnabled() {
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .contains(CandlepinCapabilities.CLOUD_REGISTRATION_CAPABILITY);
    }

    @Test
    void cloudregCapabilityAbsentWhenCloudRegistrationDisabled() {
        this.config.setProperty(ConfigProperties.CLOUD_AUTHENTICATION, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .doesNotContain(CandlepinCapabilities.CLOUD_REGISTRATION_CAPABILITY);
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
        List<Driver> drivers = backupDrivers();

        this.config.setProperty(ConfigProperties.ACTIVEMQ_ENABLED, "true");
        prepareForInitialization();

        listener.contextInitialized(evt);
        listener.contextDestroyed(evt);

        restoreDrivers(drivers);

        verify(evt, atMost(5)).getServletContext();
        verifyNoMoreInteractions(evt);
        verify(hqlistener).contextDestroyed(any(Injector.class));
    }

    @Test
    public void ensureAMQPClosedProperly() {
        List<Driver> drivers = backupDrivers();

        this.config.setProperty(ConfigProperties.SUSPEND_MODE_ENABLED, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);
        listener.contextDestroyed(evt);

        restoreDrivers(drivers);
    }

    @Test
    public void ensureEventAdapterIsInitialized() {
        prepareForInitialization();
        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null.
        listener.contextInitialized(evt);

        verify(mockEventAdapter).initialize();
    }

    @Test
    public void ensureEventAdapterIsShutdown() {
        List<Driver> drivers = backupDrivers();

        prepareForInitialization();
        listener.contextInitialized(evt);
        listener.contextDestroyed(evt);

        restoreDrivers(drivers);

        verify(mockEventAdapter).shutdown();
    }

    @Test
    public void exitStageLeft() {
        assertThat(listener.getStage(ctx))
            .isEqualTo(Stage.PRODUCTION);
    }

    @Test
    public void testCombinedReportingCapabilityPresentWhenEnabled() {
        this.config.setProperty(ConfigProperties.COMBINED_REPORTING_ENABLED, "true");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .contains(CandlepinCapabilities.COMBINED_REPORTING_CAPABILITY);
    }

    @Test
    public void testCombinedReportingCapabilityAbsentWhenDisabled() {
        this.config.setProperty(ConfigProperties.COMBINED_REPORTING_ENABLED, "false");

        prepareForInitialization();
        listener.contextInitialized(evt);

        assertThat(CandlepinCapabilities.getCapabilities())
            .doesNotContain(CandlepinCapabilities.COMBINED_REPORTING_CAPABILITY);
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

    private static List<Driver> backupDrivers() {
        List<Driver> drivers = new ArrayList<>();
        Enumeration<Driver> driverEnum = DriverManager.getDrivers();
        while (driverEnum.hasMoreElements()) {
            drivers.add(driverEnum.nextElement());
        }
        return drivers;
    }

    private static void restoreDrivers(List<Driver> drivers) {
        for (Driver driver : drivers) {
            try {
                DriverManager.registerDriver(driver);
            }
            catch (SQLException e) {
                throw new RuntimeException("Failed to re-register driver: " + driver, e);
            }
        }
    }

    private CandlepinContextListener createContextListener() {
        return new CandlepinContextListener() {
            @Override
            protected List<Module> getModules(ServletContext context) {
                List<Module> modules = new LinkedList<>();
                modules.add(DatabaseTestExtension.createJpaModule(ext.getJdbcUrl()));

                Module testingModule = new TestingModules.StandardTest(config);
                Module contextListenerTestModule = new ContextListenerTestModule();

                // Combine both the standard testing module and the context listener test module and override
                // existing already existing bindings in the testing module.
                modules.add(Modules.override(testingModule).with(contextListenerTestModule));

                return modules;
            }

            @Override
            protected Configuration readConfiguration() {
                configRead = true;
                return config;
            }

            @Override
            protected void initializeDatabase() {
                /* intentionally left empty */
            }

            @Override
            protected void initializeTranslations() {
                /* intentionally left empty */
            }
        };
    }

    private class ContextListenerTestModule extends AbstractModule {

        @SuppressWarnings("synthetic-access")
        @Override
        protected void configure() {
            bind(ActiveMQContextListener.class).toInstance(hqlistener);
            bind(ScheduledExecutorService.class).toInstance(executorService);
            bind(EventAdapter.class).toInstance(mockEventAdapter);
        }
    }

}
