/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;



/**
 * The ImportedEntityCompiler builds collections of subscriptions, products and content to import.
 */
public class ImportedEntityCompiler {
    private static Logger log = LoggerFactory.getLogger(ImportedEntityCompiler.class);

    protected Map<String, SubscriptionInfo> subscriptions;
    protected Map<String, ProductInfo> products;
    protected Map<String, ContentInfo> content;

    /**
     * Creates a new ImportedEntityCompiler
     */

    public ImportedEntityCompiler() {
        this.subscriptions = new HashMap<>();
        this.products = new HashMap<>();
        this.content = new HashMap<>();
    }

    /**
     * Adds the specified subscriptions to this entity compiler. If a given subscription has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Products and content attached to the subscriptions will be
     * mapped by this compiler. Null subscriptions will be silently ignored.
     *
     * @param subscriptions
     *  The subscriptions to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided subscription does not contain a valid subscription ID
     */
    public void addSubscriptions(SubscriptionInfo... subscriptions) {
        this.addSubscriptions(Arrays.asList(subscriptions));
    }

    /**
     * Adds the specified subscriptions to this entity compiler. If a given subscription has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Products and content attached to the subscriptions will be
     * mapped by this compiler. Null subscriptions will be silently ignored.
     *
     * @param subscriptions
     *  The subscriptions to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided subscription does not contain a valid subscription ID
     */
    public void addSubscriptions(Collection<? extends SubscriptionInfo> subscriptions) {
        if (subscriptions != null) {
            for (SubscriptionInfo subscription : subscriptions) {
                if (subscription == null) {
                    continue;
                }

                if (subscription.getId() == null || subscription.getId().isEmpty()) {
                    String msg = String.format(
                        "subscription does not contain a mappable Red Hat ID: %s", subscription);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                SubscriptionInfo existing = this.subscriptions.get(subscription.getId());
                if (existing != null && !existing.equals(subscription)) {
                    log.warn("Multiple versions of the same subscription received during refresh; " +
                        "discarding previous: {} => {}, {}", subscription.getId(), existing, subscription);
                }

                this.subscriptions.put(subscription.getId(), subscription);

                // Add any products attached to this subscription...
                this.addProducts(subscription.getProduct());
                this.addProducts(subscription.getDerivedProduct());
            }
        }
    }

    /**
     * Adds the specified products to this entity compiler. If a given product has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Content attached to the products will be mapped by this
     * compiler. Null products will be silently ignored.
     *
     * @param products
     *  The products to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided product does not contain a valid product ID
     */
    public void addProducts(ProductInfo... products) {
        this.addProducts(Arrays.asList(products));
    }

    /**
     * Adds the specified products to this entity compiler. If a given product has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Content attached to the products will be mapped by this
     * compiler. Null products will be silently ignored.
     *
     * @param products
     *  The products to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided product does not contain a valid product ID
     */
    public void addProducts(Collection<? extends ProductInfo> products) {
        if (products != null) {
            for (ProductInfo product : products) {
                if (product == null) {
                    continue;
                }

                if (product.getId() == null || product.getId().isEmpty()) {
                    String msg = String.format("product does not contain a mappable Red Hat ID: %s", product);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                ProductInfo existing = this.products.get(product.getId());
                if (existing != null && !existing.equals(product)) {
                    log.warn("Multiple versions of the same product received during refresh; " +
                        "discarding previous: {} => {}, {}", product.getId(), existing, product);
                }

                this.products.put(product.getId(), product);

                // Add any content attached to this product...
                this.addProductContent(product.getProductContent());

                if (product.getProvidedProducts() != null) {
                    addProducts(product.getProvidedProducts());
                }
            }
        }
    }

    /**
     * Adds the specified content to this entity compiler. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addProductContent(ProductContentInfo... contents) {
        this.addProductContent(Arrays.asList(contents));
    }

    /**
     * Adds the specified content to this entity compiler. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addProductContent(Collection<? extends ProductContentInfo> contents) {
        if (contents != null) {
            for (ProductContentInfo container : contents) {
                if (container == null) {
                    continue;
                }

                ContentInfo content = container.getContent();

                // Do some simple mapping validation
                if (content == null) {
                    String msg = "collection contains an incomplete product-content mapping";

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                if (content.getId() == null || content.getId().isEmpty()) {
                    String msg = String.format("content does not contain a mappable Red Hat ID: %s", content);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                ContentInfo existing = this.content.get(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.content.put(content.getId(), content);
            }
        }
    }

    /**
     * Adds the specified content to this entity compiler. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addContent(ContentInfo... contents) {
        this.addContent(Arrays.asList(contents));
    }

    /**
     * Adds the specified content to this entity compiler. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addContent(Collection<? extends ContentInfo> contents) {
        if (contents != null) {
            for (ContentInfo content : contents) {
                if (content == null) {
                    continue;
                }

                if (content.getId() == null || content.getId().isEmpty()) {
                    String msg = String.format("content does not contain a mappable Red Hat ID: %s", content);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                ContentInfo existing = this.content.get(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.content.put(content.getId(), content);
            }
        }
    }

    /**
     * Fetches the compiled subscriptions, mapped by subscription ID. If no subscriptions have been
     * added, this method returns an empty map.
     *
     * @return
     *  A mapping of the compiled, imported subscriptions
     */
    public Map<String, ? extends SubscriptionInfo> getSubscriptions() {
        return this.subscriptions;
    }

    /**
     * Fetches the compiled products, mapped by product ID. If no products have been added, this
     * method returns an empty map.
     *
     * @return
     *  A mapping of the compiled, imported products
     */
    public Map<String, ? extends ProductInfo> getProducts() {
        return this.products;
    }

    /**
     * Fetches the compiled content, mapped by content ID. If no content have been
     * added, this method returns an empty map.
     *
     * @return
     *  A mapping of the compiled, imported content
     */
    public Map<String, ? extends ContentInfo> getContent() {
        return this.content;
    }

}
