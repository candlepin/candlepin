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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Certificate;

import java.time.ZoneOffset;
import java.util.Date;

/**
 * The CertificateTranslator provides translation from Certificate model objects to
 * CertificateDTOs for the API endpoints
 */
public class CertificateTranslator implements ObjectTranslator<Certificate, CertificateDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO translate(Certificate source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO translate(ModelTranslator translator, Certificate source) {
        return source != null ? this.populate(translator, source, new CertificateDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO populate(Certificate source, CertificateDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO populate(ModelTranslator translator, Certificate source, CertificateDTO dest) {
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
            .key(source.getKey())
            .cert(source.getCert())
            .serial(translator != null ?
            translator.translate(source.getSerial(), CertificateSerialDTO.class) : null);

        return dest;
    }

}
