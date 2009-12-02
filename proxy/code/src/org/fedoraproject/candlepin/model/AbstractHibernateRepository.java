package org.fedoraproject.candlepin.model;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;

import org.hibernate.Session;

public abstract class AbstractHibernateRepository<E> {
    
    protected final Session session;
    private final Class<E> entityType;
    
    @SuppressWarnings("unchecked")
    protected AbstractHibernateRepository(Session session) {
        this.session = session;
        entityType = (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
    
    public E find(Serializable id) {
        return get(entityType, id);
    }
    
    public E create(E entity) {
        save(entity);
        flush();
        return entity;
    }
    
    protected final <T> T get(Class<T> clazz, Serializable id) {
        return clazz.cast(session.get(clazz, id));
    }
    
    protected final void save(Object anObject) {
        session.save(anObject);
    }
    
    protected final void flush() {
        session.flush();
    }
}
