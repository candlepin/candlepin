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
 * related to Product data, different implementations can handle whether or not
 * this info comes from Candlepin's DB or from a separate service.
 */
public interface ProductServiceAdapter {

    /**
     * Query a specific product by its string ID.
     * @param id product id
     * @return specific product
     */
    Product getProductById(String id);

    /**
     * Query a list of products matching the given string IDs.
     * @param ids list of product ids
     * @return list of products matching the given string IDs
     */
    List<Product> getProductsByIds(Collection<String> ids);

    /**
     * List all Products
     * @return all products.
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
     * deletes specified product
     * @param product
     */
    void deleteProduct(Product product);

    /**
     * Gets the certificate that defines the given product, creating one
     * if necessary.
     *
     * @param product
     * @return the stored or created {@link ProductCertificate}
     */
    ProductCertificate getProductCertificate(Product product);

    /**
     * Used to purge product cache
     */
    void purgeCache();

    /**
     * Remove content associated to a product.
     * @param productId Product ID.
     * @param contentId Content ID.
     */
    void removeContent(String productId, String contentId);

    boolean productHasSubscriptions(Product prod);

    void addRely(String productId, String relyId);

    void removeRely(String productId, String relyId);

    Set<String> getReliesOn(String productId);

    Product mergeProduct(Product prod);

    Set<String> getProductsWithContent(Collection<String> contentId);
}
