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
import java.util.Set;

import org.candlepin.config.Config;
import org.candlepin.model.Attribute;
import org.candlepin.model.Consumer;
import org.candlepin.model.Subscription;
import org.candlepin.util.RpmVersionComparator;

/**
 * ProductVersionValidator
 *
 * Since the introduction of cert V3, we now have the concept of a certificate
 * version. When certificates are changed (i.e, new attributes), we will bump
 * the certificate's version. See X509V3ExtenstionUtil
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
        PRODUCT_ATTR_VERSION_REQUIREMENTS.put("cores", "3.2");
    }

    private ProductVersionValidator() {
        // Can not create an instance of this class.
    }

    /**
     * Compares two version strings.
     *
     * @see RpmVersionComparator
     *
     * @param version1
     * @param version2
     * @return an int value less than, equal to, or greater than 0 depending on
     *         whether version1 is less than, equal to, or greater than version2.
     */
    public static int compareVersion(String version1, String version2) {
        RpmVersionComparator c = new RpmVersionComparator();
        return c.compare(version1, version2);
    }

    public static boolean verifyServerSupport(Config config, Consumer consumer,
        Set<? extends Attribute> productAttributes) {
        String min = ProductVersionValidator.getMin(productAttributes);
        if (ProductVersionValidator.compareVersion(min, "1.0") > 0) {
            return false;
        }
        return true;
    }

    public static boolean verifyClientSupport(Consumer consumer,
        Set<? extends Attribute> productAttributes) {
        // we do not need to worry about this check for distributors, just end clients
        if (consumer.getType() != null &&
            consumer.getType().isManifest()) {
            return true;
        }
        String consumerVersion = consumer.getFact("system.certificate_version");
        return ProductVersionValidator.validate(productAttributes, consumerVersion);
    }

    public static String getMinimumCertificateVersion(Subscription sub) {
        return ProductVersionValidator.getMin(sub.getProduct().getAttributes());
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
    private static boolean validate(Set<? extends Attribute> productAttrs, String version) {
        version = version == null || version.isEmpty() ? "1.0" : version;
        for (Attribute attr : productAttrs) {
            String attrName = attr.getName();
            if (!PRODUCT_ATTR_VERSION_REQUIREMENTS.containsKey(attrName)) {
                continue;
            }
            String registeredVersion = PRODUCT_ATTR_VERSION_REQUIREMENTS.get(attrName);
            if (compareVersion(version, registeredVersion) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determines the minimum cert version required to support a product based on
     * its attributes.
     *
     * @param productAttributes the attributes to check.
     * @return the minimum required version, 1.0.0 if no registered attributes are found.
     */
    private static String getMin(Set<? extends Attribute> productAttributes) {
        String min = "1.0";
        for (Attribute attr : productAttributes) {
            if (!PRODUCT_ATTR_VERSION_REQUIREMENTS.containsKey(attr.getName())) {
                continue;
            }

            String attrVersion = PRODUCT_ATTR_VERSION_REQUIREMENTS.get(attr.getName());
            if (ProductVersionValidator.compareVersion(min, attrVersion) < 0) {
                min = attrVersion;
            }
        }
        return min;
    }

}
