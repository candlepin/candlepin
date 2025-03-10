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

import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.paging.Page;
import org.candlepin.util.Util;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.hibernate.query.NativeQuery;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.SetJoin;
import javax.persistence.criteria.Subquery;


/**
 * PoolCurator
 */
@Singleton
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    /** The recommended number of expired pools to fetch in a single call to listExpiredPools */
    public static final int EXPIRED_POOL_BLOCK_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(PoolCurator.class);
    private final ConsumerCurator consumerCurator;
    private final ConsumerTypeCurator consumerTypeCurator;

    @Inject
    public PoolCurator(ConsumerCurator consumerCurator, ConsumerTypeCurator consumerTypeCurator) {
        super(Pool.class);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param owner Owner to filter
     * @return pools owned by the given Owner.
     */
    public List<Pool> listByOwner(Owner owner) {
        return listByOwner(owner, null);
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param owner Owner to filter
     * @param activeOn only include pools active on the given date.
     * @return pools owned by the given Owner.
     */
    public List<Pool> listByOwner(Owner owner, Date activeOn) {
        CriteriaBuilder cb = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Pool> query = cb.createQuery(this.entityType());
        Root<Pool> root = query.from(this.entityType());
        query.select(root);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get(Pool_.owner), owner));
        predicates.add(cb.equal(root.get(Pool_.activeSubscription), true));

        if (activeOn != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(Pool_.startDate), activeOn));
            predicates.add(cb.greaterThanOrEqualTo(root.get(Pool_.endDate), activeOn));
        }

        query.where(predicates.toArray(new Predicate[0]));

        return this.getEntityManager()
            .createQuery(query)
            .getResultList();
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
    public List<Pool> listBySourceEntitlement(Entitlement e) {
        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Pool> cq = cb.createQuery(Pool.class);
        Root<Pool> pool = cq.from(Pool.class);

        Predicate entitlementPredicate = cb.equal(pool.get(Pool_.SOURCE_ENTITLEMENT)
            .get(Entitlement_.ID), e.getId());
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, cb, pool);

        if (securityPredicate != null) {
            cq.where(cb.and(entitlementPredicate, securityPredicate));
        }
        else {
            cq.where(entitlementPredicate);
        }

        return em.createQuery(cq).getResultList();
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

        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Define a parameter for the entitlements
        ParameterExpression<List> entitlementsParam = cb.parameter(List.class, "entitlements");

        CriteriaQuery<Pool> cq = cb.createQuery(Pool.class);
        Root<Pool> pool = cq.from(Pool.class);

        Predicate entitlementPredicate = pool.get(Pool_.SOURCE_ENTITLEMENT).in(entitlementsParam);
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, cb, pool);

        if (securityPredicate != null) {
            cq.where(cb.and(entitlementPredicate, securityPredicate));
        }
        else {
            cq.where(entitlementPredicate);
        }

        TypedQuery<Pool> query = em.createQuery(cq);

        // Handling partitioning and setting the parameter dynamically
        for (List<Entitlement> block : this.partition(ents)) {
            query.setParameter("entitlements", block);
            List<Pool> pools = query.getResultList();

            if (pools != null) {
                output.addAll(pools);
            }
        }

        if (!output.isEmpty()) {
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
    public List<Pool> listExpiredPools(int blockSize) {
        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Pool> cq = cb.createQuery(Pool.class);
        Root<Pool> tgtPool = cq.from(Pool.class);

        Date now = new Date();

        // Subquery to check for existing entitlements with endDateOverride >= now
        Subquery<Long> subquery = cq.subquery(Long.class);
        Root<Pool> entPool = subquery.from(Pool.class);
        Join<Pool, Entitlement> ent = entPool.join(Pool_.ENTITLEMENTS);

        subquery.select(cb.literal(1L))
            .where(cb.equal(entPool.get(Pool_.ID), tgtPool.get(Pool_.ID)),
                cb.greaterThanOrEqualTo(ent.get(Entitlement_.END_DATE_OVERRIDE), now));

        // Main query predicates
        Predicate endDatePredicate = cb.lessThan(tgtPool.get(Pool_.END_DATE), now);
        Predicate notExistsPredicate = cb.not(cb.exists(subquery));

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(endDatePredicate);
        predicates.add(notExistsPredicate);

        // Security predicate
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, cb, tgtPool);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        cq.select(tgtPool).where(cb.and(predicates.toArray(new Predicate[0])));

        TypedQuery<Pool> query = em.createQuery(cq);
        if (blockSize > 0) {
            query.setMaxResults(blockSize);
        }

        List<Pool> results = query.getResultList();
        return results != null ? results : new LinkedList<>();
    }

    /**
     * Retrieves the UUIDs for all the {@Link Consumer}s that have an entitlement for the pool that
     * corresponds to the provided pool ID.
     *
     * @param poolId
     *  the ID to the pool to retrieve entitled Consumer UUIDs for
     *
     * @return all the UUIDs for {@Link Consumer}s that are entitled to the provided pool ID
     */
    public List<String> listEntitledConsumerUuids(String poolId) {
        if (poolId == null || poolId.isBlank()) {
            return new ArrayList<>();
        }

        CriteriaBuilder builder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<String> query = builder.createQuery(String.class);
        Root<Consumer> root = query.from(Consumer.class);

        Join<Consumer, Entitlement> entitlements = root.join(Consumer_.entitlements);
        Join<Entitlement, Pool> pool = entitlements.join(Entitlement_.pool);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, builder, pool);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(pool.get(Pool_.id), poolId));

        query.select(root.get(Consumer_.uuid))
            .distinct(true)
            .where(predicates.toArray(new Predicate[0]));

        return this.getEntityManager()
            .createQuery(query)
            .getResultList();
    }

    /**
     * Retrieves a paged list of {@Pool}s that fulffill the restrictions dictated by the provided
     * {@link PoolQualifier}.
     *
     * @param qualifier
     *  an object that dictates criteria that a pool must meet
     *
     * @return a paged list of all the pools that fulfill the requirements provided by the
     *  {@link PoolQualifier}
     */
    public Page<List<Pool>> listAvailableEntitlementPools(PoolQualifier qualifier) {
        if (qualifier == null) {
            return emptyPage();
        }

        Consumer consumer = qualifier.getConsumer();
        String ownerId = qualifier.getOwnerId();
        if (consumer != null && ownerId != null && !ownerId.equals(consumer.getOwnerId())) {
            // Both a consumer and an owner were specified, but the consumer belongs to a different owner.
            // We can't possibly match a pool on two owners, so we can just abort immediately with an
            // empty page
            log.warn("Attempting to filter entitlement pools by owner and a consumer belonging to a " +
                "different owner: {}, {}", ownerId, consumer);

            return emptyPage();
        }

        CriteriaBuilder builder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Pool> query = builder.createQuery(Pool.class);
        Root<Pool> root = query.from(Pool.class);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        if (consumer != null) {
            ownerId = consumer.getOwnerId();
        }

        getConsumerPredicate(query, root, builder, consumer, ownerId)
            .ifPresent(predicates::add);

        if (ownerId != null) {
            predicates.add(builder.equal(root.get(Pool_.owner).get(Owner_.id), ownerId));
        }

        getQualifierPredicates(query, root, builder, qualifier)
            .ifPresent(predicates::addAll);

        query.select(root)
            .distinct(true)
            .where(predicates.toArray(new Predicate[0]));

        if (qualifier.getOrder() != null) {
            List<Order> order = this.buildJPAQueryOrder(builder, root, qualifier);
            query.orderBy(order);
        }

        List<Pool> pools = this.getEntityManager()
            .createQuery(query)
            .getResultList();

        return new Page<List<Pool>>()
            .setPageData(pools)
            .setMaxRecords(pools.size());
    }

    private Optional<List<Predicate>> getQualifierPredicates(CriteriaQuery<?> query, Root<Pool> root,
        CriteriaBuilder builder, PoolQualifier qualifier) {

        List<Predicate> predicates = new ArrayList<>();
        buildQueryArgumentInPredicate(root.get(Pool_.id), qualifier.getIds())
            .ifPresent(predicates::add);

        getPoolExpirationPredicate(root, builder, qualifier.getActiveOn(), qualifier.getAfter(),
            qualifier.isOnlyFuture(), qualifier.getAddFuture())
            .ifPresent(predicates::add);

        getSubscriptionPredicate(root, builder, qualifier.getSubscriptionIds())
            .ifPresent(predicates::add);

        Join<Pool, Product> product = root.join(Pool_.product);
        SetJoin<Product, Product> providedProduct = null;
        getProductsPredicate(root, builder, product, providedProduct, qualifier.getProductIds())
            .ifPresent(predicates::add);

        getMatchesPredicates(query, root, builder, product, providedProduct, qualifier.getMatches())
            .ifPresent(predicates::addAll);

        getAttributesPredicate(query, root, builder, qualifier.getAttributes())
            .ifPresent(predicates::addAll);

        return Optional.of(predicates);
    }

    private Optional<Predicate> getPoolExpirationPredicate(Root<Pool> root, CriteriaBuilder builder,
        Date activeOn, Date after, boolean onlyFuture, boolean addFuture) {

        List<Predicate> predicates = new ArrayList<>();
        if (activeOn != null) {
            if (onlyFuture) {
                predicates.add(builder.greaterThanOrEqualTo(root.get(Pool_.startDate), activeOn));
            }
            else if (!addFuture) {
                predicates.add(builder.lessThanOrEqualTo(root.get(Pool_.startDate), activeOn));
                predicates.add(builder.greaterThanOrEqualTo(root.get(Pool_.endDate), activeOn));
            }
            else {
                predicates.add(builder.greaterThanOrEqualTo(root.get(Pool_.endDate), activeOn));
            }
        }

        if (after != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get(Pool_.startDate), after));
        }

        if (predicates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(builder.and(predicates.toArray(new Predicate[0])));
    }

    private Optional<Predicate> getSubscriptionPredicate(Root<Pool> root, CriteriaBuilder builder,
        Set<String> subscriptionIds) {

        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return Optional.empty();
        }

        Join<Pool, SourceSubscription> subJoin = root.join(Pool_.sourceSubscription);

        return buildQueryArgumentInPredicate(subJoin.get(SourceSubscription_.subscriptionId),
            subscriptionIds);
    }

    private Optional<Predicate> getProductsPredicate(Root<Pool> root, CriteriaBuilder builder,
        Join<Pool, Product> product, SetJoin<Product, Product> providedProduct, Set<String> productIds) {

        if ((productIds == null || productIds.isEmpty())) {
            return Optional.empty();
        }

        if (providedProduct == null) {
            providedProduct = product.join(Product_.providedProducts, JoinType.LEFT);
        }

        List<Predicate> predicates = new ArrayList<>();
        buildQueryArgumentInPredicate(product.get(Product_.id), productIds)
            .ifPresent(predicates::add);

        buildQueryArgumentInPredicate(providedProduct.get(Product_.id), productIds)
            .ifPresent(predicates::add);

        if (predicates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(builder.or(predicates.toArray(new Predicate[0])));
    }

    private Optional<List<Predicate>> getMatchesPredicates(CriteriaQuery<?> query, Root<Pool> root,
        CriteriaBuilder builder, Join<Pool, Product> product, SetJoin<Product, Product> providedProduct,
        Set<String> matches) {

        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }

        if (providedProduct == null) {
            providedProduct = product.join(Product_.providedProducts, JoinType.LEFT);
        }

        Join<Product, ProductContent> providedProdContent = providedProduct
            .join(Product_.productContent, JoinType.LEFT);

        Join<ProductContent, Content> content = providedProdContent
            .join(ProductContent_.content, JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();
        for (String match : matches) {
            String sanitized = this.sanitizeMatchesFilter(match);

            Predicate supportLevelPredicate = null;
            if (sanitized == null || sanitized.isEmpty()) {
                supportLevelPredicate = buildPoolAttributePredicate(builder, query, root,
                    Product.Attributes.SUPPORT_LEVEL, attr ->
                    builder.or(builder.isNull(attr), builder.equal(attr, "")));
            }
            else {
                supportLevelPredicate = buildPoolAttributePredicate(builder, query, root,
                    Product.Attributes.SUPPORT_LEVEL, attr -> ilike(builder, attr, sanitized));
            }

            Predicate matchesDisjunction = builder.or(
                ilike(builder, root.get(Pool_.contractNumber), sanitized),
                ilike(builder, root.get(Pool_.orderNumber), sanitized),
                ilike(builder, product.get(Product_.id), sanitized),
                ilike(builder, product.get(Product_.name), sanitized),
                ilike(builder, providedProduct.get(Product_.id), sanitized),
                ilike(builder, providedProduct.get(Product_.name), sanitized),
                ilike(builder, content.get(Content_.name), sanitized),
                ilike(builder, content.get(Content_.label), sanitized),
                supportLevelPredicate
            );

            predicates.add(matchesDisjunction);
        }

        return Optional.of(predicates);
    }

    private Optional<List<Predicate>> getAttributesPredicate(CriteriaQuery<?> query, Root<Pool> root,
        CriteriaBuilder builder, Map<String, List<String>> attributes) {

        if (attributes == null || attributes.isEmpty()) {
            return Optional.empty();
        }

        List<Predicate> predicates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Collection<String> attributeFilters = entry.getValue();

            if (key != null && !key.isEmpty()) {
                if (attributeFilters == null || attributeFilters.isEmpty()) {
                    predicates.add(buildPoolAttributePredicate(builder, query, root, key,
                        attr -> builder.or(builder.isNull(attr), builder.equal(attr, ""))));
                }

                List<Predicate> inclusionPredicates = new ArrayList<>();
                List<Predicate> exclusionPredicates = new ArrayList<>();
                for (String attributeValue : attributeFilters) {
                    String sanitized = attributeValue.startsWith("!") ?
                        sanitizeMatchesFilter(attributeValue.substring(1)) :
                        sanitizeMatchesFilter(attributeValue);

                    if (attributeValue.startsWith("!")) {
                        exclusionPredicates.add(buildPoolAttributePredicate(builder, query, root, key,
                            attr -> ilike(builder, attr, sanitized)));
                    }
                    else {
                        inclusionPredicates.add(buildPoolAttributePredicate(builder, query, root, key,
                            attr -> ilike(builder, attr, sanitized)));
                    }
                }

                if (!inclusionPredicates.isEmpty()) {
                    predicates.add(builder.or(inclusionPredicates.toArray(new Predicate[0])));
                }

                if (!exclusionPredicates.isEmpty()) {
                    predicates.add(builder.or(exclusionPredicates.toArray(new Predicate[0])).not());
                }
            }
        }

        return Optional.of(predicates);
    }

    private Optional<Predicate> getConsumerPredicate(CriteriaQuery<?> query, Root<Pool> root,
        CriteriaBuilder builder, Consumer consumer, String ownerId) {

        if (consumer == null) {
            return Optional.empty();
        }

        ConsumerType consumerType = this.consumerTypeCurator.getConsumerType(consumer);
        if (consumerType.isManifest()) {
            Subquery<String> subQuery = query.subquery(String.class);
            Root<Pool> subQueryRoot = subQuery.from(Pool.class);
            Predicate idPredicate = builder
                .equal(subQueryRoot.get(Pool_.id), root.get(Pool_.id));

            MapJoin<Pool, String, String> attributes = subQueryRoot.join(Pool_.attributes);
            Predicate attributePredicate = builder.equal(attributes.key(), Pool.Attributes.REQUIRES_HOST);

            subQuery.select(subQueryRoot.get(Pool_.id))
                .where(idPredicate, attributePredicate);

            return Optional.of(builder.exists(subQuery).not());
        }
        else if (!consumer.isGuest()) {
            return Optional.of(buildPoolAttributePredicate(builder, query, root, Pool.Attributes.VIRT_ONLY,
                attr -> builder.equal(attr, "true"))
                .not());
        }
        else if (consumer.hasFact(Consumer.Facts.VIRT_UUID)) {
            String uuidFact = consumer.getFact(Consumer.Facts.VIRT_UUID);
            if (uuidFact == null) {
                return Optional.empty();
            }

            Consumer host = this.consumerCurator.getHost(uuidFact, ownerId);
            if (host == null) {
                return Optional.empty();
            }

            // Impl note:
            // This query matches pools with the "requires_host" attribute explicitly set to a
            // value other than the host we're looking for. We then negate the results of this
            // subquery, so our final result is: fetch pools which do not have a required host
            // or have a required host equal to our host.

            Subquery<String> subQuery = query.subquery(String.class);
            Root<Pool> subQueryRoot = subQuery.from(Pool.class);
            subQuery.select(subQueryRoot.get(Pool_.id));

            Predicate idPredicate = builder.equal(subQueryRoot.get(Pool_.id), root.get(Pool_.id));

            MapJoin<Pool, String, String> attributes = subQueryRoot.join(Pool_.attributes);
            Predicate attributeKeyPredicate = builder
                .equal(attributes.key(), Pool.Attributes.REQUIRES_HOST);
            Predicate attributeValuePredicate = builder
                .notEqual(builder.lower(attributes.value()), host.getUuid().toLowerCase());

            subQuery.where(idPredicate, attributeKeyPredicate, attributeValuePredicate);

            return Optional.of(builder.exists(subQuery).not());
        }

        return Optional.empty();
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

    private Predicate ilike(CriteriaBuilder cb, Expression<String> expression, String attrValue) {
        return cb.like(cb.lower(expression), cb.lower(cb.literal(attrValue)), '!');
    }

    private Page<List<Pool>> emptyPage() {
        Page<List<Pool>> output = new Page<>();
        output.setPageData(Collections.emptyList());
        output.setMaxRecords(0);
        return output;
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
    public boolean hasActiveEntitlementPools(String ownerId, Date date) {
        if (ownerId == null || ownerId.isBlank()) {
            return false;
        }

        if (date == null) {
            date = new Date();
        }

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Pool> root = query.from(Pool.class);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner).get(Owner_.id), ownerId));
        predicates.add(builder.lessThanOrEqualTo(root.get(Pool_.startDate), date));
        predicates.add(builder.greaterThanOrEqualTo(root.get(Pool_.endDate), date));

        query.select(builder.count(root))
            .where(predicates.toArray(new Predicate[0]));

        try {
            Long count = entityManager.createQuery(query)
                .getSingleResult();

            return count > 0;
        }
        catch (NoResultException e) {
            return false;
        }
    }

    /**
     * Determines if the owner has pools for all of the products that are provided
     *
     * @param ownerKey
     *  the key to the owner who owns the pools
     *
     * @param productIds
     *   the IDs of the products to check for existing pools
     *
     * @throws IllegalArgumentException
     *  if the owner key is null or blank or if the product IDs are null or empty
     *
     * @return true if the owner has pools for all of the products,
     *  or false if there is a missing pool for any of the products
     */
    public boolean hasPoolsForProducts(String ownerKey, Collection<String> productIds) {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("Owner key cannot be null or blank");
        }

        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Product IDs cannot be null or empty");
        }

        String jpql = "SELECT prod.id " +
            "FROM Pool pool JOIN pool.owner owner JOIN pool.product prod " +
            "WHERE owner.key = :owner_key AND prod.id IN (:product_ids)";

        List<String> productsWithPools = this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_key", ownerKey)
            .setParameter("product_ids", productIds)
            .getResultList();

        return productsWithPools.containsAll(productIds);
    }

    public List<Pool> listPoolsRestrictedToUser(String username) {
        String jpql = """
            SELECT p FROM Pool p
            WHERE p.restrictedToUsername = :username""";

        return getEntityManager().createQuery(jpql, Pool.class)
            .setParameter("username", username)
            .getResultList();
    }


    public List<String> retrieveOrderedEntitlementIdsOf(Collection<Pool> pools) {
        String jpql = """
            SELECT e.id FROM Entitlement e
            WHERE e.pool IN :pools
            ORDER BY e.created DESC""";

        return getEntityManager().createQuery(jpql, String.class)
            .setParameter("pools", pools)
            .getResultList();
    }

    public List<Entitlement> retrieveOrderedEntitlementsOf(Collection<Pool> existingPools) {
        String jpql = """
            SELECT e FROM Entitlement e
            WHERE e.pool IN :pools ORDER BY e.created DESC""";

        return getEntityManager().createQuery(jpql, Entitlement.class)
            .setParameter("pools", existingPools)
            .getResultList();
    }

    /**
     * @param entitlementPool entitlement pool to search.
     * @return entitlements in the given pool.
     */
    public List<Entitlement> entitlementsIn(Pool entitlementPool) {
        String jpql = """
            SELECT e FROM Entitlement e
            WHERE e.pool = :entitlementPool""";

        return getEntityManager().createQuery(jpql, Entitlement.class)
            .setParameter("entitlementPool", entitlementPool)
            .getResultList();
    }

    /**
     * Query pools by the subscription that generated them.
     *
     * @param owner
     *  the owner of the subscriptions to query
     *
     * @param subId
     *  subscription to look up pools by
     *
     * @return pools from the given subscription, sorted by pool.id to avoid deadlocks
     */
    public List<Pool> getBySubscriptionId(Owner owner, String subId) {
        if (owner == null || subId == null || subId.isBlank()) {
            return new ArrayList<>();
        }

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Pool> query = builder.createQuery(Pool.class);
        Root<Pool> root = query.from(Pool.class);

        Join<Pool, SourceSubscription> subJoin = root.join(Pool_.sourceSubscription);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner), owner));
        predicates.add(builder.equal(subJoin.get(SourceSubscription_.subscriptionId), subId));

        query.select(root)
            .where(predicates.toArray(new Predicate[0]))
            .orderBy(builder.asc(root.get(Pool_.id)));

        return entityManager.createQuery(query)
            .getResultList();
    }

    /**
     * Query pools by the subscriptions that generated them.
     *
     * @param owner The owner of the subscriptions to query
     * @param subIds Subscriptions to look up pools by
     * @return pools from the given subscriptions, sorted by pool.id to avoid
     *         deadlocks
     */
    public List<Pool> getBySubscriptionIds(Owner owner, Collection<String> subIds) {
        return this.getBySubscriptionIds(owner.getId(), subIds);
    }

    /**
     * Query pools by the subscriptions that generated them.
     *
     * @param ownerId
     *  the owner of the subscriptions to query
     *
     * @param subIds
     *  subscriptions to look up pools by
     *
     * @return pools from the given subscriptions, sorted by pool.id to avoid
     *  deadlocks
     */
    public List<Pool> getBySubscriptionIds(String ownerId, Collection<String> subIds) {
        if (ownerId == null || ownerId.isBlank() || subIds == null || subIds.isEmpty()) {
            return new ArrayList<>();
        }

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Pool> query = builder.createQuery(Pool.class);
        Root<Pool> root = query.from(Pool.class);

        Join<Pool, SourceSubscription> subJoin = root.join(Pool_.sourceSubscription);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner).get(Owner_.id), ownerId));

        List<Pool> pools = new ArrayList<>();
        for (List<String> block : this.partition(subIds)) {
            ParameterExpression<Collection> inArgs = builder.parameter(Collection.class, "subIds");
            predicates.add(subJoin.get(SourceSubscription_.subscriptionId).in(inArgs));

            query.select(root)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(builder.asc(root.get(Pool_.id)));

            pools.addAll(entityManager.createQuery(query)
                .setParameter("subIds", block)
                .getResultList());
        }

        return pools;
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
    public List<Pool> getOversubscribedBySubscriptionIds(String ownerId, Map<String, Entitlement> subIdMap) {
        List<Pool> result = new ArrayList<>();
        int blockSize = Math.min(this.getQueryParameterLimit(), (this.getInBlockSize() - 1) / 2);

        String jpql = """
            SELECT p FROM Pool p
            WHERE p.owner.id = :ownerId
            AND p.quantity >= 0
            AND p.consumed > p.quantity
            AND p.sourceSubscription.subscriptionId IN :subscriptionIds
            AND (p.sourceEntitlement IS NULL OR p.sourceEntitlement IN :entitlements)
            """;

        TypedQuery<Pool> query = getEntityManager().createQuery(jpql, Pool.class)
            .setParameter("ownerId", ownerId);

        for (Map<String, Entitlement> block : this.partitionMap(subIdMap, blockSize)) {
            query.setParameter("subscriptionIds", new ArrayList<>(block.keySet()));
            query.setParameter("entitlements", new ArrayList<>(block.values()));

            result.addAll(query.getResultList());
        }

        return result;
    }

    public List<ActivationKey> getActivationKeysForPool(Pool pool) {
        String jpql = """
            SELECT akp.activationKey FROM ActivationKeyPool akp
            WHERE akp.pool = :pool""";

        return this.getEntityManager()
            .createQuery(jpql, ActivationKey.class)
            .setParameter("pool", pool)
            .getResultList();
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
    @SuppressWarnings("unchecked")
    public Set<String> retrieveServiceLevelsForOwner(String ownerId, boolean exempt) {
        String jpql = """
            SELECT DISTINCT key(Attribute), value(Attribute), Product.id
            FROM Pool AS Pool
              INNER JOIN Pool.product AS Product
              INNER JOIN Product.attributes AS Attribute
              LEFT JOIN Pool.entitlements AS Entitlement
            WHERE Pool.owner.id = :owner_id
              AND (key(Attribute) = :sl_attr OR key(Attribute) = :sle_attr)
              AND (Pool.endDate >= current_date() OR Entitlement.endDateOverride >= current_date())
            ORDER BY key(Attribute) DESC"""; // Needs to be ordered, because the code below assumes exempt
        // levels are first

        Query query = getEntityManager().createQuery(jpql).setParameter("owner_id", ownerId)
            .setParameter("sl_attr", Product.Attributes.SUPPORT_LEVEL)
            .setParameter("sle_attr", Product.Attributes.SUPPORT_LEVEL_EXEMPT);

        List<Object[]> results = query.getResultList();

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
                (value != null && !value.trim().isEmpty()) &&
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
            // The entity must be managed to be removed, merge it to ensure it is managed
            // Especially important if 'entity' might be in a detached state
            Pool managedEntity = getEntityManager().merge(entity);

            // Now we can remove the managed entity
            getEntityManager().remove(managedEntity);

            // Maintain runtime consistency. The entitlements for the pool have been deleted on the
            // database because delete is cascaded on Pool.entitlements relation

            // While it'd be nice to be able to skip this for every pool, we have no guarantee that
            // the pools came fresh from the DB with uninitialized entitlement collections. Since
            // it could be initialized, we should clear it so other bits using the pool don't
            // attempt to use the entitlements.
            PersistenceUnitUtil unitUtil = getEntityManager()
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();

            if (unitUtil.isLoaded(entity, "entitlements")) {
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
     * Batch deletes a collection of pools. If the given collection is null, this method silently returns.
     * Null elements in the collection will be ignored.
     *
     * @param pools pools to delete
     * @param alreadyDeletedPools pools to skip, they have already been deleted.
     */
    public void batchDelete(Collection<Pool> pools, Collection<String> alreadyDeletedPools) {
        if (pools == null) {
            return;
        }

        if (alreadyDeletedPools == null) {
            alreadyDeletedPools = new HashSet<>();
        }

        for (Pool pool : pools) {
            if (pool == null) {
                continue;
            }

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
     * Fetches the pools associated with the given consumer with the provided stack IDs. If no
     * consumer is provided or no stack IDs are provided, this method returns an empty list.
     *
     * @param consumer
     *  the consumer for which to find stacked pools
     *
     * @param stackIds
     *  a collection of stack IDs representing the stacks of pools to fetch
     *
     * @return
     *  a list containing all of the stacked pools with the given stack IDs owned by the specified
     *  consumer
     */
    public List<Pool> getSubPoolsForStackIds(Consumer consumer, Collection<String> stackIds) {
        return consumer != null ?
            this.getSubPoolsForStackIds(Arrays.asList(consumer), stackIds) :
            (new ArrayList<>());
    }

    /**
     * Fetches the pools associated with the given consumers with the provided stack IDs. If no
     * consumers are provided or no stack IDs are provided, this method returns an empty list.
     *
     * @param consumers
     *  a collection of consumers for which to find stacked pools
     *
     * @param stackIds
     *  a collection of stack IDs representing the stacks of pools to fetch
     *
     * @return
     *  a list containing all of the stacked pools with the given stack IDs owned by any of the
     *  specified consumer
     */
    public List<Pool> getSubPoolsForStackIds(Collection<Consumer> consumers, Collection<String> stackIds) {
        List<Pool> output = new ArrayList<>();

        if (consumers == null || consumers.isEmpty()) {
            return output;
        }

        if (stackIds == null || stackIds.isEmpty()) {
            return output;
        }

        // Impl note: There is some optimization that could be done here to determine the best way
        // to chunk the two collections here to minimize the number of queries, but in the general
        // case, this is likely sufficient. For now.
        int blockSize = Math.min(this.getQueryParameterLimit() / 2, this.getInBlockSize());

        String jpql = "SELECT pool FROM Pool pool JOIN pool.sourceStack stack " +
            "WHERE stack.sourceStackId IN (:stackIds) AND stack.sourceConsumer IN (:consumers)";

        TypedQuery<Pool> query = this.getEntityManager()
            .createQuery(jpql, Pool.class);

        for (List<Consumer> consumerBlock : this.partition(consumers, blockSize)) {
            for (List<String> stackIdBlock : this.partition(stackIds, blockSize)) {
                output.addAll(query.setParameter("stackIds", stackIdBlock)
                    .setParameter("consumers", consumerBlock)
                    .getResultList());
            }
        }

        return output;
    }

    /**
     * Retrieves pools that have a {@SourceSubscription} that has a source stack ID equal to the provided
     * stack ID.
     *
     * @param owner
     *  the owner of the pools to retrieve
     *
     * @param stackId
     *  the ID of the stack for the source subscriptions to retrieve pools for
     *
     * @return the pools that have a source subscription with a source stack ID equal to the provided
     *  stack ID.
     */
    public List<Pool> getOwnerSubPoolsForStackId(Owner owner, String stackId) {
        if (owner == null || stackId == null || stackId.isBlank()) {
            return new ArrayList<>();
        }

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Pool> query = builder.createQuery(Pool.class);
        Root<Pool> root = query.from(Pool.class);

        Join<Pool, SourceStack> stackJoin = root.join(Pool_.sourceStack);

        List<Predicate> predicates = new ArrayList<>();
        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, builder, root);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        predicates.add(builder.equal(root.get(Pool_.owner), owner));
        predicates.add(builder.equal(stackJoin.get(SourceStack_.sourceStackId), stackId));

        query.select(root)
            .where(predicates.toArray(new Predicate[0]));

        return entityManager.createQuery(query)
            .getResultList();
    }

    /**
     * Lookup all pools for subscriptions which are not in the given list of subscription
     * IDs. Used for pool cleanup during refresh.
     *
     * @param owner
     * @param expectedSubIds Full list of all expected subscription IDs.
     * @return a list of pools for subscriptions not matching the specified subscription list
     */
    public List<Pool> getPoolsFromBadSubs(Owner owner, Collection<String> expectedSubIds) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Pool> cq = cb.createQuery(Pool.class);
        Root<Pool> pool = cq.from(Pool.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(pool.get("owner"), owner));

        if (!expectedSubIds.isEmpty()) {
            Join<Pool, SourceSubscription> sourceSub = pool.join("sourceSubscription");
            predicates.add(cb.and(
                cb.not(sourceSub.get("subscriptionId").in(expectedSubIds)),
                cb.isNotNull(sourceSub.get("subscriptionId"))
            ));
        }

        cq.select(pool)
            .where(predicates.toArray(new Predicate[0]))
            .orderBy(cb.asc(pool.get("id")));

        return getEntityManager().createQuery(cq).getResultList();
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

            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(SourceSubscription.class);

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
    public Collection<String> getEntitlementIdsForPools(Collection<String> poolIds) {
        Set<String> entitlementIds = new HashSet<>();

        String jpql = """
            SELECT DISTINCT e.id FROM Pool p
            JOIN p.entitlements e
            WHERE p.id IN :block
            """;

        TypedQuery<String> query = getEntityManager().createQuery(jpql, String.class);

        if (poolIds != null && !poolIds.isEmpty()) {
            for (List<String> block : this.partition(poolIds)) {
                entitlementIds.addAll(query.setParameter("block", block)
                    .getResultList());
            }
        }

        return entitlementIds;
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
    public Collection<String> getPoolIdsForSourceEntitlements(Collection<String> entIds) {
        Set<String> poolIds = new HashSet<>();

        String jpql = """
            SELECT DISTINCT p.id FROM Pool p
            JOIN p.sourceEntitlement e
            WHERE e.id IN :block
            """;

        TypedQuery<String> query = getEntityManager().createQuery(jpql, String.class);

        if (entIds != null && !entIds.isEmpty()) {
            for (List<String> block : this.partition(entIds)) {
                poolIds.addAll(query.setParameter("block", block)
                    .getResultList());
            }
        }

        return poolIds;
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
    public Collection<String> getPoolIdsForEntitlements(Collection<String> entIds) {
        Set<String> poolIds = new HashSet<>();

        String jpql = """
            SELECT DISTINCT p.id FROM Entitlement e
            JOIN e.pool p
            WHERE e.id IN :block
            """;

        TypedQuery<String> query = getEntityManager().createQuery(jpql, String.class);

        if (entIds != null && !entIds.isEmpty()) {
            for (List<String> block : this.partition(entIds)) {
                poolIds.addAll(query
                    .setParameter("block", block)
                    .getResultList());
            }
        }

        return poolIds;
    }

    /**
     * Fetches the pools associated with the specified subscription IDs as a mapping of subscription
     * IDs to a list of pools for that subscription. If no pools exist for the given subscription
     * IDs, this method returns an empty map
     *
     * @param subscriptionIds
     *  a collection of subscription IDs to use for fetching pools
     *
     * @return
     *  a map containing subscription IDs mapped to lists of associated pools
     */
    public Map<String, List<Pool>> mapPoolsBySubscriptionIds(Collection<String> subscriptionIds) {
        Map<String, List<Pool>> output = new HashMap<>();

        if (subscriptionIds != null && !subscriptionIds.isEmpty()) {
            String jpql = "SELECT DISTINCT ss.pool FROM SourceSubscription ss " +
                "WHERE ss.subscriptionId in (:sub_ids)";

            TypedQuery<Pool> query = this.getEntityManager()
                .createQuery(jpql, Pool.class);

            for (List<String> block : this.partition(subscriptionIds)) {
                query.setParameter("sub_ids", block)
                    .getResultList()
                    .forEach(pool -> {
                        output.computeIfAbsent(pool.getSubscriptionId(), key -> new LinkedList<>())
                            .add(pool);
                    });
            }
        }

        return output;
    }

    public List<Pool> getPoolsBySubscriptionId(String subId) {
        if (subId == null || subId.isBlank()) {
            return new ArrayList<>();
        }

        return getPoolsBySubscriptionIds(List.of(subId));
    }

    public List<Pool> getPoolsBySubscriptionIds(Collection<String> subIds) {
        if (subIds == null || subIds.isEmpty()) {
            return new ArrayList<>();
        }

        EntityManager em = this.getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Pool> cq = cb.createQuery(Pool.class);
        Root<SourceSubscription> sourceSubscription = cq.from(SourceSubscription.class);
        Join<SourceSubscription, Pool> poolJoin = sourceSubscription.join(SourceSubscription_.POOL);

        // Define a parameter for the subscription IDs
        ParameterExpression<List> subIdsParam = cb.parameter(List.class, "subIds");

        // Common predicates
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(sourceSubscription.get(SourceSubscription_.SUBSCRIPTION_ID).in(subIdsParam));

        Predicate securityPredicate = this.getSecurityPredicate(Pool.class, cb, poolJoin);
        if (securityPredicate != null) {
            predicates.add(securityPredicate);
        }

        cq.select(poolJoin).distinct(true)
            .where(cb.and(predicates.toArray(new Predicate[0])))
            .orderBy(cb.asc(poolJoin.get(Pool_.ID)));

        TypedQuery<Pool> query = em.createQuery(cq);

        // Handling partitioning and setting the parameter dynamically
        List<Pool> pools = new ArrayList<>();
        for (List<String> block : this.partition(subIds)) {
            query.setParameter("subIds", block);
            pools.addAll(query.getResultList());
        }

        return pools;
    }

    public List<Pool> getOwnersFloatingPools(Owner owner) {
        String jpql = """
            SELECT p FROM Pool p
            LEFT JOIN p.sourceSubscription sourceSub
            WHERE p.owner = :owner
              AND sourceSub.subscriptionId IS NULL
            ORDER BY p.id ASC""";

        return getEntityManager().createQuery(jpql, Pool.class)
            .setParameter(Pool_.OWNER, owner)
            .getResultList();
    }

    /**
     * Retrieves all known primary pools (subscriptions) for all owners.
     *
     * @return
     *  A query to fetch all known primary pools
     */
    public List<Pool> getPrimaryPools() {
        String jpql = "SELECT p FROM Pool p WHERE p.sourceSubscription.subscriptionSubKey = 'master'";

        return this.getEntityManager()
            .createQuery(jpql, Pool.class)
            .getResultList();
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
        String jpql = """
            UPDATE Pool p
            SET p.consumed = coalesce(
                (SELECT sum(quantity) FROM Entitlement ent
                 WHERE ent.pool.id = p.id), 0)
            WHERE p.owner = :owner""";

        Query query = getEntityManager().createQuery(jpql);

        query.setParameter("owner", owner)
            .executeUpdate();
    }

    public void calculateExportedForOwnersPools(Owner owner) {
        String jpql = """
            UPDATE Pool p
            SET p.exported = coalesce(
                (SELECT sum(ent.quantity) FROM Entitlement ent, Consumer cons, ConsumerType ctype
                 WHERE ent.pool.id = p.id
                 AND ent.consumer.id = cons.id
                 AND cons.typeId = ctype.id
                 AND ctype.manifest = 'Y'), 0)
            WHERE p.owner = :owner""";

        Query query = getEntityManager().createQuery(jpql);

        query.setParameter("owner", owner)
            .executeUpdate();
    }

    public void markCertificatesDirtyForPoolsWithProducts(Owner owner, Collection<String> productIds) {
        for (List<String> batch : Iterables.partition(productIds, getInBlockSize())) {
            markCertificatesDirtyForPoolsWithNormalProducts(owner, batch);
            markCertificatesDirtyForPoolsWithProvidedProducts(owner, batch);
        }
    }

    private void markCertificatesDirtyForPoolsWithNormalProducts(Owner owner, Collection<String> productIds) {
        String jpql = """
            UPDATE Entitlement e SET e.dirty=true
            WHERE e.pool.id IN
                (SELECT p.id FROM Pool p
                 WHERE p.owner=:owner
                 AND p.product.id IN :productIds)""";

        Query query = getEntityManager().createQuery(jpql);

        query.setParameter("owner", owner)
            .setParameter("productIds", productIds)
            .executeUpdate();
    }

    private void markCertificatesDirtyForPoolsWithProvidedProducts(Owner owner,
        Collection<String> productIds) {
        String jpql = """
            UPDATE Entitlement e
            SET e.dirty=true
            WHERE e.pool.id in
                (SELECT p.id FROM Pool p
                 JOIN p.product.providedProducts pp
                 WHERE pp.id IN :productIds)
            AND e.owner = :owner""";

        Query query = getEntityManager().createQuery(jpql);

        query.setParameter("owner", owner)
            .setParameter("productIds", productIds)
            .executeUpdate();
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
    public Map<String, Set<String>> getProvidedProductIdsByPools(Collection<Pool> pools) {
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
        Map<String, Set<String>> ppMap = new HashMap<>();

        // FIXME: This does not properly handle true N-tier; update as necessary
        String sql = "SELECT pool.id, prod.product_id FROM cp_pool pool " +
            "JOIN cp_product_provided_products ppp ON pool.product_uuid = ppp.product_uuid " +
            "JOIN cp_products prod ON prod.uuid = ppp.provided_product_uuid " +
            "WHERE pool.id IN (:pool_ids)";

        if (poolIds != null && !poolIds.isEmpty()) {
            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(Pool.class)
                .addSynchronizedEntityClass(Product.class)
                .addSynchronizedQuerySpace("cp_product_provided_products");

            for (List<String> block : this.partition(poolIds)) {
                query.setParameter("pool_ids", block);

                for (Object[] cols : (List<Object[]>) query.getResultList()) {
                    ppMap.computeIfAbsent((String) cols[0], key -> new HashSet<>())
                        .add((String) cols[1]);
                }
            }
        }

        return ppMap;
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
    public Map<String, Set<String>> getDerivedProvidedProductIdsByPools(Collection<Pool> pools) {
        if (pools == null || pools.isEmpty()) {
            return new HashMap<>();
        }

        Set<String> poolIds = pools.stream()
            .filter(pool -> pool != null && pool.getId() != null)
            .map(Pool::getId)
            .collect(Collectors.toSet());

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
        Map<String, Set<String>> dppMap = new HashMap<>();

        // FIXME: This does not properly handle true N-tier; update as necessary
        String sql = "SELECT pool.id, dprod.product_id FROM cp_pool pool " +
            "JOIN cp_products prod ON prod.uuid = pool.product_uuid " +
            "JOIN cp_product_provided_products ppp ON prod.derived_product_uuid = ppp.product_uuid " +
            "JOIN cp_products dprod ON dprod.uuid = ppp.provided_product_uuid " +
            "WHERE pool.id IN (:pool_ids)";

        if (poolIds != null && !poolIds.isEmpty()) {
            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(Pool.class)
                .addSynchronizedEntityClass(Product.class)
                .addSynchronizedQuerySpace("cp_product_provided_products");

            for (List<String> block : this.partition(poolIds)) {
                query.setParameter("pool_ids", block);

                for (Object[] cols : (List<Object[]>) query.getResultList()) {
                    dppMap.computeIfAbsent((String) cols[0], key -> new HashSet<>())
                        .add((String) cols[1]);
                }
            }
        }

        return dppMap;
    }

    @Transactional
    public void removeCdn(Cdn cdn) {
        if (cdn == null) {
            throw new IllegalArgumentException("Attempt to remove pool's cdn with null cdn value.");
        }

        String jpql = """
            UPDATE Pool p
            SET p.cdn = null
            WHERE p.cdn = :cdn""";

        int updated = getEntityManager()
            .createQuery(jpql)
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

            String jpql = """
                UPDATE Pool
                SET sourceEntitlement = null
                WHERE id IN (:pids)""";

            Query query = getEntityManager().createQuery(jpql);

            for (List<String> block : this.partition(poolIds)) {
                query.setParameter("pids", block)
                    .executeUpdate();
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

                    Set<String> poolIds = consumerPoolMap.computeIfAbsent(consumerId, k -> new HashSet<>());

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
            "    JOIN cp_product_attributes ppa ON ppa.product_uuid = p.product_uuid " +
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

            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("stackid_attrib_name", Product.Attributes.STACKING_ID)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(SourceStack.class)
                .addSynchronizedEntityClass(Entitlement.class)
                .addSynchronizedEntityClass(Pool.class)
                .addSynchronizedEntityClass(Product.class)
                .addSynchronizedQuerySpace("cp_product_attributes");

            int blockSize = Math.min(this.getQueryParameterLimit() - 1, this.getInBlockSize());
            for (List<String> block : Iterables.partition(entitlementIds, blockSize)) {
                query.setParameter("eids", block);
                output.addAll(query.getResultList());
            }
        }
        else {
            sql += ") ec ON ec.consumer_id = ss.sourceconsumer_id AND ec.stack_id = ss.sourcestackid " +
                "WHERE ec.entitlement_id IS NULL";

            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("stackid_attrib_name", Product.Attributes.STACKING_ID)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(SourceStack.class)
                .addSynchronizedEntityClass(Entitlement.class)
                .addSynchronizedEntityClass(Pool.class)
                .addSynchronizedEntityClass(Product.class)
                .addSynchronizedQuerySpace("cp_product_attributes");

            output.addAll(query.getResultList());
        }

        return output;
    }

    private Map<String, Set<String>> getBaseSyspurposeMap() {
        return Map.of(
            "usage", new HashSet<>(),
            "roles", new HashSet<>(),
            "addons", new HashSet<>(),
            "support_type", new HashSet<>(),
            "support_level", new HashSet<>());
    }

    /**
     * Builds a query for fetching the system purpose attributes mapped to the given owner's
     * products. Will only return attributes for products that are related to unexpired pools.
     *
     * @param ownerId
     *  The owner ID for which to fetch system purpose attributes
     *
     * @return
     *  a map of attributes belonging to the given owner
     */
    public Map<String, Set<String>> getSyspurposeAttributesByOwner(String ownerId) {
        Map<String, Set<String>> output = this.getBaseSyspurposeMap();

        if (ownerId == null) {
            return output;
        }

        // FIXME: This will also include future-dated subscriptions

        String sql = "SELECT DISTINCT attr.name, attr.value " +
            "FROM cp_product_attributes attr " +
            "JOIN cp_pool pool ON attr.product_uuid = pool.product_uuid " +
            "WHERE pool.owner_id = :owner_id " +
            "AND pool.endDate > CURRENT_TIMESTAMP " +
            "AND attr.name IN ('usage', 'roles', 'addons', 'support_type')" +
            "UNION " +
            "SELECT DISTINCT attr.name, attr.value " +
            "FROM cp_product_attributes attr " +
            "JOIN cp_pool pool ON attr.product_uuid = pool.product_uuid " +
            "WHERE pool.owner_id = :owner_id " +
            "AND pool.endDate > CURRENT_TIMESTAMP " +
            "AND attr.name = 'support_level' " +
            "AND NOT EXISTS (" +
            "  SELECT * FROM cp_product_attributes sle_attr " +
            "  WHERE sle_attr.product_uuid = attr.product_uuid " +
            "  AND sle_attr.name = 'support_level_exempt' " +
            "  AND sle_attr.value = 'true')";

        ((List<Object[]>) this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("owner_id", ownerId)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Pool.class)
            .addSynchronizedEntityClass(Product.class)
            .addSynchronizedQuerySpace("cp_product_attributes")
            .getResultList())
            .forEach(pair -> output.get((String) pair[0]).addAll(Util.toList((String) pair[1])));

        return output;
    }

    /**
     * Builds a query for fetching the system purpose attributes mapped to the given owner's
     * products. Will only return attributes for products that are related to unexpired pools.
     *
     * @param owner
     *  The owner for which to fetch system purpose attributes
     *
     * @return
     *  a map of attributes belonging to the given owner
     */
    public Map<String, Set<String>> getSyspurposeAttributesByOwner(Owner owner) {
        if (owner == null) {
            return getBaseSyspurposeMap();
        }

        return this.getSyspurposeAttributesByOwner(owner.getId());
    }

    /**
     * Fetches a list of IDs of pools referencing a product that exists in another org's namespace.
     * That is, a product not in the global namespace or the namespace of the pool's owner. If the
     * org has no pools with invalid product references, this method returns an empty list.
     *
     * @param ownerId
     *  the ID of the owner for which to fetch pools
     *
     * @return
     *  a list of IDs of pools referencing invalid products
     */
    public List<Pool> getPoolsReferencingInvalidProducts(String ownerId) {
        String jpql = "SELECT pool FROM Pool pool " +
            "JOIN pool.product prod " +
            "JOIN pool.owner owner " +
            "WHERE owner.id = :owner_id AND prod.namespace != '' AND prod.namespace != owner.key";

        return this.getEntityManager()
            .createQuery(jpql, Pool.class)
            .setParameter("owner_id", ownerId)
            .getResultList();
    }

    /**
     * Sets the "dirtyProduct" flag for any pool referencing a product in the given list of product
     * uuids.
     *
     * @param productUuids
     *  a collection of product UUIDs to use for flagging pools
     *
     * @return
     *  the number of pools flagged by this method, which may include pools which were already
     *  flagged
     */
    public int markPoolsDirtyReferencingProducts(Collection<String> productUuids) {
        if (productUuids == null || productUuids.isEmpty()) {
            return 0;
        }

        String jpql = "UPDATE Pool pool SET pool.dirtyProduct = true " +
            "WHERE pool.product.uuid IN (:product_uuids)";

        int count = 0;

        Query query = this.getEntityManager()
            .createQuery(jpql);

        for (List<String> block : this.partition(productUuids)) {
            count += query.setParameter("product_uuids", block)
                .executeUpdate();
        }

        return count;
    }

}
