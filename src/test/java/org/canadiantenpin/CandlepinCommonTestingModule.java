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
package org.canadianTenPin;

import javax.validation.MessageInterpolator;

import org.canadianTenPin.audit.EventSink;
import org.canadianTenPin.auth.Principal;
import org.canadianTenPin.config.CanadianTenPinCommonTestConfig;
import org.canadianTenPin.config.Config;
import org.canadianTenPin.config.LoggingConfig;
import org.canadianTenPin.controller.CanadianTenPinPoolManager;
import org.canadianTenPin.controller.PoolManager;
import org.canadianTenPin.guice.CanadianTenPinModule;
import org.canadianTenPin.guice.CanadianTenPinSingletonScope;
import org.canadianTenPin.guice.CanadianTenPinSingletonScoped;
import org.canadianTenPin.guice.HttpMethodMatcher;
import org.canadianTenPin.guice.I18nProvider;
import org.canadianTenPin.guice.JPAInitializer;
import org.canadianTenPin.guice.PrincipalProvider;
import org.canadianTenPin.guice.ScriptEngineProvider;
import org.canadianTenPin.guice.TestPrincipalProvider;
import org.canadianTenPin.guice.ValidationListenerProvider;
import org.canadianTenPin.hibernate.CanadianTenPinMessageInterpolator;
import org.canadianTenPin.pinsetter.core.GuiceJobFactory;
import org.canadianTenPin.pinsetter.core.PinsetterJobListener;
import org.canadianTenPin.pinsetter.tasks.CertificateRevocationListTask;
import org.canadianTenPin.pki.PKIReader;
import org.canadianTenPin.pki.PKIUtility;
import org.canadianTenPin.pki.SubjectKeyIdentifierWriter;
import org.canadianTenPin.pki.impl.BouncyCastlePKIUtility;
import org.canadianTenPin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.canadianTenPin.policy.criteria.CriteriaRules;
import org.canadianTenPin.policy.js.JsRunner;
import org.canadianTenPin.policy.js.JsRunnerProvider;
import org.canadianTenPin.policy.js.entitlement.Enforcer;
import org.canadianTenPin.policy.js.pool.PoolRules;
import org.canadianTenPin.resource.ActivationKeyResource;
import org.canadianTenPin.resource.ConsumerResource;
import org.canadianTenPin.resource.EntitlementResource;
import org.canadianTenPin.resource.EnvironmentResource;
import org.canadianTenPin.resource.OwnerResource;
import org.canadianTenPin.resource.PoolResource;
import org.canadianTenPin.resource.ProductResource;
import org.canadianTenPin.resource.SubscriptionResource;
import org.canadianTenPin.service.EntitlementCertServiceAdapter;
import org.canadianTenPin.service.IdentityCertServiceAdapter;
import org.canadianTenPin.service.OwnerServiceAdapter;
import org.canadianTenPin.service.ProductServiceAdapter;
import org.canadianTenPin.service.SubscriptionServiceAdapter;
import org.canadianTenPin.service.UniqueIdGenerator;
import org.canadianTenPin.service.UserServiceAdapter;
import org.canadianTenPin.service.impl.DefaultIdentityCertServiceAdapter;
import org.canadianTenPin.service.impl.DefaultOwnerServiceAdapter;
import org.canadianTenPin.service.impl.DefaultProductServiceAdapter;
import org.canadianTenPin.service.impl.DefaultSubscriptionServiceAdapter;
import org.canadianTenPin.service.impl.DefaultUniqueIdGenerator;
import org.canadianTenPin.service.impl.DefaultUserServiceAdapter;
import org.canadianTenPin.service.impl.stub.StubEntitlementCertServiceAdapter;
import org.canadianTenPin.test.AuthMethodInterceptorFactory;
import org.canadianTenPin.test.DateSourceForTesting;
import org.canadianTenPin.test.EnforcerForTesting;
import org.canadianTenPin.test.EventSinkForTesting;
import org.canadianTenPin.test.PKIReaderForTesting;
import org.canadianTenPin.util.DateSource;
import org.canadianTenPin.util.ExpiryDateFunction;
import org.canadianTenPin.util.X509ExtensionUtil;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

import com.google.common.base.Function;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.persist.jpa.JpaPersistModule;

public class CanadianTenPinCommonTestingModule extends CanadianTenPinModule {

    private TestingInterceptor authMethodInterceptor;

    @Override
    public void configure() {
        CanadianTenPinSingletonScope singletonScope = new CanadianTenPinSingletonScope();
        bindScope(CanadianTenPinSingletonScoped.class, singletonScope);
        bind(CanadianTenPinSingletonScope.class).toInstance(singletonScope);

        bind(BeanValidationEventListener.class).toProvider(ValidationListenerProvider.class);
        bind(MessageInterpolator.class).to(CanadianTenPinMessageInterpolator.class);

        install(new JpaPersistModule("default"));
        bind(JPAInitializer.class).asEagerSingleton();

        bind(X509ExtensionUtil.class);

        // allowing folks to override the config in unit tests.
        bindConfig();

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
        bind(OwnerServiceAdapter.class).to(
            DefaultOwnerServiceAdapter.class);
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

        bindInterceptor(Matchers.inPackage(Package.getPackage("org.canadianTenPin.resource")),
            new HttpMethodMatcher(), authMethodInterceptor);

        bind(CertificateRevocationListTask.class);
        // temporary
        bind(IdentityCertServiceAdapter.class).to(
            DefaultIdentityCertServiceAdapter.class);
        bind(PoolRules.class);
        bind(CriteriaRules.class);
        bind(PoolManager.class).to(CanadianTenPinPoolManager.class);
        bind(UniqueIdGenerator.class).to(DefaultUniqueIdGenerator.class);

        bind(Function.class).annotatedWith(Names.named("endDateGenerator"))
            .to(ExpiryDateFunction.class).in(Singleton.class);
    }

    public TestingInterceptor securityInterceptor() {
        return authMethodInterceptor;
    }

    /**
     * Allows overriding the Config bind from a test class.
     */
    protected void bindConfig() {
        bind(Config.class).to(CanadianTenPinCommonTestConfig.class).asEagerSingleton();
    }
}
