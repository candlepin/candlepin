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

import org.candlepin.auth.Principal;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.paging.PageRequest;
import org.candlepin.util.FactValidator;
import org.candlepin.util.Util;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Hibernate;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * ConsumerCurator
 */
@Singleton
public class ConsumerCurator extends AbstractHibernateCurator<Consumer> {
    private static final Logger log = LoggerFactory.getLogger(ConsumerCurator.class);

    /** Defines the maximum number of consumer facts that can be provided for a single query */
    public static final int MAX_CONSUMER_FACTS_PER_QUERY = 10;

    /** Regular expression used to convert fact expressions to SQL-safe LIKE expressions */
    private static final Pattern FACT_TRANSLATION_REGEX = Pattern.compile("(\\\\?+)([*?]|(?<=\\\\)\\\\)");

    /**
     * Container object for providing various arguments to the consumer lookup method(s).
     */
    public static class ConsumerQueryArguments extends QueryArguments<ConsumerQueryArguments> {

        private Owner owner;
        private String username;
        private Collection<String> uuids;
        private Collection<ConsumerType> types;
        private Collection<String> hypervisorIds;
        private Map<String, Collection<String>> facts;
        private String environmentId;

        public ConsumerQueryArguments setOwner(Owner owner) {
            this.owner = owner;
            return this;
        }

        public Owner getOwner() {
            return this.owner;
        }

        public ConsumerQueryArguments setUsername(String username) {
            this.username = username;
            return this;
        }

        public String getUsername() {
            return this.username;
        }

        public ConsumerQueryArguments setUuids(Collection<String> uuids) {
            this.uuids = uuids;
            return this;
        }

        public ConsumerQueryArguments setUuids(String... uuids) {
            return this.setUuids(uuids != null ? Arrays.asList(uuids) : null);
        }

        public Collection<String> getUuids() {
            return this.uuids;
        }

        public ConsumerQueryArguments setTypes(Collection<ConsumerType> types) {
            this.types = types;
            return this;
        }

        public ConsumerQueryArguments setTypes(ConsumerType... types) {
            return this.setTypes(types != null ? Arrays.asList(types) : null);
        }

        public Collection<ConsumerType> getTypes() {
            return this.types;
        }

        public ConsumerQueryArguments setHypervisorIds(Collection<String> hypervisorIds) {
            this.hypervisorIds = hypervisorIds;
            return this;
        }

        public ConsumerQueryArguments setHypervisorIds(String... hypervisorIds) {
            return this.setHypervisorIds(hypervisorIds != null ? Arrays.asList(hypervisorIds) : null);
        }

        public Collection<String> getHypervisorIds() {
            return this.hypervisorIds;
        }

        public ConsumerQueryArguments addFact(String fact, String value) {
            if (fact == null || fact.isEmpty()) {
                throw new IllegalArgumentException("fact is null or empty");
            }

            if (this.facts == null) {
                this.facts = new HashMap<>();
            }

            this.facts.computeIfAbsent(fact, key -> new HashSet<>())
                .add(value);

            return this;
        }

        public Map<String, Collection<String>> getFacts() {
            return this.facts;
        }

        public ConsumerQueryArguments setEnvironmentId(String environmentId) {
            this.environmentId = environmentId;
            return this;
        }

        public String getEnvironmentId() {
            return this.environmentId;
        }
    }

    private final EntitlementCurator entitlementCurator;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final DeletedConsumerCurator deletedConsumerCurator;
    private final FactValidator factValidator;
    private final Provider<HostCache> cachedHostsProvider;
    private final PrincipalProvider principalProvider;

    @Inject
    public ConsumerCurator(EntitlementCurator entitlementCurator, ConsumerTypeCurator consumerTypeCurator,
        DeletedConsumerCurator deletedConsumerCurator, FactValidator factValidator,
        Provider<HostCache> cachedHostsProvider, PrincipalProvider principalProvider) {
        super(Consumer.class);
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.deletedConsumerCurator = Objects.requireNonNull(deletedConsumerCurator);
        this.factValidator = Objects.requireNonNull(factValidator);
        this.cachedHostsProvider = Objects.requireNonNull(cachedHostsProvider);
        this.principalProvider = Objects.requireNonNull(principalProvider);
    }

    @Override
    @Transactional
    public Consumer create(Consumer entity, boolean flush) {
        entity.ensureUUID();
        this.validateFacts(entity);
        return super.create(entity, flush);
    }

    @Override
    @Transactional
    public void delete(Consumer entity) {
        log.debug("Deleting consumer: {}", entity);

        // Fetch the principal that's triggering this
        Principal principal = this.principalProvider.get();

        Owner owner = entity.getOwner();

        // Check if we've already got a record for this consumer (???), creating one if necessary
        DeletedConsumer deletedConsumer = this.deletedConsumerCurator.findByConsumerUuid(entity.getUuid());
        if (deletedConsumer == null) {
            deletedConsumer = new DeletedConsumer();
        }

        // Set/update the properties on our deleted consumer record
        deletedConsumer.setConsumerUuid(entity.getUuid())
            .setOwnerId(entity.getOwnerId())
            .setOwnerKey(owner.getKey())
            .setConsumerName(entity.getName())
            .setOwnerDisplayName(owner.getDisplayName())
            .setPrincipalName(principal != null ? principal.getName() : null);

        // Actually delete the consumer
        super.delete(entity);

        // Save our deletion record
        this.deletedConsumerCurator.saveOrUpdate(deletedConsumer);
    }

    /**
     * Lookup consumer by its virt.uuid.
     *
     * In some cases the hypervisor will report UUIDs with uppercase, while the guest will report
     * lowercase. As such we do case-insensitive comparison when looking these up.
     *
     * @param uuid
     *     consumer virt.uuid to find
     * @return Consumer whose name matches the given virt.uuid, null otherwise.
     */
    public Consumer findByVirtUuid(String uuid, String ownerId) {
        String jpql = """
            SELECT c FROM Consumer c JOIN c.facts f
            WHERE KEY(f) = 'virt.uuid'
                AND LOWER(f) IN :guestIds
                AND c.ownerId = :ownerId
            ORDER BY c.updated DESC
            """;

        try {
            // We need to handle uuid with list because we need to cover both endianness, but we only care
            // about most recent updated consumer. So we take all consumers by uuid, sort by update date
            // and take only the most recently modified one
            return getEntityManager().createQuery(jpql, Consumer.class)
                .setParameter("guestIds", Util.getPossibleUuids(uuid))
                .setParameter("ownerId", ownerId)
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Lookup all consumers matching the given guest IDs.
     *
     * Maps guest ID to the most recent registered consumer that matches it. Any guest ID not found will
     * not return null.
     *
     * If multiple registered consumers report this guest ID (re-registraiton), only the most recently
     * updated will be returned.
     *
     * @param guestIds
     *
     * @return VirtConsumerMap of guest ID to it's registered guest consumer, or null if none exists.
     */
    @Transactional
    public VirtConsumerMap getGuestConsumersMap(String ownerId, Set<String> guestIds) {
        VirtConsumerMap guestConsumersMap = new VirtConsumerMap();

        if (guestIds.isEmpty()) {
            return guestConsumersMap;
        }

        List<String> possibleUuids = Util.getPossibleUuids(guestIds);

        // We need to filter down to only the most recently registered consumer with
        // each guest ID.
        String jpql = """
            SELECT c FROM Consumer c JOIN c.facts f
            WHERE KEY(f) = 'virt.uuid'
                AND LOWER(f) IN :guestIds
                AND c.ownerId = :ownerId
            ORDER BY c.updated DESC
            """;

        List<Consumer> consumers = new LinkedList<>();

        TypedQuery<Consumer> query = getEntityManager()
            .createQuery(jpql, Consumer.class)
            .setParameter("ownerId", ownerId);

        for (List<String> block : this.partition(possibleUuids)) {
            query.setParameter("guestIds", block);
            consumers.addAll(query.getResultList());
        }

        if (consumers.isEmpty()) {
            return guestConsumersMap;
        }

        // At this point we might have duplicates for re-registered consumers:
        for (Consumer c : consumers) {
            String virtUuid = c.getFact(Consumer.Facts.VIRT_UUID).toLowerCase();
            if (guestConsumersMap.get(virtUuid) == null) {
                // Store both big and little endian forms in the result:
                guestConsumersMap.add(virtUuid, c);
            }

            // Can safely ignore if already in the map, this would be another consumer
            // reporting the same guest ID (re-registration), but we already sorted by
            // last update time.
        }

        return guestConsumersMap;
    }

    /**
     * Candlepin supports the notion of a user being a consumer. When in effect a consumer will exist in
     * the system who is tied to a particular user.
     *
     * @param username
     *     the username to use to find a consumer
     * @return Consumer for this user if one exists, null otherwise.
     */
    public Consumer findByUsername(String username) {
        ConsumerType person = consumerTypeCurator
            .getByLabel(ConsumerType.ConsumerTypeEnum.PERSON.getLabel());

        if (person == null) {
            return null;
        }

        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Consumer> criteriaQuery = criteriaBuilder.createQuery(Consumer.class);
        Root<Consumer> root = criteriaQuery.from(Consumer.class);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, criteriaBuilder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(criteriaBuilder.equal(root.get(Consumer_.username), username));
        predicates.add(criteriaBuilder.equal(root.get(Consumer_.typeId), person.getId()));

        criteriaQuery.select(root)
            .where(predicates.toArray(new Predicate[0]));

        try {
            return getEntityManager()
                .createQuery(criteriaQuery)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Lookup the Consumer by its UUID.
     *
     * @param uuid
     *     Consumer UUID sought.
     * @return Consumer whose UUID matches the given value, or null otherwise.
     */
    public Consumer findByUuid(String uuid) {
        return getConsumer(uuid);
    }

    @Transactional
    public Collection<Consumer> findByUuids(Collection<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return new HashSet<>();
        }

        Set<Consumer> consumers = new HashSet<>();

        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Define a parameter for the UUIDs
        ParameterExpression<List> uuidsParam = cb.parameter(List.class, "uuids");

        CriteriaQuery<Consumer> cq = cb.createQuery(Consumer.class);
        Root<Consumer> consumerRoot = cq.from(Consumer.class);

        Predicate uuidPredicate = consumerRoot.get("uuid").in(uuidsParam);
        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, cb, consumerRoot);

        if (securityPredicate != null) {
            cq.where(cb.and(uuidPredicate, securityPredicate));
        }
        else {
            cq.where(uuidPredicate);
        }

        TypedQuery<Consumer> query = em.createQuery(cq);

        // Handling partitioning and setting the parameter dynamically
        for (List<String> block : this.partition(uuids)) {
            query.setParameter("uuids", block);
            List<Consumer> blockConsumers = query.getResultList();

            if (blockConsumers != null) {
                consumers.addAll(blockConsumers);
            }
        }

        return consumers;
    }

    @Transactional
    public Collection<Consumer> findByUuidsAndOwner(Collection<String> uuids, String ownerId) {
        Set<Consumer> consumers = new HashSet<>();

        String jpql = """
            SELECT c FROM Consumer c
            WHERE c.ownerId = :oid
                AND c.uuid IN (:uuids)
            """;

        TypedQuery<Consumer> query = this.getEntityManager()
            .createQuery(jpql, Consumer.class)
            .setParameter("oid", ownerId);

        if (uuids != null && !uuids.isEmpty()) {
            for (List<String> block : this.partition(uuids)) {
                query.setParameter("uuids", block);

                consumers.addAll(query.getResultList());
            }
        }

        return consumers;
    }

    // NOTE: This is a giant hack that is for use *only* by SSLAuth in order
    // to bypass the authentication. Do not call it!
    // TODO: Come up with a better way to do this!
    public Consumer getConsumer(String uuid) {
        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Consumer> query = cb.createQuery(Consumer.class);
        Root<Consumer> consumerRoot = query.from(Consumer.class);

        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, cb, consumerRoot);
        Predicate uuidPredicate = cb.equal(consumerRoot.get("uuid"), uuid);

        if (securityPredicate != null) {
            query.select(consumerRoot).where(cb.and(securityPredicate, uuidPredicate));
        }
        else {
            query.select(consumerRoot).where(uuidPredicate);
        }

        try {
            return em.createQuery(query).getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Fetches consumers with the specified IDs. If a consumer does not exist for a given ID, no
     * matching consumer object will be returned, nor will an exception be thrown. As such, the number
     * of consumer objects fetched may be lower than the number of consumer IDs provided.
     *
     * @param consumerIds
     *     A collection of consumer IDs specifying the consumers to fetch
     *
     * @return A query to fetch the consumers with the specified consumer IDs
     */
    public Collection<Consumer> getConsumers(Collection<String> consumerIds) {
        if (consumerIds != null && !consumerIds.isEmpty()) {
            List<Consumer> consumers = new ArrayList<>();

            String jpql = """
            SELECT c FROM Consumer c
            WHERE c.id IN :ids""";

            TypedQuery<Consumer> query = getEntityManager().createQuery(jpql, Consumer.class);

            for (List<String> block : this.partition(consumerIds)) {
                query.setParameter("ids", block);

                consumers.addAll(query.getResultList());
            }

            return consumers;
        }

        return Collections.emptyList();
    }

    /**
     * Fetches all unique addon attribute values set by all the consumers of the specified owner.
     *
     * @param owner
     *     The owner the consumers belong to.
     * @return A list of the all the distinct values of the addon attribute that the consumers belonging
     * to the specified owner have set.
     */
    @Transactional
    public List<String> getDistinctSyspurposeAddonsByOwner(Owner owner) {
        if (owner == null || owner.getId() == null) {
            return Collections.emptyList();
        }

        String jpql = """
            SELECT DISTINCT addon FROM Consumer c
            JOIN c.addOns addon
            WHERE c.ownerId = :ownerId
                AND addon IS NOT NULL
                AND addon != ''
            """;

        return getEntityManager().createQuery(jpql, String.class)
            .setParameter("ownerId", owner.getOwnerId())
            .getResultList();
    }

    /**
     * @param updatedConsumer
     *     updated Consumer values.
     * @return Updated consumers
     */
    @Transactional
    public Consumer update(Consumer updatedConsumer) {
        return update(updatedConsumer, true);
    }

    /**
     * Updates an existing consumer with the state specified by the given Consumer instance. If the
     * consumer has not yet been created, it will be created.
     * <p></p>
     * <strong>Warning:</strong> Using an pre-existing and persisted Consumer entity as the update to
     * apply may cause issues, as Hibernate may opt to save changes to nested collections (facts,
     * guestIds, tags, etc.) when any other database operation is performed. To avoid this issue, it is
     * advised to use only detached or otherwise unmanaged entities for the updated consumer to pass to
     * this method.
     *
     * @param updatedConsumer
     *     A Consumer instance representing the updated state of a consumer
     *
     * @param flush
     *     Whether or not to flush pending database operations after creating or updating the given
     *     consumer
     *
     * @return The persisted, updated consumer
     */
    @Transactional
    public Consumer update(Consumer updatedConsumer, boolean flush) {
        // TODO: FIXME:
        // We really need to use a DTO here. Hibernate has so many pitfalls with this approach that
        // can and will lead to odd, broken or out-of-order behavior.
        // What is even happening here and why?

        // Validate inbound facts before even attempting to apply the update
        this.validateFacts(updatedConsumer);

        Consumer existingConsumer = this.get(updatedConsumer.getId());

        if (existingConsumer == null) {
            return this.create(updatedConsumer, flush);
        }

        // If we try to update a consumer to itself, bad things start happening
        if (existingConsumer != updatedConsumer) {
            // TODO: Are any of these read-only?
            existingConsumer.setEntitlements(
                entitlementCurator.bulkUpdate(updatedConsumer.getEntitlements()));

            // This set of updates is strange. We're ignoring the "null-as-no-change" semantics we use
            // everywhere else, and just blindly copying everything over.
            existingConsumer.setFacts(updatedConsumer.getFacts());
            existingConsumer.setName(updatedConsumer.getName());
            existingConsumer.setOwner(updatedConsumer.getOwner());

            // Set TypeId only if the existing consumer and update consumer typeId is not equal.
            // This check has been added for updating Swatch timestamp
            if (updatedConsumer.getTypeId() != null &&
                !Util.equals(existingConsumer.getTypeId(), updatedConsumer.getTypeId())) {
                existingConsumer.setTypeId(updatedConsumer.getTypeId());
            }

            existingConsumer.setUuid(updatedConsumer.getUuid());
        }

        this.getEntityManager()
            .persist(existingConsumer);

        if (flush) {
            this.flush();
        }

        return existingConsumer;
    }

    /**
     * Modifies the last check in and persists the entity. Make sure that the data is refreshed before
     * using this method.
     *
     * @param consumer
     *     the consumer to update
     */
    public void updateLastCheckin(Consumer consumer) {
        this.updateLastCheckin(consumer, new Date());
    }

    @Transactional
    public void updateLastCheckin(Consumer consumer, Date checkinDate) {
        String jpql = """
            UPDATE Consumer c
            SET c.lastCheckin = :date, c.updated = :date
            WHERE c.id = :cid
            """;

        getEntityManager().createQuery(jpql)
            .setParameter("date", checkinDate)
            .setParameter("cid", consumer.getId())
            .executeUpdate();
    }

    @Transactional
    public void heartbeatUpdate(final String reporterId, final Date checkIn, final String ownerKey)
        throws PersistenceException {
        final String query;
        final String dialect = this.getDatabaseDialect();

        if (dialect.contains("mysql") || dialect.contains("maria")) {
            query = "" +
                "UPDATE cp_consumer consumer" +
                " JOIN cp_consumer_hypervisor hypervisor on consumer.id = hypervisor.consumer_id " +
                " JOIN cp_owner owner on owner.id = consumer.owner_id" +
                " SET consumer.lastcheckin = :checkin" +
                " WHERE hypervisor.reporter_id = :reporter" +
                " AND owner.account = :ownerKey";
        }
        else if (dialect.contains("postgresql")) {
            query = "" +
                "UPDATE cp_consumer consumer" +
                " SET lastcheckin = :checkin" +
                " FROM cp_consumer_hypervisor hypervisor, cp_owner owner" +
                " WHERE consumer.id = hypervisor.consumer_id" +
                " AND hypervisor.reporter_id = :reporter" +
                " AND consumer.owner_id = owner.id" +
                " AND owner.account = :ownerKey";
        }
        else {
            throw new PersistenceException(
                "The HypervisorHearbeatUpdate cannot execute as the database dialect is not recognized.");
        }

        this.getEntityManager()
            .createNativeQuery(query)
            .setParameter("checkin", checkIn)
            .setParameter("reporter", reporterId)
            .setParameter("ownerKey", ownerKey)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Consumer.class)
            .addSynchronizedEntityClass(HypervisorId.class)
            .addSynchronizedEntityClass(Owner.class)
            .executeUpdate();
    }

    /**
     * Validates the facts associated with the given consumer. If any fact fails validation a
     * PropertyValidationException will be thrown.
     *
     * @param consumer
     *     The consumer containing the facts to validate
     */
    private void validateFacts(Consumer consumer) {
        // Impl note:
        // Unlike the previous implementation, we are no longer attempting to "fix" anything here;
        // if it's broken at this point, we're in trouble, so we're going to throw an exception
        // instead of waiting for CP to die with a DB exception sometime in the very near future.
        //
        // Also, we're no longer using ConfigProperties.CONSUMER_FACTS_MATCHER at this point, as
        // it's something that belongs with the other input validation and filtering.

        Map<String, String> facts = consumer.getFacts();
        if (facts != null) {
            for (Entry<String, String> fact : facts.entrySet()) {
                this.factValidator.validate(fact.getKey(), fact.getValue());
            }
        }
    }

    /**
     * @param consumers
     *     consumers to update
     * @param flush
     *     whether to flush or not
     * @return updated consumers
     */
    @Transactional
    public Set<Consumer> bulkUpdate(Set<Consumer> consumers, boolean flush) {
        Set<Consumer> toReturn = new HashSet<>();
        for (Consumer toUpdate : consumers) {
            toReturn.add(update(toUpdate, flush));
        }

        return toReturn;
    }

    /**
     * Get host consumer for a guest system id.
     *
     * As multiple hosts could have reported the same guest ID, we find the newest and assume this is
     * the authoritative host for the guest.
     *
     * This search needs to be case insensitive as some hypervisors report uppercase guest UUIDs, when
     * the guest itself will report lowercase.
     *
     * The first lookup will retrieve the host and then place it in the map. This will save from
     * reloading the host from the database if it is asked for again during the session. An auto-bind
     * can call this method up to 50 times and this will cut the database calls significantly.
     *
     * @param guestId
     *     a virtual guest ID (not a consumer UUID)
     * @param ownerId
     *     ID of the organization to scope the search
     * @return host consumer who most recently reported the given guestId (if any)
     */
    @Transactional
    public Consumer getHost(String guestId, String ownerId) {
        if (guestId == null) {
            return null;
        }
        String guestLower = guestId.toLowerCase();

        Pair<String, String> key = new ImmutablePair<>(guestLower, ownerId);
        if (cachedHostsProvider.get().containsKey(key)) {
            return cachedHostsProvider.get().get(key);
        }

        String jpql = """
            SELECT g.consumer FROM GuestId g
            WHERE g.consumer.owner.id = :ownerId
                AND g.guestIdLower IN :possibleIds
            ORDER BY g.updated DESC
            """;

        Consumer host = null;
        try {
            host = getEntityManager().createQuery(jpql, Consumer.class)
                .setParameter("ownerId", ownerId)
                .setParameter("possibleIds", Util.getPossibleUuids(guestLower))
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }

        cachedHostsProvider.get().put(key, host);
        return host;
    }

    /**
     * Get guest consumers for a host consumer.
     *
     * @param consumer
     *     host consumer to find the guests for
     * @return list of registered guest consumers for this host
     */
    @Transactional
    public List<Consumer> getGuests(Consumer consumer) {
        if (consumer.getFact(Consumer.Facts.VIRT_UUID) != null &&
            !consumer.getFact(Consumer.Facts.VIRT_UUID).trim().equals("")) {
            throw new BadRequestException(i18nProvider.get().tr(
                "The system with UUID {0} is a virtual guest. It does not have guests.",
                consumer.getUuid()));
        }

        List<Consumer> guests = new ArrayList<>();
        List<GuestId> consumerGuests = consumer.getGuestIds();
        if (consumerGuests != null) {
            consumerGuests = consumerGuests.stream().distinct()
                .collect(Collectors.toList());
            for (GuestId cg : consumerGuests) {
                // Check if this is the most recent host to report the guest by asking
                // for the consumer's current host and comparing it to ourselves.
                if (consumer.equals(getHost(cg.getGuestId(), consumer.getOwnerId()))) {
                    Consumer guest = findByVirtUuid(cg.getGuestId(), consumer.getOwnerId());
                    if (guest != null) {
                        guests.add(guest);
                    }
                }
            }
        }

        return guests;
    }

    /**
     * This is an insecure query, because we need to know whether or not the consumer exists
     *
     * We do not require that the hypervisor be consumerType hypervisor because we need to allow regular
     * consumers to be given HypervisorIds to be updated via hypervisorResource
     *
     * @param hypervisorId
     *     Unique identifier of the hypervisor
     * @param ownerId
     *     Id of org namespace to search
     * @return Consumer that matches the given
     */
    public Consumer getHypervisor(String hypervisorId, String ownerId) {
        String jpql = """
            SELECT c FROM Consumer c
            WHERE c.ownerId = :ownerId
                AND LOWER(c.hypervisorId.hypervisorId) = :hypervisorId
            """;

        try {
            return getEntityManager().createQuery(jpql, Consumer.class)
                .setParameter("ownerId", ownerId)
                .setParameter("hypervisorId", hypervisorId.toLowerCase())
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    public Consumer getConsumerBySystemUuid(String ownerId, String systemUuid) {
        String jpql = """
                SELECT c FROM Consumer c
                JOIN c.facts f
                WHERE KEY(f) = :factKey
                    AND LOWER(f) = :uuid
                    AND c.ownerId = :ownerId
                ORDER BY c.updated DESC
                """;

        try {
            return getEntityManager().createQuery(jpql, Consumer.class)
                .setParameter("factKey", Consumer.Facts.DMI_SYSTEM_UUID)
                .setParameter("ownerId", ownerId)
                .setParameter("uuid", systemUuid.toLowerCase())
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Retrieves the identity certificate IDs for the provided consumer ids.
     *
     * @param consumerIds
     *  ids of the {@link Consumer}s.
     *
     * @return
     *  identity Certificate ids.
     */
    public List<String> getIdentityCertIds(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return new ArrayList<>();
        }

        String jpql = """
            SELECT idCert.id FROM Consumer
            WHERE id IN (:consumerIds)
            """;

        List<String> idCertIds = getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("consumerIds", consumerIds)
            .getResultList();

        return idCertIds.stream()
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Retrieves the content access certificate ids for provided consumer ids.
     *
     * @param consumerIds
     *     - ids of the {@link Consumer}s.
     * @return content access certificate ids.
     */
    public List<String> getContentAccessCertIds(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return new ArrayList<>();
        }

        String jpql = """
            SELECT contentAccessCert.id FROM Consumer
            WHERE id IN (:consumerIds)
            """;
        List<String> caCertIds = getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("consumerIds", consumerIds)
            .getResultList();

        return caCertIds.stream()
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Fetches a list of certificate serial IDs associated with the provided consumer IDs. This method will
     * fetch serials for identity certificates and content access certificates; all other certificates will
     * be ignored. The output of this method is not ordered or in any way linked to its inputs.
     *
     * @param consumerIds
     *  A collection of IDs of the consumers for which to fetch certificate serials
     *
     * @return
     *  An unordered list of certificate serials for the given consumers
     */
    public List<Long> getConsumerCertSerialIds(Collection<String> consumerIds) {
        String sql = """
            WITH consumers (id, id_cert_id, ca_cert_id) AS (
                -- bit of a hack job to avoid injecting the IDs parameter multiple times
                SELECT id, consumer_idcert_id AS id_cert_id, cont_acc_cert_id AS ca_cert_id
                  FROM cp_consumer
                  WHERE id IN (:consumer_ids)
            )
            SELECT cert.serial_id
                FROM consumers c
                JOIN cp_id_cert cert ON cert.id = c.id_cert_id
            UNION ALL
            SELECT cert.serial_id
                FROM consumers c
                JOIN cp_cont_access_cert cert ON cert.id = c.ca_cert_id
            """;

        List<Long> output = new ArrayList<>();

        // Impl note: The scalar is necessary here because the generics provide literally zero validation for
        // us in this code. Without the scalar explicitly setting how the DB field should be converted, it
        // will be fetched as a BigInteger, which will eventually cause problems when the output list is
        // processed as longs.
        Query query = this.getEntityManager()
            .createNativeQuery(sql)
            .unwrap(NativeQuery.class)
            .addScalar("serial_id", StandardBasicTypes.LONG);

        for (List<String> block : this.partition(consumerIds)) {
            List<Long> qresult = query.setParameter("consumer_ids", block)
                .getResultList();

            output.addAll(qresult);
        }

        return output;
    }

    /**
     * Retrieves all the serial ids for provided {@link SCACertificate} ids and
     * {@link IdentityCertificate} ids.
     *
     * @param caCertIds
     *     - ids of content access certificates.
     * @param idCertIds
     *     - ids of identity certificates.
     * @return serial ids.
     */
    public List<Long> getSerialIdsForCerts(Collection<String> caCertIds, Collection<String> idCertIds) {
        List<Long> serialIds = new ArrayList<>();
        EntityManager em = getEntityManager();
        if (caCertIds != null && !caCertIds.isEmpty()) {
            String jpql = "SELECT ca.serial.id " +
                "FROM SCACertificate ca " +
                "WHERE ca.id IN (:certIds)";

            List<Long> caCertSerialIds = em
                .createQuery(jpql, Long.class)
                .setParameter("certIds", caCertIds)
                .getResultList();

            serialIds.addAll(caCertSerialIds.stream()
                .filter(Objects::nonNull)
                .toList());
        }

        if (idCertIds != null && !idCertIds.isEmpty()) {
            String jpql = "SELECT idcert.serial.id " +
                "FROM IdentityCertificate idcert " +
                "WHERE idcert.id IN (:certIds)";

            List<Long> idCertSerialIds = em
                .createQuery(jpql, Long.class)
                .setParameter("certIds", idCertIds)
                .getResultList();

            serialIds.addAll(idCertSerialIds.stream()
                .filter(Objects::nonNull)
                .toList());
        }
        return serialIds;
    }

    /**
     * Deletes {@link Consumer}s based on the provided consumer ids.
     *
     * @param consumerIds
     *  ids of the consumers to delete
     *
     * @return
     *  the number of consumer that were deleted
     */
    public int deleteConsumers(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return 0;
        }

        int deleted = 0;

        Query query = this.getEntityManager()
            .createQuery("DELETE Consumer WHERE id IN (:consumerIds)");

        for (List<String> block : this.partition(consumerIds)) {
            deleted += query.setParameter("consumerIds", block)
                .executeUpdate();
        }

        return deleted;
    }

    /**
     * Retrieves records of inactive consumers based on the provided last check-in and last-updated retention
     * dates.
     * <p>
     * A given consumer is considered inactive if it is a non-manifest consumer, with no active entitlements,
     * and either (a) the consumer's last check-in is older than the provided last-checked-in retention date,
     * or (b) the consumer has not yet checked in, and it was last updated internally before the provided
     * last-updated retention date.
     *
     * @param lastCheckedInRetention
     *  consumers that have not checked in before this date are considered inactive
     *
     * @param lastUpdatedRetention
     *  if the consumer has no checked in date, then the consumers that have an update date older than the
     *  provided retention date is considered inactive.
     *
     * @return
     *  a list of {@link InactiveConsumerRecord}s representing the consumers to be considered inactive
     *  according to the provided retention dates
     */
    public List<InactiveConsumerRecord> getInactiveConsumers(Instant lastCheckedInRetention,
        Instant lastUpdatedRetention) {

        if (lastCheckedInRetention == null) {
            throw new IllegalArgumentException("Last checked-in retention date cannot be null.");
        }

        if (lastUpdatedRetention == null) {
            throw new IllegalArgumentException("Last updated retention date cannot be null.");
        }

        String jpql = "SELECT new org.candlepin.model.InactiveConsumerRecord(consumer.id, consumer.uuid, " +
            "owner.key) " +
            "FROM Consumer consumer " +
            "  JOIN ConsumerType type on type.id = consumer.typeId " +
            "  JOIN consumer.owner owner " +
            "  LEFT JOIN consumer.entitlements ent " +
            "WHERE ent IS NULL " +
            "  AND type.manifest = 'N' " +
            "  AND (consumer.lastCheckin < :lastCheckedInRetention OR " +
            "    (consumer.lastCheckin IS NULL AND consumer.updated < :nonCheckedInRetention)) " +
            "ORDER BY owner.key, consumer.uuid";

        return this.getEntityManager()
            .createQuery(jpql, InactiveConsumerRecord.class)
            .setParameter("lastCheckedInRetention", Date.from(lastCheckedInRetention))
            .setParameter("nonCheckedInRetention", Date.from(lastUpdatedRetention))
            .getResultList();
    }

    public List<Consumer> getHypervisorsBulk(String ownerId, Collection<String> hypervisorIds) {
        return this.getHypervisorsBulk(ownerId, hypervisorIds, null);
    }

    public List<Consumer> getHypervisorsBulk(String ownerId, Collection<String> hypervisorIds,
        PageRequest pageRequest) {
        if (hypervisorIds == null || hypervisorIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> lowerCaseHypervisorIds = toLowerCase(hypervisorIds);

        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Consumer> criteriaQuery = criteriaBuilder.createQuery(Consumer.class);
        Root<Consumer> root = criteriaQuery.from(Consumer.class);
        criteriaQuery.select(root);
        Join<Consumer, HypervisorId> hypervisorIdJoin = root.join(Consumer_.hypervisorId);

        Predicate ownerIdPredicate = criteriaBuilder.equal(root.get(Consumer_.ownerId), ownerId);
        Predicate hypervisorIdPredicate = criteriaBuilder.lower(hypervisorIdJoin
            .get(HypervisorId_.hypervisorId)).in(lowerCaseHypervisorIds);

        criteriaQuery.where(criteriaBuilder.and(ownerIdPredicate, hypervisorIdPredicate));

        if (pageRequest != null) {
            Path<String> sortBy = pageRequest.getSortBy() == null ?
                hypervisorIdJoin.get(HypervisorId_.hypervisorId) :
                root.get(pageRequest.getSortBy());
            Order order = pageRequest.getOrder() == PageRequest.Order.DESCENDING ?
                criteriaBuilder.desc(sortBy) :
                criteriaBuilder.asc(sortBy);
            criteriaQuery.orderBy(order);
        }

        TypedQuery<Consumer> query = this.getEntityManager().createQuery(criteriaQuery);

        if (pageRequest != null && pageRequest.isPaging()) {
            int offset = (pageRequest.getPage() - 1) * pageRequest.getPerPage();
            int limit = pageRequest.getPerPage();
            query.setFirstResult(offset).setMaxResults(limit);
        }

        return query.getResultList();
    }

    public Integer countHypervisorsBulk(String ownerId, Collection<String> hypervisorIds) {
        if (hypervisorIds == null || hypervisorIds.isEmpty()) {
            return 0;
        }

        List<String> lowerCaseHypervisorIds = toLowerCase(hypervisorIds);

        String jpql = """
            SELECT COUNT(c) FROM Consumer c
            JOIN c.hypervisorId h
            WHERE c.ownerId = :ownerId
                AND LOWER(h.hypervisorId) IN (:lowerCaseHypervisorIds)
            """;

        // Query param limit handling was omitted. The maximum number of elements should be handled by
        // pagination and so the limit should never be reached.
        return getEntityManager().createQuery(jpql, Long.class)
            .setParameter("ownerId", ownerId)
            .setParameter("lowerCaseHypervisorIds", lowerCaseHypervisorIds)
            .getSingleResult()
            .intValue();
    }

    private static List<String> toLowerCase(Collection<String> hypervisorIds) {
        return hypervisorIds.stream()
            .distinct()
            .map(String::toLowerCase)
            .sorted()
            .toList();
    }

    public List<Consumer> getHypervisorsForOwner(String ownerId) {
        return getHypervisorsForOwner(ownerId, null);
    }

    public List<Consumer> getHypervisorsForOwner(String ownerId, PageRequest pageRequest) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Consumer> criteriaQuery = criteriaBuilder.createQuery(Consumer.class);
        Root<Consumer> root = criteriaQuery.from(Consumer.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(root.get(Consumer_.OWNER_ID), ownerId));
        predicates.add(criteriaBuilder.isNotNull(root.get(Consumer_.hypervisorId)
            .get(HypervisorId_.hypervisorId)));

        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, criteriaBuilder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        criteriaQuery.select(root)
            .where(predicates.toArray(new Predicate[0]));

        if (pageRequest != null && pageRequest.getSortBy() != null) {
            Order order = pageRequest.getOrder() == PageRequest.Order.ASCENDING ?
                criteriaBuilder.asc(root.get(pageRequest.getSortBy())) :
                criteriaBuilder.desc(root.get(pageRequest.getSortBy()));
            criteriaQuery.orderBy(order);
        }

        TypedQuery<Consumer> query = this.getEntityManager().createQuery(criteriaQuery);

        if (pageRequest != null && pageRequest.isPaging()) {
            int offset = (pageRequest.getPage() - 1) * pageRequest.getPerPage();
            int limit = pageRequest.getPerPage();
            query.setFirstResult(offset)
                .setMaxResults(limit);
        }

        return query.getResultList();
    }

    public int countHypervisorsForOwner(String ownerId) {
        String jpql = """
            SELECT COUNT(c) FROM Consumer c
            WHERE c.ownerId = :ownerId AND c.hypervisorId.hypervisorId IS NOT NULL
            """;

        return this.getEntityManager()
            .createQuery(jpql, Long.class)
            .setParameter("ownerId", ownerId)
            .getSingleResult()
            .intValue();
    }

    public boolean doesConsumerExist(String uuid) {
        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Consumer> consumerRoot = query.from(Consumer.class);

        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, cb, consumerRoot);
        Predicate uuidPredicate = cb.equal(consumerRoot.get("uuid"), uuid);

        if (securityPredicate != null) {
            query.select(cb.count(consumerRoot.get("id"))).where(cb.and(securityPredicate, uuidPredicate));
        }
        else {
            query.select(cb.count(consumerRoot.get("id"))).where(uuidPredicate);
        }

        return em.createQuery(query).getSingleResult() != 0;
    }

    /**
     * Given the Consumer UUIDs it returns unique consumer UUIDs that exists.
     *
     * @param consumerUuids
     *     consumer UUIDs.
     * @return set of consumer UUIDs that exists.
     */
    public Set<String> getExistingConsumerUuids(Iterable<String> consumerUuids) {
        Set<String> existingUuids = new HashSet<>();

        if (consumerUuids != null && consumerUuids.iterator().hasNext()) {
            int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit());

            String jpql = """
                SELECT c.uuid FROM Consumer c
                WHERE c.uuid IN (:uuids)
                """;
            TypedQuery<String> query = getEntityManager().createQuery(jpql, String.class);

            for (List<String> block : Iterables.partition(consumerUuids, blockSize)) {
                existingUuids.addAll(query.setParameter("uuids", block).getResultList());
            }
        }

        return existingUuids;
    }

    /**
     * Determines if any of the provided {@link Consumer} UUIDs do not exist or do not belong to the provided
     * {@link Owner}.
     *
     * @param ownerKey
     *  the key of the owner that should own the consumers
     *
     * @param consumerUuids
     *  the consumer UUIDs to check
     *
     * @return all of the provided consumer UUIDs that do not exist or do not belong to the provided owner
     */
    public Set<String> getNonExistentConsumerUuids(String ownerKey, Collection<String> consumerUuids) {
        if (ownerKey == null || ownerKey.isBlank() || consumerUuids == null || consumerUuids.isEmpty()) {
            return new HashSet<>();
        }

        String jpql = "SELECT c.uuid " +
            "FROM Consumer c " +
            "JOIN Owner o on o.id = c.ownerId " +
            "WHERE c.uuid IN (:uuids) and o.key = :ownerKey";

        Query query = getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("ownerKey", ownerKey);

        Set<String> uniqueConsumerUuids = new HashSet<>(consumerUuids);
        for (List<String> block : partition(uniqueConsumerUuids)) {
            List<String> result = query
                .setParameter("uuids", block)
                .getResultList();

            uniqueConsumerUuids.removeAll(result);
        }

        return uniqueConsumerUuids;
    }

    public Consumer verifyAndLookupConsumer(String consumerUuid) {
        Consumer consumer = this.findByUuid(consumerUuid);

        if (consumer == null) {
            throw new NotFoundException(i18nProvider.get().tr("Unit with ID \"{0}\" could not be found.",
                consumerUuid));
        }

        return consumer;
    }

    public Consumer verifyAndLookupConsumerWithEntitlements(String consumerUuid) {
        Consumer consumer = this.findByUuid(consumerUuid);
        if (consumer == null) {
            throw new NotFoundException(i18nProvider.get().tr("Unit with ID \"{0}\" could not be found.",
                consumerUuid));
        }

        for (Entitlement e : consumer.getEntitlements()) {
            Hibernate.initialize(e.getCertificates());

            if (e.getPool() != null) {
                Hibernate.initialize(e.getPool().getProductAttributes());
                Hibernate.initialize(e.getPool().getAttributes());
                Hibernate.initialize(e.getPool().getDerivedProductAttributes());
            }
        }

        return consumer;
    }

    /**
     * Fetches a collection of consumers based on the provided filter data in the query builder. If the
     * query builder is null or contains no arguments, this method will return all known consumers.
     *
     * @param queryArgs
     *     an ConsumerQueryArguments instance containing the various arguments or filters to use to
     *     select consumers
     *
     * @return a list of consumers matching the provided query arguments/filters
     */
    public List<Consumer> findConsumers(ConsumerQueryArguments queryArgs) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Consumer> criteriaQuery = criteriaBuilder.createQuery(Consumer.class);

        Root<Consumer> root = criteriaQuery.from(Consumer.class);
        criteriaQuery.select(root)
            .distinct(true);

        List<Predicate> predicates = this.buildConsumerQueryPredicates(criteriaBuilder, root, queryArgs);
        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, criteriaBuilder, root);

        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        if (!predicates.isEmpty()) {
            criteriaQuery.where(predicates.toArray(new Predicate[0]));
        }

        List<Order> order = this.buildJPAQueryOrder(criteriaBuilder, root, queryArgs);
        if (order != null && !order.isEmpty()) {
            criteriaQuery.orderBy(order);
        }

        TypedQuery<Consumer> query = this.getEntityManager()
            .createQuery(criteriaQuery);

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
     * Fetches the count of consumers matching the provided filter data in the query builder. If the
     * query builder is null or contains no arguments, this method will return the count of all known
     * consumers.
     *
     * @param queryArgs
     *     a ConsumerQueryArguments instance containing the various arguments or filters to use to count
     *     consumers
     *
     * @return the number of consumers matching the provided query arguments/filters
     */
    public long getConsumerCount(ConsumerQueryArguments queryArgs) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);

        Root<Consumer> root = criteriaQuery.from(Consumer.class);
        criteriaQuery.select(criteriaBuilder.countDistinct(root));

        List<Predicate> predicates = this.buildConsumerQueryPredicates(criteriaBuilder, root, queryArgs);
        Predicate securityPredicate = this.getSecurityPredicate(Consumer.class, criteriaBuilder, root);

        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        if (!predicates.isEmpty()) {
            criteriaQuery.where(predicates.toArray(new Predicate[0]));
        }

        return this.getEntityManager()
            .createQuery(criteriaQuery)
            .getSingleResult();
    }

    /**
     * Finds the consumer count for an Owner based on type.
     *
     * @param owner
     *     the owner to count consumers for
     * @param type
     *     the type of the Consumer to filter on.
     * @return the number of consumers based on the type.
     */
    public long getConsumerCount(Owner owner, ConsumerType type) {
        ConsumerQueryArguments builder = new ConsumerQueryArguments()
            .setOwner(owner)
            .setTypes(Collections.singleton(type));

        return this.getConsumerCount(builder);
    }

    /**
     * Builds a collection of predicates to be used for querying consumers using the JPA criteria query
     * API.
     *
     * @param criteriaBuilder
     *     the CriteriaBuilder instance to use to create predicates
     *
     * @param root
     *     the root of the query, should be a reference to the Consumer root
     *
     * @param queryArgs
     *     a ConsumerQueryArguments instance containing the various arguments or filters to use to
     *     select consumers
     *
     * @return a list of predicates to select consumers based on the query parameters provided
     */
    private List<Predicate> buildConsumerQueryPredicates(CriteriaBuilder criteriaBuilder, Root<Consumer> root,
        ConsumerQueryArguments queryArgs) {

        List<Predicate> predicates = new ArrayList<>();

        if (queryArgs != null) {
            if (queryArgs.getOwner() != null) {
                predicates.add(
                    criteriaBuilder.equal(root.get(Consumer_.ownerId), queryArgs.getOwner().getId()));
            }

            if (queryArgs.getUsername() != null) {
                predicates.add(
                    criteriaBuilder.equal(root.get(Consumer_.username), queryArgs.getUsername()));
            }

            if (this.checkQueryArgumentCollection(queryArgs.getUuids())) {
                predicates.add(root.get(Consumer_.uuid).in(queryArgs.getUuids()));
            }

            if (this.checkQueryArgumentCollection(queryArgs.getTypes())) {
                List<String> typeIds = queryArgs.getTypes().stream()
                    .map(ConsumerType::getId)
                    .collect(Collectors.toList());

                predicates.add(root.get(Consumer_.typeId).in(typeIds));
            }

            if (this.checkQueryArgumentCollection(queryArgs.getHypervisorIds())) {
                // Impl note:
                // This "case-insensitive" check is entirely dependent on a bit of code in the
                // HypervisorId class which converts hypervisor IDs to lowercase.
                List<String> lcaseHIDs = queryArgs.getHypervisorIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());

                Predicate hidPredicate;

                if (lcaseHIDs.isEmpty()) {
                    // list contained nothing but nulls
                    Join<Consumer, HypervisorId> hypervisor = root.join(Consumer_.hypervisorId,
                        JoinType.LEFT);

                    hidPredicate = hypervisor.get(HypervisorId_.hypervisorId).isNull();
                }
                else if (lcaseHIDs.size() < queryArgs.getHypervisorIds().size()) {
                    // nulls + non-nulls
                    Join<Consumer, HypervisorId> hypervisor = root.join(Consumer_.hypervisorId,
                        JoinType.LEFT);

                    hidPredicate = criteriaBuilder.or(hypervisor.get(HypervisorId_.hypervisorId).isNull(),
                        hypervisor.get(HypervisorId_.hypervisorId).in(lcaseHIDs));
                }
                else {
                    // non-nulls only
                    Join<Consumer, HypervisorId> hypervisor = root.join(Consumer_.hypervisorId);
                    hidPredicate = hypervisor.get(HypervisorId_.hypervisorId).in(lcaseHIDs);
                }

                predicates.add(hidPredicate);
            }

            Map<String, Collection<String>> facts = queryArgs.getFacts();
            if (facts != null && !facts.isEmpty()) {
                // Sanity check: make sure we don't have too many arguments for the backend to handle
                // in a single query. 10 is well below the technical limits, but if we're hitting that
                // we're probably doing something wrong, and this query will be sloooooooooow.
                if (facts.size() > MAX_CONSUMER_FACTS_PER_QUERY) {
                    throw new IllegalArgumentException("query arguments contains too many facts");
                }

                // Impl note:
                // This behavior is kind of strange -- we use an implicit conjunction to combine
                // facts of different keys, even though none of the other criteria function that way.
                // However, until we're willing to break backward compatibility, this is how this
                // part of the lookup needs to function.
                facts.entrySet().stream()
                    .map(entry -> this.buildConsumerFactPredicate(criteriaBuilder, root,
                        entry.getKey(), entry.getValue()))
                    .filter(Objects::nonNull)
                    .forEach(predicates::add);
            }

            if (queryArgs.getEnvironmentId() != null) {
                MapJoin<Consumer, String, String> environmentIdsJoin =
                    root.joinMap(Consumer_.ENVIRONMENT_IDS);
                predicates.add(
                    criteriaBuilder.equal(environmentIdsJoin.value(), queryArgs.getEnvironmentId()));
            }
        }

        return predicates;
    }

    /**
     * Builds a predicate for finding consumers with a fact containing one or more of the specified
     * values.
     *
     * @param criteriaBuilder
     *     the CriteriaBuilder to use to construct the predicate
     *
     * @param root
     *     the Consumer root from which to build the predicate
     *
     * @param fact
     *     the fact to use to select consumers
     *
     * @param values
     *     a collection containing the desired values of the target fact
     *
     * @return a predicate to be used for matching consumers by the specified fact and value
     */
    private Predicate buildConsumerFactPredicate(CriteriaBuilder criteriaBuilder, Root<Consumer> root,
        String fact, Collection<String> values) {

        // Impl note:
        // Fact filtering is irritatingly complex. To maintain backward compatibility with endpoints
        // that use this, we need to implement the following:
        // - For a given fact key, each unique value is combined via OR:
        // (mapkey = 'my_fact' AND (element = 'value1' OR element = 'value2' OR ...))
        // - Each given map key set is combined with an AND. This means each fact key
        // constitutes a correlated subquery or join
        // - Values are not compared with equals as indicated above, but with a custom
        // like statement that converts shell-style wildcards (* and ?) into like-compatible
        // wildcards, escaping any present like-native wildcards:
        // "myvalue*" => "myvalue%"
        // "%my_val?" => "!%my!_val_" (ESCAPE '!')
        // - Value comparison are case-insensitive at the query level, not collation :(
        // - The complexity with value comparison also applies to keys, sans case-insensitivity

        // Ensure we have some kind of fact data to filter on
        if (fact == null || fact.isEmpty() || values == null || values.isEmpty()) {
            return null;
        }

        MapJoin<Consumer, String, String> consumerFacts = root.join(Consumer_.facts);

        String keyExp = this.translateFactExpression(fact);
        List<Predicate> valuePredicates = new ArrayList<>();

        for (String value : values) {
            String valueExp = this.translateFactExpression(value);

            if (valueExp != null && !valueExp.isEmpty()) {
                valuePredicates.add(criteriaBuilder.like(criteriaBuilder.lower(consumerFacts.value()),
                    criteriaBuilder.lower(criteriaBuilder.literal(valueExp)), '!'));
            }
            else {
                valuePredicates.add(criteriaBuilder.isNull(consumerFacts.value()));
                valuePredicates.add(criteriaBuilder.equal(consumerFacts.value(), ""));
            }
        }

        Predicate[] predicateArray = new Predicate[valuePredicates.size()];

        return criteriaBuilder.and(
            criteriaBuilder.like(consumerFacts.key(), keyExp, '!'),
            criteriaBuilder.or(valuePredicates.toArray(predicateArray)));
    }

    /**
     * Translates a fact expression from the external syntax with shell-style wildcards to an expression
     * compatible with an SQL LIKE operation.
     * <p>
     * </p>
     * The following translations are performed by this operation:
     *
     * <ul>
     * <li>LIKE-compatible wildcards and exclamation points (bangs) are escaped using an exclamation
     * point: "_" and "%" become "!_" and "!%" respectively</li>
     * <li>Shell-style wildcards that are not escaped with a backslash (\) are translated to
     * LIKE-compatible wildcards: "?" and "*" become "_" and "%" respectively</li>
     * <li>Escaped escapes (\\) are translated to a single backslash (\)
     * <li>
     * </ul>
     *
     * Any other characters and sequences are left as-is (such as unrecognized escape sequences).
     *
     * @param expression
     *     the fact expression to translate
     *
     * @return the translated fact expression
     */
    private String translateFactExpression(String expression) {
        if (expression != null && !expression.isEmpty()) {
            Matcher matcher = FACT_TRANSLATION_REGEX.matcher(expression.replaceAll("([!_%])", "!$1"));
            StringBuilder builder = new StringBuilder();

            while (matcher.find()) {
                if (!matcher.group(1).isEmpty()) {
                    matcher.appendReplacement(builder, "$2");
                }
                else {
                    matcher.appendReplacement(builder, "*".equals(matcher.group(2)) ? "%" : "_");
                }
            }

            matcher.appendTail(builder);
            return builder.toString();
        }

        return null;
    }

    public int getConsumerEntitlementCount(Owner owner, ConsumerType type) {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Integer> cq = cb.createQuery(Integer.class);
        Root<Consumer> consumerRoot = cq.from(Consumer.class);

        Join<Consumer, Entitlement> entitlementJoin = consumerRoot.join("entitlements");

        Predicate securePredicate = getSecurityPredicate(Consumer.class, cb, consumerRoot);
        if (securePredicate == null) {
            securePredicate = cb.conjunction();
        }

        Predicate ownerPredicate = cb.equal(consumerRoot.get("ownerId"), owner.getId());
        Predicate typePredicate = cb.equal(consumerRoot.get("typeId"), type.getId());

        cq.select(cb.sum(entitlementJoin.get("quantity")))
            .where(cb.and(ownerPredicate, typePredicate, securePredicate));

        Integer result = em.createQuery(cq).getSingleResult();
        return result != null ? result : 0;
    }

    public List<String> getConsumerIdsWithStartedEnts() {
        String jpql = """
            SELECT DISTINCT e.consumer.id FROM Entitlement e
            JOIN e.pool p
            WHERE e.updatedOnStart = FALSE
                AND p.startDate < CURRENT_TIMESTAMP
            """;

        return getEntityManager()
            .createQuery(jpql, String.class)
            .getResultList();
    }

    /**
     * Clears (nulls) the content access mode for any consumer belonging to the given owner, that is
     * using a mode which is no longer in the provided set of existing modes.
     *
     * @param owner
     *     the owner for which to fetch active consumer content access modes
     *
     * @param existingModes
     *     a var-arg list of existing modes to retain
     *
     * @throws IllegalArgumentException
     *     if owner is null, or the list of existing modes is null
     *
     * @return the number of consumers updated as a result of this operation
     */
    public int cullInvalidConsumerContentAccess(Owner owner, String... existingModes) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (existingModes == null) {
            throw new IllegalArgumentException("existingModes is null");
        }

        int updated = 0;

        if (existingModes.length > 0) {
            String jpql = "UPDATE Consumer c SET c.contentAccessMode = NULL " +
                "WHERE c.owner.id = :owner_id AND c.contentAccessMode NOT IN (:access_modes)";

            updated = this.getEntityManager()
                .createQuery(jpql)
                .setParameter("owner_id", owner.getId())
                .setParameter("access_modes", Arrays.asList(existingModes))
                .executeUpdate();
        }

        return updated;
    }

    /**
     * Fetches all unique system purpose attribute values set by all the consumers of the specified
     * owner.
     *
     * @param owner
     *     The owner the consumers belong to
     * @param sysPurposeAttribute
     *     The type of system purpose attribute needs to be fetched
     * @return A list of the all the distinct values of the system purpose attributes that the consumers
     * belonging to the specified owner have set
     */
    @SuppressWarnings("unchecked")
    public List<String> getDistinctSyspurposeValuesByOwner(Owner owner,
        SystemPurposeAttributeType sysPurposeAttribute) throws RuntimeException {

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = builder.createQuery(String.class);
        Root<Consumer> root = query.from(Consumer.class);
        Path sysPurposePath;

        switch (sysPurposeAttribute) {
            case USAGE:
                sysPurposePath = root.get(Consumer_.usage);
                break;
            case ROLES:
                sysPurposePath = root.get(Consumer_.role);
                break;
            case SERVICE_LEVEL:
                sysPurposePath = root.get(Consumer_.serviceLevel);
                break;
            case SERVICE_TYPE:
                sysPurposePath = root.get(Consumer_.serviceType);
                break;
            default:
                throw new RuntimeException("Unrecognized system purpose attribute: " + sysPurposeAttribute);
        }

        Predicate notNullPredicate = builder.isNotNull(sysPurposePath);
        Predicate notEmptyPredicate = builder.notEqual(sysPurposePath, "");

        query.select(sysPurposePath)
            .where(builder.and(builder.equal(root.get(Consumer_.ownerId), owner.getId()),
                notNullPredicate, notEmptyPredicate))
            .distinct(true);

        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Takes a list of identity certificate ids and unlinks them from consumers.
     *
     * @param certIds
     *     certificate ids to be unlinked
     * @return a number of unlinked consumers
     */
    @Transactional
    public int unlinkIdCertificates(Collection<String> certIds) {
        if (certIds == null || certIds.isEmpty()) {
            return 0;
        }

        String jpql = """
            UPDATE Consumer c
            SET c.idCert = NULL, c.updated = :date
            WHERE c.idCert.id IN (:cert_ids)
            """;

        int updated = 0;
        Date updateTime = new Date();

        Query query = getEntityManager().createQuery(jpql)
            .setParameter("date", updateTime);

        for (Collection<String> certIdBlock : this.partition(certIds)) {
            updated += query.setParameter("cert_ids", certIdBlock)
                .executeUpdate();
        }

        return updated;
    }

    /**
     * Takes a list of content access certificate ids and unlinks them from consumers.
     *
     * @param certIds
     *     certificate ids to be unlinked
     * @return a number of unlinked consumers
     */
    @Transactional
    public int unlinkCaCertificates(Collection<String> certIds) {
        if (certIds == null || certIds.isEmpty()) {
            return 0;
        }

        String jpql = """
            UPDATE Consumer c
            SET c.contentAccessCert = NULL, c.updated = :date
            WHERE c.contentAccessCert.id IN (:cert_ids)
            """;

        int updated = 0;
        Date updateTime = new Date();

        Query query = getEntityManager().createQuery(jpql)
            .setParameter("date", updateTime);

        for (Collection<String> certIdBlock : this.partition(certIds)) {
            updated += query.setParameter("cert_ids", certIdBlock)
                .executeUpdate();
        }

        return updated;
    }

    /**
     * Takes a list of consumers and of each, updates its owner to the given one.
     *
     * @param consumerIds
     *  consumer to be updated
     * @param newOwner
     *  owner to update the consumers to
     */
    @Transactional
    public void bulkUpdateOwner(Collection<String> consumerIds, Owner newOwner) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return;
        }
        if (newOwner == null) {
            throw new IllegalArgumentException("New owner is required!");
        }

        String jpql = """
            UPDATE Consumer c
            SET c.owner.id = :ownerId
            WHERE c.id IN (:consumers)
            """;

        Query query = getEntityManager().createQuery(jpql)
            .setParameter("ownerId", newOwner.getId());

        for (Collection<String> consumersBlock : this.partition(consumerIds)) {
            query.setParameter("consumers", consumersBlock)
                .executeUpdate();
        }
    }

    public Collection<String> lockAndLoadIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        // Sort and de-duplicate the provided collection of IDs so we have a deterministic locking
        // order for the entities (helps avoid deadlock)
        SortedSet<Serializable> idSet = new TreeSet<>(ids);

        // Fetch the IDs of locked entities from the DB...
        String hql = """
            SELECT c.id FROM Consumer c
            WHERE c.id IN (:ids)
            """;

        TypedQuery<String> query = getEntityManager()
            .createQuery(hql, String.class)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE);

        List<String> lockedIds = new ArrayList<>(idSet.size());
        for (List<Serializable> idBlock : this.partition(idSet)) {
            lockedIds.addAll(query.setParameter("ids", idBlock).getResultList());
        }

        return lockedIds;
    }

    /**
     * Retrieves all the non-manifest type consumer UUIDs that belong to the provided owner.
     *
     * @param ownerKey
     *  the key to the owner to retrieve the UUIDs for
     *
     * @return all non-manifest type consumer UUIDs for the provided owner. This method will not return null.
     */
    public List<String> getSystemConsumerUuidsByOwner(String ownerKey) {
        List<String> consumerUuids  = new ArrayList<>();
        if (ownerKey == null || ownerKey.isBlank()) {
            return consumerUuids;
        }

        String jpql = "SELECT c.uuid FROM Consumer c " +
            "JOIN ConsumerType type ON type.id = c.typeId " +
            "WHERE c.owner.key = :owner_key AND type.manifest = 'N'";

        return this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_key", ownerKey)
            .getResultList();
    }
}
