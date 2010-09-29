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

import java.util.List;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.PoolResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;


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
    private static final String PRODUCT_CPULIMITED = "CPULIMITED001";
    private Consumer failConsumer;
    private Consumer passConsumer;
    private Consumer foreignConsumer;
    
    @Before
    public void setUp() {
        owner1 = createOwner();
        owner2 = createOwner();
        ownerCurator.create(owner1);
        ownerCurator.create(owner2);
        
        product1 = new Product(PRODUCT_CPULIMITED, PRODUCT_CPULIMITED);
        product2 = TestUtil.createProduct();
        productCurator.create(product1);
        productCurator.create(product2);
        
        
        pool1 = createPoolAndSub(owner1, product1, new Long(500),
             TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));
        pool2 = createPoolAndSub(owner1, product2, new Long(500),
             TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));
        pool3 = createPoolAndSub(owner2 , product1, new Long(500),
             TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));
        poolCurator.create(pool1);
        poolCurator.create(pool2);
        poolCurator.create(pool3);
        
        poolResource = injector.getInstance(PoolResource.class);
        
        // Consumer system with too many cpu cores:
        
        failConsumer = TestUtil.createConsumer(createOwner());
        failConsumer.setFact("cpu_cores", "4");
        consumerTypeCurator.create(failConsumer.getType());
        consumerCurator.create(failConsumer);

        // Consumer system with appropriate number of cpu cores:
        passConsumer = TestUtil.createConsumer(owner1);
        passConsumer.setFact("cpu_cores", "2");
        consumerTypeCurator.create(passConsumer.getType());
        consumerCurator.create(passConsumer);
        
        foreignConsumer = TestUtil.createConsumer(owner2);
        foreignConsumer.setFact("cpu_cores", "2");
        consumerTypeCurator.create(foreignConsumer.getType());
        consumerCurator.create(foreignConsumer);

        // Run these tests as an owner admin:
        setupPrincipal(owner1, Role.OWNER_ADMIN);
    }
    
    @Test
    public void testListAll() {
        List<Pool> pools = poolResource.list(null, null, null, false);
        assertEquals(3, pools.size());
    }
   
    @Test
    public void testListForOrg() {
        List<Pool> pools = poolResource.list(owner1.getId(), null, null, false);
        assertEquals(2, pools.size());
        setupPrincipal(owner2, Role.OWNER_ADMIN);
        pools = poolResource.list(owner2.getId(), null, null, false);
        assertEquals(1, pools.size());
    }

    @Ignore
    @Test
    public void testListForProduct() {
        List<Pool> pools = poolResource.list(null, null, product1.getId(), false);
        assertEquals(2, pools.size());
        pools = poolResource.list(null, null, product2.getId(), false);
        assertEquals(1, pools.size());
    }

    @Test
    public void testListForOrgAndProduct() {
        List<Pool> pools = poolResource.list(owner1.getId(), null, product1.getId(), false);
        assertEquals(1, pools.size());
        pools = poolResource.list(owner2.getId(), null, product2.getId(), false);
        assertEquals(0, pools.size());
    }

    @Test
    public void testListConsumerAndProductFiltering() {
        List<Pool> pools = poolResource.list(null, passConsumer.getUuid(), 
            product1.getId(), false);
        assertEquals(1, pools.size());
        pools = poolResource.list(null, failConsumer.getUuid(), 
            product1.getId(), false);
        assertEquals(0, pools.size());
    }
    
    // Filtering by both a consumer and an owner makes no sense (we should use the 
    // owner of that consumer), so make sure we error if someone tries.
    @Test(expected = BadRequestException.class)
    public void testListBlocksConsumerOwnerFiltering() {
        poolResource.list(owner1.getId(), passConsumer.getUuid(), 
            product1.getId(), false);
    }
    
    @Test
    public void testListConsumerFiltering() {
        setupPrincipal(new ConsumerPrincipal(passConsumer));
        List<Pool> pools = poolResource.list(null, passConsumer.getUuid(), null, false);
        assertEquals(2, pools.size());
    }
    
    @Test(expected = NotFoundException.class)
    public void testListNoSuchOwner() {
        poolResource.list("-1", null, null, false);
    }
    
    @Test(expected = NotFoundException.class)
    public void testListNoSuchConsumer() {
        poolResource.list(null, "blah", null, false);
    }
    
    @Test
    public void testListNoSuchProduct() {
        assertEquals(0, poolResource.list(owner1.getId(), null, "boogity", false).size());
    }
    
    @Test(expected = ForbiddenException.class)
    public void ownerAdminCannotCreatePoolsDirectly() {
        setupPrincipal(owner1, Role.OWNER_ADMIN);
        
        securityInterceptor.enable();
        crudInterceptor.enable();

        poolResource.createPool(
            TestUtil.createPool(owner1, TestUtil.createProduct()));
    }
    
    @Test
    public void ownerAdminCannotListAnotherOwnersPools() {
        List<Pool> pools = poolResource.list(owner1.getId(), null, null, false);
        assertEquals(2, pools.size());
        
        setupPrincipal(owner2, Role.OWNER_ADMIN);
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        pools = poolResource.list(owner1.getId(), null, null, false);
        assertEquals(0, pools.size());
    }


    @Test
    public void testConsumerCannotListPoolsForAnotherOwnersConsumer() {
        setupPrincipal(new ConsumerPrincipal(foreignConsumer));
        securityInterceptor.enable();
        crudInterceptor.enable();
        
        List<Pool> pools = poolResource.list(null, passConsumer.getUuid(), null, false);
        assertEquals(0, pools.size());
    }

    @Test
    public void testConsumerCannotListPoolsForAnotherOwner() {
        setupPrincipal(new ConsumerPrincipal(foreignConsumer));
        securityInterceptor.enable();
        crudInterceptor.enable();

        List<Pool> pools = poolResource.list(owner1.getId(), null, null, false);
        assertEquals(0, pools.size());
    }

}
