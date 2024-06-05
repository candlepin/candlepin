/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import com.google.inject.persist.Transactional;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * ContentOverrideCurator
 *
 * @param <T> ContentOverride type
 * @param <Parent> parent of the content override, Consumer or ActivationKey for example
 */
public abstract class ContentOverrideCurator<T extends ContentOverride<T, Parent>,
    Parent extends AbstractHibernateObject> extends AbstractHibernateCurator<T> {

    private String parentAttr;

    /**
     * @param entityType
     */
    public ContentOverrideCurator(Class<T> entityType, String parentAttrName) {
        super(entityType);
        this.parentAttr = parentAttrName;
    }

    public List<T> getList(Parent parent) {
        CriteriaBuilder cb = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(this.entityType());
        Root<T> root = query.from(this.entityType());
        query.select(root);
        query.where(cb.equal(root.get(parentAttr), parent));

        return this.getEntityManager()
            .createQuery(query)
            .getResultList();
    }

    @Transactional
    public void removeByName(Parent parent, String contentLabel, String name) {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> cd = cb.createCriteriaDelete(this.entityType());
        Root<T> root = cd.from(this.entityType());

        Predicate parentPredicate = cb.equal(root.get(parentAttr), parent);
        Predicate contentLabelPredicate = cb.equal(root.get("contentLabel"), contentLabel);
        Predicate namePredicate = cb.equal(cb.lower(root.get("name")), name.toLowerCase());

        cd.where(cb.and(parentPredicate, contentLabelPredicate, namePredicate));

        em.createQuery(cd).executeUpdate();
    }

    @Transactional
    public void removeByContentLabel(Parent parent, String contentLabel) {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> cd = cb.createCriteriaDelete(this.entityType());
        Root<T> root = cd.from(this.entityType());

        Predicate parentPredicate = cb.equal(root.get(parentAttr), parent);
        Predicate contentLabelPredicate = cb.equal(root.get("contentLabel"), contentLabel);

        cd.where(cb.and(parentPredicate, contentLabelPredicate));

        em.createQuery(cd).executeUpdate();
    }

    @Transactional
    public void removeByParent(Parent parent) {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> cd = cb.createCriteriaDelete(this.entityType());
        Root<T> root = cd.from(this.entityType());

        Predicate parentPredicate = cb.equal(root.get(parentAttr), parent);

        cd.where(parentPredicate);

        em.createQuery(cd).executeUpdate();
    }

    public T retrieve(Parent parent, String contentLabel, String name) {
        if (parent != null && contentLabel != null && name != null) {
            EntityManager em = getEntityManager();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(this.entityType());
            Root<T> root = cq.from(this.entityType());

            Predicate parentPredicate = cb.equal(root.get(parentAttr), parent);
            Predicate contentLabelPredicate = cb.equal(root.get("contentLabel"), contentLabel);
            Predicate namePredicate = cb.equal(cb.lower(root.get("name")), name.toLowerCase());

            cq.where(cb.and(parentPredicate, contentLabelPredicate, namePredicate));

            try {
                return em.createQuery(cq)
                    .setMaxResults(1)
                    .getSingleResult();
            }
            catch (NoResultException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Creates an empty/default override, to be completed by the caller.
     *
     * @return
     *  An empty/default ContentOverride instance
     */
    protected abstract T createOverride();

}
