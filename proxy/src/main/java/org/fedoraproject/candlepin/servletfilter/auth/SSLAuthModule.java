package org.fedoraproject.candlepin.servletfilter.auth;

import static com.google.inject.name.Names.*;

import javax.servlet.Filter;

import com.google.inject.AbstractModule;

public class SSLAuthModule extends AbstractModule {
    @Override
    public void configure() {
        bind(Filter.class)
            .annotatedWith(named(FilterConstants.SSL_AUTH))
            .to(SSLAuthFilter.class)
            .asEagerSingleton();
    }
}
