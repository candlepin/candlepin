/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.DeletedConsumerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.resource.ConsumerApi;
import org.candlepin.resource.DeletedConsumerApi;
import org.candlepin.resource.OwnerApi;
import org.candlepin.resource.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.JobsClient;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.SpecTestFixture;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@SpecTest
public class InactiveConsumerCleanerJobSpecTest extends SpecTestFixture {

    private static final String JOB_KEY = "InactiveConsumerCleanerJob";
    private static final int DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS = 397;

    private ConsumerApi consumerApi;
    private DeletedConsumerApi deletedConsumerApi;
    private OwnerApi ownerApi;
    private OwnerProductApi ownerProductApi;
    private JobsClient jobsClient;

    private OwnerDTO owner;

    @BeforeAll
    public void beforeAll() throws ApiException {
        ApiClient client = ApiClients.admin();
        consumerApi = client.consumers();
        deletedConsumerApi = client.deletedConsumers();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        jobsClient = getClient(JobsClient.class);

        if (owner == null) {
            owner = ownerApi.createOwner(Owners.random());
        }
    }

    @Test
    @DisplayName("should delete inactive consumers")
    public void shouldDeleteInactiveConsumers() throws Exception {
        Instant inactiveTime = Instant.now()
            .minus(DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10, ChronoUnit.DAYS);
        Instant activeTime = Instant.now()
            .minus(DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS - 10, ChronoUnit.DAYS);

        ConsumerDTO inactiveConsumer = new Consumers.Builder()
            .withOwner(owner)
            .withLastCheckin(inactiveTime.atOffset(ZoneOffset.UTC))
            .build();

        ConsumerTypeDTO manifestType = new ConsumerTypeDTO();
        manifestType.setManifest(true);
        manifestType.setLabel("candlepin");
        ConsumerDTO consumerWithManifest = new Consumers.Builder()
            .withOwner(owner)
            .withType(manifestType)
            .withLastCheckin(inactiveTime.atOffset(ZoneOffset.UTC))
            .build();
        ConsumerDTO consumerWithEntitlement = new Consumers.Builder()
            .withOwner(owner)
            .withLastCheckin(inactiveTime.atOffset(ZoneOffset.UTC))
            .build();
        ConsumerDTO activeConsumer = new Consumers.Builder()
            .withOwner(owner)
            .withLastCheckin(activeTime.atOffset(ZoneOffset.UTC))
            .build();
        ConsumerDTO activeConsumer2 = new Consumers.Builder()
            .withOwner(owner)
            .withLastCheckin(activeTime.atOffset(ZoneOffset.UTC))
            .build();

        inactiveConsumer = consumerApi.createConsumer(inactiveConsumer, null, owner.getKey(), null, false);
        consumerWithManifest = consumerApi
            .createConsumer(consumerWithManifest, null, owner.getKey(), null, false);
        consumerWithEntitlement = consumerApi
            .createConsumer(consumerWithEntitlement, null, owner.getKey(), null, false);
        activeConsumer = consumerApi.createConsumer(activeConsumer, null, owner.getKey(), null, false);
        activeConsumer2 = consumerApi.createConsumer(activeConsumer2, null, owner.getKey(), null, false);

        createProductAndPoolForConsumer(consumerWithEntitlement);

        String jobId = jobsClient.scheduleJob(JOB_KEY).getId();
        AsyncJobStatusDTO status = jobsClient.waitForJobToComplete(jobId);
        assertEquals("FINISHED", status.getState());

        // Verify that the inactive consumer has been deleted.
        final String inactiveConsumer1Uuid = inactiveConsumer.getUuid();
        assertGone(() -> consumerApi.getConsumer(inactiveConsumer1Uuid));
        // Verify that the inactive consumer has been moved to the deleted_consumers table.
        List<DeletedConsumerDTO> deletedConsumers = deletedConsumerApi
            .listByDate(inactiveConsumer.getCreated().toString());
        assertEquals(1, deletedConsumers.size());
        assertEquals(inactiveConsumer.getUuid(), deletedConsumers.get(0).getConsumerUuid());
        assertEquals(inactiveConsumer.getName(), deletedConsumers.get(0).getConsumerName());
        assertEquals(inactiveConsumer.getOwner().getId(), deletedConsumers.get(0).getOwnerId());
        assertEquals(inactiveConsumer.getOwner().getDisplayName(), deletedConsumers.get(0)
            .getOwnerDisplayName());
        assertEquals(inactiveConsumer.getOwner().getKey(), deletedConsumers.get(0).getOwnerKey());

        // Verify that the active consumers have not been deleted.
        compareConsumers(consumerWithManifest, consumerApi.getConsumer(consumerWithManifest.getUuid()));
        compareConsumers(consumerWithEntitlement, consumerApi.getConsumer(consumerWithEntitlement.getUuid()));
        compareConsumers(activeConsumer, consumerApi.getConsumer(activeConsumer.getUuid()));
        compareConsumers(activeConsumer2, consumerApi.getConsumer(activeConsumer2.getUuid()));
    }

    private void createProductAndPoolForConsumer(ConsumerDTO consumer) throws ApiException {
        ProductDTO product = new ProductDTO();
        product.setId(StringUtil.random("ID"));
        product.setName(StringUtil.random("Test Product"));
        product.setMultiplier(100L);
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);

        PoolDTO pool = new PoolDTO();
        pool.setProductId(product.getId());
        pool.setStartDate(Instant.now().atOffset(ZoneOffset.UTC));
        pool.setEndDate(Instant.now().plus(10, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC));
        pool = ownerApi.createPool(owner.getKey(), pool);

        consumerApi.bind(consumer.getUuid(), pool.getId(), new ArrayList<>(), 1, "",
            "", false, "", new ArrayList<>());
    }

    private void compareConsumers(ConsumerDTO expected, ConsumerDTO actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getUuid(), actual.getUuid());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getCreated(), actual.getCreated());
    }

}
