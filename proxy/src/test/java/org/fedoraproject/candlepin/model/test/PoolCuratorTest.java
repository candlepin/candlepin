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

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;


public class PoolCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Product product;
    private Consumer consumer;

    private static final String CPU_LIMITED_PRODUCT = "CPULIMITED001";

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
    public void testPoolNotYetActive() {
        Pool pool = createPoolAndSub(owner, product.getId(), new Long(100),
                TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer);
        assertEquals(0, results.size());

    }

    @Test
    public void testPoolExpired() {
        Pool pool = createPoolAndSub(owner, product.getId(), new Long(100),
                TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer);
        assertEquals(0, results.size());
    }

    @Test
    public void testLookupRuleFiltering() {

        Product p = new Product(CPU_LIMITED_PRODUCT, CPU_LIMITED_PRODUCT);
        productCurator.create(p);

        Pool pool = createPoolAndSub(owner, p.getId(), new Long(100),
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer);
        assertEquals(0, results.size());
    }
    
    @Test
    public void testFuzzyProductMatchingWithoutSubscription() {
        
        Product parent = TestUtil.createProduct();
        parent.addChildProduct(product);
        productCurator.create(parent);
        
        Pool p = new Pool(owner, parent.getId(), new Long(5), 
            TestUtil.createDate(2000, 3, 2), 
            TestUtil.createDate(2040, 3, 2));
        poolCurator.create(p);
        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, product);
        assertEquals(1, results.size());
        
    }
}
