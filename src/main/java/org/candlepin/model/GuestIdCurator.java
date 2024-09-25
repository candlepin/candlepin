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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * GuestIdCurator
 */
@Singleton
public class GuestIdCurator extends AbstractHibernateCurator<GuestId> {

    private static Logger log = LoggerFactory.getLogger(GuestIdCurator.class);

    public GuestIdCurator() {
        super(GuestId.class);
    }

    public List<GuestId> listByConsumer(Consumer consumer) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<GuestId> criteriaQuery = criteriaBuilder.createQuery(GuestId.class);
        Root<Consumer> root = criteriaQuery.from(Consumer.class);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, criteriaBuilder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(criteriaBuilder.equal(root.get(Consumer_.id), consumer.getId()));

        criteriaQuery.select(root.get(Consumer_.GUEST_IDS))
            .where(predicates.toArray(new Predicate[0]));

        return getEntityManager()
            .createQuery(criteriaQuery)
            .getResultList();
    }

    public GuestId findByConsumerAndId(Consumer consumer, String guestId) {
        String jpql = """
                SELECT g FROM GuestId g
                WHERE g.consumer = :consumer
                    AND g.guestIdLower = :guestId
                """;

        try {
            return this.getEntityManager().createQuery(jpql, GuestId.class)
                .setParameter("consumer", consumer)
                .setParameter("guestId", guestId.toLowerCase())
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    public GuestId findByGuestIdAndOrg(String guestUuid, String ownerId) {
        String jpql = """
                SELECT g FROM GuestId g
                JOIN g.consumer c
                WHERE g.guestIdLower = :guestUuid
                    AND c.ownerId = :ownerId
                """;

        try {
            return this.getEntityManager().createQuery(jpql, GuestId.class)
                .setParameter("guestUuid", guestUuid.toLowerCase())
                .setParameter("ownerId", ownerId)
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }
}
