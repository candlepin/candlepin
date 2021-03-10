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

package org.candlepin.policy.js.compliance.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComplianceStatusHasherTest {

    private ComplianceStatus status;
    private String initialHash;
    private Owner owner;

    @BeforeEach
    public void setUp() throws Exception {
        owner = new Owner("test-owner", "Test Owner");
        owner.setId("test-owner-id");

        Consumer consumer = createConsumer(owner);
        status = createInitialStatus(consumer);
        initialHash = generateHash(status, consumer);
    }

    @Test
    public void ensureSameHashWithNoChanges() {
        Consumer consumer = createConsumer(owner);
        assertEquals(initialHash, generateHash(createInitialStatus(consumer), consumer));
    }

    @Test
    public void ensureDifferentHashWhenNonCompliantProductsChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        testStatus.getNonCompliantProducts().clear();
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        testStatus.addNonCompliantProduct("p1");
        testStatus.addNonCompliantProduct("p2");
        assertEquals(initialHash, generateHash(testStatus, consumer));

        assertTrue(testStatus.getNonCompliantProducts().remove("p1"));
        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenCompliantProductCountChanges() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Set<Entitlement> ents = testStatus.getCompliantProducts().remove("p3");
        assertFalse(testStatus.getCompliantProducts().containsKey("p3"));
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        testStatus.getCompliantProducts().put("p3", ents);
        assertEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenCompliantProductEntitlementCountChanges() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Entitlement ent = createEntitlement(Calendar.getInstance(), owner, consumer, "test-ent");
        HashSet<Entitlement> ents = new HashSet<>();
        ents.add(ent);
        testStatus.getCompliantProducts().put(ent.getPool().getProductId(), ents);

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenPartiallyCompliantProductsChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Entitlement ent = createEntitlement(Calendar.getInstance(), owner, consumer, "test-ent");
        HashSet<Entitlement> ents = new HashSet<>();
        ents.add(ent);
        testStatus.getPartiallyCompliantProducts().put(ent.getPool().getProductId(), ents);

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenPartialStacksChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Entitlement ent = createEntitlement(Calendar.getInstance(), owner, consumer, "test-ent");
        HashSet<Entitlement> ents = new HashSet<>();
        ents.add(ent);
        testStatus.getPartialStacks().put("p-stack-2", ents);

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenReasonsChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        ComplianceReason reason = new ComplianceReason();
        reason.setKey("TEST-REASON-KEY");
        reason.setMessage("This is a test!");
        testStatus.getReasons().add(reason);

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenReasonKeyChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        ComplianceReason reason = testStatus.getReasons().iterator().next();
        reason.setKey("FOOF");

        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenReasonAttributeChanges() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        ComplianceReason reason = testStatus.getReasons().iterator().next();

        // Test new attribute map same values
        Map<String, String> newAttrs = new HashMap<>();
        newAttrs.putAll(reason.getAttributes());
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

    @Test
    public void ensureDifferentHashWhenConsumerFactsChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Map<String, String> initialConsumerFacts = consumer.getFacts();
        String firstFactKey = initialConsumerFacts.keySet().iterator().next();

        // Same facts, new map.
        consumer.setFacts(new HashMap<>(initialConsumerFacts));
        assertEquals(initialHash, generateHash(testStatus, consumer));

        // Facts cleared
        consumer.getFacts().clear();
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        // Fact added
        consumer.setFacts(new HashMap<>(initialConsumerFacts));
        assertEquals(initialHash, generateHash(testStatus, consumer));
        consumer.setFact("another", "fact");
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        // Fact removed
        consumer.setFacts(new HashMap<>(initialConsumerFacts));
        assertEquals(initialHash, generateHash(testStatus, consumer));
        consumer.getFacts().remove(firstFactKey);
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        // Fact changed
        consumer.setFacts(new HashMap<>(initialConsumerFacts));
        assertEquals(initialHash, generateHash(testStatus, consumer));
        consumer.setFact(firstFactKey, "Different Value");
        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void enssureDifferentHashWhenConsumerInstalledProductsChange() {
        Consumer consumer = createConsumer(owner);
        Product product = TestUtil.createProduct("Test Product");
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Set<ConsumerInstalledProduct> initialInstalled = consumer.getInstalledProducts();
        consumer.setInstalledProducts(new HashSet<>(initialInstalled));
        assertEquals(initialHash, generateHash(testStatus, consumer));

        consumer.setInstalledProducts(new HashSet<>(initialInstalled));
        assertEquals(initialHash, generateHash(testStatus, consumer));
        ConsumerInstalledProduct installed = new ConsumerInstalledProduct(product.getUuid(),
            product.getName());
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

        consumer.getInstalledProducts().remove(installed);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        consumer.getInstalledProducts().clear();
        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenConsumerEntitlementCountsChange() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Set<Entitlement> initialEnts = consumer.getEntitlements();
        consumer.setEntitlements(new HashSet<>(initialEnts));
        assertEquals(initialHash, generateHash(testStatus, consumer));

        // Create and add an entitlement to the consumer.
        Entitlement ent = createEntitlement(Calendar.getInstance(), owner, consumer, "tp");
        assertNotEquals(initialHash, generateHash(testStatus, consumer));

        consumer.removeEntitlement(ent);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        consumer.getEntitlements().clear();
        assertNotEquals(initialHash, generateHash(testStatus, consumer));
    }

    @Test
    public void ensureDifferentHashWhenConsumerEntitlementChanges() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

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
    public void ensureDifferentHashWhenEntitlementPoolChanges() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        assertEquals(initialHash, generateHash(testStatus, consumer));

        Entitlement ent = consumer.getEntitlements().iterator().next();
        Pool pool = ent.getPool();

        String poolId = pool.getId();
        Date poolStartDate = pool.getStartDate();
        Date poolEndDate = pool.getEndDate();
    }

    private Consumer createConsumer(Owner owner) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        Consumer consumer = new Consumer("test-consumer", "test-consumer", owner, ctype);
        consumer.setId("1");
        consumer.setUuid("12345");
        consumer.setFact("ram", "4");
        consumer.setFact("cores", "2");

        Product product1 = TestUtil.createProduct("installed-1");
        Product product2 = TestUtil.createProduct("installed-2");

        Set<ConsumerInstalledProduct> installedProducts = new HashSet<>();
        installedProducts.add(new ConsumerInstalledProduct(product1));
        installedProducts.add(new ConsumerInstalledProduct(product2));
        consumer.setInstalledProducts(installedProducts);

        return consumer;
    }

    private ComplianceStatus createInitialStatus(Consumer consumer) {
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
        initialStatus.addNonCompliantProduct("p1");
        initialStatus.addNonCompliantProduct("p2");
        initialStatus.addCompliantProduct("p3", createEntitlement(cal, owner, consumer, "p3"));
        initialStatus.addCompliantProduct("p4", createEntitlement(cal, owner, consumer, "p4"));
        initialStatus.addPartiallyCompliantProduct("p5", createEntitlement(cal, owner, consumer, "p5"));
        initialStatus.addPartialStack("p-stack", createEntitlement(cal, owner, consumer, "p6"));

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

    private Entitlement createEntitlement(Calendar cal, Owner owner, Consumer consumer,
        String productId) {

        Product product = TestUtil.createProduct(productId, productId);
        Pool pool = TestUtil.createPool(owner, product);
        pool.setId(product.getId() + "pool");
        pool.setUpdated(cal.getTime());

        Entitlement ent = new Entitlement();
        ent.setOwner(owner);
        ent.setPool(pool);
        ent.setOwner(owner);
        ent.setQuantity(2);
        ent.setCreated(cal.getTime());
        ent.setUpdated(cal.getTime());
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

    @Test
    public void shouldGenerateHashForConsumerWithNullFacts() {
        Consumer consumer = createConsumer(owner);
        ComplianceStatus testStatus = createInitialStatus(consumer);
        String initialHash = generateHash(testStatus, consumer);

        consumer.setFacts(null);
        // This should not throw NPE
        String secondHash = generateHash(testStatus, consumer);

        assertNotEquals(secondHash, initialHash);
    }

}
