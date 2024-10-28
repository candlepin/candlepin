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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.server.v1.AttributeDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ProductCertificateDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductContent;
import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;



public class OwnerProductResourceTest extends DatabaseTestFixture {

    private OwnerProductResource ownerProductResource;

    @BeforeEach
    public void setUp() {
        ownerProductResource = this.injector.getInstance(OwnerProductResource.class);

        // Make sure we don't have any latent page requests in the context
        ResteasyContext.clearContextData();
    }

    @AfterEach
    public void cleanup() throws Exception {
        // Also cleanup after ourselves for other tests
        ResteasyContext.clearContextData();
    }

    private OwnerProductResource buildResource() {
        // TODO: We could probably move the actual invocation/creation here and removing it from the class
        // scope, but for now, just prime our new tests to be ready for such a refactor.
        return this.ownerProductResource;
    }

    private ProductDTO buildTestProductDTO() {
        return TestUtil.createProductDTO("test_product")
            .addAttributesItem(this.createAttribute(Product.Attributes.VERSION, "1.0"))
            .addAttributesItem(this.createAttribute(Product.Attributes.VARIANT, "server"))
            .addAttributesItem(this.createAttribute(Product.Attributes.TYPE, "SVC"))
            .addAttributesItem(this.createAttribute(Product.Attributes.ARCHITECTURE, "ALL"));
    }

    private AttributeDTO createAttribute(String name, String value) {
        return new AttributeDTO()
            .name(name)
            .value(value);
    }

    private void addContent(ProductDTO product, ContentDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalArgumentException("dto references incomplete content");
        }

        if (product.getProductContent() == null) {
            product.setProductContent(new HashSet<>());
        }

        ProductContentDTO content = new ProductContentDTO();
        content.setContent(dto);
        content.setEnabled(true);

        product.getProductContent().add(content);
    }

    @Test
    public void testCreateProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        ProductDTO pdto = this.buildTestProductDTO();

        assertNull(this.productCurator.getProductById(owner.getKey(), pdto.getId()));

        ProductDTO result = this.ownerProductResource.createProduct(owner.getKey(), pdto);
        assertNotNull(result);

        Product entity = this.productCurator.getProductById(owner.getKey(), pdto.getId());
        assertNotNull(entity);

        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);
        assertEquals(expected, result);
    }

    @Test
    public void testCreateProductWithAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        ProductDTO pdto = new ProductDTO()
            .id("test_prod-1")
            .name("test product 1");

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        ProductDTO output = this.ownerProductResource.createProduct(owner.getKey(), pdto);

        assertNotNull(output);
        assertEquals(pdto.getId(), output.getId());
        assertEquals(pdto.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(attributes.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(attributes.containsKey(attrib.getName()));
            assertEquals(attributes.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testCreateProductFiltersUnusableAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");
        attributes.put("", "dropped");
        attributes.put("dropped", null);

        Map<String, String> expected = new HashMap<>();
        expected.put("attrib-1", "value-1");
        expected.put("attrib-2", "value-2");
        expected.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        ProductDTO pdto = new ProductDTO()
            .id("test_prod-1")
            .name("test product 1");

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        // Add some dud attributes to ensure filtering is occurring for other types of malformed
        // attribute data
        pdto.addAttributesItem(null);
        pdto.addAttributesItem(this.createAttribute(null, "dropped"));

        ProductDTO output = this.ownerProductResource.createProduct(owner.getKey(), pdto);

        assertNotNull(output);
        assertEquals(pdto.getId(), output.getId());
        assertEquals(pdto.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(expected.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(expected.containsKey(attrib.getName()));
            assertEquals(expected.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testCreateProductWithContent() {
        Owner owner = this.createOwner("Example-Corporation");
        String namespace = owner.getKey();

        Content content = TestUtil.createContent("content-1")
            .setNamespace(namespace);

        this.createContent(content);

        ProductDTO product = this.buildTestProductDTO();
        ContentDTO contentDTO = this.modelTranslator.translate(content, ContentDTO.class);
        addContent(product, contentDTO);

        assertNull(this.productCurator.getProductById(namespace, product.getId()));

        ProductDTO result = this.ownerProductResource.createProduct(namespace, product);

        Product entity = this.productCurator.getProductById(namespace, product.getId());
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        assertNotNull(result);
        assertNotNull(entity);
        assertEquals(expected, result);

        assertNotNull(result.getProductContent());
        assertEquals(1, result.getProductContent().size());
        assertEquals(contentDTO, result.getProductContent().iterator().next().getContent());
    }

    @Test
    public void testCreateProductInOrgUsingLongKey() {
        Owner owner = this.createOwner("test_owner".repeat(25));
        ProductDTO pdto = this.buildTestProductDTO();

        assertNull(this.productCurator.getProductById(owner.getKey(), pdto.getId()));

        ProductDTO result = this.ownerProductResource.createProduct(owner.getKey(), pdto);
        assertNotNull(result);

        Product entity = this.productCurator.getProductById(owner.getKey(), pdto.getId());
        assertNotNull(entity);

        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);
        assertEquals(expected, result);
    }

    @Test
    public void testUpdateProductWithAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        Product existing = TestUtil.createProduct("test_prod-1", "test product 1")
            .setNamespace(owner.getKey());

        this.createProduct(existing);

        assertNotNull(existing);
        assertTrue(existing.getAttributes().isEmpty());

        ProductDTO pdto = new ProductDTO()
            .id(existing.getId());

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        ProductDTO output = this.ownerProductResource.updateProduct(owner.getKey(), existing.getId(), pdto);

        assertNotNull(output);
        assertEquals(existing.getId(), output.getId());
        assertEquals(existing.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(attributes.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(attributes.containsKey(attrib.getName()));
            assertEquals(attributes.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testUpdateProductFiltersUnusableAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "value-3");
        attributes.put("", "dropped");
        attributes.put("dropped", null);

        Map<String, String> expected = new HashMap<>();
        expected.put("attrib-1", "value-1");
        expected.put("attrib-2", "value-2");
        expected.put("attrib-3", "value-3");

        Owner owner = this.createOwner("Test org");
        Product existing = TestUtil.createProduct("test_prod-1", "test product 1")
            .setNamespace(owner.getKey());

        this.createProduct(existing);

        assertNotNull(existing);
        assertTrue(existing.getAttributes().isEmpty());

        ProductDTO pdto = new ProductDTO()
            .id(existing.getId());

        attributes.forEach((k, v) -> pdto.addAttributesItem(this.createAttribute(k, v)));

        // Add some dud attributes to ensure filtering is occurring for other types of malformed
        // attribute data
        pdto.addAttributesItem(null);
        pdto.addAttributesItem(this.createAttribute(null, "dropped"));

        ProductDTO output = this.ownerProductResource.updateProduct(owner.getKey(), existing.getId(), pdto);

        assertNotNull(output);
        assertEquals(existing.getId(), output.getId());
        assertEquals(existing.getName(), output.getName());
        assertNotNull(output.getAttributes());
        assertEquals(expected.size(), output.getAttributes().size());

        for (AttributeDTO attrib : output.getAttributes()) {
            assertTrue(expected.containsKey(attrib.getName()));
            assertEquals(expected.get(attrib.getName()), attrib.getValue());
        }
    }

    @Test
    public void testUpdateProductWithoutId() {
        Owner owner = this.createOwner("test_owner");
        ProductDTO pdto = this.buildTestProductDTO();

        ProductDTO product = this.ownerProductResource.createProduct(owner.getKey(), pdto);
        ProductDTO update = TestUtil.createProductDTO(product.getId());
        update.setName(product.getName());
        update.getAttributes().add(createAttribute("attri", "bute"));
        ProductDTO result = this.ownerProductResource.updateProduct(owner.getKey(), product.getId(), update);
        assertEquals("bute", result.getAttributes().get(0).getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testAddContentToProduct(boolean contentEnabled) {
        Owner owner = this.createOwner("test_owner");

        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner.getKey());
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());

        this.createProduct(product);
        this.createContent(content);

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();

        ProductDTO output = this.ownerProductResource.addContentToProduct(owner.getKey(), product.getId(),
            content.getId(), contentEnabled);

        assertThat(output.getProductContent())
            .isNotNull()
            .singleElement()
            .returns(contentEnabled, ProductContentDTO::getEnabled)
            .extracting(ProductContentDTO::getContent)
            .returns(content.getId(), ContentDTO::getId);

        assertThat(product.getProductContent())
            .isNotNull()
            .singleElement()
            .returns(contentEnabled, ProductContent::isEnabled)
            .extracting(ProductContent::getContent)
            .returns(content.getId(), Content::getId);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testAddContentToProductDisallowsModifyingGlobalProduct(boolean contentEnabled) {
        Owner owner = this.createOwner("test_owner");

        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace((Owner) null);
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());

        this.createProduct(product);
        this.createContent(content);

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();

        assertThrows(ForbiddenException.class, () -> this.ownerProductResource
            .addContentToProduct(owner.getKey(), product.getId(), content.getId(), contentEnabled));

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testAddContentToProductDisallowsAddingGlobalContentToProduct(boolean contentEnabled) {
        Owner owner = this.createOwner("test_owner");

        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner.getKey());
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);

        this.createProduct(product);
        this.createContent(content);

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();

        assertThrows(ForbiddenException.class, () -> this.ownerProductResource
            .addContentToProduct(owner.getKey(), product.getId(), content.getId(), contentEnabled));

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddContentsToProduct() {
        Owner owner = this.createOwner("test_owner");

        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner.getKey());
        Content content1 = TestUtil.createContent("test_content-1", "test_content-1")
            .setNamespace(owner.getKey());
        Content content2 = TestUtil.createContent("test_content-2", "test_content-2")
            .setNamespace(owner.getKey());


        this.createProduct(product);
        this.createContent(content1);
        this.createContent(content2);

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();

        Map<String, Boolean> input = Map.of(
            content1.getId(), true,
            content2.getId(), false);

        ProductDTO output = this.ownerProductResource.addContentsToProduct(owner.getKey(), product.getId(),
            input);

        assertThat(output.getProductContent())
            .isNotNull()
            .hasSize(2);

        Map<String, Boolean> mappedOutputContent = output.getProductContent()
            .stream()
            .collect(Collectors.toMap(pc -> pc.getContent().getId(), ProductContentDTO::getEnabled));

        Map<String, Boolean> mappedProductContent = product.getProductContent()
            .stream()
            .collect(Collectors.toMap(pc -> pc.getContent().getId(), ProductContent::isEnabled));

        assertEquals(input, mappedOutputContent);
        assertEquals(input, mappedProductContent);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testAddContentsToProductDisallowsModifyingGlobalProduct(boolean contentEnabled) {
        Owner owner = this.createOwner("test_owner");

        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace((Owner) null);
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());

        this.createProduct(product);
        this.createContent(content);

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();

        assertThrows(ForbiddenException.class, () -> this.ownerProductResource
            .addContentsToProduct(owner.getKey(), product.getId(), Map.of(content.getId(), contentEnabled)));

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testAddContentsToProductDisallowsAddingGlobalContentToProduct(boolean contentEnabled) {
        Owner owner = this.createOwner("test_owner");

        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner.getKey());
        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);

        this.createProduct(product);
        this.createContent(content);

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();

        assertThrows(ForbiddenException.class, () -> this.ownerProductResource
            .addContentsToProduct(owner.getKey(), product.getId(), Map.of(content.getId(), contentEnabled)));

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testRemoveContentFromProduct() {
        Owner owner = this.createOwner("test_owner");

        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner.getKey());
        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner.getKey())
            .addContent(content, true);

        this.createContent(content);
        this.createProduct(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);

        ProductDTO output = this.ownerProductResource.removeContentFromProduct(owner.getKey(),
            product.getId(), content.getId());

        assertThat(output)
            .isNotNull()
            .returns(product.getId(), ProductDTO::getId)
            .extracting(ProductDTO::getProductContent, as(collection(ProductContentDTO.class)))
            .isEmpty();

        assertThat(product.getProductContent())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testRemoveContentFromProductDisallowsRemovingFromProductInGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace((Owner) null)
            .addContent(content, true);

        this.createContent(content);
        this.createProduct(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);

        assertThrows(ForbiddenException.class, () -> this.ownerProductResource
            .removeContentFromProduct(owner.getKey(), product.getId(), content.getId()));

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);
    }

    @Test
    public void testRemoveContentFromProductDisallowsRemovingFromProductInOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner2.getKey());
        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner2.getKey())
            .addContent(content, true);

        this.createContent(content);
        this.createProduct(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);

        assertThrows(NotFoundException.class, () -> this.ownerProductResource
            .removeContentFromProduct(owner1.getKey(), product.getId(), content.getId()));

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);
    }

    @Test
    public void testRemoveContentsFromProduct() {
        Owner owner = this.createOwner("test_owner");

        Content content1 = TestUtil.createContent("test_content-1", "test_content-1")
            .setNamespace(owner.getKey());
        Content content2 = TestUtil.createContent("test_content-2", "test_content-2")
            .setNamespace(owner.getKey());
        Content content3 = TestUtil.createContent("test_content-3", "test_content-3")
            .setNamespace(owner.getKey());
        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner.getKey())
            .addContent(content1, true)
            .addContent(content2, true)
            .addContent(content3, true);

        this.createContent(content1);
        this.createContent(content2);
        this.createContent(content3);
        this.createProduct(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(3);

        System.out.printf("CONTENT IDS %s, %s, %s\n", content1.getId(), content2.getId(), content3.getId());

        List<String> cids = Stream.of(content1, content3)
            .map(Content::getId)
            .toList();

        System.out.printf("COLLECTED: %s\n", cids);

        ProductDTO output = this.ownerProductResource.removeContentsFromProduct(owner.getKey(),
            product.getId(), cids);

        assertThat(output)
            .isNotNull()
            .returns(product.getId(), ProductDTO::getId)
            .extracting(ProductDTO::getProductContent, as(collection(ProductContentDTO.class)))
            .singleElement()
            .extracting(ProductContentDTO::getContent)
            .returns(content2.getId(), ContentDTO::getId);

        assertThat(product.getProductContent())
            .singleElement()
            .extracting(ProductContent::getContent)
            .returns(content2.getId(), Content::getId);
    }

    @Test
    public void testRemoveContentsFromProductDisallowsRemovingFromProductInGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace((Owner) null);
        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace((Owner) null)
            .addContent(content, true);

        this.createContent(content);
        this.createProduct(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);

        assertThrows(ForbiddenException.class, () -> this.ownerProductResource
            .removeContentsFromProduct(owner.getKey(), product.getId(), List.of(content.getId())));

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);
    }

    @Test
    public void testRemoveContentsFromProductDisallowsRemovingFromProductInOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Content content = TestUtil.createContent("test_content", "test_content")
            .setNamespace(owner2.getKey());
        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner2.getKey())
            .addContent(content, true);

        this.createContent(content);
        this.createProduct(product);

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);

        assertThrows(NotFoundException.class, () -> this.ownerProductResource
            .removeContentsFromProduct(owner1.getKey(), product.getId(), List.of(content.getId())));

        assertThat(product.getProductContent())
            .isNotNull()
            .hasSize(1);
    }

    @Test
    public void testCannotUpdateProductInGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Product template = TestUtil.createProduct("test_product", "test_product")
            .setNamespace((Owner) null);
        Product product = this.productCurator.create(template);

        ProductDTO update = TestUtil.createProductDTO(product.getId(), "updated_name");

        assertThrows(ForbiddenException.class,
            () -> this.ownerProductResource.updateProduct(owner.getKey(), update.getId(), update));
    }

    @Test
    public void testCannotUpdateProductInOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Product template = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner2.getKey());
        Product product = this.productCurator.create(template);

        ProductDTO update = TestUtil.createProductDTO(product.getId(), "updated_name");

        assertThrows(NotFoundException.class,
            () -> this.ownerProductResource.updateProduct(owner1.getKey(), update.getId(), update));
    }

    @Test
    public void testRemoveProduct() {
        Owner owner = this.createOwner("test_owner");
        Product product = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner.getKey());
        product = this.productCurator.create(product);

        assertNotNull(this.productCurator.getProductById(owner.getKey(), product.getId()));

        this.ownerProductResource.removeProduct(owner.getKey(), product.getId());

        assertNull(this.productCurator.getProductById(owner.getKey(), product.getId()));
        assertNull(this.productCurator.get(product.getUuid()));
    }

    @Test
    public void testRemoveProductCannitRemoveProductsWithSubscriptions() {
        Owner owner = this.createOwner("test_org");
        Product product = this.productCurator.create(TestUtil.createProduct("p1", "product1")
            .setNamespace(owner.getKey()));
        Pool pool = this.createPool(owner, product);

        Throwable throwable = assertThrows(BadRequestException.class,
            () -> this.ownerProductResource.removeProduct(owner.getKey(), product.getId()));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("referenced by one or more subscriptions");

        assertNotNull(this.productCurator.getProductById(owner.getKey(), product.getId()));
    }

    @Test
    public void testRemoveProductCannotRemoveDerivedProducts() {
        Owner owner = this.createOwner("test_org");

        Product product = TestUtil.createProduct("p1", "product1")
            .setNamespace(owner.getKey());

        Product parent = TestUtil.createProduct("parent", "parent")
            .setDerivedProduct(product);

        this.productCurator.create(product);
        this.productCurator.create(parent);

        Throwable throwable = assertThrows(BadRequestException.class,
            () -> this.ownerProductResource.removeProduct(owner.getKey(), product.getId()));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("referenced by one or more products");

        assertNotNull(this.productCurator.getProductById(owner.getKey(), product.getId()));
    }

    @Test
    public void testRemoveProductCannotRemoveProvidedProducts() {
        Owner owner = this.createOwner("test_org");

        Product product = TestUtil.createProduct("p1", "product1")
            .setNamespace(owner.getKey());

        Product parent = TestUtil.createProduct("parent", "parent")
            .setProvidedProducts(List.of(product));

        this.productCurator.create(product);
        this.productCurator.create(parent);

        Throwable throwable = assertThrows(BadRequestException.class,
            () -> this.ownerProductResource.removeProduct(owner.getKey(), product.getId()));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("referenced by one or more products");

        assertNotNull(this.productCurator.getProductById(owner.getKey(), product.getId()));
    }

    @Test
    public void testCannotRemoveProductFromGlobalNamespace() {
        Owner owner = this.createOwner("test_owner");

        Product template = TestUtil.createProduct("test_product", "test_product")
            .setNamespace((Owner) null);
        Product product = this.productCurator.create(template);

        assertThrows(ForbiddenException.class,
            () -> this.ownerProductResource.removeProduct(owner.getKey(), product.getId()));
    }

    @Test
    public void testCannotRemoveProductFromOtherNamespace() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Product template = TestUtil.createProduct("test_product", "test_product")
            .setNamespace(owner2.getKey());
        Product product = this.productCurator.create(template);

        assertThrows(NotFoundException.class,
            () -> this.ownerProductResource.removeProduct(owner1.getKey(), product.getId()));
    }

    @Test
    public void getProduct() {
        Owner owner = this.createOwner("Example-Corporation");
        Product entity = this.createProduct("test_product", "test_product");

        securityInterceptor.enable();
        ProductDTO result = this.ownerProductResource.getProductById(owner.getKey(), entity.getId());
        ProductDTO expected = this.modelTranslator.translate(entity, ProductDTO.class);

        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    public void getProductCertificate() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct("123", "AwesomeOS Core");
        // ensure we check SecurityHole
        securityInterceptor.enable();

        ProductCertificate cert = new ProductCertificate();
        cert.setCert("some text");
        cert.setKey("some key");
        cert.setProduct(entity);
        productCertificateCurator.create(cert);

        ProductCertificateDTO cert1 = ownerProductResource.getProductCertificateById(owner.getKey(),
            entity.getId());
        ProductCertificateDTO expected = this.modelTranslator.translate(cert, ProductCertificateDTO.class);
        assertEquals(cert1, expected);
    }

    @Test
    public void requiresNumericIdForProductCertificates() {
        Owner owner = this.createOwner("Example-Corporation");

        Product entity = this.createProduct("MCT123", "AwesomeOS");
        securityInterceptor.enable();

        assertThrows(BadRequestException.class,
            () -> ownerProductResource.getProductCertificateById(owner.getKey(), entity.getId()));
    }


    /**
     * Creates a set of products for use with the "testGetProductsByOwner..." family of tests below. Do
     * not make changes to these products unless you are updating the testing for the
     * OwnerProductResource.getProductsByOwner method, and do not use this method to set up data for any
     * other test!
     *
     * Note that this test data does not create full product graphs for products. Testing the definition
     * of "active" is left to a set of explicit tests which validates it in full.
     */
    private void createDataForEndpointQueryTesting() {
        List<Owner> owners = List.of(
            this.createOwner("owner1"),
            this.createOwner("owner2"),
            this.createOwner("owner3"));

        List<Product> globalProducts = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            Product gprod = new Product()
                .setId("g-prod-" + i)
                .setName("global product " + i);

            globalProducts.add(this.productCurator.create(gprod));
        }

        for (int oidx = 1; oidx <= owners.size(); ++oidx) {
            Owner owner = owners.get(oidx - 1);

            List<Product> ownerProducts = new ArrayList<>();

            // create two custom products
            for (int i = 1; i <= 3; ++i) {
                Product cprod = new Product()
                    .setId(String.format("o%d-prod-%d", oidx, i))
                    .setName(String.format("%s product %d", owner.getKey(), i))
                    .setNamespace(owner.getKey());

                ownerProducts.add(this.productCurator.create(cprod));
            }

            // create some pools:
            // - two which references a global product
            // - two which references a custom product
            Pool globalPool1 = this.createPool(owner, globalProducts.get(0));
            Pool globalPool2 = this.createPool(owner, globalProducts.get(1));

            Pool customPool1 = this.createPool(owner, ownerProducts.get(0));
            Pool customPool2 = this.createPool(owner, ownerProducts.get(1));
        }
    }

    @Test
    public void testGetProductsByOwnerFetchesWithNoFiltering() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o2-prod-1", "o2-prod-2",
            "o2-prod-3");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerDefaultsToActiveOnly() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "o2-prod-1", "o2-prod-2");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null, null, null);

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_owner" })
    @NullAndEmptySource
    public void testGetProductsByOwnerErrorsWithInvalidOwners(String ownerKey) {
        this.createDataForEndpointQueryTesting();

        OwnerProductResource resource = this.buildResource();

        assertThrows(NotFoundException.class, () -> resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetProductsByOwnerFetchesWithIDFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> productIds = List.of("g-prod-2", "o1-prod-1", "o2-prod-2", "o3-prod-3");
        List<String> expectedPids = List.of("g-prod-2", "o2-prod-2");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, productIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_prod_id" })
    @NullAndEmptySource
    public void testGetProductsByOwnerFetchesWithInvalidIDs(String invalidProductId) {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> productIds = Arrays.asList("o2-prod-1", invalidProductId);
        List<String> expectedPids = List.of("o2-prod-1");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, productIds, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerFetchesWithNameFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> productNames = Arrays.asList("global product 2", "owner1 product 1", "owner2 product 2",
            "owner3 product 3");
        List<String> expectedPids = List.of("g-prod-2", "o2-prod-2");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, productNames,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid_prod_name" })
    @NullAndEmptySource
    public void testGetProductsByOwnerFetchesWithInvalidNames(String invalidProductName) {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> productNames = Arrays.asList("global product 2", invalidProductName);
        List<String> expectedPids = List.of("g-prod-2");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, productNames,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerFetchesWithOmitActiveFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedPids = List.of("g-prod-3", "o2-prod-3");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.EXCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerFetchesWithOnlyActiveFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "o2-prod-1", "o2-prod-2");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerFetchesWithIncludeActiveFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        // active = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o2-prod-1", "o2-prod-2",
            "o2-prod-3");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerErrorsWithInvalidActiveFilter() {
        Owner owner = this.createOwner("test_org");

        OwnerProductResource resource = this.buildResource();
        assertThrows(BadRequestException.class, () -> resource.getProductsByOwner(owner.getKey(), null, null,
            "invalid_type", Inclusion.INCLUDE.name()));
    }

    @Test
    public void testGetProductsByOwnerFetchesWithOmitCustomFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerFetchesWithOnlyCustomFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedPids = List.of("o2-prod-1", "o2-prod-2", "o2-prod-3");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.EXCLUSIVE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerFetchesWithIncludeCustomFilter() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        // custom = "include" with no other filters is effectively a "fetch the world" kind of option.
        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o2-prod-1", "o2-prod-2",
            "o2-prod-3");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerErrorsWithInvalidCustomFilter() {
        Owner owner = this.createOwner("test_org");

        OwnerProductResource resource = this.buildResource();
        assertThrows(BadRequestException.class, () -> resource.getProductsByOwner(owner.getKey(), null, null,
            Inclusion.INCLUDE.name(), "invalid_type"));
    }

    @Test
    public void testGetProductsByOwnerFetchesWithMultipleFilters() {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        // This test configures a bunch of filters which loosely resolve to the following:
        // - active global products: not custom, not inactive (active = only, custom = omit)
        // - in orgs 2 or 3
        // - matching the given list of product IDs (gp1, gp2, o1p1, o2p1, o3p2)
        // - matching the given list of product names (gp1, gp2, gp3, o2p1, o2p2)
        //
        // These filters should be applied as an intersection, resulting in a singular match on gp1

        List<String> productIds = List.of("g-prod-1", "g-prod-2", "o1-prod-1", "o2-prod-1", "o3-prod-2");
        List<String> productNames = List.of("global product 1", "global product 3", "owner2 product 1",
            "owner2 product 2");
        String activeInclusion = Inclusion.EXCLUSIVE.name();
        String customInclusion = Inclusion.EXCLUDE.name();

        List<String> expectedPids = List.of("g-prod-1");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, productIds, productNames,
            activeInclusion, customInclusion);

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
    public void testGetProductsByOwnerFetchesPagedResults(int pageSize) {
        this.createDataForEndpointQueryTesting();
        String ownerKey = "owner2";

        List<String> expectedPids = List.of("g-prod-1", "g-prod-2", "g-prod-3", "o2-prod-1", "o2-prod-2",
            "o2-prod-3");

        int expectedPages = pageSize < expectedPids.size() ?
            (expectedPids.size() / pageSize) + (expectedPids.size() % pageSize != 0 ? 1 : 0) :
            1;

        OwnerProductResource resource = this.buildResource();

        List<String> found = new ArrayList<>();
        int pages = 0;
        while (pages < expectedPages) {
            // Set the context page request
            ResteasyContext.popContextData(PageRequest.class);
            ResteasyContext.pushContext(PageRequest.class, new PageRequest()
                .setPage(++pages)
                .setPerPage(pageSize));

            Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
                Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

            assertNotNull(output);
            List<String> receivedPids = output.map(ProductDTO::getId)
                .toList();

            if (receivedPids.isEmpty()) {
                break;
            }

            found.addAll(receivedPids);
        }

        assertEquals(expectedPages, pages);
        assertThat(found)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "name", "uuid" })
    public void testGetProductsByOwnerFetchesOrderedResults(String field) {
        this.createDataForEndpointQueryTesting();

        String ownerKey = "owner2";

        Map<String, Comparator<Product>> comparatorMap = Map.of(
            "id", Comparator.comparing(Product::getId),
            "name", Comparator.comparing(Product::getName),
            "uuid", Comparator.comparing(Product::getUuid));

        List<String> expectedPids = this.productCurator.resolveProductsByNamespace(ownerKey)
            .stream()
            .sorted(comparatorMap.get(field))
            .map(Product::getId)
            .toList();

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy(field));

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(ownerKey, null, null,
            Inclusion.INCLUDE.name(), Inclusion.INCLUDE.name());

        // Note that this output needs to be ordered according to our expected ordering!
        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyElementsOf(expectedPids);
    }

    @Test
    public void testGetProductsByOwnerErrorsWithInvalidOrderingRequest() {
        Owner owner = this.createOwner("test_org");

        ResteasyContext.pushContext(PageRequest.class, new PageRequest().setSortBy("invalid_field_name"));

        OwnerProductResource resource = this.buildResource();
        assertThrows(BadRequestException.class, () -> resource.getProductsByOwner(owner.getKey(), null, null,
            null, null));
    }

    // These tests verify the definition of "active" is properly implemented, ensuring "active" is defined
    // as a product which is attached to a pool which has started and has not expired, or attached to
    // another active product (recursively).
    //
    // This definition is recursive in nature, so the effect is that it should consider any product that
    // is a descendant of a product attached to a non-future pool that hasn't yet expired.

    @Test
    public void testGetActiveProductsOnlyConsidersActivePools() {
        // - "active" only considers pools which have started but not expired (start time < now() < end time)
        Owner owner = this.createOwner("test_org");

        Product prod1 = this.createProduct("prod1");
        Product prod2 = this.createProduct("prod2");
        Product prod3 = this.createProduct("prod3");

        Function<Integer, Date> days = (offset) -> TestUtil.createDateOffset(0, 0, offset);

        // Create three pools: expired, current (active), future
        Pool pool1 = this.createPool(owner, prod1, 1L, days.apply(-3), days.apply(-1));
        Pool pool2 = this.createPool(owner, prod2, 1L, days.apply(-1), days.apply(1));
        Pool pool3 = this.createPool(owner, prod3, 1L, days.apply(1), days.apply(3));

        // Active = exclusive should only find the active pool; future and expired pools should be omitted
        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(owner.getKey(), null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .singleElement()
            .isEqualTo(prod2.getId());
    }

    @Test
    public void testGetActiveProductsAlsoConsidersDescendantsOfActivePoolProducts() {
        // - "active" includes descendants of products attached to a pool
        Owner owner = this.createOwner("test_org");

        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 20; ++i) {
            Product product = new Product()
                .setId("p" + i)
                .setName("product " + i);

            products.add(product);
        }

        /*
        pool -> prod - p0
                    derived - p1
                        provided - p2
                        provided - p3
                            provided - p4
                    provided - p5
                    provided - p6

        pool -> prod - p7
                    derived - p8*
                    provided - p9

        pool -> prod - p8*
                    provided - p10
                        provided - p11
                    provided - p12
                        provided - p13

                prod - p14
                    derived - p15
                        provided - p16

                prod - p17
                    provided - p18

        pool -> prod - p19
                prod - p20
        */

        products.get(0).setDerivedProduct(products.get(1));
        products.get(0).addProvidedProduct(products.get(5));
        products.get(0).addProvidedProduct(products.get(6));

        products.get(1).addProvidedProduct(products.get(2));
        products.get(1).addProvidedProduct(products.get(3));

        products.get(3).addProvidedProduct(products.get(4));

        products.get(7).setDerivedProduct(products.get(8));
        products.get(7).addProvidedProduct(products.get(9));

        products.get(8).addProvidedProduct(products.get(10));
        products.get(8).addProvidedProduct(products.get(12));

        products.get(10).addProvidedProduct(products.get(11));

        products.get(12).addProvidedProduct(products.get(13));

        products.get(14).setDerivedProduct(products.get(15));

        products.get(15).setDerivedProduct(products.get(16));

        products.get(17).setDerivedProduct(products.get(18));

        // persist the products in reverse order so we don't hit any hibernate errors
        for (int i = products.size() - 1; i >= 0; --i) {
            this.productCurator.create(products.get(i));
        }

        Pool pool1 = this.createPool(owner, products.get(0));
        Pool pool2 = this.createPool(owner, products.get(7));
        Pool pool3 = this.createPool(owner, products.get(8));
        Pool pool4 = this.createPool(owner, products.get(19));

        List<String> expectedPids = List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9",
            "p10", "p11", "p12", "p13", "p19");

        OwnerProductResource resource = this.buildResource();
        Stream<ProductDTO> output = resource.getProductsByOwner(owner.getKey(), null, null,
            Inclusion.EXCLUSIVE.name(), Inclusion.INCLUDE.name());

        assertThat(output)
            .isNotNull()
            .map(ProductDTO::getId)
            .containsExactlyInAnyOrderElementsOf(expectedPids);
    }

}
