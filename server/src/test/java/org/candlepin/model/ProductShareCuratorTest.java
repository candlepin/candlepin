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

import java.util.List;

/**
 * Test suite for ProductShareCurator.
 */
public class ProductShareCuratorTest extends DatabaseTestFixture {

    @Test
    public void testSetProductShared() {
        Owner owner = createOwner();
        Owner recipientOwner = createOwner();
        Product product = createProduct();

        assertNull(productShareCurator.findProductShare(owner, product, recipientOwner));

        ProductShare share = new ProductShare(owner, product, recipientOwner);
        productShareCurator.save(share);

        ProductShare result = productShareCurator.findProductShare(owner, product, recipientOwner);

        assertEquals(result.getOwner(), owner);
        assertEquals(result.getProduct(), product);
        assertEquals(result.getRecipient(), recipientOwner);

        productShareCurator.delete(share);
        assertNull(productShareCurator.findProductShare(owner, product, recipientOwner));
    }

    @Test
    public void testMultipleProductShares() {
        Owner owner = createOwner();
        Owner recipientOwner = createOwner();
        Product product = createProduct();
        Product product2 = createProduct();

        assertEquals(0, productShareCurator.findProductSharesBetweenOwners(owner, recipientOwner).size());

        ProductShare share = new ProductShare(owner, product, recipientOwner);
        productShareCurator.save(share);

        ProductShare share2 = new ProductShare(owner, product2, recipientOwner);
        productShareCurator.save(share2);

        List<ProductShare> result = productShareCurator.findProductSharesBetweenOwners(owner, recipientOwner);

        assertEquals(2, result.size());
    }
}
