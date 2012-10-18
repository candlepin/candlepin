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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.candlepin.model.Product;

/**
 * ProductVersionValidator
 */
public class ProductVersionValidator {

    /**
     * A mapping of product attribute to minimum required version.
     */
    private static final Map<String, Version> PRODUCT_ATTR_VERSION_REQUIREMENTS =
        new HashMap<String, Version>();

    // Add any product atttribute version requirements here.
    static {
        PRODUCT_ATTR_VERSION_REQUIREMENTS.put("ram", new Version("3.1"));
    }

    private ProductVersionValidator() {
        // Can not create an instance of this class.
    }

    public static boolean validate(Product product, String version) {
        Version check = new Version(version);
        for (Entry<String, Version> entry : PRODUCT_ATTR_VERSION_REQUIREMENTS.entrySet()) {
            if (product.hasAttribute(entry.getKey()) &&
                check.compareTo(entry.getValue()) < 0) {
                return false;
            }
        }
        return true;
    }

}
