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
     * Adds an existing entity to this mapper. Null values will be silently ignored, but the entity
     * must have a valid, mappable ID.
     *
     * @param entity
     *  the existing entity to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the provided entity does not have a valid entity ID
     *
     * @return
     *  a reference to this entity mapper
     */
    EntityMapper<E, I> addExistingEntity(E entity);

    /**
     * Adds the collection of existing entities to this mapper. Null values will be silently
     * ignored, but all non-null entities must have a valid ID
     *
     * @param entities
     *  a collection of existing entities to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the collection contains one or more entities which are null or do not have valid entity
     *  IDs
     *
     * @return
     *  a reference to this entity mapper
     */
    EntityMapper<E, I> addExistingEntities(Collection<? extends E> entities);

    /**
     * Adds an imported entity to this mapper. Null values will be silently ignored, but the entity
     * must have a valid, mappable ID.
     *
     * @param entity
     *  the imported entity to add to this mapper
     *
     * @throws IllegalArgumentException
     *  if the provided entity does not have a valid entity ID
     *
     * @return
     *  a reference to this entity mapper
     */
    EntityMapper<E, I> addImportedEntity(I entity);

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
    EntityMapper<E, I> addImportedEntities(Collection<? extends I> entities);

    /**
     * Clears this entity mapper, removing all known existing and imported entities
     */
    void clear();

    /**
     * Clears any existing entities from this mapper, and clears any dirty flags set for existing
     * entities.
     */
    void clearExistingEntities();

    /**
     * Clears any imported entities from this mapper
     */
    void clearImportedEntities();
}
