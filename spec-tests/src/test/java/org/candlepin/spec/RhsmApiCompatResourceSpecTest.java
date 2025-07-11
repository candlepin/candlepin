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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerEntitlementCountsDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.RhsmApiConsumerEntitlementCountsQueryDTO;
import org.candlepin.dto.api.client.v1.RhsmApiEntitlementCountDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SpecTest
@OnlyInHosted
public class RhsmApiCompatResourceSpecTest {

    private static final int MAX_CONSUMER_UUIDS_AND_IDS = 1000;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Test
    public void shouldNotAllowNonAdminUsersToListEntitlementCountsForConsumers() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerIds(List.of(StringUtil.random("id-")));

        assertForbidden(() -> userClient.rhsmApi().listConsumerEntitlementCounts(body));
    }

    @Test
    public void shouldNotAllowNoAuthClientsToListEntitlementCountsForConsumers() {
        ApiClient noAuthClient = ApiClients.noAuth();

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerIds(List.of(StringUtil.random("id-")));

        assertUnauthorized(() -> noAuthClient.rhsmApi().listConsumerEntitlementCounts(body));
    }

    @Test
    public void shouldReturnBadRequestForExceedingConsumerUuidLimitWhenListingEntCountsForConsumers() {
        ApiClient adminClient = ApiClients.admin();

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO();
        for (int i = 0; i < MAX_CONSUMER_UUIDS_AND_IDS + 1; i++) {
            body.addConsumerUuidsItem(StringUtil.random("uuid-"));
        }

        assertBadRequest(() -> adminClient.rhsmApi().listConsumerEntitlementCounts(body));
    }

    @Test
    public void shouldReturnBadRequestForExceedingConsumerIdLimitWhenListingEntCountsForConsumers() {
        ApiClient adminClient = ApiClients.admin();

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO();
        for (int i = 0; i < MAX_CONSUMER_UUIDS_AND_IDS + 1; i++) {
            body.addConsumerIdsItem(StringUtil.random("id-"));
        }

        assertBadRequest(() -> adminClient.rhsmApi().listConsumerEntitlementCounts(body));
    }

    @Test
    public void shouldListEntitlementCountsForConsumers() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO product1 = adminClient.hosted().createProduct(Products.random());
        ProductDTO product2 = adminClient.hosted().createProduct(Products.random());
        ProductDTO product3 = adminClient.hosted().createProduct(Products.random());
        ProductDTO product4 = adminClient.hosted().createProduct(Products.random());

        SubscriptionDTO sub1 = adminClient.hosted().createSubscription(Subscriptions.random(owner, product1)
            .contractNumber(StringUtil.random("contract-")));
        SubscriptionDTO sub2 = adminClient.hosted().createSubscription(Subscriptions.random(owner, product2)
            .contractNumber(StringUtil.random("contract-")));
        SubscriptionDTO sub3 = adminClient.hosted().createSubscription(Subscriptions.random(owner, product3)
            .contractNumber(StringUtil.random("contract-")));
        SubscriptionDTO sub4 = adminClient.hosted().createSubscription(Subscriptions.random(owner, product4)
            .contractNumber(StringUtil.random("contract-")));

        AsyncJobStatusDTO job = adminClient.owners().refreshPools(ownerKey, true);
        job = adminClient.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();

        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));

        adminClient.consumers().bindProduct(consumer1.getUuid(), product1);
        adminClient.consumers().bindProduct(consumer2.getUuid(), product2);
        adminClient.consumers().bindProduct(consumer3.getUuid(), product3);
        adminClient.consumers().bindProduct(consumer1.getUuid(), product4);

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerIds(List.of(consumer1.getId()))
            .consumerUuids(List.of(consumer2.getUuid()));

        List<ConsumerEntitlementCountsDTO> actual = adminClient.rhsmApi()
            .listConsumerEntitlementCounts(body);

        Map<String, ConsumerEntitlementCountsDTO> expected = Map.of(
            consumer1.getUuid(), new ConsumerEntitlementCountsDTO()
                .consumerId(consumer1.getId())
                .consumerUuid(consumer1.getUuid())
                .entitlementCounts(List.of(
                    new RhsmApiEntitlementCountDTO()
                        .productId(product1.getId())
                        .productName(product1.getName())
                        .contractNumber(sub1.getContractNumber())
                        .subscriptionId(sub1.getId())
                        .count(1),
                    new RhsmApiEntitlementCountDTO()
                        .productId(product4.getId())
                        .productName(product4.getName())
                        .contractNumber(sub4.getContractNumber())
                        .subscriptionId(sub4.getId())
                        .count(1))),
            consumer2.getUuid(), new ConsumerEntitlementCountsDTO()
                .consumerId(consumer2.getId())
                .consumerUuid(consumer2.getUuid())
                .entitlementCounts(List.of(
                    new RhsmApiEntitlementCountDTO()
                        .productId(product2.getId())
                        .productName(product2.getName())
                        .contractNumber(sub2.getContractNumber())
                        .subscriptionId(sub2.getId())
                        .count(1))));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .allMatch(Objects::nonNull);

        ConsumerEntitlementCountsDTO expected1 = expected.get(actual.get(0).getConsumerUuid());
        assertNotNull(expected1);

        ConsumerEntitlementCountsDTO expected2 = expected.get(actual.get(1).getConsumerUuid());
        assertNotNull(expected2);

        assertThat(actual.get(0))
            .isNotNull()
            .returns(expected1.getConsumerId(), ConsumerEntitlementCountsDTO::getConsumerId)
            .returns(expected1.getConsumerUuid(), ConsumerEntitlementCountsDTO::getConsumerUuid)
            .extracting(ConsumerEntitlementCountsDTO::getEntitlementCounts,
                as(collection(RhsmApiEntitlementCountDTO.class)))
            .containsExactlyInAnyOrderElementsOf(expected1.getEntitlementCounts());

        assertThat(actual.get(1))
            .isNotNull()
            .returns(expected2.getConsumerId(), ConsumerEntitlementCountsDTO::getConsumerId)
            .returns(expected2.getConsumerUuid(), ConsumerEntitlementCountsDTO::getConsumerUuid)
            .extracting(ConsumerEntitlementCountsDTO::getEntitlementCounts,
                as(collection(RhsmApiEntitlementCountDTO.class)))
            .containsExactlyInAnyOrderElementsOf(expected2.getEntitlementCounts());
    }

    @Test
    public void shouldSumEntitlementCountsForPoolsWithSameContractNumberSubIdAndProduct() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO product1 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO product2 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());

        String contractNumber = StringUtil.random("contract-");
        String subId = StringUtil.random("sub-");
        String upstreamPoolId = StringUtil.random("pool-id-");

        PoolDTO pool1 = adminClient.owners().createPool(ownerKey, Pools.random(product1)
            .subscriptionId(subId)
            .subscriptionSubKey("master")
            .upstreamPoolId(upstreamPoolId)
            .contractNumber(contractNumber));

        PoolDTO pool2 = adminClient.owners().createPool(ownerKey, Pools.random(product1)
            .subscriptionId(subId)
            .subscriptionSubKey("master")
            .upstreamPoolId(upstreamPoolId)
            .contractNumber(contractNumber));

        PoolDTO poolDifferentSubId = adminClient.owners().createPool(ownerKey, Pools.random(product1)
            .subscriptionId(StringUtil.random("sub-"))
            .subscriptionSubKey("master")
            .upstreamPoolId(upstreamPoolId)
            .contractNumber(contractNumber));

        PoolDTO poolDifferentContractNumber = adminClient.owners().createPool(ownerKey, Pools.random(product1)
            .subscriptionId(subId)
            .subscriptionSubKey("master")
            .upstreamPoolId(upstreamPoolId)
            .contractNumber(StringUtil.random("contract-")));

        PoolDTO poolDifferentProduct = adminClient.owners().createPool(ownerKey, Pools.random(product2)
            .subscriptionId(subId)
            .subscriptionSubKey("master")
            .upstreamPoolId(upstreamPoolId)
            .contractNumber(contractNumber));

        // Consumer needs to be manifest type for multi-entitlement support
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));

        int pool1Quantity = 4;
        int pool2Quantity = 2;
        adminClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), pool1Quantity);
        adminClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), pool2Quantity);

        // Verify that pools with differences in subscription ID, contract number, or product have their own
        // entitlement count and are not included in the quantity summation for pools 1 and 2.
        adminClient.consumers().bindPool(consumer.getUuid(), poolDifferentSubId.getId(), 1);
        adminClient.consumers().bindPool(consumer.getUuid(), poolDifferentContractNumber.getId(), 1);
        adminClient.consumers().bindPool(consumer.getUuid(), poolDifferentProduct.getId(), 1);

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerUuids(List.of(consumer.getUuid()));

        List<ConsumerEntitlementCountsDTO> actual = adminClient.rhsmApi()
            .listConsumerEntitlementCounts(body);

        ConsumerEntitlementCountsDTO expected = new ConsumerEntitlementCountsDTO()
            .consumerId(consumer.getId())
            .consumerUuid(consumer.getUuid())
            .entitlementCounts(List.of(
                new RhsmApiEntitlementCountDTO()
                    .productId(product1.getId())
                    .productName(product1.getName())
                    .contractNumber(contractNumber)
                    .subscriptionId(subId)
                    .count(pool1Quantity + pool2Quantity),
                // Different subscription ID
                new RhsmApiEntitlementCountDTO()
                    .productId(product1.getId())
                    .productName(product1.getName())
                    .contractNumber(contractNumber)
                    .subscriptionId(poolDifferentSubId.getSubscriptionId())
                    .count(1),
                // Different contract number
                new RhsmApiEntitlementCountDTO()
                    .productId(product1.getId())
                    .productName(product1.getName())
                    .contractNumber(poolDifferentContractNumber.getContractNumber())
                    .subscriptionId(subId)
                    .count(1),
                // Different Product
                new RhsmApiEntitlementCountDTO()
                    .productId(product2.getId())
                    .productName(product2.getName())
                    .contractNumber(contractNumber)
                    .subscriptionId(subId)
                    .count(1)));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .returns(expected.getConsumerId(), ConsumerEntitlementCountsDTO::getConsumerId)
            .returns(expected.getConsumerUuid(), ConsumerEntitlementCountsDTO::getConsumerUuid)
            .extracting(ConsumerEntitlementCountsDTO::getEntitlementCounts,
                as(collection(RhsmApiEntitlementCountDTO.class)))
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected.getEntitlementCounts());
    }

    @Test
    public void shouldNotIncludeEntitlementCountsForConsumersThatHaveNoPools() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO product = adminClient.hosted().createProduct(Products.random());

        SubscriptionDTO sub = adminClient.hosted().createSubscription(Subscriptions.random(owner, product)
            .contractNumber(StringUtil.random("contract-")));

        AsyncJobStatusDTO job = adminClient.owners().refreshPools(ownerKey, true);
        job = adminClient.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();

        ConsumerDTO consumerWithPool = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumerWithoutPool = adminClient.consumers().createConsumer(Consumers.random(owner));

        adminClient.consumers().bindProduct(consumerWithPool.getUuid(), product);

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerUuids(List.of(consumerWithPool.getUuid(), consumerWithoutPool.getUuid()));

        List<ConsumerEntitlementCountsDTO> actual = adminClient.rhsmApi()
            .listConsumerEntitlementCounts(body);

        ConsumerEntitlementCountsDTO expected = new ConsumerEntitlementCountsDTO()
            .consumerId(consumerWithPool.getId())
            .consumerUuid(consumerWithPool.getUuid())
            .entitlementCounts(List.of(
                new RhsmApiEntitlementCountDTO()
                    .productId(product.getId())
                    .productName(product.getName())
                    .contractNumber(sub.getContractNumber())
                    .subscriptionId(sub.getId())
                    .count(1)));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(expected);
    }

    @Test
    public void shouldIncludeSeperateEntitlementCountsForProductsWithSameName() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO product1 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO product2 = adminClient.ownerProducts().createProduct(ownerKey, Products.random()
            .name(product1.getName()));

        PoolDTO pool1 = adminClient.owners().createPool(ownerKey, Pools.random(product1));
        PoolDTO pool2 = adminClient.owners().createPool(ownerKey, Pools.random(product2));

        // Consumer needs to be manifest type for multi-entitlement support
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));

        int pool1Quantity = 4;
        int pool2Quantity = 2;
        adminClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), pool1Quantity);
        adminClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), pool2Quantity);

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerUuids(List.of(consumer.getUuid()));

        List<ConsumerEntitlementCountsDTO> actual = adminClient.rhsmApi()
            .listConsumerEntitlementCounts(body);

        ConsumerEntitlementCountsDTO expected = new ConsumerEntitlementCountsDTO()
            .consumerId(consumer.getId())
            .consumerUuid(consumer.getUuid())
            .entitlementCounts(List.of(
                new RhsmApiEntitlementCountDTO()
                    .productId(product1.getId())
                    .productName(product1.getName())
                    .contractNumber(pool1.getContractNumber())
                    .subscriptionId(pool1.getSubscriptionId())
                    .count(pool1Quantity),
                new RhsmApiEntitlementCountDTO()
                    .productId(product2.getId())
                    .productName(product2.getName())
                    .contractNumber(pool2.getContractNumber())
                    .subscriptionId(pool2.getSubscriptionId())
                    .count(pool2Quantity)));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .returns(expected.getConsumerId(), ConsumerEntitlementCountsDTO::getConsumerId)
            .returns(expected.getConsumerUuid(), ConsumerEntitlementCountsDTO::getConsumerUuid)
            .extracting(ConsumerEntitlementCountsDTO::getEntitlementCounts,
                as(collection(RhsmApiEntitlementCountDTO.class)))
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected.getEntitlementCounts());
    }

    @Test
    public void shouldIncludeSeperateEntitlementCountsForProductsWithSameId() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner1 = adminClient.owners().createOwner(Owners.random());
        String owner1Key = owner1.getKey();
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        String owner2Key = owner2.getKey();

        ProductDTO product1 = adminClient.ownerProducts().createProduct(owner1Key, Products.random());
        ProductDTO product2 = adminClient.ownerProducts().createProduct(owner2Key, Products.random()
            .id(product1.getId()));

        PoolDTO pool1 = adminClient.owners().createPool(owner1Key, Pools.random(product1)
            .subscriptionId(StringUtil.random("sub-"))
            .subscriptionSubKey(StringUtil.random("sub-key-"))
            .upstreamPoolId(StringUtil.random("pool-id-"))
            .contractNumber(StringUtil.random("contract-")));
        PoolDTO pool2 = adminClient.owners().createPool(owner2Key, Pools.random(product2)
            .subscriptionId(StringUtil.random("sub-"))
            .subscriptionSubKey(StringUtil.random("sub-key-"))
            .upstreamPoolId(StringUtil.random("pool-id-"))
            .contractNumber(StringUtil.random("contract-")));

        // Consumer needs to be manifest type for multi-entitlement support
        ConsumerDTO consumer1 = adminClient.consumers()
            .createConsumer(Consumers.random(owner1, ConsumerTypes.Candlepin));
        ConsumerDTO consumer2 = adminClient.consumers()
            .createConsumer(Consumers.random(owner2, ConsumerTypes.Candlepin));

        int pool1Quantity = 4;
        int pool2Quantity = 2;
        adminClient.consumers().bindPool(consumer1.getUuid(), pool1.getId(), pool1Quantity);
        adminClient.consumers().bindPool(consumer2.getUuid(), pool2.getId(), pool2Quantity);

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerUuids(List.of(consumer1.getUuid(), consumer2.getUuid()));

        List<ConsumerEntitlementCountsDTO> actual = adminClient.rhsmApi()
            .listConsumerEntitlementCounts(body);

        Map<String, ConsumerEntitlementCountsDTO> expected = Map.of(
            consumer1.getUuid(), new ConsumerEntitlementCountsDTO()
                .consumerId(consumer1.getId())
                .consumerUuid(consumer1.getUuid())
                .entitlementCounts(List.of(
                    new RhsmApiEntitlementCountDTO()
                        .productId(product1.getId())
                        .productName(product1.getName())
                        .contractNumber(pool1.getContractNumber())
                        .subscriptionId(pool1.getSubscriptionId())
                        .count(pool1Quantity))),
            consumer2.getUuid(), new ConsumerEntitlementCountsDTO()
                .consumerId(consumer2.getId())
                .consumerUuid(consumer2.getUuid())
                .entitlementCounts(List.of(
                    new RhsmApiEntitlementCountDTO()
                        .productId(product2.getId())
                        .productName(product2.getName())
                        .contractNumber(pool2.getContractNumber())
                        .subscriptionId(pool2.getSubscriptionId())
                        .count(pool2Quantity))));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .allMatch(Objects::nonNull);

        ConsumerEntitlementCountsDTO expected1 = expected.get(actual.get(0).getConsumerUuid());
        assertNotNull(expected1);

        ConsumerEntitlementCountsDTO expected2 = expected.get(actual.get(1).getConsumerUuid());
        assertNotNull(expected2);

        assertThat(actual.get(0))
            .isNotNull()
            .returns(expected1.getConsumerId(), ConsumerEntitlementCountsDTO::getConsumerId)
            .returns(expected1.getConsumerUuid(), ConsumerEntitlementCountsDTO::getConsumerUuid)
            .extracting(ConsumerEntitlementCountsDTO::getEntitlementCounts,
                as(collection(RhsmApiEntitlementCountDTO.class)))
            .containsExactlyInAnyOrderElementsOf(expected1.getEntitlementCounts());

        assertThat(actual.get(1))
            .isNotNull()
            .returns(expected2.getConsumerId(), ConsumerEntitlementCountsDTO::getConsumerId)
            .returns(expected2.getConsumerUuid(), ConsumerEntitlementCountsDTO::getConsumerUuid)
            .extracting(ConsumerEntitlementCountsDTO::getEntitlementCounts,
                as(collection(RhsmApiEntitlementCountDTO.class)))
            .containsExactlyInAnyOrderElementsOf(expected2.getEntitlementCounts());
    }

    @Test
    public void shouldNotIncludeEntitlementCountsForPoolsThatHaveNoSubscriptionId() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO product = adminClient.ownerProducts().createProduct(ownerKey, Products.random());

        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(product)
            .upstreamPoolId(StringUtil.random("pool-"))
            .contractNumber(StringUtil.random("contract-")));

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));

        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerUuids(List.of(consumer.getUuid()));

        List<ConsumerEntitlementCountsDTO> actual = adminClient.rhsmApi()
            .listConsumerEntitlementCounts(body);

        ConsumerEntitlementCountsDTO expected = new ConsumerEntitlementCountsDTO()
            .consumerId(consumer.getId())
            .consumerUuid(consumer.getUuid())
            .entitlementCounts(List.of(
                new RhsmApiEntitlementCountDTO()
                    .productId(product.getId())
                    .productName(product.getName())
                    .contractNumber(pool.getContractNumber())
                    .subscriptionId(null)
                    .count(1)));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(expected);
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class ListEntitlementCountsPagingTests {
        private ApiClient adminClient = ApiClients.admin();

        private OwnerDTO owner;
        private String ownerKey;

        private int numberOfConsumers = 20;

        private List<ConsumerDTO> consumers = new ArrayList<>();
        private List<String> consumerUuids = new ArrayList<>();
        private ProductDTO product;
        private PoolDTO pool;

        @BeforeAll
        public void beforeAll() {
            owner = adminClient.owners().createOwner(Owners.random());
            ownerKey = owner.getKey();

            product = adminClient.ownerProducts().createProduct(ownerKey, Products.random());

            pool = adminClient.owners().createPool(ownerKey, Pools.random(product)
                .subscriptionId(StringUtil.random("sub-"))
                .subscriptionSubKey("master")
                .upstreamPoolId(StringUtil.random("pool-"))
                .contractNumber(StringUtil.random("contract-"))
                .quantity(1000L));

            for (int i = 0; i < numberOfConsumers; i++) {
                ConsumerDTO consumer = adminClient.consumers()
                    .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));
                consumers.add(consumer);
                consumerUuids.add(consumer.getUuid());

                adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
            }

            // The ordering of this endpoint is in ascending order for both the Consumer ID and UUID values.
            consumers.sort(Comparator.comparing(ConsumerDTO::getId)
                .thenComparing(ConsumerDTO::getUuid));
        }

        @Test
        public void shouldPageEntitlementCounts() throws Exception {
            int pageSize = 5;
            List<String> actualConsumerUuids = new ArrayList<>();
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfConsumers; pageIndex++) {
                RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
                    .consumerUuids(consumerUuids);

                Response response = Request.from(adminClient)
                    .setPath("/rhsmapi/consumers/entitlement_counts")
                    .setMethod("POST")
                    .addQueryParam("page", String.valueOf(pageIndex))
                    .addQueryParam("per_page", String.valueOf(pageSize))
                    .setBody(ApiClient.MAPPER.writeValueAsString(body))
                    .execute();

                assertThat(response)
                    .isNotNull()
                    .returns(200, Response::getCode);

                List<ConsumerEntitlementCountsDTO> entCounts = response
                    .deserialize(new TypeReference<List<ConsumerEntitlementCountsDTO>>() {});

                List<String> consumerUuids = entCounts
                    .stream()
                    .map(ConsumerEntitlementCountsDTO::getConsumerUuid)
                    .toList();

                int startIndex = (pageIndex - 1) * pageSize;
                int endIndex = startIndex + pageSize;
                List<String> expected = consumers.stream()
                    .map(ConsumerDTO::getUuid)
                    .toList()
                    .subList(startIndex, endIndex);

                assertThat(consumerUuids)
                    .isNotNull()
                    .containsExactlyElementsOf(expected);

                actualConsumerUuids.addAll(consumerUuids);
            }
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) throws Exception {
            RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
                .addConsumerIdsItem(StringUtil.random("id-"));

            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/consumers/entitlement_counts")
                .setMethod("POST")
                .addQueryParam("page", String.valueOf(page))
                .addQueryParam("per_page", "5")
                .setBody(ApiClient.MAPPER.writeValueAsString(body))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(400, Response::getCode);
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) throws Exception {
            RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
                .addConsumerIdsItem(StringUtil.random("id-"));

            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/consumers/entitlement_counts")
                .setMethod("POST")
                .addQueryParam("page", "1")
                .addQueryParam("per_page", String.valueOf(pageSize))
                .setBody(ApiClient.MAPPER.writeValueAsString(body))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(400, Response::getCode);
        }

        @Test
        public void shouldPageUsingDefaultPageSizeWithOnlyPageIndex() throws Exception {
            RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
                .consumerUuids(consumerUuids);

            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/consumers/entitlement_counts")
                .setMethod("POST")
                .addQueryParam("page", "1")
                .setBody(ApiClient.MAPPER.writeValueAsString(body))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(200, Response::getCode);

            List<ConsumerEntitlementCountsDTO> entCounts = response
                .deserialize(new TypeReference<List<ConsumerEntitlementCountsDTO>>() {});

            List<String> actual = entCounts
                .stream()
                .map(ConsumerEntitlementCountsDTO::getConsumerUuid)
                .toList();

            assertThat(actual)
                .isNotNull()
                .hasSize(DEFAULT_PAGE_SIZE);
        }

        @Test
        public void shouldPageWithOnlyPerPage() throws Exception {
            RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
                .consumerUuids(consumerUuids);

            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/consumers/entitlement_counts")
                .setMethod("POST")
                .addQueryParam("per_page", "5")
                .setBody(ApiClient.MAPPER.writeValueAsString(body))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(200, Response::getCode);

            List<ConsumerEntitlementCountsDTO> entCounts = response
                .deserialize(new TypeReference<List<ConsumerEntitlementCountsDTO>>() {});

            List<String> actual = entCounts
                .stream()
                .map(ConsumerEntitlementCountsDTO::getConsumerUuid)
                .toList();

            assertThat(actual)
                .isNotNull()
                .hasSize(5);
        }
    }

}
