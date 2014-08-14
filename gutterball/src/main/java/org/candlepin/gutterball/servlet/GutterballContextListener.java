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
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.guice.GutterballModule;
import org.candlepin.gutterball.guice.GutterballServletModule;
import org.candlepin.gutterball.mongodb.MongoConnection;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.mongodb.MongoException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18nManager;

import java.io.InputStream;
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
    GutterballGuiceResteasyBootstrap {

    public static final String CONFIGURATION_NAME = Configuration.class.getName();

    private static Logger log = LoggerFactory.getLogger(GutterballContextListener.class);

    private Configuration config;
    private MongoConnection mongo;

    private Injector injector;

    // getServletContext() from the GuiceServletContextListener is deprecated.
    // See
    // https://github.com/google/guice/blob/bf0e7ce902dd97e62ef16679c587d78d59200450
    // /extensions/servlet/src/com/google/inject/servlet/GuiceServletContextListener.java#L43-L45
    // A typical way of doing this then is to cache the context ourselves:
    // https://github.com/google/guice/issues/603
    // Currently only needed for access to the Configuration.
    private ServletContext servletContext;

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

        log.debug("Gutterball stored config on context.");

        servletContext.setAttribute(CONFIGURATION_NAME, config);

        // Setup mongodb connection.
        try {
            mongo = new MongoConnection(config);
        }
        catch (MongoException e) {
            log.error("Could not connect to mongo database. Aborting initialization.", e);
            throw new RuntimeException(e);
        }

        // set things up BEFORE calling the super class' initialize method.
        super.contextInitialized(sce);
        log.info("Gutterball context initialized.");
    }

    @Override
    protected Injector getInjector(Stage stage, List<Module> modules) {
        return Guice.createInjector(stage, modules);
    }

    protected Configuration readConfiguration(ServletContext context)
        throws ConfigurationException {

        // Use StandardCharsets.UTF_8 when we move to Java 7
        // Charset utf8 = Charset.forName("UTF-8");
        // We need to read /etc/gutterball/gutterball.conf

        MapConfiguration defaults = new MapConfiguration(
            ConfigProperties.DEFAULT_PROPERTIES);
        return defaults;
    }

    @Override
    protected Stage getStage(ServletContext context) {
        // RESTEasy 3.0 has a getState with a context that we can override.
        // Right now we don't use context for our need but when we do switch
        // we'll be able to add an @Override to this method.

        // see https://github.com/google/guice/wiki/Bootstrap for information
        // on Stage.
        return Stage.PRODUCTION;
    }

    /**
     * Returns a list of Guice modules to initialize.
     * @return a list of Guice modules to initialize.
     */
    @Override
    protected List<Module> getModules(ServletContext context) {
        // RESTEasy 3.0 has a getState with a context that we can override.
        // Right now we don't use context for our need but when we do switch
        // we'll be able to add an @Override to this method.

        if (config == null) {
            log.error("Config is null");
        }

        List<Module> modules = new LinkedList<Module>();
        modules.add(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Configuration.class).toInstance(config);
                bind(MongoConnection.class).toInstance(mongo);
            }
        });
        modules.add(new GutterballServletModule());
        modules.add(new GutterballModule());

        return modules;
    }

    protected void processInjector(ServletContext context, Injector inj) {
        injector = inj;
        super.processInjector(context, injector);
    }
}
