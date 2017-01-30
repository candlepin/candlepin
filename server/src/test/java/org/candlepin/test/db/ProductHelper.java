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
package org.candlepin.test.db;

import org.candlepin.model.Product;

import org.junit.Assert;

public final class ProductHelper {

    private ProductHelper() {
        throw new UnsupportedOperationException("This class is a utility!");
    }

    public static final String [] ignoredCols = new String [] { Product.UUID_COLUMN,
        Product.CREATED_COLUMN, Product.UPDATED_COLUMN, Product.VERSION_COLUMN};

    public final static String PRODUCT_UUID_VALUE = "uuid";
    public final static String PRODUCT_ID_VALUE = "product_id";
    public final static String PRODUCT_NAME_VALUE = "product_name";

    public static Product newProduct() {
        Product p = new Product(PRODUCT_ID_VALUE, PRODUCT_NAME_VALUE);
        p.setUuid(PRODUCT_UUID_VALUE);
        return p;
    }

    public static void assertProduct(Product prodcut) {
        Assert.assertNotNull(prodcut);
        Assert.assertEquals(PRODUCT_ID_VALUE, prodcut.getId());
        Assert.assertEquals(PRODUCT_NAME_VALUE, prodcut.getName());
    }
}
