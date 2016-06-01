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
import static org.mockito.AdditionalAnswers.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;



/**
 * ProductManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ProductManagerTest extends DatabaseTestFixture {

    private Configuration config;

    @Mock private EntitlementCertificateGenerator mockEntCertGenerator;

    private ProductManager productManager;

    @Before
    public void setup() throws Exception {
        this.config = new CandlepinCommonTestConfig();

        this.productManager = new ProductManager(
            this.productCurator, this.ownerProductCurator, this.mockEntCertGenerator, this.config
        );
    }

    @Test
    public void testCreateProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        Product output = this.productManager.createProduct(product, owner);

        assertEquals(output, product);
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateProductThatAlreadyExists() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        this.ownerCurator.create(owner);

        Product product1 = TestUtil.createProduct("p1", "prod1", owner);
        Product output = this.productManager.createProduct(product1, owner);

        Product product2 = TestUtil.createProduct("p1", "prod1", owner);
        this.productManager.createProduct(product2, owner);
    }

    @Test
    public void testCreateProductMergeWithExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        Product product1 = TestUtil.createProduct("p1", "prod1", owner1);
        Product product2 = this.createProduct("p1", "prod1", owner2);

        Product output = this.productManager.createProduct(product1, owner1);

        assertEquals(output.getUuid(), product2.getUuid());
        assertEquals(output, product2);
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
    }

    // @Test
    // public void testUpdateProductNoChange() {
    //     ProductManager.log.debug("STARTING TEST");

    //     Owner owner = this.createOwner("test-owner", "Test Owner");
    //     Product product = this.createProduct("p1", "prod1", owner);

    //     Product output = this.productManager.updateProduct(product, owner, true);

    //     assertEquals(output.getUuid(), product.getUuid());
    //     assertEquals(output, product);

    //     verifyZeroInteractions(this.mockEntCertGenerator);
    // }

    @Test
    public void testUpdateProduct() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        Product update = TestUtil.createProduct("p1", "new product name", owner);

        Product output = this.productManager.updateProduct(update, owner, false);

        assertEquals(output.getUuid(), product.getUuid());
        assertEquals(output, update);

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateProductWithCertRegeneration() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        Product update = TestUtil.createProduct("p1", "new product name", owner);

        Product output = this.productManager.updateProduct(update, owner, true);

        assertEquals(output.getUuid(), product.getUuid());
        assertEquals(output, update);

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(product)), anyBoolean());
    }

    @Test
    public void testUpdateProductConvergeWithExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product1 = this.createProduct("p1", "prod1", owner1);
        Product product2 = this.createProduct("p1", "updated product", owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        Product output = this.productManager.updateProduct(update, owner1, false);

        assertEquals(output.getUuid(), product2.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateProductConvergeWithExistingWithCertRegeneration() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product1 = this.createProduct("p1", "prod1", owner1);
        Product product2 = this.createProduct("p1", "updated product", owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        Product output = this.productManager.updateProduct(update, owner1, true);

        assertEquals(output.getUuid(), product2.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(owner1)), eq(Arrays.asList(product2)), anyBoolean());
    }

    @Test
    public void testUpdateProductDivergeFromExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1, owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        Product output = this.productManager.updateProduct(update, owner1, false);

        assertNotEquals(output.getUuid(), product.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateProductDivergeFromExistingWithCertRegeneration() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1, owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        Product output = this.productManager.updateProduct(update, owner1, true);

        assertNotEquals(output.getUuid(), product.getUuid());
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(owner1)), eq(Arrays.asList(output)), anyBoolean());
    }

    @Test(expected = IllegalStateException.class)
    public void testUpdateProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product update = TestUtil.createProduct("p1", "prod1", owner);

        this.productManager.updateProduct(update, owner, false);
    }

    @Test
    public void testRemoveProduct() {
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");
        Product product = this.createProduct("p1", "prod1", owner);

        this.productManager.removeProduct(product, owner);

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner));
        assertNull(this.productCurator.find(product.getUuid()));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveProductDivergeFromExisting() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = this.createProduct("p1", "prod1", owner1, owner2);

        this.productManager.removeProduct(product, owner1);

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertNotNull(this.productCurator.find(product.getUuid()));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveProductThatDoesntExist() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        this.productManager.removeProduct(product, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveProductWithSubscriptions() {
        long now = System.currentTimeMillis();

        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = this.createProduct("p1", "prod1", owner);
        Pool pool = this.createPool(owner, product, 1L, new Date(now - 86400), new Date(now + 86400));

        this.productManager.removeProduct(product, owner);
    }

    @Test
    public void testRemoveProductContent() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Content content = TestUtil.createContent(owner, "c1");
        product.addContent(content);
        this.contentCurator.create(content);
        this.productCurator.create(product);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner, false
        );

        assertFalse(output.hasContent(content.getId()));
        assertNotNull(this.productCurator.find(product.getUuid()));
        assertNotNull(this.contentCurator.find(content.getUuid()));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveProductContentWithCertRegeneration() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Content content = TestUtil.createContent(owner, "c1");
        product.addContent(content);
        this.contentCurator.create(content);
        this.productCurator.create(product);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner, true
        );

        assertFalse(output.hasContent(content.getId()));
        assertNotNull(this.productCurator.find(product.getUuid()));
        assertNotNull(this.contentCurator.find(content.getUuid()));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(output)), anyBoolean());
    }

    @Test
    public void testRemoveContentFromSharedProduct() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1", owner1);
        Content content = TestUtil.createContent(owner1, "c1");
        product.addContent(content);
        content = this.contentCurator.create(content);
        product = this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwners(product, owner1, owner2);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner1, false
        );

        assertNotEquals(product, output);
        assertFalse(output.hasContent(content.getId()));
        assertTrue(product.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveContentFromSharedProductWithCertRegeneration() {
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1", owner1);
        Content content = TestUtil.createContent(owner1, "c1");
        product.addContent(content);
        content = this.contentCurator.create(content);
        product = this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwners(product, owner1, owner2);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner1, true
        );

        assertNotEquals(product, output);
        assertFalse(output.hasContent(content.getId()));
        assertTrue(product.hasContent(content.getId()));

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(output, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(output, owner2));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(owner1)), eq(Arrays.asList(output)), anyBoolean());
    }

    @Test
    public void testRemoveContentFromProductForBadOwner() {
        Owner owner = this.createOwner("test-owner", "Test Owner");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Content content = TestUtil.createContent(owner, "c1");
        product.addContent(content);
        this.contentCurator.create(content);
        this.productCurator.create(product);
        this.ownerProductCurator.mapProductToOwner(product, owner);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner2, false
        );

        assertTrue(output == product);
        assertTrue(product.hasContent(content.getId()));
    }
}
