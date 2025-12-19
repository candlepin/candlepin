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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;

import org.candlepin.dto.api.client.v1.CapabilityDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;


@SpecTest
public class EntitlementMigrateSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        ownerContentApi = client.ownerContent();
    }

    @Test
    public void shouldAllowEntireEntitlementCountsToBeMovedToAnotherDistributor() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Cores.withValue("8"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1).quantity(25L));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO dist1 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .addCapabilitiesItem(new CapabilityDTO().name("cores"));
        dist1 = userClient.consumers().createConsumer(dist1);
        ApiClient dist1Client = ApiClients.ssl(dist1);
        ConsumerDTO dist2 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .addCapabilitiesItem(new CapabilityDTO().name("cores"));
        dist2 = userClient.consumers().createConsumer(dist2);
        ApiClient dist2Client = ApiClients.ssl(dist2);

        PoolDTO pool = dist1Client.pools().listPoolsByOwnerAndProduct(owner.getId(), product1.getId()).get(0);
        JsonNode entitlement = dist1Client.consumers().bindPool(dist1.getUuid(), pool.getId(), 25).get(0);
        client.entitlements().migrateEntitlement(entitlement.get("id").asText(), dist2.getUuid(), 25);

        assertThat(dist1Client.consumers().listEntitlements(dist1.getUuid())).isEmpty();
        assertThat(dist2Client.consumers().listEntitlements(dist2.getUuid()))
            .singleElement()
            .returns(25, EntitlementDTO::getQuantity)
            .doesNotReturn(entitlement.get("id").asText(), EntitlementDTO::getId);
    }

    @Test
    public void shouldAllowPartialEntitlementCountsToBeMovedToAnotherDistributor() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Cores.withValue("8"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1).quantity(25L));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO dist1 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .addCapabilitiesItem(new CapabilityDTO().name("cores"));
        dist1 = userClient.consumers().createConsumer(dist1);
        ApiClient dist1Client = ApiClients.ssl(dist1);
        ConsumerDTO dist2 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .addCapabilitiesItem(new CapabilityDTO().name("cores"));
        dist2 = userClient.consumers().createConsumer(dist2);
        ApiClient dist2Client = ApiClients.ssl(dist2);

        PoolDTO pool = dist1Client.pools().listPoolsByOwnerAndProduct(owner.getId(), product1.getId()).get(0);
        JsonNode originalEnt = dist1Client.consumers().bindPool(dist1.getUuid(), pool.getId(), 25).get(0);
        client.entitlements().migrateEntitlement(originalEnt.get("id").asText(), dist2.getUuid(), 15);

        assertThat(dist1Client.consumers().listEntitlements(dist1.getUuid()))
            .singleElement()
            .returns(10, EntitlementDTO::getQuantity)
            .returns(originalEnt.get("id").asText(), EntitlementDTO::getId);
        assertThat(dist2Client.consumers().listEntitlements(dist2.getUuid()))
            .singleElement()
            .returns(15, EntitlementDTO::getQuantity)
            .doesNotReturn(originalEnt.get("id").asText(), EntitlementDTO::getId);
    }

    @Test
    public void shouldNotAllowMigrationWhenDestinationLacksCapability() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Cores.withValue("8"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1).quantity(25L));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO dist1 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .addCapabilitiesItem(new CapabilityDTO().name("cores"));
        dist1 = userClient.consumers().createConsumer(dist1);
        ApiClient dist1Client = ApiClients.ssl(dist1);
        ConsumerDTO dist2 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value());
        dist2 = userClient.consumers().createConsumer(dist2);
        ApiClient dist2Client = ApiClients.ssl(dist2);
        final String dist2Id = dist2.getUuid();

        PoolDTO pool = dist1Client.pools().listPoolsByOwnerAndProduct(owner.getId(), product1.getId()).get(0);
        JsonNode originalEnt = dist1Client.consumers().bindPool(dist1.getUuid(), pool.getId(), 25).get(0);
        assertBadRequest(() -> client.entitlements()
            .migrateEntitlement(originalEnt.get("id").asText(), dist2Id, 25));
    }

    @Test
    public void shouldMoveEntireEntitlementCountWhenNoneSpecified() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Cores.withValue("8"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1).quantity(25L));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO dist1 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .addCapabilitiesItem(new CapabilityDTO().name("cores"));
        dist1 = userClient.consumers().createConsumer(dist1);
        ApiClient dist1Client = ApiClients.ssl(dist1);
        ConsumerDTO dist2 = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .addCapabilitiesItem(new CapabilityDTO().name("cores"));
        dist2 = userClient.consumers().createConsumer(dist2);
        ApiClient dist2Client = ApiClients.ssl(dist2);

        PoolDTO pool = dist1Client.pools().listPoolsByOwnerAndProduct(owner.getId(), product1.getId()).get(0);
        JsonNode entitlement = dist1Client.consumers().bindPool(dist1.getUuid(), pool.getId(), 25).get(0);
        client.entitlements().migrateEntitlement(entitlement.get("id").asText(), dist2.getUuid(), null);

        assertThat(dist1Client.consumers().listEntitlements(dist1.getUuid())).isEmpty();
        assertThat(dist2Client.consumers().listEntitlements(dist2.getUuid()))
            .singleElement()
            .returns(25, EntitlementDTO::getQuantity)
            .doesNotReturn(entitlement.get("id").asText(), EntitlementDTO::getId);
    }



}
