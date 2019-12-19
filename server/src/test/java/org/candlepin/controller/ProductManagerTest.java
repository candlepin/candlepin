/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyCollectionOf;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;



/**
 * ProductManagerTest
 */
public class ProductManagerTest extends DatabaseTestFixture {

    private EntitlementCertificateGenerator mockEntCertGenerator;
    private ProductManager productManager;

    @BeforeEach
    public void setup() throws Exception {
        this.mockEntCertGenerator = mock(EntitlementCertificateGenerator.class);

        this.productManager = new ProductManager(this.mockEntCertGenerator, this.ownerContentCurator,
            this.ownerProductCurator, this.productCurator);
    }

    @Test
    public void testCreateProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductDTO dto = TestUtil.createProductDTO("p1", "prod1");

        assertNull(this.ownerProductCurator.getProductById(owner, "p1"));

        Product output = this.productManager.createProduct(dto, owner);

        assertEquals(output, this.ownerProductCurator.getProductById(owner, "p1"));
    }

    @Test
    public void testCreateProductThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        ProductDTO dto = TestUtil.createProductDTO("p1", "prod1");
        Product output = this.productManager.createProduct(dto, owner);

        assertNotNull(output);
        assertEquals(output, this.ownerProductCurator.getProductById(owner, dto.getId()));

        assertThrows(IllegalStateException.class, () -> this.productManager.createProduct(dto, owner));
    }

    @Test
    public void testCreateProductMergeWithExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        Product product1 = TestUtil.createProduct("p1", "prod1");
        Product product2 = this.createProduct("p1", "prod1", owner2);

        ProductDTO pdto = this.modelTranslator.translate(product1, ProductDTO.class);
        Product output = this.productManager.createProduct(pdto, owner1);

        assertEquals(output.getUuid(), product2.getUuid());
        assertEquals(output, product2);
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
    }

    @Test
    public void testUpdateProductNoChange() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        ProductDTO dto = this.modelTranslator.translate(product, ProductDTO.class);

        Product output = this.productManager.updateProduct(dto, owner, true);

        assertEquals(output.getUuid(), product.getUuid());
        assertEquals(output, product);

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProduct(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        ProductDTO update = TestUtil.createProductDTO("p1", "new product name");

        Product output = this.productManager.updateProduct(update, owner, regenCerts);

        assertNotEquals(output.getUuid(), product.getUuid());
        assertEquals(output.getName(), update.getName());

        // We expect the original to be kept around as an orphan until the orphan removal job
        // gets around to removing them
        assertNotNull(this.productCurator.get(product.getUuid()));
        assertEquals(0, this.ownerProductCurator.getOwnerCount(product));
        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));

        if (regenCerts) {
            // TODO: Is there a better way to do this? We won't know the exact product instance,
            // we just know that a product should be refreshed as a result of this operation.
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner)), anyCollectionOf(Product.class), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProductConvergeWithExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product1 = this.createProduct("p1", "prod1", owner1);
        Product product2 = this.createProduct("p1", "updated product", owner2);
        ProductDTO update = TestUtil.createProductDTO("p1", "updated product");

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product1, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product2, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product1, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product2, owner2));

        Product output = this.productManager.updateProduct(update, owner1, regenCerts);

        assertEquals(output.getUuid(), product2.getUuid());
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product1, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product2, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product1, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product2, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProductDivergeFromExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1, owner2);
        ProductDTO update = TestUtil.createProductDTO("p1", "updated product");

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));

        Product output = this.productManager.updateProduct(update, owner1, regenCerts);

        assertNotEquals(output.getUuid(), product.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testUpdateProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "new_name");
        ProductDTO update = TestUtil.createProductDTO("p1", "new_name");

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));

        assertThrows(IllegalStateException.class,
            () -> this.productManager.updateProduct(update, owner, false));
    }

    @Test
    public void testRemoveProduct() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = this.createProduct("p1", "prod1", owner);

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner));

        this.productManager.removeProduct(owner, product);

        // The product will be orphaned, but should still exist
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));
        assertNotNull(this.productCurator.get(product.getUuid()));
        assertEquals(0, this.ownerProductCurator.getOwnerCount(product));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveProductDivergeFromExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1, owner2);

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));

        this.productManager.removeProduct(owner1, product);

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertNotNull(this.productCurator.get(product.getUuid()));
        assertEquals(1, this.ownerProductCurator.getOwnerCount(product));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));

        assertThrows(IllegalStateException.class, () -> this.productManager.removeProduct(owner, product));
    }

    @Test
    public void testRemoveProductWithSubscriptions() {
        long now = System.currentTimeMillis();

        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));

        assertThrows(IllegalStateException.class, () -> this.productManager.removeProduct(owner, product));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProductContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        Content content = TestUtil.createContent("c1");
        product.addContent(content, true);
        this.contentCurator.create(content);
        this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwners(product, owner);
        this.ownerContentCurator.mapContentToOwner(content, owner);

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        boolean removed = pdto.removeContent(content.getId());
        assertTrue(removed);

        Product output = this.productManager.updateProduct(pdto, owner, regenCerts);
        assertFalse(output.hasContent(content.getId()));

        // When we change the content associated with a product, we're making a net change to the
        // product itself, which should trigger the creation of a new product object (since reuse
        // is currently disabled). The old product will still, temporarily, exist as an orphan
        // until the orphan cleanup job has a chance to run and remove them.
        assertNotNull(this.productCurator.get(product.getUuid()));
        assertEquals(0, this.ownerProductCurator.getOwnerCount(product));
        assertNotNull(this.ownerProductCurator.getProductById(owner, product.getId()));
        assertNotNull(this.contentCurator.get(content.getUuid()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner)), anyCollectionOf(Product.class), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    public void testRemoveContentFromSharedProduct(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1");
        Content content = TestUtil.createContent("c1");
        product.addContent(content, true);
        content = this.contentCurator.create(content);
        product = this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwners(product, owner1, owner2);
        this.ownerContentCurator.mapContentToOwner(content, owner1);

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        boolean removed = pdto.removeContent(content.getId());
        assertTrue(removed);

        Product output = this.productManager.updateProduct(pdto, owner1, regenCerts);

        assertNotEquals(product, output);
        assertFalse(output.hasContent(content.getId()));
        assertTrue(product.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testRemoveContentFromProductForBadOwner() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        Content content = TestUtil.createContent("c1");
        product.addContent(content, true);
        this.contentCurator.create(content);
        this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwner(product, owner);
        this.ownerContentCurator.mapContentToOwner(content, owner);

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        boolean removed = pdto.removeContent(content.getId());
        assertTrue(removed);

        assertThrows(IllegalStateException.class,
            () -> this.productManager.updateProduct(pdto, owner2, false));
    }

    @Test
    public void testAddContentToProduct() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = this.createProduct("p1", "prod1");
        Content content = this.createContent("c1", "content1", owner);
        this.ownerProductCurator.mapProductToOwners(product, owner);

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        ContentDTO cdto = this.modelTranslator.translate(content, ContentDTO.class);
        pdto.addContent(cdto, true);

        Product output = this.productManager.updateProduct(pdto, owner, false);

        assertNotEquals(product, output);
        assertTrue(output.hasContent(content.getId()));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    public void testAddContentToSharedProduct(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1);
        Content content = this.createContent("c1", "content1", owner1);
        this.ownerProductCurator.mapProductToOwners(product, owner1, owner2);

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        ContentDTO cdto = this.modelTranslator.translate(content, ContentDTO.class);
        pdto.addContent(cdto, true);

        Product output = this.productManager.updateProduct(pdto, owner1, regenCerts);

        assertNotEquals(product, output);
        assertFalse(product.hasContent(content.getId()));
        assertTrue(output.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testCreateProductWithBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductDTO dto = TestUtil.createProductDTO("p1", "prod1");
        BrandingDTO brandingDTO = new BrandingDTO();
        brandingDTO.setProductId("eng_prod_id");
        brandingDTO.setName("brand_name");
        brandingDTO.setType("OS");
        dto.addBranding(brandingDTO);

        assertNull(this.ownerProductCurator.getProductById(owner, "p1"));

        Product output = this.productManager.createProduct(dto, owner);

        assertEquals(output, this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(1, this.ownerProductCurator.getProductById(owner, "p1").getBranding().size());
    }

    @Test
    public void testUpdateProductWithBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductDTO dto = TestUtil.createProductDTO("p1", "prod1");
        BrandingDTO brandingDTO = new BrandingDTO();
        brandingDTO.setProductId("eng_prod_id");
        brandingDTO.setName("brand_name");
        brandingDTO.setType("OS");
        dto.addBranding(brandingDTO);

        Product output = this.productManager.createProduct(dto, owner);

        assertEquals(1, output.getBranding().size());

        ProductDTO pdto = this.modelTranslator.translate(output, ProductDTO.class);
        BrandingDTO brandingDTO2 = new BrandingDTO();
        brandingDTO2.setProductId("eng_prod_id2");
        brandingDTO2.setName("brand_name2");
        brandingDTO2.setType("OS");
        pdto.addBranding(brandingDTO2);

        output = this.productManager.updateProduct(pdto, owner, false);
        assertEquals(2, output.getBranding().size());
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenBrandingAdded() {
        Product product = TestUtil.createProduct("p1", "prod1");

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        BrandingDTO brand1 = new BrandingDTO();
        brand1.setProductId("prod_id");
        brand1.setName("Brand Name");
        brand1.setType("OS");
        pdto.addBranding(brand1);

        assertTrue(ProductManager.isChangedBy(product, pdto));
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenBrandingUpdated() {
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(new Branding(product, "prod_id", "Brand Name", "OS"));

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        ((BrandingDTO) pdto.getBranding().toArray()[0]).setName("New Name!");

        assertTrue(ProductManager.isChangedBy(product, pdto));
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenBrandingRemoved() {
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(new Branding(product, "prod_id", "Brand Name", "OS"));

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        pdto.getBranding().clear();

        assertTrue(ProductManager.isChangedBy(product, pdto));
    }

    @Test
    public void testIsChangedByDTOIsFalseWithoutAnyBranding() {
        Product product = TestUtil.createProduct("p1", "prod1");

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);

        assertFalse(ProductManager.isChangedBy(product, pdto));
    }

    @Test
    public void testIsChangedByDTOIsFalseWhenBrandingWasNotRemovedOrAdded() {
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(new Branding(product, "prod_id", "Brand Name", "OS"));

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);

        assertFalse(ProductManager.isChangedBy(product, pdto));
    }

    @Test
    public void testImportProductsCreatesNewProductWithBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        Product product = TestUtil.createProduct("p1", "prod1");
        product.setLocked(true);

        product.addBranding(new Branding(product, "eng_prod_id", "Brand Name", "OS"));
        Map<String, Product> productData = new HashMap<>();
        productData.put("p1", product);

        assertNull(this.ownerProductCurator.getProductById(owner, "p1"));

        ImportResult<Product> result =
            this.productManager.importProducts(owner, productData, new HashMap<>());

        Map<String, Product> importedProducts = result.getImportedEntities();
        Map<String, Product> createdProducts = result.getCreatedEntities();
        Map<String, Product> updatedProducts = result.getUpdatedEntities();

        assertEquals(1, importedProducts.size());
        assertEquals(1, createdProducts.size());
        assertEquals(0, updatedProducts.size());
        assertEquals(importedProducts.get("p1"),
            this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(1, this.ownerProductCurator.getProductById(owner, "p1").getBranding().size());
    }

    @Test
    public void testImportProductsUpdatesProductWhenAddingBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        // Create existing product with a single branding in the db
        Product product = TestUtil.createProduct("p1", "prod1");
        product.setLocked(true);
        product.addBranding(new Branding(null, "eng_prod_id_1", "brand_name_1", "OS"));
        this.createProduct(product, owner);

        assertNotNull(this.ownerProductCurator.getProductById(owner, "p1"));

        // Add a second, new branding
        Product updatedProduct = (Product) product.clone();
        updatedProduct.addBranding(new Branding(null, "eng_prod_id_2", "brand_name_2", "OS"));

        Map<String, Product> productData = new HashMap<>();
        productData.put("p1", updatedProduct);

        ImportResult<Product> result =
            this.productManager.importProducts(owner, productData, new HashMap<>());

        Map<String, Product> importedProducts = result.getImportedEntities();
        Map<String, Product> createdProducts = result.getCreatedEntities();
        Map<String, Product> updatedProducts = result.getUpdatedEntities();

        assertEquals(1, importedProducts.size());
        assertEquals(0, createdProducts.size());
        assertEquals(1, updatedProducts.size());
        assertEquals(updatedProducts.get("p1"),
            this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(2, this.ownerProductCurator.getProductById(owner, "p1").getBranding().size());
    }

    @Test
    public void testImportProductsUpdatesProductWhenRemovingBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        // Create existing product with a single branding in the db
        Product product = TestUtil.createProduct("p1", "prod1");
        product.setLocked(true);
        product.addBranding(new Branding(null, "eng_prod_id_1", "brand_name_1", "OS"));
        this.createProduct(product, owner);

        assertNotNull(this.ownerProductCurator.getProductById(owner, "p1"));


        // Remove the branding
        Product updatedProduct = (Product) product.clone();
        updatedProduct.getBranding().clear();
        Map<String, Product> productData = new HashMap<>();
        productData.put("p1", updatedProduct);

        ImportResult<Product> result =
            this.productManager.importProducts(owner, productData, new HashMap<>());

        Map<String, Product> importedProducts = result.getImportedEntities();
        Map<String, Product> createdProducts = result.getCreatedEntities();
        Map<String, Product> updatedProducts = result.getUpdatedEntities();

        assertEquals(1, importedProducts.size());
        assertEquals(0, createdProducts.size());
        assertEquals(1, updatedProducts.size());
        assertEquals(updatedProducts.get("p1"),
            this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(0, this.ownerProductCurator.getProductById(owner, "p1").getBranding().size());
    }

    @Test
    public void testImportProductsUpdatesProductWhenUpdatingBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        // Create existing product with a single branding in the db
        Product product = TestUtil.createProduct("p1", "prod1");
        product.setLocked(true);
        product.addBranding(new Branding(null, "eng_prod_id_1", "brand_name_1", "OS"));
        this.createProduct(product, owner);

        assertNotNull(this.ownerProductCurator.getProductById(owner, "p1"));

        // Remove the existing branding and add a similar, but slightly different one.
        Branding newVersionOfExistingBranding =
            new Branding(null, "eng_prod_id_1", "Brand New Name!", "OS");
        Product updatedProduct = (Product) product.clone();
        updatedProduct.getBranding().clear();
        updatedProduct.addBranding(newVersionOfExistingBranding);

        Map<String, Product> productData = new HashMap<>();
        productData.put("p1", updatedProduct);

        ImportResult<Product> result =
            this.productManager.importProducts(owner, productData, new HashMap<>());

        Map<String, Product> importedProducts = result.getImportedEntities();
        Map<String, Product> createdProducts = result.getCreatedEntities();
        Map<String, Product> updatedProducts = result.getUpdatedEntities();

        assertEquals(1, importedProducts.size());
        assertEquals(0, createdProducts.size());
        assertEquals(1, updatedProducts.size());
        assertEquals(updatedProducts.get("p1"),
            this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(1, this.ownerProductCurator.getProductById(owner, "p1").getBranding().size());
        assertEquals("Brand New Name!",
            ((Branding) this.ownerProductCurator.getProductById(owner, "p1")
            .getBranding().toArray()[0]).getName());
    }

    @Test
    public void testIsChangedByProductInfoIsTrueWhenBrandingAdded() {
        Product existingProduct = TestUtil.createProduct("p1", "prod1");
        Product newProduct = (Product) existingProduct.clone();

        newProduct.addBranding(new Branding(null, "prod_id", "Brand Name", "OS"));

        assertTrue(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testIsChangedByProductInfoIsTrueWhenBrandingUpdated() {
        Product existingProduct = TestUtil.createProduct("p1", "prod1");
        Branding oldBranding = new Branding(existingProduct, "prod_id", "Brand Name", "OS");
        oldBranding.setId("db_id");
        existingProduct.addBranding(oldBranding);

        Product newProduct = (Product) existingProduct.clone();

        newProduct.removeBranding(oldBranding);
        newProduct.addBranding(new Branding(existingProduct, "prod_id", "Brand New Name", "OS"));

        assertTrue(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testIsChangedByProductInfosTrueWhenBrandingRemoved() {
        Product existingProduct = TestUtil.createProduct("p1", "prod1");
        Branding oldBranding = new Branding(existingProduct, "prod_id", "Brand Name", "OS");
        oldBranding.setId("db_id");
        existingProduct.addBranding(oldBranding);

        Product newProduct = (Product) existingProduct.clone();

        newProduct.removeBranding(oldBranding);

        assertTrue(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testIsChangedByProductInfoIsFalseWithEmptyBranding() {
        Product existingProduct = TestUtil.createProduct("p1", "prod1");

        Product newProduct = (Product) existingProduct.clone();

        assertFalse(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testIsChangedByProductInfoIsFalseWithNullBranding() {
        Product existingProduct = TestUtil.createProduct("p1", "prod1");

        Product newProduct = (Product) existingProduct.clone();
        newProduct.setBranding(null);

        assertFalse(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testIsChangedByProductInfoIsFalseWhenBrandingWasNotRemovedAddedOrUpdated() {
        Product existingProduct = TestUtil.createProduct("p1", "prod1");
        Branding oldBranding = new Branding(existingProduct, "prod_id", "Brand Name", "OS");
        oldBranding.setId("db_id");
        existingProduct.addBranding(oldBranding);

        Product newProduct = (Product) existingProduct.clone();

        newProduct.removeBranding(oldBranding);
        newProduct.addBranding(new Branding(existingProduct, "prod_id", "Brand Name", "OS"));

        assertFalse(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    // Move this to ContentManagerTest

    @Test
    public void testUpdateProductContentOnSharedProduct() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = new Product("p1", "prod1");
        Content content = this.createContent("c1", "content1", owner1);
        product.addContent(content, true);
        product = this.createProduct(product, owner1);
        this.ownerProductCurator.mapProductToOwners(product, owner1, owner2);

        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        pdto.getProductContent(content.getId()).setEnabled(false);

        Product output = this.productManager.updateProduct(pdto, owner1, false);

        assertNotEquals(product, output);
        assertTrue(product.hasContent(content.getId()));
        assertTrue(output.hasContent(content.getId()));
        assertTrue(product.getProductContent().iterator().next().isEnabled());
        assertFalse(output.getProductContent().iterator().next().isEnabled());

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testCreateProductWithProvidedProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductDTO dto = TestUtil.createProductDTO("p1", "prod1");
        ProductDTO prov1 = TestUtil.createProductDTO("prodID2", "OS");
        this.productManager.createProduct(prov1, owner);
        dto.addProvidedProduct(prov1);

        assertNull(this.ownerProductCurator.getProductById(owner, "p1"));

        Product output = this.productManager.createProduct(dto, owner);

        assertEquals(output, this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(1, this.ownerProductCurator.getProductById(owner, "p1")
            .getProvidedProducts().size());
    }

    @Test
    public void testIsChangedByTrueWhenProvidedProductsAdded() {
        Product product = TestUtil.createProduct("p1", "prod1");
        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        ProductDTO prov1 = TestUtil.createProductDTO("prodID2", "OS");
        pdto.addProvidedProduct(prov1);

        assertTrue(ProductManager.isChangedBy(product, pdto));
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenProvidedProductRemoved() {
        Product product = TestUtil.createProduct("p1", "prod1");
        Product providedProduct = TestUtil.createProduct("providedProd", "pp");
        product.addProvidedProduct(providedProduct);
        ProductDTO pdto = this.modelTranslator.translate(product, ProductDTO.class);
        pdto.getProvidedProducts().clear();

        assertTrue(ProductManager.isChangedBy(product, pdto));
    }

    @Test
    public void testUpdateProductWithProvidedProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductDTO dto = TestUtil.createProductDTO("p1", "prod1");

        // Creating actual provided Products
        ProductDTO prov1 = TestUtil.createProductDTO("providedId1", "OS1");
        this.productManager.createProduct(prov1, owner);
        ProductDTO prov2 = TestUtil.createProductDTO("anotherProvidedProductID", "ProvName");
        this.productManager.createProduct(prov2, owner);
        dto.addProvidedProduct(prov1);
        Product output = this.productManager.createProduct(dto, owner);

        assertEquals(1, output.getProvidedProducts().size());

        ProductDTO pdto = this.modelTranslator.translate(output, ProductDTO.class);
        pdto.addProvidedProduct(prov2);
        output = this.productManager.updateProduct(pdto, owner, false);

        assertEquals(2, output.getProvidedProducts().size());
    }

}
