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

import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.RefreshResult.EntityState;
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;



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
    private static Logger log = LoggerFactory.getLogger(NodeProcessor.class);

    private NodeMapper mapper;
    private Map<Class, NodeVisitor<?, ?>> visitors;

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
    public NodeProcessor addVisitor(NodeVisitor<?, ?> visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor is null");
        }

        this.visitors.put(visitor.getEntityClass(), visitor);
        return this;
    }

    /**
     * Fetches the node visitor registered to handle a specific node. If such a visitor does not
     * exist, this method throws an exception.
     *
     * @param node
     *  the node for which to fetch a visitor
     *
     * @throws IllegalStateException
     *  if no visitor has been registered to handle the provided node
     *
     * @return
     *  the node visitor to handle the provided node
     */
    private NodeVisitor getVisitor(EntityNode<?, ?> node) {
        NodeVisitor visitor = this.visitors.get(node.getEntityClass());
        if (visitor == null) {
            throw new IllegalStateException("No visitor configured for nodes of type: " +
                node.getEntityClass());
        }

        return visitor;
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
     *
     * @return
     *  a RefreshResult instance containing the entities affected by the refresh operation
     */
    public RefreshResult processNodes() {
        if (this.mapper == null) {
            throw new IllegalStateException("node mapper has not been set");
        }

        Set<EntityNode<?, ?>> visited = new HashSet<>();

        // Process our root nodes
        visited.clear();
        this.mapper.getRootNodeStream()
            .forEach(elem -> this.processNodeImpl(visited, elem));

        // Apply changes
        visited.clear();
        this.mapper.getRootNodeStream()
            .forEach(elem -> this.applyChangesImpl(visited, elem));

        // Compile and return the results
        return this.compileResults();
    }

    /**
     * Internal implementation that avoids repeating unnecessary input and state validation
     */
    private void processNodeImpl(Set<EntityNode<?, ?>> visited, EntityNode<?, ?> node) {
        if (node != null && !visited.contains(node)) {
            // Children must be processed first to ensure child resolution is performed properly
            node.getChildrenNodes()
                .forEach(elem -> this.processNodeImpl(visited, elem));

            log.trace("Processing node: {}", node);
            this.getVisitor(node)
                .processNode(node);

            // Add the node to our list of visited nodes so we don't process it again
            visited.add(node);
        }
    }

    /**
     * Internal implementation that avoids repeating unnecessary input and state validation
     */
    private void applyChangesImpl(Set<EntityNode<?, ?>> visited, EntityNode<?, ?> node) {
        if (node != null && !visited.contains(node)) {
            // Children must be processed first to ensure child resolution is performed properly
            node.getChildrenNodes()
                .forEach(elem -> this.applyChangesImpl(visited, elem));

            log.trace("Applying changes to node: {}", node);
            this.getVisitor(node)
                .applyChanges(node);

            visited.add(node);
        }
    }

    /**
     * Fetches and compiles the result of the nodes mapped by the backing node mapper. If the
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
    private RefreshResult compileResults() {
        RefreshResult result = new RefreshResult();

        Iterator<EntityNode<?, ?>> nodeIterator = this.mapper.getNodeStream()
            .filter(Objects::nonNull)
            .iterator();

        while (nodeIterator.hasNext()) {
            EntityNode node = nodeIterator.next();
            NodeState nodeState = node.getNodeState();

            if (nodeState == null) {
                String errmsg = String.format("node mapper contains an unprocessed node: %s [id: %s]",
                    node.getEntityClass(), node.getEntityId());

                throw new IllegalStateException(errmsg);
            }

            switch (nodeState) {
                case CREATED:
                    result.addEntity(node.getEntityClass(), node.getExistingEntity(), EntityState.CREATED);
                    break;

                case UPDATED:
                case CHILDREN_UPDATED:
                    result.addEntity(node.getEntityClass(), node.getExistingEntity(), EntityState.UPDATED);
                    break;

                case UNCHANGED:
                    result.addEntity(node.getEntityClass(), node.getExistingEntity(), EntityState.UNCHANGED);
                    break;

                case DELETED:
                    result.addEntity(node.getEntityClass(), node.getExistingEntity(), EntityState.DELETED);
                    break;

                case SKIPPED:
                    // Do nothing with this entity
                    break;

                default:
                    throw new IllegalStateException("Unexpected node state: " + nodeState);
            }
        }

        return result;
    }

}
