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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;

import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@SpecTest
@OnlyInHosted
class HostedTestSpecTest {

    private static HostedTestApi hosted;

    @BeforeAll
    static void beforeAll() {
        hosted = ApiClients.admin().hosted();
    }

    @Test
    void hostedShouldBeAlive() {
        Boolean isAlive = hosted.isAlive();

        assertThat(isAlive).isTrue();
    }

    @Test
    void shouldCreateOwner() {
        OwnerDTO owner = hosted.createOwner(Owners.random());

        assertThat(owner).isNotNull();
    }

    @Test
    void shouldCreateSubscription() {
        OwnerDTO owner = hosted.createOwner(Owners.random());
        ProductDTO product = hosted.createProduct(Products.random());

        SubscriptionDTO subscription = hosted.createSubscription(Subscriptions.random(owner, product));

        assertThat(subscription).isNotNull();
    }

    @Test
    void shouldListSubscriptions() {
        OwnerDTO owner = hosted.createOwner(Owners.random());
        ProductDTO product = hosted.createProduct(Products.random());
        hosted.createSubscription(Subscriptions.random(owner, product));

        List<SubscriptionDTO> found = hosted.listSubscriptions();

        assertThat(found).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldFindSubscription() {
        OwnerDTO owner = hosted.createOwner(Owners.random());
        ProductDTO product = hosted.createProduct(Products.random());
        SubscriptionDTO subscription = hosted.createSubscription(Subscriptions.random(owner, product));

        SubscriptionDTO found = hosted.getSubscription(subscription.getId());

        assertThat(found).isNotNull();
    }

    @Test
    void shouldUpdateSubscriptions() {
        OwnerDTO owner = hosted.createOwner(Owners.random());
        ProductDTO product = hosted.createProduct(Products.random());
        SubscriptionDTO subscription = hosted.createSubscription(Subscriptions.random(owner, product));

        subscription.quantity(100L);
        SubscriptionDTO updatedSubscription = hosted.updateSubscription(subscription.getId(), subscription);

        assertThat(updatedSubscription.getQuantity()).isEqualTo(100);
    }

    @Test
    void shouldDeleteSubscriptions() {
        OwnerDTO owner = hosted.createOwner(Owners.random());
        ProductDTO product = hosted.createProduct(Products.random());
        SubscriptionDTO subscription = hosted.createSubscription(Subscriptions.random(owner, product));

        hosted.deleteSubscription(subscription.getId());

        assertNotFound(() -> hosted.getSubscription(subscription.getId()));
    }


    @Test
    void shouldListProducts() {
        hosted.createProduct(Products.random());

        List<ProductDTO> foundProducts = hosted.listProducts();

        assertThat(foundProducts).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldFindProduct() {
        ProductDTO product = hosted.createProduct(Products.random());

        ProductDTO foundProduct = hosted.getProduct(product.getId());

        assertThat(foundProduct).isNotNull();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductDTO createdProduct = hosted.createProduct(Products.random());

        assertThat(createdProduct).isNotNull();
    }


    @Test
    void shouldUpdateProduct() {
        ProductDTO createdProduct = hosted.createProduct(Products.random());

        createdProduct.name(StringUtil.random("new_name"));
        ProductDTO updatedProduct = hosted.updateProduct(createdProduct.getId(), createdProduct);

        assertThat(updatedProduct.getName()).isEqualTo(createdProduct.getName());
    }

    @Test
    void shouldDeleteProduct() {
        ProductDTO createdProduct = hosted.createProduct(Products.random());

        hosted.deleteProduct(createdProduct.getId());

        assertNotFound(() -> hosted.getProduct(createdProduct.getId()));
    }

    @Test
    void shouldListContent() {
        hosted.createContent(Contents.random());

        List<ContentDTO> found = hosted.listContent();

        assertThat(found).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldFindContent() {
        ContentDTO content = hosted.createContent(Contents.random());

        ContentDTO found = hosted.getContent(content.getId());

        assertThat(found).isNotNull();
    }

    @Test
    void shouldCreateContent() throws Exception {
        ContentDTO content = hosted.createContent(Contents.random());

        assertThat(content).isNotNull();
    }

    @Test
    void shouldUpdateContent() {
        ContentDTO content = hosted.createContent(Contents.random());

        content.name(StringUtil.random("new_name"));
        ContentDTO updatedContent = hosted.updateContent(content.getId(), content);

        assertThat(updatedContent.getName()).isEqualTo(content.getName());
    }

    @Test
    void shouldDeleteContent() {
        ContentDTO content = hosted.createContent(Contents.random());

        hosted.deleteContent(content.getId());

        assertNotFound(() -> hosted.getContent(content.getId()));
    }

    @Test
    void shouldAddProductContent() {
        ContentDTO content = hosted.createContent(Contents.random());
        ProductDTO product = hosted.createProduct(Products.random());

        product.name(StringUtil.random("new_name"));
        ProductDTO updatedProduct = hosted.addContentToProduct(product.getId(), content.getId(), true);

        assertThat(updatedProduct.getProductContent()).hasSize(1);
    }

    @Test
    void shouldAddProductContents() {
        ContentDTO content1 = hosted.createContent(Contents.random());
        ContentDTO content2 = hosted.createContent(Contents.random());
        ContentDTO content3 = hosted.createContent(Contents.random());
        ProductDTO product = hosted.createProduct(Products.random());

        product.name(StringUtil.random("new_name"));
        ProductDTO updatedProduct = hosted.addContentToProduct(product.getId(), Map.ofEntries(
            Map.entry(content1.getId(), true),
            Map.entry(content2.getId(), true),
            Map.entry(content3.getId(), true)
        ));

        assertThat(updatedProduct.getProductContent()).hasSize(3);
    }

    @Test
    void shouldRemoveProductContent() {
        ContentDTO content1 = hosted.createContent(Contents.random());
        ContentDTO content2 = hosted.createContent(Contents.random());
        ProductDTO product = hosted.createProduct(Products.random());

        product.name(StringUtil.random("new_name"));
        hosted.addContentToProduct(product.getId(), content1.getId(), true);
        ProductDTO updatedProduct = hosted.addContentToProduct(product.getId(), content2.getId(), true);

        assertThat(updatedProduct.getProductContent()).hasSize(2);

        ProductDTO updatedProduct2 = hosted.removeContentFromProduct(product.getId(), content2.getId());
        assertThat(updatedProduct2.getProductContent()).hasSize(1);
    }

    @Test
    void shouldRemoveProductContents() {
        ContentDTO content1 = hosted.createContent(Contents.random());
        ContentDTO content2 = hosted.createContent(Contents.random());
        ContentDTO content3 = hosted.createContent(Contents.random());
        ProductDTO createdProduct = hosted.createProduct(Products.random());

        ProductDTO updatedProduct = hosted.addContentToProduct(createdProduct.getId(), Map.ofEntries(
            Map.entry(content1.getId(), true),
            Map.entry(content2.getId(), true),
            Map.entry(content3.getId(), true)
        ));
        assertThat(updatedProduct.getProductContent()).hasSize(3);

        ProductDTO updatedProduct2 = hosted.removeContentFromProduct(createdProduct.getId(), List.of(
            content1.getId(),
            content3.getId()
        ));
        assertThat(updatedProduct2.getProductContent()).hasSize(1);
    }

}
