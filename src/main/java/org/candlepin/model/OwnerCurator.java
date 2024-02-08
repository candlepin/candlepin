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

import org.candlepin.controller.OwnerContentAccess;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * OwnerCurator
 */
@Singleton
public class OwnerCurator extends AbstractHibernateCurator<Owner> {

    @Inject
    private CandlepinQueryFactory cpQueryFactory;
    private static final Logger log = LoggerFactory.getLogger(OwnerCurator.class);

    public OwnerCurator() {
        super(Owner.class);
    }

    /**
     * Fetches the Owner for the specified ownerId. If the ownerId is null or owner was not found, this
     * method throws an exception.
     *
     * @param ownerId
     *  The ownerId for which to fetch a Owner object
     *
     * @throws IllegalArgumentException
     *  if ownerId is null
     *
     * @throws IllegalStateException
     *  if the owner was not found for the given id
     *
     * @return
     *  An Owner instance for the specified ownerId
     */
    public Owner findOwnerById(String ownerId) {
        if (StringUtils.isEmpty(ownerId)) {
            throw new IllegalArgumentException("ownerId is null");
        }

        Owner owner = this.get(ownerId);

        if (owner == null) {
            throw new IllegalStateException("owner not found for the id: " + ownerId);
        }

        return owner;
    }

    @Transactional
    public Owner replicate(Owner owner) {
        this.currentSession().replicate(owner, ReplicationMode.EXCEPTION);

        return owner;
    }

    @Transactional
    @Override
    public Owner create(Owner entity) {
        return super.create(entity);
    }

    /**
     * Fetches an owner by key securely by checking principal permissions.
     *
     * @param key owner's unique key to fetch.
     * @return the owner whose key matches the one given.
     */
    @Transactional
    public Owner getByKeySecure(String key) {
        return (Owner) createSecureCriteria()
            .add(Restrictions.eq("key", key))
            .uniqueResult();
    }

    /**
     * Fetches and locks the owner by the owner key. If a matching owner could not be found, this
     * method returns null.
     *
     * @param key
     *  the key to use to lock and load the owner
     *
     * @return
     *  the locked owner, or null if a matching owner could not be found
     */
    public Owner lockAndLoadByKey(String key) {
        if (key != null && !key.isEmpty()) {
            return this.currentSession()
                .bySimpleNaturalId(Owner.class)
                .with(new LockOptions(LockMode.PESSIMISTIC_WRITE))
                .load(key);
        }

        return null;
    }

    /**
     * Fetches an owner by natural id (key). This method is non-secure.
     *
     * @param key owner's unique key to fetch.
     * @return the owner whose key matches the one given.
     */
    @Transactional
    public Owner getByKey(String key) {
        return this.currentSession().bySimpleNaturalId(Owner.class).load(key);
    }

    @Transactional
    public List<Owner> getByKeys(Collection<String> keys) {
        String jpql = "SELECT o FROM Owner o WHERE o.key in (:keys)";

        return this.getEntityManager()
            .createQuery(jpql, Owner.class)
            .setParameter("keys", keys)
            .getResultList();
    }

    public Owner getByUpstreamUuid(String upstreamUuid) {
        return (Owner) createSecureCriteria()
            .createCriteria("upstreamConsumer")
            .add(Restrictions.eq("uuid", upstreamUuid))
            .uniqueResult();
    }

    /**
     * Retrieves list of owners which have pools referencing one or more of the specified product
     * IDs. If no owners are associated with the given products, this method returns an empty list.
     *
     * @param productIds
     *  The product IDs for which to retrieve owners
     *
     * @return
     *  list of owners associated with the specified products
     */
    @SuppressWarnings("checkstyle:indentation")
    public Set<Owner> getOwnersWithProducts(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptySet();
        }

        CriteriaBuilder criteriaBuilder = this.entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Owner> query = criteriaBuilder.createQuery(Owner.class);
        Root<Owner> owner = query.from(Owner.class);
        query.distinct(true);
        query.select(owner);

        Join<Owner, Pool> pool = owner.join(Owner_.pools);
        Join<Pool, Product> product = pool.join(Pool_.product, JoinType.LEFT);
        Join<Product, Product> providedProducts = product.join(Product_.providedProducts, JoinType.LEFT);
        Join<Product, Product> derivedProducts = product.join(Product_.derivedProduct, JoinType.LEFT);
        Join<Product, Product> derivedProvidedProducts =
            derivedProducts.join(Product_.providedProducts, JoinType.LEFT);

        Expression<String> productExpr = product.get(Product_.id);
        Expression<String> providedProductsExpr = providedProducts.get(Product_.id);
        Expression<String> derivedProductsExpr = derivedProducts.get(Product_.id);
        Expression<String> derivedPPExpr = derivedProvidedProducts.get(Product_.id);

        // Block size reduced by 4 times to avoid exceeding the parameters limit as there
        // are multiple IN clauses in the query below.
        int blockSize = Math.min(this.getInBlockSize(), this.getQueryParameterLimit() / 4);
        Set<Owner> owners = new HashSet<>();
        EntityManager entityManager = this.entityManager.get();

        for (List<String> block : Iterables.partition(productIds, blockSize)) {
            Predicate predicate = criteriaBuilder.or(productExpr.in(block), providedProductsExpr.in(block),
                derivedProductsExpr.in(block), derivedPPExpr.in(block));
            query.where(predicate);
            owners.addAll(entityManager.createQuery(query).getResultList());
        }

        return owners;
    }

    public List<String> getConsumerIds(Owner owner) {
        return this.getConsumerIds(owner.getId()).list();
    }

    public CandlepinQuery<String> getConsumerIds(String ownerId) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Consumer.class)
            .add(Restrictions.eq("ownerId", ownerId))
            .setProjection(Property.forName("id"));

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<String> getConsumerUuids(Owner owner) {
        return this.getConsumerUuids(owner.getId());
    }

    public CandlepinQuery<String> getConsumerUuids(String ownerId) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Consumer.class)
            .add(Restrictions.eq("ownerId", ownerId))
            .setProjection(Property.forName("uuid"));

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("checkstyle:indentation")
    public OwnerContentAccess getOwnerContentAccess(String ownerKey) {
        TypedQuery<OwnerContentAccess> query = entityManager.get().createQuery(
            "SELECT new org.candlepin.controller.OwnerContentAccess(" +
                "o.contentAccessMode, o.contentAccessModeList)" +
                " FROM Owner o WHERE o.key = :ownerKey", OwnerContentAccess.class);
        log.info("Retrieving content access data to owner: {}.", ownerKey);
        try {
            return query
                .setParameter("ownerKey", ownerKey)
                .getSingleResult();
        }
        catch (NoResultException e) {
            throw new OwnerNotFoundException(ownerKey, e);
        }
    }

    /**
     * Checks if the owner exists in the database.
     *
     * @param ownerKey key of the owner to be checked
     * @return true if the owner exists
     */
    public boolean existsByKey(String ownerKey) {
        return this.getEntityManager()
            .createQuery("SELECT COUNT(o.id) FROM Owner o WHERE o.key = :owner_key", Long.class)
            .setParameter("owner_key", ownerKey)
            .getSingleResult() > 0;
    }

    /**
     * Sets the lastContentUpdate field to the current time for any owners with one or more pools
     * referencing any of the given products.
     *
     * @param productUuids
     *  A collection of product UUIDs representing updated products
     *
     * @return
     *  the number of rows updated by this method
     */
    public int setLastContentUpdateForOwnersWithProducts(Collection<String> productUuids) {
        if (productUuids == null || productUuids.isEmpty()) {
            return 0;
        }

        // Update-joins don't exist in JPA at the time of writing, so we'll need to first get our
        // list of orgs from the products, then update them accordingly.

        // We're doing an update, which in some configurations may give us an implicit table or
        // row lock. Order these so we try to perform the update in slightly safer blocks.
        SortedSet<String> ownerIds = new TreeSet<>();

        String ownerLookupJpql = "SELECT owner.id FROM Pool pool JOIN pool.owner owner " +
            "WHERE pool.product.uuid IN (:product_uuids)";

        String ownerUpdateJpql = "UPDATE Owner SET lastContentUpdate = CURRENT_TIMESTAMP " +
            "WHERE id IN (:owner_ids)";

        TypedQuery<String> ownerLookupQuery = this.getEntityManager()
            .createQuery(ownerLookupJpql, String.class);

        for (List<String> block : this.partition(productUuids)) {
            List<String> result = ownerLookupQuery.setParameter("product_uuids", block)
                .getResultList();

            ownerIds.addAll(result);
        }

        // Perform our update now that we have our set of owner IDs
        int count = 0;
        if (!ownerIds.isEmpty()) {
            Query updateQuery = this.getEntityManager()
                .createQuery(ownerUpdateJpql);

            for (List<String> block : this.partition(ownerIds)) {
                count += updateQuery.setParameter("owner_ids", block)
                    .executeUpdate();
            }
        }

        return count;
    }

}
