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

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class PoolTest extends DatabaseTestFixture {

    private Pool pool;
    private Product prod;
    private Owner owner;
    private Consumer consumer;
    private Entitler entitler;

    @Before
    public void createObjects() {
        beginTransaction();

        prod = TestUtil.createProduct();
        productCurator.create(prod);
        pool = createPoolAndSub(createOwner(), prod.getId(),
            new Long(1000), TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));
        owner = pool.getOwner();

        consumer = TestUtil.createConsumer(owner);
        entitler = injector.getInstance(Entitler.class);

        ownerCurator.create(owner);
        productCurator.create(prod);
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
        assertEquals(prod.getId(), lookedUp.getProductId());
    }

    public void testMultiplePoolsForOwnerProductAllowed() {
        Pool duplicatePool = createPoolAndSub(owner,
                prod.getId(), new Long(-1), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        // Just need to see no exception is thrown.
        poolCurator.create(duplicatePool);
    }

    @Test
    public void testUnlimitedPool() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        Pool unlimitedPool = createPoolAndSub(owner, newProduct
                .getId(), new Long(-1), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        poolCurator.create(unlimitedPool);
        assertTrue(unlimitedPool.entitlementsAvailable());
    }

    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() throws Exception {
        Long numAvailEntitlements = new Long(1);
        Product newProduct = TestUtil.createProduct();

        productCurator.create(newProduct);
        Pool consumerPool = createPoolAndSub(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        entitler.entitle(consumer, newProduct, new Integer("1"));

        assertFalse(poolCurator.find(consumerPool.getId())
                .entitlementsAvailable());
    }

    @Test
    public void createEntitlementShouldUpdateConsumer() throws Exception {
        Long numAvailEntitlements = new Long(1);

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        assertEquals(0, consumer.getEntitlements().size());
        entitler.entitle(consumer, newProduct, new Integer("1"));

        assertEquals(1, consumerCurator.find(consumer.getId())
                .getEntitlements().size());
    }

    // test subscription product changed exception

    @Test
    public void testLookupPoolsProvidingProduct() {
        Product parentProduct = TestUtil.createProduct();
        Product childProduct = TestUtil.createProduct();
        parentProduct.addChildProduct(childProduct);
        productCurator.create(childProduct);
        productCurator.create(parentProduct);
        
        Pool pool = new Pool(owner, parentProduct.getId().toString(),
            new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                    .createDate(3000, 2, 9));
        poolCurator.create(pool);
        
        
        List<Pool> results = poolCurator.listAvailableEntitlementPools(null, owner, 
            childProduct, false);
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
        Pool pool = createPoolAndSub(owner, newProduct.getId(), 1L,
            TestUtil.createDate(2011, 3, 30),
            TestUtil.createDate(2022, 11, 29));
        poolCurator.create(pool);
        
        assertNotNull(pool.getCreated());
    }
    
    @Test
    public void testInitialUpdateTimestamp() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        Pool pool = createPoolAndSub(owner, newProduct.getId(), 1L,
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
        Pool pool = createPoolAndSub(owner, newProduct.getId(), 1L,
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
