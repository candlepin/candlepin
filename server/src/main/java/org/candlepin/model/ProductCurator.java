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
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
        return (Product) this.createSecureCriteria()
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("id", id)).uniqueResult();
    }

    /**
     * @param ownerId The ID of the owner for which to lookup a product
     * @param productId The ID of the product to lookup. (note: not the database ID)
     * @return the Product which matches the given id.
     */
    @Transactional
    public Product lookupById(String ownerId, String productId) {
        return (Product) this.createSecureCriteria()
            .add(Restrictions.eq("owner.id", ownerId))
            .add(Restrictions.eq("id", productId)).uniqueResult();
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
            .add(Restrictions.eq("owner", owner)).list();
    }

    public List<Product> listAllByIds(Owner owner, Collection<? extends Serializable> ids) {
        return this.listByCriteria(
            this.createSecureCriteria()
                .add(Restrictions.eq("owner", owner))
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
     * Create the given product if it does not already exist, otherwise update
     * existing product.
     *
     * @param p Product to create or update.
     *
     * @return
     *  The updated or created Product instance
     */
    public Product createOrUpdate(Product p) {
        // TODO: Should we also verify that the UUID isn't in use?
        log.debug("Creating or updating product: {}", p);

        Product existing = this.lookupById(p.getOwner(), p.getId());

        if (existing == null) {
            log.debug("Could not find product by owner/id: {}, {}", p.getOwner(), p.getId());
            create(p);
            return p;
        }

        log.debug("Updating product by owner/id: {}, {}", p.getOwner(), p.getId());

        copy(p, existing);
        return merge(existing);
    }

    public void copy(Product src, Product dest) {
        if (src.getId() == null ? dest.getId() != null : !src.getId().equals(dest.getId())) {
            throw new RuntimeException(i18n.tr(
                "Products do not have matching IDs: {0} != {1}", src.getId(), dest.getId()
            ));
        }

        dest.setName(src.getName());
        dest.setMultiplier(src.getMultiplier());

        if (!dest.getAttributes().equals(src.getAttributes())) {
            dest.getAttributes().clear();
            for (ProductAttribute attr : src.getAttributes()) {
                ProductAttribute newAttr = new ProductAttribute(attr.getName(), attr.getValue());
                dest.addAttribute(newAttr);
            }
        }

        if (!dest.getProductContent().equals(src.getProductContent())) {
            dest.getProductContent().clear();
            for (ProductContent pc : src.getProductContent()) {
                dest.addProductContent(new ProductContent(dest, pc.getContent(), pc.getEnabled()));
            }
        }

        if (!dest.getDependentProductIds().equals(src.getDependentProductIds())) {
            dest.getDependentProductIds().clear();
            dest.setDependentProductIds(src.getDependentProductIds());
        }

    }

    @Transactional
    public Product create(Product entity) {
        /*
         * Ensure all referenced ProductAttributes are correctly pointing to
         * this product. This is useful for products being created from incoming
         * json.
         */
        for (ProductAttribute attr : entity.getAttributes()) {
            attr.setProduct(entity);
            validateAttributeValue(attr);
        }

        log.debug("Persisting new product entity: {}", entity);

        /*
         * Ensure that no circular reference exists
         */
        return super.create(entity);
    }

    @Transactional
    public Product merge(Product entity) {

        /*
         * Ensure all referenced ProductAttributes are correctly pointing to
         * this product. This is useful for products being created from incoming
         * json.
         */
        for (ProductAttribute attr : entity.getAttributes()) {
            attr.setProduct(entity);
            validateAttributeValue(attr);
        }
        /*
         * Ensure that no circular reference exists
         */

        log.debug("Merging product entity: {}", entity);

        return super.merge(entity);
    }

    private void validateAttributeValue(ProductAttribute attr) {
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
                    "The attribute ''{0}'' must be a boolean value.",
                    attr.getName()));
            }
        }
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
