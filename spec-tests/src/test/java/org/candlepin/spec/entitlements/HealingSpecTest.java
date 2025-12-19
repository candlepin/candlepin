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

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class HealingSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static ConsumerClient consumerClient;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        consumerClient = client.consumers();
    }

    @Test
    public void shouldEntitleNonCompliantProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.randomEng();
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng();
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName())
        );
        ProductDTO parentProduct = Products.randomEng()
            .providedProducts(Set.of(product1, product2));
        parentProduct = ownerProductApi.createProduct(owner.getKey(), parentProduct);
        PoolDTO currentPool = Pools.random(parentProduct)
            .endDate(OffsetDateTime.now().plusYears(1L));
        currentPool = ownerApi.createPool(owner.getKey(), currentPool);

        // Create a future pool, the entitlement should not come from this one:
        PoolDTO futurePool = Pools.random(parentProduct)
            .startDate(OffsetDateTime.now().plusDays(30L))
            .endDate(OffsetDateTime.now().plusDays(60L));
        futurePool = ownerApi.createPool(owner.getKey(), futurePool);

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"))
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .returns(currentPool.getId(), x -> x.get("pool").get("id").asText());
    }

    @Test
    public void shouldEntitleNonCompliantProductsDespiteAValidFutureEntitlement() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.randomEng();
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng();
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName())
        );
        ProductDTO parentProduct = Products.randomEng()
            .providedProducts(Set.of(product1, product2));
        parentProduct = ownerProductApi.createProduct(owner.getKey(), parentProduct);
        PoolDTO currentPool = Pools.random(parentProduct)
            .endDate(OffsetDateTime.now().plusYears(1L));
        currentPool = ownerApi.createPool(owner.getKey(), currentPool);

        // Create a future pool
        PoolDTO futurePool = Pools.random(parentProduct)
            .startDate(OffsetDateTime.now().plusDays(30L))
            .endDate(OffsetDateTime.now().plusDays(60L));
        futurePool = ownerApi.createPool(owner.getKey(), futurePool);

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"))
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().bindPool(consumer.getUuid(), futurePool.getId(), 1);
        assertThat(ents)
            .singleElement()
            .returns(futurePool.getId(), x -> x.get("pool").get("id").asText());
        ents = userClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .returns(currentPool.getId(), x -> x.get("pool").get("id").asText());
    }

    @Test
    public void shouldEntitleNonCompliantProductsAtAFutureDate() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.randomEng();
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng();
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName())
        );
        ProductDTO parentProduct = Products.randomEng()
            .providedProducts(Set.of(product1, product2));
        parentProduct = ownerProductApi.createProduct(owner.getKey(), parentProduct);

        // This one should be skipped, as we're going to specify a future date:
        PoolDTO currentPool = Pools.random(parentProduct)
            .endDate(OffsetDateTime.now().plusYears(1L));
        currentPool = ownerApi.createPool(owner.getKey(), currentPool);

        // Create a future pool, entitlement should end up coming from here:
        PoolDTO futurePool = Pools.random(parentProduct)
            .startDate(OffsetDateTime.now().plusYears(2L))
            .endDate(OffsetDateTime.now().plusYears(4L));
        futurePool = ownerApi.createPool(owner.getKey(), futurePool);

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"))
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        JsonNode ents = getJsonNode(consumerClient.consumers().bind(consumer.getUuid(), null,
            null, null, null, null, false,
            OffsetDateTime.now().plusYears(3L), null));
        assertThat(ents)
            .singleElement()
            .returns(futurePool.getId(), x -> x.get("pool").get("id").asText());
    }

    @Test
    public void shouldMultiEntitleStackedEntitlements() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.randomEng();
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng();
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName())
        );
        String stackId = StringUtil.random("stack");
        ProductDTO parentProduct = Products.randomEng()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(stackId))
            .providedProducts(Set.of(product1, product2));
        parentProduct = ownerProductApi.createProduct(owner.getKey(), parentProduct);
        PoolDTO currentPool = ownerApi.createPool(owner.getKey(), Pools.random(parentProduct));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"))
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .returns(currentPool.getId(), x -> x.get("pool").get("id").asText())
            .returns(4, x -> x.get("quantity").asInt());
    }

    @Test
    public void shouldCompletePartialStacksWithNoInstalledProd() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.randomEng();
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng();
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        ProductDTO product3 = Products.randomEng();
        product3 = ownerProductApi.createProduct(owner.getKey(), product3);
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product3.getId()).productName(product3.getName())
        );
        String stackId = StringUtil.random("stack");
        ProductDTO parentProduct = Products.randomEng()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(stackId))
            .providedProducts(Set.of(product3));
        parentProduct = ownerProductApi.createProduct(owner.getKey(), parentProduct);
        PoolDTO currentPool = ownerApi.createPool(owner.getKey(), Pools.random(parentProduct));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"))
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Consume 2 of the four required
        consumerClient.consumers().bindPool(consumer.getUuid(), currentPool.getId(), 2);
        // Now we have a partial stack that covers no installed products

        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .returns(currentPool.getId(), x -> x.get("pool").get("id").asText())
            .returns(2, x -> x.get("quantity").asInt());
    }

    @Test
    public void shouldMultiEntitleStackedEntitlementsAcrossPools() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.randomEng();
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng();
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName())
        );

        String stackId = StringUtil.random("stack");
        ProductDTO parentProduct1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(stackId))
            .providedProducts(Set.of(product1, product2));
        parentProduct1 = ownerProductApi.createProduct(owner.getKey(), parentProduct1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(parentProduct1).quantity(2L));
        ProductDTO parentProduct2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(stackId))
            .providedProducts(Set.of(product1, product2));
        parentProduct2 = ownerProductApi.createProduct(owner.getKey(), parentProduct2);
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(parentProduct2).quantity(2L));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"))
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents)
            .hasSize(2)
            .map(x -> x.get("quantity").asInt())
            .containsAll(List.of(2, 2));
    }

    @Test
    public void shouldCompleteAPreExistingPartialStack() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ProductDTO product1 = Products.randomEng();
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng();
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName())
        );
        String stackId = StringUtil.random("stack");
        ProductDTO parentProduct = Products.randomEng()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(stackId))
            .providedProducts(Set.of(product1, product2));
        parentProduct = ownerProductApi.createProduct(owner.getKey(), parentProduct);
        PoolDTO currentPool = ownerApi.createPool(owner.getKey(), Pools.random(parentProduct));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"))
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        // First a normal bind to get two entitlements covering 4 of our 8 sockets:
        consumerClient.consumers().bindPool(consumer.getUuid(), currentPool.getId(), 2);

        installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName())
        );
        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().installedProducts(installed));

        // Healing should now get us another entitlement also of quantity 2:
        JsonNode ents = consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .returns(2, x -> x.get("quantity").asInt());
    }

    @Test
    public void shouldHealingFailsWhenAutobindDisabledOnOwner() {
        OwnerDTO owner = Owners.random()
            .autobindDisabled(true)
            .autobindHypervisorDisabled(false);
        ownerApi.createOwner(owner);

        owner = ownerApi.getOwner(owner.getKey());
        assertThat(owner).isNotNull();

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8"));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        final String consumerUuid = consumer.getUuid();

        assertBadRequest(() -> consumerClient.consumers().autoBind(consumerUuid))
            .hasMessageContaining(String.format(
            "Ignoring request to auto-attach. It is disabled for org \\\"%s\\\".", owner.getKey()));
    }

    private JsonNode getJsonNode(String jsonString) {
        return ApiClient.MAPPER.readTree(jsonString);
    }
}
