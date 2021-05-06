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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.controller.refresher.mappers.EntityMapper;
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.mappers.ProductMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ServiceAdapterModel;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;



/**
 * Test suite for the NodeFactory class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodeFactoryTest {

    private EntityNode mockEntityNode(Owner owner, Class<? extends AbstractHibernateObject> cls, String id) {
        EntityNode node = mock(EntityNode.class);

        AbstractHibernateObject existing = mock(cls);
        AbstractHibernateObject merged = mock(cls);
        ServiceAdapterModel imported = mock(ServiceAdapterModel.class);

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

    private NodeBuilder mockNodeBuilder(Class cls) {
        NodeBuilder builder = mock(NodeBuilder.class);

        doReturn(cls).when(builder).getEntityClass();

        doAnswer(iom -> {
            Owner owner = (Owner) iom.getArguments()[2];
            String id = (String) iom.getArguments()[3];

            return mockEntityNode(owner, cls, id);
        }).when(builder).buildNode(Mockito.any(NodeFactory.class), Mockito.any(EntityMapper.class),
            Mockito.any(Owner.class), anyString());

        return builder;
    }

    @Test
    public void testAddBuilder() {
        NodeBuilder builder = mock(NodeBuilder.class);

        NodeFactory factory = new NodeFactory();
        NodeFactory output = factory.addBuilder(builder);

        assertNotNull(output);
        assertEquals(factory, output);
    }

    @Test
    public void testAddBuilderRejectsNullBuilders() {
        NodeFactory factory = new NodeFactory();
        assertThrows(IllegalArgumentException.class, () -> factory.addBuilder(null));
    }

    @Test
    public void testSetNodeMapper() {
        NodeMapper mapper = mock(NodeMapper.class);

        NodeFactory factory = new NodeFactory();
        NodeFactory output = factory.setNodeMapper(mapper);

        assertNotNull(output);
        assertEquals(factory, output);
    }

    @Test
    public void testSetNodeMapperRejectsNullMappers() {
        NodeFactory factory = new NodeFactory();
        assertThrows(IllegalArgumentException.class, () -> factory.setNodeMapper(null));
    }

    @Test
    public void testBuildNode() {
        Owner owner = TestUtil.createOwner();
        Class cls = Product.class;
        String id = "test_id";

        NodeBuilder builder = this.mockNodeBuilder(cls);
        NodeMapper nodeMapper = new NodeMapper();
        EntityMapper entityMapper = new ProductMapper();

        NodeFactory factory = new NodeFactory()
            .setNodeMapper(nodeMapper)
            .addMapper(entityMapper)
            .addBuilder(builder);

        EntityNode output = factory.buildNode(owner, cls, id);

        // Verify the node isn't null and came from our builder
        assertNotNull(output);
        verify(builder, times(1)).buildNode(eq(factory), eq(entityMapper), eq(owner), eq(id));

        // Verify that it was persisted in the node mapper
        EntityNode mapped = nodeMapper.getNode(cls, id);

        assertNotNull(mapped);
        assertEquals(output, mapped);
    }

    @Test
    public void testBuildNodeUsesMapperCache() {
        Owner owner = TestUtil.createOwner();
        Class cls = Product.class;
        String id = "test_id";

        NodeBuilder builder = this.mockNodeBuilder(cls);
        EntityNode node = this.mockEntityNode(owner, cls, id);

        NodeMapper mapper = new NodeMapper();
        mapper.addNode(node);

        NodeFactory factory = new NodeFactory()
            .setNodeMapper(mapper)
            .addBuilder(builder);

        EntityNode output = factory.buildNode(owner, cls, id);

        // Verify the node isn't null and came from our mapper
        assertNotNull(output);
        assertSame(node, output);

        verify(builder, never()).buildNode(eq(factory), Mockito.any(EntityMapper.class), eq(owner), eq(id));
    }

    @Test
    public void testBuildNodeRequiresMapper() {
        Owner owner = TestUtil.createOwner();
        Class cls = Product.class;
        String id = "test_id";

        NodeFactory factory = new NodeFactory();
        assertThrows(IllegalStateException.class, () -> factory.buildNode(owner, cls, id));
    }

    @Test
    public void testBuildNodeRequiresBuilder() {
        Owner owner = TestUtil.createOwner();
        Class cls = Product.class;
        String id = "test_id";

        NodeMapper mapper = mock(NodeMapper.class);

        NodeFactory factory = new NodeFactory()
            .setNodeMapper(mapper);

        assertThrows(IllegalStateException.class, () -> factory.buildNode(owner, cls, id));
    }

    @Test
    public void testBuildNodeRequiresBuilderForEntityClass() {
        Owner owner = TestUtil.createOwner();
        Class cls = Product.class;
        String id = "test_id";

        NodeMapper mapper = mock(NodeMapper.class);
        NodeBuilder builder = mock(NodeBuilder.class);

        doReturn(Content.class).when(builder).getEntityClass();

        NodeFactory factory = new NodeFactory()
            .setNodeMapper(mapper)
            .addBuilder(builder);

        assertThrows(IllegalStateException.class, () -> factory.buildNode(owner, cls, id));
        verify(builder, never()).buildNode(Mockito.any(NodeFactory.class), Mockito.any(EntityMapper.class),
            Mockito.any(Owner.class), anyString());
    }

    @Test
    public void testBuildNodeRequiresBuildersToReturnValidNode() {
        Owner owner = TestUtil.createOwner();
        Class cls = Product.class;
        String id = "test_id";

        NodeMapper nodeMapper = mock(NodeMapper.class);
        EntityMapper entityMapper = new ProductMapper();
        NodeBuilder builder = mock(NodeBuilder.class);

        doReturn(Product.class).when(builder).getEntityClass();

        NodeFactory factory = new NodeFactory()
            .setNodeMapper(nodeMapper)
            .addMapper(entityMapper)
            .addBuilder(builder);

        assertThrows(IllegalStateException.class, () -> factory.buildNode(owner, cls, id));
        verify(builder, times(1)).buildNode(eq(factory), eq(entityMapper), eq(owner), eq(id));
    }

}
