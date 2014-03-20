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
import static org.apache.commons.collections.TransformerUtils.invokerTransformer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.reset;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventSink;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.Subscription;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.apache.commons.collections.Transformer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityNotFoundException;

public class PoolManagerFunctionalTest extends DatabaseTestFixture {

    public static final String PRODUCT_MONITORING = "monitoring";
    public static final String PRODUCT_PROVISIONING = "provisioning";
    public static final String PRODUCT_VIRT_HOST = "virtualization_host";
    public static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    public static final String PRODUCT_VIRT_GUEST = "virt_guest";

    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    private Product monitoring;
    private Product provisioning;
    private Product socketLimitedProduct;

    private ConsumerType systemType;

    private Owner o;
    private Consumer parentSystem;
    private Consumer childVirtSystem;
    private EventSink eventSink;

    @Before
    public void setUp() throws Exception {
        o = createOwner();
        ownerCurator.create(o);
        virtHost = new Product(PRODUCT_VIRT_HOST, PRODUCT_VIRT_HOST);
        virtHostPlatform = new Product(PRODUCT_VIRT_HOST_PLATFORM,
            PRODUCT_VIRT_HOST_PLATFORM);
        virtGuest = new Product(PRODUCT_VIRT_GUEST, PRODUCT_VIRT_GUEST);
        monitoring = new Product(PRODUCT_MONITORING, PRODUCT_MONITORING);
        monitoring.addAttribute(new ProductAttribute("multi-entitlement", "yes"));

        provisioning = new Product(PRODUCT_PROVISIONING, PRODUCT_PROVISIONING);
        provisioning.addAttribute(new ProductAttribute("multi-entitlement", "yes"));

        virtHost.addAttribute(new ProductAttribute(PRODUCT_VIRT_HOST, ""));
        virtHostPlatform.addAttribute(new ProductAttribute(PRODUCT_VIRT_HOST_PLATFORM,
            ""));
        virtGuest.addAttribute(new ProductAttribute(PRODUCT_VIRT_GUEST, ""));
        monitoring.addAttribute(new ProductAttribute(PRODUCT_MONITORING, ""));
        provisioning.addAttribute(new ProductAttribute(PRODUCT_PROVISIONING, ""));

        socketLimitedProduct = new Product("socket-limited-prod",
            "Socket Limited Product");
        socketLimitedProduct.addAttribute(new ProductAttribute("sockets", "2"));
        productCurator.create(socketLimitedProduct);

        productAdapter.createProduct(virtHost);
        productAdapter.createProduct(virtHostPlatform);
        productAdapter.createProduct(virtGuest);
        productAdapter.createProduct(monitoring);
        productAdapter.createProduct(provisioning);

        subCurator.create(new Subscription(o, virtHost, new HashSet<Product>(),
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));
        subCurator.create(new Subscription(o, virtHostPlatform, new HashSet<Product>(),
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));

        subCurator.create(new Subscription(o, monitoring, new HashSet<Product>(),
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));
        subCurator.create(new Subscription(o, provisioning, new HashSet<Product>(),
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));

        poolManager.getRefresher().add(o).run();

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
        List<Pool> pools = poolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        Pool virtHostPool = poolCurator.listByOwnerAndProduct(o, virtHost.getId()).get(0);
        assertNotNull(virtHostPool);
    }

    @Test
    public void testQuantityCheck() throws Exception {
        Pool monitoringPool = poolCurator.listByOwnerAndProduct(o,
                monitoring.getId()).get(0);
        assertEquals(Long.valueOf(5), monitoringPool.getQuantity());
        for (int i = 0; i < 5; i++) {
            List<Entitlement> entitlements = poolManager.entitleByProducts(parentSystem,
                new String [] {monitoring.getId()}, new Date());
            assertEquals(1, entitlements.size());
        }

        // The cert should specify 5 monitoring entitlements, taking a 6th should fail:
        try {
            poolManager.entitleByProducts(parentSystem, new String[] {monitoring.getId()},
                new Date());
            fail();
        }
        // With criteria filtered pools we end up with a RuntimeException
        // here.
        catch (RuntimeException e) {
            //expected
        }

        assertEquals(Long.valueOf(5), monitoringPool.getConsumed());
    }

    @Test
    public void testRevocation() throws Exception {
        Entitlement e = poolManager.entitleByProducts(parentSystem,
            new String[] {monitoring.getId()}, new Date()).get(0);
        poolManager.revokeEntitlement(e);

        List<Entitlement> entitlements = entitlementCurator.listByConsumer(parentSystem);
        assertTrue(entitlements.isEmpty());
    }

    @Test
    public void testConsumeQuantity() throws Exception {
        Pool monitoringPool = poolCurator.listByOwnerAndProduct(o,
            monitoring.getId()).get(0);
        assertEquals(Long.valueOf(5), monitoringPool.getQuantity());

        Entitlement e = poolManager.entitleByPool(parentSystem, monitoringPool, 3);
        assertNotNull(e);
        assertEquals(Long.valueOf(3), monitoringPool.getConsumed());

        poolManager.revokeEntitlement(e);
        assertEquals(Long.valueOf(0), monitoringPool.getConsumed());
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithSingleEntitlement()
        throws Exception {
        this.entitlementCurator.refresh(poolManager.entitleByProducts(this.childVirtSystem,
            new String[] {provisioning.getId()}, new Date()).get(0));
        regenerateECAndAssertNotSameCertificates();
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithMultipleEntitlements()
        throws EntitlementRefusedException {
        this.entitlementCurator.refresh(poolManager.entitleByProducts(
            this.childVirtSystem, new String[] {provisioning.getId()}, new Date()).get(0));
        this.entitlementCurator.refresh(poolManager.entitleByProducts(this.childVirtSystem,
            new String[] {monitoring.getId()}, new Date()).get(0));
        regenerateECAndAssertNotSameCertificates();
    }

    @Test
    public void testRegenerateEntitlementCertificatesWithNoEntitlement() {
        reset(this.eventSink); // pool creation events went out from setup
        poolManager.regenerateEntitlementCertificates(childVirtSystem, true);
        assertEquals(0, collectEntitlementCertIds(this.childVirtSystem).size());
        Mockito.verifyZeroInteractions(this.eventSink);
    }

    @Test
    public void testEntitleByProductsWithModifierAndModifiee()
        throws EntitlementRefusedException {
        Product modifier = new Product("modifier", "modifier");

        Set<String> modified = new HashSet<String>();
        modified.add(PRODUCT_VIRT_HOST);
        Content content = new Content("modifier-content", "modifier-content",
            "modifer-content", "yum", "us", "here", "here", "");
        content.setModifiedProductIds(modified);
        modifier.addContent(content);

        contentCurator.create(content);
        productAdapter.createProduct(modifier);

        subCurator.create(new Subscription(o, modifier, new HashSet<Product>(),
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));

        poolManager.getRefresher().add(o).run();


        // This test simulates https://bugzilla.redhat.com/show_bug.cgi?id=676870
        // where entitling first to the modifier then to the modifiee causes the modifier's
        // entitlement cert to get regenerated, but since it's all in the same http call,
        // this ends up causing a hibernate failure (the old cert is asked to be deleted,
        // but it hasn't been saved yet). Since getting the pool ordering right is tricky
        // inside an entitleByProducts call, we do it in two singular calls here.
        poolManager.entitleByProducts(this.parentSystem, new String[] {"modifier"},
            new Date());

        try {
            poolManager.entitleByProducts(this.parentSystem,
                new String[] {PRODUCT_VIRT_HOST}, new Date());
        }
        catch (EntityNotFoundException e) {
            throw e;
//            fail("Hibernate failed to properly save entitlement certs!");
        }

        // If we get here, no exception was raised, so we're happy!
    }

    @Test
    public void testRefreshPoolsWithChangedProductShouldUpdatePool() {
        Product product1 = TestUtil.createProduct("product 1", "Product 1");
        Product product2 = TestUtil.createProduct("product 2", "Product 2");

        productAdapter.createProduct(product1);
        productAdapter.createProduct(product2);

        Subscription subscription = new Subscription(o, product1, new HashSet<Product>(),
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date());
        subCurator.create(subscription);

        // set up initial pool
        poolManager.getRefresher().add(o).run();

        List<Pool> pools = poolCurator.listByOwnerAndProduct(o, product1.getId());
        assertEquals(1, pools.size());

        // now alter the product behind the sub, and make sure the pool is also updated
        subscription.setProduct(product2);
        subCurator.merge(subscription);

        // set up initial pool
        poolManager.getRefresher().add(o).run();

        pools = poolCurator.listByOwnerAndProduct(o, product2.getId());
        assertEquals(1, pools.size());
    }

    @Test
    public void testListAllForConsumerIncludesWarnings() {
        Page<List<Pool>> results =
            poolManager.listAvailableEntitlementPools(parentSystem, parentSystem.getOwner(),
                null, null, true, true, new PoolFilterBuilder(), new PageRequest());
        assertEquals(4, results.getPageData().size());

        Pool pool = createPoolAndSub(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        parentSystem.setFact("cpu.sockets", "4");
        results = poolManager.listAvailableEntitlementPools(parentSystem,
            parentSystem.getOwner(), null, null, true, true, new PoolFilterBuilder(),
            new PageRequest());
        // Expect the warnings to be included. Should have one more pool available.
        assertEquals(5, results.getPageData().size());
    }

    @Test
    public void testListAllForConsumerExcludesErrors() {
        Product p = new Product("test-product", "Test Product");
        productCurator.create(p);

        Page<List<Pool>> results =
            poolManager.listAvailableEntitlementPools(parentSystem, parentSystem.getOwner(),
                null, null, true, true, new PoolFilterBuilder(), new PageRequest());
        assertEquals(4, results.getPageData().size());

        // Creating a pool with no entitlements available, which will trigger
        // a rules error:
        Pool pool = createPoolAndSub(o, p, 0L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        results = poolManager.listAvailableEntitlementPools(parentSystem,
            parentSystem.getOwner(), null, null, true, true, new PoolFilterBuilder(),
            new PageRequest());
        // Pool in error should not be included. Should have the same number of
        // initial pools.
        assertEquals(4, results.getPageData().size());
    }

    @Test
    public void testListForConsumerExcludesWarnings() {
        Page<List<Pool>> results =
            poolManager.listAvailableEntitlementPools(parentSystem, parentSystem.getOwner(),
                (String) null, null, true, false, new PoolFilterBuilder(),
                new PageRequest());
        assertEquals(4, results.getPageData().size());

        Pool pool = createPoolAndSub(o, socketLimitedProduct, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        parentSystem.setFact("cpu.cpu_socket(s)", "4");

        results = poolManager.listAvailableEntitlementPools(parentSystem,
            parentSystem.getOwner(), (String) null, null, true, false,
            new PoolFilterBuilder(), new PageRequest());
        // Pool in error should not be included. Should have the same number of
        // initial pools.
        assertEquals(4, results.getPageData().size());
    }

    /**
     *
     */
    private void regenerateECAndAssertNotSameCertificates() {
        Set<EntitlementCertificate> oldsIds =
            collectEntitlementCertIds(this.childVirtSystem);
        poolManager.regenerateEntitlementCertificates(childVirtSystem, false);
        Mockito.verify(this.eventSink, Mockito.times(oldsIds.size()))
            .sendEvent(any(Event.class));
        Set<EntitlementCertificate> newIds =
            collectEntitlementCertIds(this.childVirtSystem);
        assertFalse(containsAny(transform(oldsIds, invokerTransformer("getId")),
            transform(newIds, invokerTransformer("getId"))));
        assertFalse(containsAny(
            transform(oldsIds, invokerTransformer("getKey")),
            transform(newIds, invokerTransformer("getKey"))));
        assertFalse(containsAny(
            transform(oldsIds, invokerTransformer("getCert")),
            transform(newIds, invokerTransformer("getCert"))));
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> transform(Set<?> set, Transformer t) {
        Set<T> result = Util.newSet();
        for (Iterator iterator = set.iterator(); iterator.hasNext();) {
            result.add((T) t.transform(iterator.next()));
        }
        return result;
    }

    private Set<EntitlementCertificate> collectEntitlementCertIds(
        Consumer consumer) {
        Set<EntitlementCertificate> ids = Util.newSet();
        for (Entitlement entitlement : consumer.getEntitlements()) {
            for (EntitlementCertificate ec : entitlement.getCertificates()) {
                ids.add(ec);
            }
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
}
