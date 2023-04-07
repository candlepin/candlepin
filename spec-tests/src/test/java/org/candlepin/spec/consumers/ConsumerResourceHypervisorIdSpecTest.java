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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.HypervisorIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SpecTest
public class ConsumerResourceHypervisorIdSpecTest {

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldLetAConsumerRegisterWithAHypervisorId() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).hypervisorId(new HypervisorIdDTO().hypervisorId("aBcD")));
        assertThat(consumer)
            .returns("abcd", x -> x.getHypervisorId().getHypervisorId());
    }

    @Test
    public void shouldNotLetAConsumerRegisterWithAUsedHypervisorIdInTheSameOrg() {
        OwnerClient ownerClient = adminClient.owners();
        OwnerDTO someOwner = ownerClient.createOwner(Owners.random());
        ApiClient someUser = ApiClients.basic(UserUtil.createUser(adminClient, someOwner));
        ConsumerDTO consumer = someUser.consumers().createConsumer(
            Consumers.random(someOwner).hypervisorId(new HypervisorIdDTO().hypervisorId("aBcD")));
        // hypervisorId should always be set to lower case for the database constraint
        assertThat(consumer)
            .returns("abcd", x -> x.getHypervisorId().getHypervisorId());

        assertBadRequest(() -> someUser.consumers().createConsumer(
            Consumers.random(someOwner).hypervisorId(new HypervisorIdDTO().hypervisorId("aBcD"))));
    }

    @Test
    public void shouldAllowAConsumerToUpdateTheirHypervisorId() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getHypervisorId()).isNull();

        String hypervisorId = StringUtil.random("hYpervisor");
        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId)));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getHypervisorId())
            .returns(hypervisorId.toLowerCase(), HypervisorIdDTO::getHypervisorId);

        // Null update shouldn't modify the setting:
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO());
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getHypervisorId())
            .returns(hypervisorId.toLowerCase(), HypervisorIdDTO::getHypervisorId);
    }

    @Test
    public void shouldNotAllowAConsumerToUpdateTheirHypervisorIdToOneInUseByOwner() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        String hypervisorId = StringUtil.random("hYpervisor");
        ConsumerDTO consumer1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = userClient.consumers().createConsumer(Consumers.random(owner)
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId)));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);

        consumer1 = consumerClient1.consumers().getConsumer(consumer1.getUuid());
        assertThat(consumer1.getHypervisorId()).isNull();
        assertThat(consumer2.getHypervisorId())
            .returns(hypervisorId.toLowerCase(), HypervisorIdDTO::getHypervisorId);

        final String consumer1Uuid = consumer1.getUuid();
        assertBadRequest(() -> consumerClient1.consumers().updateConsumer(consumer1Uuid,
            new ConsumerDTO().hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId))))
            .hasMessageContaining(String.format("Hypervisor id: %s is already used.", hypervisorId));
        consumerClient2.consumers().updateConsumer(consumer2.getUuid(),
            new ConsumerDTO().hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId + "plus")));
    }

    @Test
    public void shouldAllowAConsumerToUnsetTheirHypervisorId() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getHypervisorId()).isNull();

        String hypervisorId = StringUtil.random("hYpervisor");
        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId)));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getHypervisorId())
            .returns(hypervisorId.toLowerCase(), HypervisorIdDTO::getHypervisorId);

        // Empty string update should modify the setting:
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .hypervisorId(new HypervisorIdDTO().hypervisorId("")));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getHypervisorId()).isNull();
    }
}
