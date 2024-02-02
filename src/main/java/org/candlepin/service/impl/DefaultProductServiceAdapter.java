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

import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.pki.certs.ProductCertificateGenerator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;

import com.google.inject.persist.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;



/**
 * Default implementation of the ProductserviceAdapter.
 */
public class DefaultProductServiceAdapter implements ProductServiceAdapter {
    private final ProductCurator productCurator;
    private final ProductCertificateGenerator productCertificateGenerator;

    @Inject
    public DefaultProductServiceAdapter(ProductCurator productCurator,
        ProductCertificateGenerator productCertificateGenerator) {
        this.productCurator = Objects.requireNonNull(productCurator);
        this.productCertificateGenerator = Objects.requireNonNull(productCertificateGenerator);
    }

    @Override
    public CertificateInfo getProductCertificate(String ownerKey, String productId) {
        // for product cert storage/generation - not sure if this should go in
        // a separate service?

        // Given the task here, we can't possibly know what namespace the product may exist in, so
        // we'll need to check both.
        Product entity = this.productCurator.resolveProductId(ownerKey, productId);
        return this.productCertificateGenerator.generate(entity);
    }

    @Override
    public List<ProductInfo> getChildrenByProductIds(Collection<String> skuId) {
        return new ArrayList<>();
    }

    @Override
    @Transactional
    public Collection<? extends ProductInfo> getProductsByIds(String ownerKey, Collection<String> ids) {
        return this.productCurator.resolveProductIds(ownerKey, ids)
            .values();
    }

}
