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

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.resource.OwnerApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SpecTest
class TrustedUserAuthSpecTest {

    private static ApiClient client;
    private static OwnerDTO owner;

    @BeforeAll
    static void beforeAll() throws ApiException {
        client = ApiClients.admin();
        owner = client.owners().createOwner(Owners.random());
    }

    @Test
    @DisplayName("trusted user must exist when lookup permissions header set")
    void trustedUserMustExist() {
        OwnerApi client = ApiClients.trustedUser("unknown_user", true).owners();

        assertBadRequest(() -> client.createOwner(Owners.random()));
    }

    @Test
    @DisplayName("trusted user does not need to exist")
    void trustedUserDoesNotNeedToExist() throws ApiException {
        OwnerApi client = ApiClients.trustedUser("unknown_user").owners();

        OwnerDTO createdOwner = client.createOwner(Owners.random());
        assertThat(createdOwner).isNotNull();
    }

    @Test
    @DisplayName("trusted user can access admin endpoint")
    void trustedUserHasFullAccess() throws ApiException {
        UserDTO user = UserUtil.createUser(client, owner);
        OwnerApi client = ApiClients.trustedUser(user.getUsername()).owners();

        OwnerDTO createdOwner = client.createOwner(Owners.random());
        assertThat(createdOwner).isNotNull();
    }

    @Test
    @DisplayName("trusted user cannot access admin endpoint")
    void trustedUserHasLimitedAccess() throws ApiException {
        UserDTO user = UserUtil.createUser(client, owner);
        OwnerApi userClient = ApiClients.trustedUser(user.getUsername(), true).owners();

        assertForbidden(() -> userClient.createOwner(Owners.random()));
    }

}
