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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.validation.ConstraintViolationException;



public class ConsumerTest extends DatabaseTestFixture {

    @Inject private ConsumerResource consumerResource;

    private Owner owner;
    private Product rhel;
    private Product jboss;
    private Consumer consumer;
    private ConsumerType consumerType;
    private static final String CONSUMER_TYPE_NAME = "test-consumer-type";
    private static final String CONSUMER_NAME = "Test Consumer";
    private static final String USER_NAME = "user33908";

    @BeforeEach
    public void setUpTestObjects() {
        owner = this.createOwner("Example Corporation");
        rhel = this.createProduct("rhel", "Red Hat Enterprise Linux", owner);
        jboss = this.createProduct("jboss", "JBoss", owner);

        consumerType = new ConsumerType(CONSUMER_TYPE_NAME);
        consumerTypeCurator.create(consumerType);
        consumer = new Consumer(CONSUMER_NAME, USER_NAME, owner, consumerType);
        consumer.setFact("foo", "bar");
        consumer.setFact("foo1", "bar1");

        consumerCurator.create(consumer);
    }

    @Test
    public void testConsumerTypeRequired() {
        Consumer newConsumer = new Consumer().setUuid(Util.generateUUID());
        newConsumer.setName("cname");
        newConsumer.setOwner(owner);

        assertThrows(ConstraintViolationException.class, () -> consumerCurator.create(newConsumer));
    }

    @Test
    public void testConsumerNameLengthCreate() {
        String name = "";
        for (int x = 0; x < 300; x++) {
            name += "x";
        }

        Consumer newConsumer = new Consumer().setUuid(Util.generateUUID());
        newConsumer.setName(name);
        newConsumer.setOwner(owner);
        newConsumer.setType(consumerType);

        assertThrows(ConstraintViolationException.class, () -> consumerCurator.create(newConsumer));
    }

    @Test
    public void testConsumerNameLengthUpdate() {
        String name = "";
        for (int x = 0; x < 300; x++) {
            name += "x";
        }

        Consumer newConsumer = new Consumer().setUuid(Util.generateUUID());
        newConsumer.setName(name);
        newConsumer.setOwner(owner);
        newConsumer.setType(consumerType);

        assertThrows(ConstraintViolationException.class, () -> consumerCurator.update(newConsumer));
    }

    @Test
    public void testLookup() throws Exception {
        Consumer lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(consumer.getId(), lookedUp.getId());
        assertEquals(consumer.getName(), lookedUp.getName());

        ConsumerType ctypeExpected = this.consumerTypeCurator.getConsumerType(consumer);
        ConsumerType ctypeActual = this.consumerTypeCurator.getConsumerType(lookedUp);

        assertEquals(ctypeExpected.getLabel(), ctypeActual.getLabel());
        assertNotNull(consumer.getUuid());
    }

    @Test
    public void testSetInitialization() throws Exception {
        Consumer noFacts = new Consumer(CONSUMER_NAME, USER_NAME, owner, consumerType);
        consumerCurator.create(noFacts);
        noFacts = consumerCurator.get(noFacts.getId());
        assertNotNull(noFacts.getFacts());
        assertNotNull(noFacts.getInstalledProducts());
        assertNotNull(noFacts.getGuestIds());
    }

    @Test
    public void testInfo() {
        Consumer lookedUp = consumerCurator.get(consumer.getId());
        Map<String, String> metadata = lookedUp.getFacts();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getFacts().get("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getFacts().get("foo1"));
    }

    @Test
    public void ensureUpdatedDateChangesOnUpdate() throws Exception {
        Date beforeUpdateDate = consumer.getUpdated();

        // Create a new consumer, can't re-use reference to the old:
        ConsumerDTO newConsumer = new ConsumerDTO();
        newConsumer.setUuid(consumer.getUuid());
        newConsumer.putFacts("FACT", "FACT_VALUE");

        consumerResource.updateConsumer(consumer.getUuid(), newConsumer, mock(Principal.class));

        Consumer lookedUp = consumerCurator.get(consumer.getId());
        Date lookedUpDate = lookedUp.getUpdated();
        assertEquals("FACT_VALUE", lookedUp.getFact("FACT"));

        assertTrue(beforeUpdateDate.before(lookedUpDate));
    }

    @Test
    public void testMetadataInfo() {
        Consumer consumer2 = new Consumer("consumer2", USER_NAME, owner, consumerType);
        consumer2.setFact("foo", "bar2");
        consumerCurator.create(consumer2);

        Consumer lookedUp = consumerCurator.get(consumer.getId());
        Map<String, String> metadata = lookedUp.getFacts();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getFacts().get("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getFacts().get("foo1"));

        Consumer lookedUp2 = consumerCurator.get(consumer2.getId());
        metadata = lookedUp2.getFacts();
        assertEquals(1, metadata.keySet().size());
        assertEquals("bar2", metadata.get("foo"));
    }

    @Test
    public void testModifyMetadata() {
        consumer.setFact("foo", "notbar");
        consumerCurator.merge(consumer);

        Consumer lookedUp = consumerCurator.get(consumer.getId());
        assertEquals("notbar", lookedUp.getFact("foo"));
    }

    @Test
    public void testRemoveConsumedProducts() {
        consumerCurator.delete(consumerCurator.get(consumer.getId()));
        assertNull(consumerCurator.get(consumer.getId()));
    }

    @Test
    public void testgetByUuidNonExistent() {
        consumerCurator.findByUuid("this is not a uuid!");
    }

    @Test
    public void testgetByUuid() {
        Consumer consumer2 = new Consumer("consumer2", USER_NAME, owner, consumerType);
        consumerCurator.create(consumer2);

        Consumer lookedUp = consumerCurator.findByUuid(consumer2.getUuid());
        assertEquals(lookedUp.getUuid(), consumer2.getUuid());
    }

    @Test
    public void testAddEntitlements() {
        Owner o = createOwner();
        Product newProduct = this.createProduct(o);

        Pool pool = createPool(o, newProduct, 1000L, TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));

        this.getEntityManager().persist(pool.getOwner());
        this.getEntityManager().persist(pool);

        Entitlement e1 = createEntitlement(pool, consumer, o);
        Entitlement e2 = createEntitlement(pool, consumer, o);
        Entitlement e3 = createEntitlement(pool, consumer, o);
        this.getEntityManager().persist(e1);
        this.getEntityManager().persist(e2);
        this.getEntityManager().persist(e3);

        consumer.addEntitlement(e1);
        consumer.addEntitlement(e2);
        consumer.addEntitlement(e3);
        consumerCurator.merge(consumer);

        Consumer lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(3, lookedUp.getEntitlements().size());
    }

    private Entitlement createEntitlement(Pool pool, Consumer c, Owner o) {
        Entitlement e = new Entitlement(pool, c, o, 1);
        e.setId(Util.generateDbUUID());
        return e;
    }

    @Test
    public void testNullType() {
        Consumer c = new Consumer("name", USER_NAME, owner, null);
        assertNotNull(c);
    }

    @Test
    public void canDeleteSelf() {
        setupPrincipal(new ConsumerPrincipal(consumer, owner));

        consumerCurator.delete(consumer);

        assertNull(consumerCurator.get(consumer.getId()));
    }

    @Test
    public void factsEqual() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", "two");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "two");
        second.setFact("key3", "3");

        assertTrue(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void defaultFactsEqual() {
        assertTrue(new Consumer().factsAreEqual(new Consumer().getFacts()));
    }

    @Test
    public void factsDifferentValues() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", "two");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "2");
        second.setFact("key3", "3");

        assertFalse(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void factsSecondMissing() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", "two");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");

        assertFalse(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void factsFirstMissing() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "2");
        second.setFact("key3", "3");

        assertFalse(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void factsEqualNull() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", null);

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", null);

        assertTrue(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void factsFirstNull() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", null);

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "two");

        assertFalse(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void findbugsNullDereferenceNullFacts() {
        Consumer first = new Consumer();
        first.setFacts(null);

        Consumer second = new Consumer();
        second.setFact("key1", "1");

        assertFalse(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void findbugsSecondListIsNull() {
        Consumer first = new Consumer();
        first.setFact("key1", "1");

        Consumer second = new Consumer();
        second.setFacts(null);

        assertFalse(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void factsSecondNull() {
        Consumer first = new Consumer();
        first.setFact("key1", "1");

        Consumer second = new Consumer();
        second.setFact("key1", null);

        assertFalse(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void factsBothNull() {
        Consumer first = new Consumer();
        first.setFacts(null);

        Consumer second = new Consumer();
        second.setFacts(null);

        assertTrue(first.factsAreEqual(second.getFacts()));
    }

    @Test
    public void testLookupUsersConsumer() {
        String newUsername = "newusername";

        // Need to make sure another consumer already exists, different type:
        Consumer existing = new Consumer("existing consumer", newUsername, owner,
            consumerType);
        consumerCurator.create(existing);

        ConsumerType personType = new ConsumerType(ConsumerTypeEnum.PERSON);
        consumerTypeCurator.create(personType);

        User user = new User(newUsername, "password");
        userCurator.create(user);

        Role adminRole = createAdminRole(owner);
        adminRole.addUser(user);
        roleCurator.create(adminRole);

        assertNull(consumerCurator.findByUser(user));

        consumer = new Consumer(CONSUMER_NAME, newUsername, owner, personType);
        consumerCurator.create(consumer);
        assertEquals(consumer, consumerCurator.findByUser(user));
    }

    @Test
    public void testInstalledProducts() throws Exception {
        Consumer lookedUp = consumerCurator.get(consumer.getId());
        lookedUp.addInstalledProduct(
            new ConsumerInstalledProduct("someproduct", "someproductname")
        );
        lookedUp.addInstalledProduct(
            new ConsumerInstalledProduct("someproduct2", "someproductname2")
        );
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(2, lookedUp.getInstalledProducts().size());
        ConsumerInstalledProduct installed = lookedUp.getInstalledProducts().
            iterator().next();
        lookedUp.getInstalledProducts().remove(installed);
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(1, lookedUp.getInstalledProducts().size());
    }

    @Test
    public void testGuests() throws Exception {
        Consumer lookedUp = consumerCurator.get(consumer.getId());
        lookedUp.addGuestId(new GuestId("guest1"));
        lookedUp.addGuestId(new GuestId("guest2"));
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(2, lookedUp.getGuestIds().size());
        GuestId installed = lookedUp.getGuestIds().
            iterator().next();
        lookedUp.getGuestIds().remove(installed);
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(1, lookedUp.getGuestIds().size());
    }

    @Test
    public void testRoleConvertedToEmpty() throws Exception {
        Consumer consumer = new Consumer("consumer1", "consumer1", owner, consumerType);
        consumerCurator.create(consumer);

        String cid = consumer.getId();

        consumer.setRole("test_role");
        consumer = consumerCurator.merge(consumer);
        consumerCurator.flush();

        consumer = null;
        consumerCurator.clear();

        consumer = consumerCurator.get(cid);
        assertEquals("test_role", consumer.getRole());

        consumer.setRole("");
        consumer = consumerCurator.merge(consumer);
        consumerCurator.flush();

        consumer = null;
        consumerCurator.clear();

        consumer = consumerCurator.get(cid);
        assertTrue(consumer.getRole().isEmpty());
    }

    @Test
    public void testUsageConvertedToEmpty() throws Exception {
        Consumer consumer = new Consumer("consumer1", "consumer1", owner, consumerType);
        consumerCurator.create(consumer);

        String cid = consumer.getId();

        consumer.setUsage("test_usage");
        consumer = consumerCurator.merge(consumer);
        consumerCurator.flush();

        consumer = null;
        consumerCurator.clear();

        consumer = consumerCurator.get(cid);
        assertEquals("test_usage", consumer.getUsage());

        consumer.setUsage("");
        consumer = consumerCurator.merge(consumer);
        consumerCurator.flush();

        consumer = null;
        consumerCurator.clear();

        consumer = consumerCurator.get(cid);
        assertTrue(consumer.getUsage().isEmpty());
    }

    @Test
    public void testCloudProfileFactDidNotChange() {
        Consumer consumer = new Consumer();
        consumer.setFact("dmi.bios.vendor", "vendorA");
        consumer.setFact("lscpu.model", "78");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put("dmi.bios.vendor", "vendorA");
        newFacts.put("lscpu.model", "100");

        // this should return false because the only cloud fact  the consumer has did not change
        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactDidNotChangeWhenPassingSingleFact() {
        Consumer consumer = new Consumer();
        consumer.setFact("dmi.bios.vendor", "vendorA");
        consumer.setFact("lscpu.model", "78");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put("dmi.bios.vendor", "vendorA");

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactOnEmptyExistingFacts() {
        Consumer consumer = new Consumer();

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put("dmi.bios.vendor", "vendorA");

        assertTrue(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactOnEmptyIncomingFacts() {
        Consumer consumer = new Consumer();
        consumer.setFact("dmi.bios.vendor", "vendorA");

        Map<String, String> newFacts = null;

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactOnNullValueOfIncomingFacts() {
        Consumer consumer = new Consumer();
        consumer.setFact("dmi.bios.vendor", "vendorA");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put("dmi.bios.vendor", null);

        assertTrue(consumer.checkForCloudProfileFacts(newFacts));

        newFacts = new HashMap<>();
        newFacts.put("null", "vendorA");

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactExistingIncomingFacts() {
        Consumer consumer = new Consumer();
        consumer.setFact("dmi.bios.vendor", "vendorA");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put("dmi.bios.vendor", "vendorA");

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }
}
