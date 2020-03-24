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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;
import java.util.HashSet;



/**
 * ProductManagerTest
 */
public class ProductManagerTest extends DatabaseTestFixture {

    private EntitlementCertificateGenerator mockEntCertGenerator;
    private ContentAccessManager mockContentAccessManager;
    private ProductManager productManager;

    @BeforeEach
    public void setup() {
        this.mockEntCertGenerator = mock(EntitlementCertificateGenerator.class);
        this.mockContentAccessManager = mock(ContentAccessManager.class);

        this.productManager = new ProductManager(this.mockContentAccessManager, this.mockEntCertGenerator,
            this.ownerContentCurator, this.ownerProductCurator, this.productCurator);
    }

    @Test
    public void testCreateProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");

        assertNull(this.ownerProductCurator.getProductById(owner, "p1"));

        Product output = this.productManager.createProduct(owner, productInfo);

        assertEquals(output, this.ownerProductCurator.getProductById(owner, "p1"));
    }

    @Test
    public void testCreateProductThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");
        Product output = this.productManager.createProduct(owner, productInfo);

        assertNotNull(output);
        assertEquals(output, this.ownerProductCurator.getProductById(owner, productInfo.getId()));

        assertThrows(IllegalStateException.class, () ->
            this.productManager.createProduct(owner, productInfo));
    }

    @Test
    public void testCreateProductMergeWithExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        ProductInfo product1 = TestUtil.createProductInfo("p1", "prod1");
        Product product2 = this.createProduct("p1", "prod1", owner2);

        Product output = this.productManager.createProduct(owner1, product1);

        assertEquals(output.getUuid(), product2.getUuid());
        assertEquals(output, product2);
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
    }

    @Test
    public void testUpdateProductNoChange() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        Product clone = (Product) product.clone();
        clone.setUuid(null);

        Product output = this.productManager.updateProduct(owner, clone, true);

        assertEquals(output.getUuid(), product.getUuid());
        assertEquals(output, product);

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProduct(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        ProductInfo update = TestUtil.createProductInfo("p1", "new product name");

        Product output = this.productManager.updateProduct(owner, update, regenCerts);

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
                eq(owner), eq(product.getId()), anyBoolean());
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
        ProductInfo updateInfo = TestUtil.createProductInfo("p1", "updated product");

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product1, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product2, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product1, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product2, owner2));

        Product output = this.productManager.updateProduct(owner1, updateInfo, regenCerts);

        assertEquals(output.getUuid(), product2.getUuid());
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product1, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product2, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product1, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product2, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner1), eq(product1.getId()), anyBoolean());
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
        ProductInfo updateInfo = TestUtil.createProductInfo("p1", "updated product");

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));

        Product output = this.productManager.updateProduct(owner1, updateInfo, regenCerts);

        assertNotEquals(output.getUuid(), product.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner1), eq(product.getId()), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testUpdateProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "new_name");
        ProductInfo updateInfo = TestUtil.createProductInfo("p1", "new_name");

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));

        assertThrows(IllegalStateException.class,
            () -> this.productManager.updateProduct(owner, updateInfo, false));
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

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.removeContent(content.getId());

        Product output = this.productManager.updateProduct(owner, clone, regenCerts);
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
                eq(owner), eq(product.getId()), anyBoolean());
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

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.removeContent(content.getId());

        Product output = this.productManager.updateProduct(owner1, clone, regenCerts);

        assertNotEquals(product, output);
        assertFalse(output.hasContent(content.getId()));
        assertTrue(product.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner1), eq(product.getId()), anyBoolean());
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

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.clearAttributes();

        assertThrows(IllegalStateException.class,
            () -> this.productManager.updateProduct(owner2, clone, false));
    }

    @Test
    public void testAddContentToProduct() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = this.createProduct("p1", "prod1");
        Content content = this.createContent("c1", "content1", owner);
        this.ownerProductCurator.mapProductToOwners(product, owner);

        Product productClone = (Product) product.clone();
        productClone.setUuid(null);
        Content contentClone = content.clone();
        contentClone.setUuid(null);
        productClone.addContent(contentClone, true);

        Product output = this.productManager.updateProduct(owner, productClone, false);

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

        Product productClone = (Product) product.clone();
        productClone.setUuid(null);
        Content contentClone = content.clone();
        contentClone.setUuid(null);
        productClone.addContent(contentClone, true);

        Product output = this.productManager.updateProduct(owner1, productClone, regenCerts);

        assertNotEquals(product, output);
        assertFalse(product.hasContent(content.getId()));
        assertTrue(output.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(owner1), eq(product.getId()), anyBoolean());
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testCreateProductWithBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(TestUtil.createBranding("eng_prod_id", "brand_name"));

        assertNull(this.ownerProductCurator.getProductById(owner, "p1"));

        Product output = this.productManager.createProduct(owner, product);

        assertEquals(output, this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(1, this.ownerProductCurator.getProductById(owner, "p1").getBranding().size());
    }

    @Test
    public void testUpdateProductWithBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(TestUtil.createBranding("eng_prod_id", "brand_name"));

        Product output = this.productManager.createProduct(owner, product);

        assertEquals(1, output.getBranding().size());

        Product clone = (Product) output.clone();
        clone.setUuid(null);
        clone.addBranding(TestUtil.createBranding("eng_prod_id2", "brand_name2"));

        output = this.productManager.updateProduct(owner, clone, false);
        assertEquals(2, output.getBranding().size());
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenBrandingAdded() {
        Product product = TestUtil.createProduct("p1", "prod1");

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.setBranding(new HashSet<>());
        clone.addBranding(TestUtil.createBranding("prod_id", "Brand Name"));

        assertTrue(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenBrandingUpdated() {
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(new Branding(product, "prod_id", "Brand Name", "OS"));

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        ((Branding) clone.getBranding().toArray()[0]).setName("New Name!");

        assertTrue(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenBrandingRemoved() {
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(new Branding(product, "prod_id", "Brand Name", "OS"));

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.getBranding().clear();

        assertTrue(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testIsChangedByDTOIsFalseWithoutAnyBranding() {
        Product product = TestUtil.createProduct("p1", "prod1");

        Product clone = (Product) product.clone();
        clone.setUuid(null);

        assertFalse(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testIsChangedByDTOIsFalseWhenBrandingWasNotRemovedOrAdded() {
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(new Branding(product, "prod_id", "Brand Name", "OS"));

        Product clone = (Product) product.clone();
        clone.setUuid(null);

        assertFalse(ProductManager.isChangedBy(product, clone));
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

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.getProductContent().stream()
            .filter(pc -> pc.getContent().getId().equals(content.getId()))
            .findFirst()
            .ifPresent(pc -> pc.setEnabled(false));

        Product output = this.productManager.updateProduct(owner1, clone, false);

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
        Product product = TestUtil.createProduct("p1", "prod1");
        Product prov1 = TestUtil.createProduct("prodID2", "OS");
        this.productManager.createProduct(owner, prov1);
        product.addProvidedProduct(prov1);

        assertNull(this.ownerProductCurator.getProductById(owner, "p1"));

        Product output = this.productManager.createProduct(owner, product);

        assertEquals(output, this.ownerProductCurator.getProductById(owner, "p1"));
        assertEquals(1, this.ownerProductCurator.getProductById(owner, "p1")
            .getProvidedProducts().size());
    }

    @Test
    public void testIsChangedByTrueWhenProvidedProductsAdded() {
        Product product = TestUtil.createProduct("p1", "prod1");
        Product clone = (Product) product.clone();
        Product prov1 = TestUtil.createProduct("prodID2", "OS");
        clone.addProvidedProduct(prov1);

        assertTrue(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenProvidedProductRemoved() {
        Product product = TestUtil.createProduct("p1", "prod1");
        Product providedProduct = TestUtil.createProduct("providedProd", "pp");
        product.addProvidedProduct(providedProduct);
        Product clone = (Product) product.clone();
        clone.getProvidedProducts().clear();

        assertTrue(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testUpdateProductWithProvidedProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");

        // Creating actual provided Products
        Product prov1 = TestUtil.createProduct("providedId1", "OS1");
        this.productManager.createProduct(owner, prov1);
        Product prov2 = TestUtil.createProduct("anotherProvidedProductID", "ProvName");
        this.productManager.createProduct(owner, prov2);
        product.addProvidedProduct(prov1);
        Product output = this.productManager.createProduct(owner, product);

        assertEquals(1, output.getProvidedProducts().size());

        Product clone = (Product) product.clone();
        clone.addProvidedProduct(prov2);
        output = this.productManager.updateProduct(owner, clone, false);

        assertEquals(2, output.getProvidedProducts().size());
    }

}
