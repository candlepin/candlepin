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
package org.candlepin;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventSink;
import org.candlepin.audit.NoopEventSinkImpl;
import org.candlepin.auth.Principal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.guice.HttpMethodMatcher;
import org.candlepin.common.guice.JPAInitializer;
import org.candlepin.common.validation.CandlepinMessageInterpolator;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.CandlepinRequestScoped;
import org.candlepin.guice.I18nProvider;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.guice.ScriptEngineProvider;
import org.candlepin.guice.TestPrincipalProvider;
import org.candlepin.guice.TestingRequestScope;
import org.candlepin.guice.ValidationListenerProvider;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.pinsetter.core.GuiceJobFactory;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.BouncyCastlePKIUtility;
import org.candlepin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.candlepin.policy.criteria.CriteriaRules;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
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
import org.candlepin.resteasy.ResourceLocatorMap;
import org.candlepin.resteasy.filter.StoreFactory;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultIdentityCertServiceAdapter;
import org.candlepin.service.impl.DefaultOwnerServiceAdapter;
import org.candlepin.service.impl.DefaultProductServiceAdapter;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.service.impl.DefaultUserServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.service.impl.stub.StubEntitlementCertServiceAdapter;
import org.candlepin.sync.ManifestFileService;
import org.candlepin.sync.file.DBManifestService;
import org.candlepin.test.VerifyAuthorizationFilterFactory;
import org.candlepin.test.DateSourceForTesting;
import org.candlepin.test.EnforcerForTesting;
import org.candlepin.test.PKIReaderForTesting;
import org.candlepin.util.DateSource;
import org.candlepin.util.ExpiryDateFunction;
import org.candlepin.util.Util;
import org.candlepin.util.X509ExtensionUtil;

import com.google.common.base.Function;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.servlet.RequestScoped;

import org.hibernate.Session;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.jukito.TestScope;
import org.jukito.TestSingleton;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.MessageInterpolator;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;

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
            install(new ServletEnvironmentModule());
            install(new JpaPersistModule("testing"));

            bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
            bind(MessageInterpolator.class).to(CandlepinMessageInterpolator.class);
            bind(JPAInitializer.class).asEagerSingleton();
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

            /* The JsRunnerProvider is profoundly annoying because when it is created it
             * begins by trying to read the rules out of the database with the RulesCurator.
             * Since we don't have a database with this Module, we have to fake it.
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
            this.config = new CandlepinCommonTestConfig();
        }

        public StandardTest(Configuration config) {
            this.config = config;
        }

        private TestingInterceptor authMethodInterceptor;

        @Override
        public void configure() {
            bindScope(TestSingleton.class, TestScope.SINGLETON);

            // This is not necessary in the normal module because the config is bound in the
            // context listener
            bind(Configuration.class).toInstance(config);

            CandlepinRequestScope requestScope = new CandlepinRequestScope();
            bindScope(CandlepinRequestScoped.class, requestScope);
            //RequestScoped doesn't exist in unit tests, so we must
            //define test alternative for it.
            bindScope(RequestScoped.class, new TestingRequestScope());
            bind(CandlepinRequestScope.class).toInstance(requestScope);

            bind(X509ExtensionUtil.class);

            bind(ConsumerResource.class);
            bind(PoolResource.class);
            bind(EntitlementResource.class);
            bind(OwnerResource.class);
            bind(EnvironmentResource.class);
            bind(SubscriptionResource.class);
            bind(ActivationKeyResource.class);
            bind(ProductServiceAdapter.class)
                .to(DefaultProductServiceAdapter.class);
            bind(ProductResource.class);
            bind(DateSource.class).to(DateSourceForTesting.class)
                .asEagerSingleton();
            bind(Enforcer.class).to(EnforcerForTesting.class); // .to(JavascriptEnforcer.class);
            bind(SubjectKeyIdentifierWriter.class).to(
                DefaultSubjectKeyIdentifierWriter.class);
            bind(PKIUtility.class).to(BouncyCastlePKIUtility.class);
            bind(PKIReader.class).to(PKIReaderForTesting.class).asEagerSingleton();
            bind(SubscriptionServiceAdapter.class).to(ImportSubscriptionServiceAdapter.class);
            bind(OwnerServiceAdapter.class).to(
                DefaultOwnerServiceAdapter.class);
            bind(EntitlementCertServiceAdapter.class).to(
                StubEntitlementCertServiceAdapter.class);
            bind(ManifestFileService.class).to(DBManifestService.class);
            bind(ScriptEngineProvider.class);

            bind(JobFactory.class).to(GuiceJobFactory.class);
            bind(JobListener.class).to(PinsetterJobListener.class);
            bind(UserServiceAdapter.class).to(DefaultUserServiceAdapter.class);

            bind(JsRunnerProvider.class).asEagerSingleton();
            bind(JsRunner.class).toProvider(JsRunnerProvider.class);

            bind(PrincipalProvider.class).to(TestPrincipalProvider.class);
            bind(Principal.class).toProvider(TestPrincipalProvider.class);
            bind(EventSink.class).to(NoopEventSinkImpl.class);

            bind(ResourceLocatorMap.class);
            bind(StoreFactory.class);
            VerifyAuthorizationFilterFactory amf = new VerifyAuthorizationFilterFactory();
            requestInjection(amf);
            authMethodInterceptor = new TestingInterceptor(amf);
            bind(TestingInterceptor.class).toInstance(authMethodInterceptor);

            bindInterceptor(Matchers.inPackage(Package.getPackage("org.candlepin.resource")),
                new HttpMethodMatcher(), authMethodInterceptor);

            bind(CertificateRevocationListTask.class);
            // temporary
            bind(IdentityCertServiceAdapter.class).to(
                DefaultIdentityCertServiceAdapter.class);
            bind(PoolRules.class);
            bind(CriteriaRules.class);
            bind(PoolManager.class).to(CandlepinPoolManager.class);
            bind(UniqueIdGenerator.class).to(DefaultUniqueIdGenerator.class);

            bind(Function.class).annotatedWith(Names.named("endDateGenerator"))
                .to(ExpiryDateFunction.class).in(Singleton.class);

        }
    }
}
