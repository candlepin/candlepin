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
package org.candlepin.guice;

import java.util.Properties;

import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import org.candlepin.audit.EventSink;
import org.candlepin.audit.EventSinkImpl;
import org.candlepin.auth.Principal;
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
import org.candlepin.exceptions.mappers.RollbackExceptionMapper;
import org.candlepin.exceptions.mappers.RuntimeExceptionMapper;
import org.candlepin.exceptions.mappers.UnauthorizedExceptionMapper;
import org.candlepin.exceptions.mappers.UnsupportedMediaTypeExceptionMapper;
import org.candlepin.exceptions.mappers.ValidationExceptionMapper;
import org.candlepin.exceptions.mappers.WebApplicationExceptionMapper;
import org.candlepin.exceptions.mappers.WriterExceptionMapper;
import org.candlepin.hibernate.CandlepinMessageInterpolator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.pinsetter.core.GuiceJobFactory;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.pinsetter.tasks.ExportCleaner;
import org.candlepin.pinsetter.tasks.JobCleaner;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.pinsetter.tasks.SweepBarJob;
import org.candlepin.pinsetter.tasks.UnpauseJob;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.impl.BouncyCastlePKIReader;
import org.candlepin.pki.impl.BouncyCastlePKIUtility;
import org.candlepin.policy.criteria.CriteriaRules;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.resource.ActivationKeyContentOverrideResource;
import org.candlepin.resource.ActivationKeyResource;
import org.candlepin.resource.AdminResource;
import org.candlepin.resource.AtomFeedResource;
import org.candlepin.resource.CdnResource;
import org.candlepin.resource.CertificateSerialResource;
import org.candlepin.resource.ConsumerContentOverrideResource;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.ConsumerTypeResource;
import org.candlepin.resource.ContentResource;
import org.candlepin.resource.CrlResource;
import org.candlepin.resource.DeletedConsumerResource;
import org.candlepin.resource.DistributorVersionResource;
import org.candlepin.resource.EntitlementResource;
import org.candlepin.resource.EnvironmentResource;
import org.candlepin.resource.EventResource;
import org.candlepin.resource.GuestIdResource;
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
import org.candlepin.resteasy.interceptor.DynamicFilterInterceptor;
import org.candlepin.resteasy.interceptor.LinkHeaderPostInterceptor;
import org.candlepin.resteasy.interceptor.PageRequestInterceptor;
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
 * CandlepinProductionConfiguration
 */
public class CandlepinModule extends AbstractModule {

    @Override
    public void configure() {
        // Bindings for our custom scope
        CandlepinSingletonScope singletonScope = new CandlepinSingletonScope();
        bindScope(CandlepinSingletonScoped.class, singletonScope);
        bind(CandlepinSingletonScope.class).toInstance(singletonScope);

        bind(I18n.class).toProvider(I18nProvider.class);
        bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
        bind(MessageInterpolator.class).to(CandlepinMessageInterpolator.class);

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
        bind(PoolManager.class).to(CandlepinPoolManager.class);
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
        bind(CandlepinExceptionMapper.class);
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
}
