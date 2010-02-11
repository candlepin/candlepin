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

import java.util.Properties;

import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.configuration.CandlepinConfiguration;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.EntitlementPoolResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.RulesResource;
import org.fedoraproject.candlepin.resource.TestResource;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultProductServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.fedoraproject.candlepin.util.DateSourceImpl;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.jpa.JpaUnit;

public class CandlepinProductionConfiguration extends AbstractModule {

    @Override
    public void configure() {        
        bind(JPAInitializer.class).asEagerSingleton();
        
        bind(Properties.class)
            .annotatedWith(JpaUnit.class)
            .toInstance(new CandlepinConfiguration().jpaConfiguration()); 
        bindConstant().annotatedWith(JpaUnit.class).to("production");        
        
        bind(CertificateResource.class);
        bind(ConsumerResource.class);
        bind(EntitlementPoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(ProductServiceAdapter.class).to(DefaultProductServiceAdapter.class);         
        bind(ProductResource.class);
        bind(TestResource.class);
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(JavascriptEnforcer.class);
        bind(RulesResource.class);
        bind(PostEntHelper.class);
        bind(PreEntHelper.class);
        bind(SubscriptionServiceAdapter.class).to(DefaultSubscriptionServiceAdapter.class); 
    }
}
