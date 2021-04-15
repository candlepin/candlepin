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
import org.candlepin.model.ConsumerType;
import org.candlepin.util.Util;



/**
 * The ConsumerTypeTranslator provides translation from ConsumerType model objects to
 * ConsumerTypeDTOs
 */
public class ConsumerTypeTranslator implements ObjectTranslator<ConsumerType, ConsumerTypeDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerTypeDTO translate(ConsumerType source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerTypeDTO translate(ModelTranslator translator, ConsumerType source) {
        return source != null ? this.populate(translator, source, new ConsumerTypeDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerTypeDTO populate(ConsumerType source, ConsumerTypeDTO dest) {

        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerTypeDTO populate(ModelTranslator translator, ConsumerType source, ConsumerTypeDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        return dest.id(source.getId())
            .label(source.getLabel())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .manifest(source.isManifest());
    }

}
