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
import org.candlepin.config.Configuration;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.exceptions.ConcurrentModificationException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.NaturalIdLoadAccess;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.internal.SessionImpl;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.transform.ResultTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.NonUniqueResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.TransactionRequiredException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
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

    @Inject protected CandlepinQueryFactory cpQueryFactory;
    @Inject protected Provider<EntityManager> entityManager;
    @Inject protected Provider<I18n> i18nProvider;
    @Inject protected Configuration config;
    @Inject private PrincipalProvider principalProvider;

    private final Class<E> entityType;
    private NaturalIdLoadAccess<E> natIdLoader;

    public AbstractHibernateCurator(Class<E> entityType) {
        //entityType = (Class<E>) ((ParameterizedType)
        //getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.entityType = entityType;
        this.natIdLoader = null;
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

    public String getDatabaseDialect() {
        return ((String) this.currentSession().getSessionFactory().getProperties()
            .get("hibernate.dialect")).toLowerCase();
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

    public void enableFilterList(String filterName, String parameterName, Collection value) {
        currentSession().enableFilter(filterName).setParameterList(parameterName, value);
    }

    @Transactional
    protected final <T> T secureGet(Class<T> clazz, Serializable id) {
        return clazz.cast(createSecureCriteria().add(Restrictions.idEq(id)).uniqueResult());
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
    @Transactional
    public E secureGet(Serializable id) {
        return id == null ? null : secureGet(entityType, id);
    }

    @Transactional
    protected <T> T get(Class<T> clazz, Serializable id) {
        return this.currentSession().get(clazz, id);
    }

    /**
     * @param id db id of entity to be found.
     * @return entity matching given id, or null otherwise.
     */
    @Transactional
    public E get(Serializable id) {
        return id == null ? null : this.get(entityType, id);
    }

    /**
     * Checks if entry exists in db.
     * @param id primary key of entity.
     * @return boolean value whether row exists or not
     */
    @Transactional
    public boolean exists(Serializable id) {
        boolean doesExists = false;
        String columnName = getPrimaryKeyName();

        if (columnName != null) {
            CriteriaBuilder builder = this.getEntityManager().getCriteriaBuilder();
            CriteriaQuery<Long> query = builder.createQuery(Long.class);
            query.select(builder.count(query.from(entityType)));
            Root<E> root = query.from(entityType);
            query.where(builder.equal(root.get(columnName), id));

            if (this.getEntityManager().createQuery(query).getSingleResult() > 0) {
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
    public CandlepinQuery<E> listAll() {
        DetachedCriteria criteria = this.createSecureDetachedCriteria();

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
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

    private List<E> loadPageData(CriteriaQuery<E> criteria, PageRequest pageRequest) {
        TypedQuery<E> query = this.entityManager.get().createQuery(criteria);
        if (pageRequest.isPaging()) {
            query.setFirstResult((pageRequest.getPage() - 1) * pageRequest.getPerPage());
            query.setMaxResults(pageRequest.getPerPage());
        }

        return query.getResultList();
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

    private javax.persistence.criteria.Order createPagingOrder(Root<?> root, PageRequest p) {
        String sortBy = (p.getSortBy() == null) ? AbstractHibernateObject.DEFAULT_SORT_FIELD : p.getSortBy();
        PageRequest.Order order = (p.getOrder() == null) ? PageRequest.DEFAULT_ORDER : p.getOrder();
        CriteriaBuilder criteriaBuilder = this.entityManager.get().getCriteriaBuilder();

        if (order == PageRequest.Order.ASCENDING) {
            return criteriaBuilder.asc(root.get(sortBy));
        }
        //DESCENDING
        return criteriaBuilder.desc(root.get(sortBy));
    }

    private Integer findRowCount(Criteria c) {
        c.setProjection(Projections.rowCount());
        return ((Long) c.uniqueResult()).intValue();
    }

    private Long findRowCount() {
        CriteriaBuilder criteriaBuilder = this.entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        countQuery.select(criteriaBuilder.count(countQuery.from(this.entityType)));
        return this.entityManager.get().createQuery(countQuery).getSingleResult();
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

    @Transactional
    public List<E> listByCriteria(CriteriaQuery<E> query) {
        return this.getEntityManager()
            .createQuery(query)
            .getResultList();
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
        Principal principal = this.getPrincipal();
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
                    builder.or(predicates.toArray(new Predicate[predicates.size()])) :
                    predicates.get(0);
            }
        }

        return predicate;
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

    @Transactional
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
            Session session = this.currentSession();
            session.delete(session.get(this.entityType, entity.getId()));
        }
    }

    @Transactional
    public void bulkDelete(Collection<E> entities) {
        for (E entity : entities) {
            delete(entity);
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
        return getEntityManager().merge(entity);
    }

    @Transactional
    protected void save(E anObject) {
        create(anObject, true);
    }

    @Transactional
    public E saveOrUpdate(E entity) {
        Session session = this.currentSession();
        session.saveOrUpdate(entity);

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

    public Session openSession() {
        SessionFactory factory = this.currentSession().getSessionFactory();
        return factory.openSession();
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
     * Fetches the natural ID loader for this entity. This loader can be used and reused to
     * quickly load entities using their natural IDs. Once a natural ID loader has been created for
     * a given curator instance, this method will return that single natural ID loader.
     * <p></p>
     * To load an entity using the loader, the following template can be followed:
     *
     * <pre>
     *  EntityType entity = this.getNaturalIdLoader()
     *      .using("field_name_1", "value_1)
     *      .using("field_name_2", "value_2)
     *      ...
     *      .using("field_name_n", "value_n)
     *      .load()
     * </pre>
     *
     * Where each field name represents the fields that make up the natural ID.
     * <p></p>
     * It should be noted that the field values set on the loader will be retained between calls so
     * long as the loader is not reinstantiated. This can have both positive or negative
     * consequences depending on the context. This could be used to optimize out some unnecessary
     * value assignments, but it could also lead to incorrect lookups succeeding when they should
     * fail. Care should be taken to ensure that the loader is either reinstantiated on every call
     * (which is slightly inefficient), or that every field is assigned properly before calling the
     * loader's "load" method.
     *
     * @return
     *  A natural ID loader for this curator's entity type
     */
    protected NaturalIdLoadAccess<E> getNaturalIdLoader() {
        return this.getNaturalIdLoader(false);
    }

    /**
     * Fetches the natural ID loader for this entity. See the zero-parameter getNaturalIdLoader
     * method for expected usage of this method and the natural ID loader.
     *
     * @param reinstantiate
     *  If set, forces the natural ID loader to be reinstantiated even if a natural ID loader had
     *  already been created for this curator.
     *
     * @return
     *  A natural ID loader for this curator's entity type
     */
    protected NaturalIdLoadAccess<E> getNaturalIdLoader(boolean reinstantiate) {
        if (this.natIdLoader == null || reinstantiate) {
            this.natIdLoader = this.currentSession().byNaturalId(this.entityType);
        }

        return this.natIdLoader;
    }

    @Transactional
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

    public void refresh(Iterable<E> entities) {
        if (entities != null) {
            EntityManager manager = this.getEntityManager();

            for (E entity : entities) {
                manager.refresh(entity);
            }
        }
    }

    public void refresh(E... entities) {
        if (entities != null) {
            this.refresh(Arrays.asList(entities));
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

        SessionImpl session = (SessionImpl) this.currentSession();
        ClassMetadata metadata = session.getSessionFactory().getClassMetadata(this.entityType);

        // Step through and toss all the entities into our TreeMap, which orders its entries using
        // the natural order of the key. This will ensure that we have our entity collection sorted
        // by entity ID, which should help avoid deadlock by having a deterministic locking order.
        for (E entity : entities) {
            entityMap.put(metadata.getIdentifier(entity, session), entity);
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
    @SuppressWarnings("unchecked")
    protected E lockAndLoad(Class<E> entityClass, Serializable id) {
        return this.currentSession()
            .byId(entityClass)
            .with(new LockOptions(LockMode.PESSIMISTIC_WRITE))
            .load(id);
    }

    /**
     * Loads the entities represented by the given IDs with a pessimistic write lock. If no
     * entities were found with the given IDs, this method returns an empty collection.
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
     *  A collection of locked entities represented by the given IDs
     */
    public Collection<E> lockAndLoad(Iterable<? extends Serializable> ids) {
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
     *  A collection of locked entities matching the given values
     */
    protected Collection<E> lockAndLoad(Class<E> entityClass, Iterable<? extends Serializable> ids) {
        // Sort and de-duplicate the provided collection of IDs so we have a deterministic locking
        // order for the entities (helps avoid deadlock)
        SortedSet<Serializable> idSet = new TreeSet<>();
        for (Serializable id : ids) {
            idSet.add(id);
        }

        // Fetch the entities from the DB...
        if (idSet.size() > 0) {
            return this.currentSession()
                .byMultipleIds(entityClass)
                .with(new LockOptions(LockMode.PESSIMISTIC_WRITE))
                .multiLoad(new ArrayList(idSet));
        }

        return new ArrayList<E>();
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
        catch (javax.persistence.NoResultException e) {
            SystemLock lock = new SystemLock()
                .setId(lockName);

            this.getEntityManager()
                .persist(lock);

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
     * Partitions the given map using the value returned by getInBlockSize() method as the partition
     * size.
     *
     * @param map
     *  the map to partition
     *
     * @throws IllegalArgumentException
     *  if the provided map is null
     *
     * @return
     *  An iterable collection of maps containing the partitioned data from the provided map
     */
    protected <K, V> Iterable<Map<K, V>> partitionMap(Map<K, V> map) {
        if (map == null) {
            throw new IllegalArgumentException("map is null");
        }

        int blockSize = this.getInBlockSize();
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
     * Builds a collection of order instances to be used with the JPA criteria query API.
     *
     * @param critBuilder
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
    protected List<javax.persistence.criteria.Order> buildJPAQueryOrder(CriteriaBuilder criteriaBuilder,
        Root<?> root, QueryArguments<?> queryArguments) {

        List<javax.persistence.criteria.Order> orderList = new ArrayList<>();

        if (queryArguments != null && queryArguments.getOrder() != null) {
            for (QueryArguments.Order order : queryArguments.getOrder()) {
                try {
                    orderList.add(order.reverse() ?
                        criteriaBuilder.desc(root.get(order.column())) :
                        criteriaBuilder.asc(root.get(order.column())));
                }
                catch (IllegalArgumentException e) {
                    String errmsg = String.format("Invalid attribute key: %s", order.column());
                    throw new InvalidOrderKeyException(errmsg, e);
                }
            }
        }

        return orderList;
    }
}
