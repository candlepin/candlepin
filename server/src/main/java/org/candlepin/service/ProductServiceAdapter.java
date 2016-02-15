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

import org.candlepin.model.Owner;
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
     * @param owner the owner/org in which to search for the product
     * @param id product id
     * @return specific product
     */
    Product getProductById(Owner owner, String id);

    /**
     * Query a list of products matching the given string IDs.
     * @param owner the owner/org in which to search for products
     * @param ids list of product ids
     * @return list of products matching the given string IDs
     */
    List<Product> getProductsByIds(Owner owner, Collection<String> ids);

    /**
     * List all Products
     * @return all products.
     */
    List<Product> getProducts();

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
     * @param cachedKeys productIds to remove from cache
     */
    void purgeCache(Collection<String> cachedKeys);

    /**
     * Remove content associated to a product.
     * @param productId Product ID.
     * @param contentId Content ID.
     */
    void removeContent(Owner owner, String productId, String contentId);

    /**
     * Checks if the specified product has any existing subscriptions for the given owner.
     * <p/>
     * Implementations which do not provide owner-specific product instancing may ignore the owner
     * parameter, but may need to take extra steps to ensure the results pertain to the correct
     * product.
     *
     * @param product
     *  The product for which to check for subscriptions
     *
     * @param owner
     *  The owner to which subscriptions should be restricted
     *
     * @return
     *  true if there are any subscriptions for the given product; false otherwise
     */
    boolean productHasSubscriptions(Product product, Owner owner);

    /**
     * Merges the changes provided by the specified product instance into the backing data store for
     * the given owner.
     * <p/>
     * Implementations which do not provide owner-specific product instancing may ignore the owner
     * parameter, but may need to take extra steps to ensure the results pertain to the correct
     * product.
     *
     * @param product
     *  The product instance containing the updates to merge
     *
     * @owner owner
     *  The owner for which the changes are to be applied
     *
     * @return
     *  the updated/merged product instance
     */
    Product mergeProduct(Product product, Owner owner);

    Set<String> getProductsWithContent(Owner owner, Collection<String> contentId);
}
