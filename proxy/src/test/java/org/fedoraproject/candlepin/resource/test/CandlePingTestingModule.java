package org.fedoraproject.candlepin.resource.test;

import org.fedoraproject.candlepin.guice.JPAInitializer;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.jpa.JpaUnit;

public class CandlePingTestingModule extends AbstractModule {

    @Override
    public void configure() {
        
        bind(JPAInitializer.class).asEagerSingleton();
        bindConstant().annotatedWith(JpaUnit.class).to("test");

    }
}
