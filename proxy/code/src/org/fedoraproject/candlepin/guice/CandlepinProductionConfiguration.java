package org.fedoraproject.candlepin.guice;

import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.EntitlementPoolResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.TestResource;
import org.fedoraproject.candlepin.resource.UserResource;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.jpa.JpaUnit;

public class CandlepinProductionConfiguration extends AbstractModule {

    @Override
    public void configure() {        
        bind(JPAInitializer.class).asEagerSingleton();
        bindConstant().annotatedWith(JpaUnit.class).to("production");        
        
        bind(CertificateResource.class);
        bind(ConsumerResource.class);
        bind(EntitlementPoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(ProductResource.class);
        bind(UserResource.class);
        bind(TestResource.class);
    }
}
