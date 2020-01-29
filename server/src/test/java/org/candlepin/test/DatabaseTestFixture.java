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

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import org.candlepin.TestingInterceptor;
import org.candlepin.TestingModules;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.TestPrincipalProviderSetter;
import org.candlepin.junit.LiquibaseExtension;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.UserCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.util.DateSource;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.persist.PersistFilter;
import com.google.inject.util.Modules;

import org.hibernate.Session;
import org.hibernate.cfg.beanvalidation.BeanValidationEventListener;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * Test fixture for test classes requiring access to the database.
 */
@ExtendWith(LiquibaseExtension.class)
public class DatabaseTestFixture {
    protected static Logger log = LoggerFactory.getLogger(DatabaseTestFixture.class);

    private static final String DEFAULT_CONTRACT = "SUB349923";
    private static final String DEFAULT_ACCOUNT = "ACC123";
    private static final String DEFAULT_ORDER = "ORD222";

    protected Configuration config;

    @Inject protected ActivationKeyCurator activationKeyCurator;
    @Inject protected ActivationKeyContentOverrideCurator activationKeyContentOverrideCurator;
    @Inject protected AsyncJobStatusCurator asyncJobCurator;
    @Inject protected CdnCurator cdnCurator;
    @Inject protected ConsumerCurator consumerCurator;
    @Inject protected ConsumerTypeCurator consumerTypeCurator;
    @Inject protected ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Inject protected CertificateSerialCurator certSerialCurator;
    @Inject protected ContentCurator contentCurator;
    @Inject protected EntitlementCurator entitlementCurator;
    @Inject protected EntitlementCertificateCurator entitlementCertificateCurator;
    @Inject protected EnvironmentCurator environmentCurator;
    @Inject protected EnvironmentContentCurator environmentContentCurator;
    @Inject protected IdentityCertificateCurator identityCertificateCurator;
    @Inject protected ImportRecordCurator importRecordCurator;
    @Inject protected OwnerContentCurator ownerContentCurator;
    @Inject protected OwnerCurator ownerCurator;
    @Inject protected OwnerInfoCurator ownerInfoCurator;
    @Inject protected OwnerProductCurator ownerProductCurator;
    @Inject protected ProductCertificateCurator productCertificateCurator;
    @Inject protected ProductCurator productCurator;
    @Inject protected PoolCurator poolCurator;
    @Inject protected RoleCurator roleCurator;
    @Inject protected UserCurator userCurator;

    @Inject protected PermissionFactory permissionFactory;
    @Inject protected AnnotationLocator annotationLocator;
    @Inject protected ModelTranslator modelTranslator;

    private static Injector parentInjector;
    protected Injector injector;
    private CandlepinRequestScope cpRequestScope;

    protected TestingInterceptor securityInterceptor;
    protected DateSourceForTesting dateSource;
    protected I18n i18n;
    protected Provider<I18n> i18nProvider = () -> i18n;

    @BeforeAll
    public static void initClass() {
        parentInjector = Guice.createInjector(new TestingModules.JpaModule());
        insertValidationEventListeners(parentInjector);
    }

    /**
     * There's no way to really get Guice to perform injections on stuff that
     * the JpaPersistModule is creating, so we resort to grabbing the EntityManagerFactory
     * after the fact and adding the Validation EventListener ourselves.
     * @param inj
     */
    private static void insertValidationEventListeners(Injector inj) {
        Provider<EntityManagerFactory> emfProvider = inj.getProvider(EntityManagerFactory.class);
        SessionFactoryImpl sessionFactoryImpl = (SessionFactoryImpl) emfProvider.get();
        EventListenerRegistry registry = sessionFactoryImpl
            .getServiceRegistry()
            .getService(EventListenerRegistry.class);

        Provider<BeanValidationEventListener> listenerProvider =
            inj.getProvider(BeanValidationEventListener.class);

        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listenerProvider.get());
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listenerProvider.get());
    }

    // Need a before each here and a Liquibase extension...
    @BeforeEach
    public void init() throws Exception {
        this.init(true);
    }

    public void init(boolean beginTransaction) throws Exception {
        this.config = new CandlepinCommonTestConfig();
        Module testingModule = new TestingModules.StandardTest(this.config);
        this.injector = parentInjector.createChildInjector(
            Modules.override(testingModule).with(getGuiceOverrideModule()));

        annotationLocator = this.injector.getInstance(AnnotationLocator.class);
        annotationLocator.init();

        securityInterceptor = this.injector.getInstance(TestingInterceptor.class);

        cpRequestScope = injector.getInstance(CandlepinRequestScope.class);

        // Because all candlepin operations are running in the CandlepinRequestScope
        // we'll force the instance creations to be done inside the scope.
        // Exit the scope to make sure that it is clean before starting the test.
        cpRequestScope.exit();
        cpRequestScope.enter();
        this.injector.injectMembers(this);

        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));

        HttpServletRequest req = parentInjector.getInstance(HttpServletRequest.class);
        when(req.getAttribute("username")).thenReturn("mock_user");

        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        if (beginTransaction) {
            this.beginTransaction();
        }
    }

    @AfterEach
    public void shutdown() {
        cpRequestScope.exit();

        // If we have any pending transactions, we should commit it before we move on
        EntityManager manager = this.getEntityManager();
        EntityTransaction transaction = manager.getTransaction();

        if (transaction.isActive()) {
            if (transaction.getRollbackOnly()) {
                transaction.rollback();
            }
            else {
                transaction.commit();
            }
        }

        // We are using a singleton for the principal in tests. Make sure we clear it out
        // after every test. TestPrincipalProvider controls the default behavior.
        TestPrincipalProviderSetter.get().setPrincipal(null);
        manager.clear();

        reset(parentInjector.getInstance(HttpServletRequest.class));
        reset(parentInjector.getInstance(HttpServletResponse.class));
    }

    @AfterAll
    public static void destroy() {
        parentInjector.getInstance(PersistFilter.class).destroy();

        EntityManager manager = parentInjector.getInstance(EntityManager.class);
        if (manager.isOpen()) {
            manager.close();
        }

        EntityManagerFactory emf = parentInjector.getInstance(EntityManagerFactory.class);
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

    /**
     * Populates the given object by injecting dependencies for its members tagged with the @Inject
     * annotation.
     *
     * @param object
     *  The object to populate
     */
    protected void injectMembers(Object object) {
        this.injector.injectMembers(object);
    }

    protected com.google.inject.Provider<EntityManager> getEntityManagerProvider() {
        return this.injector.getProvider(EntityManager.class);
    }

    protected EntityManager getEntityManager() {
        return this.getEntityManagerProvider().get();
    }

    protected Session getCurrentSession() {
        return (Session) this.getEntityManager().getDelegate();
    }

    /**
     * Opens a new transaction if a transaction has not already been opened. If a transaction is
     * already open, this method does nothing; repeated calls are safe within the context of a
     * single thread.
     */
    protected void beginTransaction() {
        EntityTransaction transaction = this.getEntityManager().getTransaction();

        if (!transaction.isActive()) {
            transaction.begin();
        }
        else {
            log.warn("beginTransaction called with an active transaction");
        }
    }

    /**
     * Commits the current transaction, flushing pending writes as necessary. If a transaction has
     * not yet been opened, this method does nothing; repeated calls are safe within the context of
     * a single thread.
     */
    protected void commitTransaction() {
        EntityTransaction transaction = this.getEntityManager().getTransaction();

        if (transaction.isActive()) {
            transaction.commit();
        }
        else {
            log.warn("commitTransaction called without an active transaction");
        }
    }

    /**
     * Rolls back the current transaction, discarding any pending writes. If a transaction has not
     * yet been opened, this method does nothing; repeated calls are safe within the context of a
     * single thread.
     */
    protected void rollbackTransaction() {
        EntityTransaction transaction = this.getEntityManager().getTransaction();

        if (transaction.isActive()) {
            transaction.rollback();
        }
        else {
            log.warn("rollbackTransaction called without an active transaction");
        }
    }

    // Entity creation methods
    protected ActivationKey createActivationKey(Owner owner) {
        ActivationKey key = new ActivationKey();

        key.setOwner(owner);
        key.setName("A Test Key");
        key.setServiceLevel("TestLevel");
        key.setDescription("A test description for the test key.");

        return this.activationKeyCurator.create(key);
    }

    public Role createAdminRole(Owner owner) {
        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, owner, Access.ALL);
        Role role = new Role("testrole" + TestUtil.randomInt());
        role.addPermission(p);
        return role;
    }

    protected Cdn createCdn() {
        int rand = TestUtil.randomInt();
        String name = "test-cdn-" + rand;
        String url = "https://" + rand + ".cdn.com";

        return this.createCdn(name, name, url);
    }

    protected Cdn createCdn(String name, String url) {
        return this.createCdn(name, name, url);
    }

    protected Cdn createCdn(String name, String label, String url) {
        Cdn cdn = new Cdn(name, label, url);

        return this.cdnCurator.create(cdn);
    }

    protected Content createContent(Owner... owners) {
        String contentId = "test-content-" + TestUtil.randomInt();
        return this.createContent(contentId, contentId, owners);
    }

    protected Content createContent(String id, String name, Owner... owners) {
        Content content = TestUtil.createContent(id, name);
        content = this.contentCurator.create(content);
        this.ownerContentCurator.mapContentToOwners(content, owners);

        return content;
    }

    protected Content createContent(Content content, Owner... owners) {
        content = this.contentCurator.create(content);
        this.ownerContentCurator.mapContentToOwners(content, owners);

        return content;
    }

    protected Consumer createConsumer(Owner owner, ConsumerType ctype) {
        if (ctype == null) {
            ctype = this.createConsumerType();
        }

        Consumer consumer = new Consumer("test-consumer", "test-user", owner, ctype);

        return this.consumerCurator.create(consumer);
    }

    protected Consumer createConsumer(Owner owner) {
        return this.createConsumer(owner, null);
    }

    protected Consumer createDistributor(Owner owner) {
        ConsumerType type = this.createConsumerType(true);
        Consumer consumer = new Consumer("test-distributor", "test-user", owner, type);

        return this.consumerCurator.create(consumer);
    }

    protected ConsumerType createConsumerType() {
        return this.createConsumerType(false);
    }

    protected ConsumerType createConsumerType(boolean manifest) {
        String label = "test-distributor-type-" + TestUtil.randomInt();

        return this.createConsumerType(label, manifest);
    }

    protected ConsumerType createConsumerType(String label, boolean manifest) {
        ConsumerType ctype = new ConsumerType(label);
        ctype.setManifest(manifest);

        return this.consumerTypeCurator.create(ctype);
    }

    protected Entitlement createEntitlement(Owner owner, Consumer consumer, Pool pool) {
        return this.createEntitlement(owner, consumer, pool, null);
    }

    protected Entitlement createEntitlement(Owner owner, Consumer consumer, Pool pool,
        EntitlementCertificate cert) {

        Entitlement entitlement = new Entitlement();
        entitlement.setId(Util.generateDbUUID());
        entitlement.setOwner(owner);
        entitlement.setPool(pool);
        entitlement.setConsumer(consumer);

        this.entitlementCurator.create(entitlement);

        // Maintain runtime consistency
        consumer.addEntitlement(entitlement);
        pool.getEntitlements().add(entitlement);

        if (cert != null) {
            cert.setEntitlement(entitlement);
            entitlement.addCertificate(cert);

            this.entitlementCertificateCurator.merge(cert);
            entitlement = this.entitlementCurator.merge(entitlement);
        }

        return entitlement;
    }

    protected EntitlementCertificate createEntitlementCertificate(String key, String cert) {
        return this.createEntitlementCertificate(null, key, cert);
    }

    protected EntitlementCertificate createEntitlementCertificate(Entitlement entitlement, String key,
        String cert) {

        EntitlementCertificate entcert = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Date());
        this.certSerialCurator.create(certSerial);

        entcert.setKeyAsBytes(key.getBytes());
        entcert.setCertAsBytes(cert.getBytes());
        entcert.setSerial(certSerial);

        if (entitlement != null) {
            entcert.setEntitlement(entitlement);
            entitlement.addCertificate(entcert);

            entcert = this.entitlementCertificateCurator.create(entcert);
            this.entitlementCurator.merge(entitlement);
        }

        return entcert;
    }

    protected Environment createEnvironment(Owner owner, String id) {
        String name = "test-env-" + TestUtil.randomInt();
        return this.createEnvironment(owner, name, name, null, null, null);
    }

    protected Environment createEnvironment(Owner owner, String id, String name) {
        return this.createEnvironment(owner, id, name, null, null, null);
    }

    protected Environment createEnvironment(Owner owner, String id, String name, String description,
        Collection<Consumer> consumers, Collection<Content> content) {

        Environment environment = new Environment(id, name, owner);
        environment.setDescription(description);

        if (content != null) {
            for (Content elem : content) {
                EnvironmentContent envContent = new EnvironmentContent(environment, elem, true);

                // Impl note:
                // At the time of writing, this line is redundant. But if we ever fix environment,
                // this will be good to have as a backup.
                environment.getEnvironmentContent().add(envContent);
            }
        }

        environment = this.environmentCurator.create(environment);

        // Update consumers to point to the new environment
        if (consumers != null) {
            for (Consumer consumer : consumers) {
                consumer.setEnvironmentId(environment.getId());
                this.consumerCurator.merge(consumer);
            }
        }

        return environment;
    }

    protected Owner createOwner() {
        return this.createOwner("Test Owner " + TestUtil.randomInt());
    }

    protected Owner createOwner(String key) {
        return this.createOwner(key, key);
    }

    protected Owner createOwner(String key, String name) {
        Owner owner = TestUtil.createOwner(key, name);
        owner.setId(null);

        this.ownerCurator.create(owner);

        return owner;
    }

    protected Pool createPool(Owner owner, Product product) {
        return this.createPool(
            owner, product, 1L, TestUtil.createDate(2000, 1, 1), TestUtil.createDate(2100, 1, 1)
        );
    }

    /**
     * Create an entitlement pool.
     *
     * @return an entitlement pool
     */
    protected Pool createPool(Owner owner, Product product, Long quantity, Date startDate, Date endDate) {
        Pool pool = new Pool(
            owner,
            product,
            new HashSet<>(),
            quantity,
            startDate,
            endDate,
            DEFAULT_CONTRACT,
            DEFAULT_ACCOUNT,
            DEFAULT_ORDER
        );

        pool.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        return poolCurator.create(pool);
    }

    protected Pool createPool(Owner owner, Product product, Long quantity, String subscriptionId,
        String subscriptionSubKey, Date startDate, Date endDate) {
        Pool pool = new Pool(
            owner,
            product,
            new HashSet<>(),
            quantity,
            startDate,
            endDate,
            DEFAULT_CONTRACT,
            DEFAULT_ACCOUNT,
            DEFAULT_ORDER
        );

        pool.setSourceSubscription(new SourceSubscription(subscriptionId, subscriptionSubKey));
        return poolCurator.create(pool);
    }

    protected Pool createPool(Owner owner, Product product, Collection<Product> provided, Long quantity,
        Date startDate, Date endDate) {

        Pool pool = new Pool(
            owner,
            product,
            new HashSet<>(),
            quantity,
            startDate,
            endDate,
            DEFAULT_CONTRACT,
            DEFAULT_ACCOUNT,
            DEFAULT_ORDER
        );

        return poolCurator.create(pool);
    }

    protected Pool createPool(Owner owner, Product product, Long quantity, Date startDate, Date endDate,
        String contractNr) {
        Pool pool = createPool(owner, product, quantity, startDate, endDate);
        pool.setContractNumber(contractNr);
        return poolCurator.merge(pool);
    }

    protected Pool createPool(Owner owner, Product product, Long quantity, Date startDate, Date endDate,
        String contractNr, String subscriptionId) {
        Pool pool = createPool(owner, product, quantity, subscriptionId, "master", startDate, endDate);
        pool.setContractNumber(contractNr);
        return poolCurator.merge(pool);
    }

    protected Product createProduct(Owner... owners) {
        String productId = "test-product-" + TestUtil.randomInt();
        return this.createProduct(productId, productId, owners);
    }

    protected Product createProduct(String id, String name, Owner... owners) {
        Product product = TestUtil.createProduct(id, name);
        return this.createProduct(product, owners);
    }

    protected Product createProductWithBranding(String id, String name, Branding branding,
        Owner... owners) {

        Product product = TestUtil.createProduct(id, name);
        product.addBranding(branding);
        return this.createProduct(product, owners);
    }

    protected Product createProduct(Product product, Owner... owners) {
        product = this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwners(product, owners);

        return product;
    }

    protected Principal setupPrincipal(Owner owner, Access role) {
        return setupPrincipal("someuser", owner, role);
    }

    protected Principal setupPrincipal(String username, Owner owner, Access verb) {
        OwnerPermission p = new OwnerPermission(owner, verb);
        // Only need a detached owner permission here:
        Principal ownerAdmin = new UserPrincipal(username, Arrays.asList(new Permission[] {p}), false);
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

    /**
     * For parameterized tests, the method called to provide the parameter values is called before the @Before
     * methods are called.  Our Guice injection occurs in the @Before methods and therefore injection isn't
     * a possibility in parameter providers.  Thus we need a special method to return a Configuration object
     * when required by parameter providers.
     * @return a Configuration object
     */
    protected Configuration getConfigForParameters() {
        return new CandlepinCommonTestConfig();
    }
}
