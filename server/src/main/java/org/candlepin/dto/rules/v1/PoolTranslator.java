/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;

import java.util.Collections;
import java.util.Set;


/**
 * The PoolTranslator provides translation from Pool model objects to PoolDTOs,
 * as used by the Rules framework.
 */
public class PoolTranslator implements ObjectTranslator<Pool, PoolDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO translate(Pool source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO translate(ModelTranslator translator, Pool source) {
        return source != null ? this.populate(translator, source, new PoolDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO populate(Pool source, PoolDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO populate(ModelTranslator modelTranslator, Pool source, PoolDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.setId(source.getId());
        dest.setQuantity(source.getQuantity());
        dest.setStartDate(source.getStartDate());
        dest.setEndDate(source.getEndDate());
        dest.setAttributes(source.getAttributes());
        dest.setRestrictedToUsername(source.getRestrictedToUsername());
        dest.setConsumed(source.getConsumed());
        dest.setProductId(source.getProductId());
        dest.setProductAttributes(source.getProductAttributes());
        dest.setDerivedProductId(source.getDerivedProductId());

        // Process nested objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {

            Set<Product> products = source.getProvidedProducts();
            if (products != null && !products.isEmpty()) {
                for (Product prod : products) {
                    if (prod != null) {
                        dest.addProvidedProduct(
                            new PoolDTO.ProvidedProductDTO(prod.getId(), prod.getName()));
                    }
                }
            }
            else {
                dest.setProvidedProducts(Collections.<PoolDTO.ProvidedProductDTO>emptySet());
            }

            Set<Product> derivedProducts = source.getDerivedProvidedProducts();
            if (derivedProducts != null && !derivedProducts.isEmpty()) {
                for (Product derivedProd : derivedProducts) {
                    if (derivedProd != null) {
                        dest.addDerivedProvidedProduct(
                            new PoolDTO.ProvidedProductDTO(derivedProd.getId(), derivedProd.getName()));
                    }
                }
            }
            else {
                dest.setDerivedProvidedProducts(Collections.<PoolDTO.ProvidedProductDTO>emptySet());
            }
        }

        return dest;
    }
}
