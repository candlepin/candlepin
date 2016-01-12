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
package org.candlepin.service;

import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Product data may originate from a separate service outside Candlepin in some
 * configurations. This interface defines the operations Candlepin requires
 * related to Product data. Different implementations can handle whether or not
 * this info comes from Candlepin's DB or from a separate service.
 */
public interface ProductServiceAdapter {

    /**
     * Query a specific product by its string ID. If a product isn't found
     * null is returned.
     *
     * @param id the product ID to search by
     * @return specific product, null if not found
     */
    Product getProductById(String id);

    /**
     * Query a list of products matching the given string IDs. Only the products
     * found will be returned. When no matching products are found, an empty List
     * will be returned. If the ids param is null or empty, an empty list
     * of products will be returned.
     *
     * @param ids list of product ids
     * @return list of products matching the given string IDs
     */
    List<Product> getProductsByIds(Collection<String> ids);

    /**
     * List all Products.
     * @return all products or an empty list if none are found.
     */
    List<Product> getProducts();

    /**
     * Creates a new {@link Product}.
     *
     * @param product
     * @return the created {@link Product}
     * @throws UnsupportedOperationException if this implementation does not
     *         support new product creation
     */
    Product createProduct(Product product);

    /**
     * Deletes the specified product.
     * @param product the product to delete.
     */
    void deleteProduct(Product product);

    /**
     * Gets the certificate that defines the given product, creating one
     * if necessary.
     *
     * @param product the source product of the certificate.
     * @return the stored or created {@link ProductCertificate}
     */
    ProductCertificate getProductCertificate(Product product);

    /**
     * Used to purge product cache.
     *
     * @param cachedKeys productIds to remove from cache.
     */
    void purgeCache(Collection<String> cachedKeys);

    /**
     * Remove content associated with a product.
     *
     * @param productId Product ID.
     * @param contentId Content ID.
     */
    void removeContent(String productId, String contentId);

    /**
     * Determines if the specified {@link Product} has subscriptions associated
     * with it.
     *
     * @param prod the {@link Product} to check.
     * @return true if the product has subscriptions associated with it,
     *         false otherwise.
     */
    boolean productHasSubscriptions(Product prod);

    /**
     * Merges the specified {@link Product} with the existing product.
     *
     * @param prod the {@link Product} to merge into the existing.
     * @return the merged {@link Product}
     */
    Product mergeProduct(Product prod);

    /**
     * Returns the ids of all {@link Product}s that have content matching one
     * or more of the specified content ids. An empty set should be returned if
     * there are no matches, or the contentId collection specified is null or
     * empty.
     *
     * @param contentId a collection of content ids to fetch products for.
     * @return a set of product ids matching one or more of the specified content ids.
     */
    Set<String> getProductsWithContent(Collection<String> contentId);
}
