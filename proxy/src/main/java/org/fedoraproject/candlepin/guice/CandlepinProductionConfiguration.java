package org.fedoraproject.candlepin.guice;

import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.java.JavaEnforcer;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.EntitlementPoolResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.TestResource;
import org.fedoraproject.candlepin.resource.UserResource;
import org.fedoraproject.candlepin.util.DateSourceImpl;

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
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(JavaEnforcer.class);
    }
}
