/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.util.FactValidator;
import org.candlepin.util.Util;

import com.google.common.collect.Iterables;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Provider;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;



/**
 * ConsumerCurator
 */
@Component
@Transactional
public class ConsumerCurator extends AbstractHibernateCurator<Consumer> {
    private static Logger log = LoggerFactory.getLogger(ConsumerCurator.class);

    @Autowired
    private EntitlementCurator entitlementCurator;
    @Autowired
    private ConsumerTypeCurator consumerTypeCurator;
    @Autowired
    private DeletedConsumerCurator deletedConsumerCurator;
    @Autowired
    private Configuration config;
    @Autowired
    private FactValidator factValidator;
    @Autowired
    private OwnerCurator ownerCurator;
    @Autowired
    private Provider<HostCache> cachedHostsProvider;
    @Autowired
    private PrincipalProvider principalProvider;

    public ConsumerCurator() {
        super(Consumer.class);
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
     * In some cases the hypervisor will report UUIDs with uppercase, while the guest will
     * report lowercase. As such we do case insensitive comparison when looking these up.
     *
     * @param uuid consumer virt.uuid to find
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
     * Maps guest ID to the most recent registered consumer that matches it.
     * Any guest ID not found will not return null.
     *
     * If multiple registered consumers report this guest ID (re-registraiton), only the
     * most recently updated will be returned.
     *
     * @param guestIds
     *
     * @return VirtConsumerMap of guest ID to it's registered guest consumer, or null if
     * none exists.
     */
    @Transactional
    public VirtConsumerMap getGuestConsumersMap(String ownerId, Set<String> guestIds) {
        VirtConsumerMap guestConsumersMap = new VirtConsumerMap();

        if (guestIds.size() == 0) {
            return guestConsumersMap;
        }

        List<String> possibleGuestIds = Util.getPossibleUuids(guestIds.toArray(new String [guestIds.size()]));

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
            String virtUuid = c.getFact("virt.uuid").toLowerCase();
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
     * Candlepin supports the notion of a user being a consumer. When in effect
     * a consumer will exist in the system who is tied to a particular user.
     *
     * @param user User
     * @return Consumer for this user if one exists, null otherwise.
     */
    @Transactional
    public Consumer findByUser(User user) {
        return user != null ? this.findByUsername(user.getUsername()) : null;
    }

    /**
     * Candlepin supports the notion of a user being a consumer. When in effect
     * a consumer will exist in the system who is tied to a particular user.
     *
     * @param username the username to use to find a consumer
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
     * @param uuid Consumer UUID sought.
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
     * matching consumer object will be returned, nor will an exception be thrown. As such, the
     * number of consumer objects fetched may be lower than the number of consumer IDs provided.
     *
     * @param consumerIds
     *  A collection of consumer IDs specifying the consumers to fetch
     *
     * @return
     *  A query to fetch the consumers with the specified consumer IDs
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
     * Fetches all unique role attribute values set by all the consumers of the specified owner.
     *
     * @param owner
     *  The owner the consumers belong to.
     * @return
     *  A list of the all the distinct values of the role attribute that the consumers belonging to the
     *  specified owner have set.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public List<String> getDistinctSyspurposeRolesByOwner(Owner owner) {
        return this.createSecureCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()))
            .add(Restrictions.neOrIsNotNull("role", ""))
            .setProjection(Projections.property("role"))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            .list();
    }

    /**
     * Fetches all unique usage attribute values set by all the consumers of the specified owner.
     *
     * @param owner
     *  The owner the consumers belong to.
     * @return
     *  A list of the all the distinct values of the usage attribute that the consumers belonging to the
     *  specified owner have set.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public List<String> getDistinctSyspurposeUsageByOwner(Owner owner) {
        return this.createSecureCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()))
            .add(Restrictions.neOrIsNotNull("usage", ""))
            .setProjection(Projections.property("usage"))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            .list();
    }

    /**
     * Fetches all unique serviceLevel attribute values set by all the consumers of the specified owner.
     *
     * @param owner
     *  The owner the consumers belong to.
     * @return
     *  A list of the all the distinct values of the serviceLevel attribute that the consumers belonging
     *  to the specified owner have set.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public List<String> getDistinctSyspurposeServicelevelByOwner(Owner owner) {
        return this.createSecureCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()))
            .add(Restrictions.neOrIsNotNull("serviceLevel", ""))
            .setProjection(Projections.property("serviceLevel"))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            .list();
    }

    /**
     * Fetches all unique addon attribute values set by all the consumers of the specified owner.
     *
     * @param owner
     *  The owner the consumers belong to.
     * @return
     *  A list of the all the distinct values of the addon attribute that the consumers belonging
     *  to the specified owner have set.
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
     * @param userName the username to match, or null to ignore
     * @param types the types to match, or null/empty to ignore
     * @param owner Optional owner to filter on, pass null to skip.
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
     * @param updatedConsumer updated Consumer values.
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
     * <strong>Warning:</strong> Using an pre-existing and persisted Consumer entity as the update
     * to apply may cause issues, as Hibernate may opt to save changes to nested collections
     * (facts, guestIds, tags, etc.) when any other database operation is performed. To avoid this
     * issue, it is advised to use only detached or otherwise unmanaged entities for the updated
     * consumer to pass to this method.
     *
     * @param updatedConsumer
     *  A Consumer instance representing the updated state of a consumer
     *
     * @param flush
     *  Whether or not to flush pending database operations after creating or updating the given
     *  consumer
     *
     * @return
     *  The persisted, updated consumer
     */
    @Transactional
    public Consumer update(Consumer updatedConsumer, boolean flush) {
        // TODO: FIXME:
        // We really need to use a DTO here. Hibernate has so many pitfalls with this approach that
        // can and will lead to odd, broken or out-of-order behavior.

        // Validate inbound facts before even attempting to apply the update
        this.validateFacts(updatedConsumer);

        Consumer existingConsumer = this.get(updatedConsumer.getId());

        if (existingConsumer == null) {
            return this.create(updatedConsumer, flush);
        }

        // TODO: Are any of these read-only?
        existingConsumer.setEntitlements(entitlementCurator.bulkUpdate(updatedConsumer.getEntitlements()));

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

        if (flush) {
            save(existingConsumer);
        }

        return existingConsumer;
    }

    /**
     * Modifies the last check in and persists the entity. Make sure that the data
     * is refreshed before using this method.
     * @param consumer the consumer to update
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
        final String db = ((String) this.currentSession().getSessionFactory().getProperties()
            .get("hibernate.dialect")).toLowerCase();
        if (db.contains("mysql") || db.contains("maria")) {
            query = "" +
                "UPDATE cp_consumer a" +
                " JOIN cp_consumer_hypervisor b on a.id = b.consumer_id " +
                " JOIN cp_owner c on c.id = a.owner_id" +
                " SET a.lastcheckin = :checkin" +
                " WHERE b.reporter_id = :reporter" +
                " AND c.account = :ownerKey";
        }
        else if (db.contains("postgresql")) {
            query = "" +
                "UPDATE cp_consumer" +
                " SET lastcheckin = :checkin" +
                " FROM cp_consumer a, cp_consumer_hypervisor b, cp_owner c" +
                " WHERE a.id = b.consumer_id" +
                " AND b.reporter_id = :reporter" +
                " AND cp_consumer.owner_id = c.id" +
                " AND c.account = :ownerKey";
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

    private boolean factsChanged(Map<String, String> updatedFacts, Map<String, String> existingFacts) {
        return !existingFacts.equals(updatedFacts);
    }

    /**
     * Validates the facts associated with the given consumer. If any fact fails validation a
     * PropertyValidationException will be thrown.
     *
     * @param consumer
     *  The consumer containing the facts to validate
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
     * @param consumers consumers to update
     * @return updated consumers
     */
    @Transactional
    public Set<Consumer> bulkUpdate(Set<Consumer> consumers) {
        return bulkUpdate(consumers, true);
    }

    /**
     * @param consumers consumers to update
     * @param flush whether to flush or not
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
     * As multiple hosts could have reported the same guest ID, we find the newest
     * and assume this is the authoritative host for the guest.
     *
     * This search needs to be case insensitive as some hypervisors report uppercase
     * guest UUIDs, when the guest itself will report lowercase.
     *
     * The first lookup will retrieve the host and then place it in the map. This
     * will save from reloading the host from the database if it is asked for again
     * during the session. An auto-bind can call this method up to 50 times and this
     * will cut the database calls significantly.
     *
     * @param guestId a virtual guest ID (not a consumer UUID)
     * @param ownerId ID of the organization to scope the search
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
            .addOrder(Order.desc("updated"))
            .setMaxResults(1)
            .setProjection(Projections.property("consumer"));

        Consumer host = (Consumer) crit.uniqueResult();
        cachedHostsProvider.get().put(key, host);
        return host;
    }

    /**
     * Creates a mapping of input guest IDs to GuestID objects currently tracked and stored in the
     * backing database. If a given guest ID is not present in the database, it will be mapped to
     * a null value.
     *
     * @param guestIds
     *  A collection of guest IDs to map to existing GuestID objects
     *
     * @param owner
     *  The owner to which the mapping lookup should be scoped
     *
     * @return
     *  a mapping of guest IDs to GuestID objects
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
     * @param consumer host consumer to find the guests for
     * @return list of registered guest consumers for this host
     */
    @Transactional
    public List<Consumer> getGuests(Consumer consumer) {
        if (consumer.getFact("virt.uuid") != null &&
            !consumer.getFact("virt.uuid").trim().equals("")) {
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
     * This is an insecure query, because we need to know whether or not the
     * consumer exists
     *
     * We do not require that the hypervisor be consumerType hypervisor
     * because we need to allow regular consumers to be given
     * HypervisorIds to be updated via hypervisorResource
     *
     * @param hypervisorId Unique identifier of the hypervisor
     * @param owner Org namespace to search
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
     * Results are returned as a map of hypervisor ID to the consumer record created.
     * If a hypervisor ID is not in the map, this indicates the hypervisor consumer does
     * not exist, i.e. it is new and needs to be created.
     *
     * This is an unsecured query, manually limited to an owner by the parameter given.
     * @param owner Owner to limit results to.
     * @param hypervisorIds Collection of hypervisor IDs as reported by the virt fabric.
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
     * Lookup all registered consumers matching either:
     * matching the fact dmi.system.uuid or
     * the given hypervisor IDs.
     *
     * Results are returned as a map of hypervisor ID to the consumer record created.
     * If a hypervisor ID is not in the map, this indicates the hypervisor consumer does
     * not exist, i.e. it is new and needs to be created.
     *
     * This is an unsecured query, manually limited to an owner by the parameter given.
     * @param owner Owner to limit results to.
     * @param hypervisors Collection of consumers with either hypervisor IDs or dmi.system.uuid fact
     *                       as reported by the virt fabric.
     *
     * @return VirtConsumerMap of hypervisor ID to it's consumer, or null if none exists.
     */
    @Transactional
    public VirtConsumerMap getHostConsumersMap(Owner owner, List<Consumer> hypervisors) {
        VirtConsumerMap hypervisorMap = new VirtConsumerMap();

        Map<String, HypervisorId> systemUuidHypervisorMap = new HashMap<>();
        List<String> remainingHypervisorIds = new LinkedList<>();
        for (Consumer consumer : hypervisors) {
            if (consumer.hasFact(Consumer.Facts.SYSTEM_UUID)) {
                systemUuidHypervisorMap.put(consumer.getFact(Consumer.Facts.SYSTEM_UUID).toLowerCase(),
                    consumer.getHypervisorId());
            }
            remainingHypervisorIds.add(consumer.getHypervisorId().getHypervisorId());
        }
        if (!systemUuidHypervisorMap.isEmpty()) {
            String sql = "select id from cp_consumer " +
                "inner join cp_consumer_facts " +
                "on cp_consumer.id = cp_consumer_facts.cp_consumer_id " +
                "where cp_consumer_facts.mapkey = '" + Consumer.Facts.SYSTEM_UUID + "' and " +
                "lower(cp_consumer_facts.element) in (:uuids) " +
                "and cp_consumer.owner_id = :ownerid " +
                "order by cp_consumer.updated desc";

            Iterable<List<String>> blocks = Iterables.partition(systemUuidHypervisorMap.keySet(),
                getInBlockSize());
            Query query = this.currentSession()
                .createSQLQuery(sql)
                .setParameter("ownerid", owner.getId());

            List<String> consumerIds = new LinkedList<>();
            for (List<String> block : blocks) {
                query.setParameterList("uuids", block);
                consumerIds.addAll(query.list());
            }
            for (Consumer consumer: this.getConsumers(consumerIds)) {
                if (consumer.getHypervisorId() != null) {
                    hypervisorMap.add(consumer.getHypervisorId().getHypervisorId(), consumer);
                    remainingHypervisorIds.remove(consumer.getHypervisorId().getHypervisorId());
                }
                else {
                    hypervisorMap.add(consumer.getFact(Consumer.Facts.SYSTEM_UUID).toLowerCase(), consumer);
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
        String sql =
            "select consumer_id from cp_consumer_hypervisor " +
            "where hypervisor_id = :hypervisorId " +
            "and owner_id = :ownerId";

        Query query = this.currentSession()
            .createSQLQuery(sql)
            .setParameter("ownerId", ownerId)
            .setParameter("hypervisorId", hypervisorId);
        List<String> consumerIds = query.list();

        if (consumerIds != null && consumerIds.size() > 0) {
            List<String> one = Collections.singletonList(consumerIds.get(0));
            found = (Consumer) ((List) this.getConsumers(one)).get(0);
        }
        else if (systemUuid != null) {
            sql =
                "select cp_consumer.id from cp_consumer " +
                    "join cp_consumer_facts on cp_consumer.id = cp_consumer_facts.cp_consumer_id " +
                    "where cp_consumer_facts.mapkey = '" + Consumer.Facts.SYSTEM_UUID + "' and " +
                    "lower(cp_consumer_facts.element) = :uuid " +
                    "and cp_consumer.owner_id = :ownerId " +
                    "order by cp_consumer.updated desc";
            query = this.currentSession()
                .createSQLQuery(sql)
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
     * @param hypervisorIds list of unique hypervisor identifiers
     * @param ownerId Org namespace to search
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
            .addOrder(Order.asc("hvsr.hypervisorId"))
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
     * @param consumerUuids consumer UUIDs.
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

    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Consumer> searchOwnerConsumers(Owner owner, String userName,
        Collection<ConsumerType> types, List<String> uuids, List<String> hypervisorIds,
        List<KeyValueParameter> factFilters, List<String> skus,
        List<String> subscriptionIds, List<String> contracts) {

        DetachedCriteria crit = super.createSecureDetachedCriteria();
        if (owner != null) {
            crit.add(Restrictions.eq("ownerId", owner.getId()));
        }

        if (userName != null && !userName.isEmpty()) {
            crit.add(Restrictions.eq("username", userName));
        }

        if (types != null && !types.isEmpty()) {
            Collection<String> typeIds = types.stream()
                .filter(t -> t.getId() != null)
                .map(t -> t.getId())
                .collect(Collectors.toList());

            crit.add(CPRestrictions.in("typeId", typeIds));
        }

        if (uuids != null && !uuids.isEmpty()) {
            crit.add(CPRestrictions.in("uuid", uuids));
        }

        if (hypervisorIds != null && !hypervisorIds.isEmpty()) {
            // Cannot use Restrictions.in here because hypervisorId is case insensitive
            Set<Criterion> ors = new HashSet<>();
            for (String hypervisorId : hypervisorIds) {
                ors.add(Restrictions.eq("hvsr.hypervisorId", hypervisorId.toLowerCase()));
            }
            crit.createAlias("hypervisorId", "hvsr");
            crit.add(Restrictions.or(ors.toArray(new Criterion[ors.size()])));
        }

        if (factFilters != null && !factFilters.isEmpty()) {
            // Process the filters passed for the attributes
            FilterBuilder factFilter = new FactFilterBuilder();
            for (KeyValueParameter filterParam : factFilters) {
                factFilter.addAttributeFilter(filterParam.getKey(), filterParam.getValue());
            }
            factFilter.applyTo(crit);
        }

        boolean hasSkus = (skus != null && !skus.isEmpty());
        boolean hasSubscriptionIds = (subscriptionIds != null && !subscriptionIds.isEmpty());
        boolean hasContractNumbers = (contracts != null && !contracts.isEmpty());

        if (hasSkus || hasSubscriptionIds || hasContractNumbers) {
            if (hasSkus) {
                for (String sku : skus) {
                    DetachedCriteria subCrit = DetachedCriteria.forClass(Consumer.class, "subquery_consumer");

                    if (owner != null) {
                        subCrit.add(Restrictions.eq("ownerId", owner.getId()));
                    }

                    subCrit.createCriteria("entitlements")
                        .createCriteria("pool")
                        .createCriteria("product")
                        .createAlias("attributes", "attrib")
                        .add(Restrictions.eq("id", sku))
                        .add(Restrictions.eq("attrib.indices", "type"))
                        .add(Restrictions.eq("attrib.elements", "MKT"));

                    subCrit.add(Restrictions.eqProperty("this.id", "subquery_consumer.id"));

                    crit.add(Subqueries.exists(
                        subCrit.setProjection(Projections.property("subquery_consumer.name")))
                    );
                }
            }
            if (hasSubscriptionIds) {
                for (String subId : subscriptionIds) {
                    DetachedCriteria subCrit = DetachedCriteria.forClass(Consumer.class, "subquery_consumer");

                    if (owner != null) {
                        subCrit.add(Restrictions.eq("ownerId", owner.getId()));
                    }

                    subCrit.createCriteria("entitlements").createCriteria("pool")
                        .createCriteria("sourceSubscription").add(Restrictions.eq("subscriptionId", subId));
                    subCrit.add(Restrictions.eqProperty("this.id", "subquery_consumer.id"));

                    crit.add(Subqueries.exists(
                        subCrit.setProjection(Projections.property("subquery_consumer.name")))
                    );
                }
            }
            if (hasContractNumbers) {
                for (String contract : contracts) {
                    DetachedCriteria subCrit = DetachedCriteria.forClass(Consumer.class, "subquery_consumer");

                    if (owner != null) {
                        subCrit.add(Restrictions.eq("ownerId", owner.getId()));
                    }

                    subCrit.createCriteria("entitlements").createCriteria("pool").add(
                        Restrictions.eq("contractNumber", contract)
                    );
                    subCrit.add(Restrictions.eqProperty("this.id", "subquery_consumer.id"));

                    crit.add(Subqueries.exists(
                        subCrit.setProjection(Projections.property("subquery_consumer.name")))
                    );
                }
            }
        }

        return this.cpQueryFactory.<Consumer>buildQuery(this.currentSession(), crit);
    }

    /*
     *  JPQL of below criteria can look like this.
     *  If all parameters aren't passed then only sub-parts of it are returned.
     *
     *   select count(distinct c.id) from Consumer c join c.owner o
     *       join c.type ct
     *       join c.entitlements e
     *       join e.pool po
     *       join po.product pr
     *       join pr.attributes pa
     *       join po.sourceSubscription ss
     *   where o.key = :key
     *   and ct.label in (...)
     *   and pr.id in (...)
     *   and pa.name = 'type'
     *   and pa.value = 'MKT'
     *   and ss.subscriptionId in (...)
     *   and po.contractNumber in (...)
     *
     */
    public int countConsumers(String ownerKey,
        Collection<String> typeLabels, Collection<String> skus,
        Collection<String> subscriptionIds, Collection<String> contracts) {

        if (ownerKey == null || ownerKey.isEmpty()) {
            throw new IllegalArgumentException("Owner key can't be null or empty");
        }

        Criteria crit = super.createSecureCriteria("c");

        DetachedCriteria ownerCriteria = DetachedCriteria.forClass(Owner.class, "o")
            .setProjection(Property.forName("o.id"))
            .add(Restrictions.eq("o.key", ownerKey));
        crit.add(Property.forName("c.ownerId").in(ownerCriteria));

        if (!CollectionUtils.isEmpty(typeLabels)) {
            DetachedCriteria typeQuery = DetachedCriteria.forClass(ConsumerType.class, "ctype")
                .add(CPRestrictions.in("ctype.label", typeLabels))
                .setProjection(Projections.id());

            crit.add(Subqueries.propertyIn("c.typeId", typeQuery));
        }

        boolean hasSkus = !CollectionUtils.isEmpty(skus);
        boolean hasSubscriptionIds = !CollectionUtils.isEmpty(subscriptionIds);
        boolean hasContracts = !CollectionUtils.isEmpty(contracts);
        if (hasSkus || hasSubscriptionIds || hasContracts) {
            crit.createAlias("c.entitlements", "e").createAlias("e.pool", "po");
        }

        if (hasSkus) {
            crit.createAlias("po.product", "pr")
                .createAlias("pr.attributes", "pa")
                .add(CPRestrictions.in("pr.id", skus))
                .add(Restrictions.eq("pa.indices", "type"))
                .add(Restrictions.eq("pa.elements", "MKT"));
        }

        if (hasSubscriptionIds) {
            crit.createAlias("po.sourceSubscription", "ss")
                .add(CPRestrictions.in("ss.subscriptionId", subscriptionIds));
        }

        if (hasContracts) {
            crit.add(CPRestrictions.in("po.contractNumber", contracts));
        }

        crit.setProjection(Projections.countDistinct("c.id"));

        return ((Long) crit.uniqueResult()).intValue();
    }

    /**
     * Finds the consumer count for an Owner based on type.
     *
     * @param owner the owner to count consumers for
     * @param type the type of the Consumer to filter on.
     * @return the number of consumers based on the type.
     */
    public int getConsumerCount(Owner owner, ConsumerType type) {
        Criteria c = this.createSecureCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()))
            .add(Restrictions.eq("typeId", type.getId()))
            .setProjection(Projections.rowCount());

        return ((Long) c.uniqueResult()).intValue();
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
     *  the owner for which to fetch active consumer content access modes
     *
     * @param existingModes
     *  a var-arg list of existing modes to retain
     *
     * @throws IllegalArgumentException
     *  if owner is null, or the list of existing modes is null
     *
     * @return
     *  the number of consumers updated as a result of this operation
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

}
