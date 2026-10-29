/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerTypeCurator;

import java.util.Objects;

/**
 * Translation of Consumer objects to version 2 manifest ConsumerDTOs
 */
public class ConsumerTranslator extends TimestampedEntityTranslator<Consumer, ConsumerDTO> {

    private final ConsumerTypeCurator consumerTypeCurator;

    /**
     * Initializes the consumer translator
     *
     * @param consumerTypeCurator
     *     the curator used to resolve consumer types
     */
    public ConsumerTranslator(ConsumerTypeCurator consumerTypeCurator) {
        this.consumerTypeCurator = Objects.requireNonNull(consumerTypeCurator);
    }

    @Override
    public ConsumerDTO translate(Consumer source) {
        return this.translate(null, source);
    }

    @Override
    public ConsumerDTO translate(ModelTranslator translator, Consumer source) {
        return source != null ? this.populate(translator, source, new ConsumerDTO()) : null;
    }

    @Override
    public ConsumerDTO populate(Consumer source, ConsumerDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public ConsumerDTO populate(ModelTranslator translator, Consumer source, ConsumerDTO destination) {
        destination = super.populate(translator, source, destination);

        destination.setUuid(source.getUuid());
        destination.setName(source.getName());
        destination.setOwnerId(source.getOwnerId());

        destination.setType(translator != null && source.getTypeId() != null ?
            translator.translate(this.consumerTypeCurator.getConsumerType(source), ConsumerTypeDTO.class) : null);

        return destination;
    }
}
