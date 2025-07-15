/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.dto.api.server.v1.ConsumerEntitlementCountsDTO;
import org.candlepin.dto.api.server.v1.ConsumerFeedDTO;
import org.candlepin.dto.api.server.v1.RhsmApiConsumerEntitlementCountsQueryDTO;
import org.candlepin.dto.api.server.v1.RhsmApiEntitlementCountDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class RhsmApiCompatResourceTest extends DatabaseTestFixture {

    @Inject
    private RhsmApiCompatResource rhsmApiCompatResource;

    @BeforeEach
    public void beforeEach() throws Exception {
        ResteasyContext.clearContextData();
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, -10, 11 })
    public void testListConsumerEntitlementCountsWithInvalidPageSize(int pageSize) {
        ResteasyContext.popContextData(PageRequest.class);
        ResteasyContext.pushContext(PageRequest.class, new PageRequest()
            .setPage(1)
            .setPerPage(pageSize));

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerIds(List.of(TestUtil.randomString()));

        assertThrows(BadRequestException.class, () -> rhsmApiCompatResource
            .listConsumerEntitlementCounts(body));
    }

    @Test
    public void testListConsumerEntitlementCounts() {
        Owner owner = this.createOwner();

        Consumer consumer1 = this.createConsumer(owner);
        Consumer consumer2 = this.createConsumer(owner);
        Consumer consumer3 = this.createConsumer(owner);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product1.addContent(content1, true);
        product1.addContent(content2, false);

        this.createProduct(product1);

        Product product2 = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product2.addContent(content2, true);
        product2.addContent(content3, false);

        this.createProduct(product2);

        Pool pool1 = TestUtil.createPool(owner, product1)
            .setQuantity(10L);
        pool1 = poolCurator.create(pool1);

        Pool pool2 = TestUtil.createPool(owner, product2)
            .setQuantity(10L);
        pool2 = poolCurator.create(pool2);

        Pool pool3 = TestUtil.createPool(owner, product1)
            .setQuantity(10L)
            .setContractNumber(pool1.getContractNumber())
            .setSubscriptionId(pool1.getSubscriptionId());
        pool3 = poolCurator.create(pool3);

        Entitlement ent1 = createEntitlementWithQuantity(owner, consumer1, pool1, 1);
        Entitlement ent2 = createEntitlementWithQuantity(owner, consumer2, pool2, 2);
        Entitlement ent3 = createEntitlementWithQuantity(owner, consumer1, pool3, 3);

        createEntitlementWithQuantity(owner, consumer3, pool2, 3);

        List<String> consumerUuids = List.of(consumer1.getUuid());
        List<String> consumerIds = List.of(consumer2.getId());

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerIds(consumerIds)
            .consumerUuids(consumerUuids);

        Stream<ConsumerEntitlementCountsDTO> actual = rhsmApiCompatResource
            .listConsumerEntitlementCounts(body);

        List<ConsumerEntitlementCountsDTO> expected = new ArrayList<>();
        expected.add(new ConsumerEntitlementCountsDTO()
            .consumerId(consumer1.getId())
            .consumerUuid(consumer1.getUuid())
            .entitlementCounts(List.of(
                new RhsmApiEntitlementCountDTO()
                    .contractNumber(pool1.getContractNumber())
                    .subscriptionId(pool1.getSubscriptionId())
                    .productId(product1.getId())
                    .productName(product1.getName())
                    .count(ent1.getQuantity() + ent3.getQuantity()))));

        expected.add(new ConsumerEntitlementCountsDTO()
            .consumerId(consumer2.getId())
            .consumerUuid(consumer2.getUuid())
            .entitlementCounts(List.of(
                new RhsmApiEntitlementCountDTO()
                    .contractNumber(pool2.getContractNumber())
                    .subscriptionId(pool2.getSubscriptionId())
                    .productId(product2.getId())
                    .productName(product2.getName())
                    .count(ent2.getQuantity()))));

        // We expect the response to be sorted in ascending order based on consumer ID and UUID
        expected.sort(Comparator.comparing(ConsumerEntitlementCountsDTO::getConsumerId)
                .thenComparing(ConsumerEntitlementCountsDTO::getConsumerUuid));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .containsExactlyElementsOf(expected);
    }

    @Test
    public void testListConsumerEntitlementCountsWithPaging() {
        Owner owner = this.createOwner();

        Content content = this.createContent();
        Product product = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product.addContent(content, true);

        this.createProduct(product);

        Pool pool = TestUtil.createPool(owner, product)
            .setQuantity(1000L);
        pool = poolCurator.create(pool);

        int numberOfConsumers = 20;
        List<Consumer> consumers = new ArrayList<>();
        List<String> consumerUuids = new ArrayList<>();
        for (int i = 0; i < numberOfConsumers; i++) {
            Consumer consumer = this.createConsumer(owner);
            createEntitlementWithQuantity(owner, consumer, pool, 3);

            consumers.add(consumer);
            consumerUuids.add(consumer.getUuid());
        }

        consumers.sort(Comparator.comparing(Consumer::getId)
            .thenComparing(Consumer::getUuid));

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerUuids(consumerUuids);

        int pageSize = 4;
        for (int pageIndex = 1; pageIndex * pageSize <= numberOfConsumers; pageIndex++) {
            ResteasyContext.pushContext(PageRequest.class, new PageRequest()
                .setPage(pageIndex)
                .setPerPage(pageSize));

            Stream<ConsumerEntitlementCountsDTO> actual = rhsmApiCompatResource
                .listConsumerEntitlementCounts(body);

            int startIndex = (pageIndex - 1) * pageSize;
            int endIndex = startIndex + pageSize;

            List<String> expected = consumers.stream()
                .map(Consumer::getUuid)
                .toList()
                .subList(startIndex, endIndex);

            assertThat(actual)
                .isNotNull()
                .map(ConsumerEntitlementCountsDTO::getConsumerUuid)
                .containsExactlyElementsOf(expected);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    public void testGetConsumerFeedWithNotValidOwner(String ownerKey) {
        Stream<ConsumerFeedDTO> consumerFeed = rhsmApiCompatResource.getConsumerFeeds(ownerKey, null, null,
            null, null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetConsumerFeedWithUnknownOwner() {
        Stream<ConsumerFeedDTO> consumerFeed = rhsmApiCompatResource.getConsumerFeeds("unknownKey", null,
            null, null, null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetConsumerFeedWithInvalidPageParameter() {
        createOwner("test-owner");
        ResteasyContext.pushContext(PageRequest.class, new PageRequest()
            .setPage(-1)
            .setPerPage(100));

        assertThrows(BadRequestException.class, () -> rhsmApiCompatResource.getConsumerFeeds("test-owner",
            null, null, null, -1, 100));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 1001 })
    public void testGetConsumerFeedWithInvalidPerPageParameter(Integer perPage) {
        createOwner("test-owner");
        ResteasyContext.pushContext(PageRequest.class, new PageRequest()
            .setPage(1)
            .setPerPage(perPage));

        assertThrows(BadRequestException.class, () -> rhsmApiCompatResource.getConsumerFeeds("test-owner",
            null, null, null, 1, perPage));
    }

    @Test
    public void testGetConsumerFeedPaging() {
        Owner owner = this.createOwner();

        int numberOfConsumers = 20;
        List<String> consumerIds = new ArrayList<>();
        for (int i = 0; i < numberOfConsumers; i++) {
            Consumer consumer = createConsumer(owner);
            consumerIds.add(consumer.getId());
        }

        int pageSize = 4;
        for (int pageIndex = 1; pageIndex * pageSize <= numberOfConsumers; pageIndex++) {
            ResteasyContext.pushContext(PageRequest.class, new PageRequest()
                .setPage(pageIndex)
                .setPerPage(pageSize));

            Stream<ConsumerFeedDTO> actual = rhsmApiCompatResource
                .getConsumerFeeds(owner.getKey(), null, null, null, null, null);

            int startIndex = (pageIndex - 1) * pageSize;
            int endIndex = startIndex + pageSize;

            List<String> expected = consumerIds
                .subList(startIndex, endIndex);

            assertThat(actual)
                .isNotNull()
                .map(ConsumerFeedDTO::getId)
                .containsExactlyElementsOf(expected);
        }
    }

    private Entitlement createEntitlementWithQuantity(Owner owner, Consumer consumer, Pool pool,
        int quantity) {

        Entitlement entitlement = new Entitlement()
            .setId(Util.generateDbUUID())
            .setOwner(owner)
            .setQuantity(quantity)
            .setPool(pool)
            .setConsumer(consumer);

        this.entitlementCurator.create(entitlement);

        consumer.addEntitlement(entitlement);
        pool.getEntitlements().add(entitlement);

        return entitlement;
    }

}
