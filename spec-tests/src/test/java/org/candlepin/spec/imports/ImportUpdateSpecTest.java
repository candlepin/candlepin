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

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Export;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@SpecTest
public class ImportUpdateSpecTest {

    private static ApiClient admin;
    private static OwnerClient ownerApi;
    private static ConsumerClient consumerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
        ownerApi = admin.owners();
        consumerApi = admin.consumers();
        ownerContentApi = admin.ownerContent();
        ownerProductApi = admin.ownerProducts();
    }

    @Test
    public void shouldUpdateContentFromManifest() {
        ProductDTO product = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("5"))
            .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"))
            .addAttributesItem(new AttributeDTO().name("host_limited").value("true"));
        ContentDTO content = Contents.random();
        ExportGenerator exportGenerator = new ExportGenerator(admin);
        Export export = exportGenerator.minimal().withProduct(product, content).export();
        String exportOwnerKey = exportGenerator.getExportConsumer().getOwner().getKey();

        OwnerDTO importOwner = Owners.random();
        importOwner = admin.owners().createOwner(importOwner);

        // Create a pre-existing content instance that will get overwritten by the content from the manifest
        ContentDTO fromExport = ownerContentApi.getOwnerContent(exportOwnerKey, content.getId());
        ContentDTO preexistingContent = new ContentDTO().name(fromExport.getName())
            .id(fromExport.getId())
            .label(fromExport.getLabel())
            .type(fromExport.getType())
            .vendor(fromExport.getVendor())
            .requiredTags("dummy tags");
        preexistingContent =  ownerContentApi.createContent(importOwner.getKey(), preexistingContent);

        importAsync(importOwner.getKey(), export.file());
        // Verify the manifest changed our content
        ContentDTO updatedContent = ownerContentApi.getOwnerContent(importOwner.getKey(),
            preexistingContent.getId());
        assertNotNull(updatedContent);
        assertNotEquals(updatedContent, preexistingContent);
    }

    @Test
    public void shouldSuccessfullyUpdateTheImport() {
        ProductDTO product = Products.randomEng();
        ContentDTO content = Contents.random();
        ExportGenerator exportGenerator = new ExportGenerator(admin);
        Export export = exportGenerator.minimal().withProduct(product, content).export();

        OwnerDTO importOwner = Owners.random();
        importOwner = admin.owners().createOwner(importOwner);
        importAsync(importOwner.getKey(), export.file());

        List<SubscriptionDTO> subs = admin.owners().getOwnerSubscriptions(importOwner.getKey());
        ProductDTO originalProduct = ownerProductApi.getProductByOwner(importOwner.getKey(),
            product.getId());
        assertNotNull(originalProduct);
        ContentDTO originalContent = ownerContentApi.getOwnerContent(importOwner.getKey(),
            content.getId());
        assertNotNull(originalContent);

        updateExport(exportGenerator, product, content);
        Export updatedExport = exportGenerator.export();
        importAsync(importOwner.getKey(), updatedExport.file());

        assertThat(subs).hasSize(1);
        List<SubscriptionDTO> newSubs = admin.owners().getOwnerSubscriptions(importOwner.getKey());
        assertThat(newSubs).hasSize(3);

        // ensure that the original sub doesn't have the same serial id
        SubscriptionDTO oldSub = subs.get(0);
        boolean found = false;
        List<SubscriptionDTO> matches = newSubs.stream()
            .filter(x -> x.getId().equals(oldSub.getId()))
            .collect(Collectors.toList());
        if (matches != null && matches.size() > 0) {
            assertNotEquals(matches.get(0).getCertificate().getSerial().getId(),
                oldSub.getCertificate().getSerial().getId());
            found = true;
        }
        // make sure that the original sub was in the new list
        assertTrue(found);

        ProductDTO updatedProduct = ownerProductApi.getProductByOwner(importOwner.getKey(),
            product.getId());
        ContentDTO updatedContent = ownerContentApi.getOwnerContent(importOwner.getKey(),
            content.getId());
        assertNotNull(updatedProduct);
        assertNotNull(updatedContent);
        assertNotEquals(updatedProduct, originalProduct);
        assertNotEquals(updatedContent, originalContent);
    }

    @Test
    public void shouldSuccessfullyUpdateTheImportExcess() throws Exception {
        ProductDTO product = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("5"))
            .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"))
            .addAttributesItem(new AttributeDTO().name("host_limited").value("true"));
        ContentDTO content = Contents.random();
        ExportGenerator exportGenerator = new ExportGenerator(admin);
        Export export = exportGenerator.minimal().withProduct(product, content).export();

        OwnerDTO importOwner = Owners.random();
        importOwner = admin.owners().createOwner(importOwner);
        UserDTO importUser = UserUtil.createUser(admin, importOwner);
        ApiClient importUserClient = ApiClients.basic(importUser);

        importAsync(importOwner.getKey(), export.file());

        ConsumerDTO consumer = Consumers.random(importOwner);
        consumer = importUserClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<PoolDTO> allPools = importUserClient.pools().listPoolsByOwner(importOwner.getId());
        assertThat(allPools).hasSize(2);
        PoolDTO normalPool = allPools.stream()
            .filter(x -> "NORMAL".equals(x.getType()))
            .collect(Collectors.toList()).get(0);

        consumerClient.consumers().bindPool(consumer.getUuid(), normalPool.getId(), 1);
        allPools = importUserClient.pools().listPoolsByOwner(importOwner.getId());
        assertThat(allPools).hasSize(3);
        // Sleep to create a gap between when the manifest was initially imported
        sleep(1000);

        updateExport(exportGenerator, product, content);
        Export updatedExport = exportGenerator.export();
        importAsync(importOwner.getKey(), updatedExport.file());
    }

    @Test
    public void shouldRemoveAllImportedSubscriptionsIfImportHasNoEntitlements() throws Exception {
        ProductDTO product = Products.randomEng();
        ContentDTO content = Contents.random();
        ExportGenerator exportGenerator = new ExportGenerator(admin);
        Export export = exportGenerator.minimal().withProduct(product, content).export();

        OwnerDTO importOwner = Owners.random();
        importOwner = admin.owners().createOwner(importOwner);
        importAsync(importOwner.getKey(), export.file());

        //  The manifest metadata can end up with a created date that is a fraction of a second ahead of
        //  the created date in the cp_export_metadata table. This results into the manifest metadata
        //  conflict with error "Import is the same as existing data". Hence to avoid this, adding a sleep
        //  before creating another export.
        Thread.sleep(2000);
        consumerApi.unbindAll(exportGenerator.getExportConsumer().getUuid());
        Export updatedExport = exportGenerator.export();
        importAsync(importOwner.getKey(), updatedExport.file());

        assertThat(consumerApi.listEntitlements(exportGenerator.getExportConsumer().getUuid())).hasSize(0);
        assertThat(admin.owners().getOwnerSubscriptions(importOwner.getKey())).hasSize(0);
    }

    private void importAsync(String ownerKey, File export) throws ApiException {
        AsyncJobStatusDTO importJob = admin.owners().importManifestAsync(ownerKey, export);
        admin.jobs().waitForJob(importJob);
    }

    public void updateExport(ExportGenerator exportGenerator, ProductDTO product, ContentDTO content) {
        ConsumerDTO consumer = exportGenerator.getExportConsumer();
        String ownerKey = consumer.getOwner().getKey();

        ownerProductApi.updateProductByOwner(ownerKey, product.getId(),
            product.name(product.getName() + "-updated"));
        ownerContentApi.updateContent(ownerKey, content.getId(),
            content.requiredTags("TAG2,TAG4,TAG6"));
        ownerProductApi.addContent(ownerKey, product.getId(), content.getId(), true);

        ProductDTO product1 = ownerProductApi.createProductByOwner(ownerKey, Products.randomEng());
        ProductDTO product2 = ownerProductApi.createProductByOwner(ownerKey, Products.randomEng());
        ContentDTO content1 = new ContentDTO()
            .id(StringUtil.random("id"))
            .type(StringUtil.random("type"))
            .name(StringUtil.random("name"))
            .label(StringUtil.random("label"))
            .vendor(StringUtil.random("vendor"))
            .metadataExpire(6000L)
            .requiredTags("TAG1,TAG2");
        ownerContentApi.createContent(ownerKey, content1);
        ContentDTO archContent = new ContentDTO()
            .id(StringUtil.random("id"))
            .type(StringUtil.random("type"))
            .name(StringUtil.random("name"))
            .label(StringUtil.random("label"))
            .vendor(StringUtil.random("vendor"))
            .metadataExpire(6000L)
            .requiredTags("TAG3")
            .arches("i686,x86_64");
        ownerContentApi.createContent(ownerKey, archContent);
        ownerProductApi.addContent(ownerKey, product1.getId(), content1.getId(), true);
        ownerProductApi.addContent(ownerKey, product2.getId(), content1.getId(), true);
        ownerProductApi.addContent(ownerKey, product2.getId(), archContent.getId(), true);

        EntitlementDTO ent1 = consumerApi.listEntitlements(consumer.getUuid()).get(0);
        consumerApi.regenerateEntitlementCertificates(consumer.getUuid(), ent1.getId(), false);
        consumerApi.listEntitlements(consumer.getUuid());

        PoolDTO pool1 = Pools.random().productId(product1.getId())
            .quantity(12L)
            .contractNumber("")
            .accountNumber("12345")
            .orderNumber("6789")
            .endDate(OffsetDateTime.now().plusYears(1L))
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        pool1 = ownerApi.createPool(ownerKey, pool1);
        consumerApi.bindPool(consumer.getUuid(), pool1.getId(), 1);
        PoolDTO pool2 = Pools.random().productId(product2.getId())
            .quantity(14L)
            .contractNumber("")
            .accountNumber("12345")
            .orderNumber("6789")
            .endDate(OffsetDateTime.now().plusYears(1L))
            .subscriptionId(StringUtil.random("source_sub"))
            .subscriptionSubKey(StringUtil.random("sub_key"))
            .upstreamPoolId(StringUtil.random("upstream"));
        pool2 = ownerApi.createPool(ownerKey, pool2);
        consumerApi.bindPool(consumer.getUuid(), pool2.getId(), 1);
    }
}
