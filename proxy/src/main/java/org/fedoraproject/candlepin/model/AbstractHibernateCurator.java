/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.EntityManager;

import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.wideplay.warp.persist.Transactional;

/**
 * AbstractHibernateCurator
 * @param <E> Entity specific curator.
 */
public abstract class AbstractHibernateCurator<E extends Persisted> {
    @Inject protected Provider<EntityManager> entityManager;
    private final Class<E> entityType;

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
    public List<E> listByCriteria(DetachedCriteria query) {
        return query.getExecutableCriteria(currentSession()).list();
    }
    
    /**
     * @param entity to be deleted.
     */
    @Transactional
    @EnforceAccessControl
    public void delete(E entity) {
        E toDelete = find(entity.getId());
        currentSession().delete(toDelete);
        flush();
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
        return (Session) entityManager.get().getDelegate();
    }

    protected EntityManager getEntityManager() {
        return entityManager.get();
    }
}
