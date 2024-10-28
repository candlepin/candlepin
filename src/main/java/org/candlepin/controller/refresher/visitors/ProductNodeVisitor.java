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
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;



/**
 * A NodeVisitor implementation that supports product entity nodes
 */
public class ProductNodeVisitor implements NodeVisitor<Product, ProductInfo> {
    private static final Logger log = LoggerFactory.getLogger(ProductNodeVisitor.class);

    private final ProductCurator productCurator;

    /**
     * Creates a new ProductNodeVisitor that uses the provided curators for performing database
     * operations.
     *
     * @param productCurator
     *  the ProductCurator to use for product database operations
     *
     * @throws IllegalArgumentException
     *  if any of the provided curators are null
     */
    public ProductNodeVisitor(ProductCurator productCurator) {
        this.productCurator = Objects.requireNonNull(productCurator);
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
    private void resolveChildrenEntityRefs(Product entity, ProductInfo importedEntity,
        EntityNode<Product, ProductInfo> node) {

        // Derived product
        ProductInfo derivedProduct = importedEntity.getDerivedProduct();
        if (derivedProduct != null) {
            EntityNode<Product, ProductInfo> childNode = this.lookupProductNode(node, derivedProduct.getId());
            entity.setDerivedProduct((Product) childNode.getExistingEntity());
        }
        else {
            entity.setDerivedProduct(null);
        }

        // Provided products
        Collection<? extends ProductInfo> providedProducts = importedEntity.getProvidedProducts();
        if (providedProducts != null) {
            entity.setProvidedProducts(null);

            for (ProductInfo product : providedProducts) {
                EntityNode<Product, ProductInfo> childNode = this.lookupProductNode(node, product.getId());
                entity.addProvidedProduct((Product) childNode.getExistingEntity());
            }
        }

        // Update content
        Collection<? extends ProductContentInfo> productContent = importedEntity.getProductContent();
        if (productContent != null) {
            entity.setProductContent(null);

            for (ProductContentInfo pc : productContent) {
                ContentInfo content = pc.getContent();

                EntityNode<Content, ContentInfo> childNode = this.lookupContentNode(node, content.getId());
                entity.addContent((Content) childNode.getExistingEntity(), pc.isEnabled());
            }
        }
    }

    /**
     * Creates a new entity for the given node, potentially remapping to an existing node
     */
    private Product applyProductChanges(Product entity, ProductInfo importedEntity,
        EntityNode<Product, ProductInfo> node) {

        // Ensure the RH product ID is set properly
        entity.setId(node.getEntityId());

        if (importedEntity != null) {
            if (importedEntity.getName() != null) {
                entity.setName(importedEntity.getName());
            }

            if (importedEntity.getMultiplier() != null) {
                entity.setMultiplier(importedEntity.getMultiplier());
            }

            if (importedEntity.getAttributes() != null) {
                entity.setAttributes(importedEntity.getAttributes());
            }

            if (importedEntity.getDependentProductIds() != null) {
                entity.setDependentProductIds(importedEntity.getDependentProductIds());
            }

            // Impl note: Oddly enough, branding isn't important enough to keep around, so we just
            // recreate the collection every time.
            if (importedEntity.getBranding() != null) {
                Function<BrandingInfo, Branding> converter = (binfo) -> {
                    return new Branding(entity, binfo.getProductId(), binfo.getName(), binfo.getType());
                };

                List<Branding> branding = importedEntity.getBranding()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(converter)
                    .toList();

                entity.setBranding(branding);
            }

            // Perform resolution of children entity references
            this.resolveChildrenEntityRefs(entity, importedEntity, node);
        }

        return entity;
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

        Product existingEntity = node.getExistingEntity();
        ProductInfo importedEntity = node.getImportedEntity();

        // Default the node state to UNCHANGED and let our cases below overwrite this
        node.setNodeState(NodeState.UNCHANGED);

        if (existingEntity != null) {
            if (importedEntity != null && ProductManager.isChangedBy(existingEntity, importedEntity)) {
                node.setNodeState(NodeState.UPDATED);
            }
            else {
                // If this node is not changed directly, it may still have an indirect change due
                // to one of its children changing
                boolean childrenUpdated = node.getChildrenNodes()
                    .anyMatch(EntityNode::changed);

                if (childrenUpdated) {
                    node.setNodeState(NodeState.CHILDREN_UPDATED);
                }
            }
        }
        else if (importedEntity != null) {
            // Node is new
            node.setNodeState(NodeState.CREATED);
        }
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
        Product existingEntity = node.getExistingEntity();
        ProductInfo importedEntity = node.getImportedEntity();
        Product updatedEntity;

        switch (node.getNodeState()) {
            case CREATED:
                updatedEntity = this.applyProductChanges(new Product(), importedEntity, node);
                updatedEntity = this.productCurator.create(updatedEntity, false);

                node.setExistingEntity(updatedEntity);
                break;

            case UPDATED:
                updatedEntity = this.applyProductChanges(existingEntity, importedEntity, node);
                updatedEntity = this.productCurator.merge(updatedEntity);

                node.setExistingEntity(updatedEntity);
                break;

            case DELETED:
                this.productCurator.delete(existingEntity);
                break;

            default:
                // intentionally left empty
        }
    }

}
