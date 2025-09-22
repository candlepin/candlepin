/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

package org.candlepin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventFactory;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestEventSink;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

class ConsumerMigrationTest extends DatabaseTestFixture {

    @AfterEach
    void tearDown() {
        // Each test needs to close the transaction so that migration correctly
        // runs each batch of consumers in a separate transaction.
        this.beginTransaction();
    }

    @Test
    void shouldMigrateAll() {
        TestEventSink eventSink = this.injector.getInstance(TestEventSink.class);
        EventFactory eventFactory = this.injector.getInstance(EventFactory.class);

        ConsumerMigration migration = new ConsumerMigration(this.ownerCurator, this.consumerCurator,
            this.identityCertificateCurator, this.caCertCurator, this.certSerialCurator, eventFactory,
            eventSink, this.i18n, this.config);

        Owner anonOwner = this.ownerCurator.saveOrUpdate(this.createOwner().setAnonymous(true));
        Owner destOwner = this.createOwner();
        Consumer consumer1 = this.createConsumerWithCerts(anonOwner);
        Consumer consumer2 = this.createConsumerWithCerts(anonOwner);
        Consumer consumer3 = this.createConsumerWithCerts(anonOwner);
        this.commitTransaction();

        migration.migrate(anonOwner.getKey(), destOwner.getKey());

        assertThat(this.ownerCurator.getConsumerIds(destOwner)).hasSize(3);
        assertThat(this.ownerCurator.getConsumerIds(anonOwner)).isEmpty();

        // Verify that we emit a consumer bulk migration event
        Queue<Event> dispatchedEvents = eventSink.getDispatchedEvents();
        assertEquals(1, dispatchedEvents.size());

        List<String> expectedUuids = List.of(consumer1.getUuid(), consumer2.getUuid(), consumer3.getUuid());
        Event event = dispatchedEvents.poll();
        assertBulkMigrationEvent(expectedUuids, anonOwner.getKey(), true, destOwner.getKey(), false, event);
    }

    @Test
    void shouldSkipFailedBatches() {
        this.config.setProperty(ConfigProperties.CONSUMER_MIGRATION_BATCH_SIZE, "1");
        ConsumerCurator consumerCuratorSpy = spy(this.consumerCurator);
        when(consumerCuratorSpy.lockAndLoadUuids(anyCollection()))
            .thenThrow(new IllegalArgumentException())
            .thenReturn(null);

        TestEventSink eventSink = this.injector.getInstance(TestEventSink.class);
        EventFactory eventFactory = this.injector.getInstance(EventFactory.class);
        ConsumerMigration migration = new ConsumerMigration(this.ownerCurator, consumerCuratorSpy,
            this.identityCertificateCurator, this.caCertCurator, this.certSerialCurator, eventFactory,
            eventSink, this.i18n, this.config);

        Owner anonOwner = this.ownerCurator.saveOrUpdate(this.createOwner().setAnonymous(true));
        Owner destOwner = this.createOwner();
        Consumer consumer1 = this.createConsumerWithCerts(anonOwner);
        Consumer consumer2 = this.createConsumerWithCerts(anonOwner);
        Consumer consumer3 = this.createConsumerWithCerts(anonOwner);
        this.commitTransaction();

        assertThatThrownBy(() -> migration.migrate(anonOwner.getKey(), destOwner.getKey()))
            .isInstanceOf(ConsumerMigrationFailedException.class);

        assertThat(this.ownerCurator.getConsumerIds(destOwner)).hasSize(2);
        assertThat(this.ownerCurator.getConsumerIds(anonOwner)).hasSize(1);

        // Verify that we emit a consumer bulk migration events appropriately. The first batch for consumer1
        // will fail and so we should expect an event for consumer2 and consumer3.
        Queue<Event> dispatchedEvents = eventSink.getDispatchedEvents();
        assertEquals(2, dispatchedEvents.size());

        Event event1 = dispatchedEvents.poll();
        assertBulkMigrationEvent(List.of(consumer2.getUuid()), anonOwner.getKey(), true,
            destOwner.getKey(), false, event1);

        Event event2 = dispatchedEvents.poll();
        assertBulkMigrationEvent(List.of(consumer3.getUuid()), anonOwner.getKey(), true,
            destOwner.getKey(), false, event2);
    }

    @Test
    void shouldRevokeConsumerCertificates() {
        TestEventSink eventSink = this.injector.getInstance(TestEventSink.class);
        EventFactory eventFactory = this.injector.getInstance(EventFactory.class);
        ConsumerMigration migration = new ConsumerMigration(this.ownerCurator, this.consumerCurator,
            this.identityCertificateCurator, this.caCertCurator, this.certSerialCurator, eventFactory,
            eventSink, this.i18n, this.config);

        Owner anonOwner = this.ownerCurator.saveOrUpdate(this.createOwner().setAnonymous(true));
        Owner destOwner = this.createOwner();
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.commitTransaction();

        migration.migrate(anonOwner.getKey(), destOwner.getKey());
        this.consumerCurator.flush();
        this.consumerCurator.clear();

        List<Consumer> migratedConsumers = this.consumerCurator.listAllByIds(
            this.ownerCurator.getConsumerIds(destOwner));
        assertThat(migratedConsumers)
            .allSatisfy(consumer -> assertThat(consumer)
                .returns(null, Consumer::getIdCert)
                .returns(null, Consumer::getContentAccessCert)
            );
    }

    private Consumer createConsumerWithCerts(Owner anonOwner) {
        CertificateSerial serial = new CertificateSerial();
        this.certSerialCurator.saveOrUpdate(serial);

        IdentityCertificate identCert = new IdentityCertificate();
        identCert.setCert("test_cert");
        identCert.setKey("test_key");
        identCert.setSerial(serial);
        this.identityCertificateCurator.saveOrUpdate(identCert);

        Consumer consumer = this.createConsumer(anonOwner);
        consumer.setIdCert(identCert);

        return this.consumerCurator.saveOrUpdate(consumer);
    }

    private void assertBulkMigrationEvent(Collection<String> consumerUuids,
        String sourceOwnerKey, Boolean sourceOwnerIsAnonymous, String destinationOwnerKey,
        Boolean destinationOwnerIsAnonymous, Event event) {

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("consumerUuids", consumerUuids);
        expectedData.put("sourceOwner", Map.of("key", sourceOwnerKey, "anonymous", sourceOwnerIsAnonymous));
        expectedData.put("destinationOwner", Map.of("key", destinationOwnerKey, "anonymous",
            destinationOwnerIsAnonymous));

        assertThat(event)
            .isNotNull()
            .returns(Type.BULK_MIGRATION, Event::getType)
            .returns(Target.CONSUMER, Event::getTarget)
            .returns(null, Event::getTargetName)
            .returns(null, Event::getEntityId)
            .returns(null, Event::getOwnerKey)
            .returns(null, Event::isOwnerAnonymous)
            .returns(null, Event::getConsumerUuid)
            .returns(null, Event::getReferenceId)
            .returns(null, Event::getReferenceType)
            .returns(expectedData, Event::getEventData);
    }
}
