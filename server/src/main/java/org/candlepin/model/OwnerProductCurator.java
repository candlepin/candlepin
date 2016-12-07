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

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.Query;
import org.hibernate.criterion.DetachedCriteria;
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



/**
 * The OwnerProductCurator provides functionality for managing the mapping between owners and
 * products.
 */
public class OwnerProductCurator extends AbstractHibernateCurator<OwnerProduct> {
    private static Logger log = LoggerFactory.getLogger(OwnerProductCurator.class);

    /**
     * Default constructor
     */
    public OwnerProductCurator() {
        super(OwnerProduct.class);
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
        //     the MySQL/MariaDB or Oracle element limits.
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

        // Impl note: See getOwnersByProduct for details on why we're doing this in two queries
        String jpql = "SELECT op.product.uuid FROM OwnerProduct op WHERE op.owner.id = :owner_id";

        List<String> uuids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_id", ownerId)
            .getResultList();

        if (uuids != null && !uuids.isEmpty()) {
            DetachedCriteria criteria = this.createSecureDetachedCriteria(Product.class, null)
                .add(CPRestrictions.in("uuid", uuids))
                .add(CPRestrictions.in("id", productIds));

            return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
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
     * Fetches the number of owners currently mapped to the given products. If a provided product
     * UUID does not represent an existing product, or is not mapped to any owners, it will not be
     * included in the output.
     *
     * @param productUuids
     *  A collection of product UUIDs for which to fetch the owner counts
     *
     * @return
     *  A mapping of product UUIDs to their owner counts
     */
    @Transactional
    public Map<String, Integer> getOwnerCounts(Collection<String> productUuids) {
        StringBuilder builder = new StringBuilder("SELECT op.productUuid, COUNT(op) FROM OwnerProduct op");

        if (productUuids != null && !productUuids.isEmpty()) {
            builder.append(" WHERE ");

            int blockCount = (int) Math.ceil(productUuids.size() /
                (float) AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE);

            for (int i = 1; i <= blockCount; ++i) {
                if (i > 1) {
                    builder.append(" OR ");
                }

                builder.append("op.productUuid IN :product_uuids_").append(i);
            }
        }

        builder.append(" GROUP BY op.productUuid");

        Query query = this.currentSession().createQuery(builder.toString());

        if (productUuids != null && !productUuids.isEmpty()) {
            Iterable<List<String>> blocks = Iterables.partition(productUuids,
                AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE);

            int offset = 0;
            for (List<String> block : blocks) {
                query.setParameterList("product_uuids_" + ++offset, block);
            }
        }

        Map<String, Integer> result = new HashMap<String, Integer>();

        ScrollableResults cursor = query.scroll();
        while (cursor.next()) {
            String uuid = cursor.getString(0);
            Integer count = Integer.valueOf((int) cursor.getLong(1).longValue());

            result.put(uuid, count);
        }

        cursor.close();

        return result;
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
        Set<String> existingIds = new HashSet<String>();

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

        Map<String, Object> criteria = new HashMap<String, Object>();
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
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_pool WHERE owner_id = ?1")
            .setParameter("1", owner.getId())
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
        ids = session.createSQLQuery("SELECT id FROM cp_activation_key WHERE owner_id = ?1")
            .setParameter("1", owner.getId())
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
            Session session = this.currentSession();

            // Ensure we aren't trying to remove product references for products still used by
            // pools for this owner
            Long poolCount = (Long) session.createCriteria(Pool.class)
                .createAlias("providedProducts", "providedProd", JoinType.LEFT_OUTER_JOIN)
                .createAlias("derivedProvidedProducts", "derivedProvidedProd", JoinType.LEFT_OUTER_JOIN)
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.or(
                    CPRestrictions.in("product.uuid", productUuids),
                    CPRestrictions.in("derivedProduct.uuid", productUuids),
                    CPRestrictions.in("providedProd.uuid", productUuids),
                    CPRestrictions.in("derivedProvidedProd.uuid", productUuids)))
                .setProjection(Projections.count("id"))
                .uniqueResult();

            if (poolCount != null && poolCount.longValue() > 0) {
                throw new IllegalStateException(
                    "One or more products are currently used by one or more pools");
            }

            // Owner products ////////////////////////////////
            Map<String, Object> criteria = new HashMap<String, Object>();
            criteria.put("product_uuid", productUuids);
            criteria.put("owner_id", owner.getId());

            int count = this.bulkSQLDelete(OwnerProduct.DB_TABLE, criteria);
            log.debug("{} owner-product relations removed", count);

            // Activation key products ///////////////////////
            String sql = "SELECT id FROM " + ActivationKey.DB_TABLE + " WHERE owner_id = ?1";
            List<String> ids = session.createSQLQuery(sql)
                .setParameter("1", owner.getId())
                .list();

            if (ids != null && !ids.isEmpty()) {
                criteria.clear();
                criteria.put("key_id", ids);
                criteria.put("product_uuid", productUuids);

                count = this.bulkSQLDelete("cp2_activation_key_products", criteria);
                log.debug("{} activation keys removed", count);
            }
            else {
                log.debug("0 activation keys removed");
            }
        }
    }

}
