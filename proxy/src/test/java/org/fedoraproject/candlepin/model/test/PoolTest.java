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
import static org.junit.Assert.fail;

import java.util.List;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
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

        entitler.entitle(consumer, newProduct);

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
        entitler.entitle(consumer, newProduct);

        assertEquals(1, consumerCurator.find(consumer.getId())
                .getEntitlements().size());
    }

    @Test
    public void concurrentCreationOfEntitlementsShouldWorkIfUnderMaxMemberLimit()
        throws Exception {
        Long numAvailEntitlements = new Long(2);

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        Entitler anotherEntitler = injector.getInstance(Entitler.class);

        entitler.entitle(consumer, newProduct);
        anotherEntitler.entitle(consumer, newProduct);

        assertFalse(poolCurator.find(consumerPool.getId())
                .entitlementsAvailable());
    }

    @Test
    public void concurrentCreationOfEntitlementsShouldFailIfOverMaxMemberLimit()
        throws Exception {
        Long numAvailEntitlements = new Long(1);

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(owner, newProduct
                .getId(), numAvailEntitlements, TestUtil
                .createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        Entitler anotherEntitler = injector.getInstance(Entitler.class);

        Entitlement e1 = entitler.entitle(consumer, newProduct);
        assertNotNull(e1);
        try {
            @SuppressWarnings("unused")
            Entitlement e2 = anotherEntitler.entitle(consumer, newProduct);
            fail();
        }
        catch (EntitlementRefusedException e) {
            // expected
        }
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
}
