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
package org.candlepin.spring;

import static org.candlepin.config.ConfigProperties.ACTIVEMQ_ENABLED;
import static org.candlepin.config.ConfigProperties.ENCRYPTED_PROPERTIES;
import static org.candlepin.config.ConfigProperties.PASSPHRASE_SECRET_FILE;

import org.candlepin.async.JobManager;
import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.audit.QpidConnection;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidStatus;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.EncryptedConfiguration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.logging.LoggingConfigurator;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.controller.QpidStatusMonitor;
import org.candlepin.controller.SuspendModeTransitioner;
import org.candlepin.guice.CandlepinCapabilities;
import org.candlepin.logging.LoggerContextListener;
import org.candlepin.messaging.CPMContextListener;
import org.candlepin.pki.impl.JSSProviderLoader;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.swagger.CandlepinSwaggerModelConverter;
import org.candlepin.util.CrlFileUtil;
import org.candlepin.util.Util;

import com.google.inject.Stage;

import io.swagger.converter.ModelConverters;

import org.apache.commons.lang.StringUtils;
import org.hibernate.dialect.PostgreSQL92Dialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.xnap.commons.i18n.I18nManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

import javax.cache.CacheManager;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
@Profile("!liquibase-only")
public class CandlepinContextListener implements ServletContextListener {
    public static final String CONFIGURATION_NAME = org.candlepin.common.config.Configuration.class.getName();

    // a bit of application-initialization code. Not sure if this is the
    // best spot for it.
    static {
        I18nManager.getInstance().setDefaultLocale(Locale.US);
    }

    private static Logger log = LoggerFactory.getLogger(org.candlepin.spring.CandlepinContextListener.class);

    private org.candlepin.common.config.Configuration config;

    private ServletContext servletContext;

    @Autowired
    private AnnotationLocator annotationLocator;

    @Autowired
    private QpidQmf qmf;

    @Autowired
    private QpidStatusMonitor qpidMonitor;

    @Autowired
    private QpidConnection qpidConnection;

    @Autowired
    private SuspendModeTransitioner suspendModeTransitioner;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JobManager jobManager;

    @Autowired
    private ActiveMQContextListener activeMQContextListener;

    @Autowired
    private CPMContextListener cpmContextListener;

    @Autowired
    private ModelConverters modelConverters;

    @Autowired
    private CandlepinSwaggerModelConverter candlepinSwaggerModelConverter;

    @Autowired
    private CrlFileUtil crlFileUtil;

    @Autowired
    private AMQPBusPublisher amqpBusPublisher;

    @Autowired
    private LoggerContextListener loggerListener;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Candlepin initializing context.");

        JSSProviderLoader.addProvider();

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

        // set things up BEFORE calling the super class' initialize method.
        //super.contextInitialized(sce);
        log.info("Candlepin context initialized.");
        try {
            this.initializeSubsystems();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeSubsystems() throws Exception {

        annotationLocator.init();

        // make sure our session factory is initialized before we attempt to start something
        // that relies upon it
        this.cpmContextListener.initialize();

        if (config.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            if (!config.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
                QpidStatus status = qmf.getStatus();
                if (status != QpidStatus.CONNECTED) {
                    log.error("Qpid is in status {}. Please fix Qpid configuration " +
                        "and make sure Qpid is up and running. Candlepin will shutdown now.", status);
                    throw new RuntimeException("Error during Startup: Qpid is in status " + status);
                }
            }

            qpidMonitor.addStatusChangeListener(qpidConnection);

            if (config.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
                qpidMonitor.addStatusChangeListener(suspendModeTransitioner);
            }

            // Run the monitor immediately so that the listeners are notified of the current status.
            // After which we can schedule the monitor to run on the configured interval.
            qpidMonitor.run();
            qpidMonitor.schedule();
        }

        if (config.getBoolean(ACTIVEMQ_ENABLED)) {
            // If Artemis can not be started candlepin will not start.
            activeMQContextListener.contextInitialized();
        }

        if (config.getBoolean(ConfigProperties.CACHE_JMX_STATS)) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                log.info("Enabling management and statistics for {} cache", cacheName);
                cacheManager.enableManagement(cacheName, true);
                cacheManager.enableStatistics(cacheName, true);
            });

        }

        // Setup the job manager
        this.jobManager.initialize();
        this.jobManager.start();

        // Custom ModelConverter to handle our specific serialization requirements
        modelConverters.addConverter(candlepinSwaggerModelConverter);

        if (config.getBoolean(ConfigProperties.KEYCLOAK_AUTHENTICATION)) {
            CandlepinCapabilities capabilities = CandlepinCapabilities.getCapabilities();
            capabilities.add(CandlepinCapabilities.KEYCLOAK_AUTH_CAPBILITY);
        }

        // Init CRL file
        String filePath = getCrlFilePath();
        File crlFile = new File(filePath);
        if (!crlFile.exists() || crlFile.length() == 0) {
            try {
                this.crlFileUtil.initializeCRLFile(crlFile, Collections.emptyList());
            }
            catch (IOException e) {
                log.error("Error occurred during initialization of CRL file!", e);
                throw e;
            }
            return;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        //super.contextDestroyed(event);
        try {
            this.destroySubsystems();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void destroySubsystems() throws Exception {
        // Tear down the job system
        this.jobManager.shutdown();

        // if amqp is enabled, close all connections.
        if (config.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            //Util.closeSafely(injector.getInstance(AMQPBusPublisher.class), "AMQPBusPublisher");
            Util.closeSafely(amqpBusPublisher, "AMQPBusPublisher");
        }

        //injector.getInstance(PersistService.class).stop();
        //persistService.stop();

        // deregister jdbc driver to avoid warning in tomcat shutdown log
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            try {
                DriverManager.deregisterDriver(driver);
            }
            catch (SQLException e) {
                log.info("Failed to de-registering driver {}", driver);
            }
        }

        if (config.getBoolean(ACTIVEMQ_ENABLED)) {
            activeMQContextListener.contextDestroyed();
        }

        // Make sure this is called after everything else, as other objects may rely on the
        // messaging subsystem
        //this.cpmContextListener.destroy();

        this.loggerListener.contextDestroyed();
    }

    protected void setCapabilities(org.candlepin.common.config.Configuration config) {
        CandlepinCapabilities capabilities = new CandlepinCapabilities();

        Set<String> blacklistedSet = config.getSet(ConfigProperties.HIDDEN_CAPABILITIES, null);
        if (blacklistedSet != null) {
            capabilities.removeAll(blacklistedSet);
        }

        CandlepinCapabilities.setCapabilities(capabilities);
        log.info("Candlepin will show support for the following capabilities: {}", capabilities);
    }

    protected org.candlepin.common.config.Configuration readConfiguration(ServletContext context)
        throws ConfigurationException {

        // Use StandardCharsets.UTF_8 when we move to Java 7
        Charset utf8 = Charset.forName("UTF-8");
        EncryptedConfiguration systemConfig = new EncryptedConfiguration();

        systemConfig.setEncoding(utf8);
        File configFile = new File(ConfigProperties.DEFAULT_CONFIG_FILE);

        if (configFile.canRead()) {
            log.debug("Loading system configuration");
            // First, read the system configuration
            systemConfig.load(configFile);
            log.debug("System configuration: " + systemConfig);
        }

        systemConfig.use(PASSPHRASE_SECRET_FILE).toDecrypt(ENCRYPTED_PROPERTIES);

        // load the defaults
        MapConfiguration defaults = new MapConfiguration(ConfigProperties.DEFAULT_PROPERTIES);

        // Default to Postgresql if jpa.config.hibernate.dialect is unset
        DatabaseConfigFactory.SupportedDatabase db = determinDatabaseConfiguration(systemConfig.getString
            ("jpa.config.hibernate.dialect", PostgreSQL92Dialect.class.getName()));
        log.info("Running under {}", db.getLabel());
        org.candlepin.common.config.Configuration databaseConfig = DatabaseConfigFactory.fetchConfig(db);

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

//    @Override
    protected Stage getStage(ServletContext context) {
        return Stage.PRODUCTION;
    }

    /**
     * Returns a list of Guice modules to initialize.
     * @return a list of Guice modules to initialize.
     */
//    @Override
//    protected List<Module> getModules(ServletContext context) {
//        List<Module> modules = new LinkedList<>();
//
//        modules.add(Modules.override(new DefaultConfig()).with(new CustomizableModules().load(config)));
//
//        modules.add(new AbstractModule() {
//
//            @Override
//            protected void configure() {
//                bind(org.candlepin.common.config.Configuration.class).toInstance(config);
//            }
//        });
//
//        modules.add(new CandlepinModule(config));
//        modules.add(new CandlepinFilterModule(config));
//
//        return modules;
//    }

//    /**
//     * There's no way to really get Guice to perform injections on stuff that
//     * the JpaPersistModule is creating, so we resort to grabbing the EntityManagerFactory
//     * after the fact and adding the Validation EventListener ourselves.
//     * @param injector
//     */
//    private void insertValidationEventListeners(Injector injector) {
//        javax.inject.Provider<EntityManagerFactory> emfProvider =
//                injector.getProvider(EntityManagerFactory.class);
//        HibernateEntityManagerFactory hibernateEntityManagerFactory =
//                (HibernateEntityManagerFactory) emfProvider.get();
//        SessionFactoryImpl sessionFactoryImpl =
//                (SessionFactoryImpl) hibernateEntityManagerFactory.getSessionFactory();
//        EventListenerRegistry registry =
//                sessionFactoryImpl.getServiceRegistry().getService(EventListenerRegistry.class);
//
//        javax.inject.Provider<BeanValidationEventListener> listenerProvider =
//                injector.getProvider(BeanValidationEventListener.class);
//        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listenerProvider.get());
//        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listenerProvider.get());
//        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listenerProvider.get());
//    }


    private String getCrlFilePath() throws ConfigurationException {
        String filePath = config.getString(ConfigProperties.CRL_FILE_PATH);

        if (filePath == null) {
            throw new ConfigurationException("CRL file path not defined in config file");
        }

        return filePath;
    }
}
