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

import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.service.model.ServiceAdapterModel;



/**
 * The NodeVisitor interface defines the API for a vistor responsible for processing configured
 * entity nodes of a given type.
 *
 * @param <E>
 *  The type of the database entity contained by the entity nodes this visitor will process
 *
 * @param <I>
 *  The type of the imported entity contained by the entity nodes this visitor will process
 */
public interface NodeVisitor<E extends AbstractHibernateObject, I extends ServiceAdapterModel> {

    /**
     * Fetches the entity class of the database entity this visitor will support. The value
     * returned must match the class returned by the types of entity nodes this visitor is able to
     * process for proper functionality and mapping.
     *
     * @return
     *  the entity class of the database entity supported by this visitor
     */
    Class<E> getEntityClass();

    /**
     * Processes (visits) a the specified node. The node processor and mapper provided can be used
     * for performing processing and lookup of children nodes.
     *
     * @param node
     *  the EntityNode instance to process
     */
    void processNode(EntityNode<E, I> node);

    /**
     * Applies changes that were calculated and queued in the previous processing and pruning steps
     * to the given node. If no changes were prepared for the provided node, this method should
     * silently return.
     *
     * @param node
     *  the EntityNode instance for which to apply pending changes
     */
    void applyChanges(EntityNode<E, I> node);

}
