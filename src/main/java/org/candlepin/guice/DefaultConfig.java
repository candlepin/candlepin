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

import org.candlepin.config.LoggingConfig;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultEntitlementCertServiceAdapter;
import org.candlepin.service.impl.DefaultIdentityCertServiceAdapter;
import org.candlepin.service.impl.DefaultProductServiceAdapter;
import org.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.candlepin.service.impl.DefaultUserServiceAdapter;
import org.candlepin.servlet.filter.logging.LoggingFilter;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

import com.google.inject.AbstractModule;

/**
 * DefaultConfig
 */
class DefaultConfig extends AbstractModule {

    @Override
    public void configure() {
        bind(HttpServletDispatcher.class).asEagerSingleton();
        bind(LoggingFilter.class).asEagerSingleton();
        bind(LoggingConfig.class).asEagerSingleton();
        bind(ScriptEngineProvider.class);
        bind(SubscriptionServiceAdapter.class).to(
            DefaultSubscriptionServiceAdapter.class);
        bind(IdentityCertServiceAdapter.class).to(
            DefaultIdentityCertServiceAdapter.class);
        bind(EntitlementCertServiceAdapter.class).to(
            DefaultEntitlementCertServiceAdapter.class);
        bind(UserServiceAdapter.class).to(DefaultUserServiceAdapter.class);
        bind(ProductServiceAdapter.class).to(DefaultProductServiceAdapter.class);
        bind(SubjectKeyIdentifierWriter.class).to(DefaultSubjectKeyIdentifierWriter.class);
    }
}
