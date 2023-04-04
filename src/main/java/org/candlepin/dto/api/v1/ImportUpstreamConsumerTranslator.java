/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.server.v1.ImportUpstreamConsumerDTO;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.util.Util;


/**
 * The ImportUpstreamConsumerTranslator provides translation from ImportUpstreamConsumer model objects to
 * ImportUpstreamConsumerDTOs
 */
public class ImportUpstreamConsumerTranslator implements
    ObjectTranslator<ImportUpstreamConsumer, ImportUpstreamConsumerDTO> {

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

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .uuid(source.getUuid())
            .name(source.getName())
            .ownerId(source.getOwnerId())
            .apiUrl(source.getApiUrl())
            .webUrl(source.getWebUrl())
            .contentAccessMode(source.getContentAccessMode())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()));

        // Process nested objects if we have a ModelTranslator to use to the translation...
        if (translator != null) {
            dest.setType(translator.translate(source.getType(), ConsumerTypeDTO.class));
        }
        else {
            dest.setType(null);
        }

        return dest;
    }
}
