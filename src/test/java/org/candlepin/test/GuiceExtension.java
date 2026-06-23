package org.candlepin.test;

import org.candlepin.TestingModules;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class GuiceExtension implements TestInstancePostProcessor {

    private static final Injector INJECTOR = Guice.createInjector(
        new TestingModules.MockJpaModule(),
        new TestingModules.StandardTest(),
        new TestingModules.PKIModule(),
        new TestingModules.ServletEnvironmentModule()
    );

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        INJECTOR.injectMembers(testInstance);
    }

}
