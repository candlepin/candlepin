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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;

import java.util.stream.Stream;


@SpecTest
public class ResponseFilteringSpecTest {

    private static ApiClient admin;
    private static OwnerDTO owner;
    private static ConsumerDTO consumer;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
        consumer = admin.consumers().createConsumer(Consumers.random(owner));
    }

    @Test
    public void shouldAllowSingleExclude() {
        JsonNode consumerFull = findConsumerRequest(consumer).execute().deserialize();
        JsonNode consumerFiltered = findConsumerRequest(consumer)
            .addQueryParam("exclude", "href")
            .execute()
            .deserialize();

        assertThat(consumerFiltered.has("href")).isFalse();
        assertEqualsIgnoring(consumerFull, consumerFiltered, "href");
    }

    @Test
    public void shouldAllowMultipleExcludes() {
        JsonNode consumerFull = findConsumerRequest(consumer).execute().deserialize();
        JsonNode consumerFiltered = findConsumerRequest(consumer)
            .addQueryParam("exclude", "href")
            .addQueryParam("exclude", "facts")
            .execute()
            .deserialize();

        assertThat(consumerFiltered.has("href")).isFalse();
        assertThat(consumerFiltered.has("facts")).isFalse();
        assertEqualsIgnoring(consumerFull, consumerFiltered, "href", "facts");
    }

    @Test
    public void shouldAllowSingleInclude() {
        JsonNode consumerFiltered = findConsumerRequest(consumer)
            .addQueryParam("include", "href")
            .execute()
            .deserialize();

        assertThat(consumerFiltered).hasSize(1);
        assertThat(consumerFiltered.get("href")).isNotNull();
    }

    @Test
    public void shouldAllowMultipleIncludes() {
        JsonNode consumerFiltered = findConsumerRequest(consumer)
            .addQueryParam("include", "href")
            .addQueryParam("include", "facts")
            .execute()
            .deserialize();

        assertThat(consumerFiltered).hasSize(2);
        assertThat(consumerFiltered.get("href")).isNotNull();
        assertThat(consumerFiltered.get("facts")).isNotNull();
    }

    @Test
    public void shouldAllowFiltersOnEncapsulatedObjects() {
        JsonNode consumerFiltered = findConsumerRequest(consumer)
            .addQueryParam("include", "id")
            .addQueryParam("include", "owner.id")
            .execute()
            .deserialize();

        assertThat(consumerFiltered).hasSize(2);
        assertThat(consumerFiltered.get("id")).isNotNull();
        assertThat(consumerFiltered.get("owner")).isNotNull();
        assertThat(consumerFiltered.get("owner")).hasSize(1);
        assertThat(consumerFiltered.get("owner").get("id")).isNotNull();
    }

    @Test
    public void shouldAllowFiltersOnEncapsulatedLists() {
        admin.consumers().createConsumer(Consumers.random(owner));
        admin.consumers().createConsumer(Consumers.random(owner));
        JsonNode consumers = Request.from(admin)
            .setPath("/consumers")
            .addQueryParam("owner", owner.getKey())
            .addQueryParam("type", "system")
            .addQueryParam("include", "id")
            .addQueryParam("include", "owner.id")
            .execute()
            .deserialize();

        assertThat(consumers)
            .isNotEmpty()
            .allSatisfy(consumer -> {
                assertThat(consumer).hasSize(2);
                assertThat(consumer.get("id")).isNotNull();
                assertThat(consumer.get("owner")).isNotNull();
                assertThat(consumer.get("owner")).hasSize(1);
                assertThat(consumer.get("owner").get("id")).isNotNull();
            });
    }

    @ParameterizedTest
    @MethodSource("contentTypes")
    public void shouldGetValidResponseOnInvalidContentTypes(String contentType, int expectedResponse) {
        Response response = Request.from(admin)
            .addHeader("Content-Type", contentType)
            .setPath("/consumers/" + consumer.getUuid())
            .execute();

        assertThat(response.getCode()).isEqualTo(expectedResponse);
    }

    public static Stream<Arguments> contentTypes() {
        return Stream.of(
            Arguments.of("application|json", 400),
            Arguments.of("application-json", 400),
            Arguments.of("application/json", 200)
        );
    }

    private Request findConsumerRequest(ConsumerDTO consumerToGet) {
        return Request.from(admin)
            .setPath("/consumers/" + consumerToGet.getUuid());
    }

    private void assertEqualsIgnoring(JsonNode consumerFull, JsonNode consumerFiltered, String... ignore) {
        ConsumerDTO full = toConsumerDto(consumerFull);
        ConsumerDTO filtered = toConsumerDto(consumerFiltered);
        assertThat(full)
            .usingRecursiveComparison()
            .ignoringFields(ignore)
            .isEqualTo(filtered);
    }

    private ConsumerDTO toConsumerDto(JsonNode consumerFull) {
        return ApiClient.MAPPER.treeToValue(consumerFull, ConsumerDTO.class);
    }

}
