/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.gutterball.guice;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationPrefixes;
import org.candlepin.common.exceptions.mappers.BadRequestExceptionMapper;
import org.candlepin.common.exceptions.mappers.CandlepinExceptionMapper;
import org.candlepin.common.exceptions.mappers.DefaultOptionsMethodExceptionMapper;
import org.candlepin.common.exceptions.mappers.FailureExceptionMapper;
import org.candlepin.common.exceptions.mappers.InternalServerErrorExceptionMapper;
import org.candlepin.common.exceptions.mappers.JAXBMarshalExceptionMapper;
import org.candlepin.common.exceptions.mappers.JAXBUnmarshalExceptionMapper;
import org.candlepin.common.exceptions.mappers.MethodNotAllowedExceptionMapper;
import org.candlepin.common.exceptions.mappers.NoLogWebApplicationExceptionMapper;
import org.candlepin.common.exceptions.mappers.NotAcceptableExceptionMapper;
import org.candlepin.common.exceptions.mappers.NotFoundExceptionMapper;
import org.candlepin.common.exceptions.mappers.ReaderExceptionMapper;
import org.candlepin.common.exceptions.mappers.RollbackExceptionMapper;
import org.candlepin.common.exceptions.mappers.RuntimeExceptionMapper;
import org.candlepin.common.exceptions.mappers.UnauthorizedExceptionMapper;
import org.candlepin.common.exceptions.mappers.UnsupportedMediaTypeExceptionMapper;
import org.candlepin.common.exceptions.mappers.ValidationExceptionMapper;
import org.candlepin.common.exceptions.mappers.WebApplicationExceptionMapper;
import org.candlepin.common.exceptions.mappers.WriterExceptionMapper;
import org.candlepin.common.resteasy.interceptor.DynamicFilterInterceptor;
import org.candlepin.common.resteasy.interceptor.LinkHeaderPostInterceptor;
import org.candlepin.common.resteasy.interceptor.PageRequestInterceptor;
import org.candlepin.common.validation.CandlepinMessageInterpolator;
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.curator.ConsumerStateCurator;
import org.candlepin.gutterball.eventhandler.EventHandler;
import org.candlepin.gutterball.eventhandler.EventManager;
import org.candlepin.gutterball.eventhandler.HandlerTarget;
import org.candlepin.gutterball.jackson.GutterballObjectMapper;
import org.candlepin.gutterball.receiver.EventReceiver;
import org.candlepin.gutterball.report.ConsumerStatusReport;
import org.candlepin.gutterball.report.ConsumerTrendReport;
import org.candlepin.gutterball.report.StatusTrendReport;
import org.candlepin.gutterball.report.Report;
import org.candlepin.gutterball.report.ReportFactory;
import org.candlepin.gutterball.resource.ReportsResource;
import org.candlepin.gutterball.resource.StatusResource;
import org.candlepin.gutterball.resteasy.JsonProvider;
import org.candlepin.gutterball.resteasy.interceptor.OAuthInterceptor;
import org.candlepin.gutterball.util.EventHandlerLoader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.servlet.ServletScopes;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.xnap.commons.i18n.I18n;

import java.util.Properties;

import javax.inject.Provider;
import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;


/**
 * GutterballModule configures the modules used by Gutterball using Guice.
 */
public class GutterballModule extends AbstractModule {

    protected Configuration config;

    public GutterballModule(Configuration config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configure() {
        // See JavaDoc on I18nProvider for more information of RequestScope
        bindI18n();
        bind(JsonProvider.class);

        configureJPA();

        bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
        bind(MessageInterpolator.class).to(CandlepinMessageInterpolator.class);

        bind(ComplianceSnapshotCurator.class);
        bind(ConsumerStateCurator.class);

        bind(ObjectMapper.class).toInstance(new GutterballObjectMapper());

        configureEventHandlers();
        bind(EventManager.class).asEagerSingleton();
        configureEventReciever();

        // Map our report classes so that they can be picked up by the ReportFactory.
        Multibinder<Report> reports = Multibinder.newSetBinder(binder(), Report.class);
        reports.addBinding().to(ConsumerStatusReport.class);
        reports.addBinding().to(ConsumerTrendReport.class);
        reports.addBinding().to(StatusTrendReport.class);
        bind(ReportFactory.class);

        // RestEasy API resources
        bind(StatusResource.class);
        bind(ReportsResource.class);

        bind(UnsupportedMediaTypeExceptionMapper.class);
        bind(UnauthorizedExceptionMapper.class);
        bind(NotFoundExceptionMapper.class);
        bind(NotAcceptableExceptionMapper.class);
        bind(NoLogWebApplicationExceptionMapper.class);
        bind(MethodNotAllowedExceptionMapper.class);
        bind(InternalServerErrorExceptionMapper.class);
        bind(DefaultOptionsMethodExceptionMapper.class);
        bind(BadRequestExceptionMapper.class);
        bind(RollbackExceptionMapper.class);
        bind(ValidationExceptionMapper.class);
        bind(WebApplicationExceptionMapper.class);
        bind(FailureExceptionMapper.class);
        bind(ReaderExceptionMapper.class);
        bind(WriterExceptionMapper.class);
        bind(CandlepinExceptionMapper.class);
        bind(RuntimeExceptionMapper.class);
        bind(JAXBUnmarshalExceptionMapper.class);
        bind(JAXBMarshalExceptionMapper.class);

        // Output filter interceptors
        bind(DynamicFilterInterceptor.class);
        bind(PageRequestInterceptor.class);
        bind(LinkHeaderPostInterceptor.class);

        this.configureOAuth();
    }

    protected void configureEventReciever() {
        bind(EventReceiver.class).asEagerSingleton();
    }

    protected void bindI18n() {
        bind(I18n.class).toProvider(I18nProvider.class).in(ServletScopes.REQUEST);
    }

    protected void configureJPA() {
        Configuration jpaConfig = config.strippedSubset(ConfigurationPrefixes.JPA_CONFIG_PREFIX);
        install(new JpaPersistModule("default").properties(jpaConfig.toProperties()));
    }

    protected void configureOAuth() {
        if (this.config.getBoolean(ConfigProperties.OAUTH_AUTHENTICATION, false)) {
            this.bind(OAuthInterceptor.class);
        }
    }

    protected void configureEventHandlers() {
        MapBinder<String, EventHandler> eventBinder =
                MapBinder.newMapBinder(binder(), String.class, EventHandler.class);
        for (Class<? extends EventHandler> clazz : EventHandlerLoader.getClasses()) {
            if (clazz.isAnnotationPresent(HandlerTarget.class)) {
                HandlerTarget targetAnnotation = clazz.getAnnotation(HandlerTarget.class);
                eventBinder.addBinding(targetAnnotation.value()).to(clazz).asEagerSingleton();
            }
        }
    }

    @Provides @Named("ValidationProperties")
    protected Properties getValidationProperties() {
        return new Properties();
    }

    @Provides
    protected ValidatorFactory getValidationFactory(
        Provider<MessageInterpolator> interpolatorProvider) {
        HibernateValidatorConfiguration configure =
            Validation.byProvider(HibernateValidator.class).configure();

        configure.messageInterpolator(interpolatorProvider.get());
        return configure.buildValidatorFactory();
    }
}
