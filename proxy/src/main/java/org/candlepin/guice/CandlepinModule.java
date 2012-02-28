/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.guice;

import com.google.common.base.Function;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.persist.jpa.JpaPersistModule;

import org.candlepin.audit.AMQPBusEventAdapter;
import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.EventSink;
import org.candlepin.audit.EventSinkImpl;
import org.candlepin.auth.Principal;
import org.candlepin.auth.interceptor.SecurityInterceptor;
import org.candlepin.config.Config;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.CrlGenerator;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.mappers.BadRequestExceptionMapper;
import org.candlepin.exceptions.mappers.CandlepinExceptionMapper;
import org.candlepin.exceptions.mappers.DefaultOptionsMethodExceptionMapper;
import org.candlepin.exceptions.mappers.FailureExceptionMapper;
import org.candlepin.exceptions.mappers.InternalServerErrorExceptionMapper;
import org.candlepin.exceptions.mappers.JAXBMarshalExceptionMapper;
import org.candlepin.exceptions.mappers.JAXBUnmarshalExceptionMapper;
import org.candlepin.exceptions.mappers.MethodNotAllowedExceptionMapper;
import org.candlepin.exceptions.mappers.NoLogWebApplicationExceptionMapper;
import org.candlepin.exceptions.mappers.NotAcceptableExceptionMapper;
import org.candlepin.exceptions.mappers.NotFoundExceptionMapper;
import org.candlepin.exceptions.mappers.ReaderExceptionMapper;
import org.candlepin.exceptions.mappers.RuntimeExceptionMapper;
import org.candlepin.exceptions.mappers.UnauthorizedExceptionMapper;
import org.candlepin.exceptions.mappers.UnsupportedMediaTypeExceptionMapper;
import org.candlepin.exceptions.mappers.WebApplicationExceptionMapper;
import org.candlepin.exceptions.mappers.WriterExceptionMapper;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.pinsetter.core.GuiceJobFactory;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.pinsetter.tasks.JobCleaner;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.impl.BouncyCastlePKIReader;
import org.candlepin.pki.impl.BouncyCastlePKIUtility;
import org.candlepin.policy.Enforcer;
import org.candlepin.policy.EnforcerDispatcher;
import org.candlepin.policy.PoolRules;
import org.candlepin.policy.js.JsRules;
import org.candlepin.policy.js.JsRulesProvider;
import org.candlepin.policy.js.pool.JsPoolRules;
import org.candlepin.resource.ActivationKeyResource;
import org.candlepin.resource.AdminResource;
import org.candlepin.resource.AtomFeedResource;
import org.candlepin.resource.CertificateSerialResource;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.ConsumerTypeResource;
import org.candlepin.resource.ContentResource;
import org.candlepin.resource.CrlResource;
import org.candlepin.resource.EntitlementResource;
import org.candlepin.resource.EnvironmentResource;
import org.candlepin.resource.EventResource;
import org.candlepin.resource.HypervisorResource;
import org.candlepin.resource.JobResource;
import org.candlepin.resource.MigrationResource;
import org.candlepin.resource.OwnerResource;
import org.candlepin.resource.PoolResource;
import org.candlepin.resource.ProductResource;
import org.candlepin.resource.RoleResource;
import org.candlepin.resource.RootResource;
import org.candlepin.resource.RulesResource;
import org.candlepin.resource.StatisticResource;
import org.candlepin.resource.StatusResource;
import org.candlepin.resource.SubscriptionResource;
import org.candlepin.resource.UserResource;
import org.candlepin.resteasy.JsonProvider;
import org.candlepin.resteasy.interceptor.AuthInterceptor;
import org.candlepin.resteasy.interceptor.PinsetterAsyncInterceptor;
import org.candlepin.resteasy.interceptor.VersionPostInterceptor;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.sync.ConsumerExporter;
import org.candlepin.sync.ConsumerTypeExporter;
import org.candlepin.sync.EntitlementCertExporter;
import org.candlepin.sync.Exporter;
import org.candlepin.sync.MetaExporter;
import org.candlepin.sync.RulesExporter;
import org.candlepin.util.DateSource;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.ExpiryDateFunction;
import org.candlepin.util.X509ExtensionUtil;
import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

/**
 * CandlepinProductionConfiguration
 */
public class CandlepinModule extends AbstractModule {

    @Override
    public void configure() {

        install(new JpaPersistModule("default").properties(
            new Config().jpaConfiguration()));
        bind(JPAInitializer.class).asEagerSingleton();

        bind(Config.class).asEagerSingleton();
        bind(PKIUtility.class).to(BouncyCastlePKIUtility.class).asEagerSingleton();
        bind(PKIReader.class).to(BouncyCastlePKIReader.class).asEagerSingleton();
        bind(X509ExtensionUtil.class);
        bind(CrlGenerator.class);
        bind(ConsumerResource.class);
        bind(HypervisorResource.class);
        bind(ConsumerTypeResource.class);
        bind(ContentResource.class);
        bind(AtomFeedResource.class);
        bind(EventResource.class);
        bind(PoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(RoleResource.class);
        bind(RootResource.class);
        bind(ProductResource.class);
        bind(MigrationResource.class);
        bind(SubscriptionResource.class);
        bind(ActivationKeyResource.class);
        bind(CertificateSerialResource.class);
        bind(CrlResource.class);
        bind(JobResource.class);
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(EnforcerDispatcher.class);
        bind(PoolManager.class).to(CandlepinPoolManager.class);
        bind(PoolRules.class).to(JsPoolRules.class);
        bind(Entitler.class);
        bind(RulesResource.class);
        bind(AdminResource.class);
        bind(StatusResource.class);
        bind(EnvironmentResource.class);
        bind(StatisticResource.class);
        bind(UnsupportedMediaTypeExceptionMapper.class);
        bind(UnauthorizedExceptionMapper.class);
        bind(NotFoundExceptionMapper.class);
        bind(NotAcceptableExceptionMapper.class);
        bind(NoLogWebApplicationExceptionMapper.class);
        bind(MethodNotAllowedExceptionMapper.class);
        bind(InternalServerErrorExceptionMapper.class);
        bind(DefaultOptionsMethodExceptionMapper.class);
        bind(BadRequestExceptionMapper.class);
        bind(WebApplicationExceptionMapper.class);
        bind(FailureExceptionMapper.class);
        bind(ReaderExceptionMapper.class);
        bind(WriterExceptionMapper.class);
        bind(CandlepinExceptionMapper.class);
        bind(RuntimeExceptionMapper.class);
        bind(JAXBUnmarshalExceptionMapper.class);
        bind(JAXBMarshalExceptionMapper.class);
        bind(Principal.class).toProvider(PrincipalProvider.class);
        bind(JsRulesProvider.class).asEagerSingleton();
        bind(JsRules.class).toProvider(JsRulesProvider.class);
        bind(UserResource.class);
        bind(UniqueIdGenerator.class).to(DefaultUniqueIdGenerator.class);

        bind(I18n.class).toProvider(I18nProvider.class);
        bind(AuthInterceptor.class);
        bind(PinsetterAsyncInterceptor.class);
        bind(VersionPostInterceptor.class);
        bind(JsonProvider.class).asEagerSingleton();
        bind(EventSink.class).to(EventSinkImpl.class);
        bind(JobFactory.class).to(GuiceJobFactory.class);
        bind(JobListener.class).to(PinsetterJobListener.class);
        bind(PinsetterKernel.class);
        bind(CertificateRevocationListTask.class);
        bind(JobCleaner.class);

        bind(Exporter.class).asEagerSingleton();
        bind(MetaExporter.class);
        bind(ConsumerTypeExporter.class);
        bind(ConsumerExporter.class);
        bind(RulesExporter.class);
        bind(EntitlementCertExporter.class);

        // Async Jobs
        bind(RefreshPoolsJob.class);
        bind(EntitlerJob.class);

        //UeberCerts
        bind(UeberCertificateGenerator.class);

        // The order in which interceptors are bound is important!
        // We need role enforcement to be executed before access control
        Matcher resourcePkgMatcher = Matchers.inPackage(Package.getPackage(
            "org.candlepin.resource"));
        SecurityInterceptor securityEnforcer = new SecurityInterceptor();
        requestInjection(securityEnforcer);
        bindInterceptor(resourcePkgMatcher,
                Matchers.any(), securityEnforcer);

        // AMQP stuff:
        bind(Function.class).annotatedWith(Names.named("abc"))
                .to(AMQPBusEventAdapter.class).in(Singleton.class);
      // for lazy loading:
        bind(AMQPBusPublisher.class).toProvider(AMQPBusPubProvider.class)
                .in(Singleton.class);

        // flexible end date for identity certificates
        bind(Function.class).annotatedWith(Names.named("endDateGenerator"))
            .to(ExpiryDateFunction.class).in(Singleton.class);
    }
}
