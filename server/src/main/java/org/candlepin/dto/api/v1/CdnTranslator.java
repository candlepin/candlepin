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
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;

import java.time.ZoneOffset;
import java.util.Date;

/**
 * The CdnTranslator provides translation from Cdn model objects to CdnDTOs
 */
public class CdnTranslator implements ObjectTranslator<Cdn, CdnDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CdnDTO translate(Cdn source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CdnDTO translate(ModelTranslator translator, Cdn source) {
        return source != null ? this.populate(translator, source, new CdnDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CdnDTO populate(Cdn source, CdnDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CdnDTO populate(ModelTranslator modelTranslator, Cdn source, CdnDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        Date created = source.getCreated();
        dest.created(created != null ? created.toInstant().atOffset(ZoneOffset.UTC) : null);

        Date updated = source.getUpdated();
        dest.updated(updated != null ? updated.toInstant().atOffset(ZoneOffset.UTC) : null);

        dest.id(source.getId())
            .name(source.getName())
            .label(source.getLabel())
            .url(source.getUrl());

        // Process nested objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            CdnCertificate cndCert = source.getCertificate();
            dest.setCertificate(cndCert != null ?
                modelTranslator.translate(cndCert, CertificateDTO.class) : null);
        }

        return dest;
    }

}
