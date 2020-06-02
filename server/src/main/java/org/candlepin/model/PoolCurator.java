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

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Filter;
import org.hibernate.Hibernate;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Junction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;
import org.hibernate.criterion.Subqueries;
import org.hibernate.internal.FilterImpl;
import org.hibernate.query.Query;
import org.hibernate.sql.JoinType;
import org.hibernate.type.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.TreeSet;

import javax.inject.Singleton;
import javax.persistence.TypedQuery;



/**
 * PoolCurator
 */
@Singleton
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    /** The recommended number of expired pools to fetch in a single call to listExpiredPools */
    public static final int EXPIRED_POOL_BLOCK_SIZE = 1000;

    private static Logger log = LoggerFactory.getLogger(PoolCurator.class);
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;

    @Inject
    protected Injector injector;

    @Inject
    public PoolCurator(ConsumerCurator consumerCurator, ConsumerTypeCurator consumerTypeCurator) {
        super(Pool.class);
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param owner Owner to filter
     * @return pools owned by the given Owner.
     */
    @Transactional
    public CandlepinQuery<Pool> listByOwner(Owner owner) {
        return listByOwner(owner, null);
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param owner Owner to filter
     * @param activeOn only include pools active on the given date.
     * @return pools owned by the given Owner.
     */
    @Transactional
    public CandlepinQuery<Pool> listByOwner(Owner owner, Date activeOn) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Pool.class)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("activeSubscription", Boolean.TRUE));

        if (activeOn != null) {
            criteria.add(Restrictions.le("startDate", activeOn))
                .add(Restrictions.ge("endDate", activeOn));
        }

        return this.cpQueryFactory.<Pool>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Fetches all pools for the given owner of the specified pool types. If no pools match the
     * given inputs, this method returns an empty list.
     *
     * @param ownerId
     *  the ID of the owner to use to match pools
     *
     * @param types
     *  a collection of pool types to use to match pools
     *
     * @return
     *  a list of pools matching the given owner and pool types
     */
    public List<Pool> listByOwnerAndTypes(String ownerId, PoolType... types) {
        if (types != null && types.length > 0) {
            String jpql = "SELECT p FROM Pool p WHERE p.owner.id = :owner_id AND p.type IN (:pool_types)";

            return this.getEntityManager()
                .createQuery(jpql, Pool.class)
                .setParameter("owner_id", ownerId)
                .setParameter("pool_types", Arrays.asList(types))
                .getResultList();
        }

        return new ArrayList<>();
    }

    /**
     * Return all pools referencing the given entitlement as their source entitlement.
     *
     * @param e Entitlement
     * @return Pools created as a result of this entitlement.
     */
    public CandlepinQuery<Pool> listBySourceEntitlement(Entitlement e) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("sourceEntitlement", e));

        return this.cpQueryFactory.<Pool>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Return all pools referencing the given entitlements as their source entitlements.
     * Works recursively. The method always takes the result and return all source entitlements
     * of the pools.
     * This method finds all the pools that have been created as direct consequence of creating
     * some of ents. So for example bonus pools created as consequence of creating ents.
     * @param ents Entitlements for which we search the pools
     * @return Pools created as a result of creation of one of the ents.
     */
    public Set<Pool> listBySourceEntitlements(Iterable<Entitlement> ents) {
        if (ents == null || !ents.iterator().hasNext()) {
            return new HashSet<>();
        }

        Set<Pool> output = new HashSet<>();

        // Impl note:
        // We're using the partitioning here as it's slightly faster to do individual queries,
        // and we eliminate the risk of hitting the query param limit. Since we weren't using
        // a CPQuery object to begin with, this is a low-effort win here.
        for (List<Entitlement> block : this.partition(ents)) {
            List<Pool> pools = createSecureCriteria()
                .add(CPRestrictions.in("sourceEntitlement", block))
                .setFetchMode("entitlements", FetchMode.JOIN)
                .list();

            if (pools != null) {
                output.addAll(pools);
            }
        }

        if (output.size() > 0) {
            Set<Pool> pools = this.listBySourceEntitlements(convertPoolsToEntitlements(output));
            output.addAll(pools);
        }

        return output;
    }

    private Set<Entitlement> convertPoolsToEntitlements(Collection<Pool> pools) {
        Set<Entitlement> output = new HashSet<>();

        for (Pool p : pools) {
            output.addAll(p.getEntitlements());
        }

        return output;
    }

    /**
     * Returns list of pools available to the consumer.
     *
     * @param c Consumer to filter
     * @return pools available to the consumer.
     */
    @Transactional
    public List<Pool> listByConsumer(Consumer c) {
        return listAvailableEntitlementPools(c, c.getOwnerId(), (Set<String>) null, null);
    }

    /**
     * List all entitlement pools for the given owner and product.
     *
     * @param owner owner of the entitlement pool
     * @param productId product filter.
     * @return list of EntitlementPools
     */
    @Transactional
    public List<Pool> listByOwnerAndProduct(Owner owner, String productId) {
        return listAvailableEntitlementPools(null, owner, productId, null);
    }

    /**
     * Fetches the list of non-derived, expired pools
     *
     * @return
     *  the list of non-derived, expired pools pools
     */
    public List<Pool> listExpiredPools() {
        return this.listExpiredPools(-1);
    }

    /**
     * Fetches a block of non-derived, expired pools from the database, using the specified block
     * size. If the given block size is a non-positive integer, all non-derived, expired pools will
     * be retrieved.
     * <p></p>
     * <strong>Note:</strong> This method does not set the offset (first result) for the block.
     * Unless the pools are being deleted, this method will repeatedly return the same pools. To
     * fetch all expired pools in blocks, the calling method must ensure that the pools are deleted
     * between calls to this method.
     *
     * @param blockSize
     *  The maximum number of pools to fetch; if block size is less than 1, no limit will be applied
     *
     * @return
     *  a list of non-derived, expired pools no larger than the specified block size
     */
    @Transactional
    @SuppressWarnings("checkstyle:indentation")
    public List<Pool> listExpiredPools(int blockSize) {
        Date now = new Date();

        DetachedCriteria entCheck = DetachedCriteria.forClass(Pool.class, "entPool")
            .createAlias("entitlements", "ent", JoinType.INNER_JOIN)
            .add(Restrictions.eqProperty("entPool.id", "tgtPool.id"))
            .add(Restrictions.ge("ent.endDateOverride", now))
            .setProjection(Projections.property("entPool.id"));

        Criteria criteria = this.createSecureCriteria("tgtPool")
            .add(Restrictions.lt("tgtPool.endDate", now))
            .add(Subqueries.notExists(entCheck));

        if (blockSize > 0) {
            criteria.setMaxResults(blockSize);
        }

        List<Pool> results = (List<Pool>) criteria.list();
        return results != null ? results : new LinkedList<>();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o, String productId, Date activeOn) {
        String ownerId = (o == null) ? null : o.getId();
        return listAvailableEntitlementPools(c, ownerId,
            (productId != null ? Arrays.asList(productId) : (Collection<String>) null), null, activeOn,
            new PoolFilterBuilder(), null, false, false, false, null).getPageData();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o, Collection<String> productIds,
        Date activeOn) {

        String ownerId = (o == null) ? null : o.getId();
        return listAvailableEntitlementPools(c, ownerId, productIds, null, activeOn,
            new PoolFilterBuilder(), null, false, false, false, null).getPageData();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Pool> listAvailableEntitlementPools(Consumer c, String ownerId, Collection<String> productIds,
        Date activeOn) {

        return listAvailableEntitlementPools(c, ownerId, productIds, null, activeOn,
            new PoolFilterBuilder(), null, false, false, false, null).getPageData();
    }

    @Transactional
    public List<Pool> listByFilter(PoolFilterBuilder filters) {
        return listAvailableEntitlementPools(
            null, null, (Set<String>) null, null, null, filters, null, false,
            false, false, null).getPageData();
    }

    @Transactional
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer c, String ownerId, String productId,
        String subscriptionId, Date activeOn, PoolFilterBuilder filters,
        PageRequest pageRequest, boolean postFilter, boolean addFuture, boolean onlyFuture, Date after) {

        return this.listAvailableEntitlementPools(c, ownerId,
            (productId != null ? Arrays.asList(productId) : (Collection<String>) null), subscriptionId,
            activeOn, filters, pageRequest, postFilter, addFuture, onlyFuture, after);
    }

    @Transactional
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer c, Owner o, String productId,
        String subscriptionId, Date activeOn, PoolFilterBuilder filters,
        PageRequest pageRequest, boolean postFilter, boolean addFuture, boolean onlyFuture, Date after) {

        String ownerId = (o == null) ? null : o.getId();
        return this.listAvailableEntitlementPools(c, ownerId,
            (productId != null ? Arrays.asList(productId) : (Collection<String>) null), subscriptionId,
            activeOn, filters, pageRequest, postFilter, addFuture, onlyFuture, after);
    }

    @Transactional
    public List<String> listEntitledConsumerUuids(String poolId) {
        return createSecureCriteria("pool")
            .add(Restrictions.eq("pool.id", poolId))
            .createAlias("entitlements", "entitlements", JoinType.INNER_JOIN)
            .createAlias("entitlements.consumer", "consumer", JoinType.INNER_JOIN)
            .setProjection(Projections.property("consumer.uuid"))
            .list();
    }
    /**
     * List entitlement pools.
     *
     * Pools will be refreshed from the underlying subscription service.
     *
     * @param consumer Consumer being entitled.
     * @param ownerId Owner whose subscriptions should be inspected.
     * @param productIds only entitlements which provide these products are included.
     * @param activeOn Indicates to return only pools valid on this date.
     *        Set to null for no date filtering.
     * @param filters filter builder with set filters to apply to the criteria.
     * @param pageRequest used to specify paging criteria.
     * @param postFilter if you plan on filtering the list in java
     * @return List of entitlement pools.
     */
    @Transactional
    @SuppressWarnings({"unchecked", "checkstyle:indentation", "checkstyle:methodlength"})
    // TODO: Remove the methodlength suppression once this method is cleaned up
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer, String ownerId,
        Collection<String> productIds, String subscriptionId, Date activeOn, PoolFilterBuilder filters,
        PageRequest pageRequest, boolean postFilter, boolean addFuture, boolean onlyFuture, Date after) {

        if (log.isDebugEnabled()) {
            log.debug("Listing available pools for:");
            log.debug("    consumer: {}", consumer);
            log.debug("    owner: {}", ownerId);
            log.debug("    products: {}", productIds);
            log.debug("    subscription: {}", subscriptionId);
            log.debug("    active on: {}", activeOn);
            log.debug("    after: {}", after);
        }

        boolean joinedProvided = false;

        Criteria criteria = this.createSecureCriteria("Pool")
            .createAlias("product", "Product")
            .setProjection(Projections.distinct(Projections.id()));

        if (consumer != null) {
            // Impl note: This block was inherited from the current implementation of the
            // CriteriaRules.availableEntitlementCriteria method.

            if (ownerId != null && !ownerId.equals(consumer.getOwnerId())) {
                // Both a consumer and an owner were specified, but the consumer belongs to a different owner.
                // We can't possibly match a pool on two owners, so we can just abort immediately with an
                // empty page
                log.warn("Attempting to filter entitlement pools by owner and a consumer belonging to a " +
                    "different owner: {}, {}", ownerId, consumer);

                Page<List<Pool>> output = new Page<>();
                output.setPageData(Collections.<Pool>emptyList());
                output.setMaxRecords(0);

                return output;
            }

            // We'll set the owner restriction later
            ownerId = consumer.getOwnerId();

            ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
            if (ctype.isManifest()) {
                DetachedCriteria hostPoolSubquery = DetachedCriteria.forClass(Pool.class, "PoolI")
                    .createAlias("PoolI.attributes", "attrib")
                    .setProjection(Projections.id())
                    .add(Property.forName("Pool.id").eqProperty("PoolI.id"))
                    .add(Restrictions.eq("attrib.indices", Pool.Attributes.REQUIRES_HOST));

                criteria.add(Subqueries.notExists(hostPoolSubquery));
            }
            else if (!consumer.isGuest()) {
                criteria.add(Restrictions.not(
                    this.addAttributeFilterSubquery(Pool.Attributes.VIRT_ONLY, Arrays.asList("true"))
                ));
            }
            else if (consumer.hasFact("virt.uuid")) {
                Consumer host = null;
                String uuidFact = consumer.getFact("virt.uuid");

                if (uuidFact != null) {
                    host = this.consumerCurator.getHost(uuidFact, ownerId);
                }

                // Impl note:
                // This query matches pools with the "requires_host" attribute explicitly set to a
                // value other than the host we're looking for. We then negate the results of this
                // subquery, so our final result is: fetch pools which do not have a required host
                // or have a required host equal to our host.

                // TODO: If we don't have a host, should this be filtering at all? Seems strange to
                // be filtering pools which have a null/empty required host value. Probably just
                // wasted cycles.
                DetachedCriteria hostPoolSubquery = DetachedCriteria.forClass(Pool.class, "PoolI")
                    .createAlias("PoolI.attributes", "attrib")
                    .setProjection(Projections.id())
                    .add(Property.forName("Pool.id").eqProperty("PoolI.id"))
                    .add(Restrictions.eq("attrib.indices", Pool.Attributes.REQUIRES_HOST))
                    .add(Restrictions.ne("attrib.elements", host != null ? host.getUuid() : "").ignoreCase());

                criteria.add(Subqueries.notExists(hostPoolSubquery));
            }
        }

        if (ownerId != null) {
            criteria.add(Restrictions.eq("Pool.owner.id", ownerId));
        }

        if (activeOn != null) {
            if (onlyFuture) {
                criteria.add(Restrictions.ge("Pool.startDate", activeOn));
            }
            else if (!addFuture) {
                criteria.add(Restrictions.le("Pool.startDate", activeOn));
                criteria.add(Restrictions.ge("Pool.endDate", activeOn));
            }
            else {
                criteria.add(Restrictions.ge("Pool.endDate", activeOn));
            }
        }

        if (after != null) {
            criteria.add(Restrictions.gt("Pool.startDate", after));
        }


        // TODO: This section is sloppy. If we're going to clobber the bits in the filter with our own input
        // parameters, why bother accepting a filter to begin with? Similarly, why bother accepting a filter
        // if the method takes the arguments directly? If we're going to abstract out the filtering bits, we
        // should go all-in, cut down on the massive argument list and simply take a single filter object. -C

        // Subscription ID filter
        String value = subscriptionId == null && filters != null ?
            filters.getSubscriptionIdFilter() :
            subscriptionId;

        if (value != null && !value.isEmpty()) {
            criteria.createAlias("Pool.sourceSubscription", "srcsub")
                .add(Restrictions.eq("srcsub.subscriptionId", value));
        }

        // Product ID filters
        Collection<String> values = productIds == null && filters != null ?
            filters.getProductIdFilter() :
            productIds;

        if (values != null && !values.isEmpty()) {
            if (!joinedProvided) {
                criteria.createAlias("Pool.product.providedProducts", "Provided", JoinType.LEFT_OUTER_JOIN);
                joinedProvided = true;
            }

            criteria.add(Restrictions.or(
                CPRestrictions.in("Product.id", values),
                CPRestrictions.in("Provided.id", values)
            ));
        }

        if (filters != null) {
            // Pool ID filters
            values = filters.getIdFilters();

            if (values != null && !values.isEmpty()) {
                criteria.add(CPRestrictions.in("Pool.id", values));
            }

            // Matches stuff
            values = filters.getMatchesFilters();
            if (values != null && !values.isEmpty()) {
                if (!joinedProvided) {
                    // This was an inner join -- might end up being important later
                    criteria.createAlias("Pool.product.providedProducts", "Provided",
                        JoinType.LEFT_OUTER_JOIN);
                    joinedProvided = true;
                }

                criteria.createAlias("Provided.productContent", "PPC", JoinType.LEFT_OUTER_JOIN);
                criteria.createAlias("PPC.content", "Content", JoinType.LEFT_OUTER_JOIN);

                for (String matches : values) {
                    String sanitized = this.sanitizeMatchesFilter(matches);

                    Disjunction matchesDisjunction = Restrictions.disjunction();

                    matchesDisjunction.add(CPRestrictions.ilike("Pool.contractNumber", sanitized, '!'))
                        .add(CPRestrictions.ilike("Pool.orderNumber", sanitized, '!'))
                        .add(CPRestrictions.ilike("Product.id", sanitized, '!'))
                        .add(CPRestrictions.ilike("Product.name", sanitized, '!'))
                        .add(CPRestrictions.ilike("Provided.id", sanitized, '!'))
                        .add(CPRestrictions.ilike("Provided.name", sanitized, '!'))
                        .add(CPRestrictions.ilike("Content.name", sanitized, '!'))
                        .add(CPRestrictions.ilike("Content.label", sanitized, '!'))
                        .add(this.addProductAttributeFilterSubquery(Product.Attributes.SUPPORT_LEVEL,
                            Arrays.asList(matches)));

                    criteria.add(matchesDisjunction);
                }
            }

            // Attribute filters
            for (Map.Entry<String, List<String>> entry : filters.getAttributeFilters().entrySet()) {
                String attrib = entry.getKey();
                values = entry.getValue();

                if (attrib != null && !attrib.isEmpty()) {
                    // TODO:
                    // Searching both pool and product attributes is likely an artifact from the days
                    // when we copied SKU product attributes to the pool. I don't believe there's any
                    // precedence for attribute lookups now that they're no longer being copied over.
                    // If this is not the case, then the following logic is broken and will need to be
                    // adjusted to account for one having priority over the other.

                    if (values != null && !values.isEmpty()) {
                        List<String> positives = new LinkedList<>();
                        List<String> negatives = new LinkedList<>();

                        for (String attrValue : values) {
                            if (attrValue.startsWith("!")) {
                                negatives.add(attrValue.substring(1));
                            }
                            else {
                                positives.add(attrValue);
                            }
                        }

                        if (!positives.isEmpty()) {
                            criteria.add(this.addAttributeFilterSubquery(attrib, positives));
                        }

                        if (!negatives.isEmpty()) {
                            criteria.add(Restrictions.not(
                                this.addAttributeFilterSubquery(attrib, negatives)));
                        }
                    }
                    else {
                        criteria.add(this.addAttributeFilterSubquery(attrib, values));
                    }
                }
            }
        }

        // Impl note:
        // Hibernate has an issue with properly hydrating objects within collections of the pool
        // when only a subset of the collection matches the criteria. To work around this, we pull
        // the ID list from the main filtering query, then pull the pools again using the ID list.
        // This also makes it easier to eventually start using a cursor, since the distinct entity
        // functionality doesn't work with cursors.

        List<String> poolIds = criteria.list();

        if (poolIds != null && !poolIds.isEmpty()) {
            criteria = this.currentSession()
                .createCriteria(Pool.class)
                .createAlias("product", "Product");
            criteria.add(CPRestrictions.in("id", poolIds));

            return this.listByCriteria(criteria, pageRequest, postFilter);
        }

        Page<List<Pool>> output = new Page<>();
        output.setPageData(Collections.<Pool>emptyList());
        output.setMaxRecords(0);

        return output;
    }

    @SuppressWarnings("checkstyle:indentation")
    private Criterion addAttributeFilterSubquery(String key, Collection<String> values) {
        // key = this.sanitizeMatchesFilter(key);

        // Find all pools which have the given attribute (and values) on a product, unless the pool
        // defines that same attribute
        DetachedCriteria poolAttrSubquery = DetachedCriteria.forClass(Pool.class, "PoolI")
            .createAlias("PoolI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Pool.id").eqProperty("PoolI.id"))
            .add(Restrictions.eq("attrib.indices", key));

        // Impl note:
        // The SQL restriction below uses some Hibernate magic value to get the pool ID from the
        // outer-most query. We can't use {alias} here, since we're basing the subquery on Product
        // instead of Pool to save ourselves an unnecessary join. Similarly, we use an SQL
        // restriction here because we can query the information we need, hitting only one table
        // with direct SQL, whereas the matching criteria query would end up requiring a minimum of
        // one join to get from pool to pool attributes.
        DetachedCriteria prodAttrSubquery = DetachedCriteria.forClass(Product.class, "ProdI")
            .createAlias("ProdI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Product.uuid").eqProperty("ProdI.uuid"))
            .add(Restrictions.eq("attrib.indices", key))
            .add(Restrictions.sqlRestriction(
                "NOT EXISTS (SELECT poolattr.pool_id FROM cp_pool_attribute poolattr " +
                "WHERE poolattr.pool_id = this_.id AND poolattr.name = ?)",
                key, StringType.INSTANCE
            ));

        if (values != null && !values.isEmpty()) {
            Disjunction poolAttrValueDisjunction = Restrictions.disjunction();
            Disjunction prodAttrValueDisjunction = Restrictions.disjunction();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    poolAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));

                    prodAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    poolAttrValueDisjunction.add(CPRestrictions.ilike("attrib.elements", attrValue, '!'));
                    prodAttrValueDisjunction.add(CPRestrictions.ilike("attrib.elements", attrValue, '!'));
                }
            }

            poolAttrSubquery.add(poolAttrValueDisjunction);
            prodAttrSubquery.add(prodAttrValueDisjunction);
        }

        return Restrictions.or(
            Subqueries.exists(poolAttrSubquery),
            Subqueries.exists(prodAttrSubquery)
        );
    }

    @SuppressWarnings("checkstyle:indentation")
    private Criterion addProductAttributeFilterSubquery(String key, Collection<String> values) {
        // Find all pools which have the given attribute (and values) on a product, unless the pool
        // defines that same attribute

        // Impl note:
        // The SQL restriction below uses some Hibernate magic value to get the pool ID from the
        // outer-most query. We can't use {alias} here, since we're basing the subquery on Product
        // instead of Pool to save ourselves an unnecessary join. Similarly, we use an SQL
        // restriction here because we can query the information we need, hitting only one table
        // with direct SQL, whereas the matching criteria query would end up requiring a minimum of
        // one join to get from pool to pool attributes.
        DetachedCriteria prodAttrSubquery = DetachedCriteria.forClass(Product.class, "ProdI")
            .createAlias("ProdI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Product.uuid").eqProperty("ProdI.uuid"))
            .add(Restrictions.eq("attrib.indices", key))
            .add(Restrictions.sqlRestriction(
                "NOT EXISTS (SELECT poolattr.pool_id FROM cp_pool_attribute poolattr " +
                "WHERE poolattr.pool_id = this_.id AND poolattr.name = ?)",
                key, StringType.INSTANCE
            ));

        if (values != null && !values.isEmpty()) {
            Disjunction prodAttrValueDisjunction = Restrictions.disjunction();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    prodAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    prodAttrValueDisjunction.add(
                        CPRestrictions.ilike("attrib.elements", attrValue, '!'));
                }
            }

            prodAttrSubquery.add(prodAttrValueDisjunction);
        }

        return Subqueries.exists(prodAttrSubquery);
    }

    private String sanitizeMatchesFilter(String matches) {
        StringBuilder output = new StringBuilder();
        boolean escaped = false;

        for (int index = 0; index < matches.length(); ++index) {
            char c = matches.charAt(index);

            switch (c) {
                case '!':
                case '_':
                case '%':
                    output.append('!').append(c);
                    break;

                case '\\':
                    if (escaped) {
                        output.append(c);
                    }

                    escaped = !escaped;
                    break;

                case '*':
                case '?':
                    if (!escaped) {
                        output.append(c == '*' ? '%' : '_');
                        break;
                    }

                default:
                    output.append(c);
                    escaped = false;
            }
        }

        return output.toString();
    }

    /**
     * Determine if owner has at least one active pool
     *
     * @param ownerId Owner whose subscriptions should be inspected.
     * @param date The date to test the active state.
     *        Set to null for current.
     * @return boolean is active on test date.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public boolean hasActiveEntitlementPools(String ownerId, Date date) {
        if (ownerId == null) {
            return false;
        }

        if (date == null) {
            date = new Date();
        }

        Criteria crit = createSecureCriteria();
        crit.add(Restrictions.eq("activeSubscription", Boolean.TRUE));
        crit.add(Restrictions.eq("owner.id", ownerId));
        crit.add(Restrictions.le("startDate", date));
        crit.add(Restrictions.ge("endDate", date));
        crit.setProjection(Projections.rowCount());

        long count = (Long) crit.uniqueResult();
        return count > 0;
    }

    @Transactional
    public List<Pool> listPoolsRestrictedToUser(String username) {
        return listByCriteria(
            currentSession().createCriteria(Pool.class)
                .add(Restrictions.eq("restrictedToUsername", username)));
    }

    @SuppressWarnings("unchecked")
    public List<Entitlement> retrieveOrderedEntitlementsOf(Collection<Pool> existingPools) {
        return criteriaToSelectEntitlementForPools(existingPools)
            .addOrder(Order.desc("created"))
            .list();
    }

    @SuppressWarnings("unchecked")
    public List<String> retrieveOrderedEntitlementIdsOf(Collection<Pool> pools) {
        return this.criteriaToSelectEntitlementForPools(pools)
            .addOrder(Order.desc("created"))
            .setProjection(Projections.id())
            .list();
    }

    public void deactivatePool(Pool pool) {
        if (log.isDebugEnabled()) {
            log.info("Subscription disappeared for pool: " + pool);
        }
        pool.setActiveSubscription(Boolean.FALSE);
        merge(pool);
    }

    private Criteria criteriaToSelectEntitlementForPool(Pool entitlementPool) {
        return this.currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("pool", entitlementPool));
    }

    private Criteria criteriaToSelectEntitlementForPools(Collection<Pool> entitlementPools) {
        return this.currentSession().createCriteria(Entitlement.class)
                .add(CPRestrictions.in("pool", entitlementPools));
    }

    /**
     * @param entitlementPool entitlement pool to search.
     * @return entitlements in the given pool.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Entitlement> entitlementsIn(Pool entitlementPool) {
        return criteriaToSelectEntitlementForPool(entitlementPool).list();
    }

    /**
     * Query pools by the subscription that generated them.
     *
     * @param owner The owner of the subscriptions to query
     * @param subId Subscription to look up pools by
     * @return pools from the given subscription, sorted by pool.id to avoid deadlocks
     */
    @SuppressWarnings("unchecked")
    public List<Pool> getBySubscriptionId(Owner owner, String subId) {
        return createSecureCriteria()
            .createAlias("sourceSubscription", "sourceSub")
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("sourceSub.subscriptionId", subId))
            .addOrder(Order.asc("id")).list();
    }

    /**
     * Query pools by the subscriptions that generated them.
     *
     * @param owner The owner of the subscriptions to query
     * @param subIds Subscriptions to look up pools by
     * @return pools from the given subscriptions, sorted by pool.id to avoid
     *         deadlocks
     */
    @SuppressWarnings("unchecked")
    public List<Pool> getBySubscriptionIds(Owner owner, Collection<String> subIds) {
        return this.getBySubscriptionIds(owner.getId(), subIds);
    }

    /**
     * Query pools by the subscriptions that generated them.
     *
     * @param ownerId The owner of the subscriptions to query
     * @param subIds Subscriptions to look up pools by
     * @return pools from the given subscriptions, sorted by pool.id to avoid
     *         deadlocks
     */
    @SuppressWarnings("unchecked")
    public List<Pool> getBySubscriptionIds(String ownerId, Collection<String> subIds) {
        return createSecureCriteria()
            .createAlias("sourceSubscription", "sourceSub")
            .add(Restrictions.eq("owner.id", ownerId))
            .add(CPRestrictions.in("sourceSub.subscriptionId", subIds))
            .addOrder(Order.asc("id"))
            .list();
    }

    /**
     * Attempts to find pools which are over subscribed after the creation or
     * modification of the given entitlement. To do this we search for only the
     * pools related to the subscription ID which could have changed, the two
     * cases where this can happen are:
     * 1. Bonus pool (not derived from any entitlement) after a bind. (in cases
     * such as exporting to downstream)
     * 2. A derived pool whose source entitlement just had it's quantity
     * reduced.
     * This has to be done carefully to avoid potential performance problems
     * with virt_bonus on-site subscriptions where one pool is created per
     * physical entitlement.
     *
     * @param ownerId Owner - The owner of the entitlements being passed in. Scoping
     *        this to a single owner prevents performance problems in large datasets.
     * @param subIdMap Map where key is Subscription ID of the pool, and value
     *        is the Entitlement just created or modified.
     * @return Pools with too many entitlements for their new quantity.
     */
    @SuppressWarnings("unchecked")
    public List<Pool> getOversubscribedBySubscriptionIds(String ownerId, Map<String, Entitlement> subIdMap) {
        List<Criterion> subIdMapCriteria = new ArrayList<>();
        Criterion[] exampleCriteria = new Criterion[0];
        for (Entry<String, Entitlement> entry : subIdMap.entrySet()) {
            SimpleExpression subscriptionExpr = Restrictions.eq("sourceSub.subscriptionId", entry.getKey());

            Junction subscriptionJunction = Restrictions.and(subscriptionExpr).add(
                Restrictions.or(Restrictions.isNull("sourceEntitlement"),
                Restrictions.eqOrIsNull("sourceEntitlement", entry.getValue())));

            subIdMapCriteria.add(subscriptionJunction);
        }

        return currentSession()
            .createCriteria(Pool.class)
            .createAlias("sourceSubscription", "sourceSub")
            .add(Restrictions.eq("owner.id", ownerId))
            .add(Restrictions.ge("quantity", 0L))
            .add(Restrictions.gtProperty("consumed", "quantity"))
            .add(Restrictions.or(subIdMapCriteria.toArray(exampleCriteria))).list();
    }

    @Transactional
    public Pool replicate(Pool pool) {
        pool.setSourceEntitlement(null);

        this.currentSession().replicate(pool, ReplicationMode.EXCEPTION);

        return pool;
    }

    private static final String CONSUMER_FILTER = "Entitlement_CONSUMER_FILTER";

    @SuppressWarnings("checkstyle:indentation")
    public int getNoOfDependentEntitlements(String entitlementId) {
        Filter consumerFilter = disableConsumerFilter();
        Integer result = (Integer) currentSession()
            .createCriteria(Entitlement.class)
            .setProjection(Projections.rowCount())
            .createCriteria("pool")
            .createCriteria("sourceEntitlement")
            .add(Restrictions.idEq(entitlementId))
            .list()
            .get(0);

        enableIfPrevEnabled(consumerFilter);
        return result;
    }

    // TODO: watch out for performance. Should we limit the certificates
    // retrieved?
    @SuppressWarnings("unchecked")
    public List<EntitlementCertificate> retrieveEntCertsOfPoolsWithSourceEntitlement(String entId) {
        return currentSession().createCriteria(EntitlementCertificate.class)
            .createCriteria("entitlement")
            .createCriteria("pool")
            .createCriteria("sourceEntitlement")
            .add(Restrictions.idEq(entId))
            .list();
    }

    /**
     * @param session
     * @param consumerFilter
     */
    private void enableIfPrevEnabled(Filter consumerFilter) {
        // if filter was previously enabled, restore it.
        if (consumerFilter != null) {
            FilterImpl filterImpl = (FilterImpl) consumerFilter;
            Filter filter = currentSession().enableFilter(CONSUMER_FILTER);
            filter.setParameter("consumer_id", filterImpl.getParameter("consumer_id"));
        }
    }

    public Filter disableConsumerFilter() {
        Filter consumerFilter = currentSession().getEnabledFilter(CONSUMER_FILTER);
        currentSession().disableFilter(CONSUMER_FILTER);
        return consumerFilter;
    }

    public List<ActivationKey> getActivationKeysForPool(Pool p) {
        List<ActivationKey> activationKeys = new ArrayList<>();
        List<ActivationKeyPool> activationKeyPools = currentSession().createCriteria(
            ActivationKeyPool.class).add(Restrictions.eq("pool", p)).list();

        for (ActivationKeyPool akp : activationKeyPools) {
            activationKeys.add(akp.getKey());
        }

        return activationKeys;
    }

    public Set<String> retrieveServiceLevelsForOwner(Owner owner, boolean exempt) {
        return retrieveServiceLevelsForOwner(owner.getId(), exempt);
    }

    /**
     * Method to compile service/support level lists. One is the available levels for
     *  consumers for this owner. The second is the level names that are exempt. Exempt
     *  means that a product pool with this level can be used with a consumer of any
     *  service level.
     *
     * @param ownerId The owner that has the list of available service levels for
     *              its consumers
     * @param exempt boolean to show if the desired list is the levels that are
     *               explicitly marked with the support_level_exempt attribute.
     * @return Set of levels based on exempt flag.
     */
    public Set<String> retrieveServiceLevelsForOwner(String ownerId, boolean exempt) {
        String stmt = "SELECT DISTINCT key(Attribute), value(Attribute), Product.id " +
            "FROM Pool AS Pool " +
            "  INNER JOIN Pool.product AS Product " +
            "  INNER JOIN Product.attributes AS Attribute " +
            "  LEFT JOIN Pool.entitlements AS Entitlement " +
            "WHERE Pool.owner.id = :owner_id " +
            "  AND (key(Attribute) = :sl_attr OR key(Attribute) = :sle_attr) " +
            "  AND (Pool.endDate >= current_date() OR Entitlement.endDateOverride >= current_date()) " +
            // Needs to be ordered, because the code below assumes exempt levels are first
            "ORDER BY key(Attribute) DESC";

        Query q = currentSession().createQuery(stmt)
            .setParameter("owner_id", ownerId)
            .setParameter("sl_attr", Product.Attributes.SUPPORT_LEVEL)
            .setParameter("sle_attr", Product.Attributes.SUPPORT_LEVEL_EXEMPT);

        List<Object[]> results = q.list();

        // Use case insensitive comparison here, since we treat
        // Premium the same as PREMIUM or premium, to make it easier for users to specify
        // a level on the CLI. However, use the original case, since Premium is more
        // attractive than PREMIUM.
        Set<String> slaSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> exemptSlaSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        Set<String> exemptProductIds = new HashSet<>();

        for (Object[] result : results) {
            String name = (String) result[0];
            String value = (String) result[1];
            String productId = (String) result[2];

            if (Product.Attributes.SUPPORT_LEVEL_EXEMPT.equals(name) && "true".equalsIgnoreCase(value)) {
                exemptProductIds.add(productId);
            }
            else if (Product.Attributes.SUPPORT_LEVEL.equalsIgnoreCase(name) &&
                (value != null && !value.trim().equals("")) &&
                exemptProductIds.contains(productId)) {

                exemptSlaSet.add(value);
            }
        }

        for (Object[] result : results) {
            String name = (String) result[0];
            String value = (String) result[1];

            if (!Product.Attributes.SUPPORT_LEVEL_EXEMPT.equals(name) && !exemptSlaSet.contains(value)) {
                slaSet.add(value);
            }
        }

        if (exempt) {
            return exemptSlaSet;
        }
        return slaSet;
    }

    private void deleteImpl(Pool entity) {
        if (entity != null) {
            // Before we delete the pool, we need to hydrate the attributes collection. Unlike the
            // entitlements collection below, we can't just clear the set, since we use the
            // attributes post-deletion. Also, if we were to ever create a new pool from the now-
            // deleted pool, having its attributes would be useful.

            // Impl note:
            // isPropertyInitialized does not work on attributes, for some reason. Also, Hibernate
            // lacks a proper/generic way to initialize properties without getting the proxy
            // directly (which violates proper encapsulation), so we use this not-so-clever
            // workaround to trigger collection hydration.

            // TODO: Maybe move this to the places where we actually use the attributes after
            // deletion? Not sure how much it'll actually save vs the time wasted by future devs
            // trying to figure out what the random NPE in Hibernate's internals means.
            entity.getAttributes().size();

            // Perform the actual deletion...
            this.currentSession().delete(entity);

            // Maintain runtime consistency. The entitlements for the pool have been deleted on the
            // database because delete is cascaded on Pool.entitlements relation

            // While it'd be nice to be able to skip this for every pool, we have no guarantee that
            // the pools came fresh from the DB with uninitialized entitlement collections. Since
            // it could be initialized, we should clear it so other bits using the pool don't
            // attempt to use the entitlements.
            if (Hibernate.isInitialized(entity.getEntitlements())) {
                entity.getEntitlements().clear();
            }
        }
    }

    /**
     * There is an issue with attributes in the database. Duplicate attribute
     *  name/value pairs were showing up from outside the Candlepin code.
     *  Only one was getting pulled into the HashSet and the other ignored.
     *  Uses explicit lookup and deletion instead of the Hibernate cascade which
     *  relies on the data structure in memory.
     *
     * @param entity pool to be deleted.
     */
    @Transactional
    public void delete(Pool entity) {
        Pool toDelete = this.get(entity.getId());

        if (toDelete != null) {
            this.deleteImpl(toDelete);
            this.flush();
        }
        else {
            log.debug("Pool {} not found; skipping deletion.", entity.getId());
        }
    }

    /**
     * Batch deletes a list of pools.
     *
     * @param pools pools to delete
     * @param alreadyDeletedPools pools to skip, they have already been deleted.
     */
    public void batchDelete(Collection<Pool> pools, Collection<String> alreadyDeletedPools) {
        if (alreadyDeletedPools == null) {
            alreadyDeletedPools = new HashSet<>();
        }

        for (Pool pool : pools) {
            // As we batch pool operations, pools may be deleted at multiple places in the code path.
            // We may request to delete the same pool in multiple places too, for example if an expired
            // stack derived pool has no entitlements, ExpiredPoolsJob will request to delete it twice,
            // for each reason.
            if (alreadyDeletedPools.contains(pool.getId())) {
                continue;
            }

            alreadyDeletedPools.add(pool.getId());
            this.deleteImpl(pool);
        }
    }

    /**
     * @param consumer
     * @param stackIds
     * @return Derived pools which exist for the given consumer and stack ids
     */
    @SuppressWarnings("checkstyle:indentation")
    public List<Pool> getSubPoolForStackIds(Consumer consumer, Collection stackIds) {
        Criteria getPools = createSecureCriteria()
            .createAlias("sourceStack", "ss")
            .add(Restrictions.eq("ss.sourceConsumer", consumer))
            .add(Restrictions.and(
                Restrictions.isNotNull("ss.sourceStackId"),
                CPRestrictions.in("ss.sourceStackId", stackIds))
            );

        return (List<Pool>) getPools.list();
    }

    @SuppressWarnings("unchecked")
    public List<Pool> getOwnerSubPoolsForStackId(Owner owner, String stackId) {
        return createSecureCriteria()
            .createAlias("sourceStack", "ss")
            .add(Restrictions.eq("ss.sourceStackId", stackId))
            .add(Restrictions.eq("owner", owner))
            .list();
    }

    /**
     * Lookup all pools for subscriptions which are not in the given list of subscription
     * IDs. Used for pool cleanup during refresh.
     *
     * @param owner
     * @param expectedSubIds Full list of all expected subscription IDs.
     * @return a list of pools for subscriptions not matching the specified subscription list
     */
    @SuppressWarnings("unchecked")
    public List<Pool> getPoolsFromBadSubs(Owner owner, Collection<String> expectedSubIds) {
        Criteria crit = currentSession().createCriteria(Pool.class)
            .add(Restrictions.eq("owner", owner));

        if (!expectedSubIds.isEmpty()) {
            crit.createAlias("sourceSubscription", "sourceSub");
            crit.add(Restrictions.and(
                Restrictions.not(Restrictions.in("sourceSub.subscriptionId", expectedSubIds)),
                Restrictions.isNotNull("sourceSub.subscriptionId")
            ));
        }

        crit.addOrder(Order.asc("id"));
        return crit.list();
    }

    /**
     * Fetches the IDs of the derived pools for the given pool IDs. If the provided pool IDs do not
     * have any derived pools, this method returns an empty collection.
     *
     * @param poolIds
     *  A collection of pool IDs for which to retrieve derived pool IDs
     *
     * @return
     *  A collection of pool IDs for pools derived from the given pool IDs
     */
    @SuppressWarnings("unchecked")
    public Set<String> getDerivedPoolIdsForPools(Iterable<String> poolIds) {
        Set<String> output = new HashSet<>();

        if (poolIds != null && poolIds.iterator().hasNext()) {
            // TODO: Update this method to use the pool hierarchy columns when they're available
            String sql = "SELECT DISTINCT ss2.pool_id " +
                "FROM cp2_pool_source_sub ss1 " +
                "JOIN cp2_pool_source_sub ss2 ON ss2.subscription_id = ss1.subscription_id " +
                "WHERE ss1.subscription_sub_key = 'master' " +
                "  AND ss2.subscription_sub_key != 'master' " +
                "  AND ss1.pool_id IN (:pool_ids)";

            javax.persistence.Query query = this.getEntityManager().createNativeQuery(sql);

            for (List<String> block : this.partition(poolIds)) {
                query.setParameter("pool_ids", block);
                output.addAll(query.getResultList());
            }
        }

        return output;
    }

    /**
     * Fetches the entitlement IDs for the pools specified by the given pool IDs. If there are no
     * entitlements linked to the given pool IDs, this method returns an empty collection.
     *
     * @param poolIds
     *  A collection of pool IDs for which to retrieve entitlement IDs
     *
     * @return
     *  A collection of entitlement IDs for the pools specified for the given pool IDs
     */
    @SuppressWarnings("unchecked")
    public Collection<String> getEntitlementIdsForPools(Collection<String> poolIds) {
        Set<String> eids = new HashSet<>();

        if (poolIds != null && !poolIds.isEmpty()) {
            for (List<String> block : this.partition(poolIds)) {
                eids.addAll(this.currentSession().createCriteria(Pool.class, "p")
                    .createAlias("p.entitlements", "e")
                    .add(Restrictions.in("p.id", block))
                    .setProjection(Projections.distinct(Projections.property("e.id")))
                    .list());
            }
        }

        return eids;
    }

    /**
     * Fetches the pool IDs for the pools derived from any of the entitlements specified by the
     * provided entitlement IDs. If there are no pools derived from the given entitlements, this
     * method returns an empty collection.
     *
     * @param entIds
     *  A collection of entitlement IDs for which to fetch derived pools
     *
     * @return
     *  A collection of pool IDs for pools derived from the given entitlement IDs
     */
    @SuppressWarnings("unchecked")
    public Collection<String> getPoolIdsForSourceEntitlements(Collection<String> entIds) {
        Set<String> pids = new HashSet<>();

        if (entIds != null && !entIds.isEmpty()) {
            for (List<String> block : this.partition(entIds)) {
                pids.addAll(this.currentSession().createCriteria(Pool.class, "p")
                    .createAlias("p.sourceEntitlement", "e")
                    .add(Restrictions.in("e.id", block))
                    .setProjection(Projections.distinct(Projections.property("p.id")))
                    .list());
            }
        }

        return pids;
    }

    /**
     * Fetches the IDs of the pools to which these entitlements provide access.
     *
     * @param entIds
     *  A collection of entitlement IDs for which to fetch pool IDs
     *
     * @return
     *  A collection of IDs of the pools for the given entitlement IDs
     */
    @SuppressWarnings("unchecked")
    public Collection<String> getPoolIdsForEntitlements(Collection<String> entIds) {
        Set<String> pids = new HashSet<>();

        if (entIds != null && !entIds.isEmpty()) {
            for (List<String> block : this.partition(entIds)) {
                pids.addAll(this.currentSession().createCriteria(Entitlement.class, "e")
                    .createAlias("e.pool", "p")
                    .add(Restrictions.in("e.id", block))
                    .setProjection(Projections.distinct(Projections.property("p.id")))
                    .list());
            }
        }

        return pids;
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Pool> getPoolsBySubscriptionId(String subId) {
        String jpql = "SELECT DISTINCT ss.pool.id FROM SourceSubscription ss WHERE ss.subscriptionId = :sid";

        List<String> ids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("sid", subId)
            .getResultList();

        if (ids != null && !ids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Pool.class, null)
                .add(CPRestrictions.in("id", ids))
                .addOrder(Order.asc("id"));

            return this.cpQueryFactory.<Pool>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Pool>buildQuery();
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Pool> getPoolsBySubscriptionIds(Collection<String> subIds) {
        if (subIds != null && !subIds.isEmpty()) {
            Session session = this.currentSession();

            List<String> ids = session.createCriteria(SourceSubscription.class)
                .add(CPRestrictions.in("subscriptionId", subIds))
                .setProjection(Projections.distinct(Projections.property("pool.id")))
                .list();

            if (ids != null && !ids.isEmpty()) {
                DetachedCriteria criteria = this.createSecureDetachedCriteria(Pool.class, null)
                    .add(CPRestrictions.in("id", ids))
                    .addOrder(Order.asc("id"));

                return this.cpQueryFactory.<Pool>buildQuery(session, criteria);
            }
        }

        return this.cpQueryFactory.<Pool>buildQuery();
    }

    @SuppressWarnings("unchecked")
    public List<Pool> getOwnersFloatingPools(Owner owner) {
        return currentSession().createCriteria(Pool.class)
            .add(Restrictions.eq("owner", owner))
            .createAlias("sourceSubscription", "sourceSub", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.isNull("sourceSub.subscriptionId"))
            .addOrder(Order.asc("id"))
            .list();
    }

    /**
     * Retrieves all known master pools (subscriptions) for all owners.
     *
     * @return
     *  A query to fetch all known master pools
     */
    public CandlepinQuery<Pool> getMasterPools() {
        DetachedCriteria criteria = DetachedCriteria.forClass(Pool.class)
            .createAlias("sourceSubscription", "srcsub")
            .add(Restrictions.eq("srcsub.subscriptionSubKey", "master"));

        return this.cpQueryFactory.<Pool>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Retrieves all known master pools (subscriptions) for the given owner.
     *
     * @param owner
     *  The owner for which to fetch master pools
     *
     * @return
     *  A query to fetch all known master pools for the given owner
     */
    public CandlepinQuery<Pool> getMasterPoolsForOwner(Owner owner) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Pool.class)
            .createAlias("sourceSubscription", "srcsub")
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("srcsub.subscriptionSubKey", "master"));

        return this.cpQueryFactory.<Pool>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Retrieves all known master pools (subscriptions) for the given owner that have subscription
     * IDs not present in the provided collection.
     *
     * @param owner
     *  The owner for which to fetch master pools
     *
     * @param excludedSubIds
     *  A collection of
     *
     * @return
     *  A query to fetch all known master pools for the given owner
     */
    public CandlepinQuery<Pool> getMasterPoolsForOwnerExcludingSubs(Owner owner,
        Collection<String> excludedSubIds) {

        if (excludedSubIds != null && !excludedSubIds.isEmpty()) {
            DetachedCriteria criteria = DetachedCriteria.forClass(Pool.class)
                .createAlias("sourceSubscription", "srcsub")
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.eq("srcsub.subscriptionSubKey", "master"))
                .add(Restrictions.not(CPRestrictions.in("srcsub.subscriptionId", excludedSubIds)));

            return this.cpQueryFactory.<Pool>buildQuery(this.currentSession(), criteria);
        }
        else {
            return this.getMasterPoolsForOwner(owner);
        }
    }

    /**
     * Retrieves the set of all known product IDs, as determined by looking only at pool data. If
     * there are no known products, this method returns an empty set.
     *
     * @return
     *  a set of all known product IDs.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getAllKnownProductIds() {
        // Impl note:
        // HQL does not (properly) support unions, so we have to do this query multiple times.
        Set<String> result = new HashSet<>();

        Query query = this.currentSession().createQuery(
            "SELECT DISTINCT P.product.id " +
            "FROM Pool P " +
            "WHERE NULLIF(P.product.id, '') IS NOT NULL"
        );
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT P.derivedProduct.id " +
            "FROM Pool P " +
            "WHERE NULLIF(P.derivedProduct.id, '') IS NOT NULL"
        );
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT PP.id " +
            "FROM Pool P INNER JOIN P.product.providedProducts AS PP " +
            "WHERE NULLIF(PP.id, '') IS NOT NULL"
        );
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT DPP.id " +
            "FROM Pool P INNER JOIN P.derivedProduct.providedProducts AS DPP " +
            "WHERE NULLIF(DPP.id, '') IS NOT NULL"
        );
        result.addAll(query.list());

        // Return!
        return result;
    }

    /**
     * Retrieves the set of all known product IDs for the specified owner, as determined by looking
     * only at pool data. If there are no known products for the given owner, this method returns an
     * empty set.
     *
     * @param owner
     *  The owner for which to retrieve all known product IDs
     *
     * @return
     *  a set of all known product IDs for the specified owner
     */
    @SuppressWarnings("unchecked")
    public Set<String> getAllKnownProductIdsForOwner(Owner owner) {
        // Impl note:
        // HQL does not (properly) support unions, so we have to do this query multiple times.
        Set<String> result = new HashSet<>();

        Query query = this.currentSession().createQuery(
            "SELECT DISTINCT P.product.id " +
            "FROM Pool P " +
            "WHERE NULLIF(P.product.id, '') IS NOT NULL " +
            "AND P.owner = :owner"
        );
        query.setParameter("owner", owner);
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT P.derivedProduct.id " +
            "FROM Pool P " +
            "WHERE NULLIF(P.derivedProduct.id, '') IS NOT NULL " +
            "AND P.owner = :owner"
        );
        query.setParameter("owner", owner);
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT PP.id " +
            "FROM Pool P INNER JOIN P.product.providedProducts AS PP " +
            "WHERE NULLIF(PP.id, '') IS NOT NULL " +
            "AND P.owner = :owner"
        );
        query.setParameter("owner", owner);
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT DPP.id " +
            "FROM Pool P INNER JOIN P.derivedProduct.providedProducts AS DPP " +
            "WHERE NULLIF(DPP.id, '') IS NOT NULL " +
            "AND P.owner = :owner"
        );
        query.setParameter("owner", owner);
        result.addAll(query.list());

        // Return!
        return result;
    }

    @SuppressWarnings("checkstyle:indentation")
    public Pool findDevPool(Consumer consumer) {
        PoolFilterBuilder filters = new PoolFilterBuilder();
        filters.addAttributeFilter(Pool.Attributes.DEVELOPMENT_POOL, "true");
        filters.addAttributeFilter(Pool.Attributes.REQUIRES_CONSUMER, consumer.getUuid());

        Criteria criteria = this.createSecureCriteria("Pool")
            .createAlias("product", "Product")
            .setProjection(Projections.distinct(Projections.id()));

        criteria.add(Restrictions.eq("owner.id", consumer.getOwnerId()))
            .add(this.addAttributeFilterSubquery(
                Pool.Attributes.DEVELOPMENT_POOL, Arrays.asList("true")))
            .add(this.addAttributeFilterSubquery(
                Pool.Attributes.REQUIRES_CONSUMER, Arrays.asList(consumer.getUuid())));

        // Impl note:
        // Hibernate has an issue with properly hydrating objects within collections of the pool
        // when only a subset of the collection matches the criteria. To work around this, we pull
        // the ID list from the main filtering query, then pull the pools again using the ID list.
        // This also makes it easier to eventually start using a cursor, since the distinct entity
        // functionality doesn't work with cursors.

        List<String> poolIds = criteria.list();

        if (poolIds != null && !poolIds.isEmpty()) {
            if (poolIds.size() > 1) {
                // This is probably (see: definitely) bad.
                throw new IllegalStateException("More than one dev pool found for consumer: " + consumer);
            }

            criteria = this.currentSession()
                .createCriteria(Pool.class)
                .add(Restrictions.eq("id", poolIds.get(0)));

            return (Pool) criteria.uniqueResult();
        }

        return null;
    }

    /**
    * Uses a database query to check if the pool is still
    * in the database.
    * @param pool pool to be searched in the database
    * @return true if and only if the pool is still in the database
    */
    public boolean exists(Pool pool) {
        return getEntityManager()
            .createQuery("SELECT COUNT(p) FROM Pool p WHERE p=:pool", Long.class)
            .setParameter("pool", pool).getSingleResult() > 0;
    }

    public void calculateConsumedForOwnersPools(Owner owner) {
        String stmt = "update Pool p set p.consumed = coalesce(" +
            "(select sum(quantity) from Entitlement ent where ent.pool.id = p.id),0) " +
            "where p.owner = :owner";

        Query q = currentSession().createQuery(stmt);
        q.setParameter("owner", owner);
        q.executeUpdate();
    }

    public void calculateExportedForOwnersPools(Owner owner) {
        String stmt = "update Pool p set p.exported = coalesce(" +
            "(select sum(ent.quantity) FROM Entitlement ent, Consumer cons, ConsumerType ctype " +
            "where ent.pool.id = p.id and ent.consumer.id = cons.id and cons.typeId = ctype.id " +
            "and ctype.manifest = 'Y'),0) where p.owner = :owner";

        Query q = currentSession().createQuery(stmt);
        q.setParameter("owner", owner);
        q.executeUpdate();
    }

    public void markCertificatesDirtyForPoolsWithProducts(Owner owner, Collection<String> productIds) {
        for (List<String> batch : Iterables.partition(productIds, getInBlockSize())) {
            markCertificatesDirtyForPoolsWithNormalProducts(owner, batch);
            markCertificatesDirtyForPoolsWithProvidedProducts(owner, batch);
        }
    }

    private void markCertificatesDirtyForPoolsWithNormalProducts(Owner owner, Collection<String> productIds) {
        String statement = "update Entitlement e set e.dirty=true where e.pool.id in " +
            "(select p.id from Pool p where p.owner=:owner and p.product.id in :productIds)";
        Query query = currentSession().createQuery(statement);
        query.setParameter("owner", owner);
        query.setParameterList("productIds", productIds);
        query.executeUpdate();
    }

    private void markCertificatesDirtyForPoolsWithProvidedProducts(Owner owner,
        Collection<String> productIds) {

        String statement = "update Entitlement e set e.dirty=true where e.pool.id in " +
            "(select p.id from Pool p join p.product.providedProducts pp where pp.id in :productIds)" +
            "and e.owner = :owner";
        Query query = currentSession().createQuery(statement);
        query.setParameter("owner", owner);
        query.setParameterList("productIds", productIds);
        query.executeUpdate();
    }

    /**
     * Check if this pool provides the given product
     *
     * Figures out if the pool with poolId provides a product providedProductId.
     * 'provides' means that the product is either Pool product or is linked through
     * cp2_provided_products table
     *
     * @param pool
     * @param providedProductId
     * @return True if and only if providedProductId is provided product or pool product
     */
    public Boolean provides(Pool pool, String providedProductId) {
        TypedQuery<Long> query = getEntityManager().createQuery(
            "SELECT count(product.uuid) FROM Pool p " +
            "LEFT JOIN p.product.providedProducts pproduct " +
            "LEFT JOIN p.product product " +
            "WHERE p.id = :poolid and (pproduct.id = :providedProductId OR product.id = :providedProductId)",
            Long.class);
        query.setParameter("poolid", pool.getId());
        query.setParameter("providedProductId", providedProductId);
        return query.getSingleResult() > 0;
    }

    /**
     * Check if this pool provides the given product ID as a derived provided product.
     * Used when we're looking for pools we could give to a host that will create
     * sub-pools for guest products.
     *
     * If derived product ID is not set, we just use the normal set of products.
     *
     * @param pool
     * @param derivedProvidedProductId
     * @return True if and only if derivedProvidedProductId is provided product or derived product
     */
    public Boolean providesDerived(Pool pool, String derivedProvidedProductId) {
        if (pool.getDerivedProduct() != null) {
            TypedQuery<Long> query = getEntityManager().createQuery(
                "SELECT count(product.uuid) FROM Pool p " +
                "LEFT JOIN p.derivedProduct.providedProducts pproduct " +
                "LEFT JOIN p.derivedProduct product " + "WHERE p.id = :poolid and " +
                "(pproduct.id = :providedProductId OR product.id = :providedProductId)",
                Long.class);
            query.setParameter("poolid", pool.getId());
            query.setParameter("providedProductId", derivedProvidedProductId);
            return query.getSingleResult() > 0;
        }
        else {
            return provides(pool, derivedProvidedProductId);
        }
    }

    /**
     * Fetches a mapping of pool IDs to sets of product IDs representing the provided products of
     * the given pool. The returned map will only contain mappings for pools specified in the given
     * collection of pool IDs.
     *
     * @param pools
     *  A collection of pools for which to fetch provided product IDs
     *
     * @return
     *  A mapping of pool IDs to provided product IDs
     */
    public Map<String, Set<String>> getProvidedProductIds(Collection<Pool> pools) {
        Set<String> poolIds = new HashSet<>();

        if (pools != null && !pools.isEmpty()) {
            for (Pool pool : pools) {
                if (pool != null && pool.getId() != null) {
                    poolIds.add(pool.getId());
                }
            }
        }

        return this.getProvidedProductIdsByPoolIds(poolIds);
    }

    /**
     * Fetches a mapping of pool IDs to sets of product IDs representing the provided products of
     * the given pool. The returned map will only contain mappings for pools specified in the given
     * collection of pool IDs.
     *
     * @param poolIds
     *  A collection of pool IDs for which to fetch provided product IDs
     *
     * @return
     *  A mapping of pool IDs to provided product IDs
     */
    public Map<String, Set<String>> getProvidedProductIdsByPoolIds(Collection<String> poolIds) {
        Map<String, Set<String>> providedProductMap = new HashMap<>();

        if (poolIds != null && !poolIds.isEmpty()) {
            StringBuilder builder =
                new StringBuilder("SELECT p.id, pp.id FROM Pool p JOIN p.providedProducts pp WHERE");
            javax.persistence.Query query = null;

            int blockSize = getInBlockSize();
            int blockCount = (int) Math.ceil(poolIds.size() / (float) blockSize);

            if (blockCount > 1) {
                Iterable<List<String>> blocks = Iterables.partition(poolIds, blockSize);

                for (int i = 0; i < blockCount; ++i) {
                    if (i != 0) {
                        builder.append(" OR");
                    }

                    builder.append(" p.id IN (:block").append(i).append(')');
                }

                query = this.getEntityManager().createQuery(builder.toString());
                int i = -1;

                for (List<String> block : blocks) {
                    query.setParameter("block" + ++i, block);
                }
            }
            else {
                builder.append(" p.id IN (:pids)");

                query = this.getEntityManager().createQuery(builder.toString())
                    .setParameter("pids", poolIds);
            }

            for (Object[] cols : (List<Object[]>) query.getResultList()) {
                Set<String> providedProducts = providedProductMap.get((String) cols[0]);

                if (providedProducts == null) {
                    providedProducts = new HashSet<>();
                    providedProductMap.put((String) cols[0], providedProducts);
                }

                providedProducts.add((String) cols[1]);
            }
        }

        return providedProductMap;
    }


    /**
     * Fetches a mapping of pool IDs to sets of product IDs representing the provided products of
     * the given pool. The returned map will only contain mappings for pools specified in the given
     * collection of pool IDs.
     *
     * @param pools
     *  A collection of pools for which to fetch provided product IDs
     *
     * @return
     *  A mapping of pool IDs to provided product IDs
     */
    public Map<String, Set<String>> getDerivedProvidedProductIds(Collection<Pool> pools) {
        Set<String> poolIds = new HashSet<>();

        if (pools != null && !pools.isEmpty()) {
            for (Pool pool : pools) {
                if (pool != null && pool.getId() != null) {
                    poolIds.add(pool.getId());
                }
            }
        }

        return this.getDerivedProvidedProductIdsByPoolIds(poolIds);
    }

    /**
     * Fetches a mapping of pool IDs to sets of product IDs representing the provided products of
     * the given pool. The returned map will only contain mappings for pools specified in the given
     * collection of pool IDs.
     *
     * @param poolIds
     *  A collection of pool IDs for which to fetch provided product IDs
     *
     * @return
     *  A mapping of pool IDs to provided product IDs
     */
    public Map<String, Set<String>> getDerivedProvidedProductIdsByPoolIds(Collection<String> poolIds) {
        Map<String, Set<String>> providedProductMap = new HashMap<>();

        if (poolIds != null && !poolIds.isEmpty()) {
            StringBuilder builder =
                new StringBuilder("SELECT p.id, dpp.id FROM Pool p JOIN p.derivedProvidedProducts dpp WHERE");

            javax.persistence.Query query = null;

            int blockSize = getInBlockSize();
            int blockCount = (int) Math.ceil(poolIds.size() / (float) blockSize);

            if (blockCount > 1) {
                Iterable<List<String>> blocks = Iterables.partition(poolIds, blockSize);

                for (int i = 0; i < blockCount; ++i) {
                    if (i != 0) {
                        builder.append(" OR");
                    }

                    builder.append(" p.id IN (:block").append(i).append(')');
                }

                query = this.getEntityManager().createQuery(builder.toString());
                int i = -1;

                for (List<String> block : blocks) {
                    query.setParameter("block" + ++i, block);
                }
            }
            else {
                builder.append(" p.id IN (:pids)");

                query = this.getEntityManager().createQuery(builder.toString())
                    .setParameter("pids", poolIds);
            }

            for (Object[] cols : (List<Object[]>) query.getResultList()) {
                Set<String> providedProducts = providedProductMap.get((String) cols[0]);

                if (providedProducts == null) {
                    providedProducts = new HashSet<>();
                    providedProductMap.put((String) cols[0], providedProducts);
                }

                providedProducts.add((String) cols[1]);
            }
        }

        return providedProductMap;
    }

    @Transactional
    public void removeCdn(Cdn cdn) {
        if (cdn == null) {
            throw new IllegalArgumentException("Attempt to remove pool's cdn with null cdn value.");
        }

        String hql = "UPDATE Pool p SET p.cdn = null WHERE p.cdn = :cdn";

        int updated = this.currentSession()
            .createQuery(hql)
            .setParameter("cdn", cdn)
            .executeUpdate();

        log.debug("CDN removed from {} pools: {}", updated, cdn);
    }

    /**
     * Removes source entitlements for the pools represented by the given collection of pool IDs.
     * Note that this operation does not update any fetched or cached Pool objects, and will be
     * reverted should a pool's state be persisted after this method has returned.
     *
     * @param poolIds
     *  A collection of pool IDs for which to clear source entitlement references
     */
    public void clearPoolSourceEntitlementRefs(Iterable<String> poolIds) {
        if (poolIds != null && poolIds.iterator().hasNext()) {
            String hql = "UPDATE Pool SET sourceEntitlement = null WHERE id IN (:pids)";
            Query query = this.currentSession().createQuery(hql);

            for (List<String> block : this.partition(poolIds)) {
                query.setParameterList("pids", block);
                query.executeUpdate();
            }
        }
    }

    /**
     * Fetches a set of pool IDs which represent the set of provided pool IDs that currently exist
     * in the database
     *
     * @param poolIds
     *  A collection of pool IDs to use to fetch existing pool IDs
     *
     * @return
     *  a set of pool IDs representing existing pools in the given collection of pool IDs
     */
    public Set<String> getExistingPoolIdsByIds(Iterable<String> poolIds) {
        Set<String> existing = new HashSet<>();

        if (poolIds != null && poolIds.iterator().hasNext()) {
            String jpql = "SELECT DISTINCT p.id FROM Pool p WHERE p.id IN (:pids)";
            TypedQuery<String> query = this.getEntityManager()
                .createQuery(jpql, String.class);

            for (List<String> block : this.partition(poolIds)) {
                query.setParameter("pids", block);
                existing.addAll(query.getResultList());
            }
        }

        return existing;
    }

    /**
     * Fetches a map of consumer IDs to pool IDs of stack derived pools for the given stack IDs. If
     * no such pools can be found, an empty map is returned.
     *
     * @param stackIds
     *  A collection of stack IDs to use to fetch consumer pool IDs
     *
     * @return
     *  a map of consumer IDs to pool IDs of stack derived pools fro the given stack IDs
     */
    public Map<String, Set<String>> getConsumerStackDerivedPoolIdMap(Iterable<String> stackIds) {
        Map<String, Set<String>> consumerPoolMap = new HashMap<>();

        if (stackIds != null && stackIds.iterator().hasNext()) {
            // We do this in native SQL to avoid some unnecessary joins
            String jpql = "SELECT DISTINCT ss.sourceConsumer.id, ss.derivedPool.id FROM SourceStack ss " +
                "WHERE ss.sourceStackId IN (:stackids)";

            TypedQuery<Object[]> query = this.getEntityManager().createQuery(jpql, Object[].class);

            for (List<String> block : this.partition(stackIds)) {
                query.setParameter("stackids", block);

                for (Object[] row : query.getResultList()) {
                    String consumerId = (String) row[0];
                    String poolId = (String) row[1];

                    Set<String> poolIds = consumerPoolMap.get(consumerId);
                    if (poolIds == null) {
                        poolIds = new HashSet<>();
                        consumerPoolMap.put(consumerId, poolIds);
                    }

                    poolIds.add(poolId);
                }
            }
        }

        return consumerPoolMap;
    }

    /**
     * Fetches a list of pool IDs for stack derived pools that will be unentitled with the deletion
     * of the specified entitlement IDs.
     * <p></p>
     * <strong>WARNING</strong>: This method can miss stack derived pools in cases where the number
     * of provided entitlement IDs exceeds the size limitations for the SQL IN operator and
     * entitlements for a given stack end up in multiple blocks.
     * <p></p>
     * To work around this issue, the entitlement list needs to be manually partitioned into blocks
     * either by consumer or stack ID, such that the partitions are smaller than the IN operator
     * limit returned by getInBlockSize().
     *
     * @param entitlementIds
     *  A collection of entitlement IDs for entitlements that are being deleted
     *
     * @return
     *  a collection of pool IDs representing unentitled stack derived pools with the deletion of
     *  the specified entitlements
     */
    public Set<String> getUnentitledStackDerivedPoolIds(Iterable<String> entitlementIds) {
        Set<String> output = new HashSet<>();

        String sql = "SELECT ss.derivedpool_id " +
            "FROM cp_pool_source_stack ss " +
            "LEFT JOIN (SELECT e.consumer_id, ppa.value AS stack_id, e.id AS entitlement_id " +
            "    FROM cp_entitlement e " +
            "    JOIN cp_pool p ON p.id = e.pool_id " +
            "    JOIN cp2_product_attributes ppa ON ppa.product_uuid = p.product_uuid " +
            "    LEFT JOIN cp_pool_source_stack ss ON ss.derivedpool_id = p.id " +
            "    WHERE ss.id IS NULL " +
            "        AND p.sourceentitlement_id IS NULL " +
            "        AND ppa.name = :stackid_attrib_name ";

        if (entitlementIds != null && entitlementIds.iterator().hasNext()) {
            // Impl note:
            // We do this in raw SQL as HQL/JPQL does not allow joining on a query/temp table, and
            // it allows us to skip a few joins to get directly to the data we need.
            sql += " AND e.id NOT IN (:eids) " +
                ") ec ON ec.consumer_id = ss.sourceconsumer_id AND ec.stack_id = ss.sourcestackid " +
                "WHERE ec.entitlement_id IS NULL";

            javax.persistence.Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("stackid_attrib_name", Product.Attributes.STACKING_ID);

            int blockSize = Math.min(this.getQueryParameterLimit() - 1, this.getInBlockSize());
            for (List<String> block : Iterables.partition(entitlementIds, blockSize)) {
                query.setParameter("eids", block);
                output.addAll(query.getResultList());
            }
        }
        else {
            sql += ") ec ON ec.consumer_id = ss.sourceconsumer_id AND ec.stack_id = ss.sourcestackid " +
                "WHERE ec.entitlement_id IS NULL";

            javax.persistence.Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("stackid_attrib_name", Product.Attributes.STACKING_ID);

            output.addAll(query.getResultList());
        }

        return output;
    }

    /**
     * Fetches a list of pool IDs for pools that are referencing products which are no longer linked
     * to the pool's owning organization. If no such pools exist, this method returns an empty list.
     *
     * @param ownerId
     *  the ID of the organization owning the pools to check.
     *
     * @return
     *  a list of pools using products which are not owned by the owning organization.
     */
    public List<String> getPoolsUsingOrphanedProducts(String ownerId) {
        if (ownerId == null || ownerId.isEmpty()) {
            return new ArrayList<>();
        }

        // We use native SQL here since we do a multi-field join
        String sql = "SELECT p.id FROM cp_pool p " +
            "LEFT JOIN cp2_owner_products op1 ON op1.owner_id = p.owner_id " +
            "  AND p.product_uuid = op1.product_uuid " +
            "LEFT JOIN cp2_owner_products op2 ON op2.owner_id = p.owner_id " +
            "  AND p.derived_product_uuid = op2.product_uuid " +
            "WHERE p.owner_id = :owner_id AND " +
            "  ((p.product_uuid IS NOT NULL AND op1.product_uuid IS NULL) OR " +
            "  (p.derived_product_uuid IS NOT NULL AND op2.product_uuid IS NULL))";

        return this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("owner_id", ownerId)
            .getResultList();
    }

}
