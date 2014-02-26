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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.policy.ValidationResult;
import org.candlepin.test.TestUtil;
import org.junit.Test;

public class PreEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void testBindForSameProductNotAllowed() {
        Product product = new Product(productId, "A product for testing");
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, 1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }

    @Test
    public void testListForSufficientCores() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("cores", "10"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientCores() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("cores", "10"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "2");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals("rulewarning.unsupported.number.of.cores",
            result.getWarnings().get(0).getResourceKey());
    }

    @Test
    public void testListForSufficientRAM() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("ram", "16"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("memory.memtotal", "16777216");

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientRAM() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("ram", "10"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("memory.memtotal", "16777216");

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals("rulewarning.unsupported.ram",
            result.getWarnings().get(0).getResourceKey());
    }

    @Test
    public void testListForSufficientSockets() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("sockets", "2"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "1");

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientSockets() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("sockets", "2"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "4");

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals("rulewarning.unsupported.number.of.sockets",
            result.getWarnings().get(0).getResourceKey());
    }

    @Test public void bindWithQuantityNoMultiEntitle() {
        Product product = new Product(productId, "A product for testing");
        Pool pool = createPool(owner, product);
        pool.setQuantity(new Long(100));

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 10);

        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getResourceKey().contains(
            "multi-entitlement"));
    }

    @Test
    public void testBindFromSameProductAllowedWithMultiEntitlementAttribute() {
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("multi-entitlement", "yes"));
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, 1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.isSuccessful());
        assertFalse(result.hasErrors());
        assertFalse(result.hasErrors());
    }

    @Test
    public void bindFromExhaustedPoolShouldFail() {
        Product product = new Product(productId, "A product for testing");
        Pool pool = TestUtil.createPool(owner, product, 0);
        pool.setId("fakeid" + TestUtil.randomInt());

        Entitlement e = new Entitlement(pool, consumer, 1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }

    @Test
    public void architectureALLShouldNotGenerateWarnings() {
        Pool pool = setupArchTest("arch", "ALL", "arch", "i686");
        pool.setId("fakeid" + TestUtil.randomInt());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void architectureMismatchShouldGenerateWarning() {
        Pool pool = setupArchTest("arch", "x86_64", "uname.machine", "i686");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void missingConsumerArchitectureShouldGenerateWarning() {
        Pool pool = setupArchTest("arch", "x86_64", "uname.machine", "x86_64");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void missingConsumerArchitectureShouldNotGenerateWarningForNonSystem() {

        String nonSystemType = "somethingElse";
        Product product = new Product(productId, "A product for testing");
        product.addAttribute(new ProductAttribute("arch", "x86_64"));
        product.setAttribute("requires_consumer_type", nonSystemType);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());
        consumer.setType(new ConsumerType(nonSystemType));

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void architectureMatches() {
        Pool pool = setupArchTest("arch", "x86_64", "uname.machine", "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void x86ArchitectureProvidesI386() {
        Pool pool = setupArchTest("arch", "x86", "uname.machine", "i386");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void x86ArchitectureProvidesI586() {
        Pool pool = setupArchTest("arch", "x86", "uname.machine", "i586");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void x86ArchitectureProvidesI686() {
        Pool pool = setupArchTest("arch", "x86", "uname.machine", "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testEmptyUname() {
        Pool pool = setupArchTest("arch", "s390x,x86", "uname.machine", "");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testEmptyArch() {
        Pool pool = setupArchTest("arch", "", "uname.machine", "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testDuplicateArchesMatches() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64", "uname.machine",
            "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testDuplicateArchesNoMatches() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64", "uname.machine",
            "z80");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testCommaSplitArchesTrailingComma() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64,", "uname.machine",
            "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testCommaSplitArchesExtraSpaces() {
        Pool pool = setupArchTest("arch", "x86_64,  z80 ", "uname.machine",
            "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void multipleArchesNoMatches() {
        Pool pool = setupArchTest("arch", "s390x,z80,ppc64", "uname.machine",
            "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void multipleArchesMatches() {
        Pool pool = setupArchTest("arch", "s390x,x86", "uname.machine", "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void goodArchNoUnameMachine() {
        Pool pool = setupArchTest("arch", "x86", "something.not.uname", "i686");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void fewerThanMaximumNumberOfSocketsShouldNotGenerateWarning() {
        Pool pool = setupArchTest("sockets", "128", "cpu.cpu_socket(s)", "2");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void matchingNumberOfSocketsShouldNotGenerateWarning() {
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "2");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void missingConsumerSocketsShouldNotGenerateWarning() {
        // non-system consumers do not have socket counts, no warning
        // should be generated (per IT)
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "2");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testZeroConsumerSocketsShouldNotGenerateWarning() {
        // there was a bug in an IT adapter where a null socket count was being
        // set to zero. As a hotfix, we do not generate a warning when socket
        // count is zero.
        Pool pool = setupArchTest("sockets", "0", "cpu.cpu_socket(s)", "2");

        // Get rid of the facts that setupTest set.
        consumer.setFacts(new HashMap<String, String>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    private Pool setupArchTest(final String attributeName,
        String attributeValue, final String factName, final String factValue) {

        Product product = new Product(productId, "A product for testing");
        product
            .addAttribute(new ProductAttribute(attributeName, attributeValue));
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());

        consumer.setFacts(new HashMap<String, String>() {
            {
                put(factName, factValue);
            }
        });

        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    @Test
    public void exceedingNumberOfSocketsShouldGenerateWarning() {
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "4");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void consumerHavingLessRamThanProductShouldNotGenerateWarning() {
        // Fact specified in kb
        Pool pool = setupArchTest("ram", "4", "memory.memtotal", "2000000");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void consumerHavingEqualRamAsProductShouldNotGenerateWarning() {
        // Fact specified in kb
        Pool pool = setupArchTest("ram", "2", "memory.memtotal", "2000000");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void consumerRamIsRoundedToNearestGbAndShouldNotGenerateWarning() {
        // Fact specified in kb - actual value of 2 GiB in kb.
        Pool pool = setupArchTest("ram", "2", "memory.memtotal", "2097152");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void consumerHavingMoreRamThanProductGeneratesWarning() {
        Pool pool = setupArchTest("ram", "2", "memory.memtotal", "4000000");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void correctConsumerTypeShouldNotGenerateError() {
        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.DOMAIN);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.DOMAIN));

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void mismatchingConsumerTypeShouldGenerateError() {
        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.DOMAIN);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void userRestrictedPoolPassesPre() {
        Pool pool = setupUserRestrictedPool();
        consumer.setUsername("bob");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void userRestrictedPoolFailsPre() {
        Pool pool = setupUserRestrictedPool();
        consumer.setUsername("notbob");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void virtOnlyPoolGuestHostMatches() {
        Consumer parent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupHostRestrictedPool(parent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId, owner)).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void virtOnlyPoolGuestHostDoesNotMatch() {
        when(config.standalone()).thenReturn(true);
        // Parent consumer of our guest:
        Consumer parent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        // Another parent we'll make a virt only pool for:
        Consumer otherParent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupHostRestrictedPool(otherParent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId, owner)).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals("virt.guest.host.does.not.match.pool.owner",
            result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void virtOnlyPoolGuestNoHost() {
        when(config.standalone()).thenReturn(true);

        // Another parent we'll make a virt only pool for:
        Consumer otherParent = new Consumer("test parent consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupHostRestrictedPool(otherParent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId, owner)).thenReturn(null);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals("virt.guest.host.does.not.match.pool.owner",
            result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void virtOnlyPoolGuestNoHostIsPhysical() {
        Pool pool = setupVirtOnlyPool();

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarnings().size());
        assertEquals("rulewarning.virt.only",
            result.getWarnings().get(0).getResourceKey());
    }

    @Test
    public void virtOnlyNonHostReqPool() {
        Pool pool = setupVirtOnlyPool();
        consumer.setFact("virt.is_guest", "true");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void physOnlyPoolGuestNoHostIsPhysical() {
        Pool pool = setupPhysOnlyPool();

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void physOnlyVirtualConsumer() {
        Pool pool = setupPhysOnlyPool();
        consumer.setFact("virt.is_guest", "true");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarnings().size());
        assertEquals("rulewarning.physical.only",
            result.getWarnings().get(0).getResourceKey());
    }

    private Pool setupUserRestrictedPool() {
        Product product = new Product(productId, "A user restricted product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setRestrictedToUsername("bob");
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    private Pool setupHostRestrictedPool(Consumer parent) {
        Pool pool = setupVirtOnlyPool();
        pool.addAttribute(new PoolAttribute("requires_host", parent.getUuid()));
        return pool;
    }

    private Pool setupVirtOnlyPool() {
        Product product = new Product(productId, "virt only product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("virt_only", "true"));
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    private Pool setupPhysOnlyPool() {
        Product product = new Product(productId, "physical only product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("physical_only", "true"));
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

    @Test
    public void hypervisorForSystemNotGenerateError() {
        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.SYSTEM);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.HYPERVISOR));

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void systemForHypervisorGeneratesError() {
        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.HYPERVISOR);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    private Pool setupProductWithConsumerTypeAttribute(ConsumerTypeEnum consumerType) {
        Product product = new Product(productId, "A product for testing");
        product.setAttribute("requires_consumer_type",
            consumerType.toString());
        Pool pool = createPool(owner, product);
        when(this.prodAdapter.getProductById(productId)).thenReturn(product);
        return pool;
    }

}
