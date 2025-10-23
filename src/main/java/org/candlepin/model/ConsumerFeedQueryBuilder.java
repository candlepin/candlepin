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

import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.hibernate.query.NativeQuery;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

/**
 * Purpose-built, stateful query builder that represents the end-to-end
 * construction of the “consumer feed” view.
 */
public class ConsumerFeedQueryBuilder extends QueryBuilder<ConsumerFeedQueryBuilder, ConsumerFeed> {

    private String ownerId;
    private String ownerKey;
    private String afterId;
    private String afterUuid;
    private OffsetDateTime afterCheckin;
    private Integer page;
    private Integer perPage;

    public ConsumerFeedQueryBuilder(Provider<EntityManager> emProvider) {
        super(emProvider);
    }

    /**
     * Sets the owner this query targets.
     *
     * @param owner
     *  owner entity with non-null {@code id} (DB FK) and {@code key} (natural key)
     *
     * @throws IllegalArgumentException
     *  if {@code owner.getId()} or {@code owner.getKey()} is null
     *
     * @return this builder
     */
    public ConsumerFeedQueryBuilder setOwner(Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (owner.getId() == null) {
            throw new IllegalArgumentException("owner ID is null");
        }

        if (owner.getKey() == null) {
            throw new IllegalArgumentException("owner key is null");
        }

        this.ownerId = owner.getId();
        this.ownerKey = owner.getKey();

        return this;
    }

    /**
     * Sets a lower bound on consumer {@code id} for filtering.
     *
     * @param afterId
     *  return rows with {@code id > afterId}; may be {@code null}
     *
     * @return this builder
     */
    public ConsumerFeedQueryBuilder setAfterId(String afterId) {
        this.afterId = afterId;
        return this;
    }

    /**
     * Sets a lower bound on consumer {@code uuid} for filtering.
     *
     * @param afterUuid
     *  return rows with {@code uuid > afterUuid}; may be {@code null}
     *
     * @return this builder
     */
    public ConsumerFeedQueryBuilder setAfterUuid(String afterUuid) {
        this.afterUuid = afterUuid;
        return this;
    }

    /**
     * Sets a lower bound on consumer {@code lastCheckin} timestamp for filtering.
     *
     * @param afterCheckin
     *  return rows with {@code lastCheckin > afterCheckin}; may be {@code null}
     *
     * @return this builder
     */
    public ConsumerFeedQueryBuilder setAfterCheckin(OffsetDateTime afterCheckin) {
        this.afterCheckin = afterCheckin;
        return this;
    }

    /**
     * Sets page and page size for offset/limit pagination.
     * <p>
     * Note: ordering is deterministic ({@code id ASC, lastCheckin ASC}) to keep pages stable.
     * </p>
     *
     * @param page
     *  1-based page number; may be {@code null} to disable paging
     *
     * @param perPage
     *  page size; may be {@code null} to disable paging
     *
     * @return this builder
     */
    public ConsumerFeedQueryBuilder setPaging(Integer page, Integer perPage) {
        this.page = page;
        this.perPage = perPage;
        return this;
    }

    /**
     * Helper that builds the consumer-id subquery and fills provided args map with parameters.
     */
    private String buildConsumerIdSubquery(Map<String, Object> args) {
        if (this.ownerId == null) {
            throw new IllegalStateException("Owner must be set before building the query.");
        }

        StringBuilder sb = new StringBuilder("SELECT id FROM cp_consumer WHERE owner_id = :owner_id");
        args.put("owner_id", this.ownerId);

        if (this.afterId != null) {
            sb.append(" AND id > :after_id");
            args.put("after_id", this.afterId);
        }
        if (this.afterUuid != null) {
            sb.append(" AND uuid > :after_uuid");
            args.put("after_uuid", this.afterUuid);
        }
        if (this.afterCheckin != null) {
            sb.append(" AND lastcheckin > :after_checkin");
            args.put("after_checkin", this.afterCheckin);
        }

        sb.append(" ORDER BY id ASC, lastcheckin ASC");

        if (this.page != null && this.perPage != null) {
            sb.append(" LIMIT :limit OFFSET :offset");
            args.put("limit", this.perPage);
            args.put("offset", Math.max(0, (this.page - 1) * this.perPage));
        }

        return sb.toString();
    }

    /**
     * Builds the primary JPQL projection of {@link ConsumerFeed} rows using
     * {@code ownerKey} and configured {@code after*} filters.
     * <p>
     * Ordering is {@code id ASC, lastCheckin ASC}. If paging is configured,
     * it is applied to the JPQL query as well (to keep base rows aligned with subquery).
     * </p>
     *
     * @return prepared {@link TypedQuery} that yields {@link ConsumerFeed} DTOs
     */
    private TypedQuery<ConsumerFeed> buildConsumerFeedProjection() {
        Map<String, Object> params = new HashMap<>();
        EntityManager em = this.getEntityManager();

        StringBuilder hql = new StringBuilder(
            "SELECT new org.candlepin.model.ConsumerFeed(" +
            "c.id, c.uuid, c.name, c.typeId, c.owner.key, c.lastCheckin, c.serviceLevel, c.role) " +
            "FROM Consumer c WHERE c.owner.key = :ownerKey"
        );

        params.put("ownerKey", this.ownerKey);

        if (this.afterId != null) {
            hql.append(" AND c.id > :afterId");
            params.put("afterId", this.afterId);
        }

        if (this.afterUuid != null) {
            hql.append(" AND c.uuid > :afterUuid");
            params.put("afterUuid", this.afterUuid);
        }

        if (this.afterCheckin != null) {
            hql.append(" AND c.lastCheckin > :afterCheckin");
            params.put("afterCheckin", Date.from(this.afterCheckin.toInstant()));
        }

        hql.append(" ORDER BY c.id ASC, c.lastCheckin ASC");

        TypedQuery<ConsumerFeed> consumerFeedQuery = em.createQuery(hql.toString(), ConsumerFeed.class);
        params.forEach(consumerFeedQuery::setParameter);

        if (this.page != null && this.perPage != null) {
            consumerFeedQuery.setFirstResult(Math.max(0, (this.page - 1) * this.perPage));
            consumerFeedQuery.setMaxResults(this.perPage);
        }

        return consumerFeedQuery;
    }

    /**
     * Fetches installed products grouped by consumer id for the current selection.
     *
     * @return map {@code consumerId -> set(installed products)}
     */
    private Map<String, Set<ConsumerFeedInstalledProduct>> fetchInstalledProducts() {
        Map<String, Set<ConsumerFeedInstalledProduct>> map = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        EntityManager em = this.getEntityManager();

        String sub = this.buildConsumerIdSubquery(params);

        String sql =
            "SELECT ip.consumer_id, ip.product_id, ip.product_name, ip.product_version " +
            "FROM cp_installed_products ip " +
            "JOIN (" + sub + ") cids ON cids.id = ip.consumer_id";

        Query nativeQuery = em.createNativeQuery(sql);
        params.forEach(nativeQuery::setParameter);

        for (Object[] row : (List<Object[]>) nativeQuery.getResultList()) {
            String cid  = String.valueOf(row[0]);
            String pid  = String.valueOf(row[1]);
            String name = String.valueOf(row[2]);
            String ver  = String.valueOf(row[3]);

            map.computeIfAbsent(cid, k -> new HashSet<>())
                .add(new ConsumerFeedInstalledProduct(pid, name, ver));
        }

        return map;
    }

    /**
     * Fetches system purpose add-ons grouped by consumer id for the current selection.
     *
     * @return map {@code consumerId -> set(addOn name)}
     */
    private Map<String, Set<String>> fetchAddOns() {
        Map<String, Set<String>> map = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        EntityManager em = this.getEntityManager();

        String sub = this.buildConsumerIdSubquery(params);

        String sql =
            "SELECT spao.consumer_id, spao.add_on " +
            "FROM cp_sp_add_on spao " +
            "JOIN (" + sub + ") cids ON cids.id = spao.consumer_id";

        Query nativeQuery = em.createNativeQuery(sql);
        params.forEach(nativeQuery::setParameter);

        for (Object[] row : (List<Object[]>) nativeQuery.getResultList()) {
            String cid = String.valueOf(row[0]);
            String addon = String.valueOf(row[1]);

            map.computeIfAbsent(cid, k -> new HashSet<>()).add(addon);
        }

        return map;
    }

    /**
     * Fetches raw facts grouped by consumer id for the current selection.
     *
     * @return map {@code consumerId -> map(factKey -> value)}
     */
    private Map<String, Map<String, String>> fetchFacts() {
        Map<String, Map<String, String>> map = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        EntityManager em = this.getEntityManager();

        String sub = this.buildConsumerIdSubquery(params);

        String sql =
            "SELECT fact.cp_consumer_id, fact.mapkey, fact.element " +
            "FROM cp_consumer_facts fact " +
            "JOIN (" + sub + ") cids ON cids.id = fact.cp_consumer_id";

        Query nativeQuery = em.createNativeQuery(sql);
        params.forEach(nativeQuery::setParameter);

        for (Object[] row : (List<Object[]>) nativeQuery.getResultList()) {
            String cid = String.valueOf(row[0]);
            String key = String.valueOf(row[1]);
            String val = String.valueOf(row[2]);

            map.computeIfAbsent(cid, k -> new HashMap<>()).put(key, val);
        }

        return map;
    }

    /**
     * Builds a lookup for the latest hypervisor→guest mappings per {@code guest_id}
     * within the current owner.
     * <p>
     * To support Candlepin's guest UUID matching rules, all endianness/representation
     * variants of {@code guest_id} are expanded via {@link Util#getPossibleUuids(String...)}
     * and indexed in lower-case.
     * </p>
     *
     * @return map {@code lowerCase(guestUuidVariant) -> HypervisorGuestMapping}
     */
    private Map<String, HypervisorGuestMapping> fetchHypervisorGuestMap() {
        EntityManager em = this.getEntityManager();

        String sql =
            "SELECT hypervisor.uuid, hypervisor.name, guest.guest_id " +
            "FROM cp_consumer hypervisor " +
            "JOIN cp_consumer_guests guest ON guest.consumer_id = hypervisor.id " +
            "JOIN (" +
                "SELECT guest_id, MAX(updated) AS last_updated " +
                "FROM cp_consumer_guests " +
                "GROUP BY guest_id) glu " +
            "  ON glu.guest_id = guest.guest_id AND glu.last_updated = guest.updated " +
            "WHERE hypervisor.owner_id = :owner_id";

        List<Object[]> rows = em.createNativeQuery(sql)
            .unwrap(NativeQuery.class)
            .setParameter("owner_id", ownerId)
            .getResultList();

        Map<String, HypervisorGuestMapping> map = new HashMap<>();
        for (Object[] row : rows) {
            String hypUuid = (String) row[0];
            String hypName = (String) row[1];
            String guestId = (String) row[2];

            HypervisorGuestMapping hypervisorGuestMapping =
                new HypervisorGuestMapping(hypName, hypUuid, guestId);
            Util.getPossibleUuids(guestId)
                .forEach(uuid -> map.put(uuid.toLowerCase(), hypervisorGuestMapping));
        }

        return map;
    }

    /**
     * Counts consumers matching the current filters.
     *
     * @return total count of matching consumers
     */
    @Override
    public long getResultCount() {
        Map<String, Object> params = new HashMap<>();
        EntityManager em = this.getEntityManager();

        String sub = this.buildConsumerIdSubquery(params);

        String sql = "SELECT COUNT(*) FROM (" + sub + ") x";

        Query nativeQuery = em.createNativeQuery(sql);
        params.forEach(nativeQuery::setParameter);

        Number count = (Number) nativeQuery.getSingleResult();
        return count.longValue();
    }

    /**
     * Fetches base {@link ConsumerFeed} rows and enriches them with facts, add-ons,
     * installed products, and (if applicable) hypervisor linkage determined from
     * {@code virt.uuid} fact (considering all UUID variant encodings).
     *
     * @return list of fully populated {@link ConsumerFeed} DTOs
     */
    @Override
    public List<ConsumerFeed> getResultList() {
        List<ConsumerFeed> feeds = this.buildConsumerFeedProjection().getResultList();

        if (feeds.isEmpty()) {
            return feeds;
        }

        Map<String, Map<String, String>> facts = this.fetchFacts();
        Map<String, Set<String>> addons = this.fetchAddOns();
        Map<String, Set<ConsumerFeedInstalledProduct>> installedProducts = this.fetchInstalledProducts();
        Map<String, HypervisorGuestMapping> hypMap = this.fetchHypervisorGuestMap();

        for (ConsumerFeed feed : feeds) {
            Map<String, String> consumerFacts = facts.get(feed.getId());
            if (consumerFacts != null) {
                String virtUuid = consumerFacts.get("virt.uuid");
                if (virtUuid != null && !virtUuid.isBlank()) {
                    for (String possibleUuid : Util.getPossibleUuids(virtUuid)) {
                        HypervisorGuestMapping map = hypMap.get(possibleUuid.toLowerCase());
                        if (map != null) {
                            feed.setHypervisorUuid(map.hypervisorUuid())
                                .setHypervisorName(map.hypervisorName())
                                .setGuestId(virtUuid);
                            break;
                        }
                    }
                }
            }
            feed.setFacts(ConsumerFeedFactExtractor.extractRelevantFacts(consumerFacts))
                .setInstalledProducts(installedProducts.get(feed.getId()))
                .setSyspurposeAddons(addons.get(feed.getId()));
        }

        return feeds;
    }

    /**
     * Stream view over {@link #getResultList()}.
     *
     * @return stream of {@link ConsumerFeed} DTOs
     */
    @Override
    public Stream<ConsumerFeed> getResultStream() {
        return this.getResultList().stream();
    }

    /**
     * Utility for filtering/allowlisting facts that should be exposed via {@link ConsumerFeed}.
     */
    public static class ConsumerFeedFactExtractor {

        private ConsumerFeedFactExtractor() {
            // Intentionally left empty
        }

        // List of allowed fact keys (exact matches)
        private static final Set<String> ALLOWED_FACTS = Set.of(
            "band.storage.usage",
            "bios.version",
            "conversions.activity",
            "cpu.core(s)_per_socket",
            "cpu.cpu(s)",
            "cpu.cpu_socket(s)",
            "cpu.thread(s)_per_core",
            "distribution.name",
            "distribution.version",
            "dmi.bios.vendor",
            "dmi.bios.version",
            "dmi.chassis.asset_tag",
            "dmi.system.manufacturer",
            "dmi.system.product_name",
            "dmi.system.uuid",
            "insights_id",
            "memory.memtotal",
            "network.fqdn",
            "network.hostname",
            "network.ipv4_address",
            "network.ipv6_address",
            "uname.machine",
            "uname.nodename",
            "virt.is_guest"
        );

        // Cloud fact prefixes
        private static final List<String> CLOUD_FACT_PREFIXES = List.of(
            "azure_",
            "aws_",
            "ocm.",
            "openshift.",
            "gcp_"
        );

        // Combined regex for network interface facts
        private static final Pattern NET_INTERFACE_PATTERN =
            Pattern.compile(
                "^net\\.interface\\..+?\\.(?:mac_address|ipv[46]_address(?:\\.global|\\.link)?(?:_list)?)$");

        /**
         * Extracts only the relevant facts from the given map, based on allowed keys and patterns.
         *
         * @param allFacts a map of all available facts (key -> value)
         * @return a filtered map containing only the allowed facts
         */
        public static Map<String, String> extractRelevantFacts(Map<String, String> allFacts) {
            Map<String, String> result = new HashMap<>();
            if (allFacts == null || allFacts.isEmpty()) {
                return result;
            }

            for (Map.Entry<String, String> entry : allFacts.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (ALLOWED_FACTS.contains(key) || CLOUD_FACT_PREFIXES.stream().anyMatch(key::startsWith) ||
                    NET_INTERFACE_PATTERN.matcher(key).matches()) {
                    result.put(key, value);
                }
            }
            return result;
        }
    }
}
