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
package org.candlepin.model;

import static org.candlepin.model.SourceSubscription.DERIVED_POOL_SUB_KEY;
import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Test suite for the PoolCurator object
 */
// Allow for a non-static MethodSource
@TestInstance(Lifecycle.PER_CLASS)
public class PoolCuratorTest extends DatabaseTestFixture {

    private PoolManager poolManager;
    private PoolService poolService;
    private UeberCertificateGenerator ueberCertGenerator;

    private Owner owner;
    private Product product;
    private Product providedProduct;
    private Product derivedProduct;
    private Product derivedProvidedProduct;
    private Pool pool;
    private Consumer consumer;
    private ConsumerType systemConsumerType;

    @BeforeEach
    public void setUp() {
        poolManager = injector.getInstance(PoolManager.class);
        poolService = injector.getInstance(PoolService.class);
        ueberCertGenerator = injector.getInstance(UeberCertificateGenerator.class);

        owner = createOwner();
        ownerCurator.create(owner);

        ConsumerType systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        this.systemConsumerType = this.consumerTypeCurator.create(systemType);

        product = TestUtil.createProduct();
        providedProduct = TestUtil.createProduct();
        derivedProduct = TestUtil.createProduct();
        derivedProvidedProduct = TestUtil.createProduct();

        this.product.addProvidedProduct(this.providedProduct);
        this.product.setDerivedProduct(this.derivedProduct);
        this.derivedProduct.addProvidedProduct(this.derivedProvidedProduct);

        derivedProvidedProduct = this.createProduct(derivedProvidedProduct);
        derivedProduct = this.createProduct(derivedProduct);
        providedProduct = this.createProduct(providedProduct);
        product = this.createProduct(product);

        pool = this.poolCurator.create(new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(16L)
            .setStartDate(TestUtil.createDate(2015, 10, 21))
            .setEndDate(TestUtil.createDate(2025, 1, 1))
            .setContractNumber("1")
            .setAccountNumber("2")
            .setOrderNumber("3"));

        consumer = this.createMockConsumer(owner, false);
        consumer.setFact("cpu_cores", "4");
        consumer = consumerCurator.merge(consumer);
    }

    protected Consumer createMockConsumer(Owner owner, boolean manifestType) {
        ConsumerType ctype = this.createConsumerType(manifestType);
        Consumer consumer = new Consumer()
            .setName("TestConsumer" + TestUtil.randomInt())
            .setUsername("User")
            .setOwner(owner)
            .setType(ctype);
        consumer = this.consumerCurator.create(consumer);

        return consumer;
    }

    @Test
    public void testPoolNotYetActive() {
        Pool pool = createPool(owner, product, 100L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            consumer, consumer.getOwnerId(), null, TestUtil.createDate(2450, 3, 2));

        assertEquals(0, results.size());
    }

    @Test
    public void testPoolExpired() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();

        Consumer consumer = this.createMockConsumer(owner, false);
        consumer.setFact("cpu_cores", "4");
        consumer = consumerCurator.merge(consumer);

        Pool pool = createPool(owner, product, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            consumer, consumer.getOwnerId(), null, TestUtil.createDate(2005, 3, 3));
        assertEquals(0, results.size());

        // If we specify no date filtering, the expired pool should be returned:
        results = poolCurator.listAvailableEntitlementPools(
            consumer, consumer.getOwnerId(), null, null);

        assertEquals(1, results.size());
    }

    @Test
    public void testAvailablePoolsDoesNotIncludeUeberPool() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();

        Consumer consumer = this.createMockConsumer(owner, false);
        consumer.setFact("cpu_cores", "4");
        consumer = consumerCurator.merge(consumer);

        Pool pool = createPool(owner, product, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        ueberCertGenerator.generate(owner.getKey(), new NoAuthPrincipal());

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            consumer, consumer.getOwnerId(), null, null);

        assertEquals(1, results.size());
    }

    @Test
    public void availablePoolsCanBeFilteredByProductPoolAttribute() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute(Product.Attributes.CORES, "8");
        product2 = this.createProduct(product2);

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
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters,
            req, false, false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void availablePoolsCanBeFilteredByPoolAttribute() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        pool2.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("virt_only", "true");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters,
            req, false, false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void availablePoolsCanBeFilteredByPoolId() {
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
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters, req, false,
            false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());

        filters = new PoolFilterBuilder();
        filters.addIdFilter(pool1.getId());
        filters.addIdFilter(pool2.getId());

        page = poolCurator.listAvailableEntitlementPools(
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters, req, false,
            false, false, null);
        results = page.getPageData();
        assertEquals(2, results.size());
    }

    @Test
    public void availablePoolsCanNotBeFilteredByOverriddenAttribute() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Product product = TestUtil.createProduct();
        product.setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        createProduct(product);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));

        // This product value should be overridden by the pool attr. Note that this product is used
        // by both pools, so its attributes will be reflected in both. Also note that only pool2 is
        // overriding the value.
        pool2.setAttribute(Product.Attributes.VIRT_ONLY, "false");
        poolCurator.create(pool2);

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("virt_only", "true");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters, null, false,
            false, false, null);
        List<Pool> results = page.getPageData();

        assertEquals(1, results.size());
        assertEquals(pool1, results.get(0));
    }

    @Test
    public void availablePoolsCanBeFilteredByBothPoolAndProductPoolAttribute() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute(Product.Attributes.CORES, "4");
        product2 = this.createProduct(product2);

        Pool pool2 = createPool(owner, product2, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        pool2.setAttribute(Product.Attributes.VIRT_ONLY, "true");
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
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters, req, false,
            false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void availablePoolsCanFilterByEmptyValueAttribute() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute("empty-attr", "");
        product2 = this.createProduct(product2);

        Pool pool2 = createPool(owner, product2, 100L, activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter("empty-attr", "");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters, req, false,
            false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool2.getId(), results.get(0).getId());
    }

    @Test
    public void attributeFilterValuesAreNotCaseSensitive() {
        Product product1 = TestUtil.createProduct();
        product1.setAttribute("A", "foo");
        product1.setAttribute("B", "bar");
        product1 = this.createProduct(product1);

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
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters, req, false,
            false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool, results.get(0));
    }

    @Test
    public void testFetchEntitledConsumerUuids() {
        Consumer c1 = createMockConsumer(owner, false);
        Entitlement e1 = new Entitlement(pool, c1, owner, 1);
        String id1 = Util.generateDbUUID();
        e1.setId(id1);
        entitlementCurator.create(e1);

        Consumer c2 = createMockConsumer(owner, false);
        Entitlement e2 = new Entitlement(pool, c2, owner, 1);
        String id2 = Util.generateDbUUID();
        e2.setId(id2);
        entitlementCurator.create(e2);

        Set<String> actual = new HashSet<>(poolCurator.listEntitledConsumerUuids(pool.getId()));
        Set<String> expected = new HashSet<>();
        expected.add(c1.getUuid());
        expected.add(c2.getUuid());
        assertEquals(expected, actual);
    }

    @Test
    public void requiresHostIsCaseInsensitive() {
        Product product1 = TestUtil.createProduct();

        String gid = "AAABBB";

        Consumer hostConsumer = createConsumer(owner);
        Consumer guestConsumer = createConsumer(owner);
        guestConsumer.setFact("virt.is_guest", "true");
        guestConsumer.setFact("virt.uuid", gid);
        hostConsumer.addGuestId(new GuestId(gid, guestConsumer));

        guestConsumer = consumerCurator.merge(guestConsumer);
        hostConsumer = consumerCurator.merge(hostConsumer);

        product1 = this.createProduct(product1);

        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool = createPool(owner, product1, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        pool.setAttribute(Pool.Attributes.REQUIRES_HOST, hostConsumer.getUuid().toUpperCase());
        poolCurator.create(pool);

        // This pool should not be found!
        Pool pool2 = createPool(owner, product1, 50L, activeDate, TestUtil.createDate(2005, 3, 2));
        pool2.setAttribute(Pool.Attributes.REQUIRES_HOST, "poolForSomeOtherHost");
        poolCurator.create(pool2);

        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        Page<List<Pool>> page = poolCurator.listAvailableEntitlementPools(
            guestConsumer, owner.getId(), (Collection<String>) null, null, activeDate, null, req, false,
            false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(1, results.size());
        assertEquals(pool, results.get(0));
    }

    /**
     * When filtering pools by product/pool attributes, filters specified with the same attribute name
     * are ORed, and different attributes are ANDed.
     *
     * For example applying the following filters:
     *
     * A1:foo, A1:bar, A2:biz
     *
     * will result in matches on the values of: (A1 == foo OR A1 == bar) AND A2 == biz
     *
     * Another important note is that product attributes are ORed with Pool attributes for each
     * attribute specified.
     */
    @Test
    public void testAttributeFilterLogic() {
        Product product1 = TestUtil.createProduct();
        product1.setAttribute("A", "foo");
        product1.setAttribute("B", "bar");
        product1 = this.createProduct(product1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute("A", "foo");
        product2.setAttribute("B", "zoo");
        product2 = this.createProduct(product2);

        Product product3 = TestUtil.createProduct();
        product3.setAttribute("A", "biz");
        product3.setAttribute("B", "zoo");
        product3 = this.createProduct(product3);

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
            null, owner.getId(), (Collection<String>) null, null, activeDate, filters, req, false,
            false, false, null);
        List<Pool> results = page.getPageData();
        assertEquals(2, results.size());

        Pool[] expected = new Pool[] { pool2, pool3 };
        assertTrue(results.containsAll(Arrays.asList(expected)));
    }

    @Test
    public void testProductName() {
        Product p = this.createProduct("someProduct", "An Extremely Great Product");

        Pool pool = createPool(owner, p, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, p.getId());
        Pool onlyPool = results.get(0);

        assertEquals("An Extremely Great Product", onlyPool.getProductName());
    }

    @Test
    public void testProductNameViaFind() {
        Product p = this.createProduct("another", "A Great Operating System");

        Pool pool = createPool(owner, p, 25L,
            TestUtil.createDate(1999, 1, 10), TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);
        pool = poolCurator.get(pool.getId());

        assertEquals("A Great Operating System", pool.getProductName());
    }

    @Test
    public void testProductNameViaFindAll() {
        Product p = this.createProduct("another", "A Great Operating System");

        Pool pool = createPool(owner, p, 25L, TestUtil.createDate(1999, 1, 10),
            TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);

        boolean found = false;
        for (Pool test : poolCurator.listAll()) {
            if ("A Great Operating System".equals(test.getProductName())) {
                found = true;
                break;
            }
        }

        assertTrue(found);
    }

    @Test
    public void testFuzzyProductMatchingWithoutSubscription() {
        Product product = this.createProduct();
        Product parentProduct = TestUtil.createProduct("productId", "testProductName");
        parentProduct.setProvidedProducts(Arrays.asList(product));
        Product parent = this.createProduct(parentProduct);

        Pool p = TestUtil.createPool(owner, parent, 5);
        poolCurator.create(p);

        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, product.getId());
        assertEquals(1, results.size());
    }

    @Test
    public void testPoolProducts() {
        Product another = this.createProduct();
        Pool pool = TestUtil.createPool(owner, product, 5);
        poolCurator.create(pool);
        pool = poolCurator.get(pool.getId());
        assertNotNull(pool.getProduct());
        assertTrue(pool.getProduct().getProvidedProducts().size() > 0);
    }

    // Note: This simply tests that the multiplier is read and used in pool creation.
    // All of the null/negative multiplier test cases are in ProductTest
    @Test
    public void testMultiplierCreation() {
        Product product = new Product("someProduct", "An Extremely Great Product", 10L);
        product = this.createProduct(product);

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(16L);
        sub.setStartDate(TestUtil.createDate(2006, 10, 21));
        sub.setEndDate(TestUtil.createDate(2020, 1, 1));
        sub.setModified(new Date());

        Pool newPool = poolManager.createAndEnrichPools(sub).get(0);
        List<Pool> pools = poolCurator.getBySubscriptionId(owner, sub.getId());

        assertEquals(160L, pools.get(0).getQuantity().longValue());
        assertEquals(newPool.getQuantity(), pools.get(0).getQuantity());
    }

    @Test
    public void testgetBySubscriptionIds() {
        Product product = new Product("someProduct", "An Extremely Great Product", 10L);
        product = this.createProduct(product);

        Pool pool1 = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(new Date())
            .setEndDate(new Date())
            .setContractNumber("contract")
            .setAccountNumber("account")
            .setOrderNumber("order");

        String subId1 = Util.generateDbUUID();
        pool1.setSourceSubscription(new SourceSubscription(subId1, "master"));
        poolCurator.create(pool1);

        Pool pool2 = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(new Date())
            .setEndDate(new Date())
            .setContractNumber("contract")
            .setAccountNumber("account")
            .setOrderNumber("order");

        String subId2 = Util.generateDbUUID();
        pool2.setSourceSubscription(new SourceSubscription(subId2, "master"));
        poolCurator.create(pool2);

        Pool pool3 = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(new Date())
            .setEndDate(new Date())
            .setContractNumber("contract")
            .setAccountNumber("account")
            .setOrderNumber("order");

        String subId3 = Util.generateDbUUID();
        pool3.setSourceSubscription(new SourceSubscription(subId3, "master"));
        poolCurator.create(pool3);

        List<Pool> pools = poolCurator.getBySubscriptionIds(owner, Arrays.asList(subId1, subId2));
        assertEquals(2, pools.size());
        assertThat(pools, hasItems(pool1, pool2));
        assertThat(pools, not(hasItem(pool3)));
    }

    @Test
    public void buildInCriteriaTestBulk() {
        List<String> subIds = new ArrayList<>();
        Product product = new Product("someProduct", "An Extremely Great Product", 10L);
        product = this.createProduct(product);

        for (int i = 0; i < 30; i++) {
            subIds.add(createPoolForCriteriaTest(product));
        }

        List<Pool> pools = poolCurator.getBySubscriptionIds(owner, subIds);
        assertEquals(30, pools.size());

        for (Pool pool : pools) {
            assertThat(subIds, hasItem(pool.getSubscriptionId()));
        }
    }

    private String createPoolForCriteriaTest(Product product) {
        String subId1 = Util.generateDbUUID();

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(new Date())
            .setEndDate(new Date())
            .setContractNumber("contract")
            .setAccountNumber("account")
            .setOrderNumber("order");

        pool.setSourceSubscription(new SourceSubscription(subId1, "master"));
        poolCurator.create(pool);

        return subId1;
    }

    @Test
    public void testListBySourceEntitlement() {
        Pool sourcePool = TestUtil.createPool(owner, product);
        poolCurator.create(sourcePool);
        Entitlement e = new Entitlement(sourcePool, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setSourceEntitlement(e);
        Pool pool3 = TestUtil.createPool(owner, product);
        pool3.setSourceEntitlement(e);

        poolCurator.create(pool2);
        poolCurator.create(pool3);

        assertEquals(2, poolCurator.listBySourceEntitlement(e).list().size());
    }

    @Test
    public void testListBySourceEntitlements() {
        Pool sourcePool = TestUtil.createPool(owner, product);
        Pool sourcePool2 = TestUtil.createPool(owner, product);
        Pool sourcePool3 = TestUtil.createPool(owner, product);
        poolCurator.create(sourcePool);
        poolCurator.create(sourcePool2);
        poolCurator.create(sourcePool3);
        Entitlement e = new Entitlement(sourcePool, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        Entitlement e2 = new Entitlement(sourcePool2, consumer, owner, 1);
        e2.setId(Util.generateDbUUID());
        Entitlement e3 = new Entitlement(sourcePool3, consumer, owner, 1);
        e3.setId(Util.generateDbUUID());
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

        Set<Pool> pools = poolCurator.listBySourceEntitlements(Arrays.asList(e, e2));
        assertEquals(3, pools.size());
    }

    @Test
    public void retrieveFreeEntitlementsOfPools() {
        Pool pool1 = TestUtil.createPool(owner, product);
        poolCurator.create(pool1);
        Entitlement ent11 = new Entitlement(pool1, consumer, owner, 1);
        ent11.setId(Util.generateDbUUID());
        entitlementCurator.create(ent11);
        Entitlement ent12 = new Entitlement(pool1, consumer, owner, 1);
        ent12.setId(Util.generateDbUUID());
        entitlementCurator.create(ent12);
        Entitlement ent13 = new Entitlement(pool1, consumer, owner, 1);
        ent13.setId(Util.generateDbUUID());
        entitlementCurator.create(ent13);

        Pool pool2 = TestUtil.createPool(owner, product);
        poolCurator.create(pool2);
        Entitlement ent21 = new Entitlement(pool2, consumer, owner, 1);
        ent21.setId(Util.generateDbUUID());
        entitlementCurator.create(ent21);

        Pool pool3 = TestUtil.createPool(owner, product);
        poolCurator.create(pool3);
        Entitlement ent31 = new Entitlement(pool3, consumer, owner, 1);
        ent31.setId(Util.generateDbUUID());
        entitlementCurator.create(ent31);

        List<Entitlement> ents = poolCurator.retrieveOrderedEntitlementsOf(
            Arrays.asList(pool1, pool2));
        assertEquals(4, ents.size());
        assertThat(ents, hasItems(ent11, ent12, ent13, ent21));
        assertThat(ents, not(hasItems(ent31)));
    }

    @Test
    public void testLookupOverconsumedBySubscriptionId() {

        Pool pool = createPool(owner, product, 1L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        String subid = pool.getSubscriptionId();
        assertEquals(1, poolCurator.getBySubscriptionId(owner, subid).size());

        Entitlement e = new Entitlement(pool, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);
        pool.setConsumed(pool.getConsumed() + 1);
        poolCurator.merge(pool);

        Map<String, Entitlement> subMap = new HashMap<>();
        subMap.put(subid, e);
        assertEquals(0, poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subMap).size());

        e = new Entitlement(pool, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);
        pool.setConsumed(pool.getConsumed() + 1);
        poolCurator.merge(pool);
        assertEquals(1, poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subMap).size());
    }

    @Test
    public void testBatchLookupOverconsumedBySubscriptionId() {
        Map<String, Entitlement> subIdMap = new HashMap<>();
        List<Pool> expectedPools = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Pool pool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
                TestUtil.createDate(2055, 3, 2));
            poolCurator.create(pool);
            expectedPools.add(pool);
            String subid = pool.getSubscriptionId();

            Entitlement e = new Entitlement(pool, consumer, owner, 2);
            e.setId(Util.generateDbUUID());
            entitlementCurator.create(e);
            pool.setConsumed(pool.getConsumed() + 2);
            poolCurator.merge(pool);
            subIdMap.put(subid, e);
        }

        Pool unconsumedPool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
            TestUtil.createDate(2055, 3, 2));
        poolCurator.create(unconsumedPool);

        Pool notOverConsumedPool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
            TestUtil.createDate(2055, 3, 2));
        poolCurator.create(notOverConsumedPool);
        Entitlement ent = new Entitlement(notOverConsumedPool, consumer, owner, 1);
        ent.setId(Util.generateDbUUID());
        entitlementCurator.create(ent);
        notOverConsumedPool.setConsumed(notOverConsumedPool.getConsumed() + 1);
        poolCurator.merge(notOverConsumedPool);

        Pool overConsumedPool = createPool(owner, product, 1L, TestUtil.createDate(2050, 3, 2),
            TestUtil.createDate(2055, 3, 2));
        poolCurator.create(overConsumedPool);
        Entitlement ent12 = new Entitlement(overConsumedPool, consumer, owner, 2);
        ent12.setId(Util.generateDbUUID());
        entitlementCurator.create(ent12);
        overConsumedPool.setConsumed(overConsumedPool.getConsumed() + 2);
        poolCurator.merge(overConsumedPool);

        List<Pool> gotPools = poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subIdMap);
        assertEquals(5, gotPools.size());

        assertThat(expectedPools, hasItems(gotPools.toArray(new Pool[0])));
        assertThat(gotPools, not(hasItem(unconsumedPool)));
        assertThat(gotPools, not(hasItem(notOverConsumedPool)));
        assertThat(gotPools, not(hasItem(overConsumedPool)));
    }

    @Test
    public void testLookupOverconsumedIgnoresOtherSourceEntitlementPools() {

        Pool pool = createPool(owner, product, 1L,
            TestUtil.createDate(2011, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        String subid = pool.getSubscriptionId();

        Entitlement sourceEnt = new Entitlement(pool, consumer, owner, 1);
        sourceEnt.setId(Util.generateDbUUID());
        entitlementCurator.create(sourceEnt);
        pool.setConsumed(pool.getConsumed() + 1);
        poolCurator.merge(pool);

        // Create derived pool referencing the entitlement just made:
        Pool derivedPool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDate(2011, 3, 2))
            .setEndDate(TestUtil.createDate(2055, 3, 2))
            .setSourceEntitlement(sourceEnt)
            .setSourceSubscription(new SourceSubscription(subid, DERIVED_POOL_SUB_KEY));

        poolCurator.create(derivedPool);

        Map<String, Entitlement> subMap = new HashMap<>();
        subMap.put(subid, sourceEnt);
        assertEquals(0, poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subMap).size());

        // Oversubscribe to the derived pool:
        Entitlement derivedEnt = new Entitlement(derivedPool, consumer, owner, 3);
        derivedEnt.setId(Util.generateDbUUID());
        entitlementCurator.create(derivedEnt);
        derivedPool.setConsumed(derivedPool.getConsumed() + 3);
        poolCurator.merge(derivedPool);

        // Passing the source entitlement should find the oversubscribed derived pool:
        assertEquals(1, poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subMap).size());

        subMap.clear();
        subMap.put(subid, derivedEnt);
        // Passing the derived entitlement should not see any oversubscribed pool:
        assertEquals(0, poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subMap).size());
    }

    @Test
    public void testLookupOverconsumedBySubscriptionIdIgnoresUnlimited() {
        Pool pool = createPool(owner, product, -1L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        String subid = pool.getSubscriptionId();
        assertEquals(1, poolCurator.getBySubscriptionId(owner, subid).size());

        Entitlement e = new Entitlement(pool, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);

        Map<String, Entitlement> subMap = new HashMap<>();
        subMap.put(subid, e);
        assertEquals(0, poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subMap).size());

        e = new Entitlement(pool, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);
        assertEquals(0, poolCurator.getOversubscribedBySubscriptionIds(owner.getId(), subMap).size());
    }

    @Test
    public void testListByActiveOnIncludesSameStartDay() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setStartDate(activeOn);
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, (Collection<String>) null,
            activeOn).size());
    }

    @Test
    public void testListByActiveOnIncludesSameEndDay() {
        Date startDate = TestUtil.createDate(2010, 1, 2);
        Date endDate = TestUtil.createDate(2010, 1, 3);

        Pool pool = TestUtil.createPool(owner, product)
            .setStartDate(startDate)
            .setEndDate(endDate);

        pool = poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, (Collection<String>) null,
            endDate).size());
    }

    @Test
    public void testListByActiveOnInTheMiddle() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product)
            .setStartDate(TestUtil.createDate(2011, 1, 2))
            .setEndDate(TestUtil.createDate(2011, 3, 2));

        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, (Collection<String>) null,
            activeOn).size());
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
            Product p = this.createProduct();

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
            null, owner, product.getId(), null, activeOn, new PoolFilterBuilder(),
            req, false, false, false, null);
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
            null, owner, product.getId(), null, activeOn, new PoolFilterBuilder(),
            req, false, false, false, null);
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
            null, owner, product.getId(), null, activeOn, new PoolFilterBuilder(),
            req, false, false, false, null);
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
            null, owner, product.getId(), null, activeOn, new PoolFilterBuilder(),
            req, false, false, false, null);

        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(1, page.getPageData().size());
    }

    @Test
    public void testCorrectPagingWhenResultsEmpty() {
        for (int i = 0; i < 5; i++) {
            Product p = this.createProduct();

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
            null, owner, product.getId(), null, activeOn, new PoolFilterBuilder(),
            req, false, false, false, null);
        assertEquals(Integer.valueOf(0), page.getMaxRecords());
        assertEquals(0, page.getPageData().size());
    }

    @Test
    public void testActivationKeyList() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setEndDate(activeOn);
        poolCurator.create(pool);
        List<Pool> pools = new ArrayList<>();
        pools.add(pool);

        assertEquals(0, poolCurator.getActivationKeysForPool(pool).size());

        ActivationKey ak = TestUtil.createActivationKey(owner, pools);
        activationKeyCurator.create(ak);

        // test the pool and its inverse
        assertEquals(1, ak.getPools().size());
        assertEquals(1, poolCurator.getActivationKeysForPool(pool).size());
    }

    @Test
    public void testListServiceLevelForOwnersWithExpiredPool() {
        Product product1 = TestUtil.createProduct();
        product1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "expired");
        product1 = this.createProduct(product1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute(Product.Attributes.SUPPORT_LEVEL, "fresh");
        product2 = this.createProduct(product2);

        int year = Calendar.getInstance().get(Calendar.YEAR);
        Date pastDate = TestUtil.createDate(year - 2, 3, 2);
        Date expiredDate = TestUtil.createDate(year - 1, 3, 2);
        Date futureDate = TestUtil.createDate(year + 10, 3, 2);

        Pool pool1 = createPool(owner, product1, 100L, pastDate, expiredDate);
        poolCurator.create(pool1);
        Pool pool2 = createPool(owner, product2, 100L, pastDate, futureDate);
        poolCurator.create(pool2);

        Set<String> levels = poolCurator.retrieveServiceLevelsForOwner(owner, false);
        // list should only include levels from pools that are not expired
        assertEquals(1, levels.size());
    }

    @Test
    public void testListServiceLevelForOwnersWithExpiredPoolAndEndDateOverride() {
        Product product1 = TestUtil.createProduct();
        product1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "expired");
        product1 = this.createProduct(product1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute(Product.Attributes.SUPPORT_LEVEL, "fresh");
        product2 = this.createProduct(product2);

        int year = Calendar.getInstance().get(Calendar.YEAR);
        Date pastDate = TestUtil.createDate(year - 2, 3, 2);
        Date expiredDate = TestUtil.createDate(year - 1, 3, 2);
        Date futureDate = TestUtil.createDate(year + 10, 3, 2);

        Pool pool1 = createPool(owner, product1, 100L, pastDate, expiredDate);
        poolCurator.create(pool1);
        Pool pool2 = createPool(owner, product2, 100L, pastDate, futureDate);
        poolCurator.create(pool2);

        Entitlement e = new Entitlement(pool1, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        e.setEndDateOverride(futureDate);
        entitlementCurator.create(e);

        Set<String> levels = poolCurator.retrieveServiceLevelsForOwner(owner, false);
        // list should only include levels from pools that are not expired
        // (except when the pool has entitlement with valid endDateOverride)
        assertEquals(2, levels.size());
    }

    @Test
    public void testExempt() {
        Product product1 = TestUtil.createProduct();
        product1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "premium");
        product1.setAttribute(Product.Attributes.SUPPORT_LEVEL_EXEMPT, "true");
        product1 = this.createProduct(product1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        product2 = this.createProduct(product2);

        Product product3 = TestUtil.createProduct();
        product3.setAttribute(Product.Attributes.SUPPORT_LEVEL, "super");
        product3 = this.createProduct(product3);

        Product product4 = TestUtil.createProduct();
        product4.setAttribute(Product.Attributes.SUPPORT_LEVEL, "high");
        product4.setAttribute(Product.Attributes.SUPPORT_LEVEL_EXEMPT, "false");
        product4 = this.createProduct(product4);

        Product product5 = TestUtil.createProduct();
        product5.setAttribute(Product.Attributes.SUPPORT_LEVEL, "HIGH");
        product5 = this.createProduct(product5);

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
        Product product1 = TestUtil.createProduct();
        product1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "premium");
        product1 = this.createProduct(product1);

        Product product2 = TestUtil.createProduct();
        product2.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        product2 = this.createProduct(product2);

        Product product3 = TestUtil.createProduct();
        product3.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premiums");
        product3 = this.createProduct(product3);

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

    private Pool setupHostLimitedVirtPoolStack(Owner owner, Consumer consumer, String stackId) {
        Product product = TestUtil.createProduct()
            .setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited")
            .setAttribute(Product.Attributes.STACKING_ID, stackId);

        product = this.createProduct(product);

        // Create derived pool referencing the entitlement just made:
        Pool derivedPool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-2, 0, 0))
            .setEndDate(TestUtil.createDateOffset(2, 0, 0))
            .setSourceStack(new SourceStack(consumer, stackId))
            .setAttribute(Pool.Attributes.REQUIRES_HOST, consumer.getUuid());

        return this.poolCurator.create(derivedPool);
    }

    @Test
    public void testGetSubPoolsForStackIdsSingleConsumerSingleStack() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);

        Collection<Pool> pools = this.poolCurator.getSubPoolsForStackIds(consumer1, Arrays.asList(stackId1));

        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertThat(pools, hasItems(dpool1));
    }

    @Test
    public void testGetSubPoolsForStackIdsSingleConsumerMultiStack() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);

        Collection<Pool> pools = this.poolCurator
            .getSubPoolsForStackIds(consumer1, Arrays.asList(stackId1, stackId2, stackId4));

        assertNotNull(pools);
        assertEquals(2, pools.size());
        assertThat(pools, hasItems(dpool1, dpool2));
    }

    @Test
    public void testGetSubPoolsForStackIdsMultiConsumerSingleStack() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer3 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer4 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);
        Pool dpool7 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId1);
        Pool dpool8 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId2);
        Pool dpool9 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId3);

        Collection<Pool> pools = this.poolCurator
            .getSubPoolsForStackIds(Arrays.asList(consumer1, consumer2, consumer4), Arrays.asList(stackId1));

        assertNotNull(pools);
        assertEquals(2, pools.size());
        assertThat(pools, hasItems(dpool1, dpool4));
    }

    @Test
    public void testGetSubPoolsForStackIdsNullConsumer() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);

        Collection<Pool> pools = this.poolCurator
            .getSubPoolsForStackIds((Consumer) null, Arrays.asList(stackId1, stackId2, stackId4));

        assertNotNull(pools);
        assertEquals(0, pools.size());
    }

    @Test
    public void testGetSubPoolsForStackIdsNullConsumerCollection() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer3 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer4 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);
        Pool dpool7 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId1);
        Pool dpool8 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId2);
        Pool dpool9 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId3);

        Collection<Pool> pools = this.poolCurator.getSubPoolsForStackIds((Collection<Consumer>) null,
            Arrays.asList(stackId1, stackId2, stackId4));

        assertNotNull(pools);
        assertEquals(0, pools.size());
    }

    @Test
    public void testGetSubPoolsForStackIdsEmptyConsumerCollection() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer3 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer4 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);
        Pool dpool7 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId1);
        Pool dpool8 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId2);
        Pool dpool9 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId3);

        Collection<Pool> pools = this.poolCurator.getSubPoolsForStackIds(Collections.emptyList(),
            Arrays.asList(stackId1, stackId2, stackId4));

        assertNotNull(pools);
        assertEquals(0, pools.size());
    }

    @Test
    public void testGetSubPoolsForStackIdsIgnoresNullsInCollection() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer3 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer4 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);
        Pool dpool7 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId1);
        Pool dpool8 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId2);
        Pool dpool9 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId3);

        Collection<Pool> pools = this.poolCurator.getSubPoolsForStackIds(Arrays.asList(consumer1, null),
            Arrays.asList(stackId1, stackId2, stackId4));

        assertNotNull(pools);
        assertEquals(2, pools.size());
        assertThat(pools, hasItems(dpool1, dpool2));
    }

    @Test
    public void testGetSubPoolsForStackIdsWithConsumerCollectionOfNulls() {
        Owner owner = this.createOwner();
        Consumer consumer1 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer2 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer3 = this.createConsumer(owner, this.systemConsumerType);
        Consumer consumer4 = this.createConsumer(owner, this.systemConsumerType);

        String stackId1 = "test_stack_id-1";
        String stackId2 = "test_stack_id-2";
        String stackId3 = "test_stack_id-3";
        String stackId4 = "test_stack_id-4";

        Pool dpool1 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId1);
        Pool dpool2 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId2);
        Pool dpool3 = this.setupHostLimitedVirtPoolStack(owner, consumer1, stackId3);
        Pool dpool4 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId1);
        Pool dpool5 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId2);
        Pool dpool6 = this.setupHostLimitedVirtPoolStack(owner, consumer2, stackId3);
        Pool dpool7 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId1);
        Pool dpool8 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId2);
        Pool dpool9 = this.setupHostLimitedVirtPoolStack(owner, consumer3, stackId3);

        Collection<Pool> pools = this.poolCurator.getSubPoolsForStackIds(Arrays.asList(null, null, null),
            Arrays.asList(stackId1, stackId2, stackId4));

        assertNotNull(pools);
        assertEquals(0, pools.size());
    }

    @Test
    public void confirmBonusPoolDeleted() {
        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(16L);
        sub.setStartDate(TestUtil.createDate(2006, 10, 21));
        sub.setEndDate(TestUtil.createDate(2020, 1, 1));
        sub.setModified(new Date());

        Pool sourcePool = poolManager.createAndEnrichPools(sub).get(0);
        poolCurator.create(sourcePool);
        Entitlement e = new Entitlement(sourcePool, consumer, owner, 1);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);

        Pool pool2 = TestUtil.createPool(owner, product);
        pool2.setSourceEntitlement(e);
        pool2.setSourceSubscription(
            new SourceSubscription(sourcePool.getSubscriptionId(), DERIVED_POOL_SUB_KEY));
        poolCurator.create(pool2);

        assertEquals(2, poolCurator.getBySubscriptionId(owner, sub.getId()).size());
        poolService.deletePool(sourcePool);

        // because we check for null now, we want to verify the
        // subpool gets deleted when the original pool is deleted.
        Pool gone = poolCurator.get(pool2.getId());
        assertNull(gone);
    }

    @Test
    public void batchDeleteTest() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        List<Pool> pools = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pools.add(createPool(owner2, "id123"));
        }

        for (Pool pool : pools) {
            assertNotNull(poolCurator.get(pool.getId()));
        }

        poolCurator.batchDelete(pools, null);
        for (Pool pool : pools) {
            assertNull(poolCurator.get(pool.getId()));
        }
    }

    @Test
    public void batchDeleteAlreadyDeletedTest() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        List<Pool> pools = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            Pool p = createPool(owner2, "id123");
            pools.add(p);
            ids.add(p.getId());
        }

        for (Pool pool : pools) {
            assertNotNull(poolCurator.get(pool.getId()));
        }

        poolCurator.batchDelete(pools, ids);
        for (Pool pool : pools) {
            assertNotNull(poolCurator.get(pool.getId()));
        }
    }

    @Test
    public void handleNull() {
        Pool noexist = new Pool()
            .setId("betternotexist")
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-3, 0, 0))
            .setEndDate(TestUtil.createDateOffset(3, 0, 0));

        poolCurator.delete(noexist);
    }

    @Test
    public void testGetPoolsBySubId() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        Pool pool = createPool(owner2, "id123");

        List<Pool> result = poolCurator.getPoolsBySubscriptionId(pool.getSubscriptionId()).list();
        assertEquals(1, result.size());
        assertEquals(pool, result.get(0));
    }

    @Test
    public void testGetPoolsBySubIdNull() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        createPool(owner2, "id123");

        List<Pool> result = poolCurator.getPoolsBySubscriptionId(null).list();
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

        p1Attributes.setAttribute("x", "true");
        p2Attributes.setAttribute("x", "true");
        p2BadAttributes.setAttribute("x", "false");

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

    @Test
    public void testGetPoolsOrderedByProductNameAscending() {
        Owner owner1 = this.createOwner();

        Pool p1 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p1", "xyz"))
            .setSourceSubscription(new SourceSubscription("subscriptionId-phil", PRIMARY_POOL_SUB_KEY));

        Pool p2 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p2", "abc"))
            .setSourceSubscription(new SourceSubscription("subscriptionId-ned", "primary1"));

        Pool p3 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p3", "lmn"))
            .setSourceSubscription(new SourceSubscription("subscriptionId-ned1", "primary11"));

        this.poolCurator.create(p3);
        this.poolCurator.create(p2);
        this.poolCurator.create(p1);

        PageRequest req = new PageRequest();

        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("Product.name");
        Date activeOn = new Date();

        Page<List<Pool>> page = poolManager.listAvailableEntitlementPools(null, null, owner1.getId(),
            null, null, activeOn, false, null, req, false, false, null);

        List<Pool> pools = page.getPageData();
        List<Pool> results = new ArrayList<>();
        results.add(p2);
        results.add(p3);
        results.add(p1);
        assertEquals(results, pools);

    }

    @Test
    public void testGetPoolsOrderedByProductNameDescending() {
        // Checking for Descending
        Owner owner1 = this.createOwner();
        this.ownerCurator.create(owner1);

        Pool p1 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p1", "xyz"));
        p1.setSourceSubscription(new SourceSubscription("subscriptionId-phil", PRIMARY_POOL_SUB_KEY));
        Pool p2 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p2", "abc"));
        p2.setSourceSubscription(new SourceSubscription("subscriptionId-ned", "primary1"));
        Pool p3 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p3", "lmn"));
        p3.setSourceSubscription(new SourceSubscription("subscriptionId-ned1", "primary11"));
        this.poolCurator.create(p3);
        this.poolCurator.create(p1);
        this.poolCurator.create(p2);
        PageRequest req1 = new PageRequest();
        req1.setSortBy("Product.name");
        req1.setOrder(PageRequest.Order.DESCENDING);

        Date activeOn1 = new Date();

        Page<List<Pool>> page1 = poolManager.listAvailableEntitlementPools(null, null, owner1.getId(),
            null, null, activeOn1, false, null, req1, false, false, null);

        List<Pool> pools1 = page1.getPageData();

        List<Pool> results1 = new ArrayList<>();
        results1.add(p1);
        results1.add(p3);
        results1.add(p2);

        assertEquals(results1, pools1);
    }

    private List<Owner> setupDBForProductIdTests() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        this.ownerCurator.create(owner1);
        this.ownerCurator.create(owner2);

        Product product1 = this.generateProduct(owner1, "p1", "p1");
        product1.setProvidedProducts(this.generateProductCollection(owner1, "pp-a-", 3));
        Product dProduct1 = this.generateProduct(owner1, "dp1", "dp1");
        dProduct1.setProvidedProducts(this.generateProductCollection(owner1, "dpp-a-", 3));
        product1.setDerivedProduct(dProduct1);

        Pool p1 = TestUtil.createPool(owner1, product1);

        Product product2 = this.generateProduct(owner2, "p2", "p2");
        product2.setProvidedProducts(this.generateProductCollection(owner2, "pp-b-", 3));
        Product dProduct2 = this.generateProduct(owner2, "dp2", "dp2");
        dProduct2.setProvidedProducts(this.generateProductCollection(owner2, "dpp-b-", 3));
        product2.setDerivedProduct(dProduct2);

        Pool p2 = TestUtil.createPool(owner2, product2);

        this.poolCurator.create(p1);
        this.poolCurator.create(p2);

        return Arrays.asList(owner1, owner2);
    }

    @Test
    public void testGetPoolBySubscriptionId() {
        Owner owner1 = this.createOwner();
        this.ownerCurator.create(owner1);

        Pool p1 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p1", "p1"));
        p1.setSourceSubscription(new SourceSubscription("subscriptionId-phil", PRIMARY_POOL_SUB_KEY));

        Pool p2 = TestUtil.createPool(owner1, this.generateProduct(owner1, "p2", "p2"));
        p2.setSourceSubscription(new SourceSubscription("subscriptionId-ned", PRIMARY_POOL_SUB_KEY));

        this.poolCurator.create(p1);
        this.poolCurator.create(p2);

        Page<List<Pool>> result = this.poolCurator.listAvailableEntitlementPools(null, owner1.getId(),
            (Collection<String>) null, "subscriptionId-phil", new Date(), null, null, false,
            false, false, null);
        assertEquals("subscriptionId-phil", result.getPageData().get(0).getSubscriptionId());
    }

    private Product generateProduct(Owner owner, String id, String name) {
        return this.createProduct(id, name);
    }

    private Set<Product> generateProductCollection(Owner owner, String prefix, int count) {
        Set<Product> products = new HashSet<>();

        for (int i = 0; i < count; ++i) {
            products.add(this.generateProduct(owner, prefix + i, prefix + i));
        }

        return products;
    }

    private Pool createPool(Owner o, String subId) {
        Pool pool = TestUtil.createPool(o, product);
        pool.setSourceSubscription(new SourceSubscription(subId, PRIMARY_POOL_SUB_KEY));
        return poolCurator.create(pool);
    }

    protected List<Pool> setupPrimaryPoolsTests() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        this.ownerCurator.create(owner1);
        this.ownerCurator.create(owner2);

        LinkedList<Pool> list = new LinkedList<>();

        Product prod1 = this.createProduct();
        Product prod2 = this.createProduct();
        Product prod3 = this.createProduct();
        Product prod4 = this.createProduct();
        Product prod5 = this.createProduct();
        Product prod6 = this.createProduct();

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
        p5.setSourceSubscription(new SourceSubscription("sub3", "master"));
        list.add(p5);

        Pool p6 = TestUtil.createPool(owner2, prod6);
        p6.setSourceSubscription(new SourceSubscription("sub3", "asd"));
        list.add(p6);

        this.poolCurator.create(p1);
        this.poolCurator.create(p2);
        this.poolCurator.create(p3);
        this.poolCurator.create(p4);
        this.poolCurator.create(p5);
        this.poolCurator.create(p6);

        return list;
    }

    private Pool getPrimaryPoolBySubscriptionId(String subscriptionId) {
        for (Pool pool : this.poolCurator.getPoolsBySubscriptionId(subscriptionId)) {
            if (pool.getType() == Pool.PoolType.NORMAL) {
                return pool;
            }
        }
        return null;
    }

    @Test
    public void testGetPrimaryPoolBySubscriptionId() {
        List<Pool> pools = this.setupPrimaryPoolsTests();

        Pool actual = getPrimaryPoolBySubscriptionId("sub1");
        assertEquals(pools.get(0), actual);

        actual = getPrimaryPoolBySubscriptionId("sub2");
        assertEquals(pools.get(1), actual);
        actual = getPrimaryPoolBySubscriptionId("sub5");
        assertNull(actual);
    }

    @Test
    public void testGetPrimaryPools() {
        List<Pool> pools = this.setupPrimaryPoolsTests();
        List<Pool> expected = new LinkedList<>();

        expected.add(pools.get(0));
        expected.add(pools.get(1));
        expected.add(pools.get(4));

        List<Pool> actual = this.poolCurator.getPrimaryPools().list();
        assertEquals(expected, actual);
    }

    @Test
    public void testHasAvailablePools() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            activeDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        assertTrue(poolCurator.hasActiveEntitlementPools(owner.getId(), activeDate));
    }

    @Test
    public void testHasAvailablePoolsNoPools() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);
        assertFalse(poolCurator.hasActiveEntitlementPools(owner.getId(), activeDate));
    }

    @Test
    public void testHasAvailablePoolsNotCurrent() {
        Date activeDate = TestUtil.createDate(2000, 3, 2);
        Date startDate = TestUtil.createDate(2001, 3, 2);

        Pool pool1 = createPool(owner, product, 100L,
            startDate, TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool1);

        assertFalse(poolCurator.hasActiveEntitlementPools(owner.getId(), activeDate));
    }

    @Test
    public void testLookupDevPoolForConsumer() {
        // Make sure that multiple pools exist.
        createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        Pool pool = createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        pool.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, consumer.getUuid());
        pool.setAttribute("another_attr", "20");
        pool.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        poolCurator.create(pool);

        Pool found = poolCurator.findDevPool(consumer);
        assertNotNull(found);
        assertEquals(pool.getId(), found.getId());
    }

    @Test
    public void testDevPoolForConsumerNotFoundReturnsNullWhenNoMatchOnConsumer() {
        // Make sure that multiple pools exist.
        createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        Pool pool = createPool(owner, product, -1L, TestUtil.createDate(2010, 3, 2),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        pool.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, "does-not-exist");
        pool.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        poolCurator.create(pool);

        Pool found = poolCurator.findDevPool(consumer);
        assertNull(found);
    }

    @Test
    public void testUpdateQuantityColumnsOnPool() {
        Consumer consumer = createMockConsumer(owner, true);

        Pool pool = createPool(owner, product)
            .setQuantity(10L)
            .setStartDate(TestUtil.createDate(2010, 3, 2))
            .setEndDate(TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        poolCurator.create(pool);
        Entitlement e = new Entitlement(pool, consumer, owner, 5);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);
        assertEquals(0, pool.getConsumed().longValue());
        assertEquals(0, pool.getExported().longValue());

        poolCurator.calculateConsumedForOwnersPools(owner);
        poolCurator.calculateExportedForOwnersPools(owner);
        poolCurator.refresh(pool);

        assertEquals(5, pool.getConsumed().longValue());
        assertEquals(5, pool.getExported().longValue());
    }

    @Test
    public void testUpdateQuantityColumnsOnPoolNotManifest() {
        Consumer consumer = this.createConsumer(owner);

        Pool pool = createPool(owner, product)
            .setQuantity(20L)
            .setStartDate(TestUtil.createDate(2010, 3, 2))
            .setEndDate(TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2));
        poolCurator.create(pool);
        Entitlement e = new Entitlement(pool, consumer, owner, 5);
        e.setId(Util.generateDbUUID());
        entitlementCurator.create(e);
        assertEquals(0, pool.getConsumed().longValue());
        assertEquals(0, pool.getExported().longValue());

        poolCurator.calculateConsumedForOwnersPools(owner);
        poolCurator.calculateExportedForOwnersPools(owner);
        poolCurator.refresh(pool);

        assertEquals(5, pool.getConsumed().longValue());
        assertEquals(0, pool.getExported().longValue());
    }

    @Test
    public void testMarkCertificatesDirtyForPoolsWithNormalProduct() {
        Consumer consumer = this.createConsumer(owner);

        Pool pool = createPool(owner, product, -1L, TestUtil.createDate(2017, 3, 27),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 27));

        poolCurator.create(pool);
        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool, cert);
        assertFalse(entitlement.isDirty(), "entitlement should not be dirty initially");

        poolCurator.create(pool);
        entitlementCurator.create(entitlement);
        poolCurator.markCertificatesDirtyForPoolsWithProducts(owner, Collections.singleton(product.getId()));

        entitlementCurator.refresh(entitlement);

        assertTrue(entitlement.isDirty(), "entitlement should be be marked dirty");
    }

    @Test
    public void testMarkCertificatesDirtyForPoolsWithNormalProductHavingLargeNumberOfProducts() {
        List<String> productIds = new ArrayList<>();
        for (int i = 0; i < config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE); i++) {
            productIds.add("productId" + i);
        }
        productIds.add(product.getId());

        Consumer consumer = this.createConsumer(owner);

        Pool pool = createPool(owner, product, -1L, TestUtil.createDate(2017, 3, 27),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 27));

        poolCurator.create(pool);
        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool, cert);
        assertFalse(entitlement.isDirty(), "entitlement should not be dirty initially");

        poolCurator.create(pool);
        entitlementCurator.create(entitlement);
        poolCurator.markCertificatesDirtyForPoolsWithProducts(owner, productIds);

        entitlementCurator.refresh(entitlement);

        assertTrue(entitlement.isDirty(), "entitlement should be be marked dirty");
    }

    @Test
    public void testMarkCertificatesDirtyForPoolsWithProvidedProduct() {
        Consumer consumer = this.createConsumer(owner);

        Product providedProduct = new Product("prov_prod-1", "Test Provided Product");
        productCurator.create(providedProduct);

        Product parent = TestUtil.createProduct();
        parent.setProvidedProducts(Set.of(providedProduct));
        productCurator.create(parent);

        Pool pool = TestUtil.createPool(owner, parent, 5);
        poolCurator.create(pool);

        EntitlementCertificate cert = createEntitlementCertificate("fake", "fake");
        Entitlement entitlement = createEntitlement(owner, consumer, pool, cert);
        assertFalse(entitlement.isDirty(), "entitlement should not be dirty initially");

        entitlementCurator.create(entitlement);

        poolCurator.markCertificatesDirtyForPoolsWithProducts(owner, List.of(providedProduct.getId()));
        entitlementCurator.refresh(entitlement);
        assertTrue(entitlement.isDirty(), "entitlement should be marked dirty");
    }

    @Test
    public void testFetchingPoolProvidedProductIds() {
        Owner owner = this.createOwner();

        List<Pool> pools = new ArrayList<>();
        List<Product> products = new ArrayList<>();

        int poolsToCreate = 5;
        int productsPerPool = 5;
        int productsToAttach = 3;

        for (int i = 0; i < poolsToCreate; ++i) {
            for (int p = 0; p < productsPerPool; ++p) {
                String name = String.format("prod-%d", productsPerPool * i + p);
                products.add(this.createProduct(name, name));
            }

            Product product = TestUtil.createProduct();
            for (int p = productsPerPool * i; p < i * productsPerPool + productsToAttach; ++p) {
                product.addProvidedProduct(products.get(p));
            }

            product = this.createProduct(product);
            pools.add(this.createPool(owner, product));
        }

        List<Pool> targetPools = new LinkedList<>();
        Map<String, Set<String>> expectedPoolProductMap = new HashMap<>();

        for (int i : Arrays.asList(0, 2, 4)) {
            Set<String> productIds = new HashSet<>();
            Pool pool = pools.get(i);

            for (int j = productsPerPool * i; j < productsPerPool * i + productsToAttach; ++j) {
                productIds.add(products.get(j).getId());
            }

            targetPools.add(pool);
            expectedPoolProductMap.put(pool.getId(), productIds);
        }

        Map<String, Set<String>> actualPoolProductMap = this.poolCurator
            .getProvidedProductIdsByPools(targetPools);

        assertNotNull(actualPoolProductMap);
        assertEquals(expectedPoolProductMap, actualPoolProductMap);
    }

    @Test
    public void testFetchingPoolProvidedProductIdsByPoolIds() {
        Owner owner = this.createOwner();

        List<Pool> pools = new ArrayList<>();
        List<Product> products = new ArrayList<>();

        int poolsToCreate = 5;
        int productsPerPool = 5;
        int productsToAttach = 3;

        for (int i = 0; i < poolsToCreate; ++i) {
            for (int p = 0; p < productsPerPool; ++p) {
                String name = String.format("prod-%d", productsPerPool * i + p);
                products.add(this.createProduct(name, name));
            }

            Product product = TestUtil.createProduct();
            for (int p = productsPerPool * i; p < i * productsPerPool + productsToAttach; ++p) {
                product.addProvidedProduct(products.get(p));
            }

            product = this.createProduct(product);
            pools.add(this.createPool(owner, product));
        }

        Map<String, Set<String>> expectedPoolProductMap = new HashMap<>();

        for (int i : Arrays.asList(0, 2, 4)) {
            Set<String> productIds = new HashSet<>();
            Pool pool = pools.get(i);

            for (int j = productsPerPool * i; j < productsPerPool * i + productsToAttach; ++j) {
                productIds.add(products.get(j).getId());
            }

            expectedPoolProductMap.put(pool.getId(), productIds);
        }

        Map<String, Set<String>> actualPoolProductMap = this.poolCurator
            .getProvidedProductIdsByPoolIds(expectedPoolProductMap.keySet());

        assertNotNull(actualPoolProductMap);
        assertEquals(expectedPoolProductMap, actualPoolProductMap);
    }

    @Test
    public void testFetchingPoolDerivedProvidedProductIdsByPoolIds() {
        Owner owner = this.createOwner();

        List<Pool> pools = new ArrayList<>();
        List<Product> products = new ArrayList<>();

        int poolsToCreate = 5;
        int productsPerPool = 5;
        int productsToAttach = 3;

        for (int i = 0; i < poolsToCreate; ++i) {
            for (int p = 0; p < productsPerPool; ++p) {
                String name = String.format("prod-%d", productsPerPool * i + p);
                products.add(this.createProduct(name, name));
            }

            Product product = TestUtil.createProduct();
            Product derived = TestUtil.createProduct();

            product.setDerivedProduct(derived);
            for (int p = productsPerPool * i; p < i * productsPerPool + productsToAttach; ++p) {
                derived.addProvidedProduct(products.get(p));
            }

            derived = this.createProduct(derived);
            product = this.createProduct(product);

            pools.add(this.createPool(owner, product));
        }

        Map<String, Set<String>> expectedPoolProductMap = new HashMap<>();

        for (int i : Arrays.asList(0, 2, 4)) {
            Set<String> productIds = new HashSet<>();
            Pool pool = pools.get(i);

            for (int j = productsPerPool * i; j < productsPerPool * i + productsToAttach; ++j) {
                productIds.add(products.get(j).getId());
            }

            expectedPoolProductMap.put(pool.getId(), productIds);
        }

        Map<String, Set<String>> actualPoolProductMap = this.poolCurator
            .getDerivedProvidedProductIdsByPoolIds(expectedPoolProductMap.keySet());

        assertNotNull(actualPoolProductMap);
        assertEquals(expectedPoolProductMap, actualPoolProductMap);
    }

    @Test
    public void testFetchingPoolDerivedProvidedProductIdsByPools() {
        Owner owner = this.createOwner();

        List<Pool> pools = new ArrayList<>();
        List<Product> products = new ArrayList<>();

        int poolsToCreate = 5;
        int productsPerPool = 5;
        int productsToAttach = 3;

        for (int i = 0; i < poolsToCreate; ++i) {
            for (int p = 0; p < productsPerPool; ++p) {
                String name = String.format("prod-%d", productsPerPool * i + p);
                products.add(this.createProduct(name, name));
            }

            Product product = TestUtil.createProduct();
            Product derived = TestUtil.createProduct();

            product.setDerivedProduct(derived);
            for (int p = productsPerPool * i; p < i * productsPerPool + productsToAttach; ++p) {
                derived.addProvidedProduct(products.get(p));
            }

            derived = this.createProduct(derived);
            product = this.createProduct(product);

            pools.add(this.createPool(owner, product));
        }

        List<Pool> targetPools = new LinkedList<>();
        Map<String, Set<String>> expectedPoolProductMap = new HashMap<>();

        for (int i : Arrays.asList(0, 2, 4)) {
            Set<String> productIds = new HashSet<>();
            Pool pool = pools.get(i);

            for (int j = productsPerPool * i; j < productsPerPool * i + productsToAttach; ++j) {
                productIds.add(products.get(j).getId());
            }

            targetPools.add(pool);
            expectedPoolProductMap.put(pool.getId(), productIds);
        }

        this.poolCurator.flush();

        Map<String, Set<String>> actualPoolProductMap = this.poolCurator
            .getDerivedProvidedProductIdsByPools(targetPools);

        assertNotNull(actualPoolProductMap);
        assertEquals(expectedPoolProductMap, actualPoolProductMap);
    }

    protected Stream<Object[]> getPoolSetSizes() {
        int inBlockSize = getConfigForParameters().getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
        int halfBlockSize = inBlockSize / 2;

        return Stream.of(
            new Object[] { 0 },
            new Object[] { 1 },
            new Object[] { 10 },
            new Object[] { halfBlockSize },

            new Object[] { inBlockSize },
            new Object[] { inBlockSize + 1 },
            new Object[] { inBlockSize + 10 },

            new Object[] { inBlockSize + halfBlockSize },

            new Object[] { 2 * inBlockSize },
            new Object[] { 2 * inBlockSize + 1 },
            new Object[] { 2 * inBlockSize + 10 },
            new Object[] { 2 * inBlockSize + halfBlockSize },

            new Object[] { 3 * inBlockSize },
            new Object[] { 3 * inBlockSize + 1 },
            new Object[] { 3 * inBlockSize + 10 },
            new Object[] { 3 * inBlockSize + halfBlockSize });
    }

    @ParameterizedTest
    @MethodSource("getPoolSetSizes")
    public void testFetchingPoolProvidedProductIdsWithVaryingPoolSetSizes(int poolsToCreate) {
        Owner owner = this.createOwner();

        List<Pool> pools = new LinkedList<>();
        Map<String, Set<String>> expectedPoolProductMap = new HashMap<>();

        for (int i = 0; i < poolsToCreate; ++i) {
            Product product = TestUtil.createProduct();

            String prodName = String.format("prod-%d", i);
            product.addProvidedProduct(this.createProduct(prodName, prodName));
            product = this.createProduct(product);

            Pool pool = this.createPool(owner, product);

            Set<String> providedProducts = new HashSet<>();
            providedProducts.add(prodName);

            expectedPoolProductMap.put(pool.getId(), providedProducts);

            pools.add(this.poolCurator.merge(pool));
        }

        Map<String, Set<String>> actualPoolProductMap = this.poolCurator.getProvidedProductIdsByPools(pools);

        assertNotNull(actualPoolProductMap);
        assertEquals(expectedPoolProductMap, actualPoolProductMap);
    }

    @Test
    public void testRemoveCDN() {
        Owner owner = this.createOwner();

        Cdn cdn = this.createCdn();
        Product product = this.createProduct();
        Pool pool = this.createPool(owner, product);

        pool.setCdn(cdn);
        this.poolCurator.merge(pool);
        this.poolCurator.flush();
        this.poolCurator.clear();

        this.poolCurator.removeCdn(cdn);
        Pool fetched = this.poolCurator.get(pool.getId());

        assertNotNull(fetched);
        assertNull(fetched.getCdn());
    }

    @Test
    public void testClearPoolSourceEntitlementRefs() {
        Date startDate = TestUtil.createDate(2010, 3, 2);
        Date endDate = TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2);

        Pool pool1 = createPool(owner, product, 20L, startDate, endDate);
        poolCurator.create(pool1);

        Entitlement e = new Entitlement(pool1, consumer, owner, 5);
        e.setId("test-entitlement-id-1");
        entitlementCurator.create(e);

        Pool pool2 = createPool(owner, product, 20L, startDate, endDate);
        pool2.setSourceEntitlement(e);
        poolCurator.create(pool2);

        this.poolCurator.clearPoolSourceEntitlementRefs(Arrays.asList(pool2.getId()));

        this.poolCurator.refresh(pool2);
        assertNull(pool2.getSourceEntitlement());
    }

    @Test
    public void testClearPoolSourceEntitlementRefsDoesntAffectUnspecifiedPools() {
        Date startDate = TestUtil.createDate(2010, 3, 2);
        Date endDate = TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2);

        Pool pool1 = createPool(owner, product, 20L, startDate, endDate);
        poolCurator.create(pool1);

        Entitlement e = new Entitlement(pool1, consumer, owner, 5);
        e.setId("test-entitlement-id-1");
        entitlementCurator.create(e);

        Pool pool2 = createPool(owner, product, 20L, startDate, endDate);
        pool2.setSourceEntitlement(e);
        poolCurator.create(pool2);

        this.poolCurator.clearPoolSourceEntitlementRefs(Arrays.asList(pool1.getId()));

        this.poolCurator.refresh(pool2);
        assertSame(e, pool2.getSourceEntitlement());
    }

    @Test
    public void testClearPoolSourceEntitlementRefsWorksOnMultiplePools() {
        Date startDate = TestUtil.createDate(2010, 3, 2);
        Date endDate = TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2);

        Pool pool1 = createPool(owner, product, 20L, startDate, endDate);
        poolCurator.create(pool1);

        Entitlement e = new Entitlement(pool1, consumer, owner, 5);
        e.setId("test-entitlement-id-1");
        entitlementCurator.create(e);

        Pool pool2 = createPool(owner, product, 20L, startDate, endDate);
        pool2.setSourceEntitlement(e);
        poolCurator.create(pool2);

        Pool pool3 = createPool(owner, product, 20L, startDate, endDate);
        pool3.setSourceEntitlement(e);
        poolCurator.create(pool3);

        Pool pool4 = createPool(owner, product, 20L, startDate, endDate);
        pool4.setSourceEntitlement(e);
        poolCurator.create(pool4);

        this.poolCurator.clearPoolSourceEntitlementRefs(Arrays.asList(pool2.getId(), pool3.getId()));

        this.poolCurator.refresh(pool2);
        this.poolCurator.refresh(pool3);
        this.poolCurator.refresh(pool4);

        assertNull(pool2.getSourceEntitlement());
        assertNull(pool3.getSourceEntitlement());
        assertSame(e, pool4.getSourceEntitlement());
    }

    @Test
    public void testGetExistingPoolIdsByIds() {
        Date startDate = TestUtil.createDate(2010, 3, 2);
        Date endDate = TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2);

        Pool pool1 = createPool(owner, product, 20L, startDate, endDate);
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, product, 20L, startDate, endDate);
        poolCurator.create(pool2);

        Pool pool3 = createPool(owner, product, 20L, startDate, endDate);
        poolCurator.create(pool3);

        Pool pool4 = createPool(owner, product, 20L, startDate, endDate);
        poolCurator.create(pool4);

        Set<String> output;
        output = this.poolCurator.getExistingPoolIdsByIds(Arrays.asList(pool2.getId(), pool3.getId()));

        assertFalse(output.contains(pool1.getId()));
        assertTrue(output.contains(pool2.getId()));
        assertTrue(output.contains(pool3.getId()));
        assertFalse(output.contains(pool4.getId()));
        assertFalse(output.contains("banana"));

        output = this.poolCurator.getExistingPoolIdsByIds(
            Arrays.asList(pool1.getId(), pool3.getId(), "banana"));

        assertTrue(output.contains(pool1.getId()));
        assertFalse(output.contains(pool2.getId()));
        assertTrue(output.contains(pool3.getId()));
        assertFalse(output.contains(pool4.getId()));
        assertFalse(output.contains("banana"));
    }

    @Test
    public void testGetConsumerStackDerivedPoolIdMap() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner1);
        Consumer consumer3 = this.createConsumer(owner2);

        Date startDate = TestUtil.createDate(2010, 3, 2);
        Date endDate = TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2);

        String stackId1 = "123";
        Product stackingProduct1 = TestUtil.createProduct();
        stackingProduct1.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct1.setAttribute(Product.Attributes.STACKING_ID, stackId1);
        stackingProduct1 = this.createProduct(stackingProduct1);

        String stackId2 = "456";
        Product stackingProduct2 = TestUtil.createProduct();
        stackingProduct2.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct2.setAttribute(Product.Attributes.STACKING_ID, stackId2);
        stackingProduct2 = this.createProduct(stackingProduct2);

        String stackId3 = "789";
        Product stackingProduct3 = TestUtil.createProduct();
        stackingProduct3.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct3.setAttribute(Product.Attributes.STACKING_ID, stackId3);
        stackingProduct3 = this.createProduct(stackingProduct3);

        Pool pool1 = createPool(owner, stackingProduct1, 20L, startDate, endDate);
        pool1.setSourceStack(new SourceStack(consumer1, stackId1));
        pool1.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer1.getUuid());
        poolCurator.create(pool1);

        Pool pool2 = createPool(owner, stackingProduct2, 20L, startDate, endDate);
        pool2.setSourceStack(new SourceStack(consumer1, stackId2));
        pool2.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer1.getUuid());
        poolCurator.create(pool2);

        Pool pool3 = createPool(owner, stackingProduct3, 20L, startDate, endDate);
        pool3.setSourceStack(new SourceStack(consumer2, stackId3));
        pool3.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer2.getUuid());
        poolCurator.create(pool3);

        Pool pool4 = createPool(owner, stackingProduct1, 20L, startDate, endDate);
        pool4.setSourceStack(new SourceStack(consumer2, stackId1));
        pool4.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer2.getUuid());
        poolCurator.create(pool4);

        Pool pool5 = createPool(owner, stackingProduct2, 20L, startDate, endDate);
        pool5.setSourceStack(new SourceStack(consumer2, stackId2));
        pool5.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer2.getUuid());
        poolCurator.create(pool5);

        Pool pool6 = createPool(owner, stackingProduct3, 20L, startDate, endDate);
        pool6.setSourceStack(new SourceStack(consumer3, stackId3));
        pool6.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer3.getUuid());
        poolCurator.create(pool6);

        Map<String, Set<String>> output;

        output = this.poolCurator.getConsumerStackDerivedPoolIdMap(
            Arrays.asList(stackId1, stackId2, stackId3));

        assertNotNull(output);
        assertEquals(3, output.size());
        assertTrue(output.containsKey(consumer1.getId()));
        assertTrue(output.containsKey(consumer2.getId()));
        assertTrue(output.containsKey(consumer3.getId()));
        assertEquals(output.get(consumer1.getId()), Util.asSet(pool1.getId(), pool2.getId()));
        assertEquals(output.get(consumer2.getId()), Util.asSet(pool3.getId(), pool4.getId(), pool5.getId()));
        assertEquals(output.get(consumer3.getId()), Util.asSet(pool6.getId()));

        output = this.poolCurator.getConsumerStackDerivedPoolIdMap(Arrays.asList(stackId1, stackId2));

        assertNotNull(output);
        assertEquals(2, output.size());
        assertTrue(output.containsKey(consumer1.getId()));
        assertTrue(output.containsKey(consumer2.getId()));
        assertFalse(output.containsKey(consumer3.getId()));
        assertEquals(output.get(consumer1.getId()), Util.asSet(pool1.getId(), pool2.getId()));
        assertEquals(output.get(consumer2.getId()), Util.asSet(pool4.getId(), pool5.getId()));

        output = this.poolCurator.getConsumerStackDerivedPoolIdMap(Arrays.asList(stackId1));

        assertNotNull(output);
        assertEquals(2, output.size());
        assertTrue(output.containsKey(consumer1.getId()));
        assertTrue(output.containsKey(consumer2.getId()));
        assertFalse(output.containsKey(consumer3.getId()));
        assertEquals(output.get(consumer1.getId()), Util.asSet(pool1.getId()));
        assertEquals(output.get(consumer2.getId()), Util.asSet(pool4.getId()));

        output = this.poolCurator.getConsumerStackDerivedPoolIdMap(Collections.emptyList());

        assertNotNull(output);
        assertEquals(0, output.size());

        output = this.poolCurator.getConsumerStackDerivedPoolIdMap(null);

        assertNotNull(output);
        assertEquals(0, output.size());
    }

    @Test
    @SuppressWarnings("checkstyle:methodlength")
    public void testGetUnentitledStackDerivedPoolIds() {
        Owner owner1 = this.createOwner("owner-1");
        Owner owner2 = this.createOwner("owner-2");

        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner1);
        Consumer consumer3 = this.createConsumer(owner2);

        Date startDate = TestUtil.createDate(2010, 3, 2);
        Date endDate = TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 1, 3, 2);

        String stackId1 = "123";
        Product stackingProduct1 = TestUtil.createProduct();
        stackingProduct1.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct1.setAttribute(Product.Attributes.STACKING_ID, stackId1);
        stackingProduct1 = this.createProduct(stackingProduct1);

        Product stackingProduct1b = TestUtil.createProduct();
        stackingProduct1b.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct1b.setAttribute(Product.Attributes.STACKING_ID, stackId1);
        stackingProduct1b = this.createProduct(stackingProduct1b);

        String stackId2 = "456";
        Product stackingProduct2 = TestUtil.createProduct();
        stackingProduct2.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct2.setAttribute(Product.Attributes.STACKING_ID, stackId2);
        stackingProduct2 = this.createProduct(stackingProduct2);

        Product stackingProduct2b = TestUtil.createProduct();
        stackingProduct2b.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct2b.setAttribute(Product.Attributes.STACKING_ID, stackId2);
        stackingProduct2b = this.createProduct(stackingProduct2b);

        String stackId3 = "789";
        Product stackingProduct3 = TestUtil.createProduct();
        stackingProduct3.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct3.setAttribute(Product.Attributes.STACKING_ID, stackId3);
        stackingProduct3 = this.createProduct(stackingProduct3);

        Product stackingProduct3b = TestUtil.createProduct();
        stackingProduct3b.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        stackingProduct3b.setAttribute(Product.Attributes.STACKING_ID, stackId3);
        stackingProduct3b = this.createProduct(stackingProduct3b);

        Pool pool1 = createPool(owner1, stackingProduct1, 20L, startDate, endDate);
        pool1.setSourceStack(new SourceStack(consumer1, stackId1));
        pool1.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer1.getUuid());
        poolCurator.create(pool1);

        Pool pool1a = createPool(owner1, stackingProduct1, 20L, startDate, endDate);
        poolCurator.create(pool1a);

        Entitlement ent1a = new Entitlement(pool1a, consumer1, owner, 5);
        ent1a.setId("test-entitlement-id-1a");
        entitlementCurator.create(ent1a);

        Pool pool1b = createPool(owner1, stackingProduct1, 20L, startDate, endDate);
        poolCurator.create(pool1b);

        Entitlement ent1b = new Entitlement(pool1b, consumer1, owner, 5);
        ent1b.setId("test-entitlement-id-1b");
        entitlementCurator.create(ent1b);

        Pool pool2 = createPool(owner1, stackingProduct2, 20L, startDate, endDate);
        pool2.setSourceStack(new SourceStack(consumer1, stackId2));
        pool2.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer1.getUuid());
        poolCurator.create(pool2);

        Pool pool2a = createPool(owner1, stackingProduct2, 20L, startDate, endDate);
        poolCurator.create(pool2a);

        Entitlement ent2a = new Entitlement(pool2a, consumer1, owner, 5);
        ent2a.setId("test-entitlement-id-2a");
        entitlementCurator.create(ent2a);

        Pool pool3 = createPool(owner1, stackingProduct3, 20L, startDate, endDate);
        pool3.setSourceStack(new SourceStack(consumer2, stackId3));
        pool3.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer2.getUuid());
        poolCurator.create(pool3);

        Pool pool3a = createPool(owner1, stackingProduct3, 20L, startDate, endDate);
        poolCurator.create(pool3a);

        Entitlement ent3a = new Entitlement(pool3a, consumer2, owner, 5);
        ent3a.setId("test-entitlement-id-3a");
        entitlementCurator.create(ent3a);

        Pool pool4 = createPool(owner1, stackingProduct1, 20L, startDate, endDate);
        pool4.setSourceStack(new SourceStack(consumer2, stackId1));
        pool4.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer2.getUuid());
        poolCurator.create(pool4);

        Pool pool4a = createPool(owner1, stackingProduct1, 20L, startDate, endDate);
        poolCurator.create(pool4a);

        Entitlement ent4a = new Entitlement(pool4a, consumer2, owner, 5);
        ent4a.setId("test-entitlement-id-4a");
        entitlementCurator.create(ent4a);

        Pool pool5 = createPool(owner1, stackingProduct2, 20L, startDate, endDate);
        pool5.setSourceStack(new SourceStack(consumer2, stackId2));
        pool5.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer2.getUuid());
        poolCurator.create(pool5);

        Pool pool5a = createPool(owner1, stackingProduct2, 20L, startDate, endDate);
        poolCurator.create(pool5a);

        Entitlement ent5a = new Entitlement(pool5a, consumer2, owner, 5);
        ent5a.setId("test-entitlement-id-5a");
        entitlementCurator.create(ent5a);

        Pool pool6 = createPool(owner2, stackingProduct3b, 20L, startDate, endDate);
        pool6.setSourceStack(new SourceStack(consumer3, stackId3));
        pool6.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer3.getUuid());
        poolCurator.create(pool6);

        Pool pool6a = createPool(owner2, stackingProduct3b, 20L, startDate, endDate);
        poolCurator.create(pool6a);

        Entitlement ent6a = new Entitlement(pool6a, consumer3, owner, 5);
        ent6a.setId("test-entitlement-id-6a");
        entitlementCurator.create(ent6a);

        Pool pool7 = createPool(owner2, stackingProduct1b, 20L, startDate, endDate);
        pool7.setSourceStack(new SourceStack(consumer3, stackId1));
        pool7.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer3.getUuid());
        poolCurator.create(pool7);

        Pool pool8 = createPool(owner2, stackingProduct2b, 20L, startDate, endDate);
        poolCurator.create(pool8);

        Entitlement ent8 = new Entitlement(pool8, consumer3, owner, 5);
        ent8.setId("test-entitlement-id-8");
        entitlementCurator.create(ent8);

        Set<String> output;

        // No entitlements should find existing unentitled stack derived pools
        output = this.poolCurator.getUnentitledStackDerivedPoolIds(null);

        assertNotNull(output);
        assertEquals(1, output.size());
        assertEquals(output, Util.asSet(pool7.getId()));

        output = this.poolCurator.getUnentitledStackDerivedPoolIds(Collections.emptyList());

        assertNotNull(output);
        assertEquals(1, output.size());
        assertEquals(output, Util.asSet(pool7.getId()));

        // Providing entitlements should simulate deleted entitlements
        output = this.poolCurator.getUnentitledStackDerivedPoolIds(
            Arrays.asList(ent1a.getId(), ent1b.getId(), ent2a.getId(), ent3a.getId()));

        assertNotNull(output);
        assertEquals(4, output.size());
        assertEquals(output, Util.asSet(pool1.getId(), pool2.getId(), pool3.getId(), pool7.getId()));

        // Filtering entitlements should not pull pools if more entitlements remain
        output = this.poolCurator.getUnentitledStackDerivedPoolIds(Arrays.asList(ent1a.getId()));

        assertNotNull(output);
        assertEquals(1, output.size());
        assertEquals(output, Util.asSet(pool7.getId()));

        // Filtering entitlements should not pull pools if more entitlements remain
        output = this.poolCurator.getUnentitledStackDerivedPoolIds(Arrays.asList(ent1b.getId()));

        assertNotNull(output);
        assertEquals(1, output.size());
        assertEquals(output, Util.asSet(pool7.getId()));

        // Bad entitlement IDs shouldn't impact output...
        output = this.poolCurator.getUnentitledStackDerivedPoolIds(
            Arrays.asList(ent1a.getId(), ent1b.getId(), ent2a.getId(), ent3a.getId(), "bad_ent_id"));

        assertNotNull(output);
        assertEquals(4, output.size());
        assertEquals(output, Util.asSet(pool1.getId(), pool2.getId(), pool3.getId(), pool7.getId()));

        output = this.poolCurator.getUnentitledStackDerivedPoolIds(Arrays.asList("bad_ent_id"));

        assertNotNull(output);
        assertEquals(1, output.size());
        assertEquals(output, Util.asSet(pool7.getId()));
    }

    public Map<String, List<Pool>> generateSubPools() {
        Owner owner = this.createOwner("test-owner");

        Supplier<Pool> generator = () -> {
            Product prod = this.createProduct();
            return TestUtil.createPool(owner, prod);
        };

        Map<String, List<Pool>> subPoolMap = new HashMap<>();

        subPoolMap.put("sub-1", Stream.generate(generator)
            .limit(3)
            .peek(pool -> pool.setSubscriptionId("sub-1"))
            .map(pool -> this.poolCurator.create(pool))
            .collect(Collectors.toList()));

        subPoolMap.put("sub-2", Stream.generate(generator)
            .limit(3)
            .peek(pool -> pool.setSubscriptionId("sub-2"))
            .map(pool -> this.poolCurator.create(pool))
            .collect(Collectors.toList()));

        subPoolMap.put("sub-3", Stream.generate(generator)
            .limit(3)
            .peek(pool -> pool.setSubscriptionId("sub-3"))
            .map(pool -> this.poolCurator.create(pool))
            .collect(Collectors.toList()));

        subPoolMap.put(null, Stream.generate(generator)
            .limit(3)
            .map(pool -> this.poolCurator.create(pool))
            .collect(Collectors.toList()));

        this.poolCurator.flush();

        return subPoolMap;
    }

    private void validateSubPoolMaps(Map<String, List<Pool>> expected, Map<String, List<Pool>> actual) {
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());

        for (String key : expected.keySet()) {
            assertTrue(actual.containsKey(key));

            List<Pool> expectedPools = expected.get(key);
            List<Pool> actualPools = actual.get(key);

            assertNotNull(expectedPools);
            assertNotNull(actualPools);
            assertEquals(expectedPools.size(), actualPools.size());

            assertTrue(actualPools.containsAll(expectedPools));
            assertTrue(expectedPools.containsAll(actualPools));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "sub-1", "sub-2", "sub-3", "no-sub" })
    public void testMapPoolsBySubscriptionIdsWithSingleSub(String subId) {
        Map<String, List<Pool>> subPoolMap = this.generateSubPools();
        Map<String, List<Pool>> expected = new HashMap<>();
        if (subPoolMap.containsKey(subId)) {
            expected.put(subId, subPoolMap.get(subId));
        }

        Map<String, List<Pool>> result = this.poolCurator
            .mapPoolsBySubscriptionIds(Collections.singleton(subId));

        this.validateSubPoolMaps(expected, result);
    }

    public static Stream<Arguments> multiSubMethodSource() {
        return Stream.of(
            Arguments.of(Arrays.asList("sub-1", "sub-2")),
            Arguments.of(Arrays.asList("sub-1", "sub-3")),
            Arguments.of(Arrays.asList("sub-2", "sub-3")),
            Arguments.of(Arrays.asList("sub-3", "no_sub")),
            Arguments.of(Arrays.asList("no-sub-1", "no_sub-2")));
    }

    @ParameterizedTest
    @MethodSource("multiSubMethodSource")
    public void testMapPoolsBySubscriptionIdsWithMultipleSubs(Collection<String> subIds) {
        Map<String, List<Pool>> subPoolMap = this.generateSubPools();

        Map<String, List<Pool>> expected = new HashMap<>();
        for (String subId : subIds) {
            if (subPoolMap.containsKey(subId)) {
                expected.put(subId, subPoolMap.get(subId));
            }
        }

        Map<String, List<Pool>> result = this.poolCurator
            .mapPoolsBySubscriptionIds(subIds);

        this.validateSubPoolMaps(expected, result);
    }

    @Test
    public void testMapPoolsBySubscriptionIdHandlesEmptyCollections() {
        Map<String, List<Pool>> subPoolMap = this.generateSubPools();

        Map<String, List<Pool>> result = this.poolCurator.mapPoolsBySubscriptionIds(Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testMapPoolsBySubscriptionIdHandlesNullInputs() {
        Map<String, List<Pool>> subPoolMap = this.generateSubPools();

        Map<String, List<Pool>> result = this.poolCurator.mapPoolsBySubscriptionIds(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCreatePool() {
        Product prod = this.createProduct();

        Pool pool = new Pool()
            .setOwner(this.owner)
            .setProduct(prod)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0));

        this.poolCurator.create(pool);

        this.poolCurator.flush();
        this.poolCurator.clear();

        Pool lookedUp = this.getEntityManager().find(Pool.class, pool.getId());

        assertNotNull(lookedUp);
        assertEquals(this.owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod.getId(), lookedUp.getProductId());
    }

    @Test
    public void testMultiplePoolsForOwnerProductAllowed() {
        Product prod = this.createProduct();

        Pool pool1 = new Pool()
            .setOwner(this.owner)
            .setProduct(prod)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0));

        Pool pool2 = new Pool()
            .setOwner(this.owner)
            .setProduct(prod)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0));

        this.poolCurator.create(pool1);
        this.poolCurator.create(pool2);

        this.poolCurator.flush();
        this.poolCurator.clear();

        Pool fetched1 = this.poolCurator.get(pool1.getId());
        Pool fetched2 = this.poolCurator.get(pool2.getId());

        assertNotNull(fetched1);
        assertNotNull(fetched2);
        assertNotEquals(fetched1.getId(), fetched2.getId());

        assertEquals(fetched1.getOwner().getId(), fetched1.getOwner().getId());
        assertEquals(fetched1.getProduct().getId(), fetched1.getProduct().getId());
    }

    @Test
    public void testPersistPopulatesCreatedAndUpdatedTimestamps() {
        Product product = this.createProduct();

        Pool pool = new Pool()
            .setOwner(this.owner)
            .setProduct(this.product)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0));

        assertNull(pool.getCreated());
        assertNull(pool.getUpdated());

        this.poolCurator.create(pool);

        assertNotNull(pool.getCreated());
        assertNotNull(pool.getUpdated());
    }

    @Test
    public void testPersistChangesUpdatedTimestamp() throws Exception {
        Product prod = this.createProduct();
        Pool pool = this.createPool(this.owner, prod);

        assertNotNull(pool.getCreated());
        assertNotNull(pool.getUpdated());

        Date created = (Date) pool.getCreated().clone();
        Date updated = (Date) pool.getUpdated().clone();

        // Wait a bit because MySQL tends to be silly with milliseconds
        Thread.sleep(1500);

        // Make an unrelated change to ensure the updated time is changed on persist
        pool.setQuantity(25L);
        this.poolCurator.merge(pool);
        this.poolCurator.flush();

        assertNotNull(pool.getCreated());
        assertNotNull(pool.getUpdated());

        assertNotEquals(updated, pool.getUpdated());
        assertTrue(updated.before(pool.getUpdated()));

        assertEquals(created, pool.getCreated());
    }

    @Test
    public void testLookupPoolsProvidingProduct() {
        Product childProduct = this.createProduct("2", "product-2");

        Product parentProduct = TestUtil.createProduct("1", "product-1");
        parentProduct.setProvidedProducts(Arrays.asList(childProduct));

        parentProduct = this.createProduct(parentProduct);
        Pool pool = TestUtil.createPool(owner, parentProduct, 5);
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            null, owner, childProduct.getId(), null);

        assertEquals(1, results.size());
        assertEquals(pool.getId(), results.get(0).getId());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testHasPoolsForProductsWithInvalidOwnerKey(String ownerKey) {
        assertThrows(IllegalArgumentException.class, () ->  {
            this.poolCurator.hasPoolsForProducts(ownerKey, List.of("prod-id"));
        });
    }

    @Test
    public void testHasPoolsForProductsWithNullProductIds() {
        assertThrows(IllegalArgumentException.class, () ->  {
            this.poolCurator.hasPoolsForProducts("owner-key", null);
        });
    }

    @Test
    public void testHasPoolsForProductsWithEmptyProductIds() {
        assertThrows(IllegalArgumentException.class, () ->  {
            this.poolCurator.hasPoolsForProducts("owner-key", List.of());
        });
    }

    @Test
    public void testHasPoolsForProductsWithNonExistingOwner() {
        boolean actual = this.poolCurator.hasPoolsForProducts("unknown-owner", List.of(pool.getProductId()));

        assertFalse(actual);
    }

    @Test
    public void testHasPoolsForProductsWithExistingPool() {
        boolean actual = this.poolCurator.hasPoolsForProducts(owner.getKey(), List.of(pool.getProductId()));

        assertTrue(actual);
    }

    @Test
    public void testHasPoolsForProductsWithMultipleExistingPools() {
        Product product2 = TestUtil.createProduct();
        product2 = this.createProduct(product2);
        Pool pool2 = this.poolCurator.create(new Pool()
            .setOwner(owner)
            .setProduct(product2)
            .setQuantity(20L)
            .setStartDate(TestUtil.createDate(2015, 10, 21))
            .setEndDate(TestUtil.createDate(2025, 1, 1))
            .setContractNumber("1")
            .setAccountNumber("2")
            .setOrderNumber("3"));

        Product product3 = TestUtil.createProduct();
        product3 = this.createProduct(product3);
        this.poolCurator.create(new Pool()
            .setOwner(owner)
            .setProduct(product3)
            .setQuantity(20L)
            .setStartDate(TestUtil.createDate(2015, 10, 21))
            .setEndDate(TestUtil.createDate(2025, 1, 1))
            .setContractNumber("1")
            .setAccountNumber("2")
            .setOrderNumber("3"));

        boolean actual = this.poolCurator
            .hasPoolsForProducts(owner.getKey(), List.of(pool.getProductId(), pool2.getProductId()));

        assertTrue(actual);
    }

    @Test
    public void testHasPoolsForProductsWithNonExistingProduct() {
        Owner owner2 = createOwner();
        owner2 = ownerCurator.create(owner2);

        Product product2 = TestUtil.createProduct();
        product2 = this.createProduct(product2);
        this.poolCurator.create(new Pool()
            .setOwner(owner2)
            .setProduct(product2)
            .setQuantity(20L)
            .setStartDate(TestUtil.createDate(2015, 10, 21))
            .setEndDate(TestUtil.createDate(2025, 1, 1))
            .setContractNumber("1")
            .setAccountNumber("2")
            .setOrderNumber("3"));

        boolean actual = this.poolCurator
            .hasPoolsForProducts(owner.getKey(), List.of(pool.getProductId(), product2.getId()));

        assertFalse(actual);
    }

    @Test
    public void testHasPoolsForProductsWithNonExistingPool() {
        Product product2 = TestUtil.createProduct();
        product2 = this.createProduct(product2);

        boolean actual = this.poolCurator
            .hasPoolsForProducts(owner.getKey(), List.of(pool.getProductId(), product2.getId()));

        assertFalse(actual);
    }

    private Map<String, Set<String>> getEmptySyspurposeAttributeMap() {
        return Map.of(
            "usage", new HashSet<>(),
            "roles", new HashSet<>(),
            "addons", new HashSet<>(),
            "support_type", new HashSet<>(),
            "support_level", new HashSet<>()
        );
    }

    @Test
    public void testGetSyspurposeAttributesByOwner() {
        Owner owner = this.createOwner();

        Product product1 = new Product()
            .setId("test-product-" + TestUtil.randomInt())
            .setName("test-product-" + TestUtil.randomInt())
            .setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage1a, usage1b")
            .setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons1a, addons1b")
            .setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role1a")
            .setAttribute(SystemPurposeAttributeType.SERVICE_LEVEL.toString(), "Standard");
        this.createProduct(product1);

        Product product2 = new Product()
            .setId("test-product-" + TestUtil.randomInt())
            .setName("test-product-" + TestUtil.randomInt())
            .setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage2a, usage2b")
            .setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons2a, addons2b")
            .setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role2a")
            .setAttribute(SystemPurposeAttributeType.SERVICE_LEVEL.toString(), "Layered")
            .setAttribute(Product.Attributes.SUPPORT_LEVEL_EXEMPT, "true");
        this.createProduct(product2);

        // This will be for a product with an expired pool
        Product product3 = new Product()
            .setId("test-product-" + TestUtil.randomInt())
            .setName("test-product-" + TestUtil.randomInt())
            .setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage3a, usage3b")
            .setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons3a, addons3b")
            .setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role3a");
        this.createProduct(product3);

        // This will be for product with no pool
        Product product4 = new Product()
            .setId("test-product-" + TestUtil.randomInt())
            .setName("test-product-" + TestUtil.randomInt())
            .setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage4a, usage4b")
            .setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons4a, addons4b")
            .setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role4a");
        this.createProduct(product4);

        this.createPool(owner, product1, 10L, new Date(), TestUtil.createDate(2100, 1, 1));
        this.createPool(owner, product2, 10L, new Date(), TestUtil.createDate(2100, 1, 1));
        this.createPool(owner, product3, 10L, TestUtil.createDate(2000, 1, 1),
            TestUtil.createDate(2001, 1, 1));

        Set<String> usage = Set.of("usage1a", "usage1b", "usage2a", "usage2b");
        Set<String> addons = Set.of("addons1a", "addons1b", "addons2a", "addons2b");
        Set<String> roles = Set.of("role1a", "role2a");
        Set<String> support = Set.of("Standard");

        Map<String, Set<String>> expected = this.getEmptySyspurposeAttributeMap();
        expected.get(SystemPurposeAttributeType.USAGE.toString()).addAll(usage);
        expected.get(SystemPurposeAttributeType.ADDONS.toString()).addAll(addons);
        expected.get(SystemPurposeAttributeType.ROLES.toString()).addAll(roles);
        expected.get(SystemPurposeAttributeType.SERVICE_LEVEL.toString()).addAll(support);

        Map<String, Set<String>> result = this.poolCurator.getSyspurposeAttributesByOwner(owner);
        assertEquals(expected, result);
    }

    @Test
    public void testGetNoSyspurposeAttributesByOwner() {
        Owner owner = this.createOwner();

        Map<String, Set<String>> result = this.poolCurator.getSyspurposeAttributesByOwner(owner);
        assertEquals(getEmptySyspurposeAttributeMap(), result);
    }

    @Test
    public void testGetSyspurposeAttributesNullOwner() {
        Owner owner = this.createOwner();

        Map<String, Set<String>> result = this.poolCurator.getSyspurposeAttributesByOwner((Owner) null);
        assertEquals(getEmptySyspurposeAttributeMap(), result);
    }

    @Test
    public void testGetSyspurposeAttributesNullOwnerId() {
        Owner owner = this.createOwner();

        Map<String, Set<String>> result = this.poolCurator.getSyspurposeAttributesByOwner((String) null);
        assertEquals(getEmptySyspurposeAttributeMap(), result);
    }

    @Test
    public void testGetPoolsReferencingInvalidProducts() {
        Owner owner = this.createOwner();

        Product globalProduct = TestUtil.createProduct()
            .setNamespace((Owner) null);

        Product ownerProduct = TestUtil.createProduct()
            .setNamespace(owner);

        Product otherProduct = TestUtil.createProduct()
            .setNamespace("other namespace");

        this.productCurator.create(globalProduct);
        this.productCurator.create(ownerProduct);
        this.productCurator.create(otherProduct);

        Pool pool1 = this.createPool(owner, globalProduct);
        Pool pool2 = this.createPool(owner, ownerProduct);
        Pool pool3 = this.createPool(owner, otherProduct);

        List<Pool> output = this.poolCurator.getPoolsReferencingInvalidProducts(owner.getId());

        assertNotNull(output);
        assertEquals(1, output.size());
        assertTrue(output.contains(pool3));
    }

    @Test
    public void testGetPoolsReferencingInvalidProductsReturnsEmptyCollectionWithNoResults() {
        Owner owner = this.createOwner();

        Product globalProduct = TestUtil.createProduct()
            .setNamespace((Owner) null);

        Product ownerProduct = TestUtil.createProduct()
            .setNamespace(owner);

        this.productCurator.create(globalProduct);
        this.productCurator.create(ownerProduct);

        Pool pool1 = this.createPool(owner, globalProduct);
        Pool pool2 = this.createPool(owner, ownerProduct);

        List<Pool> output = this.poolCurator.getPoolsReferencingInvalidProducts(owner.getId());

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "bad owner id" })
    public void testGetPoolsReferencingInvalidProductsReturnsEmptyCollectionWithInvalidOwner(String ownerId) {
        Owner owner = this.createOwner();

        Product globalProduct = TestUtil.createProduct()
            .setNamespace((Owner) null);

        Product ownerProduct = TestUtil.createProduct()
            .setNamespace(owner);

        Product otherProduct = TestUtil.createProduct()
            .setNamespace("other namespace");

        this.productCurator.create(globalProduct);
        this.productCurator.create(ownerProduct);
        this.productCurator.create(otherProduct);

        Pool pool1 = this.createPool(owner, globalProduct);
        Pool pool2 = this.createPool(owner, ownerProduct);
        Pool pool3 = this.createPool(owner, otherProduct);

        List<Pool> output = this.poolCurator.getPoolsReferencingInvalidProducts(ownerId);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }
}
