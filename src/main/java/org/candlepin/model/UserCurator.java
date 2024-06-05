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

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 *
 * UserCurator
 */
@Singleton
public class UserCurator extends AbstractHibernateCurator<User> {

    public UserCurator() {
        super(User.class);
    }

    public User findByLogin(String login) {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<User> cq = cb.createQuery(User.class);
        Root<User> root = cq.from(User.class);

        Predicate usernamePredicate = cb.equal(root.get("username"), login);
        Predicate securityPredicate = getSecurityPredicate(User.class, cb, root);

        if (securityPredicate != null) {
            cq.where(cb.and(usernamePredicate, securityPredicate));
        }
        else {
            cq.where(usernamePredicate);
        }

        try {
            return em.createQuery(cq)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Fetch the number of users without loading them all.
     * @return number of user accounts in our database
     */
    public Long getUserCount() {
        String jpql = "SELECT COUNT(u) FROM User u";

        return getEntityManager().createQuery(jpql, Long.class)
            .getSingleResult();
    }

}
