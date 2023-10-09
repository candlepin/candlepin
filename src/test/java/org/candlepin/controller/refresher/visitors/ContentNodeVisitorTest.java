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
package org.candlepin.controller.refresher.visitors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.candlepin.controller.refresher.nodes.ContentNode;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;



/**
 * Test suite for the ContentNodeVisitor class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentNodeVisitorTest extends DatabaseTestFixture {

    public ContentNodeVisitor buildNodeVisitor() {
        return new ContentNodeVisitor(this.contentCurator, this.ownerContentCurator);
    }

    @Test
    public void testGetEntityClassReturnsProperClass() {
        ContentNodeVisitor visitor = this.buildNodeVisitor();
        assertEquals(Content.class, visitor.getEntityClass());
    }

    @Test
    public void testProcessNodeFlagsUnchangedNodesCorrectly() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content", owner);
        ContentInfo importedEntity = (ContentInfo) existingEntity.clone();

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();

        visitor.processNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    public static Stream<Arguments> simpleContentDataProvider() {
        return Stream.of(
            Arguments.of("type", "updated_type"),
            Arguments.of("label", "updated_label"),
            Arguments.of("vendor", "updated_vendor"),
            Arguments.of("name", "updated_name"),
            Arguments.of("content_url", "updated_content_url"),
            Arguments.of("required_tags", "updated_required_tags"),
            Arguments.of("release_version", "updated_release_version"),
            Arguments.of("gpg_url", "updated_gpg_url"),
            Arguments.of("metadata_expiration", 67890L),
            Arguments.of("required_product_ids", Arrays.asList("A", "B", "C")),
            Arguments.of("arches", "updated_arches"));
    }

    private ContentInfo buildContentInfoMock(String id, String field, Object value) {
        ContentInfo entity = mock(ContentInfo.class);
        doReturn(id).when(entity).getId();

        // Impl note:
        // This is necessary, since the default behavior for mocked methods that return primitive
        // containers is to return a wrapped default primitive value, *NOT* null as one might
        // expect.
        doReturn(null).when(entity).getMetadataExpiration();

        Map<String, Mutator<ContentInfo>> mutators = Map.ofEntries(
            Map.entry("type", (c, v) -> doReturn(v).when(c).getType()),
            Map.entry("label", (c, v) -> doReturn(v).when(c).getLabel()),
            Map.entry("name", (c, v) -> doReturn(v).when(c).getName()),
            Map.entry("vendor", (c, v) -> doReturn(v).when(c).getVendor()),
            Map.entry("content_url", (c, v) -> doReturn(v).when(c).getContentUrl()),
            Map.entry("required_tags", (c, v) -> doReturn(v).when(c).getRequiredTags()),
            Map.entry("release_version", (c, v) -> doReturn(v).when(c).getReleaseVersion()),
            Map.entry("gpg_url", (c, v) -> doReturn(v).when(c).getGpgUrl()),
            Map.entry("metadata_expiration", (c, v) -> doReturn(v).when(c).getMetadataExpiration()),
            Map.entry("required_product_ids", (c, v) -> doReturn(v).when(c).getRequiredProductIds()),
            Map.entry("arches", (c, v) -> doReturn(v).when(c).getArches()));

        if (!mutators.containsKey(field)) {
            throw new IllegalStateException("no mutator for key: " + field);
        }

        mutators.get(field)
            .mutate(entity, value);

        return entity;
    }

    private void updateContentField(Content entity, String field, Object value) {
        Map<String, Mutator<Content>> mutators = Map.ofEntries(
            Map.entry("type", (c, v) -> c.setType((String) v)),
            Map.entry("label", (c, v) -> c.setLabel((String) v)),
            Map.entry("name", (c, v) -> c.setName((String) v)),
            Map.entry("vendor", (c, v) -> c.setVendor((String) v)),
            Map.entry("content_url", (c, v) -> c.setContentUrl((String) v)),
            Map.entry("required_tags", (c, v) -> c.setRequiredTags((String) v)),
            Map.entry("release_version", (c, v) -> c.setReleaseVersion((String) v)),
            Map.entry("gpg_url", (c, v) -> c.setGpgUrl((String) v)),
            Map.entry("metadata_expiration", (c, v) -> c.setMetadataExpiration((Long) v)),
            Map.entry("required_product_ids", (c, v) -> c.setModifiedProductIds((Collection) v)),
            Map.entry("arches", (c, v) -> c.setArches((String) v)));

        if (!mutators.containsKey(field)) {
            throw new IllegalStateException("no mutator for key: " + field);
        }

        mutators.get(field)
            .mutate(entity, value);
    }

    private void validateMergedField(Content mergedEntity, String field, Object expected) {
        Map<String, Accessor<ContentInfo>> accessors = Map.ofEntries(
            Map.entry("type", ContentInfo::getType),
            Map.entry("label", ContentInfo::getLabel),
            Map.entry("name", ContentInfo::getName),
            Map.entry("vendor", ContentInfo::getVendor),
            Map.entry("content_url", ContentInfo::getContentUrl),
            Map.entry("required_tags", ContentInfo::getRequiredTags),
            Map.entry("release_version", ContentInfo::getReleaseVersion),
            Map.entry("gpg_url", ContentInfo::getGpgUrl),
            Map.entry("metadata_expiration", ContentInfo::getMetadataExpiration),
            Map.entry("required_product_ids", ContentInfo::getRequiredProductIds),
            Map.entry("arches", ContentInfo::getArches));

        if (!accessors.containsKey(field)) {
            throw new IllegalStateException("no accessor for key: " + field);
        }

        Object actual = accessors.get(field)
            .access(mergedEntity);

        if (actual instanceof Collection) {
            Collection<Object> expCollection = (Collection<Object>) expected;
            Collection<Object> actCollection = (Collection<Object>) actual;

            assertNotNull(expCollection);
            assertEquals(expCollection.size(), actCollection.size());

            for (Object item : expCollection) {
                assertThat(actCollection, hasItem(item));
            }
        }
        else if (actual instanceof Map) {
            Map<Object, Object> expMap = (Map<Object, Object>) expected;
            Map<Object, Object> actMap = (Map<Object, Object>) actual;

            assertNotNull(expMap);
            assertEquals(expMap.size(), actMap.size());

            for (Map.Entry<Object, Object> entry : expMap.entrySet()) {
                assertThat(actMap, hasEntry(entry.getKey(), entry.getValue()));
            }
        }
        else {
            assertEquals(expected, actual);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("simpleContentDataProvider")
    public void testProcessNodeFlagsUpdatedNodeCorrectly(String field, Object value) {
        String id = "test_content-1";

        Owner owner = this.createOwner();
        Content existingEntity = new Content()
            .setId(id);

        ContentInfo importedEntity = this.buildContentInfoMock(id, field, value);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());
        assertNotNull(pnode.getMergedEntity());
        this.validateMergedField(pnode.getMergedEntity(), field, value);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("simpleContentDataProvider")
    public void testProcessNodeDoesNotFlagUnchangedNodeForUpdate(String field, Object value) {
        String id = "test_content-1";

        Owner owner = this.createOwner();
        Content existingEntity = new Content()
            .setId(id);

        this.updateContentField(existingEntity, field, value);

        ContentInfo importedEntity = this.buildContentInfoMock(id, field, value);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    @Test
    public void testProcessNodeFlagsCreatedNodeCorrectly() {
        Owner owner = this.createOwner();
        ContentInfo importedEntity = this.createContent("test_content-1", "Test Content", owner);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, importedEntity.getId())
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());
        assertNotNull(pnode.getMergedEntity());
        assertEquals(importedEntity.getName(), pnode.getMergedEntity().getName());
    }

    @Test
    public void testPruneNodeMarksUnusedRootForDeletion() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content", owner)
            .setLocked(true);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.pruneNode(pnode);

        assertEquals(NodeState.DELETED, pnode.getNodeState());
    }

    @Test
    public void testPruneNodeOmitsActiveRoot() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content", owner)
            .setLocked(true);
        ContentInfo importedEntity = (ContentInfo) existingEntity.clone();

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.pruneNode(pnode);

        assertNull(pnode.getNodeState());
    }

    @Test
    public void testPruneNodeNeverDeletesCustomContents() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content", owner)
            .setLocked(false);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.pruneNode(pnode);

        assertNull(pnode.getNodeState());
    }

    @Test
    public void testPruneNodeMarksLeafWithDeletedParentsForDeletion() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content", owner)
            .setLocked(true);
        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        Content existingParent = this.createContent("test_content-2", "Test Content", owner);
        EntityNode<Content, ContentInfo> parentNode = new ContentNode(owner, existingParent.getId())
            .setExistingEntity(existingParent)
            .setNodeState(NodeState.DELETED);

        pnode.addParentNode(parentNode);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.pruneNode(pnode);

        assertEquals(NodeState.DELETED, pnode.getNodeState());
    }

    @Test
    public void testPruneNodeOmitsLeafWithUndeletedParents() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content", owner)
            .setLocked(true);
        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        Content existingParent = this.createContent("test_content-2", "Test Content", owner);
        EntityNode<Content, ContentInfo> parentNode = new ContentNode(owner, existingParent.getId())
            .setExistingEntity(existingParent)
            .setNodeState(NodeState.UPDATED);

        pnode.addParentNode(parentNode);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.pruneNode(pnode);

        assertNull(pnode.getNodeState());
    }

    @Test
    public void testPruneNodeOmitsLeafWithMixedParents() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content", owner)
            .setLocked(true);
        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        Content existingParent1 = this.createContent("test_content-2", "Test Content", owner);
        EntityNode<Content, ContentInfo> parentNode1 = new ContentNode(owner, existingParent1.getId())
            .setExistingEntity(existingParent1)
            .setNodeState(NodeState.UPDATED);

        Content existingParent2 = this.createContent("test_content-3", "Test Content", owner);
        EntityNode<Content, ContentInfo> parentNode2 = new ContentNode(owner, existingParent2.getId())
            .setExistingEntity(existingParent2)
            .setNodeState(NodeState.DELETED);

        pnode.addParentNode(parentNode1);
        pnode.addParentNode(parentNode2);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.pruneNode(pnode);

        assertNull(pnode.getNodeState());
    }

    @Test
    public void testApplyChangesPerformsVersionResolution() {
        String id = "test_content-1";
        String name = "test content 1";

        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content existing1 = this.createContent(id, "old name", owner1);
        Content existing2 = this.createContent(id, name, owner2);

        Content importedEntity = new Content()
            .setId(id)
            .setName(name);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner1, existing1.getId())
            .setExistingEntity(existing1)
            .setImportedEntity(importedEntity);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        visitor.applyChanges(pnode);

        Content mergedEntity = pnode.getMergedEntity();
        assertNotNull(mergedEntity);
        assertEquals(existing2.getUuid(), mergedEntity.getUuid());
    }

    @Test
    public void testCompleteDoesNotActTwice() {
        Owner owner = this.createOwner();

        Content content = new Content()
            .setId("test_content")
            .setName("test content")
            .setLabel("test content")
            .setVendor("test vendor")
            .setType("type");

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, content.getId())
            .setImportedEntity(content);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.CREATED, pnode.getNodeState());
        assertNotNull(pnode.getMergedEntity());
        assertNotNull(pnode.getMergedEntity().getUuid());

        // If this executes twice, a cached creation op would try to run twice, which would fail
        // with a persistence exception of some kind
        visitor.complete();
    }

    @Test
    public void testFullCyclePersistsNewEntity() {
        Owner owner = this.createOwner();

        Content content = new Content()
            .setId("test_content")
            .setName("test content")
            .setLabel("test content")
            .setVendor("test vendor")
            .setType("type");

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, content.getId())
            .setImportedEntity(content);

        assertNull(this.ownerContentCurator.getContentById(owner, content.getId()));

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.CREATED, pnode.getNodeState());

        Content merged = pnode.getMergedEntity();
        assertNotNull(merged);
        assertNotNull(merged.getUuid());

        this.contentCurator.flush();
        this.contentCurator.clear();

        Content created = this.ownerContentCurator.getContentById(owner, content.getId());
        assertNotNull(created);
        assertEquals(merged.getUuid(), created.getUuid());
    }

    @Test
    public void testFullCyclePersistsUpdatedEntity() {
        Owner owner = this.createOwner();

        Content existing = this.createContent("test_content", "content name", owner);
        existing.setLocked(true);

        Content content = new Content()
            .setId(existing.getId())
            .setName("updated content");

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, content.getId())
            .setExistingEntity(existing)
            .setImportedEntity(content);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        Content merged = pnode.getMergedEntity();
        assertNotNull(merged);
        assertNotNull(merged.getUuid());
        assertNotEquals(existing.getUuid(), merged.getUuid());
        assertNotEquals(existing.getName(), merged.getName());

        this.contentCurator.flush();
        this.contentCurator.clear();

        Content updated = this.ownerContentCurator.getContentById(owner, content.getId());
        assertNotNull(updated);
        assertEquals(merged.getUuid(), updated.getUuid());
        assertEquals(content.getName(), updated.getName());
    }

    @Test
    public void testFullCycleDeletesUnusedEntity() {
        Owner owner = this.createOwner();

        Content existing = this.createContent("test_content", "content name", owner);
        existing.setLocked(true);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existing.getId())
            .setExistingEntity(existing);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.DELETED, pnode.getNodeState());
        assertNull(pnode.getMergedEntity());

        this.contentCurator.flush();
        this.contentCurator.clear();

        Content deleted = this.ownerContentCurator.getContentById(owner, existing.getId());
        assertNull(deleted);
    }

    /**
     * This test verifies that a content version collision on a given content ID can be resolved by
     * clearing the entity version of the existing content, operating under the assumption that the
     * current content is broken and the new one is the "correct" entity for the version.
     */
    @Test
    public void testEntityVersionCollisionResolution() {
        Owner owner2 = this.createOwner();
        Content collider = this.createContent("test_content", "test content", owner2);

        this.ownerContentCurator.flush();
        this.ownerContentCurator.clear();

        Owner owner1 = this.createOwner();

        Content imported = collider.clone()
            .setUuid(null)
            .setLabel("imported label");

        long entityVersion = imported.getEntityVersion();

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner1, collider.getId())
            .setImportedEntity(imported);

        // Forcefully set the entity version
        int count = this.getEntityManager()
            .createQuery("UPDATE Content SET entityVersion = :version WHERE uuid = :uuid")
            .setParameter("version", entityVersion)
            .setParameter("uuid", collider.getUuid())
            .executeUpdate();

        assertEquals(1, count);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.CREATED, pnode.getNodeState());
        assertNotNull(pnode.getMergedEntity());
        assertNotNull(pnode.getMergedEntity().getUuid());
        assertEquals(entityVersion, pnode.getMergedEntity().getEntityVersion());

        // Query the entity version directly so we avoid the automatic regeneration when it's null
        Long existingEntityVersion = this.getEntityManager()
            .createQuery("SELECT entityVersion FROM Content WHERE uuid = :uuid", Long.class)
            .setParameter("uuid", collider.getUuid())
            .getSingleResult();

        assertNull(existingEntityVersion);
    }

}
