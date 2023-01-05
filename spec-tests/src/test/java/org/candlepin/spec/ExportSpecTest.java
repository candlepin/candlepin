/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ExportResultDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.CdnApi;
import org.candlepin.resource.client.v1.ConsumerTypeApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.RolesApi;
import org.candlepin.resource.client.v1.RulesApi;
import org.candlepin.resource.client.v1.UsersApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.Cdns;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Roles;
import org.candlepin.spec.bootstrap.data.builder.Users;
import org.candlepin.spec.bootstrap.data.util.ExportUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.codec.binary.Base64;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SpecTest
class ExportSpecTest {
    private static final String RECORD_CLEANER_JOB_KEY = "ImportRecordCleanerJob";
    private static final String UNDO_IMPORTS_JOB_KEY = "UndoImportsJob";
    // The following are directory location paths within a manifest
    private static final String EXPORT_PATH = "export/";
    private static final String PRODUCTS_PATH = "export/products/";
    private static final String ENTITILEMENTS_PATH = "export/entitlements/";
    private static final String ENTITILEMENT_CERTIFICATES_PATH = "export/entitlement_certificates/";
    private static final String DISTRIBUTOR_VERSION_PATH = "export/distributor_version/";
    private static final String CONSUMER_TYPE_PATH = "export/consumer_types/";
    private static final String CONTENT_ACCESS_CERTS_PATH = "export/content_access_certificates/";
    private static final String CDN_PATH = "export/content_delivery_network/";
    private static final String RULES_PATH = "export/rules2/";

    @Test
    void shouldAllowManifestCreationWithReadOnlyUser() throws Exception {
        ApiClient client = ApiClients.admin();
        OwnerDTO owner = client.owners().createOwner(Owners.random());
        ConsumerDTO readOnlyConsumer = createReadOnlyConsumer(client, owner);

        File readOnlyManifest = createExport(client, readOnlyConsumer.getUuid(), null);
        ZipFile readOnlyExport = ExportUtil.getExportArchive(readOnlyManifest);
        String path = EXPORT_PATH + "consumer.json";

        ConsumerDTO actualConsumer = ExportUtil
            .deserializeJsonFile(readOnlyExport, path, ConsumerDTO.class);
        assertEquals(readOnlyConsumer.getUuid(), actualConsumer.getUuid());
        assertEquals(readOnlyConsumer.getName(), actualConsumer.getName());

        assertEquals(owner.getId(), actualConsumer.getOwner().getId());
        assertEquals(owner.getKey(), actualConsumer.getOwner().getKey());
        assertEquals(owner.getDisplayName(), actualConsumer.getOwner().getDisplayName());
    }

    @Test
    public void shouldShowContentAccessModeChangeInExportForTheDistributorConsumer() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), content.getId(), true);
        adminClient.owners().createPool(ownerKey, Pools.random(prod));

        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        owner.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);
        adminClient.owners().updateOwner(ownerKey, owner);
        consumer.contentAccessMode(Owners.ENTITLEMENT_ACCESS_MODE);
        consumer.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        File manifest = createExport(consumerClient, consumer.getUuid(), null);
        ZipFile export = ExportUtil.getExportArchive(manifest);
        String path = EXPORT_PATH + "consumer.json";

        ConsumerDTO actualConsumer = ExportUtil
            .deserializeJsonFile(export, path, ConsumerDTO.class);

        assertThat(actualConsumer)
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, ConsumerDTO::getContentAccessMode);
    }

    @Test
    public void shouldImportContentAccessCertsForAConsumerBelongingToOwnerInSCAMode() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ProductDTO modifiedProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.random());
        ProductDTO prod = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ContentDTO cont = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        adminClient.ownerProducts().addContent(ownerKey, prod.getId(), cont.getId(), true);
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        File manifest = createCertExport(consumerClient, consumer.getUuid());
        ZipFile export = ExportUtil.getExportArchive(manifest);

        // Check if content access certs are present in exported zip file.
        List<ZipEntry> caCerts = export.stream()
            .filter(entry -> entry.getName().startsWith(CONTENT_ACCESS_CERTS_PATH))
            .filter(entry -> entry.getName().lastIndexOf('/') == CONTENT_ACCESS_CERTS_PATH.length() - 1)
            .collect(Collectors.toList());

        assertThat(caCerts).singleElement();

        // Check if entitlement certs are present in exported zip file.
        List<ZipEntry> entitlementCerts = export.stream()
            .filter(entry -> entry.getName().startsWith(ENTITILEMENT_CERTIFICATES_PATH))
            .filter(entry -> entry.getName().lastIndexOf('/') == ENTITILEMENT_CERTIFICATES_PATH.length() - 1)
            .collect(Collectors.toList());

        assertThat(entitlementCerts).singleElement();
    }

    @Nested
    @DisplayName("Standard Export Tests")
    class StandardExporter {
        private ApiClient client;
        private OwnerClient ownerApi;
        private OwnerContentApi ownerContentApi;
        private OwnerProductApi ownerProductApi;
        private CdnApi cdnApi;
        private ConsumerClient consumerApi;
        private ConsumerTypeApi consumerTypeApi;
        private RulesApi rulesApi;
        private RolesApi rolesApi;
        private UsersApi usersApi;
        private JobsClient jobsApi;

        private OwnerDTO owner;
        private ConsumerDTO consumer;
        private CdnDTO cdn;
        private ZipFile export;
        private File manifest;
        private Map<String, ProductDTO> productIdToProduct = new HashMap<>();

        @BeforeEach
        void beforeEach() throws Exception {
            client = ApiClients.admin();
            ownerApi = client.owners();
            ownerContentApi = client.ownerContent();
            ownerProductApi = client.ownerProducts();
            cdnApi = client.cdns();
            consumerApi = client.consumers();
            consumerTypeApi = client.consumerTypes();
            rulesApi = client.rules();
            rolesApi = client.roles();
            usersApi = client.users();
            jobsApi = client.jobs();

            initializeData();

            manifest = createExport(client, consumer.getUuid(), cdn);
            export = ExportUtil.getExportArchive(manifest);
        }

        @AfterEach
        void afterEach() throws Exception {
            if (export != null) {
                export.close();
            }
        }

        @Test
        void shouldExportConsumerTypes() throws Exception {
            List<ConsumerTypeDTO> expectedConsumerTypes = consumerTypeApi.getConsumerTypes();
            for (ConsumerTypeDTO expected : expectedConsumerTypes) {
                String path = CONSUMER_TYPE_PATH + expected.getLabel() + ".json";
                ConsumerTypeDTO actual = ExportUtil.deserializeJsonFile(export, path, ConsumerTypeDTO.class);
                assertNotNull(actual);
                assertEquals(expected.getId(), actual.getId());
                assertEquals(expected.getLabel(), actual.getLabel());
            }
        }

        @Test
        void shouldExportConsumers() throws Exception {
            String path = EXPORT_PATH + "consumer.json";
            ConsumerDTO actual = ExportUtil
                .deserializeJsonFile(export, path, ConsumerDTO.class);
            assertNotNull(actual);
            assertEquals(consumer.getUuid(), actual.getUuid());
            assertEquals(consumer.getName(), actual.getName());
        }

        @Test
        void shouldExportCdnUrl() throws Exception {
            ZipEntry metaFile = export.getEntry(EXPORT_PATH + "meta.json");
            assertNotNull(metaFile);
            try (InputStream istream = export.getInputStream(metaFile)) {
                JsonNode root = ApiClient.MAPPER.readTree(istream);
                String actualCdnLabel = root.get("cdnLabel").asText();
                assertEquals(cdn.getLabel(), actualCdnLabel);
            }
        }

        @Test
        void shouldNotIncludeConsumerJsonInEntitlements() throws Exception {
            int target = ENTITILEMENTS_PATH.length() - 1;
            long count = export.stream()
                .filter(entry -> entry.getName().startsWith(ENTITILEMENTS_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .peek(entry -> {
                    try (InputStream istream = export.getInputStream(entry)) {
                        JsonNode root = ApiClient.MAPPER.readTree(istream);
                        assertNull(root.get("consumer"));
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();
            assertEquals(6, count);
        }

        @Test
        void shouldExportCandlepinBackwardCompatibleProductDefinitions() throws Exception {
            int target = ENTITILEMENTS_PATH.length() - 1;
            long entitlementCount = export.stream()
                .filter(entry -> entry.getName().startsWith(ENTITILEMENTS_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .peek(entry -> {
                    try (InputStream istream = export.getInputStream(entry)) {
                        JsonNode root = ApiClient.MAPPER.readTree(istream);
                        JsonNode pool = root.get("pool");
                        Iterator<JsonNode> providedProducts = pool.get("providedProducts").iterator();
                        while (providedProducts.hasNext()) {
                            JsonNode providedProduct = providedProducts.next();
                            assertNotNull(providedProduct.get("productId"));
                            assertNotNull(providedProduct.get("productName"));
                        }

                        Iterator<JsonNode> derivedProvidedProducts = pool.get("derivedProvidedProducts")
                            .iterator();
                        while (derivedProvidedProducts.hasNext()) {
                            JsonNode derivedProvidedProduct = derivedProvidedProducts.next();
                            assertNotNull(derivedProvidedProduct.get("productId"));
                            assertNotNull(derivedProvidedProduct.get("productName"));
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();

            assertEquals(6, entitlementCount);
        }

        @Test
        void shouldExportEntitlementCertificates() throws Exception {
            int target = ENTITILEMENT_CERTIFICATES_PATH.length() - 1;
            export.stream()
                .filter(entry -> entry.getName().startsWith(ENTITILEMENT_CERTIFICATES_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .forEach(entry -> {
                    try (InputStream istream = export.getInputStream(entry)) {
                        String cert = new String(istream.readAllBytes());
                        assertEquals("-----BEGIN CERTIFICATE-----", cert.substring(0, 27));
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        @Test
        @OnlyInStandalone
        void shouldPurgeImportRecords() throws Exception {
            try {
                OwnerDTO importOwner = ownerApi.createOwner(Owners.random());
                int expectedRecordCount = 11;
                for (int i = 0; i < expectedRecordCount; i++) {
                    List<String> force = List.of("SIGNATURE_CONFLICT", "MANIFEST_SAME");
                    AsyncJobStatusDTO status = client.owners()
                        .importManifestAsync(importOwner.getKey(), force, manifest);
                    status = client.jobs().waitForJob(status.getId());
                    assertEquals("FINISHED", status.getState());
                }

                List<ImportRecordDTO> records = ownerApi.getImports(importOwner.getKey());
                assertEquals(expectedRecordCount, records.size());

                AsyncJobStatusDTO cleanerJob = jobsApi.scheduleJob(RECORD_CLEANER_JOB_KEY);
                cleanerJob = jobsApi.waitForJob(cleanerJob.getId());
                assertEquals("FINISHED", cleanerJob.getState());

                records = ownerApi.getImports(importOwner.getKey());
                assertEquals(10, records.size());
            }
            finally {
                // Cleanup rules that have been created.
                rulesApi.deleteRules();
            }
        }

        @Test
        void shouldExportRegeneratedEntitlementCertificates() throws Exception {
            int target = ENTITILEMENT_CERTIFICATES_PATH.length() - 1;
            List<ZipEntry> entitlementCerts = export.stream()
                .filter(entry -> entry.getName().startsWith(ENTITILEMENT_CERTIFICATES_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .collect(Collectors.toList());

            // Regenerate some entitlement certificates, and generate a new manifest:
            String consumerUuid = consumer.getUuid();
            consumerApi.regenerateEntitlementCertificates(consumerUuid, null, true);
            File newManifest = createExport(client, consumerUuid, cdn);
            ZipFile newExport = ExportUtil.getExportArchive(newManifest);
            long newEntitlementsSize = newExport.stream()
                .filter(entry -> entry.getName().startsWith(ENTITILEMENT_CERTIFICATES_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .peek(entry -> {
                    // Cert filenames should be completely different now
                    assertFalse(entitlementCerts.contains(entry));
                })
                .count();

            assertEquals(entitlementCerts.size(), newEntitlementsSize);
        }

        @Test
        void shouldExportRules() throws Exception {
            ZipEntry rulesFile = export.getEntry(RULES_PATH + "rules.js");
            assertNotNull(rulesFile);
            String expectedRules = rulesApi.getRules();
            Base64 base64 = new Base64();
            expectedRules = new String(base64.decode(expectedRules.getBytes()));

            try (InputStream istream = export.getInputStream(rulesFile)) {
                String actualRules = new String(istream.readAllBytes());
                assertEquals(expectedRules, actualRules);
            }
        }

        @Test
        void shouldExportProducts() throws Exception {
            int target = PRODUCTS_PATH.length() - 1;
            long productCount = export.stream()
                .filter(entry -> entry.getName().startsWith(PRODUCTS_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .filter(entry -> entry.getName().endsWith(".json"))
                .peek(entry -> {
                    try {
                        String name = new File(entry.getName()).getName().replace(".json", "");
                        ProductDTO expected = productIdToProduct.get(name);
                        assertNotNull(expected);
                        ProductDTO actual = ExportUtil
                            .deserializeJsonFile(export, entry.getName(), ProductDTO.class);
                        assertNotNull(actual);
                        assertEquals(expected.getId(), actual.getId());
                        assertEquals(expected.getName(), actual.getName());
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();

            assertEquals(productIdToProduct.size(), productCount);
        }

        @Test
        void shouldExportProductsAsync() throws Exception {
            manifest = createExportAsync(client, consumer.getUuid(), cdn);
            export = ExportUtil.getExportArchive(manifest);

            int target = PRODUCTS_PATH.length() - 1;
            long productCount = export.stream()
                .filter(entry -> entry.getName().startsWith(PRODUCTS_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .filter(entry -> entry.getName().endsWith(".json"))
                .peek(entry -> {
                    try {
                        String name = new File(entry.getName()).getName().replace(".json", "");
                        ProductDTO expected = productIdToProduct.get(name);
                        assertNotNull(expected);
                        ProductDTO actual = ExportUtil
                            .deserializeJsonFile(export, entry.getName(), ProductDTO.class);
                        assertNotNull(actual);
                        assertEquals(expected.getId(), actual.getId());
                        assertEquals(expected.getName(), actual.getName());
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();

            assertEquals(productIdToProduct.size(), productCount);
        }

        @Test
        @OnlyInStandalone
        void shouldExportProductsAndCerts() throws Exception {
            int target = PRODUCTS_PATH.length() - 1;
            List<String> products = new ArrayList<>();
            List<String> productsWithCerts = new ArrayList<>();
            List<String> certs = new ArrayList<>();
            export.stream()
                .filter(entry -> entry.getName().startsWith(PRODUCTS_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .forEach(entry -> {
                    if (entry.getName().endsWith(".json")) {
                        products.add(entry.getName());
                        String fileName = new File(entry.getName()).getName().replace(".json", "");
                        ProductDTO product = productIdToProduct.get(fileName);
                        assertNotNull(product);
                        // Count numeric ids
                        try {
                            Integer.parseInt(fileName);
                            //expectedNumberOfCerts;
                            productsWithCerts.add(fileName);
                        }
                        catch (NumberFormatException e) {
                            // ExpectedName is not a numeric id
                        }
                    }

                    if (entry.getName().endsWith(".pem")) {
                        certs.add(new File(entry.getName()).getName().replace(".pem", ""));
                    }
                });
            assertEquals(productIdToProduct.size(), products.size());
            assertEquals(productsWithCerts.size(), certs.size());
            for (String cert : certs) {
                assertTrue(productsWithCerts.contains(cert));
            }
        }

        @Test
        public void shouldNotScheduleNonCronTasks() throws Exception {
            assertForbidden(() -> jobsApi.scheduleJob(UNDO_IMPORTS_JOB_KEY));
        }

        private void initializeData() throws Exception {
            owner = ownerApi.createOwner(Owners.random());
            String ownerKey = owner.getKey();

            RoleDTO role = rolesApi.createRole(Roles.ownerAll(owner));
            UserDTO user = usersApi.createUser(Users.random());
            role = rolesApi.addUserToRole(role.getName(), user.getUsername());
            consumer = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin),
                user.getUsername(), owner.getKey(), null, true);

            ProductDTO engProduct = ownerProductApi.createProductByOwner(ownerKey, Products.randomEng());
            productIdToProduct.put(engProduct.getId(), engProduct);

            Set<BrandingDTO> brandings = Set.of(Branding.build("Branded Eng Product", "OS")
                .productId(engProduct.getId()));

            ProductDTO derivedProvidedProduct = ownerProductApi
                .createProductByOwner(ownerKey, Products.random());
            productIdToProduct.put(derivedProvidedProduct.getId(), derivedProvidedProduct);

            ProductDTO derivedProduct = Products.random();
            derivedProduct.setProvidedProducts(Set.of(derivedProvidedProduct));
            derivedProduct = ownerProductApi.createProductByOwner(ownerKey, derivedProduct);
            productIdToProduct.put(derivedProduct.getId(), derivedProduct);

            ProductDTO product1 = Products.random();
            product1.setMultiplier(2L);
            product1.setBranding(brandings);
            product1.setProvidedProducts(Set.of(engProduct));
            product1 = ownerProductApi.createProductByOwner(ownerKey, product1);
            productIdToProduct.put(product1.getId(), product1);

            ProductDTO product2 = Products.random();
            product2 = ownerProductApi.createProductByOwner(ownerKey, product2);
            productIdToProduct.put(product2.getId(), product2);

            ProductDTO virtProduct = Products.withAttributes(ProductAttributes.VirtualOnly.withValue("true"));
            virtProduct = ownerProductApi.createProductByOwner(ownerKey, virtProduct);
            productIdToProduct.put(virtProduct.getId(), virtProduct);

            ProductDTO product3 = Products.withAttributes(ProductAttributes.Arch.withValue("x86_64"),
                ProductAttributes.VirtualLimit.withValue("unlimited"));
            product3.setDerivedProduct(derivedProduct);
            product3 = ownerProductApi.createProductByOwner(ownerKey, product3);
            productIdToProduct.put(product3.getId(), product3);

            ProductDTO productVdc = createVDCProduct(client, ownerKey);
            productIdToProduct.put(productVdc.getId(), productVdc);
            ProductDTO productDc = productVdc.getDerivedProduct();
            productIdToProduct.put(productDc.getId(), productDc);

            // this is for the update process
            ProductDTO productUp = ownerProductApi.createProductByOwner(ownerKey, Products.random());
            productIdToProduct.put(productUp.getId(), productUp);

            ContentDTO content1 = Contents.random()
                .metadataExpire(6000L)
                .requiredTags("TAG1,TAG2");
            content1 = ownerContentApi.createContent(ownerKey, content1);

            ContentDTO archContent = Contents.random()
                .metadataExpire(6000L)
                .contentUrl("/path/to/arch/specific/content")
                .requiredTags("TAG1,TAG2")
                .arches("i386,x86_64");
            archContent = ownerContentApi.createContent(ownerKey, archContent);

            ownerProductApi.addContent(ownerKey, product1.getId(), content1.getId(), true);
            ownerProductApi.addContent(ownerKey, product2.getId(), content1.getId(), true);
            ownerProductApi.addContent(ownerKey, product2.getId(), archContent.getId(), true);
            ownerProductApi.addContent(ownerKey, derivedProduct.getId(), content1.getId(), true);

            List<ProductDTO> poolProducts =
                List.of(product1, product2, virtProduct, product3, productUp, productVdc);
            Map<String, PoolDTO> poolIdToPool =
                createPoolsForProducts(ownerApi, ownerKey, poolProducts, brandings);

            consumer.setFacts(Map.of("distributor_version", "sam-1.3"));
            ReleaseVerDTO releaseVer = new ReleaseVerDTO()
                .releaseVer("");
            consumer.setReleaseVer(releaseVer);
            consumerApi.updateConsumer(consumer.getUuid(), consumer);
            consumer = consumerApi.getConsumer(consumer.getUuid());

            bindPoolsToConsumer(consumerApi, consumer.getUuid(), poolIdToPool.keySet());

            cdn = cdnApi.createCdn(Cdns.random());
        }

    }

    private File createExport(ApiClient apiClient, String consumerUuid, CdnDTO cdn)
        throws ApiException {
        String cdnLabel = cdn == null ? null : cdn.getLabel();
        String cdnName = cdn == null ? null : cdn.getName();
        String cdnUrl = cdn == null ? null : cdn.getUrl();
        File export = apiClient.consumers().exportData(consumerUuid, cdnLabel, cdnName, cdnUrl);
        export.deleteOnExit();

        return export;
    }

    private File createCertExport(ApiClient client, String consumerUuid) throws IOException {
        Response response = Request.from(client)
            .setPath("/consumers/{consumer_uuid}/certificates")
            .setPathParam("consumer_uuid", consumerUuid)
            .addHeader("accept", "application/zip")
            .execute();

        assertThat(response).returns(200, Response::getCode);
        File export = Files.newTemporaryFile();
        export.deleteOnExit();
        try (FileOutputStream os = new FileOutputStream(export)) {
            os.write(response.getBody());
        }

        return export;
    }

    private File createExportAsync(ApiClient apiClient, String consumerUuid, CdnDTO cdn)
        throws ApiException, IOException {
        String cdnLabel = cdn == null ? null : cdn.getLabel();
        String cdnName = cdn == null ? null : cdn.getName();
        String cdnUrl = cdn == null ? null : cdn.getUrl();
        AsyncJobStatusDTO jobStatus = apiClient.consumers()
            .exportDataAsync(consumerUuid, cdnLabel, cdnName, cdnUrl);
        jobStatus = apiClient.jobs().waitForJob(jobStatus.getId());
        if (!jobStatus.getState().equals("FINISHED")) {
            throw new ApiException("Unable to create export.");
        }

        ExportResultDTO result = ApiClient.MAPPER
            .convertValue(jobStatus.getResultData(), ExportResultDTO.class);
        assertNotNull(result);
        assertNotNull(result.getExportId());

        File export = apiClient.consumers().downloadExistingExport(consumerUuid, result.getExportId());
        export.deleteOnExit();

        return export;
    }

    private ConsumerDTO createReadOnlyConsumer(ApiClient client, OwnerDTO owner) throws ApiException {
        UserDTO readOnlyUser = UserUtil.createReadOnlyUser(client, owner);
        ConsumerDTO readOnlyConsumer = Consumers.random(owner, ConsumerTypes.Candlepin);

        return client.consumers().createConsumer(readOnlyConsumer, readOnlyUser.getUsername(),
            owner.getKey(), null, true);
    }

    private ProductDTO createVDCProduct(ApiClient client, String ownerKey) throws ApiException {
        OwnerProductApi ownerProductsApi = client.ownerProducts();
        ProductDTO productDc = Products.withAttributes(ProductAttributes.Arch.withValue("x86_64"),
            ProductAttributes.StackingId.withValue("stack-dc"));
        productDc = ownerProductsApi.createProductByOwner(ownerKey, productDc);

        ProductDTO productVdc = Products.withAttributes(ProductAttributes.Arch.withValue("x86_64"),
            ProductAttributes.VirtualLimit.withValue("unlimited"),
            ProductAttributes.StackingId.withValue("stack-vdc"));
        productVdc.setDerivedProduct(productDc);
        productVdc = ownerProductsApi.createProductByOwner(ownerKey, productVdc);

        return productVdc;
    }

    private List<JsonNode> bindPoolsToConsumer(ConsumerClient consumerApi, String consumerUuid,
        Collection<String> poolIds) throws ApiException, JsonProcessingException {
        List<JsonNode> poolNodes = new ArrayList<>();
        for (String poolId : poolIds) {
            poolNodes.add(consumerApi.bindPool(consumerUuid, poolId, 1));
        }

        return poolNodes;
    }

    private Map<String, PoolDTO> createPoolsForProducts(OwnerClient ownerApi, String ownerKey,
        Collection<ProductDTO> products, Collection<BrandingDTO> brandings) throws ApiException {
        Map<String, PoolDTO> poolIdToPool = new HashMap<>();
        for (ProductDTO product : products) {
            PoolDTO pool = createPool(ownerApi, ownerKey, product, 2L, brandings);
            poolIdToPool.put(pool.getId(), pool);
        }

        return poolIdToPool;
    }

    private PoolDTO createPool(OwnerClient ownerApi, String ownerKey, ProductDTO product, long quantity,
        Collection<BrandingDTO> brandings) throws ApiException {
        PoolDTO pool = Pools.random(product)
            .quantity(quantity)
            .providedProducts(new HashSet<>())
            .accountNumber("12345")
            .orderNumber("6789")
            .endDate(OffsetDateTime.now().plusYears(5))
            .branding(new HashSet<>(brandings));

        return ownerApi.createPool(ownerKey, pool);
    }

}
