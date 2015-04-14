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

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;



public class PoolTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private SubscriptionServiceAdapter subAdapter;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private CandlepinPoolManager poolManager;

    private Pool pool;
    private Product prod1;
    private Product prod2;
    private Owner owner;
    private Consumer consumer;
    private Subscription subscription;

    @Before
    public void createObjects() {
        beginTransaction();

        owner = new Owner("testowner");
        ownerCurator.create(owner);

        prod1 = TestUtil.createProduct(owner);
        prod2 = TestUtil.createProduct(owner);
        productCurator.create(prod1);
        productCurator.create(prod2);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(prod2);

        pool = TestUtil.createPool(owner, prod1, providedProducts, 1000);
        subscription = TestUtil.createSubscription(owner, prod1);
        subAdapter.createSubscription(subscription);
        pool.setSourceSubscription(new SourceSubscription(subscription.getId(), "master"));
        poolCurator.create(pool);
        owner = pool.getOwner();

        consumer = TestUtil.createConsumer(owner);

        productCurator.create(prod1);
        poolCurator.create(pool);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        commitTransaction();
    }

    @Test
    public void testCreate() {
        Pool lookedUp = entityManager().find(Pool.class, pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod1.getId(), lookedUp.getProductId());
        assertTrue(lookedUp.provides(prod1.getId()));
    }

    @Test
    public void testCreateWithDerivedProvidedProducts() {
        Product derivedProd = TestUtil.createProduct(owner);
        productCurator.create(derivedProd);

        Pool p = TestUtil.createPool(owner, prod1, new HashSet<Product>(), 1000);
        p.addProvidedProduct(prod2);
        Set<Product> derivedProducts = new HashSet<Product>();
        derivedProducts.add(derivedProd);

        p.setDerivedProvidedProducts(derivedProducts);
        poolCurator.create(p);

        Pool lookedUp = entityManager().find(Pool.class, p.getId());
        assertEquals(1, lookedUp.getProvidedProducts().size());
        assertEquals(prod2.getId(),
            lookedUp.getProvidedProducts().iterator().next().getId());
        assertEquals(1, lookedUp.getDerivedProvidedProducts().size());
        assertEquals(derivedProd.getId(),
            lookedUp.getDerivedProvidedProducts().iterator().next().getId());
    }

    @Test
    public void testMultiplePoolsForOwnerProductAllowed() {
        Pool duplicatePool = createPoolAndSub(owner,
                prod1, -1L, TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        // Just need to see no exception is thrown.
        poolCurator.create(duplicatePool);
    }

    @Test
    public void testIsOverflowing() {
        Pool duplicatePool = createPoolAndSub(owner,
                prod1, -1L, TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        assertFalse(duplicatePool.isOverflowing());
    }

    @Test
    public void testUnlimitedPool() {
        Product newProduct = TestUtil.createProduct(owner);
        productCurator.create(newProduct);
        Pool unlimitedPool = createPoolAndSub(owner, newProduct,
                -1L, TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        poolCurator.create(unlimitedPool);
        assertTrue(unlimitedPool.entitlementsAvailable(1));
    }

    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() throws Exception {
        Long numAvailEntitlements = 1L;
        Product newProduct = TestUtil.createProduct(owner);

        productCurator.create(newProduct);
        Pool consumerPool = createPoolAndSub(owner, newProduct,
                numAvailEntitlements, TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        poolManager.entitleByPool(consumer, consumerPool, 1);

        consumerPool = poolCurator.find(consumerPool.getId());
        assertFalse(consumerPool.entitlementsAvailable(1));
        assertEquals(1, consumerPool.getEntitlements().size());
    }

    @Test
    public void createEntitlementShouldUpdateConsumer() throws Exception {
        Long numAvailEntitlements = 1L;

        Product newProduct = TestUtil.createProduct(owner);
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(
            owner,
            newProduct,
            numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2050, 11, 30)
        );

        poolCurator.create(consumerPool);

        assertEquals(0, consumer.getEntitlements().size());
        poolManager.entitleByPool(consumer, consumerPool, 1);

        assertEquals(1, consumerCurator.find(consumer.getId()).getEntitlements().size());
    }

    // test subscription product changed exception

    @Test
    public void testLookupPoolsProvidingProduct() {
        Product parentProduct = TestUtil.createProduct("1", "product-1", owner);
        Product childProduct = TestUtil.createProduct("2", "product-2", owner);
        productCurator.create(childProduct);
        productCurator.create(parentProduct);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(childProduct);

        Pool pool = TestUtil.createPool(owner, parentProduct, providedProducts, 5);
        poolCurator.create(pool);


        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            null, owner, childProduct.getId(), null, false
        );
        assertEquals(1, results.size());
        assertEquals(pool.getId(), results.get(0).getId());
    }

    /**
     * After creating a new pool object, test is made to determine whether
     * the created and updated values are present and not null.
     */
    @Test
    public void testCreationTimestamp() {
        Product newProduct = TestUtil.createProduct(owner);
        productCurator.create(newProduct);
        Pool pool = createPoolAndSub(owner, newProduct, 1L,
            TestUtil.createDate(2011, 3, 30),
            TestUtil.createDate(2022, 11, 29));
        poolCurator.create(pool);

        assertNotNull(pool.getCreated());
    }

    @Test
    public void testInitialUpdateTimestamp() {
        Product newProduct = TestUtil.createProduct(owner);
        productCurator.create(newProduct);
        Pool pool = createPoolAndSub(owner, newProduct, 1L,
            TestUtil.createDate(2011, 3, 30),
            TestUtil.createDate(2022, 11, 29));
        pool = poolCurator.create(pool);

        assertNotNull(pool.getUpdated());
    }

    /**
     * After updating an existing pool object, test is made to determine whether
     * the updated value has changed
     */
    @Test
    public void testSubsequentUpdateTimestamp() {
        Product newProduct = TestUtil.createProduct(owner);
        productCurator.create(newProduct);
        Pool pool = createPoolAndSub(owner, newProduct, 1L,
            TestUtil.createDate(2011, 3, 30),
            TestUtil.createDate(2022, 11, 29));

        pool = poolCurator.create(pool);

        // set updated to 10 minutes ago
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        pool.setUpdated(calendar.getTime());

        Date updated = (Date) pool.getUpdated().clone();
        pool.setQuantity(23L);
        pool = poolCurator.merge(pool);

        assertFalse(updated.getTime() == pool.getUpdated().getTime());
    }

    @Test
    public void providedProductCleanup() {
        Product parentProduct = TestUtil.createProduct("1", "product-1", owner);
        Product childProduct1 = TestUtil.createProduct("child1", "child1", owner);
        Product childProduct2 = TestUtil.createProduct("child2", "child2", owner);
        Product childProduct3 = TestUtil.createProduct("child3", "child3", owner);
        Product providedProduct = TestUtil.createProduct("provided", "Child 1", owner);
        productCurator.create(providedProduct);
        productCurator.create(childProduct1);
        productCurator.create(childProduct2);
        productCurator.create(childProduct3);
        productCurator.create(parentProduct);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(providedProduct);

        Pool pool = TestUtil.createPool(owner, parentProduct, providedProducts, 5);
        poolCurator.create(pool);
        pool = poolCurator.find(pool.getId());
        assertEquals(1, pool.getProvidedProducts().size());

        // Clear the set and create a new one:
        pool.getProvidedProducts().clear();
        pool.addProvidedProduct(childProduct2);
        pool.addProvidedProduct(childProduct3);
        poolCurator.merge(pool);

        pool = poolCurator.find(pool.getId());
        assertEquals(2, pool.getProvidedProducts().size());
    }

    @Test
    public void nullAttributeValue() {
        ProductAttribute ppa = new ProductAttribute("Name", null);
        PoolAttribute pa = new PoolAttribute("Name", null);
        ppa.toString();
        pa.toString();
        ppa.hashCode();
        pa.hashCode();
    }

    // sunny test - real rules not invoked here. Can only be sure the counts are recorded.
    // Rule tests already exist for quantity filter.
    // Will use spec tests to see if quantity rules are followed in this scenario.
    @Test
    public void testEntitlementQuantityChange() throws EntitlementRefusedException {
        Entitlement ent = poolManager.entitleByPool(consumer, pool, 3);
        assertTrue(ent.getQuantity() == 3);
        poolManager.adjustEntitlementQuantity(consumer, ent, 5);
        Entitlement ent2 = entitlementCurator.find(ent.getId());
        assertTrue(ent2.getQuantity() == 5);
        Pool pool2 = poolCurator.find(pool.getId());
        assertTrue(pool2.getConsumed() == 5);
        assertTrue(pool2.getEntitlements().size() == 1);
    }

    @Test
    public void testPoolType() {
        pool.setAttribute(Pool.DERIVED_POOL_ATTRIBUTE, "true");
        assertEquals(PoolType.BONUS, pool.getType());

        pool.setSourceEntitlement(new Entitlement());
        assertEquals(PoolType.ENTITLEMENT_DERIVED, pool.getType());

        pool.setSourceEntitlement(null);
        pool.setSourceStack(new SourceStack(new Consumer(), "something"));
        assertEquals(PoolType.STACK_DERIVED, pool.getType());

        pool.setAttribute(Pool.UNMAPPED_GUESTS_ATTRIBUTE, "true");
        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());

        pool.setSourceEntitlement(new Entitlement());
        pool.setSourceStack(null);
        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());

        pool.removeAttribute(Pool.DERIVED_POOL_ATTRIBUTE);
        assertEquals(PoolType.NORMAL, pool.getType());

        pool.setSourceEntitlement(null);
        assertEquals(PoolType.NORMAL, pool.getType());

        pool.setSourceStack(new SourceStack(new Consumer(), "something"));
        assertEquals(PoolType.NORMAL, pool.getType());
    }

    @Test
    public void testSetSubIdFromValue() {
        pool.setSubscriptionId("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionId());
        // subkey should be unchanged
        assertEquals("master", pool.getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testSetSubIdFromNull() {
        pool.setSourceSubscription(null);
        pool.setSubscriptionId("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionId());
        // subkey should be null
        assertNull(pool.getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testSetSubIdNullRemoval() {
        pool.getSourceSubscription().setSubscriptionSubKey(null);
        pool.setSubscriptionId(null);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubIdNullEmptyString() {
        pool.getSourceSubscription().setSubscriptionSubKey(null);
        pool.setSubscriptionId("");
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubKeyFromValue() {
        pool.setSubscriptionSubKey("testkey");
        assertEquals("testkey", pool.getSourceSubscription().getSubscriptionSubKey());
        // subkey should be unchanged
        assertEquals(subscription.getId(), pool.getSourceSubscription().getSubscriptionId());
    }

    @Test
    public void testSetSubKeyFromNull() {
        pool.setSourceSubscription(null);
        pool.setSubscriptionSubKey("testid");
        assertEquals("testid", pool.getSourceSubscription().getSubscriptionSubKey());
        // subkey should be null
        assertNull(pool.getSourceSubscription().getSubscriptionId());
    }

    @Test
    public void testSetSubKeyNullRemoval() {
        pool.getSourceSubscription().setSubscriptionId(null);
        pool.setSubscriptionSubKey(null);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSubKeyNullEmptyString() {
        pool.getSourceSubscription().setSubscriptionId(null);
        pool.setSubscriptionSubKey("");
        assertNull(pool.getSourceSubscription());
    }
}
