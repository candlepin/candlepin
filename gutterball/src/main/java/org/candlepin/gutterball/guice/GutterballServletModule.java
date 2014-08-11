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
package org.candlepin.gutterball.guice;

import org.candlepin.common.filter.LoggingFilter;

import com.google.inject.servlet.ServletModule;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

import java.util.HashMap;
import java.util.Map;
/**
 * GutterballServletModule is responsible for starting Guice and binding
 * all the dependencies.
 */
public class GutterballServletModule extends ServletModule {

    @Override
    protected void configureServlets() {
        bind(HttpServletDispatcher.class).asEagerSingleton();

        serve("/*").with(HttpServletDispatcher.class);
        // configure filters and or servlets as needed
        Map<String, String> loggingFilterConfig = new HashMap<String, String>();
        loggingFilterConfig.put("header.name", "x-gutterball-request-uuid");
        filter("/*").through(LoggingFilter.class, loggingFilterConfig);
    }

}
