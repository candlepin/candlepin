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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ExportResultDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.CdnApi;
import org.candlepin.resource.client.v1.ConsumerTypeApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.RolesApi;
import org.candlepin.resource.client.v1.RulesApi;
import org.candlepin.resource.client.v1.UsersApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyWithCapability;
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
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


@SpecTest
@OnlyInHosted
class ExportSpecTest {
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

        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO content = adminClient.ownerContent().createContent(ownerKey, Contents.random());
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), content.getId(), true);
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
    public void shouldExportContentAccessCertsForAConsumerBelongingToOwnerInSCAMode() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO modifiedProd = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO cont = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), cont.getId(), true);
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        // Switching to SCA to verify that only the SCA certificate is present and not the entitlement
        // certificate. We need to wait here for a while to allow the EntitlementRevokingJob to complete.
        owner.setContentAccessMode("org_environment");
        adminClient.owners().updateOwner(ownerKey, owner);

        AsyncJobStatusDTO entJob = adminClient.jobs()
            .listMatchingJobStatusForOrg(ownerKey, null, null)
            .stream()
            .filter(job -> job.getKey().equals("EntitlementRevokingJob"))
            .findFirst()
            .get();

        assertThatJob(entJob)
            .isNotNull()
            .terminates(adminClient)
            .isFinished();


        File manifest = consumerClient.consumers().exportCertificatesInZipFormat(consumer.getUuid(), null);
        ZipFile export = ExportUtil.getExportArchive(manifest);

        // Check if content access certs are present in exported zip file.
        List<ZipEntry> caCerts = export.stream()
            .filter(entry -> entry.getName().startsWith(CONTENT_ACCESS_CERTS_PATH))
            .filter(entry -> entry.getName().lastIndexOf('/') == CONTENT_ACCESS_CERTS_PATH.length() - 1)
            .collect(Collectors.toList());

        assertThat(caCerts).singleElement();

        // Should not contain entitlement certificate in SCA mode
        List<ZipEntry> entitlementCerts = export.stream()
            .filter(entry -> entry.getName().startsWith(ENTITILEMENT_CERTIFICATES_PATH))
            .filter(entry -> entry.getName().lastIndexOf('/') == ENTITILEMENT_CERTIFICATES_PATH.length() - 1)
            .collect(Collectors.toList());

        assertThat(entitlementCerts).isEmpty();
    }

    @Test
    public void shouldExportEntitlementCertsForAConsumerBelongingToOwnerInEntitlementMode() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();

        ProductDTO modifiedProd = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO prod = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ContentDTO cont = adminClient.ownerContent().createContent(ownerKey, Contents.random()
            .modifiedProductIds(Set.of(modifiedProd.getId())));
        adminClient.ownerProducts().addContentToProduct(ownerKey, prod.getId(), cont.getId(), true);
        PoolDTO pool = adminClient.owners().createPool(ownerKey, Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        File manifest = consumerClient.consumers().exportCertificatesInZipFormat(consumer.getUuid(), null);
        ZipFile export = ExportUtil.getExportArchive(manifest);

        // Should not contain content access certs in exported zip file in Entitlement mode.
        List<ZipEntry> caCerts = export.stream()
            .filter(e -> e.getName().startsWith(CONTENT_ACCESS_CERTS_PATH))
            .filter(e -> e.getName().lastIndexOf('/') == CONTENT_ACCESS_CERTS_PATH.length() - 1)
            .collect(Collectors.toList());

        assertThat(caCerts).isEmpty();

        // Should contain entitlement certificate in Entitlement mode
        List<ZipEntry> entitlementCerts = export.stream()
            .filter(e -> e.getName().startsWith(ENTITILEMENT_CERTIFICATES_PATH))
            .filter(e -> e.getName().lastIndexOf('/') == ENTITILEMENT_CERTIFICATES_PATH.length() - 1)
            .collect(Collectors.toList());

        assertThat(entitlementCerts).singleElement();
    }

    @Test
    @OnlyInHosted
    @OnlyWithCapability("cloud_registration")
    public void shouldNotExportAnonymousManifestWithAnonymousToken() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        adminClient.hosted().createOwner(owner);
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");

        ProductDTO product1 = adminClient.hosted().createProduct(Products.random());
        ContentDTO content1 = adminClient.hosted().createContent(Contents.random());
        ContentDTO content2 = adminClient.hosted().createContent(Contents.random());
        adminClient.hosted().addContentToProduct(product1.getId(), content1.getId(), true);
        adminClient.hosted().addContentToProduct(product1.getId(), content2.getId(), true);

        ProductDTO product2 = adminClient.hosted().createProduct(Products.random());
        ContentDTO content3 = adminClient.hosted().createContent(Contents.random());
        adminClient.hosted().addContentToProduct(product2.getId(), content3.getId(), true);

        adminClient.hosted()
            .associateProductIdsToCloudOffer(offerId, List.of(product1.getId(), product2.getId()));
        adminClient.hosted().associateOwnerToCloudAccount(accountId, owner.getKey());

        CloudAuthenticationResultDTO result = ApiClients.noAuth().cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        Response response = Request.from(ApiClients.bearerToken(result.getToken()))
            .setPath("/consumers/{consumer_uuid}/certificates")
            .setPathParam("consumer_uuid",  result.getAnonymousConsumerUuid())
            .addHeader("accept", "application/zip")
            .execute();

        assertEquals(400, response.getCode());
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
        private HostedTestApi hostedTestApi;

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
            this.hostedTestApi = this.client.hosted();

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
            ConsumerDTO actual = ExportUtil.deserializeJsonFile(export, path, ConsumerDTO.class);
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
        void shouldExportRegeneratedEntitlementCertificates() throws Exception {
            int target = ENTITILEMENT_CERTIFICATES_PATH.length() - 1;
            List<ZipEntry> entitlementCerts = export.stream()
                .filter(entry -> entry.getName().startsWith(ENTITILEMENT_CERTIFICATES_PATH))
                .filter(entry -> entry.getName().lastIndexOf('/') == target)
                .collect(Collectors.toList());

            // Regenerate some entitlement certificates, and generate a new manifest:
            String consumerUuid = consumer.getUuid();
            consumerApi.regenerateEntitlementCertificates(consumerUuid, null, true, false);
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

        // This test is legacy, maybe? This test's behavior is a function of internal knowledge of
        // how certain adapters work; not necessarily guaranteed behavior. Whether or not a cert is
        // present is dependent on the adapter implementation, *not* the operating mode.
        // Also standalone Sat instances don't export manifests, so this will never come up in
        // production.

        // @Test
        // void shouldExportProductsAndCerts() throws Exception {
        //     int target = PRODUCTS_PATH.length() - 1;
        //     List<String> products = new ArrayList<>();
        //     List<String> productsWithCerts = new ArrayList<>();
        //     List<String> certs = new ArrayList<>();
        //     export.stream()
        //         .filter(entry -> entry.getName().startsWith(PRODUCTS_PATH))
        //         .filter(entry -> entry.getName().lastIndexOf('/') == target)
        //         .forEach(entry -> {
        //             if (entry.getName().endsWith(".json")) {
        //                 products.add(entry.getName());
        //                 String fileName = new File(entry.getName()).getName().replace(".json", "");
        //                 ProductDTO product = productIdToProduct.get(fileName);
        //                 assertNotNull(product);
        //                 // Count numeric ids
        //                 try {
        //                     Integer.parseInt(fileName);
        //                     //expectedNumberOfCerts;
        //                     productsWithCerts.add(fileName);
        //                 }
        //                 catch (NumberFormatException e) {
        //                     // ExpectedName is not a numeric id
        //                 }
        //             }

        //             if (entry.getName().endsWith(".pem")) {
        //                 certs.add(new File(entry.getName()).getName().replace(".pem", ""));
        //             }
        //         });
        //     assertEquals(productIdToProduct.size(), products.size());
        //     assertEquals(productsWithCerts.size(), certs.size());
        //     for (String cert : certs) {
        //         assertTrue(productsWithCerts.contains(cert));
        //     }
        // }

        @Test
        public void shouldNotScheduleNonCronTasks() throws Exception {
            assertForbidden(() -> jobsApi.scheduleJob(UNDO_IMPORTS_JOB_KEY));
        }

        private SubscriptionDTO createSubscription(ProductDTO product) {
            OffsetDateTime now = OffsetDateTime.now();

            return new SubscriptionDTO()
                .id(StringUtil.random("export_sub-", 8, StringUtil.CHARSET_NUMERIC_HEX))
                .owner(Owners.toNested(this.owner))
                .product(product)
                .quantity(2L)
                .startDate(now.minusDays(1))
                .endDate(now.plusYears(5))
                .accountNumber("12345")
                .orderNumber("6789");
        }

        private AsyncJobStatusDTO refreshPools(String ownerKey) {
            AsyncJobStatusDTO job = this.ownerApi.refreshPools(ownerKey, false);
            assertNotNull(job);

            job = this.jobsApi.waitForJob(job);
            assertEquals("FINISHED", job.getState());

            return job;
        }

        private boolean isDerivedPool(PoolDTO pool) {
            return pool.getAttributes()
                .stream()
                .filter(attrib -> "pool_derived".equalsIgnoreCase(attrib.getName()))
                .anyMatch(attrib -> "true".equalsIgnoreCase(attrib.getValue()));
        }

        private void initializeData() throws Exception {
            this.owner = ownerApi.createOwner(Owners.random());

            UserDTO user = usersApi.createUser(Users.random());
            RoleDTO role = rolesApi.createRole(Roles.ownerAll(owner).addUsersItem(user));

            // prep data for creation upstream
            ProductDTO engProduct = Products.randomEng();

            ProductDTO derivedProvidedProduct = Products.random();

            ProductDTO derivedProduct = Products.random()
                .providedProducts(Set.of(derivedProvidedProduct));

            ProductDTO product1 = Products.random()
                .multiplier(2L)
                .providedProducts(Set.of(engProduct))
                .branding(Set.of(Branding.build("Branded Eng Product", "OS", engProduct)));

            ProductDTO product2 = Products.random();

            ProductDTO virtProduct = Products.withAttributes(ProductAttributes.VirtualOnly.withValue("true"));

            ProductDTO product3 = Products.random()
                .addAttributesItem(ProductAttributes.Arch.withValue("x86_64"))
                .addAttributesItem(ProductAttributes.VirtualLimit.withValue("unlimited"))
                .derivedProduct(derivedProduct);

            ProductDTO productDc = Products.random()
                .addAttributesItem(ProductAttributes.Arch.withValue("x86_64"))
                .addAttributesItem(ProductAttributes.StackingId.withValue("stack-dc"));

            ProductDTO productVdc = Products.random()
                .addAttributesItem(ProductAttributes.Arch.withValue("x86_64"))
                .addAttributesItem(ProductAttributes.VirtualLimit.withValue("unlimited"))
                .addAttributesItem(ProductAttributes.StackingId.withValue("stack-vdc"))
                .derivedProduct(productDc);

            ProductDTO productUp = Products.random();

            ContentDTO content1 = Contents.random()
                .metadataExpire(6000L)
                .requiredTags("TAG1,TAG2");

            ContentDTO archContent = Contents.random()
                .metadataExpire(6000L)
                .contentUrl("/path/to/arch/specific/content")
                .requiredTags("TAG1,TAG2")
                .arches("i386,x86_64");

            // Persist upstream data
            Stream.of(engProduct, derivedProvidedProduct, derivedProduct, product1, product2, virtProduct,
                product3, productDc, productVdc, productUp)
                .map(this.hostedTestApi::createProduct)
                .forEach(pdto -> productIdToProduct.put(pdto.getId(), pdto));

            List.of(content1, archContent).forEach(content -> this.hostedTestApi.createContent(content));

            this.hostedTestApi.addContentToProduct(product1.getId(), content1.getId(), true);
            this.hostedTestApi.addContentToProduct(product2.getId(), content1.getId(), true);
            this.hostedTestApi.addContentToProduct(product2.getId(), archContent.getId(), true);
            this.hostedTestApi.addContentToProduct(derivedProduct.getId(), content1.getId(), true);

            Stream.of(product1, product2, virtProduct, product3, productUp, productVdc)
                .map(this::createSubscription)
                .forEach(this.hostedTestApi::createSubscription);

            // Create consumer to bind all this junk
            ConsumerDTO consumer = Consumers.random(owner, ConsumerTypes.Candlepin)
                .putFactsItem("distributor_version", "sam-1.3")
                .releaseVer(new ReleaseVerDTO().releaseVer(""))
                .capabilities(null);

            this.consumer = this.consumerApi.createConsumer(consumer, user.getUsername(), owner.getKey(),
                null, true);

            // Refresh & Bind
            this.refreshPools(owner.getKey());

            List<String> primaryPoolIds = this.ownerApi.listOwnerPools(owner.getKey()).stream()
                .filter(Predicate.not(this::isDerivedPool))
                .map(PoolDTO::getId)
                .toList();

            bindPoolsToConsumer(this.consumerApi, this.consumer.getUuid(), primaryPoolIds);

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

    private List<JsonNode> bindPoolsToConsumer(ConsumerClient consumerApi, String consumerUuid,
        Collection<String> poolIds) throws ApiException {
        List<JsonNode> poolNodes = new ArrayList<>();
        for (String poolId : poolIds) {
            poolNodes.add(consumerApi.bindPool(consumerUuid, poolId, 1));
        }

        return poolNodes;
    }

}
