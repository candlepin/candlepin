/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.ContentNode;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContent;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Test suite for the ContentNodeVisitor class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentNodeVisitorTest {

    private Map<String, Mutator<Content>> contentMutators;
    private Map<String, Mutator<ContentInfo>> cinfoMutators;
    private Map<String, MergeValidator<ContentInfo>> validators;

    private ContentCurator mockContentCurator;
    private OwnerContentCurator mockOwnerContentCurator;
    private NodeProcessor mockNodeProcessor;
    private NodeMapper mockNodeMapper;

    @BeforeEach
    public void init() {
        this.contentMutators = new HashMap<>();
        this.contentMutators.put("type", (c, v) -> c.setType((String) v));
        this.contentMutators.put("label", (c, v) -> c.setLabel((String) v));
        this.contentMutators.put("name", (c, v) -> c.setName((String) v));
        this.contentMutators.put("vendor", (c, v) -> c.setVendor((String) v));
        this.contentMutators.put("content_url", (c, v) -> c.setContentUrl((String) v));
        this.contentMutators.put("required_tags", (c, v) -> c.setRequiredTags((String) v));
        this.contentMutators.put("release_version", (c, v) -> c.setReleaseVersion((String) v));
        this.contentMutators.put("gpg_url", (c, v) -> c.setGpgUrl((String) v));
        this.contentMutators.put("metadata_expiration", (c, v) -> c.setMetadataExpiration((Long) v));
        this.contentMutators.put("required_product_ids", (c, v) -> c.setModifiedProductIds((Collection) v));
        this.contentMutators.put("arches", (c, v) -> c.setArches((String) v));

        this.cinfoMutators = new HashMap<>();
        this.cinfoMutators.put("type", (c, v) -> doReturn(v).when(c).getType());
        this.cinfoMutators.put("label", (c, v) -> doReturn(v).when(c).getLabel());
        this.cinfoMutators.put("name", (c, v) -> doReturn(v).when(c).getName());
        this.cinfoMutators.put("vendor", (c, v) -> doReturn(v).when(c).getVendor());
        this.cinfoMutators.put("content_url", (c, v) -> doReturn(v).when(c).getContentUrl());
        this.cinfoMutators.put("required_tags", (c, v) -> doReturn(v).when(c).getRequiredTags());
        this.cinfoMutators.put("release_version", (c, v) -> doReturn(v).when(c).getReleaseVersion());
        this.cinfoMutators.put("gpg_url", (c, v) -> doReturn(v).when(c).getGpgUrl());
        this.cinfoMutators.put("metadata_expiration", (c, v) -> doReturn(v).when(c).getMetadataExpiration());
        this.cinfoMutators.put("required_product_ids", (c, v) -> doReturn(v).when(c).getRequiredProductIds());
        this.cinfoMutators.put("arches", (c, v) -> doReturn(v).when(c).getArches());

        this.validators = new HashMap<>();
        this.validators.put("type", c -> c.getType());
        this.validators.put("label", c -> c.getLabel());
        this.validators.put("name", c -> c.getName());
        this.validators.put("vendor", c -> c.getVendor());
        this.validators.put("content_url", c -> c.getContentUrl());
        this.validators.put("required_tags", c -> c.getRequiredTags());
        this.validators.put("release_version", c -> c.getReleaseVersion());
        this.validators.put("gpg_url", c -> c.getGpgUrl());
        this.validators.put("metadata_expiration", c -> c.getMetadataExpiration());
        this.validators.put("required_product_ids", (CollectionMergeValidator<ContentInfo, String>)
            c -> c.getRequiredProductIds());
        this.validators.put("arches", c -> c.getArches());

        this.mockContentCurator = mock(ContentCurator.class);
        this.mockOwnerContentCurator = mock(OwnerContentCurator.class);
        this.mockNodeProcessor = mock(NodeProcessor.class);
        this.mockNodeMapper = mock(NodeMapper.class);

        doAnswer(returnsFirstArg())
            .when(this.mockContentCurator)
            .saveOrUpdate(Mockito.any(Content.class));

        doAnswer(returnsFirstArg())
            .when(this.mockOwnerContentCurator)
            .saveOrUpdate(Mockito.any(OwnerContent.class));
    }

    public static List<Arguments> contentDataProvider() {
        return Arrays.asList(
            Arguments.of("type", "base_type", "updated_type"),
            Arguments.of("label", "base_label", "updated_label"),
            Arguments.of("vendor", "base_vendor", "updated_vendor"),
            Arguments.of("name", "base_name", "updated_name"),
            Arguments.of("content_url", "base_content_url", "updated_content_url"),
            Arguments.of("required_tags", "base_required_tags", "updated_required_tags"),
            Arguments.of("release_version", "base_release_version", "updated_release_version"),
            Arguments.of("gpg_url", "base_gpg_url", "updated_gpg_url"),
            Arguments.of("metadata_expiration", 12345L, 67890L),
            Arguments.of("required_product_ids", Arrays.asList("1", "2", "3"), Arrays.asList("A", "B", "C")),
            Arguments.of("arches", "base_arches", "updated_arches"));
    }

    private ContentNodeVisitor buildContentNodeVisitor() {
        return new ContentNodeVisitor(this.mockContentCurator, this.mockOwnerContentCurator);
    }

    private Content createPopulatedExistingEntity(String id, String key, Object value) {
        Content entity = new Content();
        entity.setId(id);

        Mutator mutator = this.contentMutators.get(key);
        if (mutator == null) {
            throw new IllegalStateException("No mutator for key: " + key);
        }

        mutator.mutate(entity, value);

        return entity;
    }

    private ContentInfo createPopulatedImportedEntity(String id, String key, Object value) {
        ContentInfo entity = mock(ContentInfo.class);
        doReturn(id).when(entity).getId();

        // Impl note:
        // This is necessary, since the default behavior for mocked methods that return primitive
        // containers is to return a wrapped default primitive value, *NOT* null as one might
        // expect.
        doReturn(null).when(entity).getMetadataExpiration();

        Mutator mutator = this.cinfoMutators.get(key);
        if (mutator == null) {
            throw new IllegalStateException("No mutator for key: " + key);
        }

        mutator.mutate(entity, value);

        return entity;
    }

    private void validateMergedEntity(Content existing, ContentInfo imported, Content merged) {
        // Assert that we actually have a merged entity
        assertNotNull(merged);

        // Ensure the ID is set properly
        assertNotNull(merged.getId());

        if (existing != null) {
            assertNotNull(existing.getId());
            assertEquals(existing.getId(), merged.getId());
        }

        if (imported != null) {
            assertNotNull(imported.getId());
            assertEquals(imported.getId(), merged.getId());
        }

        // Check that the content is locked properly
        if (existing != null) {
            assertEquals(existing.isLocked(), merged.isLocked());
        }
        else {
            assertNotNull(imported);
            assertTrue(merged.isLocked());
        }

        // Check other attributes
        for (MergeValidator validator : this.validators.values()) {
            validator.validate(existing, imported, merged);
        }
    }


    @Test
    public void testGetEntityClass() {
        ContentNodeVisitor visitor = this.buildContentNodeVisitor();

        assertEquals(Content.class, visitor.getEntityClass());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("contentDataProvider")
    public void testProcessNodeForAbsentUpstreamEntity(String key, Object base, Object update) {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Content existing = this.createPopulatedExistingEntity(id, key, base);

        EntityNode<Content, ContentInfo> node = new ContentNode(owner, id)
            .setExistingEntity(existing);

        ContentNodeVisitor visitor = this.buildContentNodeVisitor();

        // Ensure initial node state
        assertNull(node.getNodeState());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertEquals(NodeState.UNCHANGED, node.getNodeState());
        assertNull(node.getMergedEntity());

        // This is a bit brittle, but we want to verify that we've attempted to store a new
        // Content instance and a new owner-content map
        verify(this.mockContentCurator, never()).saveOrUpdate(any(Content.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("contentDataProvider")
    public void testProcessNodeForUnmodifiedEntity(String key, Object base, Object update) {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Content existing = this.createPopulatedExistingEntity(id, key, base);
        ContentInfo imported = this.createPopulatedImportedEntity(id, key, null);

        EntityNode<Content, ContentInfo> node = new ContentNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ContentNodeVisitor visitor = this.buildContentNodeVisitor();

        // Ensure initial node state
        assertNull(node.getNodeState());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertEquals(NodeState.UNCHANGED, node.getNodeState());
        assertNull(node.getMergedEntity());

        verify(this.mockContentCurator, never()).saveOrUpdate(any(Content.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("contentDataProvider")
    public void testProcessNodeForUnchangedEntity(String key, Object base, Object update) {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Content existing = this.createPopulatedExistingEntity(id, key, base);
        ContentInfo imported = this.createPopulatedImportedEntity(id, key, base);

        EntityNode<Content, ContentInfo> node = new ContentNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ContentNodeVisitor visitor = this.buildContentNodeVisitor();

        // Ensure initial node state
        assertNull(node.getNodeState());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertEquals(NodeState.UNCHANGED, node.getNodeState());
        assertNull(node.getMergedEntity());

        verify(this.mockContentCurator, never()).saveOrUpdate(any(Content.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("contentDataProvider")
    public void testProcessNodeForNewEntity(String key, Object base, Object update) {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        ContentInfo imported = this.createPopulatedImportedEntity(id, key, update);

        EntityNode<Content, ContentInfo> node = new ContentNode(owner, id)
            .setImportedEntity(imported);

        ContentNodeVisitor visitor = this.buildContentNodeVisitor();

        // Ensure initial node state
        assertNull(node.getNodeState());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertEquals(NodeState.CREATED, node.getNodeState());
        assertNotNull(node.getMergedEntity());
        this.validateMergedEntity(null, imported, node.getMergedEntity());

        verify(this.mockContentCurator, times(1)).saveOrUpdate(any(Content.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("contentDataProvider")
    public void testProcessNodeForUpdatedEntity(String key, Object base, Object update) {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Content existing = this.createPopulatedExistingEntity(id, key, base);
        ContentInfo imported = this.createPopulatedImportedEntity(id, key, update);

        EntityNode<Content, ContentInfo> node = new ContentNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ContentNodeVisitor visitor = this.buildContentNodeVisitor();

        // Ensure initial node state
        assertNull(node.getNodeState());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertEquals(NodeState.UPDATED, node.getNodeState());
        assertNotNull(node.getMergedEntity());
        this.validateMergedEntity(existing, imported, node.getMergedEntity());

        verify(this.mockContentCurator, times(1)).saveOrUpdate(any(Content.class));
    }

}
