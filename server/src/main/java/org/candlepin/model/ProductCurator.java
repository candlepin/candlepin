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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;



/**
 * interact with Products.
 */
public class ProductCurator extends AbstractHibernateCurator<Product> {

    private static Logger log = LoggerFactory.getLogger(ProductCurator.class);

    private Configuration config;
    private I18n i18n;

    /** Whether or not all product updates should occur in place (ie: global products) */
    private boolean updateInPlace;

    /**
     * default ctor
     */
    @Inject
    public ProductCurator(Configuration config, I18n i18n) {
        super(Product.class);

        this.config = config;
        this.i18n = i18n;

        this.updateInPlace = !config.getBoolean(ConfigProperties.PER_ORG_PRODUCTS, true);
    }

    /**
     * Retrieves a Product instance for the product with the specified name. If a matching product
     * could not be found, this method returns null.
     *
     * @param owner
     *  The owner/org in which to search for a product
     *
     * @param name
     *  The name of the product to retrieve
     *
     * @return
     *  a Product instance for the product with the specified name, or null if a matching product
     *  was not found.
     */
    public Product lookupByName(Owner owner, String name) {
        return (Product) this.createSecureCriteria()
            .createAlias("owners", "owner")
            .add(Restrictions.eq("owner.id", owner.getId()))
            .add(Restrictions.eq("name", name))
            .uniqueResult();
    }

    /**
     * Performs an owner-agnostic product lookup by product ID.
     *
     * @deprecated
     *  This method is provided for legacy functionality only and may return the incorrect product
     *  instance in situations where multiple owners exist with the same product.Use lookupById with
     *  a specific owner to get accurate results.
     *
     * @param id Product ID to lookup. (note: not the database ID)
     * @return the Product which matches the given id.
     */
    @Deprecated
    @Transactional
    public Product lookupById(String id) {
        // This provides an interesting problem in that we don't have a way to reference a specific
        // version of the product. Since we're using a timestamp for the version, we can use the
        // max value as our tiebreaker

        return (Product) this.createSecureCriteria()
            .add(Restrictions.eq("id", id)).uniqueResult();
    }

    /**
     * @param owner owner to lookup product for
     * @param id Product ID to lookup. (note: not the database ID)
     * @return the Product which matches the given id.
     */
    @Transactional
    public Product lookupById(Owner owner, String id) {
        return this.lookupById(owner.getId(), id);
    }

    /**
     * @param ownerId The ID of the owner for which to lookup a product
     * @param productId The ID of the product to lookup. (note: not the database ID)
     * @return the Product which matches the given id.
     */
    @Transactional
    public Product lookupById(String ownerId, String productId) {
        return (Product) this.createSecureCriteria()
            .createAlias("owners", "owner")
            .add(Restrictions.eq("owner.id", ownerId))
            .add(Restrictions.eq("id", productId))
            .uniqueResult();
    }

    /**
     * Retrieves a Product instance for the specified product UUID. If a matching product could not
     * be found, this method returns null.
     *
     * @param uuid
     *  The UUID of the product to retrieve
     *
     * @return
     *  the Product instance for the product with the specified UUID or null if a matching product
     *  was not found.
     */
    @Transactional
    public Product lookupByUuid(String uuid) {
        return (Product) this.createSecureCriteria()
            .add(Restrictions.eq("uuid", uuid)).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Product> listByOwner(Owner owner) {
        return this.createSecureCriteria()
            .createAlias("owners", "owner")
            .add(Restrictions.eq("owner.id", owner.getId()))
            .list();
    }

    public List<Product> listAllByIds(Owner owner, Collection<? extends Serializable> ids) {
        return this.listByCriteria(
            this.createSecureCriteria()
                .createAlias("owners", "owner")
                .add(Restrictions.eq("owner.id", owner.getId()))
                .add(Restrictions.in("id", ids))
        );
    }

    /**
     * List all products with a Red Hat ID matching any of the IDs provided. Note that this method
     * may return multiple products for a given ID if multiple owners have a product with the same
     * ID.
     *
     * @param ids
     *  A collection of product IDs for which to search
     *
     * @return
     *  a list of Product instances with IDs matching those provided
     */
    @Override
    public List<Product> listAllByIds(Collection<? extends Serializable> ids) {
        return this.listByCriteria(
            this.createSecureCriteria().add(Restrictions.in("id", ids))
        );
    }

    public List<Product> listAllByUuids(Collection<? extends Serializable> uuids) {
        return this.listByCriteria(
            this.createSecureCriteria().add(Restrictions.in("uuid", uuids))
        );
    }

    /**
     * Retrieves a list of products with the specified Red Hat product ID and entity version. If no
     * products were found matching the given criteria, this method returns an empty list.
     *
     * @param productId
     *  The Red Hat product ID
     *
     * @param hashcode
     *  The hash code representing the product version
     *
     * @return
     *  a list of products matching the given product ID and entity version
     */
    public List<Product> getProductsByVersion(String productId, int hashcode) {
        return this.listByCriteria(
            this.createSecureCriteria()
                .add(Restrictions.eq("id", productId))
                .add(Restrictions.or(
                    Restrictions.isNull("entityVersion"),
                    Restrictions.eq("entityVersion", hashcode)
                ))
        );
    }

    // TODO:
    // This seems like something that should happen at the resource level, not in the curator.
    protected void validateAttributeValue(ProductAttribute attr) {
        Set<String> intAttrs = config.getSet(ConfigProperties.INTEGER_ATTRIBUTES);
        Set<String> posIntAttrs = config.getSet(
            ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES);
        Set<String> longAttrs = config.getSet(ConfigProperties.LONG_ATTRIBUTES);
        Set<String> posLongAttrs = config.getSet(
            ConfigProperties.NON_NEG_LONG_ATTRIBUTES);
        Set<String> boolAttrs = config.getSet(ConfigProperties.BOOLEAN_ATTRIBUTES);

        if (StringUtils.isBlank(attr.getValue())) { return; }

        if (intAttrs != null && intAttrs.contains(attr.getName()) ||
            posIntAttrs != null && posIntAttrs.contains(attr.getName())) {
            int value = -1;
            try {
                value = Integer.parseInt(attr.getValue());
            }
            catch (NumberFormatException nfe) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must be an integer value.",
                    attr.getName()));
            }
            if (posIntAttrs != null && posIntAttrs.contains(
                attr.getName()) &&
                value < 0) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must have a positive value.",
                    attr.getName()));
            }
        }
        else if (longAttrs != null && longAttrs.contains(attr.getName()) ||
            posLongAttrs != null && posLongAttrs.contains(attr.getName())) {
            long value = -1;
            try {
                value = Long.parseLong(attr.getValue());
            }
            catch (NumberFormatException nfe) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must be a long value.",
                    attr.getName()));
            }
            if (posLongAttrs != null && posLongAttrs.contains(
                attr.getName()) &&
                value <= 0) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must have a positive value.",
                    attr.getName()));
            }
        }
        else if (boolAttrs != null && boolAttrs.contains(attr.getName())) {
            if (attr.getValue() != null &&
                !"true".equalsIgnoreCase(attr.getValue().trim()) &&
                !"false".equalsIgnoreCase(attr.getValue()) &&
                !"1".equalsIgnoreCase(attr.getValue()) &&
                !"0".equalsIgnoreCase(attr.getValue())) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must be a Boolean value.",
                    attr.getName()));
            }
        }
    }

    /**
     * Validates and corrects the object references maintained by the given product instance.
     *
     * @param entity
     *  The product entity to validate
     *
     * @return
     *  The provided product reference
     */
    protected Product validateProductReferences(Product entity) {
        if (entity.getAttributes() != null) {
            for (ProductAttribute pa : entity.getAttributes()) {
                pa.setProduct(entity);
                this.validateAttributeValue(pa);
            }
        }

        if (entity.getProductContent() != null) {
            for (ProductContent pc : entity.getProductContent()) {
                pc.setProduct(entity);
            }
        }

        // TODO: Add more reference checks here.

        return entity;
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
    protected Product updateOwnerProductReferences(Product current, Product updated,
        Collection<Owner> owners) {
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

        this.refresh(current);
        this.refresh(updated);

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
    protected void removeOwnerProductReferences(Product entity, Collection<Owner> owners) {
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

    @Transactional
    public Product create(Product entity) {
        log.debug("Persisting new product entity: {}", entity);

        this.validateProductReferences(entity);

        return super.create(entity);
    }

    @Transactional
    public Product merge(Product entity) {
        log.debug("Merging product entity: {}", entity);

        this.validateProductReferences(entity);

        return super.merge(entity);
    }

    // Needs an override due to the use of UUID as db identifier.
    @Override
    @Transactional
    public void delete(Product entity) {
        Product toDelete = find(entity.getUuid());
        currentSession().delete(toDelete);
    }

    /**
     * Creates a new Product for the given owner, potentially using a different version than the
     * entity provided if a matching entity has already been registered for another owner.
     *
     * @param entity
     *  A Product instance representing the product to create
     *
     * @param owner
     *  The owner for which to create the product
     *
     * @return
     *  a new Product instance representing the specified product for the given owner
     */
    @Transactional
    public Product createProduct(Product entity, Owner owner) {
        log.debug("Creating new product instance from entity: {}, {}", entity, owner);

        Product existing = this.lookupById(owner, entity.getId());

        if (existing != null) {
            // If we're doing an exclusive creation, this should be an error condition
            throw new IllegalStateException("Product has already been created");
        }

        // Check if we have an alternate version we can use instead.

        // TODO: Not sure if we really even need the version check. If we have any other matching
        // product, we should probably use it -- regardless of the actual version value.
        List<Product> alternateVersions = this.getProductsByVersion(entity.getId(), entity.hashCode());

        for (Product alt : alternateVersions) {
            if (alt.equals(entity)) {
                // If we're "creating" a product, we shouldn't have any other object references to
                // update for this product. Instead, we'll just add the new owner to the product.
                alt.addOwner(owner);

                log.debug("Found a matching version, merging into existing entity: {}", alt);
                log.debug("Owners: {}", alt.getOwners());
                return this.merge(alt);
            }
        }

        entity.addOwner(owner);
        return this.create(entity);
    }

    /**
     * Updates the specified product instance, creating a new version of the product as necessary.
     * The product instance returned by this method is not guaranteed to be the same instance passed
     * in. As such, once this method has been called, callers should only use the instance output by
     * this method.
     *
     * @param entity
     *  The product entity to update
     *
     * @param owner
     *  The owner for which to update the product
     *
     * @return
     *  the updated product entity, or a new product entity
     */
    @Transactional
    public Product updateProduct(Product entity, Owner owner) {
        log.debug("Applying product update for org: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Product existing = this.lookupById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }

        if (existing == entity) {
            // Nothing to do, really. The caller likely intends for the changes to be persisted, so
            // we can do that for them.
            return this.merge(existing);
        }

        // Check for newer versions of the same product. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the product (which matches the version provided by
        // the caller), we can just point the given orgs to the new product instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.
        List<Product> alternateVersions = this.getProductsByVersion(entity.getId(), entity.hashCode());

        for (Product alt : alternateVersions) {
            if (alt.equals(entity)) {
                return this.updateOwnerProductReferences(existing, alt, Arrays.asList(owner));
            }
        }

        // Make sure we actually have something to update.
        if (!existing.equals(entity)) {
            // If we're making the update for every owner using the product, don't bother creating
            // a new version -- just do a raw update.
            if (this.updateInPlace || existing.getOwners().size() == 1) {
                // The org receiving the update is the only org using it. We can do an in-place
                // update here.
                existing.merge(entity);
                entity = existing;

                this.merge(entity);
            }
            else {
                List<Owner> owners = Arrays.asList(owner);

                // This org isn't the only org using the product. We need to create a new product
                // instance and move the org over to the new product.
                Product copy = (Product) entity.clone();

                // Clear the UUID so Hibernate doesn't think our copy is a detached entity
                copy.setUuid(null);

                // Set the owner so when we create it, we don't end up with duplicate keys...
                existing.removeOwner(owner);
                copy.setOwners(owners);

                this.merge(existing);
                copy = this.create(copy);

                entity = this.updateOwnerProductReferences(existing, copy, owners);
            }
        }

        return entity;
    }

    /**
     * Removes the specified product from the given owner. If the product is in use by multiple
     * owners, the product will not actually be deleted, but, instead, will simply by removed from
     * the given owner's visibility.
     *
     * @param entity
     *  The product entity to remove
     *
     * @param owner
     *  The owner for which to remove the product
     */
    @Transactional
    public void removeProduct(Product entity, Owner owner) {
        log.debug("Removing product from owner: {}, {}", entity, owner);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        // This has to fetch a new instance, or we'll be unable to compare the objects
        Product existing = this.lookupById(owner, entity.getId());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }

        if (this.productHasSubscriptions(existing, owner)) {
            throw new IllegalStateException("Product is currently in use by one or more pools");
        }

        log.debug("Existing owners for this product: {}", existing.getOwners());

        existing.removeOwner(owner);

        log.debug("Updated owners for this product: {}", existing.getOwners());

        if (existing.getOwners().size() == 0) {
            this.delete(existing);
        }

        // Clean up any dangling references to content
        this.removeOwnerProductReferences(existing, Arrays.asList(owner));
    }

    /**
     * Removes the specified content from the given product for a single owner. The changes made to
     * the product may result in the convergence or divergence of product versions.
     *
     * @param product
     *  the product from which to remove content
     *
     * @param content
     *  the content to remove
     *
     * @param owner
     *  the owner for which the change should take effect
     *
     * @return
     *  the updated product instance
     */
    @Transactional
    public Product removeProductContent(Product product, Collection<Content> content, Owner owner) {
        Set<ProductContent> remove = new HashSet<ProductContent>();

        for (Content test : content) {
            for (ProductContent pc : product.getProductContent()) {
                if (test == pc.getContent() || test.equals(pc.getContent())) {
                    remove.add(pc);
                }
            }
        }

        if (remove.size() > 0) {
            product = (Product) product.clone();
            product.getProductContent().removeAll(remove);

            return this.updateProduct(product, owner);
        }

        return product;
    }

    public boolean productHasSubscriptions(Product prod, Owner owner) {
        return ((Long) currentSession().createCriteria(Pool.class)
            .createAlias("providedProducts", "providedProd", JoinType.LEFT_OUTER_JOIN)
            .createAlias("derivedProvidedProducts", "derivedProvidedProd", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.or(
                Restrictions.eq("product.uuid", prod.getUuid()),
                Restrictions.eq("derivedProduct.uuid", prod.getUuid()),
                Restrictions.eq("providedProd.uuid", prod.getUuid()),
                Restrictions.eq("derivedProvidedProd.uuid", prod.getUuid())))
            .setProjection(Projections.count("id"))
            .uniqueResult()) > 0;
    }

    @SuppressWarnings("unchecked")
    public List<Product> getProductsWithContent(Owner owner, Collection<String> contentIds) {
        if (owner == null || contentIds == null || contentIds.isEmpty()) {
            return new LinkedList<Product>();
        }

        return this.createSecureCriteria()
            .createAlias("productContent", "pcontent")
            .createAlias("pcontent.content", "content")
            .createAlias("owners", "owner")
            .add(Restrictions.eq("owner.id", owner.getId()))
            .add(Restrictions.in("content.id", contentIds))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            // .setProjection(Projections.id())
            .list();
    }

    @SuppressWarnings("unchecked")
    public List<Product> getProductsWithContent(Collection<String> contentUuids) {
        if (contentUuids == null || contentUuids.isEmpty()) {
            return new LinkedList<Product>();
        }

        return this.createSecureCriteria()
            .createAlias("productContent", "pcontent")
            .createAlias("pcontent.content", "content")
            .add(Restrictions.in("content.uuid", contentUuids))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            // .setProjection(Projections.id())
            .list();
    }
}
