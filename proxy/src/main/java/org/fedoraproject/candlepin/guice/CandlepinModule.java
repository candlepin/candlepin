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

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.resource.AdminResource;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.PoolResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.RulesResource;
import org.fedoraproject.candlepin.resource.StatusResource;
import org.fedoraproject.candlepin.resource.TestResource;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultProductServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;
import org.fedoraproject.candlepin.util.DateSourceImpl;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.jpa.JpaUnit;

import java.util.Properties;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultEntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultIdentityCertServiceAdapter;

/**
 * CandlepinProductionConfiguration
 */
public class CandlepinModule extends AbstractModule {

    @Override
    public void configure() {
        bind(JPAInitializer.class).asEagerSingleton();

        bind(Properties.class).annotatedWith(JpaUnit.class).toInstance(
            new Config().jpaConfiguration());

        // We default to test persistence unit (HSQL),
        // /etc/candlepin/candlepin.conf
        // will override:
        bindConstant().annotatedWith(JpaUnit.class).to("default");

        bind(Config.class);
        bind(CertificateResource.class);
        bind(ConsumerResource.class);
        bind(PoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(ProductServiceAdapter.class)
            .to(DefaultProductServiceAdapter.class);
        bind(ProductResource.class);
        bind(TestResource.class);
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(JavascriptEnforcer.class);
        bind(RulesResource.class);
        bind(AdminResource.class);
        bind(PostEntHelper.class);
        bind(PreEntHelper.class);
        bind(StatusResource.class);
        bind(SubscriptionServiceAdapter.class).to(
            DefaultSubscriptionServiceAdapter.class);
        bind(IdentityCertServiceAdapter.class).to(
            DefaultIdentityCertServiceAdapter.class);
        bind(EntitlementCertServiceAdapter.class).to(
            DefaultEntitlementCertServiceAdapter.class);
    }
}
