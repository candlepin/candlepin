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
import org.candlepin.util.FactValidator;
import org.candlepin.util.Util;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Hibernate;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Order;
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
    }

    private EntitlementCurator entitlementCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private DeletedConsumerCurator deletedConsumerCurator;
    private FactValidator factValidator;
    private Provider<HostCache> cachedHostsProvider;
    private PrincipalProvider principalProvider;

    @Inject
    public ConsumerCurator(EntitlementCurator entitlementCurator, ConsumerTypeCurator consumerTypeCurator,
        DeletedConsumerCurator deletedConsumerCurator, FactValidator factValidator,
        Provider<HostCache> cachedHostsProvider, PrincipalProvider principalProvider) {
        super(Consumer.class);
        this.entitlementCurator = entitlementCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.deletedConsumerCurator = deletedConsumerCurator;
        this.factValidator = factValidator;
        this.cachedHostsProvider = cachedHostsProvider;
        this.principalProvider = principalProvider;
    }

    @Transactional
    @Override
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

    @Transactional
    public Consumer replicate(Consumer consumer) {
        for (Entitlement entitlement : consumer.getEntitlements()) {
            entitlement.setConsumer(consumer);
        }

        consumer.setTypeId(consumer.getTypeId());

        IdentityCertificate idCert = consumer.getIdCert();
        this.currentSession().replicate(idCert.getSerial(), ReplicationMode.EXCEPTION);
        this.currentSession().replicate(idCert, ReplicationMode.EXCEPTION);

        this.currentSession().replicate(consumer, ReplicationMode.EXCEPTION);

        return consumer;
    }

    /**
     * Lookup consumer by its virt.uuid.
     *
     * In some cases the hypervisor will report UUIDs with uppercase, while the guest will report
     * lowercase. As such we do case insensitive comparison when looking these up.
     *
     * @param uuid
     *     consumer virt.uuid to find
     * @return Consumer whose name matches the given virt.uuid, null otherwise.
     */
    @Transactional
    public Consumer findByVirtUuid(String uuid, String ownerId) {
        Consumer result = null;
        List<String> possibleGuestIds = Util.getPossibleUuids(uuid);

        String sql = "select cp_consumer.id from cp_consumer " +
            "inner join cp_consumer_facts " +
            "on cp_consumer.id = cp_consumer_facts.cp_consumer_id " +
            "where cp_consumer_facts.mapkey = 'virt.uuid' and " +
            "lower(cp_consumer_facts.element) in (:guestids) " +
            "and cp_consumer.owner_id = :ownerid " +
            "order by cp_consumer.updated desc";

        Query q = currentSession().createSQLQuery(sql);
        q.setParameterList("guestids", possibleGuestIds);
        q.setParameter("ownerid", ownerId);
        List<String> options = q.list();

        if (options != null && options.size() != 0) {
            result = this.get(options.get(0));
        }

        return result;
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

        if (guestIds.size() == 0) {
            return guestConsumersMap;
        }

        List<String> possibleGuestIds = Util.getPossibleUuids(guestIds.toArray(new String[guestIds.size()]));

        String sql = "select cp_consumer.uuid from cp_consumer " +
            "inner join cp_consumer_facts " +
            "on cp_consumer.id = cp_consumer_facts.cp_consumer_id " +
            "where cp_consumer_facts.mapkey = 'virt.uuid' and " +
            "lower(cp_consumer_facts.element) in (:guestids) " +
            "and cp_consumer.owner_id = :ownerid " +
            "order by cp_consumer.updated desc";

        // We need to filter down to only the most recently registered consumer with
        // each guest ID.

        List<String> consumerUuids = new LinkedList<>();

        Iterable<List<String>> blocks = Iterables.partition(possibleGuestIds, getInBlockSize());

        Query query = this.currentSession()
            .createSQLQuery(sql)
            .setParameter("ownerid", ownerId);

        for (List<String> block : blocks) {
            query.setParameterList("guestids", block);
            consumerUuids.addAll(query.list());
        }

        if (consumerUuids.isEmpty()) {
            return guestConsumersMap;
        }

        // At this point we might have duplicates for re-registered consumers:
        for (Consumer c : this.findByUuidsAndOwner(consumerUuids, ownerId)) {
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
     * @param user
     *     User
     * @return Consumer for this user if one exists, null otherwise.
     */
    @Transactional
    public Consumer findByUser(User user) {
        return user != null ? this.findByUsername(user.getUsername()) : null;
    }

    /**
     * Candlepin supports the notion of a user being a consumer. When in effect a consumer will exist in
     * the system who is tied to a particular user.
     *
     * @param username
     *     the username to use to find a consumer
     * @return Consumer for this user if one exists, null otherwise.
     */
    @Transactional
    public Consumer findByUsername(String username) {
        ConsumerType person = consumerTypeCurator
            .getByLabel(ConsumerType.ConsumerTypeEnum.PERSON.getLabel());

        if (person != null) {
            return (Consumer) createSecureCriteria()
                .add(Restrictions.eq("username", username))
                .add(Restrictions.eq("typeId", person.getId())).uniqueResult();
        }

        return null;
    }

    /**
     * Lookup the Consumer by its UUID.
     *
     * @param uuid
     *     Consumer UUID sought.
     * @return Consumer whose UUID matches the given value, or null otherwise.
     */
    @Transactional
    public Consumer findByUuid(String uuid) {
        return getConsumer(uuid);
    }

    @Transactional
    public Collection<Consumer> findByUuids(Collection<String> uuids) {
        Set<Consumer> consumers = new HashSet<>();

        for (List<String> block : this.partition(uuids)) {
            // Unfortunately, this needs to be a secure criteria due to the contexts in which this
            // is called.
            Criteria criteria = this.createSecureCriteria()
                .add(Restrictions.in("uuid", block));

            consumers.addAll(criteria.list());
        }

        return consumers;
    }

    @Transactional
    public Collection<Consumer> findByUuidsAndOwner(Collection<String> uuids, String ownerId) {
        Set<Consumer> consumers = new HashSet<>();

        javax.persistence.Query query = this.getEntityManager()
            .createQuery("SELECT c FROM Consumer c WHERE c.ownerId = :oid AND c.uuid IN (:uuids)")
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
        Criteria criteria = this.createSecureCriteria()
            .add(Restrictions.eq("uuid", uuid));

        return (Consumer) criteria.uniqueResult();
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
            List<String> cids;

            // Unfortunately multiLoad does not accept a generic collection, so we need to cast it
            // or convert it as necessary.
            if (consumerIds instanceof List) {
                cids = (List<String>) consumerIds;
            }
            else {
                cids = new ArrayList(consumerIds);
            }

            return this.currentSession()
                .byMultipleIds(this.entityType())
                .enableSessionCheck(true)
                .enableOrderedReturn(false)
                .multiLoad(cids);
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
    @SuppressWarnings("unchecked")
    @Transactional
    public List<String> getDistinctSyspurposeAddonsByOwner(Owner owner) {
        String sql = "SELECT DISTINCT add_on FROM cp_sp_add_on, cp_consumer " +
            "WHERE cp_consumer.id = cp_sp_add_on.consumer_id " +
            "AND cp_consumer.owner_id = :ownerid " +
            "AND add_on IS NOT NULL " +
            "AND add_on != ''";
        Query query = this.currentSession().createSQLQuery(sql);
        query.setParameter("ownerid", owner.getId());
        return query.list();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public CandlepinQuery<Consumer> listByOwner(Owner owner) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()));

        return this.cpQueryFactory.<Consumer>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Search for Consumers with fields matching those provided.
     *
     * @param userName
     *     the username to match, or null to ignore
     * @param types
     *     the types to match, or null/empty to ignore
     * @param owner
     *     Optional owner to filter on, pass null to skip.
     * @return a list of matching Consumers
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public CandlepinQuery<Consumer> listByUsernameAndType(String userName, List<ConsumerType> types,
        Owner owner) {

        DetachedCriteria criteria = this.createSecureDetachedCriteria();

        if (userName != null) {
            criteria.add(Restrictions.eq("username", userName));
        }

        if (types != null && !types.isEmpty()) {
            criteria.add(Restrictions.in("type", types));
        }

        if (owner != null) {
            criteria.add(Restrictions.eq("owner", owner));
        }

        return this.cpQueryFactory.<Consumer>buildQuery(this.currentSession(), criteria);
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
     * <p>
     * </p>
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

        if (flush) {
            save(existingConsumer);
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
        String hql = "UPDATE Consumer c SET c.lastCheckin = :date, c.updated = :date WHERE c.id = :cid";

        this.currentSession().createQuery(hql)
            .setTimestamp("date", checkinDate)
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

        this.currentSession().createSQLQuery(query)
            .setParameter("checkin", checkIn)
            .setParameter("reporter", reporterId)
            .setParameter("ownerKey", ownerKey)
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
     * @return updated consumers
     */
    @Transactional
    public Set<Consumer> bulkUpdate(Set<Consumer> consumers) {
        return bulkUpdate(consumers, true);
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

        Disjunction guestIdCrit = Restrictions.disjunction();
        for (String possibleId : Util.getPossibleUuids(guestId)) {
            guestIdCrit.add(Restrictions.eq("guestIdLower", possibleId.toLowerCase()));
        }

        Criteria crit = currentSession()
            .createCriteria(GuestId.class)
            .createAlias("consumer", "gconsumer")
            .add(Restrictions.eq("gconsumer.ownerId", ownerId))
            .add(guestIdCrit)
            .addOrder(org.hibernate.criterion.Order.desc("updated"))
            .setMaxResults(1)
            .setProjection(Projections.property("consumer"));

        Consumer host = (Consumer) crit.uniqueResult();
        cachedHostsProvider.get().put(key, host);
        return host;
    }

    /**
     * Creates a mapping of input guest IDs to GuestID objects currently tracked and stored in the
     * backing database. If a given guest ID is not present in the database, it will be mapped to a null
     * value.
     *
     * @param guestIds
     *     A collection of guest IDs to map to existing GuestID objects
     *
     * @param owner
     *     The owner to which the mapping lookup should be scoped
     *
     * @return a mapping of guest IDs to GuestID objects
     */
    public Map<String, GuestId> getGuestIdMap(Iterable<String> guestIds, Owner owner) {
        if (guestIds == null || owner == null) {
            return Collections.<String, GuestId>emptyMap();
        }

        String hql = "SELECT gid FROM GuestId gid " +
            "WHERE gid.consumer.ownerId = :owner AND gid.guestIdLower IN (:guest_ids)" +
            "ORDER BY gid.updated DESC";

        TypedQuery<GuestId> query = this.getEntityManager().createQuery(hql, GuestId.class);
        Map<String, GuestId> output = new HashMap<>();

        for (List<String> block : this.partition(guestIds)) {
            List<String> sanitized = new ArrayList<>(block.size());

            for (String guestId : block) {
                if (guestId != null && !guestId.isEmpty() && !output.containsKey(guestId)) {
                    sanitized.add(guestId.toLowerCase());
                    output.put(guestId, null);
                }
            }

            List<GuestId> guests = query
                .setParameter("owner", owner.getId())
                .setParameter("guest_ids", sanitized)
                .getResultList();

            if (guests != null) {
                for (GuestId fetched : guests) {
                    GuestId existing = output.get(fetched.getGuestId());

                    if (existing == null || this.safeDateAfter(fetched.getUpdated(), existing.getUpdated())) {
                        output.put(fetched.getGuestId(), fetched);
                    }
                }
            }
        }

        return output;
    }

    private boolean safeDateAfter(Date d1, Date d2) {
        return d1 != null && (d2 == null || d1.after(d2));
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
     * @param owner
     *     Org namespace to search
     * @return Consumer that matches the given
     */
    @Transactional
    public Consumer getHypervisor(String hypervisorId, Owner owner) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.eq("ownerId", owner.getId()))
            .createAlias("hypervisorId", "hvsr")
            .add(Restrictions.eq("hvsr.hypervisorId", hypervisorId.toLowerCase()))
            .setMaxResults(1)
            .uniqueResult();
    }

    /**
     * Lookup all registered consumers matching one of the given hypervisor IDs.
     *
     * Results are returned as a map of hypervisor ID to the consumer record created. If a hypervisor ID
     * is not in the map, this indicates the hypervisor consumer does not exist, i.e. it is new and
     * needs to be created.
     *
     * This is an unsecured query, manually limited to an owner by the parameter given.
     *
     * @param owner
     *     Owner to limit results to.
     * @param hypervisorIds
     *     Collection of hypervisor IDs as reported by the virt fabric.
     *
     * @return VirtConsumerMap of hypervisor ID to it's consumer, or null if none exists.
     */
    @Transactional
    public VirtConsumerMap getHostConsumersMap(Owner owner, Iterable<String> hypervisorIds) {
        VirtConsumerMap hypervisorMap = new VirtConsumerMap();

        for (Consumer consumer : this.getHypervisorsBulk(hypervisorIds, owner.getId())) {
            hypervisorMap.add(consumer.getHypervisorId().getHypervisorId(), consumer);
        }

        return hypervisorMap;
    }

    /**
     * Lookup all registered consumers matching either: matching the fact dmi.system.uuid or the given
     * hypervisor IDs.
     *
     * Results are returned as a map of hypervisor ID to the consumer record created. If a hypervisor ID
     * is not in the map, this indicates the hypervisor consumer does not exist, i.e. it is new and
     * needs to be created.
     *
     * This is an unsecured query, manually limited to an owner by the parameter given.
     *
     * @param owner
     *     Owner to limit results to.
     * @param hypervisors
     *     Collection of consumers with either hypervisor IDs or dmi.system.uuid fact as reported by the
     *     virt fabric.
     *
     * @return VirtConsumerMap of hypervisor ID to it's consumer, or null if none exists.
     */
    @Transactional
    public VirtConsumerMap getHostConsumersMap(Owner owner, List<Consumer> hypervisors) {
        VirtConsumerMap hypervisorMap = new VirtConsumerMap();

        Map<String, HypervisorId> systemUuidHypervisorMap = new HashMap<>();
        List<String> remainingHypervisorIds = new LinkedList<>();

        for (Consumer consumer : hypervisors) {
            if (consumer.hasFact(Consumer.Facts.DMI_SYSTEM_UUID)) {
                systemUuidHypervisorMap.put(consumer.getFact(Consumer.Facts.DMI_SYSTEM_UUID).toLowerCase(),
                    consumer.getHypervisorId());
            }

            remainingHypervisorIds.add(consumer.getHypervisorId().getHypervisorId());
        }

        if (!systemUuidHypervisorMap.isEmpty()) {
            String sql = "select id from cp_consumer " +
                "inner join cp_consumer_facts " +
                "on cp_consumer.id = cp_consumer_facts.cp_consumer_id " +
                "where cp_consumer_facts.mapkey = :fact_key and " +
                "lower(cp_consumer_facts.element) in (:uuids) " +
                "and cp_consumer.owner_id = :ownerid " +
                "order by cp_consumer.updated desc";

            Iterable<List<String>> blocks = Iterables.partition(systemUuidHypervisorMap.keySet(),
                getInBlockSize());

            Query query = this.currentSession()
                .createSQLQuery(sql)
                .setParameter("fact_key", Consumer.Facts.DMI_SYSTEM_UUID)
                .setParameter("ownerid", owner.getId());

            List<String> consumerIds = new LinkedList<>();
            for (List<String> block : blocks) {
                query.setParameterList("uuids", block);
                consumerIds.addAll(query.list());
            }

            for (Consumer consumer : this.getConsumers(consumerIds)) {
                if (consumer.getHypervisorId() != null) {
                    hypervisorMap.add(consumer.getHypervisorId().getHypervisorId(), consumer);
                    remainingHypervisorIds.remove(consumer.getHypervisorId().getHypervisorId());
                }
                else {
                    hypervisorMap.add(consumer.getFact(Consumer.Facts.DMI_SYSTEM_UUID).toLowerCase(),
                        consumer);
                }
            }
        }
        if (!remainingHypervisorIds.isEmpty()) {
            for (Consumer consumer : this.getHypervisorsBulk(remainingHypervisorIds, owner.getId())) {
                hypervisorMap.add(consumer.getHypervisorId().getHypervisorId(), consumer);
            }
        }

        return hypervisorMap;
    }

    @Transactional
    public Consumer getExistingConsumerByHypervisorIdOrUuid(String ownerId, String hypervisorId,
        String systemUuid) {
        VirtConsumerMap hypervisorMap = new VirtConsumerMap();
        Consumer found = null;
        String sql = "select consumer_id from cp_consumer_hypervisor " +
            "where hypervisor_id = :hypervisorId " +
            "and owner_id = :ownerId";

        Query query = this.currentSession()
            .createSQLQuery(sql)
            .setParameter("ownerId", ownerId)
            .setParameter("hypervisorId", hypervisorId.toLowerCase());
        List<String> consumerIds = query.list();

        if (consumerIds != null && consumerIds.size() > 0) {
            List<String> one = Collections.singletonList(consumerIds.get(0));
            found = (Consumer) ((List) this.getConsumers(one)).get(0);
        }
        else if (systemUuid != null) {
            sql = "select cp_consumer.id from cp_consumer " +
                "join cp_consumer_facts on cp_consumer.id = cp_consumer_facts.cp_consumer_id " +
                "where cp_consumer_facts.mapkey = :fact_key and " +
                "lower(cp_consumer_facts.element) = :uuid " +
                "and cp_consumer.owner_id = :ownerId " +
                "order by cp_consumer.updated desc";

            query = this.currentSession()
                .createSQLQuery(sql)
                .setParameter("fact_key", Consumer.Facts.DMI_SYSTEM_UUID)
                .setParameter("ownerId", ownerId)
                .setParameter("uuid", systemUuid.toLowerCase());

            consumerIds = query.list();
            if (consumerIds != null && consumerIds.size() > 0) {
                List<String> one = Collections.singletonList(consumerIds.get(0));
                found = (Consumer) ((List) this.getConsumers(one)).get(0);
            }
        }
        return found;
    }

    /**
     * Retrieves the identity Certificate ids for the provided consumer ids.
     *
     * @param consumerIds
     *     - ids of the {@link Consumer}s.
     * @return identity Certificate ids.
     */
    public List<String> getIdentityCertIds(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return new ArrayList<>();
        }

        String retrieveIdCertsHql = "SELECT idCert.id FROM Consumer WHERE id IN (:consumerIds)";
        List<String> idCertIds = entityManager.get()
            .createQuery(retrieveIdCertsHql)
            .setParameter("consumerIds", consumerIds)
            .getResultList();

        return idCertIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
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

        String retrieveScaCertsHql = "SELECT contentAccessCert.id FROM Consumer WHERE id IN (:consumerIds)";
        List<String> caCertIds = entityManager.get()
            .createQuery(retrieveScaCertsHql)
            .setParameter("consumerIds", consumerIds)
            .getResultList();

        return caCertIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves all the serial ids for provided {@link ContentAccessCertificate} ids and
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
        if (caCertIds != null && !caCertIds.isEmpty()) {
            String caCertHql = "SELECT ca.serial.id " +
                "FROM ContentAccessCertificate ca " +
                "WHERE ca.id IN (:certIds)";

            List<Long> caCertSerialIds = entityManager.get()
                .createQuery(caCertHql)
                .setParameter("certIds", caCertIds)
                .getResultList();

            serialIds.addAll(caCertSerialIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        }

        if (idCertIds != null && !idCertIds.isEmpty()) {
            String idCertHql = "SELECT idcert.serial.id " +
                "FROM IdentityCertificate idcert " +
                "WHERE idcert.id IN (:certIds)";

            List<Long> idCertSerialIds = entityManager.get()
                .createQuery(idCertHql)
                .setParameter("certIds", idCertIds)
                .getResultList();

            serialIds.addAll(idCertSerialIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        }

        return serialIds;
    }

    /**
     * Deletes {@link Consumer}s based on the provided consumer ids.
     *
     * @param consumerIds
     *     - ids of the consumers to delete.
     * @return the number of consumer that were deleted.
     */
    public int deleteConsumers(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return 0;
        }

        entityManager.get()
            .createQuery("DELETE Consumer WHERE id IN (:consumerIds)")
            .setParameter("consumerIds", consumerIds)
            .executeUpdate();

        return consumerIds.size();
    }

    /**
     * Retrieves the Ids for inactive {@link Consumer}s based on the provided last checked in retention
     * date and the last updated retention date. Consumers are considered inactive if the have a checked
     * in date and that date is older than the provided checked in retention date, or the consumer's
     * update date is older than the provided last update retention date. Also, the consumer must not
     * have any attached entitlements and must have a non-manifest type to be considered inactive.
     *
     * @param lastCheckedInRetention
     *     - consumers that have not checked in before this date are considered inactive.
     * @param lastUpdatedRetention
     *     - if the consumer has no checked in date, then the consumers that have an update date older
     *     than the provided retention date is considered inactive.
     * @return a list of Ids for inactive {@link Consumer}s.
     */
    public List<String> getInactiveConsumerIds(Instant lastCheckedInRetention, Instant lastUpdatedRetention) {
        if (lastCheckedInRetention == null) {
            throw new IllegalArgumentException("Last checked-in retention date cannot be null.");
        }

        if (lastUpdatedRetention == null) {
            throw new IllegalArgumentException("Last updated retention date cannot be null.");
        }

        String hql = "SELECT consumer.id " +
            "FROM Consumer consumer " +
            "JOIN ConsumerType type ON type.id=consumer.typeId " +
            "LEFT JOIN Entitlement ent ON ent.consumer.id=consumer.id " +
            "WHERE ((consumer.lastCheckin < :lastCheckedInRetention) " +
            "    OR (consumer.lastCheckin IS NULL AND consumer.updated < :nonCheckedInRetention)) " +
            "AND ent.consumer.id IS NULL " +
            "AND type.manifest = 'N'";

        return entityManager.get()
            .createQuery(hql)
            .setParameter("lastCheckedInRetention", Date.from(lastCheckedInRetention))
            .setParameter("nonCheckedInRetention", Date.from(lastUpdatedRetention))
            .getResultList();
    }

    /**
     * @param hypervisorIds
     *     list of unique hypervisor identifiers
     * @param ownerId
     *     Org namespace to search
     * @return Consumer that matches the given
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public CandlepinQuery<Consumer> getHypervisorsBulk(Iterable<String> hypervisorIds, String ownerId) {
        if (hypervisorIds == null || !hypervisorIds.iterator().hasNext()) {
            return this.cpQueryFactory.<Consumer>buildQuery();
        }

        DetachedCriteria criteria = DetachedCriteria.forClass(Consumer.class)
            .createAlias("hypervisorId", "hvsr")
            .add(Restrictions.eq("ownerId", ownerId))
            .add(this.getHypervisorIdRestriction(hypervisorIds))
            .addOrder(org.hibernate.criterion.Order.asc("hvsr.hypervisorId"))
            .setFetchMode("type", FetchMode.SELECT);

        return this.cpQueryFactory.<Consumer>buildQuery(this.currentSession(), criteria)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    private Criterion getHypervisorIdRestriction(Iterable<String> hypervisorIds) {
        Disjunction disjunction = Restrictions.disjunction();
        for (String hid : hypervisorIds) {
            disjunction.add(Restrictions.eq("hvsr.hypervisorId", hid.toLowerCase()));
        }

        return disjunction;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public CandlepinQuery<Consumer> getHypervisorsForOwner(String ownerId) {

        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .createAlias("hypervisorId", "hvsr")
            .add(Restrictions.eq("ownerId", ownerId))
            .add(Restrictions.isNotNull("hvsr.hypervisorId"));

        return this.cpQueryFactory.<Consumer>buildQuery(this.currentSession(), criteria);
    }

    public boolean doesConsumerExist(String uuid) {
        long result = (Long) createSecureCriteria()
            .add(Restrictions.eq("uuid", uuid))
            .setProjection(Projections.count("id"))
            .uniqueResult();

        return result != 0;
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

            String querySql = "SELECT C.uuid FROM Consumer C WHERE C.uuid IN (:uuids)";
            javax.persistence.Query query = this.getEntityManager().createQuery(querySql);

            for (List<String> block : Iterables.partition(consumerUuids, blockSize)) {
                existingUuids.addAll(query.setParameter("uuids", block).getResultList());
            }
        }

        return existingUuids;
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
            criteriaQuery.where(predicates.toArray(new Predicate[predicates.size()]));
        }

        List<Order> order = this.buildJPAQueryOrder(criteriaBuilder, root, queryArgs);
        if (order != null && order.size() > 0) {
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
            criteriaQuery.where(predicates.toArray(new Predicate[predicates.size()]));
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
        Criteria c = createSecureCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()))
            .add(Restrictions.eq("typeId", type.getId()))
            .createAlias("entitlements", "ent")
            .setProjection(Projections.sum("ent.quantity"));

        Long result = (Long) c.uniqueResult();
        return result == null ? 0 : result.intValue();
    }

    @SuppressWarnings("unchecked")
    public List<String> getConsumerIdsWithStartedEnts() {
        Date now = new Date();
        return currentSession().createCriteria(Entitlement.class)
            .createAlias("pool", "p")
            .add(Restrictions.eq("updatedOnStart", false))
            .add(Restrictions.lt("p.startDate", now))
            .setProjection(Projections.property("consumer.id"))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            .list();
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

        String query = "UPDATE Consumer c" +
            " SET c.idCert = NULL, c.updated = :date" +
            " WHERE c.idCert.id IN (:cert_ids)";

        int updated = 0;
        Date updateTime = new Date();
        for (Collection<String> certIdBlock : this.partition(certIds)) {
            updated += this.currentSession().createQuery(query)
                .setParameter("date", updateTime)
                .setParameter("cert_ids", certIdBlock)
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

        String query = "UPDATE Consumer c" +
            " SET c.contentAccessCert = NULL, c.updated = :date" +
            " WHERE c.contentAccessCert.id IN (:cert_ids)";

        int updated = 0;
        Date updateTime = new Date();
        for (Collection<String> certIdBlock : this.partition(certIds)) {
            updated += this.currentSession().createQuery(query)
                .setParameter("date", updateTime)
                .setParameter("cert_ids", certIdBlock)
                .executeUpdate();
        }

        return updated;
    }
}
