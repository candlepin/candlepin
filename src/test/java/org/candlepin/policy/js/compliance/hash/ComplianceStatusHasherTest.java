/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.policy.js.compliance.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.js.compliance.ComplianceReason;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComplianceStatusHasherTest {

    private static final String RELATED_FACT_INITIAL = "memory.memtotal";
    private static final String RELATED_FACT_NEW = "virt.is_guest";
    private static final String UNRELATED_FACT_INITIAL = "unrelated";
    private static final String UNRELATED_FACT_NEW = "unrelated2";
    private static final String NON_COMPLIANT_PRODUCT_1 = "ncp1";
    private static final String NON_COMPLIANT_PRODUCT_2 = "ncp2";
    private static final String NON_COMPLIANT_PRODUCT_3 = "ncp3";
    private static final String COMPLIANT_PRODUCT_1 = "cp1";
    private static final String COMPLIANT_PRODUCT_2 = "cp2";
    private static final String COMPLIANT_PRODUCT_3 = "cp3";

    private String initialHash;
    private Owner owner;

    @BeforeEach
    public void setUp() throws Exception {
        owner = new Owner()
            .setId("test-owner-id")
            .setKey("test-owner")
            .setDisplayName("Test Owner");

        Consumer consumer = createConsumer(owner);
        ComplianceStatus status = createStatusOf(consumer);
        initialHash = generateHash(status, consumer);
    }

    @Test
    void ensureSameHashWithNoChanges() {
        Consumer consumer = createConsumer(owner);
        assertEquals(initialHash, generateHash(createStatusOf(consumer), consumer));
    }

    @Nested
    class NonCompliantProductChangesTest {
        @Test
        void shouldChangeWhenNonCompliantProductsAreRemoved() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            testStatus.getNonCompliantProducts().clear();
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void ensureDifferentHashWhenNonCompliantProductsAreAdded() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            testStatus.addNonCompliantProduct(NON_COMPLIANT_PRODUCT_3);
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }
    }

    @Nested
    class CompliantProductChangesTest {
        @Test
        void ensureDifferentHashWhenCompliantProductRemoved() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            testStatus.getCompliantProducts().remove(COMPLIANT_PRODUCT_1);
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void ensureDifferentHashWhenCompliantProductAdded() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            testStatus.addCompliantProduct(COMPLIANT_PRODUCT_3,
                createEntitlement(new Date(), owner, consumer, COMPLIANT_PRODUCT_3));
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void ensureDifferentHashWhenCompliantProductEntitlementCountChanges() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            Entitlement ent = createEntitlement(new Date(), owner, consumer, "test-ent");
            HashSet<Entitlement> ents = new HashSet<>();
            ents.add(ent);
            testStatus.getCompliantProducts().put(ent.getPool().getProductId(), ents);

            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }
    }

    @Test
    public void ensureDifferentHashWhenPartiallyCompliantProductsChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createStatusOf(consumer);

        Entitlement ent = createEntitlement(new Date(), owner, consumer, "test-ent");
        HashSet<Entitlement> ents = new HashSet<>();
        ents.add(ent);
        testStatus.getPartiallyCompliantProducts().put(ent.getPool().getProductId(), ents);

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenPartialStacksChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createStatusOf(consumer);

        Entitlement ent = createEntitlement(new Date(), owner, consumer, "test-ent");
        HashSet<Entitlement> ents = new HashSet<>();
        ents.add(ent);
        testStatus.getPartialStacks().put("p-stack-2", ents);

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenReasonsChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createStatusOf(consumer);

        ComplianceReason reason = new ComplianceReason();
        reason.setKey("TEST-REASON-KEY");
        reason.setMessage("This is a test!");
        testStatus.getReasons().add(reason);

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenReasonKeyChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createStatusOf(consumer);

        ComplianceReason reason = testStatus.getReasons().iterator().next();
        reason.setKey("FOOF");

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenReasonAttributeChanges() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createStatusOf(consumer);

        ComplianceReason reason = testStatus.getReasons().iterator().next();

        // Test new attribute map same values
        Map<String, String> newAttrs = new HashMap<>(reason.getAttributes());
        reason.setAttributes(newAttrs);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        // Test new value
        newAttrs.put(reason.getKey() + "-attr", reason.getKey() + "-value");
        assertEquals(initialHash, generateHash(testStatus, consumer));
        newAttrs.put(reason.getKey() + "-attr", "new value");
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        // Test new attribute.
        newAttrs.put("test-key", "test-value");
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        // Test attribute count.
        newAttrs.clear();
        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Nested
    class ConsumerChangesTest {

        @Test
        void shouldNotChangeRegardlessOfContainer() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            // Same facts, new map.
            consumer.setFacts(new HashMap<>(consumer.getFacts()));
            assertEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldChangeOnClearedFacts() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.setFacts(new HashMap<>());
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldChangeWithAdditionOfNewRelatedFact() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.setFact(RELATED_FACT_NEW, "fact");
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldChangeWithRemovalOfRelatedFact() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.removeFact(RELATED_FACT_INITIAL);
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldChangeWithUpdateOfRelatedFact() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.setFact(RELATED_FACT_INITIAL, "Different Value");
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldNotChangeWithNewUnrelatedFact() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.setFact(UNRELATED_FACT_NEW, "fact");
            assertEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldNotChangeWithRemovedUnrelatedFact() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.removeFact(UNRELATED_FACT_INITIAL);
            assertEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldNotChangeWithUnrelatedFactUpdate() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.setFact(UNRELATED_FACT_INITIAL, "Different Value");
            assertEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void ensureDifferentHashWhenConsumerInstalledProductsChange() {
            Consumer consumer = createConsumer(owner);
            Product product = TestUtil.createProduct("Test Product");
            ComplianceStatus testStatus = createStatusOf(consumer);

            Set<ConsumerInstalledProduct> initialInstalled = consumer.getInstalledProducts();
            consumer.setInstalledProducts(new HashSet<>(initialInstalled));
            assertEquals(initialHash, generateHash(testStatus, consumer));

            consumer.setInstalledProducts(new HashSet<>(initialInstalled));
            assertEquals(initialHash, generateHash(testStatus, consumer));

            ConsumerInstalledProduct installed = new ConsumerInstalledProduct()
                .setProductId(product.getId())
                .setProductName(product.getName());

            consumer.addInstalledProduct(installed);

            String updatedHash = generateHash(testStatus, consumer);
            assertNotEquals(initialHash, updatedHash);

            // Test arch change
            installed.setArch("test-arch");
            assertNotEquals(updatedHash, generateHash(testStatus, consumer));
            installed.setArch(null);
            assertEquals(updatedHash, generateHash(testStatus, consumer));

            // Test version change
            installed.setVersion("1.2.3.4");
            assertNotEquals(updatedHash, generateHash(testStatus, consumer));
            installed.setVersion(null);
            assertEquals(updatedHash, generateHash(testStatus, consumer));

            consumer.removeInstalledProduct(installed);
            assertEquals(initialHash, generateHash(testStatus, consumer));

            consumer.setInstalledProducts(null);
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void ensureDifferentHashWhenConsumerEntitlementCountsChange() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            Set<Entitlement> initialEnts = consumer.getEntitlements();
            consumer.setEntitlements(new HashSet<>(initialEnts));
            assertEquals(initialHash, generateHash(testStatus, consumer));

            // Create and add an entitlement to the consumer.
            Entitlement ent = createEntitlement(new Date(), owner, consumer, "tp");
            assertNotEquals(initialHash, generateHash(testStatus, consumer));

            consumer.removeEntitlement(ent);
            assertEquals(initialHash, generateHash(testStatus, consumer));

            consumer.setEntitlements(null);
            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void ensureDifferentHashWhenConsumerEntitlementChanges() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            Entitlement ent = consumer.getEntitlements().iterator().next();
            String id = ent.getId();
            Integer quantity = ent.getQuantity();

            // Check the ID
            ent.setId("somethhing_differerent");
            assertNotEquals(initialHash, generateHash(testStatus, consumer));

            ent.setId(id);
            assertEquals(initialHash, generateHash(testStatus, consumer));

            // Check the quantity
            ent.setQuantity(112);
            assertNotEquals(initialHash, generateHash(testStatus, consumer));

            ent.setQuantity(quantity);
            assertEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void ensureDifferentHashWhenEntitlementPoolChanges() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.getEntitlements().stream().findFirst()
                .map(Entitlement::getPool)
                .ifPresent(pool -> pool.setId("changedId"));

            assertNotEquals(initialHash, generateHash(testStatus, consumer));
        }

        @Test
        void shouldGenerateHashForConsumerWithNullFacts() {
            Consumer consumer = createConsumer(owner);
            ComplianceStatus testStatus = createStatusOf(consumer);

            consumer.setFacts(null);
            // This should not throw NPE
            String secondHash = generateHash(testStatus, consumer);

            assertNotEquals(secondHash, initialHash);
        }
    }

    private Consumer createConsumer(Owner owner) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        Consumer consumer = new Consumer()
            .setId("1")
            .setUuid("12345")
            .setType(ctype)
            .setOwner(owner)
            .setName("test-consumer")
            .setUsername("test-consumer")
            .setFact(RELATED_FACT_INITIAL, "4")
            .setFact("cpu.cpu_socket(s)", "2")
            .setFact(UNRELATED_FACT_INITIAL, "true");

        Product product1 = TestUtil.createProduct("installed-1");
        Product product2 = TestUtil.createProduct("installed-2");

        ConsumerInstalledProduct cip1 = new ConsumerInstalledProduct()
            .setProductId(product1.getId())
            .setProductName(product1.getName());

        ConsumerInstalledProduct cip2 = new ConsumerInstalledProduct()
            .setProductId(product2.getId())
            .setProductName(product2.getName());

        consumer.setInstalledProducts(Set.of(cip1, cip2));

        return consumer;
    }

    private ComplianceStatus createStatusOf(Consumer consumer) {
        // Need to make sure that dates are exactly the same
        // as this method will be called more than once.
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2001);
        cal.set(Calendar.MONTH, 4);
        cal.set(Calendar.DATE, 12);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        ComplianceStatus initialStatus = new ComplianceStatus(cal.getTime());

        addMonths(cal, 4);
        Date time = cal.getTime();
        initialStatus.addNonCompliantProduct(NON_COMPLIANT_PRODUCT_1);
        initialStatus.addNonCompliantProduct(NON_COMPLIANT_PRODUCT_2);
        initialStatus.addCompliantProduct(COMPLIANT_PRODUCT_1,
            createEntitlement(time, owner, consumer, COMPLIANT_PRODUCT_1));
        initialStatus.addCompliantProduct(COMPLIANT_PRODUCT_2,
            createEntitlement(time, owner, consumer, COMPLIANT_PRODUCT_2));
        initialStatus.addPartiallyCompliantProduct("p5", createEntitlement(time, owner, consumer, "p5"));
        initialStatus.addPartialStack("p-stack", createEntitlement(time, owner, consumer, "p6"));

        ComplianceReason reason1 = createReason("TEST-REASON-1");
        initialStatus.getReasons().add(reason1);
        ComplianceReason reason2 = createReason("TEST-REASON-2");
        initialStatus.getReasons().add(reason2);


        return initialStatus;
    }

    private ComplianceReason createReason(String key) {
        ComplianceReason reason = new ComplianceReason();
        reason.setKey(key);
        reason.setMessage(key + ": This is a test!");

        Map<String, String> attrs = new HashMap<>();
        attrs.put(key + "-attr", key + "-value");
        reason.setAttributes(attrs);
        return reason;
    }

    private Entitlement createEntitlement(Date time, Owner owner, Consumer consumer,
        String productId) {

        Product product = TestUtil.createProduct(productId, productId);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId(product.getId() + "pool");
        pool.setUpdated(time);

        Entitlement ent = new Entitlement();
        ent.setOwner(owner);
        ent.setPool(pool);
        ent.setOwner(owner);
        ent.setQuantity(2);
        ent.setCreated(time);
        ent.setUpdated(time);
        ent.setId(product.getId() + "ent");
        consumer.addEntitlement(ent);
        return ent;
    }

    private void addMonths(Calendar cal, int numOfMonths) {
        cal.add(Calendar.MONTH, numOfMonths);
    }

    private String generateHash(ComplianceStatus status, Consumer consumer) {
        ComplianceStatusHasher hasher = new ComplianceStatusHasher(consumer, status);
        return hasher.hash();
    }

}
