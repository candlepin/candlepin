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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.HypervisorUpdateResultDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@SpecTest
public class ConsumerCheckinSpecTest {

    @Test
    public void shouldUpdateLastCheckinTimeOnConsumerUpdate() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        OffsetDateTime initialCheckin = consumer.getLastCheckin();

        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .doesNotReturn(initialCheckin, ConsumerDTO::getLastCheckin);
    }

    @Test
    public void shouldUpdateLastCheckinTimeOnEntitlementRegeneration() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        OffsetDateTime initialCheckin = consumer.getLastCheckin();

        consumerClient.consumers().regenerateEntitlementCertificates(consumer.getUuid(), null, true, false);

        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .doesNotReturn(initialCheckin, ConsumerDTO::getLastCheckin);
    }

    @Test
    public void shouldUpdateLastCheckingTimeWhenFetchingEntitlementCerts() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        OffsetDateTime initialCheckin = consumer.getLastCheckin();

        consumerClient.consumers().fetchCertificates(consumer.getUuid());

        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .doesNotReturn(initialCheckin, ConsumerDTO::getLastCheckin);
    }

    @Test
    public void shouldUpdateLastCheckinTimeWhenFetchingEntitlementCertSerials() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        OffsetDateTime initialCheckin = consumer.getLastCheckin();

        consumerClient.consumers().getEntitlementCertificateSerials(consumer.getUuid());

        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .doesNotReturn(initialCheckin, ConsumerDTO::getLastCheckin);
    }

    @Test
    public void shouldUpdateLastCheckinTimeOnAsyncHypervisorCheckin() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        OffsetDateTime initialCheckin = consumer.getLastCheckin();

        hypervisorCheckin(owner, consumerClient, StringUtil.random("name-"), StringUtil.random("id-"),
            List.of("guest-1", "guest-2", "guest-3"), Map.of("test_fact", "fact_value"),
            StringUtil.random("reporter-"), true);

        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .doesNotReturn(initialCheckin, ConsumerDTO::getLastCheckin);
    }

    @Test
    public void shouldNotUpdateLastCheckinWhenFetchingReleases() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        OffsetDateTime initialCheckin = consumer.getLastCheckin();

        consumerClient.consumers().getRelease(consumer.getUuid());

        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns(initialCheckin, ConsumerDTO::getLastCheckin);
    }

    private HypervisorUpdateResultDTO hypervisorCheckin(OwnerDTO owner, ApiClient consumerClient,
        String hypervisorName, String hypervisorId, List<String> guestIds, Map<String, String> facts,
        String reporterId, boolean createMissing) throws ApiException, IOException {
        JsonNode hostGuestMapping = getAsyncHostGuestMapping(hypervisorName, hypervisorId, guestIds, facts);
        AsyncJobStatusDTO job = consumerClient.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), createMissing, reporterId, hostGuestMapping.toString());
        HypervisorUpdateResultDTO resultData = null;
        if (job != null) {
            AsyncJobStatusDTO status = consumerClient.jobs().waitForJob(job.getId());
            assertThatJob(status).isFinished();
            resultData = getResultData(status);
        }

        return resultData;
    }

    private JsonNode getAsyncHostGuestMapping(String name, String id, List<String> expectedGuestIds,
        Map<String, String> facts) {
        List<Map<String, String>> guestIds = expectedGuestIds.stream()
            .map(gid -> Map.of("guestId", gid))
            .collect(Collectors.toList());

        Map<String, Object> hypervisor = Map.of(
            "name", name,
            "hypervisorId", Map.of("hypervisorId", id),
            "guestIds", guestIds,
            "facts", facts);

        Object node = Map.of("hypervisors", List.of(hypervisor));

        return ApiClient.MAPPER.valueToTree(node);
    }

    private HypervisorUpdateResultDTO getResultData(AsyncJobStatusDTO status) throws IOException {
        if (status == null || status.getResultData() == null) {
            return null;
        }

        return ApiClient.MAPPER.convertValue(status.getResultData(), HypervisorUpdateResultDTO.class);
    }
}
