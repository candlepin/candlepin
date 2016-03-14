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

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.test.TestUtil;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PreEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void testBindForSameProductNotAllowed() {
        Product product = new Product(productId, "A product for testing", owner);
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, 1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }

    @Test
    public void testListForSufficientCores() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("cores", "10"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientCores() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("cores", "10"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "2");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals("rulewarning.unsupported.number.of.cores",
            result.getWarnings().get(0).getResourceKey());
    }

    @Test
    public void testListForSufficientRAM() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("ram", "16"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("memory.memtotal", "16777216");

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientRAM() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("ram", "10"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("memory.memtotal", "16777216");

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals("rulewarning.unsupported.ram",
            result.getWarnings().get(0).getResourceKey());
    }

    @Test
    public void testListForSufficientSockets() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("sockets", "2"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "1");

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientSockets() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("sockets", "2"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "4");

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals("rulewarning.unsupported.number.of.sockets",
            result.getWarnings().get(0).getResourceKey());
    }

    @Test public void bindWithQuantityNoMultiEntitle() {
        Product product = new Product(productId, "A product for testing", owner);
        Pool pool = createPool(owner, product);
        pool.setId("TaylorSwift");
        pool.setQuantity(new Long(100));

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 10);

        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getResourceKey().contains(
            "multi-entitlement"));
    }

    @Test
    public void bindWithQuantityNoMultiEntitleBatch() {
        Product product = new Product(productId, "A product for testing", owner);
        Pool pool = createPool(owner, product);
        pool.setId("TaylorSwift");
        pool.setQuantity(new Long(100));

        Pool pool2 = createPool(owner, product);
        pool2.setId("SwiftTaylor");
        pool2.setQuantity(new Long(100));

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId())).thenReturn(product);

        Map<String, ValidationResult> results = enforcer.preEntitlement(consumer,
                createPoolQuantities(100, pool, pool2),
                CallerType.UNKNOWN);

        assertEquals(2, results.size());
        for (ValidationResult result : results.values()) {
            assertFalse(result.isSuccessful());
            assertTrue(result.hasErrors());
            assertEquals(1, result.getErrors().size());
            assertTrue(result.getErrors().get(0).getResourceKey().contains("multi-entitlement"));
        }
    }

    @Test
    public void testBindFromSameProductAllowedWithMultiEntitlementAttribute() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("multi-entitlement", "yes"));
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, 1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.isSuccessful());
        assertFalse(result.hasErrors());
        assertFalse(result.hasErrors());
    }

    @Test
    public void bindFromExhaustedPoolShouldFail() {
        Product product = new Product(productId, "A product for testing", owner);
        Pool pool = TestUtil.createPool(owner, product, 0);
        pool.setId("fakeid" + TestUtil.randomInt());

        Entitlement e = new Entitlement(pool, consumer, 1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

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
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("arch", "x86_64"));
        product.setAttribute("requires_consumer_type", nonSystemType);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());
        consumer.setType(new ConsumerType(nonSystemType));

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

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
    public void matchingNumberOfSocketsShouldNotGenerateWarningBatch() {
        Pool pool = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "2");
        Pool pool2 = setupArchTest("sockets", "2", "cpu.cpu_socket(s)", "2");

        Map<String, ValidationResult> results = enforcer.preEntitlement(consumer,
                createPoolQuantities(1, pool, pool2),
                CallerType.UNKNOWN);
        assertEquals(2, results.size());
        for (ValidationResult result : results.values()) {
            assertFalse(result.hasErrors());
            assertFalse(result.hasWarnings());
        }
    }

    private List<PoolQuantity> createPoolQuantities(Integer quantity, Pool... pools) {
        List<PoolQuantity> poolQuantities = new ArrayList<PoolQuantity>();
        for (Pool pool : pools) {
            poolQuantities.add(new PoolQuantity(pool, quantity));
        }
        return poolQuantities;
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

        Product product = new Product(productId, "A product for testing", owner);
        product
            .addAttribute(new ProductAttribute(attributeName, attributeValue));
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());

        consumer.setFacts(new HashMap<String, String>() {
            {
                put(factName, factValue);
            }
        });

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);
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
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
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
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);

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

    @Test
    public void unmappedGuestGoodDate() {
        Pool pool = setupUnmappedGuestPool();
        Consumer newborn = new Consumer("test newborn consumer", "test user", owner,
                new ConsumerType(ConsumerTypeEnum.SYSTEM));
        newborn.setFact("virt.is_guest", "true");
        newborn.setCreated(new Date());
        ValidationResult result = enforcer.preEntitlement(newborn, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void unmappedGuestBadDate() {
        Pool pool = setupUnmappedGuestPool();
        Consumer tooOld = new Consumer("test newborn consumer", "test user", owner,
                new ConsumerType(ConsumerTypeEnum.SYSTEM));
        tooOld.setFact("virt.is_guest", "true");
        Date twentyFiveHoursAgo = new Date(new Date().getTime() - 25L * 60L * 60L * 1000L);
        tooOld.setCreated(twentyFiveHoursAgo);
        ValidationResult result = enforcer.preEntitlement(tooOld, pool, 1);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals("virt.guest.cannot.use.unmapped.guest.pool.not.new",
            result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void unmappedGuestFuturePoolDate() {
        Date fourHoursFromNow = new Date(new Date().getTime() + 4L * 60L * 60L * 1000L);
        Pool pool = setupUnmappedGuestPool();
        pool.setStartDate(fourHoursFromNow);

        Consumer consumer = new Consumer("test newborn consumer", "test user", owner,
                new ConsumerType(ConsumerTypeEnum.SYSTEM));
        consumer.setFact("virt.is_guest", "true");
        consumer.setCreated(new Date());
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1, CallerType.BIND);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals("virt.guest.cannot.bind.future.unmapped.guest.pool",
            result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void mappedGuest() {
        Consumer parent = new Consumer("test parent consumer", "test user", owner,
                new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Consumer newborn = new Consumer("test newborn consumer", "test user", owner,
                new ConsumerType(ConsumerTypeEnum.SYSTEM));
        newborn.setCreated(new java.util.Date());
        Pool pool = setupUnmappedGuestPool();

        String guestId = "virtguestuuid";
        newborn.setFact("virt.is_guest", "true");
        newborn.setFact("virt.uuid", guestId);
        when(consumerCurator.getHost(guestId, owner)).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(newborn, pool, 1);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals("virt.guest.cannot.use.unmapped.guest.pool.has.host",
            result.getErrors().get(0).getResourceKey());
    }

    private Pool setupUserRestrictedPool() {
        Product product = new Product(productId, "A user restricted product", owner);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setRestrictedToUsername("bob");
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);
        return pool;
    }

    private Pool setupHostRestrictedPool(Consumer parent) {
        Pool pool = setupVirtOnlyPool();
        pool.addAttribute(new PoolAttribute("requires_host", parent.getUuid()));
        return pool;
    }

    private Pool setupDevConsumerRestrictedPool(Consumer consumer) {
        Product product = new Product(productId, "product", owner);
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("dev_pool", "true"));
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);
        pool.addAttribute(new PoolAttribute("requires_consumer", consumer.getUuid()));
        return pool;
    }

    private Pool setupUnmappedGuestPool() {
        Pool pool = setupVirtOnlyPool();
        pool.addAttribute(new PoolAttribute("unmapped_guests_only", "true"));
        return pool;
    }

    private Pool setupVirtOnlyPool() {
        Product product = new Product(productId, "virt only product", owner);
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("virt_only", "true"));
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);
        return pool;
    }

    private Pool setupPhysOnlyPool() {
        Product product = new Product(productId, "physical only product", owner);
        Pool pool = TestUtil.createPool(owner, product);
        pool.addAttribute(new PoolAttribute("physical_only", "true"));
        pool.setId("fakeid" + TestUtil.randomInt());
        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);
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
        Product product = new Product(productId, "A product for testing", owner);
        product.setAttribute("requires_consumer_type",
            consumerType.toString());
        Pool pool = createPool(owner, product);
        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);
        return pool;
    }

    @Test
    public void testBindForSameProductNotAllowedList() {
        Product product = new Product(productId, "A product for testing", owner);
        Pool pool = createPool(owner, product);
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);

        Entitlement e = new Entitlement(pool, consumer, 1);
        consumer.addEntitlement(e);

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        List<Pool> filtered = enforcer.filterPools(consumer, pools, true);

        assertTrue(filtered.isEmpty());
    }

    @Test
    public void testListForSufficientCoresList() {
        Product product = new Product(productId, "A product for testing", owner);
        product.addAttribute(new ProductAttribute("cores", "10"));
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<String, String>());
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        when(this.prodAdapter.getProductById(product.getOwner(), product.getId()))
            .thenReturn(product);

        List<Pool> pools = new LinkedList<Pool>();
        pools.add(pool);
        List<Pool> filtered = enforcer.filterPools(consumer, pools, false);

        assertEquals(1, filtered.size());
        assertTrue(filtered.contains(pool));
    }

    @Test
    public void devPoolConsumerMatches() {
        Pool pool = setupDevConsumerRestrictedPool(consumer);
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void devPoolConsumerDoesNotMatch() {
        // Another comsumer we'll make a dev pool for:
        Consumer otherConsumer = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));
        Pool pool = setupDevConsumerRestrictedPool(otherConsumer);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals("consumer.does.not.match.pool.consumer.requirement",
            result.getErrors().get(0).getResourceKey());
    }

}
