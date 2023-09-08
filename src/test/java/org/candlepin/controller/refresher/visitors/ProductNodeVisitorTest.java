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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.Product;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    private static final int DEFAULT_ORPHANED_ENTITY_GRACE_PERIOD = 30;

    public ProductNodeVisitor buildNodeVisitor() {
        return this.buildNodeVisitor(DEFAULT_ORPHANED_ENTITY_GRACE_PERIOD);
    }

    public ProductNodeVisitor buildNodeVisitor(int orphanEntityGracePeriod) {
        return new ProductNodeVisitor(this.productCurator, this.ownerProductCurator, orphanEntityGracePeriod);
    }

    @Test
    public void testGetEntityClassReturnsProperClass() {
        ProductNodeVisitor visitor = this.buildNodeVisitor();
        assertEquals(Product.class, visitor.getEntityClass());
    }

    @Test
    public void testProcessNodeFlagsUnchangedNodesCorrectly() {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner);
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
            Branding elem = new Branding()
                .setId("branding-" + i)
                .setName("branding " + i)
                .setProductId("branded_pid-" + i)
                .setType("btype");

            branding.add(elem);
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

    @ParameterizedTest(name = "{displayName} {index}: {0}")
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

    @ParameterizedTest(name = "{displayName} {index}: {0}")
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
        ProductInfo importedEntity = this.createProduct("test_prod-1", "Test Product", owner);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, importedEntity.getId())
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());
    }

    // TODO: Temporarily disabled until DELETE is supported properly. Not to be done in the prototype

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @ValueSource(ints = { -1, 0, 30 })
    // public void testPruneNodeOmitsActiveRoot(int orphanedEntityGracePeriod) {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(true);
    //     ProductInfo importedEntity = (ProductInfo) existingEntity.clone();

    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity)
    //         .setImportedEntity(importedEntity);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
    //     visitor.processNode(pnode);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @ValueSource(ints = { -1, 0, 30 })
    // public void testPruneNodeNeverDeletesCustomProducts(int orphanedEntityGracePeriod) {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(false);

    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
    //     visitor.processNode(pnode);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.UNCHANGED, pnode.getNodeState());

    //     this.productCurator.flush();
    //     this.productCurator.clear();

    //     OwnerProduct op = this.ownerProductCurator.getOwnerProduct(owner.getId(), existingEntity.getId());
    //     assertNotNull(op);
    //     assertNull(op.getOrphanedDate());
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @ValueSource(ints = { 1, 5, 30 })
    // public void testPruneNodeDoesntFlagNewlyOrphanedEntitiesWithinGracePeriod(int orphanedEntityGracePeriod) {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(true);

    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
    //     visitor.processNode(pnode);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @ValueSource(ints = { 1, 5, 30 })
    // public void testPruneNodeDoesntFlagOrphanedEntitiesWithinGracePeriod(int orphanedEntityGracePeriod) {
    //     Owner owner = this.createOwner();
    //     Product existing = new Product()
    //         .setId("test_product")
    //         .setName("test product")
    //         .setLocked(true);

    //     Instant orphanedDate = Instant.now()
    //         .minus(orphanedEntityGracePeriod / 2, ChronoUnit.DAYS);

    //     OwnerProduct op = new OwnerProduct()
    //         .setOwner(owner)
    //         .setProduct(existing)
    //         .setOrphanedDate(orphanedDate);

    //     this.productCurator.create(existing);
    //     this.ownerProductCurator.create(op);

    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
    //         .setExistingEntity(existing);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
    //     visitor.processNode(pnode);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @ValueSource(ints = { 1, 5, 30 })
    // public void testPruneNodeFlagsOrphanedEntitiesAfterGracePeriod(int orphanedEntityGracePeriod) {
    //     Owner owner = this.createOwner();
    //     Product existing = new Product()
    //         .setId("test_product")
    //         .setName("test product")
    //         .setLocked(true);

    //     Instant orphanedDate = Instant.now()
    //         .minus(orphanedEntityGracePeriod + 5, ChronoUnit.DAYS);

    //     OwnerProduct op = new OwnerProduct()
    //         .setOwner(owner)
    //         .setProduct(existing)
    //         .setOrphanedDate(orphanedDate);

    //     this.productCurator.create(existing);
    //     this.ownerProductCurator.create(op);

    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
    //         .setExistingEntity(existing);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
    //     visitor.processNode(pnode);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.DELETED, pnode.getNodeState());
    // }

    // @Test
    // public void testPruneNodeFlagsUnusedNodeForDeletionWithNoGracePeriod() {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(true);

    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(0);
    //     visitor.processNode(pnode);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.DELETED, pnode.getNodeState());
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @ValueSource(ints = { -1, -5, -5000 })
    // public void testPruneNodeNeverFlagsNodesWithInfiniteGracePeriod(int orphanedEntityGracePeriod) {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(true);

    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
    //     visitor.processNode(pnode);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    // }

    // @Test
    // public void testPruneNodeFlagsLeafWithDeletedParentsForDeletion() {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(true);
    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity);

    //     Product existingParent = this.createProduct("test_prod-2", "Test Product", owner);
    //     EntityNode<Product, ProductInfo> parentNode = new ProductNode(owner, existingParent.getId())
    //         .setExistingEntity(existingParent)
    //         .setNodeState(NodeState.DELETED);

    //     pnode.addParentNode(parentNode);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(0);
    //     visitor.pruneNode(pnode);

    //     assertEquals(NodeState.DELETED, pnode.getNodeState());
    // }

    // @Test
    // public void testPruneNodeOmitsLeafWithUndeletedParents() {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(true);
    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity);

    //     Product existingParent = this.createProduct("test_prod-2", "Test Product", owner);
    //     EntityNode<Product, ProductInfo> parentNode = new ProductNode(owner, existingParent.getId())
    //         .setExistingEntity(existingParent)
    //         .setNodeState(NodeState.UPDATED);

    //     pnode.addParentNode(parentNode);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor(0);
    //     visitor.pruneNode(pnode);

    //     assertNull(pnode.getNodeState());
    // }

    // @Test
    // public void testPruneNodeOmitsLeafWithMixedParents() {
    //     Owner owner = this.createOwner();
    //     Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
    //         .setLocked(true);
    //     EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
    //         .setExistingEntity(existingEntity);

    //     Product existingParent1 = this.createProduct("test_prod-2", "Test Product", owner);
    //     EntityNode<Product, ProductInfo> parentNode1 = new ProductNode(owner, existingParent1.getId())
    //         .setExistingEntity(existingParent1)
    //         .setNodeState(NodeState.UPDATED);

    //     Product existingParent2 = this.createProduct("test_prod-3", "Test Product", owner);
    //     EntityNode<Product, ProductInfo> parentNode2 = new ProductNode(owner, existingParent2.getId())
    //         .setExistingEntity(existingParent2)
    //         .setNodeState(NodeState.DELETED);

    //     pnode.addParentNode(parentNode1);
    //     pnode.addParentNode(parentNode2);

    //     assertNull(pnode.getNodeState());

    //     ProductNodeVisitor visitor = this.buildNodeVisitor();
    //     visitor.pruneNode(pnode);

    //     assertNull(pnode.getNodeState());
    // }

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

    @ParameterizedTest(name = "{displayName} {index}: {0}")
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
        Product existingEntity = this.createProduct(id, "test product", owner);

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
        Product existingEntity = this.createProduct(id, "test product", owner);

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
        Product existingEntity = this.createProduct(id, "test product", owner);

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

        assertNull(this.productCurator.getProductById(imported.getId()));

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.CREATED, pnode.getNodeState());

        Product createdEntity = pnode.getExistingEntity();
        assertNotNull(createdEntity);
        assertNotNull(createdEntity.getUuid());

        this.productCurator.flush();
        this.productCurator.clear();

        Product created = this.productCurator.getProductById(imported.getId());
        assertNotNull(created);
    }

    @Test
    public void testFullCyclePersistsUpdatedEntity() {
        Owner owner = this.createOwner();

        Product existing = this.createProduct("test_product", "product name", owner);
        existing.setLocked(true);

        Product imported = new Product()
            .setId(existing.getId())
            .setName("updated product");

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, imported.getId())
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        Product updatedEntity = pnode.getExistingEntity();
        assertNotNull(updatedEntity);
        assertEquals(imported.getName(), updatedEntity.getName());

        this.productCurator.flush();
        this.productCurator.clear();

        Product fetchedEntity = this.productCurator.getProductById(existing.getId());
        assertNotNull(fetchedEntity);
        assertEquals(updatedEntity.getName(), fetchedEntity.getName());
    }

}
