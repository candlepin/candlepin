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

import org.candlepin.controller.ContentManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.controller.refresher.builders.ContentNodeBuilder;
import org.candlepin.controller.refresher.builders.NodeFactory;
import org.candlepin.controller.refresher.builders.PoolNodeBuilder;
import org.candlepin.controller.refresher.builders.ProductNodeBuilder;
import org.candlepin.controller.refresher.mappers.ContentMapper;
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.mappers.PoolMapper;
import org.candlepin.controller.refresher.mappers.ProductMapper;
import org.candlepin.controller.refresher.visitors.ContentNodeVisitor;
import org.candlepin.controller.refresher.visitors.NodeProcessor;
import org.candlepin.controller.refresher.visitors.PoolNodeVisitor;
import org.candlepin.controller.refresher.visitors.ProductNodeVisitor;
import org.candlepin.controller.util.EntityVersioningRetryWrapper;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.Transactional;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;



/**
 * The RefreshWorker gathers upstream objects to refresh, and then performs the actual work to
 * update their local representations.
 */
public class RefreshWorker {
    private static Logger log = LoggerFactory.getLogger(RefreshWorker.class);

    /**
     * The number of times to retry certain CRUD operations which may fail as a result of
     * entity versioning constraints
     */
    private static final int VERSIONING_CONSTRAINT_VIOLATION_RETRIES = 4;

    /** Default grace period for orphaned entities */
    private static final int ORPHANED_ENTITY_DEFAULT_GRACE_PERIOD = -1;

    private final PoolCurator poolCurator;
    private final ContentCurator contentCurator;
    private final OwnerContentCurator ownerContentCurator;
    private final OwnerProductCurator ownerProductCurator;
    private final ProductCurator productCurator;

    private PoolMapper poolMapper;
    private ProductMapper productMapper;
    private ContentMapper contentMapper;

    private int orphanedEntityGracePeriod;


    /**
     * Creates a new RefreshWorker
     */
    @Inject
    public RefreshWorker(PoolCurator poolCurator, ProductCurator productCurator,
        OwnerProductCurator ownerProductCurator, ContentCurator contentCurator,
        OwnerContentCurator ownerContentCurator) {

        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.ownerProductCurator = Objects.requireNonNull(ownerProductCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);

        this.poolMapper = new PoolMapper();
        this.productMapper = new ProductMapper();
        this.contentMapper = new ContentMapper();

        this.orphanedEntityGracePeriod = ORPHANED_ENTITY_DEFAULT_GRACE_PERIOD;
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
     * Sets the orphaned entity grace period, which determines how long a given entity must be
     * orphaned before it will be deleted as part of the cleanup step. The behavior of entity
     * cleanup will differ depending on this value:
     * <ul>
     *  <li>
     *  If the grace period is a positive integer, entities which have been orphaned for more
     *  than the number of days will be removed
     *  </li>
     *  <li>
     *  If the grace period is zero, entities which are orphaned will be cleaned up immediately
     *  </li>
     *  <li>
     *  If the grace period is a negative integer, orphaned entities will not be removed at all
     *  </li>
     * </ul>
     *
     * @param period
     *  the orphaned entity grace period in days, or a negative value to disable orphaned entity
     *  cleanup
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker setOrphanedEntityGracePeriod(int period) {
        this.orphanedEntityGracePeriod = period;
        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addSubscriptions(SubscriptionInfo... subscriptions) {
        if (subscriptions != null) {
            this.addSubscriptions(Arrays.asList(subscriptions));
        }

        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addSubscriptions(Collection<? extends SubscriptionInfo> subscriptions) {
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
                this.addProducts(subscription.getProduct());
            }
        }

        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addProducts(ProductInfo... products) {
        if (products != null) {
            this.addProducts(Arrays.asList(products));
        }

        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addProducts(Collection<? extends ProductInfo> products) {
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

                // Add any nested products
                this.addProducts(product.getDerivedProduct());
                this.addProducts(product.getProvidedProducts());

                // Add any content attached to this product...
                this.addProductContent(product.getProductContent());

            }
        }

        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addProductContent(ProductContentInfo... contents) {
        if (contents != null) {
            this.addProductContent(Arrays.asList(contents));
        }

        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addProductContent(Collection<? extends ProductContentInfo> contents) {
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

        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addContent(ContentInfo... contents) {
        if (contents != null) {
            this.addContent(Arrays.asList(contents));
        }

        return this;
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
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addContent(Collection<? extends ContentInfo> contents) {
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

        return this;
    }

    /**
     * Fetches the current orphaned entity grace period for this refresher. See the documentation
     * associated with the setOrphanedEntityGracePeriod method for details on the meaning of
     * specific values.
     *
     * @return
     *  the current orphaned entity grace period for this refresher
     */
    public int getOrphanedEntityGracePeriod() {
        return this.orphanedEntityGracePeriod;
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
    @SuppressWarnings("indentation")
    public RefreshResult execute(Owner owner) {
        Transactional<RefreshResult> block = this.poolCurator.transactional((args) -> {
            NodeMapper nodeMapper = new NodeMapper();

            NodeFactory nodeFactory = new NodeFactory()
                .setNodeMapper(nodeMapper)
                .addMapper(this.poolMapper)
                .addMapper(this.productMapper)
                .addMapper(this.contentMapper)
                .addBuilder(new PoolNodeBuilder())
                .addBuilder(new ProductNodeBuilder())
                .addBuilder(new ContentNodeBuilder());

            NodeProcessor nodeProcessor = new NodeProcessor()
                .setNodeMapper(nodeMapper)
                .addVisitor(new PoolNodeVisitor(this.poolCurator))
                .addVisitor(new ProductNodeVisitor(this.productCurator, this.ownerProductCurator,
                    this.orphanedEntityGracePeriod))
                .addVisitor(new ContentNodeVisitor(this.contentCurator, this.ownerContentCurator));

            // Obtain system locks on products and content so we don't need to worry about
            // orphan cleanup deleting stuff out from under us
            this.ownerContentCurator.getSystemLock(ContentManager.SYSTEM_LOCK, LockModeType.PESSIMISTIC_READ);
            this.ownerProductCurator.getSystemLock(ProductManager.SYSTEM_LOCK, LockModeType.PESSIMISTIC_READ);

            // Clear existing entities in the event this isn't the first run of this refresher
            this.poolMapper.clearExistingEntities();
            this.productMapper.clearExistingEntities();
            this.contentMapper.clearExistingEntities();

            // Add in our existing entities
            this.poolMapper.addExistingEntities(
                this.poolCurator.listByOwnerAndTypes(owner.getId(), PoolType.NORMAL, PoolType.DEVELOPMENT));

            this.productMapper.addExistingEntities(this.ownerProductCurator.getProductsByOwner(owner).list());
            this.contentMapper.addExistingEntities(this.ownerContentCurator.getContentByOwner(owner).list());

            // Have our node factory build the node trees
            nodeFactory.buildNodes(owner);

            // Process our nodes, starting at the roots, letting the processors build up any persistence
            // state necessary to finalize everything
            return nodeProcessor.processNodes();
        });

        // Attempt to retry if we're not already in a transaction
        // Impl note: at the time of writing, nested transactions are not supported in Hibernate
        EntityTransaction transaction = this.poolCurator.getTransaction();
        if (transaction == null || !transaction.isActive()) {
            // Retry this operation if we hit a constraint violation on the entity version constraint
            return new EntityVersioningRetryWrapper()
                .retries(VERSIONING_CONSTRAINT_VIOLATION_RETRIES)
                .execute(() -> block.execute());
        }
        else {
            // A transaction is already active, just run the block as-is
            return block.allowExistingTransactions()
                .execute();
        }
    }

}
