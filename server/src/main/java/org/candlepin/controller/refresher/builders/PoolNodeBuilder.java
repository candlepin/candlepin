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
import org.candlepin.controller.refresher.nodes.PoolNode;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;



/**
 * The PoolNodeBuilder is a NodeBuilder implementation responsible for building product nodes.
 */
public class PoolNodeBuilder implements NodeBuilder<Pool, SubscriptionInfo> {

    /**
     * Creates a new PoolNodeBuilder
     */
    public PoolNodeBuilder() {
        // Intentionally left empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Pool> getEntityClass() {
        return Pool.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<Pool, SubscriptionInfo> buildNode(NodeFactory factory,
        EntityMapper<Pool, SubscriptionInfo> mapper, Owner owner, String id) {

        if (!mapper.hasEntity(id)) {
            throw new IllegalStateException("Cannot find an entity with the specified ID: " + id);
        }

        Pool existingEntity = mapper.getExistingEntity(id);
        SubscriptionInfo importedEntity = mapper.getImportedEntity(id);

        EntityNode<Pool, SubscriptionInfo> node = new PoolNode(owner, id)
            .setExistingEntity(existingEntity)
            .setImportedEntity(importedEntity);

        // Figure out our source entity from which we'll derive children nodes
        SubscriptionInfo sourceEntity = importedEntity != null ? importedEntity : existingEntity;

        // Add children: product and provided product
        this.addChildProductNode(factory, node, owner, sourceEntity.getProduct());
        this.addChildProductNode(factory, node, owner, sourceEntity.getDerivedProduct());

        return node;
    }

    /**
     * Adds a child product node to the parent node. If the product is null, no product node will be
     * created or added.
     *
     * @param factory
     *  the node factory to use to create the child node
     *
     * @param parent
     *  the parent node to receive the created child node
     *
     * @param product
     *  the product for which to build and add the child node
     */
    private void addChildProductNode(NodeFactory factory, EntityNode parent, Owner owner,
        ProductInfo product) {

        if (product != null) {
            EntityNode child = factory.buildNode(owner, Product.class, product.getId());

            parent.addChildNode(child);
            child.addParentNode(parent);
        }
    }

}
