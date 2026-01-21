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
package org.candlepin;

import static org.mockito.Mockito.*;

import org.candlepin.async.JobManager;
import org.candlepin.audit.EventSink;
import org.candlepin.audit.NoopEventSinkImpl;
import org.candlepin.auth.Principal;
import org.candlepin.bind.BindChainFactory;
import org.candlepin.bind.BindContextFactory;
import org.candlepin.bind.PreEntitlementRulesCheckOpFactory;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.CandlepinRequestScoped;
import org.candlepin.guice.HttpMethodMatcher;
import org.candlepin.guice.I18nProvider;
import org.candlepin.guice.JPAInitializer;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.guice.ScriptEngineProvider;
import org.candlepin.guice.TestPrincipalProvider;
import org.candlepin.guice.TestingScope;
import org.candlepin.guice.ValidationListenerProvider;
import org.candlepin.messaging.CPMContextListener;
import org.candlepin.messaging.CPMSessionFactory;
import org.candlepin.messaging.impl.noop.NoopContextListener;
import org.candlepin.messaging.impl.noop.NoopSessionFactory;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastlePrivateKeyReader;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.resource.ActivationKeyResource;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.EntitlementResource;
import org.candlepin.resource.EnvironmentResource;
import org.candlepin.resource.OwnerResource;
import org.candlepin.resource.PoolResource;
import org.candlepin.resource.ProductResource;
import org.candlepin.resource.SubscriptionResource;
import org.candlepin.resource.util.AWSProviderFactParser;
import org.candlepin.resource.util.AzureProviderFactParser;
import org.candlepin.resource.util.CloudProviderFactParser;
import org.candlepin.resource.util.GCPProviderFactParser;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.filter.StoreFactory;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultCloudRegistrationAdapter;
import org.candlepin.service.impl.DefaultEventAdapter;
import org.candlepin.service.impl.DefaultOwnerServiceAdapter;
import org.candlepin.service.impl.DefaultProductServiceAdapter;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.service.impl.DefaultUserServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.service.impl.stub.StubEntitlementCertServiceAdapter;
import org.candlepin.sync.file.DBManifestService;
import org.candlepin.sync.file.ManifestFileService;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.DateSourceForTesting;
import org.candlepin.test.EnforcerForTesting;
import org.candlepin.test.VerifyAuthorizationFilterFactory;
import org.candlepin.util.DateSource;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;
import org.candlepin.validation.CandlepinMessageInterpolator;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.persist.jpa.JpaPersistOptions;
import com.google.inject.servlet.RequestScoped;

import org.hibernate.Session;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.validator.HibernateValidator;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;


/**
 * Guice modules for unit testing
 */
public class TestingModules {
    private TestingModules() {
        // This class is just a container for various Guice Modules used during testing
    }

    public static class ServletEnvironmentModule extends AbstractModule {
        @Override
        public void configure() {
            bind(I18n.class).toProvider(I18nProvider.class);

            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getAttribute("username")).thenReturn("mock_user");

            bind(HttpServletRequest.class).toInstance(request);
            bind(HttpServletResponse.class).toInstance(mock(HttpServletResponse.class));
        }
    }

    public static class JpaModule extends AbstractModule {
        @Override
        public void configure() {
            // As of Guice 6.0, UnitOfWork is no longer automatically started upon fetching the
            // EntityManager. This option restores that behavior.
            JpaPersistOptions jpaOptions = JpaPersistOptions.builder()
                .setAutoBeginWorkOnEntityManagerCreation(true)
                .build();

            install(new ServletEnvironmentModule());
            install(new JpaPersistModule("testing", jpaOptions));

            bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
            bind(MessageInterpolator.class).to(CandlepinMessageInterpolator.class);
            bind(JPAInitializer.class).asEagerSingleton();
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
    }

    public static class MockJpaModule extends AbstractModule {
        private EntityManager em = mock(EntityManager.class);
        private Session session = mock(Session.class);

        public Session getMockSession() {
            return session;
        }

        public EntityManager getMockEntityManager() {
            return em;
        }

        private Rules getRules() {
            InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
            Rules result = new Rules(Util.readFile(is));
            result.setRulesSource(Rules.RulesSourceEnum.DEFAULT);
            return result;
        }

        @Override
        protected void configure() {
            bind(EntityManagerFactory.class).toInstance(mock(EntityManagerFactory.class));
            bind(UnitOfWork.class).toInstance(mock(UnitOfWork.class));
            bind(PersistService.class).toInstance(mock(PersistService.class));
            bind(EntityManager.class).toInstance(em);
            bind(ValidatorFactory.class).toInstance(mock(ValidatorFactory.class));

            /*
             * The JsRunnerProvider is profoundly annoying because when it is created it begins by trying to
             * read the rules out of the database with the RulesCurator. Since we don't have a database with
             * this Module, we have to fake it.
             */
            RulesCurator rulesCurator = mock(RulesCurator.class);

            Answer<Rules> sameRules = new Answer<Rules>() {
                @Override
                public Rules answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    return (Rules) args[0];
                }
            };

            when(rulesCurator.getDbRules()).thenReturn(getRules());
            when(rulesCurator.getRules()).thenReturn(getRules());
            when(rulesCurator.create(any(Rules.class))).thenAnswer(sameRules);
            when(rulesCurator.update(any(Rules.class))).thenAnswer(sameRules);
            when(rulesCurator.getUpdated()).thenReturn(new Date());

            bind(RulesCurator.class).toInstance(rulesCurator);

            when(em.getDelegate()).thenReturn(session);
        }
    }

    public static class StandardTest extends AbstractModule {
        private Configuration config;

        public StandardTest() {
            this.config = TestConfig.defaults();
        }

        public StandardTest(Configuration config) {
            this.config = config;
        }

        private TestingInterceptor authMethodInterceptor;

        @Override
        public void configure() {
            CandlepinCache mockedCandlepinCache = mock(CandlepinCache.class);
            when(mockedCandlepinCache.getStatusCache()).thenReturn(mock(StatusCache.class));
            // This is not necessary in the normal module because the config is bound in the
            // context listener
            bind(Configuration.class).toInstance(config);
            // When testing, we are using mock Candlepin cache. It's
            // methods are basically no-op
            bind(CandlepinCache.class).toInstance(mockedCandlepinCache);
            CandlepinRequestScope requestScope = new CandlepinRequestScope();
            bindScope(CandlepinRequestScoped.class, requestScope);
            // RequestScoped doesn't exist in unit tests, so we must
            // define test alternative for it.
            bindScope(RequestScoped.class, TestingScope.EAGER_SINGLETON);
            bind(CandlepinRequestScope.class).toInstance(requestScope);

            JobManager mockJobManager = mock(JobManager.class);
            bind(JobManager.class).toInstance(mockJobManager);

            bind(X509ExtensionUtil.class);

            bind(ConsumerResource.class);
            bind(PoolResource.class);
            bind(EntitlementResource.class);
            bind(OwnerResource.class);
            bind(EnvironmentResource.class);
            bind(SubscriptionResource.class);
            bind(ActivationKeyResource.class);
            bind(ProductServiceAdapter.class).to(DefaultProductServiceAdapter.class);
            bind(CloudRegistrationAdapter.class).to(DefaultCloudRegistrationAdapter.class);
            bind(ProductResource.class);
            bind(DateSource.class).to(DateSourceForTesting.class).asEagerSingleton();
            bind(Enforcer.class).to(EnforcerForTesting.class); // .to(JavascriptEnforcer.class);
            bind(SubjectKeyIdentifierWriter.class).to(BouncyCastleSubjectKeyIdentifierWriter.class);
            bind(PemEncoder.class).to(BouncyCastlePemEncoder.class);
            bind(PrivateKeyReader.class).to(BouncyCastlePrivateKeyReader.class);
            bind(CertificateReader.class).to(CertificateReaderForTesting.class).asEagerSingleton();
            bind(KeyPairGenerator.class).to(BouncyCastleKeyPairGenerator.class).asEagerSingleton();

            bind(SubscriptionServiceAdapter.class).to(ImportSubscriptionServiceAdapter.class);
            bind(OwnerServiceAdapter.class).to(DefaultOwnerServiceAdapter.class);
            bind(EntitlementCertServiceAdapter.class).to(StubEntitlementCertServiceAdapter.class);
            bind(EventAdapter.class).to(DefaultEventAdapter.class);
            bind(ManifestFileService.class).to(DBManifestService.class);
            bind(ScriptEngineProvider.class);

            bind(UserServiceAdapter.class).to(DefaultUserServiceAdapter.class);

            bind(JsRunnerProvider.class).asEagerSingleton();
            bind(JsRunner.class).toProvider(JsRunnerProvider.class);

            bind(PrincipalProvider.class).to(TestPrincipalProvider.class);
            bind(Principal.class).toProvider(TestPrincipalProvider.class);
            bind(EventSink.class).to(NoopEventSinkImpl.class);

            bind(AnnotationLocator.class);
            bind(StoreFactory.class);
            VerifyAuthorizationFilterFactory amf = new VerifyAuthorizationFilterFactory();
            requestInjection(amf);
            authMethodInterceptor = new TestingInterceptor(amf);
            bind(TestingInterceptor.class).toInstance(authMethodInterceptor);

            bindInterceptor(Matchers.inPackage(Package.getPackage("org.candlepin.resource")),
                new HttpMethodMatcher(), authMethodInterceptor);

            // temporary
            bind(PoolRules.class);
            bind(PoolManager.class);
            bind(UniqueIdGenerator.class).to(DefaultUniqueIdGenerator.class);

            bind(CandlepinModeManager.class).asEagerSingleton();
            bind(BindChainFactory.class);
            bind(BindContextFactory.class);
            bind(PreEntitlementRulesCheckOpFactory.class);

            // Bind model translator
            bind(ModelTranslator.class).to(StandardTranslator.class).asEagerSingleton();

            // Async job stuff
            bind(SchedulerFactory.class).to(StdSchedulerFactory.class);

            // Messaging
            bind(CPMSessionFactory.class).to(NoopSessionFactory.class).in(Singleton.class);
            bind(CPMContextListener.class).to(NoopContextListener.class).in(Singleton.class);
            bind(ObjectMapperFactory.class).asEagerSingleton();
            bind(ObjectMapper.class).toProvider(ObjectMapperFactory.class).asEagerSingleton();

            Multibinder<CloudProviderFactParser> parserBinder =
                Multibinder.newSetBinder(binder(), CloudProviderFactParser.class);
            parserBinder.addBinding().to(AWSProviderFactParser.class);
            parserBinder.addBinding().to(AzureProviderFactParser.class);
            parserBinder.addBinding().to(GCPProviderFactParser.class);
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
            return ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());
        }

        @Provides
        @Singleton
        @Named("ExportObjectMapper")
        private ObjectMapper configureExportObjectMapper() {
            return ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());
        }

        @Provides
        @Singleton
        @Named("RulesObjectMapper")
        private RulesObjectMapper configureRulesObjectMapper() {
            return ObjectMapperFactory.getRulesObjectMapper();
        }
    }
}
