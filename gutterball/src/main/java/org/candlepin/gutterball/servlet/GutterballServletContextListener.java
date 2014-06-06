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

import org.candlepin.gutterball.configuration.Configuration;
import org.candlepin.gutterball.configuration.ConfigurationException;
import org.candlepin.gutterball.configuration.PropertiesFileConfiguration;
import org.candlepin.gutterball.guice.GutterballServletModule;
import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;

import org.jboss.resteasy.plugins.guice.GuiceResourceFactory;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResourceFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.GetRestful;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18nManager;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.ext.Provider;

/**
 * The GutterballServletContextListener initializes all the injections and
 * registers all the RESTEasy resources.
 */
public class GutterballServletContextListener extends
    GuiceServletContextListener {

    public static final String CONFIGURATION_NAME = Configuration.class.getName();

    private static Logger log = LoggerFactory.getLogger(I18nProvider.class);

    private Configuration config;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        I18nManager.getInstance().setDefaultLocale(Locale.US);
        ServletContext context = sce.getServletContext();

        try {
            config = readConfiguration(context);
        }
        catch (ConfigurationException e) {
            log.error("Could not read configuration file.  Aborting initialization.", e);
            throw new RuntimeException(e);
        }

        context.setAttribute(CONFIGURATION_NAME, config);

        super.contextInitialized(sce);
        processRestEasy(context);
    }

    protected Configuration readConfiguration(ServletContext servletContext)
        throws ConfigurationException {

        Charset utf8 = StandardCharsets.UTF_8;

        InputStream defaultStream = GutterballServletModule.class
                .getClassLoader().getResourceAsStream("default.properties");

        PropertiesFileConfiguration defaults =
                new PropertiesFileConfiguration(defaultStream, utf8);
        return defaults;

        // String confFile = servletContext.getInitParameter("org.candlepin.gutterball.config_file");
        // Configuration userConfig = new PropertiesFileConfiguration(confFile, utf8);
        // return userConfig.merge(defaults);
    }

    @Override
    protected Injector getInjector() {
        return Guice.createInjector(new GutterballServletModule());
    }

    /**
     * The RESTEasy ModuleProcessor class doesn't return the injector nor does
     * it allow you to send an injector in.  In order to use the more flexible
     * GuiceServletContextListener, we have to implement getInjector() so we need
     * a method that can accept an injector.  Thus, we duplicate the
     * ModuleProcessor functionality.
     *
     * @param context
     */
    @SuppressWarnings("rawtypes")
    protected void processRestEasy(ServletContext context) {
        final Injector injector = (Injector) context
                .getAttribute(Injector.class.getName());
        final Registry registry = (Registry) context
                .getAttribute(Registry.class.getName());
        final ResteasyProviderFactory providerFactory = (ResteasyProviderFactory) context
                .getAttribute(ResteasyProviderFactory.class.getName());

        for (final Binding<?> binding : injector.getBindings().values()) {
            final Type type = binding.getKey().getTypeLiteral().getType();
            if (type instanceof Class) {
                final Class<?> beanClass = (Class) type;
                if (GetRestful.isRootResource(beanClass)) {
                    final ResourceFactory resourceFactory = new GuiceResourceFactory(
                            binding.getProvider(), beanClass);
                    log.info("Registering factory for {}", beanClass.getName());
                    registry.addResourceFactory(resourceFactory);
                }
                if (beanClass.isAnnotationPresent(Provider.class)) {
                    log.info("Registering provider instance for {}", beanClass.getName());
                    providerFactory.registerProviderInstance(binding
                            .getProvider().get());
                }
            }
        }
    }
}
