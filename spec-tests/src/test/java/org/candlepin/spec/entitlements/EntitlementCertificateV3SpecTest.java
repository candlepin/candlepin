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
package org.candlepin.spec.entitlements;


import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.CertificateAssert.assertThatCert;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.BrandingDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.data.builder.Branding;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.OID;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;


// TODO: FIXME:
// This test suite needs to be rewritten to separate its flows between standalone and hosted modes.
// Additionally, the tests should not be using shared, global data.

@SpecTest
@SuppressWarnings("indentation")
public class EntitlementCertificateV3SpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerContentApi ownerContentApi;
    private static OwnerProductApi ownerProductApi;
    private static ConsumerClient consumerApi;
    private static HostedTestApi hostedTestApi;

    private OwnerDTO owner;
    private ConsumerDTO system;
    private ProductDTO product;
    private ProductDTO product30;
    private ContentDTO content;
    private ContentDTO archContent;
    private PoolDTO pool;

    private SubscriptionDTO subscription;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerContentApi = client.ownerContent();
        ownerProductApi = client.ownerProducts();
        consumerApi = client.consumers();
        hostedTestApi = client.hosted();
    }

    @BeforeEach
    public void beforeEach() {
        product = Products.randomEng()
            .attributes(List.of(
                ProductAttributes.Version.withValue("6.4"),
                ProductAttributes.Arch.withValue("i386, x86_64"),
                ProductAttributes.Sockets.withValue("4"),
                ProductAttributes.Cores.withValue("8"),
                ProductAttributes.Ram.withValue("16"),
                ProductAttributes.Usage.withValue("Disaster Recovery"),
                ProductAttributes.Roles
                    .withValue("Red Hat Enterprise Linux Server, Red Hat Enterprise Linux Workstation"),
                ProductAttributes.Addons
                    .withValue("my_server_addon, my_workstation_addon"),
                ProductAttributes.WarningPeriod.withValue("15"),
                ProductAttributes.ManagementEnabled.withValue("true"),
                ProductAttributes.StackingId.withValue("8888"),
                ProductAttributes.VirtualOnly.withValue("false"),
                ProductAttributes.SupportLevel.withValue("standard"),
                ProductAttributes.SupportType.withValue("excellent")
            ));
        product30 = Products.randomEng()
            .attributes(List.of(
                ProductAttributes.Version.withValue("6.4"),
                ProductAttributes.Arch.withValue("i386, x86_64"),
                ProductAttributes.Sockets.withValue("4"),
                ProductAttributes.WarningPeriod.withValue("15"),
                ProductAttributes.ManagementEnabled.withValue("true"),
                ProductAttributes.VirtualOnly.withValue("false"),
                ProductAttributes.SupportLevel.withValue("standard"),
                ProductAttributes.SupportType.withValue("excellent")
            ));

        content = Contents.random()
            .type("yum")
            .gpgUrl("gpg_url")
            .contentUrl("/content/dist/rhel/$releasever/$basearch/os")
            .metadataExpire(6400L)
            .requiredTags("TAG1,TAG2");
        archContent = Contents.random()
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
            subscription = hostedTestApi.createSubscription(Subscriptions.random(owner, product)
                .contractNumber("12345")
                .accountNumber("6789")
                .orderNumber("order1"));
            hostedTestApi.createSubscription(Subscriptions.random(owner, product30)
                .contractNumber("123456")
                .accountNumber("67890")
                .orderNumber("order2"));
            AsyncJobStatusDTO refreshJob = ownerApi.refreshPools(owner.getKey(), false);
            assertThatJob(refreshJob)
                .isNotNull()
                .terminates(client)
                .isFinished();
        }
        else {
            product = ownerProductApi.createProduct(owner.getKey(), product);
            product30 = ownerProductApi.createProduct(owner.getKey(), product30);
            content = ownerContentApi.createContent(owner.getKey(), content);
            archContent = ownerContentApi.createContent(owner.getKey(), archContent);
            product = ownerProductApi.addContentToProduct(owner.getKey(), product.getId(),
                content.getId(), false);
            product = ownerProductApi.addContentToProduct(owner.getKey(), product.getId(),
                archContent.getId(), false);
            pool = ownerApi.createPool(owner.getKey(), Pools.random(product)
                .contractNumber("12345")
                .accountNumber("6789")
                .orderNumber("order1"));
            ownerApi.createPool(owner.getKey(), Pools.random(product30)
                .contractNumber("123456")
                .accountNumber("67890")
                .orderNumber("order2"));
        }

        system = consumerApi.createConsumer(Consumers.random(owner)
            .name(StringUtil.random("system"))
            .type(new ConsumerTypeDTO().label("system"))
            .facts(Map.of("system.certificate_version", "3.4", "uname.machine", "i386")));
    }

    /**
     * This test covers the case where the system supports 3.0 certs, but
     * the server is creating 3.4 certs, and the product contains attributes
     * supported by 3.0.
     */
    @Test
    public void shouldGenerateThreeFourCertRequestThreeZero() {
        ConsumerDTO v3System = consumerApi.createConsumer(Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("system.certificate_version", "3.0", "uname.machine", "i386")));
        ApiClient v3SystemClient = ApiClients.ssl(v3System);
        v3SystemClient.consumers().bindProduct(v3System.getUuid(), product30.getId());

        List<EntitlementDTO> v3SystemEntitlements = v3SystemClient.consumers()
            .listEntitlements(v3System.getUuid());
        CertificateDTO certificate = v3SystemEntitlements.stream()
            .map(this::firstCertOf)
            .findFirst()
            .orElseThrow();

        assertThatCert(certificate)
            .hasVersion("3.4");
    }

    @Test
    public void shouldGenerateThreeFourCert() {
        consumerApi.bindProduct(system.getUuid(), product.getId());
        EntitlementDTO ent = consumerApi.listEntitlements(system.getUuid()).get(0);
        CertificateDTO certificate = firstCertOf(ent);

        assertThatCert(certificate)
            .hasVersion("3.4");
    }

    @Test
    public void shouldGenerateThreeFourCertForCapableDistributor() {
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
        distributor = consumerApi.createConsumer(distributor);
        ApiClient consumerClient = ApiClients.ssl(distributor);
        consumerClient.consumers().bindProduct(distributor.getUuid(), product30.getId());
        EntitlementDTO ent = consumerApi.listEntitlements(distributor.getUuid()).get(0);
        CertificateDTO certificate = firstCertOf(ent);

        assertThatCert(certificate)
            .hasVersion("3.4");

        client.distributorVersions().delete(version.getId());
    }

    @Test
    public void shouldGenerateCorrectBodyInTheBlob() {
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
        assertTrue(subscription.get("management").asBoolean());
        assertEquals("8888", subscription.get("stacking_id").asText());
        assertNull(subscription.get("virt_only"));
        assertEquals("standard", subscription.get("service").get("level").asText());
        assertEquals("excellent", subscription.get("service").get("type").asText());


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
        assertFalse(regRetContent.get("enabled").asBoolean());
        assertEquals(6400, regRetContent.get("metadata_expire").asInt());
        assertThat(regRetContent.get("required_tags")).hasSize(2);

        assertEquals("yum", archRetContent.get("type").asText());
        assertEquals(archContent.getName(), archRetContent.get("name").asText());
        assertEquals(archContent.getLabel(), archRetContent.get("label").asText());
        assertEquals(archContent.getVendor(), archRetContent.get("vendor").asText());
        assertEquals(archContent.getGpgUrl(), archRetContent.get("gpg_url").asText());
        assertEquals("/content/dist/rhel/arch/specific/$releasever/$basearch/os",
            archRetContent.get("path").asText());
        assertFalse(archRetContent.get("enabled").asBoolean());
        assertEquals(6400, archRetContent.get("metadata_expire").asInt());
        assertThat(archRetContent.get("required_tags")).hasSize(2);
        assertThat(archRetContent.get("arches")).hasSize(2);
        String arches = archRetContent.get("arches").toString();
        assert arches.contains("i386");
        assert arches.contains("x86_64");
    }

    @Test
    public void shouldHaveCorrectBrandingInTheBlob() {
        ProductDTO engProduct = Products.randomEng()
            .name("engineering_product_name");
        engProduct = ownerProductApi.createProduct(owner.getKey(), engProduct);
        BrandingDTO branding = Branding.random("Super Branded Name")
            .type("Some Type")
            .productId(engProduct.getId());
        ProductDTO mktProduct = Products.randomSKU()
            .name("marketing_product_name")
            .branding(Set.of(branding))
            .providedProducts(Set.of(engProduct));
        mktProduct = ownerProductApi.createProduct(owner.getKey(), mktProduct);
        ProvidedProductDTO engProvidedProduct = (new ProvidedProductDTO())
            .productName(engProduct.getName())
            .productId(engProduct.getId());
        pool = Pools.random(mktProduct)
            .contractNumber("12345")
            .accountNumber("6789")
            .orderNumber("order1")
            .providedProducts(Set.of(engProvidedProduct));
        pool = ownerApi.createPool(owner.getKey(), pool);

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
    public void shouldEncodeTheContentUrls() {
        String ownerKey = this.owner.getKey();

        ContentDTO content1 = Contents.random();
        content1.setContentUrl("/content/dist/rhel/$releasever/$basearch/debug");
        ContentDTO content2 = Contents.random();
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
            content1 = ownerContentApi.createContent(ownerKey, content1);
            product = ownerProductApi.addContentToProduct(ownerKey, product.getId(), content1.getId(), true);
            content2 = ownerContentApi.createContent(ownerKey, content2);
            product = ownerProductApi.addContentToProduct(ownerKey, product.getId(), content2.getId(), true);
            pool = Pools.random();
            pool.setProductId(product.getId());
            pool.setProductName(product.getName());
            pool = ownerApi.createPool(owner.getKey(), pool);
        }
        ApiClient consumerClient = ApiClients.ssl(system);
        ConsumerClient consumers = consumerClient.consumers();
        JsonNode bindResult = consumers.bindProduct(system.getUuid(), product.getId());

        List<JsonNode> jsonNodes = consumers.exportCertificates(system.getUuid(), null);
        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertThat(jsonBody.get("products").get(0).get("content")).hasSize(4);

        assertThatCert(X509Cert.fromEnt(bindResult.get(0)))
            .extractingEntitlementPayload()
            .contains(
                "/content/dist/rhel/$releasever/$basearch/os",
                "/content/dist/rhel/$releasever/$basearch/debug",
                "/content/beta/rhel/$releasever/$basearch/source/SRPMS"
            );
    }

    @Test
    public void shouldEncodeManyContentUrls() {
        for (int i = 0; i < 100; i++) {
            ContentDTO content = Contents.random();
            content.setContentUrl(
                String.format("/content/dist/rhel/$releasever-%s/$basearch-%s/debug-%s", i, i, i));
            if (CandlepinMode.isHosted()) {
                content = hostedTestApi.createContent(content);
                hostedTestApi.addContentToProduct(product.getId(), Map.of(content.getId(), true));
            }
            else {
                content = ownerContentApi.createContent(owner.getKey(), content);
                product = ownerProductApi.addContentToProduct(owner.getKey(), product.getId(),
                    content.getId(), true);
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
        ApiClient consumerClient = ApiClients.ssl(system);
        ConsumerClient consumers = consumerClient.consumers();
        JsonNode bindResult = consumers.bindProduct(system.getUuid(), product.getId());

        // confirm content count to cross-check
        List<JsonNode> jsonNodes = consumers.exportCertificates(system.getUuid(), null);
        assertEquals(1, jsonNodes.size());
        JsonNode jsonBody = jsonNodes.get(0);
        assertThat(jsonBody.get("products")).hasSize(1);
        assertThat(jsonBody.get("products").get(0).get("content")).hasSize(102);

        // confirm encoded urls
        assertThatCert(X509Cert.fromEnt(bindResult.get(0)))
            .extractingEntitlementPayload()
            .hasSize(102)
            .contains(
                "/content/dist/rhel/$releasever-0/$basearch-0/debug-0",
                "/content/dist/rhel/$releasever-29/$basearch-29/debug-29",
                "/content/dist/rhel/$releasever-41/$basearch-41/debug-41",
                "/content/dist/rhel/$releasever-75/$basearch-75/debug-75",
                "/content/dist/rhel/$releasever-99/$basearch-99/debug-99"
            );
    }

    @Test
    @OnlyInStandalone
    public void shouldIncludeEntitlementNamespaceForNamespacedProducts() {
        ProductDTO product = ownerProductApi.createProduct(this.owner.getKey(), Products.randomEng());
        PoolDTO pool = ownerApi.createPool(this.owner.getKey(), Pools.random(product));

        List<EntitlementDTO> entitlements = consumerApi.bindPoolSync(system.getUuid(), pool.getId(), 1);

        assertThat(entitlements)
            .isNotNull()
            .hasSize(1);

        assertThatCert(X509Cert.fromEnt(entitlements.get(0)))
            .hasEntitlementNamespace(this.owner.getKey());
    }

    @Test
    @OnlyInHosted
    public void shouldNotIncludeEntitlementNamespaceForGlobalProducts() {
        // Create upstream data
        ProductDTO product = hostedTestApi.createProduct(Products.randomEng());
        String productId = product.getId();

        SubscriptionDTO sub = hostedTestApi.createSubscription(Subscriptions.random(this.owner, product));

        // Refresh to pull it in locally
        AsyncJobStatusDTO refreshJob = ownerApi.refreshPools(this.owner.getKey(), false);
        assertThatJob(refreshJob)
            .isNotNull()
            .terminates(client)
            .isFinished();

        // Find the pool in the org so we can bind it
        List<PoolDTO> pools = ownerApi.listOwnerPools(this.owner.getKey());
        assertThat(pools)
            .isNotNull()
            .hasSizeGreaterThanOrEqualTo(1);

        PoolDTO pool = pools.stream()
            .filter(elem -> productId.equals(elem.getProductId()))
            .findAny()
            .get();

        List<EntitlementDTO> entitlements = consumerApi.bindPoolSync(system.getUuid(), pool.getId(), 1);

        assertThat(entitlements)
            .isNotNull()
            .hasSize(1);

        assertThatCert(X509Cert.fromEnt(entitlements.get(0)))
            .doesNotHaveExtension(OID.entitlementNamespace());
    }

    private CertificateDTO firstCertOf(EntitlementDTO ent) {
        if (ent == null || ent.getCertificates() == null) {
            throw new NoSuchElementException();
        }
        return ent.getCertificates().stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow();
    }

}
