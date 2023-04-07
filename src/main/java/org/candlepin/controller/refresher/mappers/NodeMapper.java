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
package org.candlepin.controller.refresher.mappers;

import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.service.model.ServiceAdapterModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;



/**
 * The NodeMapper is responsible for mapping entity nodes by entity class and ID.
 */
public class NodeMapper {

    Map<Class, Map<String, EntityNode<?, ?>>> nodeMap;

    /**
     * Creates a new NodeMapper
     */
    public NodeMapper() {
        this.nodeMap = new HashMap<>();
    }

    /**
     * Fetches the entity node for the given entity class and ID. If no such entity node has been
     * mapped, this method returns null.
     *
     * @param cls
     *  the entity class of the node to fetch
     *
     * @param id
     *  the entity id of the node to fetch
     *
     * @return
     *  the entity node for the entity with the given entity class and ID, or null if a matching
     *  entity has not yet been mapped
     */
    public <E extends AbstractHibernateObject, I extends ServiceAdapterModel>
        EntityNode<E, I> getNode(Class<E> cls, String id) {

        return (EntityNode<E, I>) this.nodeMap.getOrDefault(cls, Collections.emptyMap())
            .get(id);
    }

    /**
     * Maps the given node with this mapper
     *
     * @param node
     *  the entity node to map
     *
     * @throws IllegalArgumentException
     *  if the given entity node is null
     *
     * @return
     *  true if the node is mapped successfully; false if the mapping already existed
     */
    public boolean addNode(EntityNode<?, ?> node) {
        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }

        // Should we bother verifying that the entity class and entity ID are not null? Probably a
        // bit heavy-handed.

        return this.nodeMap.computeIfAbsent(node.getEntityClass(), key -> new HashMap<>())
            .put(node.getEntityId(), node) != node;
    }

    /**
     * Retrieves a stream of all known entity nodes. This method never returns null.
     *
     * @return
     *  a stream of all known entity nodes
     */
    public Stream<EntityNode<?, ?>> getNodeStream() {
        return this.nodeMap.values()
            .stream()
            .flatMap(map -> map.values().stream());
    }

    /**
     * Retreives a stream of all known root entity nodes, where a root node is defined as any entity
     * node that has no parent nodes. This method never returns null.
     *
     * @return
     *  a stream of all known root entity nodes
     */
    public Stream<EntityNode<?, ?>> getRootNodeStream() {
        return this.getNodeStream()
            .filter(EntityNode::isRootNode);
    }

    /**
     * Retreives a stream of all known leaf entity nodes, where a leaf node is defined as any entity
     * node that has no children nodes. This method never returns null.
     *
     * @return
     *  a stream of all known leaf entity nodes
     */
    public Stream<EntityNode<?, ?>> getLeafNodeStream() {
        return this.getNodeStream()
            .filter(EntityNode::isLeafNode);
    }
}
