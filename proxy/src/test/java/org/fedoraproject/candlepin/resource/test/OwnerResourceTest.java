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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

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

    @Before
    public void setUp() {
        ownerResource = new OwnerResource(ownerCurator, poolCurator, i18n);
    }

    @Test
    public void testCreateOwner() {
        Owner toSubmit = new Owner(OWNER_NAME);

        Owner submitted = ownerResource.createOwner(toSubmit);

        assertNotNull(submitted);
        assertNotNull(ownerCurator.find(submitted.getId()));
        assertTrue(submitted.getEntitlementPools().size() == 0);
    }
    
    @Test    
    public void testSimpleDeleteOwner() {
        Owner owner = new Owner(OWNER_NAME);
        ownerCurator.create(owner);
        assertNotNull(owner.getId());
        Long id = owner.getId();
        ownerResource.deleteOwner(id);
        owner = ownerCurator.find(id);
        assertTrue(owner == null);
    }

    @Test
    public void testRefreshPoolsWithNewSubscriptions() {
        Owner owner = new Owner(OWNER_NAME);
        ownerCurator.create(owner);
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
        Owner owner = new Owner(OWNER_NAME);
        ownerCurator.create(owner);
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
        Owner owner = new Owner(OWNER_NAME);
        ownerCurator.create(owner);
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
}
