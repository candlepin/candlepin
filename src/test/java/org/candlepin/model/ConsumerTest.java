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
package org.candlepin.model;

import static org.candlepin.model.CloudIdentifierFacts.AWS_ACCOUNT_ID;
import static org.candlepin.model.CloudIdentifierFacts.AWS_INSTANCE_ID;
import static org.candlepin.model.CloudIdentifierFacts.AZURE_OFFER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.exceptions.DuplicateEntryException;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.validation.ConstraintViolationException;



public class ConsumerTest extends DatabaseTestFixture {

    private ConsumerResource consumerResource;

    private Owner owner;
    private Consumer consumer;
    private ConsumerType consumerType;
    private static final String CONSUMER_TYPE_NAME = "test-consumer-type";
    private static final String CONSUMER_NAME = "Test Consumer";
    private static final String USER_NAME = "user33908";

    @BeforeEach
    public void setUpTestObjects() {
        owner = this.createOwner("Example Corporation");

        consumerType = new ConsumerType(CONSUMER_TYPE_NAME);
        consumerTypeCurator.create(consumerType);
        consumer = new Consumer()
            .setName(CONSUMER_NAME)
            .setUsername(USER_NAME)
            .setOwner(owner)
            .setType(consumerType)
            .setFact("foo", "bar")
            .setFact("foo1", "bar1");
        consumerResource = injector.getInstance(ConsumerResource.class);
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
    public void testLookup() {
        Consumer lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(consumer.getId(), lookedUp.getId());
        assertEquals(consumer.getName(), lookedUp.getName());

        ConsumerType ctypeExpected = this.consumerTypeCurator.getConsumerType(consumer);
        ConsumerType ctypeActual = this.consumerTypeCurator.getConsumerType(lookedUp);

        assertEquals(ctypeExpected.getLabel(), ctypeActual.getLabel());
        assertNotNull(consumer.getUuid());
    }

    @Test
    public void testSetInitialization() {
        Consumer noFacts = new Consumer()
            .setName(CONSUMER_NAME)
            .setUsername(USER_NAME)
            .setOwner(owner)
            .setType(consumerType);
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
    public void ensureUpdatedDateChangesOnUpdate() {
        Date beforeUpdateDate = consumer.getUpdated();

        // Create a new consumer, can't re-use reference to the old:
        ConsumerDTO newConsumer = new ConsumerDTO();
        newConsumer.setUuid(consumer.getUuid());
        newConsumer.putFactsItem("FACT", "FACT_VALUE");

        consumerResource.updateConsumer(consumer.getUuid(), newConsumer);

        Consumer lookedUp = consumerCurator.get(consumer.getId());
        Date lookedUpDate = lookedUp.getUpdated();
        assertEquals("FACT_VALUE", lookedUp.getFact("FACT"));

        assertTrue(beforeUpdateDate.before(lookedUpDate));
    }

    @Test
    public void testMetadataInfo() {
        Consumer consumer2 = new Consumer()
            .setName("consumer2")
            .setUsername(USER_NAME)
            .setOwner(owner)
            .setType(consumerType)
            .setFact("foo", "bar2");
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
        Consumer consumer2 = new Consumer()
            .setName("consumer2")
            .setUsername(USER_NAME)
            .setOwner(owner)
            .setType(consumerType);
        consumerCurator.create(consumer2);

        Consumer lookedUp = consumerCurator.findByUuid(consumer2.getUuid());
        assertEquals(lookedUp.getUuid(), consumer2.getUuid());
    }

    @Test
    public void testAddEntitlements() {
        Owner o = createOwner();
        Product newProduct = this.createProduct();

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
    public void testConsumerTypeCannotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> new Consumer().setType(null));
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
    public void testInstalledProducts() {
        Consumer lookedUp = consumerCurator.get(consumer.getId());
        lookedUp.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId("someproduct")
            .setProductName("someproductname"));
        lookedUp.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId("someproduct2")
            .setProductName("someproductname2"));
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(2, lookedUp.getInstalledProducts().size());
        ConsumerInstalledProduct installed = lookedUp.getInstalledProducts().iterator().next();
        lookedUp.removeInstalledProduct(installed);
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(1, lookedUp.getInstalledProducts().size());
    }

    @Test
    public void testGuests() {
        Consumer lookedUp = consumerCurator.get(consumer.getId());
        lookedUp.addGuestId(new GuestId("guest1"));
        lookedUp.addGuestId(new GuestId("guest2"));
        consumerCurator.update(lookedUp);

        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(2, lookedUp.getGuestIds().size());
        GuestId installed = lookedUp.getGuestIds().iterator().next();
        lookedUp.removeGuestId(installed);

        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.get(consumer.getId());
        assertEquals(1, lookedUp.getGuestIds().size());
    }

    @Test
    public void testRoleConvertedToEmpty() {
        Consumer consumer = new Consumer()
            .setName("consumer1")
            .setUsername("consumer1")
            .setOwner(owner)
            .setType(consumerType);

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
    public void testUsageConvertedToEmpty() {
        Consumer consumer = new Consumer()
            .setName("consumer1")
            .setUsername("consumer1")
            .setOwner(owner)
            .setType(consumerType);
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
        consumer.setFact(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");
        consumer.setFact("lscpu.model", "78");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");
        newFacts.put("lscpu.model", "100");

        // this should return false because the only cloud fact the consumer has did not change
        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactDidNotChangeWhenPassingSingleFact() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");
        consumer.setFact("lscpu.model", "78");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactOnEmptyExistingFacts() {
        Consumer consumer = new Consumer();

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");

        assertTrue(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactOnEmptyIncomingFacts() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");

        Map<String, String> newFacts = null;

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactOnNullValueOfIncomingFacts() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put(Consumer.Facts.DMI_BIOS_VENDOR, null);

        assertTrue(consumer.checkForCloudProfileFacts(newFacts));

        newFacts = new HashMap<>();
        newFacts.put("null", "vendorA");

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testCloudProfileFactExistingIncomingFacts() {
        Consumer consumer = new Consumer();
        consumer.setFact(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");

        Map<String, String> newFacts = new HashMap<>();
        newFacts.put(Consumer.Facts.DMI_BIOS_VENDOR, "vendorA");

        assertFalse(consumer.checkForCloudProfileFacts(newFacts));
    }

    @Test
    public void testServiceTypeConvertedToEmpty() {
        Consumer consumer = new Consumer()
            .setName("consumer1")
            .setUsername("consumer1")
            .setOwner(owner)
            .setType(consumerType);
        consumerCurator.create(consumer);

        String cid = consumer.getId();

        consumer.setServiceType("test_service_type");
        consumer = consumerCurator.merge(consumer);
        consumerCurator.flush();

        consumer = null;
        consumerCurator.clear();

        consumer = consumerCurator.get(cid);
        assertEquals("test_service_type", consumer.getServiceType());

        consumer.setServiceType("");
        consumer = consumerCurator.merge(consumer);
        consumerCurator.flush();

        consumer = null;
        consumerCurator.clear();

        consumer = consumerCurator.get(cid);
        assertTrue(consumer.getServiceType().isEmpty());
    }

    @Test
    public void shouldFailWithDuplicateEnvIds() {
        Consumer consumer = new Consumer()
            .setName("consumer1")
            .setUsername("consumer1")
            .setOwner(owner)
            .setType(consumerType);

        assertThrows(DuplicateEntryException.class,
            () -> consumer.setEnvironmentIds(List.of("env_1", "env_2", "env_1")));
    }

    @Test
    public void shouldCleanEnvIdsWhenNull() {
        Consumer consumer = new Consumer()
            .setName("consumer1")
            .setUsername("consumer1")
            .setOwner(owner)
            .setType(consumerType);

        consumer.setEnvironmentIds(List.of("env_1", "env_2"));
        assertEquals(2, consumer.getEnvironmentIds().size());

        consumer.setEnvironmentIds(null);
        assertTrue(consumer.getEnvironmentIds().isEmpty());
    }

    @Test
    public void shouldAddEnvironments() {
        Consumer consumer = new Consumer()
            .setName("consumer1")
            .setUsername("consumer1")
            .setOwner(owner)
            .setType(consumerType);

        assertTrue(consumer.getEnvironmentIds().isEmpty());

        consumer.setEnvironmentIds(List.of("env_1", "env_2"));
        assertEquals(2, consumer.getEnvironmentIds().size());
    }

    @Test
    public void deletingConsumerShouldDeleteIdCertAsWell() {
        Owner owner = createOwner();
        Consumer consumer = createConsumerWithIdCert(owner);
        this.consumerCurator.saveOrUpdate(consumer);

        assertNotNull(this.identityCertificateCurator.get(consumer.getIdCert().getId()));

        this.consumerCurator.delete(consumer);

        assertNull(this.identityCertificateCurator.get(consumer.getIdCert().getId()));
    }

    @Test
    public void testGetOwnerKey() {
        Owner owner = createOwner();
        Consumer consumer = createConsumerWithIdCert(owner);
        consumer = this.consumerCurator.saveOrUpdate(consumer);

        assertEquals(owner.getKey(), consumer.getOwnerKey());
    }

    @Test
    public void testGetOwnerKeyWithNoOwner() {
        Consumer consumer = new Consumer();

        assertNull(consumer.getOwnerKey());
    }

    @Test
    public void testSetConsumerCloudData() {
        Consumer consumer = new Consumer();
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        consumer.setConsumerCloudData(consumerCloudData);

        assertSame(consumerCloudData, consumer.getConsumerCloudData());
    }

    @ParameterizedTest(name = "Should return true for existing: {0} and incoming: {1}")
    @MethodSource("trueCases")
    void testCheckForCloudIdentifierFactsTrue(Map<String, String> existingFacts,
        Map<String, String> incomingFacts) {
        Consumer consumer = new Consumer();
        consumer.setFacts(existingFacts);

        assertTrue(consumer.checkForCloudIdentifierFacts(incomingFacts));
    }

    @ParameterizedTest(name = "Should return false for existing: {0} and incoming: {1}")
    @MethodSource("falseCases")
    void testCheckForCloudIdentifierFactsFalse(Map<String, String> existingFacts,
        Map<String, String> incomingFacts) {
        Consumer consumer = new Consumer();
        consumer.setFacts(existingFacts);

        assertFalse(consumer.checkForCloudIdentifierFacts(incomingFacts));
    }

    private static Stream<Arguments> trueCases() {
        return Stream.of(
            // Case 1: When existing facts are null and incoming contains a cloud identifier, return true.
            Arguments.of(null, Map.of(AWS_ACCOUNT_ID.getValue(), "123")),

            // Case 2: When the cloud identifier value differs (e.g., "123" vs "456"), return true.
            Arguments.of(Map.of(AWS_ACCOUNT_ID.getValue(), "123"),
                Map.of(AWS_ACCOUNT_ID.getValue(), "456")),

            // Case 3: When there are multiple cloud keys and at least one value differs, return true.
            Arguments.of(
                Map.of(AWS_ACCOUNT_ID.getValue(), "123", AWS_INSTANCE_ID.getValue(), "inst1",
                    AZURE_OFFER.getValue(), "offer1"),
                new HashMap<String, String>() {{
                        put(AWS_ACCOUNT_ID.getValue(), "123");
                        put(AWS_INSTANCE_ID.getValue(), "inst2");
                        put(AZURE_OFFER.getValue(), "offer1");
                    }}
            )
        );
    }

    private static Stream<Arguments> falseCases() {
        return Stream.of(
            // Case 1: When incoming facts are null, return false.
            Arguments.of(Map.of(AWS_ACCOUNT_ID.getValue(), "123"), null),

            // Case 2: When incoming facts are identical to the existing facts, return false.
            Arguments.of(
                Map.of(AWS_ACCOUNT_ID.getValue(), "123", AWS_INSTANCE_ID.getValue(), "inst1"),
                Map.of(AWS_ACCOUNT_ID.getValue(), "123", AWS_INSTANCE_ID.getValue(), "inst1")
            ),

            // Case 3: When incoming facts do not contain any cloud identifier key, return false.
            Arguments.of(Map.of("non_cloud_key", "value1"), Map.of("non_cloud_key", "value2")),

            // Case 4: When incoming contains an extra non-cloud key but cloud facts remain the same,
            // return false.
            Arguments.of(
                Map.of(AWS_ACCOUNT_ID.getValue(), "123", AWS_INSTANCE_ID.getValue(), "inst1",
                    AZURE_OFFER.getValue(), "offer1"),
                new HashMap<String, String>() {{
                        put(AWS_ACCOUNT_ID.getValue(), "123");
                        put(AWS_INSTANCE_ID.getValue(), "inst1");
                        put(AZURE_OFFER.getValue(), "offer1");
                        put("non_cloud_key", "some_value");
                    }}
            )
        );
    }

    private Consumer createConsumerWithIdCert(Owner owner) {
        IdentityCertificate idCert = createIdCert();
        Consumer consumer = createConsumer(owner)
            .setIdCert(idCert);
        return this.consumerCurator.saveOrUpdate(consumer);
    }

    private IdentityCertificate createIdCert() {
        IdentityCertificate idCert = TestUtil.createIdCert(TestUtil.createDateOffset(2, 0, 0));
        idCert.setId(null);
        this.certSerialCurator.create(idCert.getSerial());
        return this.identityCertificateCurator.create(idCert);
    }

}
