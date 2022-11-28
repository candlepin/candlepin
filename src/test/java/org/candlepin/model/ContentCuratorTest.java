/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;



/**
 * ContentCuratorTest
 */
public class ContentCuratorTest extends DatabaseTestFixture {

    private Content createOrphanedContent() {
        String contentId = "test-content-" + TestUtil.randomInt();
        Content content = TestUtil.createContent(contentId, contentId);
        return this.contentCurator.create(content);
    }

    private Product createProductWithContent(Owner owner, Content... contents) {
        String productId = "test-product-" + TestUtil.randomInt();
        Product product = TestUtil.createProduct(productId, productId);

        if (contents != null && contents.length > 0) {
            for (Content content : contents) {
                product.addContent(content, true);
            }
        }

        return this.createProduct(product, owner);
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
    public void testGetOrphanedContentUuids() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent(owner);
        Content orphanedContent1 = this.createOrphanedContent();
        Content orphanedContent2 = this.createOrphanedContent();

        List<String> output = this.contentCurator.getOrphanedContentUuids();
        assertNotNull(output);
        assertEquals(2, output.size());

        assertFalse(output.contains(content1.getUuid()));
        assertTrue(output.contains(orphanedContent1.getUuid()));
        assertTrue(output.contains(orphanedContent2.getUuid()));
    }

    @Test
    public void testGetOrphanedContentUuidsWithNoContent() {
        List<String> output = this.contentCurator.getOrphanedContentUuids();
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetOrphanedContentUuidsWithNoOrphans() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);
        Content content3 = this.createContent(owner);

        List<String> output = this.contentCurator.getOrphanedContentUuids();
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsReferencingContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content content1 = this.createContent(owner1);
        Content content2 = this.createContent(owner2);
        Content content3 = this.createContent(owner1);

        Product product1 = this.createProductWithContent(owner1, content1);
        Product product2 = this.createProductWithContent(owner2, content2, content3);
        Product product3 = this.createProductWithContent(owner1);

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
        Owner owner = this.createOwner();

        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);
        Content content3 = this.createContent(owner);

        Product product1 = this.createProductWithContent(owner, content1, content2);
        Product product2 = this.createProductWithContent(owner, content2, content3);
        Product product3 = this.createProductWithContent(owner, content3, content1);

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
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content content1 = this.createContent(owner1);
        Content content2 = this.createContent(owner2);
        Content content3 = this.createContent(owner1);

        Product product1 = this.createProduct(owner1);
        Product product2 = this.createProduct(owner2);
        Product product3 = this.createProduct(owner1);

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

}
