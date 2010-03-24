/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.guice;

import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.guice.ModuleProcessor;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;


/**
 * Customized Candlepin version of {@link GuiceResteasyBootstrapServletContextListener}.
 *
 * The base version pulls in Guice modules by class name from web.xml and instanciates
 * them - however we have a need to add in modules programmatically for, e.g., servlet
 * filters and the wideplay JPA module.  This context listener overrides some of the
 * module initialization code to allow for module specification beyond simply listing
 * class names.
 */
public class CandlepinContextListener extends
        GuiceResteasyBootstrapServletContextListener {

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
        final ModuleProcessor processor = new ModuleProcessor(registry, providerFactory);

        processor.process(getModules());
    }

    /**
     * Returns a list of Guice modules to initialize.
     * @return a list of Guice modules to initialize.
     */
    protected List<Module> getModules() {
        List<Module> modules = new LinkedList<Module>();

        modules.add(PersistenceService.usingJpa().across(UnitOfWork.REQUEST)
                .buildModule());

        modules.add(Modules.override(new DefaultConfig()).with(
                new CustomizableModules().load()));

        modules.add(new CandlepinModule());
        modules.add(new CandlepinFilterModule());

        return modules;
    }
}
