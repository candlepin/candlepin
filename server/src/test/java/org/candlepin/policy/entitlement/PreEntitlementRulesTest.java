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
package org.candlepin.policy.entitlement;

import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.entitlement.Enforcer.CallerType;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * PreEntitlementRulesTest
 */
public class PreEntitlementRulesTest extends EntitlementRulesTestFixture {
    @Test
    public void testBindForSameProductNotAllowed() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, owner,  1);
        consumer.addEntitlement(e);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertTrue(result.hasErrors());
        assertFalse(result.isSuccessful());
    }

    @Test
    public void testListForSufficientCores() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.CORES, "10");
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<>());
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientCores() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.CORES, "10");
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<>());
        consumer.setFact("cpu.cpu_socket(s)", "2");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals(EntitlementRulesTranslator.WarningKeys.CORE_NUMBER_UNSUPPORTED,
            result.getWarnings().get(0));
    }

    @Test
    public void testListForSufficientRAM() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.RAM, "16");
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<>());
        consumer.setFact("memory.memtotal", "16777216");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientRAM() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.RAM, "10");
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<>());
        consumer.setFact("memory.memtotal", "16777216");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals(EntitlementRulesTranslator.WarningKeys.RAM_NUMBER_UNSUPPORTED,
            result.getWarnings().get(0));
    }

    @Test
    public void testListForSufficientSockets() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<>());
        consumer.setFact("cpu.cpu_socket(s)", "1");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testListForInsufficientSockets() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.SOCKETS, "2");
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<>());
        consumer.setFact("cpu.cpu_socket(s)", "4");

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.isSuccessful());
        assertEquals(EntitlementRulesTranslator.WarningKeys.SOCKET_NUMBER_UNSUPPORTED,
            result.getWarnings().get(0));
    }

    @Test public void bindWithQuantityNoMultiEntitle() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        Pool pool = createPool(owner, product);
        pool.setId("TaylorSwift");
        pool.setQuantity(new Long(100));

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 10);

        assertFalse(result.isSuccessful());
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals(EntitlementRulesTranslator.ErrorKeys.MULTI_ENTITLEMENT_UNSUPPORTED,
            result.getErrors().get(0));
    }

    @Test
    public void bindWithQuantityNoMultiEntitleBatch() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        Pool pool = createPool(owner, product);
        pool.setId("TaylorSwift");
        pool.setQuantity(new Long(100));

        Pool pool2 = createPool(owner, product);
        pool2.setId("SwiftTaylor");
        pool2.setQuantity(new Long(100));

        Map<String, ValidationResult> results = enforcer.preEntitlement(consumer,
            createPoolQuantities(100, pool, pool2), CallerType.UNKNOWN);

        assertEquals(2, results.size());
        for (ValidationResult result : results.values()) {
            assertFalse(result.isSuccessful());
            assertTrue(result.hasErrors());
            assertEquals(1, result.getErrors().size());
            assertEquals(EntitlementRulesTranslator.ErrorKeys.MULTI_ENTITLEMENT_UNSUPPORTED,
                result.getErrors().get(0));
        }
    }

    @Test
    public void testBindFromSameProductAllowedWithMultiEntitlementAttribute() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        Pool pool = createPool(owner, product);

        Entitlement e = new Entitlement(pool, consumer, owner, 1);
        consumer.addEntitlement(e);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.isSuccessful());
        assertFalse(result.hasErrors());
        assertFalse(result.hasErrors());
    }

    @Test
    public void bindFromExhaustedPoolShouldFail() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        Pool pool = TestUtil.createPool(owner, product, 0);
        pool.setId("fakeid" + TestUtil.randomInt());

        Entitlement e = new Entitlement(pool, consumer, owner, 1);
        consumer.addEntitlement(e);

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
        consumer.setFacts(new HashMap<>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void missingConsumerArchitectureShouldNotGenerateWarningForNonSystem() {

        String nonSystemType = "somethingElse";
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        product.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, nonSystemType);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());

        ConsumerType ctype = this.mockConsumerType(new ConsumerType(nonSystemType));
        consumer.setType(ctype);

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
        Pool pool = setupArchTest("arch", "x86_64,x86_64", "uname.machine", "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testDuplicateArchesNoMatches() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64", "uname.machine", "z80");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    public void testCommaSplitArchesTrailingComma() {
        Pool pool = setupArchTest("arch", "x86_64,x86_64,", "uname.machine", "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testCommaSplitArchesExtraSpaces() {
        Pool pool = setupArchTest("arch", "x86_64,  z80 ", "uname.machine", "x86_64");
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void multipleArchesNoMatches() {
        Pool pool = setupArchTest("arch", "s390x,z80,ppc64", "uname.machine", "i686");
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
            createPoolQuantities(1, pool, pool2), CallerType.UNKNOWN);
        assertEquals(2, results.size());
        for (ValidationResult result : results.values()) {
            assertFalse(result.hasErrors());
            assertFalse(result.hasWarnings());
        }
    }

    private List<PoolQuantity> createPoolQuantities(Integer quantity, Pool... pools) {
        List<PoolQuantity> poolQuantities = new ArrayList<>();
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
        consumer.setFacts(new HashMap<>());

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
        consumer.setFacts(new HashMap<>());

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    private Pool setupArchTest(final String attributeName,
        String attributeValue, final String factName, final String factValue) {

        Product product = TestUtil.createProduct(productId, "A product for testing");
        product
            .setAttribute(attributeName, attributeValue);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId("fakeid" + TestUtil.randomInt());

        consumer.setFacts(new HashMap<String, String>() {
            {
                put(factName, factValue);
            }
        });

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
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.DOMAIN));

        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.DOMAIN);
        consumer.setType(ctype);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void mismatchingConsumerTypeShouldGenerateError() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.PERSON));

        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.DOMAIN);
        consumer.setType(ctype);

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
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Consumer parent = new Consumer("test parent consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        Pool pool = setupHostRestrictedPool(parent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId, owner.getId())).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void virtOnlyPoolGuestHostDoesNotMatch() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        // Parent consumer of our guest:
        Consumer parent = new Consumer("test parent consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());

        // Another parent we'll make a virt only pool for:
        Consumer otherParent = new Consumer("test parent consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        Pool pool = setupHostRestrictedPool(otherParent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId, owner.getId())).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals(EntitlementRulesTranslator.ErrorKeys.VIRT_HOST_MISMATCH,
            result.getErrors().get(0));
    }

    @Test
    public void virtOnlyPoolGuestNoHost() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);

        // Another parent we'll make a virt only pool for:
        Consumer otherParent = new Consumer("test parent consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        Pool pool = setupHostRestrictedPool(otherParent);

        String guestId = "virtguestuuid";
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", guestId);

        when(consumerCurator.getHost(guestId, owner.getId())).thenReturn(null);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals(EntitlementRulesTranslator.ErrorKeys.VIRT_HOST_MISMATCH, result.getErrors().get(0));
    }

    @Test
    public void virtOnlyPoolGuestNoHostIsPhysical() {
        Pool pool = setupVirtOnlyPool();

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getWarnings().size());
        assertEquals(EntitlementRulesTranslator.WarningKeys.VIRT_ONLY, result.getWarnings().get(0));
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
        assertEquals(EntitlementRulesTranslator.WarningKeys.PHYSICAL_ONLY, result.getWarnings().get(0));
    }

    @Test
    public void unmappedGuestGoodDate() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Pool pool = setupUnmappedGuestPool();
        Consumer newborn = new Consumer("test newborn consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        newborn.setFact("virt.is_guest", "true");
        newborn.setCreated(new Date());
        ValidationResult result = enforcer.preEntitlement(newborn, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void unmappedGuestBadDate() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Pool pool = setupUnmappedGuestPool();
        Consumer tooOld = new Consumer("test newborn consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        tooOld.setFact("virt.is_guest", "true");
        Date twentyFiveHoursAgo = new Date(new Date().getTime() - 25L * 60L * 60L * 1000L);
        tooOld.setCreated(twentyFiveHoursAgo);
        ValidationResult result = enforcer.preEntitlement(tooOld, pool, 1);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals(EntitlementRulesTranslator.ErrorKeys.VIRTUAL_GUEST_RESTRICTED,
            result.getErrors().get(0));
    }

    @Test
    public void unmappedGuestFuturePoolDate() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Date fourHoursFromNow = new Date(new Date().getTime() + 4L * 60L * 60L * 1000L);
        Pool pool = setupUnmappedGuestPool();
        pool.setStartDate(fourHoursFromNow);

        Consumer consumer = new Consumer("test newborn consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        consumer.setFact("virt.is_guest", "true");
        consumer.setCreated(new Date());
        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1, CallerType.BIND);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals(EntitlementRulesTranslator.ErrorKeys.TEMPORARY_FUTURE_POOL, result.getErrors().get(0));
    }

    @Test
    public void mappedGuest() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Consumer parent = new Consumer("test parent consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        Consumer newborn = new Consumer("test newborn consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        newborn.setCreated(new java.util.Date());
        Pool pool = setupUnmappedGuestPool();

        String guestId = "virtguestuuid";
        newborn.setFact("virt.is_guest", "true");
        newborn.setFact("virt.uuid", guestId);
        when(consumerCurator.getHost(guestId, owner.getId())).thenReturn(parent);

        ValidationResult result = enforcer.preEntitlement(newborn, pool, 1);
        assertTrue(result.hasErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals(EntitlementRulesTranslator.ErrorKeys.UNMAPPED_GUEST_RESTRICTED,
            result.getErrors().get(0));
    }

    private Pool setupUserRestrictedPool() {
        Product product = TestUtil.createProduct(productId, "A user restricted product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setRestrictedToUsername("bob");
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

    private Pool setupHostRestrictedPool(Consumer parent) {
        Pool pool = setupVirtOnlyPool();
        pool.setAttribute(Pool.Attributes.REQUIRES_HOST, parent.getUuid());
        return pool;
    }

    private Pool setupDevConsumerRestrictedPool(Consumer consumer) {
        Product product = TestUtil.createProduct(productId, "product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        pool.setId("fakeid" + TestUtil.randomInt());
        pool.setAttribute(Pool.Attributes.REQUIRES_CONSUMER, consumer.getUuid());
        return pool;
    }

    private Pool setupUnmappedGuestPool() {
        Pool pool = setupVirtOnlyPool();
        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        return pool;
    }

    private Pool setupVirtOnlyPool() {
        Product product = TestUtil.createProduct(productId, "virt only product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

    private Pool setupPhysOnlyPool() {
        Product product = TestUtil.createProduct(productId, "physical only product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "true");
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

    @Test
    public void hypervisorForSystemNotGenerateError() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.HYPERVISOR));

        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.SYSTEM);
        consumer.setType(ctype);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void systemForHypervisorGeneratesError() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Pool pool = setupProductWithConsumerTypeAttribute(ConsumerTypeEnum.HYPERVISOR);
        consumer.setType(ctype);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertTrue(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    private Pool setupProductWithConsumerTypeAttribute(ConsumerTypeEnum consumerType) {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE,
            consumerType.toString());
        Pool pool = createPool(owner, product);
        return pool;
    }

    @Test
    public void testBindForSameProductNotAllowedList() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        Pool pool = createPool(owner, product);
        List<Pool> pools = new LinkedList<>();
        pools.add(pool);

        Entitlement e = new Entitlement(pool, consumer, owner, 1);
        consumer.addEntitlement(e);

        List<Pool> filtered = enforcer.filterPools(consumer, pools, true);
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void testListForSufficientCoresList() {
        Product product = TestUtil.createProduct(productId, "A product for testing");
        product.setAttribute(Product.Attributes.CORES, "10");
        Pool pool = createPool(owner, product);

        consumer.setFacts(new HashMap<>());
        consumer.setFact("cpu.cpu_socket(s)", "1");
        consumer.setFact("cpu.core(s)_per_socket", "10");

        List<Pool> pools = new LinkedList<>();
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
        // Another consumer we'll make a dev pool for:
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Consumer otherConsumer = new Consumer("test consumer", "test user", owner, ctype)
            .setUuid(Util.generateUUID());
        Pool pool = setupDevConsumerRestrictedPool(otherConsumer);

        ValidationResult result = enforcer.preEntitlement(consumer, pool, 1);
        assertFalse(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals(EntitlementRulesTranslator.ErrorKeys.CONSUMER_MISMATCH, result.getErrors().get(0));
    }

}
