/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.v1.BrandingDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;


/**
 * Class providing factory functions for BrandingDTO instances.
 */
public final class Branding {

    /**
     * Throws an UnsupportedOperationException; individual instantiation of this class is not
     * permitted.
     *
     * @throws UnsupportedOperationException
     */
    private Branding() {
        throw new UnsupportedOperationException();
    }

    /**
     * Builds a BrandingDTO instance with a randomly generated name and type.
     *
     * @return
     *  a BrandingDTO instance with a randomly generated name and type
     */
    public static BrandingDTO random() {
        String suffix = StringUtil.random(8, StringUtil.CHARSET_NUMERIC_HEX);

        return build("branding-" + suffix, "brand_type-" + suffix);
    }

    /**
     * Builds a BrandingDTO instance using the given name and a randomly generated type.
     *
     * @param name
     *  the name to assign to the branding object
     *
     * @return
     *  a BrandingDTO instance with the given name and a randomly generated type
     */
    public static BrandingDTO random(String name) {
        return build(name, StringUtil.random("brand_type-", 8, StringUtil.CHARSET_NUMERIC_HEX));
    }

    /**
     * Builds a random BrandingDTO instance using the provided product's id.
     *
     * @param product
     *  the product to use for populating BrandingDTO's product Id.
     *
     * @return a BrandingDTO instance with a randomly generated name and type using the product's id.
     */
    public static BrandingDTO random(ProductDTO product) {
        return random()
            .productId(product.getId());
    }

    /**
     * Builds a BrandingDTO instance using the given name and type.
     *
     * @param name
     *  the name to assign to the branding object
     *
     * @param type
     *  the type to assign to the branding object
     *
     * @return
     *  a BrandingDTO instance with the given name and type
     */
    public static BrandingDTO build(String name, String type) {
        return new BrandingDTO()
            .name(name)
            .type(type);
    }

}
