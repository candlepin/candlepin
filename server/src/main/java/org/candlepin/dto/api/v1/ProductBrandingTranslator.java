/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import org.candlepin.model.ProductBranding;

/**
 * The ProductBrandingTranslator provides translation from ProductBranding model objects to BrandingDTOs
 */
public class ProductBrandingTranslator extends TimestampedEntityTranslator<ProductBranding, BrandingDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO translate(ProductBranding source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO translate(ModelTranslator translator, ProductBranding source) {
        return source != null ? this.populate(translator, source, new BrandingDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO populate(ProductBranding source, BrandingDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO populate(ModelTranslator modelTranslator, ProductBranding source, BrandingDTO dest) {
        dest = super.populate(modelTranslator, source, dest);

        dest.setProductId(source.getProductId());
        dest.setName(source.getName());
        dest.setType(source.getType());

        return dest;
    }
}
