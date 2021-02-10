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

import java.util.Collection;
import java.util.Set;



/**
 * The EntityNode interface defines the API for a node to act as part of a tree model of entities
 * that currently exist and/or are being imported.
 *
 * @param <E>
 *  The type of the existing entity used by this node
 *
 * @param <I>
 *  The type of the imported entity used by this node
 */
public interface EntityNode<E extends AbstractHibernateObject, I extends ServiceAdapterModel> {

    /**
     * The NodeState enum defines the pseudo-states a node can be in as a result of node processing.
     */
    public static enum NodeState {
        /**
         * The CREATED state represents a node with an imported entity that causes a net-new entity
         * to be created as a result of the refresh operation
         */
        CREATED,

        /**
         * The UPDATED state represents a node with an existing entity that was updated as a result
         * of the refresh operation
         */
        UPDATED,

        /**
         * The UNCHANGED state represents a node with an existing entity that was unchanged during
         * the refresh operation
         */
        UNCHANGED,

        /**
         * The DELETED state represents a node with an existing entity which was deleted as a result
         * of the refresh operation
         */
        DELETED,

        /**
         * The SKIPPED state represents a node that should not be processed, nor included in the
         * refresh result
         */
        SKIPPED
    }

    /**
     * Fetches the class of the database model entity mapped by this entity node.
     *
     * @return
     *  the class of database model entity mapped by this node
     */
    Class<? extends AbstractHibernateObject> getEntityClass();

    /**
     * Fetches the organization that owns the entity contained by this node.
     *
     * @return
     *  the Owner instance representing the organization that owns the entity contained by this node
     */
    Owner getOwner();

    /**
     * Fetches the ID of this entity node. The entity ID should match the ID of any existing
     * database entity or imported entity contained by this node.
     *
     * @return
     *  the ID of this entity node.
     */
    String getEntityId();

    /**
     * Adds the specified entity node as a parent of this node. If the provided parent node is null
     * or a reference to this entity node, this method throws an exception.
     *
     * @param parent
     *  the entity node to add as a parent of this node
     *
     * @throws IllegalArgumentException
     *  if the provided parent node is null or a reference to this entity node
     *
     * @return
     *  a reference to this entity node
     */
    EntityNode<E, I> addParentNode(EntityNode<?, ?> parent);

    /**
     * Fetches a collection containing the known parent nodes of this node. If this node has no
     * parents, this method returns an empty collection.
     *
     * @return
     *  a collection of the known parent nodes of this entity node
     */
    Collection<EntityNode<?, ?>> getParentNodes();

    /**
     * Adds the specified entity node as a child of this node. If the provided parent node is null
     * or a reference to this entity node, this method throws an exception.
     *
     * @param child
     *  the entity node to add as a child of this node
     *
     * @throws IllegalArgumentException
     *  if the provided parent node is null or a reference to this entity node
     *
     * @return
     *  a reference to this entity node
     */
    EntityNode<E, I> addChildNode(EntityNode<?, ?> child);

    /**
     * Fetches a collection containing the known children nodes of this node. If this node has no
     * children, this method returns an empty collection.
     *
     * @return
     *  a collection of the known children nodes of this entity node
     */
    Collection<EntityNode<?, ?>> getChildrenNodes();

    /**
     * Checks if this entity node is a root node, indicating that it has no parent nodes.
     *
     * @return
     *  true if this node is a root node; false otherwise
     */
    boolean isRootNode();

    /**
     * Checks if this entity node is a leaf node, indicating that it has no children nodes.
     *
     * @return
     *  true if this node is a leaf node; false otherwise
     */
    boolean isLeafNode();

    /**
     * Checks if this node represents a new or updated database entity.
     *
     * @return
     *  true if this node represents a new or updated database entity; false otherwise
     */
    boolean changed();

    /**
     * Fetches the operation to be performed on this node
     *
     * @return
     *  a NodeState representing the pseudo-state of this node
     */
    NodeState getNodeState();

    /**
     * Sets the pseudo-state of this node. If the provided state is null, any existing state will be
     * cleared and visitors may treat the node like a new node, potentially reprocessing it.
     *
     * @param state
     *  the state to assign to this node
     *
     * @return
     *  a reference to this entity node
     */
    EntityNode<E, I> setNodeState(NodeState state);

    /**
     * Sets the existing database entity for this node. If the provided entity is null, any existing
     * database entity will be cleared.
     *
     * @param entity
     *  the existing entity to be set for this entity node, or null to clear the existing entity
     *
     * @return
     *  a reference to this entity node
     */
    EntityNode<E, I> setExistingEntity(E entity);

    /**
     * Fetches the existing database entity contained by this node. If the existing entity has not
     * been set or does not exist, this method returns null.
     *
     * @return
     *  the existing entity of this node, or null if the existing entity has not been set
     */
    E getExistingEntity();

    /**
     * Sets the imported entity for this node. If the provided entity is null, any imported entity
     * will be cleared.
     *
     * @param entity
     *  the imported entity to be set for this entity node, or null to clear the imported entity
     *
     * @return
     *  a reference to this entity node
     */
    EntityNode<E, I> setImportedEntity(I entity);

    /**
     * Fetches the imported entity contained by this node. If the imported eneity has not been set
     * or does not exist, this method returns null.
     *
     * @return
     *  the imported entity of this node, or null if the imported entity has not been set
     */
    I getImportedEntity();

    /**
     * Sets the merged entity for this node. The merged entity represents the merged changes
     * applied to a database model entity to persist. If the merged entity is not set, no database
     * changes will be made for the entity represented by this node.
     *
     * @param entity
     *  the merged entity to be set for this node, or null to clear the merged entity
     *
     * @return
     *  a reference to this entity node
     */
    EntityNode<E, I> setMergedEntity(E entity);

    /**
     * Fetches the merged entity for this node, representing the merged changes applied to a
     * database model entity to persist. If the merged entity is not set, this method will return
     * null.
     *
     * @return
     *  the merged entity set for this node, or null if this node does not represent a database
     *  change
     */
    E getMergedEntity();

    /**
     * Sets the collection of candidate entities for the updated entity of this node. If the
     * provided collection of entities is null, any existing candidate entity set will be cleared.
     *
     * @param entities
     *  the candidate entities to set for this entity node, or null to clear the candidate entities
     *
     * @return
     *  a reference to this entity node
     */
    EntityNode<E, I> setCandidateEntities(Set<E> entities);

    /**
     * Fetches the set of candidate entities for the updated entity for this node. If there are no
     * available candidate entities, this method returns null.
     *
     * @return
     *  the candidate entities for the updated node, or null if no candidate entities are available
     */
    Set<E> getCandidateEntities();
}
