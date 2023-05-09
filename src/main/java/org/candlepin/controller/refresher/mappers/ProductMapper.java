/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
    protected String getEntityId(ProductInfo entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        String id = entity.getId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("entity lacks a mappable ID: " + entity);
        }

        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEntityId(Product entity) {
        return this.getEntityId((ProductInfo) entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean entitiesMatch(Product current, Product inbound) {
        if (current == null) {
            throw new IllegalArgumentException("current is null");
        }

        if (inbound == null) {
            throw new IllegalArgumentException("inbound is null");
        }

        // Impl note:
        // This test is contingent on some internal knowledge of the model and how Product.equals
        // works. Since product entities are expected to be immutable, a matching UUID *should* also
        // guarantee entity equality. Product.equals also leverages this knowledge to avoid a bunch
        // of additional field checks if the UUID is present and equal on both entities, which is
        // why we don't bother calling into it unless one of them lacks a UUID (which itself isn't
        // something that should happen).
        // Even in the weird case where the UUIDs equal but the product instances themselves are
        // not, we'll still be defaulting to taking the last one seen (which should be the locally
        // mapped instance from the DB), so we should maintain consistency even in the worst case.

        String currentUuid = current.getUuid();
        String inboundUuid = inbound.getUuid();

        return currentUuid == null || inboundUuid == null ?
            current.equals(inbound) :
            currentUuid.equals(inboundUuid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean entitiesMatch(ProductInfo current, ProductInfo inbound) {
        if (current == null) {
            throw new IllegalArgumentException("current is null");
        }

        if (inbound == null) {
            throw new IllegalArgumentException("inbound is null");
        }

        return current.equals(inbound);
    }

}
