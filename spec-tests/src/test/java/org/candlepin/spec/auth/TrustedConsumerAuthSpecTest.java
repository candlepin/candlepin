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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.resource.ConsumerApi;
import org.candlepin.resource.OwnerApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SpecTest
class TrustedConsumerAuthSpecTest {

    private static ApiClient client;
    private static OwnerDTO owner;

    @BeforeAll
    static void beforeAll() throws ApiException {
        client = ApiClients.admin();
        owner = client.owners().createOwner(Owners.random());
    }

    @Test
    @DisplayName("trusted consumer must exist")
    void consumerMustExist() {
        OwnerApi client = ApiClients.trustedConsumer("unknown_consumer").owners();

        assertUnauthorized(() -> client.createOwner(Owners.random()));
    }

    @Test
    @DisplayName("deleted consumers should be rejected")
    void deletedConsumerShouldFail() throws ApiException {
        ConsumerDTO consumer = client.consumers().register(Consumers.random(owner));
        client.consumers().deleteConsumer(consumer.getUuid());
        OwnerApi consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).owners();

        assertGone(() -> consumerClient.createOwner(Owners.random()));
    }

    @Test
    @DisplayName("should pass for existing consumers")
    void existingConsumerShouldPass() throws ApiException {
        ConsumerDTO consumer = client.consumers().register(Consumers.random(owner));
        ConsumerApi consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).consumers();

        ConsumerDTO foundConsumer = consumerClient.getConsumer(consumer.getUuid());

        assertThat(foundConsumer).isNotNull();
    }

}
