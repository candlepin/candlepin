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

import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ServiceAdapterModel;



/**
 * The NodeBuilder interface defines the API for building nodes for explicit types
 *
 * @param <E>
 *  The type of the existing entity to be used by nodes built by this builder
 *
 * @param <I>
 *  The type of the imported entity to be used by nodes built by this builder
 */
public interface NodeBuilder<E extends AbstractHibernateObject, I extends ServiceAdapterModel> {

    /**
     * Fetches the class of the database model entity used by the nodes created by this builder.
     * The value returned by this method should match the entity class of nodes to ensure proper
     * mapping and creation.
     *
     * @return
     *  the entity class of nodes created by this builder
     */
    Class<E> getEntityClass();

    /**
     * Builds a new entity node owned by the given owner, using the specified entity ID. The
     * provided node factory should be used when performing construction of children nodes to ensure
     * proper mapping and avoiding code duplication.
     *
     * @param factory
     *  the node factory to use for performing construction of children nodes
     *
     * @param owner
     *  the organization that will own the entity represented by the new entity node
     *
     * @param id
     *  the ID of the entity represented by the new entity node
     *
     * @throws IllegalStateException
     *  if an entity node representing the given ID cannot be created
     *
     * @return
     *  a new EntityNode instance representing the entity with the provided ID
     */
    EntityNode<E, I> buildNode(NodeFactory factory, Owner owner, String id);

}
