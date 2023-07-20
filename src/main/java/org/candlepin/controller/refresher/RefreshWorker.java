/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.controller.refresher.RefreshResult.EntityState;
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
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
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
     * Adds the specified subscriptions to this refresher, and any children entities each
     * subscription contains. If a given subscription has already been added, but differs from the
     * existing version, a warning will be generated and the previous entry will be replaced. Null
     * entities within the collection will be silently discarded.
     *
     * @param subscriptions
     *  a collection of subscriptions to add to this refresher
     *
     * @throws IllegalArgumentException
     *  if any of the given subscriptions lacks a valid, mappable ID
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
     * Adds the specified subscriptions to this refresher, and any children entities each
     * subscription contains. If a given subscription has already been added, but differs from the
     * existing version, a warning will be generated and the previous entry will be replaced. Null
     * entities within the collection will be silently discarded.
     *
     * @param subscriptions
     *  a collection of subscriptions to add to this refresher
     *
     * @throws IllegalArgumentException
     *  if any of the given subscriptions lacks a valid, mappable ID
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addSubscriptions(Collection<? extends SubscriptionInfo> subscriptions) {
        if (subscriptions != null) {
            this.poolMapper.addImportedEntities(subscriptions);

            Collection<ProductInfo> products = subscriptions.stream()
                .map(SubscriptionInfo::getProduct)
                .collect(Collectors.toList());

            this.addProducts(products);
        }

        return this;
    }

    /**
     * Adds the specified upstream products to this refresher, and any children entities each
     * product contains. If a given product has already been added, but differs from the existing
     * version, a warning will be generated and the previous entry will be replaced. Null entities
     * within the collection will be silently discarded.
     *
     * @param products
     *  a collection of upstream products to add to this refresher
     *
     * @throws IllegalArgumentException
     *  if any of the given products lacks a valid, mappable ID
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addProducts(Collection<? extends ProductInfo> products) {
        if (products != null) {
            this.productMapper.addImportedEntities(products);

            for (ProductInfo product : products) {
                if (product == null) {
                    continue;
                }

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
     * Adds the specified products to this refresher, and any children entities each product
     * contains. If a given product has already been added, but differs from the existing version,
     * a warning will be generated and the previous entry will be replaced. Null entities within the
     * collection will be silently discarded.
     *
     * @param products
     *  the products to add to this refresher
     *
     * @throws IllegalArgumentException
     *  if any of the given products lacks a valid, mappable ID
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
     * Utility method for unpacking collections of upstream product content to add to this refresher.
     *
     * @param productContent
     *  a collection of upstream product content to add to this refresher
     *
     * @throws IllegalArgumentException
     *  if any of the given content lacks a valid, mappable ID, or any product-content instance has
     *  a null content
     */
    private void addProductContent(Collection<? extends ProductContentInfo> productContent) {
        if (productContent != null) {
            Collection<ContentInfo> filtered = productContent.stream()
                .filter(Objects::nonNull)
                .map(container -> {
                    ContentInfo content = container.getContent();
                    if (content == null) {
                        String errmsg = "Content collection contains an incomplete product-content mapping!";
                        throw new IllegalArgumentException(errmsg);
                    }

                    return content;
                }).collect(Collectors.toList());

            this.addContent(filtered);
        }
    }

    /**
     * Adds the specified content to this refresher. If a given content has already been added, but
     * differs from the existing version, a warning will be generated and the previous entry will be
     * replaced. Null entities within the collection will be silently discarded.
     *
     * @param content
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addContent(Collection<? extends ContentInfo> content) {
        this.contentMapper.addImportedEntities(content);
        return this;
    }

    /**
     * Adds the specified content to this refresher. If a given content has already been added, but
     * differs from the existing version, a warning will be generated and the previous entry will be
     * replaced. Null entities within the collection will be silently discarded.
     *
     * @param content
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     *
     * @return
     *  a reference to this refresh worker
     */
    public RefreshWorker addContent(ContentInfo... content) {
        if (content != null) {
            this.addContent(Arrays.asList(content));
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
     * Maps the given collection of existing pools, and their refresh-critical children entities.
     *
     * @param pools
     *  the collection of pool entities to map
     */
    private void mapExistingPools(Collection<Pool> pools) {
        if (pools == null || pools.isEmpty()) {
            return;
        }

        this.poolMapper.addExistingEntities(pools);

        Set<String> productUuids = pools.stream()
            .map(Pool::getProductUuid)
            .collect(Collectors.toSet());

        // TODO: This lookup may not be necessary once the native query cache invalidation issue
        // is resolved.

        Set<Product> products = this.productCurator.getProductsByUuidCached(productUuids);

        this.mapExistingProducts(products);
    }

    /**
     * Maps the given collection of existing products, and their refresh-critical children entities.
     *
     * @param products
     *  the collection of product entities to map
     */
    private void mapExistingProducts(Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        this.productMapper.addExistingEntities(products);

        Set<String> productUuids = products.stream()
            .map(Product::getUuid)
            .collect(Collectors.toSet());

        // TODO: These lookups may not be necessary once the native query cache invalidation issue
        // is resolved.

        Set<Product> childrenProducts = this.productCurator
            .getChildrenProductsOfProductsByUuids(productUuids);

        this.mapExistingProducts(childrenProducts);

        Set<Content> content = this.contentCurator.getChildrenContentOfProductsByUuids(productUuids);

        this.mapExistingContent(content);
    }

    /**
     * Maps the given collection of existing content.
     *
     * @param content
     *  the collection of content entities to map
     */
    private void mapExistingContent(Collection<Content> content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        this.contentMapper.addExistingEntities(content);
    }

    /**
     * Collects and builds a mapping of products that should exist in the org as a result of the
     * refresh operation, and recreates the org-product mappings for those products.
     *
     * @param owner
     *  the org for which the refresh was performed
     *
     * @param result
     *  a RefreshResult instance containing the products to map to the org
     */
    private void rebuildOwnerProductMapping(Owner owner, RefreshResult result) {
        Set<EntityState> states = Set.of(EntityState.CREATED, EntityState.UPDATED, EntityState.UNCHANGED);

        Map<String, String> entityIdMap = result.streamEntities(Product.class, states)
            .collect(Collectors.toMap(Product::getId, Product::getUuid));

        this.ownerProductCurator.rebuildOwnerProductMapping(owner, entityIdMap);
    }

    /**
     * Collects and builds a mapping of content that should exist in the org as a result of the
     * refresh operation, and recreates the org-product mappings for those content.
     *
     * @param owner
     *  the org for which the refresh was performed
     *
     * @param result
     *  a RefreshResult instance containing the content to map to the org
     */
    private void rebuildOwnerContentMapping(Owner owner, RefreshResult result) {
        Set<EntityState> states = Set.of(EntityState.CREATED, EntityState.UPDATED, EntityState.UNCHANGED);

        Map<String, String> entityIdMap = result.streamEntities(Content.class, states)
            .collect(Collectors.toMap(Content::getId, Content::getUuid));

        this.ownerContentCurator.rebuildOwnerContentMapping(owner, entityIdMap);
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
            List<Pool> pools = this.poolCurator
                .listByOwnerAndTypes(owner.getId(), PoolType.NORMAL, PoolType.DEVELOPMENT);
            this.mapExistingPools(pools);

            // Add in the org-mapped entities to ensure we catch everything for this org, as well
            // verifying there aren't any dangling references to out-of-org entities
            Collection<Product> ownerProducts = this.ownerProductCurator.getProductsByOwner(owner);
            this.mapExistingProducts(ownerProducts);

            Collection<Content> ownerContent = this.ownerContentCurator.getContentByOwner(owner);
            this.mapExistingContent(ownerContent);

            // Have our node factory build the node trees
            nodeFactory.buildNodes(owner);

            // Process our nodes, starting at the roots, letting the processors build up any persistence
            // state necessary to finalize everything
            RefreshResult result = nodeProcessor.processNodes();

            // If we had any dirty mappings, rebuild the org's mappings to ensure no leftover shenanigans
            if (this.productMapper.isDirty() ||
                !this.productMapper.containsOnlyExistingEntities(ownerProducts)) {

                log.warn("Found one or more dirty product mappings for org {}; remapping products", owner);
                this.rebuildOwnerProductMapping(owner, result);
            }

            if (this.contentMapper.isDirty() ||
                !this.contentMapper.containsOnlyExistingEntities(ownerContent)) {

                log.warn("Found one or more dirty content mappings for org {}; remapping content", owner);
                this.rebuildOwnerContentMapping(owner, result);
            }

            return result;
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
