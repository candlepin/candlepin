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
package org.candlepin.model;

import org.candlepin.auth.Principal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.config.Configuration;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.exceptions.ConcurrentModificationException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;

import com.google.common.collect.Iterables;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.TransactionRequiredException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;



/**
 * AbstractHibernateCurator base class for all Candlepin curators. Curators are
 * the applications database layer, typically following the pattern of one
 * curator for each model type. This class contains methods common to all
 * curators.
 * @param <E> Entity specific curator.
 */
public abstract class AbstractHibernateCurator<E extends Persisted> {
    private static Logger log = LoggerFactory.getLogger(AbstractHibernateCurator.class);

    @Inject protected Provider<EntityManager> entityManager;
    @Inject protected Provider<I18n> i18nProvider;
    @Inject protected Configuration config;
    @Inject private PrincipalProvider principalProvider;

    private final Class<E> entityType;

    public AbstractHibernateCurator(Class<E> entityType) {
        this.entityType = entityType;
    }

    public Class<E> entityType() {
        return entityType;
    }

    public int getInBlockSize() {
        return config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
    }

    public int getBatchBlockSize() {
        return config.getInt(DatabaseConfigFactory.BATCH_BLOCK_SIZE);
    }

    public int getQueryParameterLimit() {
        return config.getInt(DatabaseConfigFactory.QUERY_PARAMETER_LIMIT);
    }

    public String getDatabaseDialect() {
        return ((String) this.currentSession().getSessionFactory().getProperties()
            .get("hibernate.dialect")).toLowerCase();
    }

    protected final <T> T secureGet(Class<T> clazz, Serializable id) {
        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(clazz);
        Root<T> root = query.from(clazz);

        Predicate securityPredicate = this.getSecurityPredicate(clazz, cb, root);
        Predicate idPredicate = cb.equal(root.get("id"), id);

        if (securityPredicate != null) {
            query.select(root).where(cb.and(securityPredicate, idPredicate));
        }
        else {
            query.select(root).where(idPredicate);
        }

        try {
            return em.createQuery(query).getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Same as {@link get} but allows permissions on the current principal to inject
     * filters into the query before it is run. Primarily useful in authentication when
     * we want to verify access to an entity specified in the URL, but not reveal if
     * the entity exists or not if you don't have permissions to see it at all.
     *
     * @param id db id of entity to be found.
     * @return entity matching given id, or null otherwise.
     */
    public E secureGet(Serializable id) {
        return id == null ? null : secureGet(entityType, id);
    }

    protected <T> T get(Class<T> clazz, Serializable id) {
        return this.getEntityManager().find(clazz, id);
    }

    /**
     * @param id db id of entity to be found.
     * @return entity matching given id, or null otherwise.
     */
    public E get(Serializable id) {
        return id == null ? null : this.get(entityType, id);
    }

    /**
     * Checks if entry exists in db.
     * @param id primary key of entity.
     * @return boolean value whether row exists or not
     */
    public boolean exists(Serializable id) {
        boolean doesExists = false;
        String columnName = getPrimaryKeyName();

        if (columnName != null) {
            EntityManager em = this.getEntityManager();
            CriteriaBuilder builder = em.getCriteriaBuilder();
            CriteriaQuery<Long> query = builder.createQuery(Long.class);
            query.select(builder.count(query.from(entityType)));
            Root<E> root = query.from(entityType);
            query.where(builder.equal(root.get(columnName), id));

            if (em.createQuery(query).getSingleResult() > 0) {
                doesExists = true;
            }
        }

        return doesExists;
    }

    /**
     * Returns name of primary key column
     * @return Returns entity primary key column
     */
    public String getPrimaryKeyName() {
        String primaryColumn = null;

        try {
            primaryColumn = this.getEntityManager()
                .getMetamodel()
                .entity(entityType)
                .getId(String.class)
                .getName();
        }
        catch (IllegalArgumentException e) {
            log.debug("Unable to get primary key for entity {}", entityType, e);
        }

        return primaryColumn;
    }

    /**
     * Persists the given entity, flushing after the persistence if the calling thread is operating
     * within a transaction.
     *
     * @param entity
     *  the new entity to persist
     *
     * @return
     *  the newly persisted entity
     */
    public E create(E entity) {
        return create(entity, this.inTransaction());
    }

    /**
     * Persists the given entity, optionally flushing after persistence.
     *
     * @param entity
     *  the new entity to persist
     *
     * @param flush
     *  whether or not to flush the persist operation. Should not be set when operating outside of
     *  a transaction
     *
     * @return
     *  the newly persisted entity
     */
    @Transactional
    public E create(E entity, boolean flush) {
        this.getEntityManager()
            .persist(entity);

        if (flush) {
            this.flush();
        }

        return entity;
    }

    /**
     * @return all entities for a particular type.
     */
    public List<E> listAll() {
        EntityManager em = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<E> criteriaQuery = criteriaBuilder.createQuery(this.entityType);
        Root<E> root = criteriaQuery.from(this.entityType);
        criteriaQuery.select(root);

        Predicate securityPredicate = this.getSecurityPredicate(this.entityType, criteriaBuilder, root);
        if (securityPredicate != null) {
            criteriaQuery.where(securityPredicate);
        }

        return em.createQuery(criteriaQuery).getResultList();
    }

    public List<E> listAllByIds(Collection<? extends Serializable> ids) {
        EntityManager em = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<E> criteriaQuery = criteriaBuilder.createQuery(this.entityType);
        Root<E> root = criteriaQuery.from(this.entityType);
        criteriaQuery.select(root);

        ParameterExpression<Collection> idList = criteriaBuilder.parameter(Collection.class);
        Predicate predicate = root.get("id").in(idList);

        Predicate securityPredicate = this.getSecurityPredicate(this.entityType, criteriaBuilder, root);
        if (securityPredicate != null) {
            predicate = criteriaBuilder.and(predicate, securityPredicate);
        }
        criteriaQuery.where(predicate);
        TypedQuery<E> query = em.createQuery(criteriaQuery);

        // to ensure uniqueness of the members of the input collection
        if (!(ids instanceof Set)) {
            ids = new HashSet<>(ids);
        }

        List<E> result = new ArrayList<>();
        for (List block : this.partition(ids)) {
            result.addAll(query.setParameter(idList, block).getResultList());
        }
        return result;
    }

    private List<E> loadPageData(CriteriaQuery<E> criteria, PageRequest pageRequest) {
        TypedQuery<E> query = this.entityManager.get().createQuery(criteria);
        if (pageRequest.isPaging()) {
            query.setFirstResult((pageRequest.getPage() - 1) * pageRequest.getPerPage());
            query.setMaxResults(pageRequest.getPerPage());
        }

        return query.getResultList();
    }

    private Order createPagingOrder(Root<?> root, PageRequest p) {
        String sortBy = (p.getSortBy() == null) ? PageRequest.DEFAULT_SORT_FIELD : p.getSortBy();
        PageRequest.Order order = (p.getOrder() == null) ? PageRequest.DEFAULT_ORDER : p.getOrder();
        CriteriaBuilder criteriaBuilder = this.entityManager.get().getCriteriaBuilder();

        if (order == PageRequest.Order.ASCENDING) {
            return criteriaBuilder.asc(root.get(sortBy));
        }

        //DESCENDING
        return criteriaBuilder.desc(root.get(sortBy));
    }

    public List<E> listByCriteria(CriteriaQuery<E> query) {
        return this.getEntityManager()
            .createQuery(query)
            .getResultList();
    }

    /**
     * Fetches the principal for the current request or scope. If the scope does not have a
     * principal, this method returns null.
     *
     * @return
     *  the principal for the current scope
     */
    protected Principal getPrincipal() {
        return this.principalProvider.get();
    }

    /**
     * Builds the security predicate for the given entity class using the provided builder and query
     * root. If the current principal does not require a security predicate, this method returns
     * null.
     *
     * @param entityClass
     *  the entity class for which to build the security predicate
     *
     * @param builder
     *  a CriteriaBuilder instance to use to build the security predicate
     *
     * @param root
     *  the root path to use to build the security predicate
     *
     * @throws IllegalArgumentException
     *  if entityClass, builder, or root are null
     *
     * @return
     *  the security predicate to apply to a query, or null if the current principal does not
     *  require any query restrictions
     */
    protected <T> Predicate getSecurityPredicate(Class<T> entityClass, CriteriaBuilder builder,
        From<?, T> root) {

        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass is null");
        }

        if (builder == null) {
            throw new IllegalArgumentException("builder is null");
        }

        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }

        Principal principal = this.getPrincipal();
        Predicate predicate = null;

        if (principal != null && !principal.hasFullAccess()) {
            List<Predicate> predicates = new ArrayList<>();

            for (Permission permission : principal.getPermissions()) {
                Predicate restriction = permission.getQueryRestriction(entityClass, builder, root);

                if (restriction != null) {
                    log.debug("Received restriction predicate from permission {} for {}: {}",
                        permission, entityClass, restriction);

                    predicates.add(restriction);
                }
            }

            if (!predicates.isEmpty()) {
                predicate = predicates.size() > 1 ?
                    builder.or(predicates.toArray(new Predicate[0])) :
                    predicates.get(0);
            }
        }

        return predicate;
    }


    public Page<List<E>> listByCriteria(Root<E> root, CriteriaQuery<E> criteria, PageRequest pageRequest,
        int maxRecords) {
        Page<List<E>> page = new Page<>();
        if (pageRequest != null) {
            criteria.orderBy(createPagingOrder(root, pageRequest));
            // TODO page should store long
            page.setMaxRecords(maxRecords);
            page.setPageData(loadPageData(criteria, pageRequest));
            page.setPageRequest(pageRequest);
        }
        else {
            List<E> pageData = listByCriteria(criteria);
            page.setMaxRecords(pageData.size());
            page.setPageData(pageData);
        }

        return page;
    }

    /**
     * @param entity to be deleted.
     */
    @Transactional
    public void delete(E entity) {
        if (entity != null) {
            EntityManager em = this.getEntityManager();
            em.remove(em.find(this.entityType, entity.getId()));
        }
    }

    @Transactional
    public void bulkDelete(Collection<E> entities) {
        if (entities != null) {
            entities.forEach(this::delete);
        }
    }

    public void detach(E entity) {
        getEntityManager().detach(entity);
    }

    public void batchDetach(Collection<E> entities) {
        for (E entity : entities) {
            detach(entity);
        }
    }

    /**
     * @param entity entity to be merged.
     * @return merged entity.
     */
    @Transactional
    public E merge(E entity) {
        if (entity == null) {
            return null;
        }

        return this.getEntityManager()
            .merge(entity);
    }

    @Transactional
    protected void save(E entity) {
        if (entity == null) {
            return;
        }

        create(entity, true);
    }

    @Transactional
    public E saveOrUpdate(E entity) {
        EntityManager em = this.getEntityManager();
        if (isEntityNew(entity)) {
            em.persist(entity);
        }
        else {
            em.merge(entity);
        }
        return entity;
    }

    public void flush() {
        try {
            EntityManager entityManager = this.getEntityManager();

            // If there's no transaction or it's not active, there's no reason to flush. Attempting
            // to do so will trigger an exception. Instead, just toss out a warning about it.
            if (this.inTransaction()) {
                entityManager.flush();
            }
            else {
                String errmsg = "flush issued outside of a transaction";
                log.warn(errmsg, log.isDebugEnabled() ? new TransactionRequiredException(errmsg) : "");
            }
        }
        catch (OptimisticLockException e) {
            throw new ConcurrentModificationException(getConcurrentModificationMessage(), e);
        }
    }

    public void clear() {
        try {
            getEntityManager().clear();
        }
        catch (OptimisticLockException e) {
            throw new ConcurrentModificationException(getConcurrentModificationMessage(), e);
        }
    }

    public Session currentSession() {
        return (Session) entityManager.get().getDelegate();
    }

    public EntityManager getEntityManager() {
        return entityManager.get();
    }

    public EntityTransaction getTransaction() {
        EntityManager manager = this.getEntityManager();
        return manager != null ? manager.getTransaction() : null;
    }

    /**
     * Checks if the calling thread is currently operating within the context of a database
     * transaction.
     *
     * @return
     *  true if the calling thread is operating within a database transaction; false otherwise
     */
    public boolean inTransaction() {
        EntityTransaction transaction = this.getTransaction();
        return transaction != null && transaction.isActive();
    }

    /**
     * Creates a new transactional wrapper from the backing entity manager
     *
     * @return
     *  a Transactional wrapper configured to execute the specified action
     */
    public <O> org.candlepin.util.Transactional<O> transactional() {
        return new org.candlepin.util.Transactional<O>(this.getEntityManager());
    }

    /**
     * Creates a new transactional wrapper from the backing entity manager using the specified
     * action.
     *
     * @param action
     *  The action to perform in a transaction
     *
     * @return
     *  a Transactional wrapper configured to execute the specified action
     */
    public <O> org.candlepin.util.Transactional<O> transactional(
        org.candlepin.util.Transactional.Action<O> action) {

        return this.<O>transactional()
            .run(action);
    }

    /**
     * Retrieves the primary key of the given entity.
     * <p>
     * This method utilizes the EntityManager to access the PersistenceUnitUtil and fetch the
     * identifier of the entity. We employ this approach because not all entities in our
     * application use "id" as their primary key. Some entities, such as Content and Product, use
     * "uuid" as their primary key.
     *
     * @param entity the entity whose primary key is to be retrieved
     *
     * @return the primary key of the entity as an Object, or null if the entity is not yet persisted
     */
    private Object getEntityPrimaryKey(E entity) {
        return this.getEntityManager()
            .getEntityManagerFactory()
            .getPersistenceUnitUtil()
            .getIdentifier(entity);
    }

    /**
     * Checks if the given entity is new (not yet persisted).
     * <p>
     * This method determines if the entity is new by checking if its primary key is null.
     * An entity is considered new if its primary key is null, indicating it has not been
     * persisted in the database.
     *
     * @param entity the entity to check
     *
     * @return true if the entity is new (i.e., its primary key is null), false otherwise
     */
    private boolean isEntityNew(E entity) {
        return getEntityPrimaryKey(entity) == null;
    }

    @Transactional
    public Collection<E> saveAll(Collection<E> entities, boolean flush, boolean evict) {
        if (entities != null && !entities.isEmpty()) {
            try {
                EntityManager em = this.getEntityManager();
                Iterable<List<E>> blocks = Iterables.partition(entities, getBatchBlockSize());

                for (List<E> block : blocks) {
                    block.forEach(em::persist);

                    if (flush) {
                        em.flush();

                        if (evict) {
                            block.forEach(em::detach);
                        }
                    }
                }
            }
            catch (OptimisticLockException e) {
                throw new ConcurrentModificationException(getConcurrentModificationMessage(), e);
            }
        }

        return entities;
    }

    @Transactional
    public Collection<E> updateAll(Collection<E> entities, boolean flush, boolean evict) {
        if (entities != null && !entities.isEmpty()) {
            try {
                EntityManager em = this.getEntityManager();
                Iterable<List<E>> blocks = Iterables.partition(entities, getBatchBlockSize());

                for (List<E> block : blocks) {
                    block.forEach(em::merge);

                    if (flush) {
                        em.flush();

                        if (evict) {
                            block.forEach(em::detach);
                        }
                    }
                }
            }
            catch (OptimisticLockException e) {
                throw new ConcurrentModificationException(getConcurrentModificationMessage(), e);
            }
        }

        return entities;
    }

    @Transactional
    public Iterable<E> saveOrUpdateAll(Iterable<E> entities, boolean flush, boolean evict) {
        if (entities != null) {
            try {
                EntityManager em = this.getEntityManager();
                Iterable<List<E>> blocks = Iterables.partition(entities, getBatchBlockSize());

                for (List<E> block : blocks) {
                    for (E entity : block) {
                        if (isEntityNew(entity)) {
                            em.persist(entity);
                        }
                        else {
                            em.merge(entity);
                        }
                    }

                    if (flush) {
                        em.flush();

                        if (evict) {
                            block.forEach(em::detach);
                        }
                    }
                }
            }
            catch (OptimisticLockException e) {
                throw new ConcurrentModificationException(getConcurrentModificationMessage(), e);
            }
        }

        return entities;
    }

    public void refresh(Iterable<E> entities) {
        if (entities != null) {
            EntityManager manager = this.getEntityManager();
            entities.forEach(manager::refresh);
        }
    }

    public void refresh(E... entities) {
        if (entities != null) {
            this.refresh(Arrays.asList(entities));
        }
    }

    public E evict(E entity) {
        this.getEntityManager().detach(entity);
        return entity;
    }

    public List<E> takeSubList(QueryArguments<?> query, List<E> results) {
        if (query.getOffset() == null || query.getLimit() == null ||
            results == null || results.isEmpty()) {

            return results;
        }

        int fromIndex = (query.getOffset() - 1) * query.getLimit();
        int toIndex = fromIndex + query.getLimit();

        return takeSubList(fromIndex, toIndex, results);
    }

    private List<E> takeSubList(int fromIndex, int toIndex, List<E> results) {
        if (fromIndex >= results.size()) {
            return new ArrayList<>();
        }

        if (toIndex > results.size()) {
            toIndex = results.size();
        }

        // sublist returns a portion of the list between the specified fromIndex,
        // inclusive, and toIndex, exclusive.
        return results.subList(fromIndex, toIndex);
    }

    private String getConcurrentModificationMessage() {
        return i18nProvider.get().tr("Request failed due to concurrent modification, please re-try.");
    }

    /**
     * Locks a collection of entities with a pessimisitic write lock. Note that none of the entities
     * will be refreshed as a result of a call to this method. If an entity needs to be locked and
     * refreshed, used the lockAndLoad method family instead.
     *
     * @param entities
     *  A collection of entities to lock
     *
     * @return
     *  the provided collection of now-locked entities
     */
    public Collection<E> lock(Iterable<E> entities) {
        Map<Serializable, E> entityMap = new TreeMap<>();

        // Step through and toss all the entities into our TreeMap, which orders its entries using
        // the natural order of the key. This will ensure that we have our entity collection sorted
        // by entity ID, which should help avoid deadlock by having a deterministic locking order.
        for (E entity : entities) {
            entityMap.put((Serializable) getEntityPrimaryKey(entity), entity);
        }

        for (E entity : entityMap.values()) {
            this.lock(entity);
        }

        return entityMap.values();
    }

    public Collection<E> lock(E... entities) {
        return entities != null ? this.lock(Arrays.asList(entities)) : null;
    }

    /**
     * Locks the specified entity with a pessimistic write lock. Note that the entity will not be
     * refreshed as a result of a call to this method. If the entity needs to be locked and
     * refreshed, use the lockAndLoad method family instead.
     *
     * @param entity
     *  The entity to lock
     *
     * @throws IllegalArgumentException
     *  if entity is null
     *
     * @return
     *  The locked entity
     */
    public E lock(E entity) {
        return this.lock(entity, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Locks the specified entity using the given lock mode. Note that the entity will not be
     * refreshed as a result of a call to this method. If the entity needs to be locked and
     * refreshed, use the lockAndLoad method family instead.
     *
     * @param entity
     *  The entity to lock
     *
     * @param lockMode
     *  The lock mode to apply to the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or lockMode are null
     *
     * @return
     *  The locked entity
     */
    public E lock(E entity, LockModeType lockMode) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        if (lockMode == null) {
            throw new IllegalArgumentException("lockMode is null");
        }

        this.getEntityManager().lock(entity, lockMode);
        return entity;
    }

    /**
     * Loads an entity with a pessimistic lock using the specified ID. If the entity has already
     * been loaded, it will be refreshed with the lock instead. If a matching entity could not be
     * found, this method returns null.
     * <p></p>
     * <strong>Note:</strong> There is no guarantee that the entity returned by this method will be
     * loaded from the database at the time it is called. Due to various caching mechanisms, it's
     * possible that an existing entity will be returned in its current state, including any
     * pending, uncommitted changes or operations. As such, the only guarantees this method
     * provides is that if a non-null value is returned, it will be a locked instance of the entity.
     * Any desired refresh or resynchronization behavior must be done by the caller after this
     * method returns.
     *
     *
     * @param id
     *  The id of the entity to load/refresh and lock
     *
     * @return
     *  A locked entity instance, or null if a matching entity could not be found
     */
    public E lockAndLoad(Serializable id) {
        return this.lockAndLoad(this.entityType, id);
    }

    /**
     * Loads an entity with a pessimistic lock using the specified entity class and ID. If the
     * entity has already been loaded, it will be refreshed with the lock instead. If a matching
     * entity could not be found, this method returns null.
     * <p></p>
     * <strong>Note:</strong> There is no guarantee that the entity returned by this method will be
     * loaded from the database at the time it is called. Due to various caching mechanisms, it's
     * possible that an existing entity will be returned in its current state, including any
     * pending, uncommitted changes or operations. As such, the only guarantees this method
     * provides is that if a non-null value is returned, it will be a locked instance of the entity.
     * Any desired refresh or resynchronization behavior must be done by the caller after this
     * method returns.
     *
     * @param entityClass
     *  The class representing the type of entity to fetch (i.e. Pool.class or Product.class)
     *
     * @param id
     *  The id of the entity to load/refresh and lock
     *
     * @return
     *  A locked entity instance, or null if a matching entity could not be found
     */
    protected E lockAndLoad(Class<E> entityClass, Serializable id) {
        return this.getEntityManager().find(entityClass, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Loads the entities represented by the given IDs with a pessimistic write lock. If no entities
     * could be found matching the given IDs, this method returns an empty collection. Note that this
     * method makes no attempt to ensure that an entity is loaded for every ID provided. It is possible
     * for the output collection to be smaller than the provided set of IDs.
     * <p></p>
     * <strong>Note:</strong> There is no guarantee that the entities returned by this method will
     * be loaded from the database at the time it is called. Due to various caching mechanisms, it's
     * possible that an existing entity will be returned in its current state, including any
     * pending, uncommitted changes or operations. As such, the only guarantees this method
     * provides is that if a non-null value is returned, it will be a locked instance of the entity.
     * Any desired refresh or resynchronization behavior must be done by the caller after this
     * method returns.
     *
     * @param ids
     *  A collection of entity IDs to use to load and lock the represented entities
     *
     * @return
     *  A list of locked entities represented by the given IDs
     */
    public List<E> lockAndLoad(Iterable<? extends Serializable> ids) {
        return this.lockAndLoad(this.entityType, ids);
    }

    /**
     * Loads a collection of entities with a pessimistic lock using the specified entity class
     * and collection of IDs. If no entities could be found matching the given IDs, this method
     * returns an empty collection. Note that this method makes no attempt to ensure that an entity
     * is loaded for every ID provided. It is possible for the output collection to be smaller than
     * the provided set of IDs.
     * <p></p>
     * <strong>Note:</strong> There is no guarantee that the entities returned by this method will
     * be loaded from the database at the time it is called. Due to various caching mechanisms, it's
     * possible that an existing entity will be returned in its current state, including any
     * pending, uncommitted changes or operations. As such, the only guarantees this method
     * provides is that if a non-null value is returned, it will be a locked instance of the entity.
     * Any desired refresh or resynchronization behavior must be done by the caller after this
     * method returns.
     *
     * @param entityClass
     *  The class representing the type of the entity to load (i.e. Pool.class or Product.class)
     *
     * @param ids
     *  A collection of IDs to use to load
     *
     * @return
     *  A list of locked entities matching the given values
     */
    protected List<E> lockAndLoad(Class<E> entityClass, Iterable<? extends Serializable> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }

        // Sort and de-duplicate the provided collection of IDs so we have a deterministic locking
        // order for the entities (helps avoid deadlock)
        List<? extends Serializable> ordered = StreamSupport.stream(ids.spliterator(), false)
            .filter(Objects::nonNull)
            .sorted()
            .distinct()
            .toList();

        // Fetch the entities from the DB...
        if (ordered.size() > 0) {
            return this.currentSession()
                .byMultipleIds(entityClass)
                .enableOrderedReturn(false)
                .with(new LockOptions(LockMode.PESSIMISTIC_WRITE))
                .multiLoad(ordered);
        }

        return new ArrayList<>();
    }

    /**
     * Creates a new system lock row, attempting to minimize cases where parallel operations attempt
     * to create the same system lock.
     *
     * @param lockName
     *  the name of the system lock to create
     */
    private void createSystemLock(String lockName) {
        String dialect = this.getDatabaseDialect();
        String query = "%s INTO %s VALUES (:lock_name) %s";
        String[] pieces;

        if (dialect.contains("mysql") || dialect.contains("maria")) {
            pieces = new String[] { "INSERT IGNORE", SystemLock.DB_TABLE, "" };
        }
        else if (dialect.contains("postgresql")) {
            pieces = new String[] { "INSERT", SystemLock.DB_TABLE, "ON CONFLICT DO NOTHING" };
        }
        else {
            // Unrecognized dialect, just stick to a basic SQL INSERT and hope for the best.
            // Depending on how the underlying DB handles locks, transaction isolation, and parallel
            // insertions, this may or may not fail; but it'll *probably* fail in this extremely
            // rare case.
            pieces = new String[] { "INSERT", SystemLock.DB_TABLE, "" };
        }

        this.getEntityManager()
            .createNativeQuery(String.format(query, (Object[]) pieces))
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(SystemLock.class)
            .setParameter("lock_name", lockName)
            .executeUpdate();
    }

    /**
     * Obtains a system-level lock for serializing certain critical operations
     *
     * @param lockName
     *  the name of the system lock to obtain
     *
     * @param lockMode
     *  the type of lock to obtain; must be a pessimistic read or write lock
     *
     * @throws IllegalArgumentException
     *  if no lock name is provided, or the provided lock mode is not a pessimistic read or
     *  pessimistic write lock
     */
    public void getSystemLock(String lockName, LockModeType lockMode) {
        if (lockName == null || lockName.isEmpty()) {
            throw new IllegalArgumentException("lockName is null or empty");
        }

        if (!(lockMode == LockModeType.PESSIMISTIC_READ || lockMode == LockModeType.PESSIMISTIC_WRITE)) {
            throw new IllegalArgumentException("Unsupported lock mode: " + lockMode);
        }

        log.trace("Obtaining system lock \"{}\" with lock mode {}...", lockName, lockMode);

        try {
            SystemLock lock = this.getEntityManager()
                .createQuery("SELECT l FROM SystemLock l WHERE l.id = :lock_name", SystemLock.class)
                .setParameter("lock_name", lockName)
                .setLockMode(lockMode)
                .getSingleResult();
        }
        catch (NoResultException e) {
            this.createSystemLock(lockName);
            this.getSystemLock(lockName, lockMode);
        }
    }

    /**
     * Partitions the given collection using the value returned by the getInBlockSize() method as
     * the partition size.
     *
     * @param collection
     *  The collection to partition
     *
     * @return
     *  An iterable collection of lists containing the partitioned data from the provided collection
     */
    protected <T> Iterable<List<T>> partition(Iterable<T> collection) {
        return this.partition(collection, this.getInBlockSize());
    }

    /**
     * Partitions the given collection using the provided block size.
     *
     * @param collection
     *  The collection to partition
     *
     * @param blockSize
     *  The maximum size of the blocks to build when partitioning the collection
     *
     * @return
     *  An iterable collection of lists containing the partitioned data from the provided collection
     */
    protected <T> Iterable<List<T>> partition(Iterable<T> collection, int blockSize) {
        return Iterables.partition(collection, blockSize);
    }

    /**
     * Partitions the given map using the custom partition value
     *
     * @param map
     *  the map to partition
     *
     * @param blockSize
     *  value for the partition size
     *
     * @throws IllegalArgumentException
     *  if the provided map is null
     *
     * @return
     *  An iterable collection of maps containing the partitioned data from the provided map
     */
    protected <K, V> Iterable<Map<K, V>> partitionMap(Map<K, V> map, int blockSize) {
        if (map == null) {
            throw new IllegalArgumentException("map is null");
        }

        List<Map<K, V>> blockList = new LinkedList<>();

        if (map.size() > blockSize) {
            Map<K, V> block = new HashMap<>();
            blockList.add(block);

            for (Map.Entry<K, V> entry : map.entrySet()) {
                block.put(entry.getKey(), entry.getValue());

                if (block.size() >= blockSize) {
                    block = new HashMap<>();
                    blockList.add(block);
                }
            }
        }
        else {
            blockList.add(map);
        }

        return blockList;
    }

    /**
     * Checks if the given collection contains data to be applied to the query and, if so, verifies
     * that the size of the collection is below safe limits for execution.
     *
     * @param collection
     *  the collection to check
     *
     * @throws IllegalArgumentException
     *  if the collection contains more elements than are allowed in a query built from the query
     *  builder
     *
     * @return
     *  true if the collection contains data to be added to the query; false otherwise
     */
    protected boolean checkQueryArgumentCollection(Collection<?> collection) {
        if (collection != null && !collection.isEmpty()) {
            if (collection.size() > QueryArguments.COLLECTION_SIZE_LIMIT) {
                // TODO: Do we have a better exception to throw here?
                throw new IllegalArgumentException("Collection contains too many elements");
            }

            return true;
        }

        return false;
    }

    /**
     * Creates an IN statement predicate based on the provided path and collection of elements from
     * a query argument.
     *
     * @param path
     *  path to create the IN statement predicate for
     *
     * @param collection
     *  the collection of elements for the IN statement
     *
     * @throws IllegalArgumentException
     *  if the number of elements exceeds the limit of elements for a query argument
     *
     * @return an IN statement predicate for the provided path and collection of elements
     */
    protected Optional<Predicate> buildQueryArgumentInPredicate(Path<?> path, Collection<?> collection) {
        if (path == null || collection == null || collection.isEmpty()) {
            return Optional.empty();
        }

        int size = collection.size();
        if (size > QueryArguments.COLLECTION_SIZE_LIMIT) {
            String msg = String.format("In statement collection of size %d exceeds the limit of %d",
                size, QueryArguments.COLLECTION_SIZE_LIMIT);
            throw new IllegalArgumentException(msg);
        }

        return Optional.of(path.in(collection));
    }

    /**
     * Builds a collection of order instances to be used with the JPA criteria query API.
     *
     * @param criteriaBuilder
     *  the CriteriaBuilder instance to use to create order

     * @param root
     *  the root of the query
     *
     * @param queryArguments
     *  a QueryArguments instance containing the ordering information
     *
     * @throws InvalidOrderKeyException
     *  if an order is provided referencing an attribute name (key) that does not exist
     *
     * @return
     *  a list of order instances to sort the query results
     */
    protected List<Order> buildJPAQueryOrder(CriteriaBuilder criteriaBuilder,
        Root<?> root, QueryArguments<?> queryArguments) {

        List<Order> orderList = new ArrayList<>();

        if (queryArguments != null && queryArguments.getOrder() != null) {
            for (QueryArguments.Order order : queryArguments.getOrder()) {
                try {
                    orderList.add(order.reverse() ?
                        criteriaBuilder.desc(root.get(order.column())) :
                        criteriaBuilder.asc(root.get(order.column())));
                }
                catch (IllegalArgumentException e) {
                    String errmsg = String.format("Invalid attribute key: %s", order.column());
                    throw new InvalidOrderKeyException(errmsg, root.getModel());
                }
            }
        }

        return orderList;
    }
}
