/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.service.model;

import org.apache.commons.lang3.builder.EqualsBuilder;

import java.util.Comparator;

/**
 * The BrandingInfo represents a minimal set of branding information used by the service adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface BrandingInfo extends ServiceAdapterModel {

    /**
     * Fetches the name of this branding instance. If the name has not been set, this method
     * returns null.
     *
     * @return
     *  The name of the branding instance, or null if the name has not been set
     */
    String getName();

    /**
     * Fetches the type of this branding instance. If the type has not been set, this method
     * returns null.
     *
     * @return
     *  The type of the branding instance, or null if the type has not been set
     */
    String getType();

    /**
     * Fetches the product ID of the product affected by this branding instance. If the product ID
     * has not been set, this method returns null.
     *
     * @return
     *  The product ID of the product affected by this branding instance, or null if the product ID
     *  has not been set
     */
    String getProductId();

    /**
     * Utility method that returns a Comparator for objects that implement BrandingInfo.
     *
     * @return A comparator for BrandingInfo objects.
     */
    static Comparator<BrandingInfo> getBrandingInfoComparator() {
        return (lhs, rhs) -> {
            if (lhs != null && rhs != null) {
                boolean equals = new EqualsBuilder()
                    .append(lhs.getProductId(), rhs.getProductId())
                    .append(lhs.getName(), rhs.getName())
                    .append(lhs.getType(), rhs.getType())
                    .isEquals();
                return equals ? 0 : 1;
            }

            return 1;
        };
    }
}
