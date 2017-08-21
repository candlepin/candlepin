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

import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.APIDTOFactory;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ResultIterator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.Collection;
import java.util.LinkedList;



/**
 * Default implementation of the ProductserviceAdapter.
 */
public class DefaultProductServiceAdapter implements ProductServiceAdapter {

    private OwnerProductCurator ownerProductCurator;
    private ProductCertificateCurator prodCertCurator;
    private APIDTOFactory dtoFactory;

    @Inject
    public DefaultProductServiceAdapter(OwnerProductCurator ownerProductCurator,
        ProductCertificateCurator prodCertCurator, ContentCurator contentCurator,
        UniqueIdGenerator idGenerator, APIDTOFactory dtoFactory) {

        this.ownerProductCurator = ownerProductCurator;
        this.prodCertCurator = prodCertCurator;
        this.dtoFactory = dtoFactory;
    }

    @Override
    public ProductCertificate getProductCertificate(Owner owner, String productId) {
        // for product cert storage/generation - not sure if this should go in
        // a separate service?
        Product entity = this.ownerProductCurator.getProductById(owner, productId);
        return entity != null ? this.prodCertCurator.getCertForProduct(entity) : null;
    }

    @Override
    @Transactional
    public Collection<ProductDTO> getProductsByIds(Owner owner, Collection<String> ids) {
        Collection<ProductDTO> productData = new LinkedList<ProductDTO>();

        CandlepinQuery<Product> query = this.ownerProductCurator.getProductsByIds(owner, ids);
        CandlepinQuery<ProductDTO> transformed = this.dtoFactory.<Product, ProductDTO>transformQuery(query);

        for (ProductDTO dto : transformed) {
            productData.add(dto);
        }

        return productData;
    }

}
