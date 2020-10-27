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
package org.candlepin.controller;

import static org.apache.commons.collections.CollectionUtils.containsAny;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.reset;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventSink;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Branding;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.Product;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;


public class PoolManagerFunctionalTest extends DatabaseTestFixture {
    private static final String PRODUCT_MONITORING = "monitoring";
    private static final String PRODUCT_PROVISIONING = "provisioning";
    private static final String PRODUCT_VIRT_HOST = "virtualization_host";
    private static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    private static final String PRODUCT_VIRT_GUEST = "virt_guest";

    @Inject
    private CandlepinPoolManager poolManager;

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
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

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

        poolManager.getRefresher(subAdapter).add(o).run();

        this.systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);

        parentSystem = new Consumer("system", "user", o, systemType);
        parentSystem.getFacts().put("total_guests", "0");
        consumerCurator.create(parentSystem);

        childVirtSystem = new Consumer("virt system", "user", o, systemType);

        consumerCurator.create(childVirtSystem);
    }

    @Test
    public void testEntitlementPoolsCreated() {
        List<Pool> pools = poolCurator.listByOwner(o).list();
        assertTrue(pools.size() > 0);

        Pool virtHostPool = poolCurator.listByOwnerAndProduct(o, virtHost.getId()).get(0);
        assertNotNull(virtHostPool);
    }

    @Test
    public void testQuantityCheck() throws Exception {
        Pool monitoringPool = poolCurator.listByOwnerAndProduct(o, monitoring.getId()).get(0);
        assertEquals(Long.valueOf(5), monitoringPool.getQuantity());
        AutobindData data = AutobindData.create(parentSystem, o).on(new Date())
            .forProducts(new String[] { monitoring.getId() });
        for (int i = 0; i < 5; i++) {
            List<Entitlement> entitlements = poolManager.entitleByProducts(data);
            assertEquals(1, entitlements.size());
        }

        // The cert should specify 5 monitoring entitlements, taking a 6th should fail:
        assertEquals(0, poolManager.entitleByProducts(data).size());
        assertEquals(Long.valueOf(5), monitoringPool.getConsumed());
    }

    @Test
    public void testDeletePool() {
        Pool pool = createPool(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);
        List<Pool> pools = poolCurator.listByOwner(o).list();
        assertEquals(5, poolCurator.listByOwner(o).list().size());
        poolManager.deletePools(Arrays.asList(pool, pools.get(0)));
        pools = poolCurator.listByOwner(o).list();
        assertEquals(3, pools.size());
        poolManager.deletePools(pools);
        pools = poolCurator.listByOwner(o).list();
        assertTrue(pools.isEmpty());
    }

    @Test
    public void testRevocation() throws Exception {
        AutobindData data = AutobindData.create(parentSystem, o).on(new Date())
            .forProducts(new String[] { monitoring.getId() });
        Entitlement e = poolManager.entitleByProducts(data).get(0);
        poolManager.revokeEntitlement(e);

        List<Entitlement> entitlements = entitlementCurator.listByConsumer(parentSystem);
        assertTrue(entitlements.isEmpty());
    }

    @Test
    public void testConsumeQuantity() throws Exception {
        Pool monitoringPool = poolCurator.listByOwnerAndProduct(o,
            monitoring.getId()).get(0);
        assertEquals(Long.valueOf(5), monitoringPool.getQuantity());

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(monitoringPool.getId(), 3);
        List<Entitlement> eList = poolManager.entitleByPools(parentSystem, poolQuantities);
        assertEquals(1, eList.size());
        assertEquals(Long.valueOf(3), monitoringPool.getConsumed());
        consumerCurator.get(parentSystem.getId());
        assertEquals(3, parentSystem.getEntitlementCount());

        poolManager.revokeEntitlement(eList.get(0));
        assertEquals(Long.valueOf(0), monitoringPool.getConsumed());
        consumerCurator.get(parentSystem.getId());
        assertEquals(0, parentSystem.getEntitlementCount());
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithSingleEntitlement()
        throws Exception {
        AutobindData data = AutobindData.create(childVirtSystem, o).on(new Date())
            .forProducts(new String[] { provisioning.getId() });
        this.entitlementCurator.refresh(poolManager.entitleByProducts(data).get(0));
        regenerateECAndAssertNotSameCertificates();
    }

    @Test
    public void testFabricateWithBranding() {
        List<Pool> masterPools = poolManager.getPoolsBySubscriptionId(sub4.getId()).list();
        Pool masterPool = null;
        for (Pool pool : masterPools) {
            if (pool.getType() == Pool.PoolType.NORMAL) {
                masterPool = pool;
            }
        }

        Subscription fabricated = poolManager.fabricateSubscriptionFromPool(masterPool);
        assertNotNull(fabricated);
        assertNotNull(masterPool.getProduct());
        assertNotNull(fabricated.getProduct());

        Collection<Branding> brandingSet = fabricated.getProduct().getBranding();

        assertNotNull(brandingSet);
        assertEquals(2, brandingSet.size());
        ArrayList<Branding> list = new ArrayList<>(brandingSet);
        list.sort(Comparator.comparing(Branding::getName));

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
        AutobindData data = AutobindData.create(childVirtSystem, o).on(new Date())
            .forProducts(new String[] { provisioning.getId() });
        this.entitlementCurator.refresh(poolManager.entitleByProducts(data).get(0));
        this.entitlementCurator.refresh(poolManager.entitleByProducts(data).get(0));
        regenerateECAndAssertNotSameCertificates();
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithNoEntitlement() {
        reset(this.eventSink); // pool creation events went out from setup
        poolManager.regenerateCertificatesOf(childVirtSystem, true);
        assertEquals(0, collectEntitlementCertIds(this.childVirtSystem).size());
        Mockito.verifyZeroInteractions(this.eventSink);
    }

    @Test
    public void testEntitleByProductsWithModifierAndModifiee() throws EntitlementRefusedException {
        Product modifier = TestUtil.createProduct("modifier", "modifier");

        Set<String> modified = new HashSet<>();
        modified.add(PRODUCT_VIRT_HOST);
        Content content = TestUtil.createContent("modifier-content", "modifier-content");
        content.setModifiedProductIds(modified);
        modifier.addContent(content, true);

        contentCurator.create(content);
        productCurator.create(modifier);
        this.ownerContentCurator.mapContentToOwner(content, this.o);

        List<SubscriptionDTO> subscriptions = new LinkedList<>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

        SubscriptionDTO sub = new SubscriptionDTO();
        sub.setQuantity(5L);
        sub.setOwner(this.modelTranslator.translate(o, OwnerDTO.class));
        sub.setProduct(this.modelTranslator.translate(modifier, ProductDTO.class));
        sub.setStartDate(new Date());
        sub.setEndDate(TestUtil.createDate(3020, 12, 12));
        sub.setLastModified(new Date());

        sub.setId(Util.generateDbUUID());

        subscriptions.add(sub);

        poolManager.getRefresher(subAdapter).add(o).run();

        // This test simulates https://bugzilla.redhat.com/show_bug.cgi?id=676870
        // where entitling first to the modifier then to the modifiee causes the modifier's
        // entitlement cert to get regenerated, but since it's all in the same http call,
        // this ends up causing a hibernate failure (the old cert is asked to be deleted,
        // but it hasn't been saved yet). Since getting the pool ordering right is tricky
        // inside an entitleByProducts call, we do it in two singular calls here.
        AutobindData data = AutobindData.create(parentSystem, o).on(new Date())
            .forProducts(new String[] { "modifier" });

        poolManager.entitleByProducts(data);

        try {
            data = AutobindData.create(parentSystem, o).on(new Date())
                .forProducts(new String[] { PRODUCT_VIRT_HOST });

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
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

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
        poolManager.getRefresher(subAdapter).add(o).run();

        List<Pool> pools = poolCurator.listByOwnerAndProduct(o, product1.getId());
        assertEquals(1, pools.size());

        // now alter the product behind the sub, and make sure the pool is also updated
        subscription.setProduct(this.modelTranslator.translate(product2, ProductDTO.class));

        // set up initial pool
        poolManager.getRefresher(subAdapter).add(o).run();

        pools = poolCurator.listByOwnerAndProduct(o, product2.getId());
        assertEquals(1, pools.size());
    }

    @Test
    public void testListAllForConsumerIncludesWarnings() {
        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(
            parentSystem, null, parentSystem.getOwnerId(), null, null, null, true,
            new PoolFilterBuilder(), new PageRequest(), false, false, null);
        assertEquals(4, results.getPageData().size());

        Pool pool = createPool(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        parentSystem.setFact("cpu.sockets", "4");
        results = poolManager.listAvailableEntitlementPools(parentSystem, null,
            parentSystem.getOwnerId(), null, null, null, true, new PoolFilterBuilder(),
            new PageRequest(), false, false, null);
        // Expect the warnings to be included. Should have one more pool available.
        assertEquals(5, results.getPageData().size());
    }

    @Test
    public void testListAllForConsumerExcludesErrors() {
        Product p = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(p);

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(
            parentSystem, null, parentSystem.getOwnerId(), null, null, null, true,
            new PoolFilterBuilder(), new PageRequest(), false, false, null);
        assertEquals(4, results.getPageData().size());

        // Creating a pool with no entitlements available, which will trigger
        // a rules error:
        Pool pool = createPool(o, p, 0L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        results = poolManager.listAvailableEntitlementPools(parentSystem, null,
            parentSystem.getOwnerId(), null, null, null, true, new PoolFilterBuilder(),
            new PageRequest(), false, false, null);
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
        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(
            null, ak, parentSystem.getOwnerId(), null, null, null, true,
            new PoolFilterBuilder(), new PageRequest(), false, false, null);
        assertEquals(4, results.getPageData().size());

        // Creating a pool with no entitlements available, which does not trigger
        // a rules error:
        Pool pool = createPool(o, p, 0L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        results = poolManager.listAvailableEntitlementPools(null, ak,
            parentSystem.getOwnerId(), null, null, null, true, new PoolFilterBuilder(),
            new PageRequest(), false, false, null);
        // Pool in error should not be included. Should have the same number of
        // initial pools.
        assertEquals(5, results.getPageData().size());
    }

    @Test
    public void testListForConsumerExcludesWarnings() {
        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(
            parentSystem, null, parentSystem.getOwnerId(), null, null, null, true,
            new PoolFilterBuilder(), new PageRequest(), false, false, null);
        assertEquals(4, results.getPageData().size());

        Pool pool = createPool(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        parentSystem.setFact("cpu.cpu_socket(s)", "4");

        results = poolManager.listAvailableEntitlementPools(parentSystem, null,
            parentSystem.getOwnerId(), null, null, null, false,
            new PoolFilterBuilder(), new PageRequest(), false, false, null);

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

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(
            childVirtSystem, null, o.getId(), virtGuest.getId(), null, null, true,
            new PoolFilterBuilder(), new PageRequest(), false, false, null);
        int newbornPools = results.getPageData().size();
        childVirtSystem.setCreated(TestUtil.createDate(2000, 1, 1));
        consumerCurator.update(childVirtSystem);

        results = poolManager.listAvailableEntitlementPools(
            childVirtSystem, null, o.getId(), virtGuest.getId(), null, null, true,
            new PoolFilterBuilder(), new PageRequest(), false, false, null);

        assertEquals(newbornPools - 1, results.getPageData().size());
    }

    private void regenerateECAndAssertNotSameCertificates() {
        Set<EntitlementCertificate> oldsIds =
            collectEntitlementCertIds(this.childVirtSystem);
        poolManager.regenerateCertificatesOf(childVirtSystem, false);
        Mockito.verify(this.eventSink, Mockito.times(oldsIds.size()))
            .queueEvent(any(Event.class));
        Set<EntitlementCertificate> newIds =
            collectEntitlementCertIds(this.childVirtSystem);
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
    public void testListConditionDevPools() {
        Owner owner = createOwner();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(p);

        Pool pool1 = createPool(owner, p, 10L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        pool1.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        poolCurator.create(pool1);
        Pool pool2 = createPool(owner, p, 10L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool2);

        Consumer devSystem = new Consumer("dev", "user", owner, systemType);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));
        Consumer nonDevSystem = new Consumer("system", "user", owner, systemType);
        nonDevSystem.addInstalledProduct(new ConsumerInstalledProduct(p));

        Page<List<Pool>> results = poolManager.listAvailableEntitlementPools(devSystem, null,
            owner.getId(), null, null, null, true, new PoolFilterBuilder(), new PageRequest(),
            false, false, null);
        assertEquals(2, results.getPageData().size());

        results = poolManager.listAvailableEntitlementPools(nonDevSystem, null,
            owner.getId(), null, null, null, true, new PoolFilterBuilder(), new PageRequest(),
            false, false, null);
        assertEquals(1, results.getPageData().size());
        Pool found2 = results.getPageData().get(0);
        assertEquals(pool2, found2);
    }

    @Test
    public void testDevPoolRemovalAtUnbind() throws EntitlementRefusedException {
        Owner owner = createOwner();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(p);

        Consumer devSystem = new Consumer("dev", "user", owner, systemType);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));
        consumerCurator.create(devSystem);

        Pool pool1 = createPool(owner, p, 10L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        pool1.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, devSystem.getUuid());
        poolCurator.create(pool1);
        Pool pool2 = createPool(owner, p, 10L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool2);
        List<String> possPools = new ArrayList<>();
        possPools.add(pool1.getId());

        AutobindData ad = new AutobindData(devSystem, owner);
        ad.setPossiblePools(possPools);
        List<Entitlement> results = poolManager.entitleByProducts(ad);
        assertEquals(1, results.size());
        assertEquals(results.get(0).getPool(), pool1);

        Entitlement e = entitlementCurator.get(results.get(0).getId());
        poolManager.revokeEntitlement(e);
        assertNull(poolCurator.get(pool1.getId()));
        assertNotNull(poolCurator.get(pool2.getId()));
    }

    @Test
    public void testDevPoolBatchBind() throws EntitlementRefusedException {
        Owner owner = createOwner();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(p);

        Consumer devSystem = new Consumer("dev", "user", owner, systemType);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));
        consumerCurator.create(devSystem);

        Pool pool1 = createPool(owner, p, 10L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        pool1.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, devSystem.getUuid());
        poolCurator.create(pool1);
        Pool pool2 = createPool(owner, p, 10L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool2);

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(pool1.getId(), 1);
        poolQuantities.put(pool2.getId(), 1);

        List<Entitlement> results = poolManager.entitleByPools(devSystem, poolQuantities);
        assertEquals(2, results.size());
        assertTrue(results.get(0).getPool() == pool1 || results.get(0).getPool() == pool2);
        assertTrue(results.get(1).getPool() == pool1 || results.get(1).getPool() == pool2);

        pool1 = poolCurator.get(pool1.getId());
        pool2 = poolCurator.get(pool2.getId());

        assertEquals(1, pool1.getConsumed().intValue());
        assertEquals(1, pool2.getConsumed().intValue());

    }

    @Test
    public void testBatchBindError() {
        Owner owner = createOwner();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        p.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        productCurator.create(p);

        Consumer devSystem = new Consumer("dev", "user", owner, systemType);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));
        consumerCurator.create(devSystem);

        Pool pool1 = createPool(owner, p, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        pool1.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, devSystem.getUuid());
        poolCurator.create(pool1);
        Entitlement ent = createEntitlement(owner, devSystem, pool1,
            createEntitlementCertificate("keycert", "cert"));
        ent.setQuantity(1);
        entitlementCurator.create(ent);
        pool1.setConsumed(pool1.getConsumed() + 1);
        poolCurator.merge(pool1);
        poolCurator.flush();

        assertEquals(1, poolCurator.get(pool1.getId()).getConsumed().intValue());
        Pool pool2 = createPool(owner, p, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool2);
        Entitlement ent2 = createEntitlement(owner, devSystem, pool2,
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
            List<Entitlement> results = poolManager.entitleByPools(devSystem, poolQuantities);
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
        Product p = TestUtil.createProduct("test-product", "Test Product");
        productCurator.create(p);

        Consumer devSystem = new Consumer("dev", "user", owner, systemType);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));
        consumerCurator.create(devSystem);

        Pool pool1 = createPool(owner, p, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        pool1.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        pool1.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, devSystem.getUuid());
        pool1.setConsumed(1L);
        poolCurator.create(pool1);

        assertEquals(1, poolCurator.get(pool1.getId()).getConsumed().intValue());
        Pool pool2 = createPool(owner, p, 1L, TestUtil.createDate(2000, 3, 2),
            TestUtil.createDate(2050, 3, 2));
        pool2.setConsumed(1L);
        poolCurator.create(pool2);

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(pool1.getId(), 0);
        poolQuantities.put(pool2.getId(), 0);

        List<Entitlement> results = poolManager.entitleByPools(devSystem, poolQuantities);
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
        Product product1 = this.createProduct("test-product-1", "Test Product 1", owner);
        Product product2 = this.createProduct("test-product-2", "Test Product 2", owner);
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
            Product product = this.createProduct("test-product-" + i, "Test Product " + i, owner);
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
        assertNull(this.poolCurator.get(pools.get(1).getId()));    // Expired pool, no ent
        assertNotNull(this.poolCurator.get(pools.get(2).getId())); // Active pool, active ent
        assertNotNull(this.poolCurator.get(pools.get(3).getId())); // Expired pool, active ent
        assertNotNull(this.poolCurator.get(pools.get(4).getId())); // Active pool, expired ent
        assertNull(this.poolCurator.get(pools.get(5).getId()));    // Expired pool, expired ent
    }

    @Test
    public void testCleanupExpiredNonDerivedPools() {
        long ct = System.currentTimeMillis();
        Date activeStart = new Date(ct + 3600000);
        Date activeEnd = new Date(ct + 7200000);
        Date expiredStart = new Date(ct - 7200000);
        Date expiredEnd = new Date(ct - 3600000);

        Owner owner = this.createOwner();
        Product product1 = this.createProduct("test-product-1", "Test Product 1", owner);
        Product product2 = this.createProduct("test-product-2", "Test Product 2", owner);
        Pool pool1 = this.createPool(owner, product1, 1L, activeStart, activeEnd);
        Pool pool2 = this.createPool(owner, product2, 1L, expiredStart, expiredEnd);
        Pool pool3 = this.createPool(owner, product2, 1L, activeStart, activeEnd);
        Pool pool4 = this.createPool(owner, product2, 1L, expiredStart, expiredEnd);

        pool3.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        pool4.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        this.poolCurator.merge(pool3);
        this.poolCurator.merge(pool4);

        this.poolManager.cleanupExpiredPools();

        assertNotNull(this.poolCurator.get(pool1.getId()));        // Active pool, no attrib
        assertNull(this.poolCurator.get(pool2.getId()));           // Expired pool, no attrib
        assertNotNull(this.poolCurator.get(pool3.getId()));        // Active pool, derived attrib
        assertNull(this.poolCurator.get(pool4.getId()));           // Expired pool, derived attrib
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
        Product product1 = this.createProduct("test-product-1", "Test Product 1", owner);
        String suscriptionId = Util.generateDbUUID();
        Pool pool2 = this.createPool(owner, product1, 1L, expiredStart, expiredEnd, suscriptionId, "master");
        Pool pool3 = this.createPool(owner, product1, 1L, expiredStart, expiredEnd, suscriptionId, "derived");

        pool3.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        this.poolCurator.merge(pool3);

        Entitlement ent = this.createEntitlement(owner, this.createConsumer(owner), pool3, null);

        this.poolManager.cleanupExpiredPools();

        assertNull(this.poolCurator.get(pool2.getId()));
        assertNull(this.poolCurator.get(pool3.getId()));
        assertNull(this.entitlementCurator.get(ent.getId()));
    }

    @Test
    public void testRevocationRevokesEntitlementCertSerial() throws Exception {
        AutobindData data = AutobindData.create(parentSystem, o).on(new Date())
            .forProducts(new String[] { monitoring.getId() });
        Entitlement e = poolManager.entitleByProducts(data).get(0);
        CertificateSerial serial = e.getCertificates().iterator().next().getSerial();
        poolManager.revokeEntitlement(e);

        List<Entitlement> entitlements = entitlementCurator.listByConsumer(parentSystem);
        assertTrue(entitlements.isEmpty());

        CertificateSerial revoked = certSerialCurator.get(serial.getId());
        assertTrue(revoked.isRevoked(), "cert serial should be marked as revoked once deleted!");
    }
}

