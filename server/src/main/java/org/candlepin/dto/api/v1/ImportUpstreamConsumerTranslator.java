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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.ImportUpstreamConsumer;



/**
 * The ImportUpstreamConsumerTranslator provides translation from ImportUpstreamConsumer model objects to
 * ImportUpstreamConsumerDTOs
 */
public class ImportUpstreamConsumerTranslator extends
    TimestampedEntityTranslator<ImportUpstreamConsumer, ImportUpstreamConsumerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportUpstreamConsumerDTO translate(ImportUpstreamConsumer source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportUpstreamConsumerDTO translate(ModelTranslator translator, ImportUpstreamConsumer source) {
        return source != null ? this.populate(translator, source, new ImportUpstreamConsumerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportUpstreamConsumerDTO populate(ImportUpstreamConsumer source,
        ImportUpstreamConsumerDTO destination) {

        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportUpstreamConsumerDTO populate(ModelTranslator translator, ImportUpstreamConsumer source,
        ImportUpstreamConsumerDTO dest) {

        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setUuid(source.getUuid());
        dest.setName(source.getName());
        dest.setOwnerId(source.getOwnerId());
        dest.setApiUrl(source.getApiUrl());
        dest.setWebUrl(source.getWebUrl());
        dest.setContentAccessMode(source.getContentAccessMode());

        // Process nested objects if we have a ModelTranslator to use to the translation...
        if (translator != null) {
            dest.setConsumerType(translator.translate(source.getType(), ConsumerTypeDTO.class));
        }
        else {
            dest.setConsumerType(null);
        }

        return dest;
    }
}
