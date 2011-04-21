/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.hibernate.criterion.Restrictions;

import com.wideplay.warp.persist.Transactional;

/**
 * interact with Products.
 */
public class ProductCurator extends AbstractHibernateCurator<Product> {

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
            .add(Restrictions.eq("name", name))
            .uniqueResult();
    }

    /**
     * @param id product id to lookup
     * @return the Product which matches the given id.
     */
    @Transactional
    public Product lookupById(String id) {
        return (Product) currentSession().createCriteria(Product.class)
            .add(Restrictions.eq("id", id))
            .uniqueResult();
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
        }

        merge(p);
    }

    @Transactional
    @EnforceAccessControl
    public Product create(Product entity) {

        /* Ensure all referenced ProductAttributes are correctly pointing to
         * this product. This is useful for products being created from
         * incoming json.
         */
        for (ProductAttribute attr : entity.getAttributes()) {
            attr.setProduct(entity);
        }

        return super.create(entity);
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

}
