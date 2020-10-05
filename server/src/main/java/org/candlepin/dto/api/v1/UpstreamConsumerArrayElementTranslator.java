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
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.util.Util;

/**
 * The UpstreamConsumerArrayElementTranslator provides translation
 * from UpstreamConsumer model objects to UpstreamConsumerDTOArrayElements
 */
public class UpstreamConsumerArrayElementTranslator implements
    ObjectTranslator<UpstreamConsumer, UpstreamConsumerDTOArrayElement> {

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamConsumerDTOArrayElement translate(UpstreamConsumer source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamConsumerDTOArrayElement translate(ModelTranslator translator, UpstreamConsumer source) {
        return source != null ? this.populate(translator, source,
            new UpstreamConsumerDTOArrayElement()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamConsumerDTOArrayElement populate(UpstreamConsumer source,
        UpstreamConsumerDTOArrayElement destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UpstreamConsumerDTOArrayElement populate(ModelTranslator translator, UpstreamConsumer source,
        UpstreamConsumerDTOArrayElement dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.id(source.getId())
            .uuid(source.getUuid())
            .name(source.getName())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
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
