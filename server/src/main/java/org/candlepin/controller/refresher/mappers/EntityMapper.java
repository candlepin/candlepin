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

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.service.model.ServiceAdapterModel;

import java.util.Collection;
import java.util.Map;
import java.util.Set;



/**
 * The EntityMapper interface defines a number of methods for collecting and mapping entities
 * that currently exist, entities to import and a version map for doing fast versioning candidate
 * lookups.
 *
 * @param <E>
 *  The type of the existing entity to be handled by this mapper
 *
 * @param <I>
 *  The type of the imported entity to be handled by this mapper
 */
public interface EntityMapper<E extends AbstractHibernateObject, I extends ServiceAdapterModel> {

    /**
     * Fetches the class of the existing model entities mapped by this mapper. The value returned by
     * this method should match the class of existing entities to ensure proper mapping and node
     * creation.
     *
     * @return
     *  the class of existing entities mapped by this entity mapper
     */
    Class<E> getExistingEntityClass();

    /**
     * Fetches the class of the imported model entities mapped by this mapper. The value returned by
     * this method should match the class of imported entities to ensure proper mapping and node
     * creation.
     *
     * @return
     *  the class of imported entities mapped by this entity mapper
     */
    Class<I> getImportedEntityClass();

    /**
     * Fetches the existing database entity with the given ID. If the ID has not been mapped to an
     * existing database entity, this method returns null.
     *
     * @param id
     *  the id of the existing database entity to fetch
     *
     * @return
     *  the existing database entity for the given ID, or null if the no such entity has been mapped
     */
    E getExistingEntity(String id);

    /**
     * Fetches the imported entity with the given ID. If the ID has not been mapped to an imported
     * entity, this method returns null.
     *
     * @param id
     *  the id of the imported entity to fetch
     *
     * @return
     *  the imported entity for the given ID, or null if no such entity has been mapped
     */
    I getImportedEntity(String id);

    /**
     * Checks if this mapper has an existing or imported entity with the given ID.
     *
     * @param id
     *  the ID to check for existence
     *
     * @return
     *  true if an entity has been mapped for the given ID; false otherwise
     */
    boolean hasEntity(String id);

    /**
     * Fetches a set containing all known IDs which are mapped to an existing entity or an imported
     * entity.
     *
     * @return
     *  the set of all known entity IDs
     */
    Set<String> getEntityIds();

    /**
     * Fetches a map containing all known existing database entities, mapped by entity ID.
     *
     * @return
     *  a map containing all known existing database entities, mapped by entity ID
     */
    Map<String, E> getExistingEntities();

    /**
     * Fetches a map containing all known imported entities, mapped by entity ID.
     *
     * @return
     *  a map containing all known imported entities, mapped by entity ID
     */
    Map<String, I> getImportedEntities();

    /**
     * Fetches a set of entities with the same ID, owned by other owners which may be used for
     * version resolution and entity deduplication. If there are no other candidate entities for the
     * given ID, this method returns null.
     *
     * @param id
     *  the entity ID for which to fetch candidate entities
     *
     * @return
     *  a set of candidate entities for the given entity ID, or null if no candidate entities are
     *  available
     */
    Set<E> getCandidateEntities(String id);

    /**
     * Adds an existing entity to this mapper. The entity must not be null and it must have a valid
     * entity ID.
     *
     * @param entity
     *  the existing entity to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the provided entity is null or does not have a valid entity ID
     *
     * @return
     *  true if the entity is mapped successfully; false if the mapping already existed
     */
    boolean addExistingEntity(E entity);

    /**
     * Adds an existing entity to this mapper. The entity must not be null and the entity ID must
     * be valid.
     * <p></p>
     * Note that this method is provided for compatibility with entities which may not have a
     * "standard" method for fetching the entity ID. The single-parameter <tt>addExistingEntity</tt>
     * should be used instead wherever possible.
     *
     * @param id
     *  the entity ID to use for mapping the given entity
     *
     * @param entity
     *  the existing entity to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the provided entity is null, or the entity ID is null or invalid
     *
     * @return
     *  true if the entity is mapped successfully; false if the mapping already existed
     */
    boolean addExistingEntity(String id, E entity);

    /**
     * Adds the collection of existing entities to this mapper. The entities must not be null and
     * must have valid entity IDs.
     *
     * @param entities
     *  a collection of existing entities to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the collection contains one or more entities which are null or do not have valid entity
     *  IDs
     *
     * @return
     *  the number of new entity mappings created as a result of this operation
     */
    int addExistingEntities(Collection<E> entities);

    /**
     * Adds an imported entity to this mapper. The entity must not be null and it must have a valid
     * entity ID.
     *
     * @param entity
     *  the imported entity to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the provided entity is null or does not have a valid entity ID
     *
     * @return
     *  true if the entity is mapped successfully; false if the mapping already existed
     */
    boolean addImportedEntity(I entity);

    /**
     * Adds an imported entity to this mapper. The entity must not be null and the entity ID must
     * be valid.
     * <p></p>
     * Note that this method is provided for compatibility with entities which may not have a
     * "standard" method for fetching the entity ID. The single-parameter <tt>addImportedEntity</tt>
     * should be used instead wherever possible.
     *
     * @param id
     *  the entity ID to use for mapping the given entity
     *
     * @param entity
     *  the imported entity to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the provided entity is null, or the entity ID is null or invalid
     *
     * @return
     *  true if the entity is mapped successfully; false if the mapping already existed
     */
    boolean addImportedEntity(String id, I entity);

    /**
     * Adds the collection of imported entities to this mapper. The entities must not be null and
     * must have valid entity IDs.
     *
     * @param entities
     *  a collection of imported entities to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the collection contains one or more entities which are null or do not have valid entity
     *  IDs
     *
     * @return
     *  the number of new entity mappings created as a result of this operation
     */
    int addImportedEntities(Collection<I> entities);

    /**
     * Sets the mapping of candidate entities (versioned entities) to use for performing lookups of
     * candidate entities. The map should be a mapping of entity IDs to sets of existing database
     * entities owner by other organizations.
     *
     * @param versionedEntitiesMap
     *
     * @return
     *  true if the candidate entities are updated sucessfully; false if no change occurred
     */
    boolean setCandidateEntitiesMap(Map<String, Set<E>> versionedEntitiesMap);

    /**
     * Clears this entity mapper, removing all known existing and imported entities, and clearing any
     * provided candidate entities map.
     */
    void clear();
}
