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
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ServiceAdapterModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;



/**
 * The NodeFactory is responsible for orchestrating the complete creation and mapping of a node and
 * any children nodes it may create.
 * <p></p>
 * The tasks that make up the creation and mapping of a node may be performed by other objects, but
 * those objects must be provided before a node creation request is made.
 */
public class NodeFactory {

    private NodeMapper nodeMapper;
    private Map<Class, EntityMapper<?, ?>> mappers;
    private Map<Class, NodeBuilder<?, ?>> builders;

    /**
     * Creates a new NodeFactory without any mappers or builders
     */
    public NodeFactory() {
        this.mappers = new HashMap<>();
        this.builders = new HashMap<>();
    }

    /**
     * Adds a builder to this factory. The builder will be used to create nodes for the class
     * returned by the builder's <tt>getEntityClass</tt> method.
     *
     * @param builder
     *  the builder to add to this factory
     *
     * @throws IllegalArgumentException
     *  if the provided builder is null
     *
     * @return
     *  a reference to this node factory
     */
    public NodeFactory addBuilder(NodeBuilder<?, ?> builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder is null");
        }

        this.builders.put(builder.getEntityClass(), builder);
        return this;
    }

    /**
     * Adds a mapper to this factory. The mapper will be used to provide entities during node
     * construction for the class returned by the mapper's <tt>getEntityClass</tt> method.
     *
     * @param mapper
     *  the mapper to add to this factory
     *
     * @throws IllegalArgumentException
     *  if the provided mapper is null
     *
     * @return
     *  a reference to this node factory
     */
    public NodeFactory addMapper(EntityMapper<?, ?> mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper is null");
        }

        this.mappers.put(mapper.getExistingEntityClass(), mapper);
        return this;
    }

    /**
     * Sets the mapper to use for mapping nodes created by this factory.
     *
     * @param nodeMapper
     *  the node mapper to use for mapping nodes
     *
     * @throws IllegalArgumentException
     *  if the provided mapper is null
     *
     * @return
     *  a reference to this node factory
     */
    public NodeFactory setNodeMapper(NodeMapper nodeMapper) {
        if (nodeMapper == null) {
            throw new IllegalArgumentException("nodeMapper is null");
        }

        this.nodeMapper = nodeMapper;
        return this;
    }

    /**
     * Returns an entity node for the given entity class and ID, creating and mapping it as
     * necessary.
     *
     * @param owner
     *  the organization that will own the created node
     *
     * @param cls
     *  the entity class of the node to create
     *
     * @param id
     *  the entity ID of the node to create
     *
     * @throws IllegalStateException
     *  if the node mapper has not been set, or a mapper or builder has not been provided for the
     *  given entity class, or the builder failed to create a node for the entity
     *
     * @return
     *  the entity node for the given entity class and ID
     */
    protected <E extends AbstractHibernateObject, I extends ServiceAdapterModel> EntityNode<E, I>
        buildNode(Owner owner, Class<E> cls, String id) {

        if (this.nodeMapper == null) {
            throw new IllegalStateException("mapper is null");
        }

        EntityNode<E, I> node = this.nodeMapper.getNode(cls, id);

        if (node == null) {
            EntityMapper<E, I> mapper = (EntityMapper<E, I>) this.mappers.get(cls);
            NodeBuilder<E, I> builder = (NodeBuilder<E, I>) this.builders.get(cls);

            if (mapper == null) {
                throw new IllegalStateException("no entity mapper provided for the entity class: " + cls);
            }

            if (builder == null) {
                throw new IllegalStateException("no node builder provided for the entity class: " + cls);
            }

            node = builder.buildNode(this, mapper, owner, id);
            if (node == null) {
                String errmsg = String.format("Unable to build node for entity: %s [id: %s]", cls, id);
                throw new IllegalStateException(errmsg);
            }

            this.nodeMapper.addNode(node);
        }

        return node;
    }

    /**
     * Builds nodes for all known entities present in every entity mapper registered with this node
     * factory. If matching nodes are already present in the registered node mapper, new nodes will
     * not be created for them.
     *
     * @param owner
     *  the organization that will own the created nodes
     *
     * @throws IllegalStateException
     *  if the node mapper has not been set, or a mapper or builder has not been provided for the
     *  given entity class, or the builder failed to create a node for the entity
     */
    public void buildNodes(Owner owner) {
        for (Map.Entry<Class, EntityMapper<?, ?>> entry : this.mappers.entrySet()) {
            Class cls = entry.getKey();
            EntityMapper<?, ?> mapper = entry.getValue();

            for (String id : (Set<String>) mapper.getEntityIds()) {
                this.buildNode(owner, cls, id);
            }
        }
    }

}
