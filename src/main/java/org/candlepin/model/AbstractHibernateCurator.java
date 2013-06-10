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

import org.candlepin.auth.interceptor.EnforceAccessControl;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.Page;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.impl.CriteriaImpl;
import org.hibernate.transform.ResultTransformer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;

/**
 * AbstractHibernateCurator
 * @param <E> Entity specific curator.
 */
public abstract class AbstractHibernateCurator<E extends Persisted> {
    @Inject protected Provider<EntityManager> entityManager;
    private final Class<E> entityType;
    private int batchSize = 30;

    protected AbstractHibernateCurator(Class<E> entityType) {
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
    @EnforceAccessControl
    public E find(Serializable id) {
        return id == null ? null : get(entityType, id);
    }

    /**
     * @param entity to be created.
     * @return newly created entity
     */
    @Transactional
    @EnforceAccessControl
    public E create(E entity) {
        save(entity);
        return entity;
    }

    /**
     * @return all entities for a particular type.
     */
    public List<E> listAll() {
        return listByCriteria(DetachedCriteria.forClass(entityType));
    }
    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
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
    @EnforceAccessControl
    public Page<List<E>> listAll(PageRequest pageRequest) {
        Page<List<E>> page = new Page<List<E>>();

        if (pageRequest != null) {
            Criteria count = currentSession().createCriteria(entityType);
            page.setMaxRecords(findRowCount(count));

            Criteria c = currentSession().createCriteria(entityType);
            page.setPageData(loadPageData(c, pageRequest));
            page.setPageRequest(pageRequest);
        }
        else {
            page.setPageData(listAll());
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
        return (Integer) c.uniqueResult();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public List<E> listByCriteria(DetachedCriteria query) {
        return query.getExecutableCriteria(currentSession()).list();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public Page<List<E>> listByCriteria(DetachedCriteria query,
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

    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public Page<List<E>> listByCriteria(DetachedCriteria query,
        PageRequest pageRequest) {
        Page<List<E>> page = new Page<List<E>>();

        if (pageRequest != null) {
            // see https://forum.hibernate.org/viewtopic.php?t=974802
            Criteria c = query.getExecutableCriteria(currentSession());

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
            page.setPageData(listByCriteria(query));
        }

        return page;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public E getByCriteria(DetachedCriteria query) {
        return (E) query.getExecutableCriteria(currentSession()).uniqueResult();
    }

    /**
     * @param entity to be deleted.
     */
    @Transactional
    @EnforceAccessControl
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
    @EnforceAccessControl
    public E merge(E entity) {
        return getEntityManager().merge(entity);
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

    protected final void flush() {
        getEntityManager().flush();
    }

    protected Session currentSession() {
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }

    protected EntityManager getEntityManager() {
        return entityManager.get();
    }

    public void saveOrUpdateAll(List<E> entries) {
        Session session = currentSession();
        for (int i = 0; i < entries.size(); i++) {
            session.saveOrUpdate(entries.get(i));
            if (i % batchSize == 0) {
                session.flush();
                session.clear();
            }
        }

    }

    public void refresh(E object) {
        getEntityManager().refresh(object);
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
}
