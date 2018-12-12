/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.ConcurrentModificationException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.guice.PrincipalProvider;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.SQLQuery;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.internal.SessionImpl;
import org.hibernate.jpa.AvailableSettings;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.transform.ResultTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.persistence.CacheRetrieveMode;
import javax.persistence.CacheStoreMode;
import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.NonUniqueResultException;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;



/**
 * AbstractHibernateCurator base class for all Candlepin curators. Curators are
 * the applications database layer, typically following the pattern of one
 * curator for each model type. This class contains methods common to all
 * curators.
 * @param <E> Entity specific curator.
 */
public abstract class AbstractHibernateCurator<E extends Persisted> {
    @Inject protected CandlepinQueryFactory cpQueryFactory;
    @Inject protected Provider<EntityManager> entityManager;
    @Inject protected Provider<I18n> i18nProvider;
    @Inject protected Configuration config;
    private final Class<E> entityType;

    @Inject private PrincipalProvider principalProvider;
    private static Logger log = LoggerFactory.getLogger(AbstractHibernateCurator.class);

    public AbstractHibernateCurator(Class<E> entityType) {
        //entityType = (Class<E>) ((ParameterizedType)
        //getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.entityType = entityType;
    }

    public Class<E> entityType() {
        return entityType;
    }

    public int getInBlockSize() {
        return config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
    }

    public int getCaseBlockSize() {
        return config.getInt(DatabaseConfigFactory.CASE_OPERATOR_BLOCK_SIZE);
    }

    public int getBatchBlockSize() {
        return config.getInt(DatabaseConfigFactory.BATCH_BLOCK_SIZE);
    }

    public int getQueryParameterLimit() {
        return config.getInt(DatabaseConfigFactory.QUERY_PARAMETER_LIMIT);
    }

    /**
     * Get one or zero items.  Thanks http://stackoverflow.com/a/6378045/6124862
     * @param query
     * @param <E>
     * @return one and only one object of type E
     */
    public <E> E getSingleResult(TypedQuery<E> query) {
        List<E> list = query.getResultList();
        if (list.isEmpty()) {
            return null;
        }
        else if (list.size() == 1) {
            return list.get(0);
        }
        throw new NonUniqueResultException();
    }

    public void enableFilter(String filterName, String parameterName, Object value) {
        currentSession().enableFilter(filterName).setParameter(parameterName, value);
    }

    public void enableFilterList(String filterName, String parameterName,
        Collection value) {
        currentSession().enableFilter(filterName).setParameterList(parameterName, value);
    }

    /**
     * @param id db id of entity to be found.
     * @return entity matching given id, or null otherwise.
     */
    @Transactional
    public E find(Serializable id) {
        return id == null ? null : get(entityType, id);
    }

    /**
     * Same as {@link find} but allows permissions on the current principal to inject
     * filters into the query before it is run. Primarily useful in authentication when
     * we want to verify access to an entity specified in the URL, but not reveal if
     * the entity exists or not if you don't have permissions to see it at all.
     *
     * @param id db id of entity to be found.
     * @return entity matching given id, or null otherwise.
     */
    @Transactional
    public E secureFind(Serializable id) {
        return id == null ? null : secureGet(entityType, id);
    }

    /**
     * @param entity to be created.
     * @return newly created entity
     */
    @Transactional
    public E create(E entity) {
        return create(entity, true);
    }

    /**
     * @param entity to be created.
     * @param flush whether or not to flush after the persist.
     * @return newly created entity.
     */
    @Transactional
    public E create(E entity, boolean flush) {
        getEntityManager().persist(entity);
        if (flush) {
            flush();
        }
        return entity;
    }

    /**
     * @return all entities for a particular type.
     */
    public CandlepinQuery<E> listAll() {
        DetachedCriteria criteria = this.createSecureDetachedCriteria();

        return this.cpQueryFactory.<E>buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<E> listAllByIds(Collection<? extends Serializable> ids) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(CPRestrictions.in("id", ids));

        return this.cpQueryFactory.<E>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Page<List<E>> listAll(PageRequest pageRequest, boolean postFilter) {
        Page<List<E>> resultsPage;
        if (postFilter) {
            // Create a copy of the page request with just the order and sort by values.
            // Since we are filtering after the results are returned, we don't want
            // to send the page or page size values in.
            PageRequest orderAndSortByPageRequest = null;
            if (pageRequest != null) {
                orderAndSortByPageRequest = new PageRequest();
                orderAndSortByPageRequest.setOrder(pageRequest.getOrder());
                orderAndSortByPageRequest.setSortBy(pageRequest.getSortBy());
            }

            resultsPage = listAll(orderAndSortByPageRequest);

            // Set the pageRequest to the correct object here.
            resultsPage.setPageRequest(pageRequest);
        }
        else {
            resultsPage = listAll(pageRequest);
        }
        return resultsPage;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Page<List<E>> listAll(PageRequest pageRequest) {
        Page<List<E>> page = new Page<>();

        if (pageRequest != null) {
            Criteria count = createSecureCriteria();
            page.setMaxRecords(findRowCount(count));

            Criteria c = createSecureCriteria();
            page.setPageData(loadPageData(c, pageRequest));
            page.setPageRequest(pageRequest);
        }
        else {
            List<E> pageData = this.listAll().list();
            page.setMaxRecords(pageData.size());
            page.setPageData(pageData);
        }

        return page;
    }

    @SuppressWarnings("unchecked")
    private List<E> loadPageData(Criteria c, PageRequest pageRequest) {
        c.addOrder(createPagingOrder(pageRequest));

        if (pageRequest.isPaging()) {
            c.setFirstResult((pageRequest.getPage() - 1) * pageRequest.getPerPage());
            c.setMaxResults(pageRequest.getPerPage());
        }

        return c.list();
    }

    private Order createPagingOrder(PageRequest p) {
        String sortBy = (p.getSortBy() == null) ? AbstractHibernateObject.DEFAULT_SORT_FIELD : p.getSortBy();
        PageRequest.Order order = (p.getOrder() == null) ? PageRequest.DEFAULT_ORDER : p.getOrder();

        switch (order) {
            case ASCENDING:
                return Order.asc(sortBy);

            //DESCENDING
            default:
                return Order.desc(sortBy);
        }
    }

    private Integer findRowCount(Criteria c) {
        c.setProjection(Projections.rowCount());
        return ((Long) c.uniqueResult()).intValue();
    }

    @SuppressWarnings("unchecked")
    public Page<ResultIterator<E>> paginateResults(CandlepinQuery<E> query, PageRequest pageRequest) {
        Page<ResultIterator<E>> page = new Page<>();

        if (pageRequest != null) {
            page.setMaxRecords(query.getRowCount());

            query.addOrder(this.createPagingOrder(pageRequest));
            if (pageRequest.isPaging()) {
                query.setFirstResult((pageRequest.getPage() - 1) * pageRequest.getPerPage());
                query.setMaxResults(pageRequest.getPerPage());
            }

            page.setPageRequest(pageRequest);
        }

        page.setPageData(query.iterate());
        return page;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<E> listByCriteria(Criteria query) {
        return query.list();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Page<List<E>> listByCriteria(Criteria query, PageRequest pageRequest, boolean postFilter) {
        Page<List<E>> resultsPage;
        if (postFilter) {
            // Create a copy of the page request with just the order and sort by values.
            // Since we are filtering after the results are returned, we don't want
            // to send the page or page size values in.
            PageRequest orderAndSortByPageRequest = null;
            if (pageRequest != null) {
                orderAndSortByPageRequest = new PageRequest();
                orderAndSortByPageRequest.setOrder(pageRequest.getOrder());
                orderAndSortByPageRequest.setSortBy(pageRequest.getSortBy());
            }

            resultsPage = listByCriteria(query, orderAndSortByPageRequest);

            // Set the pageRequest to the correct object here.
            resultsPage.setPageRequest(pageRequest);
        }
        else {
            resultsPage = listByCriteria(query, pageRequest);
        }

        return resultsPage;
    }

    /**
     * Gives the permissions a chance to add aliases and then restrictions to the query.
     * Uses an "or" so a principal could carry permissions for multiple owners
     * (for example), but still have their results filtered without one of the perms
     * hiding the results from the other.
     *
     * @return Criteria Final criteria query with all filters applied.
     */
    protected Criteria createSecureCriteria() {
        return this.createSecureCriteria(null);
    }

    /**
     * Gives the permissions a chance to add aliases and then restrictions to the query.
     * Uses an "or" so a principal could carry permissions for multiple owners
     * (for example), but still have their results filtered without one of the perms
     * hiding the results from the other.
     *
     * @param alias
     *  The alias to assign to the entity managed by the resultant Criteria.
     *
     * @return Criteria Final criteria query with all filters applied.
     */
    protected Criteria createSecureCriteria(String alias) {
        return this.createSecureCriteria(this.entityType, alias);
    }

    /**
     * Creates a "secure" criteria for the given entity class. The criteria object returned will
     * have zero or more restrictions applied to the entity class based on the current principal's
     * permissions.
     *
     * @param entityClass
     *  The class of entity to be retrieved and restricted by the generated criteria
     *
     * @param alias
     *  The alias to assign to the root entity; ignored if null
     *
     * @return
     *  a new Criteria instance with any applicable entity restrictions
     */
    protected Criteria createSecureCriteria(Class entityClass, String alias) {
        Criteria criteria = (alias != null && alias.length() > 0) ?
            this.currentSession().createCriteria(entityClass, alias) :
            this.currentSession().createCriteria(entityClass);

        Criterion restrictions = this.getSecureCriteriaRestrictions(entityClass);

        if (restrictions != null) {
            criteria.add(restrictions);
        }

        return criteria;
    }

    /**
     * Creates a detached criteria object to use as the basis of a permission-oriented entity
     * lookup query.
     *
     * @return
     *  a detached criteria object containing permission restrictions
     */
    protected DetachedCriteria createSecureDetachedCriteria() {
        return this.createSecureDetachedCriteria(null);
    }

    /**
     * Creates a detached criteria object to use as the basis of a permission-oriented entity
     * lookup query.
     *
     * @param alias
     *  The alias to use for the main entity, or null to omit an alias
     *
     * @return
     *  a detached criteria object containing permission restrictions
     */
    protected DetachedCriteria createSecureDetachedCriteria(String alias) {
        return this.createSecureDetachedCriteria(this.entityType, null);
    }

    /**
     * Creates a detached criteria object to use as the basis of a permission-oriented entity
     * lookup query.
     *
     * @param entityClass
     *  The class of entity to be retrieved and restricted by the generated criteria
     *
     * @param alias
     *  The alias to assign to the root entity; ignored if null
     *
     * @return
     *  a detached criteria object containing permission restrictions
     */
    protected DetachedCriteria createSecureDetachedCriteria(Class entityClass, String alias) {
        DetachedCriteria criteria = (alias != null && !alias.equals("")) ?
            DetachedCriteria.forClass(entityClass, alias) :
            DetachedCriteria.forClass(entityClass);

        Criterion restrictions = this.getSecureCriteriaRestrictions(entityClass);

        if (restrictions != null) {
            criteria.add(restrictions);
        }

        return criteria;
    }

    /**
     * Builds the criteria restrictions for the given entity class. If the entity does not need any
     * restrictions or the current principal otherwise has full access, this method returns null.
     *
     * @param entityClass
     *  The entity class for which to build secure criteria restrictions
     *
     * @return
     *  the criteria restrictions for the given entity class, or null if no restrictions are
     *  necessary.
     */
    protected Criterion getSecureCriteriaRestrictions(Class entityClass) {
        Principal principal = this.principalProvider.get();
        Criterion restrictions = null;

        // If we do not yet have a principal (during authentication) or the principal has full
        // access, skip the restriction building
        if (principal != null && !principal.hasFullAccess()) {
            for (Permission permission : principal.getPermissions()) {
                Criterion restriction = permission.getCriteriaRestrictions(entityClass);

                if (restriction != null) {
                    log.debug("Adding criteria restriction from permission {} for {}: {}",
                        permission, entityClass, restriction);

                    restrictions = (restrictions != null) ?
                        Restrictions.or(restrictions, restriction) : restriction;
                }
            }
        }

        return restrictions;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Page<List<E>> listByCriteria(Criteria c, PageRequest pageRequest) {
        Page<List<E>> page = new Page<>();

        if (pageRequest != null) {
            // see https://forum.hibernate.org/viewtopic.php?t=974802

            // Save original Projection and ResultTransformer
            CriteriaImpl cImpl = (CriteriaImpl) c;
            Projection origProjection = cImpl.getProjection();
            ResultTransformer origRt = cImpl.getResultTransformer();

            // Get total number of records by setting a rowCount projection
            page.setMaxRecords(findRowCount(c));

            // Restore original Projection and ResultTransformer
            c.setProjection(origProjection);
            c.setResultTransformer(origRt);

            page.setPageData(loadPageData(c, pageRequest));
            page.setPageRequest(pageRequest);
        }
        else {
            List<E> pageData = listByCriteria(c);
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
        E toDelete = find(entity.getId());
        currentSession().delete(toDelete);
    }

    public void bulkDelete(Collection<E> entities) {
        for (E entity : entities) {
            delete(entity);
        }
    }

    @Transactional
    public void bulkDeleteTransactional(Collection<E> entities) {
        this.bulkDelete(entities);
    }

    /**
     * @param entity entity to be merged.
     * @return merged entity.
     */
    @Transactional
    public E merge(E entity) {
        return getEntityManager().merge(entity);
    }

    @Transactional
    protected final <T> T secureGet(Class<T> clazz, Serializable id) {
        return clazz.cast(createSecureCriteria().add(Restrictions.idEq(id)).uniqueResult());
    }

    @Transactional
    protected <T> T get(Class<T> clazz, Serializable id) {
        return this.currentSession().get(clazz, id);
    }

    @Transactional
    protected void save(E anObject) {
        create(anObject, true);
    }

    public void flush() {
        try {
            getEntityManager().flush();
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
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }

    public Session openSession() {
        SessionFactory factory = this.currentSession().getSessionFactory();
        return factory.openSession();
    }

    public EntityManager getEntityManager() {
        return entityManager.get();
    }

    public Collection<E> saveAll(Collection<E> entities, boolean flush, boolean evict) {
        if (entities != null && !entities.isEmpty()) {
            try {
                Session session = this.currentSession();
                EntityManager em = this.getEntityManager();
                Iterable<List<E>> blocks = Iterables.partition(entities, getBatchBlockSize());

                for (List<E> block : blocks) {
                    for (E entity : block) {
                        session.save(entity);
                    }

                    if (flush) {
                        em.flush();

                        if (evict) {
                            for (E entity : block) {
                                session.evict(entity);
                            }
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

    public Collection<E> updateAll(Collection<E> entities, boolean flush, boolean evict) {
        if (entities != null && !entities.isEmpty()) {
            try {
                Session session = this.currentSession();
                EntityManager em = this.getEntityManager();
                Iterable<List<E>> blocks = Iterables.partition(entities, getBatchBlockSize());

                for (List<E> block : blocks) {
                    for (E entity : block) {
                        session.update(entity);
                    }

                    if (flush) {
                        em.flush();

                        if (evict) {
                            for (E entity : block) {
                                session.evict(entity);
                            }
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

    public Iterable<E> saveOrUpdateAll(Iterable<E> entities, boolean flush, boolean evict) {
        if (entities != null) {
            try {
                Session session = this.currentSession();
                EntityManager em = this.getEntityManager();
                Iterable<List<E>> blocks = Iterables.partition(entities, getBatchBlockSize());

                for (List<E> block : blocks) {
                    for (E entity : block) {
                        session.saveOrUpdate(entity);
                    }

                    if (flush) {
                        em.flush();

                        if (evict) {
                            for (E entity : block) {
                                session.evict(entity);
                            }
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

    public Collection<E> mergeAll(Collection<E> entities, boolean flush) {
        if (entities != null && !entities.isEmpty()) {
            try {
                Session session = this.currentSession();
                EntityManager em = this.getEntityManager();

                if (flush) {
                    int i = 0;
                    for (E entity : entities) {
                        session.merge(entity);

                        if (++i % getBatchBlockSize() == 0) {
                            em.flush();
                            session.clear();
                        }
                    }

                    if (i % getBatchBlockSize() != 0) {
                        em.flush();
                        session.clear();
                    }
                }
                else {
                    for (E entity : entities) {
                        session.merge(entity);
                    }
                }
            }
            catch (OptimisticLockException e) {
                throw new ConcurrentModificationException(getConcurrentModificationMessage(), e);
            }
        }

        return entities;
    }

    public void refresh(E... entities) {
        if (entities != null) {
            EntityManager manager = this.getEntityManager();

            for (E entity : entities) {
                manager.refresh(entity);
            }
        }
    }

    public E evict(E entity) {
        this.currentSession().evict(entity);
        return entity;
    }

    /**
     * Evicts all of the given entities from the level-one cache.
     *
     * @param collection
     *  An iterable collection of entities to evict from the session
     */
    public void evictAll(Iterable<E> collection) {
        Session session = this.currentSession();

        for (E entity : collection) {
            session.evict(entity);
        }
    }

    public List<E> takeSubList(PageRequest pageRequest, List<E> results) {
        int fromIndex = (pageRequest.getPage() - 1) * pageRequest.getPerPage();
        if (fromIndex >= results.size()) {
            return new ArrayList<>();
        }

        int toIndex = fromIndex + pageRequest.getPerPage();
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
     * Performs a direct SQL update or delete operation with a collection by breaking the collection
     * into chunks and repeatedly performing the update.
     * <p></p>
     * The parameter receiving the collection chunks must be the last parameter in the query and the
     * provided collection must support the subList operation.
     *
     * @param sql
     *  The SQL statement to execute; must be an UPDATE or DELETE operation
     *
     * @param collection
     *  The collection to be broken up into chunks
     *
     * @return
     *  the number of rows updated as a result of this query
     */
    protected int safeSQLUpdateWithCollection(String sql, Collection<?> collection, Object... params) {
        int count = 0;

        Session session = this.currentSession();
        SQLQuery query = session.createSQLQuery(sql);

        for (List<?> block : this.partition(collection)) {
            int index = 1;

            if (params != null) {
                for (; index <= params.length; ++index) {
                    query.setParameter(String.valueOf(index), params[index - 1]);
                }
            }
            query.setParameterList(String.valueOf(index), block);

            count += query.executeUpdate();
        }

        return count;
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
     * Refreshes/reloads the given entity and locks it using a pessimistic write lock.
     *
     * @param entity
     *  The entity to lock and load
     *
     * @return
     *  The locked entity
     */
    public E lockAndLoad(E entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        // Pull the entity's metadata and identifier, just in case we were passed a detached
        // entity or some such.
        SessionImpl session = (SessionImpl) this.currentSession();
        ClassMetadata metadata = session.getSessionFactory().getClassMetadata(this.entityType);

        if (metadata == null || !metadata.hasIdentifierProperty()) {
            throw new UnsupportedOperationException(
                "lockAndLoad only supports entities with database identifiers");
        }

        return this.lockAndLoadById(this.entityType, metadata.getIdentifier(entity, session));
    }

    /**
     * Locks the given collection of entities, reloading them as necessary.
     *
     * @param entities
     *  A collection of entities to lock
     *
     * @throws RuntimeException
     *  If this method is called for a curator handling an entity type that is not a subclass of
     *  the AbstractHibernateObject class.
     *
     * @return
     *  The collection of locked entities
     */
    public Collection<E> lockAndLoad(Iterable<E> entities) {
        // Impl note:
        // We're going to take advantage of some blackbox knowledge of how LockAndLoadByIds works to
        // minimize the amount of extra loops we need to do. We can pass a custom iterable which
        // fetches the entity's ID on the call to "next" and pass that through to LockAndLoadByIds.

        if (entities == null) {
            return Collections.<E>emptyList();
        }

        // We redeclare the collection here so we don't require the final modifier in subclass
        // definitions
        final Iterable<E> entityCollection = entities;
        final SessionImpl session = (SessionImpl) this.currentSession();
        final ClassMetadata metadata = session.getSessionFactory().getClassMetadata(this.entityType);

        if (metadata == null || !metadata.hasIdentifierProperty()) {
            throw new UnsupportedOperationException(
                "lockAndLoad only supports entities with database identifiers");
        }

        Iterable<Serializable> iterable = new Iterable<Serializable>() {
            @Override
            public Iterator<Serializable> iterator() {
                return new Iterator<Serializable>() {
                    private Iterator<E> entityIterator;

                    /* initializer */ {
                        this.entityIterator = entityCollection.iterator();
                    }

                    @Override
                    public boolean hasNext() {
                        return this.entityIterator.hasNext();
                    }

                    @Override
                    public Serializable next() {
                        E next = this.entityIterator.next();
                        return next != null ? metadata.getIdentifier(next, session) : null;
                    }

                    @Override
                    public void remove() {
                        this.entityIterator.remove();
                    }
                };
            }
        };

        return this.lockAndLoadByIds(this.entityType, iterable);
    }

    /**
     * Loads an entity with a pessimistic lock using the specified ID. If the entity has already
     * been loaded, it will be refreshed with the lock instead. If a  matching entity could not be
     * found, this method returns null.
     *
     * @param id
     *  The id of the entity to load/refresh and lock
     *
     * @return
     *  A locked entity instance, or null if a matching entity could not be found
     */
    public E lockAndLoadById(Serializable id) {
        return this.lockAndLoadById(this.entityType, id);
    }

    /**
     * Loads an entity with a pessimistic lock using the specified entity class and ID. If the
     * entity has already been loaded, it will be refreshed with the lock instead. If a  matching
     * entity could not be found, this method returns null.
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
    @SuppressWarnings("unchecked")
    protected E lockAndLoadById(Class<E> entityClass, Serializable id) {
        EntityManager entityManager = this.getEntityManager();
        SessionImpl session = (SessionImpl) this.currentSession();
        ClassMetadata metadata = session.getFactory().getClassMetadata(entityClass);

        // Get the entity's metadata so we can ask Hibernate for the name of its identifier
        // and check if it's already in the session cache without doing a database lookup
        if (metadata == null || !metadata.hasIdentifierProperty()) {
            throw new UnsupportedOperationException(
                "lockAndLoad only supports entities with database identifiers");
        }

        // Fetch the entity persister and session context so we can check the session cache for an
        // entity before hitting the database.
        EntityPersister persister = session.getFactory().getEntityPersister(metadata.getEntityName());
        PersistenceContext context = session.getPersistenceContext();

        // Lookup whether or not we have an entity with a given ID in the current session's cache.
        // See the notes in lockAndLoadByIds for details as to why we're going about it this way.
        EntityKey key = session.generateEntityKey(id, persister);
        E entity = (E) context.getEntity(key);

        if (entity == null) {
            // The entity isn't in the local session, we'll need to query for it

            String idName = metadata.getIdentifierPropertyName();
            if (idName == null) {
                // This shouldn't happen.
                throw new RuntimeException("Unable to fetch identifier property name");
            }

            // Impl note:
            // We're building the query here using JPA Criteria to avoid fiddling with string
            // building and, potentially, erroneously using the class name as the entity name.
            // Additionally, using a query (as opposed to the .find and .load methods) lets us set
            // the flush, cache and lock modes for the entity we're attempting to fetch.
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<E> query = builder.createQuery(entityClass);
            Root<E> root = query.from(entityClass);
            Path<Serializable> target = root.<Serializable>get(idName);
            ParameterExpression<Serializable> param = builder.parameter(Serializable.class);

            query.select(root).where(builder.equal(target, param));

            // Note that it's critical here to set both modes, as Hibernate is wildly inconsistent
            // (and non-standard) in which properties it actually accepts when processing its own
            // config objects. The cache mode combination specified below ends up being evaluated
            // by Hibernate down to a CacheMode.REFRESH.
            try {
                entity = entityManager.createQuery(query)
                    .setFlushMode(FlushModeType.COMMIT)
                    .setHint(AvailableSettings.SHARED_CACHE_RETRIEVE_MODE, CacheRetrieveMode.BYPASS)
                    .setHint(AvailableSettings.SHARED_CACHE_STORE_MODE, CacheStoreMode.REFRESH)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setParameter(param, id)
                    .getSingleResult();
            }
            catch (NoResultException exception) {
                // No entity found matching the ID. We don't define this as an error case, so we're
                // going to silently discard the exception.
            }
        }
        else {
            // It's already available locally. Issue a refresh with a lock.
            entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        }

        return entity;
    }

    /**
     * Loads the entities represented by the given IDs with a pessimistic write lock. If no
     * entities were found with the given IDs, this method returns an empty collection.
     *
     * @param ids
     *  A collection of entity IDs to use to load and lock the represented entities
     *
     * @return
     *  A collection of locked entities represented by the given IDs
     */
    public Collection<E> lockAndLoadByIds(Iterable<? extends Serializable> ids) {
        return this.lockAndLoadByIds(this.entityType, ids);
    }

    /**
     * Loads a collection of entities with a pessimistic lock using the specified entity class
     * and collection of IDs. If no entities could be found matching the given IDs, this method
     * returns an empty collection. Note that this method makes no attempt to ensure that an entity
     * is loaded for every ID provided. It is possible for the output collection to be smaller than
     * the provided set of IDs.
     * <p></p>
     * Depending on the session state when this method is called, this method may perform a refresh
     * on each entity individually rather than performing a bulk lookup. This is due to a current
     * limitation in Hibernate that forces use of the L1 cache when executing a query. To avoid
     * this bottleneck, before calling this method, the caller should either evict the target
     * entities from the session -- using session.evict or session.clear -- or use this method to
     * perform the initial lookup straight away. Entities which are not already in the session
     * cache will be fetched and locked in bulk, rather than refreshed and locked individually.
     *
     * @param entityClass
     *  The class representing the type of the entity to load (i.e. Pool.class or Product.class)
     *
     * @param ids
     *  A collection of IDs to use to load
     *
     * @return
     *  A collection of locked entities matching the given values
     */
    protected Collection<E> lockAndLoadByIds(Class<E> entityClass, Iterable<? extends Serializable> ids) {
        // The lockAndLoadById(s) methods work in two separate stages. The first stage determines
        // whether or not an entity associated with a given ID is present in Hibernate's L1 cache.
        // If it is, we need to fetch it from the cache and perform an explicit refresh operation
        // for that entity. Otherwise, if it is not present, we can do a normal(ish) query to fetch
        // it, and any other non-present entities.
        //
        // Unfortunately, there isn't a single, concise method for performing such a lookup.
        // Instead, we need to check with the persistence context and determine whether or not it
        // has an entity associated with a given entity key, which we generate using the session
        // and entity persister. It's convoluted, but necessary to get consistently correct
        // behavior from these methods.
        List<E> result = new ArrayList<>();

        if (ids != null && ids.iterator().hasNext()) {
            EntityManager entityManager = this.getEntityManager();
            SessionImpl session = (SessionImpl) this.currentSession();
            ClassMetadata metadata = session.getFactory().getClassMetadata(entityClass);

            if (metadata == null || !metadata.hasIdentifierProperty()) {
                throw new UnsupportedOperationException(
                    "lockAndLoad only supports entities with database identifiers");
            }

            EntityPersister persister = session.getFactory().getEntityPersister(metadata.getEntityName());
            PersistenceContext context = session.getPersistenceContext();

            SortedSet<Serializable> idSet = new TreeSet<>();
            SortedMap<Serializable, E> entitySet = new TreeMap<>();

            // Step through the collection of IDs and figure out which entities we have to refresh,
            // and which we need to lookup.
            for (Serializable id : ids) {
                // Make sure we don't doubly load/lock anything
                if (id != null && !idSet.contains(id) && !entitySet.containsKey(id)) {
                    EntityKey key = session.generateEntityKey(id, persister);
                    E entity = (E) context.getEntity(key);

                    if (entity != null) {
                        entitySet.put(id, entity);
                    }
                    else {
                        idSet.add(id);
                    }
                }
            }

            // First address the slow (and hopefully smaller) part of the lookup and refresh the
            // entities that exist

            // TODO: Maybe add a debug warning here to call out situations where our existing
            // entity size is larger than our absent entity size. Those are areas where we may be
            // reloading entities unnecessarily and could be optimized to avoid doing extraneous
            // work.
            if (entitySet.size() > 0) {
                for (E entity : entitySet.values()) {
                    entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
                    result.add(entity);
                }
            }

            // Build a query to fetch the remaining entities
            if (idSet.size() > 0) {
                // Get the entity's metadata so we can ask Hibernate for the name of its identifier
                String idName = metadata.getIdentifierPropertyName();
                if (idName == null) {
                    // This shouldn't happen.
                    throw new RuntimeException("Unable to fetch identifier property name");
                }

                // Impl note:
                // We're building the query here using JPA Criteria to avoid fiddling with string
                // building and, potentially, erroneously using the class name as the entity name.
                // Additionally, using a query (as opposed to the .find and .load methods) lets us set
                // the flush, cache and lock modes for the entity we're attempting to fetch.
                CriteriaBuilder builder = entityManager.getCriteriaBuilder();
                CriteriaQuery<E> query = builder.createQuery(entityClass);
                Root<E> root = query.from(entityClass);
                Path<Serializable> target = root.<Serializable>get(idName);
                ParameterExpression<List> param = builder.parameter(List.class);

                query.select(root).where(target.in(param));

                // Note that it's critical here to set both modes, as Hibernate is wildly inconsistent
                // (and non-standard) in which properties it actually accepts when processing its own
                // config objects. The cache mode combination specified below ends up being evaluated
                // by Hibernate down to a CacheMode.REFRESH.
                TypedQuery<E> executable = entityManager.createQuery(query)
                    .setFlushMode(FlushModeType.COMMIT)
                    .setHint(AvailableSettings.SHARED_CACHE_RETRIEVE_MODE, CacheRetrieveMode.BYPASS)
                    .setHint(AvailableSettings.SHARED_CACHE_STORE_MODE, CacheStoreMode.REFRESH)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE);

                // Step through the query in blocks
                for (List<Serializable> block : Iterables.partition(idSet, getBatchBlockSize())) {
                    executable.setParameter(param, block);
                    result.addAll(executable.getResultList());
                }
            }
        }

        // Should we be returning a view of the list, rather than the fully mutable list here?
        return result;
    }

    /**
     * Performs a bulk update on the given table/column, setting the values from the keys of the
     * provided map to the map's values.
     * <p></p>
     * <strong>Note:</strong> It's important to note that every row selected by the given criteria
     * will be updated and included in the update count, even if the value does not change. If the
     * number of actual changes made is significant to the caller, the criteria should also include
     * the keySet from the given values.
     *
     * @param table
     *  The name of the table to update
     *
     * @param column
     *  The name of the column to update
     *
     * @param values
     *  A mapping of values to apply to the table (current => new)
     *
     * @param criteria
     *  A mapping of criteria to apply to the update (column name => value); applied as a
     *  conjunction.
     *
     * @return
     *  the number of rows updated as a result of this query
     */
    protected int bulkSQLUpdate(String table, String column, Map<Object, Object> values,
        Map<String, Object> criteria) {

        if (values == null || values.isEmpty()) {
            return 0;
        }

        Iterable<List<Map.Entry<Object, Object>>> blocks = Iterables.partition(values.entrySet(),
            getCaseBlockSize());

        int count = 0;
        int lastBlock = -1;

        Session session = this.currentSession();
        SQLQuery query = null;

        for (List<Map.Entry<Object, Object>> block : blocks) {
            if (block.size() != lastBlock) {
                // Rebuild update block
                int param = 0;
                boolean whereStarted = false;

                StringBuilder builder = new StringBuilder("UPDATE ").append(table).append(" SET ")
                    .append(column).append(" = ");

                // Note: block.size() should never be zero here.
                if (block.size() > 1) {
                    builder.append("CASE");

                    for (int i = 0; i < block.size(); ++i) {
                        builder.append(" WHEN ").append(column).append(" = ?").append(++param)
                            .append(" THEN ?").append(++param);
                    }

                    builder.append(" ELSE ").append(column).append(" END ");
                }
                else {
                    builder.append('?').append(++param).append(" WHERE ").append(column).append(" = ?")
                        .append(++param).append(' ');

                    whereStarted = true;
                }

                // Add criteria
                if (criteria != null && !criteria.isEmpty()) {
                    for (Map.Entry<String, Object> criterion : criteria.entrySet()) {
                        if (criterion.getValue() instanceof Collection) {
                            if (((Collection) criterion.getValue()).size() > 0) {
                                int inBlocks = (int) Math.ceil((((Collection) criterion.getValue()).size() /
                                    (float) getInBlockSize()));

                                builder.append(whereStarted ? " AND " : " WHERE ");
                                whereStarted = true;

                                if (inBlocks > 1) {
                                    builder.append('(');

                                    for (int i = 0; i < inBlocks; ++i) {
                                        if (i != 0) {
                                            builder.append(" OR ");
                                        }

                                        builder.append(criterion.getKey()).append(" IN (?").append(++param)
                                            .append(')');
                                    }

                                    builder.append(')');
                                }
                                else {
                                    builder.append(criterion.getKey()).append(" IN (?").append(++param)
                                        .append(')');
                                }
                            }
                        }
                        else {
                            builder.append(whereStarted ? " AND " : " WHERE ");
                            whereStarted = true;

                            builder.append(criterion.getKey()).append(" = ?").append(++param);
                        }
                    }
                }

                query = session.createSQLQuery(builder.toString());
            }

            // Set params
            int param = 0;

            if (block.size() > 1) {
                for (Map.Entry<Object, Object> entry : block) {
                    query.setParameter(String.valueOf(++param), entry.getKey())
                        .setParameter(String.valueOf(++param), entry.getValue());
                }
            }
            else {
                Map.Entry<Object, Object> entry = block.get(0);

                query.setParameter(String.valueOf(++param), entry.getValue())
                    .setParameter(String.valueOf(++param), entry.getKey());
            }

            // Set criteria if the block size has changed
            if (block.size() != lastBlock && criteria != null && !criteria.isEmpty()) {
                for (Object criterion : criteria.values()) {
                    if (criterion instanceof Collection) {
                        Iterable<List> inBlocks = this.partition((Collection) criterion);

                        for (List inBlock : inBlocks) {
                            query.setParameterList(String.valueOf(++param), inBlock);
                        }
                    }
                    else {
                        query.setParameter(String.valueOf(++param), criterion);
                    }
                }
            }

            int blockUpdates = query.executeUpdate();

            // Impl note:
            // Since our criteria does not change between queries, the number of rows we update
            // should be consistent between executions. This being the case, our count will not
            // be the cumulative update count, but the max count of any one of the queries
            // executed. This looks odd, admittedly, but it's correct.
            if (count == 0) {
                count = blockUpdates;
            }

            lastBlock = block.size();
        }

        return count;
    }

    /**
     * Performs an SQL delete on the given table, using the given criteria map to filter rows to
     * delete.
     *
     * @param table
     *  The name of the table to update
     *
     * @param criteria
     *  A mapping of criteria to apply to the deletion (column name => value); applied as a
     *  conjunction.
     *
     * @return
     *  the number of rows deleted as a result of this query
     */
    protected int bulkSQLDelete(String table, Map<String, Object> criteria) {
        StringBuilder builder = new StringBuilder("DELETE FROM ").append(table);

        // Add criteria
        if (criteria != null && !criteria.isEmpty()) {
            boolean whereStarted = false;
            int param = 0;

            for (Map.Entry<String, Object> criterion : criteria.entrySet()) {
                if (criterion.getValue() instanceof Collection) {
                    if (((Collection) criterion.getValue()).size() > 0) {
                        int inBlocks = (int) Math.ceil((((Collection) criterion.getValue()).size() /
                            (float) getInBlockSize()));

                        builder.append(whereStarted ? " AND " : " WHERE ");
                        whereStarted = true;

                        if (inBlocks > 1) {
                            builder.append('(');

                            for (int i = 0; i < inBlocks; ++i) {
                                if (i != 0) {
                                    builder.append(" OR ");
                                }

                                builder.append(criterion.getKey()).append(" IN (?").append(++param)
                                    .append(')');
                            }

                            builder.append(')');
                        }
                        else {
                            builder.append(criterion.getKey()).append(" IN (?").append(++param) .append(')');
                        }
                    }
                }
                else {
                    builder.append(whereStarted ? " AND " : " WHERE ");
                    whereStarted = true;

                    builder.append(criterion.getKey()).append(" = ?").append(++param);
                }
            }
        }

        SQLQuery query = this.currentSession().createSQLQuery(builder.toString());

        if (criteria != null && !criteria.isEmpty()) {
            int param = 0;

            for (Object criterion : criteria.values()) {
                if (criterion instanceof Collection) {
                    Iterable<List> inBlocks = this.partition((Collection) criterion);

                    for (List inBlock : inBlocks) {
                        query.setParameterList(String.valueOf(++param), inBlock);
                    }
                }
                else {
                    query.setParameter(String.valueOf(++param), criterion);
                }
            }
        }

        return query.executeUpdate();
    }

    /**
     * Partitions the given collection using the value returned by the getInBlockSize() method as
     * the partition size. This method is provided as a utility method to avoid referencing a very
     * long constant name used in many curators. Callers which need this behavior with a custom
     * size can simulate the behavior by using the <tt>Iterables.partition</tt> method directly:
     * <pre>
     *  Iterable<List<String>> blocks = ', 'entityIds, blockSize);
     * </pre>
     *
     * @param collection
     *  The collection to partition
     *
     * @return
     *  An iterable collection of lists containing the partitioned data in the provided collection
     */
    protected <T> Iterable<List<T>> partition(Iterable<T> collection) {
        return Iterables.partition(collection, this.getInBlockSize());
    }
}
