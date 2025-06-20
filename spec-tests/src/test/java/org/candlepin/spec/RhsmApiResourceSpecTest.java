package org.candlepin.spec;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.RhsmApiConsumerEntitlementCountsDTO;
import org.candlepin.dto.api.client.v1.RhsmApiConsumerEntitlementCountsDTOAllOfEntitlementCounts;
import org.candlepin.dto.api.client.v1.RhsmApiConsumerEntitlementCountsQueryDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.junit.jupiter.api.Test;

@SpecTest
public class RhsmApiResourceSpecTest {

    // TODO: Maybe there is a better name here?
    private static final int MAX_CONSUMER_UUIDS = 1000;

    @Test
    @OnlyInHosted
    public void shouldListEntitlementsForConsumers() {
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

        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .isNotNull()
            .hasSize(4);

        // TODO: This may not be correct because of the pool ordering!
        adminClient.consumers().bindPool(consumer1.getId(), pools.get(0).getId(), 1);
        adminClient.consumers().bindPool(consumer2.getId(), pools.get(1).getId(), 2);
        adminClient.consumers().bindPool(consumer3.getId(), pools.get(2).getId(), 3);
        adminClient.consumers().bindPool(consumer1.getId(), pools.get(3).getId(), 4);

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO()
            .consumerUuids(List.of(consumer1.getUuid(), consumer2.getUuid()));

        List<RhsmApiConsumerEntitlementCountsDTO> actual = adminClient.rhsmApi()
            .listConsumerEntitlementCounts(body);

        List<RhsmApiConsumerEntitlementCountsDTO> expected = List.of(
            new RhsmApiConsumerEntitlementCountsDTO()
                .consumerId(consumer1.getId())
                .consumerUuid(consumer1.getUuid())
                .entitlementCounts(List.of(
                    new RhsmApiConsumerEntitlementCountsDTOAllOfEntitlementCounts()
                        .productId(product1.getId())
                        .productName(product1.getName())
                        .contractNumber(sub1.getContractNumber())
                        .subscriptionId(sub1.getId())
                        .count(1), 
                    new RhsmApiConsumerEntitlementCountsDTOAllOfEntitlementCounts()
                        .productId(product4.getId())
                        .productName(product4.getName())
                        .contractNumber(sub4.getContractNumber())
                        .subscriptionId(sub4.getId())
                        .count(3))),
            new RhsmApiConsumerEntitlementCountsDTO()
                .consumerId(consumer2.getId())
                .consumerUuid(consumer2.getUuid())
                .entitlementCounts(List.of(
                    new RhsmApiConsumerEntitlementCountsDTOAllOfEntitlementCounts()
                        .productId(product2.getId())
                        .productName(product2.getName())
                        .contractNumber(sub2.getContractNumber())
                        .subscriptionId(sub2.getId())
                        .count(1))));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void shouldReturnBadRequestForExceedingConsumerUuidLimitWhenListingEntsForConsumers() {
        ApiClient adminClient = ApiClients.admin();

        RhsmApiConsumerEntitlementCountsQueryDTO body = new RhsmApiConsumerEntitlementCountsQueryDTO();
        for (int i = 0; i < MAX_CONSUMER_UUIDS + 1; i++) {
            body.addConsumerUuidsItem(StringUtil.random("uuid-"));
        }

        assertBadRequest(() -> adminClient.rhsmApi().listConsumerEntitlementCounts(body));
    }

}
