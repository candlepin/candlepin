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
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

/**
 * The ConsumerTranslator provides translation from Consumer model objects to
 * ConsumerDTOs, as used by the manifest import/export API.
 */
public class ConsumerTranslator implements ObjectTranslator<Consumer, ConsumerDTO> {

    private ConsumerTypeCurator consumerTypeCurator;
    private OwnerCurator ownerCurator;

    public ConsumerTranslator(ConsumerTypeCurator consumerTypeCurator, OwnerCurator ownerCurator) {
        if (consumerTypeCurator == null) {
            throw new IllegalArgumentException("ConsumerTypeCurator is null");
        }
        this.consumerTypeCurator = consumerTypeCurator;
        if (ownerCurator == null) {
            throw new IllegalArgumentException("OwnerCurator is null");
        }
        this.ownerCurator = ownerCurator;
    }

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
            if (source.getOwnerId() != null) {
                Owner owner = this.ownerCurator.findOwnerById(source.getOwnerId());
                dest.setOwner(owner != null ? translator.translate(owner, OwnerDTO.class) : null);
            }
            // Temporary measure to maintain API compatibility
            if (source.getTypeId() != null) {
                ConsumerType ctype = this.consumerTypeCurator.getConsumerType(source);
                dest.setType(translator.translate(ctype, ConsumerTypeDTO.class));
            }
            else {
                dest.setType(null);
            }
        }
        else {
            dest.setOwner(null);
            dest.setType(null);
        }

        return dest;
    }
}
