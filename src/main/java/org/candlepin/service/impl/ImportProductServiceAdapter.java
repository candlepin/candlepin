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
package org.candlepin.service.impl;

import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;



/**
 * Implements the product service adapter during import to direct upstream product lookup requests
 * to data originating from the imported manifest.
 */
public class ImportProductServiceAdapter implements ProductServiceAdapter {

    private final String ownerKey;
    private final Map<String, ? extends ProductInfo> productMap;

    /**
     * Creates a new ImportProductServiceAdapter that will use the specified product importer to
     * pull product information for the given organization.
     *
     * @param ownerKey
     *  the key of the organization (owner) the imported products belong to
     *
     * @param productMap
     *  a mapping of imported products, mapped by product ID
     *
     * @throws IllegalArgumentException
     *  if ownerKey is null or empty
     */
    public ImportProductServiceAdapter(String ownerKey, Map<String, ? extends ProductInfo> productMap) {
        if (ownerKey == null || ownerKey.isEmpty()) {
            throw new IllegalArgumentException("ownerKey is null or empty");
        }

        this.ownerKey = ownerKey;
        this.productMap = productMap != null ? productMap : Map.of();
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
            return productIds.stream()
                .map(pid -> this.productMap.get(pid))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        return new LinkedList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ProductInfo> getChildrenByProductIds(Collection<String> skuIds) {
        List<ProductInfo> prods = new ArrayList<>();
        if (skuIds == null || skuIds.isEmpty()) {
            return prods;
        }

        return skuIds.stream()
            .map(skuId -> this.productMap.get(skuId))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

}
