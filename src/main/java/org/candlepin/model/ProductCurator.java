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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;
import org.hibernate.Criteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

/**
 * interact with Products.
 */
public class ProductCurator extends AbstractHibernateCurator<Product> {

    @Inject private Config config;
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
     * Create the given product if it does not already exist, otherwise update
     * existing product.
     *
     * @param p Product to create or update.
     */
    public void createOrUpdate(Product p) {
        Product existing = lookupById(p.getId());
        if (existing == null) {
            create(p);
        }
        else {
            merge(p);
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
        validateReliesOn(entity);

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
        validateReliesOn(entity);

        return super.merge(entity);
    }

    private void validateAttributeValue(ProductAttribute attr) {
        List<String> intAttrs = config.getStringList(ConfigProperties.INTEGER_ATTRIBUTES);
        List<String> posIntAttrs = config.getStringList(
            ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES);
        List<String> longAttrs = config.getStringList(ConfigProperties.LONG_ATTRIBUTES);
        List<String> posLongAttrs = config.getStringList(
            ConfigProperties.NON_NEG_LONG_ATTRIBUTES);
        List<String> boolAttrs = config.getStringList(ConfigProperties.BOOLEAN_ATTRIBUTES);

        if (attr.getValue() == null || attr.getValue().trim().equals("")) { return; }

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

    private void validateReliesOn(Product prod) {
        if (relyPointsBackTo(prod.getId(), prod)) {
            throw new BadRequestException(i18n.tr(
                "You cannot create a circular reference for products that" +
                " ''{0}'' relies on.",
                prod.getId()));
        }
    }

    private boolean relyPointsBackTo(String productIdToFind, Product prod) {
        if (prod.getReliesOn() == null || prod.getReliesOn().size() == 0) { return false; }
        if (prod.getReliesOn().contains(productIdToFind)) {
            return true;
        }
        boolean pointsBack = false;
        for (String id : prod.getReliesOn()) {
            Product rely = this.find(id);
            if (rely == null) { continue; }
            pointsBack = pointsBack || relyPointsBackTo(productIdToFind, rely);
        }
        return pointsBack;
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

    public void addRely(Product prod, String relyId) {
        prod.addRely(relyId);
        merge(prod);
    }

    public void removeRely(Product prod, String relyId) {
        prod.removeRely(relyId);
        merge(prod);
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
}
