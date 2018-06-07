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

import static org.candlepin.config.ConfigProperties.ENCRYPTED_PROPERTIES;
import static org.candlepin.config.ConfigProperties.ACTIVEMQ_ENABLED;
import static org.candlepin.config.ConfigProperties.PASSPHRASE_SECRET_FILE;

import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidQmf.QpidStatus;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.EncryptedConfiguration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.logging.LoggingConfigurator;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.controller.SuspendModeTransitioner;
import org.candlepin.logging.LoggerContextListener;
import org.candlepin.model.Status;
import org.candlepin.pinsetter.core.PinsetterContextListener;
import org.candlepin.pki.impl.JSSProviderLoader;
import org.candlepin.resteasy.ResourceLocatorMap;
import org.candlepin.swagger.CandlepinSwaggerModelConverter;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;

import org.apache.commons.lang.StringUtils;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18nManager;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.management.ManagementService;

import io.swagger.converter.ModelConverters;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.management.MBeanServer;
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

    private ActiveMQContextListener activeMQContextListener;
    private PinsetterContextListener pinsetterListener;
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
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Candlepin initializing context.");

//        BouncyCastleProviderLoader.addProvider();
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
        super.contextInitialized(sce);
        log.info("Candlepin context initialized.");
    }

    @Override
    public void withInjector(Injector injector) {
        // Must call super.contextInitialized() before accessing injector
        insertValidationEventListeners(injector);
        ResourceLocatorMap map = injector.getInstance(ResourceLocatorMap.class);
        map.init();

        if (config.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED) &&
            !config.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
            QpidQmf qmf = injector.getInstance(QpidQmf.class);
            QpidStatus status = qmf.getStatus();
            if (status != QpidStatus.CONNECTED) {
                log.error("Qpid is in status {}. Please fix Qpid configuration " +
                    "and make sure Qpid is up and running. Candlepin will shutdown " +
                    "now.", status);
                throw new RuntimeException("Error during Startup: Qpid is in status " + status);
            }
        }

        if (config.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED) &&
            config.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
            SuspendModeTransitioner mw = injector.getInstance(SuspendModeTransitioner.class);
            mw.transitionAppropriately();
            mw.startPeriodicExecutions();
        }

        if (config.getBoolean(ACTIVEMQ_ENABLED)) {
            try {
                activeMQContextListener = injector.getInstance(ActiveMQContextListener.class);
                activeMQContextListener.contextInitialized(injector);
            }
            catch (Exception e) {
                log.error("Exception occurred while trying to load ActiveMQ", e);
            }
        }

        if (config.getBoolean(ConfigProperties.CACHE_JMX_STATS)) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ManagementService.registerMBeans(CacheManager.getInstance(), mBeanServer,
                true, true, true, true);
        }

        pinsetterListener = injector.getInstance(PinsetterContextListener.class);
        pinsetterListener.contextInitialized();

        loggerListener = injector.getInstance(LoggerContextListener.class);

        /**
         * Custom ModelConverter to handle our specific serialization requirements
         */
        ModelConverters.getInstance()
            .addConverter(injector.getInstance(CandlepinSwaggerModelConverter.class));

        this.injector = injector;
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        super.contextDestroyed(event);
        if (config.getBoolean(ACTIVEMQ_ENABLED)) {
            activeMQContextListener.contextDestroyed();
        }
        pinsetterListener.contextDestroyed();
        loggerListener.contextDestroyed();

        // if amqp is enabled, close all connections.
        if (config.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            Util.closeSafely(injector.getInstance(AMQPBusPublisher.class),
                "AMQPBusPublisher");
        }
    }

    protected void setCapabilities(Configuration config) {
        Set<String> blacklistedSet = config.getSet(ConfigProperties.HIDDEN_CAPABILITIES,
            Collections.<String>emptySet());
        Set<String> exposedSet = new HashSet<>(Arrays.asList(Status.DEFAULT_CAPABILITIES));
        exposedSet.removeAll(blacklistedSet);

        Status.setAvailableCapabilities(exposedSet.toArray(new String[exposedSet.size()]));
        log.info("Candlepin will show support for {}", Status.getAvailableCapabilities());
    }

    protected Configuration readConfiguration(ServletContext context)
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
            ("jpa.config.hibernate.dialect", PostgreSQLDialect.class.getName()));
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
        else if (StringUtils.containsIgnoreCase(
            dialect, DatabaseConfigFactory.SupportedDatabase.ORACLE.getLabel())) {
            return DatabaseConfigFactory.SupportedDatabase.ORACLE;
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
}
