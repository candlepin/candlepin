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

import org.candlepin.controller.refresher.builders.ContentNodeBuilder;
import org.candlepin.controller.refresher.builders.NodeFactory;
import org.candlepin.controller.refresher.builders.ProductNodeBuilder;
import org.candlepin.controller.refresher.mappers.ContentMapper;
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.mappers.PoolMapper;
import org.candlepin.controller.refresher.mappers.ProductMapper;
import org.candlepin.controller.refresher.visitors.ContentNodeVisitor;
import org.candlepin.controller.refresher.visitors.NodeProcessor;
import org.candlepin.controller.refresher.visitors.ProductNodeVisitor;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;



/**
 * The RefreshWorker gathers upstream objects to refresh, and then performs the actual work to
 * update their local representations.
 */
public class RefreshWorker {
    private static Logger log = LoggerFactory.getLogger(RefreshWorker.class);

    private ContentCurator contentCurator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerProductCurator ownerProductCurator;
    private ProductCurator productCurator;

    private PoolMapper poolMapper;
    private ProductMapper productMapper;
    private ContentMapper contentMapper;


    /**
     * Creates a new RefreshWorker
     */
    @Inject
    public RefreshWorker(ProductCurator productCurator, OwnerProductCurator ownerProductCurator,
        ContentCurator contentCurator, OwnerContentCurator ownerContentCurator) {

        this.productCurator = Objects.requireNonNull(productCurator);
        this.ownerProductCurator = Objects.requireNonNull(ownerProductCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);

        this.poolMapper = new PoolMapper();
        this.productMapper = new ProductMapper();
        this.contentMapper = new ContentMapper();
    }

    /**
     * Clears the subscriptions, products, and content collected in this refresh worker
     */
    public void clear() {
        this.poolMapper.clear();
        this.productMapper.clear();
        this.contentMapper.clear();
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
        if (subscriptions != null) {
            this.addSubscriptions(Arrays.asList(subscriptions));
        }
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

                SubscriptionInfo existing = this.poolMapper.getImportedEntity(subscription.getId());
                if (existing != null && !existing.equals(subscription)) {
                    log.warn("Multiple versions of the same subscription received during refresh; " +
                        "discarding previous: {} => {}, {}", subscription.getId(), existing, subscription);
                }

                this.poolMapper.addImportedEntity(subscription);

                // Add any products attached to this subscription...
                this.addProducts(subscription.getProduct(), subscription.getDerivedProduct());
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
        if (products != null) {
            this.addProducts(Arrays.asList(products));
        }
    }

    /**
     * Adds the specified products to this refresher. If a given product has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced.
     * <p></p>
     * Child entities attached to the product (such as provided products and content) will also be
     * added to this refresher. Null products and children will be silently discarded.
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

                ProductInfo existing = this.productMapper.getImportedEntity(product.getId());
                if (existing != null && !existing.equals(product)) {
                    log.warn("Multiple versions of the same product received during refresh; " +
                        "discarding previous: {} => {}, {}", product.getId(), existing, product);
                }

                this.productMapper.addImportedEntity(product);

                // Add any nested provided products
                this.addProducts(product.getProvidedProducts());

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
        if (contents != null) {
            this.addProductContent(Arrays.asList(contents));
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

                ContentInfo existing = this.contentMapper.getImportedEntity(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.contentMapper.addImportedEntity(content);
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
        if (contents != null) {
            this.addContent(Arrays.asList(contents));
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

                ContentInfo existing = this.contentMapper.getImportedEntity(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.contentMapper.addImportedEntity(content);
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
        return this.poolMapper.getImportedEntities();
    }

    /**
     * Fetches the compiled set of products to import, mapped by product ID. If no products have been added,
     * this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the products to import
     */
    public Map<String, ? extends ProductInfo> getProducts() {
        return this.productMapper.getImportedEntities();
    }

    /**
     * Fetches the compiled set of content to import, mapped by content ID. If no content has been added,
     * this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the content to import
     */
    public Map<String, ? extends ContentInfo> getContent() {
        return this.contentMapper.getImportedEntities();
    }

    /**
     * Performs the import operation on the currently compiled objects
     *
     * @return
     *  the result of this refresh operation
     */
    @Transactional
    public RefreshResult execute(Owner owner) {
        NodeMapper nodeMapper = new NodeMapper();

        NodeFactory nodeFactory = new NodeFactory()
            .setNodeMapper(nodeMapper)
            .addBuilder(new ProductNodeBuilder(this.productMapper))
            .addBuilder(new ContentNodeBuilder(this.contentMapper));

        NodeProcessor processor = new NodeProcessor()
            .setNodeMapper(nodeMapper)
            .addVisitor(new ProductNodeVisitor(this.productCurator, this.ownerProductCurator))
            .addVisitor(new ContentNodeVisitor(this.contentCurator, this.ownerContentCurator));

        // Add in our existing entities
        this.productMapper.addExistingEntities(this.ownerProductCurator.getProductsByOwner(owner).list());
        this.contentMapper.addExistingEntities(this.ownerContentCurator.getContentByOwner(owner).list());

        // If we have any versioned entities, fetch the candidate mapping
        Set<String> pids = this.productMapper.getEntityIds();
        if (pids != null && !pids.isEmpty()) {
            Map<String, Set<Product>> vmap = this.ownerProductCurator.getVersionedProductsById(owner, pids);
            this.productMapper.setCandidateEntitiesMap(vmap);
        }

        Set<String> cids = this.contentMapper.getEntityIds();
        if (cids != null && !cids.isEmpty()) {
            Map<String, Set<Content>> vmap = this.ownerContentCurator.getVersionedContentById(owner, cids);
            this.contentMapper.setCandidateEntitiesMap(vmap);
        }

        // Step through our IDs and have our node factory build some nodes
        // TODO: Add pool/subscription processing here.

        // FIXME: Update the NodeFactory to handle this for us. We should provide the entity mappers
        // directly to it and let it just build the node trees. This is just some boilerplate that
        // could be improved.

        for (String id : pids) {
            nodeFactory.buildNode(owner, Product.class, id);
        }

        for (String id : cids) {
            nodeFactory.buildNode(owner, Content.class, id);
        }

        // Process our nodes, starting at the roots, letting the processors build up any persistence
        // state necessary to finalize everything
        processor.processNodes();

        // Finalize our node state and build our result
        RefreshResult result = processor.compileResults();

        return result;
    }

}
