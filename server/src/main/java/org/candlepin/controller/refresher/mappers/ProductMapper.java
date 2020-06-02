/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher.mappers;

import org.candlepin.model.Product;
import org.candlepin.service.model.ProductInfo;



/**
 * The ProductMapper class is a simple implementation of the EntityMapper specific to Product.
 */
public class ProductMapper extends AbstractEntityMapper<Product, ProductInfo> {

    /**
     * Creates a new ProductMapper instance
     */
    public ProductMapper() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Product> getExistingEntityClass() {
        return Product.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<ProductInfo> getImportedEntityClass() {
        return ProductInfo.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addExistingEntity(Product entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        return this.addExistingEntity(entity.getId(), entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addImportedEntity(ProductInfo entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        return this.addImportedEntity(entity.getId(), entity);
    }

}
