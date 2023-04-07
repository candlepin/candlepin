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
package org.candlepin.dto.shim;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.service.model.CertificateInfo;

/**
 * The CertificateTranslator provides translation from Certificate model objects to
 * CertificateDTOs for the API endpoints
 */
public class CertificateInfoTranslator implements ObjectTranslator<CertificateInfo, CertificateDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO translate(CertificateInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO translate(ModelTranslator translator, CertificateInfo source) {
        return source != null ? this.populate(translator, source, new CertificateDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO populate(CertificateInfo source, CertificateDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO populate(ModelTranslator translator, CertificateInfo source, CertificateDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.key(source.getKey())
            .cert(source.getCertificate())
            .id(null)
            .created(null)
            .updated(null);

        if (translator != null) {
            dest.serial(translator.translate(source.getSerial(), CertificateSerialDTO.class));
        }
        else {
            dest.serial(null);
        }

        return dest;
    }

}
