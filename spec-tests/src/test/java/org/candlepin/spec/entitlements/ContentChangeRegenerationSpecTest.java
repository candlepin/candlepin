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
package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductContentDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


@SpecTest
class ContentChangeRegenerationSpecTest {
    private enum ProductLocation {
        DIRECT, PROVIDED, DERIVED, DERIVED_PROVIDED
    }

    private enum ApiChange {
        ADD_CONTENT, ADD_CONTENT_BATCH, UPDATE_PRODUCT, UPDATE_CONTENT, UPSERT_CONTENT
    }

    private static Stream<Arguments> apiContentChanges() {
        return Arrays.stream(ProductLocation.values())
            .flatMap(location -> Arrays.stream(ApiChange.values())
                .map(change -> Arguments.of(location, change)));
    }

    @ParameterizedTest
    @MethodSource("apiContentChanges")
    void shouldRegenerateCertificatesAfterApiContentChanges(ProductLocation location, ApiChange change) {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());
        ContentDTO original = admin.ownerContent().createContent(owner.getKey(), Contents.random());
        ProductDTO target = admin.ownerProducts().createProduct(owner.getKey(), Products.randomEng()
            .addProductContentItem(new ProductContentDTO().content(original).enabled(true)));
        ProductDTO sku = createSku(admin, owner, target, location);
        PoolDTO pool = admin.owners().createPool(owner.getKey(), Pools.randomUpstream(sku));
        ConsumerDTO distributor = createDistributor(admin, owner);
        ConsumerDTO system = admin.consumers().createConsumer(Consumers.random(owner));
        EntitlementDTO distributorEnt = bind(admin, distributor, pool);
        EntitlementDTO systemEnt = bind(admin, system, pool);
        ProductDTO unrelated = admin.ownerProducts().createProduct(owner.getKey(), Products.randomSKU());
        EntitlementDTO unrelatedEnt = bind(admin, distributor,
            admin.owners().createPool(owner.getKey(), Pools.randomUpstream(unrelated)));

        // The same product ID in another namespace must not be invalidated.
        OwnerDTO otherOwner = admin.owners().createOwner(Owners.random());
        ProductDTO otherProduct = admin.ownerProducts().createProduct(otherOwner.getKey(),
            Products.randomEng().id(target.getId()));
        ConsumerDTO otherConsumer = admin.consumers().createConsumer(Consumers.random(otherOwner));
        EntitlementDTO otherEnt = bind(admin, otherConsumer,
            admin.owners().createPool(otherOwner.getKey(), Pools.randomUpstream(otherProduct)));

        CertificateDTO initial = certificate(distributorEnt);
        assertContainsContent(initial, target, original);
        boolean updatingContent = change == ApiChange.UPDATE_CONTENT || change == ApiChange.UPSERT_CONTENT;
        ContentDTO changed = updatingContent ? Contents.copy(original).label(original.getLabel() + "-updated") :
            admin.ownerContent().createContent(owner.getKey(), Contents.random());
        applyContentChange(admin, owner, target, changed, change);

        // Listing without regeneration must still return the old certificate: invalidation is lazy.
        assertThat(admin.consumers().listEntitlements(distributor.getUuid()))
            .filteredOn(ent -> ent.getId().equals(distributorEnt.getId()))
            .singleElement()
            .returns(initial.getId(), ent -> certificate(ent).getId());

        CertificateDTO regenerated = certificate(admin.entitlements().getEntitlement(distributorEnt.getId()));
        assertThat(regenerated.getId()).isNotEqualTo(initial.getId());
        assertContainsContent(regenerated, target, changed);
        if (!updatingContent) {
            assertContainsContent(regenerated, target, original);
        }
        CertificateDTO systemCertificate = certificate(admin.entitlements().getEntitlement(systemEnt.getId()));
        if (location == ProductLocation.DIRECT || location == ProductLocation.PROVIDED) {
            assertThat(systemCertificate.getId()).isNotEqualTo(certificate(systemEnt).getId());
            assertContainsContent(systemCertificate, target, changed);
        }
        else {
            // Only distributors include the derived branch in their certificate.
            assertThat(systemCertificate.getId()).isEqualTo(certificate(systemEnt).getId());
        }
        assertCertificateUnchanged(admin, unrelatedEnt);
        assertCertificateUnchanged(admin, otherEnt);
        assertThat(certificate(admin.entitlements().getEntitlement(distributorEnt.getId())).getId())
            .isEqualTo(regenerated.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @OnlyInHosted
    void shouldRegenerateAcrossDifferentSkusSharingProductOrContent(boolean sharedContentOnly) {
        ApiClient admin = ApiClients.admin();
        OwnerDTO ownerA = admin.owners().createOwner(Owners.random());
        OwnerDTO ownerB = admin.owners().createOwner(Owners.random());
        ContentDTO original = admin.hosted().createContent(Contents.random());
        ProductDTO providedA = admin.hosted().createProduct(Products.randomEng()
            .addProductContentItem(new ProductContentDTO().content(original).enabled(true)));
        ProductDTO providedB = sharedContentOnly ? admin.hosted().createProduct(Products.randomEng()
            .addProductContentItem(new ProductContentDTO().content(original).enabled(true))) : providedA;
        ProductDTO skuA = admin.hosted().createProduct(Products.randomSKU().providedProducts(Set.of(providedA)));
        ProductDTO derivedB = admin.hosted().createProduct(Products.randomSKU()
            .providedProducts(Set.of(providedB)));
        ProductDTO skuB = admin.hosted().createProduct(Products.randomSKU().derivedProduct(derivedB));
        ProductDTO unrelated = admin.hosted().createProduct(Products.randomSKU());
        admin.hosted().createSubscription(Subscriptions.random(ownerA, skuA));
        admin.hosted().createSubscription(Subscriptions.random(ownerB, skuB));
        admin.hosted().createSubscription(Subscriptions.random(ownerB, unrelated));
        refresh(admin, ownerA);
        refresh(admin, ownerB);

        ConsumerDTO distributorA = createDistributor(admin, ownerA);
        ConsumerDTO distributorB = createDistributor(admin, ownerB);
        EntitlementDTO entitlementA = bind(admin, distributorA, findPool(admin, ownerA, skuA));
        EntitlementDTO entitlementB = bind(admin, distributorB, findPool(admin, ownerB, skuB));
        EntitlementDTO unrelatedEnt = bind(admin, distributorB, findPool(admin, ownerB, unrelated));
        CertificateDTO initialA = certificate(entitlementA);
        CertificateDTO initialB = certificate(entitlementB);
        assertContainsContent(initialA, providedA, original);
        assertContainsContent(initialB, providedB, original);

        ContentDTO changed;
        if (sharedContentOnly) {
            changed = admin.hosted().updateContent(original.getId(),
                Contents.copy(original).label(original.getLabel() + "-updated"));
        }
        else {
            changed = admin.hosted().createContent(Contents.random());
            admin.hosted().addContentToProduct(providedA.getId(), changed.getId(), true);
        }

        // A imports the global change first; B's different SKU is outside A's refresh graph.
        refresh(admin, ownerA);
        CertificateDTO updatedA = certificate(admin.entitlements().getEntitlement(entitlementA.getId()));
        assertThat(updatedA.getId()).isNotEqualTo(initialA.getId());
        assertContainsContent(updatedA, providedA, changed);
        assertCertificateUnchanged(admin, entitlementB);
        assertCertificateUnchanged(admin, unrelatedEnt);

        // B now sees already-updated global entities. Its pool must retain the invalidation from A.
        refresh(admin, ownerB);
        CertificateDTO updatedB = certificate(admin.entitlements().getEntitlement(entitlementB.getId()));
        assertThat(updatedB.getId()).isNotEqualTo(initialB.getId());
        assertContainsContent(updatedB, providedB, changed);
        assertCertificateUnchanged(admin, unrelatedEnt);

        refresh(admin, ownerB);
        assertThat(certificate(admin.entitlements().getEntitlement(entitlementB.getId())).getId())
            .isEqualTo(updatedB.getId());
        assertCertificateUnchanged(admin, unrelatedEnt);
    }

    private ProductDTO createSku(ApiClient admin, OwnerDTO owner, ProductDTO target, ProductLocation location) {
        if (location == ProductLocation.DIRECT) {
            return target;
        }
        ProductDTO sku = Products.randomSKU();
        switch (location) {
            case PROVIDED -> sku.providedProducts(Set.of(target));
            case DERIVED -> sku.derivedProduct(target);
            case DERIVED_PROVIDED -> {
                ProductDTO derived = admin.ownerProducts().createProduct(owner.getKey(),
                    Products.randomSKU().providedProducts(Set.of(target)));
                sku.derivedProduct(derived);
            }
            default -> throw new IllegalArgumentException("Unexpected product location: " + location);
        }
        return admin.ownerProducts().createProduct(owner.getKey(), sku);
    }

    private void applyContentChange(ApiClient admin, OwnerDTO owner, ProductDTO target, ContentDTO content,
        ApiChange change) {

        switch (change) {
            case ADD_CONTENT -> admin.ownerProducts().addContentToProduct(owner.getKey(), target.getId(),
                content.getId(), true);
            case ADD_CONTENT_BATCH -> admin.ownerProducts().addContentsToProduct(owner.getKey(), target.getId(),
                Map.of(content.getId(), true));
            case UPDATE_PRODUCT -> admin.ownerProducts().updateProduct(owner.getKey(), target.getId(),
                Products.copy(target).addProductContentItem(new ProductContentDTO().content(content)
                    .enabled(true)));
            case UPDATE_CONTENT -> admin.ownerContent().updateContent(owner.getKey(), content.getId(), content);
            case UPSERT_CONTENT -> admin.ownerContent().createContent(owner.getKey(), content);
            default -> throw new IllegalArgumentException("Unexpected API change: " + change);
        }
    }

    private ConsumerDTO createDistributor(ApiClient admin, OwnerDTO owner) {
        Set<DistributorVersionCapabilityDTO> capabilities = admin.status().status().getManagerCapabilities()
            .stream().map(capability -> new DistributorVersionCapabilityDTO().name(capability))
            .collect(Collectors.toSet());
        DistributorVersionDTO version = admin.distributorVersions().create(new DistributorVersionDTO()
            .name(StringUtil.random("content-regen-distributor-"))
            .displayName("Content regeneration test").capabilities(capabilities));
        return admin.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin)
            .putFactsItem("distributor_version", version.getName()));
    }

    private EntitlementDTO bind(ApiClient admin, ConsumerDTO consumer, PoolDTO pool) {
        List<EntitlementDTO> entitlements = admin.consumers().bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        assertThat(entitlements).singleElement();
        return entitlements.get(0);
    }

    private PoolDTO findPool(ApiClient admin, OwnerDTO owner, ProductDTO sku) {
        List<PoolDTO> pools = admin.pools().listPoolsByOwnerAndProduct(owner.getId(), sku.getId());
        assertThat(pools).singleElement();
        return pools.get(0);
    }

    private void refresh(ApiClient admin, OwnerDTO owner) {
        AsyncJobStatusDTO job = admin.owners().refreshPools(owner.getKey(), false);
        assertThatJob(admin.jobs().waitForJob(job)).isFinished();
    }

    private static CertificateDTO certificate(EntitlementDTO entitlement) {
        assertThat(entitlement.getCertificates()).singleElement();
        return entitlement.getCertificates().iterator().next();
    }

    private void assertCertificateUnchanged(ApiClient admin, EntitlementDTO original) {
        assertThat(certificate(admin.entitlements().getEntitlement(original.getId())).getId())
            .isEqualTo(certificate(original).getId());
    }

    private void assertContainsContent(CertificateDTO certificate, ProductDTO product, ContentDTO content) {
        JsonNode body = CertificateUtil.decodeAndUncompressCertificate(certificate.getCert(), ApiClient.MAPPER);
        assertThat(body).isNotNull();
        List<JsonNode> contents = StreamSupport.stream(body.path("products").spliterator(), false)
            .filter(node -> node.path("id").asText().equals(product.getId()))
            .flatMap(node -> StreamSupport.stream(node.path("content").spliterator(), false))
            .filter(node -> node.path("id").asText().equals(content.getId()))
            .toList();
        assertThat(contents).singleElement()
            .returns(content.getLabel(), node -> node.path("label").asText());
    }
}
