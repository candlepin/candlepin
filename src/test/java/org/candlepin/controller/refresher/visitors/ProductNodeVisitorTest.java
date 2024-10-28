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
package org.candlepin.controller.refresher.visitors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.candlepin.controller.refresher.nodes.ContentNode;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.controller.refresher.nodes.ProductNode;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;



/**
 * Test suite for the ProductNodeVisitor class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProductNodeVisitorTest extends DatabaseTestFixture {

    public ProductNodeVisitor buildNodeVisitor() {
        return new ProductNodeVisitor(this.productCurator);
    }

    @Test
    public void testGetEntityClassReturnsProperClass() {
        ProductNodeVisitor visitor = this.buildNodeVisitor();
        assertEquals(Product.class, visitor.getEntityClass());
    }

    @Test
    public void testProcessNodeFlagsUnchangedNodesCorrectly() {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product");
        ProductInfo importedEntity = (ProductInfo) existingEntity.clone();

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor();

        visitor.processNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    public static Stream<Arguments> simpleProductDataProvider() {
        List<String> depProductIds = Arrays.asList("a", "b", "c");

        Map<String, String> attributes = Map.of(
            "attrib-1", "value-1",
            "attrib-2", "value-2",
            "attrib-3", "value-3");

        List<Branding> branding = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            branding.add(new Branding("branded_pid-" + i, "branding " + i, "btype"));
        }

        return Stream.of(
            Arguments.of("name", "updated_name"),
            Arguments.of("multiplier", 12345L),
            Arguments.of("dependent_product_ids", depProductIds),
            Arguments.of("attributes", attributes),
            Arguments.of("branding", branding));
    }

    private ProductInfo buildProductInfoMock(String id, String field, Object value) {
        ProductInfo entity = mock(ProductInfo.class);
        doReturn(id).when(entity).getId();

        // Impl note:
        // This is necessary, since the default behavior for mocked methods that return primitive
        // containers is to return a wrapped default primitive value, *NOT* null as one might
        // expect.
        doReturn(null).when(entity).getMultiplier();

        Map<String, Mutator<ProductInfo>> mutators = Map.of(
            "name", (p, v) -> doReturn(v).when(p).getName(),
            "multiplier", (p, v) -> doReturn(v).when(p).getMultiplier(),
            "dependent_product_ids", (p, v) -> doReturn(v).when(p).getDependentProductIds(),
            "attributes", (p, v) -> doReturn(v).when(p).getAttributes(),
            "branding", (p, v) -> doReturn(v).when(p).getBranding());

        if (!mutators.containsKey(field)) {
            throw new IllegalStateException("no mutator for key: " + field);
        }

        mutators.get(field)
            .mutate(entity, value);

        return entity;
    }

    private void updateProductField(Product entity, String field, Object value) {
        Map<String, Mutator<Product>> mutators = Map.of(
            "name", (p, v) -> p.setName((String) v),
            "multiplier", (p, v) -> p.setMultiplier((Long) v),
            "dependent_product_ids", (p, v) -> p.setDependentProductIds((Collection) v),
            "attributes", (p, v) -> p.setAttributes((Map) v),
            "branding", (p, v) -> p.setBranding((Collection) v));

        if (!mutators.containsKey(field)) {
            throw new IllegalStateException("no mutator for key: " + field);
        }

        mutators.get(field)
            .mutate(entity, value);
    }

    @ParameterizedTest
    @MethodSource("simpleProductDataProvider")
    public void testProcessNodeFlagsUpdatedNodeCorrectly(String field, Object value) {
        String id = "test_product-1";

        Owner owner = this.createOwner();
        Product existingEntity = new Product()
            .setId(id);

        ProductInfo importedEntity = this.buildProductInfoMock(id, field, value);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        // Since we've not called into applyChanges yet, we should not have any changes applied
    }

    @Test
    public void testProcessNodeDetectsChildrenUpdatedOnUnchangedNode() {
        Owner owner = this.createOwner();

        Product product = this.createProduct("test_product-1", "test product");
        Content content = this.createContent("test_content-1", "test content");

        EntityNode<Content, ContentInfo> cnode = new ContentNode(owner, content.getId())
            .setExistingEntity(content)
            .setImportedEntity(content)
            .setNodeState(NodeState.UPDATED);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, product.getId())
            .setExistingEntity(product)
            .setImportedEntity(product)
            .addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.CHILDREN_UPDATED, pnode.getNodeState());
    }

    @Test
    public void testProcessNodeDetectsGrandchildrenUpdatedOnUnchangedNode() {
        Owner owner = this.createOwner();

        Product product1 = this.createProduct("test_product-1", "test product");
        Product product2 = this.createProduct("test_product-2", "test child product");
        Content content = this.createContent("test_content-1", "test content");

        EntityNode<Content, ContentInfo> cnode = new ContentNode(owner, content.getId())
            .setExistingEntity(content)
            .setImportedEntity(content)
            .setNodeState(NodeState.UPDATED);

        EntityNode<Product, ProductInfo> pnode2 = new ProductNode(owner, product2.getId())
            .setExistingEntity(product2)
            .setImportedEntity(product2)
            .addChildNode(cnode)
            .setNodeState(NodeState.CHILDREN_UPDATED);

        EntityNode<Product, ProductInfo> pnode1 = new ProductNode(owner, product1.getId())
            .setExistingEntity(product1)
            .setImportedEntity(product1)
            .addChildNode(pnode2);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode1);

        assertEquals(NodeState.CHILDREN_UPDATED, pnode1.getNodeState());
    }

    @ParameterizedTest
    @MethodSource("simpleProductDataProvider")
    public void testProcessNodePrioritizesUpdatedStateOverChildrenUpdated(String field, Object value) {
        Owner owner = this.createOwner();

        Content content = this.createContent("test_content-1", "test content");

        EntityNode<Content, ContentInfo> cnode = new ContentNode(owner, content.getId())
            .setExistingEntity(content)
            .setImportedEntity(content)
            .setNodeState(NodeState.UPDATED);

        Product existingEntity = new Product()
            .setId("test_product-1");

        ProductInfo importedEntity = this.buildProductInfoMock(existingEntity.getId(), field, value);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity)
            .addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        // Even though we have a child that was updated, the expectation is that the local update
        // state is prioritized over the state of any children.
        assertEquals(NodeState.UPDATED, pnode.getNodeState());
    }

    @ParameterizedTest
    @MethodSource("simpleProductDataProvider")
    public void testProcessNodeDoesNotFlagUnchangedNodeForUpdate(String field, Object value) {
        String id = "test_product-1";

        Owner owner = this.createOwner();
        Product existingEntity = new Product()
            .setId(id);

        this.updateProductField(existingEntity, field, value);

        ProductInfo importedEntity = this.buildProductInfoMock(id, field, value);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    @Test
    public void testProcessNodeFlagsCreatedNodeCorrectly() {
        Owner owner = this.createOwner();
        ProductInfo importedEntity = this.createProduct("test_prod-1", "Test Product");

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, importedEntity.getId())
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());
    }

    private void validateEntityField(Product entity, String field, Object expected) {
        Map<String, Accessor<ProductInfo>> accessors = Map.of(
            "name", ProductInfo::getName,
            "multiplier", ProductInfo::getMultiplier,
            "dependent_product_ids", ProductInfo::getDependentProductIds,
            "attributes", ProductInfo::getAttributes,
            "branding", ProductInfo::getBranding);

        assertNotNull(entity);

        if (!accessors.containsKey(field)) {
            throw new IllegalStateException("no accessor for key: " + field);
        }

        Object actual = accessors.get(field)
            .access(entity);

        if (actual instanceof Collection) {
            Collection<Object> expCollection = (Collection<Object>) expected;
            Collection<Object> actCollection = (Collection<Object>) actual;

            assertNotNull(expCollection);
            assertEquals(expCollection.size(), actCollection.size());

            for (Object item : expCollection) {
                assertThat(actCollection, hasItem(item));
            }
        }
        else if (actual instanceof Map) {
            Map<Object, Object> expMap = (Map<Object, Object>) expected;
            Map<Object, Object> actMap = (Map<Object, Object>) actual;

            assertNotNull(expMap);
            assertEquals(expMap.size(), actMap.size());

            for (Map.Entry<Object, Object> entry : expMap.entrySet()) {
                assertThat(actMap, hasEntry(entry.getKey(), entry.getValue()));
            }
        }
        else {
            assertEquals(expected, actual);
        }
    }

    @ParameterizedTest
    @MethodSource("simpleProductDataProvider")
    public void testApplyChangesUpdatesEntity(String field, Object value) {
        String id = "test_product-1";

        Owner owner = this.createOwner();
        Product existingEntity = new Product()
            .setId(id)
            .setName("test product");

        ProductInfo importedEntity = this.buildProductInfoMock(id, field, value);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());
        this.validateEntityField(pnode.getExistingEntity(), field, value);
    }

    @Test
    public void testApplyChangesResolvesContentProperly() {
        String id = "test_product-1";
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct(id, "test product");

        Content content = this.createContent("test_content-1", "test content");

        Product importedEntity = new Product()
            .setId(id);
        importedEntity.addContent(content, true);

        EntityNode<Content, ContentInfo> cnode = new ContentNode(owner, content.getId())
            .setExistingEntity(content) // Faking the creation step with this
            .setImportedEntity(content)
            .setNodeState(NodeState.CREATED);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        pnode.addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        assertNotNull(pnode.getExistingEntity());
        assertTrue(pnode.getExistingEntity().hasContent(content.getId()));
    }

    @Test
    public void testApplyChangesResolvesDerivedProductProperly() {
        String id = "test_product-1";
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct(id, "test product");

        Product derived = this.createProduct("derived_product-1", "derived product");

        ProductInfo importedEntity = new Product()
            .setId(id)
            .setDerivedProduct(derived);

        EntityNode<Product, ProductInfo> cnode = new ProductNode(owner, derived.getId())
            .setExistingEntity(derived) // Faking the creation step with this
            .setImportedEntity(derived)
            .setNodeState(NodeState.CREATED);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        pnode.addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());
        assertEquals(derived.getId(), pnode.getExistingEntity().getDerivedProduct().getId());
    }

    @Test
    public void testApplyChangesResolvesProvidedProductProperly() {
        String id = "test_product-1";
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct(id, "test product");

        Product provided = this.createProduct("provided_product-1", "provided product");

        Product importedEntity = new Product()
            .setId(id);
        importedEntity.addProvidedProduct(provided);

        EntityNode<Product, ProductInfo> cnode = new ProductNode(owner, provided.getId())
            .setExistingEntity(provided) // Faking the creation step with this
            .setImportedEntity(provided)
            .setNodeState(NodeState.CREATED);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        pnode.addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        assertNotNull(pnode.getExistingEntity());
        Collection<Product> providedProducts = pnode.getExistingEntity().getProvidedProducts();

        assertNotNull(providedProducts);
        assertEquals(1, providedProducts.size());
        assertEquals(provided.getId(), providedProducts.iterator().next().getId());
    }

    @Test
    public void testFullCyclePersistsNewEntity() {
        Owner owner = this.createOwner();

        Product imported = new Product()
            .setId("test_product")
            .setName("test product");

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, imported.getId())
            .setImportedEntity(imported);

        assertNull(this.productCurator.getProductById(null, imported.getId()));

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());

        Product createdEntity = pnode.getExistingEntity();
        assertNotNull(createdEntity);
        assertNotNull(createdEntity.getUuid());

        this.productCurator.flush();
        this.productCurator.clear();

        Product created = this.productCurator.getProductById(null, imported.getId());
        assertNotNull(created);
    }

    @Test
    public void testFullCyclePersistsUpdatedEntity() {
        Owner owner = this.createOwner();

        Product existing = this.createProduct("test_product", "product name");

        Product imported = new Product()
            .setId(existing.getId())
            .setName("updated product");

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, imported.getId())
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        Product updatedEntity = pnode.getExistingEntity();
        assertNotNull(updatedEntity);
        assertEquals(imported.getName(), updatedEntity.getName());

        this.productCurator.flush();
        this.productCurator.clear();

        Product fetchedEntity = this.productCurator.getProductById(null, existing.getId());
        assertNotNull(fetchedEntity);
        assertEquals(updatedEntity.getName(), fetchedEntity.getName());
    }

}
