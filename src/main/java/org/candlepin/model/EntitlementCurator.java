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

import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.hibernate.Hibernate;
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
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.SetJoin;
import javax.persistence.criteria.Subquery;


/**
 * EntitlementCurator
 */
@Singleton
public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    private static final Logger log = LoggerFactory.getLogger(EntitlementCurator.class);

    private final ConsumerTypeCurator consumerTypeCurator;

    /**
     * default ctor
     */
    @Inject
    public EntitlementCurator(ConsumerTypeCurator consumerTypeCurator) {
        super(Entitlement.class);

        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
    }

    // TODO: handles addition of new entitlements only atm!

    /**
     * @param entitlements entitlements to update
     * @return updated entitlements.
     */
    @Transactional
    public Set<Entitlement> bulkUpdate(Set<Entitlement> entitlements) {
        Set<Entitlement> toReturn = new HashSet<>();
        for (Entitlement toUpdate : entitlements) {
            Entitlement found = this.get(toUpdate.getId());
            if (found != null) {
                toReturn.add(found);
                continue;
            }
            toReturn.add(create(toUpdate));
        }
        return toReturn;
    }

    @SuppressWarnings("checkstyle:indentation")
    private List<Predicate> createCriteriaFromFilters(
        Root<Entitlement> root,
        CriteriaQuery<?> query,
        EntitlementFilterBuilder filterBuilder) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Join<Entitlement, Pool> pool = root.join(Entitlement_.pool);
        Join<Pool, Product> product = pool.join(Pool_.product);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.greaterThanOrEqualTo(pool.get(Pool_.endDate), new Date()));

        if (filterBuilder != null) {
            Collection<String> idFilters = filterBuilder.getIdFilters();
            if (idFilters != null && !idFilters.isEmpty()) {
                predicates.add(inPredicate(cb, pool.get(Pool_.id), idFilters));
            }

            // Matches stuff
            Collection<String> matchesFilters = filterBuilder.getMatchesFilters();
            if (matchesFilters != null && !matchesFilters.isEmpty()) {
                SetJoin<Product, Product> providedProducts = product
                    .join(Product_.providedProducts, JoinType.LEFT);
                SetJoin<Product, ProductContent> productContent = providedProducts
                    .join(Product_.productContent, JoinType.LEFT);
                Join<ProductContent, Content> content = productContent
                    .join(ProductContent_.content, JoinType.LEFT);

                for (String matches : matchesFilters) {
                    String sanitized = this.sanitizeMatchesFilter(matches);

                    Predicate matchesDisjunction = cb.or(
                        ilike(cb, pool.get(Pool_.contractNumber), sanitized),
                        ilike(cb, pool.get(Pool_.orderNumber), sanitized),
                        ilike(cb, product.get(Product_.id), sanitized),
                        ilike(cb, product.get(Product_.name), sanitized),
                        ilike(cb, providedProducts.get(Product_.id), sanitized),
                        ilike(cb, providedProducts.get(Product_.name), sanitized),
                        ilike(cb, content.get(Content_.name), sanitized),
                        ilike(cb, content.get(Content_.label), sanitized),
                        this.addProductAttributeFilterSubquery(
                            query,
                            pool.get(Pool_.id),
                            product,
                            Product.Attributes.SUPPORT_LEVEL,
                            Collections.singletonList(matches)
                        )
                    );

                    predicates.add(matchesDisjunction);
                }
            }

            // Attribute filters
            for (Map.Entry<String, List<String>> entry : filterBuilder.getAttributeFilters().entrySet()) {
                String attrib = entry.getKey();
                Collection<String> attributeFilters = entry.getValue();

                if (attrib != null && !attrib.isEmpty()) {
                    // TODO:
                    // Searching both pool and product attributes is likely an artifact from the days
                    // when we copied SKU product attributes to the pool. I don't believe there's any
                    // precedence for attribute lookups now that they're no longer being copied over.
                    // If this is not the case, then the following logic is broken and will need to be
                    // adjusted to account for one having priority over the other.

                    if (attributeFilters != null && !attributeFilters.isEmpty()) {
                        List<String> positives = new LinkedList<>();
                        List<String> negatives = new LinkedList<>();

                        for (String attributeFilter : attributeFilters) {
                            if (attributeFilter.startsWith("!")) {
                                negatives.add(attributeFilter.substring(1));
                            }
                            else {
                                positives.add(attributeFilter);
                            }
                        }

                        if (!positives.isEmpty()) {
                            predicates.add(this.addAttributeFilterSubquery(
                                query, attrib, positives, pool, product));
                        }

                        if (!negatives.isEmpty()) {
                            predicates.add(cb.not(this.addAttributeFilterSubquery(
                                query, attrib, negatives, pool, product)));
                        }
                    }
                    else {
                        predicates.add(this.addAttributeFilterSubquery(
                            query, attrib, attributeFilters, pool, product));
                    }
                }
            }
        }

        return predicates;
    }

    private Predicate inPredicate(CriteriaBuilder cb, Expression<String> path, Collection<String> values) {
        CriteriaBuilder.In<String> in = cb.in(path);
        for (String value : values) {
            in.value(value);
        }
        return in;
    }

    private Predicate addAttributeFilterSubquery(
        CriteriaQuery<?> query, String key, Collection<String> values,
        Join<Entitlement, Pool> pool, Join<Pool, Product> product) {

        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        return cb.or(
            poolAttributeFilterSubquery(query, key, values, pool),
            productAttributeFilterSubquery(query, key, values, product, pool)
        );
    }

    private Predicate poolAttributeFilterSubquery(
        CriteriaQuery<?> query,
        String key, Collection<String> values, Join<Entitlement, Pool> parentPool) {

        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Subquery<String> poolAttrSubquery = query.subquery(String.class);
        Root<Pool> pool = poolAttrSubquery.from(Pool.class);
        poolAttrSubquery.select(pool.get(Pool_.id));
        MapJoin<Pool, String, String> attributes = pool.join(Pool_.attributes);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(attributes.key(), key));
        Join<Entitlement, Pool> correlatedPool = poolAttrSubquery.correlate(parentPool);
        predicates.add(cb.equal(pool.get(Pool_.id), correlatedPool.get(Pool_.id)));

        if (values != null && !values.isEmpty()) {
            List<Predicate> poolAttrValueDisjunction = new ArrayList<>();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    poolAttrValueDisjunction.add(cb.isNull(attributes.value()));
                    poolAttrValueDisjunction.add(cb.equal(attributes.value(), ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    poolAttrValueDisjunction.add(ilike(cb, attributes.value(), attrValue));
                }
            }

            predicates.add(cb.or(toArray(poolAttrValueDisjunction)));
        }

        poolAttrSubquery.where(toArray(predicates));

        return cb.exists(poolAttrSubquery);
    }

    private Predicate productAttributeFilterSubquery(
        CriteriaQuery<?> query, String key, Collection<String> values,
        Join<Pool, Product> parentProduct, Join<Entitlement, Pool> pool) {

        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Subquery<String> prodAttrSubquery = query.subquery(String.class);
        Root<Product> product = prodAttrSubquery.from(Product.class);
        prodAttrSubquery.select(product.get(Product_.uuid));
        MapJoin<Product, String, String> attributes = product.join(Product_.attributes);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(attributes.key(), key));

        Join<Pool, Product> correlatedProduct = prodAttrSubquery.correlate(parentProduct);
        predicates.add(cb.equal(product.get(Product_.uuid), correlatedProduct.get(Product_.uuid)));
        predicates.add(attributeNotExists(prodAttrSubquery, pool.get(Pool_.id), key));

        if (values != null && !values.isEmpty()) {
            List<Predicate> poolAttrValueDisjunction = new ArrayList<>();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    poolAttrValueDisjunction.add(cb.isNull(attributes.value()));
                    poolAttrValueDisjunction.add(cb.equal(attributes.value(), ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    poolAttrValueDisjunction.add(ilike(cb, attributes.value(), attrValue));
                }
            }

            predicates.add(cb.or(toArray(poolAttrValueDisjunction)));
        }

        prodAttrSubquery.where(toArray(predicates));

        return cb.exists(prodAttrSubquery);
    }

    private Predicate addProductAttributeFilterSubquery(
        CriteriaQuery<?> query,
        Path<String> poolId,
        Join<Pool, Product> parentProduct,
        String key, Collection<String> values) {
        // Find all pools which have the given attribute (and values) on a product, unless the pool
        // defines that same attribute
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Subquery<String> prodAttributeSubquery = query.subquery(String.class);
        Root<Product> product = prodAttributeSubquery.from(Product.class);

        prodAttributeSubquery.select(product.get(Product_.uuid));

        List<Predicate> predicates = new ArrayList<>();

        MapJoin<Product, String, String> attributes = product.join(Product_.attributes);
        Join<Pool, Product> correlatedProduct = prodAttributeSubquery.correlate(parentProduct);
        predicates.add(cb.equal(product.get(Product_.uuid), correlatedProduct.get(Product_.uuid)));
        predicates.add(cb.equal(attributes.key(), key));
        predicates.add(attributeNotExists(prodAttributeSubquery, poolId, key));

        if (values != null && !values.isEmpty()) {
            List<Predicate> prodAttrValueDisjunction = new ArrayList<>();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    prodAttrValueDisjunction.add(cb.isNull(attributes.value()));
                    prodAttrValueDisjunction.add(cb.equal(attributes.value(), ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    prodAttrValueDisjunction.add(cb.like(attributes.value(), attrValue, '!'));
                }
            }
            predicates.add(cb.or(toArray(prodAttrValueDisjunction)));
        }

        prodAttributeSubquery.where(cb.and(toArray(predicates)));

        return cb.exists(prodAttributeSubquery);
    }

    private Predicate attributeNotExists(Subquery<?> query, Path<String> poolId, String key) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Subquery<String> subquery = query.subquery(String.class);
        Root<PoolAttribute> poolAttribute = subquery.from(PoolAttribute.class);

        subquery.select(poolAttribute.get(PoolAttribute_.poolId));
        subquery.where(cb.and(cb.equal(poolAttribute.get(PoolAttribute_.poolId), poolId),
            ilike(cb, poolAttribute.get(PoolAttribute_.name), key)));

        return cb.not(cb.exists(subquery));
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
     * This must return a sorted list in order to avoid deadlocks
     *
     * @param consumer
     * @return list of entitlements belonging to the consumer, ordered by pool id
     */
    public List<Entitlement> listByConsumer(Consumer consumer) {
        return listByConsumer(consumer, new EntitlementFilterBuilder());
    }

    public List<Entitlement> listByConsumer(Consumer consumer, EntitlementFilterBuilder filters) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Entitlement> query = cb.createQuery(Entitlement.class);
        Root<Entitlement> root = query.from(Entitlement.class);
        List<Predicate> criteria = this.createCriteriaFromFilters(root, query, filters);
        criteria.add(cb.equal(root.get(Entitlement_.consumer), consumer));

        query.distinct(true);
        query.where(toArray(criteria));

        return listByCriteria(query);
    }

    /**
     * Retrieves all the {@link Entitlement}s that belong to the {@link Consumer}s correspond to the provided
     * consumer UUIDs.
     *
     * @param consumerUuids
     *  the UUIDs to the consumers to retrieve entitlements for
     *
     * @return all the {@link Entitlement}s that belong to the {@link Consumer}s correspond to the provided
     *  consumer UUIDs. This method will not return null.
     */
    public List<Entitlement> listByConsumerUuids(Collection<String> consumerUuids) {
        List<Entitlement> entitlements = new ArrayList<>();
        if (consumerUuids == null || consumerUuids.isEmpty()) {
            return entitlements;
        }

        String jpql = "SELECT e FROM Entitlement e " +
            "WHERE e.consumer.uuid IN (:consumer_uuids)";

        TypedQuery<Entitlement> query = this.getEntityManager()
            .createQuery(jpql, Entitlement.class);

        for (List<String> block : this.partition(consumerUuids)) {
            query.setParameter("consumer_uuids", block);
            entitlements.addAll(query.getResultList());
        }

        return entitlements;
    }

    public List<Entitlement> listByConsumerAndPoolId(Consumer consumer, String poolId) {
        String jpql = "SELECT e FROM Entitlement e " +
            "WHERE e.consumer = :consumer AND e.pool.id = :pool_id ";

        if (consumer != null) {
            return this.getEntityManager()
                .createQuery(jpql, Entitlement.class)
                .setParameter("consumer", consumer)
                .setParameter("pool_id", poolId)
                .getResultList();
        }
        return Collections.emptyList();
    }

    public Page<List<Entitlement>> listByConsumer(Consumer consumer, String productId,
        EntitlementFilterBuilder filters, PageRequest pageRequest) {
        return listFilteredPages(
            consumer != null ? consumer.getOwnerId() : null,
            consumer, "consumer", productId, filters, pageRequest);
    }

    public Page<List<Entitlement>> listByOwner(Owner owner, String productId,
        EntitlementFilterBuilder filters, PageRequest pageRequest) {
        return listFilteredPages(
            owner != null ? owner.getId() : null, owner,
            "owner", productId, filters, pageRequest);
    }

    public Page<List<Entitlement>> listAll(EntitlementFilterBuilder filters, PageRequest pageRequest) {
        return listFilteredPages(null, null, null, null, filters, pageRequest);
    }

    private Page<List<Entitlement>> listFilteredPages(String ownerId, AbstractHibernateObject object,
        String objectType, String productId, EntitlementFilterBuilder filters, PageRequest pageRequest) {
        Page<List<Entitlement>> entitlementsPage;

        // No need to add filters when matching by product.
        if (object != null && productId != null) {
            entitlementsPage = matchByProducts(ownerId, object, objectType, productId, pageRequest);
        }
        else {
            // Build up any provided entitlement filters from query params.
            entitlementsPage = matchByFilters(object, objectType, filters, pageRequest);
        }
        return entitlementsPage;
    }

    private Page<List<Entitlement>> matchByProducts(String ownerId, AbstractHibernateObject object,
        String objectType, String productId, PageRequest pageRequest) {

        if (object == null || productId == null) {
            return new Page<>();
        }

        return listByProduct(object, objectType, productId, pageRequest);
    }

    private Page<List<Entitlement>> matchByFilters(AbstractHibernateObject<?> object, String objectType,
        EntitlementFilterBuilder filters, PageRequest pageRequest) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Entitlement> query = cb.createQuery(Entitlement.class);
        Root<Entitlement> root = query.from(Entitlement.class);
        List<Predicate> criteria = this.createCriteriaFromFilters(root, query, filters);
        if (object != null) {
            criteria.add(cb.equal(root.get(objectType), object));
        }

        query.distinct(true);
        query.where(toArray(criteria));
        return listByCriteria(root, query, pageRequest, countMatchesByFilters(object, objectType, filters));
    }

    private int countMatchesByFilters(AbstractHibernateObject<?> object, String objectType,
        EntitlementFilterBuilder filters) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Entitlement> root = query.from(Entitlement.class);
        List<Predicate> criteria = this.createCriteriaFromFilters(root, query, filters);
        if (object != null) {
            criteria.add(cb.equal(root.get(objectType), object));
        }

        query.distinct(true);
        query.select(cb.count(root));
        query.where(toArray(criteria));
        return this.entityManager.get().createQuery(query).getSingleResult().intValue();
    }

    public List<Entitlement> listByOwner(Owner owner) {
        String jpql = "SELECT e FROM Entitlement e WHERE e.owner = :owner";

        return this.getEntityManager()
            .createQuery(jpql, Entitlement.class)
            .setParameter("owner", owner)
            .getResultList();
    }

    /**
     * Fetches a the entitlements used by consumers in the specified environment.
     *
     * @param environment
     *  The environment for which to fetch entitlements
     *
     * @return
     *  A CandlepinQuery to iterate over the entitlements in the specified environment
     */
    public List<Entitlement> listByEnvironment(Environment environment) {
        return this.listByEnvironment(environment != null ? environment.getId() : null);
    }

    /**
     * Fetches the entitlements used by consumers in the specified environment.
     *
     * @param environmentId
     *  The ID of the environment for which to fetch entitlements
     *
     * @return
     *  A List to iterate over the entitlements in the specified environment
     */
    public List<Entitlement> listByEnvironment(String environmentId) {
        CriteriaBuilder cb = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Entitlement> query = cb.createQuery(Entitlement.class);
        Root<Entitlement> root = query.from(Entitlement.class);
        Join<Entitlement, Consumer> consumer = root.join(Entitlement_.consumer);
        MapJoin<Consumer, String, String> consumerEnvironments = consumer.join(Consumer_.environmentIds);

        query.select(root)
            .distinct(true)
            .where(cb.equal(consumerEnvironments.value(), environmentId));

        return listByCriteria(query);
    }

    /**
     * Fetches a the entitlement ids in the specified environment containing the
     *  specified content ids.
     *
     * @param environmentId
     *  The ID of the environment for which to fetch entitlements
     *
     *  @param contentIds
     *  A collection of content Ids for which to fetch entitlements
     *
     * @return List of entitlement ids
     */
    public List<String> listEntitlementIdByEnvironmentAndContent(String environmentId,
        Collection<String> contentIds) {

        // TODO: FIXME: Fix these queries? At worst this should be a union, not two individual queries.

        String sql =
            "select a.id from cp_entitlement a " +
            "    join cp_consumer b on a.consumer_id=b.id " +
            "    join cp_consumer_environments c on b.id=c.cp_consumer_id " +
            "    join cp_pool d on a.pool_id=d.id " +
            "    join cp_product_contents f on d.product_uuid=f.product_uuid " +
            "    join cp_contents g on f.content_uuid=g.uuid " +
            "where c.environment_id=:environmentId"  +
            "    and g.content_id in (:contentIds) ";

        List<String> results = this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("environmentId", environmentId)
            .setParameter("contentIds", contentIds)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Entitlement.class)
            .addSynchronizedEntityClass(Consumer.class)
            .addSynchronizedEntityClass(Pool.class)
            .addSynchronizedEntityClass(Product.class)
            .addSynchronizedEntityClass(Content.class)
            .addSynchronizedEntityClass(ProductContent.class)
            .addSynchronizedQuerySpace("cp_consumer_environments")
            .getResultList();

        sql =
            "select a.id from cp_entitlement a " +
            "    join cp_consumer b on a.consumer_id=b.id " +
            "    join cp_consumer_environments c on b.id=c.cp_consumer_id " +
            "    join cp_pool d on a.pool_id=d.id " +
            "    join cp_product_provided_products f on d.product_uuid=f.product_uuid " +
            "    join cp_product_contents g on f.provided_product_uuid=g.product_uuid " +
            "    join cp_contents h on g.content_uuid=h.uuid " +
            "where c.environment_id=:environmentId " +
            "    and h.content_id in (:contentIds) ";

        results.addAll(this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("environmentId", environmentId)
            .setParameter("contentIds", contentIds)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Entitlement.class)
            .addSynchronizedEntityClass(Consumer.class)
            .addSynchronizedEntityClass(Pool.class)
            .addSynchronizedEntityClass(Product.class)
            .addSynchronizedEntityClass(Content.class)
            .addSynchronizedEntityClass(ProductContent.class)
            .addSynchronizedQuerySpace("cp_consumer_environments")
            .addSynchronizedQuerySpace("cp_product_provided_products")
            .getResultList());

        return results;
    }

    /**
     * List entitlements for a consumer which are valid for a specific date.
     *
     * @param consumer Consumer to list entitlements for.
     * @param activeOn The date we want to see entitlements which are active on.
     * @return List of entitlements.
     */
    public List<Entitlement> listByConsumerAndDate(Consumer consumer, Date activeOn) {

        /*
         * Essentially the opposite of the above query which searches for entitlement
         * overlap with a "modifying" entitlement being granted. This query is used to
         * search for modifying entitlements which overlap with a regular entitlement
         * being granted. As such the logic is basically reversed.
         *
         */
        String jpql = "SELECT e FROM Entitlement e " +
            "JOIN Pool p on e.pool = p.id " +
            "WHERE e.consumer = :consumer " +
            "AND :activeOn >= p.startDate " +
            "AND :activeOn <= p.endDate";

        return this.getEntityManager()
            .createQuery(jpql, Entitlement.class)
            .setParameter("consumer", consumer)
            .setParameter("activeOn", activeOn)
            .getResultList();
    }

    /**
     * Lists dirty entitlements for the given consumer. If the consumer does not have any dirty
     * entitlements, this method returns an empty collection.
     *
     * @param consumer
     *  The consumer for which to find dirty entitlements
     *
     * @return
     *  a collection of dirty entitlements for the given consumer
     */
    public List<Entitlement> listDirty(Consumer consumer) {
        String jpql = "SELECT e FROM Entitlement e " +
            "WHERE e.consumer.id = :consumer_id AND e.dirty = true " +
            "ORDER BY e.id ASC";

        if (consumer != null) {
            return this.getEntityManager()
                .createQuery(jpql, Entitlement.class)
                .setParameter("consumer_id", consumer.getId())
                .getResultList();
        }

        return Collections.emptyList();
    }

    /**
     * Returns the set of all entitled product IDs from consumer's entitlements, current pool &
     * set of all pools about to be entitled (selected via auto attach or bulk pool attach) which overlap
     * the given date range.
     *
     * i.e. Given start date must be within the entitlements start/end dates, or
     * the given end date must be within the entitlements start/end dates,
     * or the given start date must be before the entitlement *and* the given end date
     * must be after entitlement. (i.e. we are looking for *any* overlap)
     *
     * @param consumer
     *  Entitled consumer
     * @param pool
     *  Pool under consideration to test for date overlap
     * @param poolsToBeEntitled
     *  Set of pools about to be entitled
     *
     * @return
     *  Set of entitled product IDs
     */
    public Set<String> listEntitledProductIds(Consumer consumer, Pool pool, Set<Pool> poolsToBeEntitled) {
        // FIXME Either address the TODO below, or move this method out of the curator.
        // TODO: Swap this to a db query if we're worried about memory:
        Set<String> entitledProductIds = new HashSet<>();
        List<Pool> pools = new LinkedList<>();
        for (Entitlement e : consumer.getEntitlements()) {
            pools.add(e.getPool());
        }

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        pools.add(pool);
        pools.addAll(poolsToBeEntitled);
        for (Pool p : pools) {
            if (!poolOverlapsRange(p, pool.getStartDate(), pool.getEndDate())) {
                // Skip this entitlement:
                continue;
            }

            entitledProductIds.add(p.getProduct().getId());
            for (Product pp : p.getProduct().getProvidedProducts()) {
                entitledProductIds.add(pp.getId());
            }

            // A distributor should technically be entitled to derived products and
            // will need to be able to sync content downstream.
            if (ctype.isManifest() && p.getDerivedProduct() != null) {
                entitledProductIds.add(p.getDerivedProduct().getId());

                for (Product dpp : p.getDerivedProduct().getProvidedProducts()) {
                    entitledProductIds.add(dpp.getId());
                }
            }
        }

        return entitledProductIds;
    }

    private boolean poolOverlapsRange(Pool p, Date startDate, Date endDate) {
        Date poolStart = p.getStartDate();
        Date poolEnd = p.getEndDate();
        // If pool start is within the range we're looking for:
        if (poolStart.compareTo(startDate) >= 0 && poolStart.compareTo(endDate) <= 0) {
            return true;
        }
        // If pool end is within the range we're looking for:
        if (poolEnd.compareTo(startDate) >= 0 && poolEnd.compareTo(endDate) <= 0) {
            return true;
        }
        // If pool completely encapsulates the range we're looking for:
        if (poolStart.compareTo(startDate) <= 0 && poolEnd.compareTo(endDate) >= 0) {
            return true;
        }
        return false;
    }

    /**
     * Marks all dependent entitlements of the given entitlements dirty, excluding those specified
     * as input. The result is the number of entitlements marked dirty by this operation.
     *
     * @param entitlementIds
     *  The collection of entitlement Ids for which to mark dependent entitlements dirty
     *
     * @return
     *  the number of entitlements marked dirty as a result of this operation
     */
    public int markDependentEntitlementsDirty(Iterable<String> entitlementIds) {
        if (entitlementIds != null && entitlementIds.iterator().hasNext()) {
            Set<String> eids = new HashSet<>();
            if (entitlementIds != null && entitlementIds.iterator().hasNext()) {
                // Find all entitlements that are modified by any of the specified entitlements.
                eids.addAll(findDependentEntitlementsByProvidedProduct(entitlementIds));

                // Distributors require modified product content matched on derived products to flow
                // downstream in the manifest. Determine if there are any modified entitlements based
                // on derived products of any distributors and include those as well.
                //
                // We do this in a separate query as it gives us a chance to skip the second query
                // if it does not apply to any of the specified entitlements.
                //
                // We do the filter here to avoid any unnecessary work in the
                // following query and to reduce the number of entitlements to check.
                Set<String> distributorEntitlements = filterDistributorEntitlementIds(entitlementIds);
                if (!distributorEntitlements.isEmpty()) {
                    eids.addAll(findDependentEntitlementsByDerivedProvidedProduct(distributorEntitlements));
                }
            }

            // At this point we have all of the entitlements that need to be marked dirty.
            // Update the affected entitlements, if necessary...
            return eids.isEmpty() ? 0 : this.markEntitlementsDirty(eids);
        }
        return 0;
    }

    /**
     * Given a collection of entitlement IDs, determine which belong to a distributor.
     *
     * @param entsIdsToFilter the Entitlement IDs to filter
     * @return all entitlement IDs that belong to a distributor.
     */
    public Set<String> filterDistributorEntitlementIds(Iterable<String> entsIdsToFilter) {
        Set<String> filteredIds = new HashSet<>();
        if (entsIdsToFilter != null && entsIdsToFilter.iterator().hasNext()) {
            String querySql = "SELECT DISTINCT e.id " +
                "FROM Entitlement e, Consumer c, ConsumerType t " +
                "WHERE t.manifest = true AND c.typeId = t.id AND e.consumer = c AND e.id IN (:eids)";

            Query query = this.getEntityManager().createQuery(querySql);
            int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit());

            for (List<String> block : Iterables.partition(entsIdsToFilter, blockSize)) {
                query.setParameter("eids", block);
                filteredIds.addAll(query.getResultList());
            }
        }
        return filteredIds;
    }

    /**
     * Marks the given entitlements as dirty; forcing a regeneration the next time it is requested.
     *
     * @param entitlementIds
     *  A collection of IDs of the entitlements to mark dirty
     *
     * @return
     *  The number of certificates updated
     */
    @Transactional
    public int markEntitlementsDirty(Iterable<String> entitlementIds) {
        int count = 0;

        if (entitlementIds != null && entitlementIds.iterator().hasNext()) {
            String hql = "UPDATE Entitlement SET dirty = true WHERE id IN (:entIds)";
            Query query = this.getEntityManager().createQuery(hql);

            for (List<String> block : this.partition(entitlementIds)) {
                count += query.setParameter("entIds", block).executeUpdate();
            }
        }

        return count;
    }

    /**
     * Marks all entitlements assigned to the specified organization as dirty. If the organization
     * has no entitlements, or the owner does not exist, this method returns 0. The output of this
     * method does not account for entitlements that are already dirty, and will, in effect, return
     * the number of entitlements in the organization.
     *
     * @param ownerId
     *  the ID of the organization (owner) for which to flag entitlements
     *
     * @return
     *  the number of entitlements flagged as dirty
     */
    public int markEntitlementsDirtyForOwner(String ownerId) {
        String jpql = "UPDATE Entitlement SET dirty = true WHERE owner.id = :owner_id";

        return this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", ownerId)
            .executeUpdate();
    }

    private Page<List<Entitlement>> listByProduct(
        AbstractHibernateObject object, String objectType, String productId, PageRequest pageRequest) {
        CriteriaBuilder builder = this.entityManager.get().getCriteriaBuilder();

        CriteriaQuery<Entitlement> entitlementQuery = builder.createQuery(Entitlement.class);
        Root<Entitlement> entitlement = entitlementQuery.from(Entitlement.class);
        entitlementQuery.where(createListByProductCriteria(object, objectType, productId, entitlement));

        return listByCriteria(entitlement, entitlementQuery, pageRequest,
            countProducts(object, objectType, productId));
    }

    private int countProducts(
        AbstractHibernateObject object, String objectType, String productId) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();

        CriteriaQuery<Long> entitlementQuery = cb.createQuery(Long.class);
        Root<Entitlement> entitlement = entitlementQuery.from(Entitlement.class);
        entitlementQuery.select(cb.count(entitlement));
        entitlementQuery.where(createListByProductCriteria(object, objectType, productId, entitlement));

        return this.entityManager.get().createQuery(entitlementQuery).getSingleResult().intValue();
    }

    private Predicate createListByProductCriteria(AbstractHibernateObject object, String objectType,
        String productId, Root<Entitlement> entitlement) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();

        Join<Entitlement, Pool> pool = entitlement.join(Entitlement_.pool);
        Join<Pool, Product> product = pool.join(Pool_.product);
        Join<Product, Product> providedProducts = product.join(Product_.providedProducts, JoinType.LEFT);

        return cb.and(
            cb.equal(entitlement.get(objectType), object),
            cb.greaterThanOrEqualTo(pool.get(Pool_.endDate), new Date()),
            cb.or(cb.equal(product.get(Product_.id), productId),
                cb.equal(providedProducts.get(Product_.id), productId)));
    }

    /**
     * Deletes the given entitlement.
     *
     * @param entity
     *  The entitlement entity to delete
     */
    @Transactional
    public void delete(Entitlement entity) {
        Entitlement toDelete = this.get(entity.getId());

        if (toDelete != null) {
            this.deleteImpl(toDelete);

            // Maintain runtime consistency.
            entity.setCertificates(null);
            entity.getConsumer().removeEntitlement(entity);
            entity.getPool().getEntitlements().remove(entity);
        }
    }

    /**
     * Deletes the given collection of entitlements.
     * <p/></p>
     * Note: Unlike the standard delete method, this method does not perform a lookup on an entity
     * before deleting it.
     *
     * @param entitlements
     *  The collection of entitlement entities to delete
     */
    public void batchDelete(Collection<Entitlement> entitlements) {
        for (Entitlement entitlement : entitlements) {
            this.deleteImpl(entitlement);

            // Maintain runtime consistency.
            entitlement.setCertificates(null);

            if (Hibernate.isInitialized(entitlement.getConsumer().getEntitlements())) {
                entitlement.getConsumer().removeEntitlement(entitlement);
            }

            if (Hibernate.isInitialized(entitlement.getPool().getEntitlements())) {
                entitlement.getPool().getEntitlements().remove(entitlement);
            }
        }
    }

    /**
     * Deletes the entitlements with the given collection of ids, using a SQL delete.
     *
     * WARNING: This method does not maintain runtime consistency for any of the entitlements it is deleting,
     * so the caller should take care of that manually for any of those entitlements that are loaded,
     * preferably by calling {@link #unlinkEntitlements} first.
     *
     * @param entitlementIds The collection of ids of the entitlements to be deleted.
     *
     * @return
     *  the number of entitlements deleted as a result of this operation
     */
    public int batchDeleteByIds(Collection<String> entitlementIds) {
        int deleted = 0;

        if (entitlementIds != null) {
            String jpql = "DELETE FROM Entitlement e WHERE e.id IN (:eids)";
            Query query = this.getEntityManager().createQuery(jpql);

            int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit());
            for (List<String> block : Iterables.partition(entitlementIds, blockSize)) {
                deleted += query.setParameter("eids", block)
                    .executeUpdate();
            }
        }

        return deleted;
    }

    /**
     * Maintains the runtime consistency of the given collection of entitlements, by removing references to
     * themselves and their certificates. This method is supposed to be run before performing the deletion
     * of the given list of entitlements.
     *
     * @param entitlements The collection of entitlements whose references are to be removed.
     */
    public void unlinkEntitlements(Collection<Entitlement> entitlements) {
        for (Entitlement entitlement : entitlements) {
            // Maintain runtime consistency.
            entitlement.setCertificates(null);

            if (Hibernate.isInitialized(entitlement.getConsumer().getEntitlements())) {
                entitlement.getConsumer().removeEntitlement(entitlement);
            }

            if (Hibernate.isInitialized(entitlement.getPool().getEntitlements())) {
                entitlement.getPool().getEntitlements().remove(entitlement);
            }
        }
    }

    private void deleteImpl(Entitlement entity) {
        log.debug("Deleting entitlement: {}", entity);
        EntityManager entityManager = this.getEntityManager();

        if (entity.getCertificates() != null) {
            log.debug("certs.size = {}", entity.getCertificates().size());

            for (EntitlementCertificate cert : entity.getCertificates()) {
                entityManager.remove(cert);
            }
        }

        entityManager.remove(entity);
    }

    public Entitlement findByCertificateSerial(Long serial) {
        String jpql = "SELECT e FROM Entitlement e " +
            "JOIN EntitlementCertificate ec on ec.entitlement.id = e.id " +
            "WHERE ec.serial.id = :serial ";

        try {
            return this.getEntityManager()
                .createQuery(jpql, Entitlement.class)
                .setParameter("serial", serial)
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            // Intentionally left empty
        }

        return null;
    }

    /**
     * Find the entitlements for the given consumer that are part of the specified stack.
     *
     * @param consumer the consumer
     * @param stackId the ID of the stack
     * @return the list of entitlements for the consumer that are in the stack.
     */
    public List<Entitlement> findByStackId(Consumer consumer, String stackId) {
        return findByStackIds(consumer, Arrays.asList(stackId));
    }

    /**
     * Find the entitlements for the given consumer that are part of the
     * specified stacks.
     *
     * @param consumer the consumer
     * @param stackIds the IDs of the stacks
     * @return the list of entitlements for the consumer that are in the stack.
     */
    public List<Entitlement> findByStackIds(Consumer consumer, Collection<String> stackIds) {
        List<Entitlement> result = new ArrayList<>();
        for (List<String> block: this.partition(stackIds, this.getInBlockSize())) {
            result.addAll(findByStackIds(consumer, block));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Entitlement> findByStackIds(Consumer consumer, List<String> stackIds) {
        if (stackIds == null || stackIds.isEmpty()) {
            return Collections.emptyList();
        }
        EntityManager em = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Entitlement> criteriaQuery = criteriaBuilder.createQuery(Entitlement.class);
        Root<Entitlement> entRoot = criteriaQuery.from(Entitlement.class);
        Join<Entitlement, Pool> poolJoin = entRoot.join(Entitlement_.POOL);
        Join<Pool, Product> productJoin = poolJoin.join(Pool_.PRODUCT);
        MapJoin<Product, String, String> attributes = productJoin.joinMap(Product_.ATTRIBUTES);
        Join<Pool, SourceStack> stackJoin = poolJoin.join(Pool_.SOURCE_STACK, JoinType.LEFT);
        criteriaQuery.select(entRoot);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(attributes.key(), Product.Attributes.STACKING_ID));
        predicates.add(inPredicate(criteriaBuilder, attributes, stackIds));
        predicates.add(criteriaBuilder.isNull(poolJoin.get(Pool_.SOURCE_ENTITLEMENT)));
        predicates.add(criteriaBuilder.isNull(stackJoin.get(SourceStack_.ID)));

        if (consumer != null) {
            predicates.add(criteriaBuilder.equal(entRoot.get(Entitlement_.CONSUMER), consumer));
        }
        Predicate[] predicateArray = new Predicate[predicates.size()];
        criteriaQuery.where(predicates.toArray(predicateArray));
        return em.createQuery(criteriaQuery).getResultList();
    }

    public List<Entitlement> findByPoolAttribute(Consumer consumer, String attributeName,
        String value) {

        EntityManager em = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Entitlement> criteriaQuery = criteriaBuilder.createQuery(Entitlement.class);
        Root<Entitlement> entitlement = criteriaQuery.from(Entitlement.class);
        MapJoin<Pool, String, String> attributes = entitlement.join(Entitlement_.POOL)
            .joinMap(Pool_.ATTRIBUTES);
        criteriaQuery.select(entitlement);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(attributes.key(), attributeName));
        predicates.add(criteriaBuilder.equal(attributes.value(), value));
        if (consumer != null) {
            predicates.add(criteriaBuilder.equal(entitlement.get(Entitlement_.consumer), consumer));
        }

        Predicate[] predicateArray = new Predicate[predicates.size()];
        criteriaQuery.where(predicates.toArray(predicateArray));

        return em.createQuery(criteriaQuery)
            .getResultList();
    }

    public List<Entitlement> findByPoolAttribute(String attributeName, String value) {
        return findByPoolAttribute(null, attributeName, value);
    }

    /**
     * For a given stack, find the eldest active entitlement with a subscription ID.
     * This is used to look up the upstream subscription certificate to use to talk to
     * the CDN.
     *
     * @param consumer the consumer
     * @param stackId the ID of the stack
     * @return the eldest active entitlement with a subscription ID, or null if none can
     * be found.
     */
    public Entitlement findUpstreamEntitlementForStack(Consumer consumer, String stackId) {
        Date currentDate = new Date();
        EntityManager em = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Entitlement> criteriaQuery = criteriaBuilder.createQuery(Entitlement.class);
        Root<Entitlement> entRoot = criteriaQuery.from(Entitlement.class);
        Join<Entitlement, Pool> poolJoin = entRoot.join(Entitlement_.POOL);
        Join<Pool, Product> productJoin = poolJoin.join(Pool_.PRODUCT);
        MapJoin<Product, String, String> attributes = productJoin.joinMap(Product_.ATTRIBUTES);
        Join<Pool, SourceSubscription> subscriptionJoin = poolJoin.join(Pool_.SOURCE_SUBSCRIPTION);
        criteriaQuery.orderBy(criteriaBuilder.asc(entRoot.get(Entitlement_.CREATED)));
        criteriaQuery.select(entRoot);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.lessThanOrEqualTo(poolJoin.get(Pool_.START_DATE), currentDate));
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(poolJoin.get(Pool_.END_DATE), currentDate));
        predicates.add(criteriaBuilder.equal(attributes.key(), Product.Attributes.STACKING_ID));
        predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(attributes.value()),
            stackId.toUpperCase()));
        predicates.add(criteriaBuilder.isNull(poolJoin.get(Pool_.SOURCE_ENTITLEMENT)));
        predicates.add(criteriaBuilder.isNotNull(subscriptionJoin.get(SourceSubscription_.ID)));

        if (consumer != null) {
            predicates.add(criteriaBuilder.equal(entRoot.get(Entitlement_.CONSUMER), consumer));
        }
        Predicate[] predicateArray = new Predicate[predicates.size()];
        criteriaQuery.where(predicates.toArray(predicateArray));

        try {
            return em.createQuery(criteriaQuery)
                .setMaxResults(1)
                .getSingleResult();
        }
        catch (NoResultException e) {
            // Intentionally left empty
        }

        return null;
    }

    /**
     * Finds the dependent entitlements for the specified entitlements, matching
     * on provided products only. Dependent entitlements are those who's
     * content are being modified by the consumption of another entitlement.
     *
     * Note : This method does not support N-tier product hierarchy.
     *
     * @param entitlementIds the entitlements to match on.
     * @return the set of entitlement IDs for the matched modifier entitlements.
     */
    private Set<String> findDependentEntitlementsByProvidedProduct(Iterable<String> entitlementIds) {
        String queryStr = "SELECT DISTINCT e2.id " +
            // Required entitlement
            "FROM cp_entitlement e1 " +
            // Required pool => required product
            "JOIN cp_pool pl1 on pl1.id = e1.pool_id " +
            // Pools Product => Provided product
            "JOIN cp_product_provided_products ppp1 ON ppp1.product_uuid = pl1.product_uuid " +
            // Provided product => Product
            "JOIN cp_products p ON p.uuid = ppp1.provided_product_uuid " +
            // Required product => conditional content
            "JOIN cp_content_required_products crp ON crp.product_id = p.product_id " +
            // Conditional content => dependent product
            "JOIN cp_product_contents pc ON pc.content_uuid = crp.content_uuid " +
            // Provided Product => Dependent product => dependent pool
            "JOIN cp_product_provided_products ppp2 ON ppp2.provided_product_uuid = pc.product_uuid " +
            "JOIN cp_pool pl2 on pl2.product_uuid = ppp2.product_uuid " +
            // Dependent pool => dependent entitlement
            "JOIN cp_entitlement e2 ON e2.pool_id = pl2.id " +
            "WHERE e1.consumer_id = e2.consumer_id " +
            "  AND e1.id != e2.id " +
            "  AND e2.dirty = false " +
            "  AND e1.id IN (:entitlement_ids)" +
            "  AND e2.id NOT IN (:entitlement_ids)";

        Query query = this.getEntityManager()
            .createNativeQuery(queryStr)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Entitlement.class)
            .addSynchronizedEntityClass(Pool.class)
            .addSynchronizedEntityClass(Product.class)
            .addSynchronizedEntityClass(Content.class)
            .addSynchronizedEntityClass(ProductContent.class)
            .addSynchronizedQuerySpace("cp_product_provided_products")
            .addSynchronizedQuerySpace("cp_content_required_products");

        Set<String> result = new HashSet<>();
        if (entitlementIds == null || !entitlementIds.iterator().hasNext()) {
            return result;
        }

        int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() / 2);
        for (List<String> block : Iterables.partition(entitlementIds, blockSize)) {
            result.addAll(query.setParameter("entitlement_ids", block).getResultList());
        }
        return result;
    }

    /**
     * Finds the dependent entitlements for the specified entitlements, matching
     * on derived provided products only. Dependent entitlements are those who's
     * content are being modified by the consumption of another entitlement.
     *
     * Note : This method does not support N-tier product hierarchy.
     *
     * @param entitlementIds the entitlements to match on.
     * @return the set of entitlement IDs for the matched modifier entitlements.
     */
    private Set<String> findDependentEntitlementsByDerivedProvidedProduct(Iterable<String> entitlementIds) {
        String queryStr = "SELECT DISTINCT e2.id " +
            // Required entitlement
            "FROM cp_entitlement e1 " +
            "JOIN cp_pool pl1 ON pl1.id = e1.pool_id " +
            "JOIN cp_products pp ON pp.uuid = pl1.product_uuid " +
            // Required entitlement => required pool => derived product => provided product
            "JOIN cp_product_provided_products ppp1 ON ppp1.product_uuid = pp.derived_product_uuid " +
            "JOIN cp_products p ON p.uuid = ppp1.provided_product_uuid " +
            // Required product => conditional content
            "JOIN cp_content_required_products crp ON crp.product_id = p.product_id " +
            // Conditional content => dependent product
            "JOIN cp_product_contents pc ON pc.content_uuid = crp.content_uuid " +
            // Dependent product => dependent pool
            "JOIN cp_product_provided_products ppp2 ON ppp2.provided_product_uuid = pc.product_uuid " +
            "JOIN cp_pool pl2 on pl2.product_uuid = ppp2.product_uuid " +
            // Dependent pool => dependent entitlement
            "JOIN cp_entitlement e2 ON e2.pool_id = pl2.id " +
            "WHERE e1.consumer_id = e2.consumer_id " +
            "  AND e1.id != e2.id " +
            "  AND e2.dirty = false " +
            "  AND e1.id IN (:entitlement_ids)" +
            "  AND e2.id NOT IN (:entitlement_ids)";

        Query query = this.getEntityManager()
            .createNativeQuery(queryStr)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(Entitlement.class)
            .addSynchronizedEntityClass(Pool.class)
            .addSynchronizedEntityClass(Product.class)
            .addSynchronizedEntityClass(Content.class)
            .addSynchronizedEntityClass(ProductContent.class)
            .addSynchronizedQuerySpace("cp_product_provided_products")
            .addSynchronizedQuerySpace("cp_content_required_products");

        Set<String> result = new HashSet<>();
        if (entitlementIds == null || !entitlementIds.iterator().hasNext()) {
            return result;
        }

        int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() / 2);
        for (List<String> block : Iterables.partition(entitlementIds, blockSize)) {
            result.addAll(query.setParameter("entitlement_ids", block).getResultList());
        }
        return result;
    }

    /**
     * Fetches dependent entitlement IDs for the specified collection of pools, belonging to the
     * given consumer. Dependent entitlements are those who's content are being modified by the
     * consumption of another entitlement.
     *
     * Note : This method does not support N-tier product hierarchy.
     *
     * @param consumer
     *  The consumer for which to find dependent entitlement IDs
     *
     * @param poolIds
     *  A collection of IDs of pools, which the fetched entitlements depend upon
     *
     * @return
     *  a collection of entitlement IDs for the entitlements dependent upon the provided pools
     *  belonging to the given consumer
     */
    public Collection<String> getDependentEntitlementIdsForPools(Consumer consumer,
        Iterable<String> poolIds) {
        Set<String> entitlementIds = new HashSet<>();

        if (consumer != null && poolIds != null && poolIds.iterator().hasNext()) {
            ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

            // Impl note:
            // We do this in direct SQL, as it lets us take a sane path from base to dependent
            // entitlements, rather than the lunacy that would be required with HQL, JPQL or
            // CriteriaBuilder.
            String querySql = "SELECT DISTINCT e.id " +
                // Required pool => required product
                "FROM cp_pool pl1 " +
                "JOIN cp_product_provided_products ppp1 on ppp1.product_uuid = pl1.product_uuid " +
                // Provided product => Product
                "JOIN cp_products p ON p.uuid = ppp1.provided_product_uuid " +
                // Required product => conditional content
                "JOIN cp_content_required_products crp ON crp.product_id = p.product_id " +
                // Conditional content => dependent product
                "JOIN cp_product_contents pc ON pc.content_uuid = crp.content_uuid " +
                // Dependent product => dependent pool
                "JOIN cp_product_provided_products ppp2 ON ppp2.provided_product_uuid = pc.product_uuid " +
                "JOIN cp_pool pl2 on pl2.product_uuid = ppp2.product_uuid " +
                // Dependent pool => dependent entitlement
                "JOIN cp_entitlement e ON e.pool_id = pl2.id " +
                "WHERE e.consumer_id = :consumer_id " +
                "  AND pl1.id IN (:pool_ids) ";

            Query query = this.getEntityManager()
                .createNativeQuery(querySql)
                .setParameter("consumer_id", consumer.getId())
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(Entitlement.class)
                .addSynchronizedEntityClass(Pool.class)
                .addSynchronizedEntityClass(Product.class)
                .addSynchronizedEntityClass(Content.class)
                .addSynchronizedEntityClass(ProductContent.class)
                .addSynchronizedQuerySpace("cp_product_provided_products")
                .addSynchronizedQuerySpace("cp_content_required_products");

            int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() - 1);
            for (List<String> block : Iterables.partition(poolIds, blockSize)) {
                query.setParameter("pool_ids", block);
                entitlementIds.addAll(query.getResultList());
            }

            // Need to check for dependent ents matching the derived provided products
            // if we are processing a distributor. We do this in a separate query to
            // avoid unnecessary query overhead when we are not dealing with a distributor.
            if (ctype.isManifest()) {
                querySql = "SELECT DISTINCT e.id " +
                    // Required pool => required product
                    "FROM cp_pool pl1 " +
                    "JOIN cp_products prod1 ON pl1.product_uuid = prod1.uuid " +
                    // product => derived provided products
                    "JOIN cp_product_provided_products ppp1 " +
                    "  ON ppp1.product_uuid = prod1.derived_product_uuid " +
                    // Provided product => Product
                    "JOIN cp_products prod2 ON prod2.uuid = ppp1.provided_product_uuid " +
                    // Required product => conditional content
                    "JOIN cp_content_required_products crp ON crp.product_id = prod2.product_id " +
                    // Conditional content => dependent product
                    "JOIN cp_product_contents pc ON pc.content_uuid = crp.content_uuid " +
                    // Dependent product => dependent pool
                    "JOIN cp_product_provided_products ppp2 " +
                    "  ON ppp2.provided_product_uuid = pc.product_uuid " +
                    "JOIN cp_pool pl2 on pl2.product_uuid = ppp2.product_uuid " +
                    // Dependent pool => dependent entitlement
                    "JOIN cp_entitlement e ON e.pool_id = pl2.id " +
                    "WHERE e.consumer_id = :consumer_id " +
                    "  AND pl1.id IN (:pool_ids) ";

                query = getEntityManager()
                    .createNativeQuery(querySql)
                    .setParameter("consumer_id", consumer.getId())
                    .unwrap(NativeQuery.class)
                    .addSynchronizedEntityClass(Entitlement.class)
                    .addSynchronizedEntityClass(Pool.class)
                    .addSynchronizedEntityClass(Product.class)
                    .addSynchronizedEntityClass(Content.class)
                    .addSynchronizedEntityClass(ProductContent.class)
                    .addSynchronizedQuerySpace("cp_product_provided_products")
                    .addSynchronizedQuerySpace("cp_content_required_products");

                for (List<String> block : Iterables.partition(poolIds, blockSize)) {
                    query.setParameter("pool_ids", block);
                    entitlementIds.addAll(query.getResultList());
                }
            }
        }

        return entitlementIds;
    }

    private Predicate ilike(CriteriaBuilder cb, Expression<String> expression, String attrValue) {
        return cb.like(
            cb.lower(
                expression
            ), cb.lower(
                cb.literal("%" + attrValue + "%")
            ), '!'
        );
    }

    private Predicate[] toArray(List<Predicate> predicates) {
        Predicate[] array = new Predicate[predicates.size()];
        return predicates.toArray(array);
    }

    /**
     * Returns a mapping of entitlement ID to consumer IDs for the given collection of consumer IDs.
     * If none of the consumers have entitlements, this method returns an empty map.
     *
     * @param consumerIds
     *  a collection of consumer IDs for which to fetch an entitlement-consumer mapping
     *
     * @return
     *  the entitlement-consumer map for the given consumers
     */
    public Map<String, String> getEntitlementConsumerIdMap(Iterable<String> consumerIds) {
        Map<String, String> entConsumerIdMap = new HashMap<>();

        if (consumerIds != null) {
            String jpql = "SELECT ent.id, consumer.id FROM Entitlement ent JOIN ent.consumer consumer " +
                "WHERE consumer.id IN (:consumer_ids)";

            Query query = this.getEntityManager()
                .createQuery(jpql);

            for (List<String> block : this.partition(consumerIds)) {
                List<Object[]> rows = query.setParameter("consumer_ids", block)
                    .getResultList();

                rows.forEach(row -> entConsumerIdMap.put((String) row[0], (String) row[1]));
            }
        }

        return entConsumerIdMap;
    }

    /**
     * Returns a mapping of entitlement ID to content IDs attached to the base product and provided
     * products for the pool of the entitlement. Entitlements which do not have any content will not
     * have an entry in the map. If none of the specified entitlements exist, or none of the
     * entitlements contain any content, this method returns an empty map.
     *
     * @param entitlementIds
     *  a collection of entitlements for which to fetch an entitlement-content mapping
     *
     * @return
     *  the entitlement-content map for the given entitlements
     */
    public Map<String, Set<String>> getEntitlementContentIdMap(Iterable<String> entitlementIds) {
        Map<String, Set<String>> entContentIdMap = new HashMap<>();

        if (entitlementIds != null) {
            String sql = "SELECT ent.id, content.content_id FROM cp_entitlement ent " +
                "    JOIN cp_pool pool ON pool.id = ent.pool_id " +
                "    JOIN cp_product_contents pc ON pc.product_uuid = pool.product_uuid " +
                "    JOIN cp_contents content ON content.uuid = pc.content_uuid " +
                "    WHERE ent.id IN (:ent_ids_1) " +
                "UNION " +
                "SELECT ent.id, content.content_id FROM cp_entitlement ent " +
                "    JOIN cp_pool pool ON pool.id = ent.pool_id " +
                "    JOIN cp_product_provided_products ppp ON ppp.product_uuid = pool.product_uuid " +
                "    JOIN cp_product_contents pc ON pc.product_uuid = ppp.provided_product_uuid " +
                "    JOIN cp_contents content ON content.uuid = pc.content_uuid " +
                "    WHERE ent.id IN (:ent_ids_2) ";

            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(Entitlement.class)
                .addSynchronizedEntityClass(Pool.class)
                .addSynchronizedEntityClass(Product.class)
                .addSynchronizedEntityClass(Content.class)
                .addSynchronizedEntityClass(ProductContent.class)
                .addSynchronizedQuerySpace("cp_product_provided_products");

            // Since we're using a union and slaping down the ID block twice, we have to halve our
            // block size to not risk running over the parameter limit
            int blockSize = this.getInBlockSize() / 2;
            for (List<String> block : this.partition(entitlementIds, blockSize)) {
                List<Object[]> rows = query.setParameter("ent_ids_1", block)
                    .setParameter("ent_ids_2", block)
                    .getResultList();

                for (Object[] row : rows) {
                    entContentIdMap.computeIfAbsent((String) row[0], (key) -> new HashSet<>())
                        .add((String) row[1]);
                }
            }
        }

        return entContentIdMap;
    }
}
