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
     * Checks if any existing entity mapped by this mapper is "dirty," indicating it has been given
     * two or more different versions of the same entity, or it contains a reference to an entity
     * which is not mapped to the owning organization.
     *
     * @return
     *  true if any mapped existing entities have dirty references
     */
    boolean isDirty();

    /**
     * Checks if the existing entity mapped to the given ID is "dirty," indicating it has been
     * mapped to two or more different versions of the entity, or is mapped to an entity which
     * is not mapped to the owning organization.
     *
     * @param id
     *  the ID of the entity to check
     *
     * @throws IllegalArgumentException
     *  if the provided ID is null or invalid
     *
     * @return
     *  true if the existing entity mapped to the given ID is dirty; false otherwise
     */
    boolean isDirty(String id);

    /**
     * Validates that this mapper only contains existing entity mappings for the entities with the
     * given IDs. Any mapped existing entities with IDs not present in the provided collection will
     * be flagged as "dirty," indicating that the mapping is to an entity that exists outside of the
     * owning organization.
     * This method returns true if any mapping is flagged as dirty upon completion of this check,
     * even if the dirty flag was already set prior to the call to this method.
     *
     * @param ids
     *  a collection containing the superset of entity IDs known to the owning organization
     *
     * @return
     *  true if any existing entity mapping is dirty after a call to this method; false otherwise
     */
    boolean validateExistingEntities(Collection<String> ids);

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
