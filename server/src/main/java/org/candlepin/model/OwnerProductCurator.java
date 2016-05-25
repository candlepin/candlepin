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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

    @Transactional
    public List<Owner> getOwnersByProduct(Product product) {
        String hql = "SELECT o " +
            "FROM OwnerProduct op " +
            "  JOIN op.product p " +
            "  JOIN op.owner o "+
            "WHERE p = :product";

        return (List<Owner>) this.getEntityManager()
            .createQuery(hql, Owner.class)
            .setParameter("product", product)
            .getResultList();
    }

    @Transactional
    public List<Product> getProductsByOwner(Owner owner) {
        String hql = "SELECT o " +
            "FROM OwnerProduct op " +
            "  JOIN op.product p " +
            "  JOIN op.owner o "+
            "WHERE o = :owner";

        return (List<Product>) this.getEntityManager()
            .createQuery(hql, Product.class)
            .setParameter("owner", owner)
            .getResultList();
    }

    @Transactional
    public Long getOwnerCount(Product product) {
        return this.getEntityManager()
            .createQuery("SELECT count(op) FROM OwnerProduct op WHERE op.product = :product", Long.class)
            .setParameter("product", product)
            .getSingleResult();
    }

    @Transactional
    public boolean isProductMappedToOwner(Product product, Owner owner) {
        String hql = "SELECT count(op) FROM OwnerProduct op " +
            "WHERE op.owner = :owner AND op.product = :product";

        int count = (Integer) this.getEntityManager()
            .createQuery(hql)
            .setParameter("owner", owner)
            .setParameter("product", product)
            .getSingleResult();

        return count > 0;
    }

    @Transactional
    public boolean mapProductToOwner(Product product, Owner owner) {
        if (!this.isProductMappedToOwner(product, owner)) {
            this.create(new OwnerProduct(owner.getId(), product.getUuid()));

            return true;
        }

        return false;
    }

    @Transactional
    public boolean removeOwnerFromProduct(Product product, Owner owner) {
        String hql = "DELETE FROM OwnerProduct op " +
            "WHERE op.product = :product AND op.owner = :owner";

        int rows = this.getEntityManager()
            .createQuery(hql)
            .setParameter("owner", owner)
            .setParameter("product", product)
            .executeUpdate();

        return rows > 0;
    }

    @Transactional
    public int clearOwnersForProduct(Product product) {
        String hql = "DELETE FROM OwnerProduct op " +
            "WHERE op.product = :product";

        return this.getEntityManager()
            .createQuery(hql)
            .setParameter("product", product)
            .executeUpdate();
    }

    @Transactional
    public int clearProductsForOwner(Owner owner) {
        String hql = "DELETE FROM OwnerProduct op " +
            "WHERE op.owner = :owner";

        return this.getEntityManager()
            .createQuery(hql)
            .setParameter("owner", owner)
            .executeUpdate();
    }

    /**
     * Updates the product references currently pointing to the original product to instead point to
     * the updated product for the specified owners.
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

        // Installed products
        ids = session.createSQLQuery("SELECT id FROM cp_consumer WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "UPDATE cp2_installed_products SET product_uuid = ?1 " +
            "WHERE product_uuid = ?2 AND consumer_id IN (?3)";

        int ipCount = this.safeSQLUpdateWithCollection(sql, ids, updated.getUuid(), current.getUuid());
        log.debug("{} installed products updated", ipCount);

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

        // Installed products
        ids = session.createSQLQuery("SELECT id FROM cp_consumer WHERE owner_id IN (?1)")
            .setParameterList("1", ownerIds)
            .list();

        sql = "DELETE FROM cp2_installed_products WHERE product_uuid = ?1 AND consumer_id IN (?2)";

        int ipCount = this.safeSQLUpdateWithCollection(sql, ids, entity.getUuid());
        log.debug("{} installed products removed", ipCount);

        // Impl note:
        // We have a restriction in removeProduct which should prevent a product from being removed
        // from an owner if it is being used by a pool. As such, we shouldn't need to manually clean
        // the pool tables here.
    }

}