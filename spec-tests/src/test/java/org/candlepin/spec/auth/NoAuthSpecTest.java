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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;

import org.candlepin.dto.api.client.v1.StatusDTO;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.resource.client.v1.StatusApi;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.Test;

@SpecTest
class NoAuthSpecTest {

    @Test
    void shouldRejectNoAuthRequestsForSuperAdminEndpoints() {
        OwnerApi client = ApiClients.noAuth().owners();

        assertUnauthorized(() -> client.createOwner(Owners.random()));
    }

    @Test
    void shouldRejectNoAuthRequestsForVerifiedEndpoints() {
        OwnerApi client = ApiClients.noAuth().owners();

        assertUnauthorized(() -> client.getOwner("some_key"));
    }

    @Test
    void shouldRejectNoAuthRequestsForSecurityHoleEndpoints() {
        ProductsApi client = ApiClients.noAuth().products();

        assertUnauthorized(() -> client.getProduct("some_uuid"));
    }

    @Test
    void shouldAcceptNoAuthRequestsForNoAuthSecurityHoleEndpoints() {
        StatusApi client = ApiClients.noAuth().status();

        StatusDTO status = client.status();

        assertThat(status).isNotNull();
    }

}
