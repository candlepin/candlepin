/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.Content;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;



/**
 * Test suite for the RefreshResult class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefreshResultTest {

    @Test
    public void testAddCreatedPool() {
        Pool pool = new Pool();
        String poolId = "test_id";
        pool.setId(poolId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Pool> pools = refreshResult.getCreatedPools();
        assertNotNull(pools);
        assertEquals(0, pools.size());

        boolean output = refreshResult.addCreatedPool(pool);
        assertTrue(output);

        pools = refreshResult.getCreatedPools();
        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertThat(pools, hasEntry(poolId, pool));

        // Verify that re-adds do not change the state
        output = refreshResult.addCreatedPool(pool);
        assertFalse(output);

        pools = refreshResult.getCreatedPools();
        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertThat(pools, hasEntry(poolId, pool));
    }

    @Test
    public void testAddCreatedPoolsDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addCreatedPool(null));
    }

    @Test
    public void testAddUpdatedPool() {
        Pool pool = new Pool();
        String poolId = "test_id";
        pool.setId(poolId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Pool> pools = refreshResult.getUpdatedPools();
        assertNotNull(pools);
        assertEquals(0, pools.size());

        boolean output = refreshResult.addUpdatedPool(pool);
        assertTrue(output);

        pools = refreshResult.getUpdatedPools();
        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertThat(pools, hasEntry(poolId, pool));

        // Verify that re-adds do not change the state
        output = refreshResult.addUpdatedPool(pool);
        assertFalse(output);

        pools = refreshResult.getUpdatedPools();
        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertThat(pools, hasEntry(poolId, pool));
    }

    @Test
    public void testAddUpdatedPoolsDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addUpdatedPool(null));
    }

    @Test
    public void testAddSkippedPool() {
        Pool pool = new Pool();
        String poolId = "test_id";
        pool.setId(poolId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Pool> pools = refreshResult.getSkippedPools();
        assertNotNull(pools);
        assertEquals(0, pools.size());

        boolean output = refreshResult.addSkippedPool(pool);
        assertTrue(output);

        pools = refreshResult.getSkippedPools();
        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertThat(pools, hasEntry(poolId, pool));

        // Verify that re-adds do not change the state
        output = refreshResult.addSkippedPool(pool);
        assertFalse(output);

        pools = refreshResult.getSkippedPools();
        assertNotNull(pools);
        assertEquals(1, pools.size());
        assertThat(pools, hasEntry(poolId, pool));
    }

    @Test
    public void testAddSkippedPoolsDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addSkippedPool(null));
    }

    @Test
    public void testGetPoolFindsCreatedPools() {
        Pool pool = new Pool();
        String poolId = "test_id";
        pool.setId(poolId);

        RefreshResult refreshResult = new RefreshResult();

        Pool output = refreshResult.getPool(poolId);
        assertNull(output);

        boolean result = refreshResult.addCreatedPool(pool);
        assertTrue(result);

        output = refreshResult.getPool(poolId);
        assertNotNull(output);
        assertSame(pool, output);
    }

    @Test
    public void testGetPoolFindsUpdatedPools() {
        Pool pool = new Pool();
        String poolId = "test_id";
        pool.setId(poolId);

        RefreshResult refreshResult = new RefreshResult();

        Pool output = refreshResult.getPool(poolId);
        assertNull(output);

        boolean result = refreshResult.addUpdatedPool(pool);
        assertTrue(result);

        output = refreshResult.getPool(poolId);
        assertNotNull(output);
        assertSame(pool, output);
    }

    @Test
    public void testGetPoolFindsSkippedPools() {
        Pool pool = new Pool();
        String poolId = "test_id";
        pool.setId(poolId);

        RefreshResult refreshResult = new RefreshResult();

        Pool output = refreshResult.getPool(poolId);
        assertNull(output);

        boolean result = refreshResult.addSkippedPool(pool);
        assertTrue(result);

        output = refreshResult.getPool(poolId);
        assertNotNull(output);
        assertSame(pool, output);
    }

    @Test
    public void testGetProcessedPools() {
        Pool pool1 = new Pool();
        pool1.setId(TestUtil.randomString("pool"));

        Pool pool2 = new Pool();
        pool2.setId(TestUtil.randomString("pool"));

        Pool pool3 = new Pool();
        pool3.setId(TestUtil.randomString("pool"));

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Pool> processed = refreshResult.getProcessedPools();
        assertNotNull(processed);
        assertEquals(0, processed.size());

        assertTrue(refreshResult.addCreatedPool(pool1));
        assertTrue(refreshResult.addUpdatedPool(pool2));
        assertTrue(refreshResult.addSkippedPool(pool3));

        processed = refreshResult.getProcessedPools();
        assertNotNull(processed);
        assertEquals(3, processed.size());
        assertThat(processed, hasEntry(pool1.getId(), pool1));
        assertThat(processed, hasEntry(pool2.getId(), pool2));
        assertThat(processed, hasEntry(pool3.getId(), pool3));
    }

    @Test
    public void testAddCreatedProduct() {
        Product product = new Product();
        String productId = "test_id";
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Product> products = refreshResult.getCreatedProducts();
        assertNotNull(products);
        assertEquals(0, products.size());

        boolean output = refreshResult.addCreatedProduct(product);
        assertTrue(output);

        products = refreshResult.getCreatedProducts();
        assertNotNull(products);
        assertEquals(1, products.size());
        assertThat(products, hasEntry(productId, product));

        // Verify that re-adds do not change the state
        output = refreshResult.addCreatedProduct(product);
        assertFalse(output);

        products = refreshResult.getCreatedProducts();
        assertNotNull(products);
        assertEquals(1, products.size());
        assertThat(products, hasEntry(productId, product));
    }

    @Test
    public void testAddCreatedProductsDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addCreatedProduct(null));
    }

    @Test
    public void testAddUpdatedProduct() {
        Product product = new Product();
        String productId = "test_id";
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Product> products = refreshResult.getUpdatedProducts();
        assertNotNull(products);
        assertEquals(0, products.size());

        boolean output = refreshResult.addUpdatedProduct(product);
        assertTrue(output);

        products = refreshResult.getUpdatedProducts();
        assertNotNull(products);
        assertEquals(1, products.size());
        assertThat(products, hasEntry(productId, product));

        // Verify that re-adds do not change the state
        output = refreshResult.addUpdatedProduct(product);
        assertFalse(output);

        products = refreshResult.getUpdatedProducts();
        assertNotNull(products);
        assertEquals(1, products.size());
        assertThat(products, hasEntry(productId, product));
    }

    @Test
    public void testAddUpdatedProductsDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addUpdatedProduct(null));
    }

    @Test
    public void testAddSkippedProduct() {
        Product product = new Product();
        String productId = "test_id";
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Product> products = refreshResult.getSkippedProducts();
        assertNotNull(products);
        assertEquals(0, products.size());

        boolean output = refreshResult.addSkippedProduct(product);
        assertTrue(output);

        products = refreshResult.getSkippedProducts();
        assertNotNull(products);
        assertEquals(1, products.size());
        assertThat(products, hasEntry(productId, product));

        // Verify that re-adds do not change the state
        output = refreshResult.addSkippedProduct(product);
        assertFalse(output);

        products = refreshResult.getSkippedProducts();
        assertNotNull(products);
        assertEquals(1, products.size());
        assertThat(products, hasEntry(productId, product));
    }

    @Test
    public void testAddSkippedProductsDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addSkippedProduct(null));
    }

    @Test
    public void testGetProductFindsCreatedProducts() {
        Product product = new Product();
        String productId = "test_id";
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult();

        Product output = refreshResult.getProduct(productId);
        assertNull(output);

        boolean result = refreshResult.addCreatedProduct(product);
        assertTrue(result);

        output = refreshResult.getProduct(productId);
        assertNotNull(output);
        assertSame(product, output);
    }

    @Test
    public void testGetProductFindsUpdatedProducts() {
        Product product = new Product();
        String productId = "test_id";
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult();

        Product output = refreshResult.getProduct(productId);
        assertNull(output);

        boolean result = refreshResult.addUpdatedProduct(product);
        assertTrue(result);

        output = refreshResult.getProduct(productId);
        assertNotNull(output);
        assertSame(product, output);
    }

    @Test
    public void testGetProductFindsSkippedProducts() {
        Product product = new Product();
        String productId = "test_id";
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult();

        Product output = refreshResult.getProduct(productId);
        assertNull(output);

        boolean result = refreshResult.addSkippedProduct(product);
        assertTrue(result);

        output = refreshResult.getProduct(productId);
        assertNotNull(output);
        assertSame(product, output);
    }

    @Test
    public void testGetProcessedProducts() {
        Product product1 = new Product();
        product1.setId(TestUtil.randomString("product"));

        Product product2 = new Product();
        product2.setId(TestUtil.randomString("product"));

        Product product3 = new Product();
        product3.setId(TestUtil.randomString("product"));

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Product> processed = refreshResult.getProcessedProducts();
        assertNotNull(processed);
        assertEquals(0, processed.size());

        assertTrue(refreshResult.addCreatedProduct(product1));
        assertTrue(refreshResult.addUpdatedProduct(product2));
        assertTrue(refreshResult.addSkippedProduct(product3));

        processed = refreshResult.getProcessedProducts();
        assertNotNull(processed);
        assertEquals(3, processed.size());
        assertThat(processed, hasEntry(product1.getId(), product1));
        assertThat(processed, hasEntry(product2.getId(), product2));
        assertThat(processed, hasEntry(product3.getId(), product3));
    }

    @Test
    public void testAddCreatedContent() {
        Content content = new Content();
        String contentId = "test_id";
        content.setId(contentId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Content> contentMap = refreshResult.getCreatedContent();
        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        boolean output = refreshResult.addCreatedContent(content);
        assertTrue(output);

        contentMap = refreshResult.getCreatedContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());
        assertThat(contentMap, hasEntry(contentId, content));

        // Verify that re-adds do not change the state
        output = refreshResult.addCreatedContent(content);
        assertFalse(output);

        contentMap = refreshResult.getCreatedContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());
        assertThat(contentMap, hasEntry(contentId, content));
    }

    @Test
    public void testAddCreatedContentDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addCreatedContent(null));
    }

    @Test
    public void testAddUpdatedContent() {
        Content content = new Content();
        String contentId = "test_id";
        content.setId(contentId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Content> contentMap = refreshResult.getUpdatedContent();
        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        boolean output = refreshResult.addUpdatedContent(content);
        assertTrue(output);

        contentMap = refreshResult.getUpdatedContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());
        assertThat(contentMap, hasEntry(contentId, content));

        // Verify that re-adds do not change the state
        output = refreshResult.addUpdatedContent(content);
        assertFalse(output);

        contentMap = refreshResult.getUpdatedContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());
        assertThat(contentMap, hasEntry(contentId, content));
    }

    @Test
    public void testAddUpdatedContentDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addUpdatedContent(null));
    }

    @Test
    public void testAddSkippedContent() {
        Content content = new Content();
        String contentId = "test_id";
        content.setId(contentId);

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Content> contentMap = refreshResult.getSkippedContent();
        assertNotNull(contentMap);
        assertEquals(0, contentMap.size());

        boolean output = refreshResult.addSkippedContent(content);
        assertTrue(output);

        contentMap = refreshResult.getSkippedContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());
        assertThat(contentMap, hasEntry(contentId, content));

        // Verify that re-adds do not change the state
        output = refreshResult.addSkippedContent(content);
        assertFalse(output);

        contentMap = refreshResult.getSkippedContent();
        assertNotNull(contentMap);
        assertEquals(1, contentMap.size());
        assertThat(contentMap, hasEntry(contentId, content));
    }

    @Test
    public void testAddSkippedContentDoesNotAllowNull() {
        RefreshResult refreshResult = new RefreshResult();
        assertThrows(IllegalArgumentException.class, () -> refreshResult.addSkippedContent(null));
    }

    @Test
    public void testGetContentFindsCreatedContent() {
        Content content = new Content();
        String contentId = "test_id";
        content.setId(contentId);

        RefreshResult refreshResult = new RefreshResult();

        Content output = refreshResult.getContent(contentId);
        assertNull(output);

        boolean result = refreshResult.addCreatedContent(content);
        assertTrue(result);

        output = refreshResult.getContent(contentId);
        assertNotNull(output);
        assertSame(content, output);
    }

    @Test
    public void testGetContentFindsUpdatedContent() {
        Content content = new Content();
        String contentId = "test_id";
        content.setId(contentId);

        RefreshResult refreshResult = new RefreshResult();

        Content output = refreshResult.getContent(contentId);
        assertNull(output);

        boolean result = refreshResult.addUpdatedContent(content);
        assertTrue(result);

        output = refreshResult.getContent(contentId);
        assertNotNull(output);
        assertSame(content, output);
    }

    @Test
    public void testGetContentFindsSkippedContent() {
        Content content = new Content();
        String contentId = "test_id";
        content.setId(contentId);

        RefreshResult refreshResult = new RefreshResult();

        Content output = refreshResult.getContent(contentId);
        assertNull(output);

        boolean result = refreshResult.addSkippedContent(content);
        assertTrue(result);

        output = refreshResult.getContent(contentId);
        assertNotNull(output);
        assertSame(content, output);
    }

    @Test
    public void testGetProcessedContent() {
        Content content1 = new Content();
        content1.setId(TestUtil.randomString("content"));

        Content content2 = new Content();
        content2.setId(TestUtil.randomString("content"));

        Content content3 = new Content();
        content3.setId(TestUtil.randomString("content"));

        RefreshResult refreshResult = new RefreshResult();

        Map<String, Content> processed = refreshResult.getProcessedContent();
        assertNotNull(processed);
        assertEquals(0, processed.size());

        assertTrue(refreshResult.addCreatedContent(content1));
        assertTrue(refreshResult.addUpdatedContent(content2));
        assertTrue(refreshResult.addSkippedContent(content3));

        processed = refreshResult.getProcessedContent();
        assertNotNull(processed);
        assertEquals(3, processed.size());
        assertThat(processed, hasEntry(content1.getId(), content1));
        assertThat(processed, hasEntry(content2.getId(), content2));
        assertThat(processed, hasEntry(content3.getId(), content3));
    }

}
