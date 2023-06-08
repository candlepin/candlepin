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

import org.candlepin.async.JobManager;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.LegacyEncryptedInterceptor;
import org.candlepin.config.RyeConfig;
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

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.command.CommandScope;
import liquibase.command.core.StatusCommandStep;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18nManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final String CHANGELOG_FILE_NAME = "db/changelog/changelog-update.xml";
    private static final Path DEFAULT_CONFIG_FILE = Paths.get("/etc/candlepin/candlepin.conf");
    private static final Path DEFAULT_CONFIG_DIR = Paths.get("/etc/candlepin/conf.d");

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

        log.info("Candlepin reading configuration.");
        config = readConfiguration();

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
        if (config.getBoolean(ConfigProperties.KEYCLOAK_AUTHENTICATION)) {
            capabilities.add(CandlepinCapabilities.KEYCLOAK_AUTH_CAPABILITY);
            capabilities.add(CandlepinCapabilities.DEVICE_AUTH_CAPABILITY);
        }

        if (config.getBoolean(ConfigProperties.CLOUD_AUTHENTICATION)) {
            capabilities.add(CandlepinCapabilities.CLOUD_REGISTRATION_CAPABILITY);
        }

        if (config.getBoolean(ConfigProperties.SSL_VERIFY)) {
            capabilities.add(CandlepinCapabilities.SSL_VERIFY_CAPABILITY);
        }

        // Remove hidden capabilities
        Set<String> hidden = config.getSet(ConfigProperties.HIDDEN_CAPABILITIES);
        capabilities.removeAll(hidden);

        log.info("Candlepin will show support for the following capabilities: {}", capabilities);
        CandlepinCapabilities.setCapabilities(capabilities);
    }

    protected Configuration readConfiguration() {
        SmallRyeConfigBuilder configBuilder = new SmallRyeConfigBuilder()
            .addDefaultSources()
            .addDefaultInterceptors()
            .withInterceptors(new LegacyEncryptedInterceptor(ENCRYPTED_PROPERTIES))
            .withDefaultValues(ConfigProperties.DEFAULT_PROPERTIES);

        // Read config from /etc/candlepin/candlepin.conf
        if (Files.exists(DEFAULT_CONFIG_FILE)) {
            log.info("Loading candlepin.conf configuration!");
            configBuilder.withSources(externalCandlepinConfig(DEFAULT_CONFIG_FILE, 225));
        }

        // Read config files from /etc/candlepin/conf.d
        if (Files.exists(DEFAULT_CONFIG_DIR)) {
            log.info("Loading config files in candlepin/conf.d");
            try (Stream<Path> configFiles = Files.list(DEFAULT_CONFIG_DIR)) {
                configFiles.forEach(path -> {
                    log.info("Loading config file {}", path);
                    configBuilder.withSources(externalCandlepinConfig(path, 250));
                });
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return new RyeConfig(configBuilder.build());
    }

    /**
     * Creates a custom config source according to the given path and ordinal.
     *
     * @param path - a path to the external config
     * @param ordinal - a priority of the source. Higher ordinals have higher priority.
     * @return new config source
     */
    private PropertiesConfigSource externalCandlepinConfig(Path path, int ordinal) {
        try {
            return new PropertiesConfigSource(
                path.toUri().toURL(), ordinal
            );
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public enum DBManagementLevel {
        NONE("NONE"),
        REPORT("REPORT"),
        HALT("HALT"),
        MANAGE("MANAGE");

        private String name;

        DBManagementLevel(String name) {
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
        String configStart = config.getString(DB_MANAGE_ON_START);
        log.info("Liquibase startup management set to {}", configStart);
        DBManagementLevel dbmLevel = null;
        try {
            dbmLevel = DBManagementLevel.valueOf(configStart.toUpperCase());
        }
        catch (IllegalArgumentException iae) {
            log.error("The value {} of parameter '{}' is not allowed", configStart, DB_MANAGE_ON_START);
            throw new RuntimeException(iae.getMessage());
        }

        if (DBManagementLevel.NONE.equals(dbmLevel)) {
            return;
        }

        try (Database database = this.getDatabase()) {
            List<ChangeSet> unrunChangeSets = getUnrunChangeSets(database);
            if (unrunChangeSets.isEmpty()) {
                log.info("Candlepin database is up to date!");
            }
            else {
                Stream<String> csStream = unrunChangeSets.stream()
                    .map(changeset ->
                    String.format("file: %s, changeset: %s", changeset.getFilePath(), changeset.getId()));

                switch (dbmLevel) {
                    case REPORT:
                        log.warn("Database has {} unrun changeset(s): \n{}", unrunChangeSets.size(),
                            csStream.collect(Collectors.joining("\n  ", "  ", "")));
                        break;
                    case HALT:
                        log.error("Database has {} unrun changeset(s); halting startup...\n{}",
                            unrunChangeSets.size(), csStream.collect(Collectors.joining("\n  ", "  ", "")));
                        throw new RuntimeException("The database is missing Liquibase changeset(s)");
                    case MANAGE:
                        log.info("Calling liquibase to update the database");
                        log.info("Database has {} unrun changeset(s): \n{}", unrunChangeSets.size(),
                            csStream.collect(Collectors.joining("\n  ", "  ", "")));
                        executeUpdate(database);
                        log.info("Update complete");
                        break;
                    default:
                        throw new RuntimeException("Cannot determine database management mode.");
                }
            }
        }
        catch (LiquibaseException e) {
            log.error("Liquibase exception: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Initializes the CommandScope object and executes the update
     *
     * @param database the object which defines the database connection
     * @throws LiquibaseException
     */
    protected void executeUpdate(Database database) throws LiquibaseException {
        CommandScope commandScope = new CommandScope(UpdateCommandStep.COMMAND_NAME)
            .addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, database)
            .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG_FILE_NAME);
        commandScope.execute();
    }

    /**
     * Reads the list of unrun changesets from the database supplied based on the changelog.
     *
     * @param database the object which defines the database connection
     * @return List of unrun changesets
     * @throws LiquibaseException if there is an issue with reading the changesets.
     */
    protected List<ChangeSet> getUnrunChangeSets(Database database) throws LiquibaseException {
        try {
            return new StatusCommandStep().listUnrunChangeSets(
                null,
                null,
                new XMLChangeLogSAXParser().parse(CHANGELOG_FILE_NAME,
                    new ChangeLogParameters(), new ClassLoaderResourceAccessor()),
                database);
        }
        catch (Exception e) {
            // method throws generic exception
            throw new LiquibaseException(e.getMessage());
        }
    }

    /**
     * Establish the object for database communication from the configuration parameters
     *
     * @return Database object which defines the database connection
     * @throws LiquibaseException if there is an issue with establishing the Database object
     */
    protected Database getDatabase() throws LiquibaseException {
        Database database = null;
        try {
            Class.forName(config.getString(ConfigProperties.DB_DRIVER_CLASS))
                .getDeclaredConstructor().newInstance();
            Connection connection = DriverManager.getConnection(
                config.getString(ConfigProperties.DB_URL),
                config.getString(ConfigProperties.DB_USERNAME),
                config.getString(ConfigProperties.DB_PASSWORD));
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            database =
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
        }
        catch (ReflectiveOperationException | SQLException e) {
            throw new LiquibaseException(e);
        }
        return database;
    }
}
