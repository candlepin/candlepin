/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.spec.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.DeletedConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.DeletedConsumerApi;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * These tests are run in insolation because it is possible that an inactive consumer cleaner job from one
 * test can delete consumers from another test if they are run in parallel. This can lead to sporadic test
 * failures.
 */
@Isolated
@SpecTest
public class InactiveConsumerCleanerJobSpecTest {

    private static final String JOB_KEY = "InactiveConsumerCleanerJob";
    private static final int DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS = 397;
    private static final int DEFAULT_ANON_OWNER_LAST_CHECKED_IN_RETENTION_IN_DAYS = 30;

    private static ConsumerClient consumerApi;
    private static DeletedConsumerApi deletedConsumerApi;
    private static OwnerApi ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static JobsClient jobsClient;

    private OwnerDTO owner;

    @BeforeAll
    public static void beforeAll() {
        ApiClient client = ApiClients.admin();
        consumerApi = client.consumers();
        deletedConsumerApi = client.deletedConsumers();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        jobsClient = client.jobs();
    }

    @BeforeEach
    void setUp() {
        owner = ownerApi.createOwner(Owners.random());
    }

    @Test
    public void shouldDeleteInactiveConsumers() {
        Instant inactiveTime = Instant.now()
            .minus(DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10, ChronoUnit.DAYS);
        Instant activeTime = Instant.now()
            .minus(DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS - 10, ChronoUnit.DAYS);
        Instant anonymousOwnerInactiveTime = Instant.now()
            .minus(DEFAULT_ANON_OWNER_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10, ChronoUnit.DAYS);
        Instant anonymousOwnerActiveTime = Instant.now()
            .minus(DEFAULT_ANON_OWNER_LAST_CHECKED_IN_RETENTION_IN_DAYS - 10, ChronoUnit.DAYS);

        Map<String, ConsumerDTO> inactiveConsumers = new HashMap<>();
        Map<String, ConsumerDTO> activeConsumers = new HashMap<>();

        // Point in time before we create our consumers. We will use this date time for retrieving
        // deleted consumers.
        OffsetDateTime deletedConsumersCreationDate = OffsetDateTime.now();

        ConsumerDTO consumerWithManifest = Consumers.random(owner, ConsumerTypes.Candlepin)
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC));
        ConsumerDTO consumerWithEntitlement = Consumers.random(owner)
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC));
        ConsumerDTO activeConsumer = Consumers.random(owner)
            .lastCheckin(activeTime.atOffset(ZoneOffset.UTC));
        ConsumerDTO activeConsumer2 = Consumers.random(owner)
            .lastCheckin(activeTime.atOffset(ZoneOffset.UTC));

        consumerWithManifest = consumerApi
            .createConsumer(consumerWithManifest, null, owner.getKey(), null, false);
        consumerWithEntitlement = consumerApi
            .createConsumer(consumerWithEntitlement, null, owner.getKey(), null, false);
        activeConsumer = consumerApi.createConsumer(activeConsumer, null, owner.getKey(), null, false);
        activeConsumer2 = consumerApi.createConsumer(activeConsumer2, null, owner.getKey(), null, false);
        createProductAndPoolForConsumer(consumerWithEntitlement);
        activeConsumers.put(consumerWithManifest.getUuid(), consumerWithManifest);
        activeConsumers.put(consumerWithEntitlement.getUuid(), consumerWithEntitlement);
        activeConsumers.put(activeConsumer.getUuid(), activeConsumer);
        activeConsumers.put(activeConsumer2.getUuid(), activeConsumer2);

        ConsumerDTO inactiveConsumer = consumerApi.createConsumer(Consumers.random(owner)
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC)));
        inactiveConsumers.put(inactiveConsumer.getUuid(), inactiveConsumer);

        // Create an active and inactive consumer for an anonymous owner.
        // The retention policy is different for consumers in anonymous owners.
        OwnerDTO anonOwner = ownerApi.createOwner(Owners.randomSca().anonymous(true));
        ConsumerDTO activeConsumerFromAnonOwner = consumerApi.createConsumer(Consumers.random(anonOwner)
            .lastCheckin(anonymousOwnerActiveTime.atOffset(ZoneOffset.UTC)));
        activeConsumers.put(activeConsumerFromAnonOwner.getUuid(), activeConsumerFromAnonOwner);

        ConsumerDTO inactiveConsumerFromAnonOwner = consumerApi.createConsumer(Consumers.random(anonOwner)
            .lastCheckin(anonymousOwnerInactiveTime.atOffset(ZoneOffset.UTC)));
        inactiveConsumers.put(inactiveConsumerFromAnonOwner.getUuid(), inactiveConsumerFromAnonOwner);

        String jobId = jobsClient.scheduleJob(JOB_KEY).getId();
        AsyncJobStatusDTO status = jobsClient.waitForJob(jobId);
        assertThatJob(status)
            .isFinished();

        // Verify that the inactive consumers have been deleted
        for (String uuid : inactiveConsumers.keySet()) {
            assertGone(() -> consumerApi.getConsumer(uuid));
        }

        // Verify that the inactive consumers have been moved to the deleted_consumers table
        List<DeletedConsumerDTO> deletedConsumers = deletedConsumerApi
            .listByDate(deletedConsumersCreationDate, 1, 50, "desc", "created");
        // Assert that other tests might have created an inactive consumer
        assertThat(deletedConsumers).hasSizeGreaterThanOrEqualTo(inactiveConsumers.size());

        List<DeletedConsumerDTO> actual = deletedConsumers.stream()
            .filter(consumer -> inactiveConsumers.keySet().contains(consumer.getConsumerUuid()))
            .toList();

        // Make sure we have the exact size that we are expecting so that we do not have any extra or missing
        // deletions
        assertEquals(inactiveConsumers.size(), actual.size());

        for (DeletedConsumerDTO deletedConsumer : actual) {
            ConsumerDTO expected = inactiveConsumers.get(deletedConsumer.getConsumerUuid());
            assertNotNull(expected);

            assertThat(deletedConsumer)
                .returns(expected.getUuid(), DeletedConsumerDTO::getConsumerUuid)
                .returns(expected.getName(), DeletedConsumerDTO::getConsumerName)
                .returns(expected.getOwner().getId(), DeletedConsumerDTO::getOwnerId)
                .returns(expected.getOwner().getKey(), DeletedConsumerDTO::getOwnerKey)
                .returns(expected.getOwner().getDisplayName(), DeletedConsumerDTO::getOwnerDisplayName)
                .returns("admin", DeletedConsumerDTO::getPrincipalName);
        }

        // Verify that the active consumers have not been deleted
        for (Entry<String, ConsumerDTO> entry : activeConsumers.entrySet()) {
            compareConsumers(entry.getValue(), consumerApi.getConsumer(entry.getKey()));
        }
    }

    @Test
    public void shouldCreateNewConsumerDeletionRecordWhenConsumerUuidReused() {
        final String uuid = UUID.randomUUID().toString();

        Instant inactiveTime = Instant.now()
            .minus(DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10, ChronoUnit.DAYS);

        ConsumerDTO c1 = Consumers.random(owner)
            .uuid(uuid)
            .name("consumer1")
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC));
        c1 = consumerApi.createConsumer(c1, null, owner.getKey(), null, false);

        // Delete Consumer1 (simulate previous deletion in the past)
        consumerApi.deleteConsumer(c1.getUuid());

        // Capture the first deletion record (must exist now)
        List<DeletedConsumerDTO> afterFirstDeletion = deletedConsumerApi
            .listByDate(c1.getCreated(), 1, 200, "desc", "created");
        List<DeletedConsumerDTO> firstDeletionForUuid = afterFirstDeletion.stream()
            .filter(dc -> uuid.equals(dc.getConsumerUuid()))
            .toList();

        assertThat(firstDeletionForUuid)
            .hasSize(1);

        OffsetDateTime firstDeletedCreated = firstDeletionForUuid.get(0).getCreated();

        // create Consumer2 with the SAME UUID, make it inactive
        ConsumerDTO c2 = Consumers.random(owner)
            .uuid(uuid)
            .name("consumer2")
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC));
        c2 = consumerApi.createConsumer(c2, null, owner.getKey(), null, false);

        String jobId = jobsClient.scheduleJob(JOB_KEY).getId();
        AsyncJobStatusDTO status = jobsClient.waitForJob(jobId);
        assertThatJob(status)
            .isFinished();

        // ensure only ONE row exists for this UUID and it reflects the 2nd deletion
        List<DeletedConsumerDTO> afterSecondDeletion = deletedConsumerApi
            .listByDate(firstDeletedCreated.minusDays(1), 1, 200, "desc", "created");
        List<DeletedConsumerDTO> rowsForUuid = afterSecondDeletion.stream()
            .filter(dc -> uuid.equals(dc.getConsumerUuid()))
            .toList();

        assertThat(rowsForUuid)
            .isNotNull()
            .hasSize(2);

        for (DeletedConsumerDTO rec : rowsForUuid) {
            ConsumerDTO source = rec.getConsumerName().equals(c1.getName()) ? c1 : c2;

            assertThat(rec)
                .returns(source.getUuid(), DeletedConsumerDTO::getConsumerUuid)
                .returns(source.getName(), DeletedConsumerDTO::getConsumerName)
                .returns(source.getOwner().getId(), DeletedConsumerDTO::getOwnerId)
                .returns(source.getOwner().getKey(), DeletedConsumerDTO::getOwnerKey)
                .returns(source.getOwner().getDisplayName(), DeletedConsumerDTO::getOwnerDisplayName);
        }

        // And verify that the live consumer is now gone
        assertGone(() -> consumerApi.getConsumer(uuid));
    }

    private void createProductAndPoolForConsumer(ConsumerDTO consumer) {
        ProductDTO product = new ProductDTO();
        product.setId(StringUtil.random("ID"));
        product.setName(StringUtil.random("Test Product"));
        product.setMultiplier(100L);
        product = ownerProductApi.createProduct(owner.getKey(), product);

        PoolDTO pool = new PoolDTO();
        pool.setProductId(product.getId());
        pool.setStartDate(Instant.now().atOffset(ZoneOffset.UTC));
        pool.setEndDate(Instant.now().plus(10, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC));
        pool = ownerApi.createPool(owner.getKey(), pool);

        consumerApi.bind(consumer.getUuid(), pool.getId(), new ArrayList<>(), 1, "",
            "", false, null, new ArrayList<>());
    }

    private void compareConsumers(ConsumerDTO expected, ConsumerDTO actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getUuid(), actual.getUuid());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getCreated(), actual.getCreated());
    }

}
