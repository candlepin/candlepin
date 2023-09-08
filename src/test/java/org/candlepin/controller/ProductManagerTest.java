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
package org.candlepin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;



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
            this.productCurator, this.contentCurator);
    }

    @Test
    public void testCreateProduct() {
        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");

        assertNull(this.productCurator.getProductById("p1"));

        Product output = this.productManager.createProduct(productInfo);

        assertEquals(output, this.productCurator.getProductById("p1"));
    }

    @Test
    public void testCreateProductThatAlreadyExists() {
        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");
        Product output = this.productManager.createProduct(productInfo);

        assertNotNull(output);
        assertEquals(output, this.productCurator.getProductById(productInfo.getId()));

        assertThrows(IllegalStateException.class, () -> this.productManager.createProduct(productInfo));
    }

    @Test
    public void testUpdateProductNoChange() {
        Product product = this.createProduct("p1", "prod1");
        Product clone = (Product) product.clone();

        Product output = this.productManager.updateProduct(product, clone, true);

        assertEquals(output, product);

        verifyNoInteractions(this.mockEntCertGenerator);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProduct(boolean regenCerts) {
        Product product = this.createProduct("p1", "prod1");
        ProductInfo update = TestUtil.createProductInfo("p1", "new product name");

        Product output = this.productManager.updateProduct(product, update, regenCerts);

        assertEquals(output.getName(), update.getName());

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1))
                .regenerateCertificatesForProduct(eq(product), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testDeleteProduct() {
        Product product = this.createProduct("p1", "prod1");

        assertTrue(this.productCurator.productExistsById(product.getId()));

        this.productManager.deleteProduct(product);

        assertFalse(this.productCurator.productExistsById(product.getId()));
        verifyNoInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testDeleteProductThatDoesntExist() {
        Product product = TestUtil.createProduct("p1", "prod1");
        assertThrows(IllegalStateException.class, () -> this.productManager.deleteProduct(product));
    }

    @Test
    public void testDeleteProductWithSubscriptions() {
        long now = System.currentTimeMillis();

        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));

        assertThrows(IllegalStateException.class, () -> this.productManager.deleteProduct(product));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testDeleteProductContent(boolean regenCerts) {
        Product product = TestUtil.createProduct("p1", "prod1");
        Content content = TestUtil.createContent("c1");
        product.addContent(content, true);
        this.contentCurator.create(content);
        this.productCurator.create(product);

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.removeContent(content.getId());

        Product output = this.productManager.updateProduct(product, clone, regenCerts);
        assertFalse(output.hasContent(content.getId()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1))
                .regenerateCertificatesForProduct(eq(product), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testAddContentToProduct(boolean regenCerts) {
        Product product = this.createProduct("p1", "prod1");
        Content content = this.createContent("c1", "content1");

        Product update = new Product();
        update.addContent(content, true);

        Product output = this.productManager.updateProduct(product, update, regenCerts);
        assertNotNull(output);

        Collection<ProductContent> productContent = output.getProductContent();
        assertNotNull(productContent);
        assertEquals(1, productContent.size());

        Collection<Content> contentList = productContent.stream()
            .map(ProductContent::getContent)
            .toList();

        assertTrue(contentList.contains(content));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1))
                .regenerateCertificatesForProduct(eq(product), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    public void testCreateProductWithBranding() {
        Branding branding = TestUtil.createBranding("eng_prod_id", "brand_name");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setBranding(List.of(branding));

        assertNull(this.productCurator.getProductById("p1"));

        Product output = this.productManager.createProduct(product);
        assertNotNull(output);

        Collection<Branding> brandingList = output.getBranding();
        assertNotNull(brandingList);
        assertEquals(1, brandingList.size());
        assertTrue(brandingList.contains(branding));

        Product fetched = this.productCurator.getProductById("p1");
        assertNotNull(fetched);

        brandingList = fetched.getBranding();
        assertNotNull(brandingList);
        assertEquals(1, brandingList.size());
        assertTrue(brandingList.contains(branding));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProductWithBranding(boolean regenCerts) {
        Branding branding1 = TestUtil.createBranding("eng_prod_id", "brand_name");
        Branding branding2 = TestUtil.createBranding("eng_prod_id2", "brand_name2");

        Product product = TestUtil.createProduct("p1", "prod1")
            .setBranding(List.of(branding1));

        product = this.productManager.createProduct(product);
        assertNotNull(product);

        Collection<Branding> brandingList = product.getBranding();
        assertNotNull(brandingList);
        assertEquals(1, brandingList.size());
        assertTrue(brandingList.contains(branding1));
        assertFalse(brandingList.contains(branding2));



        Product update = new Product()
            .setBranding(List.of(branding1, branding2));

        product = this.productManager.updateProduct(product, update, regenCerts);
        assertNotNull(product);

        brandingList = product.getBranding();
        assertNotNull(brandingList);
        assertEquals(2, brandingList.size());
        assertTrue(brandingList.contains(branding1));
        assertTrue(brandingList.contains(branding2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1))
                .regenerateCertificatesForProduct(eq(product), anyBoolean());
        }
        else {
            verifyNoInteractions(this.mockEntCertGenerator);
        }
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

    @Test
    public void testCreateProductWithProvidedProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        Product prov1 = TestUtil.createProduct("prodID2", "OS");
        this.productManager.createProduct(prov1);
        product.addProvidedProduct(prov1);

        assertNull(this.productCurator.getProductById("p1"));

        Product output = this.productManager.createProduct(product);

        assertEquals(output, this.productCurator.getProductById("p1"));
        assertEquals(1, this.productCurator.getProductById("p1")
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

        // Creating actual provided Products
        Product prov1 = new Product()
            .setId("providedId1")
            .setName("OS1");

        Product prov2 = new Product()
            .setId("anotherProvidedProductID")
            .setName("ProvName");

        this.productManager.createProduct(prov1);
        this.productManager.createProduct(prov2);

        Product product = new Product()
            .setId("p1")
            .setName("prod1")
            .setProvidedProducts(List.of(prov1));

        product = this.productManager.createProduct(product);
        assertNotNull(product);
        assertNotNull(product.getUuid());

        Collection<Product> provprods = product.getProvidedProducts();
        assertNotNull(provprods);
        assertEquals(1, provprods.size());
        assertTrue(provprods.contains(prov1));
        assertFalse(provprods.contains(prov2));

        Product update = new Product()
            .setProvidedProducts(List.of(prov2));

        Product output = this.productManager.updateProduct(product, update, false);
        assertNotNull(output);

        provprods = output.getProvidedProducts();
        assertNotNull(provprods);
        assertEquals(1, provprods.size());
        assertFalse(provprods.contains(prov1));
        assertTrue(provprods.contains(prov2));
    }

    @Test
    public void testCreateProductFiltersNullValuedAttributes() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductInfo pinfo = mock(ProductInfo.class);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value 1");
        attributes.put("attrib-2", null);
        attributes.put("attrib-3", "value 3");

        doReturn(attributes).when(pinfo).getAttributes();
        doReturn("p1").when(pinfo).getId();
        doReturn("prod1").when(pinfo).getName();

        Product entity = this.productManager.createProduct(pinfo);

        assertNotNull(entity);
        assertNotNull(entity.getAttributes());

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                assertTrue(entity.hasAttribute(entry.getKey()), "expected attribute is not present: " +
                    entry.getKey());

                assertEquals(entry.getValue(), entity.getAttributeValue(entry.getKey()));
            }
            else {
                assertFalse(entity.hasAttribute(entry.getKey()), "unexpected attribute is present: " +
                    entry.getKey());
            }
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProductFiltersNullValuedAttributes(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product base = TestUtil.createProduct("p1", "prod1");
        base.setAttributes(Map.of("attrib-1", "original value"));
        this.createProduct(base, owner);

        ProductInfo pinfo = mock(ProductInfo.class);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value 1");
        attributes.put("attrib-2", null);
        attributes.put("attrib-3", "value 3");

        doReturn(attributes).when(pinfo).getAttributes();
        doReturn(base.getId()).when(pinfo).getId();
        doReturn(base.getName()).when(pinfo).getName();

        Product entity = this.productManager.updateProduct(base, pinfo, regenCerts);

        assertNotNull(entity);
        assertNotNull(entity.getAttributes());

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                assertTrue(entity.hasAttribute(entry.getKey()), "expected attribute is not present: " +
                    entry.getKey());

                assertEquals(entry.getValue(), entity.getAttributeValue(entry.getKey()));
            }
            else {
                assertFalse(entity.hasAttribute(entry.getKey()), "unexpected attribute is present: " +
                    entry.getKey());
            }
        }
    }

}
