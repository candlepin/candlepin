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
package org.candlepin.policy.js.entitlement;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.ValidationWarning;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.test.TestUtil;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ManifestEntitlementRulesTest
 */
public class ManifestEntitlementRulesTest extends EntitlementRulesTestFixture {

    private Consumer createMockConsumer(boolean manifestDistributor) {
        ConsumerType type = this.mockConsumerType(TestUtil.createConsumerType());
        type.setManifest(manifestDistributor);

        Consumer consumer = TestUtil.createConsumer();
        consumer.setType(type);

        return consumer;
    }

    @Test
    public void postEntitlement() {
        Consumer c = this.createMockConsumer(true);
        Owner o = mock(Owner.class);
        PoolManager pm = mock(PoolManager.class);
        Entitlement e = mock(Entitlement.class);
        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);

        when(e.getPool()).thenReturn(pool);
        when(e.getConsumer()).thenReturn(c);

        when(pool.getProductId()).thenReturn("testProd");
        when(product.getAttributes()).thenReturn(new HashMap<>());
        when(pool.getAttributes()).thenReturn(new HashMap<>());

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put("pool", e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put("pool", new PoolQuantity(pool, 1));
        enforcer.postEntitlement(pm, c, o, entitlements, null, false, poolQuantityMap);
    }

    @Test
    public void preEntitlementIgnoresSocketAttributeChecking() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        c.setFact("cpu.socket(s)", "12");

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1);
        assertNotNull(results);
        assertTrue(results.getErrors().isEmpty());
    }

    @Test
    public void preEntitlementNoCoreCapableBindError() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        c.setFact("cpu.core(s)_per_socket", "2");
        Set<ConsumerCapability> caps = new HashSet<>();
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.CORES, "2");
        Pool p = TestUtil.createPool(prod);
        p.setId("poolId");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(0, results.getWarnings().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.cores.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoCoreCapableListWarn() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        c.setFact("cpu.core(s)_per_socket", "2");
        Set<ConsumerCapability> caps = new HashSet<>();
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.CORES, "2");
        Pool p = TestUtil.createPool(prod);
        p.setId("poolId");
        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        ValidationWarning warning = results.getWarnings().get(0);
        assertEquals("rulewarning.cores.unsupported.by.consumer", warning.getResourceKey());
    }

    @Test
    public void preEntitlementSuccessCoreCapable() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        c.setFact("cpu.core(s)_per_socket", "2");
        Set<ConsumerCapability> caps = new HashSet<>();
        ConsumerCapability cc = new ConsumerCapability(c, "cores");
        caps.add(cc);
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.CORES, "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        assertEquals(0, results.getWarnings().size());
    }

    @Test
    public void preEntitlementNoRamCapableBindError() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        c.setFact("memory.memtotal", "2000000");
        Set<ConsumerCapability> caps = new HashSet<>();
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.RAM, "2");
        Pool p = TestUtil.createPool(prod);
        p.setId("poolId");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(0, results.getWarnings().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.ram.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoRamCapableListWarn() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        c.setFact("memory.memtotal", "2000000");
        Set<ConsumerCapability> caps = new HashSet<>();
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.RAM, "2");
        Pool p = TestUtil.createPool(prod);
        p.setId("poolId");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        ValidationWarning warning = results.getWarnings().get(0);
        assertEquals("rulewarning.ram.unsupported.by.consumer", warning.getResourceKey());
    }

    @Test
    public void preEntitlementSuccessRamCapable() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        c.setFact("memory.memtotal", "2000000");
        Set<ConsumerCapability> caps = new HashSet<>();
        ConsumerCapability cc = new ConsumerCapability(c, "ram");
        caps.add(cc);
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.RAM, "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        assertEquals(0, results.getWarnings().size());
    }

    @Test
    public void preEntitlementNoInstanceCapableBindError() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        Set<ConsumerCapability> caps = new HashSet<>();
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "2");
        Pool p = TestUtil.createPool(prod);
        p.setId("poolId");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(0, results.getWarnings().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.instance.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoInstanceCapableListWarn() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        Set<ConsumerCapability> caps = new HashSet<>();
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "2");
        Pool p = TestUtil.createPool(prod);
        p.setId("poolId");
        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        ValidationWarning warning = results.getWarnings().get(0);
        assertEquals("rulewarning.instance.unsupported.by.consumer", warning.getResourceKey());
    }

    @Test
    public void preEntitlementSuccessInstanceCapable() {
        // Test with sockets to make sure that they are skipped.
        Consumer c = this.createMockConsumer(true);
        Set<ConsumerCapability> caps = new HashSet<>();
        ConsumerCapability cc = new ConsumerCapability(c, "instance_multiplier");
        caps.add(cc);
        c.setCapabilities(caps);

        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "2");
        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertEquals(0, results.getErrors().size());
        assertEquals(0, results.getWarnings().size());
    }

    @Test
    public void preEntitlementShouldNotAllowConsumptionFromDerivedPools() {
        Consumer c = this.createMockConsumer(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod)
            .setId("poolId")
            .setAttribute(Product.Attributes.VIRT_ONLY, "true")
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }

    @Test
    public void preEntitlementShouldNotAllowListOfDerivedPools() {
        Consumer c = this.createMockConsumer(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod)
            .setId("poolId")
            .setAttribute(Product.Attributes.VIRT_ONLY, "true")
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }

    @Test
    public void preEntitlementShouldNotAllowConsumptionFromRequiresHostPools() {
        Consumer c = this.createMockConsumer(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod)
            .setId("poolId")
            .setAttribute(Product.Attributes.VIRT_ONLY, "true")
            .setAttribute(Pool.Attributes.REQUIRES_HOST, "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }

    @Test
    public void preEntitlementShouldNotAllowListOfRequiresHostPools() {
        Consumer c = this.createMockConsumer(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod)
            .setId("poolId")
            .setAttribute(Product.Attributes.VIRT_ONLY, "true")
            .setAttribute(Pool.Attributes.REQUIRES_HOST, "true");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("pool.not.available.to.manifest.consumers", error.getResourceKey());
    }


    @Test
    public void preEntitlementShouldNotAllowOverConsumptionOfEntitlements() {
        Consumer c = this.createMockConsumer(true);

        Product prod = TestUtil.createProduct();
        Pool p = TestUtil.createPool(prod);
        p.setQuantity(5L);

        ValidationResult results = enforcer.preEntitlement(c, p, 10);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.no.entitlements.available", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoDerivedProductCapabilityProducesErrorOnBind() {
        Consumer c = this.createMockConsumer(true);
        c.setCapabilities(new HashSet<>());

        Product prod = TestUtil.createProduct();
        Product derived = TestUtil.createProduct("sub-prod-id");
        prod.setDerivedProduct(derived);

        Pool p = TestUtil.createPool(prod)
            .setId("poolId");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        assertTrue(results.getWarnings().isEmpty());

        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.derivedproduct.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementNoDerivedProductCapabilityProducesWarningOnList() {
        Consumer c = this.createMockConsumer(true);
        c.setCapabilities(new HashSet<>());

        Product prod = TestUtil.createProduct();
        Product derived = TestUtil.createProduct("sub-prod-id");
        prod.setDerivedProduct(derived);

        Pool p = TestUtil.createPool(prod)
            .setId("poolId");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.LIST_POOLS);
        assertNotNull(results);
        assertEquals(1, results.getWarnings().size());
        assertTrue(results.getErrors().isEmpty());

        ValidationWarning warning = results.getWarnings().get(0);
        assertEquals("rulewarning.derivedproduct.unsupported.by.consumer", warning.getResourceKey());
    }

    @Test
    public void preEntitlementNoDerivedProductCapabilityProducesErrorOnBestPools() {
        Consumer c = this.createMockConsumer(true);
        c.setCapabilities(new HashSet<>());

        Product prod = TestUtil.createProduct();
        Product derived = TestUtil.createProduct("sub-prod-id");
        prod.setDerivedProduct(derived);

        Pool p = TestUtil.createPool(prod)
            .setId("poolId");

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertEquals(1, results.getErrors().size());
        assertTrue(results.getWarnings().isEmpty());

        ValidationError error = results.getErrors().get(0);
        assertEquals("rulefailed.derivedproduct.unsupported.by.consumer", error.getResourceKey());
    }

    @Test
    public void preEntitlementWithDerivedProductCapabilitySuccessOnBind() {
        Consumer c = this.createMockConsumer(true);
        HashSet<ConsumerCapability> capabilities = new HashSet<>();
        capabilities.add(new ConsumerCapability(c, "derived_product"));
        c.setCapabilities(capabilities);

        Product prod = TestUtil.createProduct();
        Product derived = TestUtil.createProduct("sub-prod-id");
        prod.setDerivedProduct(derived);

        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BIND);
        assertNotNull(results);
        assertTrue("Expected no warnings or errors.", results.isSuccessful());
    }

    @Test
    public void preEntitlementWithDerivedProductCapabilitySuccessOnBestPools() {
        Consumer c = this.createMockConsumer(true);
        HashSet<ConsumerCapability> capabilities = new HashSet<>();
        capabilities.add(new ConsumerCapability(c, "derived_product"));
        c.setCapabilities(capabilities);

        Product prod = TestUtil.createProduct();
        Product derived = TestUtil.createProduct("sub-prod-id");
        prod.setDerivedProduct(derived);

        Pool p = TestUtil.createPool(prod);

        ValidationResult results = enforcer.preEntitlement(c, p, 1, CallerType.BEST_POOLS);
        assertNotNull(results);
        assertTrue("Expected no warnings or errors.", results.isSuccessful());
    }

}
