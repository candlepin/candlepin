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
import org.candlepin.util.RpmVersionComparator;

/**
 * ProductVersionValidator
 *
 * Since the introduction of cert V3, we not have the concept of a certificate
 * version. When certificates are changed (i.e, new attributes), we will bump
 * the certificate's version.
 *
 * This class defines what certificate version is required for a Product
 * based on its attributes. It has the ability to determine if a given
 * entitlement version is valid for given product. It can also determine
 * the minimum entitlement version required in order to support the given
 * product.
 */
public class ProductVersionValidator {

    /**
     * A mapping of product attribute to minimum required version.
     */
    private static final Map<String, String> PRODUCT_ATTR_VERSION_REQUIREMENTS =
        new HashMap<String, String>();

    // Add any product atttribute version requirements here.
    static {
        PRODUCT_ATTR_VERSION_REQUIREMENTS.put("ram", "3.1");
    }

    private ProductVersionValidator() {
        // Can not create an instance of this class.
    }

    /**
     * Validates the specified entitlement version against the required versions
     * of the specified product's attributes.
     *
     * @param product the product to check against.
     * @param version the entitlement version to check.
     * @return true if the version meets the product's attribute version requirements,
     *         false otherwise.
     */
    public static boolean validate(Product product, String version) {
        RpmVersionComparator versionComparitor = new RpmVersionComparator();
        version = version == null || version.isEmpty() ? "1.0" : version;
        for (Entry<String, String> entry : PRODUCT_ATTR_VERSION_REQUIREMENTS.entrySet()) {
            if (product.hasAttribute(entry.getKey()) &&
                versionComparitor.compare(version, entry.getValue()) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines the minimum cert version required to support the product. This is
     * done by finding the max version defined by the product's attributes.
     *
     * @param product the product to check.
     * @return the minimum required version, 1.0.0 if no registered attributes are found.
     */
    public static String getMinVersion(Product product) {
        RpmVersionComparator versionComparitor = new RpmVersionComparator();
        String min = "1.0";
        for (Entry<String, String> entry : PRODUCT_ATTR_VERSION_REQUIREMENTS.entrySet()) {
            if (product.hasAttribute(entry.getKey()) &&
                versionComparitor.compare(min, entry.getValue()) < 0) {
                min = entry.getValue();
            }
        }
        return min;
    }

}
