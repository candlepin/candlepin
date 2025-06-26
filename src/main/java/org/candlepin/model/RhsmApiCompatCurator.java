/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import org.candlepin.config.Configuration;
import org.candlepin.config.DatabaseConfigFactory;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

/**
 * Curator for retrieving persisted data for operations needed by RHSM API.
 */
@Singleton
public class RhsmApiCompatCurator {

    private final EntityManager entityManager;
    private final Configuration config;

    @Inject
    public RhsmApiCompatCurator(Provider<EntityManager> entityManager, Configuration config) {
        Provider<EntityManager> provider = Objects.requireNonNull(entityManager);
        this.config = Objects.requireNonNull(config);

        this.entityManager = provider.get();
    }

    /**
     * Retrieves the entitlement count information for {@link Consumer}s based on provided consumer IDs and
     * UUIDs. The entitlement information is sorted in ascending order based on consumer ID and UUID values.
     *
     * @param consumerIds
     *  the consumer IDs to retrieve entitlement count information for
     *
     * @param consumerUuids
     *  the consumer UUIDs to retrieve entitlement count information for
     *
     * @return the list of entitlement counts
     */
    // public List<ConsumerEntitlementCount> getConsumerEntitlementCounts(Collection<String> consumerIds,
    //     Collection<String> consumerUuids) {

    //     consumerIds = consumerIds == null ? List.of() : consumerIds;
    //     consumerUuids = consumerUuids == null ? List.of() : consumerUuids;
    //     if (consumerIds.isEmpty() && consumerUuids.isEmpty()) {
    //         return List.of();
    //     }

    //     String jpql = "SELECT new org.candlepin.model.ConsumerEntitlementCount(consumer.id, consumer.uuid, " +
    //         "pool.contractNumber, pss.subscriptionId, prod.id, prod.name, SUM(ent.quantity)) " +
    //         "FROM Entitlement ent " +
    //         "JOIN ent.consumer consumer " +
    //         "JOIN ent.pool pool " +
    //         "JOIN pool.product prod " +
    //         "LEFT JOIN pool.sourceSubscription pss " +
    //         "WHERE consumer.id IN (:consumerIds) OR consumer.uuid IN (:consumerUuids) " +
    //         "GROUP BY consumer.id, consumer.uuid, pool.contractNumber, pss.subscriptionId, prod.id, " +
    //         "prod.name " +
    //         "ORDER BY consumer.id, consumer.uuid ASC";

    //     TypedQuery<ConsumerEntitlementCount> query = this.entityManager
    //         .createQuery(jpql, ConsumerEntitlementCount.class);

    //     List<ConsumerEntitlementCount> consumerEntCounts = new ArrayList<>();
    //     int inBlockSize = this.config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
    //     Iterator<List<String>> idIterator = Iterables.partition(consumerIds, inBlockSize).iterator();
    //     Iterator<List<String>> uuidIterator = Iterables.partition(consumerUuids, inBlockSize).iterator();
    //     while (idIterator.hasNext() || uuidIterator.hasNext()) {
    //         List<String> consumerIdBlock = idIterator.hasNext() ? idIterator.next() : List.of();
    //         List<String> consumerUuidBlock = uuidIterator.hasNext() ? uuidIterator.next() : List.of();

    //         query.setParameter("consumerIds", consumerIdBlock);
    //         query.setParameter("consumerUuids", consumerUuidBlock);

    //         consumerEntCounts.addAll(query.getResultList());
    //     }

    //     return consumerEntCounts;
    // }

    // TODO: Java Doc
    public List<Consumer> listConsumers(String ownerId, String afterId, OffsetDateTime afterCheckin) {
        if (ownerId == null || ownerId.isEmpty()) {
            return List.of();
        }

        String whereClause = "WHERE ";
        List<String> conditionals = new ArrayList<>();
        conditionals.add("consumer.ownerId = :ownerId");
        if (afterId != null) {
            conditionals.add("consumer.id > : afterId");
        }

        if (afterCheckin != null) {
            conditionals.add("consumer.lastcheckin > :lastCheckin");
        }

        whereClause = whereClause + String.join(" AND ", conditionals);

        String jpql = "SELECT consumer " +
            "FROM Consumer consumer " +
            whereClause + " " +
            "ORDER BY consumer.id, consumer.lastCheckin ASC";

        TypedQuery<Consumer> query = this.entityManager
            .createQuery(jpql, Consumer.class)
            .setParameter("ownerId", ownerId);

        if (afterId != null) {
            query.setParameter("afterId", afterId);
        }

        if (afterCheckin != null) {
            query.setParameter("lastCheckin", afterCheckin);
        }

        return query.getResultList();
    }

}