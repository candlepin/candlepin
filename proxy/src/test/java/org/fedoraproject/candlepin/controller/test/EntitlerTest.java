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
package org.fedoraproject.candlepin.controller.test;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class EntitlerTest extends DatabaseTestFixture {
    
    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    
    private Owner owner;
    private Consumer consumer;
    private Entitler entitler;

    @Before
    public void setup() {
        // Create required products:
        
        virtHost = new Product(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST, 
                SpacewalkCertificateCurator.PRODUCT_VIRT_HOST);
        productCurator.create(virtHost);
        
        virtHostPlatform = new Product(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM, 
                SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM);
        productCurator.create(virtHostPlatform);
        
        virtGuest = new Product(SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST, 
                SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);
        productCurator.create(virtGuest);
        
        owner = TestUtil.createOwner();
        ownerCurator.create(owner);
        
        entitler = injector.getInstance(Entitler.class);

        EntitlementPool pool = new EntitlementPool(owner, virtHost, new Long(10), 
                TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2040, 11, 30));
        entitlementPoolCurator.create(pool);

        EntitlementPool pool2 = new EntitlementPool(owner, virtHostPlatform, new Long(10), 
                TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2040, 11, 30));
        entitlementPoolCurator.create(pool2);

        ConsumerType system = new ConsumerType("system");
        consumerTypeCurator.create(system);
        
        consumer = new Consumer("system", owner, system);
        consumer.getFacts().setFact("total_guests", "0");
        consumerCurator.create(consumer);
        
    }
    
    @Test
    public void testCreateVirtualizationHostConsumption() {
        entitler.createEntitlement(owner, consumer, virtHost);
        
        // Consuming a virt host entitlement should result in a pool just for us to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator.lookupByOwnerAndProduct(owner, 
                consumer, virtGuest);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(consumer.getId(), consumerPool.getConsumer().getId());
        assertEquals(new Long(5), consumerPool.getMaxMembers());
    }
    
    @Test
    public void testCreateVirtualizationHostPlatformConsumption() {
        entitler.createEntitlement(owner, consumer, virtHostPlatform);
        
        // Consuming a virt host entitlement should result in a pool just for us to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator.lookupByOwnerAndProduct(owner, 
                consumer, virtGuest);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(consumer.getId(), consumerPool.getConsumer().getId());
        assertTrue(consumerPool.getMaxMembers() < 0);
    }
}
