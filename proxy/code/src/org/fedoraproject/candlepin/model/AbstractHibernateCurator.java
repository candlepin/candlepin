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
        //entityType = (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
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
