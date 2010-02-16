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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.test.SpacewalkCertificateCuratorTest;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;

import com.redhat.rhn.common.cert.CertificateFactory;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class EntitlerTest extends DatabaseTestFixture {
    
    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    private Product monitoring;
    private Product provisioning;
    
    private ConsumerType guestType;
    
    private Owner o;
    private Consumer parentSystem;
    private Consumer childVirtSystem;
    private Entitler entitler;

    @Before
    public void setUp() throws Exception {
        o = TestUtil.createOwner();
        ownerCurator.create(o);
        
        String certString = SpacewalkCertificateCuratorTest.readCertificate(
                "/certs/spacewalk-with-channel-families.cert");
        spacewalkCertCurator.parseCertificate(CertificateFactory.read(certString), o);

        List<EntitlementPool> pools = entitlementPoolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        virtHost = productCurator
                .lookupByLabel(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST);
        assertNotNull(virtHost);
        
        virtHostPlatform = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM);
        
        virtGuest = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);
        
        monitoring = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_MONITORING);
        
        provisioning = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_PROVISIONING);
        
        entitler = injector.getInstance(Entitler.class);

        ConsumerType system = new ConsumerType(ConsumerType.SYSTEM);
        consumerTypeCurator.create(system);
        
        guestType = new ConsumerType(ConsumerType.VIRT_SYSTEM);
        consumerTypeCurator.create(guestType);
        
        parentSystem = new Consumer("system", o, system);
        parentSystem.getFacts().setFact("total_guests", "0");
        consumerCurator.create(parentSystem);
        
        childVirtSystem = new Consumer("virt system", o, guestType);
        parentSystem.addChildConsumer(childVirtSystem);
        
        consumerCurator.create(childVirtSystem);
    }
    
    @Test
    public void testGuestTypeCreated() {
        // This guest product type should have been created just by parsing a sat cert
        // with virt entitlements:
        assertNotNull(virtGuest);
    }

    @Test
    public void testEntitlementPoolsCreated() {
        List<EntitlementPool> pools = entitlementPoolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        EntitlementPool virtHostPool = entitlementPoolCurator.listByOwnerAndProduct(o,
                null, virtHost).get(0);
        assertNotNull(virtHostPool);
    }

    @Test
    public void testVirtEntitleFailsIfAlreadyHasGuests() {
        parentSystem.getFacts().setFact("total_guests", "10");
        consumerCurator.update(parentSystem);
        Entitlement e = entitler.entitle(o, parentSystem, virtHost);
        assertNull(e);
        
        e = entitler.entitle(o, parentSystem, virtHostPlatform);
        assertNull(e);
    }
    
    @Test
    public void testVirtEntitleFailsForVirtSystem() {
        parentSystem.setType(guestType);
        consumerCurator.update(parentSystem);
        Entitlement e = entitler.entitle(o, parentSystem, virtHost);
        assertNull(e);
        
        e = entitler.entitle(o, parentSystem, virtHostPlatform);
        assertNull(e);
    }
    
    @Test
    public void testVirtualizationHostConsumption() {
        Entitlement e = entitler.entitle(o, parentSystem, virtHost);

        // Consuming a virt host entitlement should result in a pool just for us to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator.listByOwnerAndProduct(o,
                parentSystem, virtGuest).get(0);
        assertNotNull(consumerPool);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(parentSystem.getId(), consumerPool.getConsumer().getId());
        assertEquals(new Long(5), consumerPool.getMaxMembers());
        assertEquals(e.getId(), consumerPool.getSourceEntitlement().getId());
    }

    @Test
    public void testVirtualizationHostPlatformConsumption() {
        Entitlement e = entitler.entitle(o, parentSystem, virtHostPlatform);

        // Consuming a virt host entitlement should result in a pool just for us to consume
        // virt guests.
        EntitlementPool consumerPool = entitlementPoolCurator.listByOwnerAndProduct(o,
                parentSystem, virtGuest).get(0);
        assertNotNull(consumerPool.getConsumer());
        assertEquals(parentSystem.getId(), consumerPool.getConsumer().getId());
        assertTrue(consumerPool.getMaxMembers() < 0);
        assertEquals(e.getId(), consumerPool.getSourceEntitlement().getId());
    }
    
    @Test
    public void testVirtSystemGetsWhatParentHasForFree() {
        // Give parent virt host ent:
        Entitlement e = entitler.entitle(o, parentSystem, virtHost);
        assertNotNull(e);
        
        // Give parent provisioning:
        e = entitler.entitle(o, parentSystem, provisioning);
        assertNotNull(e);
        
        EntitlementPool provisioningPool = entitlementPoolCurator.listByOwnerAndProduct(o, 
                null, provisioning).get(0);
        
        Long provisioningCount = new Long(provisioningPool.getCurrentMembers());
        assertEquals(new Long(1), provisioningCount);
        
        // Now guest requests monitoring, and should get it for "free":
        e = entitler.entitle(o, childVirtSystem, provisioning);
        assertNotNull(e);
        assertTrue(e.isFree());
        assertEquals(new Long(1), provisioningPool.getCurrentMembers());
    }
    
    @Test
    public void testVirtSystemPhysicalEntitlement() {
        // Give parent virt host ent:
        Entitlement e = entitler.entitle(o, parentSystem, virtHost);
        assertNotNull(e);
        
        EntitlementPool provisioningPool = entitlementPoolCurator.listByOwnerAndProduct(o, 
                null, provisioning).get(0);
        
        Long provisioningCount = new Long(provisioningPool.getCurrentMembers());
        assertEquals(new Long(0), provisioningCount);
        
        e = entitler.entitle(o, childVirtSystem, provisioning);
        assertNotNull(e);
        assertFalse(e.isFree());
        // Should have resorted to consuming a physical entitlement, because the guest's
        // parent does not have this.
        assertEquals(new Long(1), provisioningPool.getCurrentMembers());
    }
    
    @Test
    public void testQuantityCheck() {
        EntitlementPool monitoringPool = entitlementPoolCurator.listByOwnerAndProduct(o, 
                null, monitoring).get(0);
        assertEquals(new Long(5), monitoringPool.getMaxMembers());
        for (int i = 0; i < 5; i++) {
            Entitlement e = entitler.entitle(o, parentSystem, monitoring);
            assertNotNull(e);
        }
        
        // The cert should specify 5 monitoring entitlements, taking a 6th should fail:
        Entitlement e = entitler.entitle(o, parentSystem, monitoring);
        assertNull(e);
        assertEquals(new Long(5), monitoringPool.getCurrentMembers());
    }
}
