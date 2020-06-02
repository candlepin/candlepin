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
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
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

    private EntityNode mockEntityNode(Owner owner, Class<? extends AbstractHibernateObject> cls, String id) {
        EntityNode node = mock(EntityNode.class);

        AbstractHibernateObject existing = mock(cls);
        ServiceAdapterModel imported = mock(ServiceAdapterModel.class);
        AbstractHibernateObject merged = mock(cls);

        doReturn(owner).when(node).getOwner();
        doReturn(cls).when(node).getEntityClass();
        doReturn(id).when(node).getEntityId();

        doReturn(id).when(existing).getId();
        doReturn(id).when(merged).getId();

        doReturn(existing).when(node).getExistingEntity();
        doReturn(imported).when(node).getImportedEntity();
        doReturn(merged).when(node).getMergedEntity();

        return node;
    }
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

        if (childrenNodes != null && !childrenNodes.isEmpty()) {
            doReturn(childrenNodes).when(node).getChildrenNodes();
            doReturn(false).when(node).isLeafNode();
        }
        else {
            doReturn(Collections.emptyList()).when(node).getChildrenNodes();
            doReturn(true).when(node).isLeafNode();
        }

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

        EntityNode tier1a = this.mockEntityNode(owner, cls, "tier1a");
        EntityNode tier1b = this.mockEntityNode(owner, cls, "tier1b");
        EntityNode tier1c = this.mockEntityNode(owner, cls, "tier1c");
        EntityNode tier1d = this.mockEntityNode(owner, cls, "tier1d");

        EntityNode tier2a = this.mockEntityNode(owner, cls, "tier2a");
        EntityNode tier2b = this.mockEntityNode(owner, cls, "tier2b");
        EntityNode tier2c = this.mockEntityNode(owner, cls, "tier2c");
        EntityNode tier2d = this.mockEntityNode(owner, cls, "tier2d");

        EntityNode tier3a = this.mockEntityNode(owner, cls, "tier3a");
        EntityNode tier3b = this.mockEntityNode(owner, cls, "tier3b");
        EntityNode tier3c = this.mockEntityNode(owner, cls, "tier3c");

        this.mockNodeHierarchy(tier1a, null, Arrays.asList(tier2a, tier2b));
        this.mockNodeHierarchy(tier2a, Arrays.asList(tier1a), Arrays.asList(tier3a, tier3b));
        this.mockNodeHierarchy(tier2b, Arrays.asList(tier1a), null);
        this.mockNodeHierarchy(tier3a, Arrays.asList(tier2a), null);
        this.mockNodeHierarchy(tier3b, Arrays.asList(tier2a), null);

        this.mockNodeHierarchy(tier1b, null, null);

        this.mockNodeHierarchy(tier1c, null, Arrays.asList(tier2c));
        this.mockNodeHierarchy(tier2c, Arrays.asList(tier1c, tier1d), Arrays.asList(tier3c));
        this.mockNodeHierarchy(tier3c, Arrays.asList(tier2c), null);

        this.mockNodeHierarchy(tier1d, null, Arrays.asList(tier2c, tier2d));
        this.mockNodeHierarchy(tier2d, Arrays.asList(tier1d), null);

        Collection<EntityNode> nodes = Arrays.asList(tier1a, tier1b, tier1c, tier1d, tier2a, tier2b, tier2c,
            tier2d, tier3a, tier3b, tier3c);

        for (EntityNode node : nodes) {
            assertTrue(mapper.addNode(node));
        }

        return Arrays.asList(tier1a, tier1b, tier1c, tier1d);
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

        EntityNode node = this.mockEntityNode(owner, Product.class, "node-1");
        doReturn(true).when(node).isRootNode();
        doReturn(Collections.emptyList()).when(node).getChildrenNodes();

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

        EntityNode node = this.mockEntityNode(owner, Product.class, "node-1");
        doReturn(true).when(node).isRootNode();
        doReturn(Collections.emptyList()).when(node).getChildrenNodes();

        doAnswer(iom -> {
            EntityNode receivedNode = (EntityNode) iom.getArguments()[2];

            if (receivedNode != null) {
                doReturn(NodeState.UNCHANGED).when(receivedNode).getNodeState();
            }

            return null;
        }).when(visitor1).processNode(any(NodeProcessor.class), any(NodeMapper.class), any(EntityNode.class));

        mapper.addNode(node);

        processor.setNodeMapper(mapper)
            .addVisitor(visitor1)
            .addVisitor(visitor2);

        processor.processNodes();

        verify(visitor1, times(1)).processNode(eq(processor), eq(mapper), eq(node));
        verify(visitor2, never())
            .processNode(any(NodeProcessor.class), any(NodeMapper.class), any(EntityNode.class));
    }

    private int validateNodeProcessingOrder(List<EntityNode> processOrder, EntityNode node) {
        int nodeIndex = processOrder.indexOf(node);
        int lastIndex = processOrder.lastIndexOf(node);

        // Ensure the node was actually processed, and only handed to our visitor once.
        assertNotEquals(-1, nodeIndex);
        assertEquals(nodeIndex, lastIndex);

        for (EntityNode child : (Collection<EntityNode>) node.getChildrenNodes()) {
            // Ensure the child was processed properly
            int childIndex = this.validateNodeProcessingOrder(processOrder, child);

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
            EntityNode node = (EntityNode) iom.getArguments()[2];

            if (node != null) {
                doReturn(NodeState.UNCHANGED).when(node).getNodeState();
                processOrder.add(node);
            }

            return null;
        }).when(visitor).processNode(any(NodeProcessor.class), any(NodeMapper.class), any(EntityNode.class));

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

        for (EntityNode parent : (Collection<EntityNode>) node.getParentNodes()) {
            // Ensure the parent was processed properly
            int parentIndex = this.validateNodeProcessingOrder(pruneOrder, parent);

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
                doReturn(NodeState.DELETED).when(node).getNodeState();
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
    public void testProcessNodesReturnsAccurateRefreshResult() {
        Owner owner = TestUtil.createOwner();

        NodeProcessor processor = new NodeProcessor();
        NodeMapper mapper = new NodeMapper();
        NodeVisitor productVisitor = this.mockNodeVisitor(Product.class);
        NodeVisitor contentVisitor = this.mockNodeVisitor(Content.class);

        List<Class> nodeClasses = Arrays.asList(Product.class, Content.class);
        int nodesPerCombo = 3;
        int nodeCount = 0;

        for (Class cls : nodeClasses) {
            for (NodeState state : NodeState.values()) {
                for (int i = 0; i < nodesPerCombo; ++i) {
                    EntityNode node = this.mockEntityNode(owner, cls, "node-" + ++nodeCount);
                    doReturn(state).when(node).getNodeState();

                    assertTrue(mapper.addNode(node));
                }
            }
        }

        processor.setNodeMapper(mapper)
            .addVisitor(productVisitor)
            .addVisitor(contentVisitor);

        RefreshResult result = processor.processNodes();

        assertNotNull(result);

        for (Class cls : nodeClasses) {
            for (NodeState state : NodeState.values()) {
                Map entities;

                switch (state) {
                    case CREATED:
                        entities = result.getEntities(cls, EntityState.CREATED);
                        assertNotNull(entities);
                        assertEquals(nodesPerCombo, entities.size());
                        break;

                    case UPDATED:
                        entities = result.getEntities(cls, EntityState.UPDATED);
                        assertNotNull(entities);
                        assertEquals(nodesPerCombo, entities.size());
                        break;

                    case UNCHANGED:
                        entities = result.getEntities(cls, EntityState.UNCHANGED);
                        assertNotNull(entities);
                        assertEquals(nodesPerCombo, entities.size());
                        break;

                    case DELETED:
                        entities = result.getEntities(cls, EntityState.DELETED);
                        assertNotNull(entities);
                        assertEquals(nodesPerCombo, entities.size());
                        break;

                    case SKIPPED:

                    default:
                        // Intentionally left empty
                }
            }
        }
    }

}
