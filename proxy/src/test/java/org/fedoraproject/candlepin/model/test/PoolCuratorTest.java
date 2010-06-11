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
        Pool pool = createPoolAndSub(owner, product, new Long(100),
                TestUtil.createDate(2050, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listByConsumer(consumer);
        assertEquals(0, results.size());

    }

    @Test
    public void testPoolExpired() {
        Pool pool = createPoolAndSub(owner, product, new Long(100),
                TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2005, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listByConsumer(consumer);
        assertEquals(0, results.size());
    }

    @Test
    public void testProductName() {
        Product p = new Product("someProduct", "An Extremely Great Product");
        productCurator.create(p);
        
        Pool pool = createPoolAndSub(owner, p, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);
        
        List<Pool> results = poolCurator.listByOwnerAndProduct(owner, p);
        Pool onlyPool = results.get(0);
        
        assertEquals("An Extremely Great Product", onlyPool.getProductName());
    }
    
    @Test
    public void testProductNameViaFind() {
        Product p = new Product("another", "A Great Operating System");
        productCurator.create(p);
        
        Pool pool = createPoolAndSub(owner, p, 25L,
            TestUtil.createDate(1999, 1, 10), TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);
        pool = poolCurator.find(pool.getId());
        
        assertEquals("A Great Operating System", pool.getProductName());
    }
    
    @Test
    public void testProductNameViaFindAll() {
        Product p = new Product("another", "A Great Operating System");
        productCurator.create(p);
        
        Pool pool = createPoolAndSub(owner, p, 25L,
            TestUtil.createDate(1999, 1, 10), TestUtil.createDate(2099, 1, 9));
        poolCurator.create(pool);
        pool = poolCurator.listAll().get(0);
        
        assertEquals("A Great Operating System", pool.getProductName());
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
