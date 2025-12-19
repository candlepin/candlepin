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
package org.candlepin.spec.imports;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.ImportUpstreamConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UpstreamConsumerDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.Cdns;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.ExportCdn;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;


@SpecTest
@OnlyInStandalone
public class ImportSuccessSpecTest {
    private static final String RECORD_CLEANER_JOB_KEY = "ImportRecordCleanerJob";

    private static ApiClient adminClient;

    @BeforeAll
    public static void beforeAll() {
        assumeTrue(CandlepinMode::hasManifestGenTestExtension);

        adminClient = ApiClients.admin();
    }

    private AsyncJobStatusDTO importAsync(OwnerDTO owner, File manifest, String... force) {
        List<String> forced = force != null ? Arrays.asList(force) : List.of();

        AsyncJobStatusDTO importJob = adminClient.owners()
            .importManifestAsync(owner.getKey(), forced, manifest);

        importJob = adminClient.jobs().waitForJob(importJob);
        assertThatJob(importJob)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        return importJob;
    }

    private ProductDTO addContentToProduct(ProductDTO product, ContentDTO... contents) {
        for (ContentDTO content : contents) {
            ProductContentDTO pcdto = new ProductContentDTO()
                .content(content)
                .enabled(true);

            product.addProductContentItem(pcdto);
        }

        return product;
    }

    @Test
    public void shouldCreatePools() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ProductDTO derivedProvidedProduct = Products.random();
        ProductDTO derivedProduct = Products.random()
            .providedProducts(Set.of(derivedProvidedProduct));

        ProductDTO providedProduct = Products.random();

        ProductDTO product = Products.random()
            .derivedProduct(derivedProduct)
            .providedProducts(Set.of(providedProduct));

        File manifest = new ExportGenerator()
            .addProduct(product)
            .export();

        this.importAsync(owner, manifest);

        // Verify the pool we created has the correct mappings
        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .isNotNull()
            .hasSize(1);

        assertThat(pools)
            .singleElement()
            .extracting(PoolDTO::getProvidedProducts, as(collection(ProvidedProductDTO.class)))
            .singleElement()
            .extracting(ProvidedProductDTO::getProductId)
            .isEqualTo(providedProduct.getId());

        assertThat(pools)
            .singleElement()
            .extracting(PoolDTO::getDerivedProductId)
            .isEqualTo(derivedProduct.getId());

        assertThat(pools)
            .singleElement()
            .extracting(PoolDTO::getDerivedProvidedProducts, as(collection(ProvidedProductDTO.class)))
            .singleElement()
            .extracting(ProvidedProductDTO::getProductId)
            .isEqualTo(derivedProvidedProduct.getId());
    }

    @Test
    public void shouldNotRemoveCustomPoolsDuringImport() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create some local, custom products and pools
        ProductDTO product1 = adminClient.ownerProducts().createProduct(owner.getKey(), Products.random());
        ProductDTO product2 = adminClient.ownerProducts().createProduct(owner.getKey(), Products.random());
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));

        // Create a basic manifest for import
        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        this.importAsync(owner, manifest);

        // After import, our custom pools should still exist, along with whatever else got created
        // by the manifest
        List<PoolDTO> poolsAfterImport = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(poolsAfterImport)
            .isNotNull()
            .hasSizeGreaterThan(2)
            .map(PoolDTO::getId)
            .contains(pool1.getId(), pool2.getId());
    }

    @Test
    public void shouldIgnoreMultiplierForPoolQuantity() throws Exception {
        // This test is weird. We're verifying that the quantity of the pools we get in the manifest
        // aren't affected by multipliers and junk on the given source pool/product. While that's
        // fine and all, that's not an import test; it's an *exporter* test. Once the pools are
        // exported, importing it is simply a means of examining the entitlements in the manifest.

        // TODO: Move this test to the exporter test suite and adjust it accordingly.

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ProductDTO virtProduct = Products.random()
            .multiplier(2L)
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("true"));

        SubscriptionDTO virtSub = Subscriptions.random()
            .product(virtProduct)
            .quantity(10L);

        File manifest = new ExportGenerator()
            .addSubscription(virtSub)
            .export();

        this.importAsync(owner, manifest);

        // Verify the import created pools, and the pools don't have their quantities affected by
        // the product multiplier
        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .isNotNull()
            .singleElement()
            .returns(virtSub.getQuantity(), PoolDTO::getQuantity);
    }

    @Test
    public void shouldSetUpstreamConsumerAfterSuccessfulImport() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        assertThat(owner)
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNull();

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        this.importAsync(owner, manifest);

        OwnerDTO updatedOwner = adminClient.owners().getOwner(owner.getKey());
        assertThat(updatedOwner)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNotNull()
            .extracting(UpstreamConsumerDTO::getUuid)
            .isNotNull();
    }

    @Test
    public void shouldPopulateUpstreamConsumerWithCdnDetails() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        CdnDTO cdn = adminClient.cdns().createCdn(Cdns.random());
        ExportCdn exportCdn = Cdns.toExport(cdn);

        assertThat(owner)
            .extracting(OwnerDTO::getUpstreamConsumer)
            .isNull();

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export(exportCdn);

        this.importAsync(owner, manifest);

        OwnerDTO updatedOwner = adminClient.owners().getOwner(owner.getKey());
        assertThat(updatedOwner)
            .isNotNull()
            .extracting(OwnerDTO::getUpstreamConsumer)
            .returns(exportCdn.apiUrl(), UpstreamConsumerDTO::getApiUrl)
            .returns(exportCdn.webUrl(), UpstreamConsumerDTO::getWebUrl)
            .extracting(UpstreamConsumerDTO::getIdCert)
            .isNotNull();
    }

    @Test
    public void shouldPopulateSubscriptionsWithCdnDetails() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        CdnDTO cdn = adminClient.cdns().createCdn(Cdns.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export(Cdns.toExport(cdn));

        this.importAsync(owner, manifest);

        List<SubscriptionDTO> subscriptions = adminClient.owners().getOwnerSubscriptions(owner.getKey());
        assertThat(subscriptions)
            .singleElement()
            .extracting(SubscriptionDTO::getCdn)
            .isEqualTo(cdn);
    }


    @Test
    public void shouldCreateSuccessRecordOfTheImport() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        this.importAsync(owner, manifest);

        List<ImportRecordDTO> importRecords = adminClient.owners().getImports(owner.getKey());
        assertThat(importRecords)
            .isNotNull()
            .isNotEmpty()
            .map(ImportRecordDTO::getStatus)
            .containsOnly("SUCCESS");
    }

    @Test
    public void shouldPopulateOriginInfoOfTheImportRecord() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        this.importAsync(owner, manifest);

        OwnerDTO updatedOwner = adminClient.owners().getOwner(owner.getKey());
        UpstreamConsumerDTO upstreamConsumer = updatedOwner.getUpstreamConsumer();
        assertNotNull(upstreamConsumer);

        List<ImportRecordDTO> importRecords = adminClient.owners().getImports(owner.getKey());
        assertThat(importRecords)
            .isNotNull()
            .isNotEmpty();

        for (ImportRecordDTO importRecord : importRecords) {
            assertThat(importRecord)
                .isNotNull()
                .returns("admin", ImportRecordDTO::getGeneratedBy)
                .returns(manifest.getName(), ImportRecordDTO::getFileName)
                .extracting(ImportRecordDTO::getGeneratedDate)
                .isNotNull();

            assertThat(importRecord.getUpstreamConsumer())
                .isNotNull()
                .returns(upstreamConsumer.getUuid(), ImportUpstreamConsumerDTO::getUuid)
                .returns(upstreamConsumer.getName(), ImportUpstreamConsumerDTO::getName)
                .returns(owner.getId(), ImportUpstreamConsumerDTO::getOwnerId);
        }
    }

    @Test
    public void shouldPurgeImportRecords() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        // This value comes from the default configuration of the cleaner job, which keeps the
        // latest 10 records by default. If the Candlepin instance under test is running with a
        // different configuration, this test will fail.
        int recordsRetained = 10;

        int importRecords = 12;
        for (int i = 0; i < importRecords; i++) {
            this.importAsync(owner, manifest, "MANIFEST_SAME");
        }

        List<ImportRecordDTO> importRecords1 = adminClient.owners().getImports(owner.getKey());
        assertThat(importRecords1)
            .isNotNull()
            .hasSize(importRecords);

        AsyncJobStatusDTO cleanerJob = adminClient.jobs().scheduleJob(RECORD_CLEANER_JOB_KEY);
        cleanerJob = adminClient.jobs().waitForJob(cleanerJob.getId());
        assertThatJob(cleanerJob)
            .isFinished();

        List<ImportRecordDTO> importRecords2 = adminClient.owners().getImports(owner.getKey());
        assertThat(importRecords2)
            .isNotNull()
            .hasSize(recordsRetained);
    }

    @Test
    public void shouldImportArchContentCorrectly() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ContentDTO archContent = Contents.random()
            .metadataExpire(6000L)
            .contentUrl("/path/to/arch/specific/content")
            .requiredTags("TAG1,TAG2")
            .arches("i386,x86_64");

        ProductDTO archProduct = Products.random()
            .addAttributesItem(ProductAttributes.Arch.withValue("x86_64"));

        this.addContentToProduct(archProduct, archContent);

        File manifest = new ExportGenerator()
            .addProduct(archProduct)
            .export();

        this.importAsync(owner, manifest);

        ContentDTO cdto = adminClient.ownerContent().getContentById(owner.getKey(), archContent.getId());
        assertThat(cdto)
            .isNotNull()
            .returns("i386,x86_64", ContentDTO::getArches);
    }

    @Test
    public void shouldContainBrandingInfo() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ProductDTO engProduct = Products.randomEng();
        BrandingDTO branding = Branding.random(engProduct);
        ProductDTO brandedProduct = Products.random()
            .branding(Set.of(branding));

        File manifest = new ExportGenerator()
            .addProduct(engProduct)
            .addProduct(brandedProduct)
            .export();

        this.importAsync(owner, manifest);

        List<PoolDTO> pools = adminClient.pools().listPoolsByProduct(owner.getId(), brandedProduct.getId());

        assertThat(pools)
            .singleElement()
            .extracting(PoolDTO::getBranding, as(collection(BrandingDTO.class)))
            .singleElement()
            .isNotNull()
            .returns(branding.getName(), BrandingDTO::getName)
            .returns(branding.getProductId(), BrandingDTO::getProductId)
            .returns(branding.getType(), BrandingDTO::getType);
    }

    @Test
    public void shouldNotContainBrandingWhenNoBrandingProvided() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        this.importAsync(owner, manifest);

        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .singleElement()
            .extracting(PoolDTO::getBranding, as(collection(BrandingDTO.class)))
            .isEmpty();
    }

    @Test
    public void shouldStoreTheSubscriptionUpstreamEntitlementCert() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        File manifest = new ExportGenerator()
            .addProduct(Products.random())
            .export();

        this.importAsync(owner, manifest);

        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools).singleElement();

        PoolDTO pool = pools.get(0);

        Map<String, String> subCert = adminClient.pools().getCert(pool.getId());
        assertThat(subCert.get("key"))
            .startsWith("-----BEGIN PRIVATE KEY-----");
        assertThat(subCert.get("cert"))
            .startsWith("-----BEGIN CERTIFICATE-----");

        JsonNode jsonNode = adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        String ent = adminClient.entitlements().getUpstreamCert(jsonNode.get(0).get("id").asText());

        assertThat(ent)
            .contains(subCert.get("key"))
            .contains(subCert.get("cert"));
    }

    @Test
    public void shouldImportContentWithMetadataExpirationSetToOne() throws Exception {
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        ProductDTO providedProduct = Products.random();
        ProductDTO derivedProvidedProduct = Products.random();
        ProductDTO derivedProduct = Products.random()
            .providedProducts(Set.of(derivedProvidedProduct));
        ProductDTO product = Products.random()
            .addProvidedProductsItem(providedProduct)
            .derivedProduct(derivedProduct);

        this.addContentToProduct(providedProduct, this.createContent());
        this.addContentToProduct(derivedProvidedProduct, this.createContent());
        this.addContentToProduct(derivedProduct, this.createContent());
        this.addContentToProduct(product, this.createContent());

        File manifest = new ExportGenerator()
            .addProduct(product)
            .export();

        this.importAsync(owner, manifest);

        // Impl note:
        // At the time of writing, our spec tests don't perform any clean up of the data after each
        // test or test run. As a consequence, the global namespace will be polluted with extraneous
        // products and contents by the other import/export tests, so we'll need to account for
        // that here by filtering on our expected content IDs.
        List<String> cids = Stream.of(providedProduct, derivedProvidedProduct, derivedProduct, product)
            .map(ProductDTO::getProductContent)
            .flatMap(Collection::stream)
            .map(ProductContentDTO::getContent)
            .map(ContentDTO::getId)
            .toList();

        List<ContentDTO> contents = adminClient.ownerContent()
            .getContentsByOwner(owner.getKey(), cids, List.of(), null, null);

        assertThat(contents)
            .isNotNull()
            .hasSize(4)
            .map(ContentDTO::getMetadataExpire)
            .containsOnly(1L);
    }

    private ContentDTO createContent() {
        return Contents.random()
            .metadataExpire(6000L)
            .contentUrl("/path/to/arch/specific/content")
            .requiredTags("TAG1,TAG2")
            .arches("i386,x86_64");
    }

}
