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

import static com.google.inject.name.Names.*;

import java.util.LinkedList;

import javax.servlet.Filter;

import org.fedoraproject.candlepin.LoggingFilter;
import org.fedoraproject.candlepin.servletfilter.auth.FilterConstants;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import com.wideplay.warp.servlet.Servlets;
import com.wideplay.warp.servlet.WarpServletContextListener;


/**
 * configure Guice with the resource classes.
 */
public class JerseyGuiceConfiguration extends WarpServletContextListener {

    @Override
    protected Injector getInjector() {
        return Guice.createInjector(new LinkedList<Module>() {

            {
                add(PersistenceService.usingJpa().across(UnitOfWork.REQUEST)
                        .buildModule());

                add(new CandlepinProductionConfiguration());
                
                add(
                    Servlets.configure()
                        .filters()
                            .filter("/*").through(LoggingFilter.class)
                            .filter("/*").through(Key.get(Filter.class, named(FilterConstants.BASIC_AUTH)))
                        .servlets()
                            .serve("/*").with(ServletContainer.class)
                     .buildModule()
                );

                add(Modules.override(new DefaultConfig()).with(new CustomizableModules().load()));
            }
        });
    }
}
