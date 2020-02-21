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
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.ContentNode;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.ProductNode;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Test suite for the ProductNodeVisitor class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProductNodeVisitorTest {

    private Map<String, Mutator<Product>> productMutators;
    private Map<String, Mutator<ProductInfo>> pinfoMutators;
    private Map<String, MergeValidator<ProductInfo>> validators;

    private ProductCurator mockProductCurator;
    private OwnerProductCurator mockOwnerProductCurator;
    private NodeProcessor mockNodeProcessor;
    private NodeMapper mockNodeMapper;

    @BeforeEach
    public void init() {
        this.productMutators = new HashMap<>();
        this.productMutators.put("name", (c, v) -> c.setName((String) v));
        this.productMutators.put("multiplier", (c, v) -> c.setMultiplier((Long) v));
        this.productMutators.put("dependent_product_ids", (c, v) -> c.setDependentProductIds((Collection) v));
        this.productMutators.put("attributes", (c, v) -> c.setAttributes((Map) v));
        this.productMutators.put("product_content", (c, v) -> c.setProductContent((Collection) v));
        this.productMutators.put("branding", (c, v) -> c.setBranding((Collection) v));
        this.productMutators.put("provided_products", (c, v) -> c.setProvidedProducts((Collection) v));

        this.pinfoMutators = new HashMap<>();
        this.pinfoMutators.put("name", (c, v) -> doReturn(v).when(c).getName());
        this.pinfoMutators.put("multiplier", (c, v) -> doReturn(v).when(c).getMultiplier());
        this.pinfoMutators.put("dependent_product_ids",
            (c, v) -> doReturn(v).when(c).getDependentProductIds());
        this.pinfoMutators.put("attributes", (c, v) -> doReturn(v).when(c).getAttributes());
        this.pinfoMutators.put("product_content", (c, v) -> doReturn(v).when(c).getProductContent());
        this.pinfoMutators.put("branding", (c, v) -> doReturn(v).when(c).getBranding());
        this.pinfoMutators.put("provided_products", (c, v) -> doReturn(v).when(c).getProvidedProducts());

        this.validators = new HashMap<>();
        this.validators.put("name", c -> c.getName());
        this.validators.put("multiplier", c -> c.getMultiplier());
        this.validators.put("dependent_product_ids", (CollectionMergeValidator<ProductInfo, String>)
            c -> c.getDependentProductIds());
        this.validators.put("attributes", (MapMergeValidator<ProductInfo, String, String>)
            c -> c.getAttributes());
        this.validators.put("product_content", (CollectionMergeValidator<ProductInfo, ProductContentInfo>)
            c -> (Collection<ProductContentInfo>) c.getProductContent());
        this.validators.put("branding", (CollectionMergeValidator<ProductInfo, BrandingInfo>)
            c -> (Collection<BrandingInfo>) c.getBranding());
        this.validators.put("provided_products", (CollectionMergeValidator<ProductInfo, ProductInfo>)
            c -> (Collection<ProductInfo>) c.getProvidedProducts());

        this.mockProductCurator = mock(ProductCurator.class);
        this.mockOwnerProductCurator = mock(OwnerProductCurator.class);
        this.mockNodeProcessor = mock(NodeProcessor.class);
        this.mockNodeMapper = mock(NodeMapper.class);

        doAnswer(returnsFirstArg())
            .when(this.mockProductCurator)
            .saveOrUpdate(Mockito.any(Product.class));

        doAnswer(returnsFirstArg())
            .when(this.mockOwnerProductCurator)
            .saveOrUpdate(Mockito.any(OwnerProduct.class));
    }

    private static Branding buildBranding(String id, String name) {
        Branding branding = new Branding();
        branding.setId(id);
        branding.setName(name);

        return branding;
    }

    private void mockNodeMappings(Collection<EntityNode> nodes) {
        if (nodes != null) {
            for (EntityNode node : nodes) {
                doReturn(node).when(this.mockNodeMapper)
                    .getNode(eq(node.getEntityClass()), eq(node.getEntityId()));

                // Our processor won't actually do recursive processing, so we'll pretend they've been
                // processed.
                node.markVisited();
            }
        }
    }

    private static EntityNode buildContentNode(Owner owner, String id, Content existing, Content imported) {
        EntityNode<Content, ContentInfo> node = new ContentNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        if (imported != null) {
            node.markChanged();
            node.setMergedEntity(imported);
        }

        return node;
    }

    private static ProductContent buildProductContent(String contentId, boolean enabled) {
        Content content = new Content(contentId);
        return new ProductContent(null, content, enabled);
    }

    private static EntityNode buildProductNode(Owner owner, String id, Product existing, Product imported) {
        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        if (imported != null) {
            node.markChanged();
            node.setMergedEntity(imported);
        }

        return node;
    }

    private static Product buildProvidedProduct(String productId, String productName) {
        return new Product(productId, productName);
    }

    public static List<Arguments> productDataProvider() {
        Owner owner = TestUtil.createOwner();

        Map<String, String> baseAttribs = new HashMap<>();
        baseAttribs.put("A", "1");
        baseAttribs.put("B", "2");
        baseAttribs.put("C", "3");

        Map<String, String> updatedAttribs = new HashMap<>();
        baseAttribs.put("C", "3");
        baseAttribs.put("D", "4");
        baseAttribs.put("E", "5");

        List<Branding> baseBranding = Arrays.asList(
            buildBranding("bid-1", "branding-1"),
            buildBranding("bid-2", "branding-2"),
            buildBranding("bid-3", "branding-3"));

        List<Branding> updatedBranding = Arrays.asList(
            buildBranding("bid-3", "branding-3"),
            buildBranding("bid-4", "branding-4"),
            buildBranding("bid-5", "branding-5"));

        List<ProductContent> baseProductContent = Arrays.asList(
            buildProductContent("cid-1", true),
            buildProductContent("cid-2", false),
            buildProductContent("cid-3", true));

        List<ProductContent> updatedProductContent = Arrays.asList(
            buildProductContent("cid-3", false),
            buildProductContent("cid-4", true),
            buildProductContent("cid-5", false));

        List<Product> baseProvidedProducts = Arrays.asList(
            buildProvidedProduct("pid-1", "provided-1"),
            buildProvidedProduct("pid-2", "provided-2"),
            buildProvidedProduct("pid-3", "provided-3"));

        List<Product> updatedProvidedProducts = Arrays.asList(
            buildProvidedProduct("pid-3", "provided-3"),
            buildProvidedProduct("pid-4", "provided-4"),
            buildProvidedProduct("pid-5", "provided-5"));

        EntityNode cnode1 = buildContentNode(owner, "cid-1", baseProductContent.get(0).getContent(),
            null);
        EntityNode cnode2 = buildContentNode(owner, "cid-2", baseProductContent.get(1).getContent(),
            null);
        EntityNode cnode3 = buildContentNode(owner, "cid-3", baseProductContent.get(2).getContent(),
            null);
        EntityNode cnode4 = buildContentNode(owner, "cid-3", baseProductContent.get(2).getContent(),
            updatedProductContent.get(0).getContent());
        EntityNode cnode5 = buildContentNode(owner, "cid-4", null,
            updatedProductContent.get(1).getContent());
        EntityNode cnode6 = buildContentNode(owner, "cid-5", null,
            updatedProductContent.get(2).getContent());

        EntityNode pnode1 = buildProductNode(owner, "pid-1", baseProvidedProducts.get(0), null);
        EntityNode pnode2 = buildProductNode(owner, "pid-2", baseProvidedProducts.get(1), null);
        EntityNode pnode3 = buildProductNode(owner, "pid-3", baseProvidedProducts.get(2), null);
        EntityNode pnode4 = buildProductNode(owner, "pid-3", baseProvidedProducts.get(2),
            updatedProvidedProducts.get(0));
        EntityNode pnode5 = buildProductNode(owner, "pid-4", null, updatedProvidedProducts.get(1));
        EntityNode pnode6 = buildProductNode(owner, "pid-5", null, updatedProvidedProducts.get(2));

        return Arrays.asList(
            Arguments.of("name", "base_name", null, "updated_name", null),
            Arguments.of("multiplier", 12345L, null, 67890L, null),
            Arguments.of("dependent_product_ids", Arrays.asList("1", "2", "3"), null,
                Arrays.asList("A", "B", "C"), null),
            Arguments.of("attributes", baseAttribs, null, updatedAttribs, null),
            Arguments.of("product_content", baseProductContent, Arrays.asList(cnode1, cnode2, cnode3),
                updatedProductContent, Arrays.asList(cnode4, cnode5, cnode6)),
            Arguments.of("branding", baseBranding, null, updatedBranding, null),
            Arguments.of("provided_products", baseProvidedProducts, Arrays.asList(pnode1, pnode2, pnode3),
                updatedProvidedProducts, Arrays.asList(pnode4, pnode5, pnode6)));
    }

    private ProductNodeVisitor buildProductNodeVisitor() {
        return new ProductNodeVisitor(this.mockProductCurator, this.mockOwnerProductCurator);
    }

    private Product createPopulatedExistingEntity(String id, String key, Object value) {
        Product entity = new Product();
        entity.setId(id);

        Mutator mutator = this.productMutators.get(key);
        if (mutator == null) {
            throw new IllegalStateException("No mutator for key: " + key);
        }

        mutator.mutate(entity, value);

        return entity;
    }

    private ProductInfo createPopulatedImportedEntity(String id, String key, Object value) {
        ProductInfo entity = mock(ProductInfo.class);
        doReturn(id).when(entity).getId();

        // Impl note:
        // This is necessary, since the default behavior for mocked methods that return primitive
        // containers is to return a wrapped default primitive value, *NOT* null as one might
        // expect.
        doReturn(null).when(entity).getMultiplier();

        Mutator mutator = this.pinfoMutators.get(key);
        if (mutator == null) {
            throw new IllegalStateException("No mutator for key: " + key);
        }

        mutator.mutate(entity, value);

        return entity;
    }

    private void validateMergedEntity(Product existing, ProductInfo imported, Product merged) {
        // Assert that we actually have a merged entity
        assertNotNull(merged);

        // Ensure the ID is set properly
        assertNotNull(merged.getId());

        if (existing != null) {
            assertNotNull(existing.getId());
            assertEquals(existing.getId(), merged.getId());
        }

        if (imported != null) {
            assertNotNull(imported.getId());
            assertEquals(imported.getId(), merged.getId());
        }

        // Check that the product is locked properly
        if (existing != null) {
            assertEquals(existing.isLocked(), merged.isLocked());
        }
        else {
            assertNotNull(imported);
            assertTrue(merged.isLocked());
        }

        // Check other attributes
        for (MergeValidator validator : this.validators.values()) {
            validator.validate(existing, imported, merged);
        }
    }

    @Test
    public void testGetEntityClass() {
        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        assertEquals(Product.class, visitor.getEntityClass());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("productDataProvider")
    public void testProcessNodeForSkippedEntity(String key, Object base, Collection<EntityNode> baseChildren,
        Object update, Collection<EntityNode> updatedChildren) {

        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, key, base);

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing);

        if (baseChildren != null) {
            this.mockNodeMappings(baseChildren);

            for (EntityNode child : baseChildren) {
                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        // Ensure initial node state
        assertFalse(node.visited());
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        verify(this.mockProductCurator, never()).saveOrUpdate(any(Product.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("productDataProvider")
    public void testProcessNodeForUnmodifiedEntity(String key, Object base,
        Collection<EntityNode> baseChildren, Object update, Collection<EntityNode> updatedChildren) {

        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, key, base);
        ProductInfo imported = this.createPopulatedImportedEntity(id, key, null);

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        if (baseChildren != null) {
            this.mockNodeMappings(baseChildren);

            for (EntityNode child : baseChildren) {
                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        // Ensure initial node state
        assertFalse(node.visited());
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        verify(this.mockProductCurator, never()).saveOrUpdate(any(Product.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("productDataProvider")
    public void testProcessNodeForUnchangedEntity(String key, Object base,
        Collection<EntityNode> baseChildren, Object update, Collection<EntityNode> updatedChildren) {

        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, key, base);
        ProductInfo imported = this.createPopulatedImportedEntity(id, key, base);

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        if (baseChildren != null) {
            this.mockNodeMappings(baseChildren);

            for (EntityNode child : baseChildren) {
                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        // Ensure initial node state
        assertFalse(node.visited());
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        verify(this.mockProductCurator, never()).saveOrUpdate(any(Product.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("productDataProvider")
    public void testProcessNodeForNewEntity(String key, Object base, Collection<EntityNode> baseChildren,
        Object update, Collection<EntityNode> updatedChildren) {

        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        ProductInfo imported = this.createPopulatedImportedEntity(id, key, update);

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setImportedEntity(imported);

        if (updatedChildren != null) {
            this.mockNodeMappings(updatedChildren);

            for (EntityNode child : updatedChildren) {
                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        // Ensure initial node state
        assertFalse(node.visited());
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertTrue(node.changed());
        assertNotNull(node.getMergedEntity());
        this.validateMergedEntity(null, imported, node.getMergedEntity());

        verify(this.mockProductCurator, times(1)).saveOrUpdate(any(Product.class));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("productDataProvider")
    public void testProcessNodeForUpdatedEntity(String key, Object base, Collection<EntityNode> baseChildren,
        Object update, Collection<EntityNode> updatedChildren) {

        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, key, base);
        ProductInfo imported = this.createPopulatedImportedEntity(id, key, update);

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        if (updatedChildren != null) {
            this.mockNodeMappings(updatedChildren);

            for (EntityNode child : updatedChildren) {
                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        // Ensure initial node state
        assertFalse(node.visited());
        assertFalse(node.changed());
        assertNull(node.getMergedEntity());

        // Visit/process the node
        visitor.processNode(this.mockNodeProcessor, this.mockNodeMapper, node);
        visitor.complete();

        // Validate node state
        assertTrue(node.changed());
        assertNotNull(node.getMergedEntity());
        this.validateMergedEntity(existing, imported, node.getMergedEntity());

        verify(this.mockProductCurator, times(1)).saveOrUpdate(any(Product.class));
    }

    public void testCompileResultsForSkippedEntity() {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, "name", "product-1");

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing);

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        RefreshResult result = new RefreshResult();

        visitor.compileResults(result, node);

        Map<String, Product> created = result.getCreatedProducts();
        Map<String, Product> updated = result.getUpdatedProducts();
        Map<String, Product> skipped = result.getSkippedProducts();

        assertNotNull(result.getProduct(id));
        assertEquals(0, created.size());
        assertEquals(0, updated.size());
        assertEquals(1, skipped.size());
        assertThat(skipped, hasEntry(id, existing));
    }

    public void testCompileResultsForUnmodifiedEntity() {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, "name", "product-1");
        ProductInfo imported = this.createPopulatedImportedEntity(id, "name", null);

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        RefreshResult result = new RefreshResult();

        visitor.compileResults(result, node);

        Map<String, Product> created = result.getCreatedProducts();
        Map<String, Product> updated = result.getUpdatedProducts();
        Map<String, Product> skipped = result.getSkippedProducts();

        assertNotNull(result.getProduct(id));
        assertEquals(0, created.size());
        assertEquals(0, updated.size());
        assertEquals(1, skipped.size());
        assertThat(skipped, hasEntry(id, existing));
    }

    public void testCompileResultsForUnchangedEntity() {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, "name", "product-1");
        ProductInfo imported = this.createPopulatedImportedEntity(id, "name", "product-1");

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported);

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        RefreshResult result = new RefreshResult();

        visitor.compileResults(result, node);

        Map<String, Product> created = result.getCreatedProducts();
        Map<String, Product> updated = result.getUpdatedProducts();
        Map<String, Product> skipped = result.getSkippedProducts();

        assertNotNull(result.getProduct(id));
        assertEquals(0, created.size());
        assertEquals(0, updated.size());
        assertEquals(1, skipped.size());
        assertThat(skipped, hasEntry(id, existing));
    }

    public void testCompileResultsForNewEntity() {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        ProductInfo imported = this.createPopulatedImportedEntity(id, "name", "product-1");
        Product merged = this.createPopulatedExistingEntity(id, "name", "product-1");

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setImportedEntity(imported)
            .setMergedEntity(merged);

        node.markChanged();

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        RefreshResult result = new RefreshResult();

        visitor.compileResults(result, node);

        Map<String, Product> created = result.getCreatedProducts();
        Map<String, Product> updated = result.getUpdatedProducts();
        Map<String, Product> skipped = result.getSkippedProducts();

        assertNotNull(result.getProduct(id));
        assertEquals(1, created.size());
        assertEquals(0, updated.size());
        assertEquals(0, skipped.size());
        assertThat(created, hasEntry(id, merged));
    }

    public void testCompileResultsForUpdatedEntity() {
        Owner owner = TestUtil.createOwner();
        String id = TestUtil.randomString("test_id");

        Product existing = this.createPopulatedExistingEntity(id, "name", "product-1");
        ProductInfo imported = this.createPopulatedImportedEntity(id, "name", "product-1b");
        Product merged = this.createPopulatedExistingEntity(id, "name", "product-1b");

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existing)
            .setImportedEntity(imported)
            .setMergedEntity(merged);

        node.markChanged();

        ProductNodeVisitor visitor = this.buildProductNodeVisitor();

        RefreshResult result = new RefreshResult();

        visitor.compileResults(result, node);

        Map<String, Product> created = result.getCreatedProducts();
        Map<String, Product> updated = result.getUpdatedProducts();
        Map<String, Product> skipped = result.getSkippedProducts();

        assertNotNull(result.getProduct(id));
        assertEquals(0, created.size());
        assertEquals(1, updated.size());
        assertEquals(0, skipped.size());
        assertThat(created, hasEntry(id, existing));
    }

}
