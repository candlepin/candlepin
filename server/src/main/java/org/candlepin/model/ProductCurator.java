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
import org.candlepin.util.AttributeValidator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.sql.JoinType;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;



/**
 * interact with Products.
 */
public class ProductCurator extends AbstractHibernateCurator<Product> {

    @Inject private Configuration config;
    @Inject private I18n i18n;
    @Inject private AttributeValidator attributeValidator;

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
        this.validateProductReferences(entity);

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
        this.validateProductReferences(entity);

        /*
         * Ensure that no circular reference exists
         */

        return super.merge(entity);
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

    /**
     * Validates and corrects the object references maintained by the given product instance.
     *
     * @param entity
     *  The product entity to validate
     */
    protected void validateProductReferences(Product entity) {
        if (entity.getAttributes() != null) {
            for (ProductAttribute pa : entity.getAttributes()) {
                this.attributeValidator.validate(pa.getName(), pa.getValue());
                pa.setProduct(entity);
            }
        }

        if (entity.getProductContent() != null) {
            for (ProductContent pc : entity.getProductContent()) {
                pc.setProduct(entity);
            }
        }
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
}
