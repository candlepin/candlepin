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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.model.Consumer;
import org.candlepin.model.DerivedProvidedProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.Subscription;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class PoolTest extends DatabaseTestFixture {

    private Pool pool;
    private Product prod1;
    private Product prod2;
    private Owner owner;
    private Consumer consumer;
    private Subscription subscription;

    @Before
    public void createObjects() {
        beginTransaction();

        prod1 = TestUtil.createProduct();
        prod2 = TestUtil.createProduct();
        productCurator.create(prod1);
        productCurator.create(prod2);
        owner = new Owner("testowner");
        ownerCurator.create(owner);

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct providedProduct = new ProvidedProduct(
            prod2.getId(), prod2.getName());
        providedProducts.add(providedProduct);

        pool = TestUtil.createPool(owner, prod1, providedProducts, 1000);
        subscription = TestUtil.createSubscription(owner, prod1);
        subAdapter.createSubscription(subscription);
        pool.setSourceSubscription(
            new SourceSubscription(subscription.getId(), "master"));
        providedProduct.setPool(pool);
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
        Pool lookedUp = entityManager().find(
                Pool.class, pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod1.getId(), lookedUp.getProductId());
        assertTrue(lookedUp.provides(prod1.getId()));
    }

    @Test
    public void testCreateWithDerivedProvidedProducts() {
        Product derivedProd = TestUtil.createProduct();
        productCurator.create(derivedProd);

        Pool p = TestUtil.createPool(owner, prod1, new HashSet<ProvidedProduct>(), 1000);
        p.addProvidedProduct(new ProvidedProduct(prod2.getId(), prod2.getName()));
        Set<DerivedProvidedProduct> derivedProducts = new HashSet<DerivedProvidedProduct>();
        derivedProducts.add(new DerivedProvidedProduct(derivedProd.getId(),
            derivedProd.getName(), p));

        p.setDerivedProvidedProducts(derivedProducts);
        poolCurator.create(p);

        Pool lookedUp = entityManager().find(Pool.class, p.getId());
        assertEquals(1, lookedUp.getProvidedProducts().size());
        assertEquals(prod2.getId(),
            lookedUp.getProvidedProducts().iterator().next().getProductId());
        assertEquals(1, lookedUp.getDerivedProvidedProducts().size());
        assertEquals(derivedProd.getId(),
            lookedUp.getDerivedProvidedProducts().iterator().next().getProductId());
    }

    public void testMultiplePoolsForOwnerProductAllowed() {
        Pool duplicatePool = createPoolAndSub(owner,
                prod1, -1L, TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        // Just need to see no exception is thrown.
        poolCurator.create(duplicatePool);
    }

    @Test
    public void testUnlimitedPool() {
        Product newProduct = TestUtil.createProduct();
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
        Product newProduct = TestUtil.createProduct();

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

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(owner, newProduct, numAvailEntitlements,
                TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        poolCurator.create(consumerPool);

        assertEquals(0, consumer.getEntitlements().size());
        poolManager.entitleByPool(consumer, consumerPool, 1);

        assertEquals(1, consumerCurator.find(consumer.getId())
                .getEntitlements().size());
    }

    // test subscription product changed exception

    @Test
    public void testLookupPoolsProvidingProduct() {
        Product parentProduct = TestUtil.createProduct("1", "product-1");
        Product childProduct = TestUtil.createProduct("2", "product-2");
        productCurator.create(childProduct);
        productCurator.create(parentProduct);

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct providedProduct = new ProvidedProduct(childProduct.getId(),
            childProduct.getName());
        providedProducts.add(providedProduct);

        Pool pool = TestUtil.createPool(owner, parentProduct, providedProducts, 5);
        providedProduct.setPool(pool);
        poolCurator.create(pool);


        List<Pool> results = poolCurator.listAvailableEntitlementPools(null, owner,
            childProduct.getId(), null, false);
        assertEquals(1, results.size());
        assertEquals(pool.getId(), results.get(0).getId());
    }

    /**
     * After creating a new pool object, test is made to determine whether
     * the created and updated values are present and not null.
     */
    @Test
    public void testCreationTimestamp() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        Pool pool = createPoolAndSub(owner, newProduct, 1L,
            TestUtil.createDate(2011, 3, 30),
            TestUtil.createDate(2022, 11, 29));
        poolCurator.create(pool);

        assertNotNull(pool.getCreated());
    }

    @Test
    public void testInitialUpdateTimestamp() {
        Product newProduct = TestUtil.createProduct();
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
        Product newProduct = TestUtil.createProduct();
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
        Product parentProduct = TestUtil.createProduct("1", "product-1");
        Product childProduct1 = TestUtil.createProduct("child1", "child1");
        Product childProduct2 = TestUtil.createProduct("child2", "child2");
        Product childProduct3 = TestUtil.createProduct("child3", "child3");
        productCurator.create(childProduct1);
        productCurator.create(childProduct2);
        productCurator.create(childProduct3);
        productCurator.create(parentProduct);

        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        ProvidedProduct providedProduct = new ProvidedProduct("child1",
            "child1", pool);
        providedProducts.add(providedProduct);

        Pool pool = TestUtil.createPool(owner, parentProduct, providedProducts, 5);
        poolCurator.create(pool);
        pool = poolCurator.find(pool.getId());
        assertEquals(1, pool.getProvidedProducts().size());

        // Clear the set and create a new one:
        pool.getProvidedProducts().clear();
        pool.addProvidedProduct(new ProvidedProduct("child2",
            "child2", pool));
        pool.addProvidedProduct(new ProvidedProduct("child3",
            "child3", pool));
        poolCurator.merge(pool);

        pool = poolCurator.find(pool.getId());
        assertEquals(2, pool.getProvidedProducts().size());
    }

    @Test
    public void nullAttributeValue() {
        ProductPoolAttribute ppa = new ProductPoolAttribute("Name", null, "Product");
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
        assertEquals(PoolType.NORMAL, pool.getType());

        pool.setAttribute("pool_derived", "true");
        assertEquals(PoolType.BONUS, pool.getType());

        pool.setSourceEntitlement(new Entitlement());
        assertEquals(PoolType.ENTITLEMENT_DERIVED, pool.getType());

        pool.setSourceEntitlement(null);
        pool.setSourceStack(new SourceStack(new Consumer(), "something"));
        assertEquals(PoolType.STACK_DERIVED, pool.getType());
    }
}
