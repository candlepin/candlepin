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

import org.fedoraproject.candlepin.servlet.filter.VersionFilter;
import org.fedoraproject.candlepin.servlet.filter.logging.LoggingFilter;

import com.google.inject.servlet.ServletModule;
import com.wideplay.warp.persist.PersistenceFilter;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

/**
 * Candlepin-specific {@link ServletModule} that configures servlet filters.
 */
public class CandlepinFilterModule extends ServletModule {

    @Override
    protected void configureServlets() {
        filter("/*").through(PersistenceFilter.class);
        filter("/*").through(LoggingFilter.class);
        filter("/*").through(VersionFilter.class);

        serve("/*").with(HttpServletDispatcher.class);
    }
    
}

