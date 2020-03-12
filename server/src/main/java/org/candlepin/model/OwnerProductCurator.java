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

import org.candlepin.model.activationkeys.ActivationKey;

import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;



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

    @Transactional
    public OwnerProduct getOwnerProductByProductId(Owner owner, String productId) {
        return (OwnerProduct) this.createSecureCriteria()
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .add(Restrictions.eq("owner.id", owner.getId()))
            .add(Restrictions.eq("product.id", productId))
            .uniqueResult();
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
        String jpql = "SELECT op.owner.id FROM OwnerProduct op WHERE op.product.id = :product_id";

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
     * Builds a query for fetching the products currently mapped to the given owner.
     *
     * @param owner
     *  The owner for which to fetch products
     *
     * @return
     *  a query for fetching the products belonging to the given owner
     */
    public CandlepinQuery<Product> getProductsByOwner(Owner owner) {
        return this.getProductsByOwner(owner.getId());
    }

    /**
     * Builds a query for fetching the products currently mapped to the given owner.
     *
     * @param ownerId
     *  The ID of the owner for which to fetch products
     *
     * @return
     *  a query for fetching the products belonging to the given owner
     */
    public CandlepinQuery<Product> getProductsByOwner(String ownerId) {
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
        String jpql = "SELECT count(op) FROM OwnerProduct op " +
            "WHERE op.owner.id = :owner_id AND op.product.id = :product_id";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("product_id", productId)
            .getSingleResult();

        return count > 0;
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

        for (Owner owner : owners) {
            if (this.mapProductToOwner(product, owner)) {
                ++count;
            }
        }

        return count;
    }

    @Transactional
    public int mapOwnerToProducts(Owner owner, Product... products) {
        int count = 0;

        for (Product product : products) {
            if (this.mapProductToOwner(product, owner)) {
                ++count;
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
     * Builds a query which can be used to fetch the current collection of orphaned products. Due
     * to the nature of this request, it is highly advised that this query be run within a
     * transaction, with a pessimistic lock mode set.
     *
     * @return
     *  A CandlepinQuery for fetching the orphaned products
     */
    public CandlepinQuery<Product> getOrphanedProducts() {
        // As with many of the owner=>product lookups, we have to do this in two queries. Since
        // we need to start from product and do a left join back to owner products, we have to use
        // a native query instead of any of the ORM query languages

        String sql = "SELECT p.uuid " +
            "FROM cp2_products p LEFT JOIN cp2_owner_products op ON p.uuid = op.product_uuid " +
            "WHERE op.owner_id IS NULL";

        List<String> uuids = this.getEntityManager()
            .createNativeQuery(sql)
            .getResultList();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = DetachedCriteria.forClass(Product.class)
                .add(CPRestrictions.in("uuid", uuids))
                .addOrder(Order.asc("uuid"));

            return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    /**
     * Retrieves a criteria which can be used to fetch a list of products with the specified Red Hat
     * product ID and entity version belonging to owners other than the owner provided. If no
     * products were found matching the given criteria, this method returns an empty list.
     *
     * @param owner
     *  The owner whose products should be excluded from the results. If an owner is not provided,
     *  no additional filtering will be performed.
     *
     * @param productVersions
     *  A mapping of Red Hat product IDs to product versions to fetch
     *
     * @return
     *  a criteria for fetching products by version
     */
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Product> getProductsByVersions(Owner owner, Map<String, Integer> productVersions) {
        if (productVersions == null || productVersions.isEmpty()) {
            return this.cpQueryFactory.<Product>buildQuery();
        }

        // Impl note:
        // We perform this operation with two queries here to optimize out some unnecessary queries
        // when pulling product information. Even when pulling products in a batch, Hibernate will
        // pull the product collections (attributes, content and dependent product IDs) as separate
        // query for each product (ugh). By breaking this into two queries -- one for getting the
        // product UUIDs and one for pulling the actual products -- we will save upwards of two DB
        // hits per product filtered. We will lose time in the cases where we don't filter any
        // products, or the products we filter don't have any data in their collections; but we're
        // only using one additional query in those cases, versus (0-2)n in the normal case.

        Disjunction disjunction = Restrictions.disjunction();
        Criteria uuidCriteria = this.createSecureCriteria("op")
            .createAlias("op.product", "p")
            .add(disjunction)
            .setProjection(Projections.distinct(Projections.property("p.uuid")));

        for (Map.Entry<String, Integer> entry : productVersions.entrySet()) {
            disjunction.add(Restrictions.and(
                Restrictions.eq("p.id", entry.getKey()),
                Restrictions.eq("p.entityVersion", entry.getValue())
            ));
        }

        if (owner != null) {
            uuidCriteria.add(Restrictions.not(Restrictions.eq("op.owner", owner)));
        }

        List<String> uuids = uuidCriteria.list();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Product.class, null)
                .add(CPRestrictions.in("uuid", uuids));

            return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
        }

        return this.cpQueryFactory.<Product>buildQuery();
    }

    /**
     * Updates the product references currently pointing to the original product to instead point to
     * the updated product for the specified owners.
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

        if (productUuidMap == null || productUuidMap.isEmpty()) {
            // Nothing to update
            return;
        }

        Session session = this.currentSession();

        Map<String, Object> criteria = new HashMap<>();
        Map<Object, Object> uuidMap = Map.class.cast(productUuidMap);
        criteria.put("product_uuid", productUuidMap.keySet());
        criteria.put("owner_id", owner.getId());

        // Owner products
        int count = this.bulkSQLUpdate(OwnerProduct.DB_TABLE, "product_uuid", uuidMap, criteria);

        log.debug("{} owner-product relations updated", count);

        // pool provided and derived products
        count = this.bulkSQLUpdate(Pool.DB_TABLE, "product_uuid", uuidMap, criteria);

        criteria.remove("product_uuid");
        criteria.put("derived_product_uuid", productUuidMap.keySet());

        count += this.bulkSQLUpdate(Pool.DB_TABLE, "derived_product_uuid", uuidMap, criteria);

        log.debug("{} pools updated", count);

        // pool provided products
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_pool WHERE owner_id = :ownerId")
            .setParameter("ownerId", owner.getId())
            .list();

        if (ids != null && !ids.isEmpty()) {
            criteria.clear();
            criteria.put("product_uuid", productUuidMap.keySet());
            criteria.put("pool_id", ids);

            count = this.bulkSQLUpdate("cp2_pool_provided_products", "product_uuid", uuidMap, criteria);
            log.debug("{} provided products updated", count);

            count = this.bulkSQLUpdate("cp2_pool_derprov_products", "product_uuid", uuidMap, criteria);
            log.debug("{} derived provided products updated", count);
        }
        else {
            log.debug("0 provided products updated");
            log.debug("0 derived provided products updated");
        }


        // Activation key products
        ids = session.createSQLQuery("SELECT id FROM cp_activation_key WHERE owner_id = :ownerId")
            .setParameter("ownerId", owner.getId())
            .list();

        if (ids != null && !ids.isEmpty()) {
            criteria.clear();
            criteria.put("product_uuid", productUuidMap.keySet());
            criteria.put("key_id", ids);

            count = this.bulkSQLUpdate("cp2_activation_key_products", "product_uuid", uuidMap, criteria);
            log.debug("{} activation keys updated", count);
        }
        else {
            log.debug("0 activation keys updated");
        }

        // product certificates
        // Looks like we don't need to do anything here, since we generate them on request. By
        // leaving them alone, they'll be generated as needed and we save some overhead here.
    }

    /**
     * Removes the product references currently pointing to the specified product for the given
     * owners. This method cannot be used to remove references to products which are still mapped
     * to pools. Attempting to do so will result in an IllegalStateException.
     * <p/></p>
     * <strong>Warning:</strong> Hibernate does not gracefully handle situations where the data
     * backing an entity changes via direct SQL or other outside influence. While, logically, a
     * refresh on the entity should resolve any divergence, in many cases it does not or causes
     * errors. As such, whenever this method is called, any active ActivationKey entities should
     * be manually evicted from the session and re-queried to ensure they will not clobber the
     * changes made by this method on persist, nor trigger any errors on refresh.
     *
     * @param owner
     *  The owners for which to apply the reference changes
     *
     * @param productUuids
     *  The UUIDs of the products for which to remove references
     *
     * @throws IllegalStateException
     *  if the any of the products are in use by one or more pools owned by the given owner
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

        if (productUuids != null && !productUuids.isEmpty()) {
            log.info("Removing owner-product references for owner: {}, {}", owner, productUuids);

            Session session = this.currentSession();

            // Ensure we aren't trying to remove product references for products still used by
            // pools for this owner
            Long poolCount = (Long) session.createCriteria(Pool.class, "Pool")
                .createAlias("Pool.product", "Product")
                .createAlias("Product.providedProducts", "providedProd", JoinType.LEFT_OUTER_JOIN)
                .createAlias("Pool.derivedProduct", "DProduct", JoinType.LEFT_OUTER_JOIN)
                .createAlias("DProduct.providedProducts", "derivedProvidedProd", JoinType.LEFT_OUTER_JOIN)
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.or(
                    CPRestrictions.in("Product.uuid", productUuids),
                    CPRestrictions.in("DProduct.uuid", productUuids),
                    CPRestrictions.in("providedProd.uuid", productUuids),
                    CPRestrictions.in("derivedProvidedProd.uuid", productUuids)))
                .setProjection(Projections.count("id"))
                .uniqueResult();

            if (poolCount != null && poolCount.longValue() > 0) {
                throw new IllegalStateException(
                    "One or more products are currently used by one or more pools");
            }

            // Owner products ////////////////////////////////
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("product_uuid", productUuids);
            criteria.put("owner_id", owner.getId());

            int count = this.bulkSQLDelete(OwnerProduct.DB_TABLE, criteria);
            log.info("{} owner-product relations removed", count);

            // Impl note:
            // Even though there's a valid argument to be made here to do so, we do not unlink
            // content from a product. This may cause headaches in the future when a product object
            // is unlinked from an owner, but one or more of its contents are not.

            // Activation key products ///////////////////////
            String sql = "SELECT id FROM " + ActivationKey.DB_TABLE + " WHERE owner_id = :ownerId";
            List<String> ids = session.createSQLQuery(sql)
                .setParameter("ownerId", owner.getId())
                .list();

            if (ids != null && !ids.isEmpty()) {
                criteria.clear();
                criteria.put("key_id", ids);
                criteria.put("product_uuid", productUuids);

                count = this.bulkSQLDelete("cp2_activation_key_products", criteria);
                log.info("{} activation key products removed", count);
            }
            else {
                log.info("0 activation key products removed");
            }
        }
    }

}
