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
package org.candlepin.spec.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SpecTest
class TrustedUserAuthSpecTest {

    private static ApiClient client;
    private static OwnerDTO owner;

    @BeforeAll
    static void beforeAll() {
        client = ApiClients.admin();
        owner = client.owners().createOwner(Owners.random());
    }

    @Test
    void shouldNotAllowUnknownTrustedUserWithLookupPermissions() {
        OwnerApi client = ApiClients.trustedUser("unknown_user").owners();

        assertBadRequest(() -> client.createOwner(Owners.random()));
    }

    @Test
    void shouldAllowUnknownTrustedUserWithoutLookupPermissions() {
        OwnerApi client = ApiClients.trustedUser("unknown_user", false).owners();

        OwnerDTO createdOwner = client.createOwner(Owners.random());
        assertThat(createdOwner).isNotNull();
    }

    @Test
    void shouldAllowTrustedUserWithFullAccess() {
        UserDTO user = UserUtil.createUser(client, owner);
        OwnerApi client = ApiClients.trustedUser(user.getUsername(), false).owners();

        OwnerDTO createdOwner = client.createOwner(Owners.random());
        assertThat(createdOwner).isNotNull();
    }

    @Test
    void shouldForbidTrustedUserWithLimitedAccess() {
        UserDTO user = UserUtil.createUser(client, owner);
        OwnerApi userClient = ApiClients.trustedUser(user.getUsername()).owners();

        assertForbidden(() -> userClient.createOwner(Owners.random()));
    }

    @Test
    void shouldCreateMissingOwner() {
        UserDTO user = UserUtil.createUser(client, owner);
        String username = user.getUsername();
        OwnerDTO owner = Owners.random();
        String ownerKey = owner.getKey();
        ApiClient client = ApiClients.trustedUser(username, false);

        ConsumerDTO consumer = client.consumers()
            .createConsumer(Consumers.random(owner), username, ownerKey, null, true);

        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getOwner)
            .extracting(NestedOwnerDTO::getKey)
            .isEqualTo(ownerKey);
    }

}
