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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class PoolCuratorTest extends DatabaseTestFixture {

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

        product = TestUtil.createProduct();
        productCurator.create(product);

        consumer = TestUtil.createConsumer(owner);
        consumer.setFact("cpu_cores", "4");
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);
    }

    @Test
    public void testPoolNotYetActive() {
        Pool pool = createPoolAndSub(owner, product, 100L,
                TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer, consumer.getOwner(),
                (String) null, TestUtil.createDate(20450, 3, 2), true, false);
        assertEquals(0, results.size());

    }

    @Test
    public void testPoolExpired() {
        Pool pool = createPoolAndSub(owner, product, 100L,
                TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer, consumer.getOwner(),
                (String) null, TestUtil.createDate(2005, 3, 3), true, false);
        assertEquals(0, results.size());

        // If we specify no date filtering, the expired pool should be returned:
        results =
            poolCurator.listAvailableEntitlementPools(consumer, consumer.getOwner(),
                (String) null, null, true, false);
        assertEquals(1, results.size());
    }

    @Test
    public void testAvailablePoolsDoesNotIncludeUeberPool() throws Exception {
        Pool pool = createPoolAndSub(owner, product, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        ueberCertGenerator.generate(owner, new NoAuthPrincipal());

        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer, consumer.getOwner(),
                (String) null, null, true, false);
        assertEquals(1, results.size());
    }

    @Test
    public void testProductName() {
        Product p = new Product("someProduct", "An Extremely Great Product");
        productCurator.create(p);

        Pool pool = createPoolAndSub(owner, p, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, p.getId());
        Pool onlyPool = results.get(0);

        assertEquals("An Extremely Great Product", onlyPool.getProductName());
    }

    @Test
    public void testProductNameViaFind() {
        Product p = new Product("another", "A Great Operating System");
        productCurator.create(p);

        Pool pool = createPoolAndSub(owner, p, 25L,
            TestUtil.createDate(1999, 1, 10), TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);
        pool = poolCurator.find(pool.getId());

        assertEquals("A Great Operating System", pool.getProductName());
    }

    @Test
    public void testProductNameViaFindAll() {
        Product p = new Product("another", "A Great Operating System");
        productCurator.create(p);

        Pool pool = createPoolAndSub(owner, p, 25L,
            TestUtil.createDate(1999, 1, 10), TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);
        pool = poolCurator.listAll().get(0);

        assertEquals("A Great Operating System", pool.getProductName());
    }

    @Test
    public void testFuzzyProductMatchingWithoutSubscription() {
        Product parent = TestUtil.createProduct();
        productCurator.create(parent);

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct providedProduct = new ProvidedProduct(
            product.getId(), "Test Provided Product");
        providedProducts.add(providedProduct);

        Pool p = TestUtil.createPool(owner, parent, providedProducts, 5);
        providedProduct.setPool(p);
        poolCurator.create(p);
        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, product.getId());
        assertEquals(1, results.size());
    }

    @Test
    public void testPoolProducts() {
        Product another = TestUtil.createProduct();
        productCurator.create(another);

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct providedProduct = new ProvidedProduct(
            another.getId(), "Test Provided Product");
        providedProducts.add(providedProduct);

        Pool pool = TestUtil.createPool(owner, product, providedProducts, 5);
        providedProduct.setPool(pool);
        poolCurator.create(pool);
        pool = poolCurator.find(pool.getId());
        assertTrue(pool.getProvidedProducts().size() > 0);
    }

    // Note:  This simply tests that the multiplier is read and used in pool creation.
    //        All of the null/negative multiplier test cases are in ProductTest
    @Test
    public void testMultiplierCreation() {
        Product product = new Product("someProduct", "An Extremely Great Product", 10L);
        productCurator.create(product);

        Subscription sub = new Subscription(owner, product, new HashSet<Product>(), 16L,
            TestUtil.createDate(2006, 10, 21), TestUtil.createDate(2020, 1, 1), new Date());
        this.subCurator.create(sub);

        Pool newPool = poolManager.createPoolsForSubscription(sub).get(0);
        List<Pool> pools = poolCurator.lookupBySubscriptionId(sub.getId());

        assertEquals(160L, pools.get(0).getQuantity().longValue());
        assertEquals(newPool.getQuantity(), pools.get(0).getQuantity());
    }

    @Test
    public void testListBySourceEntitlement() {

        Pool sourcePool = TestUtil.createPool(owner, product);
        poolCurator.create(sourcePool);
        Entitlement e = new Entitlement(sourcePool, consumer, sourcePool.getStartDate(),
            sourcePool.getEndDate(), 1);
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
    public void testLoookupOverconsumedBySubscriptionId() {

        Pool pool = createPoolAndSub(owner, product, 1L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        String subid = pool.getSubscriptionId();
        assertEquals(1, poolCurator.lookupBySubscriptionId(subid).size());

        Entitlement e = new Entitlement(pool, consumer, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(e);

        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionId(
            subid, e).size());

        e = new Entitlement(pool, consumer, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(e);
        assertEquals(1, poolCurator.lookupOversubscribedBySubscriptionId(
            subid, e).size());
    }

    @Test
    public void testLoookupOverconsumedIgnoresOtherSourceEntitlementPools() {

        Pool pool = createPoolAndSub(owner, product, 1L,
            TestUtil.createDate(2011, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        String subid = pool.getSubscriptionId();

        Entitlement sourceEnt = new Entitlement(pool, consumer, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(sourceEnt);

        // Create derived pool referencing the entitlement just made:
        Pool derivedPool = new Pool(owner, product.getId(), product.getName(),
            new HashSet<ProvidedProduct>(), 1L, TestUtil.createDate(2011, 3, 2),
            TestUtil.createDate(2055, 3, 2),
            "", "", "");
        derivedPool.setSourceEntitlement(sourceEnt);
        derivedPool.setSubscriptionId(subid);
        poolCurator.create(derivedPool);

        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionId(
            subid, sourceEnt).size());

        // Oversubscribe to the derived pool:
        Entitlement derivedEnt = new Entitlement(derivedPool, consumer,
            derivedPool.getStartDate(), derivedPool.getEndDate(), 2);
        entitlementCurator.create(derivedEnt);

        // Passing the source entitlement should find the oversubscribed derived pool:
        assertEquals(1, poolCurator.lookupOversubscribedBySubscriptionId(
            subid, sourceEnt).size());

        // Passing the derived entitlement should not see any oversubscribed pool:
        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionId(
            subid, derivedEnt).size());
    }

    @Test
    public void testLoookupOverconsumedBySubscriptionIdIgnoresUnlimited() {

        Pool pool = createPoolAndSub(owner, product, -1L,
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        String subid = pool.getSubscriptionId();
        assertEquals(1, poolCurator.lookupBySubscriptionId(subid).size());


        Entitlement e = new Entitlement(pool, consumer, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(e);

        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionId(
            subid, e).size());

        e = new Entitlement(pool, consumer, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(e);
        assertEquals(0, poolCurator.lookupOversubscribedBySubscriptionId(
            subid, e).size());
    }

    @Test
    public void testListByActiveOnIncludesSameStartDay() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setStartDate(activeOn);
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, null,
            activeOn, false, false).size());
    }

    @Test
    public void testListByActiveOnIncludesSameEndDay() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setEndDate(activeOn);
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, null,
            activeOn, false, false).size());
    }

    @Test
    public void testListByActiveOnInTheMiddle() {
        Date activeOn = TestUtil.createDate(2011, 2, 2);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setStartDate(TestUtil.createDate(2011, 1, 2));
        pool.setEndDate(TestUtil.createDate(2011, 3, 2));
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAvailableEntitlementPools(null, owner, null,
            activeOn, false, false).size());
    }

    @Test
    public void testCorrectPagingWhenItemsAreFiltered() {
        for (int i = 0; i < 50; i++) {
            Pool pool = TestUtil.createPool(owner, product);
            pool.setStartDate(TestUtil.createDate(2011, 1, 2));
            pool.setEndDate(TestUtil.createDate(2011, 3, 2));
            poolCurator.create(pool);
        }

        for (int i = 0; i < 50; i++) {
            Product p = TestUtil.createProduct();
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
            null, owner, product.getId(), activeOn, false, false, req);
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
            null, owner, product.getId(), activeOn, false, false, req);
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
            null, owner, product.getId(), activeOn, false, false, req);
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
            null, owner, product.getId(), activeOn, false, false, req);
        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(1, page.getPageData().size());
    }

    @Test
    public void testCorrectPagingWhenResultsEmpty() {
        for (int i = 0; i < 5; i++) {
            Product p = TestUtil.createProduct();
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
            null, owner, product.getId(), activeOn, false, false, req);
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

    /* Works outside of the normal attribute usage. Ensures that all attributes
     *  for a pool are deleted even if they are not in the pools data structure
     *  in memory.
     */

    @Test
    public void testDuplicateAttributes() {
        Pool pool = TestUtil.createPool(owner, product);
        PoolAttribute pa = new PoolAttribute("name", "value");
        pool.addAttribute(pa);
        poolCurator.create(pool);
        PoolAttribute duplicatePa = new PoolAttribute("name", "value");
        duplicatePa.setPool(pool);
        poolAttributeCurator.create(duplicatePa);
        poolCurator.delete(pool);
        assertEquals(poolAttributeCurator.find(pa.getId()), null);
        assertEquals(poolAttributeCurator.find(duplicatePa.getId()), null);

        Product product = TestUtil.createProduct();
        productCurator.create(product);
        Pool pool2 = TestUtil.createPool(owner, product);
        ProductPoolAttribute ppa = new ProductPoolAttribute("name", "value",
            product.getId());
        pool2.addProductAttribute(ppa);
        poolCurator.create(pool2);
        ProductPoolAttribute duplicatePpa = new ProductPoolAttribute("name", "value",
            product.getId());
        duplicatePpa.setPool(pool2);
        productPoolAttributeCurator.create(duplicatePpa);
        poolCurator.delete(pool2);
        assertEquals(poolAttributeCurator.find(ppa.getId()), null);
        assertEquals(poolAttributeCurator.find(duplicatePpa.getId()), null);
    }

    @Test
    public void testExempt() {
        Product product1 = TestUtil.createProduct();
        product1.addAttribute(new ProductAttribute("support_level", "premium"));
        product1.addAttribute(new ProductAttribute("support_level_exempt", "true"));
        productCurator.create(product1);
        Product product2 = TestUtil.createProduct();
        product2.addAttribute(new ProductAttribute("support_level", "Premium"));
        productCurator.create(product2);
        Product product3 = TestUtil.createProduct();
        product3.addAttribute(new ProductAttribute("support_level", "super"));
        productCurator.create(product3);
        Product product4 = TestUtil.createProduct();
        product4.addAttribute(new ProductAttribute("support_level", "high"));
        product4.addAttribute(new ProductAttribute("support_level_exempt", "false"));
        productCurator.create(product4);
        Product product5 = TestUtil.createProduct();
        product5.addAttribute(new ProductAttribute("support_level", "HIGH"));
        productCurator.create(product5);

        Pool pool1 = createPoolAndSub(owner, product1, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool1);
        Pool pool2 = createPoolAndSub(owner, product2, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool2);
        Pool pool3 = createPoolAndSub(owner, product3, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool3);
        Pool pool4 = createPoolAndSub(owner, product4, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool4);
        Pool pool5 = createPoolAndSub(owner, product5, 100L,
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
        product1.addAttribute(new ProductAttribute("support_level", "premium"));
        productCurator.create(product1);
        Product product2 = TestUtil.createProduct();
        product2.addAttribute(new ProductAttribute("support_level", "Premium"));
        productCurator.create(product2);
        Product product3 = TestUtil.createProduct();
        product3.addAttribute(new ProductAttribute("support_level", "Premiums"));
        productCurator.create(product3);

        Pool pool1 = createPoolAndSub(owner, product1, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool1);
        Pool pool2 = createPoolAndSub(owner, product2, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool2);
        Pool pool3 = createPoolAndSub(owner, product3, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool3);

        Set<String> levels = poolCurator.retrieveServiceLevelsForOwner(owner, false);
        assertEquals(2, levels.size());
    }

}
