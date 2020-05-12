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

import org.candlepin.model.Content;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;

import java.util.HashMap;
import java.util.Map;



/**
 * The RefreshResult encapsulates the entities processed during a refresh operation, allowing them
 * to be catalogued or post-processed in another operation.
 *
 * Note that this class does not fully encapsulate its collections, and modifications made to some
 * of the collections or maps received may result in changes to this class.
 */
public class RefreshResult {

    // TODO: Flesh out the collection types here a bit more, in accordance with the table below
    //
    // Existing     Imported    Merged entity       Result
    // not-null     not-null    not-null            updated entity
    // not-null     not-null    null                unchanged entity (imported but unchanged)
    // not-null     null        not-null            children updated
    // not-null     null        null                unchanged entity (not imported, no changes to children)
    // null         not-null    not-null            created entity
    // null         not-null    null                ERROR STATE - creation failed
    // null         null        null                ERROR STATE - uninitialized node
    //
    // Following this, we have 3 states to report, and 2 pseudo-states:
    // - resultant states: created (5), updated (1, 3), unchanged (2, 4)
    // - pseudo-states: imported (1, 2, 5) and skipped (3, 4)
    //
    // At the time of writing, we can kind of discern the pseudo-states by getting the collections
    // back out of the refresh worker, so maybe this is a non-issue.

    private Map<String, Pool> createdPools;
    private Map<String, Pool> updatedPools;
    private Map<String, Pool> skippedPools;

    private Map<String, Product> createdProducts;
    private Map<String, Product> updatedProducts;
    private Map<String, Product> skippedProducts;

    private Map<String, Content> createdContent;
    private Map<String, Content> updatedContent;
    private Map<String, Content> skippedContent;


    /**
     * Creates a new RefreshResult instance with no data
     */
    public RefreshResult() {
        this.createdPools = new HashMap<>();
        this.updatedPools = new HashMap<>();
        this.skippedPools = new HashMap<>();

        this.createdProducts = new HashMap<>();
        this.updatedProducts = new HashMap<>();
        this.skippedProducts = new HashMap<>();

        this.createdContent = new HashMap<>();
        this.updatedContent = new HashMap<>();
        this.skippedContent = new HashMap<>();
    }

    /**
     * Adds the specified pool as a "created" pool, where a created pool is defined as a pool which
     * did not have a local definition and was created during the refresh operation.
     *
     * @param pool
     *  the pool to add as a created pool
     *
     * @throws IllegalArgumentException
     *  if pool is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addCreatedPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        return (this.createdPools.put(pool.getId(), pool) != pool);
    }

    /**
     * Adds the specified pool as an "updated" pool, where an updated pool is defined as a pool
     * which was already defined locally, and was changed or updated during the refresh operation.
     *
     * @param pool
     *  the pool to add as an updated pool
     *
     * @throws IllegalArgumentException
     *  if pool is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addUpdatedPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        return (this.updatedPools.put(pool.getId(), pool) != pool);
    }

    /**
     * Adds the specified pool as a "skipped" pool, where a skipped pool is defined as a pool which
     * was already defined locally, but remained unchanged during the refresh operation.
     *
     * @param pool
     *  the pool to add as a skipped pool
     *
     * @throws IllegalArgumentException
     *  if pool is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addSkippedPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        return (this.skippedPools.put(pool.getId(), pool) != pool);
    }

    /**
     * Adds the specified product as a "created" product, where a created product is defined as a
     * product which did not have a local definition and was created during the refresh operation.
     *
     * @param product
     *  the product to add as a created product
     *
     * @throws IllegalArgumentException
     *  if product is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addCreatedProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        return (this.createdProducts.put(product.getId(), product) != product);
    }

    /**
     * Adds the specified product as an "updated" product, where an updated product is defined as a
     * product which was already defined locally, and was changed or updated during the refresh
     * operation.
     *
     * @param product
     *  the product to add as an updated product
     *
     * @throws IllegalArgumentException
     *  if product is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addUpdatedProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        return (this.updatedProducts.put(product.getId(), product) != product);
    }

    /**
     * Adds the specified product as a "skipped" product, where a skipped product is defined as a
     * product which was already defined locally, but remained unchanged during the refresh
     * operation.
     *
     * @param product
     *  the product to add as a skipped product
     *
     * @throws IllegalArgumentException
     *  if product is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addSkippedProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        return (this.skippedProducts.put(product.getId(), product) != product);
    }

    /**
     * Adds the specified content as a "created" content, where a created content is defined as
     * content which did not have a local definition and was created during the refresh operation.
     *
     * @param content
     *  the content to add as a created content
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addCreatedContent(Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return (this.createdContent.put(content.getId(), content) != content);
    }

    /**
     * Adds the specified content as an "updated" content, where an updated content is defined as
     * content which was already defined locally, and was changed or updated during the refresh
     * operation.
     *
     * @param content
     *  the content to add as an updated content
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addUpdatedContent(Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return (this.updatedContent.put(content.getId(), content) != content);
    }

    /**
     * Adds the specified content as a "skipped" content, where a skipped content is defined as
     * content which was already defined locally, but remained unchanged during the refresh
     * operation.
     *
     * @param content
     *  the content to add as a skipped content
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if this result instance is modified by this operation; false otherwise
     */
    public boolean addSkippedContent(Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return (this.skippedContent.put(content.getId(), content) != content);
    }

    // TODO: Return copy-on-write versions of the maps from the getters, rather than returning
    // our internal maps directly.

    /**
     * Fetches the pools created during refresh as a mapping of pool IDs to pool instances. If no
     * pools were created during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the pools created during refresh, mapped by the pool IDs
     */
    public Map<String, Pool> getCreatedPools() {
        return this.createdPools;
    }

    /**
     * Fetches the pools updated during refresh as a mapping of pool IDs to pool instances. If no
     * pools were updated during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the pools updated during refresh, mapped by the pool IDs
     */
    public Map<String, Pool> getUpdatedPools() {
        return this.updatedPools;
    }

    /**
     * Fetches the pools skipped during refresh as a mapping of pool IDs to pool instances. If no
     * pools were skipped during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the pools skipped during refresh, mapped by the pool IDs
     */
    public Map<String, Pool> getSkippedPools() {
        return this.skippedPools;
    }

    /**
     * Fetches the pool with the provided pool ID if it was processed during the refresh operation.
     * If the pool ID does not match any pool processed during refresh, this method returns null.
     * <p></p>
     * <strong>Note:</strong>
     * This method may pull from any of the known sets of pools, and provides no guarantee or
     * indication of the individual pool's refresh result.
     *
     * @param id
     *  the ID of the pool to fetch
     *
     * @return
     *  the pool with the provided pool ID, or null if the pool ID does not map to a pool processed
     *  during refresh
     */
    public Pool getPool(String id) {
        Pool pool;

        pool = this.createdPools.get(id);
        if (pool != null) {
            return pool;
        }

        pool = this.updatedPools.get(id);
        if (pool != null) {
            return pool;
        }

        return this.skippedPools.get(id);
    }

    /**
     * Fetches the products created during refresh as a mapping of product IDs to product instances.
     * If no products were created during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the products created during refresh, mapped by the product IDs
     */
    public Map<String, Product> getCreatedProducts() {
        return this.createdProducts;
    }

    /**
     * Fetches the products updated during refresh as a mapping of product IDs to product instances.
     * If no products were updated during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the products updated during refresh, mapped by the product IDs
     */
    public Map<String, Product> getUpdatedProducts() {
        return this.updatedProducts;
    }

    /**
     * Fetches the products skipped during refresh as a mapping of product IDs to product instances.
     * If no products were skipped during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the products skipped during refresh, mapped by the product IDs
     */
    public Map<String, Product> getSkippedProducts() {
        return this.skippedProducts;
    }

    /**
     * Fetches the product with the provided product ID if it was processed during the refresh
     * operation. If the product ID does not match any product processed during refresh, this method
     * returns null.
     * <p></p>
     * <strong>Note:</strong>
     * This method may pull from any of the known sets of products, and provides no guarantee or
     * indication of the individual product's refresh result.
     *
     * @param id
     *  the ID of the product to fetch
     *
     * @return
     *  the product with the provided product ID, or null if the product ID does not map to a
     *  product processed during refresh
     */
    public Product getProduct(String id) {
        Product product;

        product = this.createdProducts.get(id);
        if (product != null) {
            return product;
        }

        product = this.updatedProducts.get(id);
        if (product != null) {
            return product;
        }

        return this.skippedProducts.get(id);
    }

    /**
     * Fetches the content created during refresh as a mapping of content IDs to content instances.
     * If no content were created during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the content created during refresh, mapped by the content IDs
     */
    public Map<String, Content> getCreatedContent() {
        return this.createdContent;
    }

    /**
     * Fetches the content updated during refresh as a mapping of content IDs to content instances.
     * If no content were updated during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the content updated during refresh, mapped by the content IDs
     */
    public Map<String, Content> getUpdatedContent() {
        return this.updatedContent;
    }

    /**
     * Fetches the content skipped during refresh as a mapping of content IDs to content instances.
     * If no content were skipped during the refresh operation, this method returns an empty map.
     *
     * @return
     *  a map containing the content skipped during refresh, mapped by the content IDs
     */
    public Map<String, Content> getSkippedContent() {
        return this.skippedContent;
    }

    /**
     * Fetches the content with the provided content ID if it was processed during the refresh
     * operation. If the content ID does not match any content processed during refresh, this method
     * returns null.
     * <p></p>
     * <strong>Note:</strong>
     * This method may pull from any of the known sets of content, and provides no guarantee or
     * indication of the individual content's refresh result.
     *
     * @param id
     *  the ID of the content to fetch
     *
     * @return
     *  the content with the provided content ID, or null if the content ID does not map to a content
     *  processed during refresh
     */
    public Content getContent(String id) {
        Content content;

        content = this.createdContent.get(id);
        if (content != null) {
            return content;
        }

        content = this.updatedContent.get(id);
        if (content != null) {
            return content;
        }

        return this.skippedContent.get(id);
    }


    // These methods shouldn't exist. They are purely to work around some legacy code requiring use
    // of everything existing in a cache map

    /**
     * Fetches a map containing all of the pools processed during the refresh operation, mapped by
     * their pool IDs. If the refresh operation did not process any pools, this method returns an
     * empty map.
     *
     * @return
     *  a map containing all pools processed during refresh
     */
    public Map<String, Pool> getProcessedPools() {
        Map<String, Pool> imported = new HashMap<>();

        imported.putAll(this.createdPools);
        imported.putAll(this.updatedPools);
        imported.putAll(this.skippedPools);

        return imported;
    }

    /**
     * Fetches a map containing all of the products processed during the refresh operation, mapped
     * by their product IDs. If the refresh operation did not process any products, this method
     * returns an empty map.
     *
     * @return
     *  a map containing all products processed during refresh
     */
    public Map<String, Product> getProcessedProducts() {
        Map<String, Product> imported = new HashMap<>();

        imported.putAll(this.createdProducts);
        imported.putAll(this.updatedProducts);
        imported.putAll(this.skippedProducts);

        return imported;
    }

    /**
     * Fetches a map containing all of the content processed during the refresh operation, mapped by
     * their content IDs. If the refresh operation did not process any content, this method returns
     * an empty map.
     *
     * @return
     *  a map containing all content processed during refresh
     */
    public Map<String, Content> getProcessedContent() {
        // Make this more efficient/safe/more gooder
        Map<String, Content> imported = new HashMap<>();

        imported.putAll(this.createdContent);
        imported.putAll(this.updatedContent);
        imported.putAll(this.skippedContent);

        return imported;
    }

}
