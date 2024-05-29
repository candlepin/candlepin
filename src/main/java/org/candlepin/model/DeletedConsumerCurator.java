/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import org.candlepin.auth.Principal;
import org.candlepin.guice.PrincipalProvider;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;


/**
 * DeletedConsumerCurator
 */
@Singleton
public class DeletedConsumerCurator extends AbstractHibernateCurator<DeletedConsumer> {

    private PrincipalProvider principalProvider;

    /**
     * Container object for providing various arguments to the deleted consumer lookup method(s).
     */
    public static class DeletedConsumerQueryArguments extends QueryArguments<DeletedConsumerQueryArguments> {
        private OffsetDateTime date;

        public DeletedConsumerCurator.DeletedConsumerQueryArguments setDate(OffsetDateTime date) {
            this.date = date;
            return this;
        }

        public OffsetDateTime getDate() {
            return this.date;
        }
    }

    @Inject
    public DeletedConsumerCurator(PrincipalProvider principalProvider) {
        super(DeletedConsumer.class);
        this.principalProvider = principalProvider;
    }

    public DeletedConsumer findByConsumer(Consumer c) {
        return findByConsumerUuid(c.getUuid());
    }

    public DeletedConsumer findByConsumerUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }

        String query = "SELECT dc FROM DeletedConsumer dc WHERE dc.consumerUuid = :uuid";

        try {
            return this.getEntityManager()
                .createQuery(query, DeletedConsumer.class)
                .setParameter("uuid", uuid)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    public List<DeletedConsumer> findByOwner(Owner o) {
        return findByOwnerId(o.getId());
    }

    @SuppressWarnings("unchecked")
    public List<DeletedConsumer> findByOwnerId(String oid) {
        String jpql = "SELECT dc FROM DeletedConsumer dc WHERE dc.ownerId = :owner_id " +
            "ORDER BY created desc";

        return this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", oid)
            .getResultList();
    }

    public int countByConsumer(Consumer c) {
        return countByConsumerUuid(c.getUuid());
    }

    public int countByConsumerUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return 0;
        }

        String query = "SELECT COUNT(dc) FROM DeletedConsumer dc WHERE dc.consumerUuid = :uuid";

        return this.getEntityManager()
            .createQuery(query, Long.class)
            .setParameter("uuid", uuid)
            .getSingleResult()
            .intValue();
    }

    public int createDeletedConsumers(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return 0;
        }

        Principal principal = this.principalProvider.get();
        String deletedConsumersStatement = "INSERT INTO DeletedConsumer " +
            "(id, created, updated, consumerUuid, ownerId, " +
            "ownerDisplayName, ownerKey, principalName, consumerName) " +
            "SELECT consumer.id, NOW(), NOW(), consumer.uuid, consumer.ownerId, owner.displayName, " +
            "owner.key, :principalName, consumer.name " +
            "FROM Consumer consumer " +
            "JOIN Owner owner on owner.id=consumer.ownerId " +
            "WHERE consumer.id IN (:consumerIds)";

        return entityManager.get()
            .createQuery(deletedConsumersStatement)
            .setParameter("principalName", principal == null ? null : principal.getName())
            .setParameter("consumerIds", consumerIds)
            .executeUpdate();
    }

    /**
     * Fetches a collection of deleted consumers based on the data in the query builder. If the
     * query builder is null or contains no arguments, the query will not limit or sort the result.
     *
     * @param queryArgs
     *     a DeletedConsumerQueryArguments instance containing the various arguments to use to
     *     select contents
     *
     * @return a list of deleted consumers. It will be paged and sorted if specified
     */
    public List<DeletedConsumer> listAll(DeletedConsumerQueryArguments queryArgs) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<DeletedConsumer> criteriaQuery = criteriaBuilder.createQuery(DeletedConsumer.class);

        Root<DeletedConsumer> root = criteriaQuery.from(DeletedConsumer.class);
        criteriaQuery.select(root)
            .distinct(true);
        if (queryArgs != null && queryArgs.getDate() != null) {
            criteriaQuery.where(criteriaBuilder.greaterThanOrEqualTo(
                root.get(DeletedConsumer_.CREATED),
                Date.from(queryArgs.getDate().toInstant())));
        }

        List<Order> order = this.buildJPAQueryOrder(criteriaBuilder, root, queryArgs);
        if (order != null && order.size() > 0) {
            criteriaQuery.orderBy(order);
        }

        TypedQuery query = this.getEntityManager().createQuery(criteriaQuery);

        if (queryArgs != null) {
            Integer offset = queryArgs.getOffset();
            if (offset != null && offset > 0) {
                query.setFirstResult(offset);
            }

            Integer limit = queryArgs.getLimit();
            if (limit != null && limit > 0) {
                query.setMaxResults(limit);
            }
        }
        return query.getResultList();
    }

    /**
     * Fetches the count of deleted consumers available.
     *
     * @param queryArgs
     *     a DeletedConsumerQueryArguments instance containing the various arguments
     *
     * @return the number of deleted consumers available
     */
    public long getDeletedConsumerCount(DeletedConsumerQueryArguments queryArgs) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);

        Root<DeletedConsumer> root = criteriaQuery.from(DeletedConsumer.class);
        criteriaQuery.select(criteriaBuilder.countDistinct(root));
        if (queryArgs != null && queryArgs.getDate() != null) {
            criteriaQuery.where(criteriaBuilder.greaterThanOrEqualTo(
                root.get(DeletedConsumer_.CREATED),
                Date.from(queryArgs.getDate().toInstant())));
        }

        return this.getEntityManager()
            .createQuery(criteriaQuery)
            .getSingleResult();
    }
}
