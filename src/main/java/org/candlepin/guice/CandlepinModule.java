/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.async.JobManager;
import org.candlepin.async.JobMessageDispatcher;
import org.candlepin.async.JobMessageReceiver;
import org.candlepin.async.tasks.ActiveEntitlementJob;
import org.candlepin.async.tasks.CertificateCleanupJob;
import org.candlepin.async.tasks.CloudAccountOrgSetupJob;
import org.candlepin.async.tasks.ConsumerMigrationJob;
import org.candlepin.async.tasks.EntitleByProductsJob;
import org.candlepin.async.tasks.EntitlerJob;
import org.candlepin.async.tasks.ExpiredPoolsCleanupJob;
import org.candlepin.async.tasks.ExportJob;
import org.candlepin.async.tasks.HealEntireOrgJob;
import org.candlepin.async.tasks.HypervisorHeartbeatUpdateJob;
import org.candlepin.async.tasks.HypervisorUpdateJob;
import org.candlepin.async.tasks.ImportJob;
import org.candlepin.async.tasks.ImportRecordCleanerJob;
import org.candlepin.async.tasks.InactiveConsumerCleanerJob;
import org.candlepin.async.tasks.JobCleaner;
import org.candlepin.async.tasks.ManifestCleanerJob;
import org.candlepin.async.tasks.RefreshPoolsForProductJob;
import org.candlepin.async.tasks.RefreshPoolsJob;
import org.candlepin.async.tasks.RegenEnvEntitlementCertsJob;
import org.candlepin.async.tasks.RegenProductEntitlementCertsJob;
import org.candlepin.async.tasks.RevokeEntitlementsJob;
import org.candlepin.async.tasks.UndoImportsJob;
import org.candlepin.async.tasks.UnmappedGuestEntitlementCleanerJob;
import org.candlepin.audit.ArtemisMessageSource;
import org.candlepin.audit.ArtemisMessageSourceReceiverFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.audit.EventSinkImpl;
import org.candlepin.audit.MessageSource;
import org.candlepin.audit.MessageSourceReceiverFactory;
import org.candlepin.audit.NoopEventSinkImpl;
import org.candlepin.auth.Principal;
import org.candlepin.bind.BindChainFactory;
import org.candlepin.bind.BindContextFactory;
import org.candlepin.bind.PoolOpProcessor;
import org.candlepin.bind.PreEntitlementRulesCheckOpFactory;
import org.candlepin.cache.JCacheManagerProvider;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationPrefixes;
import org.candlepin.controller.ConsumerMigration;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.controller.ScheduledExecutorServiceProvider;
import org.candlepin.controller.SuspendModeTransitioner;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.exceptions.mappers.BadRequestExceptionMapper;
import org.candlepin.exceptions.mappers.CandlepinExceptionMapper;
import org.candlepin.exceptions.mappers.DefaultOptionsMethodExceptionMapper;
import org.candlepin.exceptions.mappers.FailureExceptionMapper;
import org.candlepin.exceptions.mappers.InternalServerErrorExceptionMapper;
import org.candlepin.exceptions.mappers.JAXBMarshalExceptionMapper;
import org.candlepin.exceptions.mappers.JAXBUnmarshalExceptionMapper;
import org.candlepin.exceptions.mappers.NoLogWebApplicationExceptionMapper;
import org.candlepin.exceptions.mappers.NotAcceptableExceptionMapper;
import org.candlepin.exceptions.mappers.NotAllowedExceptionMapper;
import org.candlepin.exceptions.mappers.NotAuthorizedExceptionMapper;
import org.candlepin.exceptions.mappers.NotFoundExceptionMapper;
import org.candlepin.exceptions.mappers.NotSupportedExceptionMapper;
import org.candlepin.exceptions.mappers.ReaderExceptionMapper;
import org.candlepin.exceptions.mappers.RollbackExceptionMapper;
import org.candlepin.exceptions.mappers.RuntimeExceptionMapper;
import org.candlepin.exceptions.mappers.ServiceExceptionMapper;
import org.candlepin.exceptions.mappers.ValidationExceptionMapper;
import org.candlepin.exceptions.mappers.WebApplicationExceptionMapper;
import org.candlepin.exceptions.mappers.WriterExceptionMapper;
import org.candlepin.messaging.CPMContextListener;
import org.candlepin.messaging.CPMSessionFactory;
import org.candlepin.messaging.impl.artemis.ArtemisContextListener;
import org.candlepin.messaging.impl.artemis.ArtemisSessionFactory;
import org.candlepin.messaging.impl.artemis.ArtemisUtil;
import org.candlepin.messaging.impl.noop.NoopContextListener;
import org.candlepin.messaging.impl.noop.NoopSessionFactory;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.certs.AnonymousCertificateGenerator;
import org.candlepin.pki.certs.EntitlementCertificateGenerator;
import org.candlepin.pki.certs.EntitlementPayloadGenerator;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.pki.certs.ProductCertificateGenerator;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.pki.certs.UeberCertificateGenerator;
import org.candlepin.pki.certs.X509CertificateBuilder;
import org.candlepin.pki.certs.X509CertificateBuilderProvider;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastlePrivateKeyReader;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.resource.ActivationKeyResource;
import org.candlepin.resource.AdminResource;
import org.candlepin.resource.CdnResource;
import org.candlepin.resource.CertificateSerialResource;
import org.candlepin.resource.CloudRegistrationResource;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.ConsumerTypeResource;
import org.candlepin.resource.ContentResource;
import org.candlepin.resource.CrlResource;
import org.candlepin.resource.DeletedConsumerResource;
import org.candlepin.resource.DistributorVersionResource;
import org.candlepin.resource.EntitlementResource;
import org.candlepin.resource.EnvironmentResource;
import org.candlepin.resource.GuestIdResource;
import org.candlepin.resource.HypervisorResource;
import org.candlepin.resource.JobResource;
import org.candlepin.resource.OwnerContentResource;
import org.candlepin.resource.OwnerProductResource;
import org.candlepin.resource.OwnerResource;
import org.candlepin.resource.PoolResource;
import org.candlepin.resource.ProductResource;
import org.candlepin.resource.RoleResource;
import org.candlepin.resource.RootResource;
import org.candlepin.resource.RulesResource;
import org.candlepin.resource.StatusResource;
import org.candlepin.resource.SubscriptionResource;
import org.candlepin.resource.UserResource;
import org.candlepin.resource.util.AWSProviderFactParser;
import org.candlepin.resource.util.AzureProviderFactParser;
import org.candlepin.resource.util.CloudProviderFactParser;
import org.candlepin.resource.util.GCPProviderFactParser;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.JsonProvider;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.resteasy.converter.OffsetDateTimeParamConverterProvider;
import org.candlepin.resteasy.filter.AuthenticationFilter;
import org.candlepin.resteasy.filter.AuthorizationFeature;
import org.candlepin.resteasy.filter.CandlepinSuspendModeFilter;
import org.candlepin.resteasy.filter.ConsumerCheckInFilter;
import org.candlepin.resteasy.filter.DynamicJsonFilter;
import org.candlepin.resteasy.filter.LinkHeaderResponseFilter;
import org.candlepin.resteasy.filter.PageRequestFilter;
import org.candlepin.resteasy.filter.SecurityHoleAuthorizationFilter;
import org.candlepin.resteasy.filter.StoreFactory;
import org.candlepin.resteasy.filter.SuperAdminAuthorizationFilter;
import org.candlepin.resteasy.filter.VerifyAuthorizationFilter;
import org.candlepin.resteasy.filter.VersionResponseFilter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.service.impl.HypervisorUpdateAction;
import org.candlepin.sync.ConsumerExporter;
import org.candlepin.sync.ConsumerTypeExporter;
import org.candlepin.sync.Exporter;
import org.candlepin.sync.MetaExporter;
import org.candlepin.sync.RulesExporter;
import org.candlepin.sync.SyncUtils;
import org.candlepin.util.AttributeValidator;
import org.candlepin.util.DateSource;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.FactValidator;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.validation.CandlepinMessageInterpolator;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.persist.jpa.JpaPersistOptions;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.validator.HibernateValidator;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import javax.cache.CacheManager;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;


public class CandlepinModule extends AbstractModule {
    private final Configuration config;

    public CandlepinModule(Configuration config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void configure() {
        // Bindings for our custom scope
        CandlepinRequestScope requestScope = new CandlepinRequestScope();
        bindScope(CandlepinRequestScoped.class, requestScope);
        bind(CandlepinRequestScope.class).toInstance(requestScope);
        bind(I18n.class).toProvider(I18nProvider.class);
        bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
        bind(MessageInterpolator.class).to(CandlepinMessageInterpolator.class);

        configureJPA();
        bindPki();

        bind(GuestMigration.class);

        // Endpoint resource
        resources();

        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(EntitlementRules.class);
        bind(EntitlementRulesTranslator.class);
        bind(PoolManager.class);
        bind(PoolService.class);
        bind(ConsumerMigration.class);
        bind(RefresherFactory.class);
        bind(CandlepinModeManager.class).asEagerSingleton();
        bind(SuspendModeTransitioner.class).asEagerSingleton();
        bind(ScheduledExecutorService.class).toProvider(ScheduledExecutorServiceProvider.class);
        bind(HypervisorUpdateAction.class);
        bind(OwnerManager.class);
        bind(PoolRules.class);
        bind(PoolOpProcessor.class);
        bind(Entitler.class);
        bind(NotSupportedExceptionMapper.class);
        bind(NotAuthorizedExceptionMapper.class);
        bind(NotFoundExceptionMapper.class);
        bind(NotAcceptableExceptionMapper.class);
        bind(NoLogWebApplicationExceptionMapper.class);
        bind(NotAllowedExceptionMapper.class);
        bind(InternalServerErrorExceptionMapper.class);
        bind(DefaultOptionsMethodExceptionMapper.class);
        bind(BadRequestExceptionMapper.class);
        bind(RollbackExceptionMapper.class);
        bind(ValidationExceptionMapper.class);
        bind(WebApplicationExceptionMapper.class);
        bind(FailureExceptionMapper.class);
        bind(ServiceExceptionMapper.class);
        bind(ReaderExceptionMapper.class);
        bind(WriterExceptionMapper.class);
        bind(CandlepinExceptionMapper.class);
        bind(RuntimeExceptionMapper.class);
        bind(JAXBMarshalExceptionMapper.class);
        bind(JAXBUnmarshalExceptionMapper.class);
        bind(OffsetDateTimeParamConverterProvider.class);
        bind(AnnotationLocator.class).asEagerSingleton();
        bind(MethodLocator.class).asEagerSingleton();

        bind(Principal.class).toProvider(PrincipalProvider.class);
        bind(JsRunnerProvider.class).asEagerSingleton();
        bind(JsRunner.class).toProvider(JsRunnerProvider.class);
        bind(SyncUtils.class).asEagerSingleton();
        bind(ObjectMapperFactory.class).asEagerSingleton();
        bind(ObjectMapper.class).toProvider(ObjectMapperFactory.class).asEagerSingleton();
        bind(UniqueIdGenerator.class).to(DefaultUniqueIdGenerator.class);
        bind(AttributeValidator.class);
        bind(FactValidator.class);

        bind(SystemPurposeComplianceRules.class);
        bind(JsonProvider.class);
        miscConfigurations();

        bind(CacheManager.class).toProvider(JCacheManagerProvider.class).in(Singleton.class);

        Multibinder<CloudProviderFactParser> parserBinder =
            Multibinder.newSetBinder(binder(), CloudProviderFactParser.class);
        parserBinder.addBinding().to(AWSProviderFactParser.class);
        parserBinder.addBinding().to(AzureProviderFactParser.class);
        parserBinder.addBinding().to(GCPProviderFactParser.class);

        // Configure model translators
        this.configureModelTranslator();
    }

    private void bindPki() {
        bind(CertificateReader.class).asEagerSingleton();
        bind(PrivateKeyReader.class).to(BouncyCastlePrivateKeyReader.class);
        bind(X509ExtensionUtil.class);

        bind(BouncyCastleSecurityProvider.class);
        bind(KeyPairGenerator.class).to(BouncyCastleKeyPairGenerator.class);
        bind(PemEncoder.class).to(BouncyCastlePemEncoder.class);
        bind(X509CertificateBuilderProvider.class);
        bind(X509CertificateBuilder.class).toProvider(X509CertificateBuilderProvider.class);

        bind(AnonymousCertificateGenerator.class);
        bind(EntitlementCertificateGenerator.class);
        bind(EntitlementPayloadGenerator.class);
        bind(IdentityCertificateGenerator.class);
        bind(ProductCertificateGenerator.class);
        bind(SCACertificateGenerator.class);
        bind(UeberCertificateGenerator.class);
    }

    private void resources() {
        bind(ActivationKeyResource.class);
        bind(AdminResource.class);
        bind(CdnResource.class);
        bind(CertificateSerialResource.class);
        bind(CloudRegistrationResource.class);
        bind(CrlResource.class);
        bind(ConsumerResource.class);
        bind(ConsumerTypeResource.class);
        bind(ContentResource.class);
        bind(DeletedConsumerResource.class);
        bind(DistributorVersionResource.class);
        bind(EntitlementResource.class);
        bind(EnvironmentResource.class);
        bind(HypervisorResource.class);
        bind(JobResource.class);
        bind(OwnerResource.class);
        bind(OwnerProductResource.class);
        bind(OwnerContentResource.class);
        bind(GuestIdResource.class);
        bind(PoolResource.class);
        bind(ProductResource.class);
        bind(RoleResource.class);
        bind(RootResource.class);
        bind(RulesResource.class);
        bind(SubscriptionResource.class);
        bind(StatusResource.class);
        bind(UserResource.class);
    }

    private void miscConfigurations() {
        configureInterceptors();
        configureAuth();
        configureMessaging();
        configureActiveMQComponents();
        configureAsyncJobs();
        configureExporter();
        configureBindFactories();
    }

    @Provides
    @Named("ValidationProperties")
    protected Properties getValidationProperties() {
        return new Properties();
    }

    @Provides
    protected ValidatorFactory getValidationFactory(Provider<MessageInterpolator> interpolatorProvider) {
        return Validation.byProvider(HibernateValidator.class)
            .configure()
            .messageInterpolator(interpolatorProvider.get())
            .buildValidatorFactory();
    }

    protected void configureMessaging() {
        String provider = this.config.getString(ConfigProperties.CPM_PROVIDER);

        // TODO: Change this to a map lookup as we get more providers

        if (ArtemisUtil.PROVIDER.equalsIgnoreCase(provider)) {
            bind(CPMContextListener.class).to(ArtemisContextListener.class).asEagerSingleton();
            bind(CPMSessionFactory.class).to(ArtemisSessionFactory.class).asEagerSingleton();
        }
        else {
            bind(CPMContextListener.class).to(NoopContextListener.class).asEagerSingleton();
            bind(CPMSessionFactory.class).to(NoopSessionFactory.class).asEagerSingleton();
        }
    }

    protected void configureJPA() {
        Properties jpaProperties = new Properties();
        Map<String, String> jpaConfig = config.getValuesByPrefix(ConfigurationPrefixes.JPA_CONFIG_PREFIX);
        for (Map.Entry<String, String> entry : jpaConfig.entrySet()) {
            String key = Util.stripPrefix(entry.getKey(), ConfigurationPrefixes.JPA_CONFIG_PREFIX);
            jpaProperties.put(key, entry.getValue());
        }

        // As of Guice 6.0, UnitOfWork is no longer automatically started upon fetching the
        // EntityManager. This option restores that behavior.
        JpaPersistOptions jpaOptions = JpaPersistOptions.builder()
            .setAutoBeginWorkOnEntityManagerCreation(true)
            .build();

        JpaPersistModule jpaModule = new JpaPersistModule("default", jpaOptions)
            .properties(jpaProperties);

        install(jpaModule);
        bind(JPAInitializer.class).asEagerSingleton();
    }

    private void configureBindFactories() {
        bind(BindChainFactory.class);
        bind(BindContextFactory.class);
        bind(PreEntitlementRulesCheckOpFactory.class);
    }

    private void configureAuth() {
        bind(AuthorizationFeature.class);
        bind(StoreFactory.class).asEagerSingleton();
        bind(VerifyAuthorizationFilter.class);
        bind(SuperAdminAuthorizationFilter.class);
        bind(SecurityHoleAuthorizationFilter.class);
        bind(AuthenticationFilter.class);
        bind(StoreFactory.OwnerStore.class);
        bind(StoreFactory.EnvironmentStore.class);
        bind(StoreFactory.ConsumerStore.class);
        bind(StoreFactory.EntitlementStore.class);
        bind(StoreFactory.PoolStore.class);
        bind(StoreFactory.UserStore.class);
        bind(StoreFactory.ActivationKeyStore.class);
        bind(StoreFactory.AsyncJobStatusStore.class);
    }

    private void configureInterceptors() {
        bind(ConsumerCheckInFilter.class);
        bind(PageRequestFilter.class);
        bind(VersionResponseFilter.class);
        bind(LinkHeaderResponseFilter.class);
        bind(DynamicJsonFilter.class);

        // Only bind the suspend mode filter if configured to do so
        if (this.config.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
            bind(CandlepinSuspendModeFilter.class);
        }

        bindConstant().annotatedWith(Names.named("PREFIX_APIURL_KEY")).to(ConfigProperties.PREFIX_APIURL);
    }

    private void configureAsyncJobs() {
        bind(SchedulerFactory.class).to(StdSchedulerFactory.class);

        bind(JobMessageDispatcher.class);
        bind(JobMessageReceiver.class);

        JobManager.registerJob(ActiveEntitlementJob.JOB_KEY, ActiveEntitlementJob.class);
        JobManager.registerJob(CertificateCleanupJob.JOB_KEY, CertificateCleanupJob.class);
        JobManager.registerJob(EntitlerJob.JOB_KEY, EntitlerJob.class);
        JobManager.registerJob(EntitleByProductsJob.JOB_KEY, EntitleByProductsJob.class);
        JobManager.registerJob(ExpiredPoolsCleanupJob.JOB_KEY, ExpiredPoolsCleanupJob.class);
        JobManager.registerJob(ExportJob.JOB_KEY, ExportJob.class);
        JobManager.registerJob(HealEntireOrgJob.JOB_KEY, HealEntireOrgJob.class);
        JobManager.registerJob(HypervisorHeartbeatUpdateJob.JOB_KEY, HypervisorHeartbeatUpdateJob.class);
        JobManager.registerJob(HypervisorUpdateJob.JOB_KEY, HypervisorUpdateJob.class);
        JobManager.registerJob(ImportJob.JOB_KEY, ImportJob.class);
        JobManager.registerJob(ImportRecordCleanerJob.JOB_KEY, ImportRecordCleanerJob.class);
        JobManager.registerJob(JobCleaner.JOB_KEY, JobCleaner.class);
        JobManager.registerJob(ManifestCleanerJob.JOB_KEY, ManifestCleanerJob.class);
        JobManager.registerJob(RefreshPoolsForProductJob.JOB_KEY, RefreshPoolsForProductJob.class);
        JobManager.registerJob(RefreshPoolsJob.JOB_KEY, RefreshPoolsJob.class);
        JobManager.registerJob(RegenEnvEntitlementCertsJob.JOB_KEY, RegenEnvEntitlementCertsJob.class);
        JobManager.registerJob(RegenProductEntitlementCertsJob.JOB_KEY,
            RegenProductEntitlementCertsJob.class);
        JobManager.registerJob(UndoImportsJob.JOB_KEY, UndoImportsJob.class);
        JobManager.registerJob(UnmappedGuestEntitlementCleanerJob.JOB_KEY,
            UnmappedGuestEntitlementCleanerJob.class);
        JobManager.registerJob(InactiveConsumerCleanerJob.JOB_KEY, InactiveConsumerCleanerJob.class);
        JobManager.registerJob(CloudAccountOrgSetupJob.JOB_KEY, CloudAccountOrgSetupJob.class);
        JobManager.registerJob(ConsumerMigrationJob.JOB_KEY, ConsumerMigrationJob.class);
        JobManager.registerJob(RevokeEntitlementsJob.JOB_KEY, RevokeEntitlementsJob.class);
    }

    private void configureExporter() {
        bind(Exporter.class);
        bind(MetaExporter.class);
        bind(ConsumerTypeExporter.class);
        bind(ConsumerExporter.class);
        bind(RulesExporter.class);
    }

    private void configureActiveMQComponents() {
        if (config.getBoolean(ConfigProperties.ACTIVEMQ_ENABLED)) {
            bind(MessageSource.class).to(ArtemisMessageSource.class);
            bind(MessageSourceReceiverFactory.class).to(ArtemisMessageSourceReceiverFactory.class);
            bind(EventSink.class).to(EventSinkImpl.class);
        }
        else {
            bind(EventSink.class).to(NoopEventSinkImpl.class);
        }
    }

    protected void configureModelTranslator() {
        bind(ModelTranslator.class).to(StandardTranslator.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    @Named("X509V3ExtensionUtilObjectMapper")
    private ObjectMapper configureX509V3ExtensionUtilObjectMapper() {
        return ObjectMapperFactory.getX509V3ExtensionUtilObjectMapper();
    }

    @Provides
    @Singleton
    @Named("HypervisorUpdateJobObjectMapper")
    private ObjectMapper configureHypervisorUpdateJobObjectMapper() {
        return ObjectMapperFactory.getHypervisorUpdateJobObjectMapper();
    }

    @Provides
    @Singleton
    @Named("ImportObjectMapper")
    private ObjectMapper configureImportObjectMapper() {
        return ObjectMapperFactory.getSyncObjectMapper(this.config);
    }

    @Provides
    @Singleton
    @Named("ExportObjectMapper")
    private ObjectMapper configureExportObjectMapper() {
        return ObjectMapperFactory.getSyncObjectMapper(this.config);
    }

    @Provides
    @Singleton
    @Named("RulesObjectMapper")
    private RulesObjectMapper configureRulesObjectMapper() {
        return ObjectMapperFactory.getRulesObjectMapper();
    }

}
