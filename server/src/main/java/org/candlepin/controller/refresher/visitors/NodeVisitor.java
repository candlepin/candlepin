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

import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.mappers.NodeMapper;
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
     * @param processor
     *  A NodeProcessor instance to use for performing processing of children or nested entity
     *  nodes
     *
     * @param mapper
     *  A NodeMapper instance that can be used to perform a lookup of other entity nodes
     *
     * @param node
     *  the EntityNode instance to process
     *
     * @throws IllegalStateException
     *  if the node cannot be processed for any reason
     */
    void processNode(NodeProcessor processor, NodeMapper mapper, EntityNode<E, I> node);

    /**
     * Completes any processing operations that may be pending from one or more previous calls to
     * the <tt>processNode</tt> method. Repeated, sequential calls to this method should have no
     * further effect on the visitor or the previously processed nodes or data.
     */
    void complete();

    /**
     * Compiles the results of processing the given entity node and stores them in the provided
     * RefreshResult instance. If the provided node has not yet been processed or visited, this
     * method throws an exception.
     *
     * @param result
     *  the RefreshResult instance in which to store the results
     *
     * @param node
     *  the EntityNode instance for which to compile processing results
     */
    void compileResults(RefreshResult result, EntityNode<E, I> node);

}
