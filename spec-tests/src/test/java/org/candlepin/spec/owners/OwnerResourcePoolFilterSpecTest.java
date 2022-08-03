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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SpecTest
public class OwnerResourcePoolFilterSpecTest {

    @Test
    public void shouldListPoolsWithMatchesAgainstProvidedProducts() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        String providedName = "test_name";
        Set<ProductDTO> providedProducts = new HashSet<>();
        providedProducts.add(createProductByOwner(adminClient, owner, Products.random().name(providedName)));
        providedProducts.add(createProductByOwner(adminClient, owner, Products.random()));
        providedProducts.add(createProductByOwner(adminClient, owner, Products.random()));
        ProductDTO product = createProductByOwner(
            adminClient, owner, Products.random().providedProducts(providedProducts));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey(), consumer.getUuid(), null,
            null, null, null, null, List.of(providedName), null,
            null, null, null, null, null, null, null, null);
        assertThat(pools)
            .isNotNull()
            .hasSize(1);

        PoolDTO pool = pools.get(0);
        assertThat(pool)
            .isNotNull()
            .returns(product.getId(), PoolDTO::getProductId)
            .extracting(PoolDTO::getProvidedProducts, as(collection(ProvidedProductDTO.class)))
            .isNotNull()
            .hasSize(3);

        assertThat(pool.getOwner())
            .isNotNull()
            .returns(owner.getKey(), NestedOwnerDTO::getKey);
    }

    @Test
    public void shouldAllowUserToListStandardPoolBySubscriptionId() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);

        // entitle owner for the virtual and monitoring products
        ProductDTO product = createProductByOwner(adminClient, owner, Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        List<PoolDTO> pools = userClient.owners().listOwnerPools(owner.getKey(), null, null,
            null, pool.getSubscriptionId(), null, null, null, null,
            null, null, null, null, null, null, null, null);
        assertEquals(1, pools.size());
    }

    @Test
    public void shouldAllowUserToListBonusPoolAlsoBySubscriptionId() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ProductDTO product = createProduct(adminClient, owner,
            ProductAttributes.VirtLimit.withValue("unlimited"));

        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.randomUpstream(product));

        List<PoolDTO> pools = userClient.owners().listOwnerPools(owner.getKey(), null, null,
            null, pool.getSubscriptionId(), null, null, null, null,
            null, null, null, null, null, null, null, null);
        assertEquals(2, pools.size());
    }

    @Test
    public void shouldAllowOwnersFilterPoolsByActivationKey() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = adminClient.owners().createActivationKey(
            owner.getKey(), ActivationKeys.random(owner));

        ProductDTO product1 = createProduct(adminClient, owner,
            ProductAttributes.SupportLevel.withValue("VIP")
        );
        ProductDTO product2 = createProduct(adminClient, owner,
            ProductAttributes.SupportLevel.withValue("Supurb"),
            ProductAttributes.Cores.withValue("4")
        );
        ProductDTO product3 = createProductByOwner(adminClient, owner, Products.random());

        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product3));

        adminClient.activationKeys().addPoolToKey(activationKey.getId(), pool1.getId(), null);
        adminClient.activationKeys().addPoolToKey(activationKey.getId(), pool2.getId(), null);

        // ignores the pools already with the activation key
        List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey(), null,
            activationKey.getName(), null, null, null, null, null, null, null, null,
            null, null, null, null, null, null);
        assertThat(pools)
            .isNotNull()
            .hasSize(1);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    public class PoolAndSubscriptionFiltering {

        ApiClient adminClient;
        private OwnerDTO owner;
        private ProductDTO product1;
        private ProductDTO product2;

        @BeforeAll
        public void setUp() throws ApiException {
            adminClient = ApiClients.admin();
            owner = createOwner(adminClient);

            product1 = createProduct(adminClient, owner,
                ProductAttributes.SupportLevel.withValue("VIP")
            );
            product2 = createProduct(adminClient, owner,
                ProductAttributes.SupportLevel.withValue("Supurb"),
                ProductAttributes.Cores.withValue("4")
            );
            ProductDTO product3 = createProductByOwner(adminClient, owner, Products.random());

            createPools(adminClient, owner, Pools.random(product1),
                Pools.random(product2), Pools.random(product3));
        }

        @Test
        public void shouldAllowOwnersFilterPoolsBySingleFilter() throws ApiException {
            List<PoolDTO> filteredPools = adminClient.owners().listOwnerPoolsWithAttributes(
                owner.getKey(), List.of("support_level:VIP"));
            assertThat(filteredPools)
                .isNotNull()
                .singleElement()
                .isNotNull()
                .returns(product1.getId(), PoolDTO::getProductId);

            //Now with wildcards
            filteredPools = adminClient.owners().listOwnerPoolsWithAttributes(
                owner.getKey(), List.of("support_level:V*P"));
            assertThat(filteredPools)
                .isNotNull()
                .singleElement()
                .isNotNull()
                .returns(product1.getId(), PoolDTO::getProductId);

            filteredPools = adminClient.owners().listOwnerPoolsWithAttributes(
                owner.getKey(), List.of("support_level:V?P"));
            assertThat(filteredPools)
                .isNotNull()
                .singleElement()
                .isNotNull()
                .returns(product1.getId(), PoolDTO::getProductId);
        }

        @Test
        public void shouldAllowOwnersFilterPoolsByMultipleFilter() throws ApiException {
            List<PoolDTO> filteredPools = adminClient.owners().listOwnerPoolsWithAttributes(
                owner.getKey(), List.of("support_level:Supurb", "cores:4"));
            assertThat(filteredPools)
                .isNotNull()
                .singleElement()
                .isNotNull()
                .returns(product2.getId(), PoolDTO::getProductId);
        }

        @Test
        public void shouldNotAllowOwnersFilterPoolsWithBadFormatAttribute() throws ApiException {
            OwnerDTO owner = createOwner(adminClient);

            // Try to send attribute with bad splitter(should be colon)
            assertBadRequest(() -> {
                adminClient.owners().listOwnerPoolsWithAttributes(
                    owner.getKey(), List.of("support_level;Supurb"));
            });
        }

        @Test
        public void shouldAllowOwnersFilterPoolsByPoolId() throws ApiException {
            List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey());
            for (PoolDTO pool : pools) {
                List<PoolDTO> filteredPools = adminClient.owners().listOwnerPoolsWithPoolIds(
                    owner.getKey(), List.of(pool.getId()));
                assertThat(filteredPools)
                    .map(PoolDTO::getId)
                    .containsOnly(pool.getId());
            }
        }

        @Test
        public void shouldAllowOwnersFilterPoolsByMultiplePoolId() throws ApiException {
            List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey());
            assertThat(pools)
                .isNotNull()
                .size()
                .isGreaterThan(1);

            List<PoolDTO> filteredPools = adminClient.owners().listOwnerPoolsWithPoolIds(
                owner.getKey(), List.of(pools.get(0).getId(), pools.get(1).getId()));
            assertThat(filteredPools)
                .map(PoolDTO::getId)
                .containsOnly(pools.get(0).getId(), pools.get(1).getId());
        }

    }
    private static ProductDTO createProductByOwner(ApiClient client, OwnerDTO owner, ProductDTO product)
        throws ApiException {
        return client.ownerProducts().createProductByOwner(owner.getKey(), product);
    }

    private static ApiClient createUserClient(ApiClient adminClient, OwnerDTO owner) throws ApiException {
        UserDTO user = UserUtil.createUser(adminClient, owner);
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private static OwnerDTO createOwner(ApiClient adminClient) throws ApiException {
        return adminClient.owners().createOwner(Owners.random());
    }

    private ProductDTO createProduct(ApiClient client, OwnerDTO owner, AttributeDTO... attributes)
        throws ApiException {
        return createProductByOwner(client, owner, Products.withAttributes(attributes));
    }

    private void createPools(ApiClient client, OwnerDTO owner, PoolDTO... pools) throws ApiException {
        for (PoolDTO pool : pools) {
            client.owners().createPool(owner.getKey(), pool);
        }
    }

}
