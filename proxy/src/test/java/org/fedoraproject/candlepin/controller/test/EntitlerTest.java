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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

public class EntitlerTest extends DatabaseTestFixture {
    
    public static final String PRODUCT_MONITORING = "monitoring";
    public static final String PRODUCT_PROVISIONING = "provisioning";
    public static final String PRODUCT_VIRT_HOST = "virtualization_host";
    public static final String PRODUCT_VIRT_HOST_PLATFORM = "virtualization_host_platform";
    public static final String PRODUCT_VIRT_GUEST = "virt_guest";
    
    private Product virtHost;
    private Product virtHostPlatform;
    private Product virtGuest;
    private Product monitoring;
    private Product provisioning;
    
    private ConsumerType systemType;
    
    private Owner o;
    private Consumer parentSystem;
    private Consumer childVirtSystem;
    private Entitler entitler;

    @Before
    public void setUp() throws Exception {
        o = createOwner();
        ownerCurator.create(o);

        virtHost = new Product(PRODUCT_VIRT_HOST, PRODUCT_VIRT_HOST);
        virtHostPlatform = new Product(PRODUCT_VIRT_HOST_PLATFORM, 
            PRODUCT_VIRT_HOST_PLATFORM);
        virtGuest = new Product(PRODUCT_VIRT_GUEST, PRODUCT_VIRT_GUEST);
        monitoring = new Product(PRODUCT_MONITORING, PRODUCT_MONITORING);
        provisioning = new Product(PRODUCT_PROVISIONING, PRODUCT_PROVISIONING);        
        
        virtHost.addAttribute(new Attribute(PRODUCT_VIRT_HOST, ""));
        virtHostPlatform.addAttribute(new Attribute(PRODUCT_VIRT_HOST_PLATFORM, ""));
        virtGuest.addAttribute(new Attribute(PRODUCT_VIRT_GUEST, ""));
        monitoring.addAttribute(new Attribute(PRODUCT_MONITORING, ""));
        provisioning.addAttribute(new Attribute(PRODUCT_PROVISIONING, ""));
        
        productAdapter.createProduct(virtHost);
        productAdapter.createProduct(virtHostPlatform);
        productAdapter.createProduct(virtGuest);
        productAdapter.createProduct(monitoring);
        productAdapter.createProduct(provisioning);

        subCurator.create(new Subscription(o, virtHost, new HashSet<Product>(), 
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));
        subCurator.create(new Subscription(o, virtHostPlatform, new HashSet<Product>(), 
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));
        
        subCurator.create(new Subscription(o, monitoring, new HashSet<Product>(), 
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));
        subCurator.create(new Subscription(o, provisioning, new HashSet<Product>(),
            5L, new Date(), TestUtil.createDate(3020, 12, 12), new Date()));
        
        poolCurator.refreshPools(o);
        
        entitler = injector.getInstance(Entitler.class);

        this.systemType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(systemType);
        
        parentSystem = new Consumer("system", "user", o, systemType);
        parentSystem.getFacts().put("total_guests", "0");
        consumerCurator.create(parentSystem);
        
        childVirtSystem = new Consumer("virt system", "user", o, systemType);
        parentSystem.addChildConsumer(childVirtSystem);
        
        consumerCurator.create(childVirtSystem);
    }

    @Test
    public void testEntitlementPoolsCreated() {
        List<Pool> pools = poolCurator.listByOwner(o);
        assertTrue(pools.size() > 0);

        Pool virtHostPool = poolCurator.listByOwnerAndProduct(o, virtHost.getId()).get(0);
        assertNotNull(virtHostPool);
    }

    @Test(expected = EntitlementRefusedException.class)
    public void testVirtEntitleFailsIfAlreadyHasGuests() 
        throws EntitlementRefusedException {
        
        parentSystem.getFacts().put("total_guests", "10");
        consumerCurator.update(parentSystem);
        entitler.entitleByProduct(parentSystem, virtHost.getId(), new Integer("1"));
    }
    
    // NOTE:  Disabled after virt_system was removed as a type
    //@Test(expected = EntitlementRefusedException.class)
    public void testVirtEntitleFailsForVirtSystem() throws Exception {
        parentSystem.setType(systemType);
        consumerCurator.update(parentSystem);
        entitler.entitleByProduct(parentSystem, virtHost.getId(), new Integer("1"));
    }
    
    // NOTE:  Disabled after virt_system was removed as a type
    //@Test
    public void testVirtSystemGetsWhatParentHasForFree() throws Exception {
        // Give parent virt host ent:
        Entitlement e = entitler.entitleByProduct(parentSystem, virtHost.getId(), 
            new Integer("1"));
        assertNotNull(e);
        
        // Give parent provisioning:
        e = entitler.entitleByProduct(parentSystem, provisioning.getId(), new Integer("1"));
        assertNotNull(e);
        
        Pool provisioningPool = poolCurator.listByOwnerAndProduct(o, 
                provisioning.getId()).get(0);
        
        Long provisioningCount = new Long(provisioningPool.getConsumed());
        assertEquals(new Long(1), provisioningCount);
        
        // Now guest requests monitoring, and should get it for "free":
        e = entitler.entitleByProduct(childVirtSystem, provisioning.getId(), 
            new Integer("1"));
        assertNotNull(e);
        assertTrue(e.isFree());
        assertEquals(new Long(1), provisioningPool.getConsumed());
    }
    
    @Test
    public void testVirtSystemPhysicalEntitlement() throws Exception {
        // Give parent virt host ent:
        Entitlement e = entitler.entitleByProduct(parentSystem, virtHost.getId(), 
            new Integer("1"));
        assertNotNull(e);
        
        Pool provisioningPool = poolCurator.listByOwnerAndProduct(o, 
                provisioning.getId()).get(0);
        
        Long provisioningCount = new Long(provisioningPool.getConsumed());
        assertEquals(new Long(0), provisioningCount);
        
        e = entitler.entitleByProduct(childVirtSystem, provisioning.getId(), 
            new Integer("1"));
        assertNotNull(e);
        assertFalse(e.isFree());
        // Should have resorted to consuming a physical entitlement, because the guest's
        // parent does not have this.
        assertEquals(new Long(1), provisioningPool.getConsumed());
    }
    
    @Test
    public void testQuantityCheck() throws Exception {
        Pool monitoringPool = poolCurator.listByOwnerAndProduct(o, 
                monitoring.getId()).get(0);
        assertEquals(new Long(5), monitoringPool.getQuantity());
        for (int i = 0; i < 5; i++) {
            Entitlement e = entitler.entitleByProduct(parentSystem, monitoring.getId(), 
                new Integer("1"));
            assertNotNull(e);
        }
        
        // The cert should specify 5 monitoring entitlements, taking a 6th should fail:
        try {
            entitler.entitleByProduct(parentSystem, monitoring.getId(), new Integer("1"));
            fail();
        }
        catch (EntitlementRefusedException e) {
            //expected
        }
        assertEquals(new Long(5), monitoringPool.getConsumed());
    }

    @Test
    public void testRevocation() throws Exception {
        Entitlement e = entitler.entitleByProduct(parentSystem, monitoring.getId(), 
            new Integer("1"));
        entitler.revokeEntitlement(e);

        List<Entitlement> entitlements = entitlementCurator.listByConsumer(parentSystem);
        assertTrue(entitlements.isEmpty());
    }
    
    @Test
    public void testConsumeQuantity() throws Exception {
        Pool monitoringPool = poolCurator.listByOwnerAndProduct(o, 
            monitoring.getId()).get(0);
        assertEquals(new Long(5), monitoringPool.getQuantity());
        
        Entitlement e = entitler.entitleByProduct(parentSystem, monitoring.getId(), 3);
        assertNotNull(e);
        assertEquals(new Long(3), monitoringPool.getConsumed());
        
        entitler.revokeEntitlement(e);
        assertEquals(new Long(0), monitoringPool.getConsumed());
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
