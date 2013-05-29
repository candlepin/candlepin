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
package org.candlepin.test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.util.Modules;

import org.candlepin.CandlepinCommonTestingModule;
import org.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.candlepin.TestingInterceptor;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.guice.CandlepinSingletonScope;
import org.candlepin.guice.TestPrincipalProviderSetter;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.ActivationKeyCurator;
import org.candlepin.model.ArchCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.EventCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerPermission;
import org.candlepin.model.OwnerPermissionCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttributeCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductAttributeCurator;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProductPoolAttributeCurator;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.StatisticCurator;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.model.SubscriptionsCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UserCurator;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.util.DateSource;
import org.hibernate.ejb.HibernateEntityManagerImplementor;
import org.junit.After;
import org.junit.Before;
import org.xnap.commons.i18n.I18n;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;

/**
 * Test fixture for test classes requiring access to the database.
 */
public class DatabaseTestFixture {

    private static final String DEFAULT_CONTRACT = "SUB349923";
    private static final String DEFAULT_ACCOUNT = "ACC123";
    private static final String DEFAULT_ORDER = "ORD222";

    protected EntityManagerFactory emf;
    protected Injector injector;

    protected OwnerCurator ownerCurator;
    protected UserCurator userCurator;
    protected ProductCurator productCurator;
    protected ProductCertificateCurator productCertificateCurator;
    protected ProductServiceAdapter productAdapter;
    protected SubscriptionServiceAdapter subAdapter;
    protected ConsumerCurator consumerCurator;
    protected ConsumerTypeCurator consumerTypeCurator;
    protected SubscriptionsCertificateCurator certificateCurator;
    protected PoolCurator poolCurator;
    protected PoolAttributeCurator poolAttributeCurator;
    protected ProductPoolAttributeCurator productPoolAttributeCurator;
    protected DateSourceForTesting dateSource;
    protected EntitlementCurator entitlementCurator;
    protected EnvironmentContentCurator envContentCurator;
    protected ProductAttributeCurator attributeCurator;
    protected RulesCurator rulesCurator;
    protected EventCurator eventCurator;
    protected SubscriptionCurator subCurator;
    protected ActivationKeyCurator activationKeyCurator;
    protected ContentCurator contentCurator;
    protected UnitOfWork unitOfWork;
    protected HttpServletRequest httpServletRequest;
    protected EntitlementCertificateCurator entCertCurator;
    protected CertificateSerialCurator certSerialCurator;
    protected OwnerPermissionCurator permissionCurator;
    protected RoleCurator roleCurator;
    protected EnvironmentCurator envCurator;
    protected I18n i18n;
    protected TestingInterceptor securityInterceptor;
    protected EntitlementCertServiceAdapter entitlementCertService;
    protected CandlepinPoolManager poolManager;
    protected StatisticCurator statisticCurator;
    protected ArchCurator archCurator;
    protected UniqueIdGenerator uniqueIdGenerator;
    protected UeberCertificateGenerator ueberCertGenerator;
    protected CandlepinSingletonScope cpSingletonScope;

    private PersistService persistanceService;

    @Before
    public void init() {
        Module guiceOverrideModule = getGuiceOverrideModule();
        CandlepinCommonTestingModule testingModule = new CandlepinCommonTestingModule();
        if (guiceOverrideModule == null) {
            injector = Guice.createInjector(testingModule,
                new CandlepinNonServletEnvironmentTestingModule());
              //  PersistService.usingJpa().across(UnitOfWork.REQUEST)
//                    .buildModule());
        }
        else {
            injector = Guice.createInjector(Modules.override(testingModule)
                .with(guiceOverrideModule),
                new CandlepinNonServletEnvironmentTestingModule());
        }

        cpSingletonScope = injector.getInstance(CandlepinSingletonScope.class);
        // Because all candlepin operations are running in the CandlepinSingletonScope
        // we'll force the instance creations to be done inside the scope.
        // Exit the scope to make sure that it is clean before starting the test.
        cpSingletonScope.exit();
        cpSingletonScope.enter();
        persistanceService = injector.getInstance(PersistService.class);

        injector.getInstance(EntityManagerFactory.class);
        emf = injector.getProvider(EntityManagerFactory.class).get();

        ownerCurator = injector.getInstance(OwnerCurator.class);
        userCurator = injector.getInstance(UserCurator.class);
        productCurator = injector.getInstance(ProductCurator.class);
        productCertificateCurator = injector
            .getInstance(ProductCertificateCurator.class);
        consumerCurator = injector.getInstance(ConsumerCurator.class);
        eventCurator = injector.getInstance(EventCurator.class);
        permissionCurator = injector.getInstance(OwnerPermissionCurator.class);
        roleCurator = injector.getInstance(RoleCurator.class);

        consumerTypeCurator = injector.getInstance(ConsumerTypeCurator.class);
        certificateCurator = injector
            .getInstance(SubscriptionsCertificateCurator.class);
        poolCurator = injector.getInstance(PoolCurator.class);
        poolAttributeCurator = injector.getInstance(PoolAttributeCurator.class);
        productPoolAttributeCurator = injector
            .getInstance(ProductPoolAttributeCurator.class);
        entitlementCurator = injector.getInstance(EntitlementCurator.class);
        attributeCurator = injector.getInstance(ProductAttributeCurator.class);
        rulesCurator = injector.getInstance(RulesCurator.class);
        subCurator = injector.getInstance(SubscriptionCurator.class);
        activationKeyCurator = injector.getInstance(ActivationKeyCurator.class);
        contentCurator = injector.getInstance(ContentCurator.class);
        envCurator = injector.getInstance(EnvironmentCurator.class);
        envContentCurator = injector.getInstance(EnvironmentContentCurator.class);
        unitOfWork = injector.getInstance(UnitOfWork.class);

        productAdapter = injector.getInstance(ProductServiceAdapter.class);
        subAdapter = injector.getInstance(SubscriptionServiceAdapter.class);
        entCertCurator = injector
            .getInstance(EntitlementCertificateCurator.class);
        certSerialCurator = injector
            .getInstance(CertificateSerialCurator.class);
        entitlementCertService = injector
            .getInstance(EntitlementCertServiceAdapter.class);
        poolManager = injector.getInstance(CandlepinPoolManager.class);
        statisticCurator = injector.getInstance(StatisticCurator.class);
        archCurator = injector.getInstance(ArchCurator.class);
        i18n = injector.getInstance(I18n.class);
        uniqueIdGenerator = injector.getInstance(UniqueIdGenerator.class);
        ueberCertGenerator = injector.getInstance(UeberCertificateGenerator.class);

        securityInterceptor = testingModule.securityInterceptor();

        dateSource = (DateSourceForTesting) injector
            .getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));
    }

    @After
    public void shutdown() {
        try {
            injector.getInstance(PersistFilter.class).destroy();

            HibernateEntityManagerImplementor hem =
                (HibernateEntityManagerImplementor) entityManager();
            Connection connection = hem.getSession().connection();
            try {
                connection.createStatement().execute("SHUTDOWN");
            }
            catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            entityManager().close();
            emf.close();
        }
        finally {
            cpSingletonScope.exit();
        }
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
     * Helper to commit the current db transaction. Pretty simple for now, but
     * may require additional logic and error handling down the road.

     */
    protected void commitTransaction() {
        entityManager().getTransaction().commit();
    }

    /**
     * Create an entitlement pool and matching subscription.
     *
     * @return an entitlement pool and matching subscription.
     */
    protected Pool createPoolAndSub(Owner owner, Product product,
        Long quantity, Date startDate, Date endDate) {
        Pool p = new Pool(owner, product.getId(), product.getName(),
            new HashSet<ProvidedProduct>(), quantity, startDate, endDate,
            DEFAULT_CONTRACT, DEFAULT_ACCOUNT, DEFAULT_ORDER);
        Subscription sub = new Subscription(owner, product,
            new HashSet<Product>(), quantity, startDate, endDate,
            TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);
        p.setSubscriptionId(sub.getId());
        for (ProductAttribute pa : product.getAttributes()) {
            p.addProductAttribute(new ProductPoolAttribute(pa.getName(),
                pa.getValue(), product.getId()));
        }
        return poolCurator.create(p);
    }

    protected Owner createOwner() {
        Owner o = new Owner("Test Owner " + TestUtil.randomInt());
        ownerCurator.create(o);
        return o;
    }

    protected Consumer createConsumer(Owner owner) {
        ConsumerType type = new ConsumerType("test-consumer-type-" +
            TestUtil.randomInt());
        consumerTypeCurator.create(type);
        Consumer c = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(c);
        return c;
    }

    protected Subscription createSubscription() {
        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Subscription sub = new Subscription(createOwner(),
                                            p, new HashSet<Product>(),
                                            1000L,
                                            TestUtil.createDate(2000, 1, 1),
                                            TestUtil.createDate(2010, 1, 1),
                                            TestUtil.createDate(2000, 1, 1));
        subCurator.create(sub);
        return sub;

    }

    protected ActivationKey createActivationKey(Owner owner) {
        return TestUtil.createActivationKey(owner, null);
    }

    protected Entitlement createEntitlement(Owner owner, Consumer consumer,
        Pool pool, EntitlementCertificate cert) {
        return TestUtil.createEntitlement(owner, consumer, pool, cert);
    }

    protected EntitlementCertificate createEntitlementCertificate(String key,
        String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Date());
        certSerialCurator.create(certSerial);
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }

    protected Principal setupPrincipal(Owner owner, Access role) {
        return setupPrincipal("someuser", owner, role);
    }

    protected Principal setupPrincipal(String username, Owner owner, Access verb) {
        OwnerPermission p = new OwnerPermission(owner, verb);
        // Only need a detached owner permission here:
        Principal ownerAdmin = new UserPrincipal(username, Arrays.asList(new Permission[] {
            p}), false);
        setupPrincipal(ownerAdmin);
        return ownerAdmin;
    }

    protected Principal setupAdminPrincipal(String username) {
        UserPrincipal principal = new UserPrincipal(username, null, true);
        setupPrincipal(principal);

        return principal;
    }

    protected Principal setupPrincipal(Principal p) {
        // TODO: might be good to get rid of this singleton
        TestPrincipalProviderSetter.get().setPrincipal(p);
        return p;
    }

    public Role createAdminRole(Owner owner) {
        OwnerPermission p = new OwnerPermission(owner, Access.ALL);
        Role role = new Role("testrole" + TestUtil.randomInt());
        role.addPermission(p);
        return role;
    }

}
