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
package org.candlepin.spec.pools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;



@SpecTest
class PoolSpecTest {

    private OwnerDTO createOwner(ApiClient client) throws ApiException {
        return client.owners().createOwner(Owners.random());
    }

    private ConsumerDTO createConsumer(ApiClient client, OwnerDTO owner, String name,
        Map<String, String> facts) throws ApiException {

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
            .register(consumer);
    }

    private ProductDTO createProduct(ApiClient client, OwnerDTO owner) throws ApiException {
        ProductDTO product = Products.randomEng();

        return client.ownerProducts()
            .createProductByOwner(owner.getKey(), product);
    }

    // ugh...
    private AttributeDTO buildAttribute(String key, String value) {
        return new AttributeDTO()
            .name(key)
            .value(value);
    }

    private PoolDTO createPool(ApiClient client, OwnerDTO owner) throws ApiException {
        ProductDTO product = this.createProduct(client, owner);
        return this.createPool(client, owner, product);
    }

    private PoolDTO createPool(ApiClient client, OwnerDTO owner, ProductDTO product) throws ApiException {
        PoolDTO pool = Pools.randomUpstream(product);

        return client.owners()
            .createPool(owner.getKey(), pool);
    }


    private ApiClient createUserClient(ApiClient client, OwnerDTO owner) throws ApiException {
        UserDTO user = UserUtil.createUser(client, owner);
        return ApiClients.trustedUser(user.getUsername(), true);
    }

    private ApiClient createConsumerClient(ApiClient client, OwnerDTO owner) throws ApiException {
        String consumerName = StringUtil.random("test_consumer-");
        ConsumerDTO consumer = this.createConsumer(client, owner, consumerName, null);

        return this.createConsumerClient(client, consumer);
    }

    private ApiClient createConsumerClient(ApiClient client, ConsumerDTO consumer) throws ApiException {
        return ApiClients.ssl(consumer.getIdCert());
    }


    @Test
    @DisplayName("should let consumers view their own pools")
    public void shouldLetConsumersViewTheirOwnPools() throws ApiException {
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
    @DisplayName("should not let consumers view pool entitlements")
    public void shouldNotLetConsumersViewPoolEntitlements() throws ApiException {
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
    @DisplayName("should not let org admins view pools in other orgs")
    public void shouldNotLetOrgAdminsViewPoolsInOtherOrgs() throws ApiException {
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
    @DisplayName("should not let consumers view pools in other orgs")
    public void shouldNotLetConsumersViewPoolsFromOtherOrgs() throws ApiException {
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
    @DisplayName("should not return expired pools")
    public void shouldNotReturnExpiredPools() throws ApiException {
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
    @DisplayName("should not list pools with errors for consumer clients when listAll is used")
    public void shouldNotListPoolsWithErrorsForConsumersWhenListAllIsUsed() throws ApiException {
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
    @DisplayName("should list pools with warnings for consumer clients when listAll is used")
    public void shouldListPoolsWithWarningsForConsumersWhenListAllIsUsed() throws ApiException {
        ApiClient adminClient = ApiClients.admin();

        OwnerDTO owner = this.createOwner(adminClient);
        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        ProductDTO product = Products.randomEng()
            .addAttributesItem(this.buildAttribute("arch", "x86"));

        product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), product);

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
    @DisplayName("should allow super admins to delete pools")
    public void shouldAllowSuperAdminsToDeletePools() throws ApiException {
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
    @DisplayName("should not allow org admins to delete pools")
    public void shouldNotAllowOrgAdminsToDeletePools() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient ownerClient = this.createUserClient(adminClient, owner);

        assertForbidden(() -> ownerClient.pools().deletePool(pool.getId()));
    }

    @Test
    @DisplayName("should not allow consumers to delete pools")
    public void shouldNotAllowConsumersToDeletePools() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);
        PoolDTO pool = this.createPool(adminClient, owner);

        ApiClient ownerClient = this.createUserClient(adminClient, owner);
        ApiClient consumerClient = this.createConsumerClient(ownerClient, owner);

        assertForbidden(() -> consumerClient.pools().deletePool(pool.getId()));
    }

    @Test
    @DisplayName("should delete children pools when parent pool is deleted")
    public void shouldDeleteChildrenPoolsWhenParentPoolIsDeleted() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = this.createOwner(adminClient);

        ProductDTO product = Products.randomEng()
            .addAttributesItem(this.buildAttribute("virt_limit", "10"));

        product = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), product);

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
    @DisplayName("should include calculated attributes when fetching pools")
    public void shouldIncludeCalculatedAttributesWhenFetchingPools() throws ApiException {
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

    // @Test
    // public void shouldAllowFetchingCDNByPoolId() throws ApiException {
    //     // Impl note:
    //     // This test is currently blocked on a limitation with CDNs in that we can perform CRUD
    //     // operations on them (as they are first-class objects for some reason), but the only way
    //     // to assign a CDN to a pool is via manifest import or pool refresh -- neither of which are
    //     // implementated at the time of writing.

    //     ApiClient adminClient = ApiClients.admin();
    //     OwnerDTO owner = this.createOwner(adminClient);

    //     CdnDTO cdn = new CdnDTO()
    //         .name(StringUtil.randomSuffix("test_cdn"))
    //         .label("Test CDN")
    //         .url("https://cdn.test.com");

    //     cdn = adminClient.cdns().createCdn(cdn);

    //     ProductDTO product = Products.randomSKU()
    //         .addAttributesItem(this.buildAttribute("virt_limit", "10"));

    //     product = adminClient.ownerProducts()
    //         .createProductByOwner(owner.getKey(), product);

    //     PoolDTO pool = Pools.randomUpstream(product)
    //         .setCdn(cdn);

    //     pool = adminClient.owners()
    //         .createPool(owner.getKey(), pool);


    //     // We should have two pools created, but only the base/primary pool should have the CDN
    //     // associated with it
    //     List<PoolDTO> pools = adminClient.pools()
    //         .listPools(owner.getId(), null, null, true, null);

    //     assertNotNull(pools);
    //     assertEquals(2, pools.size());

    //     Optional<PoolDTO> bpfilter = pools.stream()
    //         .filter(p -> "NORMAL".equals(p.getType()))
    //         .findFirst();

    //     Optional<PoolDTO> dpfilter = pools.stream()
    //         .filter(p -> !"NORMAL".equals(p.getType()))
    //         .findFirst();

    //     assertTrue(bpfilter.isPresent());
    //     assertTrue(dpfilter.isPresent());

    //     PoolDTO basePool = bpfilter.get();
    //     PoolDTO derivedPool = dpfilter.get();

    //     CdnDTO fetched1 = adminClient.pools().getPoolCdn(basePool.getId());
    //     assertNotNull(fetched1);
    //     assertEquals(cdn.getName(), fetched1.getName());

    //     CdnDTO fetched2 = adminClient.pools().getPoolCdn(derivedPool.getId());
    //     assertNull(fetched2);
    // }

    @Test
    @DisplayName("should include product branding when fetching pools")
    public void shouldIncludeProductBrandingWhenFetchingPool() throws ApiException {
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
            .createProductByOwner(owner.getKey(), product);

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

}
