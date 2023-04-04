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
package org.candlepin.spec.hypervisors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.HypervisorIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.HypervisorTestData;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

@SpecTest
public class HypervisorHeartbeatSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;


    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
    }

    @Test
    public void shouldUpdateLastCheckinDateOfConsumersOfGivenReporter() {
        HypervisorTestData data = new HypervisorTestData();
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(client, owner);
        ApiClient userClient = ApiClients.basic(user);
        String reporterId = StringUtil.random("reporter");

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data, reporterId);
        OffsetDateTime startCheckin = hostConsumer.getLastCheckin();
        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);

        AsyncJobStatusDTO job = userClient.hypervisors()
            .hypervisorHeartbeatUpdate(owner.getKey(), reporterId);
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertThatJob(status).isFinished();
        }
        assertTrue(job.getName().contains("Hypervisor Heartbeat Update"));
        hostConsumer = hostConsumerClient.consumers().getConsumer(hostConsumer.getUuid());
        assertThat(hostConsumer.getLastCheckin()).isAfter(startCheckin);
    }

    private ConsumerDTO createHostConsumer(OwnerDTO owner, ApiClient userClient,
        HypervisorTestData data, String reporterId) {
        ConsumerDTO hostConsumer = Consumers.random(owner)
            .name(data.getExpectedHostName())
            .type(ConsumerTypes.Hypervisor.value())
            .facts(Map.of("test_fact", "fact_value"))
            .hypervisorId(new HypervisorIdDTO().hypervisorId(data.getExpectedHostHypervisorId()))
            .lastCheckin(OffsetDateTime.now().minusDays(5));
        hostConsumer.getHypervisorId().reporterId(reporterId);
        return userClient.consumers().createConsumer(hostConsumer);
    }
}
