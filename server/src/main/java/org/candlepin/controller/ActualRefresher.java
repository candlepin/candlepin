/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
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
 * The ActualRefresher class does actual refresh operations, as opposed to calling into other
 * classes to do the refresh work. It does that, too, but the Refresher name is already in use
 * and this is PoC code so I'm not going to change the world to call this something a little
 * nicer... yet.
 *
 * This is looking like it'll almost entirely replace the ImportedEntityCompiler, and its
 * primary output from the execute operation is going to be a beefier version of ImportResult
 * that allows for looking up things directly rather than just spitting out maps.
 */
public class ActualRefresher {

    private static Logger log = LoggerFactory.getLogger(ImportedEntityCompiler.class);

    private Map<String, SubscriptionInfo> importSubscriptions;
    private Map<String, ProductInfo> importProducts;
    private Map<String, ContentInfo> importContent;

    /**
     * Creates a new ImportedEntityCompiler
     */

    public ImportedEntityCompiler() {
        this.importSubscriptions = new HashMap<>();
        this.importProducts = new HashMap<>();
        this.importContent = new HashMap<>();
    }

    /**
     * Adds the specified subscriptions to this refresher. If a given subscription has already
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
     * Adds the specified subscriptions to this refresher. If a given subscription has already
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

                SubscriptionInfo existing = this.importSubscriptions.get(subscription.getId());
                if (existing != null && !existing.equals(subscription)) {
                    log.warn("Multiple versions of the same subscription received during refresh; " +
                        "discarding previous: {} => {}, {}", subscription.getId(), existing, subscription);
                }

                this.importSubscriptions.put(subscription.getId(), subscription);

                // Add any products attached to this subscription...
                this.addProducts(subscription.getProduct());
                this.addProducts(subscription.getDerivedProduct());
            }
        }
    }

    /**
     * Adds the specified products to this refresher. If a given product has already
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
     * Adds the specified products to this refresher. If a given product has already
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

                ProductInfo existing = this.importProducts.get(product.getId());
                if (existing != null && !existing.equals(product)) {
                    log.warn("Multiple versions of the same product received during refresh; " +
                        "discarding previous: {} => {}, {}", product.getId(), existing, product);
                }

                this.importProducts.put(product.getId(), product);

                // Add any content attached to this product...
                this.addProductContent(product.getProductContent());
            }
        }
    }

    /**
     * Adds the specified content to this refresher. If a given content has already
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
     * Adds the specified content to this refresher. If a given content has already
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

                ContentInfo existing = this.importContent.get(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.importContent.put(content.getId(), content);
            }
        }
    }

    /**
     * Adds the specified content to this refresher. If a given content has already
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
     * Adds the specified content to this refresher. If a given content has already
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

                ContentInfo existing = this.importContent.get(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.importContent.put(content.getId(), content);
            }
        }
    }

    /**
     * Fetches the compiled set of subscriptions to import, mapped by subscription ID. If no subscriptions
     * have been added, this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the subscriptions to import
     */
    public Map<String, ? extends SubscriptionInfo> getSubscriptions() {
        return this.importSubscriptions;
    }

    /**
     * Fetches the compiled set of products to import, mapped by product ID. If no products have been added,
     * this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the products to import
     */
    public Map<String, ? extends ProductInfo> getProducts() {
        return this.importProducts;
    }

    /**
     * Fetches the compiled set of content to import, mapped by content ID. If no content has been added,
     * this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the content to import
     */
    public Map<String, ? extends ContentInfo> getContent() {
        return this.importContent;
    }

    /**
     * Performs the import operation on the currently compiled objects
     */
    public void execute() {

        // Step 01: Determine which entities are to be created, updated, or skipped
        // Step 02: From the entities being updated, find all existing entities *not* being imported
        //          that will be implicitly updated
        // Step 03: Initialize a list of "finished" entities
        // Step 04: Invoke the recursive block with the combined list of entities to be created or
        //          updated as the current list.

        // Recursive block:
        // Step R1: For each entity in the current list, check if the entity has already been
        //          processed. If so, continue to the next entity in the list
        // Step R2: Create a list of all children entities of the current entity.
        // Step R3: If child list is not empty, invoke the recursive block with the child list as
        //          the current list
        // Step R4: Apply the received changes to the entity, using only the finished entities for
        //          resolution of child entity referencing
        // Step R5: Store the updated entity in the finished entity collection
        // Step R6: If current entity list has more entities, continue to next entity in the list;
        //          otherwise, break out of the current invocation of the recursive block

        // Step 05: Persist pending entity changes
        // Step 06: Update entity references not already corrected in the traversal above. This
        //          should include updating owners, pools, activation keys, and other such objects
        //          which reference products or content, but are not part of the tree.

        // Step 07: Return the collections of the finalized imported entities


    }




}
