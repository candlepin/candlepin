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

import com.google.inject.persist.Transactional;

import org.hibernate.Session;
import org.hibernate.Criteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
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

    public Collection<Owner> getOwnersByProduct(Product product) {
        return this.getOwnersByProduct(product.getId());
    }

    @Transactional
    public Collection<Owner> getOwnersByProduct(String productId) {
        return (List<Owner>) this.createSecureCriteria()
            .createAlias("product", "product")
            .setProjection(Projections.property("owner"))
            .add(Restrictions.eq("product.id", productId))
            .list();
    }

    public Collection<Product> getProductsByOwner(Owner owner) {
        return this.getProductsByOwner(owner.getId());
    }

    @Transactional
    public Collection<Product> getProductsByOwner(String ownerId) {
        return (List<Product>) this.createSecureCriteria()
            .createAlias("owner", "owner")
            .setProjection(Projections.property("product"))
            .add(Restrictions.eq("owner.id", ownerId))
            .list();
    }

    public Collection<Product> getProductsByIds(Owner owner, Collection<String> productIds) {
        return this.getProductsByIds(owner.getId(), productIds);
    }

    @Transactional
    public Collection<Product> getProductsByIds(String ownerId, Collection<String> productIds) {
        Collection<Product> result = null;

        if (productIds != null && productIds.size() > 0) {
            Criteria criteria = this.createSecureCriteria()
                .createAlias("owner", "owner")
                .createAlias("product", "product")
                .setProjection(Projections.property("product"))
                .add(Restrictions.eq("owner.id", ownerId))
                .add(this.unboundedInCriterion("product.id", productIds));

            result = (Collection<Product>) criteria.list();
        }

        return result != null ? result : new LinkedList<Product>();
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
     * @param current
     *  The current product other objects are referencing
     *
     * @param updated
     *  The product other objects should reference
     *
     * @param owners
     *  A collection of owners for which to apply the reference changes
     *
     * @return
     *  a reference to the updated product
     */
    public Product updateOwnerProductReferences(Product current, Product updated, Collection<Owner> owners) {
        // Impl note:
        // We're doing this in straight SQL because direct use of the ORM would require querying all
        // of these objects and the available HQL refuses to do any joining (implicit or otherwise),
        // which prevents it from updating collections backed by a join table.
        // As an added bonus, it's quicker, but we'll have to be mindful of the memory vs backend
        // state divergence.

        if (owners.size() > AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE) {
            throw new UnsupportedOperationException("Large owner collections are currently unsupported");
        }

        Session session = this.currentSession();
        Set<String> ownerIds = new HashSet<String>();

        for (Owner owner : owners) {
            ownerIds.add(owner.getId());
        }

        // Owner products
        String sql = "UPDATE cp2_owner_products SET product_uuid = ?1 " +
            "WHERE product_uuid = ?2 AND owner_id IN (?3)";

        int opCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} owner-product relations updated", opCount);

        // Activation key products
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_activation_key WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "UPDATE cp2_activation_key_products SET product_uuid = ?1 " +
            "WHERE product_uuid = ?2 AND key_id IN (?3)";

        int akCount = this.safeSQLUpdateWithCollection(sql, ids, updated.getUuid(), current.getUuid());
        log.debug("{} activation keys updated", akCount);

        // pool provided and derived products
        sql = "UPDATE cp_pool SET product_uuid = ?1 WHERE product_uuid = ?2 AND owner_id IN (?3)";

        int ppCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} pools updated", ppCount);

        sql = "UPDATE cp_pool SET derived_product_uuid = ?1 " +
            "WHERE derived_product_uuid = ?2 AND owner_id IN (?3)";

        int pdpCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} pools updated", pdpCount);

        // pool provided products
        ids = session.createSQLQuery("SELECT id FROM cp_pool WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "UPDATE cp2_pool_provided_products SET product_uuid = ?1 " +
            "WHERE product_uuid = ?2 AND pool_id IN (?3)";

        int pppCount = this.safeSQLUpdateWithCollection(sql, ids, updated.getUuid(), current.getUuid());
        log.debug("{} provided products updated", pppCount);

        // pool derived provided products
        sql = "UPDATE cp2_pool_derprov_products SET product_uuid = ?1 " +
            "WHERE product_uuid = ?2 AND pool_id IN (?3)";

        int pdppCount = this.safeSQLUpdateWithCollection(sql, ids, updated.getUuid(), current.getUuid());
        log.debug("{} derived provided products updated", pdppCount);

        // product certificates
        // Looks like we don't need to do anything here, since we generate them on request. By
        // leaving them alone, they'll be generated as needed and we save some overhead here.

        return updated;
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
     * @param owners
     *  A collection of owners for which to apply the reference changes
     */
    public void removeOwnerProductReferences(Product entity, Collection<Owner> owners) {
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

        if (owners.size() > AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE) {
            throw new UnsupportedOperationException("Large owner collections are currently unsupported");
        }

        Session session = this.currentSession();
        Set<String> ownerIds = new HashSet<String>();

        for (Owner owner : owners) {
            ownerIds.add(owner.getId());
        }

        // Owner products
        String sql = "DELETE FROM cp2_owner_products WHERE product_uuid = ?1 AND owner_id IN (?2)";

        int opCount = session.createSQLQuery(sql)
            .setParameter("1", entity.getUuid())
            .setParameterList("2", ownerIds)
            .executeUpdate();

        log.debug("{} owner-product relations removed", opCount);

        // Activation key products
        List<String> ids = session.createSQLQuery("SELECT id FROM cp_activation_key WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "DELETE FROM cp2_activation_key_products WHERE product_uuid = ?1 AND key_id IN (?2)";

        int akCount = this.safeSQLUpdateWithCollection(sql, ids, entity.getUuid());
        log.debug("{} activation keys removed", akCount);
    }
}
