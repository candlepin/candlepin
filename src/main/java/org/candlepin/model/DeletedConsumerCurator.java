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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;

/**
 * DeletedConsumerCurator
 */
@Singleton
public class DeletedConsumerCurator extends AbstractHibernateCurator<DeletedConsumer> {
    private static Logger log = LoggerFactory.getLogger(DeletedConsumerCurator.class);

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

    public DeletedConsumer findByConsumerId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        try {
            String jpql = "SELECT dc FROM DeletedConsumer dc WHERE dc.id = :id";

            return this.getEntityManager()
                .createQuery(jpql, DeletedConsumer.class)
                .setParameter("id", id)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    public List<DeletedConsumer> findByConsumerUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return List.of();
        }

        String jpql = "SELECT dc FROM DeletedConsumer dc WHERE dc.consumerUuid = :uuid";

        return this.getEntityManager()
            .createQuery(jpql, DeletedConsumer.class)
            .setParameter("uuid", uuid)
            .getResultList();
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

        int rows = 0;

        Principal principal = this.principalProvider.get();
        String principalName = principal == null ? null : principal.getName();

        // Impl note:
        // This function is an unfortunate pile of per-database native queries. Attempting to do an upsert
        // with pure JPQL following typical query-insert-update flows results in an operation that's simply
        // too slow, taking around 5-15s per block of 1000 consumers with a sufficiently populated deleted
        // consumers table. Performing an upsert operation using INSERT ... ON CONFLICT or MERGE INTO
        // statements drops per-block runtime by an order of magnitude or two down to around 500-700ms.

        // I would prefer not doing this in general as Candlepin's codebase is not at all setup for doing
        // per-dialect queries in a maintainable way, but the performance requirements demand it.

        String postgresqlMerge = """
            INSERT INTO cp_deleted_consumers (id, created, updated, consumer_uuid, consumer_name, owner_id,
                owner_key, owner_displayname, principal_name)
            SELECT consumer.id, CURRENT_TIMESTAMP , CURRENT_TIMESTAMP , consumer.uuid, consumer.name,
                owner.id, owner.account, owner.displayname, :principal_name
            FROM cp_consumer consumer
            JOIN cp_owner owner ON owner.id = consumer.owner_id
            WHERE consumer.id IN (:consumer_ids)
            ON CONFLICT (id) DO UPDATE SET updated=EXCLUDED.updated, consumer_uuid=EXCLUDED.consumer_uuid,
                consumer_name=EXCLUDED.consumer_name, owner_id=EXCLUDED.owner_id,
                owner_key=EXCLUDED.owner_key, owner_displayname=EXCLUDED.owner_displayname,
                principal_name=EXCLUDED.principal_name
            """;

        // Impl note: This query uses the doubly deprecated VALUES() keyword (note the S on VALUES) to
        // reference values from the conflicting row. The modern way to do this is with VALUE (singular) on
        // MariaDB and with "new." or an explicit column alias declaration on MySQL. The documentation for
        // the latest versions of both dbms call out that the plural VALUES should not be used, but there
        // does not appear to be any overlap with the newer methods. In the future, we may need to
        // differentiate between MySQL and MariaDB as they continue to stray from each other.
        String mariadbMerge = """
            INSERT INTO cp_deleted_consumers (id, created, updated, consumer_uuid, consumer_name, owner_id,
                owner_key, owner_displayname, principal_name)
            SELECT consumer.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, consumer.uuid, consumer.name, owner.id,
                owner.account, owner.displayname, :principal_name
            FROM cp_consumer consumer
            JOIN cp_owner owner ON owner.id = consumer.owner_id
            WHERE consumer.id IN (:consumer_ids)
            ON DUPLICATE KEY UPDATE updated=VALUES(updated), consumer_uuid=VALUES(consumer_uuid),
                consumer_name=VALUES(consumer_name), owner_id=VALUES(owner_id), owner_key=VALUES(owner_key),
                owner_displayname=VALUES(owner_displayname), principal_name=VALUES(principal_name)
            """;

        // Impl note: HyperSQL does not appear to support conflict resolution on the INSERT statement itself,
        // but it does provide MERGE which is just as good for our purposes.
        String hsqldbMerge = """
            MERGE INTO cp_deleted_consumers dc USING (
                SELECT consumer.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, consumer.uuid, consumer.name,
                    owner.id, owner.account, owner.displayname, :principal_name
                FROM cp_consumer consumer
                JOIN cp_owner owner ON owner.id = consumer.owner_id
                WHERE consumer.id IN (:consumer_ids))
            AS entry(id, created, updated, uuid, name, owner_id, owner_key, owner_name, principal)
                ON dc.id = entry.id
            WHEN MATCHED THEN UPDATE SET
                dc.updated = entry.updated, dc.consumer_uuid = entry.uuid, dc.consumer_name = entry.name,
                dc.owner_id = entry.owner_id, dc.owner_key = entry.owner_key,
                dc.owner_displayname = entry.owner_name, dc.principal_name = entry.principal
            WHEN NOT MATCHED THEN INSERT (id, created, updated, consumer_uuid, consumer_name, owner_id,
                owner_key, owner_displayname, principal_name) VALUES entry.id, entry.created, entry.updated,
                entry.uuid, entry.name, entry.owner_id, entry.owner_key, entry.owner_name, entry.principal
            """;

        // Determine the best query for this insert
        String statement = switch (this.getDatabaseDialect()) {
            case POSTGRESQL -> postgresqlMerge;
            case MARIADB -> mariadbMerge;
            case HSQLDB -> hsqldbMerge;
        };

        Query query = this.getEntityManager()
            .createNativeQuery(statement)
            .setParameter("principal_name", principalName);

        for (List<String> block : this.partition(consumerIds)) {
            rows += query.setParameter("consumer_ids", block)
                .executeUpdate();
        }

        return rows;
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
