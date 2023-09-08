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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * Test suite for the OwnerContentCurator class
 */
public class OwnerContentCuratorTest extends DatabaseTestFixture {

    /**
     * Injects a mapping from an owner to a content directly, avoiding the use of our curators.
     *
     * @param owner
     * @param content
     * @return a new OwnerContent mapping object
     */
    private OwnerContent createOwnerContentMapping(Owner owner, Content content) {
        OwnerContent mapping = new OwnerContent(owner, content);
        this.getEntityManager().persist(mapping);
        this.getEntityManager().flush();

        return mapping;
    }

    private boolean isContentMappedToOwner(Content content, Owner owner) {
        String jpql = "SELECT count(op) FROM OwnerContent op " +
            "WHERE op.owner.id = :owner_id AND op.content.uuid = :content_uuid";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("content_uuid", content.getUuid())
            .getSingleResult();

        return count > 0;
    }

    @Test
    public void testGetActiveContentByOwner() {
        Owner owner = createOwner("test-owner", "owner-test");
        Map<Content, Boolean> expectedContentMap = new HashMap();

        Content c1 = this.createContent(owner);
        Content c2 = this.createContent(owner);
        Content c3 = this.createContent(owner);

        Product providedProduct = TestUtil.createProduct();
        providedProduct.addContent(c3, true);
        this.createProduct(providedProduct);
        expectedContentMap.put(c3, true);

        Product derivedProvidedProduct = TestUtil.createProduct();
        derivedProvidedProduct.addContent(c1, true);
        this.createProduct(derivedProvidedProduct);
        expectedContentMap.put(c1, true);

        Product derivedProduct = TestUtil.createProduct();
        derivedProduct.addProvidedProduct(derivedProvidedProduct);
        derivedProduct.addContent(c2, false);
        this.createProduct(derivedProduct);
        expectedContentMap.put(c2, false);


        // Build product & pool
        Product product = TestUtil.createProduct()
            .setDerivedProduct(derivedProduct);

        product.addContent(c1, false);
        product.addProvidedProduct(providedProduct);

        this.createProduct(product);
        Pool activePoolOne = createPool(owner, product);

        // Inactive pool
        Product productx = TestUtil.createProduct();
        this.createProduct(productx);
        this.createPool(owner, productx, 10L, Util.yesterday(), Util.yesterday());

        Map<Content, Boolean> actualResult =
            this.ownerContentCurator.getActiveContentByOwner(owner.getId());

        assertEquals(actualResult.size(), 3);
        assertEquals(expectedContentMap, actualResult);

        // Make sure c1 is true in actual result, as enabled content will have precedence.
        assertEquals(actualResult.get(c1), true);
    }

    // @Test
    // public void testRebuildOwnerContentMapping() {
    //     Owner owner1 = this.createOwner("test_owner-1");
    //     Owner owner2 = this.createOwner("test_owner-2");
    //     Owner owner3 = this.createOwner("test_owner-3");

    //     Content content1 = this.createContent("test_content-1", owner1, owner2);
    //     Content content2 = this.createContent("test_content-2", owner1, owner2);
    //     Content content3 = this.createContent("test_content-3", owner2);
    //     Content content4 = this.createContent("test_content-4", owner3);
    //     Content content5 = this.createContent("test_content-5", owner3);

    //     // Remap owner2 to contents: 2, 3, and 4
    //     Map<String, String> pidMap = Map.of(
    //         content2.getId(), content2.getUuid(),
    //         content3.getId(), content3.getUuid(),
    //         content4.getId(), content4.getUuid());

    //     this.ownerContentCurator.rebuildOwnerContentMapping(owner2, pidMap);

    //     // Verify owner1 mappings are unaffected
    //     assertTrue(this.isContentMappedToOwner(content1, owner1));
    //     assertTrue(this.isContentMappedToOwner(content2, owner1));
    //     assertFalse(this.isContentMappedToOwner(content3, owner1));
    //     assertFalse(this.isContentMappedToOwner(content4, owner1));
    //     assertFalse(this.isContentMappedToOwner(content5, owner1));

    //     // Verify owner3 mappings are unaffected
    //     assertFalse(this.isContentMappedToOwner(content1, owner3));
    //     assertFalse(this.isContentMappedToOwner(content2, owner3));
    //     assertFalse(this.isContentMappedToOwner(content3, owner3));
    //     assertTrue(this.isContentMappedToOwner(content4, owner3));
    //     assertTrue(this.isContentMappedToOwner(content5, owner3));

    //     // Verify owner2 is mapped according to the config above
    //     assertFalse(this.isContentMappedToOwner(content1, owner2));
    //     assertTrue(this.isContentMappedToOwner(content2, owner2));
    //     assertTrue(this.isContentMappedToOwner(content3, owner2));
    //     assertTrue(this.isContentMappedToOwner(content4, owner2));
    //     assertFalse(this.isContentMappedToOwner(content5, owner2));
    // }

    // @Test
    // public void testRebuildOwnerContentMappingWithConflictingContentIDs() {
    //     Owner owner1 = this.createOwner("test_owner-1");
    //     Owner owner2 = this.createOwner("test_owner-2");

    //     Content content1v1 = TestUtil.createContent("test_content-1", "test content 1 v1");
    //     Content content1v2 = TestUtil.createContent("test_content-1", "test content 1 v2");

    //     this.createContent(content1v1, owner1, owner2);
    //     this.createContent(content1v2);

    //     Map<String, String> pidMap = Map.of(content1v2.getId(), content1v2.getUuid());
    //     this.ownerContentCurator.rebuildOwnerContentMapping(owner2, pidMap);

    //     // owner 2 should now point to v2 of the content; owner1 should remain unaffected
    //     assertTrue(this.isContentMappedToOwner(content1v1, owner1));
    //     assertFalse(this.isContentMappedToOwner(content1v2, owner1));

    //     assertFalse(this.isContentMappedToOwner(content1v1, owner2));
    //     assertTrue(this.isContentMappedToOwner(content1v2, owner2));
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @NullAndEmptySource
    // public void testRebuildOwnerContentMappingAcceptsNullAndEmptyPIDMap(Map<String, String> pidMap) {
    //     Owner owner1 = this.createOwner("test_owner-1");
    //     Owner owner2 = this.createOwner("test_owner-2");
    //     Owner owner3 = this.createOwner("test_owner-3");

    //     Content content1 = this.createContent("test_content-1", owner1, owner2);
    //     Content content2 = this.createContent("test_content-2", owner1, owner2);
    //     Content content3 = this.createContent("test_content-3", owner2);
    //     Content content4 = this.createContent("test_content-4", owner3);
    //     Content content5 = this.createContent("test_content-5", owner3);

    //     // As designed, passing a null or empty map should result in the org being mapped to nothing
    //     this.ownerContentCurator.rebuildOwnerContentMapping(owner2, pidMap);

    //     // Verify owner1 mappings are unaffected
    //     assertTrue(this.isContentMappedToOwner(content1, owner1));
    //     assertTrue(this.isContentMappedToOwner(content2, owner1));
    //     assertFalse(this.isContentMappedToOwner(content3, owner1));
    //     assertFalse(this.isContentMappedToOwner(content4, owner1));
    //     assertFalse(this.isContentMappedToOwner(content5, owner1));

    //     // Verify owner3 mappings are unaffected
    //     assertFalse(this.isContentMappedToOwner(content1, owner3));
    //     assertFalse(this.isContentMappedToOwner(content2, owner3));
    //     assertFalse(this.isContentMappedToOwner(content3, owner3));
    //     assertTrue(this.isContentMappedToOwner(content4, owner3));
    //     assertTrue(this.isContentMappedToOwner(content5, owner3));

    //     // Verify owner2 isn't mapped to anything
    //     assertFalse(this.isContentMappedToOwner(content1, owner2));
    //     assertFalse(this.isContentMappedToOwner(content2, owner2));
    //     assertFalse(this.isContentMappedToOwner(content3, owner2));
    //     assertFalse(this.isContentMappedToOwner(content4, owner2));
    //     assertFalse(this.isContentMappedToOwner(content5, owner2));
    // }

    // @Test
    // public void testRebuildOwnerContentMappingRequiresOwner() {
    //     Map<String, String> pidMap = Map.of("p1", "p1_uuid");

    //     assertThrows(IllegalArgumentException.class, () ->
    //         this.ownerContentCurator.rebuildOwnerContentMapping(null, pidMap));
    // }

    // @Test
    // public void testRebuildOwnerContentMappingRequiresOwnerWithID() {
    //     Map<String, String> pidMap = Map.of("p1", "p1_uuid");

    //     Owner owner = new Owner();

    //     assertThrows(IllegalArgumentException.class, () ->
    //         this.ownerContentCurator.rebuildOwnerContentMapping(owner, pidMap));
    // }
}
