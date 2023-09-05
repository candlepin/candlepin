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
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertUnavailable;
import static org.candlepin.spec.bootstrap.data.builder.Pools.PRIMARY_POOL_SUB_KEY;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;



@SpecTest
public class SubscriptionResourceSpecTest {
    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static HostedTestApi hostedTestApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        hostedTestApi = client.hosted();
    }

    @Test
    public void shouldAllowOwnersToCreateSubscriptionsAndRetrieveAll() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        int size = 3;
        for (int i = 0; i < size; i++) {
            createSubscriptionOrPool(owner);
        }
        assertThat(ownerApi.getOwnerSubscriptions(owner.getKey())).hasSize(size);
    }

    // TODO: Rewrite this test to have the appropriate pool/sub creation flow depending on the
    // context of the test (standalone vs hosted)
    @Test
    public void shouldAllowAdminsToDeleteSubscriptions() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        PoolDTO pool = createSubscriptionOrPool(owner);
        assertThat(ownerApi.getOwnerSubscriptions(owner.getKey())).hasSize(1);
        deleteSubscriptionOrPool(owner, pool);
        assertThat(ownerApi.getOwnerSubscriptions(owner.getKey())).hasSize(0);
    }

    @Test
    @OnlyInHosted
    public void shouldActivateSubscription() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        createSubscriptionOrPool(owner);
        assertThat(ownerApi.getOwnerSubscriptions(owner.getKey())).hasSize(1);
        ConsumerDTO consumer = client.consumers().createConsumer(Consumers.random(owner));
        client.subscriptions().activateSubscription(consumer.getUuid(), "mail", "locale");
    }

    @Test
    @OnlyInStandalone
    public void shouldThrowExceptionOnActivateSubscriptionInStandalone() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners()
            .createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner));

        assertUnavailable(() -> adminClient.subscriptions()
            .activateSubscription(consumer.getUuid(), "consumer@e.mail", "consumer_locale"));
    }

    @Test
    public void shouldSubscriptionsDerivedProvidedProductsReturnEmptyArrayWhenNull() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        // Product does not have derived/provided products
        ProductDTO prod = ownerProductApi.createProductByOwner(owner.getKey(), Products.random());
        PoolDTO pool = Pools.random()
            .productId(prod.getId())
            .quantity(2L)
            .subscriptionId(StringUtil.random("test"))
            .subscriptionSubKey(PRIMARY_POOL_SUB_KEY);
        pool = ownerApi.createPool(owner.getKey(), pool);
        assertThat(ownerApi.getOwnerSubscriptions(owner.getKey())).isNotNull()
            .singleElement()
            .returns(Set.of(), SubscriptionDTO::getProvidedProducts)
            .returns(Set.of(), SubscriptionDTO::getDerivedProvidedProducts);
        client.pools().deletePool(pool.getId());

        //  Product provided products only
        ProductDTO providedProduct = ownerProductApi.createProductByOwner(owner.getKey(), Products.random());
        prod = Products.random().providedProducts(Set.of(providedProduct));
        prod = ownerProductApi.createProductByOwner(owner.getKey(), prod);
        pool = Pools.random()
            .productId(prod.getId())
            .quantity(2L)
            .subscriptionId(StringUtil.random("test"))
            .subscriptionSubKey(PRIMARY_POOL_SUB_KEY);
        pool = ownerApi.createPool(owner.getKey(), pool);
        assertThat(ownerApi.getOwnerSubscriptions(owner.getKey())).isNotNull()
            .singleElement()
            .returns(Set.of(providedProduct), SubscriptionDTO::getProvidedProducts)
            .returns(Set.of(), SubscriptionDTO::getDerivedProvidedProducts);
        client.pools().deletePool(pool.getId());

        //  Product derived provided products only
        ProductDTO derivedProduct = ownerProductApi.createProductByOwner(
            owner.getKey(), Products.random().providedProducts(Set.of(providedProduct)));
        prod = Products.random().derivedProduct(derivedProduct);
        prod = ownerProductApi.createProductByOwner(owner.getKey(), prod);
        pool = Pools.random()
            .productId(prod.getId())
            .quantity(2L)
            .subscriptionId(StringUtil.random("test"))
            .subscriptionSubKey(PRIMARY_POOL_SUB_KEY);
        pool = ownerApi.createPool(owner.getKey(), pool);
        assertThat(ownerApi.getOwnerSubscriptions(owner.getKey())).isNotNull()
            .singleElement()
            .returns(Set.of(), SubscriptionDTO::getProvidedProducts)
            .returns(Set.of(providedProduct), SubscriptionDTO::getDerivedProvidedProducts);
        client.pools().deletePool(pool.getId());
    }

    // TODO: Remove this. The old sub-or-pool creation was very bad for maintenance and test logic
    private PoolDTO createSubscriptionOrPool(OwnerDTO owner) {
        if (CandlepinMode.isStandalone()) {
            ProductDTO prod = ownerProductApi.createProductByOwner(owner.getKey(), Products.random());

            PoolDTO pool = Pools.random(prod)
                .subscriptionId(StringUtil.random("id"))
                .subscriptionSubKey(PRIMARY_POOL_SUB_KEY)
                .upstreamPoolId(StringUtil.random("pool"));

            return ownerApi.createPool(owner.getKey(), pool);
        }
        else {
            ProductDTO prod = hostedTestApi.createProduct(Products.random());
            SubscriptionDTO sub = hostedTestApi.createSubscription(Subscriptions.random(owner, prod));
            AsyncJobStatusDTO job = ownerApi.refreshPools(owner.getKey(), false);

            job = client.jobs().waitForJob(job.getId());
            assertThatJob(job).isFinished();
            return ownerApi.listOwnerPools(owner.getKey()).stream()
                .filter(x -> x.getSubscriptionId().equals(sub.getId()))
                .collect(Collectors.toList()).get(0);
        }
    }

    private void deleteSubscriptionOrPool(OwnerDTO owner, PoolDTO pool) {
        if (CandlepinMode.isStandalone()) {
            client.pools().deletePool(pool.getId());
        }
        else {
            hostedTestApi.deleteSubscription(pool.getSubscriptionId());
            AsyncJobStatusDTO job = ownerApi.refreshPools(owner.getKey(), false);
            job = client.jobs().waitForJob(job.getId());
            assertThatJob(job).isFinished();
        }
    }
}
