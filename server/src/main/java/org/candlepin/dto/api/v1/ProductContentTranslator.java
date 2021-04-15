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
import org.candlepin.model.ProductContent;


/**
 * The ProductContentTranslator provides translation from {@link ProductContent}
 * model objects to {@link ProductContentDTO}s
 */
public class ProductContentTranslator implements ObjectTranslator<ProductContent, ProductContentDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductContentDTO translate(ProductContent source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductContentDTO translate(ModelTranslator translator, ProductContent source) {
        return source != null ? this.populate(translator, source, new ProductContentDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductContentDTO populate(ProductContent source, ProductContentDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductContentDTO populate(
        ModelTranslator translator, ProductContent source, ProductContentDTO destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.enabled(source.isEnabled());
        if (translator != null) {
            destination.content(translator.translate(source.getContent(), ContentDTO.class));
        }

        return destination;
    }

}
