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
package org.candlepin.controller.refresher.builders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.controller.refresher.mappers.ContentMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * Test suite for the ContentNodeBuilder class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentNodeBuilderTest {

    private NodeFactory mockNodeFactory;
    private ContentMapper contentMapper;

    @BeforeEach
    public void init() throws Exception {
        this.mockNodeFactory = mock(NodeFactory.class);
        this.contentMapper = new ContentMapper();
    }

    private ContentNodeBuilder buildNodeBuilder() {
        return new ContentNodeBuilder();
    }

    private Set<Content> createCandidateEntitiesSet(String id) {
        Set<Content> candidates = new HashSet<>();

        for (int i = 0; i < 3; ++i) {
            Content candidate = TestUtil.createContent(id, TestUtil.randomString());
            candidates.add(candidate);
        }

        return candidates;
    }

    private void addDummyCandidateEntitiesToMap(Map<String, Set<Content>> candidateEntitiesMap) {
        for (int i = 0; i < 5; ++i) {
            String id = TestUtil.randomString();
            candidateEntitiesMap.put(id, this.createCandidateEntitiesSet(id));
        }
    }


    @Test
    public void testGetEntityClass() {
        ContentNodeBuilder builder = this.buildNodeBuilder();

        Class output = builder.getEntityClass();

        assertNotNull(output);
        assertEquals(Content.class, output);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}")
    @ValueSource(strings = { "true", "false" })
    public void testBuildNodeForCreation(boolean includeCandidates) {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        Map<String, Set<Content>> candidateEntitiesMap = new HashMap<>();
        this.addDummyCandidateEntitiesToMap(candidateEntitiesMap);

        Set<Content> candidateEntities = null;

        if (includeCandidates) {
            candidateEntities = this.createCandidateEntitiesSet(id);
            candidateEntitiesMap.put(id, candidateEntities);
        }

        this.contentMapper.addImportedEntity(imported);
        this.contentMapper.setCandidateEntitiesMap(candidateEntitiesMap);

        ContentNodeBuilder builder = this.buildNodeBuilder();
        EntityNode<Content, ContentInfo> output = builder.buildNode(this.mockNodeFactory, this.contentMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // A node for creation should have a null existing entity, and a non-null imported entity,
        // and no updated entity should be created yet
        assertNull(output.getExistingEntity());
        assertEquals(imported, output.getImportedEntity());
        assertNull(output.getMergedEntity());

        if (includeCandidates) {
            // We should have a set of candidate entities in this test
            assertNotNull(output.getCandidateEntities());
            assertEquals(candidateEntities, output.getCandidateEntities());
        }
        else {
            // In this test, we did not provide candidate entities, so the collection should be null
            assertNull(output.getCandidateEntities());
        }

        // Content does not have any children, and we do not have parents in this context
        assertNotNull(output.getParentNodes());
        assertThat(output.getParentNodes(), empty());

        assertNotNull(output.getChildrenNodes());
        assertThat(output.getChildrenNodes(), empty());

        // Its pseudo-state getters should match our expectations
        assertTrue(output.isRootNode());
        assertTrue(output.isLeafNode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}")
    @ValueSource(strings = { "true", "false" })
    public void testBuildNodeForUpdate(boolean includeCandidates) {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        Map<String, Set<Content>> candidateEntitiesMap = new HashMap<>();
        this.addDummyCandidateEntitiesToMap(candidateEntitiesMap);

        Set<Content> candidateEntities = null;

        if (includeCandidates) {
            candidateEntities = this.createCandidateEntitiesSet(id);
            candidateEntitiesMap.put(id, candidateEntities);
        }

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);
        this.contentMapper.setCandidateEntitiesMap(candidateEntitiesMap);

        ContentNodeBuilder builder = this.buildNodeBuilder();
        EntityNode<Content, ContentInfo> output = builder.buildNode(this.mockNodeFactory, this.contentMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // A node for creation should have a null existing entity, and a non-null imported entity,
        // and no updated entity should be created yet
        assertEquals(existing, output.getExistingEntity());
        assertEquals(imported, output.getImportedEntity());
        assertNull(output.getMergedEntity());

        if (includeCandidates) {
            // We should have a set of candidate entities in this test
            assertNotNull(output.getCandidateEntities());
            assertEquals(candidateEntities, output.getCandidateEntities());
        }
        else {
            // In this test, we did not provide candidate entities, so the collection should be null
            assertNull(output.getCandidateEntities());
        }

        // Content does not have any children, and we do not have parents in this context
        assertNotNull(output.getParentNodes());
        assertThat(output.getParentNodes(), empty());

        assertNotNull(output.getChildrenNodes());
        assertThat(output.getChildrenNodes(), empty());

        // Its pseudo-state getters should match our expectations
        assertTrue(output.isRootNode());
        assertTrue(output.isLeafNode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}")
    @ValueSource(strings = { "true", "false" })
    public void testBuildNodeWithNoImport(boolean includeCandidates) {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        Map<String, Set<Content>> candidateEntitiesMap = new HashMap<>();
        this.addDummyCandidateEntitiesToMap(candidateEntitiesMap);

        Set<Content> candidateEntities = null;

        if (includeCandidates) {
            candidateEntities = this.createCandidateEntitiesSet(id);
            candidateEntitiesMap.put(id, candidateEntities);
        }

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.setCandidateEntitiesMap(candidateEntitiesMap);

        ContentNodeBuilder builder = this.buildNodeBuilder();
        EntityNode<Content, ContentInfo> output = builder.buildNode(this.mockNodeFactory, this.contentMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // A node for creation should have a null existing entity, and a non-null imported entity,
        // and no updated entity should be created yet
        assertEquals(existing, output.getExistingEntity());
        assertNull(output.getImportedEntity());
        assertNull(output.getMergedEntity());

        if (includeCandidates) {
            // We should have a set of candidate entities in this test
            assertNotNull(output.getCandidateEntities());
            assertEquals(candidateEntities, output.getCandidateEntities());
        }
        else {
            // In this test, we did not provide candidate entities, so the collection should be null
            assertNull(output.getCandidateEntities());
        }

        // Content does not have any children, and we do not have parents in this context
        assertNotNull(output.getParentNodes());
        assertThat(output.getParentNodes(), empty());

        assertNotNull(output.getChildrenNodes());
        assertThat(output.getChildrenNodes(), empty());

        // Its pseudo-state getters should match our expectations
        assertTrue(output.isRootNode());
        assertTrue(output.isLeafNode());
    }

    @Test
    public void testCannotBuildNodeForUnknownId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        Map<String, Set<Content>> candidateEntitiesMap = new HashMap<>();
        this.addDummyCandidateEntitiesToMap(candidateEntitiesMap);

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);
        this.contentMapper.setCandidateEntitiesMap(candidateEntitiesMap);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class, () ->
            builder.buildNode(this.mockNodeFactory, this.contentMapper, owner, "invalid_id"));
    }

    @Test
    public void testCannotBuildNodeWithEmptyId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        Map<String, Set<Content>> candidateEntitiesMap = new HashMap<>();
        this.addDummyCandidateEntitiesToMap(candidateEntitiesMap);

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);
        this.contentMapper.setCandidateEntitiesMap(candidateEntitiesMap);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class, () ->
            builder.buildNode(this.mockNodeFactory, this.contentMapper, owner, ""));
    }

    @Test
    public void testCannotBuildNodeWithNullId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        Map<String, Set<Content>> candidateEntitiesMap = new HashMap<>();
        this.addDummyCandidateEntitiesToMap(candidateEntitiesMap);

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);
        this.contentMapper.setCandidateEntitiesMap(candidateEntitiesMap);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class, () ->
            builder.buildNode(this.mockNodeFactory, this.contentMapper, owner, null));
    }

    @Test
    public void testCannotBuildNodeWithNullOwner() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        Map<String, Set<Content>> candidateEntitiesMap = new HashMap<>();
        this.addDummyCandidateEntitiesToMap(candidateEntitiesMap);

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);
        this.contentMapper.setCandidateEntitiesMap(candidateEntitiesMap);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalArgumentException.class, () ->
            builder.buildNode(this.mockNodeFactory, this.contentMapper, null, id));
    }

}
