/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

@SpecTest
class NotFoundExceptionMapperSpecTest {

    @Test
    void shouldHideUnknownResourceUrl() {
        String path = "/" + StringUtil.random("missing-resource-");
        String queryValue = StringUtil.random("private-value-");
        Response response = Request.from(ApiClients.admin())
            .setPath(path)
            .addQueryParam("token", queryValue)
            .execute();

        assertThat(response.getCode()).isEqualTo(404);
        assertThat(response.deserialize().path("displayMessage").asString())
            .isEqualTo("Requested resource was not found.");
        assertThat(response.deserialize().path("requestUuid").asString()).isNotBlank();
        assertThat(response.getBodyAsString())
            .doesNotContain(path, queryValue, "http://", "https://", "RESTEASY", "org.jboss");
    }

    @Test
    void shouldExplainInvalidPathParameter() {
        ApiClient client = ApiClients.admin();
        OwnerDTO owner = client.owners().createOwner(Owners.random());
        ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
        Response response = Request.from(client)
            .setMethod(Request.Method.DELETE)
            .setPath("/consumers/{consumer_uuid}/certificates/{serial}")
            .setPathParam("consumer_uuid", consumer.getUuid())
            .setPathParam("serial", "bad")
            .execute();

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.deserialize().path("displayMessage").asString())
            .isEqualTo("bad is not a valid value for serial");
    }

    @Test
    void shouldPreserveCustomNotFoundMessage() {
        String roleName = StringUtil.random("missing-role-");
        Response response = Request.from(ApiClients.admin())
            .setPath("/roles/{role_name}")
            .setPathParam("role_name", roleName)
            .execute();

        assertThat(response.getCode()).isEqualTo(404);
        assertThat(response.deserialize().path("displayMessage").asString())
            .isEqualTo("Role not found: " + roleName);
    }
}
