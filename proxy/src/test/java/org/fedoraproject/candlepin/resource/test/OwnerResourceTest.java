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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * OwnerResourceTest
 */
public class OwnerResourceTest extends DatabaseTestFixture {

    private static final String OWNER_NAME = "Jar Jar Binks";

    private OwnerResource ownerResource;
    private Owner owner;
    private Product product;

    @Before
    public void setUp() {
        this.ownerResource = injector.getInstance(OwnerResource.class);

        owner = new Owner(OWNER_NAME);
        ownerCurator.create(owner);
        product = TestUtil.createProduct();
        productCurator.create(product);
    }

    @Test
    public void testCreateOwner() {
        assertNotNull(owner);
        assertNotNull(ownerCurator.find(owner.getId()));
        assertTrue(owner.getEntitlementPools().size() == 0);
    }
    
    @Test    
    public void testSimpleDeleteOwner() {
        Long id = owner.getId();
        ownerResource.deleteOwner(
            id, 
            new UserPrincipal("someuser", owner, new LinkedList<Role>()));
        owner = ownerCurator.find(id);
        assertTrue(owner == null);
    }

    @Test
    public void testRefreshPoolsWithNewSubscriptions() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);

        Subscription sub = new Subscription(owner, prod.getId().toString(),
                new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                        .createDate(3000, 2, 9),
                        TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        // Trigger the refresh:
        ownerResource.refreshEntitlementPools(owner.getKey());
        List<Pool> pools = poolCurator
                .listByOwnerAndProduct(owner, prod);
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        assertEquals(sub.getId(), newPool.getSubscriptionId());
        assertEquals(sub.getQuantity(), newPool.getQuantity());
        assertEquals(sub.getStartDate(), newPool.getStartDate());
        assertEquals(sub.getEndDate(), newPool.getEndDate());
    }
    
    @Test
    public void testRefreshPoolsWithChangedSubscriptions() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPoolAndSub(createOwner(), prod.getId(),
            new Long(1000), TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));
        Owner owner = pool.getOwner();

        Subscription sub = new Subscription(owner, prod.getId().toString(),
                new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                        .createDate(3000, 2, 9),
                        TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);
        assertTrue(pool.getQuantity() < sub.getQuantity());
        assertTrue(pool.getStartDate() != sub.getStartDate());
        assertTrue(pool.getEndDate() != sub.getEndDate());

        pool.setSubscriptionId(sub.getId());
        poolCurator.merge(pool);

        ownerResource.refreshEntitlementPools(owner.getKey());

        pool = poolCurator.find(pool.getId());
        assertEquals(sub.getId(), pool.getSubscriptionId());
        assertEquals(sub.getQuantity(), pool.getQuantity());
        assertEquals(sub.getStartDate(), pool.getStartDate());
        assertEquals(sub.getEndDate(), pool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithRemovedSubscriptions() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);

        Subscription sub = new Subscription(owner, prod.getId().toString(),
                new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                        .createDate(3000, 2, 9),
                        TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        // Trigger the refresh:
        ownerResource.refreshEntitlementPools(owner.getKey());
        List<Pool> pools = poolCurator
                .listByOwnerAndProduct(owner, prod);
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        // Now delete the subscription:
        subCurator.delete(sub);

        // Trigger the refresh:
        ownerResource.refreshEntitlementPools(owner.getKey());
        assertEquals(1, pools.size());
        newPool = pools.get(0);
        assertFalse(newPool.isActive());
    }

    @Test
    public void testRefreshMultiplePools() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Product prod2 = TestUtil.createProduct();
        productCurator.create(prod2);
        
        Subscription sub = new Subscription(owner, prod.getId().toString(),
            new Long(2000), TestUtil.createDate(2010, 2, 9), TestUtil
                    .createDate(3000, 2, 9),
                    TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        Subscription sub2 = new Subscription(owner, prod2.getId().toString(),
                new Long(800), TestUtil.createDate(2010, 2, 9), TestUtil
                        .createDate(3000, 2, 9),
                        TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub2);

        // Trigger the refresh:
        ownerResource.refreshEntitlementPools(owner.getKey());
        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(2, pools.size());
    }

    public void testComplexDeleteOwner() throws Exception {

        // Create some consumers:
        Consumer c1 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c1.getType());
        consumerCurator.create(c1);
        Consumer c2 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c2.getType());
        consumerCurator.create(c2);

        // Create a pool for this owner:
        Pool pool = TestUtil.createEntitlementPool(product, owner);
        poolCurator.create(pool);

        // Give those consumers entitlements:
        entitler.entitleByPool(c1, pool);

        assertEquals(2, consumerCurator.listByOwner(owner).size());
        assertEquals(1, poolCurator.listByOwner(owner).size());
        assertEquals(1, entitlementCurator.listByOwner(owner).size());

        ownerResource.deleteOwner(owner.getId(), null);

        assertEquals(0, consumerCurator.listByOwner(owner).size());
        assertNull(consumerCurator.lookupByUuid(c1.getUuid()));
        assertNull(consumerCurator.lookupByUuid(c2.getUuid()));
        assertEquals(0, poolCurator.listByOwner(owner).size());
        assertEquals(0, entitlementCurator.listByOwner(owner).size());
    }

    @Test(expected = ForbiddenException.class)
    public void testConsumerRoleCannotGetOwner() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));
        
        securityInterceptor.enable();
        crudInterceptor.enable();


        ownerResource.getOwner(owner.getId());
    }

    @Test
    public void testOwnerAdminCanGetPools() {
        setupPrincipal(owner, Role.OWNER_ADMIN);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createEntitlementPool(owner, p);
        Pool pool2 = TestUtil.createEntitlementPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> pools = ownerResource.ownerEntitlementPools(owner.getId());
        assertEquals(2, pools.size());
    }

    @Test
    public void testOwnerAdminCannotAccessAnotherOwnersPools() {
        Owner evilOwner = new Owner("evilowner");
        ownerCurator.create(evilOwner);
        setupPrincipal(evilOwner, Role.OWNER_ADMIN);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createEntitlementPool(owner, p);
        Pool pool2 = TestUtil.createEntitlementPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        securityInterceptor.enable();
        crudInterceptor.enable();
        
        // Filtering should just cause this to return no results:
        List<Pool> pools = ownerResource.ownerEntitlementPools(owner.getId());
        assertEquals(0, pools.size());
    }

    @Test(expected = ForbiddenException.class)
    public void testOwnerAdminCannotListAllOwners() {
        setupPrincipal(owner, Role.OWNER_ADMIN);

        securityInterceptor.enable();
        crudInterceptor.enable();
        
        ownerResource.list();
    }

    @Test(expected = ForbiddenException.class)
    public void testOwnerAdminCannotDelete() {
        Principal principal = setupPrincipal(owner, Role.OWNER_ADMIN);

        securityInterceptor.enable();
        crudInterceptor.enable();
        
        ownerResource.deleteOwner(owner.getId(), principal);
    }
}
