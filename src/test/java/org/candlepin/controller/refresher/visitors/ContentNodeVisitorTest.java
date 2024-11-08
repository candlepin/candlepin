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
        return new ContentNodeVisitor(this.contentCurator);
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

    private void validateContentField(Content entity, String field, Object expected) {
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
            .access(entity);

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

    @Test
    public void testGetEntityClassReturnsProperClass() {
        ContentNodeVisitor visitor = this.buildNodeVisitor();
        assertEquals(Content.class, visitor.getEntityClass());
    }

    @Test
    public void testProcessNodeFlagsCreatedNodeCorrectly() {
        Owner owner = this.createOwner();
        ContentInfo importedEntity = new Content("test_content-1")
            .setName("Test Content");

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, importedEntity.getId())
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());
    }

    @Test
    public void testProcessNodeFlagsUnchangedNodesCorrectly() {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "Test Content");
        ContentInfo importedEntity = (ContentInfo) existingEntity.clone();

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("simpleContentDataProvider")
    public void testProcessNodeDoesNotFlagUnchangedNodeForUpdate(String field, Object value) {
        String id = "test_content-1";

        Owner owner = this.createOwner();
        Content existingEntity = new Content(id);

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
    public void testApplyChangesCreatesNewInstances() {
        Owner owner = this.createOwner();
        ContentInfo importedEntity = new Content("test_content")
            .setName("test content")
            .setLabel("test content")
            .setVendor("test vendor")
            .setType("type");

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, importedEntity.getId())
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());
        assertNotNull(pnode.getExistingEntity());
        assertEquals(importedEntity.getName(), pnode.getExistingEntity().getName());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("simpleContentDataProvider")
    public void testApplyChangesUpdatedNodeCorrectly(String field, Object value) {
        Owner owner = this.createOwner();
        Content existingEntity = this.createContent("test_content-1", "test content");

        ContentInfo importedEntity = this.buildContentInfoMock(existingEntity.getId(), field, value);

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());
        this.validateContentField(pnode.getExistingEntity(), field, value);
    }

    @Test
    public void testFullCyclePersistsNewEntity() {
        Owner owner = this.createOwner();

        Content content = new Content("test_content")
            .setName("test content")
            .setLabel("test content")
            .setVendor("test vendor")
            .setType("type");

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, content.getId())
            .setImportedEntity(content);

        assertNull(this.contentCurator.getContentById(null, content.getId()));

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());

        Content updatedEntity = pnode.getExistingEntity();
        assertNotNull(updatedEntity);
        assertNotNull(updatedEntity.getUuid());

        this.contentCurator.flush();
        this.contentCurator.clear();

        Content fetchedEntity = this.contentCurator.getContentById(null, content.getId());
        assertNotNull(fetchedEntity);
    }

    @Test
    public void testFullCyclePersistsUpdatedEntity() {
        Owner owner = this.createOwner();

        Content existing = this.createContent("test_content", "content name");

        Content imported = new Content(existing.getId())
            .setName("updated content");

        EntityNode<Content, ContentInfo> pnode = new ContentNode(owner, imported.getId())
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ContentNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        Content updatedEntity = pnode.getExistingEntity();
        assertNotNull(updatedEntity);
        assertNotNull(updatedEntity.getUuid());
        assertEquals(imported.getName(), updatedEntity.getName());

        this.contentCurator.flush();
        this.contentCurator.clear();

        Content fetchedEntity = this.contentCurator.getContentById(null, existing.getId());
        assertNotNull(fetchedEntity);
        assertEquals(imported.getName(), fetchedEntity.getName());
    }

}
