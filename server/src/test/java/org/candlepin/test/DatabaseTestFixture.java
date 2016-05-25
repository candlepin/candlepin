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

import static org.mockito.Mockito.*;

import org.candlepin.TestingInterceptor;
import org.candlepin.TestingModules;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.TestPrincipalProviderSetter;
import org.candlepin.junit.CandlepinLiquibaseResource;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Role;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.resteasy.ResourceLocatorMap;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.DateSource;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

    @Inject protected OwnerCurator ownerCurator;
    @Inject protected OwnerProductCurator ownerProductCurator;
    @Inject protected ProductCurator productCurator;
    @Inject protected PoolCurator poolCurator;
    @Inject protected ConsumerCurator consumerCurator;
    @Inject protected ConsumerTypeCurator consumerTypeCurator;
    @Inject protected CertificateSerialCurator certSerialCurator;
    @Inject protected ContentCurator contentCurator;
    @Inject protected SubscriptionServiceAdapter subAdapter;
    @Inject protected ResourceLocatorMap locatorMap;

    private static Injector parentInjector;
    private Injector injector;
    private CandlepinRequestScope cpRequestScope;

    protected TestingInterceptor securityInterceptor;
    protected DateSourceForTesting dateSource;


    @BeforeClass
    public static void initClass() {
        parentInjector = Guice.createInjector(new TestingModules.JpaModule());
        insertValidationEventListeners(parentInjector);
    }

    @Before
    public void init() {
        CandlepinCommonTestConfig config = new CandlepinCommonTestConfig();
        Module testingModule = new TestingModules.StandardTest(config);
        injector = parentInjector.createChildInjector(
            Modules.override(testingModule).with(getGuiceOverrideModule()));

        locatorMap = injector.getInstance(ResourceLocatorMap.class);
        locatorMap.init();
        securityInterceptor = injector.getInstance(TestingInterceptor.class);

        cpRequestScope = injector.getInstance(CandlepinRequestScope.class);

        // Because all candlepin operations are running in the CandlepinRequestScope
        // we'll force the instance creations to be done inside the scope.
        // Exit the scope to make sure that it is clean before starting the test.
        cpRequestScope.exit();
        cpRequestScope.enter();
        injector.injectMembers(this);

        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));

        HttpServletRequest req = parentInjector.getInstance(HttpServletRequest.class);
        when(req.getAttribute("username")).thenReturn("mock_user");
    }

    @After
    public void shutdown() {
        cpRequestScope.exit();

        // We are using a singleton for the principal in tests. Make sure we clear it out
        // after every test. TestPrincipalProvider controls the default behavior.
        TestPrincipalProviderSetter.get().setPrincipal(null);
        EntityManager em = parentInjector.getInstance(EntityManager.class);
        em.clear();

        reset(parentInjector.getInstance(HttpServletRequest.class));
        reset(parentInjector.getInstance(HttpServletResponse.class));
    }

    @AfterClass
    public static void destroy() {
        parentInjector.getInstance(PersistFilter.class).destroy();
        EntityManager em = parentInjector.getInstance(EntityManager.class);
        if (em.isOpen()) {
            em.close();
        }
        EntityManager emf = parentInjector.getInstance(EntityManager.class);
        if (emf.isOpen()) {
            emf.close();
        }
    }

    protected Module getGuiceOverrideModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                // NO OP
            }
        };
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

    protected void rollbackTransaction() {
        entityManager().getTransaction().rollback();
    }

    /**
     * Create an entitlement pool.
     *
     * @return an entitlement pool
     */
    protected Pool createPool(Owner owner, Product product, Long quantity, Date startDate, Date endDate) {
        Pool p = new Pool(
            owner,
            product,
            new HashSet<Product>(),
            quantity,
            startDate,
            endDate,
            DEFAULT_CONTRACT,
            DEFAULT_ACCOUNT,
            DEFAULT_ORDER
        );

        p.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        return poolCurator.create(p);
    }

    protected Owner createOwner() {
        Owner o = new Owner("Test Owner " + TestUtil.randomInt());
        ownerCurator.create(o);
        return o;
    }

    protected Owner createOwner(String key, String name) {
        Owner owner = TestUtil.createOwner(key, name);
        this.ownerCurator.create(owner);

        return owner;
    }

    protected Product createProduct(String id, String name, Owner owner) {
        Product product = TestUtil.createProduct(id, name, owner);
        this.productCurator.create(product);

        return product;
    }

    protected Consumer createConsumer(Owner owner) {
        ConsumerType type = new ConsumerType("test-consumer-type-" +
            TestUtil.randomInt());
        consumerTypeCurator.create(type);
        Consumer c = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(c);
        return c;
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
    private static void insertValidationEventListeners(Injector inj) {
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
