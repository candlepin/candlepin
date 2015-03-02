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

    @Inject private Configuration config;
    @Inject private I18n i18n;

    /**
     * default ctor
     */
    public ProductCurator() {
        super(Product.class);
    }

    /**
     * @param name the product name to lookup
     * @return the Product which matches the given name.
     */
    @Transactional
    public Product lookupByName(String name) {
        return (Product) currentSession().createCriteria(Product.class)
            .add(Restrictions.eq("name", name)).uniqueResult();
    }

    /**
     * @param id product id to lookup
     * @return the Product which matches the given id.
     */
    @Transactional
    public Product lookupById(String id) {
        return (Product) currentSession().createCriteria(Product.class)
            .add(Restrictions.eq("id", id)).uniqueResult();
    }

    /**
     * @param o owner to lookup product for
     * @param id Product ID to lookup. (note: not the database ID)
     * @return the Product which matches the given id.
     */
    @Transactional
    public Product lookupById(Owner o, String id) {
        return (Product) currentSession().createCriteria(Product.class)
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.eq("id", id)).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Product> listByOwner(Owner owner) {
        return currentSession().createCriteria(Product.class)
            .add(Restrictions.eq("owner", owner)).list();
    }

    @Override
    public List<Product> listAllByIds(Collection<? extends Serializable> ids) {
        return this.listByCriteria(
            this.createSecureCriteria().add(Restrictions.in("uuid", ids))
        );
    }

    /**
     * Create the given product if it does not already exist, otherwise update
     * existing product.
     *
     * @param p Product to create or update.
     */
    public void createOrUpdate(Product p) {
        Product existing = lookupById(p.getOwner(), p.getId());
        if (existing == null) {
            create(p);
        }
        else {
            copy(p, existing);
            merge(existing);
        }
    }

    public void copy(Product incoming, Product existing) {
        if (incoming.getId() != existing.getId()) {
            throw new RuntimeException("Products do not have matching IDs: " +
                    incoming.getId() + " != " + existing.getId());
        }

        existing.setName(incoming.getName());
        existing.setMultiplier(incoming.getMultiplier());

        if (!existing.getAttributes().equals(incoming.getAttributes())) {
            existing.getAttributes().clear();
            for (ProductAttribute attr : incoming.getAttributes()) {
                ProductAttribute newAttr = new ProductAttribute(attr.getName(),
                        attr.getValue());
                existing.addAttribute(newAttr);
            }
        }

        if (!existing.getProductContent().equals(incoming.getProductContent())) {
            existing.getProductContent().clear();
            for (ProductContent pc : incoming.getProductContent()) {
                existing.addProductContent(new ProductContent(existing, pc.getContent(),
                        pc.getEnabled()));
            }
        }

        if (!existing.getDependentProductIds().equals(incoming.getDependentProductIds())) {
            existing.getDependentProductIds().clear();
            existing.setDependentProductIds(incoming.getDependentProductIds());
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
            if (content.getId().equals(pc.getContent().getId())) {
                prod.getProductContent().remove(pc);
                break;
            }
        }
        merge(prod);
    }

    public boolean productHasSubscriptions(Product prod) {
        return ((Long) currentSession().createCriteria(Subscription.class)
            .createAlias("providedProducts", "providedProd", JoinType.LEFT_OUTER_JOIN)
            .createAlias("derivedProvidedProducts", "derivedProvidedProd", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.or(
                Restrictions.eq("product", prod),
                Restrictions.eq("derivedProduct", prod),
                Restrictions.eq("providedProd.id", prod.getId()),
                Restrictions.eq("derivedProvidedProd.id", prod.getId())))
            .setProjection(Projections.count("id"))
            .uniqueResult()) > 0;
    }

    @SuppressWarnings("unchecked")
    public List<String> getProductIdsWithContent(Collection<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return new LinkedList<String>();
        }
        return currentSession().createCriteria(Product.class)
            .createAlias("productContent", "pcontent")
            .createAlias("pcontent.content", "content")
            .add(Restrictions.in("content.id", contentIds))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            .setProjection(Projections.id())
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
