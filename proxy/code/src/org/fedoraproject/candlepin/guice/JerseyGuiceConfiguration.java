package org.fedoraproject.candlepin.guice;

import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.EntitlementPoolResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.UserResource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;


public class JerseyGuiceConfiguration extends GuiceServletContextListener {
    
    @Override
    protected Injector getInjector() {
        return Guice.createInjector(new ServletModule() {

                @Override
                protected void configureServlets() {
                    bind(CertificateResource.class);
                    bind(ConsumerResource.class);
                    bind(EntitlementPoolResource.class);
                    bind(EntitlementResource.class);
                    bind(OwnerResource.class);
                    bind(ProductResource.class);
                    bind(UserResource.class);
                    
                    serve("/*").with(GuiceContainer.class);
                }
        });
    }

}
