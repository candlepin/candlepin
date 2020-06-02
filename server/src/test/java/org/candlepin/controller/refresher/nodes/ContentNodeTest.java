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
package org.candlepin.controller.refresher.nodes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.Set;



/**
 * Test suite for the ContentNode class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentNodeTest {

    /**
     * Builds a ContentNode using a generated owner and ID
     *
     * @return
     *  a new ContentNode
     */
    private ContentNode buildContentNode() {
        Owner owner = TestUtil.createOwner();
        return new ContentNode(owner, TestUtil.randomString());
    }

    @Test
    public void testGetEntityClass() {
        Owner owner = TestUtil.createOwner();
        ContentNode node = new ContentNode(owner, "test_id");

        assertEquals(Content.class, node.getEntityClass());
    }

    @Test
    public void testGetEntityOwner() {
        Owner owner = TestUtil.createOwner();
        ContentNode node = new ContentNode(owner, "test_id");

        assertEquals(owner, node.getOwner());
    }

    @Test
    public void testGetEntityId() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        ContentNode node = new ContentNode(owner, id);

        assertEquals(id, node.getEntityId());
    }

    @Test
    public void testParentNodes() {
        ContentNode node = this.buildContentNode();
        ContentNode[] parentNodes = new ContentNode[] {
            this.buildContentNode(),
            this.buildContentNode(),
            this.buildContentNode()
        };

        // Verify initial state is an empty collection
        Collection<EntityNode<?, ?>> parents = node.getParentNodes();

        assertNotNull(parents);
        assertEquals(0, parents.size());

        // Add some nodes
        for (int i = 0; i < parentNodes.length; ++i) {
            EntityNode output = node.addParentNode(parentNodes[i]);

            assertNotNull(output);
            assertSame(node, output);

            // Verify the added parent nodes are present in the returned collection
            parents = node.getParentNodes();

            assertNotNull(parents);
            assertEquals(i + 1, parents.size());

            for (int j = 0; j <= i; ++j) {
                assertThat(parents, hasItem(parentNodes[j]));
            }
        }
    }

    @Test
    public void testParentNodeRepeatedAdd() {
        ContentNode node = this.buildContentNode();
        ContentNode parent = this.buildContentNode();

        // Verify initial state is an empty collection
        Collection<EntityNode<?, ?>> parents = node.getParentNodes();
        assertNotNull(parents);
        assertEquals(0, parents.size());

        // Add the parent node
        EntityNode output = node.addParentNode(parent);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the node is present
        parents = node.getParentNodes();
        assertNotNull(parents);
        assertEquals(1, parents.size());
        assertThat(parents, hasItem(parent));

        // Add the parent node again
        output = node.addParentNode(parent);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the parent collection hasn't changed
        parents = node.getParentNodes();
        assertNotNull(parents);
        assertEquals(1, parents.size());
        assertThat(parents, hasItem(parent));
    }

    @Test
    public void testParentNodeWithNullParent() {
        ContentNode node = this.buildContentNode();
        assertThrows(IllegalArgumentException.class, () -> node.addParentNode(null));
    }

    @Test
    public void testEntityNodeCannotBecomeItsOwnParent() {
        ContentNode node = this.buildContentNode();
        assertThrows(IllegalArgumentException.class, () -> node.addParentNode(node));
    }

    @Test
    public void testChildredNodes() {
        ContentNode node = this.buildContentNode();
        ContentNode[] childrenNodes = new ContentNode[] {
            this.buildContentNode(),
            this.buildContentNode(),
            this.buildContentNode()
        };

        // Verify initial state is an empty collection
        Collection<EntityNode<?, ?>> children = node.getChildrenNodes();

        assertNotNull(children);
        assertEquals(0, children.size());

        // Add some nodes
        for (int i = 0; i < childrenNodes.length; ++i) {
            EntityNode output = node.addChildNode(childrenNodes[i]);

            assertNotNull(output);
            assertSame(node, output);

            // Verify the added child nodes are present in the returned collection
            children = node.getChildrenNodes();

            assertNotNull(children);
            assertEquals(i + 1, children.size());

            for (int j = 0; j <= i; ++j) {
                assertThat(children, hasItem(childrenNodes[j]));
            }
        }
    }

    @Test
    public void testChildNodeRepeatedAdd() {
        ContentNode node = this.buildContentNode();
        ContentNode child = this.buildContentNode();

        // Verify initial state is an empty collection
        Collection<EntityNode<?, ?>> children = node.getChildrenNodes();
        assertNotNull(children);
        assertEquals(0, children.size());

        // Add the child node
        EntityNode output = node.addChildNode(child);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the node is present
        children = node.getChildrenNodes();
        assertNotNull(children);
        assertEquals(1, children.size());
        assertThat(children, hasItem(child));

        // Add the child node again
        output = node.addChildNode(child);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the child collection hasn't changed
        children = node.getChildrenNodes();
        assertNotNull(children);
        assertEquals(1, children.size());
        assertThat(children, hasItem(child));
    }

    @Test
    public void testChildNodeWithNullChild() {
        ContentNode node = this.buildContentNode();
        assertThrows(IllegalArgumentException.class, () -> node.addChildNode(null));
    }

    @Test
    public void testEntityNodeCannotBecomeItsOwnChild() {
        ContentNode node = this.buildContentNode();
        assertThrows(IllegalArgumentException.class, () -> node.addChildNode(node));
    }

    @Test
    public void testIsRootNode() {
        ContentNode node = this.buildContentNode();
        ContentNode parent = this.buildContentNode();

        // Verify initial state is true (no parent == root)
        assertTrue(node.isRootNode());

        // Add the parent node
        EntityNode output = node.addParentNode(parent);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the updated state is false (any parents == not root)
        assertFalse(node.isRootNode());
    }

    @Test
    public void testIsLeafNode() {
        ContentNode node = this.buildContentNode();
        ContentNode child = this.buildContentNode();

        // Verify initial state is true (no children == leaf)
        assertTrue(node.isLeafNode());

        // Add the child node
        EntityNode output = node.addChildNode(child);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the updated state is false (any children == not leaf)
        assertFalse(node.isLeafNode());
    }

    @Test
    public void testExistingEntity() {
        ContentNode node = this.buildContentNode();
        Content content = TestUtil.createContent();

        // Initial state should be null
        assertNull(node.getExistingEntity());

        // Set the entity
        EntityNode output = node.setExistingEntity(content);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was stored and is returned
        Content existing = node.getExistingEntity();
        assertNotNull(existing);
        assertSame(content, existing);

        // Clear the entity
        output = node.setExistingEntity(null);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was cleared
        assertNull(node.getExistingEntity());
    }

    @Test
    public void testImportedEntity() {
        ContentNode node = this.buildContentNode();
        ContentInfo content = TestUtil.createContent();

        // Initial state should be null
        assertNull(node.getImportedEntity());

        // Set the entity
        EntityNode output = node.setImportedEntity(content);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was stored and is returned
        ContentInfo imported = node.getImportedEntity();
        assertNotNull(imported);
        assertSame(content, imported);

        // Clear the entity
        output = node.setImportedEntity(null);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was cleared
        assertNull(node.getImportedEntity());
    }

    @Test
    public void testUpdatedEntity() {
        ContentNode node = this.buildContentNode();
        Content content = TestUtil.createContent();

        // Initial state should be null
        assertNull(node.getMergedEntity());

        // Set the entity
        EntityNode output = node.setMergedEntity(content);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was stored and is returned
        Content updated = node.getMergedEntity();
        assertNotNull(updated);
        assertSame(content, updated);

        // Clear the entity
        output = node.setMergedEntity(null);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was cleared
        assertNull(node.getMergedEntity());
    }

    @Test
    public void testCandidateEntities() {
        ContentNode node = this.buildContentNode();
        Content content1 = TestUtil.createContent();
        Content content2 = TestUtil.createContent();
        Content content3 = TestUtil.createContent();
        Set<Content> candidates = Util.asSet(content1, content2, content3);

        // Initial state should be null
        assertNull(node.getCandidateEntities());

        // Set the candidate entities
        EntityNode output = node.setCandidateEntities(candidates);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the candidates were stored and can be returned
        Set<Content> result = node.getCandidateEntities();
        assertNotNull(result);
        assertEquals(candidates.size(), result.size());

        for (Content candidate : candidates) {
            assertThat(result, hasItem(candidate));
        }

        // Clear the candidate entities
        output = node.setCandidateEntities(null);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was cleared
        assertNull(node.getCandidateEntities());
    }
}
