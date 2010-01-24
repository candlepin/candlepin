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

import java.util.List;

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
import org.junit.Before;
import org.junit.Test;

import com.redhat.rhn.common.cert.CertificateFactory;

import static org.junit.Assert.*;

public class EntitlerTest extends DatabaseTestFixture {
    
    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    
    private Owner o;
    private Consumer consumer;
    private Entitler entitler;

    @Before
    public void setUp() throws Exception {
        o = TestUtil.createOwner();
        ownerCurator.create(o);
        
        String certString = SpacewalkCertificateCuratorTest.readCertificate(
                "/certs/spacewalk-with-channel-families.cert");
        spacewalkCertificateCurator.parseCertificate(CertificateFactory.read(certString), o);

        List<EntitlementPool> pools = entitlementPoolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        virtHost = productCurator.lookupByLabel(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST);
        assertNotNull(virtHost);
        
        virtHostPlatform = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM);
        
        virtGuest = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);
        
        entitler = injector.getInstance(Entitler.class);

        ConsumerType system = new ConsumerType("system");
        consumerTypeCurator.create(system);
        
        consumer = new Consumer("system", o, system);
        consumer.getFacts().setFact("total_guests", "0");
        consumerCurator.create(consumer);
        
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

        EntitlementPool virtHostPool = entitlementPoolCurator.lookupByOwnerAndProduct(o,
                null, virtHost);
        assertNotNull(virtHostPool);
    }

    @Test
    public void testVirtEntitleFailsIfAlreadyHasGuests() {
        consumer.getFacts().setFact("total_guests", "10");
        consumerCurator.update(consumer);
        Entitlement e = entitler.createEntitlement(o, consumer, virtHost);
        assertNull(e);
    }
    
//    @Test
//    public void testVirtualizationHostConsumption() {
//        Entitlement e = entitler.createEntitlement(o, consumer, virtHost);
//
//        // Consuming a virt host entitlement should result in a pool just for us to consume
//        // virt guests.
//        EntitlementPool consumerPool = entitlementPoolCurator.lookupByOwnerAndProduct(o,
//                consumer, virtGuest);
//        assertNotNull(consumerPool.getConsumer());
//        assertEquals(consumer.getId(), consumerPool.getConsumer().getId());
//        assertEquals(new Long(5), consumerPool.getMaxMembers());
//        assertEquals(e.getId(), consumerPool.getSourceEntitlement().getId());
//    }
//
//    @Test
//    public void testVirtualizationHostPlatformConsumption() {
//        Entitlement e = entitler.createEntitlement(o, consumer, virtHostPlatform);
//
//        // Consuming a virt host entitlement should result in a pool just for us to consume
//        // virt guests.
//        EntitlementPool consumerPool = entitlementPoolCurator.lookupByOwnerAndProduct(o,
//                consumer, virtGuest);
//        assertNotNull(consumerPool.getConsumer());
//        assertEquals(consumer.getId(), consumerPool.getConsumer().getId());
//        assertTrue(consumerPool.getMaxMembers() < 0);
//        assertEquals(e.getId(), consumerPool.getSourceEntitlement().getId());
//    }
}
