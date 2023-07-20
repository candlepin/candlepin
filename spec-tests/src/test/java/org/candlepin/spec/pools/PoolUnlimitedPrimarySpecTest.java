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
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;



@SpecTest
public class PoolUnlimitedPrimarySpecTest {

    private static ApiClient client;
    private OwnerDTO owner;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static ConsumerClient consumerApi;
    private ConsumerDTO physicalSystem;
    private ApiClient physicalClient;
    private ConsumerDTO guest;
    private ApiClient guestClient;
    private ConsumerDTO guestUnmapped;
    private ApiClient guestUnmappedClient;
    private ProductDTO productNoVirt;
    private ProductDTO productUnlimitedVirt;
    private ProductDTO productVirt;
    private ProductDTO proudctVirtHostDep;
    private ProductDTO productVirtInstanceMuliplier;
    private ProductDTO productVirtProductMuliplier;
    private PoolDTO poolNoVirt;
    private PoolDTO poolUnlimitedVirt;
    private PoolDTO poolVirtHostDep;
    private PoolDTO poolVirtInstanceMuliplier;
    private PoolDTO poolVirtProductMuliplier;

    @BeforeAll
    public static void beforeAll() throws ApiException {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        consumerApi = client.consumers();
    }

    @Test
    public void shouldHaveCreatedCorrectPools() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(true, true, true, true, true, true);
        assertThat(client.pools().listPoolsByOwnerAndProduct(owner.getId(), productUnlimitedVirt.getId()))
            .hasSize(2);
        assertThat(client.pools().listPoolsByOwnerAndProduct(owner.getId(), productNoVirt.getId()))
            .hasSize(1);
        assertThat(client.pools().listPoolsByOwnerAndProduct(owner.getId(), productVirt.getId()))
            .hasSize(2);
        assertThat(client.pools().listPoolsByOwnerAndProduct(owner.getId(), proudctVirtHostDep.getId()))
            .hasSize(2);
        assertThat(client.pools().listPoolsByOwnerAndProduct(owner.getId(),
            productVirtProductMuliplier.getId()))
            .hasSize(2);
        assertThat(client.pools().listPoolsByOwnerAndProduct(owner.getId(),
            productVirtInstanceMuliplier.getId()))
            .hasSize(2);
    }

    @Test
    public void shouldAllowSystemToConsumerUnlimitedQuantityPool() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(true, false, false, false, false, false);
        physicalClient.consumers().bindPool(physicalSystem.getUuid(), poolNoVirt.getId(), 300);
        List<EntitlementDTO> ents = physicalClient.consumers().listEntitlements(physicalSystem.getUuid());
        assertThat(ents).hasSize(1);
        assertEquals(300, ents.get(0).getQuantity());
    }

    @Test
    public void shouldNotShowEffectFromProductMultiplierToUnlimitedPrimaryPoolQuantity() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(false, false, false, false, false, true);
        List<PoolDTO> pools = ownerApi.listOwnerPools(owner.getKey());
        // primary pool quantity expected to be -1
        PoolDTO primaryPool = pools.stream()
            .filter(x -> x.getId().equals(poolVirtProductMuliplier.getId()))
            .toList().get(0);
        assertEquals(-1L, primaryPool.getQuantity());

        // consume primary pool with physical client in any quantity
        physicalClient.consumers().bindPool(physicalSystem.getUuid(), primaryPool.getId(), 1000);
        pools = ownerApi.listOwnerPools(owner.getKey());
        primaryPool = pools.stream()
            .filter(x -> x.getId().equals(poolVirtProductMuliplier.getId()))
            .toList().get(0);
        assertEquals(-1L, primaryPool.getQuantity());

        PoolDTO subPool = pools.stream()
            .filter(x -> x.getSubscriptionId().equals(poolVirtProductMuliplier.getSubscriptionId()) &&
                ("UNMAPPED_GUEST".equals(x.getType()) || "BONUS".equals(x.getType())))
            .toList().get(0);
        assertEquals(-1L, subPool.getQuantity());
    }

    @Test
    public void shouldNotShowEffectFromInstanceMultiplierToUnlimitedPrimaryPoolQuantity() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(false, false, false, false, true, false);
        List<PoolDTO> pools = ownerApi.listOwnerPools(owner.getKey());
        final String poolId = poolVirtInstanceMuliplier.getId();
        // primary pool quantity expected to be -1
        PoolDTO primaryPool = pools.stream()
            .filter(x -> x.getId().equals(poolId))
            .toList().get(0);
        assertEquals(-1L, primaryPool.getQuantity());

        // consume primary pool with physical client in any quantity
        physicalClient.consumers().bindPool(physicalSystem.getUuid(), primaryPool.getId(), 60);
        pools = ownerApi.listOwnerPools(owner.getKey());
        primaryPool = pools.stream()
            .filter(x -> x.getId().equals(poolId))
            .toList().get(0);
        assertEquals(-1L, primaryPool.getQuantity());

        final String subId = poolVirtInstanceMuliplier.getSubscriptionId();
        PoolDTO subPool = pools.stream()
            .filter(x -> x.getSubscriptionId().equals(subId) &&
                ("UNMAPPED_GUEST".equals(x.getType()) || "BONUS".equals(x.getType())))
            .toList().get(0);
        assertEquals(-1L, subPool.getQuantity());
    }

    @Test
    public void shouldAlwaysHaveUnlimitedPrimaryPoolQuantityEqualtoNegOne() {
        setupConsumersAndClients();
        ProductDTO product = Products.randomSKU()
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("4"))
            .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);
        // create pool with -100 quantity
        PoolDTO unlimitedPool = Pools.random(product)
            .quantity(-100L)
            .subscriptionId(StringUtil.random("sub"))
            .subscriptionSubKey(StringUtil.random("key"))
            .upstreamPoolId(StringUtil.random("pool"));
        unlimitedPool = ownerApi.createPool(owner.getKey(), unlimitedPool);
        final String poolId = unlimitedPool.getId();

        List<PoolDTO> pools = ownerApi.listOwnerPools(owner.getKey());
        // primary pool quantity expected to be -1
        PoolDTO primaryPool = pools.stream()
            .filter(x -> x.getId().equals(poolId))
            .toList().get(0);
        assertEquals(-1L, primaryPool.getQuantity());

        physicalClient.consumers().bindPool(physicalSystem.getUuid(), primaryPool.getId(), 1000);
        pools = ownerApi.listOwnerPools(owner.getKey());
        primaryPool = pools.stream()
            .filter(x -> x.getId().equals(poolId))
            .toList().get(0);
        assertEquals(-1L, primaryPool.getQuantity());
    }

    @Test
    @OnlyInStandalone
    public void shouldAllowSystemToConsumeLimitedVirtQuantityPool() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(false, false, true, false, false, false);
        List<PoolDTO> pools = client.pools().listPoolsByOwnerAndProduct(owner.getId(), productVirt.getId());
        PoolDTO guestPool = pools.stream()
            .filter(x -> "true".equals(getAttributeValue("pool_derived", x.getAttributes())) &&
                "UNMAPPED_GUEST".equals(x.getType()))
            .toList().get(0);
        guestUnmappedClient.consumers().bindPool(guestUnmapped.getUuid(), guestPool.getId(), 4000);
        List<EntitlementDTO> ents = guestUnmappedClient.consumers().listEntitlements(guestUnmapped.getUuid());
        assertThat(ents).hasSize(1);
        assertEquals(4000, ents.get(0).getQuantity());
    }

    @Test
    @OnlyInStandalone
    public void shouldAllowMappedGuestToConsumeUnlimitedVirtQuantityPool() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(false, true, false, false, false, false);
        physicalClient.consumers().bindPool(physicalSystem.getUuid(), poolUnlimitedVirt.getId(), 300);
        List<PoolDTO> pools = client.pools().listPoolsByOwnerAndProduct(owner.getId(),
            productUnlimitedVirt.getId());
        PoolDTO guestPool = pools.stream()
            .filter(x -> "true".equals(getAttributeValue("pool_derived", x.getAttributes())) &&
                "ENTITLEMENT_DERIVED".equals(x.getType()))
            .toList().get(0);

        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 5000);
        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(ents).hasSize(1);
        assertEquals(5000, ents.get(0).getQuantity());
    }

    @Test
    @OnlyInStandalone
    public void shouldAllowMappedGuestToConsumeLimitedHostDependentPool() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(false, false, false, true, false, false);
        physicalClient.consumers().bindPool(physicalSystem.getUuid(), poolVirtHostDep.getId(), 300);
        List<PoolDTO> pools = client.pools().listPoolsByOwnerAndProduct(owner.getId(),
            proudctVirtHostDep.getId());
        PoolDTO guestPool = pools.stream()
            .filter(x -> "true".equals(getAttributeValue("pool_derived", x.getAttributes())) &&
                "ENTITLEMENT_DERIVED".equals(x.getType()))
            .toList().get(0);

        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 4);
        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(ents).hasSize(1);
        assertEquals(4, ents.get(0).getQuantity());
    }

    @Test
    @OnlyInStandalone
    public void shouldAllowUnmappedGuestToConsumeUnlimitedQuantityPool() throws Exception {
        setupConsumersAndClients();
        setupProductsAndPools(false, true, false, false, false, false);
        List<PoolDTO> pools = client.pools().listPoolsByOwnerAndProduct(owner.getId(),
            productUnlimitedVirt.getId());
        PoolDTO guestPool = pools.stream()
            .filter(x -> "true".equals(getAttributeValue("pool_derived", x.getAttributes())) &&
                "UNMAPPED_GUEST".equals(x.getType()))
            .toList().get(0);

        guestUnmappedClient.consumers().bindPool(guestUnmapped.getUuid(), guestPool.getId(), 600);
        List<EntitlementDTO> ents = guestUnmappedClient.consumers().listEntitlements(guestUnmapped.getUuid());
        assertThat(ents).hasSize(1);
        assertEquals(600, ents.get(0).getQuantity());
    }

    private PoolDTO createUnlimitedPool(ProductDTO product) throws ApiException {
        PoolDTO unlimitedPool = Pools.random(product)
            .quantity(-1L)
            .subscriptionId(StringUtil.random("sub"))
            .subscriptionSubKey(StringUtil.random("key"))
            .upstreamPoolId(StringUtil.random("pool"));
        return ownerApi.createPool(owner.getKey(), unlimitedPool);
    }

    private String getAttributeValue(String name, List<AttributeDTO> attributes) {
        List<AttributeDTO> matches = attributes.stream()
            .filter(x -> name.equals(x.getName()))
            .toList();
        if (matches.size() > 0) {
            return matches.get(0).getValue();
        }
        return null;
    }

    private void setupConsumersAndClients() {
        owner = ownerApi.createOwner(Owners.random());
        String guestUuid = StringUtil.random("uuid");
        physicalSystem = Consumers.random(owner)
            .name(StringUtil.random("host"))
            .type(ConsumerTypes.System.value())
            .guestIds(List.of(new GuestIdDTO().guestId(guestUuid)));
        physicalSystem = consumerApi.createConsumer(physicalSystem);
        physicalClient = ApiClients.ssl(physicalSystem);
        guest = Consumers.random(owner)
            .name(StringUtil.random("host"))
            .type(ConsumerTypes.System.value())
            .facts(Map.of("virt.uuid", guestUuid, "virt.is_guest", "true"));
        guest = consumerApi.createConsumer(guest);
        guestClient = ApiClients.ssl(guest);
        guestUnmapped = Consumers.random(owner)
            .name(StringUtil.random("host"))
            .type(ConsumerTypes.System.value())
            .facts(Map.of("virt.is_guest", "true"));
        guestUnmapped = consumerApi.createConsumer(guestUnmapped);
        guestUnmappedClient = ApiClients.ssl(guestUnmapped);

    }

    /**
     * We don't want to create everything for all tests
     *  This allows needed granularity
     */
    private void setupProductsAndPools(boolean noVirt, boolean unlimitedVirt, boolean virt,
        boolean virtHostDep, boolean virtInstanceMultiplier, boolean virtProductMultiplier) {

        if (noVirt) {
            productNoVirt = Products.randomSKU()
                .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"));
            productNoVirt = ownerProductApi.createProductByOwner(owner.getKey(), productNoVirt);
            poolNoVirt = createUnlimitedPool(productNoVirt);
        }
        if (unlimitedVirt) {
            productUnlimitedVirt = Products.randomSKU()
                .addAttributesItem(new AttributeDTO().name("virt_limit").value("unlimited"))
                .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"));
            productUnlimitedVirt = ownerProductApi.createProductByOwner(owner.getKey(), productUnlimitedVirt);
            poolUnlimitedVirt = createUnlimitedPool(productUnlimitedVirt);
        }
        if (virt) {
            productVirt = Products.randomSKU()
                .addAttributesItem(new AttributeDTO().name("virt_limit").value("5"))
                .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"));
            productVirt = ownerProductApi.createProductByOwner(owner.getKey(), productVirt);
            PoolDTO poolVirt = createUnlimitedPool(productVirt);
        }
        if (virtHostDep) {
            proudctVirtHostDep = Products.randomSKU()
                .addAttributesItem(new AttributeDTO().name("virt_limit").value("5"))
                .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"))
                .addAttributesItem(new AttributeDTO().name("host-dependent").value("true"));
            proudctVirtHostDep = ownerProductApi.createProductByOwner(owner.getKey(), proudctVirtHostDep);
            poolVirtHostDep = createUnlimitedPool(proudctVirtHostDep);
        }
        if (virtInstanceMultiplier) {
            productVirtInstanceMuliplier = Products.randomSKU()
                .addAttributesItem(new AttributeDTO().name("virt_limit").value("8"))
                .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"))
                .addAttributesItem(new AttributeDTO().name("instance_multiplier").value("6"));
            productVirtInstanceMuliplier = ownerProductApi.createProductByOwner(owner.getKey(),
                productVirtInstanceMuliplier);
            poolVirtInstanceMuliplier = createUnlimitedPool(productVirtInstanceMuliplier);
        }
        if (virtProductMultiplier) {
            productVirtProductMuliplier = Products.randomSKU()
                .addAttributesItem(new AttributeDTO().name("virt_limit").value("8"))
                .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"))
                .multiplier(100L);
            productVirtProductMuliplier = ownerProductApi.createProductByOwner(owner.getKey(),
                productVirtProductMuliplier);
            poolVirtProductMuliplier = createUnlimitedPool(productVirtProductMuliplier);
        }
    }
}
