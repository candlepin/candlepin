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
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.v1.DistributorVersionDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProvidedProductDTO;
import org.candlepin.dto.api.v1.SubscriptionDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.OwnerContentApi;
import org.candlepin.resource.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Content;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.X509HuffmanDecodeUtil;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class EntitlementCertificateV3SpecTest {

    private static ApiClient client;
    private static OwnerDTO owner;
    private static ConsumerDTO system;
    private static ProductDTO product;
    private static ProductDTO product30;
    private static ContentDTO content;
    private static ContentDTO archContent;
    private static PoolDTO pool;
    private static PoolDTO pool30;

    private static SubscriptionDTO subscription;
    private static SubscriptionDTO subscription30;
    private OwnerClient ownerApi;
    private OwnerContentApi ownerContentApi;
    private OwnerProductApi ownerProductApi;
    private ConsumerClient consumerApi;
    private HostedTestApi hostedTestApi;

    @BeforeEach
    public void beforeEach() throws ApiException {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
        consumerApi = client.consumers();
        if (CandlepinMode.isHosted()) {
            hostedTestApi = client.hosted();
        }
        product = Products.randomEng().name(StringUtil.random("Test Product"))
            .addAttributesItem(new AttributeDTO().name("version").value("6.4"))
            .addAttributesItem(new AttributeDTO().name("arch").value("i386, x86_64"))
            .addAttributesItem(new AttributeDTO().name("sockets").value("4"))
            .addAttributesItem(new AttributeDTO().name("cores").value("8"))
            .addAttributesItem(new AttributeDTO().name("ram").value("16"))
            .addAttributesItem(new AttributeDTO().name("usage").value("Disaster Recovery"))
            .addAttributesItem(new AttributeDTO().name("roles")
                .value("Red Hat Enterprise Linux Server, Red Hat Enterprise Linux Workstation"))
            .addAttributesItem(new AttributeDTO().name("addons")
                .value("my_server_addon, my_workstation_addon"))
            .addAttributesItem(new AttributeDTO().name("warning_period").value("15"))
            .addAttributesItem(new AttributeDTO().name("management_enabled").value("true"))
            .addAttributesItem(new AttributeDTO().name("stacking_id").value("8888"))
            .addAttributesItem(new AttributeDTO().name("virt_only").value("false"))
            .addAttributesItem(new AttributeDTO().name("support_level").value("standard"))
            .addAttributesItem(new AttributeDTO().name("support_type").value("excellent"));
        product30 = Products.randomEng().name(StringUtil.random("Test Product"))
            .addAttributesItem(new AttributeDTO().name("version").value("6.4"))
            .addAttributesItem(new AttributeDTO().name("arch").value("i386, x86_64"))
            .addAttributesItem(new AttributeDTO().name("sockets").value("4"))
            .addAttributesItem(new AttributeDTO().name("warning_period").value("15"))
            .addAttributesItem(new AttributeDTO().name("management_enabled").value("true"))
            .addAttributesItem(new AttributeDTO().name("virt_only").value("false"))
            .addAttributesItem(new AttributeDTO().name("support_level").value("standard"))
            .addAttributesItem(new AttributeDTO().name("support_type").value("excellent"));

        content = Content.random()
            .type("yum")
            .gpgUrl("gpg_url")
            .contentUrl("/content/dist/rhel/$releasever/$basearch/os")
            .metadataExpire(6400L)
            .requiredTags("TAG1,TAG2");
        archContent = Content.random()
            .type("yum")
            .gpgUrl("gpg_url")
            .contentUrl("/content/dist/rhel/arch/specific/$releasever/$basearch/os")
            .metadataExpire(6400L)
            .arches("i386,x86_64")
            .requiredTags("TAG1,TAG2");

        owner = ownerApi.createOwner(Owners.random());
        if (CandlepinMode.isHosted()) {
            hostedTestApi.createOwner(owner);
            product = hostedTestApi.createProduct(product);
            product30 = hostedTestApi.createProduct(product30);
            content = hostedTestApi.createContent(content);
            archContent = hostedTestApi.createContent(archContent);
            hostedTestApi.addContentToProduct(product.getId(), Map.of(content.getId(), false));
            hostedTestApi.addContentToProduct(product.getId(), Map.of(archContent.getId(), false));
            subscription = Subscriptions.random(owner, product)
                .contractNumber("12345")
                .accountNumber("6789")
                .orderNumber("order1");
            subscription = hostedTestApi.createSubscription(subscription);
            subscription30 = Subscriptions.random(owner, product30)
                .contractNumber("123456")
                .accountNumber("67890")
                .orderNumber("order2");
            subscription30 = hostedTestApi.createSubscription(subscription30);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                client.jobs().waitForJob(refresh.getId());
            }
        }
        else {
            product = ownerProductApi.createProductByOwner(owner.getKey(), product);
            product30 = ownerProductApi.createProductByOwner(owner.getKey(), product30);
            content = ownerContentApi.createContent(owner.getKey(), content);
            archContent = ownerContentApi.createContent(owner.getKey(), archContent);
            product = ownerProductApi.addContent(owner.getKey(), product.getId(), content.getId(), false);
            product = ownerProductApi.addContent(owner.getKey(), product.getId(), archContent.getId(), false);
            pool = Pools.random(product)
                .contractNumber("12345")
                .accountNumber("6789")
                .orderNumber("order1");
            pool = ownerApi.createPool(owner.getKey(), pool);
            pool30 = Pools.random(product30)
                .contractNumber("123456")
                .accountNumber("67890")
                .orderNumber("order2");
            pool30 = ownerApi.createPool(owner.getKey(), pool30);
        }
        system = Consumers.random(owner)
            .name(StringUtil.random("system"))
            .type(new ConsumerTypeDTO().label("system"))
            .facts(Map.of("system.certificate_version", "3.4", "uname.machine", "i386"));
        system = consumerApi.register(system);
    }

    /**
     * This test covers the case where the system supports 3.0 certs, but
     * the server is creating 3.4 certs, and the product contains attributes
     * supported by 3.0.
     * @throws Exception
     */
    @Test
    @DisplayName("should generate a version 3.4 certificate when requesting a 3.0 certificate")
    public void shouldGenerateThreeFourCertRequestThreeZero() throws Exception {
        ConsumerDTO v3System = Consumers.random(owner)
            .name(StringUtil.random("system"))
            .type(new ConsumerTypeDTO().label("system"))
            .facts(Map.of("system.certificate_version", "3.0", "uname.machine", "i386"));
        v3System = consumerApi.register(v3System);
        consumerApi.bindProduct(v3System.getUuid(), product30.getId());
        String value = CertificateUtil.standardExtensionValueFromCert(
            consumerApi.listEntitlements(v3System.getUuid())
            .get(0).getCertificates().iterator().next().getCert(), "1.3.6.1.4.1.2312.9.6");
        assertEquals("3.4", value);
    }

    @Test
    @DisplayName("should generated a version 3.4 certificate")
    public void shouldGenerateThreeFourCert() throws Exception {
        consumerApi.bindProduct(system.getUuid(), product.getId());
        EntitlementDTO ent = consumerApi.listEntitlements(system.getUuid()).get(0);
        String value = CertificateUtil.standardExtensionValueFromCert(
            ent.getCertificates().iterator().next().getCert(), "1.3.6.1.4.1.2312.9.6");
        assertEquals("3.4", value);
    }

    @Test
    @DisplayName("should generate a version 3.4 certificate on distributors with a cert_v3 capability")
    public void shouldGenerateThreeFourCertForCapableDistributor() throws Exception {
        String distName = StringUtil.random("SAMvBillion");
        DistributorVersionDTO version = new DistributorVersionDTO()
            .name(distName)
            .displayName("Subscription Asset Manager Billion")
            .capabilities(Set.of(new DistributorVersionCapabilityDTO().name("cert_v3")));
        version = client.distributorVersions().create(version);
        ConsumerDTO distributor = Consumers.random(owner)
            .name(StringUtil.random("v3_system"))
            .type(ConsumerTypes.Candlepin.value())
            .facts(Map.of("distributor_version", distName));
        distributor = consumerApi.register(distributor);
        ApiClient consumerClient = ApiClients.trustedConsumer(distributor.getUuid());
        consumerApi = consumerClient.consumers();
        consumerApi.bindProduct(distributor.getUuid(), product30.getId());
        EntitlementDTO ent = consumerApi.listEntitlements(distributor.getUuid()).get(0);
        String value = CertificateUtil.standardExtensionValueFromCert(
            ent.getCertificates().iterator().next().getCert(), "1.3.6.1.4.1.2312.9.6");
        assertEquals("3.4", value);
        client.distributorVersions().delete(version.getId());
    }

    @Test
    @DisplayName("should generate the correct body in the blob")
    public void shouldGenerateCorrectBodyInTheBlob() throws Exception {
        consumerApi.bindProduct(system.getUuid(), product.getId());
        List<JsonNode> jsonNodes = consumerApi.exportCertificates(system.getUuid(), null);
        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertEquals(system.getUuid(), jsonBody.get("consumer").asText());
        assertEquals(1, jsonBody.get("quantity").asInt());
        assertNotNull(jsonBody.get("pool").get("id"));
        JsonNode subscription = jsonBody.get("subscription");
        assertEquals(product.getId(), subscription.get("sku").asText());
        assertEquals(product.getName(), subscription.get("name").asText());
        assertEquals(15, subscription.get("warning").asInt());
        assertEquals(4, subscription.get("sockets").asInt());
        assertEquals(8, subscription.get("cores").asInt());
        assertEquals(16, subscription.get("ram").asInt());
        assertEquals("Disaster Recovery", subscription.get("usage").asText());
        String roles = subscription.get("roles").toString();
        assert roles.contains("Red Hat Enterprise Linux Server");
        assert roles.contains("Red Hat Enterprise Linux Workstation");
        String addons = subscription.get("addons").toString();
        assert addons.contains("my_server_addon");
        assert addons.contains("my_workstation_addon");
        assertEquals(true, subscription.get("management").asBoolean());
        assertEquals("8888", subscription.get("stacking_id").asText());
        assertNull(subscription.get("virt_only"));
        assertEquals("standard", subscription.get("service").get("level").asText());
        assertEquals("excellent", subscription.get("service").get("type").asText());

        JsonNode order = jsonBody.get("order");
        assertEquals("order1", order.get("number").asText());
        assertEquals(10, order.get("quantity").asInt());
        assertNotNull(order.get("start"));
        assertNotNull(order.get("end"));
        assertEquals("12345", order.get("contract").asText());
        assertEquals("6789", order.get("account").asText());

        JsonNode blobProduct = jsonBody.get("products").get(0);
        assertEquals(product.getId(), blobProduct.get("id").asText());
        assertEquals(product.getName(), blobProduct.get("name").asText());
        assertEquals("6.4", blobProduct.get("version").asText());
        assertThat(blobProduct.get("architectures")).hasSize(2);
        assertNull(blobProduct.get("brandName"));
        assertNull(blobProduct.get("brandType"));

        JsonNode regRetContent = null;
        JsonNode archRetContent = null;
        JsonNode blobContents = blobProduct.get("content");
        for (JsonNode blobContent : blobContents) {
            if (content.getId().equals(blobContent.get("id").asText())) {
                regRetContent = blobContent;
            }
            else if (archContent.getId().equals(blobContent.get("id").asText())) {
                archRetContent = blobContent;
            }
        }

        assertEquals("yum", regRetContent.get("type").asText());
        assertEquals(content.getName(), regRetContent.get("name").asText());
        assertEquals(content.getLabel(), regRetContent.get("label").asText());
        assertEquals(content.getVendor(), regRetContent.get("vendor").asText());
        assertEquals(content.getGpgUrl(), regRetContent.get("gpg_url").asText());
        assertEquals("/content/dist/rhel/$releasever/$basearch/os",
            regRetContent.get("path").asText());
        assertEquals(false, regRetContent.get("enabled").asBoolean());
        assertEquals(6400, regRetContent.get("metadata_expire").asInt());
        assertThat(regRetContent.get("required_tags")).hasSize(2);

        assertEquals("yum", archRetContent.get("type").asText());
        assertEquals(archContent.getName(), archRetContent.get("name").asText());
        assertEquals(archContent.getLabel(), archRetContent.get("label").asText());
        assertEquals(archContent.getVendor(), archRetContent.get("vendor").asText());
        assertEquals(archContent.getGpgUrl(), archRetContent.get("gpg_url").asText());
        assertEquals("/content/dist/rhel/arch/specific/$releasever/$basearch/os",
            archRetContent.get("path").asText());
        assertEquals(false, archRetContent.get("enabled").asBoolean());
        assertEquals(6400, archRetContent.get("metadata_expire").asInt());
        assertThat(archRetContent.get("required_tags")).hasSize(2);
        assertThat(archRetContent.get("arches")).hasSize(2);
        String arches = archRetContent.get("arches").toString();
        assert arches.contains("i386");
        assert arches.contains("x86_64");
    }

    @Test
    @DisplayName("should have correct branding info in json blob")
    public void shouldHaveCorrectBrandingInTheBlob() throws Exception {
        BrandingDTO branding = null;
        ProductDTO mktProduct = null;
        ProductDTO engProduct = null;
        if (CandlepinMode.isHosted()) {
            engProduct = Products.randomEng()
                .name("engineering_product_name");
            engProduct = hostedTestApi.createProduct(engProduct);
            branding = Branding.random("Super Branded Name")
                .type("Some Type")
                .productId(engProduct.getId());
            mktProduct = Products.randomSKU()
                .name("marketing_product_name")
                .branding(Set.of(branding))
                .providedProducts(Set.of(engProduct));
            mktProduct = hostedTestApi.createProduct(mktProduct);
            subscription = Subscriptions.random(owner, mktProduct)
                .contractNumber("12345")
                .accountNumber("6789")
                .orderNumber("order1")
                .providedProducts(Set.of(engProduct));
            subscription = hostedTestApi.createSubscription(subscription);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                client.jobs().waitForJob(refresh.getId());
            }
            List<PoolDTO> pools = ownerApi.listOwnerPools(owner.getKey());
            for (PoolDTO subPool : pools) {
                if (subPool.getProductId().equals(mktProduct.getId())) {
                    pool = subPool;
                }
            }
        }
        else {
            engProduct = Products.randomEng()
                .name("engineering_product_name");
            engProduct = ownerProductApi.createProductByOwner(owner.getKey(), engProduct);
            branding = Branding.random("Super Branded Name")
                .type("Some Type")
                .productId(engProduct.getId());
            mktProduct = Products.randomSKU()
                .name("marketing_product_name")
                .branding(Set.of(branding))
                .providedProducts(Set.of(engProduct));
            mktProduct = ownerProductApi.createProductByOwner(owner.getKey(), mktProduct);
            ProvidedProductDTO engProvidedProduct = (new ProvidedProductDTO())
                .productName(engProduct.getName())
                .productId(engProduct.getId());
            pool = Pools.random(mktProduct)
                .contractNumber("12345")
                .accountNumber("6789")
                .orderNumber("order1")
                .providedProducts(Set.of(engProvidedProduct));
            pool = ownerApi.createPool(owner.getKey(), pool);
        }

        assertThat(pool.getBranding()).hasSize(1);
        consumerApi.bindProduct(system.getUuid(), engProduct.getId());
        List<JsonNode> jsonNodes = consumerApi.exportCertificates(system.getUuid(), null);
        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertEquals(mktProduct.getName(), jsonBody.get("subscription").get("name").asText());
        assertEquals(system.getUuid(), jsonBody.get("consumer").asText());
        // Verify branding info
        assertEquals(branding.getName(), jsonBody.get("products").get(0).get("brand_name").asText());
        assertEquals(branding.getType(), jsonBody.get("products").get(0).get("brand_type").asText());
    }

    @Test
    @DisplayName("should encode the content urls")
    public void shouldEncodeTheContentUrls() throws Exception {
        ContentDTO content1 = Content.random();
        content1.setContentUrl("/content/dist/rhel/$releasever/$basearch/debug");
        ContentDTO content2 = Content.random();
        content2.setContentUrl("/content/beta/rhel/$releasever/$basearch/source/SRPMS");

        if (CandlepinMode.isHosted()) {
            content1 = hostedTestApi.createContent(content1);
            content2 = hostedTestApi.createContent(content2);
            hostedTestApi.addContentToProduct(product.getId(), Map.of(content1.getId(), true));
            hostedTestApi.addContentToProduct(product.getId(), Map.of(content2.getId(), true));
            subscription = Subscriptions.random(owner, product);
            subscription = hostedTestApi.createSubscription(subscription);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                client.jobs().waitForJob(refresh.getId());
            }
        }
        else {
            content1 = ownerContentApi.createContent(owner.getKey(), content1);
            product = ownerProductApi.addContent(owner.getKey(), product.getId(), content1.getId(), true);
            content2 = ownerContentApi.createContent(owner.getKey(), content2);
            product = ownerProductApi.addContent(owner.getKey(), product.getId(), content2.getId(), true);
            pool = Pools.random();
            pool.setProductId(product.getId());
            pool.setProductName(product.getName());
            pool = ownerApi.createPool(owner.getKey(), pool);
        }
        ApiClient consumerClient = ApiClients.trustedConsumer(system.getUuid());
        consumerApi = consumerClient.consumers();
        JsonNode bindResult = consumerApi.bindProduct(system.getUuid(), product.getId());

        List<JsonNode> jsonNodes = consumerApi.exportCertificates(system.getUuid(), null);
        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertThat(jsonBody.get("products").get(0).get("content")).hasSize(4);

        byte[] value = CertificateUtil.compressedContentExtensionValueFromCert(bindResult.get(0)
            .get("certificates").get(0).get("cert").toString(), "1.3.6.1.4.1.2312.9.7");
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);

        assertThat(urls).containsAll(List.of("/content/dist/rhel/$releasever/$basearch/os",
            "/content/dist/rhel/$releasever/$basearch/debug",
            "/content/beta/rhel/$releasever/$basearch/source/SRPMS"));
    }

    @Test
    @DisplayName("should encode many content urls")
    public void shouldEncodeManyContentUrls() throws Exception {
        for (int i = 0; i < 100; i++) {
            ContentDTO content = Content.random();
            content.setContentUrl(
                String.format("/content/dist/rhel/$releasever-%s/$basearch-%s/debug-%s", i, i, i));
            if (CandlepinMode.isHosted()) {
                content = hostedTestApi.createContent(content);
                hostedTestApi.addContentToProduct(product.getId(), Map.of(content.getId(), true));
            }
            else {
                content = ownerContentApi.createContent(owner.getKey(), content);
                product = ownerProductApi.addContent(owner.getKey(), product.getId(), content.getId(), true);
            }
        }

        if (CandlepinMode.isHosted()) {
            subscription = Subscriptions.random(owner, product);
            subscription = hostedTestApi.createSubscription(subscription);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                client.jobs().waitForJob(refresh.getId());
            }
        }
        else {
            pool = Pools.random();
            pool.setProductId(product.getId());
            pool.setProductName(product.getName());
            pool = ownerApi.createPool(owner.getKey(), pool);
        }
        ApiClient consumerClient = ApiClients.trustedConsumer(system.getUuid());
        consumerApi = consumerClient.consumers();
        JsonNode bindResult = consumerApi.bindProduct(system.getUuid(), product.getId());

        // confirm content count to cross-check
        List<JsonNode> jsonNodes = consumerApi.exportCertificates(system.getUuid(), null);
        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertThat(jsonBody.get("products")).hasSize(1);
        assertThat(jsonBody.get("products").get(0).get("content")).hasSize(102);

        // confirm encoded urls
        byte[] value = CertificateUtil.compressedContentExtensionValueFromCert(bindResult.get(0)
            .get("certificates").get(0).get("cert").toString(), "1.3.6.1.4.1.2312.9.7");
        X509HuffmanDecodeUtil decode = new X509HuffmanDecodeUtil();
        List<String> urls = decode.hydrateContentPackage(value);
        assertThat(urls).hasSize(102);

        // spot check the data
        assertThat(urls).containsAll(List.of("/content/dist/rhel/$releasever-0/$basearch-0/debug-0",
            "/content/dist/rhel/$releasever-29/$basearch-29/debug-29",
            "/content/dist/rhel/$releasever-41/$basearch-41/debug-41",
            "/content/dist/rhel/$releasever-75/$basearch-75/debug-75",
            "/content/dist/rhel/$releasever-99/$basearch-99/debug-99"));
    }
}
