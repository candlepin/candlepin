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
package org.fedoraproject.candlepin;

import org.fedoraproject.candlepin.guice.JPAInitializer;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.test.TestRulesCurator;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.PoolResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.TestResource;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultProductServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.DateSourceForTesting;
import org.fedoraproject.candlepin.util.DateSource;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.jpa.JpaUnit;
import org.fedoraproject.candlepin.cert.BouncyCastlePKI;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.impl.stub.StubIdentityCertServiceAdapter;

public class CandlepinCommonTestingModule extends AbstractModule {

    @Override
    public void configure() {
        
        bind(JPAInitializer.class).asEagerSingleton();        
        bindConstant().annotatedWith(JpaUnit.class).to("default");
        
        bind(CertificateResource.class);
        bind(ConsumerResource.class);
        bind(PoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(ProductServiceAdapter.class).to(DefaultProductServiceAdapter.class);         
        bind(ProductResource.class);
        bind(TestResource.class);
        bind(DateSource.class).to(DateSourceForTesting.class).asEagerSingleton();
        bind(Enforcer.class).to(JavascriptEnforcer.class);
        bind(BouncyCastlePKI.class);
        bind(SubscriptionServiceAdapter.class).to(DefaultSubscriptionServiceAdapter.class);
        bind(IdentityCertServiceAdapter.class).to(StubIdentityCertServiceAdapter.class);
        bind(Config.class);
        bind(RulesCurator.class).to(TestRulesCurator.class);
    }
}
