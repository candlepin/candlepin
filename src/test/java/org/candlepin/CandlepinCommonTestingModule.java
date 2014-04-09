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

import javax.validation.MessageInterpolator;

import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.LoggingConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.guice.CandlepinModule;
import org.candlepin.guice.CandlepinSingletonScope;
import org.candlepin.guice.CandlepinSingletonScoped;
import org.candlepin.guice.HttpMethodMatcher;
import org.candlepin.guice.I18nProvider;
import org.candlepin.guice.JPAInitializer;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.guice.ScriptEngineProvider;
import org.candlepin.guice.TestPrincipalProvider;
import org.candlepin.guice.ValidationListenerProvider;
import org.candlepin.hibernate.CandlepinMessageInterpolator;
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
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultIdentityCertServiceAdapter;
import org.candlepin.service.impl.DefaultProductServiceAdapter;
import org.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.service.impl.DefaultUserServiceAdapter;
import org.candlepin.service.impl.stub.StubEntitlementCertServiceAdapter;
import org.candlepin.test.AuthMethodInterceptorFactory;
import org.candlepin.test.DateSourceForTesting;
import org.candlepin.test.EnforcerForTesting;
import org.candlepin.test.EventSinkForTesting;
import org.candlepin.test.PKIReaderForTesting;
import org.candlepin.util.DateSource;
import org.candlepin.util.ExpiryDateFunction;
import org.candlepin.util.X509ExtensionUtil;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

import com.google.common.base.Function;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.persist.jpa.JpaPersistModule;

public class CandlepinCommonTestingModule extends CandlepinModule {

    private TestingInterceptor authMethodInterceptor;

    @Override
    public void configure() {
        CandlepinSingletonScope singletonScope = new CandlepinSingletonScope();
        bindScope(CandlepinSingletonScoped.class, singletonScope);
        bind(CandlepinSingletonScope.class).toInstance(singletonScope);

        bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
        bind(MessageInterpolator.class).to(CandlepinMessageInterpolator.class);

        install(new JpaPersistModule("default"));
        bind(JPAInitializer.class).asEagerSingleton();

        bind(X509ExtensionUtil.class);
        bind(Config.class).to(CandlepinCommonTestConfig.class)
            .asEagerSingleton();
        bind(LoggingConfig.class).asEagerSingleton();
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
        bind(SubscriptionServiceAdapter.class).to(
            DefaultSubscriptionServiceAdapter.class);
        bind(EntitlementCertServiceAdapter.class).to(
            StubEntitlementCertServiceAdapter.class);
        bind(ScriptEngineProvider.class);
        bind(I18n.class).toProvider(I18nProvider.class);

        bind(JobFactory.class).to(GuiceJobFactory.class);
        bind(JobListener.class).to(PinsetterJobListener.class);
        bind(UserServiceAdapter.class).to(DefaultUserServiceAdapter.class);

        bind(JsRunnerProvider.class).asEagerSingleton();
        bind(JsRunner.class).toProvider(JsRunnerProvider.class);

        bind(PrincipalProvider.class).to(TestPrincipalProvider.class);
        bind(Principal.class).toProvider(TestPrincipalProvider.class);
        bind(EventSink.class).to(EventSinkForTesting.class);

        AuthMethodInterceptorFactory amf = new AuthMethodInterceptorFactory();
        requestInjection(amf);
        authMethodInterceptor = new TestingInterceptor(amf);

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

    public TestingInterceptor securityInterceptor() {
        return authMethodInterceptor;
    }
}
