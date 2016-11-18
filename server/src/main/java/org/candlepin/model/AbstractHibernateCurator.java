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
import org.candlepin.common.exceptions.ConcurrentModificationException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.guice.PrincipalProvider;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.apache.commons.collections.CollectionUtils;
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
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.transform.ResultTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;



/**
 * AbstractHibernateCurator base class for all Candlepin curators. Curators are
 * the applications database layer, typically following the pattern of one
 * curator for each model type. This class contains methods common to all
 * curators.
 * @param <E> Entity specific curator.
 */
public abstract class AbstractHibernateCurator<E extends Persisted> {
    // Oracle has a limit of 1000
    public static final int IN_OPERATOR_BLOCK_SIZE = 999;
    // Oracle has a limit of 255 arguments per CASE, which caps us around 100 entries.
    public static final int CASE_OPERATOR_BLOCK_SIZE = 100;
    public static final int BATCH_BLOCK_SIZE = 500;

    @Inject protected CandlepinQueryFactory cpQueryFactory;
    @Inject protected Provider<EntityManager> entityManager;
    @Inject protected I18n i18n;
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
        save(entity);
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
        Page<List<E>> page = new Page<List<E>>();

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
        Page<ResultIterator<E>> page = new Page<ResultIterator<E>>();

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
        Page<List<E>> page = new Page<List<E>>();

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

    public void bulkDelete(List<E> entities) {
        for (E entity : entities) {
            delete(entity);
        }
    }

    @Transactional
    public void bulkDeleteTransactional(List<E> entities) {
        for (E entity : entities) {
            delete(entity);
        }
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
        return clazz.cast(currentSession().get(clazz, id));
    }

    @Transactional
    protected void save(E anObject) {
        getEntityManager().persist(anObject);
        flush();
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
                Iterable<List<E>> blocks = Iterables.partition(entities, BATCH_BLOCK_SIZE);

                for (List<E> block : blocks) {
                    for (E entity : block) {
                        session.save(entity);
                    }

                    if (flush) {
                        session.flush();

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
                Iterable<List<E>> blocks = Iterables.partition(entities, BATCH_BLOCK_SIZE);

                for (List<E> block : blocks) {
                    for (E entity : block) {
                        session.update(entity);
                    }

                    if (flush) {
                        session.flush();

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

    public Collection<E> saveOrUpdateAll(Collection<E> entities, boolean flush, boolean evict) {
        if (CollectionUtils.isNotEmpty(entities)) {
            try {
                Session session = this.currentSession();
                Iterable<List<E>> blocks = Iterables.partition(entities, BATCH_BLOCK_SIZE);

                for (List<E> block : blocks) {
                    for (E entity : block) {
                        session.saveOrUpdate(entity);
                    }

                    if (flush) {
                        session.flush();

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

                if (flush) {
                    int i = 0;
                    for (E entity : entities) {
                        session.merge(entity);

                        if (++i % BATCH_BLOCK_SIZE == 0) {
                            session.flush();
                            session.clear();
                        }
                    }

                    if (i % BATCH_BLOCK_SIZE != 0) {
                        session.flush();
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

    public void lock(E object, LockModeType lmt) {
        this.getEntityManager().lock(object, lmt);
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
            return new ArrayList<E>();
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
        return i18n.tr("Request failed due to concurrent modification, please re-try.");
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

        for (List<?> block : Iterables.partition(collection, IN_OPERATOR_BLOCK_SIZE)) {
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

    public List<E> lockAndLoadBatch(Iterable<String> ids, String entityName, String keyName) {
        List<E> result = new LinkedList<E>();

        if (ids != null && ids.iterator().hasNext()) {
            StringBuilder hql = new StringBuilder("SELECT obj FROM ")
                .append(entityName)
                .append(" obj WHERE ")
                .append(keyName)
                .append(" IN (:ids)");

            javax.persistence.Query query = this.getEntityManager()
                .createQuery(hql.toString())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE);

            for (List<String> block : Iterables.partition(ids, IN_OPERATOR_BLOCK_SIZE)) {
                query.setParameter("ids", block);
                result.addAll((List<E>) query.getResultList());
            }

            //In some situations, even after locking the entity we
            //got stale in the entity e.g. Pool.consumed
            //This refresh reloads the entity after the lock has
            //been issued.
            for (E e : result) {
                getEntityManager().refresh(e);
            }
        }

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
            AbstractHibernateCurator.CASE_OPERATOR_BLOCK_SIZE);

        int count = 0;
        int lastBlock = -1;

        Session session = this.currentSession();
        SQLQuery query = null;

        for (List<Map.Entry<Object, Object>> block : blocks) {
            if (block.size() != lastBlock) {
                // Rebuild update block
                int args = 0;
                boolean whereStarted = false;

                StringBuilder builder = new StringBuilder("UPDATE ").append(table).append(" SET ")
                    .append(column).append(" = ");

                // Note: block.size() should never be zero here.
                if (block.size() > 1) {
                    builder.append("CASE");

                    for (int i = 0; i < block.size(); ++i) {
                        builder.append(" WHEN ").append(column).append(" = ?").append(++args)
                            .append(" THEN ?").append(++args);
                    }

                    builder.append(" ELSE ").append(column).append(" END ");
                }
                else {
                    builder.append('?').append(++args).append(" WHERE ").append(column).append(" = ?")
                        .append(++args).append(' ');

                    whereStarted = true;
                }

                // Add criteria
                if (criteria != null && !criteria.isEmpty()) {
                    for (Map.Entry<String, Object> criterion : criteria.entrySet()) {
                        if (criterion.getValue() instanceof Collection) {
                            if (((Collection) criterion.getValue()).size() > 0) {
                                int inBlocks = (int) Math.ceil((((Collection) criterion.getValue()).size() /
                                    (float) AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE));

                                builder.append(whereStarted ? " AND " : " WHERE ");
                                whereStarted = true;

                                if (inBlocks > 1) {
                                    builder.append('(');

                                    for (int i = 0; i < inBlocks; ++i) {
                                        if (i != 0) {
                                            builder.append(" OR ");
                                        }

                                        builder.append(criterion.getKey()).append(" IN (?").append(++args)
                                            .append(')');
                                    }

                                    builder.append(')');
                                }
                                else {
                                    builder.append(criterion.getKey()).append(" IN (?").append(++args)
                                        .append(')');
                                }
                            }
                        }
                        else {
                            builder.append(whereStarted ? " AND " : " WHERE ");
                            whereStarted = true;

                            builder.append(criterion.getKey()).append(" = ?").append(++args);
                        }
                    }
                }

                query = session.createSQLQuery(builder.toString());
            }

            // Set args
            int args = 0;

            if (block.size() > 1) {
                for (Map.Entry<Object, Object> entry : block) {
                    query.setParameter(String.valueOf(++args), entry.getKey())
                        .setParameter(String.valueOf(++args), entry.getValue());
                }
            }
            else {
                Map.Entry<Object, Object> entry = block.get(0);

                query.setParameter(String.valueOf(++args), entry.getValue())
                    .setParameter(String.valueOf(++args), entry.getKey());
            }

            // Set criteria if the block size has changed
            if (block.size() != lastBlock && criteria != null && !criteria.isEmpty()) {
                for (Object criterion : criteria.values()) {
                    if (criterion instanceof Collection) {
                        Iterable<List> inBlocks = Iterables.partition((Collection) criterion,
                            AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE);

                        for (List inBlock : inBlocks) {
                            query.setParameterList(String.valueOf(++args), inBlock);
                        }
                    }
                    else {
                        query.setParameter(String.valueOf(++args), criterion);
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
}
