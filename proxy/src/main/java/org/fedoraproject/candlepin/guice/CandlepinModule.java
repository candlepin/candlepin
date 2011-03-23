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
package org.fedoraproject.candlepin.guice;

import org.fedoraproject.candlepin.audit.AMQPBusEventAdapter;
import org.fedoraproject.candlepin.audit.AMQPBusPublisher;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.audit.EventSinkImpl;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.interceptor.AccessControlInterceptor;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.auth.interceptor.SecurityInterceptor;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.controller.CandlepinPoolManager;
import org.fedoraproject.candlepin.controller.CrlGenerator;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.exceptions.CandlepinExceptionMapper;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.pinsetter.core.GuiceJobFactory;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterJobListener;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;
import org.fedoraproject.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.fedoraproject.candlepin.pinsetter.tasks.JobCleaner;
import org.fedoraproject.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.impl.BouncyCastlePKIReader;
import org.fedoraproject.candlepin.pki.impl.BouncyCastlePKIUtility;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.EnforcerDispatcher;
import org.fedoraproject.candlepin.policy.PoolRules;
import org.fedoraproject.candlepin.policy.js.JsRules;
import org.fedoraproject.candlepin.policy.js.JsRulesProvider;
import org.fedoraproject.candlepin.policy.js.pool.JsPoolRules;
import org.fedoraproject.candlepin.resource.AdminResource;
import org.fedoraproject.candlepin.resource.AtomFeedResource;
import org.fedoraproject.candlepin.resource.CertificateSerialResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.ConsumerTypeResource;
import org.fedoraproject.candlepin.resource.ContentResource;
import org.fedoraproject.candlepin.resource.CrlResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.EventResource;
import org.fedoraproject.candlepin.resource.JobResource;
import org.fedoraproject.candlepin.resource.MigrationResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.PoolResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.RootResource;
import org.fedoraproject.candlepin.resource.RulesResource;
import org.fedoraproject.candlepin.resource.StatusResource;
import org.fedoraproject.candlepin.resource.SubscriptionResource;
import org.fedoraproject.candlepin.resource.SubscriptionTokenResource;
import org.fedoraproject.candlepin.resteasy.JsonProvider;
import org.fedoraproject.candlepin.resteasy.interceptor.AuthInterceptor;
import org.fedoraproject.candlepin.resteasy.interceptor.PinsetterAsyncInterceptor;
import org.fedoraproject.candlepin.sync.ConsumerExporter;
import org.fedoraproject.candlepin.sync.ConsumerTypeExporter;
import org.fedoraproject.candlepin.sync.EntitlementCertExporter;
import org.fedoraproject.candlepin.sync.Exporter;
import org.fedoraproject.candlepin.sync.MetaExporter;
import org.fedoraproject.candlepin.sync.RulesExporter;
import org.fedoraproject.candlepin.util.DateSource;
import org.fedoraproject.candlepin.util.DateSourceImpl;
import org.fedoraproject.candlepin.util.ExpiryDateFunction;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;

import com.google.common.base.Function;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.wideplay.warp.persist.jpa.JpaUnit;

import org.quartz.JobListener;
import org.quartz.spi.JobFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Properties;

/**
 * CandlepinProductionConfiguration
 */
public class CandlepinModule extends AbstractModule {

    @Override
    public void configure() {
        bind(JPAInitializer.class).asEagerSingleton();

        bind(Properties.class).annotatedWith(JpaUnit.class).toInstance(
            new Config().jpaConfiguration());

        // We default to test persistence unit (HSQL),
        // /etc/candlepin/candlepin.conf
        // will override:
        bindConstant().annotatedWith(JpaUnit.class).to("default");

        bind(Config.class);
        bind(PKIUtility.class).to(BouncyCastlePKIUtility.class).asEagerSingleton();
        bind(PKIReader.class).to(BouncyCastlePKIReader.class).asEagerSingleton();
        bind(X509ExtensionUtil.class);
        bind(CrlGenerator.class);
        bind(ConsumerResource.class);
        bind(ConsumerTypeResource.class);
        bind(ContentResource.class);
        bind(AtomFeedResource.class);
        bind(EventResource.class);
        bind(PoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(RootResource.class);
        bind(ProductResource.class);
        bind(MigrationResource.class);
        bind(SubscriptionResource.class);
        bind(SubscriptionTokenResource.class);
        bind(CertificateSerialResource.class);
        bind(CrlResource.class);
        bind(JobResource.class);
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(EnforcerDispatcher.class);
        bind(PoolManager.class).to(CandlepinPoolManager.class);
        bind(PoolRules.class).to(JsPoolRules.class);
        bind(RulesResource.class);
        bind(AdminResource.class);
        bind(StatusResource.class);
        bind(CandlepinExceptionMapper.class);   
        bind(Principal.class).toProvider(PrincipalProvider.class);
        bind(JsRulesProvider.class).asEagerSingleton();
        bind(JsRules.class).toProvider(JsRulesProvider.class);

        bind(I18n.class).toProvider(I18nProvider.class);
        bind(AuthInterceptor.class);
        bind(PinsetterAsyncInterceptor.class);
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
        
        // The order in which interceptors are bound is important!
        // We need role enforcement to be executed before access control
        Matcher resourcePkgMatcher = Matchers.inPackage(Package.getPackage(
            "org.fedoraproject.candlepin.resource"));
        SecurityInterceptor securityEnforcer = new SecurityInterceptor();
        requestInjection(securityEnforcer);
        bindInterceptor(resourcePkgMatcher, Matchers.any(), securityEnforcer);
        
        bindInterceptor(
            Matchers.subclassesOf(AbstractHibernateCurator.class),
            Matchers.annotatedWith(AllowRoles.class), 
            securityEnforcer);
        
        AccessControlInterceptor accessControlInterceptor = new AccessControlInterceptor();
        requestInjection(accessControlInterceptor);
        
        bindInterceptor(
            Matchers.subclassesOf(AbstractHibernateCurator.class),
            Matchers.annotatedWith(EnforceAccessControl.class), 
            accessControlInterceptor);
        
        //amqp stuff below...
        
        bind(Function.class).annotatedWith(Names.named("abc"))
                .to(AMQPBusEventAdapter.class).in(Singleton.class);
      //for lazy loading.
        bind(AMQPBusPublisher.class).toProvider(AMQPBusPubProvider.class) 
                .in(Singleton.class);
        
        //flexible end date for identity certificates
        bind(Function.class).annotatedWith(Names.named("endDateGenerator"))
            .to(ExpiryDateFunction.class).in(Singleton.class);
    }
}
