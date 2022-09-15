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

package org.candlepin.spec.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.StatusDTO;
import org.candlepin.resource.client.v1.ConsumerApi;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.resource.client.v1.StatusApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SpecTest
class ActivationKeyAuthSpecTest {

    private static OwnerDTO owner;
    private static ActivationKeyDTO activationKey;

    @BeforeAll
    static void beforeAll() {
        ApiClient userClient = ApiClients.admin();
        owner = userClient.owners().createOwner(Owners.random());
        ActivationKeyDTO testActivationKey = ActivationKeys.random(owner);
        activationKey = userClient.owners().createActivationKey(owner.getKey(), testActivationKey);
    }

    @Test
    void shouldRejectActivationKeyAuthForSuperAdminEndpoints() {
        OwnerApi client = ApiClients.activationKey(owner.getKey(), activationKey.getName()).owners();

        assertForbidden(() -> client.createOwner(Owners.random()));
    }

    @Test
    void shouldRejectActivationKeyAuthForVerifiedEndpoints() {
        OwnerApi client = ApiClients.activationKey(owner.getKey(), activationKey.getName()).owners();

        assertForbidden(() -> client.getOwner(owner.getKey()));
    }

    @Test
    void shouldRejectActivationKeyAuthForSecurityHoleEndpoints() {
        ProductsApi client = ApiClients.activationKey(owner.getKey(), activationKey.getName()).products();

        assertForbidden(() -> client.getProduct("some_uuid"));
    }

    @Test
    void shouldAcceptNoAuthRequestsForNoAuthSecurityHoleEndpoints() {
        StatusApi client = ApiClients.activationKey(owner.getKey(), activationKey.getName()).status();

        StatusDTO status = client.status();

        assertThat(status).isNotNull();
    }

    @Test
    void shouldRequireOwner() {
        ConsumerApi client = ApiClients.noAuth().consumers();

        assertBadRequest(() -> client.createConsumer(
            Consumers.random(null), null, null, activationKey.getName(), true));
    }

    @Test
    void shouldRequireExistingOwner() {
        ConsumerApi client = ApiClients.noAuth().consumers();

        assertUnauthorized(() -> client.createConsumer(
            Consumers.random(null), null, "some_owner", activationKey.getName(), true));
    }

    @Test
    void shouldRequireActivationKeys() {
        ConsumerApi client = ApiClients.noAuth().consumers();

        assertUnauthorized(() -> client.createConsumer(
            Consumers.random(null), null, owner.getKey(), null, true));
    }

    @Test
    void shouldFailWithActivationKeysAndUsernamePresentAtOnce() {
        ConsumerApi client = ApiClients.noAuth().consumers();

        assertBadRequest(() -> client.createConsumer(
            Consumers.random(null), "username", owner.getKey(), activationKey.getName(), true));
    }

    @Test
    void shouldFailWithNoValidKeys() {
        ConsumerApi client = ApiClients.noAuth().consumers();
        String activationKeys = toKeyString("some_key", "some_other_key");

        assertUnauthorized(() -> client.createConsumer(
            Consumers.random(null), null, owner.getKey(), activationKeys, true));
    }

    @Test
    void shouldPassWithAtLeastOneValidKey() {
        ConsumerApi client = ApiClients.noAuth().consumers();
        String activationKeys = toKeyString(activationKey.getName(), "some_key");

        ConsumerDTO consumer = client.createConsumer(
            Consumers.random(null), null, owner.getKey(), activationKeys, true);

        assertThat(consumer).isNotNull();
    }

    @NotNull
    private String toKeyString(String... keys) {
        return String.join(",", keys);
    }

}
