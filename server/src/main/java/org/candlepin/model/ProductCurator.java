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
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
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

    /**
     * default ctor
     */
    @Inject
    public ProductCurator(Configuration config, I18n i18n) {
        super(Product.class);

        this.config = config;
        this.i18n = i18n;
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
        List<Product> products = (List<Product>) this.createSecureCriteria().add(Restrictions.eq("id", id));
        return products.size() > 0 ? products.get(0) : null;
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
     * Returns a criteria that will fetch a single Product using the given owner and product IDs.
     *
     * @param ownerId
     *  The ID of the owner for which to lookup a product
     *
     * @param productId
     *  The RedHat ID of the product to lookup (not to be confused with the product's UUID)
     *
     * @return
     *  a criteria for fetching a single product by owner and product ID
     */
    public CandlepinCriteria<Product> fetchById(String ownerId, String productId) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .createAlias("owners", "owner")
            .add(Restrictions.eq("owner.id", ownerId))
            .add(Restrictions.eq("id", productId));

        return new CandlepinCriteria<Product>(criteria, this.currentSession());
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
    public CandlepinCriteria<Product> getProductsByVersion(String productId, int hashcode) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("id", productId))
            .add(Restrictions.or(
                Restrictions.isNull("entityVersion"),
                Restrictions.eq("entityVersion", hashcode)
            ));

        return new CandlepinCriteria<Product>(criteria, this.currentSession());
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
