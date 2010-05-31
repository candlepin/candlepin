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

import static org.junit.Assert.*;

import java.util.List;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.test.SpacewalkCertificateCuratorTest;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.redhat.rhn.common.cert.CertificateFactory;

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
    private Principal principal;

    @Before
    public void setUp() throws Exception {
        o = createOwner();
        ownerCurator.create(o);
        
        String certString = SpacewalkCertificateCuratorTest.readCertificate(
                "/certs/spacewalk-with-channel-families.cert");
        spacewalkCertCurator.parseCertificate(CertificateFactory.read(certString), o);

        List<Pool> pools = poolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);
        principal = injector.getInstance(Principal.class);

        virtHost = productCurator
                .lookupByLabel(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST);
        virtHost.addAttribute(
            new Attribute(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST, ""));
        assertNotNull(virtHost);
        
        virtHostPlatform = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM);
        virtHostPlatform.addAttribute(
            new Attribute(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM, ""));
        
        virtGuest = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);
        virtGuest.addAttribute(
            new Attribute(SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST, ""));
        
        monitoring = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_MONITORING);
        monitoring.addAttribute(
            new Attribute(SpacewalkCertificateCurator.PRODUCT_MONITORING, ""));
        
        provisioning = productCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_PROVISIONING);
        provisioning.addAttribute(
            new Attribute(SpacewalkCertificateCurator.PRODUCT_PROVISIONING, ""));
        
        entitler = injector.getInstance(Entitler.class);

        ConsumerType system = new ConsumerType(ConsumerType.SYSTEM);
        consumerTypeCurator.create(system);
        
        guestType = new ConsumerType(ConsumerType.VIRT_SYSTEM);
        consumerTypeCurator.create(guestType);
        
        parentSystem = new Consumer("system", o, system);
        parentSystem.getFacts().put("total_guests", "0");
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
        List<Pool> pools = poolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        Pool virtHostPool = poolCurator.listByOwnerAndProduct(o,
                virtHost).get(0);
        assertNotNull(virtHostPool);
    }

    @Test
    public void testVirtEntitleFailsIfAlreadyHasGuests() throws Exception {
        parentSystem.getFacts().put("total_guests", "10");
        consumerCurator.update(parentSystem);
        try {
            entitler.entitle(parentSystem, virtHost);
            fail();
        }
        catch (EntitlementRefusedException e) {
            //expected
        }

        try {
            entitler.entitle(parentSystem, virtHostPlatform);
            fail();
        }
        catch (EntitlementRefusedException e) {
            //expected
        }
    }
    
    @Test
    public void testVirtEntitleFailsForVirtSystem() throws Exception {
        parentSystem.setType(guestType);
        consumerCurator.update(parentSystem);
        try {
            entitler.entitle(parentSystem, virtHost);
            fail();
        }
        catch (EntitlementRefusedException e) {
            //expected
        }

        try {
            entitler.entitle(parentSystem, virtHostPlatform);
            fail();
        }
        catch (EntitlementRefusedException e) {
            //expected
        }
    }
    
    @Test
    public void testVirtSystemGetsWhatParentHasForFree() throws Exception {
        // Give parent virt host ent:
        Entitlement e = entitler.entitle(parentSystem, virtHost);
        assertNotNull(e);
        
        // Give parent provisioning:
        e = entitler.entitle(parentSystem, provisioning);
        assertNotNull(e);
        
        Pool provisioningPool = poolCurator.listByOwnerAndProduct(o, 
                provisioning).get(0);
        
        Long provisioningCount = new Long(provisioningPool.getConsumed());
        assertEquals(new Long(1), provisioningCount);
        
        // Now guest requests monitoring, and should get it for "free":
        e = entitler.entitle(childVirtSystem, provisioning);
        assertNotNull(e);
        assertTrue(e.isFree());
        assertEquals(new Long(1), provisioningPool.getConsumed());
    }
    
    @Test
    public void testVirtSystemPhysicalEntitlement() throws Exception {
        // Give parent virt host ent:
        Entitlement e = entitler.entitle(parentSystem, virtHost);
        assertNotNull(e);
        
        Pool provisioningPool = poolCurator.listByOwnerAndProduct(o, 
                provisioning).get(0);
        
        Long provisioningCount = new Long(provisioningPool.getConsumed());
        assertEquals(new Long(0), provisioningCount);
        
        e = entitler.entitle(childVirtSystem, provisioning);
        assertNotNull(e);
        assertFalse(e.isFree());
        // Should have resorted to consuming a physical entitlement, because the guest's
        // parent does not have this.
        assertEquals(new Long(1), provisioningPool.getConsumed());
    }
    
    @Test
    public void testQuantityCheck() throws Exception {
        Pool monitoringPool = poolCurator.listByOwnerAndProduct(o, 
                monitoring).get(0);
        assertEquals(new Long(5), monitoringPool.getQuantity());
        for (int i = 0; i < 5; i++) {
            Entitlement e = entitler.entitle(parentSystem, monitoring);
            assertNotNull(e);
        }
        
        // The cert should specify 5 monitoring entitlements, taking a 6th should fail:
        try {
            entitler.entitle(parentSystem, monitoring);
            fail();
        }
        catch (EntitlementRefusedException e) {
            //expected
        }
        assertEquals(new Long(5), monitoringPool.getConsumed());
    }

    @Test
    public void testRevocation() throws Exception {
        Entitlement e = entitler.entitle(parentSystem, monitoring);
        entitler.revokeEntitlement(e);

        List<Entitlement> entitlements = entitlementCurator.listByConsumer(parentSystem);
        assertTrue(entitlements.isEmpty());
    }
    
    @Override
    protected Module getGuiceOverrideModule() {
        return new AbstractModule() {
            
            @Override
            protected void configure() {
                bind(Enforcer.class).to(JavascriptEnforcer.class);
            }
        };
    }
}
