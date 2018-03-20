/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;

/**
 * The ConsumerTranslator provides translation from Consumer model objects to
 * ConsumerDTOs, as used by the manifest import/export API.
 */
public class ConsumerTranslator implements ObjectTranslator<Consumer, ConsumerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO translate(Consumer source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO translate(ModelTranslator translator, Consumer source) {
        return source != null ? this.populate(translator, source, new ConsumerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(Consumer source, ConsumerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerDTO populate(ModelTranslator translator, Consumer source, ConsumerDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.setUuid(source.getUuid())
            .setName(source.getName())
            .setContentAccessMode(source.getContentAccessMode());

        // Process nested objects if we have a ModelTranslator to use to the translation...
        if (translator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? translator.translate(owner, OwnerDTO.class) : null);
            dest.setType(translator.translate(source.getType(), ConsumerTypeDTO.class));
        }
        else {
            dest.setOwner(null);
            dest.setType(null);
        }

        return dest;
    }
}
