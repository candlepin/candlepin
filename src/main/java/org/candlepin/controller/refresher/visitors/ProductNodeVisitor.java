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
package org.candlepin.controller.refresher.visitors;

import org.candlepin.controller.ProductManager;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProduct;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * A NodeVisitor implementation that supports product entity nodes
 */
public class ProductNodeVisitor implements NodeVisitor<Product, ProductInfo> {
    private static final Logger log = LoggerFactory.getLogger(ProductNodeVisitor.class);

    private final ProductCurator productCurator;
    private final OwnerProductCurator ownerProductCurator;
    private final int orphanProductGracePeriod;

    // Various cross-stage cache collections
    private Set<OwnerProduct> ownerProductEntities;
    private Map<Owner, Map<String, String>> ownerProductUuidMap;
    private Map<Owner, Set<String>> deletedProductUuids;
    private Map<Owner, Set<Long>> ownerEntityVersions;
    private Map<Owner, Map<String, List<Product>>> ownerVersionedEntityMap;

    // Orphan date tracking and grace period state caching
    private Map<Owner, Map<String, Instant>> ownerOrphanedDateMap;
    private Map<Owner, Set<String>> ownerOrphanEntityIdPrecache;
    private Map<Owner, Set<String>> ownerOrphanedEntities;
    private Map<Owner, Set<String>> ownerUnorphanedEntities;

    /**
     * Creates a new ProductNodeVisitor that uses the provided curators for performing database
     * operations.
     *
     * @param productCurator
     *  the ProductCurator to use for product database operations
     *
     * @param ownerProductCurator
     *  the OwnerProductCurator to use for owner-product database operations
     *
     * @param orphanProductGracePeriod
     *  the number of days a product is allowed to be orphaned before it will be removed
     *
     * @throws IllegalArgumentException
     *  if any of the provided curators are null
     */
    public ProductNodeVisitor(ProductCurator productCurator, OwnerProductCurator ownerProductCurator,
        int orphanProductGracePeriod) {

        if (productCurator == null) {
            throw new IllegalArgumentException("productCurator is null");
        }

        if (ownerProductCurator == null) {
            throw new IllegalArgumentException("ownerProductCurator is null");
        }

        this.productCurator = productCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.orphanProductGracePeriod = orphanProductGracePeriod;

        this.ownerProductEntities = new HashSet<>();
        this.ownerProductUuidMap = new HashMap<>();
        this.deletedProductUuids = new HashMap<>();
        this.ownerEntityVersions = new HashMap<>();
        this.ownerVersionedEntityMap = new HashMap<>();

        this.ownerOrphanedDateMap = new HashMap<>();
        this.ownerOrphanEntityIdPrecache = new HashMap<>();
        this.ownerOrphanedEntities = new HashMap<>();
        this.ownerUnorphanedEntities = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Product> getEntityClass() {
        return Product.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processNode(EntityNode<Product, ProductInfo> node) {
        // If this node already has a state, we don't need to reprocess it (probably)
        if (node.getNodeState() != null) {
            return;
        }

        boolean nodeChanged = false;

        // Check if we need to make reference updates on this entity due to children updates
        boolean childrenUpdated = node.getChildrenNodes()
            .anyMatch(EntityNode::changed);

        Product existingEntity = node.getExistingEntity();
        ProductInfo importedEntity = node.getImportedEntity();

        // Default the node state to UNCHANGED and let our cases below overwrite this
        node.setNodeState(NodeState.UNCHANGED);

        if (existingEntity != null) {
            // Cache the ID of any existing entities for later bulk fetching orphan dates
            this.ownerOrphanEntityIdPrecache.computeIfAbsent(node.getOwner(), key -> new HashSet<>())
                .add(node.getEntityId());

            if (importedEntity != null) {
                nodeChanged = ProductManager.isChangedBy(existingEntity, importedEntity);
            }

            if (nodeChanged || childrenUpdated) {
                Product mergedEntity = this.createEntity(node);
                node.setMergedEntity(mergedEntity);

                node.setNodeState(NodeState.UPDATED);
            }
        }
        else if (importedEntity != null) {
            // Node is new
            Product mergedEntity = this.createEntity(node);
            node.setMergedEntity(mergedEntity);

            node.setNodeState(NodeState.CREATED);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pruneNode(EntityNode<Product, ProductInfo> node) {
        Product existingEntity = node.getExistingEntity();

        if (existingEntity != null && this.clearedForDeletion(node)) {
            this.deletedProductUuids.computeIfAbsent(node.getOwner(), key -> new HashSet<>())
                .add(existingEntity.getUuid());

            node.setNodeState(NodeState.DELETED);
        }
    }

    /**
     * Checks that the entity is no longer present upstream, and is not part of any active subtrees.
     *
     * @param node
     *  the entity node to check
     *
     * @return
     *  true if the node is cleared for deletion; false otherwise
     */
    private boolean clearedForDeletion(EntityNode<Product, ProductInfo> node) {
        // We don't delete custom entities, ever.
        boolean cleared = node.getExistingEntity().isLocked();

        // If the node is still defined upstream and is part of this refresh, we should keep it
        // around locally
        cleared = cleared && node.getImportedEntity() == null;

        // If the node is referenced by one or more parent nodes that are not being deleted
        // themselves, we should keep it.
        cleared = cleared && !node.getParentNodes()
            .anyMatch(elem -> elem.getNodeState() != NodeState.DELETED);

        // If the grace period is set to infinity (negative values), we are never clear for deletion
        cleared = cleared && this.orphanProductGracePeriod >= 0;

        if (cleared) {
            // At this point we're *almost* clear for deletion; just need to wait out the grace period:
            // - If there is not a date set, it has not been previously cleared for deletion. Set the
            //   date, update the owner-product ref to store the new date and return false
            // - if there is a date set, check if we're beyond the grace period (in days). If the date
            //   has been exceeded, flag the product for deletion
            // - if the grace period is zero, we're setup to delete without delay; skip any further
            //   checks

            if (this.orphanProductGracePeriod > 0) {
                Instant orphanedDate = this.getOwnerProductOrphanedDate(node);

                if (orphanedDate == null) {
                    this.ownerOrphanedEntities.computeIfAbsent(node.getOwner(), key -> new HashSet<>())
                        .add(node.getEntityId());

                    cleared = false;
                }
                else {
                    Instant cutoff = Instant.now()
                        .truncatedTo(ChronoUnit.DAYS)
                        .minus(this.orphanProductGracePeriod, ChronoUnit.DAYS);

                    cleared = orphanedDate.truncatedTo(ChronoUnit.DAYS)
                        .isBefore(cutoff);
                }
            }
        }
        else {
            // Entity is not cleared for deletion. Remove its orphaned date if it's been set.
            if (this.getOwnerProductOrphanedDate(node) != null) {
                this.ownerUnorphanedEntities.computeIfAbsent(node.getOwner(), key -> new HashSet<>())
                    .add(node.getEntityId());
            }
        }

        return cleared;
    }

    /**
     * Fetches the orphaned date of the entity represented by the given node. If the node does not
     * represent an orphaned entity, this method returns null.
     *
     * @param node
     *  the entity node for which to fetch the orphaned entity date
     *
     * @return
     *  the date the entity represented by the given node was orphaned, or null if the node does not
     *  represent an orphaned entity
     */
    private Instant getOwnerProductOrphanedDate(EntityNode<Product, ProductInfo> node) {
        Map<String, Instant> orphanDateMap = this.ownerOrphanedDateMap
            .computeIfAbsent(node.getOwner(), key -> new HashMap<>());

        // Check if we have some precached IDs to convert...
        Set<String> entityIdPrecache = this.ownerOrphanEntityIdPrecache.remove(node.getOwner());
        if (entityIdPrecache != null) {
            Map<String, Instant> fetched = this.ownerProductCurator
                .getOwnerProductOrphanedDates(node.getOwner(), entityIdPrecache);

            orphanDateMap.putAll(fetched);
        }

        // If this node somehow missed the processing/precaching step, do an individual lookup and
        // cache the result
        if (!orphanDateMap.containsKey(node.getEntityId())) {
            OwnerProduct op = this.ownerProductCurator
                .getOwnerProduct(node.getOwner().getId(), node.getEntityId());

            if (op != null) {
                orphanDateMap.put(node.getEntityId(), op.getOrphanedDate());
            }
        }

        return orphanDateMap.get(node.getEntityId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyChanges(EntityNode<Product, ProductInfo> node) {
        // If we don't have a state, bad things have happened...
        if (node.getNodeState() == null) {
            throw new IllegalStateException("Attempting to apply changes to a node without a state: " + node);
        }

        // We only need to do work in the UPDATED or CREATED cases; all other states are no-ops here
        if (node.getNodeState() == NodeState.UPDATED) {
            node.setMergedEntity(this.resolveEntityVersion(node));

            // Store the mapping to be updated later
            Product existingEntity = node.getExistingEntity();
            Product mergedEntity = node.getMergedEntity();

            this.ownerProductUuidMap.computeIfAbsent(node.getOwner(), key -> new HashMap<>())
                .put(existingEntity.getUuid(), mergedEntity.getUuid());
        }
        else if (node.getNodeState() == NodeState.CREATED) {
            node.setMergedEntity(this.resolveEntityVersion(node));

            // Create a new owner-product mapping for this entity. This will get persisted later
            // during the completion step
            this.ownerProductEntities.add(new OwnerProduct(node.getOwner(), node.getMergedEntity()));
        }
    }

    /**
     * Performs version resolution for the specified entity node. If a local version of the entity
     * already exists, this method returns the existing entity; otherwise, the new entity stored in
     * the provided node is persisted and returned.
     *
     * @param node
     *  the entity node on which to perform version resolution
     *
     * @return
     *  the existing version of the entity if such a version exists, or the persisted version of
     *  the newly created entity
     */
    private Product resolveEntityVersion(EntityNode<Product, ProductInfo> node) {
        Owner owner = node.getOwner();
        Product entity = node.getMergedEntity();
        long entityVersion = entity.getEntityVersion();

        Map<String, List<Product>> entityMap = this.ownerVersionedEntityMap.computeIfAbsent(owner, key -> {
            Set<Long> versions = this.ownerEntityVersions.remove(key);

            return versions != null ?
                this.ownerProductCurator.getProductsByVersions(versions) :
                Collections.emptyMap();
        });

        for (Product candidate : entityMap.getOrDefault(entity.getId(), Collections.emptyList())) {
            if (entityVersion == candidate.getEntityVersion()) {
                if (entity.equals(candidate)) {
                    // We found a match! Map to the candidate entity
                    return candidate;
                }

                // If we have a version collision, and the entity IDs are the same, there's likely
                // some shenanigans going on. Rather than halting all behavior, let's just clear
                // the old entity's version so we start mapping to the new one.
                // If we have a collision where the products are actually different, we won't
                // fail at this point (but we *will* fail), and we won't be able to detect it.

                // Impl note:
                // We've already implicitly checked the ID above by how we're pulling candidate
                // entities from the map
                log.error("Entity version collision detected; attempting resolution..." +
                    "\nConflicting entities:\n{}\n{}", entity, candidate);

                this.ownerProductCurator.clearProductEntityVersion(candidate);
                break;
            }
        }

        // If the entity has no existing version, we need to re-resolve children references (as one
        // or more children may have been resolved to an existing entity in the interim), and then
        // persist the new entity. However, we need to avoid flushing here, as flushing after each
        // entity would kneecap performance. We'll, instead, flush everything later in the
        // "completion" step.
        this.resolveChildren(entity, node);
        return this.productCurator.create(entity, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void complete() {
        // Remove owner-specific product references for deleted product
        for (Map.Entry<Owner, Set<String>> entry : this.deletedProductUuids.entrySet()) {
            this.ownerProductCurator.removeOwnerProductReferences(entry.getKey(), entry.getValue());
        }

        // Clear orphaned dates on unorphaned products
        for (Map.Entry<Owner, Set<String>> entry : this.ownerUnorphanedEntities.entrySet()) {
            this.ownerProductCurator.updateOwnerProductOrphanedDates(entry.getKey(), entry.getValue(), null);
        }

        // Set orphaned dates on newly-orphaned products
        for (Map.Entry<Owner, Set<String>> entry : this.ownerOrphanedEntities.entrySet()) {
            this.ownerProductCurator.updateOwnerProductOrphanedDates(entry.getKey(), entry.getValue(),
                Instant.now());
        }

        // Save new owner-product entities
        this.ownerProductEntities.stream()
            .forEach(elem -> this.ownerProductCurator.create(elem, false));
        this.ownerProductCurator.flush();

        // Update owner product references
        for (Map.Entry<Owner, Map<String, String>> entry : this.ownerProductUuidMap.entrySet()) {
            this.ownerProductCurator.updateOwnerProductReferences(entry.getKey(), entry.getValue());
        }

        // Clear our various caches
        this.ownerProductUuidMap.clear();
        this.ownerProductEntities.clear();
        this.deletedProductUuids.clear();
        this.ownerEntityVersions.clear();
        this.ownerVersionedEntityMap.clear();

        this.ownerOrphanEntityIdPrecache.clear();
        this.ownerOrphanedDateMap.clear();
        this.ownerOrphanedEntities.clear();
        this.ownerUnorphanedEntities.clear();
    }

    private EntityNode<Product, ProductInfo> lookupProductNode(EntityNode<Product, ProductInfo> parentNode,
        String productId) {

        EntityNode<Product, ProductInfo> childNode = parentNode.getChildNode(Product.class, productId);

        if (childNode == null) {
            String errmsg = String.format("Product references a child product which does not exist: %s",
                productId);

            throw new IllegalStateException(errmsg);
        }

        if (childNode.getNodeState() == null) {
            String errmsg = String.format("Child node accessed before it has been processed: " +
                "%s => %s", parentNode, childNode);

            throw new IllegalStateException(errmsg);
        }

        return childNode;
    }

    private EntityNode<Content, ContentInfo> lookupContentNode(EntityNode<Product, ProductInfo> parentNode,
        String contentId) {

        EntityNode<Content, ContentInfo> childNode = parentNode.getChildNode(Content.class, contentId);

        if (childNode == null) {
            String errmsg = String.format("Product references a child content which does not exist: %s",
                contentId);

            throw new IllegalStateException(errmsg);
        }

        if (childNode.getNodeState() == null) {
            String errmsg = String.format("Child node accessed before it has been processed: " +
                "%s => %s", parentNode, childNode);

            throw new IllegalStateException(errmsg);
        }

        return childNode;
    }

    /**
     * Ensures children references on the provided entity are configured correctly according to the
     * structure defined by the provided entity node.
     *
     * @param entity
     *  the entity on which to perform children resolution
     *
     * @param node
     *  the entity node to use for resolving children entity references
     */
    private void resolveChildren(Product entity, EntityNode<Product, ProductInfo> node) {
        Product existingEntity = node.getExistingEntity();
        ProductInfo importedEntity = node.getImportedEntity();
        ProductInfo sourceEntity = importedEntity != null ? importedEntity : existingEntity;

        if (sourceEntity == null) {
            throw new IllegalArgumentException("No source entity provided"); // Sanity check
        }

        // Derived product
        ProductInfo derivedProduct = sourceEntity.getDerivedProduct();
        if (derivedProduct != null) {
            EntityNode<Product, ProductInfo> childNode = this.lookupProductNode(node, derivedProduct.getId());

            entity.setDerivedProduct((Product) (childNode.changed() ?
                childNode.getMergedEntity() :
                childNode.getExistingEntity()));
        }
        else {
            entity.setDerivedProduct(null);
        }

        // Provided products
        Collection<? extends ProductInfo> providedProducts = sourceEntity.getProvidedProducts();
        if (providedProducts != null) {
            entity.setProvidedProducts(null);

            for (ProductInfo product : providedProducts) {
                EntityNode<Product, ProductInfo> childNode = this.lookupProductNode(node, product.getId());

                entity.addProvidedProduct((Product) (childNode.changed() ?
                    childNode.getMergedEntity() :
                    childNode.getExistingEntity()));
            }
        }

        // Update content
        Collection<? extends ProductContentInfo> productContent = sourceEntity.getProductContent();
        if (productContent != null) {
            entity.setProductContent(null);

            for (ProductContentInfo pc : productContent) {
                ContentInfo content = pc.getContent();
                EntityNode<Content, ContentInfo> childNode = this.lookupContentNode(node, content.getId());

                entity.addContent((Content) (childNode.changed() ?
                    childNode.getMergedEntity() :
                    childNode.getExistingEntity()),
                    pc.isEnabled());
            }
        }
    }

    /**
     * Creates a new entity for the given node, potentially remapping to an existing node
     */
    private Product createEntity(EntityNode<Product, ProductInfo> node) {
        Product existingEntity = node.getExistingEntity();
        ProductInfo importedEntity = node.getImportedEntity();

        Product updatedEntity = existingEntity != null ?
            (Product) existingEntity.clone() :
            (new Product().setLocked(true));

        // Ensure the RH product ID is set properly
        updatedEntity.setId(node.getEntityId());

        // Clear the UUID so we don't attempt to inherit it
        updatedEntity.setUuid(null);

        if (importedEntity != null) {
            if (importedEntity.getName() != null) {
                updatedEntity.setName(importedEntity.getName());
            }

            if (importedEntity.getMultiplier() != null) {
                updatedEntity.setMultiplier(importedEntity.getMultiplier());
            }

            if (importedEntity.getAttributes() != null) {
                updatedEntity.setAttributes(importedEntity.getAttributes());
            }

            if (importedEntity.getDependentProductIds() != null) {
                updatedEntity.setDependentProductIds(importedEntity.getDependentProductIds());
            }

            // Impl note: Oddly enough, branding isn't important enough to keep around, so we just
            // recreate the collection every time.
            if (importedEntity.getBranding() != null) {
                if (importedEntity.getBranding().isEmpty()) {
                    updatedEntity.setBranding(Collections.emptySet());
                }
                else {
                    Set<Branding> branding = new HashSet<>();
                    for (BrandingInfo binfo : importedEntity.getBranding()) {
                        if (binfo != null) {
                            branding.add(new Branding(
                                updatedEntity,
                                binfo.getProductId(),
                                binfo.getName(),
                                binfo.getType()));
                        }
                    }

                    updatedEntity.setBranding(branding);
                }
            }
        }

        // Perform child resolution
        this.resolveChildren(updatedEntity, node);

        // Save entity version for later version resolution
        this.ownerEntityVersions.computeIfAbsent(node.getOwner(), key -> new HashSet<>())
            .add(updatedEntity.getEntityVersion());

        return updatedEntity;
    }

}
