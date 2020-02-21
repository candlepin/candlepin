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
package org.candlepin.controller.refresher.visitors;

import org.candlepin.controller.ProductManager;
import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * A NodeVisitor implementation that supports product entity nodes
 */
public class ProductNodeVisitor implements NodeVisitor<Product, ProductInfo> {
    private final ProductCurator productCurator;
    private final OwnerProductCurator ownerProductCurator;

    private Set<OwnerProduct> ownerProductEntities;
    private Map<Owner, Map<String, String>> ownerProductUuidMap;

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
     * @throws IllegalArgumentException
     *  if any of the provided curators are null
     */
    public ProductNodeVisitor(ProductCurator productCurator, OwnerProductCurator ownerProductCurator) {
        if (productCurator == null) {
            throw new IllegalArgumentException("productCurator is null");
        }

        if (ownerProductCurator == null) {
            throw new IllegalArgumentException("ownerProductCurator is null");
        }

        this.productCurator = productCurator;
        this.ownerProductCurator = ownerProductCurator;

        this.ownerProductEntities = new HashSet<>();
        this.ownerProductUuidMap = new HashMap<>();
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
    public void processNode(NodeProcessor processor, NodeMapper mapper,
        EntityNode<Product, ProductInfo> node) {

        boolean childrenUpdated = false;
        boolean nodeChanged = false;

        if (node.visited()) {
            return;
        }

        // Check if we need to make reference updates on this entity.
        for (EntityNode child : (Collection<EntityNode>) node.getChildrenNodes()) {
            if (child.changed()) {
                childrenUpdated = true;
                break;
            }
        }

        Product existingEntity = node.getExistingEntity();
        ProductInfo importedEntity = node.getImportedEntity();

        if (existingEntity != null) {
            if (importedEntity != null) {
                nodeChanged = ProductManager.isChangedBy(existingEntity, importedEntity);
            }

            if (nodeChanged || childrenUpdated) {
                Product mergedEntity = this.createEntity(mapper, node);
                node.setMergedEntity(mergedEntity);

                // Store the mapping to be updated later
                Map<String, String> productUuidMap = this.ownerProductUuidMap.get(node.getOwner());
                if (productUuidMap == null) {
                    productUuidMap = new HashMap<>();
                    this.ownerProductUuidMap.put(node.getOwner(), productUuidMap);
                }
                productUuidMap.put(existingEntity.getUuid(), mergedEntity.getUuid());

                node.markChanged();
            }
        }
        else if (importedEntity != null) {
            // Node is new
            Product mergedEntity = this.createEntity(mapper, node);
            node.setMergedEntity(mergedEntity);

            // Create a new owner-product mapping for this entity. This will get persisted
            // later during completion
            this.ownerProductEntities.add(new OwnerProduct(node.getOwner(), mergedEntity));

            node.markChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void complete() {
        this.ownerProductCurator.saveAll(this.ownerProductEntities, false, false);
        this.ownerProductCurator.flush();

        for (Map.Entry<Owner, Map<String, String>> entry : this.ownerProductUuidMap.entrySet()) {
            this.ownerProductCurator.updateOwnerProductReferences(entry.getKey(), entry.getValue());
        }

        this.ownerProductUuidMap.clear();
        this.ownerProductEntities.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void compileResults(RefreshResult result, EntityNode<Product, ProductInfo> node) {
        if (result == null) {
            throw new IllegalArgumentException("result is null");
        }

        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }

        if (!node.visited()) {
            String errmsg = String.format("Node has not yet been visited: %s [id: %s]",
                node.getEntityClass(), node.getEntityId());

            throw new IllegalStateException(errmsg);
        }

        if (node.getMergedEntity() != null) {
            if (node.isEntityCreation()) {
                result.addCreatedProduct(node.getMergedEntity());
            }
            else {
                result.addUpdatedProduct(node.getMergedEntity());
            }
        }
        else if (node.getExistingEntity() != null) {
            result.addSkippedProduct(node.getExistingEntity());
        }
    }

    /**
     * Creates a new entity for the given node, potentially remapping to an existing node
     */
    private Product createEntity(NodeMapper mapper, EntityNode<Product, ProductInfo> node) {
        Product existingEntity = node.getExistingEntity();
        ProductInfo importedEntity = node.getImportedEntity();
        ProductInfo sourceEntity = importedEntity != null ? importedEntity : existingEntity;

        if (sourceEntity == null) {
            throw new IllegalArgumentException("No source entity provided"); // Sanity check
        }

        Product updatedEntity = existingEntity != null ?
            (Product) existingEntity.clone() :
            new Product().setLocked(true);

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
                                binfo.getType()
                            ));
                        }
                    }

                    updatedEntity.setBranding(branding);
                }
            }
        }

        // TODO:
        // Stop using the collections on the source entity and use the children on the node
        // directly.

        // Perform child resolution
        // Update products
        Collection<? extends ProductInfo> providedProducts = sourceEntity.getProvidedProducts();

        if (providedProducts != null) {
            updatedEntity.setProvidedProducts(null);

            for (ProductInfo product : providedProducts) {
                EntityNode<Product, ProductInfo> childNode = mapper.<Product, ProductInfo>
                    getNode(Product.class, product.getId());

                if (childNode == null) {
                    String errmsg = String.format("Product references a product which does not exist: %s",
                        product.getId());

                    throw new IllegalStateException(errmsg);
                }

                if (!childNode.visited()) {
                    String errmsg = String.format("Child node accessed before it has been processed: " +
                        "%s => %s", sourceEntity, product);

                    throw new IllegalStateException(errmsg);
                }

                updatedEntity.addProvidedProduct((Product) (childNode.changed() ?
                    childNode.getMergedEntity() :
                    childNode.getExistingEntity()));
            }
        }

        // Update content
        Collection<? extends ProductContentInfo> productContent = sourceEntity.getProductContent();

        if (productContent != null) {
            updatedEntity.setProductContent(null);

            for (ProductContentInfo pc : productContent) {
                ContentInfo content = pc.getContent();
                EntityNode<Content, ContentInfo> childNode = mapper.<Content, ContentInfo>
                    getNode(Content.class, content.getId());

                if (childNode == null) {
                    String errmsg = String.format("Product references a content which does not exist: %s",
                        content.getId());

                    throw new IllegalStateException(errmsg);
                }

                if (!childNode.visited()) {
                    String errmsg = String.format("Child node accessed before it has been processed: " +
                        "%s => %s", sourceEntity, content);

                    throw new IllegalStateException(errmsg);
                }

                updatedEntity.addContent((Content) (childNode.changed() ?
                    childNode.getMergedEntity() :
                    childNode.getExistingEntity()),
                    pc.isEnabled());
            }
        }

        // Do version resolution
        int version = updatedEntity.getEntityVersion();
        Set<Product> candidates = node.getCandidateEntities();

        if (candidates != null) {
            for (Product candidate : candidates) {
                if (candidate.getEntityVersion(true) == version && updatedEntity.equals(candidate)) {
                    // We've a pre-existing version; use it rather than our updatedEntity
                    return candidate;
                }
            }
        }

        // No matching versions. Persist and return our updated entity.
        return this.productCurator.saveOrUpdate(updatedEntity);
    }

}

