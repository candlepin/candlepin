/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.policy.js.entitlement.test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.ValidationWarning;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.test.TestUtil;
import org.junit.Test;

/**
 * ManifestEntitlementRulesTest
 */
public class ManifestEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void postEntitlement() {
        Consumer c = mock(Consumer.class);
        PoolHelper ph = mock(PoolHelper.class);
        Entitlement e = mock(Entitlement.class);
        ConsumerType type = mock(ConsumerType.class);
        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);

        when(e.getPool()).thenReturn(pool);
        when(e.getConsumer()).thenReturn(c);
        when(c.getType()).thenReturn(type);
        when(type.isManifest()).thenReturn(true);
        when(pool.getProductId()).thenReturn("testProd");
        when(prodAdapter.getProductById(eq("testProd"))).thenReturn(product);
        when(product.getAttributes()).thenReturn(new HashSet<ProductAttribute>());
        when(pool.getAttributes()).thenReturn(new HashSet<PoolAttribute>());

        assertEquals(ph, enforcer.postEntitlement(c, ph, e));
    }

    @Test
    public void preEntitlementIgnoresSocketAttributeChecking() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("cpu.socket(s)", "12");
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("sockets", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1);
        assertNotNull(results);
        assertTrue(results.getErrors().isEmpty());
    }

    @Test
    public void preEntitlementNoCoreCapableBindError() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("cpu.core(s)_per_socket", "2");
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("cores", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(0, results.getWarnings().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.cores.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoCoreCapableListWarn() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("cpu.core(s)_per_socket", "2");
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("cores", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        ValidationWarning warning = results.getWarnings().get(0);
        assertEquals("rulewarning.cores.unsupported.by.consumer", warning.getResourceKey());
    }

    @Test
    public void preEntitlementSuccessCoreCapable() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("cpu.core(s)_per_socket", "2");
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        ConsumerCapability cc = new ConsumerCapability(c, "cores");
        caps.add(cc);
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("cores", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        assertEquals(0, results.getWarnings().size());
    }

    @Test
    public void preEntitlementNoRamCapableBindError() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("memory.memtotal", "2000000");
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("ram", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(0, results.getWarnings().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.ram.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoRamCapableListWarn() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("memory.memtotal", "2000000");
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("ram", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        ValidationWarning warning = results.getWarnings().get(0);
        assertEquals("rulewarning.ram.unsupported.by.consumer", warning.getResourceKey());
    }

    @Test
    public void preEntitlementSuccessRamCapable() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        c.setFact("memory.memtotal", "2000000");
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        ConsumerCapability cc = new ConsumerCapability(c, "ram");
        caps.add(cc);
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("ram", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        assertEquals(0, results.getWarnings().size());
    }

    @Test
    public void preEntitlementNoInstanceCapableBindError() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("instance_multiplier", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(0, results.getWarnings().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.instance.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoInstanceCapableListWarn() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("instance_multiplier", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        ValidationWarning warning = results.getWarnings().get(0);
        assertEquals("rulewarning.instance.unsupported.by.consumer",
            warning.getResourceKey());
    }

    @Test
    public void preEntitlementSuccessInstanceCapable() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = TestUtil.createConsumer();
        Set<ConsumerCapability> caps = new HashSet<ConsumerCapability>();
        ConsumerCapability cc = new ConsumerCapability(c, "instance_multiplier");
        caps.add(cc);
        c.setCapabilities(caps);
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        prod.setAttribute("instance_multiplier", "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        assertEquals(0, results.getWarnings().size());
    }

    @Test
    public void preEntitlementShouldNotAllowConsumptionFromDerivedPools() {
        Consumer c = TestUtil.createConsumer();
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setAttribute("virt_only", "true");
        p.setAttribute("pool_derived", "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }

    @Test
    public void preEntitlementShouldNotAllowListOfDerivedPools() {
        Consumer c = TestUtil.createConsumer();
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setAttribute("virt_only", "true");
        p.setAttribute("pool_derived", "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }

    @Test
    public void preEntitlementShouldNotAllowConsumptionFromRequiresHostPools() {
        Consumer c = TestUtil.createConsumer();
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setAttribute("virt_only", "true");
        p.setAttribute("requires_host", "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }

    @Test
    public void preEntitlementShouldNotAllowListOfRequiresHostPools() {
        Consumer c = TestUtil.createConsumer();
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setAttribute("virt_only", "true");
        p.setAttribute("requires_host", "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }


    @Test
    public void preEntitlementShouldNotAllowOverConsumptionOfEntitlements() {
        Consumer c = TestUtil.createConsumer();
        c.getType().setManifest(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setQuantity(5L);

        ValidationResult results = enforcer.preEntitlement(c, p, 10);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.no.entitlements.available", error.getResourceKey());
    }

}
