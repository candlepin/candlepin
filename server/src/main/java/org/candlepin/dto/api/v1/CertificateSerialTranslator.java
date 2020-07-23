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
import org.candlepin.model.CertificateSerial;
import org.candlepin.util.Util;

/**
 * The CertificateSerialTranslator provides translation from CertificateSerial model objects to
 * CertificateSerialDTOs
 */
public class CertificateSerialTranslator implements ObjectTranslator<CertificateSerial,
    CertificateSerialDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO translate(CertificateSerial source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO translate(ModelTranslator translator, CertificateSerial source) {
        return source != null ? this.populate(translator, source, new CertificateSerialDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO populate(CertificateSerial source, CertificateSerialDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO populate(ModelTranslator translator, CertificateSerial source,
        CertificateSerialDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .serial(source.getSerial() == null ? null : source.getSerial().longValue())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .expiration(Util.toDateTime(source.getExpiration()))
            .collected(source.isCollected())
            .revoked(source.isRevoked());

        return dest;
    }

}
