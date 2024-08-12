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
package org.candlepin.controller;

import static org.apache.commons.collections4.CollectionUtils.containsAny;
import static org.candlepin.model.SourceSubscription.DERIVED_POOL_SUB_KEY;
import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQualifier;
import org.candlepin.model.Product;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.paging.Page;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;



public class PoolManagerFunctionalTest extends DatabaseTestFixture {
    private static final String PRODUCT_MONITORING = "monitoring";
    private static final String PRODUCT_PROVISIONING = "provisioning";
    private static final String PRODUCT_VIRT_HOST = "virtualization_host";
    private static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    private static final String PRODUCT_VIRT_GUEST = "virt_guest";

    private PoolManager poolManager;

    private EntitlementCertificateService certGenerator;
    private RefresherFactory refresherFactory;

    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    private Product monitoring;
    private Product provisioning;
    private Product socketLimitedProduct;

    private SubscriptionDTO sub4;

    private ConsumerType systemType;

    private Owner o;
    private Consumer parentSystem;
    private Consumer childVirtSystem;
    private EventSink eventSink;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();

        poolManager = injector.getInstance(PoolManager.class);
        certGenerator = injector.getInstance(EntitlementCertificateService.class);
        refresherFactory = injector.getInstance(RefresherFactory.class);

        o = createOwner();
        ownerCurator.create(o);

        virtHost = TestUtil.createProduct(PRODUCT_VIRT_HOST, PRODUCT_VIRT_HOST);
        virtHostPlatform = TestUtil.createProduct(PRODUCT_VIRT_HOST_PLATFORM, PRODUCT_VIRT_HOST_PLATFORM);
        virtGuest = TestUtil.createProduct(PRODUCT_VIRT_GUEST, PRODUCT_VIRT_GUEST);
        monitoring = TestUtil.createProduct(PRODUCT_MONITORING, PRODUCT_MONITORING);
        monitoring.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");

        provisioning = TestUtil.createProduct(PRODUCT_PROVISIONING, PRODUCT_PROVISIONING);
        provisioning.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        provisioning.setMultiplier(2L);
        provisioning.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "4");

        virtHost.setAttribute(PRODUCT_VIRT_HOST, "");
        virtHostPlatform.setAttribute(PRODUCT_VIRT_HOST_PLATFORM, "");
        virtGuest.setAttribute(PRODUCT_VIRT_GUEST, "");
        monitoring.setAttribute(PRODUCT_MONITORING, "");
        provisioning.setAttribute(PRODUCT_PROVISIONING, "");

        socketLimitedProduct = TestUtil.createProduct("socket-limited-prod", "Socket Limited Product");
        socketLimitedProduct.setAttribute(Product.Attributes.SOCKETS, "2");
        productCurator.create(socketLimitedProduct);

        productCurator.create(virtHost);
        productCurator.create(virtHostPlatform);
        productCurator.create(virtGuest);
        productCurator.create(monitoring);
        productCurator.create(provisioning);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        SubscriptionDTO sub1 = new SubscriptionDTO();
        sub1.setId(Util.generateDbUUID());
        sub1.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub1.setProduct(this.modelTranslator.translate(virtHost, ProductDTO.class));
        sub1.setQuantity(5L);
        sub1.setStartDate(new Date());
        sub1.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub1.setLastModified(new Date());

        SubscriptionDTO sub2 = new SubscriptionDTO();
        sub2.setId(Util.generateDbUUID());
        sub2.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub2.setProduct(this.modelTranslator.translate(virtHostPlatform, ProductDTO.class));
        sub2.setQuantity(5L);
        sub2.setStartDate(new Date());
        sub2.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub2.setLastModified(new Date());

        SubscriptionDTO sub3 = new SubscriptionDTO();
        sub3.setId(Util.generateDbUUID());
        sub3.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub3.setProduct(this.modelTranslator.translate(monitoring, ProductDTO.class));
        sub3.setQuantity(5L);
        sub3.setStartDate(new Date());
        sub3.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub3.setLastModified(new Date());

        BrandingDTO brand1 = new BrandingDTO();
        brand1.setName("branding1");
        brand1.setType("type1");
        brand1.setProductId("product1");

        BrandingDTO brand2 = new BrandingDTO();
        brand2.setName("branding2");
        brand2.setType("type2");
        brand2.setProductId("product2");

        sub4 = new SubscriptionDTO();
        sub4.setId(Util.generateDbUUID());
        sub4.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub4.setProduct(this.modelTranslator.translate(provisioning, ProductDTO.class));
        sub4.getProduct().addBranding(brand1);
        sub4.getProduct().addBranding(brand2);

        sub4.setQuantity(5L);
        sub4.setStartDate(new Date());
        sub4.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub4.setLastModified(new Date());

        subscriptions.add(sub1);
        subscriptions.add(sub2);
        subscriptions.add(sub3);
        subscriptions.add(sub4);

        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        this.refresherFactory.getRefresher(subAdapter).add(o).run();

        this.systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);

        parentSystem = new Consumer()
            .setName("system")
            .setUsername("user")
            .setOwner(o)
            .setType(systemType)
            .setFact("total_guests", "0");
        consumerCurator.create(parentSystem);

        childVirtSystem = new Consumer()
            .setName("virt system")
            .setUsername("user")
            .setOwner(o)
            .setType(systemType);

        consumerCurator.create(childVirtSystem);
    }

    @Test
    public void testEntitlementPoolsCreated() {
        List<Pool> pools = poolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        Pool virtHostPool = listPoolsByOwnerAndProduct(o, virtHost.getId()).get(0);
        assertNotNull(virtHostPool);
    }

    @Test
    public void testQuantityCheck() throws Exception {
        Pool monitoringPool = listPoolsByOwnerAndProduct(o, monitoring.getId()).get(0);
        assertEquals(Long.valueOf(5), monitoringPool.getQuantity());
        AutobindData data = new AutobindData(parentSystem, o)
            .on(new Date())
            .forProducts(Set.of(monitoring.getId()));

        for (int i = 0; i < 5; i++) {
            List<Entitlement> entitlements = poolManager.entitleByProducts(data);
            assertEquals(1, entitlements.size());
        }

        // The cert should specify 5 monitoring entitlements, taking a 6th should fail:
        assertEquals(0, poolManager.entitleByProducts(data).size());
        assertEquals(Long.valueOf(5), monitoringPool.getConsumed());
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithSingleEntitlement() throws Exception {
        AutobindData data = new AutobindData(childVirtSystem, o)
            .on(new Date())
            .forProducts(Set.of(provisioning.getId()));

        this.entitlementCurator.refresh(poolManager.entitleByProducts(data).get(0));
        regenerateECAndAssertNotSameCertificates();
    }

    @Test
    public void testFabricateWithBranding() {
        List<Pool> primaryPools = poolManager.getPoolsBySubscriptionId(sub4.getId());
        Pool primaryPool = null;
        for (Pool pool : primaryPools) {
            if (pool.getType() == Pool.PoolType.NORMAL) {
                primaryPool = pool;
            }
        }

        org.candlepin.dto.api.server.v1.SubscriptionDTO fabricated = modelTranslator.translate(primaryPool,
            org.candlepin.dto.api.server.v1.SubscriptionDTO.class);
        assertNotNull(fabricated);
        assertNotNull(primaryPool.getProduct());
        assertNotNull(fabricated.getProduct());

        Set<org.candlepin.dto.api.server.v1.BrandingDTO> brandingSet = fabricated.getProduct().getBranding();

        assertNotNull(brandingSet);
        assertEquals(2, brandingSet.size());
        ArrayList<org.candlepin.dto.api.server.v1.BrandingDTO> list = new ArrayList<>(brandingSet);
        list.sort(Comparator.comparing(org.candlepin.dto.api.server.v1.BrandingDTO::getName));

        assertEquals("branding1", list.get(0).getName());
        assertEquals("product1", list.get(0).getProductId());
        assertEquals("type1", list.get(0).getType());
        assertEquals("branding2", list.get(1).getName());
        assertEquals("product2", list.get(1).getProductId());
        assertEquals("type2", list.get(1).getType());
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithMultipleEntitlements()
        throws EntitlementRefusedException {

        AutobindData data = new AutobindData(childVirtSystem, o)
            .on(new Date())
            .forProducts(Set.of(provisioning.getId()));

        this.entitlementCurator.refresh(poolManager.entitleByProducts(data).get(0));
        this.entitlementCurator.refresh(poolManager.entitleByProducts(data).get(0));
        regenerateECAndAssertNotSameCertificates();
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithNoEntitlement() {
        reset(this.eventSink); // pool creation events went out from setup
        certGenerator.regenerateCertificatesOf(childVirtSystem, true);
        assertEquals(0, collectEntitlementCertIds(this.childVirtSystem).size());
        Mockito.verifyNoInteractions(this.eventSink);
    }

    @Test
    public void testEntitleByProductsWithModifierAndModifiee() throws EntitlementRefusedException {
        Set<String> modified = Set.of(PRODUCT_VIRT_HOST);

        Content content = TestUtil.createContent("modifier-content", "modifier-content");
        content.setModifiedProductIds(modified);

        Product modifier = TestUtil.createProduct("modifier", "modifier");
        modifier.addContent(content, true);

        contentCurator.create(content);
        productCurator.create(modifier);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setQuantity(5L);
        sub.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub.setProduct(this.modelTranslator.translate(modifier, ProductDTO.class));
        sub.setStartDate(new Date());
        sub.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub.setLastModified(new Date());

        sub.setId(Util.generateDbUUID());

        subscriptions.add(sub);

        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        this.refresherFactory.getRefresher(subAdapter).add(o).run();

        // This test simulates https://bugzilla.redhat.com/show_bug.cgi?id=676870
        // where entitling first to the modifier then to the modifiee causes the modifier's
        // entitlement cert to get regenerated, but since it's all in the same http call,
        // this ends up causing a hibernate failure (the old cert is asked to be deleted,
        // but it hasn't been saved yet). Since getting the pool ordering right is tricky
        // inside an entitleByProducts call, we do it in two singular calls here.
        AutobindData data = new AutobindData(parentSystem, o)
            .on(new Date())
            .forProducts(Set.of("modifier"));

        poolManager.entitleByProducts(data);

        try {
            data = new AutobindData(parentSystem, o)
                .on(new Date())
                .forProducts(Set.of(PRODUCT_VIRT_HOST));

            poolManager.entitleByProducts(data);
        }
        catch (EntityNotFoundException e) {
            throw e;
            // fail("Hibernate failed to properly save entitlement certs!");
        }

        // If we get here, no exception was raised, so we're happy!
    }

    @Test
    public void testRefreshPoolsWithChangedProductShouldUpdatePool() {
        Product product1 = TestUtil.createProduct("product 1", "Product 1");
        Product product2 = TestUtil.createProduct("product 2", "Product 2");

        productCurator.create(product1);
        productCurator.create(product2);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        SubscriptionDTO subscription = new SubscriptionDTO();
        subscription.setId(Util.generateDbUUID());
        subscription.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        subscription.setProduct(this.modelTranslator.translate(product1, ProductDTO.class));
        subscription.setQuantity(5L);
        subscription.setStartDate(new Date());
        subscription.setEndDate(TestUtil.createDate(3020, 12, 12));
        subscription.setLastModified(new Date());

        subscriptions.add(subscription);

        // set up initial pool
        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        this.refresherFactory.getRefresher(subAdapter).add(o).run();

        List<Pool> pools = listPoolsByOwnerAndProduct(o, product1.getId());
        assertEquals(1, pools.size());

        // now alter the product behind the sub, and make sure the pool is also updated
        subscription.setProduct(this.modelTranslator.translate(product2, ProductDTO.class));

        // set up initial pool
        this.refresherFactory.getRefresher(subAdapter).add(o).run();

        pools = listPoolsByOwnerAndProduct(o, product2.getId());
        assertEquals(1, pools.size());
    }

    @Test
    public void testListAllForConsumerIncludesWarnings() {
        PoolQualifier qualifier = new PoolQualifier()
            .setConsumer(parentSystem)
            .setOwnerId(parentSystem.getOwnerId());

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(qualifier);
        assertEquals(4, results.getPageData().size());

        Pool pool = createPool(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        parentSystem.setFact("cpu.sockets", "4");

        qualifier.setConsumer(parentSystem);

        results = poolManager.listAvailableEntitlementPools(qualifier);
        // Expect the warnings to be included. Should have one more pool available.
        assertEquals(5, results.getPageData().size());
    }

    @Test
    public void testListAllForConsumerExcludesErrors() {
        Product p = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(p);

        PoolQualifier qualifier = new PoolQualifier()
            .setConsumer(parentSystem)
            .setOwnerId(parentSystem.getOwnerId());

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(qualifier);
        assertEquals(4, results.getPageData().size());

        // Creating a pool with no entitlements available, which will trigger
        // a rules error:
        Pool pool = createPool(o, p, 0L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        results = poolManager.listAvailableEntitlementPools(qualifier);
        // Pool in error should not be included. Should have the same number of
        // initial pools.
        assertEquals(4, results.getPageData().size());
    }

    @Test
    public void testListAllForActKeyExcludesErrors() {
        Product p = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(p);

        ActivationKey ak = new ActivationKey();
        Pool akpool = new Pool();
        akpool.setId("test-id");
        akpool.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "true");
        ak.addPool(akpool, 1L);

        PoolQualifier qualifier = new PoolQualifier()
            .setOwnerId(parentSystem.getOwnerId());

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(qualifier);
        assertEquals(4, results.getPageData().size());

        // Creating a pool with no entitlements available, which does not trigger
        // a rules error:
        Pool pool = createPool(o, p, 0L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        results = poolManager.listAvailableEntitlementPools(qualifier);
        // Pool in error should not be included. Should have the same number of
        // initial pools.
        assertEquals(5, results.getPageData().size());
    }

    @Test
    public void testListForConsumerExcludesWarnings() {
        PoolQualifier qualifier = new PoolQualifier()
            .setConsumer(parentSystem)
            .setOwnerId(parentSystem.getOwnerId());

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(qualifier);
        assertEquals(4, results.getPageData().size());

        Pool pool = createPool(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        parentSystem.setFact("cpu.cpu_socket(s)", "4");

        results = poolManager.listAvailableEntitlementPools(qualifier);

        // Pool in error should not be included. Should have the same number of
        // initial pools.
        assertEquals(4, results.getPageData().size());
    }

    @Test
    public void testListAllForOldGuestExcludesTempPools() {
        Pool pool = createPool(o, virtGuest, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        poolCurator.create(pool);

        PoolQualifier qualifier = new PoolQualifier()
            .setConsumer(childVirtSystem)
            .setOwnerId(o.getId())
            .addProductId(virtGuest.getId());

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(qualifier);
        int newbornPools = results.getPageData().size();
        childVirtSystem.setCreated(TestUtil.createDate(2000, 1, 1));
        consumerCurator.update(childVirtSystem);

        qualifier.setConsumer(childVirtSystem);

        results = poolManager.listAvailableEntitlementPools(qualifier);

        assertEquals(newbornPools - 1, results.getPageData().size());
    }

    private void regenerateECAndAssertNotSameCertificates() {
        Set<EntitlementCertificate> oldsIds = collectEntitlementCertIds(this.childVirtSystem);
        certGenerator.regenerateCertificatesOf(childVirtSystem, false);
        Mockito.verify(this.eventSink, Mockito.times(oldsIds.size()))
            .queueEvent(any(Event.class));
        Set<EntitlementCertificate> newIds = collectEntitlementCertIds(this.childVirtSystem);
        assertFalse(containsAny(
            transform(oldsIds, EntitlementCertificate::getId),
            transform(newIds, EntitlementCertificate::getId)));
        assertFalse(containsAny(
            transform(oldsIds, EntitlementCertificate::getKey),
            transform(newIds, EntitlementCertificate::getKey)));
        assertFalse(containsAny(
            transform(oldsIds, EntitlementCertificate::getCert),
            transform(newIds, EntitlementCertificate::getCert)));
    }

    private static <T, R> Set<R> transform(Set<T> set, Function<T, R> t) {
        return set.stream().map(t).collect(Collectors.toSet());
    }

    private Set<EntitlementCertificate> collectEntitlementCertIds(Consumer consumer) {
        Set<EntitlementCertificate> ids = new HashSet<>();
        for (Entitlement entitlement : consumer.getEntitlements()) {
            ids.addAll(entitlement.getCertificates());
        }
        return ids;
    }

    @Override
    protected Module getGuiceOverrideModule() {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(Enforcer.class).to(EntitlementRules.class);
                eventSink = Mockito.mock(EventSink.class);
                bind(EventSink.class).toInstance(eventSink);
            }
        };
    }

    @Test
    public void testBatchBindError() {
        Owner owner = createOwner();
        Product product = TestUtil.createProduct("test-product", "Test Product");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        productCurator.create(product);

        Consumer system = new Consumer()
            .setName("dev")
            .setUsername("user")
            .setOwner(owner)
            .setType(systemType);
        system.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(product.getId())
            .setProductName(product.getName()));
        consumerCurator.create(system);

        Pool pool1 = createPool(owner, product, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, system.getUuid());
        poolCurator.create(pool1);
        Entitlement ent = createEntitlement(owner, system, pool1,
            createEntitlementCertificate("keycert", "cert"));
        ent.setQuantity(1);
        entitlementCurator.create(ent);
        pool1.setConsumed(pool1.getConsumed() + 1);
        poolCurator.merge(pool1);
        poolCurator.flush();

        assertEquals(1, poolCurator.get(pool1.getId()).getConsumed().intValue());
        Pool pool2 = createPool(owner, product, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool2);
        Entitlement ent2 = createEntitlement(owner, system, pool2,
            createEntitlementCertificate("keycert", "cert"));
        ent2.setQuantity(1);
        entitlementCurator.create(ent2);
        pool2.setConsumed(pool2.getConsumed() + 1);
        poolCurator.merge(pool2);
        poolCurator.flush();

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(pool1.getId(), 1);
        poolQuantities.put(pool2.getId(), 1);

        try {
            poolManager.entitleByPools(system, poolQuantities);
            fail();
        }
        catch (EntitlementRefusedException e) {
            assertNotNull(e.getResults());
            assertEquals(2, e.getResults().entrySet().size());
            assertEquals("rulefailed.no.entitlements.available", e.getResults().get(pool1.getId())
                .getErrors().get(0).getResourceKey());
            assertEquals("rulefailed.no.entitlements.available", e.getResults().get(pool2.getId())
                .getErrors().get(0).getResourceKey());
        }

    }

    @Test
    public void testBatchBindZeroQuantity() throws EntitlementRefusedException {
        Owner owner = createOwner();
        Product product = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(product);

        Consumer system = new Consumer()
            .setName("dev")
            .setUsername("user")
            .setOwner(owner)
            .setType(systemType);
        system.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(product.getId())
            .setProductName(product.getName()));
        consumerCurator.create(system);

        Pool pool1 = createPool(owner, product, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, system.getUuid());
        pool1.setConsumed(1L);
        poolCurator.create(pool1);

        assertEquals(1, poolCurator.get(pool1.getId()).getConsumed().intValue());
        Pool pool2 = createPool(owner, product, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        pool2.setConsumed(1L);
        poolCurator.create(pool2);

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(pool1.getId(), 0);
        poolQuantities.put(pool2.getId(), 0);

        List<Entitlement> results = poolManager.entitleByPools(system, poolQuantities);
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getQuantity().intValue());
        assertEquals(1, results.get(1).getQuantity().intValue());
    }

    @Test
    public void testCleanupExpiredPools() {
        long ct = System.currentTimeMillis();
        Date activeStart = new Date(ct + 3600000);
        Date activeEnd = new Date(ct + 7200000);
        Date expiredStart = new Date(ct - 7200000);
        Date expiredEnd = new Date(ct - 3600000);

        Owner owner = this.createOwner();
        Product product1 = this.createProduct("test-product-1", "Test Product 1");
        Product product2 = this.createProduct("test-product-2", "Test Product 2");
        Pool activePool = this.createPool(owner, product1, 1L, activeStart, activeEnd);
        Pool expiredPool = this.createPool(owner, product2, 1L, expiredStart, expiredEnd);

        this.poolManager.cleanupExpiredPools();

        assertNotNull(this.poolCurator.get(activePool.getId()));
        assertNull(this.poolCurator.get(expiredPool.getId()));
    }

    @Test
    public void testCleanupExpiredPoolsWithEntitlementEndDateOverrides() {
        long ct = System.currentTimeMillis();
        Date activeStart = new Date(ct + 3600000);
        Date activeEnd = new Date(ct + 7200000);
        Date expiredStart = new Date(ct - 7200000);
        Date expiredEnd = new Date(ct - 3600000);

        Owner owner = this.createOwner();
        List<Consumer> consumers = new LinkedList<>();
        List<Product> products = new LinkedList<>();
        List<Pool> pools = new LinkedList<>();
        List<Entitlement> entitlements = new LinkedList<>();

        int objCount = 6;

        for (int i = 0; i < objCount; ++i) {
            Consumer consumer = this.createConsumer(owner);
            Product product = this.createProduct("test-product-" + i, "Test Product " + i);
            Pool pool = (i % 2 == 0) ? this.createPool(owner, product, 1L, activeStart, activeEnd) :
                this.createPool(owner, product, 1L, expiredStart, expiredEnd);

            consumers.add(consumer);
            products.add(product);
            pools.add(pool);
        }

        entitlements.add(this.createEntitlement(owner, consumers.get(0), pools.get(2), null));
        entitlements.add(this.createEntitlement(owner, consumers.get(1), pools.get(3), null));
        entitlements.add(this.createEntitlement(owner, consumers.get(2), pools.get(4), null));
        entitlements.add(this.createEntitlement(owner, consumers.get(3), pools.get(5), null));
        entitlements.get(0).setEndDateOverride(activeEnd);
        entitlements.get(1).setEndDateOverride(activeEnd);
        entitlements.get(2).setEndDateOverride(expiredEnd);
        entitlements.get(3).setEndDateOverride(expiredEnd);

        for (Entitlement entitlement : entitlements) {
            this.entitlementCurator.merge(entitlement);
        }
        this.poolCurator.flush();

        this.poolManager.cleanupExpiredPools();

        assertNotNull(this.poolCurator.get(pools.get(0).getId())); // Active pool, no ent
        assertNull(this.poolCurator.get(pools.get(1).getId())); // Expired pool, no ent
        assertNotNull(this.poolCurator.get(pools.get(2).getId())); // Active pool, active ent
        assertNotNull(this.poolCurator.get(pools.get(3).getId())); // Expired pool, active ent
        assertNotNull(this.poolCurator.get(pools.get(4).getId())); // Active pool, expired ent
        assertNull(this.poolCurator.get(pools.get(5).getId())); // Expired pool, expired ent
    }

    @Test
    public void testCleanupExpiredNonDerivedPools() {
        long ct = System.currentTimeMillis();
        Date activeStart = new Date(ct + 3600000);
        Date activeEnd = new Date(ct + 7200000);
        Date expiredStart = new Date(ct - 7200000);
        Date expiredEnd = new Date(ct - 3600000);

        Owner owner = this.createOwner();
        Product product1 = this.createProduct("test-product-1", "Test Product 1");
        Product product2 = this.createProduct("test-product-2", "Test Product 2");
        Pool pool1 = this.createPool(owner, product1, 1L, activeStart, activeEnd);
        Pool pool2 = this.createPool(owner, product2, 1L, expiredStart, expiredEnd);
        Pool pool3 = this.createPool(owner, product2, 1L, activeStart, activeEnd);
        Pool pool4 = this.createPool(owner, product2, 1L, expiredStart, expiredEnd);

        pool3.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        pool4.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        this.poolCurator.merge(pool3);
        this.poolCurator.merge(pool4);

        this.poolManager.cleanupExpiredPools();

        assertNotNull(this.poolCurator.get(pool1.getId())); // Active pool, no attrib
        assertNull(this.poolCurator.get(pool2.getId())); // Expired pool, no attrib
        assertNotNull(this.poolCurator.get(pool3.getId())); // Active pool, derived attrib
        assertNull(this.poolCurator.get(pool4.getId())); // Expired pool, derived attrib
    }

    private Pool createPool(Owner owner, Product product, long quantity, Date startDate, Date endDate,
        String subscriptionId, String subscriptionSubkey) {

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(quantity)
            .setStartDate(startDate)
            .setEndDate(endDate)
            .setSourceSubscription(new SourceSubscription(subscriptionId, subscriptionSubkey));

        return this.poolCurator.create(pool);
    }

    @Test
    public void testCleanupExpiredDerivedPoolsAndItsEnt() {
        long ct = System.currentTimeMillis();
        Date expiredStart = new Date(ct - 7200000);
        Date expiredEnd = new Date(ct - 3600000);

        Owner owner = this.createOwner();
        Product product1 = this.createProduct("test-product-1", "Test Product 1");
        String suscriptionId = Util.generateDbUUID();
        Pool pool2 = this.createPool(
            owner, product1, 1L, expiredStart, expiredEnd, suscriptionId, PRIMARY_POOL_SUB_KEY);
        Pool pool3 = this.createPool(
            owner, product1, 1L, expiredStart, expiredEnd, suscriptionId, DERIVED_POOL_SUB_KEY);

        pool3.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        this.poolCurator.merge(pool3);

        Entitlement ent = this.createEntitlement(owner, this.createConsumer(owner), pool3, null);

        this.poolManager.cleanupExpiredPools();

        assertNull(this.poolCurator.get(pool2.getId()));
        assertNull(this.poolCurator.get(pool3.getId()));
        assertNull(this.entitlementCurator.get(ent.getId()));
    }

    @Test
    public void testRefreshPoolsWithNewSubscriptions() {
        Owner owner = this.createOwner();
        Product prod = this.createProduct();

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        this.refresherFactory.getRefresher(subAdapter).add(owner).run();
        List<Pool> pools = listPoolsByOwnerAndProduct(owner, prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        assertEquals(sub.getId(), newPool.getSubscriptionId());
        assertEquals(sub.getQuantity(), newPool.getQuantity());
        assertEquals(sub.getStartDate(), newPool.getStartDate());
        assertEquals(sub.getEndDate(), newPool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithChangedSubscriptions() {
        Owner owner = this.createOwner();
        Product prod = this.createProduct();
        Pool pool = createPool(owner, prod, 1000L,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        assertTrue(pool.getQuantity() < sub.getQuantity());
        assertNotSame(pool.getStartDate(), sub.getStartDate());
        assertNotSame(pool.getEndDate(), sub.getEndDate());

        pool.getSourceSubscription().setSubscriptionId(sub.getId());
        poolCurator.merge(pool);

        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);
        this.refresherFactory.getRefresher(subAdapter).add(owner).run();

        pool = poolCurator.get(pool.getId());
        assertEquals(sub.getId(), pool.getSubscriptionId());
        assertEquals(sub.getQuantity(), pool.getQuantity());
        assertEquals(sub.getStartDate(), pool.getStartDate());
        assertEquals(sub.getEndDate(), pool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithRemovedSubscriptions() {
        Owner owner = this.createOwner();
        Product prod = this.createProduct();

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));

        // This line is only present as a result of a (temporary?) fix for BZ 1452694. Once a
        // better fix has been implemented, the upstream pool ID can be removed.
        sub.setUpstreamPoolId("upstream_pool_id");

        subscriptions.add(sub);

        // Trigger the refresh:
        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);
        this.refresherFactory.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = listPoolsByOwnerAndProduct(owner, prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);
        String poolId = newPool.getId();

        // Now delete the subscription:
        subscriptions.remove(sub);

        // Trigger the refresh:
        subAdapter = new MockSubscriptionServiceAdapter(subscriptions);
        this.refresherFactory.getRefresher(subAdapter).add(owner).run();
        assertNull(poolCurator.get(poolId));
    }

    @Test
    public void testRefreshMultiplePools() {
        Owner owner = this.createOwner();
        Product prod = this.createProduct();
        Product prod2 = this.createProduct();

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        SubscriptionDTO sub2 = new SubscriptionDTO();
        sub2.setId(Util.generateDbUUID());
        sub2.setOwner(ownerDto);
        sub2.setProduct(this.modelTranslator.translate(prod2, ProductDTO.class));
        sub2.setQuantity(800L);
        sub2.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub2.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub2.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub2);

        // Trigger the refresh:
        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);
        this.refresherFactory.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(2, pools.size());
    }

    // test covers scenario from bug 1012386
    @Test
    public void testRefreshPoolsWithRemovedPrimaryPool() {
        config.setProperty(ConfigProperties.STANDALONE, "false");

        Owner owner = this.createOwner();
        Product prod = TestUtil.createProduct()
            .setAttribute(Product.Attributes.VIRT_LIMIT, "4");

        this.createProduct(prod);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        this.refresherFactory.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());
        String bonusId = "";
        String primaryId = "";

        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals(PRIMARY_POOL_SUB_KEY)) {
                poolCurator.delete(p);
                primaryId = p.getId();
            }
            else {
                bonusId = p.getId();
            }
        }

        // Trigger the refresh:
        subAdapter = new MockSubscriptionServiceAdapter(subscriptions);
        this.refresherFactory.getRefresher(subAdapter).add(owner).run();

        assertNull(poolCurator.get(primaryId), "Original Primary Pool should be gone");
        assertNotNull(poolCurator.get(bonusId), "Bonus Pool should be the same");
        // primary pool should have been recreated
        pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());
        boolean newPrimary = false;
        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals(PRIMARY_POOL_SUB_KEY)) {
                newPrimary = true;
            }
        }
        assertTrue(newPrimary);
    }

    // test covers a corollary scenario from bug 1012386
    @Test
    public void testRefreshPoolsWithRemovedBonusPool() {
        Owner owner = this.createOwner();
        Product prod = TestUtil.createProduct()
            .setAttribute(Product.Attributes.VIRT_LIMIT, "4");

        this.createProduct(prod);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setLastModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        this.refresherFactory.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());
        String bonusId = "";
        String primaryId = "";

        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY)) {
                poolCurator.delete(p);
                bonusId = p.getId();
            }
            else {
                primaryId = p.getId();
            }
        }

        // Trigger the refresh:
        this.refresherFactory.getRefresher(subAdapter).add(owner).run();

        assertNull(poolCurator.get(bonusId), "Original bonus pool should be gone");
        assertNotNull(poolCurator.get(primaryId), "Primary pool should be the same");
        // primary pool should have been recreated
        pools = poolCurator.getBySubscriptionId(owner, sub.getId());
        assertEquals(2, pools.size());

        boolean newBonus = pools.stream()
            .map(Pool::getSourceSubscription)
            .map(SourceSubscription::getSubscriptionSubKey)
            .anyMatch(DERIVED_POOL_SUB_KEY::equals);
        assertTrue(newBonus);
    }

    /**
     * @param pool
     * @param owner
     * @param consumer
     * @return
     */
    private Entitlement createEntitlementWithQ(Pool pool, Owner owner, Consumer consumer, int quantity,
        String date) throws ParseException {

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        Entitlement e1 = createEntitlement(owner, consumer, pool, null);
        e1.setQuantity(quantity);
        pool.getEntitlements().add(e1);

        this.entitlementCurator.create(e1);
        e1.getPool().setConsumed(e1.getPool().getConsumed() + quantity);
        this.poolCurator.merge(e1.getPool());

        e1.setCreated(dateFormat.parse(date));
        this.entitlementCurator.merge(e1);

        return e1;
    }

    private Pool doTestEntitlementsRevocationCommon(long subQ, int e1, int e2) throws ParseException {
        Owner owner = this.createOwner();
        Product prod = this.createProduct();

        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        org.candlepin.dto.manifest.v1.OwnerDTO ownerDto = this.modelTranslator.translate(owner,
            org.candlepin.dto.manifest.v1.OwnerDTO.class);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setId(Util.generateDbUUID());
        sub.setOwner(ownerDto);
        sub.setProduct(this.modelTranslator.translate(prod, ProductDTO.class));
        sub.setQuantity(1000L);
        sub.setStartDate(TestUtil.createDate(2009, 11, 30));
        sub.setEndDate(TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 10, 10, 30));
        sub.setLastModified(TestUtil.createDate(2015, 11, 30));
        subscriptions.add(sub);

        List<Pool> pools = poolManager.createAndEnrichPools(sub);
        assertTrue(pools.size() > 0);
        Pool pool = pools.get(0);

        sub.setQuantity(subQ);

        Owner retrieved = pool.getOwner();
        Consumer consumer = createConsumer(retrieved);
        Consumer consumer1 = createConsumer(retrieved);

        pool = this.poolCurator.get(pool.getId());
        createEntitlementWithQ(pool, retrieved, consumer, e1, "01/02/2010");
        createEntitlementWithQ(pool, retrieved, consumer1, e2, "01/01/2010");
        assertEquals(pool.getConsumed(), Long.valueOf(e1 + e2));

        SubscriptionServiceAdapter subAdapter = new MockSubscriptionServiceAdapter(subscriptions);

        this.refresherFactory.getRefresher(subAdapter).add(retrieved).run();
        pool = poolCurator.get(pool.getId());
        return pool;
    }

    @Test
    public void testEntitlementsRevocationWithLifoOrder() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(7, 4, 5);
        assertEquals(5L, this.poolCurator.get(pool.getId()).getConsumed());
    }

    @Test
    public void testEntitlementsRevocationWithNoOverflow() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(10, 4, 5);
        assertEquals(9L, this.poolCurator.get(pool.getId()).getConsumed());
    }

    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner, this.systemType);
        Product product = this.createProduct();

        Long numAvailEntitlements = 1L;
        Pool consumerPool = this.createPool(owner, product, numAvailEntitlements,
            TestUtil.createDateOffset(-1, 0, 0), TestUtil.createDateOffset(1, 0, 0));

        assertEquals(0, consumer.getEntitlements().size());

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(consumerPool.getId(), 1);
        this.poolManager.entitleByPools(consumer, poolQuantities);

        consumerPool = poolCurator.get(consumerPool.getId());
        assertFalse(consumerPool.entitlementsAvailable(1));
        assertEquals(1, consumerPool.getEntitlements().size());
    }

    @Test
    public void createEntitlementShouldUpdateConsumer() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner, this.systemType);
        Product product = this.createProduct();

        Long numAvailEntitlements = 1L;
        Pool consumerPool = this.createPool(owner, product, numAvailEntitlements,
            TestUtil.createDateOffset(-1, 0, 0), TestUtil.createDateOffset(1, 0, 0));

        assertEquals(0, consumer.getEntitlements().size());

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(consumerPool.getId(), 1);
        poolManager.entitleByPools(consumer, poolQuantities);

        assertEquals(1, consumerCurator.get(consumer.getId()).getEntitlements().size());
    }

    // sunny test - real rules not invoked here. Can only be sure the counts are recorded.
    // Rule tests already exist for quantity filter.
    // Will use spec tests to see if quantity rules are followed in this scenario.
    @Test
    public void testEntitlementQuantityChange() throws EntitlementRefusedException {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner, this.systemType);

        Product product = TestUtil.createProduct()
            .setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");

        product = this.createProduct(product);

        Pool consumerPool = this.createPool(owner, product, 1000L,
            TestUtil.createDateOffset(-1, 0, 0), TestUtil.createDateOffset(1, 0, 0));

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(consumerPool.getId(), 3);
        List<Entitlement> entitlements = poolManager.entitleByPools(consumer, poolQuantities);

        Entitlement ent = entitlements.get(0);
        assertEquals(3, (int) ent.getQuantity());

        poolManager.adjustEntitlementQuantity(consumer, ent, 5);
        Entitlement ent2 = entitlementCurator.get(ent.getId());
        assertEquals(5, (int) ent2.getQuantity());

        Pool pool2 = poolCurator.get(consumerPool.getId());
        assertEquals(5, (long) pool2.getConsumed());
        assertEquals(1, pool2.getEntitlements().size());
    }

    private List<Pool> listPoolsByOwnerAndProduct(Owner owner, String productId) {
        PoolQualifier qualifier = new PoolQualifier()
            .setOwnerId(owner.getId())
            .addProductId(productId);

        return poolCurator.listAvailableEntitlementPools(qualifier)
            .getPageData();
    }
}
