/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ExportResultDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.StatusDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyWithCapability;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

@SpecTest
@OnlyInHosted
public class ExportV2SpecTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("checkstyle:methodlength")
    public void shouldCreateExportAsync() throws ApiException {

        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners()
            .createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        adminClient.hosted()
            .createOwner(owner);
        ProductDTO prod = this.createActiveProductGraph(adminClient);
        ProductDTO derivedProduct = prod.getDerivedProduct();
        ContentDTO content = prod.getProductContent().iterator().next().getContent();
        // active subscription
        adminClient.hosted()
            .createSubscription(Subscriptions.random(owner, prod)
                .contractNumber("active-contract")
                .accountNumber("active-account"));
        ProductDTO futureProduct = this.createProductGraph(adminClient);
        ContentDTO futureContent = adminClient.hosted().createContent(Contents.random().arches("aarch64"));
        adminClient.hosted().addContentToProduct(futureProduct.getId(), futureContent.getId(), false);
        ProductDTO expiredProduct = this.createProductGraph(adminClient);
        // future subscription
        OffsetDateTime futureStart = OffsetDateTime.now()
            .plusYears(1);
        adminClient.hosted()
            .createSubscription(Subscriptions.random(owner, futureProduct)
                .contractNumber("future-contract")
                .accountNumber("future-account")
                .startDate(futureStart)
                .endDate(futureStart.plusYears(1)));
        OffsetDateTime expiredEnd = OffsetDateTime.now().minusYears(1);
        SubscriptionDTO expiredSubscription = adminClient.hosted()
            .createSubscription(Subscriptions.random(owner, expiredProduct)
                .contractNumber("expired-contract")
                .accountNumber("expired-account")
                .startDate(expiredEnd.minusYears(1))
                .endDate(expiredEnd));

        assertThatJob(adminClient.owners()
            .refreshPools(ownerKey, false))
            .terminates(adminClient)
            .isFinished();
        List<PoolDTO> pools = adminClient.owners()
            .listOwnerPools(ownerKey, true, false, null);

        assertEquals(2, pools.size());

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin)
                .putFactsItem("distributor_version", "sam-1.3")
                .capabilities(null));
        pools.forEach(pool -> adminClient.consumers()
            .bindPool(consumer.getUuid(), pool.getId(), 1));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO expectedProduct = adminClient.ownerProducts().getProductById(ownerKey, prod.getId());
        ProductDTO expectedFutureProduct = adminClient.ownerProducts().getProductById(ownerKey, futureProduct.getId());
        ConsumerDTO expectedConsumer = adminClient.consumers().getConsumer(consumer.getUuid());
        // Binding changes pool timestamps, so read the expected values after attaching.
        List<PoolDTO> expectedPools = adminClient.owners().listOwnerPools(ownerKey, true, false, null);
        ProductDTO namespacedProduct = this.createNamespacedProductGraph(adminClient, ownerKey);
        PoolDTO namespacedPool = adminClient.owners()
            .createPool(ownerKey, Pools.randomUpstream(namespacedProduct));
        assertTrue(adminClient.owners().listOwnerPools(ownerKey, true, false, null).stream()
            .anyMatch(pool -> namespacedPool.getId().equals(pool.getId())));
        StatusDTO status = adminClient.status().status();
        OffsetDateTime exportStart = OffsetDateTime.now();
        File export = this.createExportAsync(consumerClient, consumer.getUuid());
        OffsetDateTime exportEnd = OffsetDateTime.now();

        try (
            FileSystem archive = FileSystems.newFileSystem(
                URI.create("jar:" + export.toURI()),
                Map.of())) {
            JsonNode meta = this.readExportFile(archive, "meta.json");
            this.assertMeta(meta, status, exportStart, exportEnd, consumer.getUuid());
            Set<String> productFiles = this.assertProductFiles(archive, expectedProduct);
            productFiles.addAll(this.assertProductFiles(archive, expectedFutureProduct));
            this.assertDirectoryFiles(archive, "products", productFiles);
            this.assertProductFilesMissing(archive, expiredProduct);
            this.assertProductFilesMissing(archive, namespacedProduct);
            assertFalse(Files.exists(archive.getPath("subscriptions/" + namespacedPool.getSubscriptionId() + ".json")));
            JsonNode exportedRoot = this.readExportFile(archive, "products/" + prod.getId() + ".json");
            JsonNode exportedDerived = this.readExportFile(archive, "products/" + derivedProduct.getId() + ".json");
            this.assertField(exportedRoot.get("productContent").get(content.getId()), "enabled", true);
            this.assertField(exportedDerived.get("productContent").get(content.getId()), "enabled", false);
            assertFalse(Files.exists(archive.getPath("subscriptions/" + expiredSubscription.getId() + ".json")));
            this.assertDirectoryFiles(archive, "subscriptions", expectedPools.stream()
                .map(pool -> pool.getSubscriptionId() + ".json")
                .collect(Collectors.toSet()));
            this.assertConsumer(this.readExportFile(archive, "upstream_consumer/consumer.json"),
                expectedConsumer, meta.get("webAppPrefix"));
            for (PoolDTO pool : expectedPools) {
                this.assertSubscription(archive, pool);
            }

            Path certificateDirectory = archive.getPath("content_access_certificates");
            try (java.util.stream.Stream<Path> certificates = Files.list(certificateDirectory)) {
                Path certificatePath = certificates.findFirst().orElseThrow();
                String certificate = Files.readString(certificatePath);
                assertTrue(certificate.contains("-----BEGIN CERTIFICATE-----"));
                assertTrue(certificate.contains("-----BEGIN PRIVATE KEY-----"));
                String start = "-----BEGIN ENTITLEMENT DATA-----";
                String encoded = certificate.substring(certificate.indexOf(start) + start.length(),
                    certificate.indexOf("-----END ENTITLEMENT DATA-----"));
                byte[] compressed = Base64.getMimeDecoder().decode(encoded);
                try (InflaterInputStream data = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                    JsonNode payload = this.mapper.readTree(new String(data.readAllBytes(), StandardCharsets.UTF_8));
                    this.assertCertificateContent(payload, expectedProduct, expectedFutureProduct);
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertCertificateContent(JsonNode payload, ProductDTO... products) {
        Map<String, Boolean> expected = new HashMap<>();
        for (ProductDTO product : products) {
            this.collectCertificateContent(product, expected);
        }

        JsonNode repositories = payload.get("products").get(0).get("content");
        assertEquals(expected.size(), repositories.size());
        Map<String, Boolean> actual = new HashMap<>();
        for (JsonNode repository : repositories) {
            // Enabled content omits the flag; disabled content explicitly sets it to false.
            JsonNode enabled = repository.get("enabled");
            if (enabled != null) {
                assertTrue(enabled.isBoolean());
            }
            actual.put(repository.get("id").asString(), enabled == null || enabled.asBoolean());
        }
        assertEquals(expected, actual);
    }

    private void collectCertificateContent(ProductDTO product, Map<String, Boolean> content) {
        for (ProductContentDTO entry : product.getProductContent()) {
            content.merge(entry.getContent().getId(), entry.getEnabled(), Boolean::logicalOr);
        }
        if (product.getDerivedProduct() != null) {
            this.collectCertificateContent(product.getDerivedProduct(), content);
        }
        for (ProductDTO provided : product.getProvidedProducts()) {
            this.collectCertificateContent(provided, content);
        }
    }

    private ProductDTO createActiveProductGraph(ApiClient adminClient) {
        ProductDTO providedProduct = adminClient.hosted()
            .createProduct(Products.randomEng());
        ProductDTO derivedProduct = adminClient.hosted()
            .createProduct(Products.random());
        ProductDTO prod = adminClient.hosted()
            .createProduct(Products.random()
                .addAttributesItem(ProductAttributes.Arch.withValue("x86_64"))
                .addAttributesItem(ProductAttributes.Version.withValue("9.4"))
                .providedProducts(Set.of(providedProduct))
                .derivedProduct(derivedProduct)
                .branding(Set.of(Branding.build("Exported product", "OS", providedProduct))));
        ContentDTO content = adminClient.hosted()
            .createContent(Contents.random()
                .requiredTags("tag-one,tag-two")
                .releaseVer("9.4")
                .gpgUrl("https://example.com/export/RPM-GPG-KEY")
                .arches("x86_64,aarch64"));
        ContentDTO derivedContent = adminClient.hosted()
            .createContent(Contents.random());
        adminClient.hosted()
            .addContentToProduct(prod.getId(), content.getId(), true);
        adminClient.hosted()
            .addContentToProduct(derivedProduct.getId(), derivedContent.getId(), false);
        adminClient.hosted()
            .addContentToProduct(derivedProduct.getId(), content.getId(), false);
        return adminClient.hosted().getProduct(prod.getId());
    }

    private ProductDTO createNamespacedProductGraph(ApiClient adminClient, String ownerKey) {
        ProductDTO provided = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.randomEng());
        ProductDTO derived = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random());
        ProductDTO root = adminClient.ownerProducts()
            .createProduct(ownerKey, Products.random()
                .providedProducts(Set.of(provided))
                .derivedProduct(derived));
        for (ProductDTO product : List.of(root, derived, provided)) {
            ContentDTO content = adminClient.ownerContent()
                .createContent(ownerKey, Contents.random());
            adminClient.ownerProducts()
                .addContentToProduct(ownerKey, product.getId(), content.getId(), true);
        }
        return adminClient.ownerProducts().getProductById(ownerKey, root.getId());
    }

    private ProductDTO createProductGraph(ApiClient adminClient) {
        ProductDTO provided = adminClient.hosted()
            .createProduct(Products.randomEng());
        ProductDTO derived = adminClient.hosted()
            .createProduct(Products.random());
        ProductDTO root = adminClient.hosted()
            .createProduct(Products.random()
                .providedProducts(Set.of(provided))
                .derivedProduct(derived));
        for (ProductDTO product : List.of(root, derived, provided)) {
            ContentDTO content = adminClient.hosted()
                .createContent(Contents.random());
            adminClient.hosted()
                .addContentToProduct(product.getId(), content.getId(), true);
        }
        return adminClient.hosted().getProduct(root.getId());
    }

    private void assertProductFilesMissing(FileSystem archive, ProductDTO product) {
        String path = "products/" + product.getId() + ".json";
        assertFalse(Files.exists(archive.getPath(path)), () -> "Unexpected archive entry: " + path);
        if (product.getDerivedProduct() != null) {
            this.assertProductFilesMissing(archive, product.getDerivedProduct());
        }
        for (ProductDTO provided : product.getProvidedProducts()) {
            this.assertProductFilesMissing(archive, provided);
        }
    }

    private void assertSubscription(FileSystem archive, PoolDTO expected) throws IOException {
        JsonNode actual = this.readExportFile(archive, "subscriptions/" + expected.getSubscriptionId() + ".json");
        this.assertTimestampValues(actual, expected.getCreated(), expected.getUpdated());
        assertEquals(expected.getSubscriptionId(), actual.get("subscriptionId").asString());
        assertEquals(expected.getProductId(), actual.get("productId").asString());
        assertEquals(expected.getContractNumber(), actual.get("contractNumber").asString());
        assertEquals(expected.getAccountNumber(), actual.get("accountNumber").asString());
        this.assertDate(actual.get("startDate"), expected.getStartDate());
        this.assertDate(actual.get("endDate"), expected.getEndDate());
    }

    private void assertMeta(JsonNode actual, StatusDTO status, OffsetDateTime start, OffsetDateTime end,
        String consumerUuid) {
        this.assertField(actual, "manifestVersion", "2");
        this.assertField(actual, "version", status.getVersion() + "-" + status.getRelease());
        this.assertField(actual, "principalName", consumerUuid);
        // V2 exports do not select a CDN.
        this.assertField(actual, "cdnLabel", null);
        assertTrue(actual.hasNonNull("webAppPrefix"));
        assertFalse(actual.get("webAppPrefix")
            .asString()
            .isBlank());
        Date created = this.mapper.convertValue(actual.get("created"), Date.class);
        assertNotNull(created);
        assertTrue(created.getTime() >= start.toInstant().toEpochMilli());
        assertTrue(created.getTime() <= end.toInstant().toEpochMilli());
    }

    private Set<String> assertProductFiles(FileSystem archive, ProductDTO expected) throws IOException {
        Set<String> productFiles = new HashSet<>();
        productFiles.add(expected.getId() + ".json");
        this.assertProduct(this.readExportFile(archive, "products/" + expected.getId() + ".json"), expected);
        if (expected.getDerivedProduct() != null) {
            productFiles.addAll(this.assertProductFiles(archive, expected.getDerivedProduct()));
        }
        for (ProductDTO provided : expected.getProvidedProducts()) {
            productFiles.addAll(this.assertProductFiles(archive, provided));
        }
        return productFiles;
    }

    private void assertDirectoryFiles(FileSystem archive, String directory, Set<String> expected) throws IOException {
        try (Stream<Path> entries = Files.list(archive.getPath(directory))) {
            Set<String> actual = entries.map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
            assertEquals(expected, actual, directory);
        }
    }

    private void assertProduct(JsonNode actual, ProductDTO expected) {
        assertNotNull(actual);
        this.assertTimestampValues(actual, expected.getCreated(), expected.getUpdated());
        this.assertField(actual, "id", expected.getId());
        this.assertField(actual, "name", expected.getName());
        this.assertField(actual, "attributes", expected.getAttributes().stream()
            .collect(Collectors.toMap(attribute -> attribute.getName(), attribute -> attribute.getValue())));

        this.assertField(actual, "derivedProduct",
            expected.getDerivedProduct() == null ? null : expected.getDerivedProduct().getId());
        JsonNode providedProducts = actual.get("providedProducts");
        assertNotNull(providedProducts);
        assertTrue(providedProducts.isArray());
        assertEquals(expected.getProvidedProducts().size(), providedProducts.size());
        Set<String> actualProvided = new HashSet<>();
        for (JsonNode provided : providedProducts) {
            assertTrue(provided.isString());
            actualProvided.add(provided.asString());
        }
        Set<String> expectedProvided = expected.getProvidedProducts().stream()
            .map(ProductDTO::getId)
            .collect(Collectors.toSet());
        assertEquals(expectedProvided, actualProvided);

        JsonNode content = actual.get("productContent");
        assertNotNull(content);
        assertEquals(expected.getProductContent().size(), content.size());
        for (ProductContentDTO entry : expected.getProductContent()) {
            JsonNode actualContent = content.get(entry.getContent().getId());
            this.assertContent(actualContent, entry.getContent());
            this.assertField(actualContent, "enabled", entry.getEnabled());
        }

        JsonNode brandings = actual.get("brandings");
        assertNotNull(brandings);
        assertEquals(expected.getBranding().size(), brandings.size());
        Map<String, BrandingDTO> expectedBranding = expected.getBranding().stream()
            .collect(Collectors.toMap(BrandingDTO::getProductId, branding -> branding));
        for (JsonNode branding : brandings) {
            BrandingDTO expectedBrand = expectedBranding.get(branding.get("productId").asString());
            assertNotNull(expectedBrand);
            assertTrue(branding.hasNonNull("id"));
            assertFalse(branding.get("id")
                .asString()
                .isBlank());
            this.assertField(branding, "productId", expectedBrand.getProductId());
            this.assertField(branding, "name", expectedBrand.getName());
            this.assertField(branding, "type", expectedBrand.getType());
            this.assertTimestampValues(branding, expectedBrand.getCreated(), expectedBrand.getUpdated());
        }
    }

    private void assertContent(JsonNode actual, ContentDTO expected) {
        assertNotNull(actual);
        this.assertTimestampValues(actual, expected.getCreated(), expected.getUpdated());
        this.assertField(actual, "id", expected.getId());
        this.assertField(actual, "type", expected.getType());
        this.assertField(actual, "label", expected.getLabel());
        this.assertField(actual, "name", expected.getName());
        this.assertField(actual, "vendor", expected.getVendor());
        this.assertField(actual, "contentUrl", expected.getContentUrl());
        this.assertField(actual, "requiredTags", expected.getRequiredTags());
        this.assertField(actual, "releaseVer", expected.getReleaseVer());
        this.assertField(actual, "gpgUrl", expected.getGpgUrl());
        this.assertField(actual, "arches", expected.getArches());
    }

    private void assertConsumer(JsonNode actual, ConsumerDTO expected, JsonNode webAppPrefix) {
        this.assertTimestampValues(actual, expected.getCreated(), expected.getUpdated());
        this.assertField(actual, "uuid", expected.getUuid());
        this.assertField(actual, "name", expected.getName());
        this.assertField(actual, "ownerId", expected.getOwner().getId());
        this.assertField(actual, "urlWeb", webAppPrefix.asString());
        assertTrue(actual.hasNonNull("urlApi"));
        assertFalse(actual.get("urlApi")
            .asString()
            .isBlank());
        JsonNode type = actual.get("type");
        assertNotNull(type);
        this.assertField(type, "id", expected.getType().getId());
        this.assertField(type, "name", expected.getType().getLabel());
        // Built-in consumer types may not have source timestamps.
        this.assertTimestampValues(type, expected.getType().getCreated(), expected.getType().getUpdated());
    }

    private void assertTimestampValues(JsonNode actual, OffsetDateTime created, OffsetDateTime updated) {
        assertAll("Timestamps for " + actual,
            () -> this.assertDate(actual.get("created"), created),
            () -> this.assertDate(actual.get("updated"), updated));
    }

    private void assertDate(JsonNode actual, OffsetDateTime expected) {
        assertNotNull(actual);
        if (expected == null) {
            assertTrue(actual.isNull());
        }
        else {
            Date date = this.mapper.convertValue(actual, Date.class);
            assertNotNull(date);
            // The REST API exposes seconds, while manifest timestamps retain milliseconds.
            assertEquals(expected.toEpochSecond(), date.toInstant().getEpochSecond());
        }
    }

    private void assertField(JsonNode actual, String field, Object expected) {
        JsonNode expectedValue = this.mapper.valueToTree(expected);
        assertEquals(expectedValue, actual.get(field), field);
    }

    @Test
    @OnlyInHosted
    @OnlyWithCapability("cloud_registration")
    public void shouldNotExportAnonymousManifestWithAnonymousToken() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners()
            .createOwner(Owners.random());
        adminClient.hosted()
            .createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        ProductDTO product1 = adminClient.hosted()
            .createProduct(Products.random());
        ContentDTO content1 = adminClient.hosted()
            .createContent(Contents.random());
        ContentDTO content2 = adminClient.hosted()
            .createContent(Contents.random());
        adminClient.hosted()
            .addContentToProduct(product1.getId(), content1.getId(), true);
        adminClient.hosted()
            .addContentToProduct(product1.getId(), content2.getId(), true);

        ProductDTO product2 = adminClient.hosted()
            .createProduct(Products.random());
        ContentDTO content3 = adminClient.hosted()
            .createContent(Contents.random());
        adminClient.hosted()
            .addContentToProduct(product2.getId(), content3.getId(), true);

        adminClient.hosted()
            .associateProductIdsToCloudOffer(offerId, List.of(product1.getId(), product2.getId()));
        adminClient.hosted()
            .associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = ApiClients.noAuth()
            .cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        Response response = Request.from(ApiClients.bearerToken(result.getToken()))
            .setPath("/v2/consumers/{consumer_uuid}/export")
            .setPathParam("consumer_uuid", result.getAnonymousConsumerUuid())
            .setMethod(Request.Method.POST)
            .addHeader("accept", "application/json")
            .execute();

        assertEquals(404, response.getCode());
    }

    private JsonNode readExportFile(FileSystem archive, String path) throws IOException {
        Path contentPath = archive.getPath(path);
        assertTrue(Files.exists(contentPath), () -> "Missing archive entry: " + path);
        try (BufferedReader reader = Files.newBufferedReader(contentPath)) {
            return this.mapper.readTree(reader);
        }
    }

    private File createExportAsync(ApiClient apiClient, String consumerUuid)
        throws ApiException {
        AsyncJobStatusDTO jobStatus = apiClient.consumers()
            .exportDataAsyncV2(consumerUuid);
        jobStatus = apiClient.jobs()
            .waitForJob(jobStatus.getId());
        if (!jobStatus.getState()
            .equals("FINISHED")) {
            throw new ApiException("Unable to create export.");
        }

        ExportResultDTO result = ApiClient.MAPPER
            .convertValue(jobStatus.getResultData(), ExportResultDTO.class);
        assertNotNull(result);
        assertNotNull(result.getExportId());

        File export = apiClient.consumers()
            .downloadExistingExport(consumerUuid, result.getExportId());
        export.deleteOnExit();

        return export;
    }
}
