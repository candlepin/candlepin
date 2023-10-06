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
package org.candlepin.spec.owners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.OffsetDateTime;
import java.util.List;

@SpecTest
public class OwnerResourceFuturePoolSpecTest {


    private static final OffsetDateTime NOW = OffsetDateTime.now();
    private static final OffsetDateTime YESTERDAY = NOW.minusDays(1);
    private static final OffsetDateTime YEAR_IN_FUTURE = NOW.plusYears(1);
    private static final OffsetDateTime HALF_YEAR_IN_FUTURE = NOW.plusMonths(6);
    private static final OffsetDateTime YEAR_AND_HALF_IN_FUTURE = NOW.plusYears(1).plusMonths(6);
    private static final OffsetDateTime TWO_YEAR_IN_FUTURE = NOW.plusYears(2);

    @Test
    public void shouldAllowConsumeFuturePools() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = createProductByOwner(adminClient, owner);
        PoolDTO futurePool = adminClient.owners().createPool(
            owner.getKey(), Pools.random(product).startDate(YEAR_IN_FUTURE));

        consumerClient.consumers().bind(
            consumer.getUuid(), futurePool.getId(), null, null, null, null, null, null, null);
    }

    @Test
    public void shouldAllowFetchConsumerFuturePoolsWithEntitlements() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product1 = createProductByOwner(adminClient, owner);
        PoolDTO futurePool1 = adminClient.owners().createPool(
            owner.getKey(), Pools.random(product1).startDate(YEAR_IN_FUTURE));
        ProductDTO product2 = createProductByOwner(adminClient, owner);
        PoolDTO futurePool2 = adminClient.owners().createPool(
            owner.getKey(), Pools.random(product2).startDate(TWO_YEAR_IN_FUTURE));

        assertDoesNotThrow(() -> consumerClient.consumers().bind(
            consumer.getUuid(), futurePool1.getId(), null, null, null, null, null, null, null));

        List<PoolDTO> pools = consumerClient.owners().listOwnerPools(owner.getKey(), consumer.getUuid(), null,
            null, null, null, null, null, null, null, null, YEAR_AND_HALF_IN_FUTURE, null, null, null, null,
            null);
        assertPoolIds(pools, futurePool2.getId());
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    public class PoolFiltering {

        private ApiClient adminClient;
        private OwnerDTO owner;
        private PoolDTO currentPool;
        private PoolDTO futurePool1;
        private PoolDTO futurePool2;

        @BeforeAll
        public void setup() throws ApiException {
            adminClient = ApiClients.admin();
            owner = createOwner(adminClient);

            ProductDTO product1 = createProductByOwner(adminClient, owner);
            ProductDTO product2 = createProductByOwner(adminClient, owner);
            currentPool = adminClient.owners().createPool(
                owner.getKey(), Pools.random(product1).startDate(YESTERDAY));
            futurePool1 = adminClient.owners().createPool(
                owner.getKey(), Pools.random(product2).startDate(YEAR_IN_FUTURE));
            futurePool2 = adminClient.owners().createPool(
                owner.getKey(), Pools.random(product2).startDate(TWO_YEAR_IN_FUTURE));

        }

        @Test
        public void shouldFetchCurrentPools() throws ApiException {
            List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey());
            assertPoolIds(pools, currentPool.getId());
        }

        @Test
        public void shouldFetchCurrentAndFuturePools() throws ApiException {
            List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey(), true, false, null);
            assertThat(pools)
                .isNotNull()
                .hasSize(3);
        }

        @Test
        public void shouldAllowFetchFuturePools() throws ApiException {
            List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey(), false, true, null);
            assertPoolIds(pools, futurePool2.getId(), futurePool1.getId());
        }

        @Test
        public void shouldAllowFetchFuturePoolsBasedOnActivationDate() throws ApiException {
            List<PoolDTO> pools = adminClient.owners().listOwnerPools(
                owner.getKey(), false, false, YEAR_AND_HALF_IN_FUTURE);
            assertPoolIds(pools, futurePool2.getId());
        }

        @Test
        public void shouldAllowFetchPoolsThatStartAfterSpecifiedDate() throws ApiException {
            List<PoolDTO> pools = adminClient.owners().listOwnerPools(
                owner.getKey(), false, false, HALF_YEAR_IN_FUTURE);
            assertPoolIds(pools, futurePool1.getId(), futurePool2.getId());
            pools = adminClient.owners().listOwnerPools(
                owner.getKey(), false, false, YEAR_AND_HALF_IN_FUTURE);
            assertPoolIds(pools, futurePool2.getId());
        }

        @Test
        public void shouldNotAllowUseBothAddFutureAndOnlyFutureFlags() {
            assertBadRequest(() -> adminClient.owners().listOwnerPools(owner.getKey(), true, true, null));
        }

        @Test
        public void shouldNotAllowUseAfterAndEitherAddFutureOrOnlyFutureFlags() {
            assertBadRequest(() -> {
                adminClient.owners().listOwnerPools(owner.getKey(), true, false, YEAR_AND_HALF_IN_FUTURE);
            });
            assertBadRequest(() -> {
                adminClient.owners().listOwnerPools(owner.getKey(), false, true, YEAR_AND_HALF_IN_FUTURE);
            });
        }

    }

    private static ProductDTO createProductByOwner(ApiClient adminClient, OwnerDTO owner)
        throws ApiException {
        return adminClient.ownerProducts().createProduct(owner.getKey(), Products.random());
    }

    private OwnerDTO createOwner(ApiClient client) throws ApiException {
        return client.owners().createOwner(Owners.random());
    }

    private static ApiClient createUserClient(ApiClient client, OwnerDTO owner) throws ApiException {
        UserDTO user = UserUtil.createUser(client, owner);
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private void assertPoolIds(List<PoolDTO> pools, String... id) {
        assertThat(pools)
            .map(PoolDTO::getId)
            .containsOnly(id);
    }
}
