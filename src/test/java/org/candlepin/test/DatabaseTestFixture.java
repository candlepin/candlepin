/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;
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
import org.candlepin.config.Configuration;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.TestPrincipalProviderSetter;
import org.candlepin.junit.LiquibaseExtension;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
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
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentAccessPayloadCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentContentOverrideCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.ManifestFileRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.SubscriptionsCertificateCurator;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UserCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverrideCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.resteasy.MethodLocator;
import org.candlepin.resteasy.ResourceLocatorMap;
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
import java.util.Locale;

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

    protected DevConfig config;

    protected ActivationKeyCurator activationKeyCurator;
    protected ActivationKeyContentOverrideCurator activationKeyContentOverrideCurator;
    protected AnonymousCloudConsumerCurator anonymousCloudConsumerCurator;
    protected AnonymousContentAccessCertificateCurator anonymousContentAccessCertCurator;
    protected AsyncJobStatusCurator asyncJobCurator;
    protected CdnCurator cdnCurator;
    protected CertificateSerialCurator certSerialCurator;

    protected ConsumerCurator consumerCurator;
    protected ConsumerTypeCurator consumerTypeCurator;
    protected ConsumerContentOverrideCurator consumerContentOverrideCurator;
    protected ContentAccessCertificateCurator caCertCurator;
    protected ContentAccessPayloadCurator caPayloadCurator;
    protected ContentCurator contentCurator;
    protected DeletedConsumerCurator deletedConsumerCurator;
    protected DistributorVersionCurator distributorVersionCurator;
    protected EntitlementCurator entitlementCurator;
    protected EntitlementCertificateCurator entitlementCertificateCurator;
    protected EnvironmentCurator environmentCurator;
    protected EnvironmentContentCurator environmentContentCurator;
    protected EnvironmentContentOverrideCurator environmentContentOverrideCurator;
    protected ExporterMetadataCurator exporterMetadataCurator;
    protected GuestIdCurator guestIdCurator;
    protected IdentityCertificateCurator identityCertificateCurator;
    protected ImportRecordCurator importRecordCurator;
    protected KeyPairDataCurator keyPairDataCurator;
    protected ManifestFileRecordCurator manifestFileRecordCurator;
    protected OwnerCurator ownerCurator;
    protected OwnerInfoCurator ownerInfoCurator;
    protected PermissionBlueprintCurator permissionBlueprintCurator;
    protected ProductCertificateCurator productCertificateCurator;
    protected ProductCurator productCurator;
    protected PoolCurator poolCurator;
    protected RoleCurator roleCurator;
    protected RulesCurator rulesCurator;
    protected SubscriptionsCertificateCurator subscriptionsCertificateCurator;
    protected UeberCertificateCurator ueberCertificateCurator;
    protected UserCurator userCurator;

    protected PermissionFactory permissionFactory;
    protected ModelTranslator modelTranslator;
    protected ResourceLocatorMap locatorMap;
    protected MethodLocator methodLocator;
    protected AnnotationLocator annotationLocator;

    private static Injector parentInjector;
    protected Injector injector;
    private CandlepinRequestScope cpRequestScope;

    protected TestingInterceptor securityInterceptor;
    protected DateSourceForTesting dateSource;
    protected I18n i18n;
    protected Provider<I18n> i18nProvider;

    @BeforeAll
    public static void initClass() {
        parentInjector = Guice.createInjector(new TestingModules.JpaModule());
        insertValidationEventListeners(parentInjector);
    }

    /**
     * There's no way to really get Guice to perform injections on stuff that the JpaPersistModule is
     * creating, so we resort to grabbing the EntityManagerFactory after the fact and adding the
     * Validation EventListener ourselves.
     *
     * @param inj
     */
    private static void insertValidationEventListeners(Injector inj) {
        Provider<EntityManagerFactory> emfProvider = inj.getProvider(EntityManagerFactory.class);
        SessionFactoryImpl sessionFactoryImpl = (SessionFactoryImpl) emfProvider.get();
        EventListenerRegistry registry = sessionFactoryImpl
            .getServiceRegistry()
            .getService(EventListenerRegistry.class);

        Provider<BeanValidationEventListener> listenerProvider = inj
            .getProvider(BeanValidationEventListener.class);

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
        this.config = TestConfig.defaults();

        Module testingModule = new TestingModules.StandardTest(this.config);
        this.injector = parentInjector.createChildInjector(
            Modules.override(testingModule).with(getGuiceOverrideModule()));

        methodLocator = new MethodLocator(injector);
        methodLocator.init();

        locatorMap = new ResourceLocatorMap(injector, methodLocator);
        locatorMap.init();

        annotationLocator = new AnnotationLocator(methodLocator);
        loadFromInjector();

        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.i18nProvider = () -> this.i18n;

        // Because all candlepin operations are running in the CandlepinRequestScope
        // we'll force the instance creations to be done inside the scope.
        // Exit the scope to make sure that it is clean before starting the test.
        cpRequestScope.exit();
        cpRequestScope.enter();
        this.injector.injectMembers(this);

        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestUtil.createDate(2010, 1, 1));

        HttpServletRequest req = parentInjector.getInstance(HttpServletRequest.class);
        when(req.getAttribute("username")).thenReturn("mock_user");

        if (beginTransaction) {
            this.beginTransaction();
        }
    }

    private void loadFromInjector() {
        securityInterceptor = this.injector.getInstance(TestingInterceptor.class);
        permissionFactory = this.injector.getInstance(PermissionFactory.class);
        modelTranslator = this.injector.getInstance(ModelTranslator.class);
        cpRequestScope = this.injector.getInstance(CandlepinRequestScope.class);
        activationKeyCurator = this.injector.getInstance(ActivationKeyCurator.class);
        activationKeyContentOverrideCurator = this.injector
            .getInstance(ActivationKeyContentOverrideCurator.class);
        anonymousCloudConsumerCurator = this.injector
            .getInstance(AnonymousCloudConsumerCurator.class);
        anonymousContentAccessCertCurator = this.injector
            .getInstance(AnonymousContentAccessCertificateCurator.class);
        asyncJobCurator = this.injector.getInstance(AsyncJobStatusCurator.class);
        cdnCurator = this.injector.getInstance(CdnCurator.class);
        certSerialCurator = this.injector.getInstance(CertificateSerialCurator.class);
        consumerCurator = this.injector.getInstance(ConsumerCurator.class);
        consumerTypeCurator = this.injector.getInstance(ConsumerTypeCurator.class);
        consumerContentOverrideCurator = this.injector.getInstance(ConsumerContentOverrideCurator.class);
        caCertCurator = this.injector.getInstance(ContentAccessCertificateCurator.class);
        caPayloadCurator = this.injector.getInstance(ContentAccessPayloadCurator.class);
        contentCurator = this.injector.getInstance(ContentCurator.class);
        deletedConsumerCurator = this.injector.getInstance(DeletedConsumerCurator.class);
        distributorVersionCurator = this.injector.getInstance(DistributorVersionCurator.class);
        entitlementCurator = this.injector.getInstance(EntitlementCurator.class);
        entitlementCertificateCurator = this.injector.getInstance(EntitlementCertificateCurator.class);
        environmentCurator = this.injector.getInstance(EnvironmentCurator.class);
        environmentContentCurator = this.injector.getInstance(EnvironmentContentCurator.class);
        environmentContentOverrideCurator = this.injector
            .getInstance(EnvironmentContentOverrideCurator.class);
        exporterMetadataCurator = this.injector.getInstance(ExporterMetadataCurator.class);
        guestIdCurator = this.injector.getInstance(GuestIdCurator.class);
        identityCertificateCurator = this.injector.getInstance(IdentityCertificateCurator.class);
        importRecordCurator = this.injector.getInstance(ImportRecordCurator.class);
        keyPairDataCurator = injector.getInstance(KeyPairDataCurator.class);
        manifestFileRecordCurator = this.injector.getInstance(ManifestFileRecordCurator.class);
        ownerCurator = this.injector.getInstance(OwnerCurator.class);
        ownerInfoCurator = this.injector.getInstance(OwnerInfoCurator.class);
        permissionBlueprintCurator = this.injector.getInstance(PermissionBlueprintCurator.class);
        productCertificateCurator = this.injector.getInstance(ProductCertificateCurator.class);
        productCurator = this.injector.getInstance(ProductCurator.class);
        poolCurator = this.injector.getInstance(PoolCurator.class);
        roleCurator = this.injector.getInstance(RoleCurator.class);
        rulesCurator = this.injector.getInstance(RulesCurator.class);
        subscriptionsCertificateCurator = this.injector.getInstance(SubscriptionsCertificateCurator.class);
        ueberCertificateCurator = this.injector.getInstance(UeberCertificateCurator.class);
        userCurator = this.injector.getInstance(UserCurator.class);
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
     *     The object to populate
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
     * Opens a new transaction if a transaction has not already been opened. If a transaction is already
     * open, this method does nothing; repeated calls are safe within the context of a single thread.
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
     * Commits the current transaction, flushing pending writes as necessary. If a transaction has not
     * yet been opened, this method does nothing; repeated calls are safe within the context of a single
     * thread.
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
     * Rolls back the current transaction, discarding any pending writes. If a transaction has not yet
     * been opened, this method does nothing; repeated calls are safe within the context of a single
     * thread.
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
        key.setName(TestUtil.randomString("key-"));
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

    protected Content createContent() {
        String contentId = "test-content-" + TestUtil.randomInt();
        return this.createContent(contentId, contentId);
    }

    protected Content createContent(String id) {
        return this.createContent(id, id);
    }

    protected Content createContent(String id, String name) {
        Content content = TestUtil.createContent(id, name);
        content = this.contentCurator.create(content);

        return content;
    }

    protected Content createContent(Content content) {
        content = this.contentCurator.create(content);
        return content;
    }

    protected Consumer createConsumer(Owner owner, ConsumerType ctype) {
        if (ctype == null) {
            ctype = this.createConsumerType();
        }

        Consumer consumer = new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(ctype);

        return this.consumerCurator.create(consumer);
    }

    protected Consumer createConsumer(Owner owner) {
        return this.createConsumer(owner, null);
    }

    protected Consumer createDistributor(Owner owner) {
        ConsumerType type = this.createConsumerType(true);
        Consumer consumer = new Consumer()
            .setName("test-distributor")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(type);

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

    protected Environment createEnvironment(Owner owner) {
        String id = "test-env-" + TestUtil.randomInt();
        return this.createEnvironment(owner, id, id, null, null, null);
    }

    protected Environment createEnvironment(Owner owner, String id) {
        String name = "test-env-" + TestUtil.randomInt();
        return this.createEnvironment(owner, id, name, null, null, null);
    }

    protected Environment createEnvironment(Owner owner, String id, String name) {
        return this.createEnvironment(owner, id, name, null, null, null);
    }

    protected Environment createEnvironment(Owner owner, String id, String name, String description,
        Collection<Consumer> consumers, Collection<Content> content) {

        Environment environment = new Environment()
            .setId(id)
            .setName(name)
            .setOwner(owner)
            .setDescription(description);

        if (content != null) {
            content.forEach(elem -> environment.addContent(elem, true));
        }

        this.environmentCurator.create(environment);

        // Update consumers to point to the new environment
        if (consumers != null) {
            for (Consumer consumer : consumers) {
                consumer.addEnvironment(environment);
                this.consumerCurator.merge(consumer);
            }
        }

        return environment;
    }

    /**
     * Creates an owner with a randomly generated name in SCA (Simple Content Access) mode.
     *
     * <p>This method generates a random integer, appends it to the name "Test Owner",
     * and uses it to create a new owner instance. The owner is created in SCA mode
     * (default for owners) and is persisted in the database.</p>
     *
     * @return a newly created and persisted {@code Owner} instance in SCA mode
     */
    protected Owner createOwner() {
        return this.createOwner("Test Owner " + TestUtil.randomInt());
    }

    /**
     * Creates an owner with the specified key and a matching name in SCA (Simple Content Access) mode.
     *
     * <p>This method creates a new {@code Owner} instance where both the key and name
     * are set to the provided {@code key} value. The owner is created in SCA mode
     * (default for owners) and is persisted in the database.</p>
     *
     * @param key the key to use for the owner; it will also be used as the owner's name
     * @return a newly created and persisted {@code Owner} instance in SCA mode
     */
    protected Owner createOwner(String key) {
        return this.createOwner(key, key);
    }

    /**
     * Creates an owner with the specified key and name in SCA (Simple Content Access) mode.
     *
     * <p>This method initializes an {@code Owner} instance using the provided key and name,
     * and persists the owner in the database. The owner is created in SCA mode (default for owners).</p>
     *
     * @param key the key to assign to the owner
     * @param name the display name of the owner
     * @return a newly created and persisted {@code Owner} instance in SCA mode
     */
    protected Owner createOwner(String key, String name) {
        Owner owner = TestUtil.createOwner(key, name)
            .setId(null);

        return this.ownerCurator.create(owner);
    }

    /**
     * Creates a non-SCA (Simple Content Access) owner with the specified key.
     *
     * <p>This method creates a new {@code Owner} instance in entitlement mode, where
     * both the key and name are set to the provided {@code key} value. The owner is persisted
     * in the database.</p>
     *
     * @param key the key to use for the owner; it will also be used as the owner's name
     * @return a newly created and persisted {@code Owner} instance in entitlement mode
     */
    protected Owner createNonSCAOwner(String key) {
        return this.createNonSCAOwner(key, key);
    }

    /**
     * Creates a non-SCA (Simple Content Access) owner with the specified key and name.
     *
     * <p>This method initializes an {@code Owner} instance with the provided key and name,
     * sets the content access mode to entitlement mode, and persists the owner in the database.</p>
     *
     * @param key the key to assign to the owner
     * @param name the display name of the owner
     * @return a newly created and persisted {@code Owner} instance in entitlement mode
     */
    protected Owner createNonSCAOwner(String key, String name) {
        Owner owner = TestUtil.createOwner(key, name)
            .setId(null)
            .setContentAccessMode(ContentAccessMode.ENTITLEMENT.toDatabaseValue());

        return this.ownerCurator.create(owner);
    }

    /**
     * Create an entitlement pool.
     *
     * @return an entitlement pool
     */
    protected Pool createPool(Owner owner, Product product, Long quantity, Date startDate, Date endDate) {
        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(quantity)
            .setStartDate(startDate)
            .setEndDate(endDate)
            .setContractNumber(DEFAULT_CONTRACT)
            .setAccountNumber(DEFAULT_ACCOUNT)
            .setOrderNumber(DEFAULT_ORDER)
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), PRIMARY_POOL_SUB_KEY));

        return this.poolCurator.create(pool);
    }

    protected Pool createPool(Owner owner, Product product) {
        return this.createPool(owner, product, 1L, TestUtil.createDate(2000, 1, 1),
            TestUtil.createDate(2100, 1, 1));
    }

    protected Product createProduct() {
        String productId = "test_product-" + TestUtil.randomInt();
        return this.createProduct(productId, productId);
    }

    protected Product createProduct(String id) {
        return this.createProduct(id, id);
    }

    protected Product createProduct(String id, String name) {
        Product product = TestUtil.createProduct(id, name);
        return this.createProduct(product);
    }

    protected Product createProductWithBranding(String id, String name, Branding branding) {

        Product product = TestUtil.createProduct(id, name);
        product.addBranding(branding);
        return this.createProduct(product);
    }

    // Remove this method. It doesn't have a whole lot of value now that we don't need to map
    // products to orgs.
    protected Product createProduct(Product product) {
        return this.productCurator.create(product);
    }

    protected Principal setupPrincipal(Owner owner, Access role) {
        return setupPrincipal("someuser", owner, role);
    }

    protected Principal setupPrincipal(String username, Owner owner, Access verb) {
        OwnerPermission p = new OwnerPermission(owner, verb);
        // Only need a detached owner permission here:
        Principal ownerAdmin = new UserPrincipal(username, Arrays.asList(new Permission[] { p }), false);
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
     * For parameterized tests, the method called to provide the parameter values is called before
     * the @Before methods are called. Our Guice injection occurs in the @Before methods and therefore
     * injection isn't a possibility in parameter providers. Thus we need a special method to return a
     * Configuration object when required by parameter providers.
     *
     * @return a Configuration object
     */
    protected Configuration getConfigForParameters() {
        return TestConfig.defaults();
    }

    protected void createProductContent(Owner owner, boolean enabled, Content... contents) {
        EntityManager entityManager = this.getEntityManager();
        Product product = createProduct();
        this.createPool(owner, product);

        for (Content content : contents) {
            ProductContent productContent = new ProductContent(product, content, enabled);
            entityManager.persist(productContent);
        }
        entityManager.flush();
    }
}
