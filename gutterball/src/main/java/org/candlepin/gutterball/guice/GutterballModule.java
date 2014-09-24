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
import org.candlepin.common.guice.JPAInitializer;
import org.candlepin.gutterball.config.JPAConfigurationParser;
import org.candlepin.gutterball.curator.jpa.ComplianceSnapshotCurator;
import org.candlepin.gutterball.curator.jpa.ConsumerStateCurator;
import org.candlepin.gutterball.eventhandler.EventHandler;
import org.candlepin.gutterball.eventhandler.EventManager;
import org.candlepin.gutterball.eventhandler.HandlerTarget;
import org.candlepin.gutterball.receiver.EventReceiver;
import org.candlepin.gutterball.report.ConsumerStatusReport;
import org.candlepin.gutterball.report.ConsumerTrendReport;
import org.candlepin.gutterball.report.Report;
import org.candlepin.gutterball.report.ReportFactory;
import org.candlepin.gutterball.resource.ReportsResource;
import org.candlepin.gutterball.resource.StatusResource;
import org.candlepin.gutterball.resteasy.JsonProvider;
import org.candlepin.gutterball.util.EventHandlerLoader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.servlet.ServletScopes;

import org.xnap.commons.i18n.I18n;


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
        bind(ComplianceSnapshotCurator.class);
        bind(ConsumerStateCurator.class);

        // ObjectMapper instances are quite expensive to create, bind a single instance.
        ObjectMapper mapper = new ObjectMapper();
        // Since JSON will be coming from candlepin and the objects may have different schemas,
        // don't fail on unknown properties.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        bind(ObjectMapper.class).toInstance(mapper);

        configureEventHandlers();
        bind(EventManager.class).asEagerSingleton();
        configureEventReciever();

        // Map our report classes so that they can be picked up by the ReportFactory.
        Multibinder<Report> reports = Multibinder.newSetBinder(binder(), Report.class);
        reports.addBinding().to(ConsumerStatusReport.class);
        reports.addBinding().to(ConsumerTrendReport.class);
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
    }

    protected void configureEventReciever() {
        bind(EventReceiver.class).asEagerSingleton();
    }

    protected void bindI18n() {
        bind(I18n.class).toProvider(I18nProvider.class).in(ServletScopes.REQUEST);
    }

    protected void configureJPA() {
        JPAConfigurationParser parser = new JPAConfigurationParser(this.config);
        install(new JpaPersistModule("default").properties(parser.parseConfig()));
        bind(JPAInitializer.class).asEagerSingleton();
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
}
