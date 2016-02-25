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
import org.candlepin.util.Util;

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
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;



// PER-ORG PRODUCT VERSIONING TODO: All of this class.

/**
 * interact with Products.
 */
public class ProductCurator extends AbstractHibernateCurator<Product> {

    private static Logger log = LoggerFactory.getLogger(ProductCurator.class);

    @Inject private Configuration config;
    @Inject private I18n i18n;

    /**
     * default ctor
     */
    public ProductCurator() {
        super(Product.class);
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
            .add(Restrictions.eq("owner", owner))
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
     * Retrieves a list of products with the specified Red Hat product ID and upstream last-update
     * timestamp. If no products were found matching the given criteria, this method returns an
     * empty list.
     *
     * @param productId
     *  The Red Hat product ID
     *
     * @param updatedUpstream
     *  The timestamp for the last upstream update
     *
     * @return
     *  a list of products matching the given product ID and upstream update timestamp
     */
    public List<Product> getProductsByVersion(String productId, Date updatedUpstream) {
        return this.listByCriteria(
            this.createSecureCriteria()
                .add(Restrictions.eq("id", productId))
                .add(Restrictions.eq("updatedUpstream", updatedUpstream))
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
        for (ProductAttribute pa : entity.getAttributes()) {
            pa.setProduct(entity);
            this.validateAttributeValue(pa);
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
        // of these objects and HQL refuses to do any joining (implicit or otherwise), which
        // prevents it from updating collections backed by a join table.
        // As an added bonus, it's quicker, but we'll have to be mindful of the memory vs backend
        // state divergence.

        Session session = this.currentSession();
        Set<String> ownerIds = new HashSet<String>();

        for (Owner owner : owners) {
            ownerIds.add(owner.getId());
        }

        // Activation key products
        String sql = "UPDATE cp2_activation_key_products SET product_uuid = ? " +
            "WHERE product_uuid = ? AND key_id IN (SELECT id FROM cp_activation_key WHERE owner_id IN ?)";

        int akCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} activation keys updated", akCount);

        // Installed products
        sql = "UPDATE cp2_installed_products SET product_uuid = ? " +
            "WHERE product_uuid = ? AND consumer_id IN (SELECT id FROM cp_consumer WHERE owner_id IN ?)";

        int ipCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} installed products updated", ipCount);

        // pool provided and derived provided products
        sql = "UPDATE cp_pool SET product_uuid = ? WHERE product_uuid = ? AND owner_id IN ?";

        int ppCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} pools updated", ppCount);

        sql = "UPDATE cp_pool SET derived_product_uuid = ? WHERE derived_product_uuid = ? AND owner_id IN ?";

        int pdpCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} pools updated", pdpCount);

        sql = "UPDATE cp2_pool_provided_products SET product_uuid = ? " +
            "WHERE product_uuid = ? AND pool_id IN (SELECT id FROM cp_pool WHERE owner_id IN ?)";

        int pppCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} provided products updated", pppCount);

        sql = "UPDATE cp2_pool_dprovided_products SET product_uuid = ? " +
            "WHERE product_uuid = ? AND pool_id IN (SELECT id FROM cp_pool WHERE owner_id IN ?)";

        int pdppCount = session.createSQLQuery(sql)
            .setParameter("1", updated.getUuid())
            .setParameter("2", current.getUuid())
            .setParameterList("3", ownerIds)
            .executeUpdate();

        log.debug("{} derived provided products updated", pdppCount);

        // product certificates
        // Looks like we don't need to do anything here, since we generate them on request. By
        // leaving them alone, they'll be generated as needed and we save some overhead here.

        this.refresh(updated);

        return updated;
    }

    @Transactional
    public Product create(Product entity) {
        log.debug("Persisting new product entity: {}", entity);

        this.validateProductReferences(entity);

        /*
         * Ensure that no circular reference exists
         */
        return super.create(entity);
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
     * @param owners
     *  The owners for which to update the product
     *
     * @return
     *  the updated product entity, or a new product entity
     */
    public Product update(Product entity, Collection<Owner> owners) {
        // TODO: Should we also verify that the UUID is either null or not in use?
        log.debug("Creating or updating product: {}", entity);

        if (entity == null) {
            throw new NullPointerException("entity");
        }

        Product existing = this.lookupByUuid(entity.getUuid());

        if (existing == null) {
            // If we're doing an exclusive update, this should be an error condition
            throw new IllegalStateException("Product has not yet been created");
        }

        // TODO:
        // Check for newer versions of the same product. We want to try to dedupe as much data as we
        // can, and if we have a newer version of the product (which matches the version provided by
        // the caller), we can just point the given orgs to the new product instead of giving them
        // their own version.
        // This is probably going to be a very expensive operation, though.

        List<Product> alternateVersions = this.getProductsByVersion(
            entity.getId(), entity.getUpdatedUpstream()
        );

        for (Product alt : alternateVersions) {
            if (alt.equals(entity)) {
                return this.updateOwnerProductReferences(entity, alt, owners);
            }
        }

        // Make sure we actually have something to update.
        if (!existing.equals(entity)) {
            // TODO: Maybe we should avoid versioning in some situations (like when only one owner
            // is referencing the product)

            // If we're making the update for every owner using the product, don't bother creating
            // a new version -- just do a raw update.
            if (owners.size() != existing.getOwners().size() || !existing.getOwners().containsAll(owners)) {
                Product copy = (Product) entity.clone();

                // Generate a new UUID so we have something unique and something we can use without
                // needing to flush and refresh.
                copy.setUuid(Util.generateUUID());

                // Update owner references on both...
                copy.setOwners(owners);
                for (Owner owner : owners) {
                    existing.removeOwner(owner);
                }

                entity = this.updateOwnerProductReferences(existing, copy, owners);
                this.merge(existing);
            }
            else {
                // Copy the details over to the existing product here
                existing.merge(entity);
                entity = existing;
            }

            this.merge(entity);
        }

        return entity;
    }

    @Transactional
    public Product merge(Product entity) {
        log.debug("Merging product entity: {}", entity);

        this.validateProductReferences(entity);

        /*
         * Ensure that no circular reference exists
         */
        return super.merge(entity);
    }

    @Transactional
    public void removeProductContent(Product prod, Content content) {
        for (ProductContent pc : prod.getProductContent()) {
            if (content.getUuid().equals(pc.getContent().getUuid())) {
                prod.getProductContent().remove(pc);
                break;
            }
        }
        merge(prod);
    }

    public boolean productHasSubscriptions(Product prod) {
        return ((Long) currentSession().createCriteria(Pool.class)
            .createAlias("providedProducts", "providedProd", JoinType.LEFT_OUTER_JOIN)
            .createAlias("derivedProvidedProducts", "derivedProvidedProd", JoinType.LEFT_OUTER_JOIN)
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
            .add(Restrictions.eq("owner", owner))
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

    // Needs an override due to the use of UUID as db identifier.
    @Override
    @Transactional
    public void delete(Product entity) {
        Product toDelete = find(entity.getUuid());
        currentSession().delete(toDelete);
    }
}
