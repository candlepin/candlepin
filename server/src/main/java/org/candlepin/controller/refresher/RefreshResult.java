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
package org.candlepin.controller.refresher;

import org.candlepin.model.AbstractHibernateObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * The RefreshResult encapsulates the entities processed during a refresh operation, allowing them
 * to be catalogued or post-processed in another operation.
 *
 * Note that this class does not fully encapsulate its collections, and modifications made to some
 * of the collections or maps received may result in changes to this class.
 */
public class RefreshResult {

    /**
     * The known states an entity can be in post-refresh
     */
    public static enum EntityState {
        CREATED,
        UPDATED,
        UNCHANGED,
        DELETED
    }

    /**
     * Stores refreshed entities of a given type
     *
     * @param <T>
     *  the class of entity managed by this entity store
     */
    public static class EntityStore<T extends AbstractHibernateObject> {

        /**
         * Container class for storing entity and entity state
         */
        private static class EntityData<T extends AbstractHibernateObject> {
            private final String entityId;
            private final T entity;
            private final EntityState state;

            public EntityData(String entityId, T entity, EntityState state) {
                this.entityId = entityId;
                this.entity = entity;
                this.state = state;
            }

            public String getEntityId() {
                return this.entityId;
            }

            public T getEntity() {
                return this.entity;
            }

            public EntityState getEntityState() {
                return this.state;
            }
        }

        private Map<String, EntityData<T>> entities;

        public EntityStore() {
            this.entities = new HashMap<>();
        }

        public void addEntity(T entity, EntityState state) {
            if (entity == null) {
                throw new IllegalArgumentException("entity is null");
            }

            if (state == null) {
                throw new IllegalArgumentException("state is null");
            }

            EntityData<T> data = new EntityData<>((String) entity.getId(), entity, state);
            this.entities.put(data.getEntityId(), data);
        }

        public T getEntity(String id, Collection<EntityState> states) {
            EntityData<T> data = this.entities.get(id);

            if (data != null) {
                return states == null || states.isEmpty() || states.contains(data.getEntityState()) ?
                    data.getEntity() :
                    null;
            }

            return null;
        }

        public EntityState getEntityState(String id) {
            EntityData<T> data = this.entities.get(id);
            return data != null ? data.getEntityState() : null;
        }

        public Map<String, T> getEntities(Collection<EntityState> states) {
            Stream<EntityData<T>> stream = this.entities.values()
                .stream();

            if (states != null && !states.isEmpty()) {
                stream = stream.filter(edata -> states.contains(edata.getEntityState()));
            }

            return stream.collect(Collectors.toMap(EntityData::getEntityId, EntityData::getEntity));
        }
    }

    private Map<Class, EntityStore> entityStoreMap;

    /**
     * Creates a new RefreshResult instance with no data
     */
    public RefreshResult() {
        this.entityStoreMap = new HashMap<>();
    }

    /**
     * Fetches the entity store for the given class, optionally creating it as necessary.
     *
     * @param cls
     *  the class of the entity store to fetch
     *
     * @param create
     *  whether or not to create the entity store if it doesn't already exist
     *
     * @return
     *  the entity store for the given class, or null if an appropriate entity store does not exist
     *  and the create flag is false.
     */
    private <T extends AbstractHibernateObject> EntityStore<T> getEntityStore(Class<T> cls, boolean create) {
        if (cls == null) {
            throw new IllegalArgumentException("cls is null");
        }

        EntityStore<T> entityStore = (EntityStore<T>) this.entityStoreMap.get(cls);
        if (entityStore == null && create) {
            entityStore = new EntityStore<T>();
            this.entityStoreMap.put(cls, entityStore);
        }

        return entityStore;
    }

    /**
     * Adds the specified entity to this result with the given entity state. If the entity has
     * already been added, the state will be updated to the new state provided.
     *
     * @param cls
     *  the entity class
     *
     * @param entity
     *  the entity to add as a created entity
     *
     * @param state
     *  the state of the entity as a result of the refresh operation
     *
     * @throws IllegalArgumentException
     *  if cls, entity, or state are null
     *
     * @return
     *  a reference to this refresh result
     */
    public <T extends AbstractHibernateObject> RefreshResult addEntity(Class<T> cls, T entity,
        EntityState state) {

        if (cls == null) {
            throw new IllegalArgumentException("cls is null");
        }

        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (state == null) {
            throw new IllegalArgumentException("state is null");
        }

        this.getEntityStore(cls, true)
            .addEntity(entity, state);

        return this;
    }

    /**
     * Fetches the entity with the specified entity class and ID, optionally filtering the result
     * using the provided entity states. If no such entity was part of this refresh, or the entity
     * did not match the given filter, this method returns null.
     *
     * @param cls
     *  the entity class
     *
     * @param id
     *  the ID of the entity to fetch
     *
     * @param states
     *  an optional list of states to use to filter the output
     *
     * @return
     *  the entity matching the given entity class, ID and filter, or null if a matching entity was
     *  not found
     */
    public <T extends AbstractHibernateObject> T getEntity(Class<T> cls, String id, EntityState... states) {
        return this.getEntity(cls, id, states != null && states.length > 0 ? Arrays.asList(states) : null);
    }

    /**
     * Fetches the entity with the specified entity class and ID, optionally filtering the result
     * using the provided entity states. If no such entity was part of this refresh, or the entity
     * did not match the given filter, this method returns null.
     *
     * @param cls
     *  the entity class
     *
     * @param id
     *  the ID of the entity to fetch
     *
     * @param states
     *  an optional collection of states to use to filter the output
     *
     * @return
     *  the entity matching the given entity class, ID and filter, or null if a matching entity was
     *  not found
     */
    public <T extends AbstractHibernateObject> T getEntity(Class<T> cls, String id,
        Collection<EntityState> states) {

        EntityStore<T> entityStore = this.getEntityStore(cls, false);
        return entityStore != null ? entityStore.getEntity(id, states) : null;
    }

    /**
     * Fetches the entity state of the given entity matching the entity class and ID. If no matching
     * entity was part of this refresh, this method returns null.
     *
     * @param cls
     *  the entity class
     *
     * @param id
     *  the ID of the entity for which to fetch the entity state
     *
     * @return
     *  the EntityState for the matching entity, or null if no matching entity was part of this
     *  refresh
     */
    public <T extends AbstractHibernateObject> EntityState getEntityState(Class<T> cls, String id) {
        EntityStore<T> entityStore = this.getEntityStore(cls, false);
        return entityStore != null ? entityStore.getEntityState(id) : null;
    }

    /**
     * Fetches a mapping of entities matching the given class and optional list of entity states.
     * The entities returned will be mapped by the entity's ID. If no matching entities were part of
     * this refresh, this method returns an empty map.
     *
     * @param cls
     *  the class of entities to fetch
     *
     * @param states
     *  an optional list of entity states to use to filter the output. If provided, only entities
     *  in the states provided will be fetched
     *
     * @return
     *  a mapping of entities matching the given class and entity states, or an empty map if no
     *  matching entities were part of this refresh
     */
    public <T extends AbstractHibernateObject> Map<String, T> getEntities(Class<T> cls,
        EntityState... states) {

        return this.getEntities(cls, states != null && states.length > 0 ? Arrays.asList(states) : null);
    }

    /**
     * Fetches a mapping of entities matching the given class and optional list of entity states.
     * The entities returned will be mapped by the entity's ID. If no matching entities were part of
     * this refresh, this method returns an empty map.
     *
     * @param cls
     *  the class of entities to fetch
     *
     * @param states
     *  an optional collection of entity states to use to filter the output. If provided, only
     *  entities in the states provided will be fetched.
     *
     * @return
     *  a mapping of entities matching the given class and entity states, or an empty map if no
     *  matching entities were part of this refresh
     */
    public <T extends AbstractHibernateObject> Map<String, T> getEntities(Class<T> cls,
        Collection<EntityState> states) {

        EntityStore<T> entityStore = this.getEntityStore(cls, false);
        return entityStore != null ? entityStore.getEntities(states) : new HashMap<>();
    }

}
