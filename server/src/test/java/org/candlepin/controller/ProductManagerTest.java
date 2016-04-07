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
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collection;



/**
 * ProductManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ProductManagerTest {

    private Configuration config;

    @Mock private ProductCurator mockProductCurator;
    @Mock private EntitlementCertificateGenerator mockEntCertGenerator;

    private ProductManager productManager;

    @Before
    public void init() throws Exception {
        this.config = new CandlepinCommonTestConfig();

        this.productManager = new ProductManager(
            this.mockProductCurator, this.mockEntCertGenerator, this.config
        );

        doAnswer(returnsFirstArg()).when(this.mockProductCurator).merge(any(Product.class));
        doAnswer(returnsFirstArg()).when(this.mockProductCurator).create(any(Product.class));
        doAnswer(returnsSecondArg()).when(this.mockProductCurator)
            .updateOwnerProductReferences(any(Product.class), any(Product.class), any(Collection.class));
    }

    @Test
    public void testCreateProduct() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        Product output = this.productManager.createProduct(product, owner);

        assertEquals(output, product);
        verify(this.mockProductCurator, times(1)).create(eq(product));
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateProductThatAlreadyExists() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        when(this.mockProductCurator.lookupById(eq(owner), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.createProduct(product, owner);
    }

    @Test
    public void testCreateProductMergeWithExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");

        Product product1 = TestUtil.createProduct("p1", "prod1", owner1);
        Product product2 = TestUtil.createProduct("p1", "prod1", owner2);

        when(this.mockProductCurator.getProductsByVersion(eq(product2.getId()), eq(product2.hashCode())))
            .thenReturn(Arrays.asList(product2));

        Product output = this.productManager.createProduct(product1, owner1);

        assertEquals(output, product2);
        assertTrue(output.getOwners().contains(owner1));
        assertTrue(output.getOwners().contains(owner2));

        verify(this.mockProductCurator, times(1)).merge(eq(product2));
        verify(this.mockProductCurator, never()).create(eq(product1));
    }

    @Test
    public void testUpdateProductNoChange() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        when(this.mockProductCurator.lookupById(eq(owner), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.updateProduct(product, owner, true);

        assertTrue(output == product);

        verify(this.mockProductCurator, times(1)).merge(eq(product));
        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateProduct() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Product update = TestUtil.createProduct("p1", "new product name", owner);

        when(this.mockProductCurator.lookupById(eq(owner), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.updateProduct(update, owner, false);

        assertEquals(output, update);

        verify(this.mockProductCurator, times(1)).merge(eq(product));
        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateProductWithCertRegeneration() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Product update = TestUtil.createProduct("p1", "new product name", owner);

        when(this.mockProductCurator.lookupById(eq(owner), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.updateProduct(update, owner, true);

        assertEquals(output, update);

        verify(this.mockProductCurator, times(1)).merge(eq(product));
        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(product)), anyBoolean());
    }

    @Test
    public void testUpdateProductConvergeWithExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Product product1 = TestUtil.createProduct("p1", "prod1", owner1);
        Product product2 = TestUtil.createProduct("p1", "updated product", owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        when(this.mockProductCurator.lookupById(eq(owner1), eq(product1.getId()))).thenReturn(product1);
        when(this.mockProductCurator.lookupById(eq(owner2), eq(product2.getId()))).thenReturn(product2);
        when(this.mockProductCurator.getProductsByVersion(eq(update.getId()), eq(update.hashCode())))
            .thenReturn(Arrays.asList(product2));

        Product output = this.productManager.updateProduct(update, owner1, false);

        assertTrue(output == product2);
        assertTrue(output.getOwners().contains(owner1));
        assertTrue(output.getOwners().contains(owner2));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateProductConvergeWithExistingWithCertRegeneration() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Product product1 = TestUtil.createProduct("p1", "prod1", owner1);
        Product product2 = TestUtil.createProduct("p1", "updated product", owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        when(this.mockProductCurator.lookupById(eq(owner1), eq(product1.getId()))).thenReturn(product1);
        when(this.mockProductCurator.lookupById(eq(owner2), eq(product2.getId()))).thenReturn(product2);
        when(this.mockProductCurator.getProductsByVersion(eq(update.getId()), eq(update.hashCode())))
            .thenReturn(Arrays.asList(product2));

        Product output = this.productManager.updateProduct(update, owner1, true);

        assertTrue(output == product2);
        assertTrue(output.getOwners().contains(owner1));
        assertTrue(output.getOwners().contains(owner2));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(owner1)), eq(Arrays.asList(product2)), anyBoolean());
    }

    @Test
    public void testUpdateProductDivergeFromExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1", owner1);
        product.addOwner(owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        when(this.mockProductCurator.lookupById(eq(owner1), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.updateProduct(update, owner1, false);

        assertTrue(output != product);
        assertTrue(output.getOwners().contains(owner1));
        assertFalse(output.getOwners().contains(owner2));
        assertFalse(product.getOwners().contains(owner1));
        assertTrue(product.getOwners().contains(owner2));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testUpdateProductDivergeFromExistingWithCertRegeneration() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1", owner1);
        product.addOwner(owner2);
        Product update = TestUtil.createProduct("p1", "updated product", owner1);

        when(this.mockProductCurator.lookupById(eq(owner1), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.updateProduct(update, owner1, true);

        assertTrue(output != product);
        assertTrue(output.getOwners().contains(owner1));
        assertFalse(output.getOwners().contains(owner2));
        assertFalse(product.getOwners().contains(owner1));
        assertTrue(product.getOwners().contains(owner2));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(owner1)), eq(Arrays.asList(output)), anyBoolean());
    }

    @Test(expected = IllegalStateException.class)
    public void testUpdateProductThatDoesntExist() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        this.productManager.updateProduct(product, owner, false);
    }

    @Test
    public void testRemoveProduct() {
        Owner owner = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        when(this.mockProductCurator.lookupById(eq(owner), eq(product.getId()))).thenReturn(product);

        this.productManager.removeProduct(product, owner);

        assertFalse(product.getOwners().contains(owner));

        verify(this.mockProductCurator, times(1)).delete(eq(product));
        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveProductDivergeFromExisting() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1", owner1);
        product.addOwner(owner2);

        when(this.mockProductCurator.lookupById(eq(owner1), eq(product.getId()))).thenReturn(product);

        this.productManager.removeProduct(product, owner1);

        assertFalse(product.getOwners().contains(owner1));
        assertTrue(product.getOwners().contains(owner2));

        verify(this.mockProductCurator, never()).delete(eq(product));
        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveProductThatDoesntExist() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        this.productManager.removeProduct(product, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testRemoveProductWithSubscriptions() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);

        when(this.mockProductCurator.productHasSubscriptions(eq(product), eq(owner))).thenReturn(true);

        this.productManager.removeProduct(product, owner);
    }

    @Test
    public void testRemoveProductContent() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Content content = TestUtil.createContent(owner, "c1");
        product.addContent(content);

        when(this.mockProductCurator.lookupById(eq(owner), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner, false
        );

        assertFalse(output.hasContent(content.getId()));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveProductContentWithCertRegeneration() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Content content = TestUtil.createContent(owner, "c1");
        product.addContent(content);

        when(this.mockProductCurator.lookupById(eq(owner), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner, true
        );

        assertFalse(output.hasContent(content.getId()));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(output)), anyBoolean());
    }

    @Test
    public void testRemoveContentFromSharedProduct() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1", owner1);
        product.addOwner(owner2);

        Content content = TestUtil.createContent(owner1, "c1");
        content.addOwner(owner2);
        product.addContent(content);

        when(this.mockProductCurator.lookupById(eq(owner1), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner1, false
        );

        assertNotEquals(product, output);
        assertFalse(output.hasContent(content.getId()));
        assertTrue(product.hasContent(content.getId()));

        assertFalse(product.getOwners().contains(owner1));
        assertTrue(product.getOwners().contains(owner2));
        assertTrue(output.getOwners().contains(owner1));
        assertFalse(output.getOwners().contains(owner2));

        verifyZeroInteractions(this.mockEntCertGenerator);
    }

    @Test
    public void testRemoveContentFromSharedProductWithCertRegeneration() {
        Owner owner1 = TestUtil.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner 2");
        Product product = TestUtil.createProduct("p1", "prod1", owner1);
        product.addOwner(owner2);

        Content content = TestUtil.createContent(owner1, "c1");
        content.addOwner(owner2);
        product.addContent(content);

        when(this.mockProductCurator.lookupById(eq(owner1), eq(product.getId()))).thenReturn(product);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner1, true
        );

        assertNotEquals(product, output);
        assertFalse(output.hasContent(content.getId()));
        assertTrue(product.hasContent(content.getId()));

        assertFalse(product.getOwners().contains(owner1));
        assertTrue(product.getOwners().contains(owner2));
        assertTrue(output.getOwners().contains(owner1));
        assertFalse(output.getOwners().contains(owner2));

        verify(this.mockEntCertGenerator, times(1))
            .regenerateCertificatesOf(eq(Arrays.asList(owner1)), eq(Arrays.asList(output)), anyBoolean());
    }

    @Test
    public void testRemoveContentFromProductForBadOwner() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Owner owner2 = TestUtil.createOwner("test-owner-2", "Test Owner");
        Product product = TestUtil.createProduct("p1", "prod1", owner);
        Content content = TestUtil.createContent(owner, "c1");
        product.addContent(content);

        Product output = this.productManager.removeProductContent(
            product, Arrays.asList(content), owner2, false
        );

        assertTrue(output == product);
        assertTrue(product.hasContent(content.getId()));
    }
}
