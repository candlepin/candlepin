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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
public class DerivedProductSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        ownerContentApi = client.ownerContent();
    }

    // Complicated scenario, but we wanted to verify that if a derived SKU and an instance based
    // SKU are available, the guest autobind will have its host autobind to the derived and
    // prefer it's virt_only sub-pool to instance based.
    @Test
    public void shouldPrefersAHostAutobindVirtOnlySubPoolToInstanceBasedPoolDuringGuestAutobind() {
        // create instance based subscription:
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO mainPool = createDatacenterPool1(owner);
        ProductDTO engProduct1 = ownerProductApi.getProductById(owner.getKey(),
            mainPool.getDerivedProvidedProducts().iterator().next().getProductId());
        ProductDTO instanceProduct = Products.randomEng()
            .addAttributesItem(ProductAttributes.InstanceMultiplier.withValue("2"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("stackme"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.HostLimited.withValue("true"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .providedProducts(Set.of(engProduct1));
        instanceProduct = ownerProductApi.createProduct(owner.getKey(), instanceProduct);

        String guestUuid = StringUtil.random("uuid");
        ConsumerDTO physicalSystem = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8", "system.certificate_version", "3.2")));
        ApiClient physicalClient = ApiClients.ssl(physicalSystem);
        physicalClient.guestIds().updateGuests(physicalSystem.getUuid(),
            List.of(new GuestIdDTO().guestId(guestUuid)));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct1.getId())
            .productName(engProduct1.getName());
        ConsumerDTO guestSystem = Consumers.random(owner)
            .facts(Map.of("virt.uuid", guestUuid, "virt.is_guest", "true"))
            .installedProducts(Set.of(installed));
        guestSystem = userClient.consumers().createConsumer(guestSystem);
        ApiClient guestClient = ApiClients.ssl(guestSystem);

        guestClient.consumers().autoBind(guestSystem.getUuid());
        // Now the host should have an entitlement to the virt pool, and the guest
        // to it's derived pool.
        assertThat(physicalClient.consumers().listEntitlements(physicalSystem.getUuid()))
            .singleElement()
            .returns(mainPool.getProductId(), x -> x.getPool().getProductId());
        assertThat(guestClient.consumers().listEntitlements(guestSystem.getUuid()))
            .singleElement()
            .returns(mainPool.getDerivedProductId(), x -> x.getPool().getProductId());
    }

    @Test
    public void shouldTransferSubProductDataToMainPool() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO mainPool = createDatacenterPool1(owner);
        ProductDTO engProduct1 = ownerProductApi.getProductById(owner.getKey(),
            mainPool.getDerivedProvidedProducts().iterator().next().getProductId());

        String guestUuid = StringUtil.random("uuid");
        ConsumerDTO physicalSystem = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8", "system.certificate_version", "3.2"));
        physicalSystem = userClient.consumers().createConsumer(physicalSystem);
        ApiClient physicalClient = ApiClients.ssl(physicalSystem);
        physicalClient.guestIds().updateGuests(physicalSystem.getUuid(),
            List.of(new GuestIdDTO().guestId(guestUuid)));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct1.getId())
            .productName(engProduct1.getName());
        ConsumerDTO guestSystem = Consumers.random(owner)
            .facts(Map.of("virt.uuid", guestUuid, "virt.is_guest", "true"))
            .installedProducts(Set.of(installed));
        guestSystem = userClient.consumers().createConsumer(guestSystem);
        ApiClient guestClient = ApiClients.ssl(guestSystem);

        physicalClient.consumers().bindPool(physicalSystem.getUuid(), mainPool.getId(), 1);
        assertThat(physicalClient.consumers().listEntitlements(physicalSystem.getUuid())).singleElement();

        // Guest should now see additional sub-pool:
        assertThat(guestClient.pools().listPoolsByConsumer(guestSystem.getUuid())).hasSize(2);
        List<PoolDTO> guestPools = guestClient.pools().listPoolsByConsumerAndProduct(
            guestSystem.getUuid(), mainPool.getDerivedProductId());
        assertThat(guestPools).singleElement()
            .returns(-1L, PoolDTO::getQuantity)
            .returns("stackme", PoolDTO::getSourceStackId)
            .returns("system", x -> getPoolAttributeValue(x, "requires_consumer_type"))
            .returns(physicalSystem.getUuid(), x -> getPoolAttributeValue(x, "requires_host"))
            .returns("true", x -> getPoolAttributeValue(x, "virt_only"))
            .returns("true", x -> getPoolAttributeValue(x, "pool_derived"))
            .returns("4", x -> getProductAttributeValue(x, "sockets"))
            .returns("2", x -> getProductAttributeValue(x, "cores"));
    }

    @Test
    public void shouldAllowGuestToConsumeSubProductPool() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO mainPool = createDatacenterPool1(owner);
        ProductDTO engProduct1 = ownerProductApi.getProductById(owner.getKey(),
            mainPool.getDerivedProvidedProducts().iterator().next().getProductId());

        String guestUuid = StringUtil.random("uuid");
        ConsumerDTO physicalSystem = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8", "system.certificate_version", "3.2"));
        physicalSystem = userClient.consumers().createConsumer(physicalSystem);
        ApiClient physicalClient = ApiClients.ssl(physicalSystem);
        physicalClient.guestIds().updateGuests(physicalSystem.getUuid(),
            List.of(new GuestIdDTO().guestId(guestUuid)));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct1.getId())
            .productName(engProduct1.getName());
        ConsumerDTO guestSystem = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .facts(Map.of("virt.uuid", guestUuid, "virt.is_guest", "true"))
            .installedProducts(Set.of(installed)));
        ApiClient guestClient = ApiClients.ssl(guestSystem);

        physicalClient.consumers().bindPool(physicalSystem.getUuid(), mainPool.getId(), 1);
        assertThat(physicalClient.consumers().listEntitlements(physicalSystem.getUuid())).singleElement();

        List<PoolDTO> guestPools = guestClient.pools().listPoolsByConsumerAndProduct(
            guestSystem.getUuid(), mainPool.getDerivedProductId());
        assertThat(guestPools).singleElement();
        guestClient.consumers().bindPool(guestSystem.getUuid(), guestPools.get(0).getId(), 1);
        assertThat(guestClient.consumers().listEntitlements(guestSystem.getUuid())).singleElement();
    }

    @Test
    public void shouldNotBeVisibleByDistributorThatDoesNotHaveCapabilityAfterBasicSearch() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        createDatacenterPool1(owner);
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient distributorClient = ApiClients.ssl(distributor);

        assertThat(distributorClient.pools().listPools(null, distributor.getUuid(), null, false, null,
            null, null, null, null))
            .isEmpty();
    }

    @Test
    public void shouldBeVisibleByDistributorThatDoesNotHaveCapabilityAfterListAll() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO mainPool = createDatacenterPool1(owner);
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient distributorClient = ApiClients.ssl(distributor);

        assertThat(distributorClient.pools().listPools(null, distributor.getUuid(), null, true, null,
            null, null, null, null))
            .singleElement()
            .returns(mainPool.getDerivedProductId(), PoolDTO::getDerivedProductId)
            .returns(1, pool -> pool.getDerivedProvidedProducts().size())
            .returns(mainPool.getDerivedProvidedProducts().iterator().next()
            .getProductId(), pool -> pool.getDerivedProvidedProducts().iterator().next().getProductId());
    }


    @Test
    public void shouldPreventDistributorFromAttachingWithoutNecessaryCapabilities() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO mainPool = createDatacenterPool1(owner);
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient distributorClient = ApiClients.ssl(distributor);

        String expectedError = String.format(
            "Unit does not support derived products data required by pool \\\"%s\\\"", mainPool.getId());
        StatusCodeAssertions.assertThatStatus(() ->
            distributorClient.consumers().bindPool(distributor.getUuid(), mainPool.getId(), 1))
            .isForbidden()
            .hasMessageContaining(expectedError);
    }

    @Test
    public void shouldDistributorEntitlementCertIncludesDerivedContent() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO mainPool = createDatacenterPool1(owner);
        ProductDTO engProduct1 = ownerProductApi.getProductById(owner.getKey(),
            mainPool.getDerivedProvidedProducts().iterator().next().getProductId());
        ContentDTO engProductContent1 = ownerContentApi.getContentById(owner.getKey(),
            engProduct1.getProductContent().iterator().next().getContent().getId());
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient distributorClient = ApiClients.ssl(distributor);

        String distVersion = StringUtil.random("version");
        client.distributorVersions().create(new DistributorVersionDTO()
            .name(distVersion)
            .displayName("SAM")
            .capabilities(Set.of(new DistributorVersionCapabilityDTO().name("cert_v3"),
                new DistributorVersionCapabilityDTO().name("derived_product"))));
        distributorClient.consumers().updateConsumer(distributor.getUuid(),
            new ConsumerDTO().facts(Map.of("distributor_version", distVersion)));

        assertThat(distributorClient.consumers().bindPool(distributor.getUuid(), mainPool.getId(), 1))
            .isNotEmpty();

        List<JsonNode> certs = distributorClient.consumers().exportCertificates(distributor.getUuid(), null);
        assertThat(certs)
            .singleElement()
            .returns(3, x -> x.get("products").size());

        assertProductContent(certs.get(0).get("products"), engProduct1, engProductContent1);
    }

    @Test
    public void shouldDistributorEntitlementCertIncludesDerivedModifierContent() {
        // Content for modified product is only included if it provides the modified product
        // through derived provided products, provided products, or through another entitlement.
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO modifiedPool = createDatacenterPool2(owner);
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient distributorClient = ApiClients.ssl(distributor);

        // engProduct2 is the derived provided product on the pool with name starting "eng"
        String engProductId2 = modifiedPool.getDerivedProvidedProducts().stream()
            .filter(x -> x.getProductName().startsWith("eng"))
            .findFirst().get().getProductId();
        ProductDTO engProduct2 = ownerProductApi.getProductById(owner.getKey(), engProductId2);
        ContentDTO productModifierContent = ownerContentApi.getContentById(owner.getKey(),
            engProduct2.getProductContent().iterator().next().getContent().getId());

        String distVersion = StringUtil.random("version");
        client.distributorVersions().create(new DistributorVersionDTO()
            .name(distVersion)
            .displayName("SAM")
            .capabilities(Set.of(new DistributorVersionCapabilityDTO().name("cert_v3"),
                new DistributorVersionCapabilityDTO().name("derived_product"))));
        distributorClient.consumers().updateConsumer(distributor.getUuid(),
            new ConsumerDTO().facts(Map.of("distributor_version", distVersion)));

        assertThat(distributorClient.consumers().bindPool(distributor.getUuid(), modifiedPool.getId(), 1))
            .isNotEmpty();
        List<JsonNode> certs = distributorClient.consumers().exportCertificates(distributor.getUuid(), null);
        assertThat(certs)
            .singleElement()
            .returns(4, x -> x.get("products").size());

        assertProductContent(certs.get(0).get("products"), engProduct2, productModifierContent);
    }

    @Test
    public void shouldDistributorEntitlementCertDoesNotIncludeModifierContentWhenBaseEntitlementIsDeleted() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient distributorClient = ApiClients.ssl(distributor);

        Map<String, Object> setupData = setupModifierTest(owner, distributor);
        EntitlementDTO vdcPoolEnt = (EntitlementDTO) setupData.get("vdcPoolEnt");

        // Unbind the base entitlement. This should trigger the removal of the modifier content since the
        // distributor no longer has an entitlement that provides @modified_product.
        distributorClient.consumers().unbindByEntitlementId(distributor.getUuid(), vdcPoolEnt.getId());
        List<JsonNode> certs = distributorClient.consumers().exportCertificates(distributor.getUuid(), null);
        assertThat(certs)
            .singleElement();
        verifyModifierContent(certs, false, setupData);
    }

    @Test
    public void shouldDistributorEntitlementCertDoesNotIncludeModifierContentWhenBasePoolIsDeleted() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO distributor = userClient.consumers().createConsumer(
            Consumers.random(owner, ConsumerTypes.Candlepin));
        ApiClient distributorClient = ApiClients.ssl(distributor);

        Map<String, Object> setupData = setupModifierTest(owner, distributor);
        PoolDTO vdcPool = (PoolDTO) setupData.get("vdcPool");

        // Delete the base pool. This should trigger the removal of the modifier content since the
        // distributor no longer has an entitlement that provides @modified_product.
        client.pools().deletePool(vdcPool.getId());
        List<JsonNode> certs = distributorClient.consumers().exportCertificates(distributor.getUuid(), null);
        assertThat(certs)
            .singleElement();
        verifyModifierContent(certs, false, setupData);
    }

    @Test
    public void shouldHostEntitlementCertDoesNotIncludeDerivedContent() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO mainPool = createDatacenterPool1(owner);
        ProductDTO engProduct1 = ownerProductApi.getProductById(owner.getKey(),
            mainPool.getDerivedProvidedProducts().iterator().next().getProductId());

        ConsumerDTO physicalSystem = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "8", "system.certificate_version", "3.2")));
        ApiClient physicalClient = ApiClients.ssl(physicalSystem);

        assertThat(physicalClient.consumers().bindPool(physicalSystem.getUuid(), mainPool.getId(), 1))
            .isNotNull();
        List<JsonNode> certs = physicalClient.consumers().exportCertificates(physicalSystem.getUuid(), null);
        assertThat(certs)
            .singleElement()
            .returns(1, x -> x.get("products").size());
        assertThat(certs.stream().findFirst().get().get("products"))
            .singleElement()
            .returns(mainPool.getProductId(), x -> x.get("id").asText())
            .returns(0, x -> x.get("content").size());
    }

    /**
     * Builds a consumer capable of generating manifests and exporting certificate bundles via
     * export APIs
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
        Set<DistributorVersionCapabilityDTO> capabilities = client.status().status()
            .getManagerCapabilities()
            .stream()
            .map(capability -> new DistributorVersionCapabilityDTO().name(capability))
            .collect(Collectors.toSet());

        DistributorVersionDTO distver = new DistributorVersionDTO()
            .name(StringUtil.random("manifest_dist"))
            .displayName("SAM")
            .capabilities(capabilities);

        distver = client.distributorVersions().create(distver);

        // Create the consumer
        ConsumerDTO consumer = new ConsumerDTO()
            .name(StringUtil.random("distributor-", 8, StringUtil.CHARSET_NUMERIC_HEX))
            .type(ConsumerTypes.Candlepin.value())
            .owner(Owners.toNested(owner))
            .putFactsItem("system.certificate_version", "3.3")
            .putFactsItem("distributor_version", distver.getName());

        return client.consumers().createConsumer(consumer);
    }

    @Test
    @OnlyInHosted
    public void shouldRegenerateEntitlementsWhenUpdatingDerivedContent() {
        HostedTestApi hostedTestApi = client.hosted();
        OwnerDTO owner = ownerApi.createOwner(Owners.random());

        ConsumerDTO distributor = this.buildDistributorConsumer(owner);
        ApiClient distributorClient = ApiClients.ssl(distributor);

        ContentDTO derivedContent = hostedTestApi.createContent(Contents.random().name("dcont-1"));
        ProductDTO derivedEngProduct = hostedTestApi.createProduct(Products.randomEng());
        hostedTestApi.addContentToProduct(derivedEngProduct.getId(), derivedContent.getId(), true);

        ProductDTO derivedProduct = Products.random()
            .addAttributesItem(ProductAttributes.Cores.withValue("2"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("4"))
            .providedProducts(Set.of(derivedEngProduct));
        derivedProduct = hostedTestApi.createProduct(derivedProduct);

        // Create a subscription with an upstream product with a derived product that provides content
        ProductDTO datacenterProduct = Products.random()
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("unlimited"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("stackme"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .derivedProduct(derivedProduct);
        datacenterProduct = hostedTestApi.createProduct(datacenterProduct);

        SubscriptionDTO sub = hostedTestApi.createSubscription(
            Subscriptions.random(owner, datacenterProduct));

        AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
        assertThatJob(client.jobs().waitForJob(refresh.getId()))
            .isFinished();

        ProductDTO fetched = ownerProductApi.getProductById(owner.getKey(), derivedEngProduct.getId());
        assertThat(fetched)
            .isNotNull()
            .extracting(ProductDTO::getProductContent, as(collection(ProductContentDTO.class)))
            .isNotNull()
            .map(ProductContentDTO::getContent)
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrder(derivedContent.getId());

        List<PoolDTO> mainPools = client.pools().listPoolsByOwnerAndProduct(
            owner.getId(), datacenterProduct.getId());
        assertThat(mainPools).singleElement(); // We're expecting the base pool

        EntitlementDTO ent = distributorClient.consumers()
            .bindPoolSync(distributor.getUuid(), mainPools.get(0).getId(), 1)
            .get(0);

        List<JsonNode> certs = distributorClient.consumers().exportCertificates(distributor.getUuid(), null);
        assertThat(certs)
            .singleElement()
            .extracting(node -> node.get("products"))
            .returns(1, JsonNode::size);

        assertProductContent(certs.get(0).get("products"), derivedEngProduct, derivedContent);

        // Add content to the derived product upstream
        ContentDTO newDerivedContent = hostedTestApi.createContent(Contents.random().name("dcont-2"));
        hostedTestApi.addContentToProduct(derivedEngProduct.getId(), newDerivedContent.getId(), true);

        // Refresh the account & verify the product reflects the upstream changes
        refresh = ownerApi.refreshPools(owner.getKey(), false);
        assertThatJob(client.jobs().waitForJob(refresh.getId()))
            .isFinished();

        assertThat(ownerProductApi.getProductById(owner.getKey(), derivedEngProduct.getId()))
            .isNotNull()
            .extracting(ProductDTO::getProductContent, as(collection(ProductContentDTO.class)))
            .isNotNull()
            .map(ProductContentDTO::getContent)
            .map(ContentDTO::getId)
            .containsExactlyInAnyOrder(derivedContent.getId(), newDerivedContent.getId());

        // Export the certs again and verify the product on the certificate has changed to reflect
        // the new content
        certs = distributorClient.consumers().exportCertificates(distributor.getUuid(), null);
        assertThat(certs)
            .singleElement()
            .extracting(node -> node.get("products"))
            .returns(1, JsonNode::size);

        assertProductContent(certs.get(0).get("products"), derivedEngProduct, derivedContent,
            newDerivedContent);

        // FIXME: What is being tested beyond this point?
        distributorClient.consumers().unbindByEntitlementId(distributor.getUuid(), ent.getId());

        ent = distributorClient.consumers()
            .bindPoolSync(distributor.getUuid(), mainPools.get(0).getId(), 1)
            .get(0);

        certs = distributorClient.consumers().exportCertificates(distributor.getUuid(), null);
        assertThat(certs)
            .singleElement()
            .extracting(node -> node.get("products"))
            .returns(1, JsonNode::size);

        assertProductContent(certs.get(0).get("products"), derivedEngProduct, derivedContent,
            newDerivedContent);
    }

    private Map<String, Object> setupModifierTest(OwnerDTO owner, ConsumerDTO distributor) {
        Map<String, Object> data = new HashMap<>();
        //  Content for modified product is only included if it provides the modified product
        //  through derived provided products, provided products, or through another entitlement.
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO modifiedContentPool = createDatacenterPool2(owner);
        ProductDTO engProduct2 = ownerProductApi.getProductById(owner.getKey(),
            modifiedContentPool.getDerivedProvidedProducts()
            .stream()
            .filter(x -> x.getProductName().startsWith("eng"))
            .findFirst().get()
            .getProductId());
        ProductDTO modifiedProduct = ownerProductApi.getProductById(owner.getKey(),
            engProduct2.getProductContent().iterator().next().getContent()
            .getModifiedProductIds().iterator().next());
        ContentDTO productModifierContent = ownerContentApi.getContentById(owner.getKey(),
            engProduct2.getProductContent().iterator().next().getContent().getId());
        ProductDTO derivedProduct2 = createDerivedProduct2(owner, modifiedProduct);

        ApiClient distributorClient = ApiClients.ssl(distributor);
        String distVersion = StringUtil.random("version");
        client.distributorVersions().create(new DistributorVersionDTO()
            .name(distVersion)
            .displayName("SAM")
            .capabilities(Set.of(new DistributorVersionCapabilityDTO().name("cert_v3"),
                new DistributorVersionCapabilityDTO().name("derived_product"))));
        distributorClient.consumers().updateConsumer(distributor.getUuid(), new ConsumerDTO()
            .facts(Map.of("distributor_version", distVersion)));

        // Create a VDC style subscription that has a derived provided product matching @eng_product_2's
        // modifying content set requirement (@modified_product).
        ProductDTO vdcProduct = Products.randomEng()
            .derivedProduct(derivedProduct2)
            .providedProducts(Set.of(modifiedProduct));
        vdcProduct = ownerProductApi.createProduct(owner.getKey(), vdcProduct);

        PoolDTO vdcPool = ownerApi.createPool(owner.getKey(), Pools.random(vdcProduct));
        ProductDTO modifierEntitlementProduct = Products.randomEng()
            .providedProducts(Set.of(engProduct2));
        modifierEntitlementProduct = ownerProductApi.createProduct(owner.getKey(),
            modifierEntitlementProduct);
        PoolDTO modifierPool = ownerApi.createPool(owner.getKey(), Pools.random(modifierEntitlementProduct));

        // Grab an entitlement from the VDC style subscription.
        EntitlementDTO vdcPoolEnt = ApiClient.MAPPER.convertValue(distributorClient.consumers().bindPool(
            distributor.getUuid(), vdcPool.getId(), 1).get(0), EntitlementDTO.class);
        assertThat(vdcPoolEnt).isNotNull();
        // Grab an entitlement from the modifier pool. Already having an entitlement that provides
        // modified_product permits the addition of the additional content set from the modifier product.
        EntitlementDTO modifierPoolEnt = ApiClient.MAPPER.convertValue(distributorClient.consumers().bindPool(
            distributor.getUuid(), modifierPool.getId(), 1).get(0), EntitlementDTO.class);
        assertThat(modifierPoolEnt).isNotNull();

        data.put("vdcPool", vdcPool);
        data.put("vdcPoolEnt", vdcPoolEnt);
        data.put("modifierPool", modifierPool);
        data.put("modifierPoolEnt", modifierPoolEnt);
        data.put("engProduct2", engProduct2);
        data.put("modifiedProduct", modifiedProduct);
        data.put("productModifierContent", productModifierContent);
        return data;
    }


    private void verifyModifierContent(List<JsonNode> certs, boolean contentShouldExist,
        Map<String, Object> data) {
        assertThat(certs)
            .singleElement()
            .returns(2, x -> x.get("products").size());

        if (contentShouldExist) {
            assertProductContent(certs.get(0).get("products"), (ProductDTO) data.get("engProduct2"),
                (ContentDTO) data.get("productModifierContent"));
        }
        else {
            assertThat(certs.get(0).get("products"))
                .filteredOn(product -> ((ProductDTO) data.get("engProduct2")).getId().equals(
                    product.get("id").asText()))
                .map(product -> product.get("content"))
                .singleElement()
                .returns(0, x -> x.size());
        }
    }

    private ProductDTO createEngProduct1(OwnerDTO owner) {
        ProductDTO engProduct1 = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ContentDTO engProductContent = Contents.random()
            .gpgUrl("gpgUrl")
            .contentUrl("/content/dist/rhel/$releasever/$basearch/os")
            .metadataExpire(6400L);
        engProductContent = ownerContentApi.createContent(owner.getKey(), engProductContent);
        return ownerProductApi.addContentToProduct(owner.getKey(), engProduct1.getId(),
            engProductContent.getId(), true);
    }

    private ProductDTO createEngProduct2(OwnerDTO owner, ProductDTO modifiedProduct) {
        ProductDTO engProduct2 = Products.randomEng()
            .name(StringUtil.random("eng"));
        engProduct2 = ownerProductApi.createProduct(owner.getKey(), engProduct2);
        ContentDTO productModifierContent = Contents.random()
            .gpgUrl("gpgUrl")
            .contentUrl("/this/modifies/product")
            .metadataExpire(6400L)
            .modifiedProductIds(Set.of(modifiedProduct.getId()));
        productModifierContent = ownerContentApi.createContent(owner.getKey(), productModifierContent);
        return ownerProductApi.addContentToProduct(owner.getKey(), engProduct2.getId(),
            productModifierContent.getId(), true);
    }

    private ProductDTO createDerivedProduct1(OwnerDTO owner) {
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.Cores.withValue("2"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("4"))
            .providedProducts(Set.of(createEngProduct1(owner)));
        return ownerProductApi.createProduct(owner.getKey(), product);
    }

    private ProductDTO createDerivedProduct2(OwnerDTO owner, ProductDTO modifiedProduct) {
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.Cores.withValue("2"))
            .providedProducts(Set.of(modifiedProduct));
        return ownerProductApi.createProduct(owner.getKey(), product);
    }

    private ProductDTO createDerivedProduct3(OwnerDTO owner) {
        ProductDTO modifiedProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.randomEng());
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.Cores.withValue("2"))
            .providedProducts(Set.of(createEngProduct2(owner, modifiedProduct), modifiedProduct));
        return ownerProductApi.createProduct(owner.getKey(), product);
    }

    private PoolDTO createDatacenterPool1(OwnerDTO owner) {
        ProductDTO datacenterProduct = Products.randomEng()
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("unlimited"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("stackme"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.HostLimited.withValue("true"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .derivedProduct(createDerivedProduct1(owner));
        datacenterProduct = ownerProductApi.createProduct(owner.getKey(), datacenterProduct);
        return ownerApi.createPool(owner.getKey(), Pools.random(datacenterProduct));
    }

    private PoolDTO createDatacenterPool2(OwnerDTO owner) {
        ProductDTO datacenterProduct = Products.randomEng()
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("unlimited"))
            .addAttributesItem(ProductAttributes.HostLimited.withValue("true"))
            .derivedProduct(createDerivedProduct3(owner));
        datacenterProduct = ownerProductApi.createProduct(owner.getKey(), datacenterProduct);
        return ownerApi.createPool(owner.getKey(), Pools.random(datacenterProduct));
    }

    private String getPoolAttributeValue(PoolDTO pool, String name) {
        return pool.getAttributes().stream()
            .filter(y -> y.getName().equals(name))
            .findFirst()
            .map(AttributeDTO::getValue)
            .orElse(null);
    }

    private String getProductAttributeValue(PoolDTO pool, String name) {
        return pool.getProductAttributes().stream()
            .filter(y -> y.getName().equals(name))
            .findFirst()
            .map(AttributeDTO::getValue)
            .orElse(null);
    }

    private void assertProductContent(JsonNode products, ProductDTO derivedEngProduct,
        ContentDTO... contents) {

        List<String> expectedIds = Arrays.stream(contents)
            .map(ContentDTO::getId)
            .toList();

        assertThat(products)
            .filteredOn(product -> derivedEngProduct.getId().equals(product.get("id").asText()))
            .flatMap(product -> getContentNodes(product))
            .hasSize(contents.length)
            .extracting(jsonNode -> jsonNode.get("id").asText())
            .containsAll(expectedIds);
    }

    private List<JsonNode> getContentNodes(JsonNode product) {
        List<JsonNode> contents = new ArrayList<>();
        product.get("content").valueStream().forEach(contents::add);
        return contents;
    }
}
