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
import org.candlepin.util.ConsumerFeedFactExtractor;
import org.candlepin.util.Util;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.hibernate.Session;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

/**
 * Curator for retrieving persisted data for operations needed by RHSM API.
 */
@Singleton
public class RhsmApiCompatCurator {

    private final EntityManager entityManager;
    private final Configuration config;

    private final int inBlockSize;

    @Inject
    public RhsmApiCompatCurator(Provider<EntityManager> entityManager, Configuration config) {
        Provider<EntityManager> provider = Objects.requireNonNull(entityManager);
        this.config = Objects.requireNonNull(config);

        this.entityManager = provider.get();

        inBlockSize = this.config.getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);
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
    public List<ConsumerEntitlementCount> getConsumerEntitlementCounts(Collection<String> consumerIds,
        Collection<String> consumerUuids) {

        consumerIds = consumerIds == null ? List.of() : consumerIds;
        consumerUuids = consumerUuids == null ? List.of() : consumerUuids;
        if (consumerIds.isEmpty() && consumerUuids.isEmpty()) {
            return List.of();
        }

        String jpql = "SELECT new org.candlepin.model.ConsumerEntitlementCount(consumer.id, consumer.uuid, " +
            "pool.contractNumber, pss.subscriptionId, MAX(prod.id), MAX(prod.name), SUM(ent.quantity)) " +
            "FROM Entitlement ent " +
            "JOIN ent.consumer consumer " +
            "JOIN ent.pool pool " +
            "JOIN pool.product prod " +
            "LEFT JOIN pool.sourceSubscription pss " +
            "WHERE consumer.id IN (:consumerIds) OR consumer.uuid IN (:consumerUuids) " +
            "GROUP BY consumer.id, consumer.uuid, pool.contractNumber, pss.subscriptionId, prod.uuid " +
            "ORDER BY consumer.id, consumer.uuid ASC";

        TypedQuery<ConsumerEntitlementCount> query = this.entityManager
            .createQuery(jpql, ConsumerEntitlementCount.class);

        List<ConsumerEntitlementCount> consumerEntCounts = new ArrayList<>();
        Iterator<List<String>> idIterator = Iterables.partition(consumerIds, this.inBlockSize).iterator();
        Iterator<List<String>> uuidIterator = Iterables.partition(consumerUuids, this.inBlockSize).iterator();
        while (idIterator.hasNext() || uuidIterator.hasNext()) {
            List<String> consumerIdBlock = idIterator.hasNext() ? idIterator.next() : List.of();
            List<String> consumerUuidBlock = uuidIterator.hasNext() ? uuidIterator.next() : List.of();

            query.setParameter("consumerIds", consumerIdBlock);
            query.setParameter("consumerUuids", consumerUuidBlock);

            consumerEntCounts.addAll(query.getResultList());
        }

        return consumerEntCounts;
    }

    /**
     * Retrieves a list of {@link ConsumerFeed} objects for a given owner. The resulting list is paginated
     * and ordered deterministically by ID and last check-in. For each consumer, relevant facts and
     * installed products are extracted, and if the consumer represents a virtual machine (as indicated by
     * the "virt.uuid" fact), an associated hypervisor is looked up by matching all possible UUID
     * representations.
     *
     * @param owner         the {@link Owner} whose consumers are to be fetched; must not be null
     * @param afterId       (optional) only consumers with an ID lexicographically greater than this value
     *                      will be included
     * @param afterUuid     (optional) only consumers with a UUID lexicographically greater than this value
     *                      will be included
     * @param afterCheckin  (optional) only consumers with a last check-in timestamp after this value will
     *                      be included
     * @param page          the page number to retrieve (1-based)
     * @param perPage       the number of consumers to return per page
     * @return a list of {@link ConsumerFeed} objects matching the specified filters, with facts, installed
     *         products, add-ons, and hypervisor association (if applicable) fully populated
     */
    public List<ConsumerFeed> getConsumerFeed(Owner owner, String afterId, String afterUuid,
        OffsetDateTime afterCheckin, int page, int perPage) {

        List<ConsumerFeed> consumersFiltered = getConsumersFiltered(owner.getOwnerKey(), afterId, afterUuid,
            afterCheckin, page, perPage);
        Map<String, HypervisorGuestMapping> hypervisorGuestMappings =
            getHypervisorGuestMappingsByConsumer(owner.getOwnerId());
        Map<String, Set<String>> addOnsByConsumer =
            getAddOnsByConsumer(owner.getOwnerId(), afterId, afterUuid, afterCheckin, page, perPage);
        Map<String, Map<String, String>> factsByConsumer =
            getFactsByConsumer(owner.getOwnerId(), afterId, afterUuid, afterCheckin, page, perPage);
        Map<String, Set<ConsumerFeedInstalledProduct>> installedProductsByConsumer =
            getInstalledProductsByConsumer(owner.getOwnerId(), afterId, afterUuid, afterCheckin, page,
                perPage);

        for (ConsumerFeed consumerFeed : consumersFiltered) {
            Map<String, String> facts = factsByConsumer.get(consumerFeed.getId());

            // We must match the consumer's "virt.uuid" fact against all possible endianness variants,
            // because guest IDs can be stored in partially reversed byte order in the database.
            // By generating all possible UUID representations, we can correctly associate the consumer
            // with its hypervisor if a mapping exists.
            if (facts != null) {
                String virtUuid = facts.get("virt.uuid");
                if (virtUuid != null && !virtUuid.isEmpty()) {
                    List<String> possibleUuids = Util.getPossibleUuids(virtUuid);
                    for (String possible : possibleUuids) {
                        HypervisorGuestMapping mapping = hypervisorGuestMappings.get(possible.toLowerCase());
                        if (mapping != null) {
                            consumerFeed.setHypervisorUuid(mapping.hypervisorUuid())
                                .setHypervisorName(mapping.hypervisorName())
                                .setGuestId(mapping.guestId());
                            break;
                        }
                    }
                }
            }

            consumerFeed
                .setFacts(ConsumerFeedFactExtractor.extractRelevantFacts(facts))
                .setInstalledProducts(installedProductsByConsumer.get(consumerFeed.getId()))
                .setSyspurposeAddons(addOnsByConsumer.get(consumerFeed.getId()));
        }

        return consumersFiltered;
    }

    /**
     * Retrieves list of {@link ConsumerFeed} objects for a given owner,
     * filtered optionally by consumer ID, UUID, and last check-in timestamp.
     * <p>
     * This method constructs a dynamic HQL query to fetch consumers matching the provided filters,
     * ordered deterministically by ID and last check-in timestamp (ascending). The query is paginated
     * using the specified page and perPage parameters.
     * <p>
     * Only non-null filter parameters are applied. If no filters are provided, all consumers for the given
     * owner are returned.
     *
     * @param ownerKey     the key of the owner whose consumers are to be fetched; must not be null or empty
     * @param afterId      (optional) only consumers with an ID lexicographically greater than this value
     *                     will be included
     * @param afterUuid    (optional) only consumers with a UUID lexicographically greater than this value
     *                     will be included
     * @param afterCheckin (optional) only consumers with a last check-in timestamp after this value will
     *                     be included
     * @param page         the page number to retrieve
     * @param perPage      the number of consumers to return per page
     * @return a list of {@link ConsumerFeed} matching the given filters and paging criteria
     */
    private List<ConsumerFeed> getConsumersFiltered(String ownerKey, String afterId, String afterUuid,
        OffsetDateTime afterCheckin, int page, int perPage) {

        StringBuilder hql = new StringBuilder(
            "SELECT new org.candlepin.model.ConsumerFeed(" +
                "c.id, c.uuid, c.name, c.typeId, c.owner.key, c.lastCheckin, " +
                "c.serviceLevel, c.role)" +
                "FROM Consumer c WHERE c.owner.key = :owner");

        if (afterId != null) {
            hql.append(" AND c.id > :afterId");
        }
        if (afterUuid != null) {
            hql.append(" AND c.uuid > :afterUuid");
        }
        if (afterCheckin != null) {
            hql.append(" AND c.lastCheckin > :afterCheckin");
        }

        hql.append(" ORDER BY c.id ASC, c.lastCheckin ASC");

        TypedQuery<ConsumerFeed> query = entityManager.createQuery(hql.toString(), ConsumerFeed.class)
            .setParameter("owner", ownerKey);

        if (afterId != null) {
            query.setParameter("afterId", afterId);
        }
        if (afterUuid != null) {
            query.setParameter("afterUuid", afterUuid);
        }
        if (afterCheckin != null) {
            query.setParameter("afterCheckin", Date.from(afterCheckin.toInstant()));
        }

        int offset = (page - 1) * perPage;
        query.setFirstResult(offset);
        query.setMaxResults(perPage);

        return query.getResultList();
    }

    /**
     * Retrieves a mapping from guest UUID variants (including all endianness forms) to
     * {@link HypervisorGuestMapping} for all hypervisor-guest relationships within a given owner. Used for
     * efficient resolution of hypervisor information for consumers based on "virt.uuid" facts.
     * <p>
     * The mapping includes all recognized UUID encodings, as required by Candlepin's guest mapping rules.
     * If multiple mappings exist for the same guest, only the most recent (by update timestamp, creation
     * timestamp, and entry ID) is included, using database-specific logic for ranking or aggregation.
     *
     * @param ownerId the ID of the owner whose hypervisor-guest mappings are to be retrieved;
     *                must not be null
     * @return a map where each key is a lower-cased guest UUID variant and each value is the corresponding
     *         {@link HypervisorGuestMapping}
     */
    private Map<String, HypervisorGuestMapping> getHypervisorGuestMappingsByConsumer(String ownerId) {
        String sql;
        String databaseDialect = getDatabaseDialect();

        if (databaseDialect.contains("mysql") || databaseDialect.contains("maria") ||
            databaseDialect.contains("postgres")) {
            sql =
                "SELECT hypervisor.id, hypervisor.uuid, hypervisor.name, guest.guest_id " +
                    "FROM ( " +
                    "  SELECT hypervisor.id AS hypervisor_id, hypervisor.uuid AS hypervisor_uuid, " +
                    "         guest.guest_id, " +
                    "         DENSE_RANK() OVER (PARTITION BY guest.guest_id " +
                    "           ORDER BY guest.updated DESC, guest.created DESC, guest.id ASC) AS rank " +
                    "    FROM cp_consumer hypervisor " +
                    "    JOIN cp_consumer_guests guest ON guest.consumer_id = hypervisor.id " +
                    "   WHERE hypervisor.owner_id = :owner_id " +
                    ") hgmap " +
                    "WHERE rank = 1";
        }
        else {
            sql =
                "SELECT hypervisor.id, hypervisor.uuid, hypervisor.name, guest.guest_id " +
                    "FROM cp_consumer hypervisor " +
                    "JOIN cp_consumer_guests guest ON guest.consumer_id = hypervisor.id " +
                    "JOIN ( " +
                    "    SELECT guest_id, MAX(updated) AS last_updated " +
                    "    FROM cp_consumer_guests " +
                    "    GROUP BY guest_id " +
                    ") glu " +
                    "ON glu.guest_id = guest.guest_id AND glu.last_updated = guest.updated " +
                    "WHERE hypervisor.owner_id = :owner_id";
        }

        List<Object[]> rows = entityManager.createNativeQuery(sql)
            .setParameter("owner_id", ownerId)
            .getResultList();

        Map<String, HypervisorGuestMapping> result = new HashMap<>();
        for (Object[] row : rows) {
            String hypervisorId = (String) row[0];
            String hypervisorUuid = (String) row[1];
            String hypervisorName = (String) row[2];
            String guestId = (String) row[3];
            HypervisorGuestMapping mapping = new HypervisorGuestMapping(
                hypervisorId, hypervisorName, hypervisorUuid, guestId);

            // Since we know there will be at most 1000 consumer records, we can preload all mappings.
            // This avoids looping through all consumer feeds for each lookup and improves performance.
            List<String> guestIdVariants = Util.getPossibleUuids(guestId);
            for (String variant : guestIdVariants) {
                result.put(variant.toLowerCase(), mapping);
            }
        }
        return result;
    }

    /**
     * Retrieves a mapping from consumer ID to a list of {@link ConsumerInstalledProduct} for all
     * installed products belonging to consumers matching the specified owner and filter criteria.
     *
     * @param ownerId      the ID of the owner whose consumers' products are to be fetched; must not be null
     * @param afterId      (optional) only consumers with an ID lexicographically greater than this value
     *                     will be included
     * @param afterUuid    (optional) only consumers with a UUID lexicographically greater than this value
     *                     will be included
     * @param afterCheckin (optional) only consumers with a last check-in timestamp after this value will
     *                     be included
     * @param page         the page number to retrieve
     * @param perPage      the number of consumers to return per page
     * @return a map where each key is a consumer ID and each value is a list of installed products
     * for that consumer
     */
    private Map<String, Set<ConsumerFeedInstalledProduct>> getInstalledProductsByConsumer(String ownerId,
        String afterId, String afterUuid, OffsetDateTime afterCheckin, int page, int perPage) {
        String cte = buildConsumersCte(afterId, afterUuid, afterCheckin);
        String sql =
            "SELECT ip.consumer_id, ip.product_id, ip.product_name, ip.product_version " +
                "FROM cp_installed_products ip " +
                "JOIN (" + cte + ") cids ON cids.id = ip.consumer_id";

        Query q = entityManager.createNativeQuery(sql);
        setConsumerParams(q, ownerId, afterId, afterUuid, afterCheckin, page, perPage);

        List<Object[]> rows = q.getResultList();
        Map<String, Set<ConsumerFeedInstalledProduct>> result = new HashMap<>();
        for (Object[] row : rows) {
            String consumerId = String.valueOf(row[0]);
            String productId = String.valueOf(row[1]);
            String productName = String.valueOf(row[2]);
            String productVersion = String.valueOf(row[3]);
            result.computeIfAbsent(consumerId, k -> new HashSet<>())
                .add(new ConsumerFeedInstalledProduct(productId, productName, productVersion));
        }
        return result;
    }

    /**
     * Retrieves a mapping from consumer ID to a set of add-on names for all consumers matching the specified
     * owner and filter criteria.
     *
     * @param ownerId      the ID of the owner whose consumers' add-ons are to be fetched; must not be null
     * @param afterId      (optional) only consumers with an ID lexicographically greater than this value
     *                     will be included
     * @param afterUuid    (optional) only consumers with a UUID lexicographically greater than this value
     *                     will be included
     * @param afterCheckin (optional) only consumers with a last check-in timestamp after this value will
     *                     be included
     * @param page         the page number to retrieve
     * @param perPage      the number of consumers to return per page
     * @return a map where each key is a consumer ID and each value is a set of add-on names for that consumer
     */
    private Map<String, Set<String>> getAddOnsByConsumer(String ownerId, String afterId, String afterUuid,
        OffsetDateTime afterCheckin, int page, int perPage) {
        String cte = buildConsumersCte(afterId, afterUuid, afterCheckin);
        String sql =
            "SELECT spao.consumer_id, spao.add_on " +
                "FROM cp_sp_add_on spao " +
                "JOIN (" + cte + ") cids ON cids.id = spao.consumer_id";

        Query q = entityManager.createNativeQuery(sql);
        setConsumerParams(q, ownerId, afterId, afterUuid, afterCheckin, page, perPage);

        List<Object[]> rows = q.getResultList();
        Map<String, Set<String>> result = new HashMap<>();
        for (Object[] row : rows) {
            String consumerId = String.valueOf(row[0]);
            String addOn = String.valueOf(row[1]);
            result.computeIfAbsent(consumerId, k -> new HashSet<>()).add(addOn);
        }
        return result;
    }

    /**
     * Retrieves a mapping from consumer ID to a map of fact keys and values for all consumers matching
     * the specified owner and filter criteria.
     *
     * @param ownerId      the ID of the owner whose consumers' facts are to be fetched; must not be null
     * @param afterId      (optional) only consumers with an ID lexicographically greater than this value
     *                     will be included
     * @param afterUuid    (optional) only consumers with a UUID lexicographically greater than this value
     *                     will be included
     * @param afterCheckin (optional) only consumers with a last check-in timestamp after this value will
     *                     be included
     * @param page         the page number to retrieve
     * @param perPage      the number of consumers to return per page
     * @return a map where each key is a consumer ID and each value is a map of fact key-value pairs
     * for that consumer
     */
    private Map<String, Map<String, String>> getFactsByConsumer(String ownerId, String afterId,
        String afterUuid, OffsetDateTime afterCheckin, int page, int perPage) {
        String cte = buildConsumersCte(afterId, afterUuid, afterCheckin);
        String sql =
            "SELECT fact.cp_consumer_id AS consumer_id, fact.mapkey, fact.element " +
                "FROM cp_consumer_facts fact " +
                "JOIN (" + cte + ") cids ON cids.id = fact.cp_consumer_id";

        Query q = entityManager.createNativeQuery(sql);
        setConsumerParams(q, ownerId, afterId, afterUuid, afterCheckin, page, perPage);

        List<Object[]> rows = q.getResultList();
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Object[] row : rows) {
            String consumerId = String.valueOf(row[0]);
            String mapKey = String.valueOf(row[1]);
            String value = String.valueOf(row[2]);
            result.computeIfAbsent(consumerId, k -> new HashMap<>()).put(mapKey, value);
        }
        return result;
    }

    private String buildConsumersCte(String afterId, String afterUuid, OffsetDateTime afterCheckin) {
        StringBuilder sb = new StringBuilder("SELECT id FROM cp_consumer WHERE owner_id = :owner_id");
        if (afterId != null) {
            sb.append(" AND id > :after_id");
        }
        if (afterUuid != null) {
            sb.append(" AND uuid > :after_uuid");
        }
        if (afterCheckin != null) {
            sb.append(" AND lastcheckin > :after_checkin");
        }
        sb.append(" ORDER BY id ASC, lastcheckin ASC OFFSET :offset LIMIT :limit");
        return sb.toString();
    }

    private void setConsumerParams(Query q, String ownerId, String afterId, String afterUuid,
        OffsetDateTime afterCheckin, int page, int perPage) {
        q.setParameter("owner_id", ownerId);
        if (afterId != null) {
            q.setParameter("after_id", afterId);
        }
        if (afterUuid != null) {
            q.setParameter("after_uuid", afterUuid);
        }
        if (afterCheckin != null) {
            q.setParameter("after_checkin", afterCheckin);
        }

        int offset = (page - 1) * perPage;
        q.setParameter("offset", offset);
        q.setParameter("limit", perPage);
    }


    private String getDatabaseDialect() {
        Session session = entityManager.unwrap(Session.class);
        SessionFactoryImplementor sfi = (SessionFactoryImplementor) session.getSessionFactory();
        Dialect dialect = sfi.getJdbcServices().getDialect();
        return dialect.toString().toLowerCase();
    }
}
