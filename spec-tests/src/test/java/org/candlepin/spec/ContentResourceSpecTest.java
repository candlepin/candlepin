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
package org.candlepin.spec;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@SpecTest
public class ContentResourceSpecTest {
    private static final Logger log = LoggerFactory.getLogger(ContentResourceSpecTest.class);

    private static ConsumerClient consumerApi;
    private static OwnerApi ownerApi;
    private static OwnerContentApi ownerContentApi;
    private static OwnerProductApi ownerProductApi;
    private static ProductsApi productsApi;

    @BeforeAll
    public static void beforeAll() throws Exception {
        ApiClient client = ApiClients.admin();
        consumerApi = client.consumers();
        productsApi = client.products();
        ownerApi = client.owners();
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldShowContentOnProducts() throws Exception {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        ContentDTO expectedContent = createContent(owner1.getKey(), null, null);
        ProductDTO product = createProduct(owner1.getKey(), 4L, null);
        product = ownerProductApi.addContentToProduct(owner1.getKey(), product.getId(),
            expectedContent.getId(), true);

        ProductDTO actual = productsApi.getProductByUuid(product.getUuid());
        assertEquals(1, actual.getProductContent().size());
        ContentDTO actualContent = actual.getProductContent().iterator().next().getContent();
        assertEquals(expectedContent, actualContent);
    }

    @Test
    public void shouldFilterContentWithMismatchedArchitecture() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();

        // We expect this content to NOT be filtered out due to a match with the system's architecture
        ContentDTO content1 = createContent(owner.getKey(), "/this/is/the/path", "ppc64");
        // We expect this content to be filtered out due to a mismatch with the system's architecture
        ContentDTO content2 = createContent(owner.getKey(), "/this/is/the/path/2", "x86_64");
        // We expect this content to NOT be filtered out due it not specifying an architecture
        ContentDTO content3 = createContent(owner.getKey(), "/this/is/the/path/3", null);

        ProductDTO product = createProduct(owner.getKey(), null, null);
        product = ownerProductApi.addContentToProduct(ownerKey, product.getId(), content1.getId(), true);
        product = ownerProductApi.addContentToProduct(ownerKey, product.getId(), content2.getId(), true);
        product = ownerProductApi.addContentToProduct(ownerKey, product.getId(), content3.getId(), true);
        PoolDTO pool = createPool(owner.getKey(), product);

        ConsumerTypeDTO consumerType = new ConsumerTypeDTO();
        consumerType.setManifest(false);
        consumerType.setLabel("system");
        Map<String, String> facts = new HashMap<>();
        facts.put("system.certificate_version", "3.3");
        facts.put("uname.machine", "ppc64");
        ConsumerDTO consumer = Consumers.random(owner);
        consumer.setName(StringUtil.random("consumer-name"));
        consumer.setType(consumerType);
        consumer.setFacts(facts);

        consumer = consumerApi.createConsumer(consumer, null, owner.getKey(), null, false);
        consumerApi.bindPool(consumer.getUuid(), pool.getId(), null);

        List<JsonNode> jsonNodes = consumerApi.exportCertificates(consumer.getUuid(), null);

        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertEquals(1, jsonBody.get("products").size());
        assertEquals(2, jsonBody.get("products").get(0).get("content").size());

        JsonNode contentOutput1;
        JsonNode contentOutput3;
        // figure out the order of products in the cert, so we can assert properly
        String firstContentName = jsonBody.get("products").get(0).get("content")
            .get(0).get("name").asText();
        if (firstContentName.equals(content1.getName())) {
            contentOutput1 = jsonBody.get("products").get(0).get("content").get(0);
            contentOutput3 = jsonBody.get("products").get(0).get("content").get(1);
        }
        else {
            contentOutput1 = jsonBody.get("products").get(0).get("content").get(1);
            contentOutput3 = jsonBody.get("products").get(0).get("content").get(0);
        }

        assertEquals(content1.getType(), contentOutput1.get("type").asText());
        assertEquals(content1.getName(), contentOutput1.get("name").asText());
        assertEquals(content1.getLabel(), contentOutput1.get("label").asText());
        assertEquals(content1.getVendor(), contentOutput1.get("vendor").asText());
        assertEquals(content1.getContentUrl(), contentOutput1.get("path").asText());
        assertEquals(content1.getArches(), contentOutput1.get("arches").get(0).asText());

        assertEquals(content3.getType(), contentOutput3.get("type").asText());
        assertEquals(content3.getName(), contentOutput3.get("name").asText());
        assertEquals(content3.getLabel(), contentOutput3.get("label").asText());
        assertEquals(content3.getVendor(), contentOutput3.get("vendor").asText());
        assertEquals(content3.getContentUrl(), contentOutput3.get("path").asText());
        assertEquals(0, contentOutput3.get("arches").size());
    }

    @Test
    public void shouldFilterContentWithMismatchedArchitectureFromTheProduct() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String ownerKey = owner.getKey();

        // Even though this product has no arches specified, and should normally not be filtered out,
        // the product it belongs to has an architecture that mismatches with the system's,
        // so we do expect it to get filtered out.
        ContentDTO content1 = createContent(ownerKey, "/this/is/the/path", null);
        AttributeDTO attribute1 = ProductAttributes.Arch.withValue("x86_64");
        ProductDTO product1 = createProduct(ownerKey, null, attribute1);
        product1 = ownerProductApi.addContentToProduct(ownerKey, product1.getId(), content1.getId(), true);

        // This content has no arches specified, but the product it belongs to has an arch that
        // matches with that of the system's, so we do NOT expect it to get filtered out.
        ContentDTO content2 = createContent(ownerKey, "/this/is/the/path2", null);
        AttributeDTO attribute2 = ProductAttributes.Arch.withValue("ppc64");
        ProductDTO product2 = createProduct(ownerKey, null, attribute2);
        product2 = ownerProductApi.addContentToProduct(ownerKey, product2.getId(), content2.getId(), true);

        PoolDTO pool1 = createPool(ownerKey, product1);
        PoolDTO pool2 = createPool(ownerKey, product2);

        ConsumerTypeDTO consumerType = new ConsumerTypeDTO();
        consumerType.setManifest(false);
        consumerType.setLabel("system");
        Map<String, String> facts = new HashMap<>();
        facts.put("system.certificate_version", "3.3");
        facts.put("uname.machine", "ppc64");
        ConsumerDTO consumer = Consumers.random(owner);
        consumer.setName(StringUtil.random("consumer-name"));
        consumer.setType(consumerType);
        consumer.setFacts(facts);

        consumer = consumerApi.createConsumer(consumer, null, ownerKey, null, false);
        consumerApi.bindPool(consumer.getUuid(), pool1.getId(), null);
        consumerApi.bindPool(consumer.getUuid(), pool2.getId(), null);

        List<JsonNode> jsonNodes = consumerApi.exportCertificates(consumer.getUuid(), null);
        assertEquals(2, jsonNodes.size());
        JsonNode jsonBody1 = jsonNodes.get(0);
        assertEquals(1, jsonBody1.get("products").size());
        JsonNode jsonBody2 = jsonNodes.get(1);
        assertEquals(1, jsonBody2.get("products").size());

        JsonNode productOutput1;
        JsonNode productOutput2;
        // figure out the order of products in the cert, so we can assert properly
        if (jsonBody1.get("products").get(0).get("id").asText().equals(product1.getId())) {
            productOutput1 = jsonBody1.get("products").get(0);
            productOutput2 = jsonBody2.get("products").get(0);
        }
        else {
            productOutput1 = jsonBody2.get("products").get(0);
            productOutput2 = jsonBody1.get("products").get(0);
        }

        assertEquals(0, productOutput1.get("content").size());
        assertEquals(1, productOutput2.get("content").size());

        assertEquals(content2.getType(), productOutput2.get("content").get(0).get("type").asText());
        assertEquals(content2.getName(), productOutput2.get("content").get(0).get("name").asText());
        assertEquals(content2.getLabel(), productOutput2.get("content").get(0).get("label").asText());
        assertEquals(content2.getVendor(), productOutput2.get("content").get(0).get("vendor").asText());
        assertEquals(content2.getContentUrl(), productOutput2.get("content").get(0).get("path").asText());

        // Verify that the content's arch was inherited by the product
        String actualArch = productOutput2.get("content").get(0).get("arches").get(0).asText();
        assertEquals("ppc64", actualArch);
    }

    @Nested
    @Isolated
    @Execution(ExecutionMode.SAME_THREAD)
    class GetPages {
        @Test
        public void shouldListContentPagedAndSorted() {
            ApiClient adminClient = ApiClients.admin();

            OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
            String ownerKey = owner.getKey();

            // Ensure that there is data for this test.
            // Most likely, there will already be data left from other test runs that will show up
            IntStream.range(0, 5).forEach(entry -> {
                adminClient.ownerContent().createContent(ownerKey, Contents.random());
                // for timestamp separation
                try {
                    sleep(1000);
                }
                catch (InterruptedException ie) {
                    throw new RuntimeException("Unable to sleep as expected");
                }
            });

            List<ContentDTO> contents = adminClient.content().getContents(1, 4, "asc", "created");
            assertThat(contents)
                .isNotNull()
                .hasSize(4);
            assertThat(contents.get(0).getCreated().compareTo(contents.get(1).getCreated()))
                .isLessThanOrEqualTo(0);
            assertThat(contents.get(1).getCreated().compareTo(contents.get(2).getCreated()))
                .isLessThanOrEqualTo(0);
            assertThat(contents.get(2).getCreated().compareTo(contents.get(3).getCreated()))
                .isLessThanOrEqualTo(0);

        }
    }

    private ContentDTO createContent(String ownerKey, String contentUrl, String arches) {
        ContentDTO newContent = Contents.random();
        newContent.setContentUrl(contentUrl);
        newContent.setArches(arches);
        return ownerContentApi.createContent(ownerKey, newContent);
    }

    private ProductDTO createProduct(String ownerKey, Long multiplier, AttributeDTO attribute) {
        ProductDTO newProduct = Products.randomEng();
        if (attribute != null) {
            newProduct.setAttributes(List.of(attribute));
        }

        newProduct.setMultiplier(multiplier);
        return ownerProductApi.createProduct(ownerKey, newProduct);
    }

    private PoolDTO createPool(String ownerKey, ProductDTO product) {
        PoolDTO pool = Pools.random();
        pool.setProductId(product.getId());
        pool.setProductName(product.getName());
        return ownerApi.createPool(ownerKey, pool);
    }
}
