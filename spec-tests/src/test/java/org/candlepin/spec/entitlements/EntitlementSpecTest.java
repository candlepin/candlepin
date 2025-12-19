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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.List;


@SpecTest
public class EntitlementSpecTest {
    @Test
    public void shouldBypassRulesForCandlepinConsumers() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient cpClient = ApiClients.ssl(consumer);
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        cpClient.consumers().bindProduct(consumer.getUuid(), product.getId());

        assertThat(cpClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement();
    }

    @Test
    public void shouldThrowAnErrorWhenFilteringByANonExistantProductId() throws Exception {
        ApiClient adminClient = ApiClients.admin();

        assertBadRequest(() -> adminClient.entitlements()
            .listAllForConsumer("non-existant", null, null, null, null, null, null));
    }

    @Test
    public void shouldAllowAnEntitlementToBeConsumedByProduct() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        adminClient.consumers().bindProduct(consumer.getUuid(), product);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement();
    }

    @Test
    public void shouldAllowAnEntitlementToBeConsumedByPool() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement();
    }

    @Test
    public void shouldAllowConsumptionOfQuantityTen() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        AttributeDTO multiEntAttr = ProductAttributes.MultiEntitlement.withValue("yes");
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random().attributes(List.of(multiEntAttr)));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product).quantity(20L));

        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 10);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(10, EntitlementDTO::getQuantity);
    }

    @Test
    public void shouldAllowConsumptionOfQuantityZero() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 0);

        assertThat(adminClient.pools().getPool(pool.getId(), consumer.getUuid(), null))
            .isNotNull()
            .returns(1L, PoolDTO::getConsumed);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(1, EntitlementDTO::getQuantity);
    }

    @Test
    public void shouldAllowMultipleProductsToBeConsumed() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        AttributeDTO multiEntAttr = ProductAttributes.MultiEntitlement.withValue("yes");
        ProductDTO virtProd = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random().attributes(List.of(multiEntAttr)));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO virtPool = adminClient.owners().createPool(owner.getKey(), Pools.random(virtProd));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        adminClient.consumers().bindPool(consumer.getUuid(), virtPool.getId(), 1);
        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .hasSize(2);
    }

    @Test
    public void shouldHaveTheCorrectProductIdWhenSubscribingByProduct() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        adminClient.consumers().bindProduct(consumer.getUuid(), product);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns(product.getId(), PoolDTO::getProductId);
    }

    @Test
    public void shouldHaveTheCorrectProductIdWhenSubscribingByPool() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement();
    }

    @Test
    public void shouldFilterEntitlementsByProductAttribute() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        AttributeDTO multiEntAttr = ProductAttributes.MultiEntitlement.withValue("yes");
        ProductDTO virtProd = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random().attributes(List.of(multiEntAttr)));
        AttributeDTO expectedAttribute = ProductAttributes.Variant.withValue(StringUtil.random("var-"));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random().attributes(List.of(expectedAttribute)));
        PoolDTO virtPool = adminClient.owners().createPool(owner.getKey(), Pools.random(virtProd));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        adminClient.consumers().bindPool(consumer.getUuid(), virtPool.getId(), 1);
        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        String keyValue = expectedAttribute.getName() + ":" + expectedAttribute.getValue();
        List<EntitlementDTO> actual = adminClient.entitlements()
            .listAllForConsumer(null, null, List.of(keyValue), null, null, null, null);

        assertThat(actual)
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .extracting(PoolDTO::getProductAttributes, as(collection(AttributeDTO.class)))
            .contains(expectedAttribute);
    }

    @Test
    public void shouldBeRemovedAfterRevokingAllEntitlements() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        AttributeDTO multiEntAttr = ProductAttributes.MultiEntitlement.withValue("yes");
        ProductDTO virtProd = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random().attributes(List.of(multiEntAttr)));
        adminClient.owners().createPool(owner.getKey(), Pools.random(virtProd));
        adminClient.consumers().bindProduct(consumer.getUuid(), virtProd.getId());
        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement();

        adminClient.consumers().unbindAll(consumer.getUuid());

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .isEmpty();
    }

    @Test
    public void shouldRemoveMultipleEntitlementsAfterRevokingAllEntitlements() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        AttributeDTO multiEntAttr = ProductAttributes.MultiEntitlement.withValue("yes");
        ProductDTO virtProd = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random().attributes(List.of(multiEntAttr)));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(virtProd));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        adminClient.consumers().bindProduct(consumer.getUuid(), virtProd.getId());
        adminClient.consumers().bindProduct(consumer.getUuid(), product.getId());
        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .hasSize(2);

        adminClient.consumers().unbindAll(consumer.getUuid());

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .isEmpty();
    }

    @Test
    public void shouldNotAllowConsumingTwoEntitlementsForTheSameProduct() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        adminClient.consumers().bindProduct(consumer.getUuid(), product.getId());

        JsonNode actual = adminClient.consumers().bindProduct(consumer.getUuid(), product.getId());

        assertThat(actual).isEmpty();
    }

    @Test
    public void shouldNotAllowConsumingTwoEntitlementsInSamePool() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        assertForbidden(() -> adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1));
    }

    @Test
    public void shouldNotAllowConsumingAnOddQuantity() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        assertForbidden(() -> adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 3));
    }

    @Test
    public void shouldAllowConsumingAnEvenQuantity() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        AttributeDTO attribute = ProductAttributes.MultiEntitlement.withValue("yes");
        ProductDTO product = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random().attributes(List.of(attribute)));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 4);

        assertThat(adminClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement();
    }

}
