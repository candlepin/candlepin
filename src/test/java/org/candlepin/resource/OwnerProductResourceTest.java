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
import static org.mockito.Mockito.mock;

import org.candlepin.async.JobManager;
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
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;



public class OwnerProductResourceTest extends DatabaseTestFixture {

    private OwnerProductResource ownerProductResource;
    private JobManager jobManager;

    @BeforeEach
    public void setUp() {
        ownerProductResource = this.injector.getInstance(OwnerProductResource.class);
        this.jobManager = mock(JobManager.class);
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
    public void testGetProductsByOwnerFetchesAllVisibleEntities() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Product product1 = TestUtil.createProduct("test_product-1", "test_product-1")
            .setNamespace((Owner) null);
        Product product2 = TestUtil.createProduct("test_product-2", "test_product-2")
            .setNamespace(owner1.getKey());
        Product product3 = TestUtil.createProduct("test_product-3", "test_product-3")
            .setNamespace(owner2.getKey());

        this.createProduct(product1);
        this.createProduct(product2);
        this.createProduct(product3);

        ProductDTO cdto1 = this.modelTranslator.translate(product1, ProductDTO.class);
        ProductDTO cdto2 = this.modelTranslator.translate(product2, ProductDTO.class);

        Stream<ProductDTO> response = this.ownerProductResource
            .getProductsByOwner(owner1.getKey(), List.of(), false);

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(2)
            .containsOnly(cdto1, cdto2);
    }

    @Test
    public void testGetProductsByOwnerOmitsGlobalsWhenOmitFlagIsSet() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Product product1 = TestUtil.createProduct("test_product-1", "test_product-1")
            .setNamespace((Owner) null);
        Product product2 = TestUtil.createProduct("test_product-2", "test_product-2")
            .setNamespace(owner1.getKey());
        Product product3 = TestUtil.createProduct("test_product-3", "test_product-3")
            .setNamespace(owner2.getKey());

        this.createProduct(product1);
        this.createProduct(product2);
        this.createProduct(product3);

        ProductDTO cdto2 = this.modelTranslator.translate(product2, ProductDTO.class);

        Stream<ProductDTO> response = this.ownerProductResource
            .getProductsByOwner(owner1.getKey(), List.of(), true);

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(1)
            .containsOnly(cdto2);
    }

    @Test
    public void testGetProductsByOwnerWithEntityIDFiltering() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Product product1 = TestUtil.createProduct("test_product-1", "test_product-1")
            .setNamespace((Owner) null);
        Product product2 = TestUtil.createProduct("test_product-2", "test_product-2")
            .setNamespace(owner1.getKey());
        Product product3 = TestUtil.createProduct("test_product-3", "test_product-3")
            .setNamespace((Owner) null);
        Product product4 = TestUtil.createProduct("test_product-4", "test_product-4")
            .setNamespace(owner1.getKey());
        Product product5 = TestUtil.createProduct("test_product-5", "test_product-5")
            .setNamespace(owner2.getKey());

        this.createProduct(product1);
        this.createProduct(product2);
        this.createProduct(product3);
        this.createProduct(product4);
        this.createProduct(product5);

        List<String> ids = Stream.of(product3, product4, product5)
            .map(Product::getId)
            .toList();

        Stream<ProductDTO> response = this.ownerProductResource
            .getProductsByOwner(owner1.getKey(), ids, false);

        List<ProductDTO> expected = Stream.of(product3, product4)
            .map(this.modelTranslator.getStreamMapper(Product.class, ProductDTO.class))
            .toList();

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(expected.size())
            .containsAll(expected);
    }

    @Test
    public void testGetProductsByOwnerWithEntityIDFilteringAndOmitGlobalsSet() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Product product1 = TestUtil.createProduct("test_product-1", "test_product-1")
            .setNamespace((Owner) null);
        Product product2 = TestUtil.createProduct("test_product-2", "test_product-2")
            .setNamespace(owner1.getKey());
        Product product3 = TestUtil.createProduct("test_product-3", "test_product-3")
            .setNamespace((Owner) null);
        Product product4 = TestUtil.createProduct("test_product-4", "test_product-4")
            .setNamespace(owner1.getKey());
        Product product5 = TestUtil.createProduct("test_product-5", "test_product-5")
            .setNamespace(owner2.getKey());

        this.createProduct(product1);
        this.createProduct(product2);
        this.createProduct(product3);
        this.createProduct(product4);
        this.createProduct(product5);

        List<String> ids = Stream.of(product3, product4, product5)
            .map(Product::getId)
            .toList();

        Stream<ProductDTO> response = this.ownerProductResource
            .getProductsByOwner(owner1.getKey(), ids, true);

        List<ProductDTO> expected = Stream.of(product4)
            .map(this.modelTranslator.getStreamMapper(Product.class, ProductDTO.class))
            .toList();

        assertNotNull(response);
        assertThat(response.toList())
            .hasSize(expected.size())
            .containsAll(expected);
    }

    @Test
    public void testGetProductsByOwnerWithNoProduct() throws Exception {
        Owner owner = this.createOwner("test_owner");

        Stream<ProductDTO> response = this.ownerProductResource
            .getProductsByOwner(owner.getKey(), List.of(), false);
        assertNotNull(response);

        List<ProductDTO> received = response.toList();
        assertEquals(0, received.size());
    }

    @Test
    public void testGetProductsByOwnerBadOwner() throws Exception {
        Owner owner = this.createOwner("test_owner");

        assertThrows(NotFoundException.class,
            () -> this.ownerProductResource.getProductsByOwner("bad owner", List.of(), false));
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
    public void testRemoveProductWithSubscriptions() {
        Owner owner = this.createOwner("test_org");
        Product product = this.productCurator.create(TestUtil.createProduct("p1", "product1")
            .setNamespace(owner.getKey()));
        Pool pool = this.createPool(owner, product);

        assertThrows(BadRequestException.class,
            () -> this.ownerProductResource.removeProduct(owner.getKey(), product.getId()));
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
}
