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

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class EntitlementPoolTest extends DatabaseTestFixture {

    private EntitlementPool pool;
    private Product prod;
    private Owner owner;
    private Consumer consumer;

    @Before
    public void createObjects() {
        beginTransaction();

        pool = TestUtil.createEntitlementPool();
        owner = pool.getOwner();
        prod = pool.getProduct();
        consumer = TestUtil.createConsumer(owner);

        ownerCurator.create(owner);
        productCurator.create(prod);
        entitlementPoolCurator.create(pool);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        commitTransaction();
    }

    @Test
    public void testCreate() {
        EntitlementPool lookedUp = (EntitlementPool) entityManager().find(
                EntitlementPool.class, pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod.getId(), lookedUp.getProduct().getId());
    }

    @Test(expected = RuntimeException.class)
    public void testOwnerProductUniqueness() {
        EntitlementPool duplicatePool = new EntitlementPool(owner, prod,
                new Long(-1), TestUtil.createDate(2009, 11, 30), TestUtil
                        .createDate(2050, 11, 30));
        entitlementPoolCurator.create(duplicatePool);
    }


    @Test
    public void testUnlimitedPool() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        EntitlementPool unlimitedPool = new EntitlementPool(owner, newProduct,
                new Long(-1), TestUtil.createDate(2009, 11, 30), TestUtil
                        .createDate(2050, 11, 30));
        entitlementPoolCurator.create(unlimitedPool);
        assertTrue(entitlementPoolCurator.entitlementsAvailable(unlimitedPool));
    }

    @Test
    public void testConsumerSpecificPool() {
        EntitlementPool consumerPool = new EntitlementPool(owner, prod,
                new Long(-1), TestUtil.createDate(2009, 11, 30), TestUtil
                        .createDate(2050, 11, 30));
        consumerPool.setConsumer(consumer);
        entitlementPoolCurator.create(consumerPool);


        EntitlementPool lookedUp = entitlementPoolCurator.
            lookupByOwnerAndProduct(owner, consumer, prod);
        assertEquals(consumer.getId(), lookedUp.getConsumer().getId());
    }

    @Test(expected = RuntimeException.class)
    public void testDuplicateConsumerSpecificPool() {
        EntitlementPool consumerPool = new EntitlementPool(owner, prod,
                new Long(-1), TestUtil.createDate(2009, 11, 30), TestUtil
                        .createDate(2050, 11, 30));
        consumerPool.setConsumer(consumer);
        entitlementPoolCurator.create(consumerPool);

        EntitlementPool consumerPoolDupe = new EntitlementPool(owner, prod,
                new Long(-1), TestUtil.createDate(2009, 11, 30), TestUtil
                        .createDate(2050, 11, 30));
        consumerPool.setConsumer(consumer);
        entitlementPoolCurator.create(consumerPoolDupe);
    }

    @Test
    public void testConsumerSpecificPoolForNewProduct() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        EntitlementPool consumerPool = new EntitlementPool(owner, newProduct,
                new Long(-1), TestUtil.createDate(2009, 11, 30), TestUtil
                        .createDate(2050, 11, 30));
        consumerPool.setConsumer(consumer);
        entitlementPoolCurator.create(consumerPool);


        EntitlementPool lookedUp = entitlementPoolCurator.
            lookupByOwnerAndProduct(owner, consumer, newProduct);
        assertEquals(consumer.getId(), lookedUp.getConsumer().getId());
    }
}
