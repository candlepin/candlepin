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

import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.EventSink;
import org.candlepin.audit.EventSinkImpl;
import org.candlepin.audit.NoopEventSinkImpl;
import org.candlepin.audit.QpidConfigBuilder;
import org.candlepin.audit.QpidConnection;
import org.candlepin.auth.Principal;
import org.candlepin.bind.BindChainFactory;
import org.candlepin.bind.BindContextFactory;
import org.candlepin.bind.PreEntitlementRulesCheckOpFactory;
import org.candlepin.cache.JCacheManagerProvider;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationPrefixes;
import org.candlepin.common.exceptions.mappers.BadRequestExceptionMapper;
import org.candlepin.common.exceptions.mappers.CandlepinExceptionMapper;
import org.candlepin.common.exceptions.mappers.DefaultOptionsMethodExceptionMapper;
import org.candlepin.common.exceptions.mappers.FailureExceptionMapper;
import org.candlepin.common.exceptions.mappers.InternalServerErrorExceptionMapper;
import org.candlepin.common.exceptions.mappers.JAXBMarshalExceptionMapper;
import org.candlepin.common.exceptions.mappers.JAXBUnmarshalExceptionMapper;
import org.candlepin.common.exceptions.mappers.NoLogWebApplicationExceptionMapper;
import org.candlepin.common.exceptions.mappers.NotAcceptableExceptionMapper;
import org.candlepin.common.exceptions.mappers.NotAllowedExceptionMapper;
import org.candlepin.common.exceptions.mappers.NotAuthorizedExceptionMapper;
import org.candlepin.common.exceptions.mappers.NotFoundExceptionMapper;
import org.candlepin.common.exceptions.mappers.NotSupportedExceptionMapper;
import org.candlepin.common.exceptions.mappers.ReaderExceptionMapper;
import org.candlepin.common.exceptions.mappers.RollbackExceptionMapper;
import org.candlepin.common.exceptions.mappers.RuntimeExceptionMapper;
import org.candlepin.common.exceptions.mappers.ValidationExceptionMapper;
import org.candlepin.common.exceptions.mappers.WebApplicationExceptionMapper;
import org.candlepin.common.exceptions.mappers.WriterExceptionMapper;
import org.candlepin.common.guice.JPAInitializer;
import org.candlepin.common.resteasy.filter.DynamicJsonFilter;
import org.candlepin.common.resteasy.filter.LinkHeaderResponseFilter;
import org.candlepin.common.resteasy.filter.PageRequestFilter;
import org.candlepin.common.util.VersionUtil;
import org.candlepin.common.validation.CandlepinMessageInterpolator;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ModeManager;
import org.candlepin.controller.ModeManagerImpl;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.ScheduledExecutorServiceProvider;
import org.candlepin.controller.SuspendModeTransitioner;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.CPRestrictions;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.pinsetter.core.GuiceJobFactory;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.PinsetterTriggerListener;
import org.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.candlepin.pinsetter.tasks.EntitlerJob;
import org.candlepin.pinsetter.tasks.HypervisorUpdateJob;
import org.candlepin.pinsetter.tasks.JobCleaner;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.pinsetter.tasks.SweepBarJob;
import org.candlepin.pinsetter.tasks.UnpauseJob;
import org.candlepin.pki.JSSPrivateKeyReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.impl.JSSPKIUtility;
import org.candlepin.policy.criteria.CriteriaRules;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
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
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.util.ResolverUtil;
import org.candlepin.resteasy.DateFormatter;
import org.candlepin.resteasy.JsonProvider;
import org.candlepin.resteasy.ResourceLocatorMap;
import org.candlepin.resteasy.filter.AuthenticationFilter;
import org.candlepin.resteasy.filter.AuthorizationFeature;
import org.candlepin.resteasy.filter.CandlepinQueryInterceptor;
import org.candlepin.resteasy.filter.CandlepinSuspendModeFilter;
import org.candlepin.resteasy.filter.ConsumerCheckInFilter;
import org.candlepin.resteasy.filter.PinsetterAsyncFilter;
import org.candlepin.resteasy.filter.SecurityHoleAuthorizationFilter;
import org.candlepin.resteasy.filter.StoreFactory;
import org.candlepin.resteasy.filter.SuperAdminAuthorizationFilter;
import org.candlepin.resteasy.filter.VerifyAuthorizationFilter;
import org.candlepin.resteasy.filter.VersionResponseFilter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.swagger.CandlepinSwaggerModelConverter;
import org.candlepin.sync.ConsumerExporter;
import org.candlepin.sync.ConsumerTypeExporter;
import org.candlepin.sync.EntitlementCertExporter;
import org.candlepin.sync.Exporter;
import org.candlepin.sync.MetaExporter;
import org.candlepin.sync.RulesExporter;
import org.candlepin.util.AttributeValidator;
import org.candlepin.util.DateSource;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.ExpiryDateFunction;
import org.candlepin.util.FactValidator;
import org.candlepin.util.X509ExtensionUtil;

import com.google.common.base.Function;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.persist.jpa.JpaPersistModule;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.validator.HibernateValidator;
import org.quartz.JobListener;
import org.quartz.TriggerListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import javax.cache.CacheManager;
import javax.inject.Provider;
import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;



/**
 * CandlepinModule
 */
public class CandlepinModule extends AbstractModule {
    private Configuration config;

    public CandlepinModule(Configuration config) {
        this.config = config;
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

        bind(PKIUtility.class).to(JSSPKIUtility.class).asEagerSingleton();
        bind(CertificateReader.class).asEagerSingleton();
        bind(PrivateKeyReader.class).to(JSSPrivateKeyReader.class);
        bind(X509ExtensionUtil.class);
        bind(ResolverUtil.class);
        bind(GuestMigration.class);
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
        bind(OwnerProductResource.class);
        bind(OwnerContentResource.class);
        bind(RoleResource.class);
        bind(RootResource.class);
        bind(ProductResource.class);
        bind(SubscriptionResource.class);
        bind(ActivationKeyResource.class);
        bind(CertificateSerialResource.class);
        bind(CrlResource.class);
        bind(JobResource.class);
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(EntitlementRules.class);
        bind(EntitlementRulesTranslator.class);
        bind(PoolManager.class).to(CandlepinPoolManager.class);
        bind(ModeManager.class).to(ModeManagerImpl.class).asEagerSingleton();
        bind(SuspendModeTransitioner.class).asEagerSingleton();
        bind(ScheduledExecutorService.class).toProvider(ScheduledExecutorServiceProvider.class);
        bind(OwnerManager.class);
        bind(PoolRules.class);
        bind(CriteriaRules.class);
        bind(Entitler.class);
        bind(RulesResource.class);
        bind(AdminResource.class);
        bind(StatusResource.class);
        bind(EnvironmentResource.class);
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
        bind(ReaderExceptionMapper.class);
        bind(WriterExceptionMapper.class);
        bind(CandlepinExceptionMapper.class);
        bind(RuntimeExceptionMapper.class);
        bind(JAXBMarshalExceptionMapper.class);
        bind(JAXBUnmarshalExceptionMapper.class);
        bind(Principal.class).toProvider(PrincipalProvider.class);
        bind(JsRunnerProvider.class).asEagerSingleton();
        bind(JsRunner.class).toProvider(JsRunnerProvider.class);
        bind(RulesObjectMapper.class).asEagerSingleton();
        bind(UserResource.class);
        bind(UniqueIdGenerator.class).to(DefaultUniqueIdGenerator.class);
        bind(DistributorVersionResource.class);
        bind(DeletedConsumerResource.class);
        bind(CdnResource.class);
        bind(GuestIdResource.class);
        bind(AttributeValidator.class);
        bind(FactValidator.class);
        bind(DateFormatter.class);
        requestStaticInjection(CPRestrictions.class);

        bind(JsonProvider.class);
        miscConfigurations();

        // Async Jobs
        bind(RefreshPoolsJob.class);
        bind(EntitlerJob.class);
        requestStaticInjection(EntitlerJob.class);
        bind(HypervisorUpdateJob.class);

        // UeberCerts
        bind(UeberCertificateGenerator.class);

        // flexible end date for identity certificates
        bind(Function.class).annotatedWith(Names.named("endDateGenerator"))
            .to(ExpiryDateFunction.class).in(Singleton.class);

        // only initialize if we've enabled AMQP integration
        if (config.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
            configureAmqp();
        }

        bind(CacheManager.class).toProvider(JCacheManagerProvider.class).in(Singleton.class);

        // Configure model translators
        this.configureModelTranslator();
    }

    private void miscConfigurations() {
        configureInterceptors();
        configureAuth();
        configureEventSink();
        configurePinsetter();
        configureExporter();
        configureSwagger();
        configureBindFactories();
    }

    @Provides @Named("ValidationProperties")
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

    protected void configureJPA() {
        Configuration jpaConfig = config.strippedSubset(ConfigurationPrefixes.JPA_CONFIG_PREFIX);
        install(new JpaPersistModule("default").properties(jpaConfig.toProperties()));
        bind(JPAInitializer.class).asEagerSingleton();
    }

    private void configureBindFactories() {
        install(new FactoryModuleBuilder().build(BindChainFactory.class));
        install(new FactoryModuleBuilder().build(BindContextFactory.class));
        install(new FactoryModuleBuilder().build(PreEntitlementRulesCheckOpFactory.class));
    }

    private void configureAuth() {
        bind(AuthorizationFeature.class);
        bind(StoreFactory.class).asEagerSingleton();
        bind(VerifyAuthorizationFilter.class);
        bind(SuperAdminAuthorizationFilter.class);
        bind(SecurityHoleAuthorizationFilter.class);
        bind(AuthenticationFilter.class);
        bind(ResourceLocatorMap.class).asEagerSingleton();
    }

    private void configureInterceptors() {
        bind(ConsumerCheckInFilter.class);
        bind(CandlepinSuspendModeFilter.class);
        bind(PageRequestFilter.class);
        bind(PinsetterAsyncFilter.class);
        bind(CandlepinQueryInterceptor.class);
        bind(VersionResponseFilter.class);
        bind(LinkHeaderResponseFilter.class);
        bind(DynamicJsonFilter.class);

        bindConstant().annotatedWith(Names.named("PREFIX_APIURL_KEY")).to(ConfigProperties.PREFIX_APIURL);
    }

    private void configurePinsetter() {
        bind(JobFactory.class).to(GuiceJobFactory.class);
        bind(JobListener.class).to(PinsetterJobListener.class);
        bind(TriggerListener.class).to(PinsetterTriggerListener.class);
        bind(PinsetterKernel.class);
        bind(CertificateRevocationListTask.class);
        bind(JobCleaner.class);
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

    private void configureSwagger() {
        if (!config.getBoolean(ConfigProperties.SWAGGER_ENABLED, true)) {
            return;
        }

        /*
         * Using this binding, the swagger.(json|xml) will be available
         * for an authenticated user at context: URL/candlepin/swagger.json
         */
        bind(ApiListingResource.class);
        bind(SwaggerSerializers.class);

        bind(CandlepinSwaggerModelConverter.class);

        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setSchemes(new String[] { "https" });
        beanConfig.setBasePath("/candlepin");
        beanConfig.setResourcePackage("org.candlepin.resource");
        beanConfig.setVersion(VersionUtil.getVersionString());
        beanConfig.setTitle("Candlepin");
        beanConfig.setScan(true);
    }

    private void configureAmqp() {
        // for lazy loading:

        bind(AMQPBusPublisher.class).in(Singleton.class);
        //TODO make sure these two classes are always singletons
        bind(QpidConnection.class).in(Singleton.class);
        bind(QpidConfigBuilder.class).in(Singleton.class);
    }

    private void configureEventSink() {
        if (config.getBoolean(ConfigProperties.ACTIVEMQ_ENABLED)) {
            bind(EventSink.class).to(EventSinkImpl.class);
        }
        else {
            bind(EventSink.class).to(NoopEventSinkImpl.class);
        }
    }

    protected void configureModelTranslator() {
        bind(ModelTranslator.class).to(StandardTranslator.class).asEagerSingleton();
    }
}
