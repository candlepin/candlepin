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
package org.candlepin.async.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestEventSink;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Stream;


public class InactiveConsumerCleanerJobTest extends DatabaseTestFixture {

    private InactiveConsumerCleanerJob inactiveConsumerCleanerJob;
    private ConsumerType consumerType;
    private TestEventSink eventSink;

    @Override
    @BeforeEach
    public void init() throws Exception {
        super.init(false);

        this.consumerType = this.createConsumerType(false);

        this.eventSink = this.injector.getInstance(TestEventSink.class);
        EventFactory eventFactory = this.injector.getInstance(EventFactory.class);

        inactiveConsumerCleanerJob = new InactiveConsumerCleanerJob(this.config,
            this.consumerCurator,
            this.identityCertificateCurator,
            this.caCertCurator,
            this.certSerialCurator,
            this.eventSink,
            eventFactory);
    }

    @Test
    public void testExecutionWithInactiveCheckedInTime() throws JobExecutionException {
        Owner owner = this.createOwner(TestUtil.randomString(), TestUtil.randomString());

        Consumer inactiveConsumer = this.createConsumer(owner,
            InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10);
        Consumer activeConsumer = this.createConsumer(owner,
            InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS - 10);

        JobExecutionContext context = mock(JobExecutionContext.class);
        inactiveConsumerCleanerJob.execute(context);

        consumerCurator.flush();
        consumerCurator.clear();

        Collection<Consumer> activeConsumers = this.consumerCurator.getConsumers(
            List.of(inactiveConsumer.getId(), activeConsumer.getId()));

        assertThat(activeConsumers)
            .isNotNull()
            .containsExactly(activeConsumer);

        List<DeletedConsumer> dcRecords = this.deletedConsumerCurator
            .findByConsumerUuid(inactiveConsumer.getUuid());

        assertThat(dcRecords)
            .isNotNull()
            .singleElement()
            .returns(inactiveConsumer.getId(), DeletedConsumer::getId)
            .returns(inactiveConsumer.getUuid(), DeletedConsumer::getConsumerUuid)
            .returns(inactiveConsumer.getName(), DeletedConsumer::getConsumerName);

        DeletedConsumer dcRecord = this.deletedConsumerCurator.findByConsumerId(inactiveConsumer.getId());

        assertThat(dcRecord)
            .isNotNull()
            .returns(inactiveConsumer.getId(), DeletedConsumer::getId)
            .returns(inactiveConsumer.getUuid(), DeletedConsumer::getConsumerUuid)
            .returns(inactiveConsumer.getName(), DeletedConsumer::getConsumerName);
    }

    @Test
    public void testExecutionShouldSetAnonymousOwnerFieldOnEvent() throws Exception {
        int inactiveLastCheckin = InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10;

        Owner anonymousOwner = TestUtil.createOwner(TestUtil.randomString(), TestUtil.randomString())
            .setId(null)
            .setAnonymous(true);
        anonymousOwner = this.ownerCurator.create(anonymousOwner);

        this.createConsumer(anonymousOwner, inactiveLastCheckin);

        Owner owner = TestUtil.createOwner(TestUtil.randomString(), TestUtil.randomString())
            .setId(null)
            .setAnonymous(false);
        owner = this.ownerCurator.create(owner);

        this.createConsumer(owner, inactiveLastCheckin);

        consumerCurator.flush();
        consumerCurator.clear();

        JobExecutionContext context = mock(JobExecutionContext.class);
        inactiveConsumerCleanerJob.execute(context);

        consumerCurator.flush();
        consumerCurator.clear();

        Queue<Event> dispatchedEvents = this.eventSink.getDispatchedEvents();
        assertThat(dispatchedEvents)
            .hasSize(2)
            .extracting(Event::getOwnerKey)
            .containsExactlyInAnyOrder(anonymousOwner.getKey(), owner.getKey());

        Event event1 = dispatchedEvents.poll();
        Event event2 = dispatchedEvents.poll();
        if (anonymousOwner.getKey().equals(event1.getOwnerKey())) {
            assertThat(event1).returns(true, Event::isOwnerAnonymous);
            assertThat(event2).returns(false, Event::isOwnerAnonymous);
        }
        else {
            assertThat(event1).returns(false, Event::isOwnerAnonymous);
            assertThat(event2).returns(true, Event::isOwnerAnonymous);
        }
    }

    @Test
    public void testExecutionWithInactiveConsumersInSingleOrg() throws Exception {
        Owner owner = this.createOwner(TestUtil.randomString(), TestUtil.randomString());
        int activeLastCheckin = InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS - 10;
        int inactiveLastCheckin = InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10;

        List<Consumer> activeConsumers = Stream.generate(() -> this.createConsumer(owner, activeLastCheckin))
            .limit(10)
            .toList();

        List<Consumer> inactiveConsumers =
            Stream.generate(() -> this.createConsumer(owner, inactiveLastCheckin))
                .limit(10)
                .toList();

        consumerCurator.flush();
        consumerCurator.clear();

        JobExecutionContext context = mock(JobExecutionContext.class);
        inactiveConsumerCleanerJob.execute(context);

        consumerCurator.flush();
        consumerCurator.clear();

        // Verify all of our inactive consumers were removed
        for (Consumer consumer : inactiveConsumers) {
            DeletedConsumer deleted = this.deletedConsumerCurator.findByConsumerId(consumer.getId());
            assertThat(deleted)
                .isNotNull()
                .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
                .returns(consumer.getName(), DeletedConsumer::getConsumerName);
        }

        // Verify the active consumers were not removed
        for (Consumer consumer : activeConsumers) {
            DeletedConsumer deleted = this.deletedConsumerCurator.findByConsumerId(consumer.getId());
            assertNull(deleted);
        }

        // Verify an event was dispatched that contains all of the UUIDs of the inactive consumers
        Queue<Event> dispatchedEvents = this.eventSink.getDispatchedEvents();
        assertEquals(1, dispatchedEvents.size());

        Event event = dispatchedEvents.poll();
        this.validateBulkDeletionEvent(event, owner, inactiveConsumers);
    }

    @Test
    public void testExecutionWithInactiveConsumersInMultipleOrgs() throws Exception {
        int activeLastCheckin = InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS - 10;
        int inactiveLastCheckin = InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10;

        Map<Owner, List<Consumer>> activeConsumersMap = new HashMap<>();
        Map<Owner, List<Consumer>> inactiveConsumersMap = new HashMap<>();
        Map<String, Owner> ownerMap = new HashMap<>();

        boolean anonymous = false;
        for (int i = 0; i < 5; ++i) {
            Owner owner = this.createOwner(TestUtil.randomString(), TestUtil.randomString())
                .setAnonymous(anonymous);

            // Set half the owners to an anonymous state
            anonymous = !anonymous;

            List<Consumer> activeConsumers =
                Stream.generate(() -> this.createConsumer(owner, activeLastCheckin))
                    .limit(10)
                    .toList();

            List<Consumer> inactiveConsumers =
                Stream.generate(() -> this.createConsumer(owner, inactiveLastCheckin))
                    .limit(10)
                    .toList();

            ownerMap.put(owner.getKey(), owner);
            activeConsumersMap.put(owner, activeConsumers);
            inactiveConsumersMap.put(owner, inactiveConsumers);
        }

        consumerCurator.flush();
        consumerCurator.clear();

        JobExecutionContext context = mock(JobExecutionContext.class);
        inactiveConsumerCleanerJob.execute(context);

        consumerCurator.flush();
        consumerCurator.clear();

        // Verify all of our inactive consumers were removed
        for (List<Consumer> inactiveConsumers : inactiveConsumersMap.values()) {
            for (Consumer consumer : inactiveConsumers) {
                DeletedConsumer deleted = this.deletedConsumerCurator.findByConsumerId(consumer.getId());

                assertNotNull(deleted);
                assertEquals(consumer.getUuid(), deleted.getConsumerUuid());
                assertEquals(consumer.getName(), deleted.getConsumerName());
            }
        }

        // Verify the active consumers were not removed
        for (List<Consumer> activeConsumers : activeConsumersMap.values()) {
            for (Consumer consumer : activeConsumers) {
                DeletedConsumer deleted = this.deletedConsumerCurator.findByConsumerId(consumer.getId());

                assertNull(deleted);
            }
        }

        // Verify an event was dispatched that contains all of the UUIDs of the inactive consumers
        Queue<Event> dispatchedEvents = this.eventSink.getDispatchedEvents();
        assertEquals(inactiveConsumersMap.size(), dispatchedEvents.size());

        for (Event event = dispatchedEvents.poll(); event != null; event = dispatchedEvents.poll()) {
            String ownerKey = event.getOwnerKey();
            Owner owner = ownerMap.get(ownerKey);
            List<Consumer> inactiveConsumers = inactiveConsumersMap.get(owner);

            assertNotNull(owner);
            assertNotNull(inactiveConsumers);

            this.validateBulkDeletionEvent(event, owner, inactiveConsumers);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "0", "-50" })
    public void testExecutionWithInvalidCheckedInRetentionConfig(int rententionDays)
        throws JobExecutionException {
        setRetentionDaysConfiguration(InactiveConsumerCleanerJob.CFG_LAST_CHECKED_IN_RETENTION_IN_DAYS,
            rententionDays);

        JobExecutionContext context = mock(JobExecutionContext.class);
        assertThrows(JobExecutionException.class, () -> inactiveConsumerCleanerJob.execute(context));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "0", "-50" })
    public void testExecutionWithInvalidLastUpdatedRetentionConfig(int rententionDays)
        throws JobExecutionException {
        setRetentionDaysConfiguration(InactiveConsumerCleanerJob.CFG_LAST_UPDATED_IN_RETENTION_IN_DAYS,
            rententionDays);

        JobExecutionContext context = mock(JobExecutionContext.class);
        assertThrows(JobExecutionException.class, () -> inactiveConsumerCleanerJob.execute(context));
    }

    private Consumer createConsumer(Owner owner, Integer lastCheckedInDaysAgo) {
        Consumer newConsumer = new Consumer()
            .setOwner(owner)
            .setType(consumerType)
            .setName(TestUtil.randomString());

        if (lastCheckedInDaysAgo != null) {
            newConsumer.setLastCheckin(Util.addDaysToDt(lastCheckedInDaysAgo * -1));
        }

        return this.consumerCurator.create(newConsumer);
    }

    private void setRetentionDaysConfiguration(String configurationName, int retentionDays) {
        String configuration = ConfigProperties.jobConfig(
            InactiveConsumerCleanerJob.JOB_KEY,
            configurationName);
        this.config.setProperty(configuration, String.valueOf(retentionDays));
    }

    /**
     * Validates that the event is a bulk deletion event for the target org with the given inactive consumers
     */
    private void validateBulkDeletionEvent(Event event, Owner owner, Collection<Consumer> inactiveConsumers) {
        assertThat(event)
            .isNotNull()
            .returns(Event.Target.CONSUMER, Event::getTarget)
            .returns(Event.Type.BULK_DELETION, Event::getType)
            .returns(owner.getKey(), Event::getOwnerKey)
            .returns(owner.getAnonymous(), Event::isOwnerAnonymous);

        Map<String, Object> eventData = event.getEventData();
        assertNotNull(eventData);

        List<String> eventConsumerUuids = (List<String>) eventData.get("consumerUuids");
        assertNotNull(eventConsumerUuids);

        List<String> inactiveConsumerUuids = inactiveConsumers.stream()
            .map(Consumer::getUuid)
            .toList();

        assertThat(eventConsumerUuids)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(inactiveConsumerUuids);
    }
}
