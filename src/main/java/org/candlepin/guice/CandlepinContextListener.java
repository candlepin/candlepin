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

import org.candlepin.audit.HornetqContextListener;
import org.candlepin.logging.LoggerContextListener;
import org.candlepin.pinsetter.core.PinsetterContextListener;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import org.jboss.resteasy.plugins.guice.GuiceResourceFactory;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResourceFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.GetRestful;
import org.xnap.commons.i18n.I18nManager;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.ext.Provider;

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
public class CandlepinContextListener extends
        GuiceResteasyBootstrapServletContextListener {
    private HornetqContextListener hornetqListener;
    private PinsetterContextListener pinsetterListener;
    private LoggerContextListener loggerListener;

    private Injector injector;
    // a bit of application-initialization code. Not sure if this is the
    // best spot for it.
    static {
        I18nManager.getInstance().setDefaultLocale(Locale.US);
    }

    @Override
    public void contextInitialized(final ServletContextEvent event) {
        super.contextInitialized(event);

        // this is pulled almost verbatim from the superclass - if only they
        // had made their internal getModules() method protected, then this
        // would not be necessary.
        final ServletContext context = event.getServletContext();
        final Registry registry = (Registry) context.getAttribute(
                Registry.class.getName());
        final ResteasyProviderFactory providerFactory =
                (ResteasyProviderFactory) context.getAttribute(
                    ResteasyProviderFactory.class.getName());

        injector = Guice.createInjector(getModules());
        processInjector(registry, providerFactory, injector);

        hornetqListener = injector.getInstance(HornetqContextListener.class);
        hornetqListener.contextInitialized(injector);
        pinsetterListener = injector.getInstance(PinsetterContextListener.class);
        pinsetterListener.contextInitialized();
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        hornetqListener.contextDestroyed();
        pinsetterListener.contextDestroyed();
        loggerListener = injector.getInstance(LoggerContextListener.class);
        loggerListener.contextDestroyed();
    }

    /**
     * Returns a list of Guice modules to initialize.
     * @return a list of Guice modules to initialize.
     */
    protected List<Module> getModules() {
        List<Module> modules = new LinkedList<Module>();

        modules.add(Modules.override(new DefaultConfig()).with(
                new CustomizableModules().load()));

        modules.add(new CandlepinModule());
        modules.add(new CandlepinFilterModule());

        return modules;
    }

    /**
     * This is what RESTEasy's ModuleProcessor does, but we need the injector
     * afterwards.
     * @param injector - guice injector
     */
    private void processInjector(Registry registry,
        ResteasyProviderFactory providerFactory, Injector injector) {
        for (final Binding<?> binding : injector.getBindings().values()) {
            final Type type = binding.getKey().getTypeLiteral().getType();
            if (type instanceof Class) {
                final Class<?> beanClass = (Class) type;
                if (GetRestful.isRootResource(beanClass)) {
                    final ResourceFactory resourceFactory =
                        new GuiceResourceFactory(binding.getProvider(), beanClass);
                    registry.addResourceFactory(resourceFactory);
                }
                if (beanClass.isAnnotationPresent(Provider.class)) {
                    providerFactory.registerProviderInstance(binding.getProvider().get());
                }
            }
        }
    }
}
