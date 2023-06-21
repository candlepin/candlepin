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
    public void testGetContentById() {
        Owner owner = this.createOwner();
        Content content = this.createContent();
        this.createOwnerContentMapping(owner, content);

        Content resultA = this.ownerContentCurator.getContentById(owner, content.getId());
        assertEquals(resultA, content);

        Content resultB = this.ownerContentCurator.getContentById(owner.getId(), content.getId());
        assertEquals(resultB, content);

        assertSame(resultA, resultB);
    }

    @Test
    public void testGetContentByIdNoMapping() {
        Owner owner = this.createOwner();
        Content content = this.createContent();

        Content resultA = this.ownerContentCurator.getContentById(owner, content.getId());
        assertNull(resultA);

        Content resultB = this.ownerContentCurator.getContentById(owner.getId(), content.getId());
        assertNull(resultB);
    }

    @Test
    public void testGetContentByIdWrongContentId() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        this.createOwnerContentMapping(owner, content1);

        Content resultA = this.ownerContentCurator.getContentById(owner, content2.getId());
        assertNull(resultA);

        Content resultB = this.ownerContentCurator.getContentById(owner.getId(), content2.getId());
        assertNull(resultB);
    }

    @Test
    public void testGetOwnersByContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content = this.createContent();
        this.createOwnerContentMapping(owner1, content);
        this.createOwnerContentMapping(owner2, content);

        Collection<Owner> ownersA = this.ownerContentCurator.getOwnersByContent(content).list();
        Collection<Owner> ownersB = this.ownerContentCurator.getOwnersByContent(content.getId()).list();

        assertTrue(ownersA.contains(owner1));
        assertTrue(ownersA.contains(owner2));
        assertFalse(ownersA.contains(owner3));
        assertEquals(ownersA, ownersB);
    }

    @Test
    public void testGetOwnersByContentWithUnmappedContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content = this.createContent();

        Collection<Owner> ownersA = this.ownerContentCurator.getOwnersByContent(content).list();
        Collection<Owner> ownersB = this.ownerContentCurator.getOwnersByContent(content.getId()).list();

        assertTrue(ownersA.isEmpty());
        assertTrue(ownersB.isEmpty());
    }

    @Test
    public void testGetContentByOwnerId() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        List<Content> expected = List.of(content1, content2, content3);
        List<Content> actual = this.ownerContentCurator.getContentByOwner(owner.getId());

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(content -> assertTrue(actual.contains(content)));
    }

    @Test
    public void testGetContentByOwnerIdDoesNotIncludeUnmappedContent() {
        Owner owner = this.createOwner();

        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        Content contentU = this.createContent("unmapped_content");
        Product product = new Product()
            .setId("product1")
            .setName("product1");
        product.addContent(contentU, true);

        this.createProduct(product, owner);

        List<Content> expected = List.of(content1, content2, content3);
        List<Content> actual = this.ownerContentCurator.getContentByOwner(owner.getId());

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(content -> assertTrue(actual.contains(content)));
    }

    @Test
    public void testGetContentByOwnerIdDoesNotIncludeOtherOrgContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content content1 = this.createContent("content1", owner1);
        Content content2 = this.createContent("content2", owner2);
        Content content3 = this.createContent("content3", owner1, owner2);

        List<Content> expected = List.of(content1, content3);
        List<Content> actual = this.ownerContentCurator.getContentByOwner(owner1.getId());

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(content -> assertTrue(actual.contains(content)));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetContentByOwnerIdAcceptsNullAndEmptyInput(String input) {
        Owner owner = this.createOwner();
        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        List<Content> output = this.ownerContentCurator.getContentByOwner(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetContentByOwnerIdAcceptsInvalidInput() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        List<Content> output = this.ownerContentCurator.getContentByOwner("invalid owner id");
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetContentByOwner() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        List<Content> expected = List.of(content1, content2, content3);
        List<Content> actual = this.ownerContentCurator.getContentByOwner(owner);

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(content -> assertTrue(actual.contains(content)));
    }

    @Test
    public void testGetContentByOwnerDoesNotIncludeUnmappedContent() {
        Owner owner = this.createOwner();

        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        Content contentU = this.createContent("unmapped_content");
        Product product = new Product()
            .setId("product1")
            .setName("product1");
        product.addContent(contentU, true);

        this.createProduct(product, owner);

        List<Content> expected = List.of(content1, content2, content3);
        List<Content> actual = this.ownerContentCurator.getContentByOwner(owner);

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(content -> assertTrue(actual.contains(content)));
    }

    @Test
    public void testGetContentByOwnerDoesNotIncludeOtherOrgContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content content1 = this.createContent("content1", owner1);
        Content content2 = this.createContent("content2", owner2);
        Content content3 = this.createContent("content3", owner1, owner2);

        List<Content> expected = List.of(content1, content3);
        List<Content> actual = this.ownerContentCurator.getContentByOwner(owner1);

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(content -> assertTrue(actual.contains(content)));
    }

    @Test
    public void testGetContentByOwnerAcceptsNullInput() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        List<Content> output = this.ownerContentCurator.getContentByOwner((Owner) null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetContentByOwnerAcceptsOwnerWithNullAndEmptyId(String ownerId) {
        Owner owner = this.createOwner();
        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        Owner input = new Owner().setId(ownerId);

        List<Content> output = this.ownerContentCurator.getContentByOwner(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetContentByOwnerAcceptsOwnerWithInvalidId() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent("content1", owner);
        Content content2 = this.createContent("content2", owner);
        Content content3 = this.createContent("content3", owner);

        Owner input = new Owner().setId("invalid owner id");

        List<Content> output = this.ownerContentCurator.getContentByOwner(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetContentByOwnerCPQ() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();
        this.createOwnerContentMapping(owner, content1);
        this.createOwnerContentMapping(owner, content2);

        Collection<Content> contentA = this.ownerContentCurator.getContentByOwnerCPQ(owner).list();
        Collection<Content> contentB = this.ownerContentCurator.getContentByOwnerCPQ(owner.getId()).list();

        assertTrue(contentA.contains(content1));
        assertTrue(contentA.contains(content2));
        assertFalse(contentA.contains(content3));
        assertEquals(contentA, contentB);
    }

    @Test
    public void testGetContentByOwnerCPQWithUnmappedContent() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Collection<Content> contentA = this.ownerContentCurator.getContentByOwnerCPQ(owner).list();
        Collection<Content> contentB = this.ownerContentCurator.getContentByOwnerCPQ(owner.getId()).list();

        assertTrue(contentA.isEmpty());
        assertTrue(contentB.isEmpty());
    }

    @Test
    public void testGetContentByIds() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();
        this.createOwnerContentMapping(owner, content1);
        this.createOwnerContentMapping(owner, content2);

        Collection<String> ids = Arrays.asList(content1.getId(), content2.getId(), content3.getId(), "dud");
        Map<String, Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids);
        Map<String, Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids);

        assertEquals(2, contentA.size());

        assertTrue(contentA.containsKey(content1.getId()));
        assertEquals(content1, contentA.get(content1.getId()));

        assertTrue(contentA.containsKey(content2.getId()));
        assertEquals(content2, contentA.get(content2.getId()));

        assertFalse(contentA.containsKey(content3.getId()));

        assertEquals(contentA, contentB);
    }

    @Test
    public void testGetContentByIdsNullList() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();
        this.createOwnerContentMapping(owner, content1);
        this.createOwnerContentMapping(owner, content2);

        Collection<String> ids = null;
        Map<String, Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids);
        Map<String, Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids);

        assertTrue(contentA.isEmpty());
        assertTrue(contentB.isEmpty());
    }

    @Test
    public void testGetContentByIdsEmptyList() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();
        this.createOwnerContentMapping(owner, content1);
        this.createOwnerContentMapping(owner, content2);

        Collection<String> ids = Collections.<String>emptyList();
        Map<String, Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids);
        Map<String, Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids);

        assertTrue(contentA.isEmpty());
        assertTrue(contentB.isEmpty());
    }

    @Test
    public void testGetOwnerCount() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content = this.createContent();

        assertEquals(0L, (long) this.ownerContentCurator.getOwnerCount(content));

        this.createOwnerContentMapping(owner1, content);
        assertEquals(1L, (long) this.ownerContentCurator.getOwnerCount(content));

        this.createOwnerContentMapping(owner2, content);
        assertEquals(2L, (long) this.ownerContentCurator.getOwnerCount(content));
    }

    @Test
    public void testIsContentMappedToOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content = this.createContent();

        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner2));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner3));

        this.createOwnerContentMapping(owner1, content);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner2));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner3));

        this.createOwnerContentMapping(owner2, content);

        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner1));
        assertTrue(this.ownerContentCurator.isContentMappedToOwner(content, owner2));
        assertFalse(this.ownerContentCurator.isContentMappedToOwner(content, owner3));
    }

    @Test
    public void testMapContentToOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        List<Owner> owners = Arrays.asList(owner1, owner2, owner3);
        List<Content> contents = Arrays.asList(content1, content2, content3);

        int mapped = 0;
        for (int i = 0; i < owners.size(); ++i) {
            for (int j = 0; j < contents.size(); ++j) {
                int offset = 0;

                for (Owner owner : owners) {
                    for (Content content : contents) {
                        if (mapped > offset++) {
                            assertTrue(this.isContentMappedToOwner(content, owner));
                        }
                        else {
                            assertFalse(this.isContentMappedToOwner(content, owner));
                        }
                    }
                }

                boolean result = this.ownerContentCurator.mapContentToOwner(contents.get(j), owners.get(i));
                assertTrue(result);

                result = this.ownerContentCurator.mapContentToOwner(contents.get(j), owners.get(i));
                assertFalse(result);

                ++mapped;
            }
        }
    }

    @Test
    public void testMapContentToOwnerUnmappedOwner() {
        Owner owner = new Owner().setKey("unmapped");
        Content content = this.createContent();
        assertThrows(IllegalStateException.class, () ->
            this.ownerContentCurator.mapContentToOwner(content, owner)
        );
    }

    @Test
    public void testMapContentToOwnerUnmappedContent() {
        Owner owner = this.createOwner();
        Content content = TestUtil.createContent();
        assertThrows(IllegalStateException.class, () ->
            this.ownerContentCurator.mapContentToOwner(content, owner)
        );
    }

    @Test
    public void testMapContentToOwners() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        assertFalse(this.isContentMappedToOwner(content1, owner1));
        assertFalse(this.isContentMappedToOwner(content2, owner1));
        assertFalse(this.isContentMappedToOwner(content3, owner1));
        assertFalse(this.isContentMappedToOwner(content1, owner2));
        assertFalse(this.isContentMappedToOwner(content2, owner2));
        assertFalse(this.isContentMappedToOwner(content3, owner2));
        assertFalse(this.isContentMappedToOwner(content1, owner3));
        assertFalse(this.isContentMappedToOwner(content2, owner3));
        assertFalse(this.isContentMappedToOwner(content3, owner3));

        this.ownerContentCurator.mapContentToOwners(content1, owner1, owner2);

        assertTrue(this.isContentMappedToOwner(content1, owner1));
        assertFalse(this.isContentMappedToOwner(content2, owner1));
        assertFalse(this.isContentMappedToOwner(content3, owner1));
        assertTrue(this.isContentMappedToOwner(content1, owner2));
        assertFalse(this.isContentMappedToOwner(content2, owner2));
        assertFalse(this.isContentMappedToOwner(content3, owner2));
        assertFalse(this.isContentMappedToOwner(content1, owner3));
        assertFalse(this.isContentMappedToOwner(content2, owner3));
        assertFalse(this.isContentMappedToOwner(content3, owner3));
    }

    @Test
    public void testMapOwnerToContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        assertFalse(this.isContentMappedToOwner(content1, owner1));
        assertFalse(this.isContentMappedToOwner(content2, owner1));
        assertFalse(this.isContentMappedToOwner(content3, owner1));
        assertFalse(this.isContentMappedToOwner(content1, owner2));
        assertFalse(this.isContentMappedToOwner(content2, owner2));
        assertFalse(this.isContentMappedToOwner(content3, owner2));
        assertFalse(this.isContentMappedToOwner(content1, owner3));
        assertFalse(this.isContentMappedToOwner(content2, owner3));
        assertFalse(this.isContentMappedToOwner(content3, owner3));

        this.ownerContentCurator.mapOwnerToContent(owner1, content1, content2);

        assertTrue(this.isContentMappedToOwner(content1, owner1));
        assertTrue(this.isContentMappedToOwner(content2, owner1));
        assertFalse(this.isContentMappedToOwner(content3, owner1));
        assertFalse(this.isContentMappedToOwner(content1, owner2));
        assertFalse(this.isContentMappedToOwner(content2, owner2));
        assertFalse(this.isContentMappedToOwner(content3, owner2));
        assertFalse(this.isContentMappedToOwner(content1, owner3));
        assertFalse(this.isContentMappedToOwner(content2, owner3));
        assertFalse(this.isContentMappedToOwner(content3, owner3));
    }

    @Test
    public void testRemoveContentFromOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        List<Owner> owners = Arrays.asList(owner1, owner2, owner3);
        List<Content> contents = Arrays.asList(content1, content2, content3);

        this.createOwnerContentMapping(owner1, content1);
        this.createOwnerContentMapping(owner1, content2);
        this.createOwnerContentMapping(owner1, content3);
        this.createOwnerContentMapping(owner2, content1);
        this.createOwnerContentMapping(owner2, content2);
        this.createOwnerContentMapping(owner2, content3);
        this.createOwnerContentMapping(owner3, content1);
        this.createOwnerContentMapping(owner3, content2);
        this.createOwnerContentMapping(owner3, content3);

        int removed = 0;
        for (int i = 0; i < owners.size(); ++i) {
            for (int j = 0; j < contents.size(); ++j) {
                int offset = 0;

                for (Owner owner : owners) {
                    for (Content content : contents) {
                        if (removed > offset++) {
                            assertFalse(this.isContentMappedToOwner(content, owner));
                        }
                        else {
                            assertTrue(this.isContentMappedToOwner(content, owner));
                        }
                    }
                }

                boolean result = this.ownerContentCurator.removeOwnerFromContent(
                    contents.get(j), owners.get(i)
                );

                assertTrue(result);

                result = this.ownerContentCurator.removeOwnerFromContent(
                    contents.get(j), owners.get(i)
                );

                assertFalse(result);


                ++removed;
            }
        }
    }

    @Test
    public void testClearOwnersForContent() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        this.createOwnerContentMapping(owner1, content1);
        this.createOwnerContentMapping(owner1, content2);
        this.createOwnerContentMapping(owner1, content3);
        this.createOwnerContentMapping(owner2, content1);
        this.createOwnerContentMapping(owner2, content2);
        this.createOwnerContentMapping(owner2, content3);
        this.createOwnerContentMapping(owner3, content1);
        this.createOwnerContentMapping(owner3, content2);
        this.createOwnerContentMapping(owner3, content3);

        this.ownerContentCurator.clearOwnersForContent(content1);

        assertFalse(this.isContentMappedToOwner(content1, owner1));
        assertTrue(this.isContentMappedToOwner(content2, owner1));
        assertTrue(this.isContentMappedToOwner(content3, owner1));
        assertFalse(this.isContentMappedToOwner(content1, owner2));
        assertTrue(this.isContentMappedToOwner(content2, owner2));
        assertTrue(this.isContentMappedToOwner(content3, owner2));
        assertFalse(this.isContentMappedToOwner(content1, owner3));
        assertTrue(this.isContentMappedToOwner(content2, owner3));
        assertTrue(this.isContentMappedToOwner(content3, owner3));
    }

    @Test
    public void testClearContentForOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        this.createOwnerContentMapping(owner1, content1);
        this.createOwnerContentMapping(owner1, content2);
        this.createOwnerContentMapping(owner1, content3);
        this.createOwnerContentMapping(owner2, content1);
        this.createOwnerContentMapping(owner2, content2);
        this.createOwnerContentMapping(owner2, content3);
        this.createOwnerContentMapping(owner3, content1);
        this.createOwnerContentMapping(owner3, content2);
        this.createOwnerContentMapping(owner3, content3);

        this.ownerContentCurator.clearContentForOwner(owner1);

        assertFalse(this.isContentMappedToOwner(content1, owner1));
        assertFalse(this.isContentMappedToOwner(content2, owner1));
        assertFalse(this.isContentMappedToOwner(content3, owner1));
        assertTrue(this.isContentMappedToOwner(content1, owner2));
        assertTrue(this.isContentMappedToOwner(content2, owner2));
        assertTrue(this.isContentMappedToOwner(content3, owner2));
        assertTrue(this.isContentMappedToOwner(content1, owner3));
        assertTrue(this.isContentMappedToOwner(content2, owner3));
        assertTrue(this.isContentMappedToOwner(content3, owner3));
    }

    @Test
    public void testUpdateOwnerContentReferences() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Content original = this.createContent("c1", "c1a", owner1, owner2);
        Content modified = this.createContent("c1", "c1b");

        assertTrue(original.getUuid() != modified.getUuid());

        assertTrue(this.isContentMappedToOwner(original, owner1));
        assertTrue(this.isContentMappedToOwner(original, owner2));
        assertFalse(this.isContentMappedToOwner(modified, owner1));
        assertFalse(this.isContentMappedToOwner(modified, owner2));

        this.ownerContentCurator.updateOwnerContentReferences(owner1,
            Map.of(original.getUuid(), modified.getUuid()));

        assertFalse(this.isContentMappedToOwner(original, owner1));
        assertTrue(this.isContentMappedToOwner(modified, owner1));
        assertTrue(this.isContentMappedToOwner(original, owner2));
        assertFalse(this.isContentMappedToOwner(modified, owner2));
    }

    @Test
    public void testRemoveOwnerContentReferences() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Content content = this.createContent("c1", "c1a", owner1, owner2);

        Environment environment1 = this.createEnvironment(owner1, "test_env-1", "test_env-1", null, null,
            List.of(content));
        Environment environment2 = this.createEnvironment(owner2, "test_env-2", "test_env-2", null, null,
            List.of(content));

        assertTrue(this.isContentMappedToOwner(content, owner1));
        assertTrue(this.isContentMappedToOwner(content, owner2));

        this.ownerContentCurator.removeOwnerContentReferences(owner1, List.of(content.getUuid()));

        assertFalse(this.isContentMappedToOwner(content, owner1));
        assertTrue(this.isContentMappedToOwner(content, owner2));

        // Impl note: we must use evict+fetch here, as a refresh will attempt to refresh all child
        // entities, even if they've been removed at the database level
        this.environmentCurator.evict(environment1);
        this.environmentCurator.evict(environment2);
        environment1 = this.environmentCurator.get(environment1.getId());
        environment2 = this.environmentCurator.get(environment2.getId());

        Set<EnvironmentContent> envContent1 = environment1.getEnvironmentContent();
        assertNotNull(envContent1);
        assertTrue(envContent1.isEmpty());

        Set<EnvironmentContent> envContent2 = environment2.getEnvironmentContent();
        assertNotNull(envContent2);
        assertEquals(1, envContent2.size());

        Optional<String> contentId = envContent2.stream()
            .map(EnvironmentContent::getContentId)
            .findFirst();

        assertTrue(contentId.isPresent());
        assertEquals(content.getId(), contentId.get());
    }

    @Test
    public void testGetContentByVersionsSingleVersion() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Content c1a = this.createContent("c1", "c1a", owner1);
        Content c1b = this.createContent("c1", "c1b", owner2);
        Content c1c = this.createContent("c1", "c1c", owner3);
        Content c2a = this.createContent("c2", "c2a", owner1);

        Map<String, List<Content>> contentMap1 = this.ownerContentCurator
            .getContentByVersions(Collections.singleton(c1a.getEntityVersion()));
        Map<String, List<Content>> contentMap2 = this.ownerContentCurator
            .getContentByVersions(Collections.singleton(c1b.getEntityVersion()));

        assertEquals(1, contentMap1.size());
        assertNotNull(contentMap1.get("c1"));
        assertEquals(1, contentMap2.size());
        assertNotNull(contentMap2.get("c1"));

        List<String> uuidList1 = contentMap1.values()
            .stream()
            .flatMap(List::stream)
            .map(Content::getUuid)
            .collect(Collectors.toList());

        List<String> uuidList2 = contentMap2.values()
            .stream()
            .flatMap(List::stream)
            .map(Content::getUuid)
            .collect(Collectors.toList());

        assertEquals(1, uuidList1.size());
        assertThat(uuidList1, hasItems(c1a.getUuid()));

        assertEquals(1, uuidList2.size());
        assertThat(uuidList2, hasItems(c1b.getUuid()));
    }

    @Test
    public void testGetContentByVersionsMultipleVersions() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Content c1a = this.createContent("c1", "c1a", owner1);
        Content c2a = this.createContent("c2", "c2a", owner1);
        Content c3a = this.createContent("c3", "c3a", owner1);

        Content c1b = this.createContent("c1", "c1b", owner2);
        Content c2b = this.createContent("c2", "c2b", owner2);
        Content c3b = this.createContent("c3", "c3b", owner2);

        Content c1c = this.createContent("c1", "c1c", owner3);
        Content c2c = this.createContent("c2", "c2c", owner3);
        Content c3c = this.createContent("c3", "c3c", owner3);

        Map<String, List<Content>> contentMap1 = this.ownerContentCurator.getContentByVersions(
            List.of(c1a.getEntityVersion(), c1b.getEntityVersion(), c2c.getEntityVersion()));

        Map<String, List<Content>> contentMap2 = this.ownerContentCurator.getContentByVersions(
            List.of(c1a.getEntityVersion(), c2b.getEntityVersion(), c3c.getEntityVersion()));

        assertEquals(2, contentMap1.size());
        assertNotNull(contentMap1.get("c1"));
        assertEquals(2, contentMap1.get("c1").size());
        assertNotNull(contentMap1.get("c2"));
        assertEquals(1, contentMap1.get("c2").size());
        assertNull(contentMap1.get("c3"));

        assertEquals(3, contentMap2.size());
        assertNotNull(contentMap2.get("c1"));
        assertEquals(1, contentMap2.get("c1").size());
        assertNotNull(contentMap2.get("c2"));
        assertEquals(1, contentMap2.get("c2").size());
        assertNotNull(contentMap2.get("c3"));
        assertEquals(1, contentMap2.get("c3").size());

        List<String> uuidList1 = contentMap1.values()
            .stream()
            .flatMap(List::stream)
            .map(Content::getUuid)
            .collect(Collectors.toList());

        List<String> uuidList2 = contentMap2.values()
            .stream()
            .flatMap(List::stream)
            .map(Content::getUuid)
            .collect(Collectors.toList());

        assertEquals(3, uuidList1.size());
        assertThat(uuidList1, hasItems(c1a.getUuid(), c1b.getUuid(), c2c.getUuid()));

        assertEquals(3, uuidList2.size());
        assertThat(uuidList2, hasItems(c1a.getUuid(), c2b.getUuid(), c3c.getUuid()));
    }

    @Test
    public void testGetContentByVersionsNoVersionInfo() {
        Owner owner1 = this.createOwner();

        Map<String, List<Content>> contentMap1 = this.ownerContentCurator
            .getContentByVersions(null);
        assertEquals(0, contentMap1.size());

        Map<String, List<Content>> contentMap2 = this.ownerContentCurator
            .getContentByVersions(Collections.emptyList());
        assertEquals(0, contentMap2.size());
    }

    @Test
    public void testGetContentByVersionsDoesntFailWithLargeDataSets() {
        Owner owner = this.createOwner();

        int seed = 13579;
        List<Long> versions = new Random(seed)
            .longs()
            .boxed()
            .limit(100000)
            .collect(Collectors.toList());

        this.ownerContentCurator.getContentByVersions(versions);
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
        this.createProduct(providedProduct, owner);
        expectedContentMap.put(c3, true);

        Product derivedProvidedProduct = TestUtil.createProduct();
        derivedProvidedProduct.addContent(c1, true);
        this.createProduct(derivedProvidedProduct, owner);
        expectedContentMap.put(c1, true);

        Product derivedProduct = TestUtil.createProduct();
        derivedProduct.addProvidedProduct(derivedProvidedProduct);
        derivedProduct.addContent(c2, false);
        this.createProduct(derivedProduct, owner);
        expectedContentMap.put(c2, false);


        // Build product & pool
        Product product = TestUtil.createProduct()
            .setDerivedProduct(derivedProduct);

        product.addContent(c1, false);
        product.addProvidedProduct(providedProduct);

        this.createProduct(product, owner);
        Pool activePoolOne = createPool(owner, product);

        // Inactive pool
        Product productx = TestUtil.createProduct();
        this.createProduct(productx, owner);
        this.createPool(owner, productx, 10L, Util.yesterday(), Util.yesterday());

        Map<Content, Boolean> actualResult =
            this.ownerContentCurator.getActiveContentByOwner(owner.getId());

        assertEquals(actualResult.size(), 3);
        assertEquals(expectedContentMap, actualResult);

        // Make sure c1 is true in actual result, as enabled content will have precedence.
        assertEquals(actualResult.get(c1), true);
    }

    private Long getContentEntityVersion(String uuid) {
        // We need to query this directly, since the objects will automatically regenerate the
        // entity version if it's null
        return this.getEntityManager()
            .createQuery("SELECT entityVersion FROM Content WHERE uuid = :uuid", Long.class)
            .setParameter("uuid", uuid)
            .getSingleResult();
    }

    @Test
    public void testClearContentEntityVersionByEntity() {
        Owner owner = createOwner("test-owner", "owner-test");
        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);

        long version1 = content1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = content2.getEntityVersion();
        assertNotEquals(0, version2);

        this.ownerContentCurator.clearContentEntityVersion(content1);

        Long fetched1 = this.getContentEntityVersion(content1.getUuid());
        Long fetched2 = this.getContentEntityVersion(content2.getUuid());

        assertNull(fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearContentEntityVersionByEntityWithNullEntity() {
        Owner owner = createOwner("test-owner", "owner-test");
        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);

        long version1 = content1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = content2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerContentCurator.clearContentEntityVersion((Content) null);

        Long fetched1 = this.getContentEntityVersion(content1.getUuid());
        Long fetched2 = this.getContentEntityVersion(content2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearContentEntityVersionByEntityWithNullEntityUuid() {
        Owner owner = createOwner("test-owner", "owner-test");
        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);

        long version1 = content1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = content2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerContentCurator.clearContentEntityVersion(new Content());

        Long fetched1 = this.getContentEntityVersion(content1.getUuid());
        Long fetched2 = this.getContentEntityVersion(content2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearContentEntityVersionByUUID() {
        Owner owner = createOwner("test-owner", "owner-test");
        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);

        long version1 = content1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = content2.getEntityVersion();
        assertNotEquals(0, version2);

        this.ownerContentCurator.clearContentEntityVersion(content1.getUuid());

        Long fetched1 = this.getContentEntityVersion(content1.getUuid());
        Long fetched2 = this.getContentEntityVersion(content2.getUuid());

        assertNull(fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearContentEntityVersionByUUIDWithNullUUID() {
        Owner owner = createOwner("test-owner", "owner-test");
        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);

        long version1 = content1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = content2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerContentCurator.clearContentEntityVersion((String) null);

        Long fetched1 = this.getContentEntityVersion(content1.getUuid());
        Long fetched2 = this.getContentEntityVersion(content2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearContentEntityVersionByUUIDWithEmptyUUID() {
        Owner owner = createOwner("test-owner", "owner-test");
        Content content1 = this.createContent(owner);
        Content content2 = this.createContent(owner);

        long version1 = content1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = content2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerContentCurator.clearContentEntityVersion("");

        Long fetched1 = this.getContentEntityVersion(content1.getUuid());
        Long fetched2 = this.getContentEntityVersion(content2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testRebuildOwnerContentMapping() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        Content content1 = this.createContent("test_content-1", owner1, owner2);
        Content content2 = this.createContent("test_content-2", owner1, owner2);
        Content content3 = this.createContent("test_content-3", owner2);
        Content content4 = this.createContent("test_content-4", owner3);
        Content content5 = this.createContent("test_content-5", owner3);

        // Remap owner2 to contents: 2, 3, and 4
        Map<String, String> pidMap = Map.of(
            content2.getId(), content2.getUuid(),
            content3.getId(), content3.getUuid(),
            content4.getId(), content4.getUuid());

        this.ownerContentCurator.rebuildOwnerContentMapping(owner2, pidMap);

        // Verify owner1 mappings are unaffected
        assertTrue(this.isContentMappedToOwner(content1, owner1));
        assertTrue(this.isContentMappedToOwner(content2, owner1));
        assertFalse(this.isContentMappedToOwner(content3, owner1));
        assertFalse(this.isContentMappedToOwner(content4, owner1));
        assertFalse(this.isContentMappedToOwner(content5, owner1));

        // Verify owner3 mappings are unaffected
        assertFalse(this.isContentMappedToOwner(content1, owner3));
        assertFalse(this.isContentMappedToOwner(content2, owner3));
        assertFalse(this.isContentMappedToOwner(content3, owner3));
        assertTrue(this.isContentMappedToOwner(content4, owner3));
        assertTrue(this.isContentMappedToOwner(content5, owner3));

        // Verify owner2 is mapped according to the config above
        assertFalse(this.isContentMappedToOwner(content1, owner2));
        assertTrue(this.isContentMappedToOwner(content2, owner2));
        assertTrue(this.isContentMappedToOwner(content3, owner2));
        assertTrue(this.isContentMappedToOwner(content4, owner2));
        assertFalse(this.isContentMappedToOwner(content5, owner2));
    }

    @Test
    public void testRebuildOwnerContentMappingWithConflictingContentIDs() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content content1v1 = TestUtil.createContent("test_content-1", "test content 1 v1");
        Content content1v2 = TestUtil.createContent("test_content-1", "test content 1 v2");

        this.createContent(content1v1, owner1, owner2);
        this.createContent(content1v2);

        Map<String, String> pidMap = Map.of(content1v2.getId(), content1v2.getUuid());
        this.ownerContentCurator.rebuildOwnerContentMapping(owner2, pidMap);

        // owner 2 should now point to v2 of the content; owner1 should remain unaffected
        assertTrue(this.isContentMappedToOwner(content1v1, owner1));
        assertFalse(this.isContentMappedToOwner(content1v2, owner1));

        assertFalse(this.isContentMappedToOwner(content1v1, owner2));
        assertTrue(this.isContentMappedToOwner(content1v2, owner2));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testRebuildOwnerContentMappingAcceptsNullAndEmptyPIDMap(Map<String, String> pidMap) {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        Content content1 = this.createContent("test_content-1", owner1, owner2);
        Content content2 = this.createContent("test_content-2", owner1, owner2);
        Content content3 = this.createContent("test_content-3", owner2);
        Content content4 = this.createContent("test_content-4", owner3);
        Content content5 = this.createContent("test_content-5", owner3);

        // As designed, passing a null or empty map should result in the org being mapped to nothing
        this.ownerContentCurator.rebuildOwnerContentMapping(owner2, pidMap);

        // Verify owner1 mappings are unaffected
        assertTrue(this.isContentMappedToOwner(content1, owner1));
        assertTrue(this.isContentMappedToOwner(content2, owner1));
        assertFalse(this.isContentMappedToOwner(content3, owner1));
        assertFalse(this.isContentMappedToOwner(content4, owner1));
        assertFalse(this.isContentMappedToOwner(content5, owner1));

        // Verify owner3 mappings are unaffected
        assertFalse(this.isContentMappedToOwner(content1, owner3));
        assertFalse(this.isContentMappedToOwner(content2, owner3));
        assertFalse(this.isContentMappedToOwner(content3, owner3));
        assertTrue(this.isContentMappedToOwner(content4, owner3));
        assertTrue(this.isContentMappedToOwner(content5, owner3));

        // Verify owner2 isn't mapped to anything
        assertFalse(this.isContentMappedToOwner(content1, owner2));
        assertFalse(this.isContentMappedToOwner(content2, owner2));
        assertFalse(this.isContentMappedToOwner(content3, owner2));
        assertFalse(this.isContentMappedToOwner(content4, owner2));
        assertFalse(this.isContentMappedToOwner(content5, owner2));
    }

    @Test
    public void testRebuildOwnerContentMappingRequiresOwner() {
        Map<String, String> pidMap = Map.of("p1", "p1_uuid");

        assertThrows(IllegalArgumentException.class, () ->
            this.ownerContentCurator.rebuildOwnerContentMapping(null, pidMap));
    }

    @Test
    public void testRebuildOwnerContentMappingRequiresOwnerWithID() {
        Map<String, String> pidMap = Map.of("p1", "p1_uuid");

        Owner owner = new Owner();

        assertThrows(IllegalArgumentException.class, () ->
            this.ownerContentCurator.rebuildOwnerContentMapping(owner, pidMap));
    }
}
