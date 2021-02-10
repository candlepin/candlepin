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
package org.candlepin.service.impl;

import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;



/**
 * Implements the product service adapter during import to direct upstream product lookup requests
 * to data originating from the imported manifest.
 */
public class ImportProductServiceAdapter implements ProductServiceAdapter {

    private final String ownerKey;
    private final Collection<? extends ProductInfo> products;

    private Map<String, ? extends ProductInfo> productMap;

    /**
     * Creates a new ImportProductServiceAdapter mapped to the specified products for the given
     * organization (owner) key.
     *
     * @param ownerKey
     *  the organization (owner) key under which the provided products will be mapped
     *
     * @param products
     *  the products this adapter will map and provide to callers
     *
     * @throws IllegalArgumentException
     *  if ownerKey is null or empty
     */
    public ImportProductServiceAdapter(String ownerKey, Collection<? extends ProductInfo> products) {
        if (ownerKey == null || ownerKey.isEmpty()) {
            throw new IllegalArgumentException("ownerKey is null or empty");
        }

        this.ownerKey = ownerKey;
        this.products = products;
    }

    /**
     * Fetches the product map, creating it if necessary
     *
     * @return
     *  the mapping of products
     */
    private Map<String, ? extends ProductInfo> getProductMap() {
        if (this.productMap == null) {
            if (this.products != null) {
                this.productMap = this.products.stream()
                    .collect(Collectors.toMap(ProductInfo::getId, Function.identity()));
            }
            else {
                this.productMap = Collections.emptyMap();
            }
        }

        return this.productMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateInfo getProductCertificate(String ownerKey, String productId) {
        return null; // Maybe throw an exception here instead?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends ProductInfo> getProductsByIds(String ownerKey,
        Collection<String> productIds) {

        if (productIds != null && this.ownerKey.equalsIgnoreCase(ownerKey)) {
            Map<String, ? extends ProductInfo> productMap = this.getProductMap();

            return productIds.stream()
                .map(pid -> productMap.get(pid))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        return new LinkedList<>();
    }

}
