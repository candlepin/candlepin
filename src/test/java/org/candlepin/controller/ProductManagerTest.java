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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * ProductManagerTest
 */
public class ProductManagerTest extends DatabaseTestFixture {

    private EntitlementCertificateService mockEntCertService;
    private ContentAccessManager mockContentAccessManager;
    private ProductManager productManager;

    @BeforeEach
    public void setup() {
        this.mockEntCertService = mock(EntitlementCertificateService.class);
        this.mockContentAccessManager = mock(ContentAccessManager.class);

        this.productManager = new ProductManager(this.mockContentAccessManager, this.mockEntCertService,
            this.productCurator, this.contentCurator, this.activationKeyCurator);
    }

    @Test
    public void testCreateProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Date initialLastContentUpdate = owner.getLastContentUpdate();
        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");

        assertNull(this.productCurator.getProductById(owner.getKey(), productInfo.getId()));

        Product output = this.productManager.createProduct(owner, productInfo);

        assertEquals(output, this.productCurator.getProductById(owner.getKey(), productInfo.getId()));
        assertNotEquals(initialLastContentUpdate, owner.getLastContentUpdate());
    }

    @Test
    public void testCreateProductThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");
        Product output = this.productManager.createProduct(owner, productInfo);

        assertNotNull(output);
        assertEquals(output, this.productCurator.getProductById(owner.getKey(), productInfo.getId()));

        assertThrows(IllegalStateException.class, () ->
            this.productManager.createProduct(owner, productInfo));
    }

    @Test
    public void testCreateProductInOrgUsingLongKey() {
        Owner owner = this.createOwner("test_owner".repeat(25));
        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");

        assertNull(this.productCurator.getProductById(owner.getKey(), productInfo.getId()));

        Product output = this.productManager.createProduct(owner, productInfo);

        assertEquals(output, this.productCurator.getProductById(owner.getKey(), productInfo.getId()));
    }

    @Test
    public void testUpdateProductNoChange() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.createProduct(product);

        Product clone = (Product) product.clone();
        clone.setUuid(null);

        Product output = this.productManager.updateProduct(owner, product, clone, true);

        assertEquals(output.getUuid(), product.getUuid());
        assertEquals(output, product);

        verifyNoInteractions(this.mockEntCertService);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testUpdateProduct(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Date initialLastContentUpdate = owner.getLastContentUpdate();
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.createProduct(product);

        ProductInfo update = TestUtil.createProductInfo("p1", "new product name");

        Product output = this.productManager.updateProduct(owner, product, update, regenCerts);

        assertEquals(output.getName(), update.getName());
        assertNotEquals(initialLastContentUpdate, owner.getLastContentUpdate());

        if (regenCerts) {
            verify(this.mockEntCertService, times(1))
                .regenerateCertificatesOf(eq(owner), eq(product.getId()), eq(true));
        }
        else {
            verifyNoInteractions(this.mockEntCertService);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testAddContentToProductViaUpdate(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        Content content = this.createContent("c1", "content1")
            .setNamespace(owner.getKey());

        this.productCurator.create(product);
        this.contentCurator.create(content);

        Product update = (Product) product.clone();
        update.setUuid(null);

        update.addContent(content, true);

        assertFalse(product.hasContent(content.getId()));

        Product output = this.productManager.updateProduct(owner, product, update, regenCerts);

        assertTrue(output.hasContent(content.getId()));

        if (regenCerts) {
            verify(this.mockEntCertService, times(1))
                .regenerateCertificatesOf(eq(owner), eq(product.getId()), eq(true));
        }
        else {
            verifyNoInteractions(this.mockEntCertService);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveContentFromProductViaUpdate(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Content content = TestUtil.createContent("c1");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        product.addContent(content, true);
        this.contentCurator.create(content);
        this.productCurator.create(product);

        Product clone = (Product) product.clone();
        clone.setUuid(null);
        clone.removeContent(content.getId());

        Product output = this.productManager.updateProduct(owner, product, clone, regenCerts);
        assertFalse(output.hasContent(content.getId()));

        if (regenCerts) {
            verify(this.mockEntCertService, times(1))
                .regenerateCertificatesOf(eq(owner), eq(product.getId()), eq(true));
        }
        else {
            verifyNoInteractions(this.mockEntCertService);
        }
    }

    @Test
    public void testUpdateProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "new_name")
            .setNamespace(owner.getKey());
        ProductInfo updateInfo = TestUtil.createProductInfo("p1", "new_name");

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.updateProduct(owner, product, updateInfo, false));

        assertThat(throwable.getMessage())
            .isNotNull()
            .isEqualTo("product is not a managed entity");
    }

    @Test
    public void testRemoveProduct() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Date initialLastContentUpdate = owner.getLastContentUpdate();

        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        assertNotNull(product.getUuid());

        this.productManager.removeProduct(owner, product);

        assertNull(this.productCurator.get(product.getUuid()));
        assertNotEquals(initialLastContentUpdate, owner.getLastContentUpdate());
    }

    @Test
    public void testRemoveProductWontRemoveDerivedProductWithSingleParent() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        Product parent = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent);

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Product is referenced by one or more parent products");

        // Verify the content was not removed
        assertNotNull(this.productCurator.get(product.getUuid()));
    }

    @Test
    public void testRemoveProductWontRemoveDerivedProductWithMultipleParents() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        Product parent1 = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent1);
        Product parent2 = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent2);

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Product is referenced by one or more parent products");

        // Verify the content was not removed
        assertNotNull(this.productCurator.get(product.getUuid()));
    }

    @Test
    public void testRemoveProductWontRemoveProvidedProductWithSingleParent() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        Product parent = this.createProduct();
        parent.addProvidedProduct(product);
        this.productCurator.merge(parent);

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Product is referenced by one or more parent products");

        // Verify the content was not removed
        assertNotNull(this.productCurator.get(product.getUuid()));
    }

    @Test
    public void testRemoveProductWontRemoveProvidedProductWithMultipleParents() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        Product parent1 = this.createProduct();
        parent1.addProvidedProduct(product);
        this.productCurator.merge(parent1);

        Product parent2 = this.createProduct();
        parent2.addProvidedProduct(product);
        this.productCurator.merge(parent2);

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Product is referenced by one or more parent products");

        // Verify the content was not removed
        assertNotNull(this.productCurator.get(product.getUuid()));
    }

    @Test
    public void testRemoveProductWontRemoveProductWithMultipleParents() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        Product parent1 = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent1);
        Product parent2 = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent2);

        Product parent3 = this.createProduct();
        parent3.addProvidedProduct(product);
        this.productCurator.merge(parent3);

        Product parent4 = this.createProduct();
        parent4.addProvidedProduct(product);
        this.productCurator.merge(parent4);

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Product is referenced by one or more parent products");

        // Verify the content was not removed
        assertNotNull(this.productCurator.get(product.getUuid()));
    }

    @Test
    public void testRemoveProductDoesntAffectUnrelatedProductEntitlements() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Consumer consumer = this.createConsumer(owner);

        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        Product product2 = TestUtil.createProduct("p2", "prod2")
            .setNamespace(owner.getKey());
        this.productCurator.create(product2);

        long now = System.currentTimeMillis();
        Pool pool = this.createPool(owner, product2, 1L, new Date(now - 86400), new Date(now + 86400));
        Entitlement entitlement = this.createEntitlement(owner, consumer, pool);

        assertNotNull(product.getUuid());

        this.productManager.removeProduct(owner, product);

        assertNull(this.productCurator.get(product.getUuid()));
        assertNotNull(this.productCurator.get(product2.getUuid()));
    }

    @Test
    public void testRemoveProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product));

        assertThat(throwable.getMessage())
            .isNotNull()
            .isEqualTo("product is not a managed entity");
    }

    @Test
    public void testRemoveProductWithSubscriptions() {
        long now = System.currentTimeMillis();

        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1");
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));

        Throwable throwable = assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product));

        assertThat(throwable.getMessage())
            .isNotNull()
            .contains("Product is referenced by one or more subscriptions");

        // Verify the product was not removed
        assertNotNull(this.productCurator.get(product.getUuid()));
    }

    @Test
    public void testRemoveProductRemovesActivationKeyReferences() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());

        product = this.productCurator.create(product);

        ActivationKey actkey = new ActivationKey()
            .setOwner(owner)
            .setName("A Test Key");
        actkey.addProduct(product);

        actkey = this.activationKeyCurator.create(actkey);

        assertEquals(Set.of(product.getId()), actkey.getProductIds());

        this.productManager.removeProduct(owner, product);

        this.activationKeyCurator.refresh(actkey);

        assertEquals(Set.of(), actkey.getProductIds());
    }

    @Test
    public void testCreateProductWithBranding() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(TestUtil.createBranding("eng_prod_id", "brand_name"));

        assertNull(this.productCurator.getProductById(owner.getKey(), "p1"));

        Product output = this.productManager.createProduct(owner, product);

        Product fetched = this.productCurator.getProductById(owner.getKey(), "p1");
        assertEquals(output, fetched);
        assertEquals(1, fetched.getBranding().size());
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

        output = this.productManager.updateProduct(owner, output, clone, false);
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
        Branding branding = new Branding("prod_id", "Brand Name", "OS");

        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(branding);

        Product clone = (Product) product.clone();
        clone.setUuid(null);

        clone.removeBranding(branding);
        clone.addBranding(new Branding("prod_id", "Brand New Name", "OS"));

        assertTrue(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testIsChangedByDTOIsTrueWhenBrandingRemoved() {
        Product product = TestUtil.createProduct("p1", "prod1");
        product.addBranding(new Branding("prod_id", "Brand Name", "OS"));

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
        product.addBranding(new Branding("prod_id", "Brand Name", "OS"));

        Product clone = (Product) product.clone();
        clone.setUuid(null);

        assertFalse(ProductManager.isChangedBy(product, clone));
    }

    @Test
    public void testIsChangedByProductInfoIsTrueWhenBrandingAdded() {
        Product existingProduct = TestUtil.createProduct("p1", "prod1");
        Product newProduct = (Product) existingProduct.clone();

        newProduct.addBranding(new Branding("prod_id", "Brand Name", "OS"));

        assertTrue(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testIsChangedByProductInfoIsTrueWhenBrandingUpdated() {
        Branding oldBranding = new Branding("prod_id", "Brand Name", "OS");

        Product existingProduct = TestUtil.createProduct("p1", "prod1");
        existingProduct.addBranding(oldBranding);

        Product newProduct = (Product) existingProduct.clone();

        newProduct.removeBranding(oldBranding);
        newProduct.addBranding(new Branding("prod_id", "Brand New Name", "OS"));

        assertTrue(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testIsChangedByProductInfosTrueWhenBrandingRemoved() {
        Branding oldBranding = new Branding("prod_id", "Brand Name", "OS");

        Product existingProduct = TestUtil.createProduct("p1", "prod1");
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
        Branding oldBranding = new Branding("prod_id", "Brand Name", "OS");

        Product existingProduct = TestUtil.createProduct("p1", "prod1");
        existingProduct.addBranding(oldBranding);

        Product newProduct = (Product) existingProduct.clone();

        assertFalse(ProductManager.isChangedBy(existingProduct, newProduct));
    }

    @Test
    public void testCreateProductWithProvidedProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        Product prov1 = TestUtil.createProduct("prodID2", "OS");
        this.productManager.createProduct(owner, prov1);
        product.addProvidedProduct(prov1);

        assertNull(this.productCurator.getProductById(owner.getKey(), "p1"));

        Product output = this.productManager.createProduct(owner, product);

        assertEquals(output, this.productCurator.getProductById(owner.getKey(), "p1"));
        assertEquals(1, this.productCurator.getProductById(owner.getKey(), "p1")
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
        Product prov1 = TestUtil.createProduct("providedId1", "OS1")
            .setNamespace(owner.getKey());
        this.productManager.createProduct(owner, prov1);

        Product prov2 = TestUtil.createProduct("anotherProvidedProductID", "ProvName")
            .setNamespace(owner.getKey());
        this.productManager.createProduct(owner, prov2);

        Product product = TestUtil.createProduct("p1", "prod1");
        product.addProvidedProduct(prov1);

        product = this.productManager.createProduct(owner, product);

        assertEquals(1, product.getProvidedProducts().size());

        Product clone = (Product) product.clone();
        clone.addProvidedProduct(prov2);

        product = this.productManager.updateProduct(owner, product, clone, false);

        assertEquals(2, product.getProvidedProducts().size());
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

        Product entity = this.productManager.createProduct(owner, pinfo);

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
        Product base = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey())
            .setAttributes(Map.of("attrib-1", "original value"));

        this.createProduct(base);

        ProductInfo pinfo = mock(ProductInfo.class);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value 1");
        attributes.put("attrib-2", null);
        attributes.put("attrib-3", "value 3");

        doReturn(attributes).when(pinfo).getAttributes();
        doReturn(base.getId()).when(pinfo).getId();
        doReturn(base.getName()).when(pinfo).getName();

        Product entity = this.productManager.updateProduct(owner, base, pinfo, regenCerts);

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
