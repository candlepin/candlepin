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

import javax.persistence.EntityManager;

import org.hibernate.Session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.wideplay.warp.persist.Transactional;

public class AbstractHibernateCurator<E> {
    @Inject protected Provider<EntityManager> entityManager;
    private final Class<E> entityType;

    protected AbstractHibernateCurator(Class<E> entityType) {
        //entityType = (Class<E>) ((ParameterizedType)
        //getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.entityType = entityType;
    }

    public E find(Serializable id) {
        return get(entityType, id);
    }

    @Transactional
    public E create(E entity) {
        save(entity);
        flush();
        return entity;
    }

    protected final <T> T get(Class<T> clazz, Serializable id) {
        return clazz.cast(currentSession().get(clazz, id));
    }

    protected final void save(Object anObject) {
        currentSession().save(anObject);
    }

    protected final void flush() {
        entityManager.get().flush();
    }

    protected Session currentSession() {
        return (Session) entityManager.get().getDelegate();
    }

    protected Provider<EntityManager> getEntityManager() {
        return entityManager;
    }

    protected void setEntityManager(Provider<EntityManager> entityManager) {
        this.entityManager = entityManager;
    }
}
