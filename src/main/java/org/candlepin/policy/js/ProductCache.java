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
package org.candlepin.policy.js;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.candlepin.guice.CandlepinSingletonScoped;
import org.candlepin.model.Product;
import org.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

/**
 * ReadOnlyProductCache
 */
@CandlepinSingletonScoped
public class ProductCache {

    private ProductServiceAdapter productAdapter;
    private Map<String, Product> products;

    @Inject
    public ProductCache(ProductServiceAdapter productAdapter) {
        products = new HashMap<String, Product>();
        this.productAdapter = productAdapter;
    }

    public Product getProductById(String productId) {
        if (!products.containsKey(productId)) {
            products.put(productId,
                productAdapter.getProductById(productId));
        }
        return products.get(productId);
    }

    public void addProducts(Set<Product> products) {
        for (Product product : products) {
            if (!this.products.containsKey(product.getId())) {
                this.products.put(product.getId(), product);
            }
        }
    }

}
