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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;

import java.util.Objects;

public class ConsumerTranslator implements ObjectTranslator<Consumer, ConsumerDTO.Builder> {
    private final ConsumerTypeCurator consumerTypeCurator;

    public ConsumerTranslator(ConsumerTypeCurator consumerTypeCurator) {
        this.consumerTypeCurator = consumerTypeCurator;
    }

    @Override
    public ConsumerDTO.Builder translate(Consumer source) {
        return this.translate(null, source);
    }

    @Override
    public ConsumerDTO.Builder translate(ModelTranslator modelTranslator, Consumer source) {
        return source != null ? this.populate(modelTranslator, source, ConsumerDTO.builder()) : null;
    }

    @Override
    public ConsumerDTO.Builder populate(Consumer source, ConsumerDTO.Builder destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public ConsumerDTO.Builder populate(ModelTranslator modelTranslator, Consumer source, ConsumerDTO.Builder destination) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(destination, "destination must not be null");

        destination.setUuid(source.getUuid())
            .setName(source.getName())
            .setOwnerId(source.getOwnerId());

        if (modelTranslator != null) {
            ConsumerType consumerType = consumerTypeCurator.getConsumerType(source);

            destination.setType(new ConsumerTypeDTO(consumerType.getId(), consumerType.getLabel()));
        }

        return destination;
    }
}
