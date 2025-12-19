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

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.resource.client.v1.OwnerApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.ProductsApi;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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

    @Nested
    @Isolated
    @TestInstance(Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    public class ContentQueryTests {

        private static final String QUERY_PATH = "/content";

        private static final String INCLUSION_INCLUDE = "include";
        private static final String INCLUSION_EXCLUDE = "exclude";
        private static final String INCLUSION_EXCLUSIVE = "exclusive";

        private static record ProductInfo(ProductDTO dto, OwnerDTO owner, Set<String> activeOwners) {

            public String id() {
                return this.dto() != null ? this.dto().getId() : null;
            }

            public boolean active() {
                return !this.activeOwners().isEmpty();
            }

            public boolean custom() {
                return this.owner() != null;
            }

            public Stream<ContentDTO> content() {
                return Optional.ofNullable(this.dto())
                  .map(ProductDTO::getProductContent)
                  .map(Collection::stream)
                  .orElse(Stream.empty())
                  .map(ProductContentDTO::getContent);
            }
        };

        private ApiClient adminClient;

        private List<OwnerDTO> owners;
        private Map<String, ProductInfo> productMap;

        /**
         * Verifies either the manifest generator extension or the hosted test extension is present as
         * required by the current operating mode.
         */
        private void checkRequiredExtensions() {
            if (CandlepinMode.isStandalone()) {
                assumeTrue(CandlepinMode::hasManifestGenTestExtension);
            }
            else {
                assumeTrue(CandlepinMode::hasHostedTestExtension);
            }
        }

        private OwnerDTO createOwner(String keyPrefix) {
            OwnerDTO owner = Owners.random()
                .key(StringUtil.random(keyPrefix + "-"));

            return this.adminClient.owners()
                .createOwner(owner);
        }

        private SubscriptionDTO createSubscription(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Impl note:
            // We create active subscriptions in the future to work around a limitation on manifests
            // disallowing and ignoring expired pools
            return Subscriptions.random(owner, product)
                .startDate(active ? now.minus(7, ChronoUnit.DAYS) : now.plus(7, ChronoUnit.DAYS))
                .endDate(now.plus(30, ChronoUnit.DAYS));
        }

        private PoolDTO createPool(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            PoolDTO pool = Pools.random(product)
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(active ? now.plus(7, ChronoUnit.DAYS) : now.minus(3, ChronoUnit.DAYS));

            return this.adminClient.owners()
                .createPool(owner.getKey(), pool);
        }

        private void mapProductInfo(ProductDTO product, OwnerDTO owner, OwnerDTO activeOwner) {
            Set<String> activeOwnerKeys = new HashSet<>();
            if (activeOwner != null) {
                activeOwnerKeys.add(activeOwner.getKey());
            }

            ProductInfo existing = this.productMap.get(product.getId());
            if (existing != null) {
                activeOwnerKeys.addAll(existing.activeOwners());
            }

            ProductInfo pinfo = new ProductInfo(product, owner, activeOwnerKeys);
            this.productMap.put(product.getId(), pinfo);

        }

        private void commitGlobalSubscriptions(Collection<SubscriptionDTO> subscriptions) throws Exception {
            Map<String, List<SubscriptionDTO>> subscriptionMap = new HashMap<>();

            for (SubscriptionDTO subscription : subscriptions) {
                NestedOwnerDTO owner = subscription.getOwner();

                subscriptionMap.computeIfAbsent(owner.getKey(), key -> new ArrayList<>())
                    .add(subscription);
            }

            for (Map.Entry<String, List<SubscriptionDTO>> entry : subscriptionMap.entrySet()) {
                String ownerKey = entry.getKey();
                List<SubscriptionDTO> ownerSubs = entry.getValue();

                AsyncJobStatusDTO job;
                if (CandlepinMode.isStandalone()) {
                    File manifest = new ExportGenerator()
                        .addSubscriptions(ownerSubs)
                        .export();

                    job = this.adminClient.owners()
                        .importManifestAsync(ownerKey, List.of(), manifest);
                }
                else {
                    ownerSubs.forEach(subscription -> this.adminClient.hosted()
                        .createSubscription(subscription, true));

                    job = this.adminClient.owners()
                        .refreshPools(ownerKey, false);
                }

                assertThatJob(job)
                    .isNotNull()
                    .terminates(this.adminClient)
                    .isFinished();
            }
        }

        @BeforeAll
        public void setup() throws Exception {
            // Ensure we have our required test extensions or we'll be very broken...
            this.checkRequiredExtensions();

            this.adminClient = ApiClients.admin();

            this.owners = List.of(
                this.createOwner("owner1"),
                this.createOwner("owner2"),
                this.createOwner("owner3"));

            this.productMap = new HashMap<>();

            List<SubscriptionDTO> subscriptions = new ArrayList<>();

            // Dummy org we use for creating a bunch of future pools for our global products. We'll also
            // create per-org subscriptions for these products later. Also note that these will never be
            // fully resolved.
            OwnerDTO globalOwner = this.createOwner("global");

            List<ProductDTO> globalProducts = new ArrayList<>();
            for (int i = 1; i <= 3; ++i) {
                List<ContentDTO> contents = new ArrayList<>();

                for (int c = 0; c < 2; ++c) {
                    ContentDTO content = Contents.random()
                        .id(String.format("g-content-%d%s", i, (char) ('a' + c)))
                        .name(String.format("global_content_%d%s", i, (char) ('a' + c)))
                        .label(String.format("global content %d%s", i, (char) ('a' + c)));

                    contents.add(content);
                }

                ProductDTO gprod = new ProductDTO()
                    .id("g-prod-" + i)
                    .name("global product " + i)
                    .addProductContentItem(Contents.toProductContent(contents.get(0), true))
                    .addProductContentItem(Contents.toProductContent(contents.get(1), false));

                subscriptions.add(this.createSubscription(globalOwner, gprod, false));
                globalProducts.add(gprod);
                this.mapProductInfo(gprod, null, null);
            }

            for (int oidx = 1; oidx <= owners.size(); ++oidx) {
                OwnerDTO owner = owners.get(oidx - 1);

                List<ProductDTO> ownerProducts = new ArrayList<>();
                for (int i = 1; i <= 2; ++i) {
                    List<ContentDTO> contents = new ArrayList<>();

                    for (int c = 0; c < 2; ++c) {
                        ContentDTO content = Contents.random()
                            .id(String.format("o%d-content-%d%s", oidx, i, (char) ('a' + c)))
                            .name(String.format("%s_content_%d%s", owner.getKey(), i, (char) ('a' + c)))
                            .label(String.format("%s content %d%s", owner.getKey(), i, (char) ('a' + c)));

                        contents.add(this.adminClient.ownerContent()
                            .createContent(owner.getKey(), content));
                    }

                    ProductDTO cprod = new ProductDTO()
                        .id(String.format("o%d-prod-%d", oidx, i))
                        .name(String.format("%s product %d", owner.getKey(), i))
                        .addProductContentItem(Contents.toProductContent(contents.get(0), true))
                        .addProductContentItem(Contents.toProductContent(contents.get(1), false));

                    cprod = this.adminClient.ownerProducts()
                        .createProduct(owner.getKey(), cprod);

                    ownerProducts.add(cprod);
                    this.mapProductInfo(cprod, owner, null);
                }

                // Create an active and inactive global subscription for this org
                subscriptions.add(this.createSubscription(owner, globalProducts.get(0), true));
                subscriptions.add(this.createSubscription(owner, globalProducts.get(1), false));
                this.mapProductInfo(globalProducts.get(0), null, owner);

                // Create an active and inactive custom subscription
                this.createPool(owner, ownerProducts.get(0), true);
                this.createPool(owner, ownerProducts.get(1), false);
                this.mapProductInfo(ownerProducts.get(0), owner, owner);
            }

            this.commitGlobalSubscriptions(subscriptions);
        }

        // Impl note:
        // Since we cannot depend on the global state being prestine from run to run (or even test to
        // test), we cannot use exact matching on our output as extraneous products from previous test
        // runs or otherwise pre-existing data may show up in the result set. Instead, these tests will
        // verify our expected products are present, and unexpected products from the test data are not.

        private List<String> getUnexpectedIds(List<String> expectedIds) {
            return this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .filter(Predicate.not(expectedIds::contains))
                .toList();
        }

        @Test
        public void shouldAllowQueryingWithoutAnyFilters() {
            List<String> expectedCids = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids);
        }

        @Test
        public void shouldDefaultToActiveExclusiveCustomInclusive() {
            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, null, null);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringOnOwnerKeys() {
            List<String> ownerKeys = List.of(
                this.owners.get(0).getKey(),
                this.owners.get(1).getKey());

            Predicate<ProductInfo> ownerMatchPredicate = pinfo -> pinfo.owner() == null ||
                ownerKeys.contains(pinfo.owner().getKey());

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(ownerMatchPredicate)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(ownerKeys, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_owner" })
        @NullAndEmptySource
        public void shouldFailWithInvalidOwners(String ownerKey) {
            List<String> ownerKeys = Arrays.asList(ownerKey);

            assertNotFound(() -> this.adminClient.content().getContents(ownerKeys, null, null, null, null));
        }

        @Test
        public void shouldAllowFilteringOnIDs() {
            // Randomly seeded for consistency; seed itself chosen randomly
            Random rand = new Random(99955);

            List<String> cids = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .sorted()
                .filter(elem -> rand.nextBoolean())
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, cids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(cids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(cids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_id" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidIds(String cid) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything

            ContentDTO content = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .findAny()
                .get();

            List<String> cids = Arrays.asList(content.getId(), cid);
            List<String> expectedCids = List.of(content.getId());

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, cids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringOnLabel() {
            Random rand = new Random(86243);

            List<ContentDTO> contents = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .sorted(Comparator.comparing(ContentDTO::getId))
                .filter(elem -> rand.nextBoolean())
                .toList();

            List<String> labels = contents.stream()
                .map(ContentDTO::getLabel)
                .toList();

            List<String> expectedCids = contents.stream()
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, labels, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_label" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidLabels(String label) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything

            ContentDTO content = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .findAny()
                .get();

            List<String> labels = Arrays.asList(content.getLabel(), label);
            List<String> expectedCids = List.of(content.getId());

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, labels, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringWithActiveIncluded() {
            // This is effectively the same as no filter -- we expect everything back
            List<String> expectedCids = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids);
        }

        @Test
        public void shouldAllowFilteringWithActiveExcluded() {
            List<String> expecteCids = this.productMap.values()
                .stream()
                .filter(pinfo -> !pinfo.active())
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, INCLUSION_EXCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expecteCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expecteCids));
        }

        @Test
        public void shouldAllowFilteringWithActiveExclusive() {
            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, INCLUSION_EXCLUSIVE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldErrorWithInvalidActiveInclusion() {
            assertBadRequest(() -> this.adminClient.content()
                .getContents(null, null, null, "invalid_type", INCLUSION_INCLUDE));
        }

        @Test
        public void shouldAllowFilteringWithCustomIncluded() {
            // This is effectively the same as no filter -- we expect everything back
            List<String> expectedCids = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids);
        }

        @Test
        public void shouldAllowFilteringWithCustomExcluded() {
            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(pinfo -> !pinfo.custom())
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldAllowFilteringWithCustomExclusive() {
            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(ProductInfo::custom)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(null, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @Test
        public void shouldErrorWithInvalidCustomInclusion() {
            assertBadRequest(() -> this.adminClient.content()
                .getContents(null, null, null, INCLUSION_INCLUDE, "invalid_type"));
        }

        @Test
        public void shouldAllowQueryingWithMultipleFilters() {
            Random rand = new Random(24043);

            // Hand pick a content to ensure that we have *something* that comes out of the filter, should
            // our random selection process not have any intersection.
            ContentDTO selectedContent = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .flatMap(ProductInfo::content)
                .findAny()
                .get();

            List<String> ownerKeys = this.owners.stream()
                .filter(elem -> rand.nextBoolean())
                .map(OwnerDTO::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

            List<String> cids = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .sorted(Comparator.comparing(ContentDTO::getId))
                .filter(elem -> rand.nextBoolean())
                .map(ContentDTO::getId)
                .collect(Collectors.toCollection(ArrayList::new));

            List<String> labels = this.productMap.values()
                .stream()
                .flatMap(ProductInfo::content)
                .sorted(Comparator.comparing(ContentDTO::getId))
                .filter(elem -> rand.nextBoolean())
                .map(ContentDTO::getLabel)
                .collect(Collectors.toCollection(ArrayList::new));

            cids.add(selectedContent.getId());
            labels.add(selectedContent.getLabel());

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .filter(pinfo -> pinfo.activeOwners().stream().anyMatch(ownerKeys::contains))
                .flatMap(ProductInfo::content)
                .filter(content -> cids.contains(content.getId()))
                .filter(content -> labels.contains(content.getLabel()))
                .map(ContentDTO::getId)
                .toList();

            assertThat(expectedCids)
                .isNotEmpty();

            List<ContentDTO> output = this.adminClient.content()
                .getContents(ownerKeys, cids, labels, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
        public void shouldAllowQueryingInPages(int pageSize) {
            List<String> ownerKeys = this.owners.stream()
                .map(OwnerDTO::getKey)
                .toList();

            List<String> expectedCids = this.productMap.values()
                .stream()
                .filter(ProductInfo::custom)
                .flatMap(ProductInfo::content)
                .map(ContentDTO::getId)
                .toList();

            List<String> received = new ArrayList<>();

            int page = 0;
            while (true) {
                Response response = Request.from(this.adminClient)
                    .setPath(QUERY_PATH)
                    .addQueryParam("owner", ownerKeys)
                    .addQueryParam("page", String.valueOf(++page))
                    .addQueryParam("per_page", String.valueOf(pageSize))
                    .addQueryParam("active", "include")
                    .addQueryParam("custom", "exclusive")
                    .execute();

                assertEquals(200, response.getCode());

                List<ProductDTO> output = response.deserialize(new TypeReference<List<ProductDTO>>() {});
                assertNotNull(output);

                if (output.isEmpty()) {
                    break;
                }

                output.stream()
                    .map(ProductDTO::getId)
                    .sequential()
                    .forEach(received::add);
            }

            assertThat(received)
                .containsAll(expectedCids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedCids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("page", String.valueOf(page))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("per_page", String.valueOf(pageSize))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithAscendingOrderedOutput(String field) {
            List<String> ownerKeys = this.owners.stream()
                .map(OwnerDTO::getKey)
                .toList();

            Map<String, Comparator<ContentDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ContentDTO::getId),
                "name", Comparator.comparing(ContentDTO::getName),
                "uuid", Comparator.comparing(ContentDTO::getUuid));

            List<ContentDTO> content = this.adminClient.content()
                .getContents(ownerKeys, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedCids = content.stream()
                .sorted(comparatorMap.get(field))
                .map(ContentDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("owner", ownerKeys)
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "asc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ContentDTO> output = response.deserialize(new TypeReference<List<ContentDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyElementsOf(expectedCids); // this must be an ordered check
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithDescendingOrderedOutput(String field) {
            List<String> ownerKeys = this.owners.stream()
                .map(OwnerDTO::getKey)
                .toList();

            Map<String, Comparator<ContentDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ContentDTO::getId),
                "name", Comparator.comparing(ContentDTO::getName),
                "uuid", Comparator.comparing(ContentDTO::getUuid));

            List<ContentDTO> contents = this.adminClient.content()
                .getContents(ownerKeys, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedCids = contents.stream()
                .sorted(comparatorMap.get(field).reversed())
                .map(ContentDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("owner", ownerKeys)
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "desc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ContentDTO> output = response.deserialize(new TypeReference<List<ContentDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyElementsOf(expectedCids); // this must be an ordered check
        }

        @Test
        public void shouldFailWithInvalidOrderField() {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("sort_by", "invalid field")
                .execute();

            assertEquals(400, response.getCode());
        }

        @Test
        public void shouldFailWithInvalidOrderDirection() {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("order", "invalid order")
                .execute();

            assertEquals(400, response.getCode());
        }

        // These tests verify the definition of "active" is properly implemented, ensuring "active" is
        // defined as a product which is attached to a pool which has started and has not expired, or
        // attached to another active product (recursively).
        //
        // This definition is recursive in nature, so the effect is that it should consider any product
        // that is a descendant of a product attached to a non-future pool that hasn't yet expired.

        @Test
        public void shouldOnlySelectActiveProductsFromActivePools() {
            // "active" only considers pools which have started but have not yet expired -- that is:
            // (start time < now() < end time)
            OwnerDTO owner = this.createOwner("test_org");

            List<ProductDTO> products = new ArrayList<>();
            for (int i = 1; i <= 3; ++i) {
                ContentDTO contentA = Contents.random().id(String.format("p%d-ca", i));
                ContentDTO contentB = Contents.random().id(String.format("p%d-cb", i));

                contentA = this.adminClient.ownerContent().createContent(owner.getKey(), contentA);
                contentB = this.adminClient.ownerContent().createContent(owner.getKey(), contentB);

                ProductDTO product = Products.random()
                    .id("prod-" + i)
                    .name("product " + i)
                    .addProductContentItem(Contents.toProductContent(contentA, true))
                    .addProductContentItem(Contents.toProductContent(contentB, false));

                this.adminClient.ownerProducts().createProduct(owner.getKey(), product);
                products.add(product);
            }

            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Create three pools: expired, current (active), future
            PoolDTO pool1 = Pools.random(products.get(0))
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(now.minus(3, ChronoUnit.DAYS));
            PoolDTO pool2 = Pools.random(products.get(1))
                .startDate(now.minus(3, ChronoUnit.DAYS))
                .endDate(now.plus(3, ChronoUnit.DAYS));
            PoolDTO pool3 = Pools.random(products.get(2))
                .startDate(now.plus(3, ChronoUnit.DAYS))
                .endDate(now.plus(7, ChronoUnit.DAYS));

            this.adminClient.owners().createPool(owner.getKey(), pool1);
            this.adminClient.owners().createPool(owner.getKey(), pool2);
            this.adminClient.owners().createPool(owner.getKey(), pool3);

            // Active = exclusive should only find the active pool; future and expired pools should be omitted
            List<ContentDTO> output = this.adminClient.content()
                .getContents(List.of(owner.getKey()), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyInAnyOrder("p2-ca", "p2-cb");
        }

        @Test
        public void shouldAlsoIncludeDescendantProductsOfActiveProducts() {
            // "active" includes descendants of products attached to an active pool
            OwnerDTO owner = this.createOwner("test_org");

            List<ProductDTO> products = new ArrayList<>();
            for (int i = 0; i < 20; ++i) {
                ContentDTO contentA = Contents.random().id(String.format("p%d-ca", i));
                ContentDTO contentB = Contents.random().id(String.format("p%d-cb", i));

                contentA = this.adminClient.ownerContent().createContent(owner.getKey(), contentA);
                contentB = this.adminClient.ownerContent().createContent(owner.getKey(), contentB);

                ProductDTO product = new ProductDTO()
                    .id("p" + i)
                    .name("product " + i)
                    .addProductContentItem(Contents.toProductContent(contentA, true))
                    .addProductContentItem(Contents.toProductContent(contentB, false));

                // Impl note: We can't create the products quite yet
                products.add(product);
            }

            /*
            pool -> prod - p0
                        derived - p1
                            provided - p2
                            provided - p3
                                provided - p4
                        provided - p5
                        provided - p6

            pool -> prod - p7
                        derived - p8*
                        provided - p9

            pool -> prod - p8*
                        provided - p10
                            provided - p11
                        provided - p12
                            provided - p13

                    prod - p14
                        derived - p15
                            provided - p16

                    prod - p17
                        provided - p18

            pool -> prod - p19
                    prod - p20
            */

            products.get(0).setDerivedProduct(products.get(1));
            products.get(0).addProvidedProductsItem(products.get(5));
            products.get(0).addProvidedProductsItem(products.get(6));

            products.get(1).addProvidedProductsItem(products.get(2));
            products.get(1).addProvidedProductsItem(products.get(3));

            products.get(3).addProvidedProductsItem(products.get(4));

            products.get(7).setDerivedProduct(products.get(8));
            products.get(7).addProvidedProductsItem(products.get(9));

            products.get(8).addProvidedProductsItem(products.get(10));
            products.get(8).addProvidedProductsItem(products.get(12));

            products.get(10).addProvidedProductsItem(products.get(11));

            products.get(12).addProvidedProductsItem(products.get(13));

            products.get(14).setDerivedProduct(products.get(15));

            products.get(15).setDerivedProduct(products.get(16));

            products.get(17).setDerivedProduct(products.get(18));

            // persist the products in reverse order so we don't hit any linkage errors
            for (int i = products.size() - 1; i >= 0; --i) {
                this.adminClient.ownerProducts().createProduct(owner.getKey(), products.get(i));
            }

            // create some pools to link to our product tree
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(0)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(7)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(8)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(19)));

            List<String> expectedCids = List.of("p0-ca", "p0-cb", "p1-ca", "p1-cb", "p2-ca", "p2-cb", "p3-ca",
                "p3-cb", "p4-ca", "p4-cb", "p5-ca", "p5-cb", "p6-ca", "p6-cb", "p7-ca", "p7-cb", "p8-ca",
                "p8-cb", "p9-ca", "p9-cb", "p10-ca", "p10-cb", "p11-ca", "p11-cb", "p12-ca", "p12-cb",
                "p13-ca", "p13-cb", "p19-ca", "p19-cb");

            List<ContentDTO> output = this.adminClient.content()
                .getContents(List.of(owner.getKey()), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ContentDTO::getId)
                .containsExactlyInAnyOrderElementsOf(expectedCids);
        }
    }

}
