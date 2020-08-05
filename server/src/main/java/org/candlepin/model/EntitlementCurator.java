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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
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
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
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
 * EntitlementCurator
 */
@Singleton
public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    private static Logger log = LoggerFactory.getLogger(EntitlementCurator.class);

    private CandlepinQueryFactory cpQueryFactory;
    private OwnerProductCurator ownerProductCurator;
    private ProductCurator productCurator;
    private ConsumerTypeCurator consumerTypeCurator;

    /**
     * default ctor
     */
    @Inject
    public EntitlementCurator(OwnerProductCurator ownerProductCurator, ProductCurator productCurator,
        ConsumerTypeCurator consumerTypeCurator, CandlepinQueryFactory cpQueryFactory) {
        super(Entitlement.class);

        this.cpQueryFactory = cpQueryFactory;
        this.ownerProductCurator = ownerProductCurator;
        this.productCurator = productCurator;
        this.consumerTypeCurator = consumerTypeCurator;
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
        CriteriaQuery<Entitlement> query,
        EntitlementFilterBuilder filterBuilder) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Join<Entitlement, Pool> pool = root.join("pool");
        Join<Entitlement, Product> product = pool.join("product");

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.greaterThanOrEqualTo(pool.get("endDate"), new Date()));

        if (filterBuilder != null) {
            Collection<String> values = filterBuilder.getIdFilters();

            if (values != null && !values.isEmpty()) {
                predicates.add(cb.in(pool.get("id")).value(values));
            }

            values = filterBuilder.getProductIdFilter();

            if (values != null && !values.isEmpty()) {
                Join<Object, Object> providedProducts = pool.join("providedProducts", JoinType.LEFT);

                predicates.add(cb.or(
                    cb.in(product.get("id")).value(values),
                    cb.in(providedProducts.get("id")).value(values)));
            }

            // Subscription ID filter
            String value = filterBuilder.getSubscriptionIdFilter();

            if (value != null && !value.isEmpty()) {
                Join<Object, Object> sourceSubscription = pool.join("sourceSubscription");
                predicates.add(cb.equal(sourceSubscription.get("subscriptionId"), value));
            }

            // Matches stuff
            values = filterBuilder.getMatchesFilters();
            if (values != null && !values.isEmpty()) {
                Join<Object, Object> providedProducts = pool.join("providedProducts", JoinType.LEFT);
                Join<Object, Object> productContent = providedProducts.join("productContent", JoinType.LEFT);
                Join<Object, Object> content = productContent.join("content", JoinType.LEFT);

                for (String matches : values) {
                    String sanitized = this.sanitizeMatchesFilter(matches);

                    Predicate matchesDisjunction = cb.or(
                        ilike(cb, pool.get("contractNumber"), sanitized),
                        ilike(cb, pool.get("orderNumber"), sanitized),
                        ilike(cb, product.get("id"), sanitized),
                        ilike(cb, product.get("name"), sanitized),
                        ilike(cb, providedProducts.get("id"), sanitized),
                        ilike(cb, providedProducts.get("name"), sanitized),
                        ilike(cb, content.get("name"), sanitized),
                        ilike(cb, content.get("label"), sanitized),
                        this.addProductAttributeFilterSubquery(
                            query,
                            pool.get("id"),
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
                            query, attrib, values, pool, product));
                    }
                }
            }
        }

        return predicates;
    }

    private Predicate addAttributeFilterSubquery(
        CriteriaQuery<Entitlement> query, String key, Collection<String> values,
        Join<Entitlement, Pool> pool, Join<Entitlement, Product> product) {

        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        return cb.or(
            poolAttributeFilterSubquery(query, key, values, pool),
            productAttributeFilterSubquery(query, key, values, product)
        );
    }

    private Predicate poolAttributeFilterSubquery(
        CriteriaQuery<Entitlement> query,
        String key, Collection<String> values, Join<Entitlement, Pool> parentPool) {

        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Subquery<String> poolAttrSubquery = query.subquery(String.class);
        Root<Pool> pool = poolAttrSubquery.from(Pool.class);
        poolAttrSubquery.select(pool.get("id"));
        MapJoin<Product, String, String> attributes = pool.joinMap("attributes");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(attributes.key(), key));
        Join<Entitlement, Pool> correlatedPool = poolAttrSubquery.correlate(parentPool);
        predicates.add(cb.equal(pool.get("id"), correlatedPool.get("id")));

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
        CriteriaQuery<Entitlement> query,
        String key, Collection<String> values, Join<Entitlement, Product> parentProduct) {

        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Subquery<String> prodAttrSubquery = query.subquery(String.class);
        Root<Product> product = prodAttrSubquery.from(Product.class);
        prodAttrSubquery.select(product.get("id"));
        MapJoin<Product, String, String> attributes = product.joinMap("attributes");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(attributes.key(), key));

        Join<Entitlement, Product> correlatedProduct = prodAttrSubquery.correlate(parentProduct);
        predicates.add(cb.equal(product.get("uuid"), correlatedProduct.get("uuid")));
        predicates.add(subSubQuery(query.subquery(String.class), product.get("id"), key));

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
        CriteriaQuery<Entitlement> query,
        Path<Object> poolId,
        Join<Entitlement, Product> parentProduct,
        String key, Collection<String> values) {
        // Find all pools which have the given attribute (and values) on a product, unless the pool
        // defines that same attribute
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Subquery<Product> prodAttributeSubquery = query.subquery(Product.class);
        Root<Product> product = prodAttributeSubquery.from(Product.class);

        prodAttributeSubquery.select(product.get("uuid"));

        List<Predicate> predicates = new ArrayList<>();

        MapJoin<Product, String, String> attributes = product.joinMap("attributes");
        Join<Entitlement, Product> correlatedProduct = prodAttributeSubquery.correlate(parentProduct);
        predicates.add(cb.equal(product.get("uuid"), correlatedProduct.get("uuid")));
        predicates.add(cb.equal(attributes.key(), key));
        predicates.add(subSubQuery(prodAttributeSubquery.subquery(String.class), poolId, key));

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

    private Predicate subSubQuery(Subquery<String> subquery, Path<Object> poolId, String key) {
        CriteriaBuilder cb = this.entityManager.get().getCriteriaBuilder();
        Root<PoolAttribute> poolAttribute = subquery.from(PoolAttribute.class);

        subquery.select(poolAttribute.get("poolId"));
        subquery.where(cb.and(cb.equal(poolAttribute.get("poolId"), poolId),
            ilike(cb, poolAttribute.get("name"), key)));

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
        criteria.add(cb.equal(root.get("consumer"), consumer));

        query.distinct(true);
        query.where(toArray(criteria));

        return listByCriteria(query);
    }

    public List<Entitlement> listByConsumerAndPoolId(Consumer consumer, String poolId) {
        Criteria query = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("pool.id", poolId));
        query.add(Restrictions.eq("consumer", consumer));
        return listByCriteria(query);
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

        // No need to add filters when matching by product.
        Product p = this.ownerProductCurator.getProductById(ownerId, productId);
        if (p == null) {
            throw new BadRequestException(i18nProvider.get().tr(
                "Product with ID \"{0}\" could not be found.", productId));
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
        return listByCriteria(root, query, pageRequest);
    }

    public CandlepinQuery<Entitlement> listByOwner(Owner owner) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("owner", owner));

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
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
    public CandlepinQuery<Entitlement> listByEnvironment(Environment environment) {
        return this.listByEnvironment(environment != null ? environment.getId() : null);
    }

    /**
     * Fetches a the entitlements used by consumers in the specified environment.
     *
     * @param environmentId
     *  The ID of the environment for which to fetch entitlements
     *
     * @return
     *  A CandlepinQuery to iterate over the entitlements in the specified environment
     */
    public CandlepinQuery<Entitlement> listByEnvironment(String environmentId) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .createCriteria("consumer")
            .add(Restrictions.eq("environmentId", environmentId));

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    /**
     * List entitlements for a consumer which are valid for a specific date.
     *
     * @param consumer Consumer to list entitlements for.
     * @param activeOn The date we want to see entitlements which are active on.
     * @return List of entitlements.
     */
    public CandlepinQuery<Entitlement> listByConsumerAndDate(Consumer consumer, Date activeOn) {

        /*
         * Essentially the opposite of the above query which searches for entitlement
         * overlap with a "modifying" entitlement being granted. This query is used to
         * search for modifying entitlements which overlap with a regular entitlement
         * being granted. As such the logic is basically reversed.
         *
         */
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createCriteria("pool")
            .add(Restrictions.le("startDate", activeOn))
            .add(Restrictions.ge("endDate", activeOn));

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
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

        return Collections.<Entitlement>emptyList();
    }

    /**
     * List all entitled product IDs from entitlements which overlap the given date range.
     *
     * i.e. given start date must be within the entitlements start/end dates, or
     * the given end date must be within the entitlements start/end dates,
     * or the given start date must be before the entitlement *and* the given end date
     * must be after entitlement. (i.e. we are looking for *any* overlap)
     *
     * also assumes the consumer is about to create an entitlement with the pool provided
     * as an argument, so includes the products from that pool
     *
     * @param consumer
     * @param pool
     * @return entitled product IDs
     */
    public Set<String> listEntitledProductIds(Consumer consumer, Pool pool) {
        // FIXME Either address the TODO below, or move this method out of the curator.
        // TODO: Swap this to a db query if we're worried about memory:
        Set<String> entitledProductIds = new HashSet<>();
        List<Pool> pools = new LinkedList<>();
        for (Entitlement e : consumer.getEntitlements()) {
            pools.add(e.getPool());
        }

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        pools.add(pool);
        for (Pool p : pools) {
            if (!poolOverlapsRange(p, pool.getStartDate(), pool.getEndDate())) {
                // Skip this entitlement:
                continue;
            }

            entitledProductIds.add(p.getProduct().getId());
            for (Product pp : productCurator.getPoolProvidedProductsCached(p)) {
                entitledProductIds.add(pp.getId());
            }

            // A distributor should technically be entitled to derived products and
            // will need to be able to sync content downstream.
            if (ctype.isManifest() && p.getDerivedProduct() != null) {
                entitledProductIds.add(p.getDerivedProduct().getId());

                for (Product dpp : productCurator.getPoolDerivedProvidedProductsCached(p)) {
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
     * @deprecated
     *  This method is a utility method for revokeEntitlements and, as it has no communication with
     *  the database, does not belong in this curator
     *
     * @return a map of consumers to their entitlements
     */
    @Deprecated
    public Map<Consumer, List<Entitlement>> getDistinctConsumers(List<Entitlement> entsToRevoke) {
        Map<Consumer, List<Entitlement>> result = new HashMap<>();
        for (Entitlement ent : entsToRevoke) {
            List<Entitlement> ents = result.get(ent.getConsumer());
            if (ents == null) {
                ents = new ArrayList<>();
                result.put(ent.getConsumer(), ents);
            }
            ents.add(ent);
        }
        return result;
    }

    @Transactional
    private Page<List<Entitlement>> listByProduct(
        AbstractHibernateObject object, String objectType, String productId, PageRequest pageRequest) {

        CriteriaBuilder builder = this.entityManager.get().getCriteriaBuilder();

        CriteriaQuery<Entitlement> entitlementQuery = builder.createQuery(Entitlement.class);
        Root<Entitlement> entitlement = entitlementQuery.from(Entitlement.class);

        Join<Entitlement, Pool> pool = entitlement.join("pool");
        Join<Pool, Product> product = pool.join("product");
        Join<Pool, Product> providedProducts = pool.join("providedProducts", JoinType.LEFT);

        entitlementQuery.where(builder.and(
            builder.equal(entitlement.get(objectType), object),
            builder.greaterThanOrEqualTo(pool.get("endDate"), new Date()),
            builder.or(builder.equal(product.get("id"), productId),
            builder.equal(providedProducts.get("id"), productId))));

        return listByCriteria(entitlement, entitlementQuery, pageRequest);
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
            entity.getConsumer().getEntitlements().remove(entity);
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
                entitlement.getConsumer().getEntitlements().remove(entitlement);
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
                entitlement.getConsumer().getEntitlements().remove(entitlement);
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

    @Transactional
    public Entitlement findByCertificateSerial(Long serial) {
        return (Entitlement) currentSession().createCriteria(Entitlement.class)
            .createCriteria("certificates")
                .add(Restrictions.eq("serial.id", serial))
            .uniqueResult();
    }

    @Transactional
    public Entitlement replicate(Entitlement ent) {
        for (EntitlementCertificate ec : ent.getCertificates()) {
            ec.setEntitlement(ent);
            CertificateSerial cs = ec.getSerial();
            if (cs != null) {
                this.currentSession().replicate(cs, ReplicationMode.EXCEPTION);
            }
        }
        this.currentSession().replicate(ent, ReplicationMode.EXCEPTION);

        return ent;
    }

    /**
     * Find the entitlements for the given consumer that are part of the specified stack.
     *
     * @param consumer the consumer
     * @param stackId the ID of the stack
     * @return the list of entitlements for the consumer that are in the stack.
     */
    @SuppressWarnings("unchecked")
    public CandlepinQuery<Entitlement> findByStackId(Consumer consumer, String stackId) {
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
    @SuppressWarnings("unchecked")
    public CandlepinQuery<Entitlement> findByStackIds(Consumer consumer, Collection stackIds) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.product", "product")
            .createAlias("product.attributes", "attrs")
            .add(Restrictions.eq("attrs.indices", Product.Attributes.STACKING_ID))
            .add(CPRestrictions.in("attrs.elements", stackIds))
            .add(Restrictions.isNull("ent_pool.sourceEntitlement"))
            .createAlias("ent_pool.sourceStack", "ss", org.hibernate.sql.JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.isNull("ss.id"));

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Entitlement> findByPoolAttribute(Consumer consumer, String attributeName,
        String value) {

        DetachedCriteria criteria = DetachedCriteria.forClass(Entitlement.class)
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.attributes", "attrs")
            .add(Restrictions.eq("attrs.indices", attributeName))
            .add(Restrictions.eq("attrs.elements", value));

        if (consumer != null) {
            criteria.add(Restrictions.eq("consumer", consumer));
        }

        return this.cpQueryFactory.<Entitlement>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Entitlement> findByPoolAttribute(String attributeName, String value) {
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
        Criteria activeNowQuery = currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("consumer", consumer))
            .createAlias("pool", "ent_pool")
            .createAlias("ent_pool.product", "product")
            .createAlias("product.attributes", "attrs")
            .add(Restrictions.le("ent_pool.startDate", currentDate))
            .add(Restrictions.ge("ent_pool.endDate", currentDate))
            .add(Restrictions.eq("attrs.indices", Product.Attributes.STACKING_ID))
            .add(Restrictions.eq("attrs.elements", stackId).ignoreCase())
            .add(Restrictions.isNull("ent_pool.sourceEntitlement"))
            .createAlias("ent_pool.sourceSubscription", "sourceSub")
            .add(Restrictions.isNotNull("sourceSub.id"))
            .addOrder(Order.asc("created")) // eldest entitlement
            .setMaxResults(1);

        return (Entitlement) activeNowQuery.uniqueResult();
    }

    /**
     * Finds the dependent entitlements for the specified entitlements, matching
     * on provided products only. Dependent entitlements are those who's
     * content are being modified by the consumption of another entitlement.
     *
     * @param entitlementIds the entitlements to match on.
     * @return the set of entitlement IDs for the matched modifier entitlements.
     */
    private Set<String> findDependentEntitlementsByProvidedProduct(Iterable<String> entitlementIds) {
        String queryStr = "SELECT DISTINCT e2.id " +
            // Required entitlement
            "FROM cp_entitlement e1 " +
            // Required entitlement => required pool
            "JOIN cp2_pool_provided_products ppp1 ON ppp1.pool_id = e1.pool_id " +
            // Required pool => required product
            "JOIN cp2_products p ON p.uuid = ppp1.product_uuid " +
            // Required product => conditional content
            "JOIN cp2_content_modified_products cmp ON cmp.element = p.product_id " +
            // Conditional content => dependent product
            "JOIN cp2_product_content pc ON pc.content_uuid = cmp.content_uuid " +
            // Dependent product => dependent pool
            "JOIN cp2_pool_provided_products ppp2 ON ppp2.product_uuid = pc.product_uuid " +
            // Dependent pool => dependent entitlement
            "JOIN cp_entitlement e2 ON e2.pool_id = ppp2.pool_id " +
            "WHERE e1.consumer_id = e2.consumer_id " +
            "  AND e1.id != e2.id " +
            "  AND e2.dirty = false " +
            "  AND e1.id IN (:entitlement_ids)" +
            "  AND e2.id NOT IN (:entitlement_ids)";

        Query query = getEntityManager().createNativeQuery(queryStr);

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
     * @param entitlementIds the entitlements to match on.
     * @return the set of entitlement IDs for the matched modifier entitlements.
     */
    private Set<String> findDependentEntitlementsByDerivedProvidedProduct(Iterable<String> entitlementIds) {
        String queryStr = "SELECT DISTINCT e2.id " +
            // Required entitlement
            "FROM cp_entitlement e1 " +
            // Required entitlement => required pool
            "JOIN cp2_pool_derprov_products ppp1 ON ppp1.pool_id = e1.pool_id " +
            // Required pool => required product
            "JOIN cp2_products p ON p.uuid = ppp1.product_uuid " +
            // Required product => conditional content
            "JOIN cp2_content_modified_products cmp ON cmp.element = p.product_id " +
            // Conditional content => dependent product
            "JOIN cp2_product_content pc ON pc.content_uuid = cmp.content_uuid " +
            // Dependent product => dependent pool
            "JOIN cp2_pool_provided_products ppp2 ON ppp2.product_uuid = pc.product_uuid " +
            // Dependent pool => dependent entitlement
            "JOIN cp_entitlement e2 ON e2.pool_id = ppp2.pool_id " +
            "WHERE e1.consumer_id = e2.consumer_id " +
            "  AND e1.id != e2.id " +
            "  AND e2.dirty = false " +
            "  AND e1.id IN (:entitlement_ids)" +
            "  AND e2.id NOT IN (:entitlement_ids)";
        Query query = getEntityManager().createNativeQuery(queryStr);

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
                // Required pool
                "FROM cp2_pool_provided_products ppp1 " +
                // Required pool => required product
                "JOIN cp2_products p ON p.uuid = ppp1.product_uuid " +
                // Required product => conditional content
                "JOIN cp2_content_modified_products cmp ON cmp.element = p.product_id " +
                // Conditional content => dependent product
                "JOIN cp2_product_content pc ON pc.content_uuid = cmp.content_uuid " +
                // Dependent product => dependent pool
                "JOIN cp2_pool_provided_products ppp2 ON ppp2.product_uuid = pc.product_uuid " +
                // Dependent pool => dependent entitlement
                "JOIN cp_entitlement e ON e.pool_id = ppp2.pool_id " +
                "WHERE e.consumer_id = :consumer_id " +
                "  AND ppp1.pool_id IN (:pool_ids) ";


            int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() - 1);
            Query query = getEntityManager().createNativeQuery(querySql)
                .setParameter("consumer_id", consumer.getId());

            for (List<String> block : Iterables.partition(poolIds, blockSize)) {
                query.setParameter("pool_ids", block);
                entitlementIds.addAll(query.getResultList());
            }

            // Need to check for dependent ents matching the derived provided products
            // if we are processing a distributor. We do this in a separate query to
            // avoid unnecessary query overhead when we are not dealing with a distributor.
            if (ctype.isManifest()) {
                querySql = "SELECT DISTINCT e.id " +
                    // Required pool
                    "FROM cp2_pool_derprov_products ppp1 " +
                    // Required pool => required product
                    "JOIN cp2_products p ON p.uuid = ppp1.product_uuid " +
                    // Required product => conditional content
                    "JOIN cp2_content_modified_products cmp ON cmp.element = p.product_id " +
                    // Conditional content => dependent product
                    "JOIN cp2_product_content pc ON pc.content_uuid = cmp.content_uuid " +
                    // Dependent product => dependent pool
                    "JOIN cp2_pool_provided_products ppp2 ON ppp2.product_uuid = pc.product_uuid " +
                    // Dependent pool => dependent entitlement
                    "JOIN cp_entitlement e ON e.pool_id = ppp2.pool_id " +
                    "WHERE e.consumer_id = :consumer_id " +
                    "  AND ppp1.pool_id IN (:pool_ids) ";

                query = getEntityManager().createNativeQuery(querySql)
                    .setParameter("consumer_id", consumer.getId());

                for (List<String> block : Iterables.partition(poolIds, blockSize)) {
                    query.setParameter("pool_ids", block);
                    entitlementIds.addAll(query.getResultList());
                }
            }
        }

        return entitlementIds;
    }

    private Predicate ilike(CriteriaBuilder cb, Expression<String> x, String attrValue) {
        return cb.like(
            cb.lower(
                x
            ), cb.lower(
                cb.literal("%" + attrValue + "%")
            ), '!'
        );
    }

    private Predicate[] toArray(List<Predicate> predicates) {
        Predicate[] array = new Predicate[predicates.size()];
        return predicates.toArray(array);
    }

}
