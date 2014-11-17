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

import org.candlepin.CandlepinCommonTestingModule;
import org.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.candlepin.TestingInterceptor;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.guice.CandlepinSingletonScope;
import org.candlepin.guice.TestPrincipalProviderSetter;
import org.candlepin.junit.CandlepinLiquibaseResource;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Role;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.util.DateSource;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.persist.PersistFilter;
import com.google.inject.util.Modules;

import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * Test fixture for test classes requiring access to the database.
 */
public class DatabaseTestFixture {

    private static final String DEFAULT_CONTRACT = "SUB349923";
    private static final String DEFAULT_ACCOUNT = "ACC123";
    private static final String DEFAULT_ORDER = "ORD222";

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @ClassRule
    @Rule
    public static CandlepinLiquibaseResource liquibase = new CandlepinLiquibaseResource();

    @Inject private EntityManagerFactory emf;
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private SubscriptionCurator subCurator;
    @Inject private CertificateSerialCurator certSerialCurator;

    private Injector injector;
    private CandlepinSingletonScope cpSingletonScope;

    protected TestingInterceptor securityInterceptor;
    protected DateSourceForTesting dateSource;

    @Before
    public void init() {
        Module guiceOverrideModule = getGuiceOverrideModule();
        CandlepinCommonTestConfig config = new CandlepinCommonTestConfig();
        CandlepinCommonTestingModule testingModule = new CandlepinCommonTestingModule(config);
        if (guiceOverrideModule == null) {
            injector = Guice.createInjector(testingModule,
                new CandlepinNonServletEnvironmentTestingModule());
        }
        else {
            injector = Guice.createInjector(Modules.override(testingModule)
                .with(guiceOverrideModule),
                new CandlepinNonServletEnvironmentTestingModule());
        }
        insertValidationEventListeners(injector);

        cpSingletonScope = injector.getInstance(CandlepinSingletonScope.class);

        // Because all candlepin operations are running in the CandlepinSingletonScope
        // we'll force the instance creations to be done inside the scope.
        // Exit the scope to make sure that it is clean before starting the test.
        cpSingletonScope.exit();
        cpSingletonScope.enter();
        injector.injectMembers(this);
        securityInterceptor = testingModule.securityInterceptor();

        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));

    }

    @After
    public void shutdown() {
        // We are using a singleton for the principal in tests. Make sure we clear it out
        // after every test. TestPrincipalProvider controls the default behavior.
        TestPrincipalProviderSetter.get().setPrincipal(null);
        try {
            injector.getInstance(PersistFilter.class).destroy();
            if (entityManager().isOpen()) {
                entityManager().close();
            }
            if (emf.isOpen()) {
                emf.close();
            }
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
     *
     * If you open a transaction, you'd better close it; otherwise, your test will
     * hang forever.
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
        p.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
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
        TestPrincipalProviderSetter.get().setPrincipal(p);
        return p;
    }

    public Role createAdminRole(Owner owner) {
        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, owner,
            Access.ALL);
        Role role = new Role("testrole" + TestUtil.randomInt());
        role.addPermission(p);
        return role;
    }

    /**
     * There's no way to really get Guice to perform injections on stuff that
     * the JpaPersistModule is creating, so we resort to grabbing the EntityManagerFactory
     * after the fact and adding the Validation EventListener ourselves.
     * @param inj
     */
    private void insertValidationEventListeners(Injector inj) {
        Provider<EntityManagerFactory> emfProvider =
            inj.getProvider(EntityManagerFactory.class);
        HibernateEntityManagerFactory hibernateEntityManagerFactory =
            (HibernateEntityManagerFactory) emfProvider.get();
        SessionFactoryImpl sessionFactoryImpl =
            (SessionFactoryImpl) hibernateEntityManagerFactory.getSessionFactory();
        EventListenerRegistry registry =
            sessionFactoryImpl.getServiceRegistry().getService(EventListenerRegistry.class);

        Provider<BeanValidationEventListener> listenerProvider =
            inj.getProvider(BeanValidationEventListener.class);
        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listenerProvider.get());
    }
}
