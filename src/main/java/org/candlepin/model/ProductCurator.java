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

import org.candlepin.auth.interceptor.EnforceAccessControl;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Query;
import org.hibernate.criterion.Restrictions;
import org.xnap.commons.i18n.I18n;

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
            return;
        }

        for (ProductAttribute attr : p.getAttributes()) {
            attr.setProduct(p);
            validateAttributeValue(attr);
        }

        merge(p);
    }

    @Transactional
    @EnforceAccessControl
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

        return super.create(entity);
    }

    private void validateAttributeValue(ProductAttribute attr) {
        String intAttrs = config.getString(ConfigProperties.INTEGER_ATTRIBUTES);
        String posAttrs = config.getString(ConfigProperties.POSITIVE_INTEGER_ATTRIBUTES);
        String longAttrs = config.getString(ConfigProperties.LONG_ATTRIBUTES);
        String posLongAttrs = config.getString(ConfigProperties.POSITIVE_LONG_ATTRIBUTES);
        String boolAttrs = config.getString(ConfigProperties.BOOLEAN_ATTRIBUTES);

        if (attr.getValue() == null || attr.getValue().trim().equals("")) { return; }

        if (intAttrs != null && intAttrs.contains(attr.getName()) ||
            posAttrs != null && posAttrs.contains(attr.getName())) {
            int value = -1;
            try {
                value = Integer.parseInt(attr.getValue());
            }
            catch (NumberFormatException nfe) {
                throw new BadRequestException(i18n.tr(
                    "The attribute ''{0}'' must be an integer value.",
                    attr.getName()));
            }
            if (posAttrs != null && posAttrs.contains(
                attr.getName()) &&
                value <= 0) {
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
            if (posAttrs != null && posAttrs.contains(
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
    @EnforceAccessControl
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
        String poolString = "select id from Subscription s" +
            " where s.product.id = :prodId";
        Query poolQuery = currentSession().createQuery(poolString).setString(
            "prodId", prod.getId());
        return poolQuery.list().size() > 0;
    }

}
