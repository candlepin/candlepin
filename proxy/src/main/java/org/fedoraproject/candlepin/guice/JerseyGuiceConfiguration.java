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

import org.fedoraproject.candlepin.auth.servletfilter.BasicAuthFilter;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
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
                            .servlets()
                        .buildModule()
                );

                addAll(new CustomizableModules().load());
            }
        });
    }
}
