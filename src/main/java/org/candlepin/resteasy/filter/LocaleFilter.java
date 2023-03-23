package org.candlepin.resteasy.filter;

import io.vertx.ext.web.LanguageHeader;
import io.vertx.ext.web.RoutingContext;

import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

@Provider
@PreMatching
public class LocaleFilter implements ContainerRequestFilter {

    private final DefaultLocaleContext ctx;
    private final RoutingContext route;

    @Inject
    public LocaleFilter(DefaultLocaleContext ctx, @Context RoutingContext route) {
        this.ctx = Objects.requireNonNull(ctx);
        this.route = Objects.requireNonNull(route);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        LanguageHeader language = this.route.preferredLanguage();
        if (language == null) {
            ctx.setCurrentUser(Locale.getDefault());
        }
        else {
            Locale locale = new Locale(language.value());
            ctx.setCurrentUser(locale);
        }
    }
}
