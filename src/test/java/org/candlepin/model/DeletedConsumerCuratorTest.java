/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.DeletedConsumerCurator.DeletedConsumerQueryArguments;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.OffsetDateTime;
import java.util.List;



// TODO: FIXME: This entire test suite is very questionable in terms of quality. Deleted consumers on the
// whole is in a very weird/bad state, and at some point both need a major overhaul and/or refactor.

/**
 * DeletedConsumerCuratorTest
 */
public class DeletedConsumerCuratorTest extends DatabaseTestFixture {

    private OffsetDateTime twoResultsDate;
    private OffsetDateTime oneResultDate;
    private Owner owner;
    private ConsumerType ct;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();

        DeletedConsumer dc1 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId("test_consumer_1")
            .setConsumerUuid("abcde")
            .setConsumerName("consumerName")
            .setOwnerId("10")
            .setOwnerKey("key")
            .setOwnerDisplayName("name"));

        Thread.sleep(5);

        // save the current time, DCs created after this will have
        // a created timestamp after this time
        this.twoResultsDate = OffsetDateTime.now(); // wtf? seriously?
        DeletedConsumer dc2 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId("test_consumer_2")
            .setConsumerUuid("fghij")
            .setConsumerName("consumerName")
            .setOwnerId("10")
            .setOwnerKey("key")
            .setOwnerDisplayName("name"));

        Thread.sleep(5);

        this.oneResultDate = OffsetDateTime.now(); // FIXME: Sigh... this is awful. Remove this.
        DeletedConsumer dc3 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId("test_consumer_3")
            .setConsumerUuid("klmno")
            .setConsumerName("consumerName")
            .setOwnerId("20")
            .setOwnerKey("key")
            .setOwnerDisplayName("name"));

        // Why are we creating a global owner instance *after* we go out of way to poorly fake owner data in
        // the above deleted consumer records!?
        this.owner = this.createOwner("test-owner", "Test Owner");
        this.ct = this.consumerTypeCurator.create(new ConsumerType(ConsumerTypeEnum.SYSTEM));
    }

    public void testFindByConsumerId() {
        DeletedConsumer dc1 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId("test_consumer_1")
            .setConsumerUuid("abcde")
            .setConsumerName("consumer1")
            .setOwnerId("10")
            .setOwnerKey("owner_key-1")
            .setOwnerDisplayName("owner 1"));

        DeletedConsumer dc2 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId("test_consumer_2")
            .setConsumerUuid("abcde")
            .setConsumerName("consumer2")
            .setOwnerId("20")
            .setOwnerKey("owner_key-2")
            .setOwnerDisplayName("owner 2"));

        DeletedConsumer deletedConsumer = this.deletedConsumerCurator.findByConsumerId(dc1.getId());
        assertThat(deletedConsumer)
            .isNotNull()
            .returns(dc1.getId(), DeletedConsumer::getId)
            .returns(dc1.getConsumerUuid(), DeletedConsumer::getConsumerUuid)
            .returns(dc1.getConsumerName(), DeletedConsumer::getConsumerName)
            .returns(dc1.getOwnerId(), DeletedConsumer::getOwnerId)
            .returns(dc1.getOwnerDisplayName(), DeletedConsumer::getOwnerDisplayName)
            .returns(dc1.getOwnerKey(), DeletedConsumer::getOwnerKey);
    }

    @Test
    public void testFindByConsumerIdWithNonExistingDeletedConsumer() {
        assertNull(this.deletedConsumerCurator.findByConsumerId(TestUtil.randomString()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testFindByConsumerIdWithInvalidConsumerUuid(String id) {
        assertNull(this.deletedConsumerCurator.findByConsumerId(id));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testFindByConsumerUuidWithInvalidConsumerUuid(String uuid) {
        assertThat(this.deletedConsumerCurator.findByConsumerUuid(uuid))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testFindByConsumerUuid() {
        String uuid = "abcde";
        List<DeletedConsumer> found = this.deletedConsumerCurator.findByConsumerUuid(uuid);

        assertThat(found)
            .isNotNull()
            .singleElement()
            .returns(uuid, DeletedConsumer::getConsumerUuid);
    }

    @Test
    public void testFindByConsumerUuidMultipleUuidMatches() {
        String uuid = "test_uuid";

        DeletedConsumer dc1 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId(TestUtil.randomString("test_consumer-"))
            .setConsumerUuid(uuid)
            .setConsumerName("consumer_name-1")
            .setOwnerId("test_owner-1")
            .setOwnerKey("test_owner-1")
            .setOwnerDisplayName("Test owner 1"));

        DeletedConsumer dc2 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId(TestUtil.randomString("test_consumer-"))
            .setConsumerUuid(uuid)
            .setConsumerName("consumer_name-1")
            .setOwnerId("test_owner-1")
            .setOwnerKey("test_owner-1")
            .setOwnerDisplayName("Test owner 1"));

        DeletedConsumer dc3 = this.deletedConsumerCurator.create(new DeletedConsumer()
            .setId(TestUtil.randomString("test_consumer-"))
            .setConsumerUuid("alt_uuid")
            .setConsumerName("consumer_name-1")
            .setOwnerId("test_owner-1")
            .setOwnerKey("test_owner-1")
            .setOwnerDisplayName("Test owner 1"));

        List<DeletedConsumer> result = this.deletedConsumerCurator.findByConsumerUuid(uuid);

        assertThat(result)
            .isNotNull()
            .containsExactlyInAnyOrder(dc1, dc2);
    }

    @Test
    public void testFindByConsumerUuidWithNonExistingDeletedConsumer() {
        assertThat(this.deletedConsumerCurator.findByConsumerUuid(TestUtil.randomString()))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void byOwnerId() {
        List<DeletedConsumer> found = deletedConsumerCurator.findByOwnerId("10");
        assertEquals(2, found.size());
    }

    @Test
    public void byOwner() {
        Owner o = mock(Owner.class);
        when(o.getId()).thenReturn("20");
        List<DeletedConsumer> found = deletedConsumerCurator.findByOwner(o);
        assertEquals(1, found.size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testCountByConsumerUuidWithInvalidUuid(String uuid) {
        assertEquals(0, deletedConsumerCurator.countByConsumerUuid(uuid));
    }

    @Test
    public void testCountByConsumerUuid() {
        assertEquals(1, deletedConsumerCurator.countByConsumerUuid("abcde"));
        assertEquals(0, deletedConsumerCurator.countByConsumerUuid("dontfind"));
        assertEquals(1, deletedConsumerCurator.countByConsumerUuid("fghij"));
    }

    @Test
    public void findByDate() throws InterruptedException {
        DeletedConsumerQueryArguments args = new DeletedConsumerQueryArguments();
        args.setOffset(0);
        args.setDate(twoResultsDate);
        assertEquals(2, deletedConsumerCurator.listAll(args).size());
        args.setDate(oneResultDate);
        assertEquals(1, deletedConsumerCurator.listAll(args).size());
        Thread.sleep(2000);
        args.setDate(OffsetDateTime.now());
        assertEquals(0, deletedConsumerCurator.listAll(args).size());
    }

    @Test
    public void descOrderByDate() {
        DeletedConsumerQueryArguments args = new DeletedConsumerQueryArguments();
        args.setOffset(0);
        args.addOrder("created", true);
        args.setDate(twoResultsDate);
        DeletedConsumer newest = deletedConsumerCurator.listAll(args).get(0);
        assertEquals("klmno", newest.getConsumerUuid());
    }


    @Test
    public void testGetDeletedConsumerCount() {
        DeletedConsumerQueryArguments args = new DeletedConsumerQueryArguments();
        args.setDate(twoResultsDate);
        Long result = this.deletedConsumerCurator.getDeletedConsumerCount(args);
        assertEquals(2L, result);
    }

    @Test
    public void descOrderByOwnerId() {
        DeletedConsumer newest = deletedConsumerCurator.findByOwnerId("10").get(0);
        assertEquals("fghij", newest.getConsumerUuid());
    }

    @Test
    public void testCreateDeletedConsumers() {
        Consumer consumer1 = this.consumerCurator.create(new Consumer()
            .setName("consumer-1")
            .setUsername("testUser")
            .setOwner(this.owner)
            .setType(this.ct));

        Consumer consumer2 = this.consumerCurator.create(new Consumer()
            .setName("consumer-2")
            .setUsername("testUser")
            .setOwner(this.owner)
            .setType(this.ct));

        int actual = this.deletedConsumerCurator.createDeletedConsumers(
            List.of(consumer1.getId(), consumer2.getId(), "unknown-id"));

        assertEquals(2, actual);

        for (Consumer consumer : List.of(consumer1, consumer2)) {
            DeletedConsumer deletedConsumer = this.deletedConsumerCurator.findByConsumerId(consumer.getId());

            assertThat(deletedConsumer)
                .isNotNull()
                .returns(consumer.getId(), DeletedConsumer::getId)
                .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
                .returns(consumer.getName(), DeletedConsumer::getConsumerName)
                .returns(consumer.getOwner().getId(), DeletedConsumer::getOwnerId)
                .returns(consumer.getOwner().getDisplayName(), DeletedConsumer::getOwnerDisplayName)
                .returns(consumer.getOwner().getKey(), DeletedConsumer::getOwnerKey);
        }
    }

    @Test
    public void testCreateDeletedConsumersIsIdempotent() {
        Consumer consumer1 = this.consumerCurator.create(new Consumer()
            .setName("consumer-1")
            .setUsername("testUser")
            .setOwner(this.owner)
            .setType(this.ct));

        Consumer consumer2 = this.consumerCurator.create(new Consumer()
            .setName("consumer-2")
            .setUsername("testUser")
            .setOwner(this.owner)
            .setType(this.ct));

        int result1 = this.deletedConsumerCurator.createDeletedConsumers(
            List.of(consumer1.getId(), consumer2.getId(), "unknown-id"));

        assertEquals(2, result1);

        for (Consumer consumer : List.of(consumer1, consumer2)) {
            DeletedConsumer deletedConsumer = this.deletedConsumerCurator.findByConsumerId(consumer.getId());

            assertThat(deletedConsumer)
                .isNotNull()
                .returns(consumer.getId(), DeletedConsumer::getId)
                .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
                .returns(consumer.getName(), DeletedConsumer::getConsumerName)
                .returns(consumer.getOwner().getId(), DeletedConsumer::getOwnerId)
                .returns(consumer.getOwner().getDisplayName(), DeletedConsumer::getOwnerDisplayName)
                .returns(consumer.getOwner().getKey(), DeletedConsumer::getOwnerKey);
        }

        for (Consumer consumer : List.of(consumer1, consumer2)) {
            List<DeletedConsumer> deletedConsumers = this.deletedConsumerCurator
                .findByConsumerUuid(consumer.getUuid());

            assertThat(deletedConsumers)
                .isNotNull()
                .singleElement()
                .returns(consumer.getId(), DeletedConsumer::getId)
                .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
                .returns(consumer.getName(), DeletedConsumer::getConsumerName)
                .returns(consumer.getOwner().getId(), DeletedConsumer::getOwnerId)
                .returns(consumer.getOwner().getDisplayName(), DeletedConsumer::getOwnerDisplayName)
                .returns(consumer.getOwner().getKey(), DeletedConsumer::getOwnerKey);
        }

        // Change the consumer's names and then add new entries for them (note, the clear is necessary for
        // these changes to be persisted by Hibernate).
        consumer1.setName("consumer_1-updated");
        consumer2.setName("consumer_2-updated");

        this.consumerCurator.flush();
        this.consumerCurator.clear();

        // This deletion should still return 2, and the number of rows for the consumer when looked up by
        // ID or UUID should also only equal 2.
        int result2 = this.deletedConsumerCurator.createDeletedConsumers(
            List.of(consumer1.getId(), consumer2.getId(), "unknown-id"));

        assertEquals(2, result2);

        for (Consumer consumer : List.of(consumer1, consumer2)) {
            DeletedConsumer deletedConsumer = this.deletedConsumerCurator.findByConsumerId(consumer.getId());

            assertThat(deletedConsumer)
                .isNotNull()
                .returns(consumer.getId(), DeletedConsumer::getId)
                .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
                .returns(consumer.getName(), DeletedConsumer::getConsumerName)
                .returns(consumer.getOwner().getId(), DeletedConsumer::getOwnerId)
                .returns(consumer.getOwner().getDisplayName(), DeletedConsumer::getOwnerDisplayName)
                .returns(consumer.getOwner().getKey(), DeletedConsumer::getOwnerKey);
        }

        for (Consumer consumer : List.of(consumer1, consumer2)) {
            List<DeletedConsumer> deletedConsumers = this.deletedConsumerCurator
                .findByConsumerUuid(consumer.getUuid());

            assertThat(deletedConsumers)
                .isNotNull()
                .singleElement()
                .returns(consumer.getId(), DeletedConsumer::getId)
                .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
                .returns(consumer.getName(), DeletedConsumer::getConsumerName)
                .returns(consumer.getOwner().getId(), DeletedConsumer::getOwnerId)
                .returns(consumer.getOwner().getDisplayName(), DeletedConsumer::getOwnerDisplayName)
                .returns(consumer.getOwner().getKey(), DeletedConsumer::getOwnerKey);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testCreateDeletedConsumersHandlesNullAndEmptyValues(List<String> consumerIds) {
        // This should do nothing, but should not fail
        int actual = this.deletedConsumerCurator.createDeletedConsumers(consumerIds);
        assertEquals(0, actual);
    }

    private DeletedConsumer createDeletedConsumer(Consumer consumer) {
        DeletedConsumer deletedConsumer = new DeletedConsumer()
            .setId(consumer.getId())
            .setConsumerUuid(consumer.getUuid())
            .setConsumerName(consumer.getName())
            .setOwnerKey(consumer.getOwner().getKey())
            .setOwnerDisplayName(consumer.getOwner().getDisplayName())
            .setOwnerId(consumer.getOwner().getId());

        return this.deletedConsumerCurator.create(deletedConsumer);
    }
}
