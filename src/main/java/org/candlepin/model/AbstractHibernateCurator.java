/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */
package org.candlepin.model;

import org.candlepin.config.DatabaseConfig;
import org.candlepin.exceptions.ConcurrentModificationException;

import com.google.inject.persist.Transactional;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.TransactionRequiredException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
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

    @Inject protected EntityManager entityManager;
    @Inject protected I18n i18nProvider;
    @Inject protected DatabaseConfig config;

    private final Class<E> entityType;

    public AbstractHibernateCurator(Class<E> entityType) {
        //entityType = (Class<E>) ((ParameterizedType)
        //getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.entityType = entityType;
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
     * @param entity to be deleted.
     */
    @Transactional
    public void delete(E entity) {
        if (entity != null) {
            Session session = this.currentSession();
            session.delete(session.get(this.entityType, entity.getId()));
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
        return (Session) this.entityManager.getDelegate();
    }


    public EntityManager getEntityManager() {
        return this.entityManager;
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

    public void refresh(Iterable<E> entities) {
        if (entities != null) {
            EntityManager manager = this.getEntityManager();

            for (E entity : entities) {
                manager.refresh(entity);
            }
        }
    }

    private String getConcurrentModificationMessage() {
        return i18nProvider.tr("Request failed due to concurrent modification, please re-try.");
    }

}
