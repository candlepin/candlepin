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
package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@SpecTest
public class VirtOnlySpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static ConsumerClient consumerApi;
    private static OwnerProductApi ownerProductApi;


    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        consumerApi = client.consumers();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldAllowVirtGuestsToConsumeFromVirtOnlyPools() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO virtProduct = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_only").value("true"));
        ownerProductApi.createProduct(owner.getKey(), virtProduct);
        PoolDTO pool = Pools.random().productId(virtProduct.getId());
        ownerApi.createPool(owner.getKey(), pool);

        ConsumerDTO guest = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("virt.is_guest", "true")));
        ApiClient guestClient = ApiClients.ssl(guest);
        JsonNode entitlements = guestClient.consumers().bindProduct(guest.getUuid(),
            virtProduct.getId());
        assertEquals(1, entitlements.get(0).get("quantity").asInt());
    }

    @Test
    public void shouldAllowVirtGuestsToConsumeFromPhysicalPools() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO physicalProduct = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_only").value("false"));
        ownerProductApi.createProduct(owner.getKey(), physicalProduct);
        PoolDTO pool = Pools.random().productId(physicalProduct.getId());
        ownerApi.createPool(owner.getKey(), pool);

        ConsumerDTO guest = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("virt.is_guest", "true")));
        ApiClient guestClient = ApiClients.ssl(guest);
        JsonNode entitlements = guestClient.consumers().bindProduct(guest.getUuid(),
            physicalProduct.getId());
        assertEquals(1, entitlements.get(0).get("quantity").asInt());
    }

    @Test
    public void shouldDenyPhysicalConsumersFromVirtOnlyPools() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO virtProduct = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_only").value("true"));
        ownerProductApi.createProduct(owner.getKey(), virtProduct);
        PoolDTO pool = Pools.random().productId(virtProduct.getId());
        ownerApi.createPool(owner.getKey(), pool);

        ConsumerDTO guest = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("virt.is_guest", "false")));
        ApiClient guestClient = ApiClients.ssl(guest);
        JsonNode entitlements = guestClient.consumers().bindProduct(guest.getUuid(),
            virtProduct.getId());
        assertThat(entitlements).hasSize(0);
    }

    @Test
    public void shouldVirtOnlyProductResultInVirtOnlyPool() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO virtProduct = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_only").value("true"));
        ownerProductApi.createProduct(owner.getKey(), virtProduct);
        PoolDTO pool = Pools.random().productId(virtProduct.getId());
        ownerApi.createPool(owner.getKey(), pool);

        ConsumerDTO guest = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("virt.is_guest", "true")));
        ApiClient guestClient = ApiClients.ssl(guest);
        JsonNode entitlements = guestClient.consumers().bindProduct(guest.getUuid(),
            virtProduct.getId());

        EntitlementDTO ent = ApiClient.MAPPER.convertValue(entitlements.get(0), EntitlementDTO.class);
        PoolDTO guestPool = ent.getPool();
        AttributeDTO virtOnly = guestPool.getAttributes().stream()
            .filter(x -> x.getName().equals("virt_only"))
            .collect(Collectors.toList()).get(0);
        assertEquals("true", virtOnly.getValue());
    }

    @Test
    public void shouldAllowVirtOnlyPoolsToBeListedForManifestConsumers() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO virtProduct = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_only").value("true"));
        ownerProductApi.createProduct(owner.getKey(), virtProduct);
        PoolDTO pool = Pools.random().productId(virtProduct.getId());
        ownerApi.createPool(owner.getKey(), pool);

        ConsumerDTO manifest = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient manifestClient = ApiClients.ssl(manifest);
        List<PoolDTO> pools = manifestClient.pools().listPoolsByConsumer(manifest.getUuid());
        assertThat(pools).hasSize(1);

        AttributeDTO virtOnly = pools.get(0).getAttributes().stream()
            .filter(x -> x.getName().equals("virt_only"))
            .collect(Collectors.toList()).get(0);
        assertEquals("true", virtOnly.getValue());
    }
}
