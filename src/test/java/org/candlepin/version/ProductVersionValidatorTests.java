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
package org.candlepin.version;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.model.Product;
import org.junit.Before;
import org.junit.Test;

/**
 * ProductVersionValidatorTests
 */
public class ProductVersionValidatorTests {

    private Product product;

    @Before
    public void setUp() {
        product = new Product("123", "RAM Product");
        product.setAttribute("ram", "2");
    }

    @Test
    public void ramLimitedProductRequires3Dot1() {
        assertTrue(ProductVersionValidator.validate(product, "3.1"));
    }

    @Test
    public void ramLimitedProductValidWithVersionGreaterThan3Dot1() {
        assertTrue(ProductVersionValidator.validate(product, "3.2"));
        assertTrue(ProductVersionValidator.validate(product, "3.1.1"));
        assertTrue(ProductVersionValidator.validate(product, "3.1.0"));
    }

    @Test
    public void ramLimitedProductNotValidWithVersionLessThan3Dot1() {
        assertFalse(ProductVersionValidator.validate(product, "1.0"));
        assertFalse(ProductVersionValidator.validate(product, "3.0"));
        assertFalse(ProductVersionValidator.validate(product, "2.9.99"));
    }

    @Test
    public void validateAgainstVersion1WhenVersionStringIsNullOrEmpty() {
        assertFalse(ProductVersionValidator.validate(product, null));
        assertFalse(ProductVersionValidator.validate(product, ""));
    }

    @Test
    public void getMinVersion() {
        assertEquals("3.1.0", ProductVersionValidator.getMinVersion(product).toString());
    }

    @Test
    public void getMinVersionDefaultsToOneWhenProductAttributeNotVersioned() {
        Product noAttrVersionedProduct = new Product("333", "Test Product");
        // sockets attribute is not versioned in the ProductVersionValidator.
        noAttrVersionedProduct.setAttribute("sockets", "2");
        assertEquals("1.0.0",
            ProductVersionValidator.getMinVersion(noAttrVersionedProduct).toString());
    }

}
