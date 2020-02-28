/**
 * Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
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
package org.candlepin.controller;




public class RefreshResult {

    private Map<String, Pool> createdPools;
    private Map<String, Pool> updatedPools;
    private Set<String> skippedPools;

    private Map<String, Product> createdProducts;
    private Map<String, Product> updatedProducts;
    private Set<String> skippedProducts;

    private Map<String, Content> createdContent;
    private Map<String, Content> updatedContent;
    private Set<String> skippedContent;



    public RefreshResult() {
        this.createdPools = new HashMap<>();
        this.updatedPools = new HashMap<>();
        this.skippedPools = new HashSet<>();

        this.createdProducts = new HashMap<>();
        this.updatedProducts = new HashMap<>();
        this.skippedProducts = new HashSet<>();

        this.createdContent = new HashMap<>();
        this.updatedContent = new HashMap<>();
        this.skippedContent = new HashSet<>();
    }

    public boolean addCreatedPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        return (this.createdPools.put(pool.getId(), pool) != pool);
    }

    public boolean addUpdatedPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        return (this.updatedPools.put(pool.getId(), pool) != pool);
    }

    public boolean addSkippedPool(String poolId) {
        if (poolId == null || poolId.isEmpty()) {
            throw new IllegalArgumentException("poolId is null or empty");
        }

        return this.skippedPools.add(poolId);
    }

    public boolean addCreatedProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        return (this.createdProducts.put(product.getId(), product) != product);
    }

    public boolean addUpdatedProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        return (this.updatedProducts.put(product.getId(), product) != product);
    }

    public boolean addSkippedProduct(String productId) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        return this.skippedProducts.add(productId);
    }

    public boolean addCreatedContent(Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return (this.createdContents.put(content.getId(), content) != content);
    }

    public boolean addUpdatedContent(Content content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        return (this.updatedContents.put(content.getId(), content) != content);
    }

    public boolean addSkippedContent(String contentId) {
        if (contentId == null || contentId.isEmpty()) {
            throw new IllegalArgumentException("contentId is null or empty");
        }

        return this.skippedContents.add(contentId);
    }
















}
