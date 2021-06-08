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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.RefreshResult.EntityState;
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.AbstractNode;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.ServiceAdapterModel;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



/**
 * Test suite for the ProductNodeVisitor class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodeProcessorTest {

    private NodeVisitor mockNodeVisitor(Class cls) {
        NodeVisitor visitor = mock(NodeVisitor.class);

        doReturn(cls).when(visitor).getEntityClass();

        return visitor;
    }

    private <E extends AbstractHibernateObject, I extends ServiceAdapterModel> EntityNode<E, I>
        buildEntityNode(Owner owner, String id, Class<E> entityCls, Class<I> importCls) {

        EntityNode<E, I> node = new AbstractNode<E, I>(owner, id) {
            @Override
            public Class<E> getEntityClass() {
                return entityCls;
            }
        };

        E existing = mock(entityCls);
        I imported = mock(importCls);
        E merged = mock(entityCls);

        doReturn(id).when(existing).getId();
        doReturn(id).when(merged).getId();

        node.setExistingEntity(existing);
        node.setImportedEntity(imported);
        node.setMergedEntity(merged);

        return node;
    }

    /**
     * Builds a set of trees that looks like the following:
     *
     *          tier1a          tier1b      tier1c          tier1d
     *         /      \                        |            /     \
     *      tier2a   tier2b                 tier2c      tier2c   tier2d
     *     /      \                            |           |
     *  tier3a   tier3b                     tier3c      tier3c
     *
     * Note that the repeated use of tier2c and its children is intentional to test
     * the scenario where a node/subtree appears in multiple trees.
     *
     * @return
     *  an array of EntityNode trees
     */
    private Collection<EntityNode> buildNodeTrees(NodeMapper mapper, Class cls) {
        Owner owner = TestUtil.createOwner();

        EntityNode tier1a = this.buildEntityNode(owner, "tier1a", Product.class, ProductInfo.class);
        EntityNode tier1b = this.buildEntityNode(owner, "tier1b", Product.class, ProductInfo.class);
        EntityNode tier1c = this.buildEntityNode(owner, "tier1c", Product.class, ProductInfo.class);
        EntityNode tier1d = this.buildEntityNode(owner, "tier1d", Product.class, ProductInfo.class);

        EntityNode tier2a = this.buildEntityNode(owner, "tier2a", Product.class, ProductInfo.class);
        EntityNode tier2b = this.buildEntityNode(owner, "tier2b", Product.class, ProductInfo.class);
        EntityNode tier2c = this.buildEntityNode(owner, "tier2c", Product.class, ProductInfo.class);
        EntityNode tier2d = this.buildEntityNode(owner, "tier2d", Product.class, ProductInfo.class);

        EntityNode tier3a = this.buildEntityNode(owner, "tier3a", Product.class, ProductInfo.class);
        EntityNode tier3b = this.buildEntityNode(owner, "tier3b", Product.class, ProductInfo.class);
        EntityNode tier3c = this.buildEntityNode(owner, "tier3c", Product.class, ProductInfo.class);

        tier1a.addChildNode(tier2a)
            .addChildNode(tier2b);

        tier1c.addChildNode(tier2c);

        tier1d.addChildNode(tier2c)
            .addChildNode(tier2d);

        tier2a.addChildNode(tier3a)
            .addChildNode(tier3b);

        tier2c.addChildNode(tier3c);

        List<EntityNode> nodes = List.of(tier1a, tier1b, tier1c, tier1d, tier2a, tier2b, tier2c, tier2d,
            tier3a, tier3b, tier3c);

        for (EntityNode node : nodes) {
            assertTrue(mapper.addNode(node));
        }

        return List.of(tier1a, tier1b, tier1c, tier1d);
    }

    @Test
    public void testSetNodeMapper() {
        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();

        NodeProcessor output = processor.setNodeMapper(mapper);

        assertNotNull(output);
        assertSame(processor, output);
    }

    @Test
    public void testSetNodeMapperDoesNotAcceptNull() {
        NodeProcessor processor = new NodeProcessor();
        assertThrows(IllegalArgumentException.class, () -> processor.setNodeMapper(null));
    }

    @Test
    public void testAddNodeVisitor() {
        NodeProcessor processor = new NodeProcessor();
        NodeVisitor visitor = this.mockNodeVisitor(Product.class);

        NodeProcessor output = processor.addVisitor(visitor);

        assertNotNull(output);
        assertSame(processor, output);
    }

    @Test
    public void testAddNodeVisitorDoesNotAcceptNull() {
        NodeProcessor processor = new NodeProcessor();
        assertThrows(IllegalArgumentException.class, () -> processor.addVisitor(null));
    }

    @Test
    public void testProcessNodesRequiresMapper() {
        NodeProcessor processor = new NodeProcessor();
        assertThrows(IllegalStateException.class, () -> processor.processNodes());
    }

    @Test
    public void testProcessNodeRequiresVisitors() {
        Owner owner = TestUtil.createOwner();

        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();

        EntityNode node = this.buildEntityNode(owner, "node-1", Product.class, ProductInfo.class);
        mapper.addNode(node);

        processor.setNodeMapper(mapper);

        assertThrows(IllegalStateException.class, () -> processor.processNodes());
    }

    @Test
    public void testProcessNodeInvokesCorrectVisitor() {
        Owner owner = TestUtil.createOwner();

        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();

        NodeVisitor visitor1 = this.mockNodeVisitor(Product.class);
        NodeVisitor visitor2 = this.mockNodeVisitor(Content.class);

        EntityNode node = this.buildEntityNode(owner, "node-1", Product.class, ProductInfo.class);

        doAnswer(iom -> {
            EntityNode receivedNode = (EntityNode) iom.getArguments()[0];

            if (receivedNode != null) {
                receivedNode.setNodeState(NodeState.UNCHANGED);
            }

            return null;
        }).when(visitor1).processNode(any(EntityNode.class));

        mapper.addNode(node);

        processor.setNodeMapper(mapper)
            .addVisitor(visitor1)
            .addVisitor(visitor2);

        processor.processNodes();

        verify(visitor1, times(1)).processNode(eq(node));
        verify(visitor2, never()).processNode(any(EntityNode.class));
    }

    private int validateNodeProcessingOrder(List<EntityNode> processOrder, EntityNode node) {
        int nodeIndex = processOrder.indexOf(node);
        int lastIndex = processOrder.lastIndexOf(node);

        // Ensure the node was actually processed, and only handed to our visitor once.
        assertNotEquals(-1, nodeIndex);
        assertEquals(nodeIndex, lastIndex);

        Iterator<EntityNode<?, ?>> children = node.getChildrenNodes()
            .iterator();

        while (children.hasNext()) {
            // Ensure the child was processed properly
            int childIndex = this.validateNodeProcessingOrder(processOrder, children.next());

            // Ensure the child was processed before the node
            assertThat(childIndex, lessThan(nodeIndex));
        }

        return nodeIndex;
    }

    @Test
    public void testProcessNodesProcessesNodesAsTrees() {
        // This test verifies the order of the node processing. We're expecting that it starts
        // at a root node, and then processes all of the children of that root before continuing
        // to the next root node

        // Challenges here:
        // - We don't care about the order in which trees themselves are processed, so long
        //   as for a given tree it is processed in its entirety before moving to the next
        //   tree
        // - This restriction applies also to subtrees within a given tree. That is, we don't
        //   care about the order in which children are processed, so long as a given child is
        //   fully processed before moving on to other children.
        // - Subtrees can be shared!
        // - Because of this style of expected processing order, we have to be careful as to
        //   how the test validation is setup to ensure we don't have periodic failures if the
        //   order of the nodes on a given tier happens to change

        Class cls = Product.class;

        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();
        NodeVisitor visitor = this.mockNodeVisitor(cls);

        List<EntityNode> processOrder = new LinkedList<>();

        // Have our mock visitor store the order in which the nodes are processed.
        doAnswer(iom -> {
            EntityNode node = (EntityNode) iom.getArguments()[0];

            if (node != null) {
                node.setNodeState(NodeState.UNCHANGED);
                processOrder.add(node);
            }

            return null;
        }).when(visitor).processNode(any(EntityNode.class));

        Collection<EntityNode> trees = this.buildNodeTrees(mapper, cls);

        processor.setNodeMapper(mapper)
            .addVisitor(visitor);

        processor.processNodes();

        // Step through our trees and verify that the children are processed before the parents, and
        // that the processing doesn't happen out of order

        for (EntityNode root : trees) {
            this.validateNodeProcessingOrder(processOrder, root);
        }
    }

    private int validateNodePruningOrder(List<EntityNode> pruneOrder, EntityNode node) {
        int nodeIndex = pruneOrder.indexOf(node);
        int lastIndex = pruneOrder.lastIndexOf(node);

        // Ensure the node was actually processed, and only handed to our visitor once.
        assertNotEquals(-1, nodeIndex);
        assertEquals(nodeIndex, lastIndex);

        Iterator<EntityNode<?, ?>> parents = node.getParentNodes()
            .iterator();

        while (parents.hasNext()) {
            // Ensure the parent was processed properly
            int parentIndex = this.validateNodePruningOrder(pruneOrder, parents.next());

            // Ensure the parent was processed before the node
            assertThat(parentIndex, lessThan(nodeIndex));
        }

        return nodeIndex;
    }

    @Test
    public void testPruneNodesProcessesNodesAsTrees() {
        // This test verifies the order of the node processing. We're expecting that it starts
        // at a root node, and then processes all of the children of that root before continuing
        // to the next root node

        // Challenges here:
        // - We don't care about the order in which trees themselves are processed, so long
        //   as for a given tree it is processed in its entirety before moving to the next
        //   tree
        // - This restriction applies also to subtrees within a given tree. That is, we don't
        //   care about the order in which children are processed, so long as a given child is
        //   fully processed before moving on to other children.
        // - Subtrees can be shared!
        // - Because of this style of expected processing order, we have to be careful as to
        //   how the test validation is setup to ensure we don't have periodic failures if the
        //   order of the nodes on a given tier happens to change

        Class cls = Product.class;

        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();
        NodeVisitor visitor = this.mockNodeVisitor(cls);

        List<EntityNode> pruneOrder = new LinkedList<>();

        // Have our mock visitor store the order in which the nodes are processed.
        doAnswer(iom -> {
            EntityNode node = (EntityNode) iom.getArguments()[0];

            if (node != null) {
                node.setNodeState(NodeState.DELETED);
                pruneOrder.add(node);
            }

            return null;
        }).when(visitor).pruneNode(any(EntityNode.class));

        Collection<EntityNode> trees = this.buildNodeTrees(mapper, cls);

        processor.setNodeMapper(mapper)
            .addVisitor(visitor);

        processor.processNodes();

        // Step through our trees and verify that the parents are processed before the children, and
        // that the processing doesn't happen out of order

        for (EntityNode root : trees) {
            this.validateNodePruningOrder(pruneOrder, root);
        }
    }

    @Test
    public void testProcessNodesAppliesChangesAsTrees() {
        // This test verifies the order of change application. We're expecting that it starts
        // at a root node, and then processes all of the children of that root before continuing
        // to the next root node

        // Challenges here:
        // - We don't care about the order in which trees themselves are processed, so long
        //   as for a given tree it is processed in its entirety before moving to the next
        //   tree
        // - This restriction applies also to subtrees within a given tree. That is, we don't
        //   care about the order in which children are processed, so long as a given child is
        //   fully processed before moving on to other children.
        // - Subtrees can be shared!
        // - Because of this style of expected processing order, we have to be careful as to
        //   how the test validation is setup to ensure we don't have periodic failures if the
        //   order of the nodes on a given tier happens to change

        Class cls = Product.class;

        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();
        NodeVisitor visitor = this.mockNodeVisitor(cls);

        List<EntityNode> processOrder = new LinkedList<>();

        // Have our mock visitor store the order in which the nodes are processed.
        doAnswer(iom -> {
            EntityNode node = (EntityNode) iom.getArguments()[0];

            if (node != null) {
                node.setNodeState(NodeState.UNCHANGED);
                processOrder.add(node);
            }

            return null;
        }).when(visitor).applyChanges(any(EntityNode.class));

        Collection<EntityNode> trees = this.buildNodeTrees(mapper, cls);

        processor.setNodeMapper(mapper)
            .addVisitor(visitor);

        processor.processNodes();

        // Step through our trees and verify that the children are processed before the parents, and
        // that the processing doesn't happen out of order
        for (EntityNode root : trees) {
            this.validateNodeProcessingOrder(processOrder, root);
        }
    }

    @Test
    public void testProcessNodesReturnsAccurateRefreshResult() {
        Owner owner = TestUtil.createOwner();

        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();
        NodeVisitor productVisitor = this.mockNodeVisitor(Product.class);
        NodeVisitor contentVisitor = this.mockNodeVisitor(Content.class);

        int nodesPerState = 3;
        int nodeCount = 0;

        for (NodeState state : NodeState.values()) {
            for (int i = 0; i < nodesPerState; ++i) {
                EntityNode pnode = this.buildEntityNode(owner, "node-" + ++nodeCount,
                    Product.class, ProductInfo.class);
                pnode.setNodeState(state);

                EntityNode cnode = this.buildEntityNode(owner, "node-" + ++nodeCount,
                    Content.class, ContentInfo.class);
                cnode.setNodeState(state);

                assertTrue(mapper.addNode(pnode));
                assertTrue(mapper.addNode(cnode));
            }
        }

        processor.setNodeMapper(mapper)
            .addVisitor(productVisitor)
            .addVisitor(contentVisitor);

        RefreshResult result = processor.processNodes();
        assertNotNull(result);

        for (Class cls : List.of(Product.class, Content.class)) {
            for (NodeState state : NodeState.values()) {
                Map entities;

                switch (state) {
                    case CREATED:
                        entities = result.getEntities(cls, EntityState.CREATED);
                        assertNotNull(entities);
                        assertEquals(nodesPerState, entities.size());
                        break;

                    case UPDATED:
                        entities = result.getEntities(cls, EntityState.UPDATED);
                        assertNotNull(entities);
                        assertEquals(nodesPerState, entities.size());
                        break;

                    case UNCHANGED:
                        entities = result.getEntities(cls, EntityState.UNCHANGED);
                        assertNotNull(entities);
                        assertEquals(nodesPerState, entities.size());
                        break;

                    case DELETED:
                        entities = result.getEntities(cls, EntityState.DELETED);
                        assertNotNull(entities);
                        assertEquals(nodesPerState, entities.size());
                        break;

                    case SKIPPED:

                    default:
                        // Intentionally left empty
                }
            }
        }
    }

}
