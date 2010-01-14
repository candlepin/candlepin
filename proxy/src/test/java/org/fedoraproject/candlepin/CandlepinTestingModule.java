package org.fedoraproject.candlepin;

import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.guice.JPAInitializer;
import org.fedoraproject.candlepin.test.DateSourceForTesting;

import com.google.inject.AbstractModule;
import com.wideplay.warp.persist.jpa.JpaUnit;

public class CandlepinTestingModule extends AbstractModule {

    @Override
    public void configure() {
        
        bind(JPAInitializer.class).asEagerSingleton();
        bindConstant().annotatedWith(JpaUnit.class).to("test");
        
        bind(DateSource.class).to(DateSourceForTesting.class).asEagerSingleton();
    }
}
