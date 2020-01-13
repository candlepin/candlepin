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

import java.util.Collection;
import java.util.HashSet;
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

            Collection<Product> products = source.getProduct() == null ? null :
                source.getProduct().getProvidedProducts();
            Set<PoolDTO.ProvidedProductDTO> providedProductDTOs = new HashSet<>();
            addProvidedProducts(products, providedProductDTOs);
            dest.setProvidedProducts(providedProductDTOs);

            Collection<Product> derivedProducts = source.getDerivedProduct() == null ? null :
                source.getDerivedProduct().getProvidedProducts();
            Set<PoolDTO.ProvidedProductDTO> derivedProvidedProductDTOs = new HashSet<>();
            addProvidedProducts(derivedProducts, derivedProvidedProductDTOs);
            dest.setDerivedProvidedProducts(derivedProvidedProductDTOs);

        }

        return dest;
    }

    private void addProvidedProducts(Collection<Product> providedProducts,
        Set<PoolDTO.ProvidedProductDTO> providedProductDTOs) {

        if (providedProducts == null || providedProducts.isEmpty()) {
            return;
        }

        for (Product product : providedProducts) {
            if (product != null) {
                providedProductDTOs.add(new PoolDTO.ProvidedProductDTO(product.getId(), product.getName()));
                addProvidedProducts(product.getProvidedProducts(), providedProductDTOs);
            }
        }
    }

}
