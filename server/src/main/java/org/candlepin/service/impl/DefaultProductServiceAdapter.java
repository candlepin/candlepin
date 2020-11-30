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

import org.candlepin.model.ContentCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.ProductInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;



/**
 * Default implementation of the ProductserviceAdapter.
 */
@Component
@Scope("prototype")
public class DefaultProductServiceAdapter implements ProductServiceAdapter {

    private OwnerProductCurator ownerProductCurator;
    private ProductCertificateCurator prodCertCurator;

    @Autowired
    public DefaultProductServiceAdapter(OwnerProductCurator ownerProductCurator,
        ProductCertificateCurator prodCertCurator, ContentCurator contentCurator,
        UniqueIdGenerator idGenerator) {

        this.ownerProductCurator = ownerProductCurator;
        this.prodCertCurator = prodCertCurator;
    }

    @Override
    public CertificateInfo getProductCertificate(String ownerKey, String productId) {
        // for product cert storage/generation - not sure if this should go in
        // a separate service?

        Product entity = this.ownerProductCurator.getProductByIdUsingOwnerKey(ownerKey, productId);
        return entity != null ? this.prodCertCurator.getCertForProduct(entity) : null;
    }

    @Override
    @Transactional
    public Collection<? extends ProductInfo> getProductsByIds(String ownerKey, Collection<String> ids) {
        return this.ownerProductCurator.getProductsByIdsUsingOwnerKey(ownerKey, ids).list();
    }

}
