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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.ProductData;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Date;



/**
 * ProductManagerTest
 */
@RunWith(JUnitParamsRunner.class)
public class ProductManagerTest extends DatabaseTestFixture {

    private EntitlementCertificateGenerator mockEntCertGenerator;
    private ProductManager productManager;

    @Before
    public void setup() throws Exception {
        this.mockEntCertGenerator = mock(EntitlementCertificateGenerator.class);

        this.productManager = new ProductManager(
            this.mockEntCertGenerator, this.ownerContentCurator, this.ownerProductCurator,
            this.productCurator, this.productShareManager
        );
    }

    @Test
    public void testCreateProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        ProductData dto = TestUtil.createProductDTO("p1", "prod1");

        assertNull(this.productShareManager.resolveProductById(owner, "p1", false));

        Product output = this.productManager.createProduct(dto, owner);

        assertEquals(output, this.productShareManager.resolveProductById(owner, "p1", false));
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateProductThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");

        ProductData dto = TestUtil.createProductDTO("p1", "prod1");
        Product output = this.productManager.createProduct(dto, owner);

        assertNotNull(output);
        assertEquals(output, this.productShareManager.resolveProductById(owner, dto.getId(), false));

        this.productManager.createProduct(dto, owner);
    }

    @Test
    public void testCreateProductMergeWithExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        Product product1 = TestUtil.createProduct("p1", "prod1");
        Product product2 = this.createProduct("p1", "prod1", owner2);

        Product output = this.productManager.createProduct(product1.toDTO(), owner1);

        assertEquals(output.getUuid(), product2.getUuid());
        assertEquals(output, product2);
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
    }

    @Test
    public void testUpdateProductNoChange() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);

        Product output = this.productManager.updateProduct(product.toDTO(), owner, true);

        assertEquals(output.getUuid(), product.getUuid());
        assertEquals(output, product);

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    @Parameters({"false", "true"})
    public void testUpdateProduct(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        ProductData update = TestUtil.createProductDTO("p1", "new product name");

        Product output = this.productManager.updateProduct(update, owner, regenCerts);

        assertNotEquals(output.getUuid(), product.getUuid());
        assertEquals(output.getName(), update.getName());

        // We expect the original to be kept around as an orphan until the orphan removal job
        // gets around to removing them
        assertNotNull(this.productCurator.find(product.getUuid()));
        assertEquals(0, this.ownerProductCurator.getOwnerCount(product));
        assertNotNull(this.productShareManager.resolveProductById(owner, product.getId(), false));

        if (regenCerts) {
            // TODO: Is there a better way to do this? We won't know the exact product instance,
            // we just know that a product should be refreshed as a result of this operation.
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    @Parameters({"false", "true"})
    public void testUpdateProductConvergeWithExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product1 = this.createProduct("p1", "prod1", owner1);
        Product product2 = this.createProduct("p1", "updated product", owner2);
        ProductData update = TestUtil.createProductDTO("p1", "updated product");

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
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    @Parameters({"false", "true"})
    public void testUpdateProductDivergeFromExisting(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1, owner2);
        ProductData update = TestUtil.createProductDTO("p1", "updated product");

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
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testUpdateProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "new_name");
        ProductData update = TestUtil.createProductDTO("p1", "new_name");

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));

        this.productManager.updateProduct(update, owner, false);
    }

    @Test
    public void testRemoveProduct() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = this.createProduct("p1", "prod1", owner);

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner));

        this.productManager.removeProduct(owner, product);

        // The product will be orphaned, but should still exist
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));
        assertNotNull(this.productCurator.find(product.getUuid()));
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
        assertNotNull(this.productCurator.find(product.getUuid()));
        assertEquals(1, this.ownerProductCurator.getOwnerCount(product));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));

        this.productManager.removeProduct(owner, product);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveProductWithSubscriptions() {
        long now = System.currentTimeMillis();

        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));

        this.productManager.removeProduct(owner, product);
    }

    @Test
    @Parameters({"false", "true"})
    public void testRemoveProductContent(boolean regenCerts) {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1");
        Content content = TestUtil.createContent("c1");
        product.addContent(content, true);
        this.contentCurator.create(content);
        this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwners(product, owner);
        this.ownerContentCurator.mapContentToOwner(content, owner);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner, regenCerts
        );
        assertFalse(output.hasContent(content.getId()));

        // When we change the content associated with a product, we're making a net change to the
        // product itself, which should trigger the creation of a new product object (since reuse
        // is currently disabled). The old product will still, temporarily, exist as an orphan
        // until the orphan cleanup job has a chance to run and remove them.
        assertNotNull(this.productCurator.find(product.getUuid()));
        assertEquals(0, this.ownerProductCurator.getOwnerCount(product));
        assertNotNull(this.productShareManager.resolveProductById(owner, product.getId(), false));
        assertNotNull(this.contentCurator.find(content.getUuid()));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }

    @Test
    @Parameters({"false", "true"})
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

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner1, regenCerts
        );

        assertNotEquals(product, output);
        assertFalse(output.hasContent(content.getId()));
        assertTrue(product.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean()
            );
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

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner2, false
        );

        assertTrue(output == product);
        assertTrue(product.hasContent(content.getId()));
    }

    @Test
    public void testAddContentToProduct() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = this.createProduct("p1", "prod1");
        Content content = this.createContent("c1", "content1", owner);
        this.ownerProductCurator.mapProductToOwners(product, owner);

        Product output = this.productManager.addContentToProduct(
            product, Arrays.asList(new ProductContent(null, content, true)), owner, false
        );

        assertNotEquals(product, output);
        assertTrue(output.hasContent(content.getId()));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    @Parameters({"false", "true"})
    public void testAddContentToSharedProduct(boolean regenCerts) {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1);
        Content content = this.createContent("c1", "content1", owner1);
        this.ownerProductCurator.mapProductToOwners(product, owner1, owner2);

        Product output = this.productManager.addContentToProduct(
            product, Arrays.asList(new ProductContent(null, content, true)), owner1, regenCerts
        );

        assertNotEquals(product, output);
        assertFalse(product.hasContent(content.getId()));
        assertTrue(output.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        if (regenCerts) {
            verify(this.mockEntCertGenerator, times(1)).regenerateCertificatesOf(
                eq(Arrays.asList(owner1)), anyCollectionOf(Product.class), anyBoolean()
            );
        }
        else {
            verifyZeroInteractions(this.mockEntCertGenerator);
        }
    }


    // Move this to ContentManagerTest

    @Test
    public void testUpdateProductContentOnSharedProduct() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1);
        Content content = this.createContent("c1", "content1", owner1);
        this.ownerProductCurator.mapProductToOwners(product, owner1, owner2);

        product.setProductContent(Arrays.asList(new ProductContent(product, content, true)));
        this.productCurator.merge(product);

        Product output = this.productManager.addContentToProduct(
            product, Arrays.asList(new ProductContent(null, content, false)), owner1, false
        );

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
}
