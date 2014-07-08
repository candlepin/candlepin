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
package org.canadianTenPin.guice;

import org.canadianTenPin.config.LoggingConfig;
import org.canadianTenPin.pki.SubjectKeyIdentifierWriter;
import org.canadianTenPin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.canadianTenPin.service.EntitlementCertServiceAdapter;
import org.canadianTenPin.service.IdentityCertServiceAdapter;
import org.canadianTenPin.service.OwnerServiceAdapter;
import org.canadianTenPin.service.ProductServiceAdapter;
import org.canadianTenPin.service.SubscriptionServiceAdapter;
import org.canadianTenPin.service.UserServiceAdapter;
import org.canadianTenPin.service.impl.DefaultEntitlementCertServiceAdapter;
import org.canadianTenPin.service.impl.DefaultIdentityCertServiceAdapter;
import org.canadianTenPin.service.impl.DefaultOwnerServiceAdapter;
import org.canadianTenPin.service.impl.DefaultProductServiceAdapter;
import org.canadianTenPin.service.impl.DefaultSubscriptionServiceAdapter;
import org.canadianTenPin.service.impl.DefaultUserServiceAdapter;
import org.canadianTenPin.servlet.filter.logging.LoggingFilter;
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
        bind(OwnerServiceAdapter.class).to(
            DefaultOwnerServiceAdapter.class);
        bind(IdentityCertServiceAdapter.class).to(
            DefaultIdentityCertServiceAdapter.class);
        bind(EntitlementCertServiceAdapter.class).to(
            DefaultEntitlementCertServiceAdapter.class);
        bind(UserServiceAdapter.class).to(DefaultUserServiceAdapter.class);
        bind(ProductServiceAdapter.class).to(DefaultProductServiceAdapter.class);
        bind(SubjectKeyIdentifierWriter.class).to(DefaultSubjectKeyIdentifierWriter.class);
    }
}
