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

import org.candlepin.model.Pool.PoolType;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.hibernate.Session;
import org.hibernate.annotations.QueryHints;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;



/**
 * The OwnerProductCurator provides functionality for managing the mapping between owners and
 * products.
 */
@Singleton
public class OwnerProductCurator extends AbstractHibernateCurator<OwnerProduct> {
    private static Logger log = LoggerFactory.getLogger(OwnerProductCurator.class);

    /**
     * Default constructor
     */
    public OwnerProductCurator() {
        super(OwnerProduct.class);
    }

    /**
     * Fetches the OwnerProduct instance for the given owner ID and product ID. If the specified
     * product does not exist in the given organization, or either the organization or product do
     * not exist, this method returns null.
     *
     * @param ownerId
     *  the ID of the organization
     *
     * @param productId
     *  the Red Hat ID of the product
     *
     * @return
     *  the OwnerProduct instance mapping the given owner to the given product, or null if no such
     *  mapping exists
     */
    public OwnerProduct getOwnerProduct(String ownerId, String productId) {
        String jpql = "SELECT op FROM OwnerProduct op " +
            "WHERE op.ownerId = :owner_id AND op.productId = :product_id";

        try {
            return this.getEntityManager()
                .createQuery(jpql, OwnerProduct.class)
                .setParameter("owner_id", ownerId)
                .setParameter("product_id", productId)
                .getSingleResult();
        }
        catch (NoResultException e) {
            // Intentionally left empty
        }

        return null;
    }

    /**
     * Determines if a productId exists for a given owner while attempting to gain a lock on it
     *
     * @param owner
     *  The organization in which to lock the product
     *
     * @param productId
     *  The ID of the product
     *
     * @param lockMode
     *  The lock mode to use
     *
     * @return
     *  true if the product exists and the lock was obtained; false otherwise
     */
    public boolean lockOwnerProduct(Owner owner, String productId, LockModeType lockMode) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        if (!(lockMode == LockModeType.PESSIMISTIC_READ || lockMode == LockModeType.PESSIMISTIC_WRITE)) {
            throw new IllegalArgumentException("Unsupported lock mode: " + lockMode);
        }

        String jpql = "SELECT op FROM OwnerProduct op " +
            "WHERE op.ownerId = :owner_id AND op.productId = :product_id";

        try {
            this.getEntityManager()
                .createQuery(jpql, OwnerProduct.class)
                .setParameter("owner_id", owner.getId())
                .setParameter("product_id", productId)
                .setLockMode(lockMode)
                .getSingleResult();

            return true;
        }
        catch (NoResultException nre) {
            return false;
        }
    }

    public Product getProductById(Owner owner, String productId) {
        return this.getProductById(owner.getId(), productId);
    }

    @Transactional
    public Product getProductById(String ownerId, String productId) {
        return (Product) this.createSecureCriteria()
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .setProjection(Projections.property("product"))
            .add(Restrictions.eq("owner.id", ownerId))
            .add(Restrictions.eq("product.id", productId))
            .uniqueResult();
    }

    @Transactional
    public Product getProductByIdUsingOwnerKey(String ownerKey, String productId) {
        return (Product) this.createSecureCriteria()
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .setProjection(Projections.property("product"))
            .add(Restrictions.eq("owner.key", ownerKey))
            .add(Restrictions.eq("product.id", productId))
            .uniqueResult();
    }

    public CandlepinQuery<Owner> getOwnersByProduct(Product product) {
        return this.getOwnersByProduct(product.getId());
    }

    public CandlepinQuery<Owner> getOwnersByProduct(String productId) {
        // Impl note:
        // We have to do this in two queries due to how Hibernate processes projections here. We're
        // working around a number of issues:
        //  1. Hibernate does not rearrange a query based on a projection, but instead, performs a
        //     second query (as we're doing here).
        //  2. Because the initial query is not rearranged, we are actually pulling a collection of
        //     join objects, so filtering/sorting via CandlepinQuery is incorrect or broken
        //  3. The second query Hibernate performs uses the IN operator without any protection for
        //     the MySQL/MariaDB element limits.
        String jpql = "SELECT op.ownerId FROM OwnerProduct op WHERE op.productId = :product_id";

        List<String> ids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("product_id", productId)
            .getResultList();

        if (ids != null && !ids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Owner.class, null)
                .add(CPRestrictions.in("id", ids));

            return this.cpQueryFactory.<Owner>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Owner>buildQuery();
    }

    /**
     * Fetches a collection of product UUIDs currently mapped to the given owner. If the owner is
     * not mapped to any products, an empty collection will be returned.
     *
     * @param owner
     *  The owner for which to fetch product UUIDs
     *
     * @return
     *  a collection of product UUIDs belonging to the given owner
     */
    public Collection<String> getProductUuidsByOwner(Owner owner) {
        return this.getProductUuidsByOwner(owner.getId());
    }

    /**
     * Fetches a collection of product UUIDs currently mapped to the given owner. If the owner is
     * not mapped to any products, an empty collection will be returned.
     *
     * @param ownerId
     *  The ID of the owner for which to fetch product UUIDs
     *
     * @return
     *  a collection of product UUIDs belonging to the given owner
     */
    public Collection<String> getProductUuidsByOwner(String ownerId) {
        String jpql = "SELECT op.product.uuid FROM OwnerProduct op WHERE op.owner.id = :owner_id";

        List<String> uuids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_id", ownerId)
            .getResultList();

        return uuids != null ? uuids : Collections.<String>emptyList();
    }

    /**
     * Fetches a list containing all products mapped to the organization specified by the specified
     * organization ID. If a given product is utilized by an organization but has not been properly
     * mapped via owner-product mapping, it will not be included in the output of this method. If
     * the specified organization ID is null or no matching organization exists, this method returns
     * an empty list.
     *
     * @param ownerId
     *  the ID of the organization for which to fetch all mapped products
     *
     * @return
     *  a list containing all mapped products for the given organization
     */
    public List<Product> getProductsByOwner(String ownerId) {
        String jpql = "SELECT op.product FROM OwnerProduct op WHERE op.ownerId = :owner_id";

        return this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("owner_id", ownerId)
            .getResultList();
    }

    /**
     * Fetches a list containing all products mapped to the specified organization. If a given
     * product is utilized by an organization but has not been properly mapped via owner-product
     * mapping, it will not be included in the output of this method. If the specified organization
     * is null or no matching organization exists, this method returns an empty list.
     *
     * @param owner
     *  the organization for which to fetch all mapped products
     *
     * @return
     *  a list containing all mapped products for the given organization
     */
    public List<Product> getProductsByOwner(Owner owner) {
        return owner != null ? this.getProductsByOwner(owner.getId()) : new ArrayList();
    }

    /**
     * Builds a query for fetching the products currently mapped to the given owner.
     *
     * @deprecated
     *  this method utilizes CandlepinQuery, which itself is backed by deprecated Hibernate APIs,
     *  and should not be used. New code should use the untagged getProductsByOwner call instead.
     *
     * @param owner
     *  The owner for which to fetch products
     *
     * @return
     *  a query for fetching the products belonging to the given owner
     */
    @Deprecated
    public CandlepinQuery<Product> getProductsByOwnerCPQ(Owner owner) {
        return this.getProductsByOwnerCPQ(owner.getId());
    }

    /**
     * Builds a query for fetching the products currently mapped to the given owner.
     *
     * @deprecated
     *  this method utilizes CandlepinQuery, which itself is backed by deprecated Hibernate APIs,
     *  and should not be used. New code should use the untagged getProductsByOwner call instead.
     *
     * @param ownerId
     *  The ID of the owner for which to fetch products
     *
     * @return
     *  a query for fetching the products belonging to the given owner
     */
    @Deprecated
    public CandlepinQuery<Product> getProductsByOwnerCPQ(String ownerId) {
        // Impl note: See getOwnersByProduct for details on why we're doing this in two queries
        Collection<String> uuids = this.getProductUuidsByOwner(ownerId);

        if (!uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Product.class, null)
                .add(CPRestrictions.in("uuid", uuids));

            return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    public CandlepinQuery<Product> getProductsByIds(Owner owner, Collection<String> productIds) {
        return this.getProductsByIds(owner.getId(), productIds);
    }

    public CandlepinQuery<Product> getProductsByIds(String ownerId, Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return this.cpQueryFactory.<Product>buildQuery();
        }

        // Impl note: See getOwnersByProduct for details on why we're doing this in two queries.
        Session session = this.currentSession();

        List<String> uuids = session.createCriteria(OwnerProduct.class)
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .add(Restrictions.eq("owner.id", ownerId))
            .add(CPRestrictions.in("product.id", productIds))
            .setProjection(Projections.property("product.uuid"))
            .list();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Product.class, null)
                .add(CPRestrictions.in("uuid", uuids));

            return this.cpQueryFactory.<Product>buildQuery(session, criteria);
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    public CandlepinQuery<Product> getProductsByIdsUsingOwnerKey(String ownerKey,
        Collection<String> productIds) {

        if (productIds == null || productIds.isEmpty()) {
            return this.cpQueryFactory.<Product>buildQuery();
        }

        // Impl note: See getOwnersByProduct for details on why we're doing this in two queries.
        Session session = this.currentSession();

        List<String> uuids = session.createCriteria(OwnerProduct.class)
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .add(Restrictions.eq("owner.key", ownerKey))
            .add(CPRestrictions.in("product.id", productIds))
            .setProjection(Projections.property("product.uuid"))
            .list();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Product.class, null)
                .add(CPRestrictions.in("uuid", uuids));

            return this.cpQueryFactory.<Product>buildQuery(session, criteria);
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    /**
     * Builds a query for fetching the system purpose attributes mapped to the given owner's products
     *  Will only return attributes for products that are related to unexpired pools.
     *
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
     * Builds a query for fetching the system purpose attributes mapped to the given owner's products
     *  Will only return attributes for products that are related to unexpired pools.
     *
     *
     * @param ownerId
     *  The owner ID for which to fetch system purpose attributes
     *
     * @return
     *  a map of attributes belonging to the given owner
     */
    public Map<String, Set<String>> getSyspurposeAttributesByOwner(String ownerId) {
        if (ownerId == null) {
            return getBaseSyspurposeMap();
        }

        String sql = "SELECT DISTINCT a.name, a.value  " +
            "FROM cp2_product_attributes a " +
            "JOIN cp_pool b ON a.product_uuid=b.product_uuid " +
            "AND a.name IN ('usage','roles','addons','support_type') " +
            "AND b.owner_id=:owner_id " +
            "AND b.endDate > CURRENT_TIMESTAMP ";

        List<Object[]> result = this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("owner_id", ownerId)
            .getResultList();

        sql = "SELECT DISTINCT a.name, a.value " +
            "FROM cp2_product_attributes a " +
            "JOIN cp_pool b ON a.product_uuid=b.product_uuid " +
            "AND name = 'support_level' " +
            "AND b.owner_id=:owner_id " +
            "AND b.endDate > CURRENT_TIMESTAMP " +
            "LEFT OUTER join cp2_product_attributes c ON a.product_uuid=c.product_uuid " +
            "AND c.name='support_level_exempt' " +
            "WHERE c.value IS NULL or c.value != 'true'";

        result.addAll(this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("owner_id", ownerId)
            .getResultList());

        Map<String, Set<String>> attributeMap = getBaseSyspurposeMap();
        for (Object[] attMap : result) {
            attributeMap.get(attMap[0]).addAll(Util.toList((String) attMap[1]));
        }
        return attributeMap;
    }

    private Map<String, Set<String>> getBaseSyspurposeMap() {
        return Map.of(
            "usage", new HashSet<>(),
            "roles", new HashSet<>(),
            "addons", new HashSet<>(),
            "support_type", new HashSet<>(),
            "support_level", new HashSet<>());
    }

    @Transactional
    public long getOwnerCount(Product product) {
        String jpql = "SELECT count(op) FROM OwnerProduct op WHERE op.product.uuid = :product_uuid";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql, Long.class)
            .setParameter("product_uuid", product.getUuid())
            .getSingleResult();

        return count;
    }

    /**
     * Checks if the owner has an existing version of the specified product. This lookup is
     * different than the mapping check in that this check will find any product with the
     * specified ID, as opposed to checking if a specific version is mapped to the owner.
     *
     * @param owner
     *  The owner of the product to lookup
     *
     * @param productId
     *  The Red Hat ID of the product to lookup
     *
     * @return
     *  true if the owner has a product with the given RHID; false otherwise
     */
    @Transactional
    public boolean productExists(Owner owner, String productId) {
        String jpql = "SELECT op FROM OwnerProduct op " +
            "WHERE op.ownerId = :owner_id AND op.productId = :product_id";

        try {
            this.getEntityManager()
                .createQuery(jpql)
                .setParameter("owner_id", owner.getId())
                .setParameter("product_id", productId)
                .getSingleResult();
            return true;
        }
        catch (NoResultException nre) {
            return false;
        }
    }

    /**
     * Filters the given list of Red Hat product IDs by removing the IDs which represent unknown
     * products for the specified owner.
     *
     * @param owner
     *  The owner to search
     *
     * @param productIds
     *  A collection of Red Hat product IDs to filter
     *
     * @return
     *  A new set containing only product IDs for products which exist for the given owner
     */
    @Transactional
    public Set<String> filterUnknownProductIds(Owner owner, Collection<String> productIds) {
        Set<String> existingIds = new HashSet<>();

        if (productIds != null && !productIds.isEmpty()) {
            existingIds.addAll(this.createSecureCriteria()
                .createAlias("owner", "owner")
                .createAlias("product", "product")
                .setProjection(Projections.property("product.id"))
                .add(Restrictions.eq("owner.id", owner.getId()))
                .add(CPRestrictions.in("product.id", productIds))
                .list());
        }

        return existingIds;
    }

    @Transactional
    public boolean isProductMappedToOwner(Product product, Owner owner) {
        String jpql = "SELECT count(op) FROM OwnerProduct op " +
            "WHERE op.owner.id = :owner_id AND op.product.uuid = :product_uuid";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("product_uuid", product.getUuid())
            .getSingleResult();

        return count > 0;
    }

    @Transactional
    public boolean mapProductToOwner(Product product, Owner owner) {
        if (!this.isProductMappedToOwner(product, owner)) {
            this.create(new OwnerProduct(owner, product));

            return true;
        }

        return false;
    }

    @Transactional
    public int mapProductToOwners(Product product, Owner... owners) {
        int count = 0;

        if (owners != null) {
            for (Owner owner : owners) {
                if (this.mapProductToOwner(product, owner)) {
                    ++count;
                }
            }
        }

        return count;
    }

    @Transactional
    public int mapOwnerToProducts(Owner owner, Product... products) {
        int count = 0;

        if (products != null) {
            for (Product product : products) {
                if (this.mapProductToOwner(product, owner)) {
                    ++count;
                }
            }
        }

        return count;
    }

    @Transactional
    public boolean removeOwnerFromProduct(Product product, Owner owner) {
        String jpql = "DELETE FROM OwnerProduct op " +
            "WHERE op.product.uuid = :product_uuid AND op.owner.id = :owner_id";

        int rows = this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("product_uuid", product.getUuid())
            .executeUpdate();

        return rows > 0;
    }

    @Transactional
    public int clearOwnersForProduct(Product product) {
        String jpql = "DELETE FROM OwnerProduct op " +
            "WHERE op.product.uuid = :product_uuid";

        return this.getEntityManager()
            .createQuery(jpql)
            .setParameter("product_uuid", product.getUuid())
            .executeUpdate();
    }

    @Transactional
    public int clearProductsForOwner(Owner owner) {
        String jpql = "DELETE FROM OwnerProduct op " +
            "WHERE op.owner.id = :owner_id";

        return this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .executeUpdate();
    }

    /**
     * Fetches a list of product IDs currently used by development pools for the specified owner.
     * If no such pools exist, or the owner has no pools, this method returns an empty list.
     *
     * @param ownerId
     *  the ID of the owner for which to look up development product IDs
     *
     * @return
     *  a list of development product IDs for the given owner
     */
    public List<String> getDevProductIds(String ownerId) {
        String jpql = "SELECT prod.id FROM Pool pool JOIN pool.product prod " +
            "WHERE pool.owner.id = :owner_id AND pool.type = :pool_type";

        return this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_id", ownerId)
            .setParameter("pool_type", PoolType.DEVELOPMENT)
            .getResultList();
    }

    /**
     * Fetches a list of products within the given organization which directly reference the
     * specified product. Indirect references, such as a product which has a derived product that
     * provides the specified product, are not included in the collection returned by this method.
     *
     * @param ownerId
     *  the ID of the owner/organization in which to search for referencing products
     *
     * @param productUuid
     *  the UUID of the product for which to fetch product references
     *
     * @return
     *  a collection of products directly referencing the specified product
     */
    public List<Product> getProductsReferencingProduct(String ownerId, String productUuid) {
        String jpql = "SELECT prod FROM OwnerProduct op " +
            "JOIN op.product prod " +
            "LEFT JOIN prod.providedProducts providedProd " +
            "WHERE op.owner.id = :owner_id " +
            "  AND (prod.derivedProduct.uuid = :product_uuid " +
            "   OR providedProd.uuid = :product_uuid)";

        return this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("owner_id", ownerId)
            .setParameter("product_uuid", productUuid)
            .getResultList();
    }

    /**
     * Fetches a list of products within the given organization which directly reference the
     * specified content. Indirect references, such as a product which has a derived product that
     * provides the specified content, are not included in the collection returned by this method.
     *
     * @param ownerId
     *  the ID of the owner/organization in which to search for referencing products
     *
     * @param contentUuid
     *  the UUID of the content for which to fetch product references
     *
     * @return
     *  a collection of products directly referencing the specified content
     */
    public List<Product> getProductsReferencingContent(String ownerId, String contentUuid) {
        String jpql = "SELECT prod FROM OwnerProduct op " +
            "JOIN op.product prod " +
            "JOIN prod.productContent pc " +
            "JOIN pc.content content " +
            "WHERE op.owner.id = :owner_id " +
            "  AND content.uuid = :content_uuid";

        return this.getEntityManager()
            .createQuery(jpql, Product.class)
            .setParameter("owner_id", ownerId)
            .setParameter("content_uuid", contentUuid)
            .getResultList();
    }

    /**
     * Fetches all products having an entity version equal to one of the versions provided.
     *
     * @param versions
     *  A collection of entity versions to use to select products
     *
     * @return
     *  a map containing the products found, keyed by product ID
     */
    public Map<String, List<Product>> getProductsByVersions(Collection<Long> versions) {
        Map<String, List<Product>> result = new HashMap<>();

        if (versions != null && !versions.isEmpty()) {
            String jpql = "SELECT p FROM Product p WHERE p.entityVersion IN (:vblock)";

            TypedQuery<Product> query = this.getEntityManager()
                .createQuery(jpql, Product.class);

            for (Collection<Long> block : this.partition(versions)) {
                for (Product element : query.setParameter("vblock", block).getResultList()) {
                    result.computeIfAbsent(element.getId(), k -> new LinkedList<>())
                        .add(element);
                }
            }
        }

        return result;
    }

    /**
     * Fetches a mapping of product ID to date it has been orphaned within the given organization.
     * If a given product does not exist within the organization, it will not have a mapping in the
     * returned map. If no matching products exist within the org, this method returns an empty map.
     *
     * @param owner
     *  the organization from which orphaned product dates should be fetched
     *
     * @param productIds
     *  a collection of product IDs for which to fetch the orphan date
     *
     * @throws IllegalArgumentException
     *  if owner is null
     *
     * @return
     *  a mapping of product ID to its orphan date within the context of the provided organization
     */
    public Map<String, Instant> getOwnerProductOrphanedDates(Owner owner, Collection<String> productIds) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        return this.getOwnerProductOrphanedDates(owner.getId(), productIds);
    }

    /**
     * Fetches a mapping of product ID to date it has been orphaned within the given organization.
     * If a given product does not exist within the organization, it will not have a mapping in the
     * returned map. If no matching products exist within the org, this method returns an empty map.
     *
     * @param ownerId
     *  the ID of the organization from which orphaned product dates should be fetched
     *
     * @param productIds
     *  a collection of product IDs for which to fetch the orphan date
     *
     * @throws IllegalArgumentException
     *  if ownerId is null
     *
     * @return
     *  a mapping of product ID to its orphan date within the context of the provided organization
     */
    public Map<String, Instant> getOwnerProductOrphanedDates(String ownerId, Collection<String> productIds) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId is null");
        }

        Map<String, Instant> dates = new HashMap<>();

        if (productIds != null && !productIds.isEmpty()) {
            String jpql = "SELECT op.productId, op.orphanedDate FROM OwnerProduct op " +
                "WHERE op.ownerId = :ownerId " +
                "  AND op.productId IN (:productIds)";

            Query query = this.getEntityManager()
                .createQuery(jpql)
                .setParameter("ownerId", ownerId);

            for (Collection<String> block : this.partition(productIds)) {
                query.setParameter("productIds", block)
                    .getResultList()
                    .stream()
                    .forEach(row -> dates.put((String) ((Object[]) row)[0], (Instant) ((Object[]) row)[1]));
            }
        }

        return dates;
    }

    /**
     * Sets or clears the product orphaned dates for the given products within the provided
     * organization. If the provided date is null, any existing orphaned date will be cleared.
     *
     * @param owner
     *  the organization in which to set product orphaned dates
     *
     * @param productIds
     *  a collection of IDs of products on which to set the orphaned date
     *
     * @param instant
     *  the new orphaned dates of the given products, or null to clear it
     *
     * @throws IllegalArgumentException
     *  if ownerId is null
     *
     * @return
     *  the number of owner-product references updated as a result of this operation
     */
    public int updateOwnerProductOrphanedDates(Owner owner, Collection<String> productIds, Instant instant) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        return this.updateOwnerProductOrphanedDates(owner.getId(), productIds, instant);
    }

    /**
     * Sets or clears the product orphaned dates for the given products within the provided
     * organization. If the provided date is null, any existing orphaned date will be cleared.
     *
     * @param ownerId
     *  the ID of the organization in which to set product orphaned dates
     *
     * @param productIds
     *  a collection of IDs of products on which to set the orphaned date
     *
     * @param instant
     *  the new orphaned dates of the given products, or null to clear it
     *
     * @throws IllegalArgumentException
     *  if ownerId is null
     *
     * @return
     *  the number of owner-product references updated as a result of this operation
     */
    public int updateOwnerProductOrphanedDates(String ownerId, Collection<String> productIds,
        Instant instant) {

        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId is null");
        }

        int updated = 0;

        if (productIds != null && !productIds.isEmpty()) {
            String jpql = "UPDATE OwnerProduct op SET op.orphanedDate = :date " +
                "WHERE op.ownerId = :ownerId " +
                "  AND op.productId IN (:productIds)";

            Query query = this.getEntityManager()
                .createQuery(jpql)
                .setParameter("ownerId", ownerId)
                .setParameter("date", instant);

            for (Collection<String> block : this.partition(productIds)) {
                updated += query.setParameter("productIds", block)
                    .executeUpdate();
            }
        }

        return updated;
    }

    /**
     * Updates the product references currently pointing to the original product to instead point to
     * the updated product for the specified owners. This method does not update references which
     * would normally affect product versioning, such as references from other products.
     * <p/></p>
     * <strong>Warning:</strong> Hibernate does not gracefully handle situations where the data
     * backing an entity changes via direct SQL or other outside influence. While, logically, a
     * refresh on the entity should resolve any divergence, in many cases it does not or causes
     * errors. As such, whenever this method is called, any active ActivationKey or Pool entities
     * should be manually evicted from the session and re-queried to ensure they will not clobber
     * the changes made by this method on persist, nor trigger any errors on refresh.
     *
     * @param owner
     *  The owners for which to apply the reference changes
     *
     * @param productUuidMap
     *  A mapping of source product UUIDs to updated product UUIDs
     */
    @Transactional
    public void updateOwnerProductReferences(Owner owner, Map<String, String> productUuidMap) {
        // Impl note:
        // We're doing this in straight SQL because direct use of the ORM would require querying all
        // of these objects and the available HQL refuses to do any joining (implicit or otherwise),
        // which prevents it from updating collections backed by a join table.
        // As an added bonus, it's quicker, but we'll have to be mindful of the memory vs backend
        // state divergence.
        //
        // Because we are using native SQL to do this and the query hint is limiting the table space
        // to OwnerContent all methods that call this need to ensure all new products have been
        // flushed to the db before this is called.

        if (productUuidMap == null || productUuidMap.isEmpty()) {
            // Nothing to update
            return;
        }

        // TODO:
        // Should we step through the UUID map and verify that it doesn't try to map anything weird,
        // (like a UUID to itself), or define multiple remappings?

        this.updateOwnerProductJoinTable(owner, productUuidMap);
        this.updateOwnerProductPools(owner, productUuidMap);

        // Looks like we don't need to do anything with product certificates, since we generate
        // them on request. By leaving them alone, they'll be generated as needed and we save some
        // overhead here.
    }

    /**
     * Part of the updateOwnerProductReferences operation; updates the owner to product join table.
     * See updateOwnerProductReferences for additional details.
     *
     * @param owner
     * @param productUuidMap
     */
    private void updateOwnerProductJoinTable(Owner owner, Map<String, String> productUuidMap) {
        String sql = "UPDATE " + OwnerProduct.DB_TABLE + " SET product_uuid = :updated " +
            "WHERE product_uuid = :current AND owner_id = :owner_id";

        Query query = this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("owner_id", owner.getId())
            .setHint(QueryHints.NATIVE_SPACES, OwnerProduct.class.getName());

        int count = 0;
        for (Map.Entry<String, String> entry : productUuidMap.entrySet()) {
            count += query.setParameter("current", entry.getKey())
                .setParameter("updated", entry.getValue())
                .executeUpdate();
        }

        log.debug("{} owner-product relations updated", count);
    }

    /**
     * Part of the updateOwnerProductReferences operation; updates the pools table.
     * See updateOwnerProductReferences for additional details.
     *
     * @param owner
     * @param productUuidMap
     */
    private void updateOwnerProductPools(Owner owner, Map<String, String> productUuidMap) {
        String sql = "UPDATE " + Pool.DB_TABLE + " SET product_uuid = :updated " +
            "WHERE product_uuid = :current AND owner_id = :owner_id";

        Query query = this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("owner_id", owner.getId())
            .setHint(QueryHints.NATIVE_SPACES, Pool.class.getName());

        int count = 0;
        for (Map.Entry<String, String> entry : productUuidMap.entrySet()) {
            count += query.setParameter("current", entry.getKey())
                .setParameter("updated", entry.getValue())
                .executeUpdate();
        }

        log.debug("{} pools updated", count);
    }

    /**
     * Removes the product references currently pointing to the specified product for the given
     * owners.
     * <p/></p>
     * <strong>Warning:</strong> Hibernate does not gracefully handle situations where the data
     * backing an entity changes via direct SQL or other outside influence. While, logically, a
     * refresh on the entity should resolve any divergence, in many cases it does not or causes
     * errors. As such, whenever this method is called, any active ActivationKey entities should
     * be manually evicted from the session and re-queried to ensure they will not clobber the
     * changes made by this method on persist, nor trigger any errors on refresh.
     *
     * @param owner
     *  The owner for which to remove product references
     *
     * @param productUuids
     *  The UUIDs of the products for which to remove references
     */
    @Transactional
    @SuppressWarnings("checkstyle:indentation")
    public void removeOwnerProductReferences(Owner owner, Collection<String> productUuids) {
        // Impl note:
        // We're doing this in straight SQL because direct use of the ORM would require querying all
        // of these objects and the available HQL refuses to do any joining (implicit or otherwise),
        // which prevents it from updating collections backed by a join table.
        // As an added bonus, it's quicker, but we'll have to be mindful of the memory vs backend
        // state divergence.

        // Impl note:
        // We have a restriction in removeProduct which should prevent a product from being removed
        // from an owner if it is being used by a pool. As such, we shouldn't need to manually clean
        // the pool tables here.

        if (productUuids == null || productUuids.isEmpty()) {
            return;
        }

        EntityManager entityManager = this.getEntityManager();
        log.info("Removing owner-product references for owner: {}, {}", owner, productUuids);

        // Owner-product relations
        for (List<String> block : this.partition(productUuids)) {
            String jpql = "DELETE FROM OwnerProduct op " +
                "WHERE op.owner.id = :owner_id AND op.product.uuid IN (:product_uuids)";

            int count = entityManager.createQuery(jpql)
                .setParameter("owner_id", owner.getId())
                .setParameter("product_uuids", block)
                .executeUpdate();

            log.info("{} owner-product relations removed", count);
        }

        // Activation key products
        this.removeActivationKeyProductReferences(owner, productUuids);
    }

    /**
     * Removes the product references from activation keys in the given organization. Called as part
     * of the removeOwnerProductReferences operation.
     *
     * @param owner
     *  the owner/organization in which to remove product references from activation keys
     *
     * @param productUuids
     *  a collection of UUIDs representing products to remove from activation keys within the org
     */
    private void removeActivationKeyProductReferences(Owner owner, Collection<String> productUuids) {
        EntityManager entityManager = this.getEntityManager();

        String jpql = "SELECT DISTINCT key.id FROM ActivationKey key WHERE key.ownerId = :owner_id";
        List<String> keyIds = entityManager.createQuery(jpql, String.class)
            .setParameter("owner_id", owner.getId())
            .getResultList();

        int count = 0;

        if (keyIds != null && !keyIds.isEmpty()) {
            Set<String> productIds = new HashSet<>();

            // Convert the product UUIDs to product IDs for cleaning up activation keys
            jpql = "SELECT prod.id FROM Product prod WHERE prod.uuid IN (:product_uuids)";
            Query query = entityManager.createQuery(jpql, String.class);

            for (List<String> block : this.partition(productUuids)) {
                query.setParameter("product_uuids", block)
                    .getResultList()
                    .forEach(elem -> productIds.add((String) elem));
            }

            // Delete the entries
            // Impl note: at the time of writing, JPA doesn't support doing this operation without
            // interacting with the objects directly. So, we're doing it with native SQL to avoid
            // even more work here.
            // Also note that MySQL/MariaDB doesn't like table aliases in a delete statement.
            String sql = "DELETE FROM cp_activation_key_products " +
                "WHERE key_id IN (:key_ids) AND product_id IN (:product_ids)";

            query = entityManager.createNativeQuery(sql);

            int blockSize = Math.min(this.getQueryParameterLimit() / 2, this.getInBlockSize() / 2);
            Iterable<List<String>> kidBlocks = this.partition(keyIds, blockSize);
            Iterable<List<String>> pidBlocks = this.partition(productIds, blockSize);

            for (List<String> kidBlock : kidBlocks) {
                query.setParameter("key_ids", kidBlock);

                for (List<String> pidBlock : pidBlocks) {
                    count += query.setParameter("product_ids", pidBlock)
                        .executeUpdate();
                }
            }
        }

        log.info("{} activation key product reference(s) removed", count);
    }

    /**
     * Clears and rebuilds the product mapping for the given owner, using the provided map of
     * product IDs to UUIDs.
     *
     * @param owner
     *  the owner for which to rebuild product mappings
     *
     * @param productIdMap
     *  a mapping of product IDs to product UUIDs to use as the new product mappings for this
     *  organization. If null or empty, the organization will be left without any product mappings.
     *
     * @throws IllegalArgumentException
     *  if owner is null, or lacks an ID
     */
    public void rebuildOwnerProductMapping(Owner owner, Map<String, String> productIdMap) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null, or lacks an ID");
        }

        EntityManager entityManager = this.getEntityManager();

        int rcount = entityManager.createQuery("DELETE FROM OwnerProduct op WHERE op.owner.id = :owner_id")
            .setParameter("owner_id", owner.getId())
            .executeUpdate();

        log.debug("Removed {} owner-product mappings for owner: {}", rcount, owner);

        if (productIdMap != null) {
            String sql = "INSERT INTO " + OwnerProduct.DB_TABLE + " (owner_id, product_id, product_uuid) " +
                "VALUES(:owner_id, :product_id, :product_uuid)";

            Query query = this.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("owner_id", owner.getId())
                .setHint(QueryHints.NATIVE_SPACES, OwnerProduct.class.getName());

            int icount = 0;
            for (Map.Entry<String, String> entry : productIdMap.entrySet()) {
                icount += query.setParameter("product_id", entry.getKey())
                    .setParameter("product_uuid", entry.getValue())
                    .executeUpdate();
            }

            log.debug("Inserted {} owner-product mappings for owner: {}", icount, owner);
        }
    }

    /**
     * Clears the entity version for the product with the given UUID. Calling this method will not
     * unlink the product from any entities referencing it, but it will prevent further updates from
     * converging on the product.
     *
     * @param productUuid
     *  the UUID of the product of which to clear the entity version
     */
    public void clearProductEntityVersion(String productUuid) {
        if (productUuid == null || productUuid.isEmpty()) {
            return;
        }

        String sql = "UPDATE " + Product.DB_TABLE + " SET entity_version = NULL " +
            "WHERE uuid = :product_uuid";

        this.getEntityManager()
            .createNativeQuery(sql)
            .setParameter("product_uuid", productUuid)
            .setHint(QueryHints.NATIVE_SPACES, Product.class.getName())
            .executeUpdate();
    }

    /**
     * Clears the entity version for the given product. Calling this method will not unlink the
     * product from any entities referencing it, but it will prevent further updates from converging
     * on the product.
     *
     * @param entity
     *  the product of which to clear the entity version
     */
    public void clearProductEntityVersion(Product entity) {
        if (entity != null) {
            this.clearProductEntityVersion(entity.getUuid());
        }
    }
}
