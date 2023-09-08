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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.PersistenceException;



/**
 * ContentCuratorTest
 */
public class ContentCuratorTest extends DatabaseTestFixture {

    private Product createProductWithContent(Content... contents) {
        String productId = "test-product-" + TestUtil.randomInt();
        Product product = TestUtil.createProduct(productId, productId);

        if (contents != null && contents.length > 0) {
            for (Content content : contents) {
                product.addContent(content, true);
            }
        }

        return this.productCurator.create(product);
    }

    private Product createProductWithContent(String productId, int contentCount) {
        Product product = new Product()
            .setId(productId)
            .setName(productId);

        for (int i = 0; i < contentCount; ++i) {
            String cid = productId + "_content-" + i;
            Content content = this.createContent(cid);

            product.addContent(content, true);
        }

        return this.productCurator.create(product);
    }

    @Test
    public void testCannotPersistIdenticalProducts() {
        Content c1 = new Content()
            .setId("test-content")
            .setName("test-content")
            .setType("content-type")
            .setLabel("content-label")
            .setVendor("content-vendor");

        this.contentCurator.create(c1, true);
        this.contentCurator.clear();

        Content c2 = new Content()
            .setId("test-content")
            .setName("test-content")
            .setType("content-type")
            .setLabel("content-label")
            .setVendor("content-vendor");

        assertThrows(PersistenceException.class, () -> this.contentCurator.create(c2, true));
    }

    @Test
    public void testDeleteCannotDeleteContentReferencedByProducts() {
        Content content = this.createContent();
        Product product = this.createProductWithContent(content);

        this.contentCurator.flush();

        assertThrows(PersistenceException.class, () -> {
            this.contentCurator.delete(content);
            this.contentCurator.flush();
        });
    }

    @Test
    public void testBulkDeleteByUuids() {
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        this.contentCurator.flush();
        this.contentCurator.clear();

        assertNotNull(this.contentCurator.get(content1.getUuid()));
        assertNotNull(this.contentCurator.get(content2.getUuid()));
        assertNotNull(this.contentCurator.get(content3.getUuid()));

        this.contentCurator.flush();
        this.contentCurator.clear();

        int output = this.contentCurator.bulkDeleteByUuids(Set.of(content1.getUuid(), content2.getUuid()));
        assertEquals(2, output);

        assertNull(this.contentCurator.get(content1.getUuid()));
        assertNull(this.contentCurator.get(content2.getUuid()));
        assertNotNull(this.contentCurator.get(content3.getUuid()));
    }

    @Test
    public void testBulkDeleteByUuidsCascadesToChildren() {
        // Impl note: aside from the required fields, we *must* set some modified/required product IDs
        // to populate the child collection table for this test.

        Content content1 = new Content()
            .setId("test_content-1")
            .setName("test content 1")
            .setLabel("test_content-1")
            .setType("content-type")
            .setVendor("test vendor")
            .setModifiedProductIds(Set.of("pid1", "pid2", "pid3"));

        Content content2 = new Content()
            .setId("test_content-2")
            .setName("test content 2")
            .setLabel("test_content-2")
            .setType("content-type")
            .setVendor("test vendor")
            .setModifiedProductIds(Set.of("pid1", "pid2", "pid3"));

        content1 = this.contentCurator.create(content1);
        content2 = this.contentCurator.create(content2);

        this.contentCurator.flush();
        this.contentCurator.clear();

        int output = this.contentCurator.bulkDeleteByUuids(Set.of(content1.getUuid(), content2.getUuid()));
        assertEquals(2, output);

        assertNull(this.contentCurator.get(content1.getUuid()));
        assertNull(this.contentCurator.get(content2.getUuid()));
    }

    @Test
    public void testBulkDeleteByUuidsCannotDeleteContentReferencedByProducts() {
        Content content = this.createContent();
        Product product = this.createProductWithContent(content);

        this.contentCurator.flush();

        assertThrows(PersistenceException.class, () ->
            this.contentCurator.bulkDeleteByUuids(Set.of(content.getUuid())));
    }

    @Test
    public void testBulkDeleteByUuidsHandlesEmptyInput() {
        int output = this.contentCurator.bulkDeleteByUuids(Set.of());
        assertEquals(0, output);
    }

    @Test
    public void testBulkDeleteByUuidsHandlesNullInput() {
        int output = this.contentCurator.bulkDeleteByUuids(null);
        assertEquals(0, output);
    }

    @Test
    public void testGetProductsReferencingContent() {
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = this.createProductWithContent(content1);
        Product product2 = this.createProductWithContent(content2, content3);
        Product product3 = this.createProductWithContent();

        Set<String> input = Set.of(content1.getUuid(), content2.getUuid(), content3.getUuid());

        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(input);
        assertNotNull(output);
        assertEquals(3, output.size());

        assertTrue(output.containsKey(content1.getUuid()));
        assertEquals(Set.of(product1.getUuid()), output.get(content1.getUuid()));

        assertTrue(output.containsKey(content2.getUuid()));
        assertEquals(Set.of(product2.getUuid()), output.get(content2.getUuid()));

        assertTrue(output.containsKey(content3.getUuid()));
        assertEquals(Set.of(product2.getUuid()), output.get(content3.getUuid()));
    }

    @Test
    public void testGetProductsRefrencingContentWithMultipleReferences() {
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = this.createProductWithContent(content1, content2);
        Product product2 = this.createProductWithContent(content2, content3);
        Product product3 = this.createProductWithContent(content3, content1);

        Set<String> input = Set.of(content1.getUuid(), content2.getUuid(), content3.getUuid());

        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(input);
        assertNotNull(output);
        assertEquals(3, output.size());

        assertTrue(output.containsKey(content1.getUuid()));
        assertEquals(Set.of(product1.getUuid(), product3.getUuid()), output.get(content1.getUuid()));

        assertTrue(output.containsKey(content2.getUuid()));
        assertEquals(Set.of(product1.getUuid(), product2.getUuid()), output.get(content2.getUuid()));

        assertTrue(output.containsKey(content3.getUuid()));
        assertEquals(Set.of(product2.getUuid(), product3.getUuid()), output.get(content3.getUuid()));
    }

    @Test
    public void testGetProductsReferencingContentWithNoReferences() {
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        Set<String> input = Set.of(content1.getUuid(), content2.getUuid(), content3.getUuid());

        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsReferencingContentHandlesEmptyInput() {
        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(Set.of());
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsReferencingContentHandlesNullInput() {
        Map<String, Set<String>> output = this.contentCurator.getProductsReferencingContent(null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }


    @Test
    public void testGetChildrenContentOfProductsByUuids() {
        Product product1 = this.createProductWithContent("p1", 0);
        Product product2 = this.createProductWithContent("p2", 1);
        Product product3 = this.createProductWithContent("p3", 2);
        Product product4 = this.createProductWithContent("p4", 3);

        List<Product> products = List.of(product1, product2, product3, product4);

        Set<Content> expected = products.stream()
            .flatMap(product -> product.getProductContent().stream())
            .map(ProductContent::getContent)
            .collect(Collectors.toSet());

        List<String> input = products.stream()
            .map(Product::getUuid)
            .collect(Collectors.toList());

        Set<Content> output = this.contentCurator.getChildrenContentOfProductsByUuids(input);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testGetChildrenContentOfProductsByUuidsIgnoresInvalidProductUuids() {
        Product product1 = this.createProductWithContent("p1", 1);
        Product product2 = this.createProductWithContent("p2", 2);
        Product product3 = this.createProductWithContent("p3", 3);
        Product product4 = this.createProductWithContent("p4", 4);

        List<Product> products = List.of(product2, product3);

        Set<Content> expected = products.stream()
            .flatMap(product -> product.getProductContent().stream())
            .map(ProductContent::getContent)
            .collect(Collectors.toSet());

        List<String> input = Arrays.asList(product2.getUuid(), "invalid", product3.getUuid(), null);

        Set<Content> output = this.contentCurator.getChildrenContentOfProductsByUuids(input);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetChildrenContentOfProductsByUuidsHandlesNullAndEmptyCollections(List<String> input) {
        // Create some products just to ensure it doesn't pull random existing things for this case
        Product product1 = this.createProductWithContent("p1", 1);
        Product product2 = this.createProductWithContent("p2", 2);
        Product product3 = this.createProductWithContent("p3", 3);
        Product product4 = this.createProductWithContent("p4", 4);

        Set<Content> output = this.contentCurator.getChildrenContentOfProductsByUuids(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    // TODO: move this to content curator
    // @Test
    // public void testGetContentById() {
    //     Owner owner = this.createOwner();
    //     Content content = this.createContent();
    //     this.createOwnerContentMapping(owner, content);

    //     Content resultA = this.ownerContentCurator.getContentById(owner, content.getId());
    //     assertEquals(resultA, content);

    //     Content resultB = this.ownerContentCurator.getContentById(owner.getId(), content.getId());
    //     assertEquals(resultB, content);

    //     assertSame(resultA, resultB);
    // }

    // @Test
    // public void testGetContentByIdNoMapping() {
    //     Owner owner = this.createOwner();
    //     Content content = this.createContent();

    //     Content resultA = this.ownerContentCurator.getContentById(owner, content.getId());
    //     assertNull(resultA);

    //     Content resultB = this.ownerContentCurator.getContentById(owner.getId(), content.getId());
    //     assertNull(resultB);
    // }

    // @Test
    // public void testGetContentByIdWrongContentId() {
    //     Owner owner = this.createOwner();
    //     Content content1 = this.createContent();
    //     Content content2 = this.createContent();
    //     this.createOwnerContentMapping(owner, content1);

    //     Content resultA = this.ownerContentCurator.getContentById(owner, content2.getId());
    //     assertNull(resultA);

    //     Content resultB = this.ownerContentCurator.getContentById(owner.getId(), content2.getId());
    //     assertNull(resultB);
    // }

    // @Test
    // public void testGetContentByIds() {
    //     Owner owner = this.createOwner();
    //     Content content1 = this.createContent();
    //     Content content2 = this.createContent();
    //     Content content3 = this.createContent();
    //     this.createOwnerContentMapping(owner, content1);
    //     this.createOwnerContentMapping(owner, content2);

    //     Collection<String> ids = Arrays.asList(content1.getId(), content2.getId(), content3.getId(), "dud");
    //     Map<String, Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids);
    //     Map<String, Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids);

    //     assertEquals(2, contentA.size());

    //     assertTrue(contentA.containsKey(content1.getId()));
    //     assertEquals(content1, contentA.get(content1.getId()));

    //     assertTrue(contentA.containsKey(content2.getId()));
    //     assertEquals(content2, contentA.get(content2.getId()));

    //     assertFalse(contentA.containsKey(content3.getId()));

    //     assertEquals(contentA, contentB);
    // }

    // @Test
    // public void testGetContentByIdsNullList() {
    //     Owner owner = this.createOwner();
    //     Content content1 = this.createContent();
    //     Content content2 = this.createContent();
    //     Content content3 = this.createContent();
    //     this.createOwnerContentMapping(owner, content1);
    //     this.createOwnerContentMapping(owner, content2);

    //     Collection<String> ids = null;
    //     Map<String, Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids);
    //     Map<String, Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids);

    //     assertTrue(contentA.isEmpty());
    //     assertTrue(contentB.isEmpty());
    // }

    // @Test
    // public void testGetContentByIdsEmptyList() {
    //     Owner owner = this.createOwner();
    //     Content content1 = this.createContent();
    //     Content content2 = this.createContent();
    //     Content content3 = this.createContent();
    //     this.createOwnerContentMapping(owner, content1);
    //     this.createOwnerContentMapping(owner, content2);

    //     Collection<String> ids = Collections.<String>emptyList();
    //     Map<String, Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids);
    //     Map<String, Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids);

    //     assertTrue(contentA.isEmpty());
    //     assertTrue(contentB.isEmpty());
    // }
}
