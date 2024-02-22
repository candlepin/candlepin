package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertThatStatus;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.junit.jupiter.api.Test;

@SpecTest
public class PassivitySpecTests {

    private static final String USERNAME = "candlepin-787-test-user";

    // GET /candlepin/users/{user_id}/owners
    // Example request ID: aab32a42-d58e-4cc1-8a72-c273a1da5a73
    // Expected response: 200

    @Test
    public void shouldReturnSuccessForGetUserOwners() {
        ApiClient userClient = ApiClients.basic(createTestUser());

        assertThat(userClient.users().listUserOwners(USERNAME))
                .isNotNull()
                .hasSize(1);
    }

    // GET /candlepin/owners/{owner_key}/info
    // Example request ID: 8b4f93a3-3835-46cc-8787-c9a19cb73e9d
    // Expected response: 404

    @Test
    public void shouldReturnNotFoundForOwnerInfo() {
        ApiClient userClient = ApiClients.basic(createTestUser());

        assertThatStatus(() -> userClient.owners()
                .getOwnerInfo(StringUtil.random("ownerkey")))
                .isNotFound();
    }

    // POST /candlepin/consumers
    // Example request ID: 713a5070-4313-4e58-a2cd-ece7f900c8b2
    // Expected response: 400

    @Test
    public void shouldReturnBadRequestForPostConsumers() {
        ApiClient userClient = ApiClients.basic(createTestUser());

        assertThatStatus(() -> userClient.consumers()
                .createConsumerWithoutOwner(Consumers.randomNoOwner()))
                .isBadRequest();
    }

    // POST /candlepin/consumers?owner={owner_key}
    // Example request ID: ca3566b6-3973-4ab8-8c40-c30607e386f0
    // Expected response: 400

    @Test
    public void shouldReturnBadRequestForPostConsumersWithOwnerQueryParam() {
        ApiClient userClient = ApiClients.basic(createTestUser());

        assertThatStatus(() -> userClient.consumers()
                .createConsumer(Consumers.random(Owners.randomSca())))
                .isBadRequest();
    }

    // POST /candlepin/consumers/{consumer_uuid}
    // Example request ID: 96ce0e2c-18a4-4d58-a813-d8c8bf430a6f
    // Expected response: 403

    @Test
    public void shouldReturnForbiddenForPostConsumersWithUuid() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        ApiClient userClient = ApiClients.basic(createTestUser());

        assertThatStatus(() -> userClient.consumers()
                .regenerateIdentityCertificates(consumer.getUuid()))
                .isForbidden();
    }

    // GET /candlepin/owners/{owner_key}/pools
    // Example request ID: ac123f5d-d5ea-427d-a2b8-28a0631a4c40
    // Expected response: 404

    @Test
    public void shouldReturnNotFoundForGetOwnerPools() {
        ApiClient userClient = ApiClients.basic(createTestUser());

        assertThatStatus(() -> userClient.owners()
                .listOwnerPools(StringUtil.random("ownerkey")))
                .isNotFound();
    }

    private UserDTO createTestUser() {
        UserDTO user = new UserDTO();
        user.setSuperAdmin(false);
        user.setUsername(USERNAME);
        user.setPassword(StringUtil.random("password"));
        return user;
    }

}