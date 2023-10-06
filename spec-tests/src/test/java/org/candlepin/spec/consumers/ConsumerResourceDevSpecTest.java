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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.PoolQuantityDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
@OnlyInHosted
public class ConsumerResourceDevSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static HostedTestApi hostedTestApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        hostedTestApi = client.hosted();
    }

    @Test
    public void shouldCreateEntitlementToNewlyCreatedDevPool() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        // active subscription to allow this all to work
        ProductDTO activeProduct = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO activeSubscription = Pools.random(activeProduct)
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        ownerApi.createPool(owner.getKey(), activeSubscription);

        ProductDTO providedProduct1 = hostedTestApi.createProduct(Products.randomEng());
        ProductDTO providedProduct2 = hostedTestApi.createProduct(Products.randomEng());

        ProductDTO devProduct1 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));
        ProductDTO devProduct2 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProduct1.getId()))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        Set<ConsumerInstalledProductDTO> installed = Set.of(installedProductOf(providedProduct1),
            installedProductOf(providedProduct2));

        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().installedProducts(installed));
        autoAttachAndVerifyDevProduct(consumer, consumerClient, devProduct1.getId(), null);
    }

    @Test
    public void shouldCreateNewEntitlementWhenDevPoolAlreadyExists() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        // active subscription to allow this all to work
        ProductDTO activeProduct = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO activeSubscription = Pools.random(activeProduct)
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        ownerApi.createPool(owner.getKey(), activeSubscription);

        ProductDTO providedProduct1 = hostedTestApi.createProduct(Products.randomEng());
        ProductDTO providedProduct2 = hostedTestApi.createProduct(Products.randomEng());

        ProductDTO devProduct1 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProduct1.getId()))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        Set<ConsumerInstalledProductDTO> installed = Set.of(installedProductOf(providedProduct1),
            installedProductOf(providedProduct2));

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .installedProducts(installed));
        EntitlementDTO initialEnt = autoAttachAndVerifyDevProduct(consumer, consumerClient,
            devProduct1.getId(), null);
        autoAttachAndVerifyDevProduct(consumer, consumerClient, devProduct1.getId(), initialEnt.getId());
    }

    @Test
    public void shouldCreateNewEntitlementWenDevSkuAttributeChanges() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        // active subscription to allow this all to work
        ProductDTO activeProduct = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO activeSubscription = Pools.random(activeProduct)
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        ownerApi.createPool(owner.getKey(), activeSubscription);

        ProductDTO providedProduct1 = hostedTestApi.createProduct(Products.randomEng());
        ProductDTO providedProduct2 = hostedTestApi.createProduct(Products.randomEng());

        ProductDTO devProduct1 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));
        ProductDTO devProduct2 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProduct1.getId()))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        Set<ConsumerInstalledProductDTO> installed = Set.of(installedProductOf(providedProduct1),
            installedProductOf(providedProduct2));

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().
            installedProducts(installed));
        EntitlementDTO ent = autoAttachAndVerifyDevProduct(consumer, consumerClient,
            devProduct1.getId(), null);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProduct2.getId()))));
        autoAttachAndVerifyDevProduct(consumer, consumerClient, devProduct2.getId(), ent.getId());
    }

    @Test
    public void shouldAllowSubManGuiProcessForAutoBind() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        // active subscription to allow this all to work
        ProductDTO activeProduct = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO activeSubscription = Pools.random(activeProduct)
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        ownerApi.createPool(owner.getKey(), activeSubscription);

        ProductDTO providedProduct1 = hostedTestApi.createProduct(Products.randomEng());
        ProductDTO providedProduct2 = hostedTestApi.createProduct(Products.randomEng());

        ProductDTO devProduct1 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProduct1.getId()))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        Set<ConsumerInstalledProductDTO> installed = Set.of(installedProductOf(providedProduct1),
            installedProductOf(providedProduct2));

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .installedProducts(installed));
        List<PoolQuantityDTO> pools = consumerClient.consumers().dryBind(consumer.getUuid(), null);
        assertThat(pools).singleElement();
        consumerClient.consumers().bindPool(consumer.getUuid(), pools.get(0).getPool().getId(), 1);
        List<EntitlementDTO> ents = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns("DEVELOPMENT", PoolDTO::getType)
            .returns(devProduct1.getId(), PoolDTO::getProductId)
            .returns(2, x -> x.getProvidedProducts().size())
            .returns(pools.get(0).getPool().getId(), PoolDTO::getId);
    }

    @Test
    public void shouldNotAllowEntitlementForConsumerPastExpiration() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        // active subscription to allow this all to work
        ProductDTO activeProduct = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO activeSubscription = Pools.random(activeProduct)
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        ownerApi.createPool(owner.getKey(), activeSubscription);

        ProductDTO providedProduct1 = hostedTestApi.createProduct(Products.randomEng());
        ProductDTO providedProduct2 = hostedTestApi.createProduct(Products.randomEng());

        ProductDTO devProduct1 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProduct1.getId())))
            .created(OffsetDateTime.now().minusDays(90)));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertForbidden(() -> consumerClient.consumers().autoBind(consumer.getUuid()))
            .hasMessageContaining(String.format("Unable to attach subscription for the product \\\"%s\\\": " +
            "Subscriptions for %s expired on:", devProduct1.getId(), devProduct1.getId()));
    }

    @Test
    public void shouldUpdateDevProductOnRefresh() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        // active subscription to allow this all to work
        ProductDTO activeProduct = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO activeSubscription = Pools.random(activeProduct)
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        ownerApi.createPool(owner.getKey(), activeSubscription);

        ProductDTO providedProduct1 = hostedTestApi.createProduct(Products.randomEng());
        ProductDTO providedProduct2 = hostedTestApi.createProduct(Products.randomEng());

        ProductDTO devProduct1 = hostedTestApi.createProduct(Products.random()
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("60"))
            .providedProducts(Set.of(providedProduct1, providedProduct2)));

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.DevSku.withValue(devProduct1.getId()))));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        Set<ConsumerInstalledProductDTO> installed = Set.of(installedProductOf(providedProduct1),
            installedProductOf(providedProduct2));

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .installedProducts(installed));
        autoAttachAndVerifyDevProduct(consumer, consumerClient, devProduct1.getId(), null);

        ProductDTO providedProduct3 = hostedTestApi.createProduct(Products.randomEng());
        ProductDTO upstreamDevProduct = hostedTestApi.updateProduct(devProduct1.getId(), new ProductDTO()
            .name("updated dev product 1")
            .addAttributesItem(new AttributeDTO().name("test_attribute").value("test_value"))
            .addAttributesItem(ProductAttributes.ExpiresAfter.withValue("90"))
            .providedProducts(Set.of(providedProduct1, providedProduct2, providedProduct3)));

        ProductDTO existingDevProduct = client.ownerProducts().getProductById(owner.getKey(),
            devProduct1.getId());
        AsyncJobStatusDTO job = client.owners().refreshPools(owner.getKey(), false);
        job = client.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();

        ProductDTO updatedDevProduct = client.ownerProducts().getProductById(owner.getKey(),
            devProduct1.getId());

        // Verify base state
        assertThat(existingDevProduct)
            .returns(devProduct1.getId(), ProductDTO::getId)
            .returns(devProduct1.getName(), ProductDTO::getName)
            .returns(getAttributeValue(devProduct1, ProductAttributes.ExpiresAfter.key()), x ->
                getAttributeValue(x, ProductAttributes.ExpiresAfter.key()))
            .returns(null, x -> getAttributeValue(x, "test_attribute"));

        // Verify updated state
        assertThat(updatedDevProduct)
            .returns(devProduct1.getId(), ProductDTO::getId)
            .doesNotReturn(devProduct1.getName(), ProductDTO::getName)
            .doesNotReturn(getAttributeValue(devProduct1, ProductAttributes.ExpiresAfter.key()), x ->
                getAttributeValue(x, ProductAttributes.ExpiresAfter.key()))
            .returns("test_value", x -> getAttributeValue(x, "test_attribute"));

        assertThat(upstreamDevProduct)
            .returns(updatedDevProduct.getName(), ProductDTO::getName)
            .returns(getAttributeValue(updatedDevProduct, ProductAttributes.ExpiresAfter.key()), x ->
            getAttributeValue(x, ProductAttributes.ExpiresAfter.key()))
            .returns(getAttributeValue(updatedDevProduct, "test_attribute"), x ->
            getAttributeValue(x, "test_attribute"));
    }

    private EntitlementDTO autoAttachAndVerifyDevProduct(ConsumerDTO consumer, ApiClient consumerClient,
        String expectedProductId, String oldEntitlementId) {
        consumerClient.consumers().autoBind(consumer.getUuid());
        List<EntitlementDTO> entitlements = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(entitlements)
            .singleElement()
            .doesNotReturn(oldEntitlementId, EntitlementDTO::getId)
            .extracting(EntitlementDTO::getPool)
            .returns("DEVELOPMENT", PoolDTO::getType)
            .returns(expectedProductId, PoolDTO::getProductId)
            .returns(2, x -> x.getProvidedProducts().size());
        return entitlements.get(0);
    }

    private String getAttributeValue(ProductDTO product, String name) {
        return product.getAttributes().stream()
            .filter(y -> y.getName().equals(name))
            .findFirst()
            .map(AttributeDTO::getValue)
            .orElse(null);
    }

    private ConsumerInstalledProductDTO installedProductOf(ProductDTO product) {
        return new ConsumerInstalledProductDTO().productId(product.getId()).productName(product.getName());
    }
}
