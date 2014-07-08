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
package org.canadianTenPin.guice;

import java.util.Properties;

import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import org.canadianTenPin.audit.AMQPBusPublisher;
import org.canadianTenPin.audit.EventSink;
import org.canadianTenPin.audit.EventSinkImpl;
import org.canadianTenPin.auth.Principal;
import org.canadianTenPin.config.Config;
import org.canadianTenPin.controller.CanadianTenPinPoolManager;
import org.canadianTenPin.controller.CrlGenerator;
import org.canadianTenPin.controller.Entitler;
import org.canadianTenPin.controller.PoolManager;
import org.canadianTenPin.exceptions.mappers.BadRequestExceptionMapper;
import org.canadianTenPin.exceptions.mappers.CanadianTenPinExceptionMapper;
import org.canadianTenPin.exceptions.mappers.DefaultOptionsMethodExceptionMapper;
import org.canadianTenPin.exceptions.mappers.FailureExceptionMapper;
import org.canadianTenPin.exceptions.mappers.InternalServerErrorExceptionMapper;
import org.canadianTenPin.exceptions.mappers.JAXBMarshalExceptionMapper;
import org.canadianTenPin.exceptions.mappers.JAXBUnmarshalExceptionMapper;
import org.canadianTenPin.exceptions.mappers.MethodNotAllowedExceptionMapper;
import org.canadianTenPin.exceptions.mappers.NoLogWebApplicationExceptionMapper;
import org.canadianTenPin.exceptions.mappers.NotAcceptableExceptionMapper;
import org.canadianTenPin.exceptions.mappers.NotFoundExceptionMapper;
import org.canadianTenPin.exceptions.mappers.ReaderExceptionMapper;
import org.canadianTenPin.exceptions.mappers.RollbackExceptionMapper;
import org.canadianTenPin.exceptions.mappers.RuntimeExceptionMapper;
import org.canadianTenPin.exceptions.mappers.UnauthorizedExceptionMapper;
import org.canadianTenPin.exceptions.mappers.UnsupportedMediaTypeExceptionMapper;
import org.canadianTenPin.exceptions.mappers.ValidationExceptionMapper;
import org.canadianTenPin.exceptions.mappers.WebApplicationExceptionMapper;
import org.canadianTenPin.exceptions.mappers.WriterExceptionMapper;
import org.canadianTenPin.hibernate.CanadianTenPinMessageInterpolator;
import org.canadianTenPin.model.UeberCertificateGenerator;
import org.canadianTenPin.pinsetter.core.GuiceJobFactory;
import org.canadianTenPin.pinsetter.core.PinsetterJobListener;
import org.canadianTenPin.pinsetter.core.PinsetterKernel;
import org.canadianTenPin.pinsetter.tasks.CertificateRevocationListTask;
import org.canadianTenPin.pinsetter.tasks.EntitlerJob;
import org.canadianTenPin.pinsetter.tasks.ExportCleaner;
import org.canadianTenPin.pinsetter.tasks.JobCleaner;
import org.canadianTenPin.pinsetter.tasks.RefreshPoolsJob;
import org.canadianTenPin.pinsetter.tasks.SweepBarJob;
import org.canadianTenPin.pinsetter.tasks.UnpauseJob;
import org.canadianTenPin.pki.PKIReader;
import org.canadianTenPin.pki.PKIUtility;
import org.canadianTenPin.pki.impl.BouncyCastlePKIReader;
import org.canadianTenPin.pki.impl.BouncyCastlePKIUtility;
import org.canadianTenPin.policy.criteria.CriteriaRules;
import org.canadianTenPin.policy.js.JsRunner;
import org.canadianTenPin.policy.js.JsRunnerProvider;
import org.canadianTenPin.policy.js.entitlement.Enforcer;
import org.canadianTenPin.policy.js.entitlement.EntitlementRules;
import org.canadianTenPin.policy.js.pool.PoolRules;
import org.canadianTenPin.resource.ActivationKeyContentOverrideResource;
import org.canadianTenPin.resource.ActivationKeyResource;
import org.canadianTenPin.resource.AdminResource;
import org.canadianTenPin.resource.AtomFeedResource;
import org.canadianTenPin.resource.CdnResource;
import org.canadianTenPin.resource.CertificateSerialResource;
import org.canadianTenPin.resource.ConsumerContentOverrideResource;
import org.canadianTenPin.resource.ConsumerResource;
import org.canadianTenPin.resource.ConsumerTypeResource;
import org.canadianTenPin.resource.ContentResource;
import org.canadianTenPin.resource.CrlResource;
import org.canadianTenPin.resource.DeletedConsumerResource;
import org.canadianTenPin.resource.DistributorVersionResource;
import org.canadianTenPin.resource.EntitlementResource;
import org.canadianTenPin.resource.EnvironmentResource;
import org.canadianTenPin.resource.EventResource;
import org.canadianTenPin.resource.GuestIdResource;
import org.canadianTenPin.resource.HypervisorResource;
import org.canadianTenPin.resource.JobResource;
import org.canadianTenPin.resource.MigrationResource;
import org.canadianTenPin.resource.OwnerResource;
import org.canadianTenPin.resource.PoolResource;
import org.canadianTenPin.resource.ProductResource;
import org.canadianTenPin.resource.RoleResource;
import org.canadianTenPin.resource.RootResource;
import org.canadianTenPin.resource.RulesResource;
import org.canadianTenPin.resource.StatisticResource;
import org.canadianTenPin.resource.StatusResource;
import org.canadianTenPin.resource.SubscriptionResource;
import org.canadianTenPin.resource.UserResource;
import org.canadianTenPin.resteasy.JsonProvider;
import org.canadianTenPin.resteasy.interceptor.AuthInterceptor;
import org.canadianTenPin.resteasy.interceptor.DynamicFilterInterceptor;
import org.canadianTenPin.resteasy.interceptor.LinkHeaderPostInterceptor;
import org.canadianTenPin.resteasy.interceptor.PageRequestInterceptor;
import org.canadianTenPin.resteasy.interceptor.PinsetterAsyncInterceptor;
import org.canadianTenPin.resteasy.interceptor.VersionPostInterceptor;
import org.canadianTenPin.service.UniqueIdGenerator;
import org.canadianTenPin.service.impl.DefaultUniqueIdGenerator;
import org.canadianTenPin.sync.ConsumerExporter;
import org.canadianTenPin.sync.ConsumerTypeExporter;
import org.canadianTenPin.sync.EntitlementCertExporter;
import org.canadianTenPin.sync.Exporter;
import org.canadianTenPin.sync.MetaExporter;
import org.canadianTenPin.sync.RulesExporter;
import org.canadianTenPin.util.DateSource;
import org.canadianTenPin.util.DateSourceImpl;
import org.canadianTenPin.util.ExpiryDateFunction;
import org.canadianTenPin.util.X509ExtensionUtil;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

import com.google.common.base.Function;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.persist.jpa.JpaPersistModule;

/**
 * CanadianTenPinProductionConfiguration
 */
public class CanadianTenPinModule extends AbstractModule {

    @Override
    public void configure() {
        // Bindings for our custom scope
        CanadianTenPinSingletonScope singletonScope = new CanadianTenPinSingletonScope();
        bindScope(CanadianTenPinSingletonScoped.class, singletonScope);
        bind(CanadianTenPinSingletonScope.class).toInstance(singletonScope);

        bind(I18n.class).toProvider(I18nProvider.class);
        bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
        bind(MessageInterpolator.class).to(CanadianTenPinMessageInterpolator.class);

        Config config = new Config();
        bind(Config.class).asEagerSingleton();
        install(new JpaPersistModule("default").properties(config
            .jpaConfiguration(config)));
        bind(JPAInitializer.class).asEagerSingleton();

        bind(PKIUtility.class).to(BouncyCastlePKIUtility.class)
            .asEagerSingleton();
        bind(PKIReader.class).to(BouncyCastlePKIReader.class)
            .asEagerSingleton();
        bind(X509ExtensionUtil.class);
        bind(CrlGenerator.class);
        bind(ConsumerResource.class);
        bind(ConsumerContentOverrideResource.class);
        bind(ActivationKeyContentOverrideResource.class);
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
        bind(Enforcer.class).to(EntitlementRules.class);
        bind(PoolManager.class).to(CanadianTenPinPoolManager.class);
        bind(PoolRules.class);
        bind(CriteriaRules.class);
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
        bind(RollbackExceptionMapper.class);
        bind(ValidationExceptionMapper.class);
        bind(WebApplicationExceptionMapper.class);
        bind(FailureExceptionMapper.class);
        bind(ReaderExceptionMapper.class);
        bind(WriterExceptionMapper.class);
        bind(CanadianTenPinExceptionMapper.class);
        bind(RuntimeExceptionMapper.class);
        bind(JAXBUnmarshalExceptionMapper.class);
        bind(JAXBMarshalExceptionMapper.class);
        bind(Principal.class).toProvider(PrincipalProvider.class);
        bind(JsRunnerProvider.class).asEagerSingleton();
        bind(JsRunner.class).toProvider(JsRunnerProvider.class);
        bind(UserResource.class);
        bind(UniqueIdGenerator.class).to(DefaultUniqueIdGenerator.class);
        bind(DistributorVersionResource.class);
        bind(DeletedConsumerResource.class);
        bind(CdnResource.class);
        bind(GuestIdResource.class);

        this.configureInterceptors();
        bind(JsonProvider.class);
        bind(EventSink.class).to(EventSinkImpl.class);
        this.configurePinsetter();

        this.configureExporter();

        // Async Jobs
        bind(RefreshPoolsJob.class);
        bind(EntitlerJob.class);

        // UeberCerts
        bind(UeberCertificateGenerator.class);

        // flexible end date for identity certificates
        bind(Function.class).annotatedWith(Names.named("endDateGenerator"))
            .to(ExpiryDateFunction.class).in(Singleton.class);

        this.configureAmqp();
    }

    @Provides @Named("ValidationProperties")
    protected Properties getValidationProperties() {
        return new Properties();
    }

    @Provides
    protected ValidatorFactory getValidationFactory(Provider<MessageInterpolator> interpolatorProvider) {
        HibernateValidatorConfiguration configure =
            Validation.byProvider(HibernateValidator.class).configure();

        configure.messageInterpolator(interpolatorProvider.get());
        return configure.buildValidatorFactory();
    }

    private void configureInterceptors() {
        bind(AuthInterceptor.class);
        bind(PageRequestInterceptor.class);
        bind(PinsetterAsyncInterceptor.class);
        bind(VersionPostInterceptor.class);
        bind(LinkHeaderPostInterceptor.class);
        bind(DynamicFilterInterceptor.class);
    }

    private void configurePinsetter() {
        bind(JobFactory.class).to(GuiceJobFactory.class);
        bind(JobListener.class).to(PinsetterJobListener.class);
        bind(PinsetterKernel.class);
        bind(CertificateRevocationListTask.class);
        bind(JobCleaner.class);
        bind(ExportCleaner.class);
        bind(UnpauseJob.class);
        bind(SweepBarJob.class);
    }

    private void configureExporter() {
        bind(Exporter.class);
        bind(MetaExporter.class);
        bind(ConsumerTypeExporter.class);
        bind(ConsumerExporter.class);
        bind(RulesExporter.class);
        bind(EntitlementCertExporter.class);
    }

    private void configureAmqp() {
        // for lazy loading:
        bind(AMQPBusPublisher.class).toProvider(AMQPBusPubProvider.class)
                .in(Singleton.class);
    }
}
