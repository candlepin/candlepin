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

    @Test
    public void testProcessNodeFlagsUnchangedNodeAsUpdatedWithUpdatedChildren() {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner);
        ProductInfo importedEntity = (ProductInfo) existingEntity.clone();
        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        Product existingChild = this.createProduct("test_prod-2", "Test Product", owner);
        EntityNode<Product, ProductInfo> child = new ProductNode(owner, existingChild.getId())
            .setExistingEntity(existingChild)
            .setNodeState(NodeState.UPDATED);

        pnode.addChildNode(child);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor();

        visitor.processNode(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());
    }

    public static Stream<Arguments> simpleProductDataProvider() {

        List<String> depProductIds = Arrays.asList("a", "b", "c");

        Map<String, String> attributes = Map.of(
            "attrib-1", "value-1",
            "attrib-2", "value-2",
            "attrib-3", "value-3");

        List<Branding> branding = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            Branding elem = new Branding();
            elem.setId("branding-" + i);
            elem.setName("Branding " + i);

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

    private void validateMergedField(Product mergedEntity, String field, Object expected) {
        Map<String, Accessor<ProductInfo>> accessors = Map.of(
            "name", ProductInfo::getName,
            "multiplier", ProductInfo::getMultiplier,
            "dependent_product_ids", ProductInfo::getDependentProductIds,
            "attributes", ProductInfo::getAttributes,
            "branding", ProductInfo::getBranding);

        if (!accessors.containsKey(field)) {
            throw new IllegalStateException("no accessor for key: " + field);
        }

        Object actual = accessors.get(field)
            .access(mergedEntity);

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
        assertNotNull(pnode.getMergedEntity());
        this.validateMergedField(pnode.getMergedEntity(), field, value);
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
    public void testProcessNodeResolvesContentProperly() {
        String id = "test_product-1";
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct(id, "test product", owner);

        Content content = new Content()
            .setId("test_content-1")
            .setName("test content");

        Product importedEntity = new Product()
            .setId(id);
        importedEntity.addContent(content, true);

        EntityNode<Content, ContentInfo> cnode = new ContentNode(owner, content.getId())
            .setImportedEntity(content)
            .setMergedEntity(content)
            .setNodeState(NodeState.CREATED);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        pnode.addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        assertNotNull(pnode.getMergedEntity());
        assertFalse(existingEntity.hasContent(content.getId()));
        assertTrue(pnode.getMergedEntity().hasContent(content.getId()));
    }

    @Test
    public void testProcessNodeResolvesDerivedProductProperly() {
        String id = "test_product-1";
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct(id, "test product", owner);

        Product derived = new Product()
            .setId("derived_product-1")
            .setName("derived product");

        ProductInfo importedEntity = new Product()
            .setId(id)
            .setDerivedProduct(derived);

        EntityNode<Product, ProductInfo> cnode = new ProductNode(owner, derived.getId())
            .setImportedEntity(derived)
            .setMergedEntity(derived)
            .setNodeState(NodeState.CREATED);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        pnode.addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        assertNull(existingEntity.getDerivedProduct());
        assertNotNull(pnode.getMergedEntity());
        assertNotNull(pnode.getMergedEntity().getDerivedProduct());
        assertEquals(derived.getId(), pnode.getMergedEntity().getDerivedProduct().getId());
    }

    @Test
    public void testProcessNodeResolvesProvidedProductProperly() {
        String id = "test_product-1";
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct(id, "test product", owner);

        Product provided = new Product()
            .setId("provided_product-1")
            .setName("provided product");

        Product importedEntity = new Product()
            .setId(id);
        importedEntity.addProvidedProduct(provided);

        EntityNode<Product, ProductInfo> cnode = new ProductNode(owner, provided.getId())
            .setImportedEntity(provided)
            .setMergedEntity(provided)
            .setNodeState(NodeState.CREATED);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        pnode.addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        assertNotNull(pnode.getMergedEntity());
        Collection<Product> providedProducts = pnode.getMergedEntity().getProvidedProducts();

        assertNotNull(providedProducts);
        assertEquals(1, providedProducts.size());
        assertEquals(provided.getId(), providedProducts.iterator().next().getId());
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
        assertNotNull(pnode.getMergedEntity());
        assertEquals(importedEntity.getName(), pnode.getMergedEntity().getName());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { -1, 0, 30 })
    public void testPruneNodeOmitsActiveRoot(int orphanedEntityGracePeriod) {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(true);
        ProductInfo importedEntity = (ProductInfo) existingEntity.clone();

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { -1, 0, 30 })
    public void testPruneNodeNeverDeletesCustomProducts(int orphanedEntityGracePeriod) {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(false);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());

        this.productCurator.flush();
        this.productCurator.clear();

        OwnerProduct op = this.ownerProductCurator.getOwnerProduct(owner.getId(), existingEntity.getId());
        assertNotNull(op);
        assertNull(op.getOrphanedDate());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { 1, 5, 30 })
    public void testPruneNodeDoesntFlagNewlyOrphanedEntitiesWithinGracePeriod(int orphanedEntityGracePeriod) {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(true);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { 1, 5, 30 })
    public void testPruneNodeDoesntFlagOrphanedEntitiesWithinGracePeriod(int orphanedEntityGracePeriod) {
        Owner owner = this.createOwner();
        Product existing = new Product()
            .setId("test_product")
            .setName("test product")
            .setLocked(true);

        Instant orphanedDate = Instant.now()
            .minus(orphanedEntityGracePeriod / 2, ChronoUnit.DAYS);

        OwnerProduct op = new OwnerProduct()
            .setOwner(owner)
            .setProduct(existing)
            .setOrphanedDate(orphanedDate);

        this.productCurator.create(existing);
        this.ownerProductCurator.create(op);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
            .setExistingEntity(existing);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { 1, 5, 30 })
    public void testPruneNodeFlagsOrphanedEntitiesAfterGracePeriod(int orphanedEntityGracePeriod) {
        Owner owner = this.createOwner();
        Product existing = new Product()
            .setId("test_product")
            .setName("test product")
            .setLocked(true);

        Instant orphanedDate = Instant.now()
            .minus(orphanedEntityGracePeriod + 5, ChronoUnit.DAYS);

        OwnerProduct op = new OwnerProduct()
            .setOwner(owner)
            .setProduct(existing)
            .setOrphanedDate(orphanedDate);

        this.productCurator.create(existing);
        this.ownerProductCurator.create(op);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
            .setExistingEntity(existing);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.DELETED, pnode.getNodeState());
    }

    @Test
    public void testPruneNodeFlagsUnusedNodeForDeletionWithNoGracePeriod() {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(true);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(0);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.DELETED, pnode.getNodeState());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { -1, -5, -5000 })
    public void testPruneNodeNeverFlagsNodesWithInfiniteGracePeriod(int orphanedEntityGracePeriod) {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(true);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(orphanedEntityGracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
    }

    @Test
    public void testPruneNodeFlagsLeafWithDeletedParentsForDeletion() {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(true);
        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        Product existingParent = this.createProduct("test_prod-2", "Test Product", owner);
        EntityNode<Product, ProductInfo> parentNode = new ProductNode(owner, existingParent.getId())
            .setExistingEntity(existingParent)
            .setNodeState(NodeState.DELETED);

        pnode.addParentNode(parentNode);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(0);
        visitor.pruneNode(pnode);

        assertEquals(NodeState.DELETED, pnode.getNodeState());
    }

    @Test
    public void testPruneNodeOmitsLeafWithUndeletedParents() {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(true);
        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        Product existingParent = this.createProduct("test_prod-2", "Test Product", owner);
        EntityNode<Product, ProductInfo> parentNode = new ProductNode(owner, existingParent.getId())
            .setExistingEntity(existingParent)
            .setNodeState(NodeState.UPDATED);

        pnode.addParentNode(parentNode);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor(0);
        visitor.pruneNode(pnode);

        assertNull(pnode.getNodeState());
    }

    @Test
    public void testPruneNodeOmitsLeafWithMixedParents() {
        Owner owner = this.createOwner();
        Product existingEntity = this.createProduct("test_prod-1", "Test Product", owner)
            .setLocked(true);
        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existingEntity.getId())
            .setExistingEntity(existingEntity);

        Product existingParent1 = this.createProduct("test_prod-2", "Test Product", owner);
        EntityNode<Product, ProductInfo> parentNode1 = new ProductNode(owner, existingParent1.getId())
            .setExistingEntity(existingParent1)
            .setNodeState(NodeState.UPDATED);

        Product existingParent2 = this.createProduct("test_prod-3", "Test Product", owner);
        EntityNode<Product, ProductInfo> parentNode2 = new ProductNode(owner, existingParent2.getId())
            .setExistingEntity(existingParent2)
            .setNodeState(NodeState.DELETED);

        pnode.addParentNode(parentNode1);
        pnode.addParentNode(parentNode2);

        assertNull(pnode.getNodeState());

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.pruneNode(pnode);

        assertNull(pnode.getNodeState());
    }

    @Test
    public void testApplyChangesPerformsVersionResolution() {
        String id = "test_product-1";
        String name = "test product 1";

        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product existing1 = this.createProduct(id, "old name", owner1);
        Product existing2 = this.createProduct(id, name, owner2);

        Product importedEntity = new Product()
            .setId(id)
            .setName(name);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner1, existing1.getId())
            .setExistingEntity(existing1)
            .setImportedEntity(importedEntity);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        visitor.applyChanges(pnode);

        Product mergedEntity = pnode.getMergedEntity();
        assertNotNull(mergedEntity);
        assertEquals(existing2.getUuid(), mergedEntity.getUuid());
    }

    @Test
    public void testApplyChangesPerformsChildResolution() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product existingProduct1 = this.createProduct("derived_product", "old name", owner1);
        Product existingProduct2 = this.createProduct("derived_product", "derived product", owner2);
        Product importedProduct = new Product()
            .setId(existingProduct2.getId())
            .setName(existingProduct2.getName());

        Product product = new Product()
            .setId("test_product")
            .setName("test product")
            .setDerivedProduct(existingProduct1);
        product = this.createProduct(product, owner1);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner1, product.getId())
            .setExistingEntity(product)
            .setImportedEntity(product);

        EntityNode<Product, ProductInfo> cnode = new ProductNode(owner1, existingProduct1.getId())
            .setExistingEntity(existingProduct1)
            .setImportedEntity(importedProduct);

        pnode.addChildNode(cnode);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(cnode);
        visitor.processNode(pnode);
        assertEquals(NodeState.UPDATED, cnode.getNodeState());
        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        visitor.applyChanges(cnode);
        visitor.applyChanges(pnode);

        Product mergedEntity = pnode.getMergedEntity();
        assertNotNull(mergedEntity);
        assertNotNull(mergedEntity.getDerivedProduct());
        assertEquals(existingProduct2.getUuid(), mergedEntity.getDerivedProduct().getUuid());
    }

    @Test
    public void testCompleteDoesNotActTwice() {
        Owner owner = this.createOwner();

        Product product = new Product()
            .setId("test_product")
            .setName("test product");

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, product.getId())
            .setImportedEntity(product);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.CREATED, pnode.getNodeState());
        assertNotNull(pnode.getMergedEntity());
        assertNotNull(pnode.getMergedEntity().getUuid());

        // If this executes twice, a cached creation op would try to run twice, which would fail
        // with a persistence exception of some kind
        visitor.complete();
    }

    @Test
    public void testFullCyclePersistsNewEntity() {
        Owner owner = this.createOwner();

        Product product = new Product()
            .setId("test_product")
            .setName("test product");

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, product.getId())
            .setImportedEntity(product);

        assertNull(this.ownerProductCurator.getProductById(owner, product.getId()));

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.CREATED, pnode.getNodeState());

        Product merged = pnode.getMergedEntity();
        assertNotNull(merged);
        assertNotNull(merged.getUuid());

        this.productCurator.flush();
        this.productCurator.clear();

        Product created = this.ownerProductCurator.getProductById(owner, product.getId());
        assertNotNull(created);
        assertEquals(merged.getUuid(), created.getUuid());
    }

    @Test
    public void testFullCyclePersistsUpdatedEntity() {
        Owner owner = this.createOwner();

        Product existing = this.createProduct("test_product", "product name", owner);
        existing.setLocked(true);

        Product product = new Product()
            .setId(existing.getId())
            .setName("updated product");

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, product.getId())
            .setExistingEntity(existing)
            .setImportedEntity(product);

        ProductNodeVisitor visitor = this.buildNodeVisitor();
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.UPDATED, pnode.getNodeState());

        Product merged = pnode.getMergedEntity();
        assertNotNull(merged);
        assertNotNull(merged.getUuid());
        assertNotEquals(existing.getUuid(), merged.getUuid());
        assertNotEquals(existing.getName(), merged.getName());

        this.productCurator.flush();
        this.productCurator.clear();

        Product updated = this.ownerProductCurator.getProductById(owner, product.getId());
        assertNotNull(updated);
        assertEquals(merged.getUuid(), updated.getUuid());
        assertEquals(product.getName(), updated.getName());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { 1, 5, 30 })
    public void testFullCycleSetsOrphanedDateOnNewlyOrphanedEntitiesWithGracePeriod(int gracePeriod) {
        Owner owner = this.createOwner();
        Product existing = new Product()
            .setId("test_product")
            .setName("test product")
            .setLocked(true);

        Instant creationDate = Instant.now();

        OwnerProduct op = new OwnerProduct()
            .setOwner(owner)
            .setProduct(existing);

        this.productCurator.create(existing);
        this.ownerProductCurator.create(op);

        assertNull(op.getOrphanedDate());

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
            .setExistingEntity(existing);

        ProductNodeVisitor visitor = this.buildNodeVisitor(gracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
        assertNull(pnode.getMergedEntity());

        this.productCurator.flush();
        this.productCurator.clear();

        OwnerProduct updated = this.ownerProductCurator.getOwnerProduct(owner.getId(), existing.getId());
        assertNotNull(updated);
        assertNotNull(updated.getOrphanedDate());
        assertTrue(creationDate.isBefore(updated.getOrphanedDate()));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { 1, 5, 30 })
    public void testFullCycleOmitsOrphanedEntitiesWithinGracePeriod(int gracePeriod) {
        Owner owner = this.createOwner();
        Product existing = new Product()
            .setId("test_product")
            .setName("test product")
            .setLocked(true);

        Instant orphanedDate = Instant.now()
            .minus(gracePeriod / 2, ChronoUnit.DAYS);

        OwnerProduct op = new OwnerProduct()
            .setOwner(owner)
            .setProduct(existing)
            .setOrphanedDate(orphanedDate);

        this.productCurator.create(existing);
        this.ownerProductCurator.create(op);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
            .setExistingEntity(existing);

        ProductNodeVisitor visitor = this.buildNodeVisitor(gracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
        assertNull(pnode.getMergedEntity());

        this.productCurator.flush();
        this.productCurator.clear();

        OwnerProduct updated = this.ownerProductCurator.getOwnerProduct(owner.getId(), existing.getId());
        assertNotNull(updated);
        assertEquals(orphanedDate.truncatedTo(ChronoUnit.MILLIS),
            updated.getOrphanedDate().truncatedTo(ChronoUnit.MILLIS));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { 1, 5, 30 })
    public void testFullCycleOmitsDeletedOrphanedEntitiesOutsideOfGracePeriod(int gracePeriod) {
        Owner owner = this.createOwner();
        Product existing = new Product()
            .setId("test_product")
            .setName("test product")
            .setLocked(true);

        Instant orphanedDate = Instant.now()
            .minus(gracePeriod + 5, ChronoUnit.DAYS);

        OwnerProduct op = new OwnerProduct()
            .setOwner(owner)
            .setProduct(existing)
            .setOrphanedDate(orphanedDate);

        this.productCurator.create(existing);
        this.ownerProductCurator.create(op);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
            .setExistingEntity(existing);

        ProductNodeVisitor visitor = this.buildNodeVisitor(gracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.DELETED, pnode.getNodeState());
        assertNull(pnode.getMergedEntity());

        this.productCurator.flush();
        this.productCurator.clear();

        Product deleted = this.ownerProductCurator.getProductById(owner, existing.getId());
        assertNull(deleted);
    }

    @Test
    public void testFullCycleDeletesUnusedEntityWithNoGracePeriod() {
        Owner owner = this.createOwner();

        Product existing = this.createProduct("test_product", "product name", owner);
        existing.setLocked(true);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
            .setExistingEntity(existing);

        ProductNodeVisitor visitor = this.buildNodeVisitor(0);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.DELETED, pnode.getNodeState());
        assertNull(pnode.getMergedEntity());

        this.productCurator.flush();
        this.productCurator.clear();

        Product deleted = this.ownerProductCurator.getProductById(owner, existing.getId());
        assertNull(deleted);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { -1, -5, -9000 })
    public void testFullCycleOmitsUnusedEntityWithInfiniteGracePeriod(int gracePeriod) {
        Owner owner = this.createOwner();

        Product existing = this.createProduct("test_product", "product name", owner);
        existing.setLocked(true);

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner, existing.getId())
            .setExistingEntity(existing);

        ProductNodeVisitor visitor = this.buildNodeVisitor(gracePeriod);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.UNCHANGED, pnode.getNodeState());
        assertNull(pnode.getMergedEntity());

        this.productCurator.flush();
        this.productCurator.clear();

        OwnerProduct updated = this.ownerProductCurator.getOwnerProduct(owner.getId(), existing.getId());
        assertNotNull(updated);
        assertNull(updated.getOrphanedDate());
    }

    /**
     * This test verifies that a product version collision on a given product ID can be resolved by
     * clearing the entity version of the existing product, operating under the assumption that the
     * current product is broken and the new one is the "correct" entity for the version.
     */
    @Test
    public void testEntityVersionCollisionResolution() {
        Owner owner2 = this.createOwner();
        Product collider = this.createProduct("test_product", "test product", owner2);

        this.ownerProductCurator.flush();
        this.ownerProductCurator.clear();

        Owner owner1 = this.createOwner();

        Product imported = collider.clone()
            .setUuid(null)
            .setMultiplier(9001L);

        long entityVersion = imported.getEntityVersion();

        EntityNode<Product, ProductInfo> pnode = new ProductNode(owner1, collider.getId())
            .setImportedEntity(imported);

        // Forcefully set the entity version
        int count = this.getEntityManager()
            .createQuery("UPDATE Product SET entityVersion = :version WHERE uuid = :uuid")
            .setParameter("version", entityVersion)
            .setParameter("uuid", collider.getUuid())
            .executeUpdate();

        assertEquals(1, count);

        ProductNodeVisitor visitor = this.buildNodeVisitor(-1);
        visitor.processNode(pnode);
        visitor.pruneNode(pnode);
        visitor.applyChanges(pnode);
        visitor.complete();

        assertEquals(NodeState.CREATED, pnode.getNodeState());
        assertNotNull(pnode.getMergedEntity());
        assertNotNull(pnode.getMergedEntity().getUuid());
        assertEquals(entityVersion, pnode.getMergedEntity().getEntityVersion());

        // Query the entity version directly so we avoid the automatic regeneration when it's null
        Long existingEntityVersion = this.getEntityManager()
            .createQuery("SELECT entityVersion FROM Product WHERE uuid = :uuid", Long.class)
            .setParameter("uuid", collider.getUuid())
            .getSingleResult();

        assertNull(existingEntityVersion);
    }

}
