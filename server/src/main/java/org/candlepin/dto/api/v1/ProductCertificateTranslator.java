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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.ProductCertificate;
import org.candlepin.util.Util;


/**
 * The ProductCertificateTranslator provides translation from Product Certificate
 * model objects to ProductCertificateDTO
 */
public class ProductCertificateTranslator
    implements ObjectTranslator<ProductCertificate, ProductCertificateDTO> {

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
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .key(source.getKey())
            .cert(source.getCert());

        return dest;
    }

}
