package org.candlepin.gutterball.curator.jpa;

import org.candlepin.common.exceptions.ConcurrentModificationException;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import org.hibernate.Session;

import java.io.Serializable;

import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;

public class BaseCurator<E> {

    @Inject protected Provider<EntityManager> entityManager;
    private final Class<E> entityType;

    protected BaseCurator(Class<E> entityType) {
        this.entityType = entityType;
    }

    public Class<E> entityType() {
        return entityType;
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
     * @param entity to be created.
     * @return newly created entity
     */
    @Transactional
    public E create(E entity) {
        save(entity);
        return entity;
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



    private String getConcurrentModificationMessage() {
        return "Concurrent";
        //return i18n().tr("Request failed due to concurrent modification, please re-try.");
    }
}
