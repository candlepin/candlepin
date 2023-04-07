/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

/**
 * Class meant to provide fully randomized instances of product.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Products {

    private Products() {
        throw new UnsupportedOperationException();
    }

    public static ProductDTO random() {
        return randomSKU();
    }

    /**
     * Creates a product DTO with a randomly generated ID and name following the expected ID format
     * for SKU (base/marketing) products
     *
     * @return
     *  a product DTO with a randomly generated SKU ID and name
     */
    public static ProductDTO randomEng() {
        String id = StringUtil.random(8, StringUtil.CHARSET_NUMERIC);

        return new ProductDTO()
            .id(id)
            .name("test_product-" + id);
    }

    /**
     * Creates a product DTO with a randomly generated ID and name following the expected ID format
     * for engineering (provided) products
     *
     * @return
     *  a product DTO with a randomly generated engineering ID and name
     */
    public static ProductDTO randomSKU() {
        String id = StringUtil.random("test_product-", 8, StringUtil.CHARSET_NUMERIC_HEX);

        return new ProductDTO()
            .id(id)
            .name(id);
    }

    public static ProductDTO withAttributes(AttributeDTO... attributes) {
        ProductDTO product = random();
        for (AttributeDTO attribute : attributes) {
            product.addAttributesItem(attribute);
        }
        return product;
    }

    public static ConsumerInstalledProductDTO toInstalled(ProductDTO product) {
        return new ConsumerInstalledProductDTO()
            .productId(product.getId())
            .productName(product.getName());
    }

    public static ProvidedProductDTO toProvidedProduct(ProductDTO product) {
        return new ProvidedProductDTO()
            .productId(product.getId())
            .productName(product.getName());
    }
}
