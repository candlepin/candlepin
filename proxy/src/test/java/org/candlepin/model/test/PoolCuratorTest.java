/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

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
}
