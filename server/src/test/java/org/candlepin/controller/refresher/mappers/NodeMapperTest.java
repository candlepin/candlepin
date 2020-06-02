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

import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.service.model.ServiceAdapterModel;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;



/**
 * Test suite for the NodeMapper class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodeMapperTest {

    private EntityNode mockNodeHierarchy(EntityNode node, Collection<EntityNode> parentNodes,
        Collection<EntityNode> childrenNodes) {

        if (parentNodes != null && !parentNodes.isEmpty()) {
            doReturn(parentNodes).when(node).getParentNodes();
            doReturn(false).when(node).isRootNode();
        }
        else {
            doReturn(Collections.emptyList()).when(node).getParentNodes();
            doReturn(true).when(node).isRootNode();
        }

        if (parentNodes != null && !parentNodes.isEmpty()) {
            doReturn(parentNodes).when(node).getParentNodes();
            doReturn(false).when(node).isLeafNode();
        }
        else {
            doReturn(Collections.emptyList()).when(node).getParentNodes();
            doReturn(true).when(node).isLeafNode();
        }

        return node;
    }

    private EntityNode mockEntityNode(Owner owner, Class cls, String id) {
        EntityNode node = mock(EntityNode.class);

        AbstractHibernateObject existing = mock(AbstractHibernateObject.class);
        ServiceAdapterModel imported = mock(ServiceAdapterModel.class);

        doReturn(owner).when(node).getOwner();
        doReturn(cls).when(node).getEntityClass();
        doReturn(id).when(node).getEntityId();

        doReturn(existing).when(node).getExistingEntity();
        doReturn(imported).when(node).getImportedEntity();

        return node;
    }

    @Test
    public void testAddNode() {
        Owner owner = TestUtil.createOwner();
        EntityNode node = this.mockEntityNode(owner, Product.class, "test_id");

        NodeMapper mapper = new NodeMapper();

        boolean result = mapper.addNode(node);
        assertTrue(result);

        // Repeated additions should return false
        result = mapper.addNode(node);
        assertFalse(result);
    }

    @Test
    public void testAddNodeWithNullNode() {
        NodeMapper mapper = new NodeMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addNode(null));
    }

    @Test
    public void testGetNodeDifferentiatesByClass() {
        String id = "shared_id";
        Owner owner = TestUtil.createOwner();
        EntityNode node1 = this.mockEntityNode(owner, Product.class, id);
        EntityNode node2 = this.mockEntityNode(owner, Content.class, id);

        NodeMapper mapper = new NodeMapper();

        // Ensure our target nodes are added successfully
        assertTrue(mapper.addNode(node1));
        assertTrue(mapper.addNode(node2));

        EntityNode output = mapper.getNode(Product.class, id);
        assertNotNull(output);
        assertSame(output, node1);

        output = mapper.getNode(Content.class, id);
        assertNotNull(output);
        assertSame(output, node2);
    }

    @Test
    public void testGetNodeDifferentiatesById() {
        Class cls = Product.class;
        Owner owner = TestUtil.createOwner();
        EntityNode node1 = this.mockEntityNode(owner, cls, "id-1");
        EntityNode node2 = this.mockEntityNode(owner, cls, "id-2");

        NodeMapper mapper = new NodeMapper();

        // Ensure our target nodes are added successfully
        assertTrue(mapper.addNode(node1));
        assertTrue(mapper.addNode(node2));

        EntityNode output = mapper.getNode(cls, "id-1");
        assertNotNull(output);
        assertSame(output, node1);

        output = mapper.getNode(cls, "id-2");
        assertNotNull(output);
        assertSame(output, node2);
    }

    @Test
    public void testGetNodeWithUnmappedClass() {
        Owner owner = TestUtil.createOwner();
        EntityNode node1 = this.mockEntityNode(owner, Product.class, "id-1");
        EntityNode node2 = this.mockEntityNode(owner, Content.class, "id-2");

        NodeMapper mapper = new NodeMapper();

        // Ensure our target nodes are added successfully
        assertTrue(mapper.addNode(node1));
        assertTrue(mapper.addNode(node2));

        EntityNode output = mapper.getNode(Pool.class, "id-1");
        assertNull(output);
    }

    @Test
    public void testGetNodeWithNullClass() {
        Owner owner = TestUtil.createOwner();
        EntityNode node1 = this.mockEntityNode(owner, Product.class, "id-1");
        EntityNode node2 = this.mockEntityNode(owner, Content.class, "id-2");

        NodeMapper mapper = new NodeMapper();

        // Ensure our target nodes are added successfully
        assertTrue(mapper.addNode(node1));
        assertTrue(mapper.addNode(node2));

        EntityNode output = mapper.getNode(null, "id-1");
        assertNull(output);
    }

    @Test
    public void testGetNodeWithUnmappedId() {
        Owner owner = TestUtil.createOwner();
        EntityNode node1 = this.mockEntityNode(owner, Product.class, "id-1");
        EntityNode node2 = this.mockEntityNode(owner, Content.class, "id-2");

        NodeMapper mapper = new NodeMapper();

        // Ensure our target nodes are added successfully
        assertTrue(mapper.addNode(node1));
        assertTrue(mapper.addNode(node2));

        EntityNode output = mapper.getNode(Product.class, "unmapped_id");
        assertNull(output);
    }

    @Test
    public void testGetNodeWithNullId() {
        Owner owner = TestUtil.createOwner();
        EntityNode node1 = this.mockEntityNode(owner, Product.class, "id-1");
        EntityNode node2 = this.mockEntityNode(owner, Content.class, "id-2");

        NodeMapper mapper = new NodeMapper();

        // Ensure our target nodes are added successfully
        assertTrue(mapper.addNode(node1));
        assertTrue(mapper.addNode(node2));

        EntityNode output = mapper.getNode(Product.class, null);
        assertNull(output);
    }

    /**
     * Builds a set of trees that looks like the following:
     *
     *          tier1a          tier1b
     *         /      \            |
     *      tier2a   tier2b     tier2c
     *     /      \                |
     *  tier3a   tier3b         tier3c
     *
     * @return
     *  an array of collections, where element 0 is the collection of root nodes, and element 1
     *  is a collection of all nodes
     */
    private Collection<EntityNode>[] buildNodeTrees(NodeMapper mapper) {
        Owner owner = TestUtil.createOwner();

        EntityNode tier1a = this.mockEntityNode(owner, Product.class, "tier1a");
        EntityNode tier1b = this.mockEntityNode(owner, Product.class, "tier1b");

        EntityNode tier2a = this.mockEntityNode(owner, Product.class, "tier2a");
        EntityNode tier2b = this.mockEntityNode(owner, Product.class, "tier2b");
        EntityNode tier2c = this.mockEntityNode(owner, Product.class, "tier2c");

        EntityNode tier3a = this.mockEntityNode(owner, Product.class, "tier3a");
        EntityNode tier3b = this.mockEntityNode(owner, Product.class, "tier3b");
        EntityNode tier3c = this.mockEntityNode(owner, Product.class, "tier3c");

        this.mockNodeHierarchy(tier1a, null, Arrays.asList(tier2a, tier2b));
        this.mockNodeHierarchy(tier2a, Arrays.asList(tier1a), Arrays.asList(tier3a, tier3b));
        this.mockNodeHierarchy(tier2b, Arrays.asList(tier1a), null);
        this.mockNodeHierarchy(tier3a, Arrays.asList(tier2a), null);
        this.mockNodeHierarchy(tier3b, Arrays.asList(tier2a), null);

        this.mockNodeHierarchy(tier1b, null, Arrays.asList(tier2c));
        this.mockNodeHierarchy(tier2c, Arrays.asList(tier1b), Arrays.asList(tier3c));
        this.mockNodeHierarchy(tier3c, Arrays.asList(tier2c), null);

        Collection<EntityNode> nodes =
            Arrays.asList(tier1a, tier1b, tier2a, tier2b, tier2c, tier3a, tier3b, tier3c);

        for (EntityNode node : nodes) {
            assertTrue(mapper.addNode(node));
        }

        return new Collection[] {
            Arrays.asList(tier1a, tier1b),
            nodes
        };
    }

    @Test
    public void testGetNodeIterator() {
        NodeMapper mapper = new NodeMapper();
        Collection<EntityNode>[] nodeSets = this.buildNodeTrees(mapper);

        Iterator<EntityNode<?, ?>> iterator = mapper.getNodeIterator();
        assertNotNull(iterator);

        // Collect all the values in the iterator into an array so we can verify the
        // count and values returned
        List<EntityNode> collected = new LinkedList<>();
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        // Verify the collected values
        assertEquals(nodeSets[1].size(), collected.size());
        for (EntityNode node : nodeSets[1]) {
            assertThat(collected, hasItem(node));
        }
    }

    @Test
    public void testGetNodeIteratorRemoveUnsupported() {
        NodeMapper mapper = new NodeMapper();
        Collection<EntityNode>[] nodeSets = this.buildNodeTrees(mapper);

        Iterator<EntityNode<?, ?>> iterator = mapper.getNodeIterator();
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertThrows(UnsupportedOperationException.class, () -> iterator.remove());
    }

    @Test
    public void testGetRootIterator() {
        NodeMapper mapper = new NodeMapper();
        Collection<EntityNode>[] nodeSets = this.buildNodeTrees(mapper);

        Iterator<EntityNode<?, ?>> iterator = mapper.getRootIterator();
        assertNotNull(iterator);

        // Collect all the values in the iterator into an array so we can verify the
        // count and values returned
        List<EntityNode> collected = new LinkedList<>();
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        // Verify the collected values
        assertEquals(nodeSets[0].size(), collected.size());
        for (EntityNode node : nodeSets[0]) {
            assertThat(collected, hasItem(node));
        }
    }

    @Test
    public void testGetRootIteratorRemoveUnsupported() {
        NodeMapper mapper = new NodeMapper();
        Collection<EntityNode>[] nodeSets = this.buildNodeTrees(mapper);

        Iterator<EntityNode<?, ?>> iterator = mapper.getRootIterator();
        assertNotNull(iterator);

        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertThrows(UnsupportedOperationException.class, () -> iterator.remove());
    }
}
