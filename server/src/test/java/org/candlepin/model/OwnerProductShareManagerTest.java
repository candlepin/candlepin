/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.Assert.*;

import org.candlepin.controller.OwnerProductShareManager.ResolvedProduct;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for OwnerProductShareCurator.
 */
public class OwnerProductShareManagerTest extends DatabaseTestFixture {

    private Owner owner;
    private Owner recipientOwner;

    @Before
    public void setUp() {
        owner = createOwner();
        recipientOwner = createOwner();
    }

    @Test
    public void testResolvesToManifestProduct() {
        Product product = createProduct("testTaylor", "testTaylor", owner);
        Product product2 = createProduct("testTaylor", "testTaylor", recipientOwner);

        assertNotEquals(product.getUuid(), product2.getUuid());
        Product resolvedProduct = productShareManager.resolveProductById(recipientOwner, product.getId(),
            true);
        assertEquals(product2.getUuid(), resolvedProduct.getUuid());
    }

    @Test
    public void testResolvesToSharedProduct() {
        Product product = createProduct("testTaylor", "testTaylor", owner);
        Product resolvedProduct = productShareManager.resolveProductById(recipientOwner, product.getId(),
            true);
        assertNull(resolvedProduct);

        createShare(owner, recipientOwner, product, true);
        resolvedProduct = productShareManager.resolveProductById(recipientOwner, product.getId(),
            true);
        assertEquals(product.getUuid(), resolvedProduct.getUuid());
    }

    @Test
    public void testResolvesFromMultipleShares() {
        Product product = createProduct("testTaylor", "testTaylor", owner);
        OwnerProductShare share = createShare(owner, recipientOwner, product, true);
        Product resolvedProduct = productShareManager.resolveProductById(recipientOwner, product.getId(),
            true);
        assertEquals(product.getUuid(), resolvedProduct.getUuid());
        Owner anotherowner = createOwner();
        Product product2 = createProduct("testTaylor", "testTaylor", anotherowner);
        createShare(anotherowner, recipientOwner, product2, true);
        share.setActive(false);
        productShareCurator.merge(share);
        resolvedProduct = productShareManager.resolveProductById(recipientOwner, product.getId(),
            true);
        assertEquals(product2.getUuid(), resolvedProduct.getUuid());
    }

    @Test
    public void testResolvesFromSharesAndManifest() {
        Product productA = createProduct("testTaylor", "testTaylor", recipientOwner);
        Product productA1 = createProduct("testTaylor", "testTaylor", owner);
        Product productB = createProduct("testSwift", "testSwift", owner);
        createShare(owner, recipientOwner, productB, true);
        createShare(owner, recipientOwner, productA1, true);
        Set<String> productIds = new HashSet<String>();
        productIds.add(productA.getId());
        productIds.add(productB.getId());
        List<Product> resolvedProducts = productShareManager.resolveProductsByIds(recipientOwner, productIds,
            true);
        assertEquals(2, resolvedProducts.size());
        boolean aFound = false;
        boolean bFound = false;
        for (Product product : resolvedProducts) {
            if (product.getId().contentEquals(productA.getId())) {
                assertEquals(productA, product);
                aFound = true;
            }
            if (product.getId().contentEquals(productB.getId())) {
                assertEquals(productB, product);
                bFound = true;
            }
        }
        assertTrue(aFound);
        assertTrue(bFound);
    }

    @Test
    public void testAddNewShareWithExistingManifestProduct() {
        Product productA = createProduct("testTaylor", "testTaylor", recipientOwner);
        Product productA1 = createProduct("testTaylor", "testTaylor", owner);
        Set<Product> sharedProducts = new HashSet<Product>();
        sharedProducts.add(productA1);

        //product returned is from recipient, but active share record is created
        Map<String, ResolvedProduct> resolvedProducts = productShareManager
            .resolveProductsAndUpdateProductShares(owner, recipientOwner, sharedProducts);
        assertEquals(1, resolvedProducts.size());
        assertEquals(productA, resolvedProducts.get(productA1.getId()).getProduct());
        assertEquals(false, resolvedProducts.get(productA1.getId()).isRefreshDue());

        List<OwnerProductShare> shares = productShareCurator
            .findProductSharesByRecipient(recipientOwner, false, null);
        assertEquals(1, shares.size());
        OwnerProductShare share = shares.get(0);
        assertEquals(owner, share.getOwner());
        assertEquals(recipientOwner, share.getRecipientOwner());
        assertEquals(productA1, share.getProduct());
        assertEquals(true, share.isActive());
    }

    @Test
    public void testAddNewShareWithNonExistingManifestProduct() {
        Product productA1 = createProduct("testTaylor", "testTaylor", owner);
        Set<Product> sharedProducts = new HashSet<Product>();
        sharedProducts.add(productA1);

        //product returned is from recipient, but active share record is created
        Map<String, ResolvedProduct> resolvedProducts = productShareManager
            .resolveProductsAndUpdateProductShares(owner, recipientOwner, sharedProducts);
        assertEquals(1, resolvedProducts.size());
        assertEquals(productA1, resolvedProducts.get(productA1.getId()).getProduct());
        assertEquals(false, resolvedProducts.get(productA1.getId()).isRefreshDue());

        List<OwnerProductShare> shares = productShareCurator
            .findProductSharesByRecipient(recipientOwner, false, null);
        assertEquals(1, shares.size());
        OwnerProductShare share = shares.get(0);
        assertEquals(owner, share.getOwner());
        assertEquals(recipientOwner, share.getRecipientOwner());
        assertEquals(productA1, share.getProduct());
        assertEquals(true, share.isActive());
    }


    @Test
    public void testUpdateExistingShare() {
        Product productA1 = createProduct("testTaylor", "testTaylor", owner);
        Owner anotherowner = createOwner();
        Product productA2 = createProduct("testTaylor", "testTaylor", anotherowner);

        // first share from owner
        Map<String, ResolvedProduct> resolvedProducts = productShareManager.
            resolveProductsAndUpdateProductShares(owner, recipientOwner, Collections.singleton(productA1));

        assertEquals(1, resolvedProducts.size());
        assertEquals(productA1, resolvedProducts.get(productA1.getId()).getProduct());
        assertEquals(false, resolvedProducts.get(productA1.getId()).isRefreshDue());

        // now share from another owner
        Map<String, ResolvedProduct> resolvedProducts2 = productShareManager
            .resolveProductsAndUpdateProductShares(anotherowner,
            recipientOwner,
            Collections.singleton(productA2));

        assertEquals(1, resolvedProducts2.size());
        assertEquals(productA2, resolvedProducts2.get(productA1.getId()).getProduct());
        assertEquals(true, resolvedProducts2.get(productA1.getId()).isRefreshDue());

        // old share still points to the right product, but is inactive
        List<OwnerProductShare> shares = productShareCurator.findProductSharesBySharer(owner, false, null);
        assertEquals(1, shares.size());
        OwnerProductShare share = shares.get(0);
        assertEquals(owner, share.getOwner());
        assertEquals(recipientOwner, share.getRecipientOwner());
        assertEquals(productA1, share.getProduct());
        assertEquals(false, share.isActive());
    }
}
