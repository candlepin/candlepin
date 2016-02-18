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
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
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
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;

/**
 * AbstractHibernateCurator base class for all Candlepin curators. Curators are
 * the applications database layer, typically following the pattern of one
 * curator for each model type. This class contains methods common to all
 * curators.
 * @param <E> Entity specific curator.
 */
public abstract class AbstractHibernateCurator<E extends Persisted> {
    @Inject protected Provider<EntityManager> entityManager;
    @Inject protected I18n i18n;
    private final Class<E> entityType;
    private int batchSize = 30;
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
    public List<E> listAll() {
        return listByCriteria(createSecureCriteria());
    }

    public List<E> listAllByIds(Collection<? extends Serializable> ids) {
        return listByCriteria(
            createSecureCriteria().add(Restrictions.in("id", ids)));
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
            List<E> pageData = listAll();
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
        String sortBy = (p.getSortBy() == null) ?
            AbstractHibernateObject.DEFAULT_SORT_FIELD : p.getSortBy();
        PageRequest.Order order = (p.getOrder() == null) ?
            PageRequest.DEFAULT_ORDER : p.getOrder();

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
    @Transactional
    public List<E> listByCriteria(Criteria query) {
        return query.list();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Page<List<E>> listByCriteria(Criteria query,
        PageRequest pageRequest, boolean postFilter) {
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
        Principal principal = principalProvider.get();
        Criteria query = currentSession().createCriteria(entityType);

        /*
         * There are situations where consumer queries are run before there is a principal,
         * i.e. during authentication when we're looking up the consumer itself.
         */
        if (principal == null) {
            return query;
        }


        // Admins do not need query filtering enabled.
        if (principal.hasFullAccess()) {
            return query;
        }

        Criterion finalCriterion = null;
        for (Permission perm : principal.getPermissions()) {

            Criterion crit = perm.getCriteriaRestrictions(entityType);
            if (crit != null) {
                log.debug("Got criteria restrictions from permissions {} for {}: {}",
                    new Object [] {perm, entityType, crit});
                if (finalCriterion == null) {
                    finalCriterion = crit;
                }
                else {
                    finalCriterion = Restrictions.or(finalCriterion, crit);
                }
            }
        }

        if (finalCriterion != null) {
            query.add(finalCriterion);
        }

        return query;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Page<List<E>> listByCriteria(Criteria c,
        PageRequest pageRequest) {
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
        return clazz.cast(createSecureCriteria().
            add(Restrictions.idEq(id)).uniqueResult());
    }

    @Transactional
    protected final <T> T get(Class<T> clazz, Serializable id) {
        return clazz.cast(currentSession().get(clazz, id));
    }

    @Transactional
    protected final void save(E anObject) {
        getEntityManager().persist(anObject);
        flush();
    }

    public void flush() {
        try {
            getEntityManager().flush();
        }
        catch (OptimisticLockException e) {
            throw new ConcurrentModificationException(getConcurrentModificationMessage(),
                e);
        }
    }

    protected Session currentSession() {
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }

    protected EntityManager getEntityManager() {
        return entityManager.get();
    }

    public void saveOrUpdateAll(List<E> entries) {
        try {
            Session session = currentSession();
            for (int i = 0; i < entries.size(); i++) {
                session.saveOrUpdate(entries.get(i));
                if (i % batchSize == 0) {
                    session.flush();
                    session.clear();
                }
            }
        }
        catch (OptimisticLockException e) {
            throw new ConcurrentModificationException(getConcurrentModificationMessage(),
                e);
        }

    }

    public void refresh(E object) {
        getEntityManager().refresh(object);
    }

    public void evict(E object) {
        currentSession().evict(object);
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
}
