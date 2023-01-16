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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertConflict;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@SpecTest
public class ConsumerResourceEnvironmentSpecTest {

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldNotLetConsumerUpdateEnvironmentWithIncorrectEnvName() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertNotFound(() -> consumerClient.consumers()
            .updateConsumer(StringUtil.random("uuid-"), new ConsumerDTO()
            .environment(new EnvironmentDTO().name(StringUtil.random("name-")))));
    }

    @Test
    public void shouldLetConsumerUpdateEnvironmentWithValidEnvNameOnly() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        EnvironmentDTO env = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        assertNull(consumer.getEnvironment());

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .environments(List.of(new EnvironmentDTO().name(env.getName()))));
        consumer = adminClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getEnvironments())
            .singleElement()
            .returns(env.getId(), EnvironmentDTO::getId);
    }

    @Test
    public void shouldLetConsumerUpdateEnvironmentWithValidEnvIdOnly() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        EnvironmentDTO env = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        assertNull(consumer.getEnvironment());

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .environments(List.of(new EnvironmentDTO().id(env.getId()))));
        consumer = adminClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getEnvironments())
            .singleElement()
            .returns(env.getId(), EnvironmentDTO::getId);
    }

    @Test
    public void shouldLetNotConsumerUpdateEnvironmentWithIncorrectEnvId() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        adminClient.owners().createEnv(owner.getKey(), Environments.random());
        assertNull(consumer.getEnvironment());

        assertNotFound(() -> consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .environments(List.of(new EnvironmentDTO().id(StringUtil.random("invalid-"))))));
    }

    @Test
    public void shouldCreateConsumerWithMultipleEnvironments() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        EnvironmentDTO env1 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = adminClient.owners().createEnv(owner.getKey(), Environments.random());

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2)));
        assertThat(consumer.getEnvironments())
            .hasSize(2)
            .containsAll(List.of(env1, env2));
    }

    @Test
    public void shouldNotCreateConsumerWithNullEnvironmentInEnvironmentsList() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        EnvironmentDTO env1 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        assertNotFound(() -> userClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, Environments.random()))));
    }

    @Test
    public void shouldUpdateConsumerWithRePrioritizedEnvironments() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        EnvironmentDTO env1 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = adminClient.owners().createEnv(owner.getKey(), Environments.random());

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().environments(List.of(env1, env2)));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getEnvironments())
            .map(EnvironmentDTO::getName)
            .containsExactly(env1.getName(), env2.getName());

        // re-prioritized the environments
        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().environments(List.of(env2, env1)));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getEnvironments())
            .map(EnvironmentDTO::getName)
            .containsExactly(env2.getName(), env1.getName());
    }

    @Test
    public void shouldPreserveOtherEnvironmentPrioritiesOnAConsumerWhenAnEnvironmentIsRemoved() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        EnvironmentDTO env1 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        EnvironmentDTO env3 = adminClient.owners().createEnv(owner.getKey(), Environments.random());

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2, env3)));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getEnvironments())
            .map(EnvironmentDTO::getName)
            .containsExactly(env1.getName(), env2.getName(), env3.getName());

        // Removed the environment 2.
        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().environments(List.of(env1, env3)));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());

        // It should maintain the lowering priority for the rest of other environments.
        assertThat(consumer.getEnvironments())
            .map(EnvironmentDTO::getName)
            .containsExactly(env1.getName(), env3.getName());
    }

    @Test
    public void shouldReturnExceptionWhenEnvironmentIsSpecifiedMoreThanOnce() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        EnvironmentDTO env1 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = adminClient.owners().createEnv(owner.getKey(), Environments.random());

        // Exception raised while creating consumer
        assertConflict(() -> userClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2, env1))));

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Exception raised while updating consumer
        assertConflict(() -> userClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2, env1))));
    }

    private UserDTO createUserTypeAllAccess(ApiClient client, OwnerDTO owner) {
        return UserUtil.createWith(client,
            Permissions.USERNAME_CONSUMERS.all(owner),
            Permissions.OWNER_POOLS.all(owner),
            Permissions.ATTACH.all(owner));
    }
}
