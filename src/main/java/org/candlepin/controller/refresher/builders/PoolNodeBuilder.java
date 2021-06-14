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
 * The PoolNodeBuilder is a NodeBuilder implementation responsible for building pool nodes.
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

        // Add product node
        ProductInfo product = sourceEntity.getProduct();
        if (product != null) {
            EntityNode child = factory.buildNode(owner, Product.class, product.getId());
            node.addChildNode(child);
        }

        return node;
    }

}
