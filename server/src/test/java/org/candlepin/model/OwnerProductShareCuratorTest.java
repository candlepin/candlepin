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

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test suite for OwnerProductShareCurator.
 */
public class OwnerProductShareCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Owner recipientOwner;
    private Product product;
    private Set<String> productIds;

    @Before
    public void setUp() {
        owner = createOwner();
        recipientOwner = createOwner();
        product = createProduct(owner);
        productIds = new HashSet<String>();
        productIds.add(product.getId());
    }

    @Test
    public void testSharesAbsent() {
        assertTrue(productShareCurator.findProductSharesByRecipient(recipientOwner, false, productIds)
            .isEmpty());
    }

    @Test
    public void testFindByRecipient() {
        createShare(owner, recipientOwner, product, true);

        OwnerProductShare result = productShareCurator
            .findProductSharesByRecipient(recipientOwner, false, productIds)
            .get(0);

        assertEquals(result.getOwner(), owner);
        assertEquals(result.getProduct(), product);
        assertEquals(result.getRecipientOwner(), recipientOwner);

        // search with sharing owner as recipient does not return anything
        assertTrue(productShareCurator.
            findProductSharesByRecipient(owner, false, productIds)
            .isEmpty());
    }

    @Test
    public void testFindBySharer() {
        createShare(owner, recipientOwner, product, true);

        OwnerProductShare result = productShareCurator
            .findProductSharesBySharer(owner, false, productIds)
            .get(0);

        assertEquals(result.getOwner(), owner);
        assertEquals(result.getProduct(), product);
        assertEquals(result.getRecipientOwner(), recipientOwner);

        // search with sharing owner as recipient does not return anything
        assertTrue(productShareCurator.
            findProductSharesBySharer(recipientOwner, false, productIds)
            .isEmpty());
    }

    private void setupMultipleShares(Owner sharingOwner, Owner recipientOwner, Product product, Product
        product2) {
        assertTrue(productShareCurator.findProductSharesByRecipient(recipientOwner, false, productIds).isEmpty
            ());
        createShare(sharingOwner, recipientOwner, product, true);
        createShare(sharingOwner, recipientOwner, product2, true);
    }

    private void validateMultipleShares(Owner sharingOwner, Owner recipientOwner,
        List<OwnerProductShare> shares, Product product1, Product product2, boolean bothExpected) {
        if (bothExpected) {
            assertEquals(2, shares.size());
        }
        else {
            assertEquals(1, shares.size());
        }

        boolean productFound = false, product2Found = false;
        for (OwnerProductShare share : shares) {
            assertEquals(share.getOwner(), sharingOwner);
            assertEquals(share.getRecipientOwner(), recipientOwner);
            if (share.getProduct() == product) {
                productFound = true;
            }
            if (share.getProduct() == product2) {
                product2Found = true;
            }
        }
        assertTrue(productFound);
        assertEquals(bothExpected, product2Found);
    }

    @Test
    public void testMultipleProductSharesByRecipient() {
        Product product2 = createProduct(owner);
        productIds.add(product2.getId());

        setupMultipleShares(owner, recipientOwner, product, product2);
        List<OwnerProductShare> shares = productShareCurator.findProductSharesByRecipient(
            recipientOwner, false, productIds);

        validateMultipleShares(owner, recipientOwner, shares, product, product2, true);
    }

    @Test
    public void testMultipleProductSharesByRecipientFilteredById() {
        Product product2 = createProduct(owner);

        setupMultipleShares(owner, recipientOwner, product, product2);
        List<OwnerProductShare> shares = productShareCurator.findProductSharesByRecipient(
            recipientOwner, false, productIds);

        validateMultipleShares(owner, recipientOwner, shares, product, product2, false);
    }

    @Test
    public void testMultipleProductSharesBySharer() {
        Product product2 = createProduct(owner);
        productIds.add(product2.getId());

        setupMultipleShares(owner, recipientOwner, product, product2);
        List<OwnerProductShare> shares = productShareCurator.findProductSharesBySharer(
            owner, false, productIds);

        validateMultipleShares(owner, recipientOwner, shares, product, product2, true);
    }

    @Test
    public void testMultipleProductSharesBySharerFilteredById() {
        Product product2 = createProduct(owner);

        setupMultipleShares(owner, recipientOwner, product, product2);
        List<OwnerProductShare> shares = productShareCurator.findProductSharesBySharer(
            owner, false, productIds);

        validateMultipleShares(owner, recipientOwner, shares, product, product2, false);
    }
}
