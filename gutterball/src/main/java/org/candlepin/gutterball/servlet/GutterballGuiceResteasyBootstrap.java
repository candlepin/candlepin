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

import com.google.inject.Binding;
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
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.ext.Provider;


/**
 * GutterballGuiceResteasyBootstrap is an intermediate class that will make it
 * easier to upgrade to RESTEasy 3.0. It takes the relevant parts from
 * GuiceResteasyBootstrapServletContextListener that we need and also uses some
 * of the changes they made in 3.0 with protected methods. They are abstract
 * here since we will override them.
 */
public abstract class  GutterballGuiceResteasyBootstrap extends ResteasyBootstrap
        implements ServletContextListener {
    private static Logger log = LoggerFactory.getLogger(GutterballGuiceResteasyBootstrap.class);

    @Override
    public void contextInitialized(final ServletContextEvent event) {
        super.contextInitialized(event);
        final ServletContext context = event.getServletContext();
        final List<Module> modules = getModules(context);
        final Stage stage = getStage(context);
        try {
            processInjector(context, getInjector(stage, modules));
        }
        catch (Exception e) {
            log.error("Could not create Guice injector.", e);
            throw new RuntimeException(e);
        }

        log.debug("Returned from process injector");
    }

    protected abstract Injector getInjector(Stage stage, List<Module> modules);

    protected abstract Stage getStage(ServletContext context);

    protected abstract List<Module> getModules(final ServletContext context);

    /**
     * The RESTEasy ModuleProcessor class doesn't return the injector nor does
     * it allow you to send an injector in.  In order to use the more flexible
     * GutterballContextListener, we have to implement getInjector() so we need
     * a method that can accept an injector.  Thus, we duplicate the
     * ModuleProcessor functionality.
     *
     * TODO: RESTEasy 3.0 fixes this problem and we would no longer need this.
     * @param context
     * @param injector
     */
    @SuppressWarnings("rawtypes")
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
