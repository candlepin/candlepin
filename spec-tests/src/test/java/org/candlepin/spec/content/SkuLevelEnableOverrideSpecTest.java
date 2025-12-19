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
package org.candlepin.spec.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.CertificateAssert.assertThatCert;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class SkuLevelEnableOverrideSpecTest {

    static ApiClient client;
    static OwnerClient ownerClient;
    static ConsumerClient consumerClient;
    static OwnerProductApi ownerProductApi;
    static OwnerContentApi ownerContentApi;

    @BeforeAll
    static void beforeAll() {
        client = ApiClients.admin();
        ownerClient = client.owners();
        consumerClient = client.consumers();
        ownerProductApi = client.ownerProducts();
        ownerContentApi = client.ownerContent();
    }

    @Test
    public void shouldSkuOverrideForEnableShowsOnProvidedProductInEntitlement() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ConsumerDTO consumer = consumerClient.createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.2")));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO providedProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.randomEng());
        ContentDTO content1 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ownerProductApi.addContentToProduct(owner.getKey(), providedProduct.getId(), content1.getId(), true);
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.ContentOverrideEnabled.withValue(content1.getId()))
            .providedProducts(Set.of(providedProduct));
        product = ownerProductApi.createProduct(owner.getKey(), product);

        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        List<JsonNode> result = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        JsonNode products = result.get(0).get("products");
        assertThat(products)
            .hasSize(2);
        assertThat(products)
            .filteredOn(prod -> providedProduct.getId().equals(prod.get("id").asText()))
            .singleElement()
            .returns(1, x -> x.get("content").size())
            .returns(content1.getId(), x -> x.get("content").get(0).get("id").asText())
            .returns(null, x -> x.get("content").get(0).get("enabled"));
    }

    @Test
    public void shouldSkuOverrideForDisableShowsOnProvidedProductInEntitlement() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ConsumerDTO consumer = consumerClient.createConsumer(Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.2")));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO providedProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.randomEng());
        ContentDTO content1 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ownerProductApi.addContentToProduct(owner.getKey(), providedProduct.getId(), content1.getId(), true);
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.ContentOverrideDisabled.withValue(content1.getId()))
            .providedProducts(Set.of(providedProduct));
        product = ownerProductApi.createProduct(owner.getKey(), product);

        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        List<JsonNode> result = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        JsonNode products = result.get(0).get("products");
        assertThat(products)
            .hasSize(2);
        assertThat(products)
            .filteredOn(prod -> providedProduct.getId().equals(prod.get("id").asText()))
            .singleElement()
            .returns(1, x -> x.get("content").size())
            .returns(content1.getId(), x -> x.get("content").get(0).get("id").asText())
            .returns(false, x -> x.get("content").get(0).get("enabled").asBoolean());
    }

    @Test
    public void shouldSkuOverrideForEnabledSupercededByEnvironmentPromotion() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        EnvironmentDTO env = ownerClient.createEnvironment(owner.getKey(), Environments.random());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.2"))
            .environments(List.of(env));
        consumer = consumerClient.createConsumer(consumer);
        assertThat(consumer.getEnvironments())
            .hasAtLeastOneElementOfType(EnvironmentDTO.class);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO providedProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.randomEng());
        ContentDTO content1 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ownerProductApi.addContentToProduct(owner.getKey(), providedProduct.getId(), content1.getId(), true);
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.ContentOverrideDisabled.withValue(content1.getId()))
            .providedProducts(Set.of(providedProduct));
        product = ownerProductApi.createProduct(owner.getKey(), product);

        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));

        // Override enabled to true:
        ContentToPromoteDTO toPromote = new ContentToPromoteDTO()
            .contentId(content1.getId())
            .enabled(true);
        AsyncJobStatusDTO job = client.environments().promoteContent(
            env.getId(), List.of(toPromote), true);
        job = client.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();

        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        List<JsonNode> result = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        JsonNode products = result.get(0).get("products");
        assertThat(products)
            .hasSize(2);
        assertThat(products)
            .filteredOn(prod -> providedProduct.getId().equals(prod.get("id").asText()))
            .singleElement()
            .returns(1, x -> x.get("content").size())
            .returns(content1.getId(), x -> x.get("content").get(0).get("id").asText())
            .returns(null, x -> x.get("content").get(0).get("enabled"));
    }

    @Test
    public void shouldSkuOverrideForEnabledShowsOnProvidedProductInV1Entitlement() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0"));
        consumer = consumerClient.createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO providedProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.randomEng());
        ContentDTO content1 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ownerProductApi.addContentToProduct(owner.getKey(), providedProduct.getId(), content1.getId(), false);
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.ContentOverrideEnabled.withValue(content1.getId()))
            .providedProducts(Set.of(providedProduct));
        product = ownerProductApi.createProduct(owner.getKey(), product);

        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        EntitlementDTO ent = consumerClient.consumers().listEntitlements(consumer.getUuid()).get(0);
        CertificateDTO cert = ent.getCertificates().iterator().next();
        assertThatCert(cert).hasContentRepoEnabled(content1);
    }

    @Test
    public void shouldSkuOverrideForDisabledShowsOnProvidedProductInV1Entitlement() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0"));
        consumer = consumerClient.createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO providedProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.randomEng());
        ContentDTO content1 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ownerProductApi.addContentToProduct(owner.getKey(), providedProduct.getId(), content1.getId(), true);
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.ContentOverrideDisabled.withValue(content1.getId()))
            .providedProducts(Set.of(providedProduct));
        product = ownerProductApi.createProduct(owner.getKey(), product);

        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        EntitlementDTO ent = consumerClient.consumers().listEntitlements(consumer.getUuid()).get(0);
        CertificateDTO cert = ent.getCertificates().iterator().next();
        assertThatCert(cert).hasContentRepoDisabled(content1);
    }

    @Test
    public void shouldSkuOverrideForEnabledSupercededByEnvironmentPromotionV1() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        EnvironmentDTO env = ownerClient.createEnvironment(owner.getKey(), Environments.random());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "1.0"))
            .environments(List.of(env));
        consumer = consumerClient.createConsumer(consumer);
        assertThat(consumer.getEnvironments())
            .hasAtLeastOneElementOfType(EnvironmentDTO.class);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO providedProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.randomEng());
        ContentDTO content1 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ownerProductApi.addContentToProduct(owner.getKey(), providedProduct.getId(), content1.getId(), false);
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.ContentOverrideDisabled.withValue(content1.getId()))
            .providedProducts(Set.of(providedProduct));
        product = ownerProductApi.createProduct(owner.getKey(), product);

        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));

        // Override enabled to true:
        ContentToPromoteDTO toPromote = new ContentToPromoteDTO()
            .contentId(content1.getId())
            .enabled(true);
        AsyncJobStatusDTO job = client.environments().promoteContent(
            env.getId(), List.of(toPromote), true);
        job = client.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();

        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        EntitlementDTO ent = consumerClient.consumers().listEntitlements(consumer.getUuid()).get(0);
        CertificateDTO cert = ent.getCertificates().iterator().next();
        assertThatCert(cert).hasContentRepoEnabled(content1);
    }
}
