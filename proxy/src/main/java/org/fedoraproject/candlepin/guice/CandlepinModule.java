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

import java.util.Properties;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.interceptor.AccessControlInterceptor;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.auth.interceptor.SecurityInterceptor;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.exceptions.CandlepinExceptionMapper;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.impl.CandlepinPKIReader;
import org.fedoraproject.candlepin.pki.impl.CandlepinPKIUtility;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.resource.AdminResource;
import org.fedoraproject.candlepin.resource.CertificateResource;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.resource.ConsumerTypeResource;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.resource.PoolResource;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.resource.RulesResource;
import org.fedoraproject.candlepin.resource.StatusResource;
import org.fedoraproject.candlepin.resource.SubscriptionResource;
import org.fedoraproject.candlepin.resource.SubscriptionTokenResource;
import org.fedoraproject.candlepin.resteasy.interceptor.AuthInterceptor;
import org.fedoraproject.candlepin.util.DateSource;
import org.fedoraproject.candlepin.util.DateSourceImpl;
import org.xnap.commons.i18n.I18n;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.wideplay.warp.persist.jpa.JpaUnit;

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
        bind(CertificateResource.class);
        bind(ConsumerResource.class);
        bind(ConsumerTypeResource.class);        
        bind(PoolResource.class);
        bind(EntitlementResource.class);
        bind(OwnerResource.class);
        bind(ProductResource.class);
        bind(SubscriptionResource.class);
        bind(SubscriptionTokenResource.class);
        bind(DateSource.class).to(DateSourceImpl.class).asEagerSingleton();
        bind(Enforcer.class).to(JavascriptEnforcer.class);
        bind(RulesResource.class);
        bind(AdminResource.class);
        bind(PostEntHelper.class);
        bind(PreEntHelper.class);
        bind(StatusResource.class);
        bind(CandlepinExceptionMapper.class);
        bind(Principal.class).toProvider(PrincipalProvider.class);
        bind(I18n.class).toProvider(I18nProvider.class);
        bind(AuthInterceptor.class);

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
        
        ObjectMapper mapper = new ObjectMapper();
    }

}
