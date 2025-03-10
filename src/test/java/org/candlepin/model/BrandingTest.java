/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;



/**
 * BrandingTest
 */
public class BrandingTest {

    @Test
    public void testBrandingDTOConstructor() {
        new Branding("branding id", "branding name", "branding type");
    }

    @Test
    public void testBrandingProductConstructor() {
        Product product = new Product();
        new Branding(product, "branding id", "branding name", "branding type");
    }

    @Test
    public void testCannotCreateProductWithBrandingWithNullProductId() {
        assertThrows(NullPointerException.class, () -> new Branding(null, "branding name", "branding type"));
    }

    @Test
    public void testCannotCreateProductWithBrandingWithNullName() {
        assertThrows(NullPointerException.class, () -> new Branding("branding id", null, "branding type"));
    }

    @Test
    public void testCannotCreateProductWithBrandingWithNullType() {
        assertThrows(NullPointerException.class, () -> new Branding("branding id", "branding name", null));
    }

    @Test
    public void testCannotCreateProductWithBrandingWithNullProduct() {
        assertThrows(NullPointerException.class,
            () -> new Branding(null, "branding id", "branding name", "branding type"));
    }

}
