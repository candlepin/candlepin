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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MultiplierSpecTest {
    private static final long DEFAULT_MULTIPLIER = 1L;

    private ApiClient adminClient;
    private OwnerDTO owner;
    private String ownerKey;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
        ownerKey = owner.getKey();
    }

    @Test
    public void shouldHaveTheCorrectQuantity() {
        long multiplier = 25L;
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random().multiplier(multiplier));

        long quantity = 4L;
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(product).quantity(quantity));

        List<PoolDTO> actualPools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(actualPools)
            .singleElement()
            .returns(pool.getId(), PoolDTO::getId)
            .returns(multiplier * quantity, PoolDTO::getQuantity);
    }

    @Test
    public void shouldDefaultTheMultiplierIfItIsNegative() {
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random().multiplier(-10L));
        assertEquals(DEFAULT_MULTIPLIER, product.getMultiplier());

        long quantity = 34L;
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(product).quantity(quantity));

        List<PoolDTO> actualPools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(actualPools)
            .singleElement()
            .returns(pool.getId(), PoolDTO::getId)
            .returns(quantity * DEFAULT_MULTIPLIER, PoolDTO::getQuantity);
    }

    @Test
    public void shouldDefaultTheMultiplierIfItIsZero() {
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random().multiplier(0L));
        assertEquals(DEFAULT_MULTIPLIER, product.getMultiplier());

        long quantity = 34L;
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(product).quantity(quantity));

        List<PoolDTO> actualPools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(actualPools)
            .singleElement()
            .returns(pool.getId(), PoolDTO::getId)
            .returns(quantity * DEFAULT_MULTIPLIER, PoolDTO::getQuantity);
    }

    @Test
    @OnlyInHosted
    public void shouldHaveTheCorrectQuantityAfterARefresh() {
        long multiplier = 100L;
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random().multiplier(multiplier));

        long quantity = 5L;
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(product).quantity(quantity));

        refreshPools(adminClient, ownerKey);

        List<PoolDTO> actualPools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(actualPools)
            .singleElement()
            .returns(pool.getId(), PoolDTO::getId)
            .returns(multiplier * quantity, PoolDTO::getQuantity);
    }

    private AsyncJobStatusDTO refreshPools(ApiClient client, String ownerKey) {
        AsyncJobStatusDTO job = adminClient.owners().refreshPools(ownerKey, true);
        assertNotNull(job);
        job = adminClient.jobs().waitForJob(job);
        assertEquals("FINISHED", job.getState());

        return job;
    }
}
