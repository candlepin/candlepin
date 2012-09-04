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

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.candlepin.guice.CandlepinSingletonScoped;
import org.candlepin.model.Product;
import org.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

/**
 * ProductCache
 *
 * Caches products that have been retrieved from the product
 * adapter. If an attempt is made to retrieve a product that
 * is not in the cache, it is looked up by the adapter and
 * is automatically stored.
 *
 * The cache can contain a maximum of 100 products at a time
 * and is implemented using <code>SoftReference</code>s
 * so that when memory becomes an issue, the GC can claim
 * any products it requires.
 *
 */
@CandlepinSingletonScoped
public class ProductCache {

    private ProductServiceAdapter productAdapter;

    // Protected for testing purposes
    protected ProductMapping products;

    @Inject
    public ProductCache(ProductServiceAdapter productAdapter) {
        products = new ProductMapping();
        this.productAdapter = productAdapter;
    }

    public Product getProductById(String productId) {
        ProductReference productReference = null;
        if (!contains(productId)) {
            productReference = addProductReference(productId);
        }
        else {
            productReference = products.get(productId);
        }
        return productReference.get();
    }

    public void addProducts(Set<Product> products) {
        for (Product product : products) {
            if (!contains(product.getId())) {
                this.products.put(product.getId(), new ProductReference(product));
            }
        }
    }

    public boolean contains(String productId) {
        if (!this.products.containsKey(productId)) {
            return false;
        }

        // GC may have cleaned up the fetched reference if memory was required.
        ProductReference productReference = this.products.get(productId);
        return productReference != null && productReference.get() != null;
    }

    public int size() {
        return products.size();
    }

    private ProductReference addProductReference(String productId) {
        ProductReference productReference =
            new ProductReference(productAdapter.getProductById(productId));
        products.put(productId, productReference);
        return productReference;
    }

    /**
     * ProductReference
     *
     * A reference to a product object. We use <code>SoftReference</code>
     * here instead of WeakReference as they only get GC'd when memory
     * is getting low.
     */
    protected class ProductReference extends SoftReference<Product> {

        public ProductReference(Product referent) {
            super(referent);
        }

    }

    /**
     *
     * ProductMapping
     *
     * A <code>LinkedHashMap</code> implementation that represents a mapped
     * reference to {@link Product} references. This implementation allows
     * a maximum of 100 mapped products. When an attempt is made to insert
     * more than 100, the eldest product reference will be removed.
     */
    protected class ProductMapping extends LinkedHashMap<String, ProductReference> {

        // The maximum number of products allowed in the map.
        private static final int MAX_PRODUCTS = 100;

        /*
         * When an attempt is made to add a product to the map,
         * let the mapping remove the eldest product that was
         * added if we are over our size limit.
         */
        @Override
        protected boolean removeEldestEntry(Entry<String, ProductReference> eldest) {
            return this.size() > MAX_PRODUCTS;
        }


    }
}
