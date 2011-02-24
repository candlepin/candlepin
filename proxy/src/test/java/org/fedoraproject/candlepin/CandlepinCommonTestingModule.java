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
package org.fedoraproject.candlepin;

import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.interceptor.AccessControlInterceptor;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.auth.interceptor.SecurityInterceptor;
import org.fedoraproject.candlepin.config.CandlepinCommonTestConfig;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.controller.CandlepinPoolManager;
import org.fedoraproject.candlepin.guice.CandlepinModule;
import org.fedoraproject.candlepin.guice.I18nProvider;
import org.fedoraproject.candlepin.guice.JPAInitializer;
import org.fedoraproject.candlepin.guice.PrincipalProvider;
import org.fedoraproject.candlepin.guice.ScriptEngineProvider;
import org.fedoraproject.candlepin.guice.TestPrincipalProvider;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.test.TestRulesCurator;
import org.fedoraproject.candlepin.pinsetter.core.GuiceJobFactory;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterJobListener;
import org.fedoraproject.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.impl.CandlepinPKIUtility;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.PoolResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.SubscriptionResource;
import org.fedoraproject.candlepin.resource.SubscriptionTokenResource;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultIdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultProductServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultUserServiceAdapter;
import org.fedoraproject.candlepin.service.impl.stub.StubEntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.test.DateSourceForTesting;
import org.fedoraproject.candlepin.test.EnforcerForTesting;
import org.fedoraproject.candlepin.test.EventSinkForTesting;
import org.fedoraproject.candlepin.test.PKIReaderForTesting;
import org.fedoraproject.candlepin.util.DateSource;
import org.fedoraproject.candlepin.util.ExpiryDateFunction;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;
import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

import com.google.common.base.Function;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.wideplay.warp.persist.jpa.JpaUnit;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.policy.PoolRules;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.JsRulesProvider;
import org.fedoraproject.candlepin.policy.js.pool.JsPoolRules;

public class CandlepinCommonTestingModule extends CandlepinModule {

    private TestingInterceptor crudInterceptor;
    private TestingInterceptor securityInterceptor;

    @Override
    public void configure() {

        bind(JPAInitializer.class).asEagerSingleton();
        bindConstant().annotatedWith(JpaUnit.class).to("default");

        bind(X509ExtensionUtil.class);
        bind(Config.class).to(CandlepinCommonTestConfig.class)
            .asEagerSingleton();
        bind(ConsumerResource.class);
        bind(PoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(SubscriptionResource.class);        
        bind(SubscriptionTokenResource.class);
        bind(ProductServiceAdapter.class)
            .to(DefaultProductServiceAdapter.class);
        bind(ProductResource.class);
        bind(DateSource.class).to(DateSourceForTesting.class)
            .asEagerSingleton();
        bind(Enforcer.class).to(EnforcerForTesting.class); // .to(JavascriptEnforcer.class);
        bind(PKIUtility.class).to(CandlepinPKIUtility.class);
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

        bind(JsRulesProvider.class).asEagerSingleton();
        bind(JsRules.class).toProvider(JsRulesProvider.class);
        
        bind(PrincipalProvider.class).to(TestPrincipalProvider.class);
        bind(Principal.class).toProvider(TestPrincipalProvider.class);
        bind(EventSink.class).to(EventSinkForTesting.class);

        SecurityInterceptor se = new SecurityInterceptor();
        requestInjection(se);
        securityInterceptor = new TestingInterceptor(se);

        bindInterceptor(Matchers.inPackage(Package
            .getPackage("org.fedoraproject.candlepin.resource")), Matchers
            .any(), securityInterceptor);
        bindInterceptor(Matchers.subclassesOf(AbstractHibernateCurator.class),
            Matchers.annotatedWith(AllowRoles.class), securityInterceptor);

        AccessControlInterceptor crud = new AccessControlInterceptor();
        requestInjection(crud);
        crudInterceptor = new TestingInterceptor(crud);

        bindInterceptor(Matchers.subclassesOf(AbstractHibernateCurator.class),
            Matchers.annotatedWith(EnforceAccessControl.class), crudInterceptor);
        bind(CertificateRevocationListTask.class);
        bind(String.class).annotatedWith(Names.named("crlSignatureAlgo"))
            .toInstance("SHA1withRSA");
        // temporary
        bind(IdentityCertServiceAdapter.class).to(
            DefaultIdentityCertServiceAdapter.class);
        bind(PoolRules.class).to(JsPoolRules.class);
        bind(PoolManager.class).to(CandlepinPoolManager.class);
        
        //flexible end date for identity certificates
        bind(Function.class).annotatedWith(Names.named("endDateGenerator"))
            .to(ExpiryDateFunction.class).in(Singleton.class);
    }

    public TestingInterceptor crudInterceptor() {
        return crudInterceptor;
    }

    public TestingInterceptor securityInterceptor() {
        return securityInterceptor;
    }
}
