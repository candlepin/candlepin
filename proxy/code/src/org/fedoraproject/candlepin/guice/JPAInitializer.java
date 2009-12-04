package org.fedoraproject.candlepin.guice;

import com.google.inject.Inject;
import com.wideplay.warp.persist.PersistenceService;

public class JPAInitializer {
    @Inject 
    protected JPAInitializer(PersistenceService service) {
        service.start(); 
    }
}
