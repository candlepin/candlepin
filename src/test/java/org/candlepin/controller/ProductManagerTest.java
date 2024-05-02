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
            this.poolCurator, this.productCurator, this.contentCurator, this.activationKeyCurator);
    }

    @Test
    public void testCreateProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductInfo productInfo = TestUtil.createProductInfo("p1", "prod1");

        assertNull(this.productCurator.getProductById(owner.getKey(), productInfo.getId()));

        Product output = this.productManager.createProduct(owner, productInfo);

        assertEquals(output, this.productCurator.getProductById(owner.getKey(), productInfo.getId()));
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
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.createProduct(product);

        ProductInfo update = TestUtil.createProductInfo("p1", "new product name");

        Product output = this.productManager.updateProduct(owner, product, update, regenCerts);

        assertEquals(output.getName(), update.getName());

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
        Product product = TestUtil.createProduct("p1", "new_name");
        ProductInfo updateInfo = TestUtil.createProductInfo("p1", "new_name");

        assertThrows(IllegalStateException.class,
            () -> this.productManager.updateProduct(owner, product, updateInfo, false));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProduct(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        this.productCurator.create(product);

        assertNotNull(product.getUuid());

        this.productManager.removeProduct(owner, product, regenCerts);

        assertNull(this.productCurator.get(product.getUuid()));

        verifyNoInteractions(this.mockEntCertService);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProductDoesntAffectUnrelatedProductEntitlements(boolean regenCerts) {
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

        this.productManager.removeProduct(owner, product, regenCerts);

        assertNull(this.productCurator.get(product.getUuid()));
        assertNotNull(this.productCurator.get(product2.getUuid()));

        verifyNoInteractions(this.mockEntCertService);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveDerivedProductDoesNotTriggerEntitlementCertRegeneration(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Consumer consumer = this.createConsumer(owner);

        Product childProduct = TestUtil.createProduct("cp1", "child1")
            .setNamespace(owner.getKey());

        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey())
            .setDerivedProduct(childProduct);

        childProduct = this.productCurator.create(childProduct);
        product = this.productCurator.create(product);

        long now = System.currentTimeMillis();
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));
        Entitlement entitlement = this.createEntitlement(owner, consumer, pool);

        Product output = this.productManager.removeProduct(owner, childProduct, regenCerts);
        assertEquals(childProduct, output);

        // Impl note:
        // Derived products themselves don't directly impact content visibility for a given pool or
        // consumer. Instead, typically a derived product will have a derived/bonus pool associated
        // with it which will straight up prevent a derived product from being deleted (since it
        // will have an associated pool). However, in the odd case where it *doesn't* have a derived
        // pool and it's simply linked to another product, we would not expect that removal and
        // unlinking to trigger any entitlement regeneration hits.

        verifyNoInteractions(this.mockEntCertService);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProvidedProductTriggersEntitlementCertRegeneration(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Consumer consumer = this.createConsumer(owner);

        Product childProduct = TestUtil.createProduct("cp1", "child1")
            .setNamespace(owner.getKey());

        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());
        product.addProvidedProduct(childProduct);

        childProduct = this.productCurator.create(childProduct);
        product = this.productCurator.create(product);

        long now = System.currentTimeMillis();
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));
        Entitlement entitlement = this.createEntitlement(owner, consumer, pool);

        Product output = this.productManager.removeProduct(owner, childProduct, regenCerts);
        assertEquals(childProduct, output);

        if (regenCerts) {
            // TODO: Is there a better way to do this? We won't know the exact product instance,
            // we just know that a product should be refreshed as a result of this operation.
            verify(this.mockEntCertService, times(1))
                .regenerateCertificatesOf(eq(Set.of(entitlement)), eq(true));
        }
        else {
            verifyNoInteractions(this.mockEntCertService);
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProductThatDoesntExist(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");

        assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product, regenCerts));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProductWithSubscriptions(boolean regenCerts) {
        long now = System.currentTimeMillis();

        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1");
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));

        assertThrows(IllegalStateException.class,
            () -> this.productManager.removeProduct(owner, product, regenCerts));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProductContent(boolean regenCerts) {
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

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testRemoveProductRemovesActivationKeyReferences(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        Product product = TestUtil.createProduct("p1", "prod1")
            .setNamespace(owner.getKey());

        product = this.productCurator.create(product);

        ActivationKey actkey = new ActivationKey()
            .setOwner(owner)
            .setName("A Test Key")
            .addProduct(product);

        actkey = this.activationKeyCurator.create(actkey);

        assertEquals(Set.of(product.getId()), actkey.getProductIds());

        this.productManager.removeProduct(owner, product, regenCerts);

        this.activationKeyCurator.refresh(actkey);

        assertEquals(Set.of(), actkey.getProductIds());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"false", "true"})
    public void testAddContentToProduct(boolean regenCerts) {
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
