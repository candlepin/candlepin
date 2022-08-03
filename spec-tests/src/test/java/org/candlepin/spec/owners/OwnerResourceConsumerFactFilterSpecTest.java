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

package org.candlepin.spec.owners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@SpecTest
public class OwnerResourceConsumerFactFilterSpecTest {

    private static ApiClient adminClient;
    private static OwnerDTO owner;
    private static ConsumerDTO consumer1;
    private static ConsumerDTO consumer2;
    private static ConsumerDTO consumer3;

    @BeforeAll
    public static void setUp() throws ApiException {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner)
                .facts(Map.of("key", "value", "otherkey", "othervalue")));
        consumer2 = userClient.consumers().createConsumer(
            Consumers.random(owner)
                .facts(Map.of("key", "value", "otherkey", "someval")));
        consumer3 = userClient.consumers().createConsumer(
            Consumers.random(owner)
                .facts(Map.of("newkey", "somevalue")));
    }

    @Test
    public void shouldAllowOwnersFilterConsumersByASingleFact() throws ApiException {
        List<ConsumerDTOArrayElement> consumers = adminClient.owners().listConsumers(
            owner.getKey(), null, null, null, null, List.of("key:value"), null, null, null, null);
        assertEquals(2, consumers.size());

        consumers = adminClient.owners().listConsumers(
            owner.getKey(), null, null, null, null, List.of("newkey:somevalue"), null, null, null, null);
        assertThat(consumers)
            .isNotNull()
            .singleElement()
            .isNotNull()
            .returns(consumer3.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldAllowOwnersFilterConsumersByMultipleFacts() throws ApiException {
        List<ConsumerDTOArrayElement> consumers = adminClient.owners().listConsumers(
            owner.getKey(), null, null, null, null, List.of("key:value", "otherkey:someval"),
            null, null, null, null);
        assertThat(consumers)
            .isNotNull()
            .singleElement()
            .isNotNull()
            .returns(consumer2.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldAllowOwnersFilterConsumersByMultipleFactsSameKeyAsOr() throws ApiException {
        List<ConsumerDTOArrayElement> consumers = adminClient.owners().listConsumers(
            owner.getKey(), null, null, null, null, List.of("otherkey:othervalue", "otherkey:someval"),
            null, null, null, null);
        assertThat(consumers)
            .isNotNull()
            .hasSize(2);
    }

    @Test
    public void shouldAllowOwnersFilterConsumersByFactsWithWildcards() throws ApiException {
        List<ConsumerDTOArrayElement> consumers = adminClient.owners().listConsumers(
            owner.getKey(), null, null, null, null, List.of("*key*:*val*"), null, null, null, null);
        assertThat(consumers)
            .isNotNull()
            .hasSize(3);

        // Also make sure the value half is checked case insensitively
        consumers = adminClient.owners().listConsumers(
            owner.getKey(), null, null, null, null, List.of("ot*key*:OTher*"), null, null, null, null);
        assertThat(consumers)
            .isNotNull()
            .singleElement()
            .isNotNull()
            .returns(consumer1.getUuid(), ConsumerDTOArrayElement::getUuid);
    }
}
