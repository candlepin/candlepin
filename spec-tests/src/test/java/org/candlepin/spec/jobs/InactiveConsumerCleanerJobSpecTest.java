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
package org.candlepin.spec.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SpecTest
public class InactiveConsumerCleanerJobSpecTest {

    private static final String JOB_KEY = "InactiveConsumerCleanerJob";
    private static final int DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS = 397;

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

        ConsumerDTO consumerWithManifest = Consumers.random(owner, ConsumerTypes.Candlepin)
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC));
        ConsumerDTO consumerWithEntitlement = Consumers.random(owner)
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC));
        ConsumerDTO activeConsumer = Consumers.random(owner)
            .lastCheckin(activeTime.atOffset(ZoneOffset.UTC));
        ConsumerDTO activeConsumer2 = Consumers.random(owner)
            .lastCheckin(activeTime.atOffset(ZoneOffset.UTC));

        ConsumerDTO inactiveConsumer = consumerApi.createConsumer(Consumers.random(owner)
            .lastCheckin(inactiveTime.atOffset(ZoneOffset.UTC)));
        consumerWithManifest = consumerApi
            .createConsumer(consumerWithManifest, null, owner.getKey(), null, false);
        consumerWithEntitlement = consumerApi
            .createConsumer(consumerWithEntitlement, null, owner.getKey(), null, false);
        activeConsumer = consumerApi.createConsumer(activeConsumer, null, owner.getKey(), null, false);
        activeConsumer2 = consumerApi.createConsumer(activeConsumer2, null, owner.getKey(), null, false);

        createProductAndPoolForConsumer(consumerWithEntitlement);

        String jobId = jobsClient.scheduleJob(JOB_KEY).getId();
        AsyncJobStatusDTO status = jobsClient.waitForJob(jobId);
        assertEquals("FINISHED", status.getState());

        // Verify that the inactive consumer has been deleted.
        final String inactiveConsumer1Uuid = inactiveConsumer.getUuid();
        assertGone(() -> consumerApi.getConsumer(inactiveConsumer1Uuid));
        // Verify that the inactive consumer has been moved to the deleted_consumers table.
        List<DeletedConsumerDTO> deletedConsumers = deletedConsumerApi
            .listByDate(inactiveConsumer.getCreated());
        // Assert that other tests might have created an inactive consumer
        assertThat(deletedConsumers).hasSizeGreaterThanOrEqualTo(1);
        Optional<DeletedConsumerDTO> deletedConsumer = deletedConsumers.stream()
            .filter(consumer -> inactiveConsumer.getUuid().equals(consumer.getConsumerUuid()))
            .findFirst();
        assertThat(deletedConsumer)
            .isPresent()
            .hasValueSatisfying(consumer -> {
                assertThat(consumer.getConsumerName()).isEqualTo(inactiveConsumer.getName());
                assertThat(consumer.getOwnerId()).isEqualTo(inactiveConsumer.getOwner().getId());
                assertThat(consumer.getOwnerKey()).isEqualTo(inactiveConsumer.getOwner().getKey());
                assertThat(consumer.getOwnerDisplayName())
                    .isEqualTo(inactiveConsumer.getOwner().getDisplayName());
            });

        // Verify that the active consumers have not been deleted.
        compareConsumers(consumerWithManifest, consumerApi.getConsumer(consumerWithManifest.getUuid()));
        compareConsumers(consumerWithEntitlement, consumerApi.getConsumer(consumerWithEntitlement.getUuid()));
        compareConsumers(activeConsumer, consumerApi.getConsumer(activeConsumer.getUuid()));
        compareConsumers(activeConsumer2, consumerApi.getConsumer(activeConsumer2.getUuid()));
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
