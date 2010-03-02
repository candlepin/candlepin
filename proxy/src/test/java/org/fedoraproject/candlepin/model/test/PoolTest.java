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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

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
        pool = TestUtil.createEntitlementPool(prod);
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
        Pool lookedUp = (Pool) entityManager().find(
                Pool.class, pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod.getId(), lookedUp.getProductId());
    }

    public void testMultiplePoolsForOwnerProductAllowed() {
        Pool duplicatePool = new Pool(owner,
                prod.getId(), new Long(-1), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        // Just need to see no exception is thrown.
        poolCurator.create(duplicatePool);
    }

    @Test
    public void testUnlimitedPool() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        Pool unlimitedPool = new Pool(owner, newProduct
                .getId(), new Long(-1), TestUtil.createDate(2009, 11, 30),
                TestUtil.createDate(2050, 11, 30));
        poolCurator.create(unlimitedPool);
        assertTrue(unlimitedPool.entitlementsAvailable());
    }

    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() {
        Long numAvailEntitlements = new Long(1);
        Product newProduct = TestUtil.createProduct();

        productCurator.create(newProduct);
        Pool consumerPool = new Pool(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        entitler.entitle(owner, consumer, newProduct);

        assertFalse(poolCurator.find(consumerPool.getId())
                .entitlementsAvailable());
    }

    @Test
    public void createEntitlementShouldUpdateConsumer() {
        Long numAvailEntitlements = new Long(1);

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = new Pool(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        assertEquals(0, consumer.getEntitlements().size());
        entitler.entitle(owner, consumer, newProduct);

        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(1, lookedUp.getConsumedProducts().size());
        // assertTrue(.getConsumedProducts().contains(
        // newProduct));
        assertEquals(1, consumerCurator.find(consumer.getId())
                .getEntitlements().size());
    }

    @Test
    public void concurrentCreationOfEntitlementsShouldWorkIfUnderMaxMemberLimit() {
        Long numAvailEntitlements = new Long(2);

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = new Pool(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        Entitler anotherEntitler = injector.getInstance(Entitler.class);

        entitler.entitle(owner, consumer, newProduct);
        anotherEntitler.entitle(owner, consumer, newProduct);

        assertFalse(poolCurator.find(consumerPool.getId())
                .entitlementsAvailable());
    }

    @Test
    public void concurrentCreationOfEntitlementsShouldFailIfOverMaxMemberLimit() {
        Long numAvailEntitlements = new Long(1);

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = new Pool(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        Entitler anotherEntitler = injector.getInstance(Entitler.class);

        Entitlement e1 = entitler.entitle(owner, consumer, newProduct);
        Entitlement e2 = anotherEntitler.entitle(owner, consumer, newProduct);
        assertNotNull(e1);
        assertNull(e2);
    }

    @Test
    public void testRefreshPoolsWithNewSubscriptions() {
        Product prod2 = TestUtil.createProduct();
        productCurator.create(prod2);

        Subscription sub = new Subscription(owner, prod2.getId().toString(),
                new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                        .createDate(3000, 2, 9),
                        TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        // Pool should get created just by doing this lookup:
        List<Pool> pools = poolCurator
                .listByOwnerAndProduct(owner, prod2);
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        assertEquals(sub.getId(), newPool.getSubscriptionId());
        assertEquals(sub.getQuantity(), newPool.getMaxMembers());
        assertEquals(sub.getStartDate(), newPool.getStartDate());
        assertEquals(sub.getEndDate(), newPool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithChangedSubscriptions() {
        Subscription sub = new Subscription(owner, prod.getId().toString(),
                new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                        .createDate(3000, 2, 9),
                        TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);
        assertTrue(pool.getMaxMembers() < sub.getQuantity());
        assertTrue(pool.getStartDate() != sub.getStartDate());
        assertTrue(pool.getEndDate() != sub.getEndDate());

        pool.setSubscriptionId(sub.getId());
        poolCurator.merge(pool);

        poolCurator.listByOwnerAndProduct(owner, prod);

        pool = poolCurator.find(pool.getId());
        assertEquals(sub.getId(), pool.getSubscriptionId());
        assertEquals(sub.getQuantity(), pool.getMaxMembers());
        assertEquals(sub.getStartDate(), pool.getStartDate());
        assertEquals(sub.getEndDate(), pool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithRemovedSubscriptions() {
        Product prod2 = TestUtil.createProduct();
        productCurator.create(prod2);

        Subscription sub = new Subscription(owner, prod2.getId().toString(),
                new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                        .createDate(3000, 2, 9),
                        TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        // Pool should get created just by doing this lookup:
        List<Pool> pools = poolCurator
                .listByOwnerAndProduct(owner, prod2);
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        // Now delete the subscription:
        subCurator.delete(sub);

        // Trigger the refresh:
        pools = poolCurator
                .listByOwnerAndProduct(owner, prod2);
        assertEquals(1, pools.size());
        newPool = pools.get(0);
        assertFalse(newPool.isActive());
    }

    @Test
    public void testSubscriptionIdUnique() {

    }

    // test subscription product changed exception
}
