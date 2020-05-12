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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;



/**
 * The NodeProcessor is responsible for collecting visitors and coordinating the processing of
 * complete collections of node trees.
 * <p></p>
 * Before a node processor can be used, it must first be provided a node mapper to act as a cache,
 * and one or more visitors to actually process each type of node. The number of visitors required
 * depends on the makeup of the nodes trees that will be processed. If a tree has nodes with
 * entity types of Product and Content, then the processor must have a visitor for both of these
 * types of nodes. If a node is requested to be processed without a matching visitor, an exception
 * will be thrown.
 */
public class NodeProcessor {

    private NodeMapper mapper;
    private Map<Class, NodeVisitor> visitors;

    /**
     * Creates a new NodeProcessor, without any mappers or visitors.
     */
    public NodeProcessor() {
        this.visitors = new HashMap<>();
    }

    /**
     * Sets the mapper to use for performing node resolution while processing nodes.
     *
     * @param mapper
     *  the mapper to use for node resolution
     *
     * @throws IllegalArgumentException
     *  if the provided mapper is null
     *
     * @return
     *  a reference to this node processor
     */
    public NodeProcessor setNodeMapper(NodeMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper is null");
        }

        this.mapper = mapper;
        return this;
    }

    /**
     * Adds a visitor to this processor. The visitor will be used to process nodes for the entity
     * class returned by the visitor's <tt>getEntityClass</tt> method.
     *
     * @param visitor
     *  the visitor to add to this processor
     *
     * @throws IllegalArgumentException
     *  if the provided visitor is null
     *
     * @return
     *  a reference to this node processor
     */
    public NodeProcessor addVisitor(NodeVisitor visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor is null");
        }

        this.visitors.put(visitor.getEntityClass(), visitor);
        return this;
    }

    /**
     * Processes all nodes currently mapped by the node mapper backing this node processor. If
     * a node mapper has not yet been set, or a visitor has not been provided for one or more of the
     * nodes (or their children), this method throws an exception. If the backing node mapper does
     * not provide any root nodes, this method will silently return.
     *
     * @throws IllegalStateException
     *  if the node mapper has not yet been set, or a visitor has not been provided for one of more
     *  of the nodes mapped by the backing node mapper.
     */
    public void processNodes() {
        if (this.mapper == null) {
            throw new IllegalStateException("node mapper has not been set");
        }

        for (Iterator<EntityNode> rootIterator = this.mapper.getRootIterator(); rootIterator.hasNext();) {
            this.processNodeImpl(rootIterator.next());
        }
    }

    /**
     * Internal implementation that avoids repeating unnecessary input and state validation
     */
    private void processNodeImpl(EntityNode node) {
        if (node != null && !node.visited()) {
            // Process children nodes first (depth-first), so we can update references and avoid
            // rework
            for (EntityNode childNode : (Collection<EntityNode>) node.getChildrenNodes()) {
                this.processNodeImpl(childNode);
            }

            NodeVisitor visitor = this.visitors.get(node.getEntityClass());
            if (visitor == null) {
                throw new IllegalStateException("No visitor configured for nodes of type: " +
                    node.getEntityClass());
            }

            // TODO: Stop passing the processor and mapper in. If the visitors are implemented
            // properly, they shouldn't need to do node lookups, as the nodes should already be
            // present as children nodes on the node we're providing
            visitor.processNode(this, this.mapper, node);

            // If we got here successfully, mark the node as visited
            node.markVisited();
        }
    }

    /**
     * Compiles and fetches the result of the nodes mapped by the backing node mapper. If the
     * mapper contains any nodes which have not yet been processed, this method throws an
     * exception.
     * <p></p>
     * It should be noted that calls to this method are idempotent for a given collective state of
     * the node mapper and the nodes it's currently maintaining. While visitors may have cached
     * state or pending operations which are flushed or completed upon result compilation, repeated
     * calls to this method should produce the same result so long as the node mapper and its nodes
     * remain unchanged.
     *
     * @return
     *  a RefreshResult instance containing the result of the overall refresh operation performed
     *  by the processing of the nodes contained in the backing node mapper
     */
    public RefreshResult compileResults() {
        if (this.mapper == null) {
            throw new IllegalStateException("node mapper has not been set");
        }

        RefreshResult result = new RefreshResult();

        // Have our visitors complete any pending operations
        for (NodeVisitor visitor : this.visitors.values()) {
            visitor.complete();
        }

        // Compile the results
        for (Iterator<EntityNode> nodeIterator = this.mapper.getNodeIterator(); nodeIterator.hasNext();) {
            EntityNode node = nodeIterator.next();

            if (node != null) {
                if (!node.visited()) {
                    String errmsg = "Cannot compile results before all nodes have been processed";
                    throw new IllegalStateException(errmsg);
                }

                NodeVisitor visitor = this.visitors.get(node.getEntityClass());

                if (visitor == null) {
                    throw new IllegalStateException("No visitor configured for nodes of type: " +
                        node.getEntityClass());
                }

                visitor.compileResults(result, node);
            }
        }

        return result;
    }

}
