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

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * PoolCuratorAccessControlTest
 */
public class PoolCuratorAccessControlTest extends DatabaseTestFixture {

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
        consumer.setMetadataField("cpu_cores", "4");
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);
    }

    @Test
    public void shouldReturnPoolsBelongingToTheOwnerWhenFilterIsEnabled() {
        Pool pool = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        
        Owner anotherOwner = createOwner();
        ownerCurator.create(anotherOwner);
        
        Product anotherProduct = new Product("another_product", "another product");
        productCurator.create(anotherProduct);
        
        Pool p = TestUtil.createPool(anotherOwner, anotherProduct);
        poolCurator.create(p);

        assertEquals(2, poolCurator.listAll().size());
        
        poolCurator.enableFilter("Pool_OWNER_FILTER", "owner_id", anotherOwner.getId());
        assertEquals(1, poolCurator.listAll().size());
    }
    
    @Test
    public void shouldReturnPoolsBelongingToConsumersOwnerWhenFilterIsEnabled() {
        Pool pool = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        
        Owner anotherOwner = createOwner();
        ownerCurator.create(anotherOwner);
        
        Product anotherProduct = new Product("another_product", "another product");
        productCurator.create(anotherProduct);
        
        Pool p = TestUtil.createPool(anotherOwner, anotherProduct);
        poolCurator.create(p);

        assertEquals(2, poolCurator.listAll().size());
        
        Consumer anotherConsumer = TestUtil.createConsumer(anotherOwner);
        consumerTypeCurator.create(anotherConsumer.getType());
        consumerCurator.create(anotherConsumer);
        
        poolCurator.enableFilter("Pool_CONSUMER_FILTER", "consumer_id", 
            anotherConsumer.getId());
        assertEquals(1, poolCurator.listAll().size());
    }
    
    @Test
    public void shouldReturnUserRestrictedPoolForTheMatchingUser() {
        Pool p = TestUtil.createPool(owner, product);
        p.setRestrictedToUsername("username");
        poolCurator.create(p);
        
        assertEquals(1, poolCurator.listAll().size());
        
        Consumer anotherConsumer = TestUtil.createConsumer(owner);
        anotherConsumer.setUserName("username");
        consumerTypeCurator.create(anotherConsumer.getType());
        consumerCurator.create(anotherConsumer);
        
        poolCurator.enableFilter("Pool_CONSUMER_FILTER", "consumer_id", 
            anotherConsumer.getId());
        
        assertEquals(1, poolCurator.listAll().size());
    }
    
    @Test
    public void shouldNotReturnUserRestrictedPoolForNonMatchingUser() {
        Pool p = TestUtil.createPool(owner, product);
        p.setRestrictedToUsername("username");
        poolCurator.create(p);
        
        assertEquals(1, poolCurator.listAll().size());
        
        Consumer anotherConsumer = TestUtil.createConsumer(owner);
        anotherConsumer.setUserName("anotherusername");
        consumerTypeCurator.create(anotherConsumer.getType());
        consumerCurator.create(anotherConsumer);
        
        poolCurator.enableFilter("Pool_CONSUMER_FILTER", "consumer_id", 
            anotherConsumer.getId());
        
        assertEquals(0, poolCurator.listAll().size());
        
    }
    
    @Test(expected = ForbiddenException.class)
    public void ownerAdminCannotDeleteAnotherOwnersPools() {
        Pool pool = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        Pool pool2 = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool2);
        
        assertEquals(2, poolCurator.listAll().size());
        
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);
        
        setupPrincipal(owner2, Role.OWNER_ADMIN);
        crudInterceptor.enable();
        
        poolCurator.delete(pool);
    }
    
    @Test
    public void ownerAdminCanDeletePools() {
        Pool pool = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        Pool pool2 = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool2);
        
        assertEquals(2, poolCurator.listAll().size());
        
        setupPrincipal(owner, Role.OWNER_ADMIN);
        crudInterceptor.enable();
        
        poolCurator.delete(pool);
        assertEquals(1, poolCurator.listAll().size());
    }
    
    @Test(expected = ForbiddenException.class)
    public void ownerAdminCannotUpdateAnotherOwnersPools() {
        Pool pool = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        assertEquals(1, poolCurator.listAll().size());
        
        pool.setConsumed(10L);
        
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);
        setupPrincipal(owner2, Role.OWNER_ADMIN);
        crudInterceptor.enable();

        poolCurator.merge(pool);
    }

    @Test
    public void ownerAdminCanUpdatePools() {
        Pool pool = createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        
        assertEquals(1, poolCurator.listAll().size());
        pool.setConsumed(10L);
        
        setupPrincipal(owner, Role.OWNER_ADMIN);
        crudInterceptor.enable();
        
        poolCurator.merge(pool);
        
        Pool retirevedPool = poolCurator.find(pool.getId());
        assertEquals(new Long(10), retirevedPool.getConsumed());
    }
    
    @Test(expected = ForbiddenException.class)
    public void ownerAdminCannotCreateAnotherOwnersPools() {
        Owner owner2 = createOwner();
        setupPrincipal(owner2, Role.OWNER_ADMIN);
        crudInterceptor.enable();

        createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
    }
    
    @Test
    public void ownerAdminCanCreatePools() {
        setupPrincipal(owner, Role.OWNER_ADMIN);
        crudInterceptor.enable();
        
        createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        
        assertEquals(1, poolCurator.listAll().size());
    }
    
    @Test
    public void consumerCanSeePoolsOfItsOwner() {
        createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2009, 3, 2), TestUtil.createDate(2055, 3, 2));
        
        setupPrincipal(new ConsumerPrincipal(consumer));
        crudInterceptor.enable();
        
        assertEquals(1,
            poolCurator.listAvailableEntitlementPools(null, owner, null, false).size());
    }
    
    @Test
    public void consumerCannotSeePoolsOfOtherOwners() {
        createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2009, 3, 2), TestUtil.createDate(2055, 3, 2));
        
        setupPrincipal(new ConsumerPrincipal(createConsumer(createOwner())));
        crudInterceptor.enable();
        
        assertEquals(0,
            poolCurator.listAvailableEntitlementPools(null, owner, null, false).size());
    }
    
    @Test
    public void ownerCanSeeOwnPools() {
        createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2009, 3, 2), TestUtil.createDate(2055, 3, 2));
        
        setupPrincipal(owner, Role.OWNER_ADMIN);
        crudInterceptor.enable();
        
        assertEquals(1,
            poolCurator.listAvailableEntitlementPools(null, owner, null, false).size());
    }
    
    @Test
    public void ownerCannotSeeOtherOwnersPools() {
        createPoolAndSub(owner, product, new Long(100),
            TestUtil.createDate(2009, 3, 2), TestUtil.createDate(2055, 3, 2));
        
        setupPrincipal(createOwner(), Role.OWNER_ADMIN);
        crudInterceptor.enable();
        
        assertEquals(0,
            poolCurator.listAvailableEntitlementPools(null, owner, null, false).size());
    }
}
