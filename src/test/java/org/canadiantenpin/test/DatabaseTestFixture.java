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
package org.canadianTenPin.test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;

import org.canadianTenPin.CanadianTenPinCommonTestingModule;
import org.canadianTenPin.CanadianTenPinNonServletEnvironmentTestingModule;
import org.canadianTenPin.TestingInterceptor;
import org.canadianTenPin.auth.Access;
import org.canadianTenPin.auth.Principal;
import org.canadianTenPin.auth.UserPrincipal;
import org.canadianTenPin.auth.permissions.OwnerPermission;
import org.canadianTenPin.auth.permissions.Permission;
import org.canadianTenPin.auth.permissions.PermissionFactory;
import org.canadianTenPin.auth.permissions.PermissionFactory.PermissionType;
import org.canadianTenPin.controller.CanadianTenPinPoolManager;
import org.canadianTenPin.guice.CanadianTenPinSingletonScope;
import org.canadianTenPin.guice.TestPrincipalProviderSetter;
import org.canadianTenPin.model.CertificateSerial;
import org.canadianTenPin.model.CertificateSerialCurator;
import org.canadianTenPin.model.Consumer;
import org.canadianTenPin.model.ConsumerContentOverrideCurator;
import org.canadianTenPin.model.ConsumerCurator;
import org.canadianTenPin.model.ConsumerType;
import org.canadianTenPin.model.ConsumerTypeCurator;
import org.canadianTenPin.model.ContentCurator;
import org.canadianTenPin.model.Entitlement;
import org.canadianTenPin.model.EntitlementCertificate;
import org.canadianTenPin.model.EntitlementCertificateCurator;
import org.canadianTenPin.model.EntitlementCurator;
import org.canadianTenPin.model.EnvironmentContentCurator;
import org.canadianTenPin.model.EnvironmentCurator;
import org.canadianTenPin.model.EventCurator;
import org.canadianTenPin.model.Owner;
import org.canadianTenPin.model.OwnerCurator;
import org.canadianTenPin.model.PermissionBlueprint;
import org.canadianTenPin.model.PermissionBlueprintCurator;
import org.canadianTenPin.model.Pool;
import org.canadianTenPin.model.PoolAttributeCurator;
import org.canadianTenPin.model.PoolCurator;
import org.canadianTenPin.model.Product;
import org.canadianTenPin.model.ProductAttribute;
import org.canadianTenPin.model.ProductAttributeCurator;
import org.canadianTenPin.model.ProductCertificateCurator;
import org.canadianTenPin.model.ProductCurator;
import org.canadianTenPin.model.ProductPoolAttribute;
import org.canadianTenPin.model.ProductPoolAttributeCurator;
import org.canadianTenPin.model.ProvidedProduct;
import org.canadianTenPin.model.Role;
import org.canadianTenPin.model.RoleCurator;
import org.canadianTenPin.model.RulesCurator;
import org.canadianTenPin.model.SourceSubscription;
import org.canadianTenPin.model.StatisticCurator;
import org.canadianTenPin.model.Subscription;
import org.canadianTenPin.model.SubscriptionCurator;
import org.canadianTenPin.model.SubscriptionsCertificateCurator;
import org.canadianTenPin.model.UeberCertificateGenerator;
import org.canadianTenPin.model.UserCurator;
import org.canadianTenPin.model.activationkeys.ActivationKey;
import org.canadianTenPin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.canadianTenPin.model.activationkeys.ActivationKeyCurator;
import org.canadianTenPin.service.EntitlementCertServiceAdapter;
import org.canadianTenPin.service.OwnerServiceAdapter;
import org.canadianTenPin.service.ProductServiceAdapter;
import org.canadianTenPin.service.SubscriptionServiceAdapter;
import org.canadianTenPin.service.UniqueIdGenerator;
import org.canadianTenPin.util.DateSource;
import org.canadianTenPin.util.ServiceLevelValidator;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.ejb.HibernateEntityManagerFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.After;
import org.junit.Before;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.util.Modules;

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
    protected OwnerServiceAdapter ownerAdapter;
    protected ConsumerCurator consumerCurator;
    protected ConsumerTypeCurator consumerTypeCurator;
    protected ConsumerContentOverrideCurator consumerContentOverrideCurator;
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
    protected PermissionBlueprintCurator permissionCurator;
    protected RoleCurator roleCurator;
    protected EnvironmentCurator envCurator;
    protected I18n i18n;
    protected TestingInterceptor securityInterceptor;
    protected EntitlementCertServiceAdapter entitlementCertService;
    protected CanadianTenPinPoolManager poolManager;
    protected StatisticCurator statisticCurator;
    protected UniqueIdGenerator uniqueIdGenerator;
    protected UeberCertificateGenerator ueberCertGenerator;
    protected CanadianTenPinSingletonScope cpSingletonScope;
    protected PermissionFactory permFactory;
    protected ActivationKeyContentOverrideCurator activationKeyContentOverrideCurator;
    protected ServiceLevelValidator serviceLevelValidator;

    @Before
    public void init() {
        Module guiceOverrideModule = getGuiceOverrideModule();
        CanadianTenPinCommonTestingModule testingModule = new CanadianTenPinCommonTestingModule();
        if (guiceOverrideModule == null) {
            injector = Guice.createInjector(testingModule,
                new CanadianTenPinNonServletEnvironmentTestingModule());
        }
        else {
            injector = Guice.createInjector(Modules.override(testingModule)
                .with(guiceOverrideModule),
                new CanadianTenPinNonServletEnvironmentTestingModule());
        }
        insertValidationEventListeners(injector);

        cpSingletonScope = injector.getInstance(CanadianTenPinSingletonScope.class);
        // Because all canadianTenPin operations are running in the CanadianTenPinSingletonScope
        // we'll force the instance creations to be done inside the scope.
        // Exit the scope to make sure that it is clean before starting the test.
        cpSingletonScope.exit();
        cpSingletonScope.enter();

        injector.getInstance(EntityManagerFactory.class);
        emf = injector.getProvider(EntityManagerFactory.class).get();

        ownerCurator = injector.getInstance(OwnerCurator.class);
        userCurator = injector.getInstance(UserCurator.class);
        productCurator = injector.getInstance(ProductCurator.class);
        productCertificateCurator = injector
            .getInstance(ProductCertificateCurator.class);
        consumerCurator = injector.getInstance(ConsumerCurator.class);
        eventCurator = injector.getInstance(EventCurator.class);
        permissionCurator = injector.getInstance(PermissionBlueprintCurator.class);
        roleCurator = injector.getInstance(RoleCurator.class);

        consumerTypeCurator = injector.getInstance(ConsumerTypeCurator.class);
        consumerContentOverrideCurator = injector.getInstance(
            ConsumerContentOverrideCurator.class);
        activationKeyContentOverrideCurator = injector.getInstance(
            ActivationKeyContentOverrideCurator.class);
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
        ownerAdapter = injector.getInstance(OwnerServiceAdapter.class);
        entCertCurator = injector
            .getInstance(EntitlementCertificateCurator.class);
        certSerialCurator = injector
            .getInstance(CertificateSerialCurator.class);
        entitlementCertService = injector
            .getInstance(EntitlementCertServiceAdapter.class);
        poolManager = injector.getInstance(CanadianTenPinPoolManager.class);
        statisticCurator = injector.getInstance(StatisticCurator.class);
        i18n = injector.getInstance(I18n.class);
        uniqueIdGenerator = injector.getInstance(UniqueIdGenerator.class);
        ueberCertGenerator = injector.getInstance(UeberCertificateGenerator.class);
        permFactory = injector.getInstance(PermissionFactory.class);

        securityInterceptor = testingModule.securityInterceptor();

        dateSource = (DateSourceForTesting) injector
            .getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));
        serviceLevelValidator = injector.getInstance(ServiceLevelValidator.class);
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
        // TODO: might be good to get rid of this singleton
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
     * @param injector
     */
    private void insertValidationEventListeners(Injector injector) {
        Provider<EntityManagerFactory> emfProvider =
            injector.getProvider(EntityManagerFactory.class);
        HibernateEntityManagerFactory hibernateEntityManagerFactory =
            (HibernateEntityManagerFactory) emfProvider.get();
        SessionFactoryImpl sessionFactoryImpl =
            (SessionFactoryImpl) hibernateEntityManagerFactory.getSessionFactory();
        EventListenerRegistry registry =
            sessionFactoryImpl.getServiceRegistry().getService(EventListenerRegistry.class);

        Provider<BeanValidationEventListener> listenerProvider =
            injector.getProvider(BeanValidationEventListener.class);
        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listenerProvider.get());
    }

}
