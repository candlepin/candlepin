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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class PoolTest extends DatabaseTestFixture {

    private Pool pool;
    private Product prod1;
    private Product prod2;
    private Owner owner;
    private Consumer consumer;

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
        providedProducts.add(new ProvidedProduct(prod2.getId(), prod2.getName()));
        
        pool = TestUtil.createPool(owner, prod1.getId(), providedProducts,
            1000);
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

    public void testMultiplePoolsForOwnerProductAllowed() {
        Pool duplicatePool = createPoolAndSub(owner,
                prod1, new Long(-1), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        // Just need to see no exception is thrown.
        poolCurator.create(duplicatePool);
    }

    @Test
    public void testUnlimitedPool() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        Pool unlimitedPool = createPoolAndSub(owner, newProduct,
                new Long(-1), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        poolCurator.create(unlimitedPool);
        assertTrue(unlimitedPool.entitlementsAvailable(new Integer(1)));
    }

    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() throws Exception {
        Long numAvailEntitlements = new Long(1);
        Product newProduct = TestUtil.createProduct();

        productCurator.create(newProduct);
        Pool consumerPool = createPoolAndSub(owner, newProduct, 
                numAvailEntitlements, TestUtil.createDate(2009, 11, 30), 
                TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        poolManager.entitleByProduct(consumer, newProduct.getId(), new Integer("1"));

        consumerPool = poolCurator.find(consumerPool.getId());
        assertFalse(consumerPool.entitlementsAvailable(new Integer(1)));
        assertEquals(1, consumerPool.getEntitlements().size());
    }

    @Test
    public void createEntitlementShouldUpdateConsumer() throws Exception {
        Long numAvailEntitlements = new Long(1);

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(owner, newProduct, numAvailEntitlements, 
                TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        assertEquals(0, consumer.getEntitlements().size());
        poolManager.entitleByProduct(consumer, newProduct.getId(), new Integer("1"));

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
        providedProducts.add(new ProvidedProduct(childProduct.getId(), 
            childProduct.getName()));

        Pool pool = TestUtil.createPool(owner, parentProduct.getId(),
            providedProducts, 5);
        poolCurator.create(pool);
        
        
        List<Pool> results = poolCurator.listAvailableEntitlementPools(null, owner, 
            childProduct.getId(), false, null);
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
    
}
