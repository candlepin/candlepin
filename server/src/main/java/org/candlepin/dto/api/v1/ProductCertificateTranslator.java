/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.ProductCertificate;


/**
 * The CertificateTranslator provides translation from Certificate model objects to
 * CertificateDTOs
 */
public class ProductCertificateTranslator
    extends TimestampedEntityTranslator<ProductCertificate, ProductCertificateDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductCertificateDTO translate(ProductCertificate source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductCertificateDTO translate(ModelTranslator translator, ProductCertificate source) {
        return source != null ? this.populate(translator, source, new ProductCertificateDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductCertificateDTO populate(ProductCertificate source, ProductCertificateDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductCertificateDTO populate(ModelTranslator translator,
        ProductCertificate source, ProductCertificateDTO dest) {
        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setKey(source.getKey());
        dest.setCert(source.getCert());

        if (translator != null) {
            dest.setProduct(translator.translate(source.getProduct(), ProductDTO.class));
        }
        else {
            dest.setProduct(null);
        }

        return dest;
    }

}
