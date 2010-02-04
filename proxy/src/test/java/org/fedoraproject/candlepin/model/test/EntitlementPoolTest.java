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

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
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
    private Entitler entitler;

    @Before
    public void createObjects() {
        beginTransaction();

        pool = TestUtil.createEntitlementPool();
        owner = pool.getOwner();
        prod = pool.getProduct();
        consumer = TestUtil.createConsumer(owner);
        entitler = injector.getInstance(Entitler.class);

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
        assertTrue(unlimitedPool.entitlementsAvailable());
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
    
    @Test
    public void createEntitlementShouldIncreaseNumberOfMembers() {
        Long NUMBER_OF_ENTITLEMENTS_AVAILABLE = new Long(1);
        Product newProduct = TestUtil.createProduct();
        
        productCurator.create(newProduct);
        EntitlementPool consumerPool = new EntitlementPool(owner, newProduct,
                NUMBER_OF_ENTITLEMENTS_AVAILABLE, 
                TestUtil.createDate(2009, 11, 30), 
                TestUtil.createDate(2050, 11, 30)
        );
        consumerPool.setConsumer(consumer);
        consumerPool = entitlementPoolCurator.create(consumerPool);
        
        entitler.entitle(owner, consumer, newProduct);
        
        assertFalse(entitlementPoolCurator.find(consumerPool.getId()).entitlementsAvailable());
    }
    
    @Test
    public void createEntitlementShouldUpdateConsumer() {
        Long NUMBER_OF_ENTITLEMENTS_AVAILABLE = new Long(1);
        
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        
        EntitlementPool consumerPool = new EntitlementPool(owner, newProduct,
                NUMBER_OF_ENTITLEMENTS_AVAILABLE, 
                TestUtil.createDate(2009, 11, 30), 
                TestUtil.createDate(2050, 11, 30)
        );
        consumerPool.setConsumer(consumer);
        consumerPool = entitlementPoolCurator.create(consumerPool);
        
        assertEquals(0, consumer.getEntitlements().size());
        entitler.entitle(owner, consumer, newProduct);
        
        assertTrue(consumerCurator.find(consumer.getId()).getConsumedProducts().contains(newProduct));
        assertEquals(1, consumerCurator.find(consumer.getId()).getEntitlements().size());
    }
    
    @Test
    public void concurrentCreationOfEntitlementsShouldWorkIfUnderMaxMemberLimit() {
        Long NUMBER_OF_ENTITLEMENTS_AVAILABLE = new Long(2);
        
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        
        EntitlementPool consumerPool = new EntitlementPool(owner, newProduct,
                NUMBER_OF_ENTITLEMENTS_AVAILABLE, 
                TestUtil.createDate(2009, 11, 30), 
                TestUtil.createDate(2050, 11, 30)
        );
        consumerPool.setConsumer(consumer);
        consumerPool = entitlementPoolCurator.create(consumerPool);
        
        Entitler anotherEntitler = 
            injector.getInstance(Entitler.class);
        
        entitler.entitle(owner, consumer, newProduct);
        anotherEntitler.entitle(owner, consumer, newProduct);
        
        assertFalse(entitlementPoolCurator.find(consumerPool.getId()).entitlementsAvailable());
    }
    
    @Test
    public void concurrentCreationOfEntitlementsShouldFailIfOverMaxMemberLimit() {
        Long NUMBER_OF_ENTITLEMENTS_AVAILABLE = new Long(1);
        
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        
        EntitlementPool consumerPool = new EntitlementPool(owner, newProduct,
                NUMBER_OF_ENTITLEMENTS_AVAILABLE, 
                TestUtil.createDate(2009, 11, 30), 
                TestUtil.createDate(2050, 11, 30)
        );
        consumerPool.setConsumer(consumer);
        consumerPool = entitlementPoolCurator.create(consumerPool);
        
        Entitler anotherEntitler = 
            injector.getInstance(Entitler.class);
        
        
        Entitlement e1 = entitler.entitle(owner, consumer, newProduct);
        Entitlement e2 = anotherEntitler.entitle(owner, consumer, newProduct);
        assertNotNull(e1);
        assertNull(e2);
    }
}
