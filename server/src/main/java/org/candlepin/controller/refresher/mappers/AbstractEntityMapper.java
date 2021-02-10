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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * An abstract implementation of the EntityMapper interface, capturing common behavior shared by many
 * of the concrete mapper implementations.
 *
 * @param <E>
 *  The type of the existing entity to be handled by this mapper
 *
 * @param <I>
 *  The type of the imported entity to be handled by this mapper
 */
public abstract class AbstractEntityMapper<E extends AbstractHibernateObject, I extends ServiceAdapterModel>
    implements EntityMapper<E, I> {

    private Map<String, E> existingEntities;
    private Map<String, I> importedEntities;
    private Map<String, Set<E>> versionedEntitiesMap;

    /**
     * Creates a new AbstractEntityMapper instance
     */
    public AbstractEntityMapper() {
        this.existingEntities = new HashMap<>();
        this.importedEntities = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E getExistingEntity(String id) {
        return this.existingEntities.get(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public I getImportedEntity(String id) {
        return this.importedEntities.get(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, E> getExistingEntities() {
        return this.existingEntities;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, I> getImportedEntities() {
        return this.importedEntities;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasEntity(String id) {
        return this.existingEntities.containsKey(id) || this.importedEntities.containsKey(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getEntityIds() {
        Set<String> ids = new HashSet<>();

        ids.addAll(this.existingEntities.keySet());
        ids.addAll(this.importedEntities.keySet());

        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<E> getCandidateEntities(String id) {
        return this.versionedEntitiesMap != null ? this.versionedEntitiesMap.get(id) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addExistingEntity(String id, E entity) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id is null or empty");
        }

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        return this.existingEntities.put(id, entity) != entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int addExistingEntities(Collection<E> entities) {
        int count = 0;

        if (entities != null) {
            for (E entity : entities) {
                if (this.addExistingEntity(entity)) {
                    ++count;
                }
            }
        }

        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addImportedEntity(String id, I entity) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id is null or empty");
        }

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        return this.importedEntities.put(id, entity) != entity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int addImportedEntities(Collection<I> entities) {
        int count = 0;

        if (entities != null) {
            for (I entity : entities) {
                if (this.addImportedEntity(entity)) {
                    ++count;
                }
            }
        }

        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setCandidateEntitiesMap(Map<String, Set<E>> versionedEntitiesMap) {
        // We should probably encapsulate this better, but this is fine for now.
        boolean output = this.versionedEntitiesMap != versionedEntitiesMap;
        this.versionedEntitiesMap = versionedEntitiesMap;

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        this.existingEntities.clear();
        this.importedEntities.clear();
        this.versionedEntitiesMap = null;
    }
}
