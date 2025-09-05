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
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnauthorized;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerEntitlementCountsDTO;
import org.candlepin.dto.api.client.v1.ConsumerFeedDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.HypervisorUpdateResultDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.RhsmApiConsumerEntitlementCountsQueryDTO;
import org.candlepin.dto.api.client.v1.RhsmApiEntitlementCountDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ConsumerFeedInstalledProducts;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Test
    public void shouldNotAllowNonAdminUsersToGetConsumerFeed() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));

        assertForbidden(() -> userClient.rhsmApi().getConsumerFeeds("randomOrg", null, null, null, null
            , null));
    }

    @Test
    public void shouldNotAllowNoAuthClientsToGetConsumerFeed() {
        ApiClient noAuthClient = ApiClients.noAuth();

        assertUnauthorized(() -> noAuthClient.rhsmApi().getConsumerFeeds("randomOrg", null, null, null, null
            , null));
    }

    @Test
    public void shouldGetConsumerFeed() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, null, null, null, null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .hasSize(3)
            .extracting(ConsumerFeedDTO::getId)
            .containsExactlyInAnyOrder(consumer1.getId(), consumer2.getId(), consumer3.getId());
    }

    @Test
    public void shouldGetConsumerFeedIdFilter() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(
            Consumers.random(owner).id(StringUtil.random("A")));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(
            Consumers.random(owner).id(StringUtil.random("B")));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(
            Consumers.random(owner).id(StringUtil.random("C")));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, consumer1.getId(), null, null, null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .hasSize(2)
            .extracting(ConsumerFeedDTO::getId)
            .containsExactlyInAnyOrder(consumer2.getId(), consumer3.getId());
    }

    @Test
    public void shouldGetConsumerFeedUuidFilter() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(
            Consumers.random(owner).uuid(StringUtil.random("A")));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(
            Consumers.random(owner).uuid(StringUtil.random("B")));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(
            Consumers.random(owner).uuid(StringUtil.random("C")));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, null, consumer1.getUuid(), null, null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .hasSize(2)
            .extracting(ConsumerFeedDTO::getId)
            .containsExactlyInAnyOrder(consumer2.getId(), consumer3.getId());
    }

    @Test
    public void shouldGetConsumerFeedAfterCheckinFilter() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(
            Consumers.random(owner).lastCheckin(OffsetDateTime.now().minusDays(3)));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(
            Consumers.random(owner).lastCheckin(OffsetDateTime.now().minusDays(2)));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(
            Consumers.random(owner).lastCheckin(OffsetDateTime.now().minusDays(1)));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, null, null, consumer2.getLastCheckin().plusMinutes(1), null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .singleElement()
            .returns(consumer3.getId(), ConsumerFeedDTO::getId);
    }

    @Test
    public void shouldGetConsumerFeedAllFiltersCombined() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(
            Consumers.random(owner)
                .id(StringUtil.random("A"))
                .uuid(StringUtil.random("A"))
                .lastCheckin(OffsetDateTime.now().minusDays(4)));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(
            Consumers.random(owner)
                .id(StringUtil.random("B"))
                .uuid(StringUtil.random("B"))
                .lastCheckin(OffsetDateTime.now().minusDays(3)));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(
            Consumers.random(owner)
                .id(StringUtil.random("C"))
                .uuid(StringUtil.random("C"))
                .lastCheckin(OffsetDateTime.now().minusDays(2)));
        ConsumerDTO consumer4 = adminClient.consumers().createConsumer(
            Consumers.random(owner)
                .id(StringUtil.random("D"))
                .uuid(StringUtil.random("D"))
                .lastCheckin(OffsetDateTime.now().minusDays(1)));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, consumer3.getId(), consumer2.getUuid(),
                consumer1.getLastCheckin().plusMinutes(1), null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .singleElement()
            .returns(consumer4.getId(), ConsumerFeedDTO::getId);
    }

    @Test
    public void shouldGetConsumerFeedWithInstalledProducts() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        ConsumerInstalledProductDTO product = new ConsumerInstalledProductDTO()
            .productId(StringUtil.random("productId"))
            .productName(StringUtil.random("productName"))
            .version(StringUtil.random("version"));
        ConsumerDTO consumer =
            adminClient.consumers().createConsumer(Consumers.random(owner).addInstalledProductsItem(product));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, null, null, null, null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .singleElement()
            .returns(Set.of(ConsumerFeedInstalledProducts.toConsumerFeedInstalled(product)),
                ConsumerFeedDTO::getInstalledProducts);
    }

    @Test
    public void shouldGetConsumerFeedWithFacts() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        Map<String, String> facts = new HashMap<>();
        facts.put("cpu.cpu_socket(s)", "2");             // allowed
        facts.put("network.fqdn", "host.redhat.com");    // allowed
        facts.put("random.fact", "xxx");
        ConsumerDTO consumer =
            adminClient.consumers().createConsumer(Consumers.random(owner).facts(facts));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, null, null, null, null, null);

        Map<String, String> expectedFacts = Map.of("cpu.cpu_socket(s)", "2", "network.fqdn",
            "host.redhat.com");

        assertThat(consumerFeed)
            .isNotNull()
            .singleElement()
            .returns(expectedFacts, ConsumerFeedDTO::getFacts);
    }

    @Test
    public void shouldGetConsumerFeedWithAddons() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        HashSet<String> addOns = new HashSet<>();
        addOns.add("addon1");
        addOns.add("addon2");
        ConsumerDTO consumer =
            adminClient.consumers().createConsumer(Consumers.random(owner).addOns(addOns));

        List<ConsumerFeedDTO> consumerFeed = adminClient.rhsmApi()
            .getConsumerFeeds(ownerKey, null, null, null, null, null);

        assertThat(consumerFeed)
            .isNotNull()
            .singleElement()
            .returns(addOns, ConsumerFeedDTO::getSyspurposeAddons);
    }

    @Test
    public void shouldGetConsumerFeedWithHypervisorsAndGuests() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ConsumerDTO host1 = admin.consumers().createConsumer(Consumers.random(owner));
        String host1Guest1VirtUuid = StringUtil.random("host1-guest1-virt-uuid-");
        String host1Guest2VirtUuid = StringUtil.random("host1-guest2-virt-uuid-");
        ConsumerDTO host1Guest1 = createGuest(admin, owner, host1Guest1VirtUuid);
        ConsumerDTO host1Guest2 = createGuest(admin, owner, host1Guest2VirtUuid);
        linkHostToGuests(admin, host1, host1Guest1VirtUuid, host1Guest2VirtUuid);

        ConsumerDTO host2 = admin.consumers().createConsumer(Consumers.random(owner));
        String host2Guest1VirtUuid = StringUtil.random("host2-guest1-virt-uuid-");
        String host2Guest2VirtUuid = StringUtil.random("host2-guest2-virt-uuid-");
        ConsumerDTO host2Guest1 = createGuest(admin, owner, host2Guest1VirtUuid);
        ConsumerDTO host2Guest2 = createGuest(admin, owner, host2Guest2VirtUuid);
        linkHostToGuests(admin, host2, host2Guest1VirtUuid, host2Guest2VirtUuid);

        List<ConsumerFeedDTO> actual = admin.rhsmApi()
            .getConsumerFeeds(owner.getKey(), null, null, null, null, null);

        assertThat(actual)
            .isNotNull()
            .hasSize(6);

        List<ConsumerFeedDTO> filteredResult = actual.stream()
            .filter(x -> x.getHypervisorUuid() != null)
            .toList();

        assertThat(filteredResult)
            .isNotNull()
            .hasSize(4)
            .extracting(
                ConsumerFeedDTO::getHypervisorUuid,
                ConsumerFeedDTO::getHypervisorName,
                ConsumerFeedDTO::getGuestId,
                ConsumerFeedDTO::getId,
                ConsumerFeedDTO::getUuid
            )
            .containsExactly(
                tuple(host1.getUuid(), host1.getName(), host1Guest1VirtUuid,
                    host1Guest1.getId(), host1Guest1.getUuid()),
                tuple(host1.getUuid(), host1.getName(), host1Guest2VirtUuid,
                    host1Guest2.getId(), host1Guest2.getUuid()),
                tuple(host2.getUuid(), host2.getName(), host2Guest1VirtUuid,
                    host2Guest1.getId(), host2Guest1.getUuid()),
                tuple(host2.getUuid(), host2.getName(), host2Guest2VirtUuid,
                    host2Guest2.getId(), host2Guest2.getUuid())
            );
    }

    @Test
    public void shouldGetConsumerFeedWithAllAttributesAndFiltersCorrectlyApplied() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        OffsetDateTime now = OffsetDateTime.now();

        // Host 1 will be filtered out
        ConsumerDTO host1 = admin.consumers().createConsumer(Consumers.random(owner));
        String host1Guest1VirtUuid = StringUtil.random("host1-guest1-virt-uuid-");
        String host1Guest2VirtUuid = StringUtil.random("host1-guest2-virt-uuid-");
        ConsumerDTO host1Guest1 = admin.consumers().createConsumer(Consumers.random(owner)
            .id(StringUtil.random("aaa"))
            .uuid(StringUtil.random("aaa-uuid"))
            .lastCheckin(now.minusDays(4))
            .facts(Map.ofEntries(
                Facts.VirtUuid.withValue(host1Guest1VirtUuid),
                Facts.VirtIsGuest.withValue("true"),
                Facts.Arch.withValue("x86_64")
            )));
        admin.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(
                Facts.VirtUuid.withValue(host1Guest2VirtUuid),
                Facts.VirtIsGuest.withValue("true"),
                Facts.Arch.withValue("x86_64")
            )));
        linkHostToGuests(admin, host1, host1Guest1VirtUuid, host1Guest2VirtUuid);

        // Host 2 + guests (only one will be used)
        ConsumerDTO host2 = admin.consumers().createConsumer(Consumers.random(owner));
        String host2Guest1VirtUuid = StringUtil.random("host2-guest1-virt-uuid-");
        String host2Guest2VirtUuid = StringUtil.random("host2-guest2-virt-uuid-");

        // not used guest
        createGuest(admin, owner, host2Guest1VirtUuid);

        // target guest
        Set<String> addons = Set.of("addon1", "addon2");
        ConsumerInstalledProductDTO product = new ConsumerInstalledProductDTO()
            .productId("pid")
            .productName("pname")
            .version("1.0");

        Map<String, String> facts = Map.ofEntries(
            Facts.VirtUuid.withValue(host2Guest2VirtUuid),
            Facts.VirtIsGuest.withValue("true"),
            Facts.Arch.withValue("x86_64"),
            Facts.CpuSockets.withValue("2")
        );

        ConsumerDTO target = admin.consumers().createConsumer(Consumers.random(owner)
            .id(StringUtil.random("bbb"))
            .uuid(StringUtil.random("bbb-uuid"))
            .lastCheckin(now.minusDays(1))
            .addOns(addons)
            .addInstalledProductsItem(product)
            .facts(facts)
        );

        linkHostToGuests(admin, host2, host2Guest1VirtUuid, host2Guest2VirtUuid);

        List<ConsumerFeedDTO> actual = admin.rhsmApi().getConsumerFeeds(
            owner.getKey(), host1Guest1.getId(), host1Guest1.getUuid(), now.minusDays(4), null, null);

        Map<String, String> expectedFacts = Map.of("cpu.cpu_socket(s)", "2",
            "uname.machine", "x86_64",
            "virt.is_guest", "true");

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .returns(host2Guest2VirtUuid, ConsumerFeedDTO::getGuestId)
            .returns(host2.getUuid(), ConsumerFeedDTO::getHypervisorUuid)
            .returns(host2.getName(), ConsumerFeedDTO::getHypervisorName)
            .returns(expectedFacts, ConsumerFeedDTO::getFacts)
            .returns(addons, ConsumerFeedDTO::getSyspurposeAddons)
            .returns(Set.of(ConsumerFeedInstalledProducts.toConsumerFeedInstalled(product)),
                ConsumerFeedDTO::getInstalledProducts);
    }

    @Test
    public void shouldReturnGuestsWithNonReverseEndianVirtUuidUsingHypervisorCheckIn() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String guestUuidSame = "78d7e200-b7d6-4cfe-b7a9-5700e8094df3"; // normal form
        String guestUuidReversed = "00e2d778-d6b7-fe4c-b7a9-5700e8094df3"; // reverse-endian of host UUID
        ConsumerDTO guest = createGuest(adminClient, owner, guestUuidSame);
        String hypervisorName = StringUtil.random("name-");

        hypervisorCheckin(owner, adminClient, hypervisorName, StringUtil.random("id-"),
            List.of(guestUuidReversed), Map.of("random.fact", "random.value"),
            StringUtil.random("reporter-"), true);

        List<ConsumerFeedDTO> feeds = adminClient.rhsmApi()
            .getConsumerFeeds(owner.getKey(), null, null, null, null, null);

        assertThat(feeds)
            .isNotNull()
            .hasSize(2);

        List<ConsumerFeedDTO> filteredFeeds = feeds
            .stream()
            .filter(x -> x.getHypervisorUuid() != null)
            .toList();

        assertThat(filteredFeeds)
            .isNotNull()
            .singleElement()
            .returns(guestUuidSame, ConsumerFeedDTO::getGuestId)
            .returns(hypervisorName, ConsumerFeedDTO::getHypervisorName)
            .returns(guest.getId(), ConsumerFeedDTO::getId)
            .returns(guest.getUuid(), ConsumerFeedDTO::getUuid);
    }

    @Test
    public void shouldSelectLatestHostWhenGuestMovedDeduplicatedByMaxUpdated()
        throws InterruptedException {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());
        ConsumerDTO hostA = admin.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO hostB = admin.consumers().createConsumer(Consumers.random(owner));

        String guestVirtUuid = StringUtil.random("moving-guest-virt-uuid-");
        ConsumerDTO guest = createGuest(admin, owner, guestVirtUuid);

        // First mapping → hostA (older)
        linkHostToGuests(admin, hostA, guestVirtUuid);
        Thread.sleep(1000);
        // Second mapping → hostB (newer) => should win via MAX(updated)
        linkHostToGuests(admin, hostB, guestVirtUuid);

        List<ConsumerFeedDTO> feed = admin.rhsmApi()
            .getConsumerFeeds(owner.getKey(), null, null, null, null, null);

        List<ConsumerFeedDTO> filteredFeed = feed.stream()
            .filter(x -> x.getHypervisorUuid() != null)
            .toList();

        assertThat(filteredFeed)
            .isNotNull()
            .singleElement()
            .returns(guestVirtUuid, ConsumerFeedDTO::getGuestId)
            .returns(hostB.getUuid(), ConsumerFeedDTO::getHypervisorUuid)
            .returns(hostB.getName(), ConsumerFeedDTO::getHypervisorName)
            .returns(guest.getId(), ConsumerFeedDTO::getId)
            .returns(guest.getUuid(), ConsumerFeedDTO::getUuid);
    }

    @Test
    public void shouldGetConsumerFeedWithMultipleHypervisorsUsingHypervisorCheckin() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String guest1VirtUuid = UUID.randomUUID().toString();
        String guest2VirtUuid = UUID.randomUUID().toString();
        String guest3VirtUuid = UUID.randomUUID().toString();
        String guest4VirtUuid = UUID.randomUUID().toString();
        ConsumerDTO guest1 = createGuest(adminClient, owner, guest1VirtUuid);
        ConsumerDTO guest2 = createGuest(adminClient, owner, guest2VirtUuid);
        ConsumerDTO guest3 = createGuest(adminClient, owner, guest3VirtUuid);
        ConsumerDTO guest4 = createGuest(adminClient, owner, guest4VirtUuid);
        String hypervisorName1 = StringUtil.random("name-");
        String hypervisorName2 = StringUtil.random("name-");

        hypervisorCheckin(owner, adminClient, hypervisorName1, StringUtil.random("id-"),
            List.of(guest1VirtUuid, guest2VirtUuid),
            Map.of("random.fact", "random.value"),
            StringUtil.random("reporter-"), true);
        hypervisorCheckin(owner, adminClient, hypervisorName2, StringUtil.random("id-"),
            List.of(guest3VirtUuid, guest4VirtUuid),
            Map.of("random.fact", "random.value"),
            StringUtil.random("reporter-"), true);

        List<ConsumerFeedDTO> feeds = adminClient.rhsmApi()
            .getConsumerFeeds(owner.getKey(), null, null, null, null, null);

        assertThat(feeds)
            .isNotNull()
            .hasSize(6);

        List<ConsumerFeedDTO> filteredFeeds = feeds
            .stream()
            .filter(x -> x.getHypervisorUuid() != null)
            .toList();

        assertThat(filteredFeeds)
            .isNotNull()
            .hasSize(4)
            .extracting(
                ConsumerFeedDTO::getHypervisorName,
                ConsumerFeedDTO::getGuestId,
                ConsumerFeedDTO::getId,
                ConsumerFeedDTO::getUuid
            )
            .containsExactly(
                tuple(hypervisorName1, guest1VirtUuid, guest1.getId(), guest1.getUuid()),
                tuple(hypervisorName1, guest2VirtUuid, guest2.getId(), guest2.getUuid()),
                tuple(hypervisorName2, guest3VirtUuid, guest3.getId(), guest3.getUuid()),
                tuple(hypervisorName2, guest4VirtUuid, guest4.getId(), guest4.getUuid())
            );
    }

    @Test
    public void shouldGetConsumerFeedWithHypervisorUsingHypervisorCheckin() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String guest1VirtUuid = UUID.randomUUID().toString();
        String guest2VirtUuid = UUID.randomUUID().toString();
        String guest3VirtUuid = UUID.randomUUID().toString();
        ConsumerDTO guest1 = createGuest(adminClient, owner, guest1VirtUuid);
        ConsumerDTO guest2 = createGuest(adminClient, owner, guest2VirtUuid);
        ConsumerDTO guest3 = createGuest(adminClient, owner, guest3VirtUuid);
        String hypervisorName = StringUtil.random("name-");

        hypervisorCheckin(owner, adminClient, hypervisorName, StringUtil.random("id-"),
            List.of(guest1VirtUuid, guest2VirtUuid, guest3VirtUuid),
            Map.of("random.fact", "random.value"),
            StringUtil.random("reporter-"), true);

        List<ConsumerFeedDTO> feeds = adminClient.rhsmApi()
            .getConsumerFeeds(owner.getKey(), null, null, null, null, null);

        assertThat(feeds)
            .isNotNull()
            .hasSize(4);

        List<ConsumerFeedDTO> filteredFeeds = feeds
            .stream()
            .filter(x -> x.getHypervisorUuid() != null)
            .toList();

        assertThat(filteredFeeds)
            .isNotNull()
            .hasSize(3)
            .extracting(
                ConsumerFeedDTO::getHypervisorName,
                ConsumerFeedDTO::getGuestId,
                ConsumerFeedDTO::getId,
                ConsumerFeedDTO::getUuid
            )
            .containsExactly(
                tuple(hypervisorName, guest1VirtUuid,
                    guest1.getId(), guest1.getUuid()),
                tuple(hypervisorName, guest2VirtUuid,
                    guest2.getId(), guest2.getUuid()),
                tuple(hypervisorName, guest3VirtUuid,
                    guest3.getId(), guest3.getUuid())
            );
    }

    private HypervisorUpdateResultDTO hypervisorCheckin(OwnerDTO owner, ApiClient consumerClient,
        String hypervisorName, String hypervisorId, List<String> guestIds, Map<String, String> facts,
        String reporterId, boolean createMissing) throws ApiException, IOException {
        JsonNode hostGuestMapping = getAsyncHostGuestMapping(hypervisorName, hypervisorId, guestIds, facts);
        AsyncJobStatusDTO job = consumerClient.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), createMissing, reporterId, hostGuestMapping.toString());
        HypervisorUpdateResultDTO resultData = null;
        if (job != null) {
            AsyncJobStatusDTO status = consumerClient.jobs().waitForJob(job.getId());
            assertThatJob(status).isFinished();
            resultData = getResultData(status);
        }

        return resultData;
    }

    private JsonNode getAsyncHostGuestMapping(String name, String id, List<String> expectedGuestIds,
        Map<String, String> facts) {
        List<Map<String, String>> guestIds = expectedGuestIds.stream()
            .map(gid -> Map.of("guestId", gid))
            .collect(Collectors.toList());

        Map<String, Object> hypervisor = Map.of(
            "name", name,
            "hypervisorId", Map.of("hypervisorId", id),
            "guestIds", guestIds,
            "facts", facts);

        Object node = Map.of("hypervisors", List.of(hypervisor));

        return ApiClient.MAPPER.valueToTree(node);
    }

    private HypervisorUpdateResultDTO getResultData(AsyncJobStatusDTO status) throws IOException {
        if (status == null || status.getResultData() == null) {
            return null;
        }

        return ApiClient.MAPPER.convertValue(status.getResultData(), HypervisorUpdateResultDTO.class);
    }

    private ConsumerDTO createGuest(ApiClient client, OwnerDTO owner, String virtUuid) {
        return client.consumers().createConsumer(Consumers.random(owner).facts(Map.ofEntries(
            Facts.VirtUuid.withValue(virtUuid),
            Facts.VirtIsGuest.withValue("true"),
            Facts.Arch.withValue("x86_64")
        )));
    }

    private void linkHostToGuests(ApiClient client, ConsumerDTO host, String... virtUuid) {
        List<GuestIdDTO> guestIds = Arrays.stream(virtUuid)
            .map(this::toGuestId)
            .collect(Collectors.toList());

        linkHostToGuests(client, host, guestIds);
    }

    private void linkHostToGuests(ApiClient client, ConsumerDTO host, List<GuestIdDTO> guestIds) {
        client.consumers()
            .updateConsumer(host.getUuid(), host.guestIds(guestIds));
    }

    private GuestIdDTO toGuestId(String guestId) {
        return new GuestIdDTO()
            .guestId(guestId);
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class ListConsumerFeedPagingTests {
        private final ApiClient adminClient = ApiClients.admin();

        private OwnerDTO owner;
        private String ownerKey;

        private final int numberOfConsumers = 20;

        private final List<String> consumerUuids = new ArrayList<>();

        @BeforeAll
        public void beforeAll() {
            owner = adminClient.owners().createOwner(Owners.random());
            ownerKey = owner.getKey();

            for (int i = 0; i < numberOfConsumers; i++) {
                ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
                consumerUuids.add(consumer.getUuid());
            }
        }

        @Test
        public void shouldPageConsumerFeed() {
            int pageSize = 5;
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfConsumers; pageIndex++) {

                Response response = Request.from(adminClient)
                    .setPath("/rhsmapi/owners/{org_key}/consumer_feed")
                    .setPathParam("org_key", ownerKey)
                    .addQueryParam("page", String.valueOf(pageIndex))
                    .addQueryParam("per_page", String.valueOf(pageSize))
                    .execute();

                assertThat(response)
                    .isNotNull()
                    .returns(200, Response::getCode);

                List<ConsumerFeedDTO> consumerFeeds = response
                    .deserialize(new TypeReference<List<ConsumerFeedDTO>>() {});

                List<String> actualConsumerUuids = consumerFeeds
                    .stream()
                    .map(ConsumerFeedDTO::getUuid)
                    .toList();

                int startIndex = (pageIndex - 1) * pageSize;
                int endIndex = startIndex + pageSize;
                List<String> expected = new ArrayList<>(consumerUuids.subList(startIndex, endIndex));

                assertThat(actualConsumerUuids)
                    .isNotNull()
                    .containsExactlyElementsOf(expected);
            }
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) throws Exception {
            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/owners/{org_key}/consumer_feed")
                .setPathParam("org_key", ownerKey)
                .addQueryParam("page", String.valueOf(page))
                .addQueryParam("per_page", "5")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(400, Response::getCode);
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) throws Exception {
            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/owners/{org_key}/consumer_feed")
                .setPathParam("org_key", ownerKey)
                .addQueryParam("page", "1")
                .addQueryParam("per_page", String.valueOf(pageSize))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(400, Response::getCode);
        }

        @Test
        public void shouldPageUsingDefaultPageSizeWithOnlyPageIndex() throws Exception {
            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/owners/{org_key}/consumer_feed")
                .setPathParam("org_key", ownerKey)
                .addQueryParam("page", "1")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(200, Response::getCode);

            List<ConsumerFeedDTO> entCounts = response
                .deserialize(new TypeReference<List<ConsumerFeedDTO>>() {});

            List<String> actual = entCounts
                .stream()
                .map(ConsumerFeedDTO::getUuid)
                .toList();

            assertThat(actual)
                .isNotNull()
                .hasSize(DEFAULT_PAGE_SIZE);
        }

        @Test
        public void shouldPageWithOnlyPerPage() throws Exception {
            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/owners/{org_key}/consumer_feed")
                .setPathParam("org_key", ownerKey)
                .addQueryParam("per_page", "5")
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(200, Response::getCode);

            List<ConsumerFeedDTO> consumerFeeds = response
                .deserialize(new TypeReference<List<ConsumerFeedDTO>>() {});

            List<String> actual = consumerFeeds
                .stream()
                .map(ConsumerFeedDTO::getUuid)
                .toList();

            assertThat(actual)
                .isNotNull()
                .hasSize(5);
        }

        @Test
        public void shouldReturnEmptyPageConsumerFeed() {
            Response response = Request.from(adminClient)
                .setPath("/rhsmapi/owners/{org_key}/consumer_feed")
                .setPathParam("org_key", ownerKey)
                .addQueryParam("page", String.valueOf(2))
                .addQueryParam("per_page", String.valueOf(numberOfConsumers))
                .execute();

            assertThat(response)
                .isNotNull()
                .returns(200, Response::getCode);

            List<ConsumerFeedDTO> consumerFeeds = response
                .deserialize(new TypeReference<List<ConsumerFeedDTO>>() {});

            assertThat(consumerFeeds)
                .isNotNull()
                .isEmpty();
        }
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
