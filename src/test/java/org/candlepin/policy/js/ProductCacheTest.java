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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.model.Product;
import org.candlepin.service.ProductServiceAdapter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * ProductCacheTest
 */
public class ProductCacheTest {

    @Mock
    private ProductServiceAdapter mockProductAdapter;
    private TestingProductCache cache;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);
        cache = new TestingProductCache(mockProductAdapter);
    }

    @Test
    public void getProductFromAdapterIfNotInCache() {
        Product p = new Product("a_product", "a_product");
        when(mockProductAdapter.getProductById(p.getId())).thenReturn(p);
        assertFalse(cache.contains(p.getId()));
        Product fetched = cache.getProductById(p.getId());
        assertEquals(p.getId(), fetched.getId());
        assertTrue(cache.contains(p.getId()));

        verify(mockProductAdapter, times(1)).getProductById(eq(p.getId()));
    }

    @Test
    public void doNotGetProductFromAdapterIfInCache() {
        Product p = new Product("a_product", "a_product");
        when(mockProductAdapter.getProductById(p.getId())).thenReturn(p);
        assertFalse(cache.contains(p.getId()));
        // Look up the product so it is fetched from the adapter
        cache.getProductById(p.getId());
        assertTrue(cache.contains(p.getId()));

        // Look the product up again so that we can verify that
        // a second adapter call was not made.
        Product fetched = cache.getProductById(p.getId());
        assertEquals(p.getId(), fetched.getId());

        // The adapter should be hit only once.
        verify(mockProductAdapter, times(1)).getProductById(eq(p.getId()));
    }

    @Test
    public void ensureFirstProductRemovedWhenMaxReached() {

        Product initial = addProductToCache("initial_product");
        for (int i = 0; i < 99; i++) {
            addProductToCache("Product" + i);
        }

        assertEquals(100, cache.size());
        assertTrue(cache.contains(initial.getId()));

        // Add one more to roll the cache over its max.
        Product overflow = addProductToCache("overflow");

        // Cache size should remain at MAX.
        assertEquals(100, cache.size());
        // First product added should no longer be there.
        assertFalse(cache.contains(initial.getId()));
        // New product should exist.
        assertTrue(cache.contains(overflow.getId()));
    }

    @Test
    public void doesNotContainProductWhenIdIsNotFound() {
        assertEquals(0, cache.size());
        assertFalse(cache.contains("does_not_exist"));
    }

    @Test
    public void doesNotContainProductWhenReferenceIsNull() {
        String productId = "product";
        addProductToCache(productId);
        assertTrue(cache.contains(productId));

        cache.setNullReferenceForKey(productId);
        assertFalse(cache.contains(productId));
    }

    @Test
    public void doesNotContainProductWhenReferencedProductIsNull() {
        String productId = "product";
        addProductToCache(productId);
        assertTrue(cache.contains(productId));

        cache.setNullReferenceForProduct(productId);
        assertFalse(cache.contains(productId));
    }

    @Test
    public void productLookedUpAgainIfReferenceIsNull() {
        String productId = "product";
        addProductToCache(productId);
        assertTrue(cache.contains(productId));

        cache.setNullReferenceForKey(productId);

        // Look the product up again so that we can verify that
        // a second adapter call was not made.
        Product fetched = cache.getProductById(productId);
        assertEquals(productId, fetched.getId());

        // The adapter should be hit again on the second lookup.
        verify(mockProductAdapter, times(2)).getProductById(eq(productId));
    }

    @Test
    public void productLookedUpAgainIfProductIsNullInReference() {
        String productId = "product";
        addProductToCache(productId);
        assertTrue(cache.contains(productId));

        cache.setNullReferenceForProduct(productId);

        Product fetched = cache.getProductById(productId);
        assertEquals(productId, fetched.getId());

        // The adapter should be hit again on the second lookup.
        verify(mockProductAdapter, times(2)).getProductById(eq(productId));
    }

    private Product addProductToCache(String productId) {
        Product product = new Product(productId, productId);
        when(mockProductAdapter.getProductById(product.getId())).thenReturn(product);
        assertNotNull("Failed to add product to cache.",
            cache.getProductById(product.getId()));
        return product;
    }

    private class TestingProductCache extends ProductCache {

        public TestingProductCache(ProductServiceAdapter productAdapter) {
            super(productAdapter);
        }

        public void setNullReferenceForKey(String productId) {
            assertTrue(contains(productId));
            this.products.put(productId, null);
        }

        public void setNullReferenceForProduct(String productId) {
            assertTrue(contains(productId));
            this.products.get(productId).clear();
            assertNotNull(this.products.get(productId));
        }
    }
}
