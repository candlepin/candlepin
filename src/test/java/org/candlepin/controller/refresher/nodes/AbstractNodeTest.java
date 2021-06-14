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

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ServiceAdapterModel;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * General tests that apply to all entity nodes. Test suites for specific entity node
 * implementations should extend this class and override the necessary methods.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractNodeTest<E extends AbstractHibernateObject, I extends ServiceAdapterModel> {

    /**
     * Builds an EntityNode instance using the provided owner and entity ID
     *
     * @param owner
     *  the owner for the new entity node
     *
     * @param entityId
     *  the ID of the new entity node, and the expected ID of the entities it will contain
     *
     * @return
     *  the newly created EntityNode instance
     */
    protected abstract EntityNode<E, I> buildEntityNode(Owner owner, String entityId);

    /**
     * Builds a new "local" entity to be used for testing.
     *
     * @param owner
     *  the owner for the new local entity
     *
     * @param entityId
     *  the ID for the new entity
     *
     * @return
     *  a new local entity instance
     */
    protected abstract E buildLocalEntity(Owner owner, String entityId);

    /**
     * Builds a new "imported" entity to be used for testing
     *
     * @param owner
     *  the owner for the new imported entity
     *
     * @param entityId
     *  the ID for the new entity
     *
     * @return
     *  a new imported entity instance
     */
    protected abstract I buildImportedEntity(Owner owner, String entityId);

    /**
     * Builds an entity node using a generated owner and entity ID
     *
     * @return
     *  a new entity node
     */
    public EntityNode<E, I> buildEntityNode() {
        return this.buildEntityNode(TestUtil.createOwner(), TestUtil.randomString());
    }

    @Test
    public void testGetEntityOwner() {
        Owner owner = TestUtil.createOwner();
        EntityNode<E, I> node = this.buildEntityNode(owner, "test_id");

        assertSame(owner, node.getOwner());
    }

    @Test
    public void testGetEntityId() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        EntityNode<E, I> node = this.buildEntityNode(owner, "test_id");

        assertEquals(id, node.getEntityId());
    }

    @Test
    public void testParentNodes() {
        Owner owner = TestUtil.createOwner();
        EntityNode<E, I> node = this.buildEntityNode(owner, "node");
        List<EntityNode<E, I>> parentNodes = List.of(
            this.buildEntityNode(owner, "p1"),
            this.buildEntityNode(owner, "p2"),
            this.buildEntityNode(owner, "p3")
        );

        // Verify initial state is an empty collection
        Stream<EntityNode<?, ?>> parents = node.getParentNodes();
        assertNotNull(parents);

        List<EntityNode<?, ?>> collected = parents.collect(Collectors.toList());
        assertEquals(0, collected.size());

        // Add some nodes
        for (int i = 0; i < parentNodes.size(); ++i) {
            EntityNode output = node.addParentNode(parentNodes.get(i));

            assertNotNull(output);
            assertSame(node, output);

            // Verify the added parent nodes are present in the returned collection
            parents = node.getParentNodes();
            assertNotNull(parents);

            collected = parents.collect(Collectors.toList());
            assertEquals(i + 1, collected.size());

            for (int j = 0; j <= i; ++j) {
                assertThat(collected, hasItem(parentNodes.get(j)));
            }
        }
    }

    @Test
    public void testParentNodeRepeatedAdd() {
        EntityNode<E, I> node = this.buildEntityNode();
        EntityNode<E, I> parent = this.buildEntityNode();

        // Verify initial state is an empty collection
        Stream<EntityNode<?, ?>> parents = node.getParentNodes();
        assertNotNull(parents);

        List<EntityNode<?, ?>> collected = parents.collect(Collectors.toList());
        assertEquals(0, collected.size());

        // Add the parent node
        EntityNode output = node.addParentNode(parent);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the node is present
        parents = node.getParentNodes();
        assertNotNull(parents);

        collected = parents.collect(Collectors.toList());
        assertEquals(1, collected.size());
        assertThat(collected, hasItem(parent));

        // Add the parent node again
        output = node.addParentNode(parent);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the parent collection hasn't changed
        parents = node.getParentNodes();
        assertNotNull(parents);

        collected = parents.collect(Collectors.toList());
        assertEquals(1, collected.size());
        assertThat(collected, hasItem(parent));
    }

    @Test
    public void testParentNodeWithNullParent() {
        EntityNode<E, I> node = this.buildEntityNode();
        assertThrows(IllegalArgumentException.class, () -> node.addParentNode(null));
    }

    @Test
    public void testEntityNodeCannotBecomeItsOwnParent() {
        EntityNode<E, I> node = this.buildEntityNode();
        assertThrows(IllegalArgumentException.class, () -> node.addParentNode(node));
    }

    @Test
    public void testChildredNodes() {
        Owner owner = TestUtil.createOwner();
        EntityNode<E, I> node = this.buildEntityNode(owner, "node");
        List<EntityNode<E, I>> childrenNodes = List.of(
            this.buildEntityNode(owner, "c1"),
            this.buildEntityNode(owner, "c2"),
            this.buildEntityNode(owner, "c3")
        );

        // Verify initial state is an empty collection
        Stream<EntityNode<?, ?>> children = node.getChildrenNodes();
        assertNotNull(children);

        List<EntityNode<?, ?>> collected = children.collect(Collectors.toList());
        assertEquals(0, collected.size());

        // Add some nodes
        for (int i = 0; i < childrenNodes.size(); ++i) {
            EntityNode output = node.addChildNode(childrenNodes.get(i));
            assertNotNull(output);
            assertSame(node, output);

            // Verify the added child nodes are present in the returned collection
            children = node.getChildrenNodes();
            assertNotNull(children);

            collected = children.collect(Collectors.toList());
            assertEquals(i + 1, collected.size());

            for (int j = 0; j <= i; ++j) {
                assertThat(collected, hasItem(childrenNodes.get(j)));
            }
        }
    }

    @Test
    public void testChildNodeRepeatedAdd() {
        EntityNode<E, I> node = this.buildEntityNode();
        EntityNode<E, I> child = this.buildEntityNode();

        // Verify initial state is an empty collection
        Stream<EntityNode<?, ?>> children = node.getChildrenNodes();
        assertNotNull(children);

        List<EntityNode<?, ?>> collected = children.collect(Collectors.toList());
        assertEquals(0, collected.size());

        // Add the child node
        EntityNode output = node.addChildNode(child);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the node is present
        children = node.getChildrenNodes();
        assertNotNull(children);

        collected = children.collect(Collectors.toList());
        assertEquals(1, collected.size());
        assertThat(collected, hasItem(child));

        // Add the child node again
        output = node.addChildNode(child);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the child collection hasn't changed
        children = node.getChildrenNodes();
        assertNotNull(children);

        collected = children.collect(Collectors.toList());
        assertEquals(1, collected.size());
        assertThat(collected, hasItem(child));
    }

    @Test
    public void testChildNodeWithNullChild() {
        EntityNode<E, I> node = this.buildEntityNode();
        assertThrows(IllegalArgumentException.class, () -> node.addChildNode(null));
    }

    @Test
    public void testEntityNodeCannotBecomeItsOwnChild() {
        EntityNode<E, I> node = this.buildEntityNode();
        assertThrows(IllegalArgumentException.class, () -> node.addChildNode(node));
    }

    @Test
    public void testIsRootNode() {
        EntityNode<E, I> node = this.buildEntityNode();
        EntityNode<E, I> parent = this.buildEntityNode();

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
        EntityNode<E, I> node = this.buildEntityNode();
        EntityNode<E, I> child = this.buildEntityNode();

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
        Owner owner = TestUtil.createOwner();
        E entity = this.buildLocalEntity(owner, "test_id");
        EntityNode<E, I> node = this.buildEntityNode(owner, "test_id");

        // Initial state should be null
        assertNull(node.getExistingEntity());

        // Set the entity
        EntityNode output = node.setExistingEntity(entity);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was stored and is returned
        E existing = node.getExistingEntity();
        assertNotNull(existing);
        assertSame(entity, existing);

        // Clear the entity
        output = node.setExistingEntity(null);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was cleared
        assertNull(node.getExistingEntity());
    }

    @Test
    public void testImportedEntity() {
        Owner owner = TestUtil.createOwner();
        I entity = this.buildImportedEntity(owner, "test_id");
        EntityNode<E, I> node = this.buildEntityNode(owner, "test_id");

        // Initial state should be null
        assertNull(node.getImportedEntity());

        // Set the entity
        EntityNode output = node.setImportedEntity(entity);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was stored and is returned
        I imported = node.getImportedEntity();
        assertNotNull(imported);
        assertSame(entity, imported);

        // Clear the entity
        output = node.setImportedEntity(null);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was cleared
        assertNull(node.getImportedEntity());
    }

    @Test
    public void testUpdatedEntity() {
        Owner owner = TestUtil.createOwner();
        E entity = this.buildLocalEntity(owner, "test_id");
        EntityNode<E, I> node = this.buildEntityNode(owner, "test_id");

        // Initial state should be null
        assertNull(node.getMergedEntity());

        // Set the entity
        EntityNode output = node.setMergedEntity(entity);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was stored and is returned
        E updated = node.getMergedEntity();
        assertNotNull(updated);
        assertSame(entity, updated);

        // Clear the entity
        output = node.setMergedEntity(null);
        assertNotNull(output);
        assertSame(node, output);

        // Verify the entity was cleared
        assertNull(node.getMergedEntity());
    }
}
