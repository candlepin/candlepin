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
import org.hibernate.LockOptions;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
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
import org.hibernate.sql.JoinType;
import org.hibernate.type.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * EntitlementPoolCurator
 */
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    /** The recommended number of expired pools to fetch in a single call to listExpiredPools */
    public static final int EXPIRED_POOL_BLOCK_SIZE = 2048;

    private static Logger log = LoggerFactory.getLogger(PoolCurator.class);
    private ConsumerCurator consumerCurator;
    @Inject
    protected Injector injector;

    @Inject
    public PoolCurator(ConsumerCurator consumerCurator) {
        super(Pool.class);
        this.consumerCurator = consumerCurator;
    }

    @Override
    @Transactional
    public Pool find(Serializable id) {
        Pool pool = super.find(id);
        return pool;
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

    @Transactional
    public CandlepinQuery<Pool> listByOwnerAndType(Owner owner, PoolType type) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Pool.class)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("type", type));

        return this.cpQueryFactory.<Pool>buildQuery(this.currentSession(), criteria);
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
    public List<Pool> listBySourceEntitlements(List<Entitlement> ents) {
        if (ents.size() == 0) {
            return new ArrayList<Pool>();
        }

        List<Pool> results = createSecureCriteria()
            .add(CPRestrictions.in("sourceEntitlement", ents))
            .setFetchMode("entitlements", FetchMode.JOIN)
            .list();

        if (results == null) {
            results = new LinkedList<Pool>();
        }

        if (results.size() > 0) {
            List<Pool> pools = listBySourceEntitlements(convertPoolsToEntitlements(results));
            results.addAll(pools);
        }

        return results;
    }

    private List<Entitlement> convertPoolsToEntitlements(List<Pool> pools) {
        List<Entitlement> result = new ArrayList<Entitlement>();

        for (Pool p : pools) {
            result.addAll(p.getEntitlements());
        }

        return result;
    }

    /**
     * Returns list of pools available to the consumer.
     *
     * @param c Consumer to filter
     * @return pools available to the consumer.
     */
    @Transactional
    public List<Pool> listByConsumer(Consumer c) {
        return listAvailableEntitlementPools(c, c.getOwner(), (Set<String>) null, null);
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
        return results != null ? results : new LinkedList<Pool>();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o, String productId, Date activeOn) {
        return listAvailableEntitlementPools(c, o,
            (productId != null ? Arrays.asList(productId) : (Collection<String>) null), null, activeOn,
            new PoolFilterBuilder(), null, false, false, false).getPageData();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o, Collection<String> productIds,
        Date activeOn) {

        return listAvailableEntitlementPools(c, o, productIds, null, activeOn,
            new PoolFilterBuilder(), null, false, false, false).getPageData();
    }

    @Transactional
    public List<Pool> listByFilter(PoolFilterBuilder filters) {
        return listAvailableEntitlementPools(
            null, null, (Set<String>) null, null, null, filters, null, false,
            false, false).getPageData();
    }

    @Transactional
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer c, Owner o, String productId,
        String subscriptionId, Date activeOn, PoolFilterBuilder filters,
        PageRequest pageRequest, boolean postFilter, boolean addFuture, boolean onlyFuture) {

        return this.listAvailableEntitlementPools(c, o,
            (productId != null ? Arrays.asList(productId) : (Collection<String>) null), subscriptionId,
            activeOn, filters, pageRequest, postFilter, addFuture, onlyFuture);
    }

    /**
     * List entitlement pools.
     *
     * Pools will be refreshed from the underlying subscription service.
     *
     * @param consumer Consumer being entitled.
     * @param owner Owner whose subscriptions should be inspected.
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
    public Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer, Owner owner,
        Collection<String> productIds, String subscriptionId, Date activeOn, PoolFilterBuilder filters,
        PageRequest pageRequest, boolean postFilter, boolean addFuture, boolean onlyFuture) {

        if (log.isDebugEnabled()) {
            log.debug("Listing available pools for:");
            log.debug("    consumer: {}", consumer);
            log.debug("    owner: {}", owner);
            log.debug("    products: {}", productIds);
            log.debug("    subscription: {}", subscriptionId);
        }

        boolean joinedProvided = false;

        Criteria criteria = this.createSecureCriteria("Pool")
            .createAlias("product", "Product")
            .setProjection(Projections.distinct(Projections.id()));

        if (consumer != null) {
            // Impl note: This block was inherited from the current implementation of the
            // CriteriaRules.availableEntitlementCriteria method.

            if (owner != null && !owner.equals(consumer.getOwner())) {
                // Both a consumer and an owner were specified, but the consumer belongs to a different owner.
                // We can't possibly match a pool on two owners, so we can just abort immediately with an
                // empty page
                log.warn("Attempting to filter entitlement pools by owner and a consumer belonging to a " +
                    "different owner: {}, {}", owner, consumer);

                Page<List<Pool>> output = new Page<List<Pool>>();
                output.setPageData(Collections.<Pool>emptyList());
                output.setMaxRecords(0);

                return output;
            }

            // We'll set the owner restriction later
            owner = consumer.getOwner();

            if (consumer.getType().isManifest()) {
                DetachedCriteria hostPoolSubquery = DetachedCriteria.forClass(Pool.class, "PoolI")
                    .createAlias("PoolI.attributes", "attrib")
                    .setProjection(Projections.id())
                    .add(Property.forName("Pool.id").eqProperty("PoolI.id"))
                    .add(Restrictions.eq("attrib.indices", Pool.Attributes.REQUIRES_HOST));

                criteria.add(Subqueries.notExists(hostPoolSubquery));
            }
            else if (!"true".equalsIgnoreCase(consumer.getFact("virt.is_guest"))) {
                criteria.add(Restrictions.not(
                    this.addAttributeFilterSubquery(Pool.Attributes.VIRT_ONLY, Arrays.asList("true"))
                ));
            }
            else if (consumer.hasFact("virt.uuid")) {
                Consumer host = null;
                String uuidFact = consumer.getFact("virt.uuid");

                if (uuidFact != null) {
                    host = this.consumerCurator.getHost(uuidFact, owner);
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

        if (owner != null) {
            criteria.add(Restrictions.eq("Pool.owner", owner));
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
                criteria.createAlias("Pool.providedProducts", "Provided", JoinType.LEFT_OUTER_JOIN);
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
                    criteria.createAlias("Pool.providedProducts", "Provided", JoinType.LEFT_OUTER_JOIN);
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
                        List<String> positives = new LinkedList<String>();
                        List<String> negatives = new LinkedList<String>();

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
                .add(CPRestrictions.in("id", poolIds));

            return this.listByCriteria(criteria, pageRequest, postFilter);
        }

        Page<List<Pool>> output = new Page<List<Pool>>();
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
     * @param o Owner whose subscriptions should be inspected.
     * @param date The date to test the active state.
     *        Set to null for current.
     * @return boolean is active on test date.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public boolean hasActiveEntitlementPools(Owner o, Date date) {
        if (o == null) {
            return false;
        }

        if (date == null) {
            date = new Date();
        }

        Criteria crit = createSecureCriteria();
        crit.add(Restrictions.eq("activeSubscription", Boolean.TRUE));
        crit.add(Restrictions.eq("owner", o));
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
    public List<Entitlement> retrieveFreeEntitlementsOfPools(List<Pool> existingPools, boolean lifo) {
        return criteriaToSelectEntitlementForPools(existingPools)
            .addOrder(lifo ? Order.desc("created") : Order.asc("created"))
            .list();
    }

    @SuppressWarnings("unchecked")
    public List<String> retrieveFreeEntitlementIdsOfPool(Pool existingPool, boolean lifo) {
        return criteriaToSelectEntitlementForPool(existingPool)
            .addOrder(lifo ? Order.desc("created") : Order.asc("created"))
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

    private Criteria criteriaToSelectEntitlementForPools(List<Pool> entitlementPools) {
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
    public List<Pool> lookupBySubscriptionId(Owner owner, String subId) {
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
    public List<Pool> lookupBySubscriptionIds(Owner owner, Collection<String> subIds) {
        return createSecureCriteria()
            .createAlias("sourceSubscription", "sourceSub")
            .add(Restrictions.eq("owner", owner))
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
     * @param owner Owner - The owner of the entitlements being passed in. Scoping
     *        this to a single owner prevents performance problems in large datasets.
     * @param subIdMap Map where key is Subscription ID of the pool, and value
     *        is the Entitlement just created or modified.
     * @return Pools with too many entitlements for their new quantity.
     */
    @SuppressWarnings("unchecked")
    public List<Pool> lookupOversubscribedBySubscriptionIds(Owner owner, Map<String, Entitlement> subIdMap) {
        List<Criterion> subIdMapCriteria = new ArrayList<Criterion>();
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
            .add(Restrictions.eq("owner", owner))
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

    @Transactional
    public Pool create(Pool entity) {
        /* Ensure all referenced PoolAttributes are correctly pointing to
         * this pool. This is useful for pools being created from
         * incoming json.
         */
        return super.create(entity);
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

    public Pool lockAndLoad(Pool pool) {
        currentSession().refresh(pool, LockOptions.UPGRADE);
        getEntityManager().refresh(pool);
        return pool;
    }

    public List<Pool> lockAndLoadBatch(Collection<String> ids) {
        return lockAndLoadBatch(ids, "Pool", "id");
    }

    public void lock(List<Pool> poolsToLock) {
        if (poolsToLock.isEmpty()) {
            log.debug("Nothing to lock");
            return;
        }
        List<String> ids = new ArrayList<String>();
        for (Pool p : poolsToLock) {
            ids.add(p.getId());
        }
        lockAndLoadBatchById(ids);
        log.debug("Done locking pools");
    }

    public List<ActivationKey> getActivationKeysForPool(Pool p) {
        List<ActivationKey> activationKeys = new ArrayList<ActivationKey>();
        List<ActivationKeyPool> activationKeyPools = currentSession().createCriteria(
            ActivationKeyPool.class).add(Restrictions.eq("pool", p)).list();

        for (ActivationKeyPool akp : activationKeyPools) {
            activationKeys.add(akp.getKey());
        }

        return activationKeys;
    }

    /**
     * Method to compile service/support level lists. One is the available levels for
     *  consumers for this owner. The second is the level names that are exempt. Exempt
     *  means that a product pool with this level can be used with a consumer of any
     *  service level.
     *
     * @param owner The owner that has the list of available service levels for
     *              its consumers
     * @param exempt boolean to show if the desired list is the levels that are
     *               explicitly marked with the support_level_exempt attribute.
     * @return Set of levels based on exempt flag.
     */
    public Set<String> retrieveServiceLevelsForOwner(Owner owner, boolean exempt) {
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
            .setParameter("owner_id", owner.getId())
            .setParameter("sl_attr", Product.Attributes.SUPPORT_LEVEL)
            .setParameter("sle_attr", Product.Attributes.SUPPORT_LEVEL_EXEMPT);

        List<Object[]> results = q.list();

        // Use case insensitive comparison here, since we treat
        // Premium the same as PREMIUM or premium, to make it easier for users to specify
        // a level on the CLI. However, use the original case, since Premium is more
        // attractive than PREMIUM.
        Set<String> slaSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        Set<String> exemptSlaSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        Set<String> exemptProductIds = new HashSet<String>();

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
        Pool toDelete = find(entity.getId());
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
    public void batchDelete(List<Pool> pools, Set<String> alreadyDeletedPools) {
        if (alreadyDeletedPools == null) {
            alreadyDeletedPools = new HashSet<String>();
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

    @SuppressWarnings("unchecked")
    public List<Pool> getPoolsBySubscriptionId(String subId) {
        return currentSession().createCriteria(Pool.class)
            .createAlias("sourceSubscription", "sourceSub", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.eq("sourceSub.subscriptionId", subId))
            .addOrder(Order.asc("id"))
            .list();
    }

    @SuppressWarnings("unchecked")
    public List<Pool> getPoolsBySubscriptionIds(Collection<String> subIds) {
        return currentSession().createCriteria(Pool.class)
            .createAlias("sourceSubscription", "sourceSub", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.in("sourceSub.subscriptionId", subIds))
            .addOrder(Order.asc("id"))
            .list();
    }

    @SuppressWarnings("unchecked")
    public Pool getMasterPoolBySubscriptionId(String subscriptionId) {
        return (Pool) currentSession().createCriteria(Pool.class)
            .createAlias("sourceSubscription", "srcsub", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.eq("srcsub.subscriptionId", subscriptionId))
            .add(Restrictions.eq("srcsub.subscriptionSubKey", "master"))
            .uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public List<Pool> listMasterPools() {
        return this.currentSession().createCriteria(Pool.class)
            .createAlias("sourceSubscription", "srcsub", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.eq("srcsub.subscriptionSubKey", "master"))
            .list();
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
        Set<String> result = new HashSet<String>();

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
            "FROM Pool P INNER JOIN P.providedProducts AS PP " +
            "WHERE NULLIF(PP.id, '') IS NOT NULL"
        );
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT DPP.id " +
            "FROM Pool P INNER JOIN P.derivedProvidedProducts AS DPP " +
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
        Set<String> result = new HashSet<String>();

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
            "FROM Pool P INNER JOIN P.providedProducts AS PP " +
            "WHERE NULLIF(PP.id, '') IS NOT NULL " +
            "AND P.owner = :owner"
        );
        query.setParameter("owner", owner);
        result.addAll(query.list());

        query = this.currentSession().createQuery(
            "SELECT DISTINCT DPP.id " +
            "FROM Pool P INNER JOIN P.derivedProvidedProducts AS DPP " +
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

        criteria.add(Restrictions.eq("owner", consumer.getOwner()))
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
            "where ent.pool.id = p.id and ent.consumer.id = cons.id and cons.type.id = ctype.id " +
            "and ctype.manifest = 'Y'),0) where p.owner = :owner";

        Query q = currentSession().createQuery(stmt);
        q.setParameter("owner", owner);
        q.executeUpdate();
    }

    public void markCertificatesDirtyForPoolsWithProducts(Owner owner, Collection<String> productIds) {
        for (List<String> batch : Iterables.partition(productIds, IN_OPERATOR_BLOCK_SIZE)) {
            markCertificatesDirtyForPoolsWithNormalProducts(owner, batch);
            markCertificatesDirtyForPoolsWithProvidedProducts(owner, batch);
        }
    }

    private void markCertificatesDirtyForPoolsWithNormalProducts(Owner owner,
        Collection<String> productIds) {

        String statement = "update Entitlement e set e.dirty=true where e.pool.id in " +
            "(select p.id from Pool p where p.product.id in :productIds) and e.owner = :owner";
        Query query = currentSession().createQuery(statement);
        query.setParameter("owner", owner);
        query.setParameterList("productIds", productIds);
        query.executeUpdate();
    }

    private void markCertificatesDirtyForPoolsWithProvidedProducts(Owner owner,
        Collection<String> productIds) {

        String statement = "update Entitlement e set e.dirty=true where e.pool.id in " +
            "(select p.id from Pool p join p.providedProducts pp where pp.id in :productIds)" +
            "and e.owner = :owner";
        Query query = currentSession().createQuery(statement);
        query.setParameter("owner", owner);
        query.setParameterList("productIds", productIds);
        query.executeUpdate();
    }
}
