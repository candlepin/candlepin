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
import org.candlepin.util.Util;

import java.math.BigInteger;



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


        String cert = source.getCertificateAsString();
        String payload = source.getPayloadAsString();
        StringBuilder combined = new StringBuilder();

        if (cert != null) {
            combined.append(cert);
        }

        if (payload != null) {
            combined.append(payload);
        }

        dest.id(source.getId())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .key(source.getPrivateKeyAsString())
            .cert(combined.toString());

        CertificateSerialDTO serialDto = dest.getSerial();
        if (serialDto == null) {
            serialDto = new CertificateSerialDTO();
            dest.setSerial(serialDto);
        }

        BigInteger serial = source.getSerial();
        serialDto.serial(serial != null ? serial.toString() : null)
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .expiration(Util.toDateTime(source.getExpiration()))
            .revoked(source.isRevoked());

        return dest;
    }

}
