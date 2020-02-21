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
package org.candlepin.controller.refresher.mappers;

import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.service.model.ServiceAdapterModel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;



/**
 * The NodeMapper is responsible for mapping entity nodes by entity class and ID.
 */
public class NodeMapper {

    Map<Class, Map<String, EntityNode>> nodeMap;

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

        Map<String, EntityNode> idMap = this.nodeMap.get(cls);
        return (EntityNode<E, I>) (idMap != null ? idMap.get(id) : null);
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
    public boolean addNode(EntityNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node is null");
        }

        // Should we bother verifying that the entity class and entity ID are not null? Probably a
        // bit heavy-handed.
        Map<String, EntityNode> idMap = this.nodeMap.get(node.getEntityClass());

        if (idMap == null) {
            idMap = new HashMap<>();
            this.nodeMap.put(node.getEntityClass(), idMap);
        }

        return idMap.put(node.getEntityId(), node) != node;
    }

    /**
     * Retrieves an iterator that steps through all known entity nodes. This method never returns
     * null.
     *
     * @return
     *  an iterator to step through all known entity nodes
     */
    public Iterator<EntityNode> getNodeIterator() {
        return this.nodeMap.values()
            .stream()
            .flatMap(map -> map.values().stream())
            .iterator();
    }

    /**
     * Retreives an iterator that steps through all known root entity nodes, where a root node is
     * defined as any entity node that has no parent nodes. This method never returns null.
     *
     * @return
     *  an iterator to step through all known root entity nodes
     */
    public Iterator<EntityNode> getRootIterator() {
        return this.nodeMap.values()
            .stream()
            .flatMap(map -> map.values().stream())
            .filter(node -> node.isRootNode())
            .iterator();
    }

}
