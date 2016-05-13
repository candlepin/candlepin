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
package org.candlepin.model;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.hamcrest.Matchers;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.InExpression;
import org.hibernate.criterion.LogicalExpression;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;


public class PoolCuratorTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private ActivationKeyCurator activationKeyCurator;
    @Inject private UeberCertificateGenerator ueberCertGenerator;
    @Inject private CandlepinPoolManager poolManager;

    private Owner owner;
    private Product product;
    private Consumer consumer;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);

        ConsumerType systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);

        ConsumerType ueberCertType = new ConsumerType(ConsumerTypeEnum.UEBER_CERT);
        consumerTypeCurator.create(ueberCertType);

        product = TestUtil.createProduct(owner);
        productCurator.create(product);

        consumer = TestUtil.createConsumer(owner);
        consumer.setFact("cpu_cores", "4");
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);
    }

    @Test
    public void testPoolNotYetActive() {
        Pool pool = createPool(owner, product, 100L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            consumer, consumer.getOwner(), (Collection<String>) null, TestUtil.createDate(20450, 3, 2), true);
        assertEquals(0, results.size());
    }

    @Test
    public void testPoolExpired() {
        Pool pool = createPool(owner, product, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            consumer, consumer.getOwner(), (Collection<String>) null, TestUtil.createDate(2005, 3, 3), true);
        assertEquals(0, results.size());

        // If we specify no date filtering, the expired pool should be returned:
        results =
            poolCurator.listAvailableEntitlementPools(consumer, consumer.getOwner(),
                (String) null, null, true);
        assertEquals(1, results.size());
    }

    @Test
    public void testAvailablePoolsDoesNotIncludeUeberPool() throws Exception {
        Pool pool = createPool(owner, product, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        ueberCertGenerator.generate(owner, new NoAuthPrincipal());

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            consumer, consumer.getOwner(), (Collection<String>) null, null, true);
        assertEquals(1, results.size());
    }

    @Test
    public void availablePoolsCanBeFilteredByProductPoolAttribute() throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct(owner);
        product2.addAttribute(new ProductAttribute("cores", "8"));
        productCurator.create(product2);

        Pool pool2 = createPool(owner, product2, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("cores", "8");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters,
            req, false);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void availablePoolsCanBeFilteredByPoolAttribute() throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        pool2.setAttribute("virt_only", "true");
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("virt_only", "true");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters,
            req, false);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void availablePoolsCanBeFilteredByPoolId() throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addIdFilter(pool2.getId());

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters, req, false);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());

        filters = new PoolFilterBuilder();
        filters.addIdFilter(pool1.getId());
        filters.addIdFilter(pool2.getId());

        page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters, req, false);
        results = page.getPageData();
        assertEquals(2, results.size());
    }

    @Test
    public void availablePoolsCanNotBeFilteredByOverriddenAttribute() throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));

        // This product value should be overridden by the pool attr. Note that this product is used
        // by both pools, so its attributes will be reflected in both. Also note that only pool2 is
        // overriding the value.
        pool2.getProduct().setAttribute("virt_only", "true");
        pool2.setAttribute("virt_only", "false");
        poolCurator.create(pool2);

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("virt_only", "true");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters, null, false);
        List<Pool> results = page.getPageData();

        assertEquals(1, results.size());
        assertEquals(pool1, results.get(0));
    }

    @Test
    public void availablePoolsCanBeFilteredByBothPoolAndProductPoolAttribute()
        throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct(owner);
        product2.addAttribute(new ProductAttribute("cores", "4"));
        productCurator.create(product2);

        Pool pool2 = createPool(owner, product2, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        pool2.setAttribute("virt_only", "true");
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("virt_only", "true");
        filters.addAttributeFilter("cores", "4");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters, req, false);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void availablePoolsCanFilterByEmptyValueAttribute() throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct(owner);
        product2.addAttribute(new ProductAttribute("empty-attr", ""));
        productCurator.create(product2);

        Pool pool2 = createPool(owner, product2, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("empty-attr", "");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters, req, false);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void attributeFilterValuesAreNotCaseSensitive() {
        Product product1 = TestUtil.createProduct(owner);
        product1.addAttribute(new ProductAttribute("A", "foo"));
        product1.addAttribute(new ProductAttribute("B", "bar"));
        productCurator.create(product1);

        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool = createPool(owner, product1, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("A", "FOO");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters, req, false);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool, results.get(0));
    }

    /**
     * When filtering pools by product/pool attributes, filters specified with
     * the same attribute name are ORed, and different attributes are ANDed.
     *
     * For example applying the following filters:
     *
     * A1:foo, A1:bar, A2:biz
     *
     * will result in matches on the values of:
     * (A1 == foo OR A1 == bar) AND A2 == biz
     *
     * Another important note is that product attributes are
     * ORed with Pool attributes for each attribute specified.
     */
    @Test
    public void testAttributeFilterLogic() {

        Product product1 = TestUtil.createProduct(owner);
        product1.addAttribute(new ProductAttribute("A", "foo"));
        product1.addAttribute(new ProductAttribute("B", "bar"));
        productCurator.create(product1);

        Product product2 = TestUtil.createProduct(owner);
        product2.addAttribute(new ProductAttribute("A", "foo"));
        product2.addAttribute(new ProductAttribute("B", "zoo"));
        productCurator.create(product2);

        Product product3 = TestUtil.createProduct(owner);
        product3.addAttribute(new ProductAttribute("A", "biz"));
        product3.addAttribute(new ProductAttribute("B", "zoo"));
        productCurator.create(product3);

        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product1, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, product2, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool2);

        Pool pool3 = createPool(owner, product3, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool3);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("A", "foo");
        filters.addAttributeFilter("A", "biz");
        filters.addAttributeFilter("B", "zoo");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, (Collection<String>) null, null, activeDate, false, filters, req, false);
        List<Pool> results = page.getPageData();
        assertEquals(2, results.size());

        Pool[] expected = new Pool[]{ pool2, pool3 };
        assertTrue(results.containsAll(Arrays.asList(expected)));
    }

    @Test
    public void testProductName() {
        Product p = new Product("someProduct", "An Extremely Great Product", owner);
        productCurator.create(p);

        Pool pool = createPool(owner, p, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, p.getId());
        Pool onlyPool = results.get(0);

        assertEquals("An Extremely Great Product", onlyPool.getProductName());
    }

    @Test
    public void testProductNameViaFind() {
        Product p = new Product("another", "A Great Operating System", owner);
        productCurator.create(p);

        Pool pool = createPool(owner, p, 25L,
            TestUtil.createDate(1999, 1, 10), TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);
        pool = poolCurator.find(pool.getId());

        assertEquals("A Great Operating System", pool.getProductName());
    }

    @Test
    public void testProductNameViaFindAll() {
        Product p = new Product("another", "A Great Operating System", owner);
        productCurator.create(p);

        Pool pool = createPool(owner, p, 25L,
            TestUtil.createDate(1999, 1, 10), TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);
        pool = poolCurator.listAll().get(0);

        assertEquals("A Great Operating System", pool.getProductName());
    }

    @Test
    public void testFuzzyProductMatchingWithoutSubscription() {
        Product parent = TestUtil.createProduct(owner);
        productCurator.create(parent);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(product);

        Pool p = TestUtil.createPool(owner, parent, providedProducts, 5);
        poolCurator.create(p);
        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, product.getId());
        assertEquals(1, results.size());
    }

    @Test
    public void testPoolProducts() {
        Product another = TestUtil.createProduct(owner);
        productCurator.create(another);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(another);

        Pool pool = TestUtil.createPool(owner, product, providedProducts, 5);
        poolCurator.create(pool);
        pool = poolCurator.find(pool.getId());
        assertTrue(pool.getProvidedProducts().size() > 0);
    }

    // Note:  This simply tests that the multiplier is read and used in pool creation.
    //        All of the null/negative multiplier test cases are in ProductTest
    @Test
    public void testMultiplierCreation() {
        Product product = new Product("someProduct", "An Extremely Great Product", owner, 10L);
        productCurator.create(product);

        Subscription sub = new Subscription(owner, product, new HashSet<Product>(), 16L,
            TestUtil.createDate(2006, 10, 21), TestUtil.createDate(2020, 1, 1), new Date());
        sub.setId(Util.generateDbUUID());

        Pool newPool = poolManager.createAndEnrichPools(sub).get(0);
        List<Pool> pools = poolCurator.lookupBySubscriptionId(sub.getId());

        assertEquals(160L, pools.get(0).getQuantity().longValue());
        assertEquals(newPool.getQuantity(), pools.get(0).getQuantity());
    }

    @Test
    public void testlookupBySubscriptionIds() {
        Product product = new Product("someProduct", "An Extremely Great Product", owner, 10L);
        productCurator.create(product);

        Pool p = new Pool(owner, product, new HashSet<Product>(), 1L, new Date(), new Date(), "contract",
            "account", "order");

        String subId1 = Util.generateDbUUID();
        p.setSourceSubscription(new SourceSubscription(subId1, "master"));
        poolCurator.create(p);

        Pool p2 = new Pool(owner, product, new HashSet<Product>(), 1L, new Date(), new Date(), "contract",
            "account", "order");
        String subId2 = Util.generateDbUUID();
        p2.setSourceSubscription(new SourceSubscription(subId2, "master"));
        poolCurator.create(p2);

        Pool p3 = new Pool(owner, product, new HashSet<Product>(), 1L, new Date(), new Date(), "contract",
            "account", "order");
        String subId3 = Util.generateDbUUID();
        p3.setSourceSubscription(new SourceSubscription(subId3, "master"));
        poolCurator.create(p3);

        List<Pool> pools = poolCurator.lookupBySubscriptionIds(Arrays.asList(subId1, subId2));
        assertEquals(2, pools.size());
        assertThat(pools, hasItems(p, p2));
        assertThat(pools, not(hasItem(p3)));
    }

    @Test
    public void buildInCriteriaTestBulk() {
        List<String> subIds = new ArrayList<String>();
        Product product = new Product("someProduct", "An Extremely Great Product", owner, 10L);
        productCurator.create(product);
        for (int i = 0; i < 30; i++) {
            subIds.add(createPoolForCriteriaTest(product));
        }

        List<Pool> pools = poolCurator.lookupBySubscriptionIds(subIds);
        assertEquals(30, pools.size());

        for (Pool pool : pools) {
            assertThat(subIds, hasItem(pool.getSubscriptionId()));
        }
    }

    private String createPoolForCriteriaTest(Product product) {
        String subId1 = Util.generateDbUUID();

        Pool p = new Pool(owner, product, new HashSet<Product>(), 1L, new Date(), new Date(), "contract",
            "account", "order");

        p.setSourceSubscription(new SourceSubscription(subId1, "master"));
        poolCurator.create(p);
        return subId1;
    }

    @Test
    public void buildInCriteriaTestBatch() {
        List<String> items = new ArrayList<String>();
        StringBuilder expected = new StringBuilder("taylor in (");

        for (int i = 0; i < AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE * 3; ++i) {
            items.add(String.valueOf(i));

            if (items.size() % AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE == 0) {
                expected.append(i).append(") or taylor in (");
            }
            else {
                expected.append(i).append(", ");
            }
        }
        expected.setLength(expected.length() - 15);

        Criterion crit = poolCurator.unboundedInCriterion("taylor", items);
        LogicalExpression le = (LogicalExpression) crit;
        assertEquals("or", le.getOp());
        assertEquals(expected.toString(), le.toString());
    }

    @Test
    public void buildInCriteriaTestSimple() {
        List<String> items = new ArrayList<String>();
        String expected = "swift in (";
        int i = 0;
        for (; i < AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE - 1; i++) {
            expected += i + ", ";
            items.add("" + i);
        }
        expected += i + ")";
        items.add("" + i);
        Criterion crit = poolCurator.unboundedInCriterion("swift", items);
        InExpression ie = (InExpression) crit;
        assertEquals(expected, ie.toString());
    }

    @Test
    public void testListBySourceEntitlement() {

        Pool sourcePool = TestUtil.createPool(owner, product);
        poolCurator.create(sourcePool);
        Entitlement e = new Entitlement(sourcePool, consumer, 1);
        entitlementCurator.create(e);

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setSourceEntitlement(e);
        Pool pool3 = TestUtil.createPool(owner, product);
        pool3.setSourceEntitlement(e);

        poolCurator.create(pool2);
        poolCurator.create(pool3);

        assertEquals(2, poolCurator.listBySourceEntitlement(e).size());

    }

    @Test
    public void testListBySourceEntitlements() {
        Pool sourcePool = TestUtil.createPool(owner, product);
        Pool sourcePool2 = TestUtil.createPool(owner, product);
        Pool sourcePool3 = TestUtil.createPool(owner, product);
        poolCurator.create(sourcePool);
        poolCurator.create(sourcePool2);
        poolCurator.create(sourcePool3);
        Entitlement e = new Entitlement(sourcePool, consumer, 1);
        Entitlement e2 = new Entitlement(sourcePool2, consumer, 1);
        Entitlement e3 = new Entitlement(sourcePool3, consumer, 1);
        entitlementCurator.create(e);
        entitlementCurator.create(e2);
        entitlementCurator.create(e3);

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setSourceEntitlement(e);
        Pool pool3 = TestUtil.createPool(owner, product);
        pool3.setSourceEntitlement(e);
        Pool pool4 = TestUtil.createPool(owner, product);
        pool4.setSourceEntitlement(e2);
        Pool pool5 = TestUtil.createPool(owner, product);
        pool5.setSourceEntitlement(e3);

        poolCurator.create(pool2);
        poolCurator.create(pool3);
        poolCurator.create(pool4);
        poolCurator.create(pool5);

        List<Pool> pools = poolCurator.listBySourceEntitlements(Arrays.asList(e, e2));
        assertEquals(3, pools.size());
    }

    @Test
    public void retrieveFreeEntitlementsOfPools() {
        Pool pool1 = TestUtil.createPool(owner, product);
        poolCurator.create(pool1);
        Entitlement ent11 = new Entitlement(pool1, consumer, 1);
        entitlementCurator.create(ent11);
        Entitlement ent12 = new Entitlement(pool1, consumer, 1);
        entitlementCurator.create(ent12);
        Entitlement ent13 = new Entitlement(pool1, consumer, 1);
        entitlementCurator.create(ent13);

        Pool pool2 = TestUtil.createPool(owner, product);
        poolCurator.create(pool2);
        Entitlement ent21 = new Entitlement(pool2, consumer, 1);
        entitlementCurator.create(ent21);

        Pool pool3 = TestUtil.createPool(owner, product);
        poolCurator.create(pool3);
        Entitlement ent31 = new Entitlement(pool3, consumer, 1);
        entitlementCurator.create(ent31);

        List<Entitlement> ents = poolCurator.retrieveFreeEntitlementsOfPools(
            Arrays.asList(pool1, pool2), true);
        assertEquals(4, ents.size());
        assertThat(ents, hasItems(ent11, ent12, ent13, ent21));
        assertThat(ents, not(hasItems(ent31)));
    }

    @Test
    public void testLoookupOverconsumedBySubscriptionId() {

        Pool pool = createPool(owner, product, 1L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        String subid = pool.getSubscriptionId();
        assertEquals(1, poolCurator.lookupBySubscriptionId(subid).size());

        Entitlement e = new Entitlement(pool, consumer, 1);
        entitlementCurator.create(e);

        Map<String, Entitlement> subMap = new HashMap<String, Entitlement>();
        subMap.put(subid, e);
        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionIds(subMap).size());

        e = new Entitlement(pool, consumer, 1);
        entitlementCurator.create(e);
        assertEquals(1, poolCurator.lookupOversubscribedBySubscriptionIds(subMap).size());
    }

    @Test
    public void testBatchLoookupOverconsumedBySubscriptionId() {
        Map<String, Entitlement> subIdMap = new HashMap<String, Entitlement>();
        List<Pool> expectedPools = new ArrayList<Pool>();
        for (Integer i = 0; i < 5; i++) {
            Pool pool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
                TestUtil.createDate(2055, 3, 2));
            poolCurator.create(pool);
            expectedPools.add(pool);
            String subid = pool.getSubscriptionId();

            Entitlement e = new Entitlement(pool, consumer, 2);
            entitlementCurator.create(e);
            subIdMap.put(subid, e);
        }

        Pool unconsumedPool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
            TestUtil.createDate(2055, 3, 2));
        poolCurator.create(unconsumedPool);

        Pool notOverConsumedPool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
            TestUtil.createDate(2055, 3, 2));
        poolCurator.create(notOverConsumedPool);
        entitlementCurator.create(new Entitlement(notOverConsumedPool, consumer, 1));

        Pool overConsumedPool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
            TestUtil.createDate(2055, 3, 2));
        poolCurator.create(overConsumedPool);
        entitlementCurator.create(new Entitlement(overConsumedPool, consumer, 2));

        List<Pool> gotPools = poolCurator.lookupOversubscribedBySubscriptionIds(subIdMap);
        assertEquals(5, gotPools.size());

        assertThat(expectedPools, hasItems(gotPools.toArray(new Pool[0])));
        assertThat(gotPools, not(hasItem(unconsumedPool)));
        assertThat(gotPools, not(hasItem(notOverConsumedPool)));
        assertThat(gotPools, not(hasItem(overConsumedPool)));
    }

    @Test
    public void testLoookupOverconsumedIgnoresOtherSourceEntitlementPools() {

        Pool pool = createPool(owner, product, 1L,
            TestUtil.createDate(2011, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        String subid = pool.getSubscriptionId();

        Entitlement sourceEnt = new Entitlement(pool, consumer, 1);
        entitlementCurator.create(sourceEnt);

        // Create derived pool referencing the entitlement just made:
        Pool derivedPool = new Pool(
            owner,
            product,
            new HashSet<Product>(),
            1L,
            TestUtil.createDate(2011, 3, 2),
            TestUtil.createDate(2055, 3, 2),
            "",
            "",
            ""
        );
        derivedPool.setSourceEntitlement(sourceEnt);
        derivedPool.setSourceSubscription(new SourceSubscription(subid, "derived"));
        poolCurator.create(derivedPool);

        Map<String, Entitlement> subMap = new HashMap<String, Entitlement>();
        subMap.put(subid, sourceEnt);
        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionIds(subMap).size());

        // Oversubscribe to the derived pool:
        Entitlement derivedEnt = new Entitlement(derivedPool, consumer,
            2);
        entitlementCurator.create(derivedEnt);

        // Passing the source entitlement should find the oversubscribed derived pool:
        assertEquals(1, poolCurator.lookupOversubscribedBySubscriptionIds(subMap).size());

        subMap.clear();
        subMap.put(subid, derivedEnt);
        // Passing the derived entitlement should not see any oversubscribed pool:
        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionIds(subMap).size());
    }

    @Test
    public void testLoookupOverconsumedBySubscriptionIdIgnoresUnlimited() {

        Pool pool = createPool(owner, product, -1L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        String subid = pool.getSubscriptionId();
        assertEquals(1, poolCurator.lookupBySubscriptionId(subid).size());


        Entitlement e = new Entitlement(pool, consumer, 1);
        entitlementCurator.create(e);

        Map<String, Entitlement> subMap = new HashMap<String, Entitlement>();
        subMap.put(subid, e);
        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionIds(subMap).size());

        e = new Entitlement(pool, consumer, 1);
        entitlementCurator.create(e);
        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionIds(subMap).size());
    }

    @Test
    public void testListByActiveOnIncludesSameStartDay() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setStartDate(activeOn);
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, (Collection<String>) null,
            activeOn, false).size());
    }

    @Test
    public void testListByActiveOnIncludesSameEndDay() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setEndDate(activeOn);
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, (Collection<String>) null,
            activeOn, false).size());
    }

    @Test
    public void testListByActiveOnInTheMiddle() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setStartDate(TestUtil.createDate(2011, 1, 2));
        pool.setEndDate(TestUtil.createDate(2011, 3, 2));
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, (Collection<String>) null,
            activeOn, false).size());
    }

    @Test
    public void testCorrectPagingWhenItemsAreFilteredByProductId() {
        for (int i = 0; i < 50; i++) {
            Pool pool = TestUtil.createPool(owner, product);
            pool.setStartDate(TestUtil.createDate(2011, 1, 2));
            pool.setEndDate(TestUtil.createDate(2011, 3, 2));
            poolCurator.create(pool);
        }

        for (int i = 0; i < 50; i++) {
            Product p = TestUtil.createProduct(owner);
            productCurator.create(p);

            Pool pool = TestUtil.createPool(owner, p);
            pool.setStartDate(TestUtil.createDate(2011, 1, 2));
            pool.setEndDate(TestUtil.createDate(2011, 3, 2));
            poolCurator.create(pool);
        }

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        Date activeOn = TestUtil.createDate(2011, 2, 2);
        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, product.getId(), null, activeOn, false, new PoolFilterBuilder(),
            req, false);
        assertEquals(Integer.valueOf(50), page.getMaxRecords());

        List<Pool> pools = page.getPageData();
        assertEquals(10, pools.size());

        // Make sure we have the real PageRequest, not the dummy one we send in
        // with the order and sortBy fields.
        assertEquals(req, page.getPageRequest());

        // Check that we've sorted ascending on the id
        for (int i = 0; i < pools.size(); i++) {
            if (i < pools.size() - 1) {
                assertTrue(pools.get(i).getId().compareTo(pools.get(i + 1).getId()) < 1);
            }
        }
    }

    @Test
    public void testCorrectPagingWhenResultsLessThanPageSize() {
        for (int i = 0; i < 5; i++) {
            Pool pool = TestUtil.createPool(owner, product);
            pool.setStartDate(TestUtil.createDate(2011, 1, 2));
            pool.setEndDate(TestUtil.createDate(2011, 3, 2));
            poolCurator.create(pool);
        }

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Date activeOn = TestUtil.createDate(2011, 2, 2);
        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, product.getId(), null, activeOn, false, new PoolFilterBuilder(),
            req, false);
        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(5, page.getPageData().size());
    }

    @Test
    public void testCorrectPagingWhenPageRequestOutOfBounds() {
        for (int i = 0; i < 5; i++) {
            Pool pool = TestUtil.createPool(owner, product);
            pool.setStartDate(TestUtil.createDate(2011, 1, 2));
            pool.setEndDate(TestUtil.createDate(2011, 3, 2));
            poolCurator.create(pool);
        }

        PageRequest req = new PageRequest();
        req.setPage(5);
        req.setPerPage(10);

        Date activeOn = TestUtil.createDate(2011, 2, 2);
        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, product.getId(), null, activeOn, false, new PoolFilterBuilder(),
            req, false);
        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(0, page.getPageData().size());
    }

    @Test
    public void testCorrectPagingWhenLastPage() {
        for (int i = 0; i < 5; i++) {
            Pool pool = TestUtil.createPool(owner, product);
            pool.setStartDate(TestUtil.createDate(2011, 1, 2));
            pool.setEndDate(TestUtil.createDate(2011, 3, 2));
            poolCurator.create(pool);
        }

        PageRequest req = new PageRequest();
        req.setPage(3);
        req.setPerPage(2);

        Date activeOn = TestUtil.createDate(2011, 2, 2);
        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, product.getId(), null, activeOn, false, new PoolFilterBuilder(),
            req, false);
        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(1, page.getPageData().size());
    }

    @Test
    public void testCorrectPagingWhenResultsEmpty() {
        for (int i = 0; i < 5; i++) {
            Product p = TestUtil.createProduct(owner);
            productCurator.create(p);

            Pool pool = TestUtil.createPool(owner, p);
            pool.setStartDate(TestUtil.createDate(2011, 1, 2));
            pool.setEndDate(TestUtil.createDate(2011, 3, 2));
            poolCurator.create(pool);
        }

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Date activeOn = TestUtil.createDate(2011, 2, 2);
        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner, product.getId(), null, activeOn, false, new PoolFilterBuilder(),
            req, false);
        assertEquals(Integer.valueOf(0), page.getMaxRecords());
        assertEquals(0, page.getPageData().size());
    }

    @Test
    public void testActivationKeyList() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setEndDate(activeOn);
        poolCurator.create(pool);
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(pool);

        assertEquals(0, poolCurator.getActivationKeysForPool(pool).size());

        ActivationKey ak = TestUtil.createActivationKey(owner, pools);
        activationKeyCurator.create(ak);

        // test the pool and its inverse
        assertEquals(1, ak.getPools().size());
        assertEquals(1, poolCurator.getActivationKeysForPool(pool).size());
    }

    @Test
    public void testExempt() {
        Product product1 = TestUtil.createProduct(owner);
        product1.addAttribute(new ProductAttribute("support_level", "premium"));
        product1.addAttribute(new ProductAttribute("support_level_exempt", "true"));
        productCurator.create(product1);
        Product product2 = TestUtil.createProduct(owner);
        product2.addAttribute(new ProductAttribute("support_level", "Premium"));
        productCurator.create(product2);
        Product product3 = TestUtil.createProduct(owner);
        product3.addAttribute(new ProductAttribute("support_level", "super"));
        productCurator.create(product3);
        Product product4 = TestUtil.createProduct(owner);
        product4.addAttribute(new ProductAttribute("support_level", "high"));
        product4.addAttribute(new ProductAttribute("support_level_exempt", "false"));
        productCurator.create(product4);
        Product product5 = TestUtil.createProduct(owner);
        product5.addAttribute(new ProductAttribute("support_level", "HIGH"));
        productCurator.create(product5);

        Pool pool1 = createPool(owner, product1, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool1);
        Pool pool2 = createPool(owner, product2, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool2);
        Pool pool3 = createPool(owner, product3, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool3);
        Pool pool4 = createPool(owner, product4, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool4);
        Pool pool5 = createPool(owner, product5, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool5);

        // list includes levels that are exempt false or not specified.
        // different casings will all appear on available list.
        Set<String> levels = poolCurator.retrieveServiceLevelsForOwner(owner, false);
        assertEquals(2, levels.size());
        // list includes on only those levels that have exempt attribute set.
        // The others that have that level but not the attribute do not appear on
        // the available level list but also do not appear on the exempt list. Pool
        // selection will use the exempt list to ensure that equivalent levels will
        // be treated as exempt.
        levels = poolCurator.retrieveServiceLevelsForOwner(owner, true);
        assertEquals(1, levels.size());
        assertEquals("premium", levels.toArray()[0]);
    }

    @Test
    public void testSupportCasing() {
        Product product1 = TestUtil.createProduct(owner);
        product1.addAttribute(new ProductAttribute("support_level", "premium"));
        productCurator.create(product1);
        Product product2 = TestUtil.createProduct(owner);
        product2.addAttribute(new ProductAttribute("support_level", "Premium"));
        productCurator.create(product2);
        Product product3 = TestUtil.createProduct(owner);
        product3.addAttribute(new ProductAttribute("support_level", "Premiums"));
        productCurator.create(product3);

        Pool pool1 = createPool(owner, product1, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool1);
        Pool pool2 = createPool(owner, product2, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool2);
        Pool pool3 = createPool(owner, product3, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool3);

        Set<String> levels = poolCurator.retrieveServiceLevelsForOwner(owner, false);
        assertEquals(2, levels.size());
    }

    @Test
    public void getSubPoolCountForStack() {
        String expectedStackId = "13245";
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("virt_limit", "3");
        product.setAttribute("stacking_id", expectedStackId);
        productCurator.create(product);

        // Create derived pool referencing the entitlement just made:
        Pool derivedPool = new Pool(
            owner, product,
            new HashSet<Product>(),
            1L,
            TestUtil.createDate(2011, 3, 2),
            TestUtil.createDate(2055, 3, 2),
            "",
            "",
            ""
        );
        derivedPool.setSourceStack(new SourceStack(consumer, expectedStackId));
        derivedPool.setAttribute("requires_host", consumer.getUuid());

        poolCurator.create(derivedPool);

        Pool pool = poolCurator.getSubPoolForStackIds(consumer, Arrays.asList(expectedStackId)).get(0);
        assertNotNull(pool);
    }

    @Test
    public void getSubPoolsForStackIds() {
        Set stackIds = new HashSet<String>();
        for (Integer i = 0; i < 5; i++) {
            String stackId = "12345" + i.toString();
            stackIds.add(stackId);
            Product product = TestUtil.createProduct(owner);
            product.setAttribute("virt_limit", "3");
            product.setAttribute("stacking_id", stackId);
            productCurator.create(product);

            // Create derived pool referencing the entitlement just made:
            Pool derivedPool = new Pool(owner, product, new HashSet<Product>(), 1L,
                TestUtil.createDate(2011, 3, 2), TestUtil.createDate(2055, 3, 2), "", "", "");
            derivedPool.setSourceStack(new SourceStack(consumer, stackId));
            derivedPool.setAttribute("requires_host", consumer.getUuid());

            poolCurator.create(derivedPool);
        }

        List<Pool> pools = poolCurator.getSubPoolForStackIds(consumer, stackIds);
        assertEquals(5, pools.size());
        for (Pool pool : pools) {
            assertTrue(pool.getSourceStackId().startsWith("12345"));
        }
    }

    @Test
    public void confirmBonusPoolDeleted() {
        Subscription sub = new Subscription(owner, product, new HashSet<Product>(), 16L,
            TestUtil.createDate(2006, 10, 21), TestUtil.createDate(2020, 1, 1), new Date());
        sub.setId(Util.generateDbUUID());

        Pool sourcePool = poolManager.createAndEnrichPools(sub).get(0);
        poolCurator.create(sourcePool);
        Entitlement e = new Entitlement(sourcePool, consumer, 1);
        entitlementCurator.create(e);

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setSourceEntitlement(e);
        pool2.setSourceSubscription(new SourceSubscription(
            sourcePool.getSubscriptionId(), "derived"));
        poolCurator.create(pool2);

        assertTrue(poolCurator.lookupBySubscriptionId(sub.getId()).size() == 2);
        poolManager.deletePool(sourcePool);

        // because we check for null now, we want to verify the
        // subpool gets deleted when the original pool is deleted.
        Pool gone = poolCurator.find(pool2.getId());
        assertEquals(gone, null);
    }

    @Test
    public void handleNull() {
        Pool noexist = new Pool(
            owner,
            product,
            new HashSet<Product>(),
            1L,
            TestUtil.createDate(2011, 3, 2),
            TestUtil.createDate(2055, 3, 2),
            "",
            "",
            ""
        );

        noexist.setId("betternotexist");

        poolCurator.delete(noexist);
    }

    @Test
    public void testGetPoolsBySubId() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        Pool pool = createPool(owner2, "id123");

        List<Pool> result = poolCurator.getPoolsBySubscriptionId(pool.getSubscriptionId());
        assertEquals(1, result.size());
        assertEquals(pool, result.get(0));
    }

    @Test
    public void testGetPoolsBySubIdNull() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        createPool(owner2, "id123");

        List<Pool> result = poolCurator.getPoolsBySubscriptionId(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetPoolsByFilter() {
        Owner owner1 = createOwner();
        ownerCurator.create(owner1);

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        Pool p1Attributes = TestUtil.createPool(owner1, product);
        Pool p1NoAttributes = TestUtil.createPool(owner1, product);

        Pool p2Attributes = TestUtil.createPool(owner2, product);
        Pool p2NoAttributes = TestUtil.createPool(owner2, product);
        Pool p2BadAttributes = TestUtil.createPool(owner2, product);

        p1Attributes.addAttribute(new PoolAttribute("x", "true"));
        p2Attributes.addAttribute(new PoolAttribute("x", "true"));
        p2BadAttributes.addAttribute(new PoolAttribute("x", "false"));

        poolCurator.create(p1Attributes);
        poolCurator.create(p1NoAttributes);
        poolCurator.create(p2Attributes);
        poolCurator.create(p2NoAttributes);
        poolCurator.create(p2BadAttributes);

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("x", "true");

        List<Pool> results = poolCurator.listByFilter(filters);

        assertThat(results, Matchers.hasItems(p1Attributes, p2Attributes));
    }

    private List<Owner> setupDBForProductIdTests() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        this.ownerCurator.create(owner1);
        this.ownerCurator.create(owner2);

        Pool p1 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p1", "p1"));
        p1.setDerivedProduct(this.generateProduct(owner1, "dp1", "dp1"));
        p1.setProvidedProducts(this.generateProductCollection(owner1, "pp-a-", 3));
        p1.setDerivedProvidedProducts(this.generateProductCollection(owner1, "dpp-a-", 3));

        Pool p2 = TestUtil.createPool(owner2, this.generateProduct(owner2, "p2", "p2"));
        p2.setDerivedProduct(this.generateProduct(owner2, "dp2", "dp2"));
        p2.setProvidedProducts(this.generateProductCollection(owner2, "pp-b-", 3));
        p2.setDerivedProvidedProducts(this.generateProductCollection(owner2, "dpp-b-", 3));

        this.poolCurator.create(p1);
        this.poolCurator.create(p2);

        return Arrays.asList(owner1, owner2);
    }

    @Test
    public void testGetAllKnownProductIds() {
        this.setupDBForProductIdTests();

        Set<String> expected = new HashSet<String>();
        expected.add("p1");
        expected.add("p2");
        expected.add("dp1");
        expected.add("dp2");
        expected.add("pp-a-0");
        expected.add("pp-a-1");
        expected.add("pp-a-2");
        expected.add("pp-b-0");
        expected.add("pp-b-1");
        expected.add("pp-b-2");
        expected.add("dpp-a-0");
        expected.add("dpp-a-1");
        expected.add("dpp-a-2");
        expected.add("dpp-b-0");
        expected.add("dpp-b-1");
        expected.add("dpp-b-2");

        Set<String> result = this.poolCurator.getAllKnownProductIds();

        assertEquals(expected, result);
    }

    @Test
    public void testGetAllKnownProductIdsForOwner() {
        List<Owner> owners = this.setupDBForProductIdTests();

        Set<String> expected = new HashSet<String>();
        expected.add("p1");
        expected.add("dp1");
        expected.add("pp-a-0");
        expected.add("pp-a-1");
        expected.add("pp-a-2");
        expected.add("dpp-a-0");
        expected.add("dpp-a-1");
        expected.add("dpp-a-2");

        assertEquals(expected, this.poolCurator.getAllKnownProductIdsForOwner(owners.get(0)));

        expected = new HashSet<String>();
        expected.add("p2");
        expected.add("dp2");
        expected.add("pp-b-0");
        expected.add("pp-b-1");
        expected.add("pp-b-2");
        expected.add("dpp-b-0");
        expected.add("dpp-b-1");
        expected.add("dpp-b-2");

        assertEquals(expected, this.poolCurator.getAllKnownProductIdsForOwner(owners.get(1)));
    }

    @Test
    public void testGetPoolBySubscriptionId() {
        Owner owner1 = this.createOwner();
        this.ownerCurator.create(owner1);

        Pool p1 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p1", "p1"));
        p1.setSourceSubscription(new SourceSubscription("subscriptionId-phil", "master"));

        Pool p2 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p2", "p2"));
        p2.setSourceSubscription(new SourceSubscription("subscriptionId-ned", "master"));

        this.poolCurator.create(p1);
        this.poolCurator.create(p2);

        Page<List<Pool>> result = this.poolCurator.listAvailableEntitlementPools(null, owner1,
            (Collection<String>) null, "subscriptionId-phil", new Date(), false, null, null, false);
        assertEquals("subscriptionId-phil", result.getPageData().get(0).getSubscriptionId());
    }

    private Product generateProduct(Owner owner, String id, String name) {
        Product product = TestUtil.createProduct(id, name, owner);
        this.productCurator.create(product);

        return product;
    }

    private Set<Product> generateProductCollection(Owner owner, String prefix, int count) {
        Set<Product> products = new HashSet<Product>();

        for (int i = 0; i < count; ++i) {
            products.add(this.generateProduct(owner, prefix + i, prefix + i));
        }

        return products;
    }

    private Pool createPool(Owner o, String subId) {
        Pool pool = TestUtil.createPool(o, product);
        pool.setSourceSubscription(new SourceSubscription(subId, "master"));
        return poolCurator.create(pool);
    }

    protected List<Pool> setupMasterPoolsTests() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        this.ownerCurator.create(owner1);
        this.ownerCurator.create(owner2);

        LinkedList<Pool> list = new LinkedList<Pool>();

        Product prod1 = TestUtil.createProduct(owner1);
        Product prod2 = TestUtil.createProduct(owner2);
        Product prod3 = TestUtil.createProduct(owner1);
        Product prod4 = TestUtil.createProduct(owner2);
        Product prod5 = TestUtil.createProduct(owner1);
        this.productCurator.create(prod1);
        this.productCurator.create(prod2);
        this.productCurator.create(prod3);
        this.productCurator.create(prod4);
        this.productCurator.create(prod5);

        Pool p1 = TestUtil.createPool(owner1, prod1);
        p1.setSourceSubscription(new SourceSubscription("sub1", "master"));
        list.add(p1);

        Pool p2 = TestUtil.createPool(owner2, prod2);
        p2.setSourceSubscription(new SourceSubscription("sub2", "master"));
        list.add(p2);

        Pool p3 = TestUtil.createPool(owner1, prod3);
        p3.setSourceSubscription(new SourceSubscription("sub1", "asd"));
        list.add(p3);

        Pool p4 = TestUtil.createPool(owner2, prod4);
        p4.setSourceSubscription(new SourceSubscription("sub2", "asd"));
        list.add(p4);

        Pool p5 = TestUtil.createPool(owner1, prod5);
        p5.setSourceSubscription(new SourceSubscription("sub3", "asd"));
        list.add(p5);

        this.poolCurator.create(p1);
        this.poolCurator.create(p2);
        this.poolCurator.create(p3);
        this.poolCurator.create(p4);
        this.poolCurator.create(p5);

        return list;
    }

    @Test
    public void testGetMasterPoolBySubscriptionId() {
        List<Pool> pools = this.setupMasterPoolsTests();

        Pool actual = this.poolCurator.getMasterPoolBySubscriptionId("sub1");
        assertEquals(pools.get(0), actual);

        actual = this.poolCurator.getMasterPoolBySubscriptionId("sub2");
        assertEquals(pools.get(1), actual);

        actual = this.poolCurator.getMasterPoolBySubscriptionId("sub3");
        assertEquals(null, actual);
    }

    @Test
    public void testListMasterPools() {
        List<Pool> pools = this.setupMasterPoolsTests();
        List<Pool> expected = new LinkedList<Pool>();

        expected.add(pools.get(0));
        expected.add(pools.get(1));

        List<Pool> actual = this.poolCurator.listMasterPools();
        assertEquals(expected, actual);
    }

    @Test
    public void testHasAvailablePools()
        throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        assertTrue(poolCurator.hasActiveEntitlementPools(owner, activeDate));
    }

    @Test
    public void testHasAvailablePoolsNoPools()
        throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);
        assertFalse(poolCurator.hasActiveEntitlementPools(owner, activeDate));
    }

    @Test
    public void testHasAvailablePoolsNotCurrent()
        throws Exception {
        Date activeDate = TestUtil.createDate(2000, 3, 2);
        Date startDate = TestUtil.createDate(2001, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            startDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        assertFalse(poolCurator.hasActiveEntitlementPools(owner, activeDate));
    }

    @Test
    public void testLookupDevPoolForConsumer() throws Exception {
        // Make sure that multiple pools exist.
        createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        Pool pool = createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        pool.setAttribute("requires_consumer", consumer.getUuid());
        pool.setAttribute("another_attr", "20");
        pool.setAttribute("dev_pool", "true");
        poolCurator.create(pool);

        Pool found = poolCurator.findDevPool(consumer);
        assertNotNull(found);
        assertEquals(pool.getId(), found.getId());
    }

    @Test
    public void testDevPoolForConsumerNotFoundReturnsNullWhenNoMatchOnConsumer() throws Exception {
        // Make sure that multiple pools exist.
        createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        Pool pool = createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        pool.setAttribute("requires_consumer", "does-not-exist");
        pool.setAttribute("dev_pool", "true");
        poolCurator.create(pool);

        Pool found = poolCurator.findDevPool(consumer);
        assertNull(found);
    }
}
