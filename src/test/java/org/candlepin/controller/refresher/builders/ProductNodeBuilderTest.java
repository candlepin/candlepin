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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.controller.refresher.mappers.ProductMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.ServiceAdapterModel;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.stream.Collectors;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProductNodeBuilderTest {

    private NodeFactory mockNodeFactory;
    private ProductMapper productMapper;

    @BeforeEach
    public void init() throws Exception {
        this.mockNodeFactory = mock(NodeFactory.class);
        this.productMapper = new ProductMapper();
    }

    private ProductNodeBuilder buildNodeBuilder() {
        return new ProductNodeBuilder();
    }

    private EntityNode mockEntityNode(Owner owner, Class cls, String id,
        AbstractHibernateObject existingEntity, ServiceAdapterModel importedEntity) {

        EntityNode node = mock(EntityNode.class);

        doReturn(owner).when(node).getOwner();
        doReturn(cls).when(node).getEntityClass();
        doReturn(id).when(node).getEntityId();

        doReturn(existingEntity).when(node).getExistingEntity();
        doReturn(importedEntity).when(node).getImportedEntity();

        doReturn(node).when(this.mockNodeFactory).buildNode(eq(owner), eq(cls), eq(id));

        return node;
    }

    @Test
    public void testGetEntityClass() {
        ProductNodeBuilder builder = this.buildNodeBuilder();

        Class output = builder.getEntityClass();

        assertNotNull(output);
        assertEquals(Product.class, output);
    }

    @Test
    public void testBuildNodeForCreation() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        ProductInfo imported = TestUtil.createProduct(id, "imported");

        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();
        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
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

        // The node should not have any flags set
        assertNull(output.getNodeState());

        // Product does not have any children, and we do not have parents in this context
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
        Product existing = TestUtil.createProduct(id, "existing");
        ProductInfo imported = TestUtil.createProduct(id, "imported");

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();
        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
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

        // The node should not have any flags set
        assertNull(output.getNodeState());

        // Product does not have any children, and we do not have parents in this context
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
        Product existing = TestUtil.createProduct(id, "existing");
        ProductInfo imported = TestUtil.createProduct(id, "imported");

        this.productMapper.addExistingEntity(existing);

        ProductNodeBuilder builder = this.buildNodeBuilder();
        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
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

        // The node should not have any flags set
        assertNull(output.getNodeState());

        // Product does not have any children, and we do not have parents in this context
        assertNotNull(output.getParentNodes());
        assertEquals(0, output.getParentNodes().count());

        assertNotNull(output.getChildrenNodes());
        assertEquals(0, output.getChildrenNodes().count());

        // Its pseudo-state getters should match our expectations
        assertTrue(output.isRootNode());
        assertTrue(output.isLeafNode());
    }

    @Test
    public void testBuildNodeForExistingEntityWithContent() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Content content1 = TestUtil.createContent("c1");
        Content content2 = TestUtil.createContent("c2");
        Content content3 = TestUtil.createContent("c3");

        existing.addContent(content1, true);
        existing.addContent(content2, true);
        existing.addContent(content3, true);

        EntityNode cnode1 = this.mockEntityNode(owner, Content.class, content1.getId(), content1, null);
        EntityNode cnode2 = this.mockEntityNode(owner, Content.class, content2.getId(), content2, null);
        EntityNode cnode3 = this.mockEntityNode(owner, Content.class, content3.getId(), content3, null);

        this.productMapper.addExistingEntity(existing);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(0, output.getParentNodes().count());
        assertEquals(3, childrenNodes.size());
        assertThat(childrenNodes, hasItems(cnode1, cnode2, cnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Content.class), anyString());
    }

    @Test
    public void testBuildNodeForExistingEntityWithProvidedProducts() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Product provided1 = TestUtil.createProduct("provided1");
        Product provided2 = TestUtil.createProduct("provided2");
        Product provided3 = TestUtil.createProduct("provided3");

        existing.addProvidedProduct(provided1);
        existing.addProvidedProduct(provided2);
        existing.addProvidedProduct(provided3);

        EntityNode pnode1 = this.mockEntityNode(owner, Product.class, provided1.getId(), provided1, null);
        EntityNode pnode2 = this.mockEntityNode(owner, Product.class, provided2.getId(), provided2, null);
        EntityNode pnode3 = this.mockEntityNode(owner, Product.class, provided3.getId(), provided3, null);

        this.productMapper.addExistingEntity(existing);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(0, output.getParentNodes().count());
        assertEquals(3, childrenNodes.size());
        assertThat(childrenNodes, hasItems(pnode1, pnode2, pnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Product.class), anyString());
    }

    @Test
    public void testBuildNodeForExistingEntityWithProvidedProductsAndContent() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Product provided1 = TestUtil.createProduct("provided1");
        Product provided2 = TestUtil.createProduct("provided2");
        Product provided3 = TestUtil.createProduct("provided3");
        Content content1 = TestUtil.createContent("c1");
        Content content2 = TestUtil.createContent("c2");
        Content content3 = TestUtil.createContent("c3");

        existing.addProvidedProduct(provided1);
        existing.addProvidedProduct(provided2);
        existing.addProvidedProduct(provided3);
        existing.addContent(content1, true);
        existing.addContent(content2, true);
        existing.addContent(content3, true);

        EntityNode pnode1 = this.mockEntityNode(owner, Product.class, provided1.getId(), provided1, null);
        EntityNode pnode2 = this.mockEntityNode(owner, Product.class, provided2.getId(), provided2, null);
        EntityNode pnode3 = this.mockEntityNode(owner, Product.class, provided3.getId(), provided3, null);
        EntityNode cnode1 = this.mockEntityNode(owner, Content.class, content1.getId(), content1, null);
        EntityNode cnode2 = this.mockEntityNode(owner, Content.class, content2.getId(), content2, null);
        EntityNode cnode3 = this.mockEntityNode(owner, Content.class, content3.getId(), content3, null);

        this.productMapper.addExistingEntity(existing);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(0, output.getParentNodes().count());
        assertEquals(6, childrenNodes.size());
        assertThat(childrenNodes, hasItems(pnode1, pnode2, pnode3, cnode1, cnode2, cnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Product.class), anyString());
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Content.class), anyString());
    }

    @Test
    public void testBuildNodeForImportedEntityWithContent() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product imported = TestUtil.createProduct(id, "imported");
        Content content1 = TestUtil.createContent("c1");
        Content content2 = TestUtil.createContent("c2");
        Content content3 = TestUtil.createContent("c3");

        imported.addContent(content1, true);
        imported.addContent(content2, true);
        imported.addContent(content3, true);

        EntityNode cnode1 = this.mockEntityNode(owner, Content.class, content1.getId(), content1, null);
        EntityNode cnode2 = this.mockEntityNode(owner, Content.class, content2.getId(), content2, null);
        EntityNode cnode3 = this.mockEntityNode(owner, Content.class, content3.getId(), content3, null);

        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(0, output.getParentNodes().count());
        assertEquals(3, childrenNodes.size());
        assertThat(childrenNodes, hasItems(cnode1, cnode2, cnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Content.class), anyString());
    }

    @Test
    public void testBuildNodeForImportedEntityWithProvidedProducts() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product imported = TestUtil.createProduct(id, "imported");
        Product provided1 = TestUtil.createProduct("provided1");
        Product provided2 = TestUtil.createProduct("provided2");
        Product provided3 = TestUtil.createProduct("provided3");

        imported.addProvidedProduct(provided1);
        imported.addProvidedProduct(provided2);
        imported.addProvidedProduct(provided3);

        EntityNode pnode1 = this.mockEntityNode(owner, Product.class, provided1.getId(), provided1, null);
        EntityNode pnode2 = this.mockEntityNode(owner, Product.class, provided2.getId(), provided2, null);
        EntityNode pnode3 = this.mockEntityNode(owner, Product.class, provided3.getId(), provided3, null);

        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(0, output.getParentNodes().count());
        assertEquals(3, childrenNodes.size());
        assertThat(childrenNodes, hasItems(pnode1, pnode2, pnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Product.class), anyString());
    }

    @Test
    public void testBuildNodeForImportedEntityWithProvidedProductsAndContent() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product imported = TestUtil.createProduct(id, "imported");
        Product provided1 = TestUtil.createProduct("provided1");
        Product provided2 = TestUtil.createProduct("provided2");
        Product provided3 = TestUtil.createProduct("provided3");
        Content content1 = TestUtil.createContent("c1");
        Content content2 = TestUtil.createContent("c2");
        Content content3 = TestUtil.createContent("c3");

        imported.addProvidedProduct(provided1);
        imported.addProvidedProduct(provided2);
        imported.addProvidedProduct(provided3);
        imported.addContent(content1, true);
        imported.addContent(content2, true);
        imported.addContent(content3, true);

        EntityNode pnode1 = this.mockEntityNode(owner, Product.class, provided1.getId(), provided1, null);
        EntityNode pnode2 = this.mockEntityNode(owner, Product.class, provided2.getId(), provided2, null);
        EntityNode pnode3 = this.mockEntityNode(owner, Product.class, provided3.getId(), provided3, null);
        EntityNode cnode1 = this.mockEntityNode(owner, Content.class, content1.getId(), content1, null);
        EntityNode cnode2 = this.mockEntityNode(owner, Content.class, content2.getId(), content2, null);
        EntityNode cnode3 = this.mockEntityNode(owner, Content.class, content3.getId(), content3, null);

        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(0, output.getParentNodes().count());
        assertEquals(6, childrenNodes.size());
        assertThat(childrenNodes, hasItems(pnode1, pnode2, pnode3, cnode1, cnode2, cnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Product.class), anyString());
        verify(this.mockNodeFactory, times(3)).buildNode(eq(owner), eq(Content.class), anyString());
    }

    @Test
    public void testBuildNodeForUpdatedEntityWithContent() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Product imported = TestUtil.createProduct(id, "imported");
        Content content1 = TestUtil.createContent("c1");
        Content content2 = TestUtil.createContent("c2");
        Content content3 = TestUtil.createContent("c3");

        existing.addContent(content1, true);
        imported.addContent(content2, true);
        imported.addContent(content3, true);

        EntityNode cnode1 = this.mockEntityNode(owner, Content.class, content1.getId(), content1, null);
        EntityNode cnode2 = this.mockEntityNode(owner, Content.class, content2.getId(), content2, null);
        EntityNode cnode3 = this.mockEntityNode(owner, Content.class, content3.getId(), content3, null);

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        assertEquals(0, output.getParentNodes().count());

        // With both an existing and imported entity, we expect the children on the imported entity
        // to take priority over those on the existing entity
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(2, childrenNodes.size());
        assertThat(childrenNodes, hasItems(cnode2, cnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(2)).buildNode(eq(owner), eq(Content.class), anyString());
    }

    @Test
    public void testBuildNodeForUpdatedEntityWithNullContent() {
        // A null value for the provided products collection indicates "no change", which means
        // we should fall back to the existing entity for building children nodes
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Content content1 = TestUtil.createContent("c1");
        Content content2 = TestUtil.createContent("c2");

        ProductInfo imported = spy(TestUtil.createProduct(id, "imported"));

        existing.addContent(content1, true);
        existing.addContent(content2, true);

        // Products can't typically return null, so we'll force it here.
        doReturn(null).when(imported).getProductContent();

        EntityNode cnode1 = this.mockEntityNode(owner, Content.class, content1.getId(), content1, null);
        EntityNode cnode2 = this.mockEntityNode(owner, Content.class, content2.getId(), content2, null);

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        assertEquals(0, output.getParentNodes().count());

        // With both an existing and imported entity, the imported entity typically has priority,
        // but in the case of a "no change" collection, we should fall back to the existing entity's
        // provided product collection instead
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(2, childrenNodes.size());
        assertThat(childrenNodes, hasItems(cnode1, cnode2));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(2)).buildNode(eq(owner), eq(Content.class), anyString());
    }

    @Test
    public void testBuildNodeForUpdatedEntityWithProvidedProducts() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Product imported = TestUtil.createProduct(id, "imported");
        Product provided1 = TestUtil.createProduct("provided1");
        Product provided2 = TestUtil.createProduct("provided2");
        Product provided3 = TestUtil.createProduct("provided3");

        existing.addProvidedProduct(provided1);
        imported.addProvidedProduct(provided2);
        imported.addProvidedProduct(provided3);

        EntityNode pnode1 = this.mockEntityNode(owner, Product.class, provided1.getId(), provided1, null);
        EntityNode pnode2 = this.mockEntityNode(owner, Product.class, provided2.getId(), provided2, null);
        EntityNode pnode3 = this.mockEntityNode(owner, Product.class, provided3.getId(), provided3, null);

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children product nodes were created
        assertEquals(0, output.getParentNodes().count());

        // With both an existing and imported entity, we expect the children on the imported entity
        // to take priority over those on the existing entity
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(2, childrenNodes.size());
        assertThat(childrenNodes, hasItems(pnode2, pnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(2)).buildNode(eq(owner), eq(Product.class), anyString());
    }

    @Test
    public void testBuildNodeForUpdatedEntityWithNullProvidedProducts() {
        // A null value for the provided products collection indicates "no change", which means
        // we should fall back to the existing entity for building children nodes

        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Product provided1 = TestUtil.createProduct("provided1");
        Product provided2 = TestUtil.createProduct("provided2");

        ProductInfo imported = spy(TestUtil.createProduct(id, "imported"));

        existing.addProvidedProduct(provided1);
        existing.addProvidedProduct(provided2);

        // Products can't typically return null, so we'll force it here.
        doReturn(null).when(imported).getProvidedProducts();

        EntityNode pnode1 = this.mockEntityNode(owner, Product.class, provided1.getId(), provided1, null);
        EntityNode pnode2 = this.mockEntityNode(owner, Product.class, provided2.getId(), provided2, null);

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children product nodes were created
        assertEquals(0, output.getParentNodes().count());

        // With both an existing and imported entity, the imported entity typically has priority,
        // but in the case of a "no change" collection, we should fall back to the existing entity's
        // provided product collection instead
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(2, childrenNodes.size());
        assertThat(childrenNodes, hasItems(pnode1, pnode2));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(2)).buildNode(eq(owner), eq(Product.class), anyString());
    }

    @Test
    public void testBuildNodeForUpdatedEntityWithProvidedProductsAndContent() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        Product imported = TestUtil.createProduct(id, "imported");
        Product provided1 = TestUtil.createProduct("provided1");
        Product provided2 = TestUtil.createProduct("provided2");
        Product provided3 = TestUtil.createProduct("provided3");
        Content content1 = TestUtil.createContent("c1");
        Content content2 = TestUtil.createContent("c2");
        Content content3 = TestUtil.createContent("c3");

        existing.addProvidedProduct(provided1);
        imported.addProvidedProduct(provided2);
        imported.addProvidedProduct(provided3);
        existing.addContent(content1, true);
        imported.addContent(content2, true);
        imported.addContent(content3, true);

        EntityNode pnode1 = this.mockEntityNode(owner, Product.class, provided1.getId(), provided1, null);
        EntityNode pnode2 = this.mockEntityNode(owner, Product.class, provided2.getId(), provided2, null);
        EntityNode pnode3 = this.mockEntityNode(owner, Product.class, provided3.getId(), provided3, null);
        EntityNode cnode1 = this.mockEntityNode(owner, Content.class, content1.getId(), content1, null);
        EntityNode cnode2 = this.mockEntityNode(owner, Content.class, content2.getId(), content2, null);
        EntityNode cnode3 = this.mockEntityNode(owner, Content.class, content3.getId(), content3, null);

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        EntityNode<Product, ProductInfo> output = builder.buildNode(this.mockNodeFactory, this.productMapper,
            owner, id);

        assertNotNull(output);
        assertEquals(id, output.getEntityId());
        assertEquals(owner, output.getOwner());

        // The entity class on the node and builder should be the same, non-null value
        assertNotNull(output.getEntityClass());
        assertEquals(builder.getEntityClass(), output.getEntityClass());

        // Check that our children content nodes were created
        List<EntityNode<?, ?>> childrenNodes = output.getChildrenNodes()
            .collect(Collectors.toList());

        assertEquals(0, output.getParentNodes().count());

        // With both an existing and imported entity, we expect the children on the imported entity
        // to take priority over those on the existing entity
        assertEquals(4, childrenNodes.size());
        assertThat(childrenNodes, hasItems(pnode2, pnode3, cnode2, cnode3));

        // Ensure that the children were created using the node factory and not an internal method
        verify(this.mockNodeFactory, times(2)).buildNode(eq(owner), eq(Product.class), anyString());
        verify(this.mockNodeFactory, times(2)).buildNode(eq(owner), eq(Content.class), anyString());
    }

    @Test
    public void testCannotBuildNodeForUnknownId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        ProductInfo imported = TestUtil.createProduct(id, "imported");

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.productMapper, owner, "invalid_id"));
    }

    @Test
    public void testCannotBuildNodeWithEmptyId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        ProductInfo imported = TestUtil.createProduct(id, "imported");

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.productMapper, owner, ""));
    }

    @Test
    public void testCannotBuildNodeWithNullId() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        ProductInfo imported = TestUtil.createProduct(id, "imported");

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalStateException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.productMapper, owner, null));
    }

    @Test
    public void testCannotBuildNodeWithNullOwner() {
        String id = "test_id";

        Owner owner = TestUtil.createOwner();
        Product existing = TestUtil.createProduct(id, "existing");
        ProductInfo imported = TestUtil.createProduct(id, "imported");

        this.productMapper.addExistingEntity(existing);
        this.productMapper.addImportedEntity(imported);

        ProductNodeBuilder builder = this.buildNodeBuilder();

        assertThrows(IllegalArgumentException.class,
            () -> builder.buildNode(this.mockNodeFactory, this.productMapper, null, id));
    }

}
