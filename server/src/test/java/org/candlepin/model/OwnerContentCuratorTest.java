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

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



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
    public void testGetContentByOwner() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();
        this.createOwnerContentMapping(owner, content1);
        this.createOwnerContentMapping(owner, content2);

        Collection<Content> contentA = this.ownerContentCurator.getContentByOwner(owner).list();
        Collection<Content> contentB = this.ownerContentCurator.getContentByOwner(owner.getId()).list();

        assertTrue(contentA.contains(content1));
        assertTrue(contentA.contains(content2));
        assertFalse(contentA.contains(content3));
        assertEquals(contentA, contentB);
    }

    @Test
    public void testGetContentByOwnerWithUnmappedContent() {
        Owner owner = this.createOwner();
        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Collection<Content> contentA = this.ownerContentCurator.getContentByOwner(owner).list();
        Collection<Content> contentB = this.ownerContentCurator.getContentByOwner(owner.getId()).list();

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
        Collection<Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids).list();
        Collection<Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids).list();

        assertEquals(2, contentA.size());
        assertTrue(contentA.contains(content1));
        assertTrue(contentA.contains(content2));
        assertFalse(contentA.contains(content3));
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
        Collection<Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids).list();
        Collection<Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids).list();

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
        Collection<Content> contentA = this.ownerContentCurator.getContentByIds(owner, ids).list();
        Collection<Content> contentB = this.ownerContentCurator.getContentByIds(owner.getId(), ids).list();

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

    @Test(expected = IllegalStateException.class)
    public void testMapContentToOwnerUnmappedOwner() {
        Owner owner = TestUtil.createOwner();
        Content content = this.createContent();

        this.ownerContentCurator.mapContentToOwner(content, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testMapContentToOwnerUnmappedContent() {
        Owner owner = this.createOwner();
        Content content = TestUtil.createContent();

        this.ownerContentCurator.mapContentToOwner(content, owner);
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
        Content original = this.createContent("c1", "c1", owner1);
        Content unmodified = this.createContent("c1", "c1", owner2);
        Content updated = this.createContent("c1", "c1");

        assertTrue(original.getUuid() != updated.getUuid());
        assertTrue(original.getUuid() != unmodified.getUuid());
        assertTrue(updated.getUuid() != unmodified.getUuid());

        Environment environment1 = this.createEnvironment(
            owner1, "test_env-1", "test_env-1", null, null, Arrays.asList(original)
        );

        Environment environment2 = this.createEnvironment(
            owner2, "test_env-2", "test_env-2", null, null, Arrays.asList(unmodified)
        );

        assertTrue(this.isContentMappedToOwner(original, owner1));
        assertFalse(this.isContentMappedToOwner(updated, owner1));
        assertTrue(this.isContentMappedToOwner(unmodified, owner2));

        Map<String, String> uuidMap = new HashMap<>();
        uuidMap.put(original.getUuid(), updated.getUuid());

        this.ownerContentCurator.updateOwnerContentReferences(owner1, uuidMap);

        assertFalse(this.isContentMappedToOwner(original, owner1));
        assertTrue(this.isContentMappedToOwner(updated, owner1));
        assertTrue(this.isContentMappedToOwner(unmodified, owner2));

        this.environmentCurator.evict(environment1);
        this.environmentCurator.evict(environment2);
        environment1 = this.environmentCurator.get(environment1.getId());
        environment2 = this.environmentCurator.get(environment2.getId());

        assertEquals(1, environment1.getEnvironmentContent().size());
        assertEquals(1, environment2.getEnvironmentContent().size());
        assertEquals(updated.getUuid(), environment1.getEnvironmentContent().iterator().next().getContent()
            .getUuid());
        assertEquals(unmodified.getUuid(), environment2.getEnvironmentContent().iterator().next().getContent()
            .getUuid());
    }

    @Test
    public void testRemoveOwnerContentReferences() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Content original = this.createContent("c1", "c1", owner1);
        Content unmodified = this.createContent("c1", "c1", owner2);

        assertTrue(original.getUuid() != unmodified.getUuid());

        Environment environment1 = this.createEnvironment(
            owner1, "test_env-1", "test_env-1", null, null, Arrays.asList(original)
        );

        Environment environment2 = this.createEnvironment(
            owner2, "test_env-2", "test_env-2", null, null, Arrays.asList(unmodified)
        );

        assertTrue(this.isContentMappedToOwner(original, owner1));
        assertTrue(this.isContentMappedToOwner(unmodified, owner2));

        this.ownerContentCurator.removeOwnerContentReferences(owner1, Arrays.asList(original.getUuid()));

        assertFalse(this.isContentMappedToOwner(original, owner1));
        assertTrue(this.isContentMappedToOwner(unmodified, owner2));

        this.environmentCurator.evict(environment1);
        this.environmentCurator.evict(environment2);
        environment1 = this.environmentCurator.get(environment1.getId());
        environment2 = this.environmentCurator.get(environment2.getId());

        assertEquals(0, environment1.getEnvironmentContent().size());
        assertEquals(1, environment2.getEnvironmentContent().size());
        assertEquals(unmodified.getUuid(), environment2.getEnvironmentContent().iterator().next().getContent()
            .getUuid());
    }

    @Test
    public void testGetContentByVersions() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Content content1 = this.createContent("p1", "p1", owner1);
        Content content2 = this.createContent("p1", "p1", owner2);
        Content content3 = this.createContent("p1", "p1", owner3);
        Content content4 = this.createContent("p2", "p2", owner2);

        List<Content> contentList1 = this.ownerContentCurator.getContentByVersions(owner1,
            Collections.<String, Integer>singletonMap(content1.getId(), content1.getEntityVersion())).list();

        List<Content> contentList2 = this.ownerContentCurator.getContentByVersions(owner2,
            Collections.<String, Integer>singletonMap(content2.getId(), content2.getEntityVersion())).list();

        // contentList1 should contain only content2 and content3
        // contentList2 should contain only content1 and content3

        assertEquals(2, contentList1.size());
        assertEquals(2, contentList2.size());

        List<String> uuidList1 = new LinkedList<>();
        for (Content content : contentList1) {
            uuidList1.add(content.getUuid());
        }

        List<String> uuidList2 = new LinkedList<>();
        for (Content content : contentList2) {
            uuidList2.add(content.getUuid());
        }

        assertEquals(Arrays.asList(content2.getUuid(), content3.getUuid()), uuidList1);
        assertEquals(Arrays.asList(content1.getUuid(), content3.getUuid()), uuidList2);
    }

    @Test
    public void testGetContentByVersionsNoOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Content content1 = this.createContent("p1", "p1", owner1);
        Content content2 = this.createContent("p1", "p1", owner2);
        Content content3 = this.createContent("p1", "p1", owner3);
        Content content4 = this.createContent("p2", "p2", owner2);

        List<Content> contentList1 = this.ownerContentCurator.getContentByVersions(null,
            Collections.<String, Integer>singletonMap(content1.getId(), content1.getEntityVersion())).list();

        List<Content> contentList2 = this.ownerContentCurator.getContentByVersions(null,
            Collections.<String, Integer>singletonMap(content2.getId(), content2.getEntityVersion())).list();

        // Both lists should contain both content1, 2 and 3

        assertEquals(3, contentList1.size());
        assertEquals(3, contentList2.size());

        List<String> uuidList1 = new LinkedList<>();
        for (Content content : contentList1) {
            uuidList1.add(content.getUuid());
        }

        List<String> uuidList2 = new LinkedList<>();
        for (Content content : contentList2) {
            uuidList2.add(content.getUuid());
        }

        // We're counting on .equals not caring about order here
        assertEquals(Arrays.asList(content1.getUuid(), content2.getUuid(), content3.getUuid()), uuidList1);
        assertEquals(Arrays.asList(content1.getUuid(), content2.getUuid(), content3.getUuid()), uuidList2);
    }

    @Test
    public void testGetContentByVersionsMultipleVersions() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Content p1 = this.createContent("p1", "p1", owner1);
        Content p2 = this.createContent("p1", "p1", owner2);
        Content p3 = this.createContent("p1", "p1", owner3);
        Content p4 = this.createContent("p2", "p2", owner1);
        Content p5 = this.createContent("p2", "p2", owner2);
        Content p6 = this.createContent("p2", "p2", owner3);
        Content p7 = this.createContent("p3", "p3", owner1);
        Content p8 = this.createContent("p3", "p3", owner2);
        Content p9 = this.createContent("p3", "p3", owner3);

        Map<String, Integer> versions = new HashMap<>();
        versions.put(p1.getId(), p1.getEntityVersion());
        versions.put(p4.getId(), p4.getEntityVersion());
        versions.put("bad_id", p7.getEntityVersion());

        List<Content> contentList1 = this.ownerContentCurator.getContentByVersions(owner1, versions).list();
        List<Content> contentList2 = this.ownerContentCurator.getContentByVersions(owner2, versions).list();
        List<Content> contentList3 = this.ownerContentCurator.getContentByVersions(null, versions).list();

        // List 1 should contain content 2, 3, 5 and 6
        // List 2 should contain content 1, 3, 4 and 6
        // List 3 should contain content 1 through 6

        assertEquals(4, contentList1.size());
        assertEquals(4, contentList2.size());
        assertEquals(6, contentList3.size());

        List<String> uuidList1 = new LinkedList<>();
        for (Content content : contentList1) {
            uuidList1.add(content.getUuid());
        }

        List<String> uuidList2 = new LinkedList<>();
        for (Content content : contentList2) {
            uuidList2.add(content.getUuid());
        }

        List<String> uuidList3 = new LinkedList<>();
        for (Content content : contentList3) {
            uuidList3.add(content.getUuid());
        }

        // We're counting on .equals not caring about order here
        assertEquals(Arrays.asList(p2.getUuid(), p3.getUuid(), p5.getUuid(), p6.getUuid()), uuidList1);
        assertEquals(Arrays.asList(p1.getUuid(), p3.getUuid(), p4.getUuid(), p6.getUuid()), uuidList2);
        assertEquals(
            Arrays.asList(p1.getUuid(), p2.getUuid(), p3.getUuid(), p4.getUuid(), p5.getUuid(), p6.getUuid()),
            uuidList3
        );
    }

    @Test
    public void testGetContentByVersionsNoVersionInfo() {
        Owner owner1 = this.createOwner();

        List<Content> contentList1 = this.ownerContentCurator.getContentByVersions(owner1, null).list();
        assertEquals(0, contentList1.size());

        List<Content> contentList2 = this.ownerContentCurator.getContentByVersions(owner1,
            Collections.<String, Integer>emptyMap()).list();
        assertEquals(0, contentList2.size());

        List<Content> contentList3 = this.ownerContentCurator.getContentByVersions(null, null).list();
        assertEquals(0, contentList3.size());

        List<Content> contentList4 = this.ownerContentCurator.getContentByVersions(null,
            Collections.<String, Integer>emptyMap()).list();
        assertEquals(0, contentList4.size());
    }
}
