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
package org.fedoraproject.candlepin.test;

import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;

import org.fedoraproject.candlepin.CandlepinCommonTestingModule;
import org.fedoraproject.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.fedoraproject.candlepin.TestingInterceptor;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.guice.TestPrincipalProviderSetter;
import org.fedoraproject.candlepin.model.AttributeCurator;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.model.SubscriptionToken;
import org.fedoraproject.candlepin.model.SubscriptionTokenCurator;
import org.fedoraproject.candlepin.model.SubscriptionsCertificateCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.util.DateSource;
import org.junit.Before;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import com.wideplay.warp.persist.WorkManager;

/**
 * Test fixture for test classes requiring access to the database.
 */
public class DatabaseTestFixture {
    
    protected EntityManagerFactory emf;
    protected Injector injector;
    
    protected OwnerCurator ownerCurator;
    protected ProductCurator productCurator;
    protected ProductServiceAdapter productAdapter;
    protected SubscriptionServiceAdapter subAdapter;
    protected ConsumerCurator consumerCurator;
    protected ConsumerTypeCurator consumerTypeCurator;
    protected SubscriptionsCertificateCurator certificateCurator;
    protected PoolCurator poolCurator;
    protected DateSourceForTesting dateSource;
    protected SpacewalkCertificateCurator spacewalkCertCurator;
    protected EntitlementCurator entitlementCurator;
    protected AttributeCurator attributeCurator;
    protected RulesCurator rulesCurator;
    protected SubscriptionCurator subCurator;
    protected SubscriptionTokenCurator subTokenCurator;
    protected WorkManager unitOfWork;
    protected HttpServletRequest httpServletRequest;
    protected EntitlementCertificateCurator entCertCurator;
    protected CertificateSerialCurator certSerialCurator;
    protected I18n i18n;
    protected TestingInterceptor crudInterceptor;
    protected TestingInterceptor filterInterceptor;
    protected TestingInterceptor securityInterceptor;

    
    @Before
    public void init() {
        Module guiceOverrideModule = getGuiceOverrideModule();
        CandlepinCommonTestingModule testingModule = new CandlepinCommonTestingModule();
        if (guiceOverrideModule == null) {
            injector = Guice.createInjector(
                    testingModule,
                    new CandlepinNonServletEnvironmentTestingModule(),
                    PersistenceService.usingJpa()
                        .across(UnitOfWork.REQUEST)
                        .buildModule()
            );
        }
        else {
            injector = Guice.createInjector(
                Modules.override(testingModule).with(
                    guiceOverrideModule),
                new CandlepinNonServletEnvironmentTestingModule(),
                PersistenceService.usingJpa()
                    .across(UnitOfWork.REQUEST)
                    .buildModule()
            );
        }

        injector.getInstance(EntityManagerFactory.class); 
        emf = injector.getProvider(EntityManagerFactory.class).get();
        
        ownerCurator = injector.getInstance(OwnerCurator.class);
        productCurator = injector.getInstance(ProductCurator.class);
        consumerCurator = injector.getInstance(ConsumerCurator.class);

        consumerTypeCurator = injector.getInstance(ConsumerTypeCurator.class);
        certificateCurator = injector.getInstance(SubscriptionsCertificateCurator.class);
        poolCurator = injector.getInstance(PoolCurator.class);
        spacewalkCertCurator = injector.getInstance(SpacewalkCertificateCurator.class);
        entitlementCurator = injector.getInstance(EntitlementCurator.class);
        attributeCurator = injector.getInstance(AttributeCurator.class);
        rulesCurator = injector.getInstance(RulesCurator.class);
        subCurator = injector.getInstance(SubscriptionCurator.class);
        subTokenCurator = injector.getInstance(SubscriptionTokenCurator.class);
        unitOfWork = injector.getInstance(WorkManager.class);
        
        productAdapter = injector.getInstance(ProductServiceAdapter.class);
        subAdapter = injector.getInstance(SubscriptionServiceAdapter.class);
        entCertCurator = injector.getInstance(EntitlementCertificateCurator.class);
        certSerialCurator = injector.getInstance(CertificateSerialCurator.class);

        i18n = injector.getInstance(I18n.class);
        
        crudInterceptor = testingModule.crudInterceptor();
        filterInterceptor = testingModule.filterInterceptor();
        securityInterceptor = testingModule.securityInterceptor(); 
        
        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));
        
        // Tests run as super admin by default:
//        Owner superAdminOwner = new Owner("superadminowner", "superadminowner");
//        ownerCurator.create(superAdminOwner);
//        setupPrincipal(superAdminOwner, Role.SUPER_ADMIN);
    }
    
    protected Module getGuiceOverrideModule() {
        return null;
    }
        
    protected EntityManager entityManager() {
        return injector.getProvider(EntityManager.class).get();
    }
    
    /**
     * Helper to open a new db transaction. Pretty simple for now, but may 
     * require additional logic and error handling down the road.
     */
    protected void beginTransaction() {
        entityManager().getTransaction().begin();
    }

    /**
     * Helper to commit the current db transaction. Pretty simple for now, but may 
     * require additional logic and error handling down the road.
     */
    protected void commitTransaction() {
        entityManager().getTransaction().commit();
    }

    /**
     * Create an entitlement pool and matching subscription.
     * @return an entitlement pool and matching subscription.
     */
    protected Pool createPoolAndSub(Owner owner, String productId, Long quantity,
        Date startDate, Date endDate) {
        Pool p = new Pool(owner, productId, quantity, startDate, endDate);
        Subscription sub = new Subscription(owner, productId, quantity, startDate,
            endDate, TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);
        p.setSubscriptionId(sub.getId());
        poolCurator.create(p);
        return p;
    }

    protected Owner createOwner() {
        Owner o = new Owner("Test Owner " + TestUtil.randomInt());
        ownerCurator.create(o);
        return o;
    }

    protected Consumer createConsumer(Owner owner) {
        ConsumerType type = new ConsumerType("test-consumer-type-" + TestUtil.randomInt());
        consumerTypeCurator.create(type);
        Consumer c = new Consumer("test-consumer", owner, type);
        consumerCurator.create(c);
        return c;
    }
    
    protected Subscription createSubscription() {
        Subscription sub = new Subscription(createOwner(), 
                                            TestUtil.createProduct().getId(),        
                                            new Long(1000), 
                                            TestUtil.createDate(2000, 1, 1),
                                            TestUtil.createDate(2010, 1, 1), 
                                            TestUtil.createDate(2000, 1, 1));
        subCurator.create(sub);
        return sub;

    }
    
    protected SubscriptionToken createSubscriptionToken() {
        Subscription sub = createSubscription();
       
        SubscriptionToken st = new SubscriptionToken();
        st.setToken("this_is_a_test_token");
       
        st.setSubscription(sub);
        subTokenCurator.create(st);
        return st;
        
    }
    
    protected Entitlement createEntitlement(Owner owner, Consumer consumer, Pool pool, 
            EntitlementCertificate cert) {
        Entitlement toReturn = new Entitlement();
        toReturn.setOwner(owner);
        toReturn.setPool(pool);
        toReturn.setOwner(owner);
        cert.setEntitlement(toReturn);
        toReturn.getCertificates().add(cert);
        return toReturn;
    }
    
    protected EntitlementCertificate createEntitlementCertificate(String key, String cert, 
            BigInteger serial) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        toReturn.setKey(key.getBytes());
        toReturn.setCert(cert.getBytes());
        toReturn.setSerial(serial);
        return toReturn;
    }
    
    protected void setupPrincipal(Owner owner, Role role) { 
        List<Role> roles = new LinkedList<Role>();
        roles.add(role);
        Principal ownerAdmin = new UserPrincipal("someuser", owner, roles);
        
        setupPrincipal(ownerAdmin);
    }

    protected void setupPrincipal(Principal p) {
        // TODO: might be good to get rid of this singleton
        TestPrincipalProviderSetter.get().setPrincipal(p);
    }

}
