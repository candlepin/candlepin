package org.fedoraproject.candlepin.servletfilter.auth;

import static com.google.inject.name.Names.*;

import javax.servlet.Filter;

import com.google.inject.AbstractModule;

public class BasicAuthViaDbModule extends AbstractModule {
    @Override
    public void configure() {
        bind(Filter.class).annotatedWith(named(FilterConstants.BASIC_AUTH)).to(BasicAuthViaDbFilter.class);    }
}
