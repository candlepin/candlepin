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
package org.candlepin.gutterball.servlet;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.config.PropertiesFileConfiguration;
import org.candlepin.common.logging.LoggingConfigurator;
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.guice.GutterballModule;
import org.candlepin.gutterball.guice.GutterballServletModule;
import org.candlepin.gutterball.receiver.EventReceiver;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18nManager;

import java.io.File;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 * The GutterballContextListener initializes all the injections and
 * registers all the RESTEasy resources.
 */
public class GutterballContextListener extends
    GuiceResteasyBootstrapServletContextListener {

    public static final String CONFIGURATION_NAME = Configuration.class.getName();

    private static Logger log = LoggerFactory.getLogger(GutterballContextListener.class);

    private Configuration config;

    // getServletContext() from the GuiceServletContextListener is deprecated.
    // See
    // https://github.com/google/guice/blob/bf0e7ce902dd97e62ef16679c587d78d59200450
    // /extensions/servlet/src/com/google/inject/servlet/GuiceServletContextListener.java#L43-L45
    // A typical way of doing this then is to cache the context ourselves:
    // https://github.com/google/guice/issues/603
    // Currently only needed for access to the Configuration.
    private ServletContext servletContext;

    private EventReceiver eventReceiver;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Gutterball initializing context.");
        I18nManager.getInstance().setDefaultLocale(Locale.US);
        servletContext = sce.getServletContext();

        try {
            log.info("Gutterball reading configuration.");
            config = readConfiguration(servletContext);
        }
        catch (ConfigurationException e) {
            log.error("Could not read configuration file.  Aborting initialization.", e);
            throw new RuntimeException(e);
        }

        LoggingConfigurator.init(config);
        servletContext.setAttribute(CONFIGURATION_NAME, config);
        log.debug("Gutterball stored config on context.");

        // set things up BEFORE calling the super class' initialize method.
        super.contextInitialized(sce);
        log.info("Gutterball context initialized.");
    }

    protected Configuration readConfiguration(ServletContext context)
        throws ConfigurationException {

        // Use StandardCharsets.UTF_8 when we move to Java 7
        Charset utf8 = Charset.forName("UTF-8");
        PropertiesFileConfiguration systemConfig = new PropertiesFileConfiguration();
        systemConfig.setEncoding(utf8);
        File configFile = new File(ConfigProperties.DEFAULT_CONFIG_FILE);

        if (configFile.canRead()) {
            log.debug("Loading system configuration");
            // First, read the system configuration
            systemConfig.load(configFile);
            log.debug("System configuration: " + systemConfig);
        }

        // load the defaults
        MapConfiguration defaults = new MapConfiguration(
            ConfigProperties.DEFAULT_PROPERTIES);

        log.debug("Loading default configuration values");

        log.debug("Default config: " + defaults);
        // merge the defaults with the system configuration. ORDER MATTERS.
        // system config must be read FIRST otherwise settings won't be applied.

        // merge does NOT affect systemConfig, it just returns a new object
        // not sure I like that.
        Configuration merged = PropertiesFileConfiguration.merge(systemConfig, defaults);

        log.debug("Configuration: " + merged);
        return merged;
    }

    @Override
    protected Stage getStage(ServletContext context) {
        // see https://github.com/google/guice/wiki/Bootstrap for information
        // on Stage.
        return Stage.PRODUCTION;
    }

    @Override
    protected List<Module> getModules(ServletContext context) {
        if (config == null) {
            log.error("Config is null");
        }

        List<Module> modules = new LinkedList<Module>();
        modules.add(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Configuration.class).toInstance(config);
            }
        });
        modules.add(new GutterballServletModule());
        modules.add(new GutterballModule(config));

        return modules;
    }

    @Override
    public void withInjector(Injector injector) {
        this.eventReceiver = injector.getInstance(EventReceiver.class);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        log.info("Destroying gutterball context");
        super.contextDestroyed(event);

        eventReceiver.finish();
    }
}
