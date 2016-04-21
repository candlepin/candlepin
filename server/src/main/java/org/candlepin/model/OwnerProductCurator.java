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

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.hibernate.Session;
import org.hibernate.Query;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
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
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .createAlias("product", "product")
            .setProjection(Projections.property("owner"))
            .add(Restrictions.eq("product.id", productId));

        return this.cpQueryFactory.<Owner>buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<Product> getProductsByOwner(Owner owner) {
        return this.getProductsByOwner(owner.getId());
    }

    public CandlepinQuery<Product> getProductsByOwner(String ownerId) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .createAlias("owner", "owner")
            .setProjection(Projections.property("product"))
            .add(Restrictions.eq("owner.id", ownerId));

        return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<Product> getProductsByIds(Owner owner, Collection<String> productIds) {
        return this.getProductsByIds(owner.getId(), productIds);
    }

    public CandlepinQuery<Product> getProductsByIds(String ownerId, Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return this.cpQueryFactory.<Product>buildQuery();
        }

        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .createAlias("owner", "owner")
            .createAlias("product", "product")
            .setProjection(Projections.property("product"))
            .add(Restrictions.eq("owner.id", ownerId))
            .add(CPRestrictions.in("product.id", productIds));

        return this.cpQueryFactory.<Product>buildQuery(this.currentSession(), criteria);
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
     * owners.
     * <p/></p>
     * <strong>Warning:</strong> Hibernate does not gracefully handle situations where the data
     * backing an entity changes via direct SQL or other outside influence. While, logically, a
     * refresh on the entity should resolve any divergence, in many cases it does not or causes
     * errors. As such, whenever this method is called, any active ActivationKey entities should
     * be manually evicted from the session and re-queried to ensure they will not clobber the
     * changes made by this method on persist, nor trigger any errors on refresh.
     *
     * @param entity
     *  The product other objects are referencing
     *
     * @param owner
     *  The owners for which to apply the reference changes
     */
    @Transactional
    public void removeOwnerProductReferences(Product entity, Owner owner) {
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

        Session session = this.currentSession();

        // Owner products
        String sql = "DELETE FROM cp2_owner_products WHERE product_uuid = ?1 AND owner_id = ?2";

        int count = session.createSQLQuery(sql)
            .setParameter("1", entity.getUuid())
            .setParameter("2", owner.getId())
            .executeUpdate();

        log.debug("{} owner-product relations removed", count);

        // Activation key products
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_activation_key WHERE owner_id = ?1")
            .setParameter("1", owner.getId())
            .list();

        if (ids != null && !ids.isEmpty()) {
            int inBlocks = ids.size() / AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE + 1;

            StringBuilder builder = new StringBuilder("DELETE FROM cp2_activation_key_products ")
                .append("WHERE product_uuid = ?1 AND (");

            for (int i = 0; i < inBlocks; ++i) {
                if (i != 0) {
                    builder.append(" OR ");
                }

                builder.append("key_id IN (?").append(i + 2).append(')');
            }

            builder.append(')');

            Query query = session.createSQLQuery(builder.toString())
                .setParameter("1", entity.getUuid());

            int args = 1;
            for (List<String> block : Iterables.partition(ids,
                AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE)) {

                query.setParameterList(String.valueOf(++args), block);
            }

            count = query.executeUpdate();
            log.debug("{} activation keys removed", count);
        }
        else {
            log.debug("0 activation keys removed");
        }
    }
}
