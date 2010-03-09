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

import static com.google.inject.name.Names.named;

import java.io.Reader;

import javax.script.ScriptEngine;
import javax.servlet.Filter;

import org.fedoraproject.candlepin.servlet.filter.auth.FilterConstants;
import org.fedoraproject.candlepin.servlet.filter.auth.PassThroughAuthenticationFilter;
import org.fedoraproject.candlepin.util.LoggingFilter;
import org.fedoraproject.candlepin.LoggingFilter;
import org.fedoraproject.candlepin.service.CertificateServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultCertificateServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.fedoraproject.candlepin.servlet.filter.auth.FilterConstants;
import org.fedoraproject.candlepin.servlet.filter.auth.PassThroughAuthenticationFilter;

import com.google.inject.AbstractModule;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

/**
 * DefaultConfig
 */
class DefaultConfig extends AbstractModule {

    @Override
    public void configure() {
        bind(HttpServletDispatcher.class).asEagerSingleton();
        bind(LoggingFilter.class).asEagerSingleton();
        bind(Filter.class).annotatedWith(named(FilterConstants.BASIC_AUTH)).to(
            PassThroughAuthenticationFilter.class).asEagerSingleton();
        bind(Filter.class).annotatedWith(named(FilterConstants.SSL_AUTH)).to(
            PassThroughAuthenticationFilter.class).asEagerSingleton();
        bind(ScriptEngine.class).toProvider(ScriptEngineProvider.class);
        bind(Reader.class).annotatedWith(named("RulesReader"))
            .toProvider(RulesReaderProvider.class);
    }
}
