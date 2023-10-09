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
package org.candlepin.controller.refresher.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.controller.refresher.mappers.ContentMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;



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

    @Test
    public void testGetEntityClass() {
        ContentNodeBuilder builder = this.buildNodeBuilder();

        Class output = builder.getEntityClass();

        assertNotNull(output);
        assertEquals(Content.class, output);
    }

    @Test
    public void testBuildNodeForCreation() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        this.contentMapper.addImportedEntity(imported);

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

        // Content does not have any children, and we do not have parents in this context
        assertNotNull(output.getParentNodes());
        assertEquals(0, output.getParentNodes().count());

        assertNotNull(output.getChildrenNodes());
        assertEquals(0, output.getChildrenNodes().count());

        // Its pseudo-state getters should match our expectations
        assertTrue(output.isRootNode());
        assertTrue(output.isLeafNode());
    }

    @Test
    public void testBuildNodeForUpdate() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);

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

        // Content does not have any children, and we do not have parents in this context
        assertNotNull(output.getParentNodes());
        assertEquals(0, output.getParentNodes().count());

        assertNotNull(output.getChildrenNodes());
        assertEquals(0, output.getChildrenNodes().count());

        // Its pseudo-state getters should match our expectations
        assertTrue(output.isRootNode());
        assertTrue(output.isLeafNode());
    }

    @Test
    public void testBuildNodeWithNoImport() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        this.contentMapper.addExistingEntity(existing);

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

        // Content does not have any children, and we do not have parents in this context
        assertNotNull(output.getParentNodes());
        assertEquals(0, output.getParentNodes().count());

        assertNotNull(output.getChildrenNodes());
        assertEquals(0, output.getChildrenNodes().count());

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

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.contentMapper, owner, "invalid_id"));
    }

    @Test
    public void testCannotBuildNodeWithEmptyId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.contentMapper, owner, ""));
    }

    @Test
    public void testCannotBuildNodeWithNullId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.contentMapper, owner, null));
    }

    @Test
    public void testCannotBuildNodeWithNullOwner() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Content existing = TestUtil.createContent(id, "existing");
        ContentInfo imported = TestUtil.createContent(id, "imported");

        this.contentMapper.addExistingEntity(existing);
        this.contentMapper.addImportedEntity(imported);

        ContentNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalArgumentException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.contentMapper, null, id));
    }

}
