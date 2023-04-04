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
package org.candlepin.controller.refresher.mappers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.controller.refresher.nodes.AbstractNode;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Test suite for the NodeMapper class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodeMapperTest {

    private <E extends AbstractHibernateObject, I extends ServiceAdapterModel> EntityNode<E, I>
        buildEntityNode(Owner owner, Class<E> cls, String id) {

        return new AbstractNode<E, I>(owner, id) {
            @Override
            public Class<E> getEntityClass() {
                return cls;
            }
        };
    }

    @Test
    public void testAddNode() {
        Owner owner = TestUtil.createOwner();
        EntityNode<Product, ProductInfo> node = this.buildEntityNode(owner, Product.class, "test_id");

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
        EntityNode<Product, ProductInfo> node1 = this.buildEntityNode(owner, Product.class, id);
        EntityNode<Content, ContentInfo> node2 = this.buildEntityNode(owner, Content.class, id);

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
        Class<Product> cls = Product.class;
        Owner owner = TestUtil.createOwner();
        EntityNode<Product, ProductInfo> node1 = this.buildEntityNode(owner, cls, "id-1");
        EntityNode<Product, ProductInfo> node2 = this.buildEntityNode(owner, cls, "id-2");

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
        EntityNode<Product, ProductInfo> node1 = this.buildEntityNode(owner, Product.class, "id-1");
        EntityNode<Content, ContentInfo> node2 = this.buildEntityNode(owner, Content.class, "id-2");

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
        EntityNode<Product, ProductInfo> node1 = this.buildEntityNode(owner, Product.class, "id-1");
        EntityNode<Content, ContentInfo> node2 = this.buildEntityNode(owner, Content.class, "id-2");

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
        EntityNode<Product, ProductInfo> node1 = this.buildEntityNode(owner, Product.class, "id-1");
        EntityNode<Content, ContentInfo> node2 = this.buildEntityNode(owner, Content.class, "id-2");

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
        EntityNode<Product, ProductInfo> node1 = this.buildEntityNode(owner, Product.class, "id-1");
        EntityNode<Content, ContentInfo> node2 = this.buildEntityNode(owner, Content.class, "id-2");

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
    private Collection<EntityNode> buildNodeTrees(NodeMapper mapper) {
        Owner owner = TestUtil.createOwner();

        EntityNode<Product, ProductInfo> tier1a = this.buildEntityNode(owner, Product.class, "tier1a");
        EntityNode<Product, ProductInfo> tier1b = this.buildEntityNode(owner, Product.class, "tier1b");

        EntityNode<Product, ProductInfo> tier2a = this.buildEntityNode(owner, Product.class, "tier2a");
        EntityNode<Product, ProductInfo> tier2b = this.buildEntityNode(owner, Product.class, "tier2b");
        EntityNode<Product, ProductInfo> tier2c = this.buildEntityNode(owner, Product.class, "tier2c");

        EntityNode<Product, ProductInfo> tier3a = this.buildEntityNode(owner, Product.class, "tier3a");
        EntityNode<Product, ProductInfo> tier3b = this.buildEntityNode(owner, Product.class, "tier3b");
        EntityNode<Product, ProductInfo> tier3c = this.buildEntityNode(owner, Product.class, "tier3c");

        tier1a.addChildNode(tier2a)
            .addChildNode(tier2b);

        tier1b.addChildNode(tier2c);

        tier2a.addChildNode(tier3a)
            .addChildNode(tier3b);

        tier2c.addChildNode(tier3c);

        List<EntityNode> nodes = List.of(tier1a, tier1b, tier2a, tier2b, tier2c, tier3a, tier3b, tier3c);
        for (EntityNode node : nodes) {
            assertTrue(mapper.addNode(node));
        }

        return nodes;
    }

    @Test
    public void testGetNodeIterator() {
        NodeMapper mapper = new NodeMapper();
        Collection<EntityNode> expected = this.buildNodeTrees(mapper);

        Stream<EntityNode<?, ?>> stream = mapper.getNodeStream();
        assertNotNull(stream);

        // Collect all the values in the iterator into an array so we can verify the
        // count and values returned
        List<EntityNode> collected = stream.collect(Collectors.toList());

        // Verify the collected values
        assertEquals(expected.size(), collected.size());
        for (EntityNode node : expected) {
            assertThat(collected, hasItem(node));
        }
    }

    @Test
    public void testGetRootNodeStream() {
        NodeMapper mapper = new NodeMapper();
        List<EntityNode> expected = this.buildNodeTrees(mapper).stream()
            .filter(EntityNode::isRootNode)
            .collect(Collectors.toList());

        Stream<EntityNode<?, ?>> stream = mapper.getRootNodeStream();
        assertNotNull(stream);

        // Collect all the values in the iterator into an array so we can verify the
        // count and values returned
        List<EntityNode> collected = stream.collect(Collectors.toList());

        // Verify the collected values
        assertEquals(expected.size(), collected.size());
        for (EntityNode node : expected) {
            assertThat(collected, hasItem(node));
        }
    }

    @Test
    public void testGetLeafNodeStream() {
        NodeMapper mapper = new NodeMapper();
        List<EntityNode> expected = this.buildNodeTrees(mapper).stream()
            .filter(EntityNode::isLeafNode)
            .collect(Collectors.toList());

        Stream<EntityNode<?, ?>> stream = mapper.getLeafNodeStream();
        assertNotNull(stream);

        // Collect all the values in the iterator into an array so we can verify the
        // count and values returned
        List<EntityNode> collected = stream.collect(Collectors.toList());

        // Verify the collected values
        assertEquals(expected.size(), collected.size());
        for (EntityNode node : expected) {
            assertThat(collected, hasItem(node));
        }
    }
}
