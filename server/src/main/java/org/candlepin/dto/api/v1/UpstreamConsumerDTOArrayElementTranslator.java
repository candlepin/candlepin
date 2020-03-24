/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import java.time.ZoneOffset;

/**
 * The UpstreamConsumerDTOArrayElementTranslator provides translation from UpstreamConsumer model objects to
 * UpstreamConsumerDTOArrayElement (a special version of UpstreamConsumerDTO that only include certificate).
 *
 */
public class UpstreamConsumerDTOArrayElementTranslator implements
    ObjectTranslator<UpstreamConsumer, UpstreamConsumerDTOArrayElement> {

    @Override
    public UpstreamConsumerDTOArrayElement translate(UpstreamConsumer source) {
        return this.translate(null, source);
    }

    @Override
    public UpstreamConsumerDTOArrayElement translate(ModelTranslator translator, UpstreamConsumer source) {
        return source != null ? this.populate(translator, source, new UpstreamConsumerDTOArrayElement()) :
            null;
    }

    @Override
    public UpstreamConsumerDTOArrayElement populate(UpstreamConsumer source,
        UpstreamConsumerDTOArrayElement destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public UpstreamConsumerDTOArrayElement populate(ModelTranslator translator, UpstreamConsumer source,
        UpstreamConsumerDTOArrayElement destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.created(source.getCreated() != null ?
            source.getCreated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .updated(source.getUpdated() != null ? source.getUpdated()
            .toInstant().atOffset(ZoneOffset.UTC) : null);

        if (translator != null) {
            destination.setIdCert(translator.translate(source.getIdCert(), CertificateDTO.class));
        }

        return destination;
    }
}
