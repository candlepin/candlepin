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

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test suite for ProductShareCurator.
 */
public class ProductShareCuratorTest extends DatabaseTestFixture {

    @Test
    public void testSetProductShared() {
        Owner owner = createOwner();
        Owner recipientOwner = createOwner();
        Product product = createProduct();

        Set<String> productIds = Collections.singleton(product.getId());
        assertTrue(productShareCurator.findProductSharesByRecipient(recipientOwner, productIds).isEmpty());

        ProductShare share = new ProductShare(owner, product, recipientOwner);
        productShareCurator.save(share);

        ProductShare result = productShareCurator
            .findProductSharesByRecipient(recipientOwner, productIds)
            .get(0);

        assertEquals(result.getOwner(), owner);
        assertEquals(result.getProduct(), product);
        assertEquals(result.getRecipient(), recipientOwner);

        productShareCurator.delete(share);
        assertTrue(productShareCurator.findProductSharesByRecipient(recipientOwner, productIds).isEmpty());
    }

    @Test
    public void testMultipleProductShares() {
        Owner owner = createOwner();
        Owner recipientOwner = createOwner();
        Product product = createProduct();
        Product product2 = createProduct();

        Set<String> productIds = new HashSet<>();
        productIds.add(product.getId());
        productIds.add(product2.getId());
        assertTrue(productShareCurator.findProductSharesByRecipient(recipientOwner, productIds).isEmpty());

        ProductShare share = new ProductShare(owner, product, recipientOwner);
        productShareCurator.save(share);

        ProductShare share2 = new ProductShare(owner, product2, recipientOwner);
        productShareCurator.save(share2);

        List<ProductShare> shares = productShareCurator.findProductSharesByRecipient(
            recipientOwner, productIds);
        assertEquals(2, shares.size());
    }
}
