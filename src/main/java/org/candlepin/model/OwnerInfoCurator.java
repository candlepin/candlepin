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

import org.candlepin.dto.api.server.v1.OwnerInfo;

import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;



/**
 * OwnerInfoCurator
 */
@Singleton
public class OwnerInfoCurator {
    private static final Pattern CONSUMER_TYPE_SPLITTER = Pattern.compile("\\s*,\\s*");

    private final Provider<EntityManager> entityManager;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final ConsumerCurator consumerCurator;
    private final PoolCurator poolCurator;

    @Inject
    public OwnerInfoCurator(Provider<EntityManager> entityManager, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, PoolCurator poolCurator) {

        this.entityManager = Objects.requireNonNull(entityManager);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);
    }

    /**
     * Builds a JPA predicate for performing an attribute equality check using the given builder,
     * query, and root. Note that the predicate is built using a correlated subquery matching the
     * provided pool root.
     * <p></p>
     * The attribute name is checked against the pool's attributes, and then against the pool's
     * product's attributes if, and only if, the pool itself does not define the attribute. The
     * check is performed using a standard equality operation which uses the underlying character
     * collation to determine case-sensitivity.
     *
     * @param builder
     *  the criteria builder to use for building various JPA criteria objects
     *
     * @param query
     *  the base query which needs to check for attribute equality
     *
     * @param root
     *  the query root which will be correlated to the subqueries built by this subquery; must be
     *  a pool root
     *
     * @param attribute
     *  the name of the attribute to match
     *
     * @param valuePredicateFunc
     *  a function which receives an expression representing the value for the attribute for each
     *  matching pool, and returns a predicate for determining if the value matches
     *
     * @return
     *  a predicate for performing attribute equality checks for pools
     */
    private Predicate buildPoolAttributePredicate(CriteriaBuilder builder, CriteriaQuery<?> query,
        Root<Pool> root, String attribute, Function<Expression<String>, Predicate> valuePredicateFunc) {

        Subquery<Pool> subquery = query.subquery(Pool.class);
        Root<Pool> correlation = subquery.correlate(root);

        Root<Pool> subqueryRoot = subquery.from(Pool.class);
        Join<Pool, Product> prodJoin = subqueryRoot.join(Pool_.product);
        MapJoin<Pool, String, String> poolAttr = subqueryRoot.join(Pool_.attributes, JoinType.LEFT);
        poolAttr.on(builder.equal(poolAttr.key(), attribute));
        MapJoin<Product, String, String> prodAttr = prodJoin.join(Product_.attributes, JoinType.LEFT);
        prodAttr.on(builder.equal(prodAttr.key(), attribute));

        subquery.select(subqueryRoot)
            .where(builder.equal(subqueryRoot, correlation),
                builder.or(builder.isNotNull(poolAttr.value()), builder.isNotNull(prodAttr.value())),
                valuePredicateFunc.apply(builder.coalesce(poolAttr.value(), prodAttr.value())));

        return builder.exists(subquery);
    }

    /**
     * Fetches a list of all known values for pool attributes of pools in the specified organization
     * that are active on the given date. This lookup will fall back to product attributes on the
     * pool's product if, and only if, the product defines the attribute but the pool does not.
     *
     * @param owner
     *  the organization in which to find pool attribute values
     *
     * @param date
     *  the active-on date to use to filter pools; pools with a start-end date range outside of this
     *  date will not be evaluated
     *
     * @param attribute
     *  the name of the attribute for which to fetch values
     *
     * @return
     *  a list of known pool attribute values for pools in the organization that are active on the
     *  given date
     */
    private List<String> getPoolAttributeValues(Owner owner, Date date, String attribute) {
        // Select product families from every active pool, first pulling from pool attributes and
        // then from product attributes iff no product family is defined on the pool.

        EntityManager em = this.entityManager.get();
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<String> query = builder.createQuery(String.class);
        Root<Pool> root = query.from(Pool.class);
        Join<Pool, Product> prodJoin = root.join(Pool_.product);
        MapJoin<Pool, String, String> poolAttr = root.join(Pool_.attributes, JoinType.LEFT);
        poolAttr.on(builder.equal(poolAttr.key(), attribute));
        MapJoin<Product, String, String> prodAttr = prodJoin.join(Product_.attributes, JoinType.LEFT);
        prodAttr.on(builder.equal(prodAttr.key(), attribute));

        List<Predicate> predicates = new ArrayList<>();

        Predicate securityPredicate = this.poolCurator.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner), owner));
        predicates.add(builder.lessThan(root.get(Pool_.startDate), date));
        predicates.add(builder.greaterThan(root.get(Pool_.endDate), date));
        predicates.add(builder.or(builder.isNotNull(poolAttr.value()), builder.isNotNull(prodAttr.value())));

        query.select(builder.coalesce(poolAttr.value(), prodAttr.value()))
            .where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query)
            .getResultList();
    }

    public OwnerInfo getByOwner(Owner owner) {
        OwnerInfoBuilder info = new OwnerInfoBuilder();
        Date now = new Date();

        List<ConsumerType> types = consumerTypeCurator.listAll();
        HashMap<String, ConsumerType> typeHash = new HashMap<>();
        for (ConsumerType type : types) {
            // Store off the type for later use
            typeHash.put(type.getLabel(), type);

            // Do the real work
            int consumers = (int) consumerCurator.getConsumerCount(owner, type);
            int entCount = consumerCurator.getConsumerEntitlementCount(owner, type);
            info.addTypeTotal(type, consumers, entCount);

            int count = getRequiresConsumerTypeCount(type, owner, now);
            info.addToConsumerTypeCountByPool(type, count);

            count = getEnabledConsumerTypeCount(type, owner, now);
            if (count > 0) {
                info.addToEnabledConsumerTypeCountByPool(type, count);
            }
        }

        int activePools = getActivePoolCount(owner, now);
        info.addDefaultEnabledConsumerTypeCount(activePools);

        Collection<String> families = this.getPoolAttributeValues(owner, now, Pool.Attributes.PRODUCT_FAMILY);

        for (String family : families) {
            int virtualCount = getProductFamilyCount(owner, now, family, true);
            int totalCount = getProductFamilyCount(owner, now, family, false);
            info.addToEntitlementsConsumedByFamily(family, totalCount - virtualCount, virtualCount);
        }

        int virtTotalEntitlements = getProductFamilyCount(owner, now, null, true);
        int totalEntitlements = getProductFamilyCount(owner, now, null, false);

        info.addDefaultEntitlementsConsumedByFamily(
            totalEntitlements - virtTotalEntitlements,
            virtTotalEntitlements);

        this.setVirtConsumerCounts(owner, info);
        setConsumerCountsByComplianceStatus(owner, info);

        return info.build();
    }

    /**
     * Fetches the count of all known consumers for a given org. If the organization does not exist,
     * or does not have any consumers, this method returns 0.
     *
     * @param owner
     *  the organization in which to count consumers
     *
     * @return
     *  the number of consumers in the organization, or zero if the organization does not exist or
     *  does not have any consumers
     */
    private int getConsumerCount(Owner owner) {
        EntityManager em = this.entityManager.get();
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Consumer> root = query.from(Consumer.class);

        List<Predicate> predicates = new ArrayList<>();

        Predicate securityPredicate = this.poolCurator.getSecurityPredicate(Consumer.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Consumer_.owner), owner));

        query.select(builder.count(root.get(Consumer_.id)))
            .where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query)
            .getSingleResult()
            .intValue();
    }

    /**
     * Counts the number of guest consumers in the given organization, where a guest is defined as a
     * consumer that has the "virt.is_guest" fact set to true. Note that this check is a case
     * insensitive check regardless of character collation. If the organization does not exist, or
     * does not have any consumers, this method returns 0.
     *
     * @param owner
     *  the organization in which to count guest consumers
     *
     * @return
     *  the number of guest consumers in the organization, or zero if the organization does not
     *  exist or does not have any guest consumers
     */
    private int getGuestConsumerCount(Owner owner) {
        EntityManager em = this.entityManager.get();
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Consumer> root = query.from(Consumer.class);
        MapJoin<Consumer, String, String> facts = root.join(Consumer_.facts);

        List<Predicate> predicates = new ArrayList<>();

        Predicate securityPredicate = this.poolCurator.getSecurityPredicate(Consumer.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Consumer_.owner), owner));
        predicates.add(builder.equal(builder.lower(facts.key()), Consumer.Facts.VIRT_IS_GUEST.toLowerCase()));
        predicates.add(builder.equal(builder.lower(facts.value()), "true"));

        query.select(builder.countDistinct(root.get(Consumer_.id)))
            .where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query)
            .getSingleResult()
            .intValue();
    }

    private void setVirtConsumerCounts(Owner owner, OwnerInfoBuilder info) {
        int consumerCount = this.getConsumerCount(owner);
        int guestCount = this.getGuestConsumerCount(owner);

        int physicalCount = consumerCount - guestCount;

        info.setGuestCount(guestCount);
        info.setPhysicalCount(physicalCount);
    }

    private void setConsumerCountsByComplianceStatus(Owner owner, OwnerInfoBuilder info) {
        EntityManager em = this.entityManager.get();
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<Consumer> root = query.from(Consumer.class);
        Path<String> entitlementStatus = root.get(Consumer_.entitlementStatus);

        List<Predicate> predicates = new ArrayList<>();

        Predicate securityPredicate = this.poolCurator.getSecurityPredicate(Consumer.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Consumer_.owner), owner));
        predicates.add(builder.isNotNull(entitlementStatus));

        query.multiselect(entitlementStatus, builder.count(root.get(Consumer_.id)))
            .groupBy(entitlementStatus)
            .where(predicates.toArray(new Predicate[0]));

        em.createQuery(query)
            .getResultList()
            .forEach(tuple -> info.setConsumerCountByComplianceStatus(tuple.get(0, String.class),
                tuple.get(1, Long.class).intValue()));
    }

    private int getActivePoolCount(Owner owner, Date date) {
        EntityManager em = this.entityManager.get();
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Pool> root = query.from(Pool.class);

        List<Predicate> predicates = new ArrayList<>();

        Predicate securityPredicate = this.poolCurator.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner), owner));
        predicates.add(builder.lessThan(root.get(Pool_.startDate), date));
        predicates.add(builder.greaterThan(root.get(Pool_.endDate), date));

        query.select(builder.count(root.get(Pool_.id)))
            .where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query)
            .getSingleResult()
            .intValue();
    }

    private int getRequiresConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        EntityManager em = this.entityManager.get();
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Pool> root = query.from(Pool.class);

        List<Predicate> predicates = new ArrayList<>();

        Predicate securityPredicate = this.poolCurator.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner), owner));
        predicates.add(builder.lessThan(root.get(Pool_.startDate), date));
        predicates.add(builder.greaterThan(root.get(Pool_.endDate), date));
        predicates.add(this.buildPoolAttributePredicate(builder, query, root,
            Pool.Attributes.REQUIRES_CONSUMER_TYPE, attr -> builder.equal(attr, type.getLabel())));

        query.select(builder.countDistinct(root.get(Pool_.id)))
            .where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query)
            .getSingleResult()
            .intValue();
    }

    private int getEnabledConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        // We could attempt to do this on the DB, but using JPA restricts so much functionality,
        // it's far easier to just fetch the attributes and count the pools locally. Probably
        // faster, too.

        int count = 0;

        List<String> enabledConsumerTypes = this.getPoolAttributeValues(owner, date,
            Pool.Attributes.ENABLED_CONSUMER_TYPES);

        for (String row : enabledConsumerTypes) {
            for (String label : CONSUMER_TYPE_SPLITTER.split(row)) {
                if (label.equalsIgnoreCase(type.getLabel())) {
                    ++count;
                }
            }
        }

        return count;
    }

    /*
     * If called with virt = false, returns the total number of all entitlements in
     * that family for that owner. So you probably want to subtract  the result of
     * virt=true from that.
     *
     * use family = null to get counts for all pools.
     *
     * At the time of writing, the current JPA criteria query resolves to the following SQL:
     *
     * select coalesce(sum(entitlemen1_.quantity), 0) as col_0_0_
     *   from cp_pool pool0_
     *   inner join cp_entitlement entitlemen1_ on pool0_.id=entitlemen1_.pool_id
     *   where pool0_.owner_id=?
     *     and pool0_.startDate<? and pool0_.endDate>?
     *     -- product family check
     *     and (exists (<SQ1>))
     *     -- virt attribute check
     *     and (exists (<SQ2>))
     *
     * SQ1 (product family check):
     * select pool2_.id
     *   from cp_pool pool2_
     *   inner join cp_products product3_ on pool2_.product_uuid=product3_.uuid
     *   left outer join cp_product_attributes attributes4_ on product3_.uuid=attributes4_.product_uuid
     *     and (attributes4_.name=?)
     *   left outer join cp_pool_attribute attributes5_ on pool2_.id=attributes5_.pool_id
     *     and (attributes5_.name=?)
     *   where pool2_.id=pool0_.id
     *     and (attributes5_.value is not null or attributes4_.value is not null)
     *     and coalesce(attributes5_.value, attributes4_.value)=?)
     *
     * SQ2 (virt attribute check):
     * select pool6_.id
     *   from cp_pool pool6_
     *   inner join cp_products product7_ on pool6_.product_uuid=product7_.uuid
     *   left outer join cp_product_attributes attributes8_ on product7_.uuid=attributes8_.product_uuid
     *     and (attributes8_.name=?)
     *   left outer join cp_pool_attribute attributes9_ on pool6_.id=attributes9_.pool_id
     *     and (attributes9_.name=?)
     *   where pool6_.id=pool0_.id
     *     and (attributes9_.value is not null or attributes8_.value is not null)
     *     and coalesce(attributes9_.value, attributes8_.value)=?)
     *
     * If either family is null or virt is false, the corresponding subquery is not present in the
     * generated SQL.
     */
    private int getProductFamilyCount(Owner owner, Date date, String family, boolean virt) {
        EntityManager em = this.entityManager.get();
        CriteriaBuilder builder = em.getCriteriaBuilder();

        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Pool> root = query.from(Pool.class);
        Join<Pool, Entitlement> entJoin = root.join(Pool_.entitlements);

        List<Predicate> predicates = new ArrayList<>();

        Predicate securityPredicate = this.poolCurator.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner), owner));
        predicates.add(builder.lessThan(root.get(Pool_.startDate), date));
        predicates.add(builder.greaterThan(root.get(Pool_.endDate), date));

        if (family != null) {
            predicates.add(this.buildPoolAttributePredicate(builder, query, root,
                Pool.Attributes.PRODUCT_FAMILY, attr -> builder.equal(attr, family)));
        }

        if (virt) {
            predicates.add(this.buildPoolAttributePredicate(builder, query, root,
                Pool.Attributes.VIRT_ONLY, attr -> builder.equal(attr, "true")));
        }

        query.select(builder.coalesce(builder.sumAsLong(entJoin.get(Entitlement_.quantity)), 0L))
            .where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query)
            .getSingleResult()
            .intValue();
    }
}
