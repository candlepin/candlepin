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
package org.candlepin.controller.refresher.nodes;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ServiceAdapterModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;



/**
 * An abstract implementation of the EntityNode interface, capturing common behavior shared by many
 * of the concrete entity node implementations.
 *
 * @param <E>
 *  The type of the existing entity used by this node
 *
 * @param <I>
 *  The type of the imported entity used by this node
 */
public abstract class AbstractNode<E extends AbstractHibernateObject, I extends ServiceAdapterModel>
    implements EntityNode<E, I> {

    private String id;
    private Owner owner;

    private Map<Class<?>, Map<String, EntityNode<?, ?>>> parents;
    private Map<Class<?>, Map<String, EntityNode<?, ?>>> children;
    private NodeState state;

    private E existingEntity;
    private I importedEntity;
    private E mergedEntity;

    /**
     * Creates a new abstract node owned by the given organization, using the specified entity ID.
     *
     * @param owner
     *  the organization to own this node
     *
     * @param id
     *  the entity ID to use for this node
     *
     * @throws IllegalArgumentException
     *  if owner is null, or id is null or invalid
     */
    public AbstractNode(Owner owner, String id) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id is null or empty");
        }

        this.id = id;
        this.owner = owner;

        this.parents = new HashMap<>();
        this.children = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Owner getOwner() {
        return this.owner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEntityId() {
        return this.id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<E, I> addParentNode(EntityNode<?, ?> parent) {
        if (parent == null) {
            throw new IllegalArgumentException("parent is null");
        }

        if (parent == this) {
            throw new IllegalArgumentException("cannot add a node to itself as a parent");
        }

        EntityNode<?, ?> prev = this.parents.computeIfAbsent(parent.getEntityClass(), key -> new HashMap<>())
            .put(parent.getEntityId(), parent);

        if (prev != parent) {
            parent.addChildNode(this);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends AbstractHibernateObject, D extends ServiceAdapterModel> EntityNode<T, D> getParentNode(
        Class<T> entityClass, String entityId) {

        return (EntityNode<T, D>) this.parents.getOrDefault(entityClass, Collections.emptyMap())
            .get(entityId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<EntityNode<?, ?>> getParentNodes() {
        return this.parents.values()
            .stream()
            .flatMap(map -> map.values().stream());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<E, I> addChildNode(EntityNode<?, ?> child) {
        if (child == null) {
            throw new IllegalArgumentException("child is null");
        }

        if (child == this) {
            throw new IllegalArgumentException("cannot add a node to itself as a child");
        }

        EntityNode<?, ?> prev = this.children.computeIfAbsent(child.getEntityClass(), key -> new HashMap<>())
            .put(child.getEntityId(), child);

        if (prev != child) {
            child.addParentNode(this);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends AbstractHibernateObject, D extends ServiceAdapterModel> EntityNode<T, D> getChildNode(
        Class<T> entityClass, String entityId) {

        return (EntityNode<T, D>) this.children.getOrDefault(entityClass, Collections.emptyMap())
            .get(entityId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<EntityNode<?, ?>> getChildrenNodes() {
        return this.children.values()
            .stream()
            .flatMap(map -> map.values().stream());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRootNode() {
        return this.parents.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLeafNode() {
        return this.children.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean changed() {
        NodeState state = this.getNodeState();
        return state == NodeState.CREATED || state == NodeState.UPDATED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeState getNodeState() {
        return this.state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<E, I> setNodeState(NodeState state) {
        this.state = state;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<E, I> setExistingEntity(E entity) {
        this.existingEntity = entity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E getExistingEntity() {
        return this.existingEntity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<E, I> setImportedEntity(I entity) {
        this.importedEntity = entity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public I getImportedEntity() {
        return this.importedEntity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityNode<E, I> setMergedEntity(E entity) {
        this.mergedEntity = entity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E getMergedEntity() {
        return this.mergedEntity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("EntityNode [class: %s, entity id: %s, state: %s]",
            this.getEntityClass(), this.getEntityId(), this.getNodeState());
    }
}
