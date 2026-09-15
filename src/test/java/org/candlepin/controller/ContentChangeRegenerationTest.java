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
package org.candlepin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.resource.OwnerContentResource;
import org.candlepin.resource.OwnerProductResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;


public class ContentChangeRegenerationTest extends DatabaseTestFixture {
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
    public void testApiContentChangesInvalidateAffectedEntitlements(ProductLocation location, ApiChange change) {
        Owner owner = this.createOwner();
        Product target = this.createProduct(TestUtil.createProduct("target").setNamespace(owner.getKey()));
        Product sku = target;
        if (location != ProductLocation.DIRECT) {
            sku = TestUtil.createProduct("sku").setNamespace(owner.getKey());
            switch (location) {
                case PROVIDED -> sku.addProvidedProduct(target);
                case DERIVED -> sku.setDerivedProduct(target);
                case DERIVED_PROVIDED -> {
                    Product derived = this.createProduct(TestUtil.createProduct("derived")
                        .setNamespace(owner.getKey()).setProvidedProducts(List.of(target)));
                    sku.setDerivedProduct(derived);
                }
                default -> throw new IllegalStateException("Unexpected product location");
            }
            sku = this.createProduct(sku);
        }

        Pool pool = this.createPool(owner, sku);
        Entitlement distributorEnt = this.createEntitlement(owner, this.createDistributor(owner), pool);
        Entitlement systemEnt = this.createEntitlement(owner, this.createConsumer(owner), pool);
        Pool unrelatedPool = this.createPool(owner, this.createProduct());
        Entitlement unrelatedEnt = this.createEntitlement(owner, this.createDistributor(owner), unrelatedPool);
        Owner otherOwner = this.createOwner();
        Product otherProduct = this.createProduct(TestUtil.createProduct(target.getId())
            .setNamespace(otherOwner.getKey()));
        Entitlement otherEnt = this.createEntitlement(otherOwner, this.createDistributor(otherOwner),
            this.createPool(otherOwner, otherProduct));
        Pool derivedPool = this.createPool(owner, sku);
        derivedPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        Entitlement derivedPoolEnt = this.createEntitlement(owner, this.createDistributor(owner), derivedPool);

        Content content = this.createContent(TestUtil.createContent("content")
            .setNamespace(owner.getKey()).setContentUrl("/original"));
        boolean updatingContent = change == ApiChange.UPDATE_CONTENT || change == ApiChange.UPSERT_CONTENT;
        if (updatingContent) {
            target.addContent(content, true);
            this.productCurator.create(target);
        }
        this.getEntityManager().flush();

        boolean includesSystem = location == ProductLocation.DIRECT || location == ProductLocation.PROVIDED;
        Set<String> expectedEntitlements = new HashSet<>(Set.of(distributorEnt.getId()));
        if (includesSystem) {
            expectedEntitlements.add(systemEnt.getId());
            expectedEntitlements.add(derivedPoolEnt.getId());
        }
        assertThat(this.entitlementCurator.listEntitlementIdsForProducts(owner, List.of(target.getId())))
            .isEqualTo(expectedEntitlements);

        OwnerProductResource productResource = this.injector.getInstance(OwnerProductResource.class);
        OwnerContentResource contentResource = this.injector.getInstance(OwnerContentResource.class);
        ContentDTO contentDTO = this.modelTranslator.translate(content, ContentDTO.class);
        switch (change) {
            case ADD_CONTENT -> productResource.addContentToProduct(owner.getKey(), target.getId(),
                content.getId(), true);
            case ADD_CONTENT_BATCH -> productResource.addContentsToProduct(owner.getKey(), target.getId(),
                Map.of(content.getId(), true));
            case UPDATE_PRODUCT -> productResource.updateProduct(owner.getKey(), target.getId(),
                new ProductDTO().productContent(Set.of(new ProductContentDTO().content(contentDTO)
                    .enabled(true))));
            case UPDATE_CONTENT -> contentResource.updateContent(owner.getKey(), content.getId(),
                new ContentDTO().contentUrl("/updated"));
            case UPSERT_CONTENT -> contentResource.createContent(owner.getKey(), contentDTO.contentUrl("/updated"));
            default -> throw new IllegalStateException("Unexpected API change");
        }

        this.getEntityManager().flush();
        this.getEntityManager().clear();
        assertThat(this.entitlementCurator.get(distributorEnt.getId()).isDirty()).isTrue();
        assertThat(this.entitlementCurator.get(systemEnt.getId()).isDirty()).isEqualTo(includesSystem);
        assertThat(this.entitlementCurator.get(derivedPoolEnt.getId()).isDirty()).isEqualTo(includesSystem);
        assertThat(this.entitlementCurator.get(unrelatedEnt.getId()).isDirty()).isFalse();
        assertThat(this.entitlementCurator.get(otherEnt.getId()).isDirty()).isFalse();
        assertThat(this.poolCurator.get(pool.getId()).hasDirtyProduct()).isFalse();
    }

    @Test
    public void testProductInvalidationAcrossBatchesDeduplicatesEntitlements() {
        Owner owner = this.createOwner();
        List<Product> provided = new ArrayList<>();
        // The test configuration limits IN clauses to ten elements.
        for (int i = 0; i < 25; ++i) {
            provided.add(this.createProduct());
        }
        Product sku = this.createProduct(TestUtil.createProduct().setProvidedProducts(provided));
        Entitlement entitlement = this.createEntitlement(owner, this.createDistributor(owner),
            this.createPool(owner, sku));
        Entitlement unrelated = this.createEntitlement(owner, this.createDistributor(owner),
            this.createPool(owner, this.createProduct()));
        List<String> productIds = provided.stream().map(Product::getId).toList();
        this.getEntityManager().flush();

        assertThat(this.entitlementCurator.listEntitlementIdsForProducts(owner, productIds))
            .containsExactly(entitlement.getId());
        this.entitlementCurator.markEntitlementsDirtyForProducts(owner, productIds);
        this.getEntityManager().clear();
        assertThat(this.entitlementCurator.get(entitlement.getId()).isDirty()).isTrue();
        assertThat(this.entitlementCurator.get(unrelated.getId()).isDirty()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testProductContentChangeInvalidatesEntitlementsOutsidePoolDates(boolean future) {
        Owner owner = this.createOwner();
        Product product = this.createProduct(TestUtil.createProduct().setNamespace(owner.getKey()));
        Content content = this.createContent(TestUtil.createContent().setNamespace(owner.getKey()));
        long now = System.currentTimeMillis();
        long day = 86_400_000L;
        Pool pool = this.createPool(owner, product, 1L,
            new Date(now + (future ? day : -2 * day)), new Date(now + (future ? 2 * day : -day)));
        Entitlement entitlement = this.createEntitlement(owner, this.createDistributor(owner), pool);
        entitlement.setEndDateOverride(new Date(now + 3 * day));
        this.getEntityManager().flush();

        this.injector.getInstance(OwnerProductResource.class)
            .addContentToProduct(owner.getKey(), product.getId(), content.getId(), true);

        this.getEntityManager().flush();
        this.getEntityManager().clear();
        assertThat(this.entitlementCurator.get(entitlement.getId()).isDirty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testRefreshInvalidatesOtherSkuSharingProductOrContent(boolean sharedContentOnly) {
        Owner ownerA = this.createOwner();
        Owner ownerB = this.createOwner();
        Content content = this.createContent(TestUtil.createContent("original").setContentUrl("/original"));
        Product shared = this.createProduct(TestUtil.createProduct("provided-a").addContent(content, true));
        Product providedB = sharedContentOnly ?
            this.createProduct(TestUtil.createProduct("provided-b").addContent(content, true)) : shared;
        Product skuA = this.createProduct(TestUtil.createProduct("sku-a").setProvidedProducts(List.of(shared)));
        // Exercise multiple parent levels and both provided and derived relationships.
        Product derivedB = this.createProduct(TestUtil.createProduct("derived-b")
            .setProvidedProducts(List.of(providedB)));
        Product skuB = this.createProduct(TestUtil.createProduct("sku-b").setDerivedProduct(derivedB));
        Pool poolA = this.createPool(ownerA, skuA);
        Pool poolB = this.createPool(ownerB, skuB);
        Entitlement entitlementA = this.createEntitlement(ownerA, this.createDistributor(ownerA), poolA);
        Entitlement entitlementB = this.createEntitlement(ownerB, this.createDistributor(ownerB), poolB);
        Pool unrelatedPool = this.createPool(ownerB, this.createProduct());
        Entitlement unrelatedEnt = this.createEntitlement(ownerB, this.createDistributor(ownerB), unrelatedPool);
        Date previousContentUpdate = TestUtil.createDate(2000, 1, 1);
        ownerB.setLastContentUpdate(previousContentUpdate);
        SubscriptionDTO subscriptionA = this.subscription(poolA);
        SubscriptionDTO subscriptionB = this.subscription(poolB);
        if (sharedContentOnly) {
            subscriptionA.getProduct().getProvidedProducts().iterator().next()
                .getProductContent().iterator().next().getContent().setContentUrl("/updated");
            subscriptionB.getProduct().getDerivedProduct().getProvidedProducts().iterator().next()
                .getProductContent().iterator().next().getContent().setContentUrl("/updated");
        }
        else {
            org.candlepin.dto.manifest.v1.ContentDTO newContent = this.modelTranslator.translate(
                TestUtil.createContent("new-content"), org.candlepin.dto.manifest.v1.ContentDTO.class);
            subscriptionA.getProduct().getProvidedProducts().iterator().next().addContent(newContent, true);
            subscriptionB.getProduct().getDerivedProduct().getProvidedProducts().iterator().next()
                .addContent(newContent, true);
        }

        this.commitTransaction();
        this.getEntityManager().clear();
        this.refresh(ownerA, subscriptionA);

        this.beginTransaction();
        this.getEntityManager().clear();
        assertThat(this.poolCurator.get(poolB.getId()).hasDirtyProduct()).isTrue();
        assertThat(this.entitlementCurator.get(entitlementA.getId()).isDirty()).isTrue();
        assertThat(this.entitlementCurator.get(entitlementB.getId()).isDirty()).isFalse();
        assertThat(this.ownerCurator.get(ownerB.getId()).getLastContentUpdate()).isAfter(previousContentUpdate);
        assertThat(this.poolCurator.get(unrelatedPool.getId()).hasDirtyProduct()).isFalse();
        this.commitTransaction();
        this.getEntityManager().clear();

        this.refresh(ownerB, subscriptionB);

        this.beginTransaction();
        this.getEntityManager().clear();
        assertThat(this.poolCurator.get(poolB.getId()).hasDirtyProduct()).isFalse();
        assertThat(this.entitlementCurator.get(entitlementB.getId()).isDirty()).isTrue();
        assertThat(this.entitlementCurator.get(unrelatedEnt.getId()).isDirty()).isFalse();

        // Once regenerated, an identical refresh must not invalidate the entitlement again.
        this.entitlementCurator.get(entitlementB.getId()).setDirty(false);
        this.commitTransaction();
        this.getEntityManager().clear();
        this.refresh(ownerB, subscriptionB);
        this.beginTransaction();
        this.getEntityManager().clear();
        assertThat(this.entitlementCurator.get(entitlementB.getId()).isDirty()).isFalse();
    }

    private SubscriptionDTO subscription(Pool pool) {
        SubscriptionDTO subscription = new SubscriptionDTO();
        subscription.setId(pool.getSubscriptionId());
        subscription.setOwner(this.modelTranslator.translate(pool.getOwner(), OwnerDTO.class));
        subscription.setProduct(this.modelTranslator.translate(pool.getProduct(),
            org.candlepin.dto.manifest.v1.ProductDTO.class));
        subscription.setQuantity(pool.getQuantity());
        subscription.setStartDate(pool.getStartDate());
        subscription.setEndDate(pool.getEndDate());
        subscription.setContractNumber(pool.getContractNumber());
        subscription.setAccountNumber(pool.getAccountNumber());
        subscription.setOrderNumber(pool.getOrderNumber());
        return subscription;
    }

    private void refresh(Owner owner, SubscriptionDTO subscription) {
        this.injector.getInstance(RefresherFactory.class)
            .getRefresher(new MockSubscriptionServiceAdapter(List.of(subscription)))
            .setLazyCertificateRegeneration(true)
            .add(owner)
            .run();
    }
}
