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

import java.util.List;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.PoolResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * PoolResourceTest
 */
public class PoolResourceTest extends DatabaseTestFixture {
    
    private Owner owner1;
    private Owner owner2;
    private Pool pool1;
    private Pool pool2;
    private Pool pool3;
    private Product product1;
    private Product product2;
    private PoolResource poolResource;
    
    @Before
    public void setUp() {
        owner1 = TestUtil.createOwner();
        owner2 = TestUtil.createOwner();
        ownerCurator.create(owner1);
        ownerCurator.create(owner2);
        
        product1 = TestUtil.createProduct();
        product2 = TestUtil.createProduct();
        productCurator.create(product1);
        productCurator.create(product2);
        
        
        pool1 = new Pool(owner1, product1.getId(), new Long(500), 
             TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));
        pool2 = new Pool(owner1, product2.getId(), new Long(500), 
             TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));
        pool3 = new Pool(owner2 , product1.getId(), new Long(500), 
             TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));
        poolCurator.create(pool1);
        poolCurator.create(pool2);
        poolCurator.create(pool3);
        
        poolResource = new PoolResource(poolCurator, consumerCurator, ownerCurator, 
            productAdapter);
    }
    
    @Test
    public void testLookupAll() {
        List<Pool> pools = poolResource.list(null, null, null);
        assertEquals(3, pools.size());
    }
   
    @Test
    public void testLookupForOrg() {
        List<Pool> pools = poolResource.list(owner1.getId(), null, null);
        assertEquals(2, pools.size());
        pools = poolResource.list(owner2.getId(), null, null);
        assertEquals(1, pools.size());
    }

    @Test
    public void testLookupForProduct() {
        List<Pool> pools = poolResource.list(null, null, product1.getId());
        assertEquals(2, pools.size());
        pools = poolResource.list(null, null, product2.getId());
        assertEquals(1, pools.size());
    }

    @Test
    public void testLookupForOrgAndProduct() {
        List<Pool> pools = poolResource.list(owner1.getId(), null, product1.getId());
        assertEquals(1, pools.size());
        pools = poolResource.list(owner2.getId(), null, product2.getId());
        assertEquals(0, pools.size());
    }

    // test consumer filtering
    
    // test query param data
}
