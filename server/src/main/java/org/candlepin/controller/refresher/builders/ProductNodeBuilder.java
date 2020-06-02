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
package org.candlepin.controller.refresher.builders;

import org.candlepin.controller.refresher.mappers.EntityMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.ProductNode;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;

import java.util.Collection;



/**
 * The ProductNodeBuilder is a NodeBuilder implementation responsible for building product nodes.
 */
public class ProductNodeBuilder implements NodeBuilder<Product, ProductInfo> {

    /**
     * Creates a new ProductNodeBuilder
     */
    public ProductNodeBuilder() {
        // Intentionally left empty
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
    public EntityNode<Product, ProductInfo> buildNode(NodeFactory factory,
        EntityMapper<Product, ProductInfo> mapper, Owner owner, String id) {

        if (!mapper.hasEntity(id)) {
            throw new IllegalStateException("Cannot find an entity with the specified ID: " + id);
        }

        Product existingEntity = mapper.getExistingEntity(id);
        ProductInfo importedEntity = mapper.getImportedEntity(id);

        EntityNode<Product, ProductInfo> node = new ProductNode(owner, id)
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity)
            .setCandidateEntities(mapper.getCandidateEntities(id));

        // Figure out our source entity from which we'll derive children nodes
        ProductInfo sourceEntity = importedEntity != null ? importedEntity : existingEntity;

        // Add provided products
        Collection<? extends ProductInfo> providedProducts = sourceEntity.getProvidedProducts();
        if (providedProducts != null) {
            for (ProductInfo provided : providedProducts) {
                EntityNode<Product, ProductInfo> child = factory.
                    <Product, ProductInfo>buildNode(owner, Product.class, provided.getId());

                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        // Add content nodes
        Collection<? extends ProductContentInfo> productContent = sourceEntity.getProductContent();

        // Product content processing is a bit... weird. We don't care about the join object for
        // our purposes here. It will be properly updated accordingly when we apply updates later.
        // However, we do want to track the content referencing for the tree construction.

        // TODO: The above paragraph is kinda wrong. We should have a ProductContent joining node
        // so we can properly process node trees without needing to jump back to the mapper to
        // perform lookups.

        if (productContent != null) {
            for (ProductContentInfo pc : productContent) {
                if (pc == null) {
                    continue;
                }

                ContentInfo content = pc.getContent();

                if (content == null) {
                    // This is so very bad. Fail out immediately.
                    throw new IllegalStateException("Product content references a null or invalid content");
                }

                EntityNode<Content, ContentInfo> child = factory.
                    <Content, ContentInfo>buildNode(owner, Content.class, content.getId());

                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        return node;
    }

}
