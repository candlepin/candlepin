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

import com.google.common.base.Function;
import java.util.Properties;

import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.audit.EventSinkImpl;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.interceptor.AccessControlInterceptor;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.auth.interceptor.SecurityInterceptor;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.controller.CrlGenerator;
import org.fedoraproject.candlepin.exceptions.CandlepinExceptionMapper;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.pinsetter.core.HighlanderJobFactory;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;
import org.fedoraproject.candlepin.pinsetter.tasks.CertificateRevocationListTask;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.impl.CandlepinPKIReader;
import org.fedoraproject.candlepin.pki.impl.CandlepinPKIUtility;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.EnforcerDispatcher;
import org.fedoraproject.candlepin.resource.AdminResource;
import org.fedoraproject.candlepin.resource.AtomFeedResource;
import org.fedoraproject.candlepin.resource.CertificateSerialResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.ConsumerTypeResource;
import org.fedoraproject.candlepin.resource.ContentResource;
import org.fedoraproject.candlepin.resource.CrlResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.EventResource;
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
import org.fedoraproject.candlepin.sync.ConsumerExporter;
import org.fedoraproject.candlepin.sync.ConsumerTypeExporter;
import org.fedoraproject.candlepin.sync.EntitlementCertExporter;
import org.fedoraproject.candlepin.sync.Exporter;
import org.fedoraproject.candlepin.sync.MetaExporter;
import org.fedoraproject.candlepin.sync.RulesExporter;
import org.fedoraproject.candlepin.util.DateSource;
import org.fedoraproject.candlepin.util.DateSourceImpl;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;
import org.xnap.commons.i18n.I18n;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.wideplay.warp.persist.jpa.JpaUnit;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.fedoraproject.candlepin.audit.AMQPBusEventAdapter;
import org.fedoraproject.candlepin.audit.AMQPBusPublisher;
import org.xnap.commons.i18n.I18nFactory;

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
        bind(PKIUtility.class).to(CandlepinPKIUtility.class).asEagerSingleton();
        bind(PKIReader.class).to(CandlepinPKIReader.class).asEagerSingleton();
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
        bind(SubscriptionResource.class);
        bind(SubscriptionTokenResource.class);
        bind(CertificateSerialResource.class);
        bind(CrlResource.class);
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(EnforcerDispatcher.class);
        bind(RulesResource.class);
        bind(AdminResource.class);
        bind(StatusResource.class);
        bind(CandlepinExceptionMapper.class);   
        bind(Principal.class).toProvider(PrincipalProvider.class);

        bind(AuthInterceptor.class);
        bind(JsonProvider.class);
        bind(EventSink.class).to(EventSinkImpl.class);
        bind(HighlanderJobFactory.class);
        bind(PinsetterKernel.class);
        bind(CertificateRevocationListTask.class);
        bind(String.class).annotatedWith(Names.named("crlSignatureAlgo"))
            .toInstance("SHA1withRSA");
                    
        bind(Exporter.class).asEagerSingleton();
        bind(MetaExporter.class);
        bind(ConsumerTypeExporter.class);
        bind(ConsumerExporter.class);
        bind(RulesExporter.class);
        bind(EntitlementCertExporter.class);
        
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
    }

    @Provides
    public I18n provideI18n() {
        HttpServletRequest request;
        Locale locale = null;

        try {
            Provider<HttpServletRequest> requestProvider =
                    this.getProvider(HttpServletRequest.class);
            request = requestProvider.get();
        }
        catch (Exception e) {
            request = null;
        }

        if (request != null) {
            locale = request.getLocale();
        }

        locale = (locale == null) ? Locale.US : locale;

        return I18nFactory.getI18n(
            getClass(),
            locale,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );
    }

}
