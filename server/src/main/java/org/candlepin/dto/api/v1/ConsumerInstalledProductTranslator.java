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
import org.candlepin.model.ConsumerInstalledProduct;

/**
 * The ConsumerInstalledProductTranslator provides translation from ConsumerInstalledProduct model objects to
 * ConsumerInstalledProductDTOs
 */
public class ConsumerInstalledProductTranslator extends
    TimestampedEntityTranslator<ConsumerInstalledProduct, ConsumerInstalledProductDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInstalledProductDTO translate(ConsumerInstalledProduct source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInstalledProductDTO translate(ModelTranslator translator,
        ConsumerInstalledProduct source) {
        return source != null ? this.populate(translator, source, new ConsumerInstalledProductDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInstalledProductDTO populate(ConsumerInstalledProduct source,
        ConsumerInstalledProductDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInstalledProductDTO populate(ModelTranslator translator, ConsumerInstalledProduct source,
        ConsumerInstalledProductDTO dest) {

        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setProductId(source.getProductId());
        dest.setProductName(source.getProductName());
        dest.setVersion(source.getVersion());
        dest.setArch(source.getArch());
        dest.setStatus(source.getStatus());
        dest.setStartDate(source.getStartDate());
        dest.setEndDate(source.getEndDate());

        return dest;
    }

}
