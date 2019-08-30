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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.config.ConfigProperties;
import org.candlepin.servlet.filter.CandlepinPersistFilter;
import org.candlepin.servlet.filter.CandlepinScopeFilter;
import org.candlepin.servlet.filter.EventFilter;

import com.google.inject.servlet.ServletModule;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Candlepin-specific {@link ServletModule} that configures servlet filters.
 */
public class CandlepinFilterModule extends ServletModule {
    private Configuration config;

    public CandlepinFilterModule(Configuration config) {
        this.config = config;
    }

    @Override
    protected void configureServlets() {
        Map<String, String> loggingFilterConfig = new HashMap<>();
        loggingFilterConfig.put("header.name", "x-candlepin-request-uuid");

        String regex = ".*";

        /*
         * A negative lookeahead regex that makes sure that we can serve
         * static content at docs/ and/or token/
         */
        if (config.getBoolean(ConfigProperties.TOKENPAGE_ENABLED) &&
            config.getBoolean(ConfigProperties.SWAGGER_ENABLED)) {
            // don't filter docs or token
            regex = "^(?!/docs|/token).*";
        }
        else if (config.getBoolean(ConfigProperties.SWAGGER_ENABLED)) {
            // don't filter docs
            regex = "^(?!/docs).*";
        }
        else if (config.getBoolean(ConfigProperties.TOKENPAGE_ENABLED)) {
            // don't filter token
            regex = "^(?!/token).*";
        }
        filterRegex(regex).through(CandlepinScopeFilter.class);
        filterRegex(regex).through(CandlepinPersistFilter.class);
        filterRegex(regex).through(LoggingFilter.class, loggingFilterConfig);
        filterRegex(regex).through(EventFilter.class);

        serveRegex(regex).with(HttpServletDispatcher.class);
    }
}
