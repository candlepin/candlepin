package org.fedoraproject.candlepin.model;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;

import javax.persistence.EntityManager;

import org.hibernate.Session;

public abstract class AbstractHibernateRepository<E> {
    
    protected final EntityManager em;
    private final Class<E> entityType;
    
    @SuppressWarnings("unchecked")
    protected AbstractHibernateRepository(EntityManager em) {
        this.em = em;
        entityType = (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
    
    public E find(Serializable id) {
        return get(entityType, id);
    }
    
    public E create(E entity) {
        save(entity);
//        flush();
        return entity;
    }
    
    protected final <T> T get(Class<T> clazz, Serializable id) {
        return clazz.cast(currentSession().get(clazz, id));
    }
    
    protected final void save(Object anObject) {
        currentSession().save(anObject);
    }
    
    protected final void flush() {
        em.flush();
    }
    
    protected Session currentSession() {
        return (Session) em.getDelegate();
    }
}
