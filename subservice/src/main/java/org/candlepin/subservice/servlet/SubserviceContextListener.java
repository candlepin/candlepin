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
package org.candlepin.subservice.servlet;

import org.candlepin.subservice.guice.SubserviceModule;
import org.candlepin.subservice.guice.SubserviceServletModule;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import org.jboss.resteasy.plugins.guice.GuiceResourceFactory;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResourceFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.GetRestful;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.ext.Provider;

/**
 * The SubserviceContextListener initializes all the injections and
 * registers all the RESTEasy resources.
 */
public class SubserviceContextListener extends ResteasyBootstrap
    implements ServletContextListener {

    private static Logger log = LoggerFactory.getLogger(SubserviceContextListener.class);
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        super.contextInitialized(sce);
        final ServletContext context = sce.getServletContext();
        final List<Module> modules = getModules(context);
        final Stage stage = getStage(context);
        try {
            processInjector(context, getInjector(stage, modules));
        }
        catch (Exception e) {
            log.error("Could not create Guice injector.", e);
            throw new RuntimeException(e);
        }

        log.info("Guice injector creation complete");

    }

    protected Injector getInjector(Stage stage, List<Module> modules) {
        return Guice.createInjector(stage, modules);
    }

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
    protected List<Module> getModules(ServletContext context) {
        List<Module> modules = new LinkedList<Module>();
        modules.add(new SubserviceServletModule());
        modules.add(new SubserviceModule());

        return modules;
    }

    protected void processInjector(ServletContext context, Injector inj) {
        final Registry registry = (Registry) context
                .getAttribute(Registry.class.getName());
        final ResteasyProviderFactory providerFactory = (ResteasyProviderFactory) context
                .getAttribute(ResteasyProviderFactory.class.getName());

        for (final Binding<?> binding : inj.getBindings().values()) {
            final Type type = binding.getKey().getTypeLiteral().getType();
            if (type instanceof Class) {
                final Class<?> beanClass = (Class) type;
                if (GetRestful.isRootResource(beanClass)) {
                    final ResourceFactory resourceFactory = new GuiceResourceFactory(
                            binding.getProvider(), beanClass);
                    log.debug("Registering factory for {}", beanClass.getName());
                    registry.addResourceFactory(resourceFactory);
                }
                if (beanClass.isAnnotationPresent(Provider.class)) {
                    log.debug("Registering provider instance for {}", beanClass.getName());
                    providerFactory.registerProviderInstance(binding
                            .getProvider().get());
                }
            }
        }
    }


}
