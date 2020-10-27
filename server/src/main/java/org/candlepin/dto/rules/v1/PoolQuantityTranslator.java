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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.PoolQuantity;

/**
 * The PoolQuantityTranslator provides translation from PoolQuantity model objects to PoolQuantityDTOs.
 */
public class PoolQuantityTranslator implements ObjectTranslator<PoolQuantity, PoolQuantityDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolQuantityDTO translate(PoolQuantity source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolQuantityDTO translate(ModelTranslator translator, PoolQuantity source) {
        return source != null ? this.populate(translator, source, new PoolQuantityDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolQuantityDTO populate(PoolQuantity source, PoolQuantityDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolQuantityDTO populate(ModelTranslator translator, PoolQuantity source,
        PoolQuantityDTO destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        destination.setQuantity(source.getQuantity());

        if (translator != null) {
            destination.setPool(translator.translate(source.getPool(), PoolDTO.class));
        }

        return destination;
    }
}
