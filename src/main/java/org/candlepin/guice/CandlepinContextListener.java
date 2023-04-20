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

import static org.candlepin.config.ConfigProperties.ACTIVEMQ_ENABLED;
import static org.candlepin.config.ConfigProperties.DB_MANAGE_ON_START;
import static org.candlepin.config.ConfigProperties.ENCRYPTED_PROPERTIES;
import static org.candlepin.config.ConfigProperties.PASSPHRASE_SECRET_FILE;

import org.candlepin.async.JobManager;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.config.EncryptedConfiguration;
import org.candlepin.config.MapConfiguration;
import org.candlepin.logging.LoggerContextListener;
import org.candlepin.logging.LoggingConfigurator;
import org.candlepin.messaging.CPMContextListener;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.resteasy.ResourceLocatorMap;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.persist.PersistService;
import com.google.inject.util.Modules;

import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.dialect.PostgreSQL92Dialect;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18nManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.cache.CacheManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;


/**
 * Customized Candlepin version of
 * {@link GuiceResteasyBootstrapServletContextListener}.
 *
 * The base version pulls in Guice modules by class name from web.xml and
 * instantiates them - however we have a need to add in modules
 * programmatically for, e.g., servlet filters and the wideplay JPA module.
 * This context listener overrides some of the module initialization code to
 * allow for module specification beyond simply listing class names.
 */
public class CandlepinContextListener extends GuiceResteasyBootstrapServletContextListener {
    public static final String CONFIGURATION_NAME = Configuration.class.getName();

    /**
     * The (rough) state of this listener's lifecycle, not including transitional states.
     */
    public static enum ListenerState {
        /** Listener state after instantiation but before a successful call to contextInitialized */
        UNINITIALIZED,

        /**
         * Listener state after a successful call to contextInitialized, but before a successful call to
         * contextDestroyed
         */
        INITIALIZED,

        /** Listener state after a successful call to contextDestroyed */
        DESTROYED
    }

    private ListenerState state = ListenerState.UNINITIALIZED;

    private CPMContextListener cpmContextListener;

    private ActiveMQContextListener activeMQContextListener;
    private JobManager jobManager;
    private LoggerContextListener loggerListener;

    // a bit of application-initialization code. Not sure if this is the
    // best spot for it.
    static {
        I18nManager.getInstance().setDefaultLocale(Locale.US);
    }

    private static Logger log = LoggerFactory.getLogger(CandlepinContextListener.class);
    private Configuration config;

    // getServletContext() from the GuiceServletContextListener is deprecated.
    // See
    // https://github.com/google/guice/blob/bf0e7ce902dd97e62ef16679c587d78d59200450
    // /extensions/servlet/src/com/google/inject/servlet/GuiceServletContextListener.java#L43-L45
    // A typical way of doing this then is to cache the context ourselves:
    // https://github.com/google/guice/issues/603
    // Currently only needed for access to the Configuration.
    private ServletContext servletContext;

    private Injector injector;

    @Override
    public synchronized void contextInitialized(ServletContextEvent sce) {
        if (this.state != ListenerState.UNINITIALIZED) {
            throw new IllegalStateException("context listener already initialized");
        }

        log.info("Candlepin initializing context.");

        I18nManager.getInstance().setDefaultLocale(Locale.US);
        servletContext = sce.getServletContext();

        try {
            log.info("Candlepin reading configuration.");
            config = readConfiguration(servletContext);
        }
        catch (ConfigurationException e) {
            log.error("Could not read configuration file.  Aborting initialization.", e);
            throw new RuntimeException(e);
        }

        LoggingConfigurator.init(config);

        servletContext.setAttribute(CONFIGURATION_NAME, config);
        setCapabilities(config);
        log.debug("Candlepin stored config on context.");

        // check state of database against liquibase changelogs
        checkDbChangelog();

        // set things up BEFORE calling the super class' initialize method.
        super.contextInitialized(sce);

        this.state = ListenerState.INITIALIZED;
        log.info("Candlepin context initialized.");
    }

    @Override
    public void withInjector(Injector injector) {
        try {
            this.initializeSubsystems(injector);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeSubsystems(Injector injector) throws Exception {
        // Must call super.contextInitialized() before accessing injector
        insertValidationEventListeners(injector);

        MethodLocator methodLocator = injector.getInstance(MethodLocator.class);
        methodLocator.init();

        ResourceLocatorMap map = injector.getInstance(ResourceLocatorMap.class);
        map.init();

        // make sure our session factory is initialized before we attempt to start something
        // that relies upon it
        this.cpmContextListener = injector.getInstance(CPMContextListener.class);
        this.cpmContextListener.initialize(injector);

        if (config.getBoolean(ACTIVEMQ_ENABLED)) {
            // If Artemis can not be started candlepin will not start.
            activeMQContextListener = injector.getInstance(ActiveMQContextListener.class);
            activeMQContextListener.contextInitialized(injector);
        }

        if (config.getBoolean(ConfigProperties.CACHE_JMX_STATS)) {
            CacheManager cacheManager = injector.getInstance(CacheManager.class);
            cacheManager.getCacheNames().forEach(cacheName -> {
                log.info("Enabling management and statistics for {} cache", cacheName);
                cacheManager.enableManagement(cacheName, true);
                cacheManager.enableStatistics(cacheName, true);
            });
        }

        // Setup the job manager
        this.jobManager = injector.getInstance(JobManager.class);
        this.jobManager.initialize();
        this.jobManager.start();

        loggerListener = injector.getInstance(LoggerContextListener.class);

        this.injector = injector;
    }

    @Override
    public synchronized void contextDestroyed(ServletContextEvent event) {
        if (this.state != ListenerState.INITIALIZED) {
            // Silently ignore the event if we never completed initialization
            return;
        }

        // TODO: Add transitional states and checks if we still have issues with completing
        // an initialization or shutdown op.

        try {
            this.destroySubsystems();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        super.contextDestroyed(event);
        this.state = ListenerState.DESTROYED;
    }

    private void destroySubsystems() throws Exception {
        // Perform graceful shutdown operations before the job system's final destruction
        this.cpmContextListener.shutdown();

        // Tear down the job system
        this.jobManager.shutdown();

        injector.getInstance(PersistService.class).stop();
        // deregister jdbc driver to avoid warning in tomcat shutdown log
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            try {
                DriverManager.deregisterDriver(driver);
            }
            catch (SQLException e) {
                log.info("Failed to de-registering driver {}", driver, e);
            }
        }

        if (config.getBoolean(ACTIVEMQ_ENABLED)) {
            activeMQContextListener.contextDestroyed(injector);
        }

        // Make sure this is called after everything else, as other objects may rely on the
        // messaging subsystem
        this.cpmContextListener.destroy();

        this.loggerListener.contextDestroyed();
    }

    protected void setCapabilities(Configuration config) {
        CandlepinCapabilities capabilities = new CandlepinCapabilities();

        // Update our capabilities with configurable features
        if (config.getBoolean(ConfigProperties.KEYCLOAK_AUTHENTICATION, false)) {
            capabilities.add(CandlepinCapabilities.KEYCLOAK_AUTH_CAPABILITY);
            capabilities.add(CandlepinCapabilities.DEVICE_AUTH_CAPABILITY);
        }

        if (config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION, false)) {
            capabilities.add(CandlepinCapabilities.CLOUD_REGISTRATION_CAPABILITY);
        }

        if (config.getBoolean(ConfigProperties.SSL_VERIFY, false)) {
            capabilities.add(CandlepinCapabilities.SSL_VERIFY_CAPABILITY);
        }

        // Remove hidden capabilities
        Set<String> hidden = config.getSet(ConfigProperties.HIDDEN_CAPABILITIES, null);
        if (hidden != null) {
            capabilities.removeAll(hidden);
        }

        log.info("Candlepin will show support for the following capabilities: {}", capabilities);
        CandlepinCapabilities.setCapabilities(capabilities);
    }

    protected Configuration readConfiguration(ServletContext context) throws ConfigurationException {
        File configFile = new File(ConfigProperties.DEFAULT_CONFIG_FILE);

        EncryptedConfiguration systemConfig = new EncryptedConfiguration();

        if (configFile.canRead()) {
            log.debug("Loading system configuration");

            // First, read the system configuration
            systemConfig.load(configFile, StandardCharsets.UTF_8);
            log.debug("System configuration: {}", systemConfig);
        }

        systemConfig.use(PASSPHRASE_SECRET_FILE).toDecrypt(ENCRYPTED_PROPERTIES);

        // load the defaults
        MapConfiguration defaults = new MapConfiguration(ConfigProperties.DEFAULT_PROPERTIES);

        // Default to Postgresql if jpa.config.hibernate.dialect is unset
        DatabaseConfigFactory.SupportedDatabase db = determinDatabaseConfiguration(
            systemConfig.getString("jpa.config.hibernate.dialect", PostgreSQL92Dialect.class.getName()));
        log.info("Running under {}", db.getLabel());
        Configuration databaseConfig = DatabaseConfigFactory.fetchConfig(db);

        // merge the defaults with the system configuration. PARAMETER ORDER MATTERS.
        // systemConfig must be FIRST to override subsequent configs.  This set up allows
        // the systemConfig to override databaseConfig if necessary, but that's not really something
        // users should be doing unbidden so it is undocumented.
        return EncryptedConfiguration.merge(systemConfig, databaseConfig, defaults);
    }

    private DatabaseConfigFactory.SupportedDatabase determinDatabaseConfiguration(String dialect) {
        if (StringUtils.containsIgnoreCase(
            dialect, DatabaseConfigFactory.SupportedDatabase.MYSQL.getLabel())) {
            return DatabaseConfigFactory.SupportedDatabase.MYSQL;
        }
        if (StringUtils
            .containsIgnoreCase(dialect, DatabaseConfigFactory.SupportedDatabase.MARIADB.getLabel())) {
            return DatabaseConfigFactory.SupportedDatabase.MARIADB;
        }
        return DatabaseConfigFactory.SupportedDatabase.POSTGRESQL;
    }

    @Override
    protected Stage getStage(ServletContext context) {
        return Stage.PRODUCTION;
    }

    /**
     * Returns a list of Guice modules to initialize.
     * @return a list of Guice modules to initialize.
     */
    @Override
    protected List<Module> getModules(ServletContext context) {
        List<Module> modules = new LinkedList<>();

        modules.add(Modules.override(new DefaultConfig()).with(new CustomizableModules().load(config)));

        modules.add(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Configuration.class).toInstance(config);
            }
        });

        modules.add(new CandlepinModule(config));
        modules.add(new CandlepinFilterModule(config));

        return modules;
    }

    /**
     * There's no way to really get Guice to perform injections on stuff that
     * the JpaPersistModule is creating, so we resort to grabbing the EntityManagerFactory
     * after the fact and adding the Validation EventListener ourselves.
     * @param injector
     */
    private void insertValidationEventListeners(Injector injector) {
        javax.inject.Provider<EntityManagerFactory> emfProvider =
            injector.getProvider(EntityManagerFactory.class);
        HibernateEntityManagerFactory hibernateEntityManagerFactory =
            (HibernateEntityManagerFactory) emfProvider.get();
        SessionFactoryImpl sessionFactoryImpl =
            (SessionFactoryImpl) hibernateEntityManagerFactory.getSessionFactory();
        EventListenerRegistry registry =
            sessionFactoryImpl.getServiceRegistry().getService(EventListenerRegistry.class);

        javax.inject.Provider<BeanValidationEventListener> listenerProvider =
            injector.getProvider(BeanValidationEventListener.class);
        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listenerProvider.get());
    }

    public enum DBManageLevel {
        NONE("none"),
        REPORT("report"),
        HALT("halt"),
        MANAGE("manage");

        private String name;

        DBManageLevel(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    /**
     * Check the state of the database in regards to the application of changesets via Liquibase.
     *
     * The number of changesets not applied will be logged at error level.
     *
     * @throws RuntimeException if there are missing changesets or a LiqubaseException
     */
    protected void checkDbChangelog() {
        log.info(String.format("Liquibase startup management set to %s",
            config.getString(DB_MANAGE_ON_START)));
        String liquibaseOnStart = config.getString(DB_MANAGE_ON_START).toLowerCase();

        if (liquibaseOnStart == null || liquibaseOnStart.equals(DBManageLevel.NONE.getName())) {
            return;
        }
        Liquibase liquibase = null;
        try {
            liquibase = getLiquibase();
            List<ChangeSet> unrunChangesets = liquibase.listUnrunChangeSets(null, null);

            if (!unrunChangesets.isEmpty()) {
                StringBuilder builder = new StringBuilder()
                    .append("The database is missing ")
                    .append(unrunChangesets.size())
                    .append(" Liquibase changeset(s):")
                    .append('\n');

                unrunChangesets.forEach(changeset -> {
                    builder.append("  ")
                        .append(changeset.getId())
                        .append(", filename: ")
                        .append(changeset.getFilePath())
                        .append('\n');
                });
                // all levels above None need this
                log.warn(builder.toString());

                if (liquibaseOnStart.equals(DBManageLevel.REPORT.getName())) {
                    log.warn("Please update the database");
                }
                else if (liquibaseOnStart.equals(DBManageLevel.HALT.getName())) {
                    log.error("Aborting Candlepin initialization...");
                    throw new RuntimeException("The database is missing Liquibase changeset(s)");
                }
                else if (liquibaseOnStart.equals(DBManageLevel.MANAGE.getName())) {
                    log.warn("Calling liquibase to update the database");
                    liquibase.update();
                    log.warn("Update complete");
                }
                else {
                    log.error("The value for 'candlepin.db.database_manage_on_startup' is invalid. " +
                        "No action taken.");
                }
            }
        }
        catch (LiquibaseException e) {
            log.error("Liquibase exception: " +  e.getMessage());
            throw new RuntimeException(e);
        }
        finally {
            if (liquibase != null && liquibase.getDatabase() != null) {
                DatabaseConnection dc = liquibase.getDatabase().getConnection();
                if (dc != null) {
                    try {
                        dc.rollback();
                        dc.close();
                    }
                    catch (DatabaseException de) {
                        // just go on
                    }
                }
            }
        }
    }

    /**
     * Initializes the connection to the database and a Liquibase object for detection of the state
     * of the database compared to the liquibase changeset list.
     *
     * @return Liquibase initialized for the changeset comparison
     * @throws RuntimeException if there is an issue with establishing the Liquibase object
     */
    protected Liquibase getLiquibase() {
        Liquibase liquibase = null;
        try {
            // this ensures that the driver class has been loaded
            Class.forName(config.getString("jpa.config.hibernate.connection.driver_class"))
                .getDeclaredConstructor().newInstance();
            Connection connection = DriverManager.getConnection(
                config.getString("jpa.config.hibernate.connection.url"),
                config.getString("jpa.config.hibernate.connection.username"),
                config.getString("jpa.config.hibernate.connection.password"));
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database =
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            ResourceAccessor accessor = new ClassLoaderResourceAccessor();
            liquibase = new Liquibase("db/changelog/changelog-create.xml",
                accessor, database);
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        catch (LiquibaseException e) {
            throw new RuntimeException(e);
        }
        return liquibase;
    }
}
