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

package org.candlepin.spec.imports;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.CdnDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ImportRecordDTO;
import org.candlepin.dto.api.client.v1.ImportUpstreamConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UpstreamConsumerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Export;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.PoolAttributes;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpecTest
@OnlyInStandalone
public class ImportSuccessSpecTest {

    private static final String EXPECTED_CONTENT_URL = "/path/to/arch/specific/content";

    private ApiClient admin;
    private OwnerDTO owner;
    private ApiClient userClient;

    @BeforeEach
    public void beforeAll() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        userClient = ApiClients.basic(user);
    }

    @Test
    public void shouldCreatePools() {
        ProductDTO derivedProvidedProduct = Products.random();
        ProductDTO derivedProduct = Products.random()
            .providedProducts(Set.of(derivedProvidedProduct));
        ProductDTO product = Products.random()
            .derivedProduct(derivedProduct);
        Export export = generateWith(generator -> generator
            .withProduct(derivedProvidedProduct)
            .withProduct(derivedProduct)
            .withProduct(product));
        doImport(owner.getKey(), export.file());

        List<PoolDTO> pools = userClient.pools().listPoolsByOwner(owner.getId());

        assertAnyNonEmpty(pools, PoolDTO::getProvidedProducts);
        assertAnyNonEmpty(pools, PoolDTO::getDerivedProvidedProducts);
    }

    @Test
    public void shouldIgnoreMultiplierForPoolQuantity() {
        ProductDTO virtProduct = Products
            .withAttributes(ProductAttributes.VirtualOnly.withValue("true"));
        Export export = generateWith(generator -> generator
            .withProduct(virtProduct));
        doImport(owner.getKey(), export.file());

        List<PoolDTO> pools = userClient.pools().listPoolsByOwner(owner.getId());

        Set<PoolDTO> mappedPools = filterMappedPools(pools);

        assertThat(mappedPools)
            .map(PoolDTO::getQuantity)
            .containsOnly(1L);
    }

    @Test
    public void shouldModifyTheOwnerToReferenceUpstreamConsumer() {
        Export export = generateWith(generator -> generator
            .withProduct(Products.random()));
        doImport(owner.getKey(), export.file());

        OwnerDTO updatedOwner = userClient.owners().getOwner(owner.getKey());
        userClient.pools().listPoolsByOwner(owner.getId());

        assertThat(updatedOwner.getUpstreamConsumer().getUuid()).isEqualTo(export.consumer().getUuid());
    }

    @Test
    public void shouldPopulateOriginInfoOfTheImportRecord() {
        Export export = generateWith(generator -> generator
            .withProduct(Products.random()));
        doImport(owner.getKey(), export.file());

        List<ImportRecordDTO> imports = userClient.owners().getImports(owner.getKey());

        for (ImportRecordDTO anImport : imports) {
            assertThat(anImport.getGeneratedBy()).isEqualTo("admin");
            assertThat(anImport.getGeneratedDate()).isNotNull();
            assertThat(anImport.getFileName()).isEqualTo(export.file().getName());

            ImportUpstreamConsumerDTO upstreamConsumer = anImport.getUpstreamConsumer();
            assertThat(upstreamConsumer.getUuid()).isEqualTo(export.consumer().getUuid());
            assertThat(upstreamConsumer.getName()).isEqualTo(export.consumer().getName());
            assertThat(upstreamConsumer.getOwnerId()).isEqualTo(owner.getId());
        }
    }

    @Test
    public void shouldCreateSuccessRecordOfTheImport() {
        Export export = generateWith(generator -> generator
            .withProduct(Products.random()));
        doImport(owner.getKey(), export.file());

        List<ImportRecordDTO> imports = userClient.owners().getImports(owner.getKey());

        assertThat(imports)
            .map(ImportRecordDTO::getStatus)
            .containsOnly("SUCCESS");
    }

    @Test
    public void shouldImportArchContentCorrectly() {
        ProductDTO archProduct = Products.withAttributes(
            ProductAttributes.Arch.withValue("x86_64")
        );
        ContentDTO archContent = Contents.random()
            .metadataExpire(6000L)
            .contentUrl("/path/to/arch/specific/content")
            .requiredTags("TAG1,TAG2")
            .arches("i386,x86_64");
        Export export = generateWith(generator -> generator
            .withProduct(archProduct, archContent));
        doImport(owner.getKey(), export.file());

        List<ContentDTO> ownerContent = userClient.ownerContent().listOwnerContent(owner.getKey());

        assertThat(ownerContent)
            .filteredOn(content -> EXPECTED_CONTENT_URL.equalsIgnoreCase(content.getContentUrl()))
            .map(ContentDTO::getArches)
            .containsExactly("i386,x86_64");
    }

    @Test
    public void shouldContainsUpstreamConsumer() {
        Export export = generateWith(generator -> generator
            .withProduct(Products.random()));
        doImport(owner.getKey(), export.file());

        OwnerDTO importOwner = admin.owners().getOwner(owner.getKey());
        UpstreamConsumerDTO upstreamConsumer = importOwner.getUpstreamConsumer();

        assertThat(upstreamConsumer)
            .isNotNull()
            .hasFieldOrPropertyWithValue("uuid", export.consumer().getUuid())
            .hasFieldOrPropertyWithValue("name", export.consumer().getName())
            .hasFieldOrPropertyWithValue("apiUrl", export.cdn().apiUrl())
            .hasFieldOrPropertyWithValue("webUrl", export.cdn().webUrl());
        assertThat(upstreamConsumer.getId()).isNotNull();
        assertThat(upstreamConsumer.getIdCert()).isNotNull();
        assertThat(upstreamConsumer.getType()).isEqualTo(export.consumer().getType());
    }

    @Test
    public void shouldContainAllDerivedProductData() {
        ProductDTO derivedProvidedProduct = Products.random();
        ProductDTO derivedProduct = Products.random()
            .providedProducts(Set.of(derivedProvidedProduct));
        ProductDTO product = Products.random()
            .derivedProduct(derivedProduct);
        Export export = generateWith(generator -> generator
            .withProduct(derivedProvidedProduct)
            .withProduct(derivedProduct)
            .withProduct(product));
        doImport(owner.getKey(), export.file());

        List<PoolDTO> pools = userClient.pools().listPoolsByProduct(owner.getId(), product.getId());
        PoolDTO pool = pools.stream().findFirst().orElseThrow();

        assertThat(pool.getDerivedProductId()).isEqualTo(derivedProduct.getId());
        assertThat(pool.getDerivedProvidedProducts())
            .hasSize(1)
            .map(ProvidedProductDTO::getProductId)
            .allSatisfy(productId -> assertThat(productId).isEqualTo(derivedProvidedProduct.getId()));
    }

    @Test
    public void shouldContainBrandingInfo() {
        ProductDTO engProduct = Products.randomEng();
        BrandingDTO branding = Branding.random(engProduct);
        ProductDTO brandedProduct = Products.random().branding(Set.of(branding));
        Export export = generateWith(generator -> generator
            .withProduct(engProduct)
            .withProduct(brandedProduct));
        doImport(owner.getKey(), export.file());

        List<PoolDTO> pools = userClient.pools().listPoolsByProduct(owner.getId(), brandedProduct.getId());

        assertThat(pools)
            .hasSize(1)
            .map(PoolDTO::getBranding)
            .first()
            .satisfies(brandings -> {
                assertThat(brandings)
                    .map(BrandingDTO::getName)
                    .containsOnly(branding.getName());
            });
    }

    @Test
    public void shouldNotContainBrandingInfo() {
        Export export = generateWith(generator -> generator.withProduct(Products.random()));
        doImport(owner.getKey(), export.file());

        List<PoolDTO> pools = userClient.pools().listPoolsByOwner(owner.getId());

        assertThat(pools)
            .hasSize(1)
            .map(PoolDTO::getBranding)
            .allSatisfy(brandings -> assertThat(brandings).isEmpty());
    }

    @Test
    public void shouldStoreTheSubscriptionUpstreamEntitlementCert() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        Export export = generateWith(generator -> generator.withProduct(Products.random()));
        doImport(owner.getKey(), export.file());

        // we only want the product that maps to a normal pool
        // i.e. no virt, no multipliers, etc.
        // this is to fix a intermittent test failures when trying
        // to bind to a virt_only or other weird pool
        List<PoolDTO> pools = userClient.pools().listPoolsByOwner(owner.getId());
        PoolDTO pool = pools.stream()
            .filter(dto -> "master".equalsIgnoreCase(dto.getSubscriptionSubKey()))
            .filter(dto -> !isVirtOnly(dto))
            .findFirst()
            .orElseThrow();

        Map<String, String> subCert = admin.pools().getCert(pool.getId());

        assertThat(subCert.get("key"))
            .startsWith("-----BEGIN PRIVATE KEY-----");
        assertThat(subCert.get("cert"))
            .startsWith("-----BEGIN CERTIFICATE-----");

        JsonNode jsonNode = userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        String ent = admin.entitlements().getUpstreamCert(jsonNode.get(0).get("id").asText());

        assertThat(ent)
            .contains(subCert.get("key"))
            .contains(subCert.get("cert"));
    }

    @Test
    public void shouldPutTheCdnFromTheManifestIntoTheCreatedSubscriptions() {
        Export export = generateWith(generator -> generator.withProduct(Products.random()));
        doImport(owner.getKey(), export.file());

        List<SubscriptionDTO> subscriptions = admin.owners().getOwnerSubscriptions(owner.getKey());

        assertThat(subscriptions)
            .map(SubscriptionDTO::getCdn)
            .map(CdnDTO::getLabel)
            .containsOnly(export.cdn().label());
    }

    public Export generateWith(Consumer<ExportGenerator> setup) {
        ExportGenerator exportGenerator = new ExportGenerator(admin);
        setup.accept(exportGenerator.minimal());
        return exportGenerator.export();
    }

    private boolean isVirtOnly(PoolDTO pool) {
        return pool.getAttributes().stream()
            .anyMatch(ProductAttributes.VirtualOnly::isKeyOf);
    }

    private Set<PoolDTO> filterMappedPools(List<PoolDTO> pools) {
        return pools.stream()
            .filter(this::isMapped)
            .collect(Collectors.toSet());
    }

    private boolean isMapped(PoolDTO pool) {
        if (pool == null || pool.getAttributes() == null || pool.getAttributes().isEmpty()) {
            return false;
        }

        boolean isUnmapped = pool.getAttributes().stream()
            .filter(PoolAttributes.UnmappedGuestsOnly::isKeyOf)
            .map(AttributeDTO::getValue)
            .anyMatch(Boolean::parseBoolean);

        return !isUnmapped;
    }

    private void assertAnyNonEmpty(List<PoolDTO> pools,
        Function<PoolDTO, Set<ProvidedProductDTO>> getDerivedProvidedProducts) {
        assertThat(pools)
            .map(getDerivedProvidedProducts)
            .filteredOn(products -> !products.isEmpty())
            .isNotEmpty();
    }

    private void doImport(String ownerKey, File export) {
        importAsync(ownerKey, export, List.of());
    }

    private void importAsync(String ownerKey, File export, List<String> force) throws ApiException {
        AsyncJobStatusDTO importJob = admin.owners().importManifestAsync(ownerKey, force, export);
        admin.jobs().waitForJob(importJob);
    }

}
