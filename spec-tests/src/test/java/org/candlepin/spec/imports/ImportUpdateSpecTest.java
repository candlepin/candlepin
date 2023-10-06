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

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;



@SpecTest
@OnlyInStandalone
public class ImportUpdateSpecTest {

    private static ApiClient adminClient;
    private static OwnerClient ownerApi;
    private static ConsumerClient consumerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;

    @BeforeAll
    public static void beforeAll() {
        assumeTrue(CandlepinMode::hasManifestGenTestExtension);

        adminClient = ApiClients.admin();
        ownerApi = adminClient.owners();
        consumerApi = adminClient.consumers();
        ownerContentApi = adminClient.ownerContent();
        ownerProductApi = adminClient.ownerProducts();
    }

    private AsyncJobStatusDTO importAsync(OwnerDTO owner, File manifest, String... force) {
        List<String> forced = force != null ? Arrays.asList(force) : List.of();

        AsyncJobStatusDTO importJob = adminClient.owners()
            .importManifestAsync(owner.getKey(), forced, manifest);

        return adminClient.jobs().waitForJob(importJob);
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
    public void shouldUpdateContentFromManifest() throws Exception {
        String initialTags = "tag1,tag2,tag3";
        String updatedTags = "tagA,tagB";

        ContentDTO content = Contents.random()
            .requiredTags(initialTags);

        ProductDTO product = Products.random()
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("5"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.HostLimited.withValue("true"));

        this.addContentToProduct(product, content);

        ExportGenerator exportGenerator = new ExportGenerator()
            .addProduct(product);

        File manifest1 = exportGenerator.export();

        // Create an owner to handle imports
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        // Do the initial import to populate the org
        AsyncJobStatusDTO importJob1 = this.importAsync(owner, manifest1);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        ContentDTO existingContent = ownerContentApi.getContentById(owner.getKey(), content.getId());
        assertThat(existingContent)
            .isNotNull()
            .extracting(ContentDTO::getRequiredTags)
            .isEqualTo(initialTags);

        // Modify the original content and then generate a new manifest
        content.setRequiredTags(updatedTags);
        File manifest2 = exportGenerator.export();

        // Import updated manifest and verify the content has indeed changed
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest2);
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        ContentDTO updatedContent = ownerContentApi.getContentById(owner.getKey(), content.getId());
        assertThat(updatedContent)
            .isNotNull()
            .extracting(ContentDTO::getRequiredTags)
            .isEqualTo(updatedTags);
    }

    @Test
    public void shouldSuccessfullyUpdateTheImport() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ContentDTO content1 = Contents.random();
        ProductDTO product1 = this.addContentToProduct(Products.randomEng(), content1);

        ExportGenerator exportGenerator = new ExportGenerator()
            .addProduct(product1);

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, exportGenerator.export());
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        List<SubscriptionDTO> importedSubscriptions = ownerApi.getOwnerSubscriptions(owner.getKey());
        assertThat(importedSubscriptions)
            .isNotNull()
            .hasSize(1);

        SubscriptionDTO importedSubscription = importedSubscriptions.get(0);
        Long importedSubscriptionSerial = importedSubscription.getCertificate()
            .getSerial()
            .getId();

        ProductDTO importedProduct = ownerProductApi.getProductById(owner.getKey(), product1.getId());
        assertNotNull(importedProduct);

        ContentDTO importedContent = ownerContentApi.getContentById(owner.getKey(), content1.getId());
        assertNotNull(importedContent);

        // Make some changes to the original manifest data and then import again
        product1.setName(product1.getName() + "-updated");
        content1.setName(content1.getName() + "-updated");

        ContentDTO content2 = Contents.random();
        ContentDTO content3 = Contents.random()
            .arches("i686,x86_64");

        ProductDTO product2 = this.addContentToProduct(Products.randomEng(), content2);
        ProductDTO product3 = this.addContentToProduct(Products.randomEng(), content2, content3);

        exportGenerator.addProducts(product2, product3);

        // Generate new manifest and re-import
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, exportGenerator.export());
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        // Verify some changes were made
        ProductDTO updatedProduct = ownerProductApi.getProductById(owner.getKey(), product1.getId());
        assertThat(updatedProduct)
            .isNotNull()
            .isNotEqualTo(importedProduct);

        ContentDTO updatedContent = ownerContentApi.getContentById(owner.getKey(), content1.getId());
        assertThat(updatedContent)
            .isNotNull()
            .isNotEqualTo(importedContent);

        List<SubscriptionDTO> updatedSubscriptions = ownerApi.getOwnerSubscriptions(owner.getKey());
        assertThat(updatedSubscriptions)
            .isNotNull()
            .hasSize(3); // two additional subscriptions were added

        SubscriptionDTO updatedSubscription = updatedSubscriptions.stream()
            .filter(sub -> sub.getId().equals(importedSubscription.getId()))
            .findAny()
            .orElse(null);

        assertThat(updatedSubscription)
            .isNotNull()
            .extracting(SubscriptionDTO::getCertificate)
            .extracting(CertificateDTO::getSerial)
            .extracting(CertificateSerialDTO::getId)
            .isNotEqualTo(importedSubscriptionSerial);
    }

    @Test
    public void shouldSuccessfullyUpdateTheImportExcess() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ContentDTO content = Contents.random();

        ProductDTO product = this.addContentToProduct(Products.random(), content)
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("5"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.HostLimited.withValue("true"));

        ExportGenerator exportGenerator = new ExportGenerator()
            .addProduct(product);

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, exportGenerator.export());
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .isNotNull()
            .hasSize(2);

        PoolDTO normalPool = pools.stream()
            .filter(pool -> "NORMAL".equals(pool.getType()))
            .findAny()
            .orElseThrow();

        // Bind a consumer to the base pool
        ConsumerDTO consumer = this.consumerApi.createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().bindPool(consumer.getUuid(), normalPool.getId(), 1);

        // Ensure a bonus pool has popped into existence due to our consumption of the base pool
        assertThat(adminClient.pools().listPoolsByOwner(owner.getId()))
            .isNotNull()
            .hasSize(3);

        // Sleep to create a gap between when the manifest was initially imported
        Thread.sleep(1000);

        // ???
        // What is this test actually testing at this point...?

        // Make some changes to the original manifest data and then import again?
        product.setName(product.getName() + "-updated");
        content.setName(content.getName() + "-updated");

        ContentDTO content2 = Contents.random();
        ContentDTO content3 = Contents.random()
            .arches("i686,x86_64");

        ProductDTO product2 = this.addContentToProduct(Products.randomEng(), content2);
        ProductDTO product3 = this.addContentToProduct(Products.randomEng(), content2, content3);

        exportGenerator.addProducts(product2, product3);

        // Import updated manifest
        AsyncJobStatusDTO importJob2 = this.importAsync(owner, exportGenerator.export());
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");
    }

    @Test
    public void shouldRemoveAllImportedSubscriptionsIfImportHasNoEntitlements() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ContentDTO content = Contents.random();
        ProductDTO product = this.addContentToProduct(Products.randomEng(), content);

        ExportGenerator exportGenerator = new ExportGenerator()
            .addProduct(product);

        AsyncJobStatusDTO importJob1 = this.importAsync(owner, exportGenerator.export());
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        List<SubscriptionDTO> importedSubs = ownerApi.getOwnerSubscriptions(owner.getKey());
        assertThat(importedSubs)
            .isNotNull()
            .hasSize(1);

        // Clear the subs from our manifest generator and re-import
        exportGenerator.clear();

        AsyncJobStatusDTO importJob2 = this.importAsync(owner, exportGenerator.export());
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS_WITH_WARNING")
            .contains("No active subscriptions found in the file");

        List<SubscriptionDTO> updatedSubs = ownerApi.getOwnerSubscriptions(owner.getKey());
        assertThat(updatedSubs)
            .isNotNull()
            .isEmpty();
    }

    /**
     * Builds a consumer capable of generating manifests via export APIs
     *
     * @param owner
     *  the owner for which to build a manifest consumer
     *
     * @return
     *  a consumer capable of creating manifests within the given owner
     */
    private ConsumerDTO buildDistributorConsumer(OwnerDTO owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        // Gather capabilities from the status endpoint
        Set<DistributorVersionCapabilityDTO> capabilities = adminClient.status().status()
            .getManagerCapabilities()
            .stream()
            .map(capability -> new DistributorVersionCapabilityDTO().name(capability))
            .collect(Collectors.toSet());

        DistributorVersionDTO distver = new DistributorVersionDTO()
            .name(StringUtil.random("manifest_dist"))
            .displayName("SAM")
            .capabilities(capabilities);

        distver = adminClient.distributorVersions().create(distver);

        // Create the consumer
        ConsumerDTO consumer = new ConsumerDTO()
            .name(StringUtil.random("distributor-", 8, StringUtil.CHARSET_NUMERIC_HEX))
            .type(ConsumerTypes.Candlepin.value())
            .owner(Owners.toNested(owner))
            .putFactsItem("system.certificate_version", "3.3")
            .putFactsItem("distributor_version", distver.getName());

        return adminClient.consumers().createConsumer(consumer);
    }

    @Test
    public void shouldBeAbleToMaintainMultipleImportedEntitlementsFromTheSamePool() throws Exception {
        // This test is a bit painful with the restriction that we can't create "global" data
        // manually via the API anymore. To get around this we use the fact that Candlepin will
        // gleefully let us chain manifests. So we use the manifest generator to generate a
        // manifest, then import it and use the global data it creates to build the manifest we
        // need for this test.
        OwnerDTO distributorOwner = adminClient.owners().createOwner(Owners.random());

        ProductDTO product = Products.random();

        SubscriptionDTO sub = Subscriptions.random()
            .product(product)
            .quantity(10L);

        File distributorManifest = new ExportGenerator()
            .addSubscription(sub)
            .export();

        AsyncJobStatusDTO importJob1 = this.importAsync(distributorOwner, distributorManifest);
        assertThatJob(importJob1)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        List<PoolDTO> distributorPools = adminClient.pools().listPoolsByOwner(distributorOwner.getId());
        assertThat(distributorPools)
            .singleElement()
            .returns(product.getId(), PoolDTO::getProductId)
            .returns(10L, PoolDTO::getQuantity);

        PoolDTO subPool = distributorPools.get(0);

        // We want to consume the subscription pool twice so we end up with two entitlements for it
        ConsumerDTO distributor = this.buildDistributorConsumer(distributorOwner);
        adminClient.consumers().bindPool(distributor.getUuid(), subPool.getId(), 2);
        adminClient.consumers().bindPool(distributor.getUuid(), subPool.getId(), 3);

        List<EntitlementDTO> entitlements = adminClient.consumers().listEntitlements(distributor.getUuid());
        assertThat(entitlements)
            .hasSize(2)
            .map(EntitlementDTO::getPool)
            .map(PoolDTO::getId)
            .containsOnly(subPool.getId());

        List<Long> quantities = entitlements.stream()
            .map(EntitlementDTO::getQuantity)
            .map(Long::valueOf)
            .toList();

        File manifest = adminClient.consumers().exportData(distributor.getUuid(), null, null, null);

        // Import the manifest into a new org and verify we get one pool for each of the
        // distributor's entitlements
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        AsyncJobStatusDTO importJob2 = this.importAsync(owner, manifest);
        assertThatJob(importJob2)
            .isFinished()
            .contains("SUCCESS")
            .doesNotContain("_WITH_WARNING");

        List<PoolDTO> pools = adminClient.pools().listPoolsByOwner(owner.getId());
        assertThat(pools)
            .hasSize(2)
            .allSatisfy(pool -> assertEquals(product.getId(), pool.getProductId()))
            .map(PoolDTO::getQuantity)
            .containsExactlyInAnyOrderElementsOf(quantities);
    }

}
