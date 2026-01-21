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
package org.candlepin.spec.pools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.PoolsClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.Cdns;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
class PoolResourceSpecTest {

    private OwnerDTO createOwner(ApiClient client) {
        return client.owners().createOwner(Owners.random());
    }

    private ConsumerDTO createConsumer(ApiClient client, OwnerDTO owner, String name,
        Map<String, String> facts) {

        ConsumerTypeDTO ctype = new ConsumerTypeDTO()
            .label("system");

        ConsumerDTO consumer = new ConsumerDTO()
            .name(name)
            .owner(Owners.toNested(owner))
            .type(ctype);

        if (facts != null) {
            // Copy the map into a new hashmap to ensure we can still modify it later if we so choose
            consumer.facts(new HashMap<>(facts));
        }

        consumer.putFactsItem("system.certificate_version", "3.3");

        return client.consumers()
            .createConsumer(consumer);
    }

    private ProductDTO createProduct(ApiClient client, OwnerDTO owner) {
        ProductDTO product = Products.randomEng();

        return client.ownerProducts()
            .createProduct(owner.getKey(), product);
    }

    // ugh...
    private AttributeDTO buildAttribute(String key, String value) {
        return new AttributeDTO()
            .name(key)
            .value(value);
    }

    private PoolDTO createPool(ApiClient client, OwnerDTO owner) {
        ProductDTO product = this.createProduct(client, owner);
        return this.createPool(client, owner, product);
    }

    private PoolDTO createPool(ApiClient client, OwnerDTO owner, ProductDTO product) {
        PoolDTO pool = Pools.randomUpstream(product);

        return client.owners()
            .createPool(owner.getKey(), pool);
    }


    private ApiClient createUserClient(ApiClient client, OwnerDTO owner) {
        UserDTO user = UserUtil.createUser(client, owner);
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private ApiClient createConsumerClient(ApiClient client, OwnerDTO owner) {
        String consumerName = StringUtil.random("test_consumer-");
        ConsumerDTO consumer = this.createConsumer(client, owner, consumerName, null);

        return this.createConsumerClient(client, consumer);
    }

    private ApiClient createConsumerClient(ApiClient client, ConsumerDTO consumer) {
        return ApiClients.ssl(consumer.getIdCert());
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenCreatingPools() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        PoolDTO output = this.createPool(adminClient, owner);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldLetConsumersViewTheirOwnPools() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient userClient = this.createUserClient(adminClient, owner);

        List<PoolDTO> pools = userClient.pools().listPoolsByOwner(owner.getId());

        assertNotNull(pools);
        assertEquals(1, pools.size());

        ApiClient consumerClient = this.createConsumerClient(userClient, owner);

        PoolDTO fetched = consumerClient.pools()
            .getPool(pool.getId(), null, null);

        assertNotNull(fetched);
        assertEquals(pool.getId(), fetched.getId());
    }

    @Test
    public void shouldNotLetConsumersViewPoolEntitlements() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient userClient = this.createUserClient(adminClient, owner);
        ApiClient consumerClient = this.createConsumerClient(userClient, owner);

        List<EntitlementDTO> entitlements = userClient.pools()
            .getPoolEntitlements(pool.getId());

        assertNotNull(entitlements);

        assertForbidden(() -> consumerClient.pools().getPoolEntitlements(pool.getId()));
    }

    @Test
    public void shouldNotLetOrgAdminsViewPoolsInOtherOrgs() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner1 = this.createOwner(adminClient);
        OwnerDTO owner2 = this.createOwner(adminClient);
        PoolDTO owner1Pool = this.createPool(adminClient, owner1);
        PoolDTO owner2Pool = this.createPool(adminClient, owner2);
        ApiClient owner1Client = this.createUserClient(adminClient, owner1);
        ApiClient owner2Client = this.createUserClient(adminClient, owner2);

        // Consumers should be able to view their own pools
        PoolDTO fetched1 = owner1Client.pools().getPool(owner1Pool.getId(), null, null);
        assertNotNull(fetched1);
        assertEquals(owner1Pool.getId(), fetched1.getId());

        PoolDTO fetched2 = owner2Client.pools().getPool(owner2Pool.getId(), null, null);
        assertNotNull(fetched2);
        assertEquals(owner2Pool.getId(), fetched2.getId());

        // Consumers should not be able to view the other org's pools
        assertNotFound(() -> owner1Client.pools().getPool(owner2Pool.getId(), null, null));
        assertNotFound(() -> owner2Client.pools().getPool(owner1Pool.getId(), null, null));
    }

    @Test
    public void shouldNotLetConsumersViewPoolsFromOtherOrgs() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner1 = this.createOwner(adminClient);
        OwnerDTO owner2 = this.createOwner(adminClient);
        PoolDTO owner1Pool = this.createPool(adminClient, owner1);
        PoolDTO owner2Pool = this.createPool(adminClient, owner2);
        ApiClient owner1Client = this.createUserClient(adminClient, owner1);
        ApiClient owner2Client = this.createUserClient(adminClient, owner2);
        ApiClient consumerClient1 = this.createConsumerClient(owner1Client, owner1);
        ApiClient consumerClient2 = this.createConsumerClient(owner2Client, owner2);

        // Consumers should be able to view their own pools
        PoolDTO fetched1 = consumerClient1.pools().getPool(owner1Pool.getId(), null, null);
        assertNotNull(fetched1);
        assertEquals(owner1Pool.getId(), fetched1.getId());

        PoolDTO fetched2 = consumerClient2.pools().getPool(owner2Pool.getId(), null, null);
        assertNotNull(fetched2);
        assertEquals(owner2Pool.getId(), fetched2.getId());

        // Consumers should not be able to view the other org's pools
        assertNotFound(() -> consumerClient1.pools().getPool(owner2Pool.getId(), null, null));
        assertNotFound(() -> consumerClient2.pools().getPool(owner1Pool.getId(), null, null));
    }

    @Test
    public void shouldNotReturnExpiredPools() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = this.createOwner(adminClient);
        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        ProductDTO product = this.createProduct(adminClient, owner);

        OffsetDateTime endDate = OffsetDateTime.now().minusDays(30);
        OffsetDateTime startDate = endDate.minusYears(1);

        PoolDTO pool = Pools.randomUpstream(product)
            .startDate(startDate)
            .endDate(endDate);

        pool = adminClient.owners()
            .createPool(owner.getKey(), pool);

        List<PoolDTO> pools = ownerClient.pools().listPoolsByOwner(owner.getId());

        assertNotNull(pools);
        assertEquals(0, pools.size());
    }

    @Test
    public void shouldNotListPoolsWithErrorsForConsumersWhenListAllIsUsed() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = this.createOwner(adminClient);
        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        ProductDTO product = this.createProduct(adminClient, owner);

        PoolDTO pool = Pools.randomUpstream(product)
            .quantity(1L);

        pool = adminClient.owners()
            .createPool(owner.getKey(), pool);

        ConsumerDTO consumer1 = this.createConsumer(ownerClient, owner,
            StringUtil.random("test_consumer_1-"), null);
        ConsumerDTO consumer2 = this.createConsumer(ownerClient, owner,
            StringUtil.random("test_consumer_2-"), null);
        ApiClient consumerClient1 = this.createConsumerClient(ownerClient, consumer1);
        ApiClient consumerClient2 = this.createConsumerClient(ownerClient, consumer2);

        // Consume the pool
        String output = consumerClient1.consumers()
            .bind(consumer1.getUuid(), pool.getId(), null, 1, null, null, false, null, null);

        assertNotNull(output);

        // Neither consumer should see a fully-consumed pool
        List<PoolDTO> pools1 = consumerClient1.pools().listPoolsByConsumer(consumer1.getUuid());

        assertNotNull(pools1);
        assertEquals(0, pools1.size());

        // Consumer 2 shouldn't see a fully-consumed pool
        List<PoolDTO> pools2 = consumerClient2.pools().listPoolsByConsumer(consumer2.getUuid());

        assertNotNull(pools2);
        assertEquals(0, pools2.size());
    }

    @Test
    public void shouldListPoolsWithWarningsForConsumersWhenListAllIsUsed() {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = this.createOwner(adminClient);
        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        ProductDTO product = Products.randomEng()
            .addAttributesItem(this.buildAttribute("arch", "x86"));

        product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), product);

        PoolDTO pool = this.createPool(adminClient, owner, product);

        // Create a consumer with a different arch than the product
        ConsumerDTO consumer = this.createConsumer(ownerClient, owner,
            StringUtil.random("test_consumer-"), Map.of("uname.machine", "x86_64"));

        ApiClient consumerClient = this.createConsumerClient(ownerClient, consumer);

        // Consumer should see the pools regardless of warnings due to use of the "listall" flag
        List<PoolDTO> pools = consumerClient.pools().listPoolsByOwner(owner.getId());

        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertEquals(pool.getId(), pools.get(0).getId());
    }

    @Test
    public void shouldAllowSuperAdminsToDeletePools() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        ConsumerDTO consumer = this.createConsumer(ownerClient, owner,
            StringUtil.random("test_consumer-"), null);

        ApiClient consumerClient = this.createConsumerClient(ownerClient, consumer);

        String output = consumerClient.consumers()
            .bind(consumer.getUuid(), pool.getId(), null, 1, null, null, false, null, null);

        assertNotNull(output);

        // Could translate the output here, but it's less work to just query for it again
        List<EntitlementDTO> entitlements1 = consumerClient.consumers()
            .listEntitlements(consumer.getUuid());

        assertNotNull(entitlements1);
        assertEquals(1, entitlements1.size());

        // Verify the pool exists and can be queried
        PoolDTO fetched = adminClient.pools().getPool(pool.getId(), null, null);
        assertNotNull(fetched);
        assertEquals(pool.getId(), fetched.getId());

        // Delete the pool
        adminClient.pools().deletePool(pool.getId());

        // The pool should no longer be present
        assertNotFound(() -> adminClient.pools().getPool(pool.getId(), null, null));

        // The consumer's entitlement should be gone
        assertNotFound(() -> adminClient.entitlements().getEntitlement(entitlements1.get(0).getId()));

        List<EntitlementDTO> entitlements2 = consumerClient.consumers().listEntitlements(consumer.getUuid());

        assertNotNull(entitlements2);
        assertEquals(0, entitlements2.size());
    }

    @Test
    public void shouldNotAllowOrgAdminsToDeletePools() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        assertForbidden(() -> ownerClient.pools().deletePool(pool.getId()));
    }

    @Test
    public void shouldNotAllowConsumersToDeletePools() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient ownerClient = this.createUserClient(adminClient, owner);
        ApiClient consumerClient = this.createConsumerClient(ownerClient, owner);

        assertForbidden(() -> consumerClient.pools().deletePool(pool.getId()));
    }

    @Test
    public void shouldDeleteChildrenPoolsWhenParentPoolIsDeleted() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ProductDTO product = Products.randomEng()
            .addAttributesItem(this.buildAttribute("virt_limit", "10"));

        product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), product);

        PoolDTO pool = this.createPool(adminClient, owner, product);

        // Fetch the org's pools, verify the target pool and its unmapped guest pool exist
        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());

        assertNotNull(pools);
        assertEquals(2, pools.size());

        Optional<PoolDTO> nfilter = pools.stream()
            .filter(p -> "NORMAL".equals(p.getType()))
            .findFirst();

        assertTrue(nfilter.isPresent());
        PoolDTO npool = nfilter.get();

        Optional<PoolDTO> dfilter = pools.stream()
            .filter(p -> !"NORMAL".equalsIgnoreCase(p.getType()))
            .findFirst();

        assertTrue(dfilter.isPresent());
        PoolDTO dpool = dfilter.get();

        // Delete the pool
        adminClient.pools().deletePool(pool.getId());

        // Verify the both pools were deleted
        pools = adminClient.pools().listPoolsByOwner(owner.getId());

        assertNotNull(pools);
        assertEquals(0, pools.size());

        assertNotFound(() -> adminClient.pools().getPool(npool.getId(), null, null));
        assertNotFound(() -> adminClient.pools().getPool(dpool.getId(), null, null));
    }

    @Test
    public void shouldIncludeCalculatedAttributesWhenFetchingPools() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        String consumerName = StringUtil.random("test_consumer-");
        ConsumerDTO consumer = this.createConsumer(ownerClient, owner, consumerName, null);
        ApiClient consumerClient = this.createConsumerClient(ownerClient, consumer);

        PoolDTO fetched = consumerClient.pools().getPool(pool.getId(), consumer.getUuid(), null);
        assertNotNull(fetched);

        Map<String, String> attributes = fetched.getCalculatedAttributes();
        assertNotNull(attributes);

        assertThat(attributes)
            .isNotNull()
            .hasSizeGreaterThanOrEqualTo(2)
            .containsEntry("suggested_quantity", "1")
            .containsEntry("compliance_type", "Standard");
    }

    @Test
    @OnlyInHosted
    public void shouldAllowFetchingCDNByPoolId() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        HostedTestApi upstreamClient = adminClient.hosted();

        CdnDTO cdn = Cdns.random();
        cdn = adminClient.cdns().createCdn(cdn);

        ProductDTO product = Products.randomSKU()
            .addAttributesItem(this.buildAttribute("virt_limit", "10"));

        product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), product);

        upstreamClient.createProduct(product);
        upstreamClient.createSubscription(Subscriptions.random(owner, product).cdn(cdn));

        AsyncJobStatusDTO job =  adminClient.owners().refreshPools(owner.getKey(), false);
        job = adminClient.jobs().waitForJob(job.getId());
        assertThat(job)
            .hasFieldOrPropertyWithValue("state", "FINISHED");

        // We should have two pools created, but only the base/primary pool should have the CDN
        // associated with it
        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());

        assertNotNull(pools);
        assertEquals(2, pools.size());

        Optional<PoolDTO> bpfilter = pools.stream()
            .filter(p -> "NORMAL".equals(p.getType()))
            .findFirst();

        Optional<PoolDTO> dpfilter = pools.stream()
            .filter(p -> !"NORMAL".equals(p.getType()))
            .findFirst();

        assertTrue(bpfilter.isPresent());
        assertTrue(dpfilter.isPresent());

        PoolDTO basePool = bpfilter.get();
        PoolDTO derivedPool = dpfilter.get();

        CdnDTO fetched1 = adminClient.pools().getPoolCdn(basePool.getId());
        assertNotNull(fetched1);
        assertEquals(cdn.getName(), fetched1.getName());

        CdnDTO fetched2 = adminClient.pools().getPoolCdn(derivedPool.getId());
        assertNull(fetched2);
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class ListPoolsPagingTests {
        private ApiClient adminClient = ApiClients.admin();

        private OwnerDTO owner;
        private String ownerId;
        private String ownerKey;

        private int numberOfPools = 20;
        private List<PoolDTO> pools = new ArrayList<>();

        private Map<String, Comparator<PoolDTO>> comparatorMap = Map.of(
            "id", Comparator.comparing(PoolDTO::getId),
            "quantity", Comparator.comparing(PoolDTO::getQuantity));

        @BeforeAll
        public void setup() {
            owner = adminClient.owners().createOwner(Owners.randomSca());
            ownerId = owner.getId();
            ownerKey = owner.getKey();

            Random random = new Random();
            for (int i = 0; i < numberOfPools; i++) {
                ProductDTO product = adminClient.ownerProducts()
                    .createProduct(ownerKey, Products.random());
                PoolDTO pool = Pools.random(product)
                    .quantity(random.nextLong(100L, 10000L));

                pool = adminClient.owners().createPool(ownerKey, pool);
                pools.add(pool);
            }
        }

        @Test
        public void shouldPagePools() {
            int pageSize = 5;
            List<String> actualPoolsIds = new ArrayList<>();
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfPools; pageIndex++) {
                List<String> poolIds = adminClient.pools()
                    .listPools(ownerId, null, null, null, null, pageIndex, pageSize, "asc", "id")
                    .stream()
                    .map(PoolDTO::getId)
                    .collect(Collectors.toList());

                actualPoolsIds.addAll(poolIds);
            }

            List<String> expectedPoolIds = pools.stream()
                .map(PoolDTO::getId)
                .toList();

            assertThat(actualPoolsIds)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @Test
        public void shouldPagePoolsWithOrderByNotSpecified() {
            int pageSize = 5;
            List<String> actualPoolsIds = new ArrayList<>();
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfPools; pageIndex++) {
                List<String> poolIds = adminClient.pools()
                    .listPools(ownerId, null, null, null, null, pageIndex, pageSize, "asc", null)
                    .stream()
                    .map(PoolDTO::getId)
                    .collect(Collectors.toList());

                actualPoolsIds.addAll(poolIds);
            }

            List<String> expectedPoolIds = pools.stream()
                .map(PoolDTO::getId)
                .toList();

            assertThat(actualPoolsIds)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @Test
        public void shouldPagePoolsInDescendingOrderWithOrderNotSpecified() {
            int pageSize = 5;
            List<String> actualPoolsIds = new ArrayList<>();
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfPools; pageIndex++) {
                List<String> poolIds = adminClient.pools()
                    .listPools(ownerId, null, null, null, null, pageIndex, pageSize, null, "id")
                    .stream()
                    .map(PoolDTO::getId)
                    .collect(Collectors.toList());

                actualPoolsIds.addAll(poolIds);
            }

            List<String> expectedPoolIds = pools.stream()
                .sorted(comparatorMap.get("id").reversed())
                .map(PoolDTO::getId)
                .toList();

            assertThat(actualPoolsIds)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) {
            PoolsClient pools = adminClient.pools();

            assertBadRequest(() -> pools
                .listPools(ownerId, null, null, null, null, page, 5, "asc", "id"));
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) {
            PoolsClient pools = adminClient.pools();

            assertBadRequest(() -> pools
                .listPools(ownerId, null, null, null, null, 1, pageSize, "asc", "id"));
        }

        @Test
        public void shouldFailWithInvalidOrderDirection() {
            PoolsClient pools = adminClient.pools();

            assertBadRequest(() -> pools
                .listPools(ownerId, null, null, null, null, 1, numberOfPools, StringUtil.random(""), "id"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "quantity" })
        public void shouldOrderInAscendingOrder(String field) {
            PoolsClient poolsClient = adminClient.pools();
            List<String> expectedPoolIds = pools.stream()
                .sorted(comparatorMap.get(field))
                .map(PoolDTO::getId)
                .toList();

            List<PoolDTO> actual = poolsClient
                .listPools(ownerId, null, null, null, null, 1, numberOfPools, "asc", field);

            assertThat(actual)
                .isNotNull()
                .map(PoolDTO::getId)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "quantity" })
        public void shouldOrderInDescendingOrder(String field) {
            PoolsClient poolsClient = adminClient.pools();
            List<String> expectedPoolIds = pools.stream()
                .sorted(comparatorMap.get(field).reversed())
                .map(PoolDTO::getId)
                .toList();

            List<PoolDTO> actual = poolsClient
                .listPools(ownerId, null, null, null, null, 1, numberOfPools, "desc", field);

            assertThat(actual)
                .isNotNull()
                .map(PoolDTO::getId)
                .containsExactlyElementsOf(expectedPoolIds);
        }
    }

    @Test
    public void shouldIncludeProductBrandingWhenFetchingPool() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        // Impl note: it is not strictly necessary to have the product ID match anything for the
        // purposes of this test
        BrandingDTO brand1 = Branding.build("branding-1", "brand_type-1")
            .productId("test_product-1");

        BrandingDTO brand2 = Branding.build("branding-2", "brand_type-2")
            .productId("test_product-2");

        ProductDTO product = Products.randomEng()
            .addBrandingItem(brand1)
            .addBrandingItem(brand2);

        product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), product);

        PoolDTO pool = this.createPool(adminClient, owner, product);

        PoolDTO fetched = adminClient.pools().getPool(pool.getId(), null, null);
        assertNotNull(fetched);

        Set<BrandingDTO> branding = fetched.getBranding();
        assertNotNull(branding);
        assertEquals(2, branding.size());

        Optional<BrandingDTO> b1filter = branding.stream()
            .filter(brand -> brand1.getName().equals(brand.getName()))
            .findFirst();

        Optional<BrandingDTO> b2filter = branding.stream()
            .filter(brand -> brand2.getName().equals(brand.getName()))
            .findFirst();

        assertTrue(b1filter.isPresent());
        assertTrue(b2filter.isPresent());
    }

    @Test
    public void shouldIncludesNullValuesInJson() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ProductDTO product = createProduct(adminClient, owner);
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        JsonNode nodes = Request.from(adminClient)
            .setPath("/pools")
            .addQueryParam("owner", owner.getId())
            .execute()
            .deserialize();

        assertThatObject(nodes)
            .isNotNull()
            .returns(true, JsonNode::isArray)
            .returns(false, JsonNode::isEmpty);

        List<String> fields = List.of("upstreamPoolId", "upstreamEntitlementId", "upstreamConsumerId");

        for (JsonNode jsonNode : nodes) {
            assertThatObject(jsonNode)
                .isNotNull()
                .returns(true, JsonNode::isObject);

            for (String field : fields) {
                assertTrue(jsonNode.has(field));
                assertThatObject(jsonNode.get(field))
                    .isNotNull()
                    .returns(true, JsonNode::isNull);
            }
        }
    }
}
